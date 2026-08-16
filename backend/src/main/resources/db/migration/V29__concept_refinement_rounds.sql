-- 컨셉 다듬기 라운드 — **durable cursor**.
--
-- 루프를 도는 것은 Spring 이고, 워커는 폴링마다 새로 깨어난다. 라운드 번호와 직전 라운드의
-- 기각 사유를 메모리에 두면 재시작 한 번에 루프가 처음부터 다시 돈다 — LLM 값을 다시 낸다.
--
-- 왜 기각 사유까지 남기나. 다음 라운드 입력으로 **되먹이기** 때문이다. 왜 막혔는지를
-- 모델에 돌려주지 않으면 같은 제안을 3라운드 내내 반복한다.

CREATE TABLE concept_refinement_rounds (
    id                     BIGSERIAL PRIMARY KEY,
    project_id             BIGINT       NOT NULL REFERENCES projects(id),
    selection_id           BIGINT       NOT NULL REFERENCES concept_portfolio_selections(id),
    round                  INTEGER      NOT NULL,
    -- 이번 라운드가 받은 제안 원본. 계약으로 거르기 **전** 것이다 — 무엇을 걸렀는지
    -- 되짚으려면 거르기 전이 있어야 한다.
    proposal_json          TEXT         NOT NULL,
    -- 드리프트 계약이 기각한 것과 그 사유. 다음 라운드 입력이 된다.
    drift_rejections_json  TEXT,
    -- DELTA_LEGAL 결과. 아직 안 돌았으면 NULL 이다.
    legal_outcome          VARCHAR(20),
    legal_reasons_json     TEXT,
    created_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at             TIMESTAMP,

    CONSTRAINT ck_concept_refinement_round_no CHECK (round >= 1 AND round <= 3),
    CONSTRAINT ck_concept_refinement_legal_outcome
        CHECK (legal_outcome IS NULL OR legal_outcome IN ('PASSED', 'BLOCKED', 'FAILED')),
    -- 한 선택의 한 라운드는 하나뿐이다. 중복 실행이 라운드를 두 번 세면 상한(3)이 무의미해진다.
    CONSTRAINT uq_concept_refinement_round UNIQUE (selection_id, round)
);

CREATE INDEX idx_concept_refinement_rounds_selection
    ON concept_refinement_rounds(selection_id, round DESC);
