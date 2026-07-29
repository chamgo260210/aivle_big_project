ALTER TABLE analysis_jobs ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE analysis_jobs ADD COLUMN next_attempt_at TIMESTAMP;
ALTER TABLE analysis_jobs ADD COLUMN claimed_at TIMESTAMP;
ALTER TABLE analysis_jobs ADD COLUMN claimed_by VARCHAR(100);
ALTER TABLE analysis_jobs ADD COLUMN heartbeat_at TIMESTAMP;
ALTER TABLE analysis_jobs ADD COLUMN last_error_code VARCHAR(100);
ALTER TABLE analysis_jobs ADD COLUMN retryable BOOLEAN;
ALTER TABLE analysis_jobs ADD COLUMN claim_token VARCHAR(64);

CREATE INDEX idx_job_claim_candidates
    ON analysis_jobs(job_type, status, next_attempt_at);

ALTER TABLE structured_plan_sections ADD COLUMN display_order INTEGER;

ALTER TABLE structured_plans
    ADD CONSTRAINT uk_plan_source_document_version
    UNIQUE (source_document_version_id);
