ALTER TABLE users ADD COLUMN locked_at TIMESTAMP;
ALTER TABLE users ADD COLUMN locked_reason VARCHAR(500);
ALTER TABLE users ADD COLUMN disabled_at TIMESTAMP;
ALTER TABLE users ADD COLUMN role_updated_at TIMESTAMP;
ALTER TABLE users ADD COLUMN role_updated_by BIGINT;

CREATE TABLE service_settings (
    setting_key VARCHAR(100) PRIMARY KEY,
    setting_value VARCHAR(500) NOT NULL,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_service_settings_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
);

CREATE INDEX idx_users_role_status ON users(role, status);
