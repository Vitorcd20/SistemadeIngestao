ALTER TABLE ingestion_jobs
    ADD COLUMN public_id UUID NOT NULL DEFAULT gen_random_uuid();

CREATE UNIQUE INDEX idx_ingestion_jobs_public_id
    ON ingestion_jobs (public_id);
