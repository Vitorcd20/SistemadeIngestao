ALTER TABLE ingestion_jobs
    ADD COLUMN owner_user_id BIGINT NOT NULL REFERENCES users(id);

ALTER TABLE transactions
    ADD COLUMN owner_user_id BIGINT NOT NULL REFERENCES users(id);

DROP INDEX idx_transactions_date_id;
CREATE INDEX idx_transactions_owner_date_id
    ON transactions (owner_user_id, transaction_date DESC, id DESC);

DROP INDEX idx_transactions_agg;
CREATE INDEX idx_transactions_owner_agg
    ON transactions (owner_user_id, transaction_date, category) INCLUDE (amount);
