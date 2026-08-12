-- 경쟁 씨앗 — 사업안 화면이 받는 「경쟁/현재 대안(선택)」 칸.
--
-- 왜 필요한가: 슬롯 하네스가 F_COMP 슬롯의 subject 를 이 씨앗에서 가져온다
-- (`harness/slot_harness.py:_seed_lines`). 비워 두면 모델이 실명을 지어내거나
-- 자리표시자를 만든다 — 2026-08-08 실측이고, 그래서 공시 대조에서 전부 죽었다.
--
-- ⚠ 씨앗이 0개여도 **막지 않는다.** 입구계약서가 「수리 대상」으로 남겨 둔 자리라
--    (백로그 39) 하드 게이트로 굳히지 않는다. 대신 하네스가 corp_name 요구를 스스로 끈다.
--
-- ⚠ `운영사` 는 **법인명**이지 서비스명이 아니다. DART 조회가 법인명으로만 되고,
--    코드가 corpCode 사전과 대조해 「공시법인」인 씨앗에만 corp_name 을 허용한다.
CREATE TABLE research_competitor_seeds (
    id VARCHAR(64) PRIMARY KEY,
    project_id BIGINT NOT NULL,
    -- 화면에 보이는 순서. 사용자가 적은 차례가 곧 중요도라 보존한다.
    display_order INT NOT NULL,
    -- 경쟁·대체재의 이름. 하네스 프롬프트에 그대로 실린다.
    name VARCHAR(200) NOT NULL,
    -- 「왜 이것이 경쟁인가」. 프롬프트에 이름과 함께 나가 슬롯의 뜻을 정한다.
    reason VARCHAR(500) NOT NULL,
    -- 법인명(선택). 비면 web 계량으로만 관측한다.
    operator_name VARCHAR(200),
    created_by_user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    -- ⚠ `BaseEntity` 가 @Version 을 들고 있다. 이 칸을 빠뜨리면 `ddl-auto=validate` 가
    --    「missing column [version]」으로 **기동 자체를 거부한다** — 이 표에서 실제로 그랬다.
    --    BaseEntity 를 상속하는 모든 표가 이 줄을 갖는다(V20 등과 같은 모양).
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_research_competitor_seed_project FOREIGN KEY (project_id)
        REFERENCES projects (id)
);

-- 한 프로젝트 안에서 같은 이름을 두 번 적지 않는다. 같은 경쟁을 두 줄로 두면
-- 하네스 프롬프트에 같은 subject 가 두 번 실리고 슬롯이 갈린다.
CREATE UNIQUE INDEX uk_research_competitor_seed_name
    ON research_competitor_seeds (project_id, name)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_research_competitor_seed_project
    ON research_competitor_seeds (project_id, display_order)
    WHERE deleted_at IS NULL;
