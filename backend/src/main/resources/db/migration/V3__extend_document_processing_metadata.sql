ALTER TABLE structured_plans ADD COLUMN provider VARCHAR(100);
ALTER TABLE structured_plans ADD COLUMN model_name VARCHAR(100);
ALTER TABLE structured_plans ADD COLUMN prompt_version VARCHAR(100);
ALTER TABLE structured_plans ADD COLUMN parser_name VARCHAR(100);
ALTER TABLE structured_plans ADD COLUMN parser_version VARCHAR(100);
ALTER TABLE structured_plans ADD COLUMN raw_result_hash VARCHAR(64);

ALTER TABLE structured_plan_sections ADD COLUMN section_code VARCHAR(80);
ALTER TABLE structured_plan_sections ADD COLUMN item_status VARCHAR(20);
ALTER TABLE structured_plan_sections ADD COLUMN reason TEXT;
ALTER TABLE structured_plan_sections ADD COLUMN evidence_json TEXT;
ALTER TABLE structured_plan_sections ADD COLUMN source_block_references_json TEXT;
ALTER TABLE structured_plan_sections
    ADD CONSTRAINT uk_plan_section_code UNIQUE (structured_plan_id, section_code);

ALTER TABLE missing_fields ADD COLUMN section_code VARCHAR(80);
ALTER TABLE missing_fields ADD COLUMN reason TEXT;
ALTER TABLE missing_fields ADD COLUMN priority VARCHAR(20);

ALTER TABLE document_versions ADD COLUMN parser_name VARCHAR(100);
ALTER TABLE document_versions ADD COLUMN parser_version VARCHAR(100);
ALTER TABLE document_versions ADD COLUMN parse_metadata_json TEXT;

ALTER TABLE analysis_jobs ADD COLUMN idempotency_key VARCHAR(100);
ALTER TABLE analysis_jobs ADD COLUMN request_fingerprint VARCHAR(64);
ALTER TABLE analysis_jobs ADD COLUMN source_document_version_id BIGINT;
ALTER TABLE analysis_jobs
    ADD CONSTRAINT fk_job_source_document_version
    FOREIGN KEY (source_document_version_id) REFERENCES document_versions(id);
ALTER TABLE analysis_jobs
    ADD CONSTRAINT uk_job_idempotency
    UNIQUE (project_id, job_type, idempotency_key);

CREATE INDEX idx_job_source_document_version
    ON analysis_jobs(source_document_version_id);
