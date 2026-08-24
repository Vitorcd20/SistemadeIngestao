# Sistema de Ingestão

Sistema full-stack para ingestão, processamento e exploração de grandes arquivos CSV de transações financeiras (1M+ linhas) sem carregar o arquivo na memória, sem travar o banco de dados e com uma interface que permanece responsiva independente do tamanho da tabela.

**Stack:** Java 17 + Spring Boot (backend) · React + Vite + Zustand (frontend) · PostgreSQL 16 (Docker) · orquestrado inteiramente via `docker compose`.

## Como rodar

```bash
cp .env.example .env
docker compose up --build
```

Só isso — nenhuma instalação local de Java, Node, Maven ou Postgres é necessária. Assim que os containers reportarem healthy:

- Frontend: http://localhost:3000 (registre-se ou faça login — uploads, transações e dados do painel são privados por conta)
- API do backend: não publicada diretamente — acessível apenas pelo proxy `/api` do nginx (veja [Autenticação](#autenticação) para entender o porquê). Para debug local, use `docker compose exec backend sh` ou adicione temporariamente um `ports:` no `docker-compose.yml`.
- Postgres: localhost:5432 (credenciais no `.env`)

### Gerando dados de teste

O CSV não está incluído no repositório (ignorado via `.gitignore` — são dados grandes e regeneráveis, não código-fonte). Gere um antes de testar o fluxo de upload:

```bash
python data-generator/generate_csv.py --rows 1500000 --out data-generator/transactions.csv
```

O script escreve as linhas diretamente no disco via `csv.writer` — a memória permanece estável (~15MB medidos) independente do valor de `--rows`, então é possível gerar arquivos arbitrariamente grandes sem bufferização.

## Arquitetura

```
frontend (nginx, :3000) --/api--> backend (Spring Boot, :8080) --> postgres (:5432)
```

O nginx serve o app React buildado e faz proxy reverso de `/api/*` para o backend (com `proxy_buffering off`, necessário para o endpoint SSE — veja abaixo). Em desenvolvimento local, o proxy do servidor Vite faz o mesmo papel.

### Estrutura de módulos do backend

```
backend/src/main/java/com/ingestion/
├── config/        AsyncConfig (thread pools de ingestão + SSE), records *Properties
├── controller/    UploadController, JobController, TransactionController, AggregationController
├── service/       CsvIngestionService (pipeline de ingestão em stream/batch)
├── repository/    acesso a dados via JdbcTemplate (sem JPA no caminho quente)
├── dto/           records simples, sem entidades ORM
└── exception/     GlobalExceptionHandler
```

## Como o OOM foi evitado no processamento de arquivos

Três decisões independentes se combinam para manter a memória estável independente do tamanho do arquivo — medido em **~180–205MB** processando um CSV de 1,5M linhas / 78MB no container de produção (base ~180MB com tabela vazia; pico ~205MB durante a ingestão; volta a ~186MB ao final — sem tendência de crescimento ao longo da execução):

1. **O upload nunca é bufferizado em memória.**
   `spring.servlet.multipart.file-size-threshold` é configurado baixo (`1KB`), o que força o Tomcat a fazer spool do corpo multipart diretamente em um arquivo temporário em disco em vez de mantê-lo como array de bytes. O `UploadController` então lê desse arquivo spoolado para seu próprio caminho temporário gerenciado via `Files.copy(inputStream, targetPath)` — uma cópia com buffer de tamanho fixo, não uma chamada a `getBytes()`.

2. **O CSV é parseado linha por linha via parser em stream, nunca completamente materializado.** O `CsvIngestionService` abre o arquivo através de um `BufferedReader` encapsulado no `CSVParser` do Apache Commons CSV, iterado com um `for` simples. O iterador do Commons CSV puxa um registro por vez do reader subjacente — o conjunto de trabalho instantâneo é um `CSVRecord`, não o arquivo inteiro. (Chamar `parser.getRecords()` em vez disso derrotaria silenciosamente isso — materializa todas as linhas como uma `List` de uma vez, que é a forma clássica de um pipeline desse tipo causar OOM acidentalmente. Nunca chamamos isso.)

3. **Apenas um batch (padrão 5.000 linhas) é mantido em memória por vez.**
   As linhas parseadas acumulam em um único `ArrayList<TransactionRow>` reutilizado. Ao atingir o tamanho do batch, ele é enviado ao Postgres via `JdbcTemplate.batchUpdate` e limpo com `.clear()` — não reatribuído, então a capacidade do array de suporte é reutilizada em todos os ~300 batches de um arquivo de 1,5M linhas em vez de ser realocada. A lista é limitada por `app.ingestion.batch-size` independente de quantas linhas o arquivo tem: um arquivo de 100 linhas e um de 100M linhas atingem o mesmo pico de memória por batch, apenas por um número diferente de iterações.

Falhas por linha (data inválida, valor inválido, id ausente, etc.) são capturadas individualmente, contadas e ignoradas — não cancelam o job inteiro. A **amostra de erros mantida para o status/UI é limitada** (`app.ingestion.error-sample-limit`, padrão 50) em vez de ilimitada, porque um arquivo com um bug de formatação sistêmico poderia gerar um milhão de strings de erro e derrotar o propósito.

**Assíncrono, não bloqueante:** `POST /api/uploads` retorna assim que o arquivo é spoolado em disco e uma linha de job é criada — não aguarda a ingestão. O processamento roda em um thread pool dedicado pequeno (`AsyncConfig.ingestionExecutor`, 2 threads) via `@Async`, mantido pequeno deliberadamente já que a ingestão é limitada por escrita no banco, não por CPU — mais threads significariam apenas mais escritores de batch concorrentes disputando a mesma tabela e índices, sem ganho de throughput.

## Estratégia de batch insert

- `JdbcTemplate.batchUpdate` puro, **não** Spring Data JPA, no caminho de ingestão. O contexto de persistência do JPA mantém uma referência gerenciada para cada entidade que toca em uma sessão — exatamente o padrão de crescimento de memória ilimitado que este sistema foi projetado para evitar. `TransactionRepository` e `IngestionJobRepository` são ambos JDBC puro.
- `INSERT ... ON CONFLICT (id) DO NOTHING`, em batches de 5.000 linhas por chamada ao `batchUpdate`. Isso torna o re-upload do mesmo arquivo idempotente — verificado fazendo upload do arquivo de 1,5M linhas duas vezes: a segunda execução inseriu zero linhas duplicadas e, graças aos caches quentes do OS/Postgres, completou mais rápido (~37s vs ~103s em testes locais).
- O contador `rows_processed` do job é gravado no Postgres **uma vez por batch** (a cada 5.000 linhas), não uma vez por linha — ~300 gravações de status para um arquivo de 1,5M linhas em vez de 1,5M, mantendo o overhead de rastreamento de status desprezível em relação às gravações reais de dados.

**Throughput medido:** ~16.300 linhas/seg (1,5M linhas em 92s) no container Docker empacotado nesta máquina de desenvolvimento. Isso varia com hardware/disco, mas a propriedade de memória estável não varia — é função do limite do tamanho do batch, não da máquina.

## Status em tempo real: SSE, consultando o banco

`GET /api/jobs/{id}/events` abre um `SseEmitter` sustentado por um **loop de polling que lê a linha de `ingestion_jobs` a cada segundo**, em vez de conectar a thread de ingestão diretamente ao emitter com um pub/sub em memória. Essa foi uma troca deliberada de simplicidade vs latência: a conexão direta entregaria atualizações instantaneamente, mas requer rastrear emitters por job entre threads e tratar reconexões/múltiplas abas corretamente. Consultar o banco significa:

- qualquer número de clientes (abas, navegadores) pode se inscrever no mesmo job independentemente, e uma reconexão simplesmente retoma o polling — sem estado compartilhado para reconciliar;
- a latência de ~1s de atualização é imperceptível para um processo que leva dezenas de segundos a minutos;
- `GET /api/jobs/{id}` (polling simples, sem SSE) está disponível como fallback se a conexão `EventSource` do cliente for bloqueada por um proxy ou firewall.

## Paginação: keyset, não OFFSET

`GET /api/transactions?cursor=&limit=` usa **paginação por keyset ("seek")**, não `OFFSET/LIMIT`. Essa foi uma escolha deliberada, não um padrão:

- `OFFSET 500000 LIMIT 50` força o Postgres a escanear e descartar as primeiras 500.000 linhas correspondentes em cada requisição — o custo cresce linearmente com a profundidade da página. Em uma tabela de 1M+ linhas, isso se torna o custo dominante do endpoint.
- A paginação por keyset em vez disso pede "me dê as 50 linhas após este ponto específico", expresso como `WHERE (transaction_date, id) < (?, ?)`, que o Postgres pode satisfazer com uma busca direta por índice — **verificado via `EXPLAIN ANALYZE`: tempo de resposta de 7ms, constante independente de quão fundo no cursor a tabela aponte**, versus um custo que escala com a profundidade do offset na abordagem OFFSET.
- **O trade-off:** clientes podem avançar/voltar via cursor opaco, mas não podem pular para um número de página arbitrário ("ir para página 4.213"). Para uma tabela desse tamanho, essa é a troca certa — um seletor de página numerado sobre milhões de linhas não é significativamente mais útil para um usuário do que anterior/próximo de qualquer forma, e não é barato de suportar.
- O `transactionsStore` do frontend (Zustand) implementa "Anterior" mantendo um pequeno histórico client-side de cursores visitados e re-buscando, em vez de cachear conteúdo de páginas — cada busca é ~7ms, então re-buscar é mais barato do que a complexidade de um cache.

## Design de índices

```sql
PRIMARY KEY (id)                                                    -- dedup no batch insert, lookups por ponto
CREATE INDEX idx_transactions_date_id ON transactions
    (transaction_date DESC, id DESC);                               -- paginação
CREATE INDEX idx_transactions_agg ON transactions
    (transaction_date, category) INCLUDE (amount);                  -- agregação
```

**`idx_transactions_date_id` (paginação).** Corresponde exatamente à ordem de classificação e predicado de cursor do endpoint de listagem (`ORDER BY transaction_date DESC, id DESC` / `WHERE (transaction_date, id) < (?, ?)`), então é usado como busca direta por índice em vez de scan.

**`idx_transactions_agg` (agregação) — e uma correção que vale documentar.** O endpoint de agregação (`GET /api/aggregations/by-category-month`) computa `SUM(amount) GROUP BY category, month`, opcionalmente filtrado por intervalo de data — correspondendo a como um dashboard real realmente consulta isso (ex.: "últimos 12 meses por categoria"), não um dump completo sem filtro.

A primeira versão deste índice era `(category, transaction_date) INCLUDE (amount)`, assumindo que liderar com a primeira coluna do `GROUP BY` ajudaria. Testando com `EXPLAIN ANALYZE` contra a tabela real de 1,5M linhas mostrou que essa suposição estava errada para o padrão de acesso real: um filtro de intervalo de data contra esse índice produziu um `Bitmap Heap Scan` com fetches reais do heap — **841ms**. Reordenar para liderar com `transaction_date` (a coluna sobre a qual o filtro realmente atua) em vez disso dá um **Index Only Scan com 0 heap fetches — 68ms, ~12x mais rápido** — porque o Postgres agora pode buscar diretamente no intervalo de datas e ler `category`+`amount` direto do índice sem tocar na tabela. `category` ainda acompanha como segunda coluna-chave (necessária para o `GROUP BY`) e `amount` é carregado via `INCLUDE` (não parte da chave — é apenas lido, nunca filtrado ou ordenado).

Uma chamada **sem filtro** ao mesmo endpoint (sem `from`/`to`) ainda recebe razoavelmente um `Seq Scan` do planner, e isso está correto, não é uma regressão — sem filtro, a query toca 100% da tabela de qualquer forma, e um scan sequencial do heap é mais barato do que percorrer todo o nível folha do índice para a mesma contagem de linhas.

**Por que não indexar `category` isoladamente, ou liderar com ela?** A query realista do dashboard filtra por data (um intervalo) e agrupa por categoria (uma dimensão de baixa cardinalidade, 14 valores nos dados gerados) — category isolada não é um filtro seletivo, então um índice liderando com ela não reduziria um scan da forma que liderar com a data faz. Deliberadamente não adicionando um quarto índice para um padrão de query que os endpoints não servem — cada índice adicional é pago em cada uma das 1,5M+ linhas inseridas em batch.

## Referência da API

Todos os endpoints abaixo, exceto `/api/auth/register` e `/api/auth/login`, requerem uma sessão autenticada e estão escopados aos dados do chamador — veja [Autenticação](#autenticação).

| Endpoint | Finalidade |
|---|---|
| `POST /api/auth/register` | Cria uma conta `{username, password}` e loga imediatamente |
| `POST /api/auth/login` | `{username, password}` → cookie de sessão |
| `POST /api/auth/logout` | Invalida a sessão atual |
| `GET /api/auth/me` | Usuário autenticado atual |
| `POST /api/uploads` (multipart `file`) | Aceita um CSV, retorna `{jobId}` imediatamente, processa em background |
| `GET /api/jobs/{id}` | Status do job: linhas processadas, falhas, amostra de erros, estado |
| `GET /api/jobs/{id}/events` | Stream SSE do mesmo status, cadência ~1s |
| `GET /api/transactions?cursor=&limit=` | Listagem de transações paginada por keyset |
| `GET /api/aggregations/by-category-month?from=&to=` | `SUM(amount)` agrupado por categoria + mês, intervalo de data opcional |
| `GET /api/aggregations/summary` | Métricas de cabeçalho do painel (total de transações, volume líquido, contagem de categorias, intervalo de datas) |

## Autenticação

Usuário/senha, baseada em cookie de sessão (Spring Security), não JWT — o frontend é servido same-origin pelo nginx, então não há problema de armazenamento de token cross-origin para o JWT resolver, e um cookie de sessão evita armazenamento de token exposto a XSS. CSRF é tratado via cookie que o SPA lê e ecoa de volta como `X-XSRF-TOKEN` nas requisições de mudança de estado.

**Os dados de cada usuário são completamente isolados**, não apenas o histórico de jobs: uploads, transações e agregações do painel são todos escopados à conta que fez o upload via um `owner_user_id` desnormalizado diretamente em `transactions` (em vez de joined de `ingestion_jobs`), então paginação e agregação mantêm o comportamento de index-only-scan descrito acima. O painel de uma conta nova começa vazio até que ela faça upload do próprio arquivo.

O backend não tem porta publicada no `docker-compose.yml` — o nginx é o único caminho de entrada. É isso que torna seguro confiar no `X-Forwarded-For` do nginx incondicionalmente (`server.forward-headers-strategy: framework`) para o rate limiting por IP em `/api/uploads` e `/api/auth/*`: nada externo pode alcançar o backend diretamente para falsificá-lo.

## Trade-offs e suposições, explicitamente

- **O schema do CSV é esperado ser `id,date,category,amount,description`** com datas ISO (`YYYY-MM-DD`) — correspondendo ao que `data-generator/generate_csv.py` produz. Linhas que não parseiam são ignoradas e contadas, não coercidas silenciosamente.
- **O `id` é tomado diretamente do CSV como chave primária**, não uma chave substituta gerada. Isso torna re-uploads idempotentes via `ON CONFLICT DO NOTHING` gratuitamente, ao custo de assumir que os ids upstream já são únicos (verdadeiro para o gerador; precisaria ser revisitado para ingestão multi-fonte onde colisões de id entre fontes são possíveis).
- **Amostras de erro são limitadas a 50 por job**, não exaustivas. Um arquivo sistematicamente quebrado (delimitador errado, ordem de colunas errada) reportará suas primeiras 50 falhas e uma contagem total — suficiente para diagnosticar o problema — em vez de arriscar memória/armazenamento ilimitado para uma entrada patológica.
- **`GET /api/aggregations/summary` é um full-table scan** (`COUNT(DISTINCT category)` em particular não pode ser servido por uma busca de índice). Medido em ~1s contra 1,5M linhas. Aceitável porque é uma chamada única por carregamento do painel, não um caminho quente em loop — mas precisaria de um rollup materializado ou contagem aproximada se a atualização do resumo precisasse ser sub-segundo em 10x esse volume de dados.
- **Atualizações SSE têm ~1s de latência, não são instantâneas** (veja o design de polling acima) — uma troca deliberada de simplicidade para um processo que leva dezenas de segundos a minutos.
- **Role de usuário único implícito, sem tabela de roles/permissões.** Cada conta pode fazer upload/visualizar seus próprios dados e nada mais — não há role admin/elevada no escopo, então um sistema de roles seria complexidade não utilizada.
- **O bundle de produção do frontend é ~614KB** (Recharts é a maioria disso); notado pelo output de build do Vite mas não endereçado, já que separar o route do painel em code splitting não valeria a complexidade para este escopo. Usaria `React.lazy()` no route do painel primeiro se isso importasse.
