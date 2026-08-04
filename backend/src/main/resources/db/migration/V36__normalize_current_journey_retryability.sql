UPDATE task_runs
SET max_attempts = 3,
    retryable = TRUE
WHERE task_type IN ('IDEA_INTERPRETATION', 'IDEA_LEGAL_PRECHECK')
  AND state IN ('FAILED', 'TIMED_OUT')
  AND attempt_count < 3
  AND last_error_code IN ('AI_SERVICE_UNAVAILABLE', 'TASK_TIMEOUT');

UPDATE task_runs
SET last_error_code = 'AI_CONFIGURATION_INVALID',
    retryable = FALSE
WHERE task_type IN ('IDEA_INTERPRETATION', 'IDEA_LEGAL_PRECHECK')
  AND state = 'FAILED'
  AND EXISTS (
      SELECT 1
      FROM task_attempts
      WHERE task_attempts.id = task_runs.current_attempt_id
        AND task_attempts.normalized_error_reason = 'AI_CONFIGURATION_INVALID'
  );

UPDATE legal_precheck_runs
SET error_code = 'AI_CONFIGURATION_INVALID'
WHERE state = 'FAILED'
  AND task_run_id IN (
      SELECT id FROM task_runs WHERE last_error_code = 'AI_CONFIGURATION_INVALID'
  );
