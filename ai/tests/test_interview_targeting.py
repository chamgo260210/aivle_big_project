"""타겟 사전 필터 — 술어 평가와 8:2 분할. LLM 호출 0회 (조건식은 직접 만든다).

가장 중요한 것: **조건이 좁아 타겟이 마를 때 죽지 않고 사실로 남기는가.**
조건이 좁다는 것 자체가 읽어야 할 정보이지 실패가 아니다.
"""

from app.interview.targeting import TargetCriteria, criteria_text, draw_split, matches

CARD = ("저는 만 41세 여성입니다. 서울 시 지역에 살고 있습니다. "
        "2세대가구(부부+자녀) 형태의 3인 가구이고, 개인 월소득은 300~400만 원 미만 "
        "수준입니다. 일은 일반 지원 사무직 쪽 일을 임금 근로자로 하고 있습니다.")

PROFILE = {"age": 41, "gender": "여성", "household": "3인 가구", "region": "서울",
           "income": "월소득 300~400만 원", "job": "일반 지원 사무직"}


def criteria(**overrides) -> TargetCriteria:
    base = {"ageMin": 0, "ageMax": 0, "genders": [], "householdSizeMin": 0,
            "householdSizeMax": 0, "regions": [], "incomeKeywords": [], "jobKeywords": []}
    base.update(overrides)
    return TargetCriteria(**base)


# ── 술어 평가 ────────────────────────────────────────────────────────
def test_no_condition_means_everyone_is_a_target():
    assert matches(PROFILE, criteria()) is True
    assert matches({}, criteria()) is True


def test_zero_means_unbounded_on_that_side():
    assert matches(PROFILE, criteria(ageMin=30)) is True
    assert matches(PROFILE, criteria(ageMin=50)) is False
    assert matches(PROFILE, criteria(ageMax=50)) is True
    assert matches(PROFILE, criteria(ageMax=30)) is False


def test_conditions_are_and_across_axes():
    assert matches(PROFILE, criteria(genders=["여성"], regions=["서울"])) is True
    assert matches(PROFILE, criteria(genders=["여성"], regions=["부산"])) is False


def test_a_list_inside_one_axis_is_or():
    assert matches(PROFILE, criteria(regions=["부산", "서울"])) is True


def test_household_size_is_read_out_of_the_profile_sentence():
    assert matches(PROFILE, criteria(householdSizeMin=3)) is True
    assert matches(PROFILE, criteria(householdSizeMin=4)) is False
    assert matches(PROFILE, criteria(householdSizeMax=2)) is False


def test_keywords_match_by_substring():
    assert matches(PROFILE, criteria(jobKeywords=["사무"])) is True
    assert matches(PROFILE, criteria(jobKeywords=["학생", "주부"])) is False
    assert matches(PROFILE, criteria(incomeKeywords=["300"])) is True


def test_an_unreadable_field_fails_the_condition_it_cannot_confirm():
    """확인할 수 없는 것을 타겟으로 세면 타겟 표본이 조용히 오염된다."""
    blank = {**PROFILE, "age": None, "household": None}
    assert matches(blank, criteria(ageMin=30)) is False
    assert matches(blank, criteria(householdSizeMin=2)) is False
    assert matches(blank, criteria(regions=["서울"])) is True   # 그 축엔 조건이 없다


def test_criteria_text_says_so_when_there_is_no_condition():
    assert "조건 없음" in criteria_text(criteria())
    assert criteria_text(criteria(ageMin=30, ageMax=49, genders=["여성"])) == \
        "만 30~49세 / 여성"


# ── 8:2 분할 ─────────────────────────────────────────────────────────
def _bank(target_count: int, other_count: int):
    cards, frame = {}, []
    for index in range(target_count):
        pid = f"t{index:03d}"
        cards[pid] = CARD
        frame.append({"pid_hash": pid, "gender": "여", "band": "40대"})
    for index in range(other_count):
        pid = f"x{index:03d}"
        cards[pid] = CARD.replace("만 41세 여성", "만 68세 남성")
        frame.append({"pid_hash": pid, "gender": "남", "band": "60+"})
    return cards, frame


def test_target_gets_eight_tenths_and_non_target_the_rest():
    cards, frame = _bank(50, 50)
    drawn, targets, sampling, targeting = draw_split(cards, frame, 20, criteria(ageMax=50))
    assert targeting["targetDrawn"] == 16 and targeting["nonTargetDrawn"] == 4
    assert len(drawn) == 20 and len(targets) == 16
    assert sampling["requested"] == 20 and sampling["drawn"] == 20
    assert targeting["shortfall"] == 0


def test_a_shallow_target_frame_is_recorded_not_raised():
    """조건이 좁다는 것 자체가 읽어야 할 정보다. 여기서 죽이면 그 정보가 사라진다."""
    cards, frame = _bank(5, 60)
    drawn, targets, _sampling, targeting = draw_split(cards, frame, 20, criteria(ageMax=50))
    assert targeting["targetDrawn"] == 5
    assert targeting["nonTargetDrawn"] == 15          # 부족분을 비타겟에서 채운다
    assert len(drawn) == 20 and len(targets) == 5
    assert targeting["shortfall"] == 0


def test_shortfall_is_reported_when_neither_side_can_fill_the_sample():
    cards, frame = _bank(3, 4)
    drawn, _targets, sampling, targeting = draw_split(cards, frame, 20, criteria(ageMax=50))
    assert len(drawn) == 7
    assert targeting["shortfall"] == 13
    assert sampling["requested"] == 20 and sampling["drawn"] == 7


def test_the_criteria_ride_along_so_a_wrong_filter_is_visible():
    """자유 서술을 기계가 옮긴 것이라 틀릴 수 있고, 틀렸는지는 사용자만 안다."""
    cards, frame = _bank(30, 30)
    _drawn, _targets, _sampling, targeting = draw_split(
        cards, frame, 20, criteria(ageMax=50, genders=["여성"]))
    assert targeting["criteria"]["ageMax"] == 50
    assert "여성" in targeting["criteriaText"]


def test_sampling_merges_both_halves_into_the_four_contract_fields():
    cards, frame = _bank(50, 50)
    _drawn, _targets, sampling, _targeting = draw_split(cards, frame, 20, criteria(ageMax=50))
    assert set(sampling) == {"requested", "drawn", "strata", "shortCells"}
    assert sum(sampling["strata"].values()) == 20
