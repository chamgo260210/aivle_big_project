-- 사업 검증 — 시장조사(FULL)와 BM 을 한 실행으로 이은 갈래를 허용한다.
--
-- 컬럼은 그대로다. kind 가 VARCHAR(10) 이고 'VALIDATION' 이 **정확히 10자**라
-- 폭을 늘릴 필요가 없다. 바꾸는 것은 CHECK 둘뿐이다.
--
-- 옛 값 FULL·BM 은 지우지 않는다 — 이미 쌓인 이력이 그 값으로 남아 있고,
-- 2-4 이전에 돈 실행을 읽을 수 있어야 한다.
--
-- task_runs.task_type 에는 CHECK 가 없어 TaskType 추가에는 마이그레이션이 필요 없다
-- (V23__market_interview.sql 이 같은 이유로 task_type 을 안 건드렸다).

ALTER TABLE market_research_runs DROP CONSTRAINT ck_market_research_run_kind;
ALTER TABLE market_research_runs ADD CONSTRAINT ck_market_research_run_kind
    CHECK (kind IN ('FULL', 'BM', 'VALIDATION'));

ALTER TABLE market_research_versions DROP CONSTRAINT ck_market_research_version_kind;
ALTER TABLE market_research_versions ADD CONSTRAINT ck_market_research_version_kind
    CHECK (kind IN ('FULL', 'BM', 'VALIDATION'));
