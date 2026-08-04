ALTER TABLE marketing_content_versions
    ADD COLUMN analysis_job_id BIGINT;

ALTER TABLE marketing_content_versions
    ADD CONSTRAINT fk_marketing_content_version_job
        FOREIGN KEY (analysis_job_id) REFERENCES analysis_jobs(id);

ALTER TABLE marketing_content_versions
    ADD CONSTRAINT uk_marketing_content_version_job
        UNIQUE (analysis_job_id);
