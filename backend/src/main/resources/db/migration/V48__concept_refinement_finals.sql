-- 다듬기의 **최종 산물** — 한 선택에 하나.
--
-- 라운드 표(V25)는 «어떻게 왔나»를 남기고, 이 표는 «무엇으로 끝났나»를 남긴다. 둘을 한 표에
-- 넣지 않는 이유: 라운드는 최대 3행이고 서술문·오버레이는 수렴 뒤 한 번만 생긴다. 라운드 행에
-- 얹으면 «어느 라운드 것이 최종인가»를 매번 다시 골라야 하고, 그 고르는 규칙이 화면과 서버
-- 두 곳에 생긴다.
--
-- overlay_json — 다듬기가 고쳤지만 **가설도 BM 계획도 아닌** 칸(`targetUsers`·`featureSet`).
--   갈 문이 없어 지금까지 조용히 버려지던 것들이다. 최종 확정 때 시드 스냅샷에 얹힌다.
--   ⚠ 컨셉 원본 candidate 는 덮지 않는다 — 캐노니컬 해시와 계보가 흔들린다.
--
-- narrative_json — 최종 컨셉 서술문 조각 배열. **검증을 통과한 것만** 들어온다.
--   통과 못 하면 아예 NULL 로 두고 화면이 칸 나열로 폴백한다. 반쯤 맞는 문장을 컨셉 원문
--   자리에 세우면 그것이 곧 지어낸 근거가 된다.

CREATE TABLE concept_refinement_finals (
    id             BIGSERIAL PRIMARY KEY,
    project_id     BIGINT    NOT NULL REFERENCES projects(id),
    selection_id   BIGINT    NOT NULL REFERENCES concept_portfolio_selections(id),
    overlay_json   TEXT,
    narrative_json TEXT,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at     TIMESTAMP,

    -- 한 선택에 하나. 둘이 되면 «어느 것이 최종인가»가 다시 모호해진다.
    CONSTRAINT uq_concept_refinement_final UNIQUE (selection_id)
);
