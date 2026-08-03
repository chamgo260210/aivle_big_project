ALTER TABLE marketing_contents
    ADD COLUMN panel_interview_id BIGINT;

ALTER TABLE marketing_contents
    ADD COLUMN market_response_id BIGINT;

ALTER TABLE marketing_contents
    ADD COLUMN source_snapshot_version INTEGER NOT NULL DEFAULT 1;

ALTER TABLE marketing_contents
    ADD CONSTRAINT fk_marketing_content_panel
        FOREIGN KEY (panel_interview_id) REFERENCES persona_panel_interviews(id);

ALTER TABLE marketing_contents
    ADD CONSTRAINT fk_marketing_content_market
        FOREIGN KEY (market_response_id) REFERENCES market_response_predictions(id);

ALTER TABLE marketing_content_versions
    ADD COLUMN source_snapshot_version INTEGER NOT NULL DEFAULT 1;

ALTER TABLE marketing_content_versions
    ADD COLUMN source_changed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE marketing_content_versions
    ADD COLUMN copy_changed BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX idx_marketing_content_validation_sources
    ON marketing_contents(project_id, panel_interview_id, market_response_id);
