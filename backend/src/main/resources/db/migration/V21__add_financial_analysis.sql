-- Extend the dormant V1 financial_analyses table without rewriting historical rows.
ALTER TABLE financial_analyses ALTER COLUMN analysis_job_id DROP NOT NULL;
ALTER TABLE financial_analyses ADD COLUMN created_by_user_id BIGINT;
ALTER TABLE financial_analyses ADD COLUMN feasibility_assessment_id BIGINT;
ALTER TABLE financial_analyses ADD COLUMN structured_plan_id BIGINT;
ALTER TABLE financial_analyses ADD COLUMN source_document_version_id BIGINT;
ALTER TABLE financial_analyses ADD COLUMN version_number INTEGER;
ALTER TABLE financial_analyses ADD COLUMN title VARCHAR(200);
ALTER TABLE financial_analyses ADD COLUMN scenarios_json TEXT NOT NULL DEFAULT '[]';
ALTER TABLE financial_analyses ADD COLUMN summary_json TEXT;
ALTER TABLE financial_analyses ADD COLUMN source_snapshot_json TEXT NOT NULL DEFAULT '{}';
ALTER TABLE financial_analyses ADD COLUMN input_hash VARCHAR(64);
ALTER TABLE financial_analyses ADD COLUMN result_hash VARCHAR(64);
ALTER TABLE financial_analyses ADD COLUMN completed_at TIMESTAMP;

ALTER TABLE financial_analyses ADD CONSTRAINT fk_financial_created_by
    FOREIGN KEY (created_by_user_id) REFERENCES users(id);
ALTER TABLE financial_analyses ADD CONSTRAINT fk_financial_feasibility
    FOREIGN KEY (feasibility_assessment_id) REFERENCES feasibility_assessments(id);
ALTER TABLE financial_analyses ADD CONSTRAINT fk_financial_plan
    FOREIGN KEY (structured_plan_id) REFERENCES structured_plans(id);
ALTER TABLE financial_analyses ADD CONSTRAINT fk_financial_source_document
    FOREIGN KEY (source_document_version_id) REFERENCES document_versions(id);

CREATE INDEX idx_financial_project_deleted ON financial_analyses(project_id, deleted_at);
CREATE INDEX idx_financial_project_status_updated ON financial_analyses(project_id, status, updated_at);
CREATE INDEX idx_financial_feasibility ON financial_analyses(feasibility_assessment_id);
