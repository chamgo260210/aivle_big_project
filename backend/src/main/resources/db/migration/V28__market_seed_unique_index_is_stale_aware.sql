-- 시드 유일 색인을 「현재 시드는 하나」라는 **코드가 쓰는 정의**에 맞춘다.
--
-- 왜 필요했나. 2026-08-15 실측: 다듬기를 마치고 「이 컨셉으로 확정하기」를 다시 누르면
--   ① ConceptPortfolioSelectionMaterializationService 가 옛 시드를 **낡음(stale_at)** 으로 찍고
--   ② 새 시드를 넣는다
-- 그런데 V15 의 색인 조건은 `deleted_at IS NULL` 뿐이라, 낡음 처리된 행이 자리를 **계속
-- 차지했다**. 그 결과 재확정이 uk_market_seed_portfolio_selection 중복 키로 죽고,
-- **「시장조사 시작하기」가 아예 서지 않았다**(portfolio_selection_id=4 실측).
--
-- 조회는 전부 `...StaleAtIsNullAndDeletedAtIsNull` 이다
-- (ConceptPortfolioSelectionService:272·328·340, MaterializationService:167).
-- 즉 코드에서 「현재」는 **낡지 않았고 지워지지 않은 것**인데, 색인만 혼자 그 정의에서
-- 벗어나 「지워지지 않은 것」이라고 읽고 있었다.
--
-- ⚠ 새 조건은 옛 조건보다 **좁다.** 옛 색인이 `deleted_at IS NULL` 중복을 이미 막고 있었으므로
--    지금 데이터가 새 조건을 위반할 수는 없다 — 기존 행을 손대지 않는다.
-- ⚠ 낡음 처리된 시드를 **지우지 않는다.** 낡음과 삭제는 다른 사실이고, 옛 시드는 이력으로
--    남아야 한다(어떤 컨셉으로 조사가 나갔는지의 근거다).

DROP INDEX IF EXISTS uk_market_seed_portfolio_selection;

CREATE UNIQUE INDEX uk_market_seed_portfolio_selection
    ON market_analysis_seed_snapshots(portfolio_selection_id)
    WHERE source_type = 'CONCEPT_PORTFOLIO_V2' AND stale_at IS NULL AND deleted_at IS NULL;
