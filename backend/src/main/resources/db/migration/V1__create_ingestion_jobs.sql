CREATE TABLE ingestion_jobs (
    id              BIGSERIAL PRIMARY KEY,
    file_name       TEXT NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    rows_processed  BIGINT NOT NULL DEFAULT 0,
    rows_failed     BIGINT NOT NULL DEFAULT 0,
    error_sample    TEXT,
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_ingestion_jobs_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'))
);
