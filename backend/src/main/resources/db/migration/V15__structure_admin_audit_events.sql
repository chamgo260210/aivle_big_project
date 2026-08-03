ALTER TABLE audit_events ADD COLUMN result VARCHAR(20) NOT NULL DEFAULT 'SUCCESS';
ALTER TABLE audit_events ADD COLUMN actor_role VARCHAR(20);
ALTER TABLE audit_events ADD COLUMN error_code VARCHAR(100);
ALTER TABLE audit_events ADD COLUMN ip_address VARCHAR(64);
ALTER TABLE audit_events ADD COLUMN user_agent VARCHAR(500);
ALTER TABLE audit_events ADD COLUMN target_label VARCHAR(255);
ALTER TABLE audit_events ADD COLUMN reason VARCHAR(500);
ALTER TABLE audit_events ADD COLUMN before_json TEXT;
ALTER TABLE audit_events ADD COLUMN after_json TEXT;

CREATE INDEX idx_audit_result_occurred ON audit_events(result, occurred_at);
CREATE INDEX idx_audit_request_id ON audit_events(request_id);
