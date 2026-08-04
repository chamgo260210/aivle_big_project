CREATE TABLE task_runs (
    id VARCHAR(64) PRIMARY KEY,
    project_id BIGINT NOT NULL,
    task_type VARCHAR(50) NOT NULL,
    subject_type VARCHAR(80) NOT NULL,
    subject_id VARCHAR(64) NOT NULL,
    state VARCHAR(20) NOT NULL,
    input_snapshot_json TEXT NOT NULL,
    canonical_input_hash VARCHAR(71) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    idempotency_scope VARCHAR(200) NOT NULL,
    last_retry_idempotency_key VARCHAR(128),
    correlation_id VARCHAR(128) NOT NULL,
    contract_version VARCHAR(20) NOT NULL,
    task_schema_version VARCHAR(20) NOT NULL,
    locale VARCHAR(20) NOT NULL,
    retryable BOOLEAN NOT NULL,
    cancel_requested BOOLEAN NOT NULL,
    max_attempts INTEGER NOT NULL,
    attempt_count INTEGER NOT NULL,
    current_attempt_id VARCHAR(64),
    final_result_id VARCHAR(64),
    last_error_code VARCHAR(80),
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL,
    CONSTRAINT fk_task_runs_project FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT uk_task_runs_idempotency UNIQUE (project_id, idempotency_scope, idempotency_key),
    CONSTRAINT ck_task_runs_attempts CHECK (max_attempts > 0 AND attempt_count >= 0 AND attempt_count <= max_attempts),
    CONSTRAINT ck_task_runs_input_hash CHECK (canonical_input_hash LIKE 'sha256:%')
);

CREATE TABLE task_attempts (
    id VARCHAR(64) PRIMARY KEY,
    task_run_id VARCHAR(64) NOT NULL,
    attempt_number INTEGER NOT NULL,
    state VARCHAR(20) NOT NULL,
    claim_token VARCHAR(64),
    claimed_by VARCHAR(128),
    claimed_at TIMESTAMP,
    lease_expires_at TIMESTAMP,
    heartbeat_at TIMESTAMP,
    deadline_at TIMESTAMP NOT NULL,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    normalized_error_code VARCHAR(80),
    normalized_error_reason VARCHAR(100),
    retryable BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL,
    CONSTRAINT fk_task_attempts_run FOREIGN KEY (task_run_id) REFERENCES task_runs(id),
    CONSTRAINT uk_task_attempt_number UNIQUE (task_run_id, attempt_number),
    CONSTRAINT uk_task_attempt_claim_token UNIQUE (claim_token),
    CONSTRAINT ck_task_attempt_number CHECK (attempt_number > 0)
);

CREATE TABLE task_results (
    id VARCHAR(64) PRIMARY KEY,
    task_run_id VARCHAR(64) NOT NULL,
    task_attempt_id VARCHAR(64) NOT NULL,
    validation_state VARCHAR(20) NOT NULL,
    contract_version VARCHAR(20) NOT NULL,
    task_schema_version VARCHAR(20) NOT NULL,
    result_schema_version VARCHAR(20) NOT NULL,
    result_json TEXT NOT NULL,
    result_hash VARCHAR(71) NOT NULL,
    rejection_code VARCHAR(100),
    received_at TIMESTAMP NOT NULL,
    validated_at TIMESTAMP,
    adopted_at TIMESTAMP,
    rejected_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL,
    CONSTRAINT fk_task_results_run FOREIGN KEY (task_run_id) REFERENCES task_runs(id),
    CONSTRAINT fk_task_results_attempt FOREIGN KEY (task_attempt_id) REFERENCES task_attempts(id),
    CONSTRAINT ck_task_results_hash CHECK (result_hash LIKE 'sha256:%')
);

ALTER TABLE task_runs ADD CONSTRAINT fk_task_runs_current_attempt
    FOREIGN KEY (current_attempt_id) REFERENCES task_attempts(id);
ALTER TABLE task_runs ADD CONSTRAINT fk_task_runs_final_result
    FOREIGN KEY (final_result_id) REFERENCES task_results(id);

CREATE INDEX idx_task_runs_claim ON task_runs(state, created_at);
CREATE INDEX idx_task_runs_project_history ON task_runs(project_id, created_at, id);
CREATE INDEX idx_task_runs_subject ON task_runs(project_id, subject_type, subject_id);
CREATE INDEX idx_task_runs_active_conflict ON task_runs(project_id, task_type, subject_type, subject_id, canonical_input_hash, state);
CREATE INDEX idx_task_attempts_lease ON task_attempts(state, lease_expires_at);
CREATE INDEX idx_task_results_run ON task_results(task_run_id, created_at);
CREATE INDEX idx_task_results_attempt ON task_results(task_attempt_id, validation_state);
