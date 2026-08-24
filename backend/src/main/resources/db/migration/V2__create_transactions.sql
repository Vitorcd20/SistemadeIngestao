CREATE TABLE transactions (
    id                BIGINT PRIMARY KEY,
    transaction_date  DATE NOT NULL,
    category          VARCHAR(100) NOT NULL,
    amount            NUMERIC(14, 2) NOT NULL,
    description       TEXT,
    ingestion_job_id  BIGINT NOT NULL REFERENCES ingestion_jobs(id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_transactions_date_id
    ON transactions (transaction_date DESC, id DESC);

CREATE INDEX idx_transactions_agg
    ON transactions (transaction_date, category) INCLUDE (amount);
