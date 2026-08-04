-- V21 preserves dormant legacy rows by adding new columns as nullable.
-- Backfill only fields that have safe operational defaults, then align the schema
-- with the required fields used for every new FinancialAnalysis entity.
UPDATE financial_analyses
SET version_number = 1
WHERE version_number IS NULL;

UPDATE financial_analyses
SET title = '기존 재무 분석'
WHERE title IS NULL OR TRIM(title) = '';

UPDATE financial_analyses
SET analysis_period_months = 12
WHERE analysis_period_months IS NULL;

UPDATE financial_analyses
SET assumptions_json = '{}'
WHERE assumptions_json IS NULL;

ALTER TABLE financial_analyses ALTER COLUMN version_number SET NOT NULL;
ALTER TABLE financial_analyses ALTER COLUMN title SET NOT NULL;
ALTER TABLE financial_analyses ALTER COLUMN analysis_period_months SET NOT NULL;
ALTER TABLE financial_analyses ALTER COLUMN assumptions_json SET NOT NULL;
