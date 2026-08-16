-- V29 **이전에 닫힌** 라운드를 사실과 맞춘다.
--
-- 왜 필요했나. V29 가 「사람이 고른 칸」을 만들었지만 기존 행은 NULL 로 남겼다. 그런데 그
-- 라운드들은 옛 규칙(전량 자동 적용)으로 **정말 전부 적용됐다** — 프로젝트 3의 가격이
-- 8,900 → 9,500원으로 바뀐 그 행이다. NULL 을 화면은 「아직 결정 전」으로 읽으므로:
--
--   · 제안마다 「반영함」 배지가 안 뜬다 — 반영돼 있는데도
--   · **「고쳤으니 다시 조사하세요」 경고가 안 뜬다** — 이쪽이 더 나쁘다.
--     조건이 `changes.some(c => c.accepted)` 인데(`RefinementSummary.jsx`) 전부 null 이라
--     거짓이 되고, 사용자는 **고친 컨셉을 옛 조사 수치로 읽는다.**
--
-- 그래서 「전량 반영됨」으로 적는다. 지어내는 것이 아니라 **실제로 그렇게 적용된 사실**이다.
--
-- ⚠ **열린 라운드(`legal_outcome IS NULL`)는 절대 건드리지 않는다.** 그것은 「아직 사람이
--    안 골랐다」는 뜻이고, 여기를 채우면 이 판이 세운 문을 마이그레이션이 뒤에서 연다.
-- ⚠ Flyway 는 immutable — V29 를 고치지 않고 새 파일로 얹는다.
-- ⚠ 값의 모양은 V29 와 같아야 한다: `fieldKey` **문자열의 JSON 배열**.
--    `ConceptRefinementService.acceptedOf` 가 그대로 읽는다.

UPDATE concept_refinement_rounds
   SET accepted_fields_json = COALESCE(
           (SELECT jsonb_agg(DISTINCT item ->> 'fieldKey')::text
              FROM jsonb_array_elements(proposal_json::jsonb) AS item
             WHERE item ->> 'fieldKey' IS NOT NULL),
           '[]')
 WHERE accepted_fields_json IS NULL
   AND legal_outcome IS NOT NULL
   AND deleted_at IS NULL
   AND proposal_json IS NOT NULL
   AND jsonb_typeof(proposal_json::jsonb) = 'array';
