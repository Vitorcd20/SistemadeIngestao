# Ingestion System

A full-stack system for ingesting, processing, and exploring large CSV files of
financial transactions (1M+ rows) without loading the file into memory, without
running the database into a slow crawl, and with a UI that stays responsive no
matter how large the underlying table gets.

**Stack:** Java 17 + Spring Boot (backend) · React + Vite + Zustand (frontend) ·
PostgreSQL 16 (Docker) · orchestrated entirely via `docker compose`.

## Running it

```bash
cp .env.example .env
docker compose up --build
```

That's it — no local Java, Node, Maven, or Postgres installation required. Once
containers report healthy:

- Frontend: http://localhost:3000 (register/log in here — all uploads,
  transactions, and dashboard data are private to your account)
- Backend API: not published directly — reachable only through the frontend's
  `/api` proxy (see [Authentication](#authentication) for why). For local
  debugging, use `docker compose exec backend sh` or temporarily add a
  `ports:` mapping back in `docker-compose.yml`.
- Postgres: localhost:5432 (credentials in `.env`)

### Generating test data

A CSV isn't included in the repo (kept out via `.gitignore` — it's large,
regenerable data, not source). Generate one before testing the upload flow:

```bash
python data-generator/generate_csv.py --rows 1500000 --out data-generator/transactions.csv
```

This streams rows directly to disk via `csv.writer` — memory stays flat
(~15MB measured) regardless of `--rows`, so it can generate arbitrarily large
files without buffering them.

## Architecture

```
frontend (nginx, :3000) --/api--> backend (Spring Boot, :8080) --> postgres (:5432)
```

nginx serves the built React app and reverse-proxies `/api/*` to the backend
(with `proxy_buffering off`, required for the SSE endpoint — see below). In
local dev, Vite's dev server proxy does the same job.

### Backend module layout

```
backend/src/main/java/com/ingestion/
├── config/        AsyncConfig (ingestion + SSE thread pools), *Properties records
├── controller/     UploadController, JobController, TransactionController, AggregationController
├── service/        CsvIngestionService (the streaming/batch ingestion pipeline)
├── repository/     JdbcTemplate-based data access (no JPA on the hot path)
├── dto/             plain records, no ORM entities
└── exception/       GlobalExceptionHandler
```

## How OOM was avoided during file processing

Three independent decisions compound to keep memory flat regardless of file
size — measured at **~180–205MB** processing a 1.5M-row / 78MB CSV in the
packaged production container (baseline ~180MB with an empty table; peak
~205MB mid-ingestion; back to ~186MB after — no growth trend across the run):

1. **The upload itself is never buffered in memory.**
   `spring.servlet.multipart.file-size-threshold` is set low (`1KB`), which
   forces Tomcat to spool the incoming multipart body straight to a temp file
   on disk instead of holding it as a byte array. `UploadController` then
   streams from that spooled file to its own managed temp path via
   `Files.copy(inputStream, targetPath)` — a fixed-size-buffer copy, not a
   `getBytes()` call.

2. **The CSV is parsed row-by-row via a streaming parser, never fully
   materialized.** `CsvIngestionService` opens the file through a
   `BufferedReader` wrapped in Apache Commons CSV's `CSVParser`, iterated with
   a plain `for` loop. Commons CSV's iterator pulls one record at a time from
   the underlying reader — the moment-to-moment working set is one
   `CSVRecord`, not the file. (Calling `parser.getRecords()` instead would
   silently defeat this — it materializes every row as a `List` up front, which
   is the classic way this kind of pipeline accidentally OOMs. We never call it.)

3. **Only one batch (default 5,000 rows) is held in memory at a time.**
   Parsed rows accumulate into a single, reused `ArrayList<TransactionRow>`.
   Once it reaches the batch size, it's flushed to Postgres via
   `JdbcTemplate.batchUpdate` and `.clear()`'d — not reassigned, so the
   backing array's capacity is reused across all ~300 batches in a 1.5M-row
   file rather than re-allocated. The list is bounded by `app.ingestion.batch-size`
   regardless of how many total rows the file has: a 100-row file and a
   100M-row file both peak at the same per-batch memory footprint, just for
   a different number of iterations.

Row-level failures (bad date, bad amount, missing id, etc.) are caught per-row,
counted, and skipped — they don't fail the whole job. The **error sample kept
for the status/UI is capped** (`app.ingestion.error-sample-limit`, default 50)
rather than unbounded, because a file with a systemic formatting bug could
otherwise produce a million error strings and defeat the whole point.

**Async, not blocking:** `POST /api/uploads` returns as soon as the file is
spooled to disk and a job row is created — it does not wait for ingestion.
Processing runs on a small dedicated thread pool (`AsyncConfig.ingestionExecutor`,
2 threads) via `@Async`, deliberately kept small since ingestion is
DB-write-bound, not CPU-bound — more threads would just mean more concurrent
batch writers contending for the same table and indexes, not more throughput.

## The batch-insert strategy

- Raw `JdbcTemplate.batchUpdate`, **not** Spring Data JPA, on the ingestion
  path. JPA's persistence context retains a managed reference to every entity
  it touches in a session — exactly the unbounded-memory-growth pattern this
  system is designed to avoid. `TransactionRepository` and
  `IngestionJobRepository` are both plain JDBC.
- `INSERT ... ON CONFLICT (id) DO NOTHING`, batched at 5,000 rows per
  `batchUpdate` call. This makes re-uploading the same file idempotent — verified
  by uploading the 1.5M-row file twice: the second run inserted zero
  duplicate rows and, thanks to warm OS/Postgres caches, completed faster
  (~37s vs ~103s in local dev testing).
- The job's `rows_processed` counter is written to Postgres **once per batch**
  (every 5,000 rows), not once per row — that's ~300 status writes for a
  1.5M-row file instead of 1.5M, keeping the status-tracking overhead
  negligible relative to the actual data writes.

**Measured throughput:** ~16,300 rows/sec (1.5M rows in 92s) in the packaged
Docker container on this dev machine. This will vary with hardware/disk, but
the flat-memory property does not — it's a function of the batch-size bound,
not the machine.

## Real-time status: SSE, polling the database

`GET /api/jobs/{id}/events` opens an `SseEmitter` backed by a **poll loop that
reads the `ingestion_jobs` row every second**, rather than wiring the
ingestion thread directly to the emitter with an in-memory pub/sub. This was
a deliberate simplicity/latency trade-off: direct wiring would deliver
updates instantly, but requires tracking emitters per job across threads and
handling reconnects/multiple tabs correctly. Polling the DB means:

- any number of clients (tabs, browsers) can subscribe to the same job
  independently, and a reconnect just resumes polling — no shared state to
  reconcile;
- the ~1s update latency is imperceptible for a process that takes tens of
  seconds to minutes;
- `GET /api/jobs/{id}` (plain polling, no SSE) is available as a fallback if
  a client's `EventSource` connection is blocked by a proxy or firewall.

## Pagination: keyset, not OFFSET

`GET /api/transactions?cursor=&limit=` uses **keyset ("seek") pagination**,
not `OFFSET/LIMIT`. This was a deliberate choice, not a default:

- `OFFSET 500000 LIMIT 50` forces Postgres to scan and discard the first
  500,000 matching rows on every request — cost grows linearly with page
  depth. On a 1M+ row table this becomes the dominant cost of the endpoint.
- Keyset pagination instead asks "give me the 50 rows after this specific
  point," expressed as `WHERE (transaction_date, id) < (?, ?)`, which
  Postgres can satisfy with a direct index seek — **verified via
  `EXPLAIN ANALYZE`: 7ms response time, constant regardless of how deep into
  the table the cursor points**, versus a cost that scales with offset depth
  for the OFFSET approach.
- **The trade-off:** clients can step forward/backward via an opaque cursor,
  but can't jump to an arbitrary page number ("go to page 4,213"). For a
  table this size, that's the right trade — a numbered page picker over
  millions of rows isn't meaningfully more useful to a user than next/previous
  anyway, and it isn't cheap to support.
- The frontend's `transactionsStore` (Zustand) implements "Previous" by
  keeping a small client-side history of visited cursors and re-fetching,
  rather than caching page contents — each fetch is ~7ms, so re-fetching is
  cheaper than the complexity of a cache.

## Index design

```sql
-- transactions table
PRIMARY KEY (id)                                                    -- dedup on batch insert, point lookups
CREATE INDEX idx_transactions_date_id ON transactions
    (transaction_date DESC, id DESC);                                -- pagination
CREATE INDEX idx_transactions_agg ON transactions
    (transaction_date, category) INCLUDE (amount);                   -- aggregation
```

**`idx_transactions_date_id` (pagination).** Matches the listing endpoint's
sort order and cursor predicate exactly (`ORDER BY transaction_date DESC, id
DESC` / `WHERE (transaction_date, id) < (?, ?)`), so it's used as a direct
index seek rather than a scan.

**`idx_transactions_agg` (aggregation) — and a correction worth documenting.**
The aggregation endpoint (`GET /api/aggregations/by-category-month`) computes
`SUM(amount) GROUP BY category, month`, optionally filtered by a date range —
matching how a real dashboard actually queries this (e.g. "last 12 months by
category"), not an unfiltered full-history dump.

The first version of this index was `(category, transaction_date) INCLUDE
(amount)`, on the assumption that leading with the `GROUP BY`'s first column
would help. Testing it with `EXPLAIN ANALYZE` against the real 1.5M-row table
showed that assumption was wrong for the actual access pattern: a date-range
filter against that index produced a `Bitmap Heap Scan` with real heap
fetches — **841ms**. Reordering to lead with `transaction_date` (the column
the filter is actually on) instead gives an **Index Only Scan with 0 heap
fetches — 68ms, ~12x faster** — because Postgres can now seek directly to the
date range and read `category`+`amount` straight out of the index without
touching the table at all. `category` still rides along as the second key
column (needed for the `GROUP BY`) and `amount` is carried via `INCLUDE` (not
part of the key — it's only ever read, never filtered or sorted on).

An **unfiltered** call to the same endpoint (no `from`/`to`) reasonably still
gets a plain `Seq Scan` from the planner, and that's correct, not a
regression — with no filter, the query touches 100% of the table either way,
and a sequential heap scan is cheaper than walking the entire index leaf
level for the same row count.

**Why not index `category` alone, or lead with it?** The realistic dashboard
query filters by date (a range) and groups by category (a low-cardinality
dimension, 14 values in the generated data) — category alone isn't a
selective filter, so an index leading with it wouldn't narrow a scan the way
leading with the date does. Deliberately not adding a fourth index for a
query pattern the endpoints don't actually serve — every additional index is
paid for on every one of the 1.5M+ batch-inserted rows.

## API reference

All endpoints below except `/api/auth/register` and `/api/auth/login` require
an authenticated session and are scoped to the caller's own data — see
[Authentication](#authentication).

| Endpoint | Purpose |
|---|---|
| `POST /api/auth/register` | Create an account `{username, password}`, log in immediately |
| `POST /api/auth/login` | `{username, password}` → session cookie |
| `POST /api/auth/logout` | Invalidate the current session |
| `GET /api/auth/me` | Current authenticated user |
| `POST /api/uploads` (multipart `file`) | Accepts a CSV, returns `{jobId}` immediately, processes in the background |
| `GET /api/jobs/{id}` | Job status: rows processed, rows failed, error sample, state |
| `GET /api/jobs/{id}/events` | SSE stream of the same status, ~1s cadence |
| `GET /api/transactions?cursor=&limit=` | Keyset-paginated transaction listing |
| `GET /api/aggregations/by-category-month?from=&to=` | `SUM(amount)` grouped by category + month, optional date range |
| `GET /api/aggregations/summary` | Dashboard headline metrics (total transactions, net volume, category count, date range) |

## Authentication

Username/password, session-cookie based (Spring Security), not JWT — the
frontend is served same-origin through nginx, so there's no cross-origin
token-storage problem for JWT to solve, and a session cookie avoids
XSS-exposed token storage. CSRF is handled via a cookie the SPA reads and
echoes back as `X-XSRF-TOKEN` on state-changing requests.

**Every user's data is fully isolated**, not just their job history: uploads,
transactions, and dashboard aggregates are all scoped to the uploading
account via an `owner_user_id` denormalized directly onto `transactions`
(rather than joined from `ingestion_jobs`), so pagination and aggregation
keep the index-only-scan behavior described above — just leading with
`owner_user_id` instead of `transaction_date`. A brand-new account's
dashboard starts empty until it uploads its own file.

The backend has no published port in `docker-compose.yml` — nginx is the
only path in. That's what makes it safe to trust nginx's `X-Forwarded-For`
unconditionally (`server.forward-headers-strategy: framework`) for the
per-IP rate limiting on `/api/uploads` and `/api/auth/*`: nothing external
can reach the backend directly to spoof it.

## Trade-offs and assumptions, explicitly

- **CSV schema is trusted to be `id,date,category,amount,description`** with
  ISO dates (`YYYY-MM-DD`) — matching what `data-generator/generate_csv.py`
  produces. Rows that don't parse are skipped and counted, not silently
  coerced.
- **`id` is taken directly from the CSV as the primary key**, not a generated
  surrogate key. This makes re-uploads idempotent via `ON CONFLICT DO
  NOTHING` for free, at the cost of assuming upstream ids are already unique
  (true for the generator; would need revisiting for multi-source ingestion
  where id collisions across sources are possible).
- **Error samples are capped at 50 per job**, not exhaustive. A systemically
  broken file (wrong delimiter, wrong column order) will report its first 50
  failures and a total count — enough to diagnose the problem — rather than
  risk unbounded memory/storage for a pathological input.
- **`GET /api/aggregations/summary` is a full-table scan** (`COUNT(DISTINCT
  category)` in particular can't be served by an index seek). Measured at
  ~1s against 1.5M rows. Acceptable because it's a once-per-dashboard-load
  call, not a hot path in a loop — but it would need a materialized
  rollup or approximate counting if summary freshness needed to be
  sub-second at 10x this data size.
- **SSE updates are ~1s-latency, not instant** (see the polling design above)
  — a deliberate simplicity trade for a process that takes tens of seconds
  to minutes.
- **Single implicit user role, no roles/permissions table.** Every account
  can upload/view its own data and nothing else — there's no admin/elevated
  role in scope, so a roles system would be unused complexity.
- **The frontend's production bundle is ~614KB** (Recharts is the majority of
  that); noted by Vite's build output but not addressed, since code-splitting
  the dashboard route wasn't worth the complexity for this scope. Would
  reach for `React.lazy()` on the dashboard route first if this mattered.
