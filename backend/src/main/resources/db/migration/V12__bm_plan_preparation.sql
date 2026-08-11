-- BM 실행 계획 준비 — 사용자가 BM 캔버스 앞에서 채우는 칸.
--
-- 왜 이 테이블이 필요한가: 계획 4칸(핵심 활동·핵심 자원·핵심 파트너·고객 관계)은
-- **컨셉 계약이 주지 않는 값**이다. 입구계약서(`시장조사/문서/계약/2_입구계약서…`) §1 의
-- 선택 필드는 region · price_hypothesis_krw · constraint · _다듬기5 · _경쟁_씨앗 뿐이고,
-- 거기에 활동·자원·파트너·고객 관계가 없다. 그래서 화면이 따로 받아 여기 둔다.
--
-- ⚠ **버전 번호는 파일 목록으로 판별했다.** V1~V11 이 실제로 존재하므로 다음은 V12 다.
--    `V10__market_research.sql` 헤더의 「실제 다음 번호는 V2」는 stale 이다 — 그 문서를
--    믿고 번호를 정하면 Flyway 가 기동에서 죽는다.
--
-- 모양은 `financial_input_preparations`(V1:769) 를 그대로 따른다 — 같은 성질의 것
-- (사용자가 채워 두고 나중에 하류 실행이 읽는 준비물)이라 새 형태를 만들지 않는다.

CREATE TABLE bm_plan_preparations (
    id VARCHAR(64) PRIMARY KEY,
    project_id BIGINT NOT NULL,
    -- 계획 4칸. AI 쪽 `bm_adapter.PLAN_FIELDS` 와 같은 키를 쓴다.
    plan_json TEXT NOT NULL,
    -- 비용 구조 칸의 재료 {budget_krw, months, team}. **정수만** —
    -- taskInput 부동소수점 금지(canonical hash 가 런타임에 거부한다).
    constraint_json TEXT NOT NULL,
    revision INTEGER NOT NULL DEFAULT 1,
    updated_by_user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    -- 프로젝트당 하나. 여러 벌을 두면 「어느 것으로 돌렸나」가 흐려진다.
    CONSTRAINT uk_bm_plan_preparation_project UNIQUE (project_id),
    CONSTRAINT fk_bm_plan_preparation_project FOREIGN KEY (project_id)
        REFERENCES projects(id) ON DELETE NO ACTION,
    CONSTRAINT ck_bm_plan_preparation_revision CHECK (revision > 0)
);

CREATE INDEX idx_bm_plan_preparation_project
    ON bm_plan_preparations(project_id, updated_at DESC);
