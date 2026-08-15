-- 다듬기를 **조사판마다 새로 돌게** 한다.
--
-- 왜 필요했나(2026-08-16 실측). 다듬기 라운드는 「선택당 정확히 한 번」만 걸렸다
-- (`ConceptRefinementService.startFirstRound`). 폴러 중복을 막으려던 규칙인데,
-- **조사를 다시 돌려도 다듬기가 안 걸린다**는 뜻이 된다. 실제로 프로젝트 3은
-- 오늘 사업 검증이 **다섯 번** 성공했는데 다듬기 라운드는 **8/13 것 하나뿐**이었다.
--
-- 그래서 한 화면 안에서 왼쪽(시장조사)은 오늘 것이고 오른쪽(다듬어진 컨셉)은 이틀 전
-- 것이었다. 화면 어디에도 그 말이 없었다. 「고친 컨셉으로 다시 조사 안 했다」는 경고는
-- 있었지만 그 **반대 방향**(조사가 새로 돌았는데 다듬기가 낡음)은 아무도 말하지 않았다.
--
--   research_version  이 라운드가 **어느 조사판을 근거로** 만들어졌나.
--                     NULL = V31 이전 행(모른다). 새 주기가 오면 물러난다.

ALTER TABLE concept_refinement_rounds
    ADD COLUMN research_version INTEGER;

-- ⚠⚠ **여기가 없으면 새 주기가 중복 키로 죽는다.**
--
-- 새 조사판이 오면 옛 라운드를 소프트 삭제하고 라운드 번호를 1부터 다시 센다. 그런데 이
-- 색인에는 `deleted_at IS NULL` 이 없어서, 지워진 라운드 1이 자리를 계속 차지한다 →
-- 새 라운드 1 을 넣는 순간 `uq_concept_refinement_round` 중복 키.
--
-- ⚠ **V28 이 시드 색인에서 고친 것과 똑같은 어긋남이다.** 이 표의 조회는 전부
--   `...DeletedAtIsNull...` 인데 색인만 그 정의에서 혼자 벗어나 있었다. 2026-08-15 에
--   잠복 상태로 발견해 적어 두었고(당시 소프트 삭제하는 코드가 0곳이라 안 닿았다),
--   이 판이 그 코드를 만들면서 **닿게 된다.**

-- ⚠ 색인이 아니라 **UNIQUE 제약**이다(`ALTER TABLE ... ADD CONSTRAINT` 로 생겼다).
--   `DROP INDEX` 는 「제약이 이 색인을 필요로 한다」(SQLSTATE 2BP01)로 거절한다.
--   부분 색인은 제약으로는 만들 수 없으므로, 제약을 떼고 **색인으로** 다시 세운다.
ALTER TABLE concept_refinement_rounds
    DROP CONSTRAINT IF EXISTS uq_concept_refinement_round;
DROP INDEX IF EXISTS uq_concept_refinement_round;

CREATE UNIQUE INDEX uq_concept_refinement_round
    ON concept_refinement_rounds(selection_id, round)
    WHERE deleted_at IS NULL;
