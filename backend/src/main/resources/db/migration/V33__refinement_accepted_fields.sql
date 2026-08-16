-- 사람이 **고른 것**을 적는 칸.
--
-- 왜 필요했나. 이 단계의 정의는 「AI 가 제안하고, 사람이 체크해서 고른 것만 적용된다」인데
-- 그 문장이 코드에 한 줄도 없었다. AI 결과가 채택되는 순간
-- `ConceptPortfolioSelectionMaterializationService` 가 **사람 개입 없이 전량 적용**했고
-- (그 한 곳뿐이었다), DB 에 물증이 남아 있다 — 근거 0건 제안 2건이 최종 컨셉 문장의 가격을
-- 8,900 → 9,500원으로 바꿨다.
--
-- 라운드에는 제안 원본(`proposal_json`)과 기각(`drift_rejections_json`)만 있었고
-- 「채택/미채택」을 담을 칸이 없었다. 그 칸을 여기 만든다.
--
--   NULL           아직 안 골랐다 — 사람 차례다
--   '[]'           전부 넘겼다
--   '["price",…]'  고른 칸
--
-- ⚠ 제안의 이름표는 `round + fieldKey` 다. AI 계약에 id 가 없고 화면 번호는 렌더 순서일 뿐이라
--    그것으로는 「내가 체크한 그것」을 가리킬 수 없다. 한 라운드에 같은 칸 제안이 둘 오는지는
--    실측했다(`p47-refine-01`: 안 온다).
-- ⚠ 기존 행은 NULL 로 남는다. 그 라운드들은 «옛 규칙(전량 자동 적용)»으로 이미 적용된 것이라
--    화면이 「고를 차례」로 보이면 안 된다 — 그래서 화면 판정은 **라운드가 열려 있을 때만**
--    이 칸을 본다(`ConceptRefinementController.outcomeOf`). 닫힌 옛 라운드는 그대로 결말을 낸다.

ALTER TABLE concept_refinement_rounds
    ADD COLUMN accepted_fields_json TEXT;
