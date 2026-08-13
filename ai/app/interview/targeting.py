"""타겟 / 비타겟 뱅크 사전 필터.

컨셉보드의 `targetUsers` 는 자유 서술이고 표집틀(`twin_frame.csv`)에는 `pid_hash·gender·band`
세 칸뿐이다. 그래서 두 단계를 거친다.

1. **조건식 변환 (LLM 1회).** 자유 서술 → 프로필 술어. LLM 은 **조건만** 만들고 판정은
   코드가 한다. 조건식은 결과 봉투에 그대로 실어 화면에 보인다 — 기계가 옮긴 것이라
   틀릴 수 있고, 틀렸는지는 사용자만 안다.
2. **뱅크 2분할 + 각각 층화 추출.** 타겟 8 : 비타겟 2. 비타겟을 남기는 이유는 대비를 보기
   위해서다 — 타겟 밖에서 의외의 반응이 나오면 그것이 타겟을 다시 그릴 근거가 된다.

**⚠ 표집 재현성이 여기서 약해진다.** `bank.stratified_sample` 이 난수를 안 쓰는 이유는
「조사 간 비교가 사람 교체가 아니라 자극 차이만 반영하게」 하려는 것이었다. 조건식을 LLM 이
만들므로 같은 사업안을 두 번 조사해도 표집틀이 갈릴 수 있다. 그래서 조건식 호출은 좁은
구조화 스키마로 묶고, 나온 조건식을 봉투에 박아 둔다 — 두 판이 갈리면 왜 갈렸는지는 보인다.

**프로필을 못 읽은 칸은 조건을 통과시키지 않는다.** 확인할 수 없는 것을 타겟으로 세면
타겟 표본이 조용히 오염된다. 뱅크 8,604장의 6필드 실측 커버리지는 100% 라 실제로 드물다.
"""

import math
import re

from pydantic import Field, ValidationError

from app.interview.models import StrictModel
from app.providers import ProviderFailure, execute_structured_prompt
from app.twin.bank import stratified_sample
from app.twin.profile import parse_profile

__all__ = ["TargetCriteria", "draw_split", "matches", "criteria_text"]

CRITERIA_SCHEMA = "market_interview_target_criteria_v1"
#: 타겟 : 비타겟 = 8 : 2.
TARGET_SHARE = 0.8

_HOUSEHOLD_SIZE = re.compile(r"(\d{1,2})인 가구")

CRITERIA_PROMPT = """너는 조사 표본을 설계하는 사람이다. 상품의 「누구를 위한 것인가」 설명을 읽고,
응답자 패널에서 그 대상을 **거를 수 있는 조건**으로 옮긴다.

패널에 대해 알 수 있는 것은 여섯 가지뿐이다: 나이 · 성별 · 가구원 수 · 거주 지역 ·
개인 월소득 · 하는 일. **이 여섯 가지로 표현할 수 없는 조건은 만들지 마라.**
"요리를 자주 하는 사람", "환경에 관심 있는 사람" 같은 행동·태도 조건은 **낼 수 없다** —
그런 칸이 패널에 없다. 그런 조건뿐이라면 전부 비워서 「누구나」로 둔다.

**넓게 잡아라.** 조건을 좁히면 표본이 마르고, 마른 표본은 조사가 아니라 일화가 된다.
설명에 없는 조건을 상상해서 덧붙이지 마라.

- ageMin / ageMax: 만 나이. **모르면 0** 을 넣는다(0 은 「제한 없음」이다).
- genders: "남성" 또는 "여성" 만 쓴다. 상관없으면 빈 배열.
- householdSizeMin / householdSizeMax: 가구원 수. 모르면 0.
- regions: "서울", "경기" 처럼 광역 이름만. 상관없으면 빈 배열.
- incomeKeywords: 소득 구간 표기에 들어갈 말(예: "300", "400"). 확실하지 않으면 빈 배열.
- jobKeywords: 직업 설명에 들어갈 말(예: "학생", "주부", "자영"). 확실하지 않으면 빈 배열."""


class TargetCriteria(StrictModel):
    """프로필 술어. 축끼리는 AND, 한 축 안의 목록은 OR. **0 과 빈 배열이 「제한 없음」이다.**

    `int | None` 을 쓰지 않는 것은 OpenAI strict json_schema 에서 nullable 정수가 공급자마다
    다르게 처리되기 때문이다. 0 을 센티널로 두는 쪽이 계약이 단순하다.
    """

    ageMin: int = Field(ge=0, le=120)
    ageMax: int = Field(ge=0, le=120)
    genders: list[str] = Field(max_length=2)
    householdSizeMin: int = Field(ge=0, le=20)
    householdSizeMax: int = Field(ge=0, le=20)
    regions: list[str] = Field(max_length=20)
    incomeKeywords: list[str] = Field(max_length=10)
    jobKeywords: list[str] = Field(max_length=15)


def criteria_text(criteria: TargetCriteria) -> str:
    """화면에 그대로 보일 한 줄. 조건이 하나도 없으면 그렇다고 말한다."""
    parts = []
    if criteria.ageMin and criteria.ageMax:
        parts.append(f"만 {criteria.ageMin}~{criteria.ageMax}세")
    elif criteria.ageMin:
        parts.append(f"만 {criteria.ageMin}세 이상")
    elif criteria.ageMax:
        parts.append(f"만 {criteria.ageMax}세 이하")
    if criteria.genders:
        parts.append(" 또는 ".join(criteria.genders))
    if criteria.householdSizeMin and criteria.householdSizeMax:
        parts.append(f"{criteria.householdSizeMin}~{criteria.householdSizeMax}인 가구")
    elif criteria.householdSizeMin:
        parts.append(f"{criteria.householdSizeMin}인 이상 가구")
    elif criteria.householdSizeMax:
        parts.append(f"{criteria.householdSizeMax}인 이하 가구")
    if criteria.regions:
        parts.append(" · ".join(criteria.regions))
    if criteria.incomeKeywords:
        parts.append("소득 " + " · ".join(criteria.incomeKeywords))
    if criteria.jobKeywords:
        parts.append(" · ".join(criteria.jobKeywords))
    return " / ".join(parts) if parts else "조건 없음 — 패널 전체가 타겟이다"


def _household_size(profile: dict):
    match = _HOUSEHOLD_SIZE.search(profile.get("household") or "")
    return int(match.group(1)) if match else None


def matches(profile: dict, criteria: TargetCriteria) -> bool:
    """축끼리 AND. **못 읽은 칸은 조건을 통과시키지 않는다**(그 축에 조건이 있을 때만)."""
    age = profile.get("age")
    if criteria.ageMin or criteria.ageMax:
        if not isinstance(age, int):
            return False
        if criteria.ageMin and age < criteria.ageMin:
            return False
        if criteria.ageMax and age > criteria.ageMax:
            return False
    if criteria.genders and (profile.get("gender") or "") not in criteria.genders:
        return False
    if criteria.householdSizeMin or criteria.householdSizeMax:
        size = _household_size(profile)
        if size is None:
            return False
        if criteria.householdSizeMin and size < criteria.householdSizeMin:
            return False
        if criteria.householdSizeMax and size > criteria.householdSizeMax:
            return False
    if criteria.regions:
        region = profile.get("region") or ""
        if not any(name in region for name in criteria.regions if name):
            return False
    if criteria.incomeKeywords:
        income = profile.get("income") or ""
        if not any(word in income for word in criteria.incomeKeywords if word):
            return False
    if criteria.jobKeywords:
        job = profile.get("job") or ""
        if not any(word in job for word in criteria.jobKeywords if word):
            return False
    return True


async def resolve_criteria(target_users: str, problem_scenario: str,
                           timeout_seconds: float) -> TargetCriteria:
    """자유 서술 → 술어. 설명이 비어 있으면 **호출하지 않는다**(전원이 타겟이다)."""
    body = "\n".join(filter(None, [target_users.strip(), problem_scenario.strip()]))
    if not body:
        return TargetCriteria(ageMin=0, ageMax=0, genders=[], householdSizeMin=0,
                              householdSizeMax=0, regions=[], incomeKeywords=[],
                              jobKeywords=[])
    raw = await execute_structured_prompt(
        CRITERIA_PROMPT, body, response_schema=TargetCriteria.model_json_schema(),
        schema_name=CRITERIA_SCHEMA, task_type="MARKET_INTERVIEW",
        timeout_seconds_override=timeout_seconds)
    try:
        return TargetCriteria.model_validate(raw)
    except ValidationError as failure:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False) from failure


def _merge(first: dict, second: dict, requested: int) -> dict:
    """두 분할의 표집 보고를 `sampling` 4칸으로 합친다 — 계약이 정확 집합이라 늘릴 수 없다."""
    strata: dict[str, int] = dict(first["strata"])
    for cell, count in second["strata"].items():
        strata[cell] = strata.get(cell, 0) + count
    short: dict[str, dict] = {}
    for report in (first, second):
        for cell, detail in report["shortCells"].items():
            merged = short.setdefault(cell, {"quota": 0, "available": 0})
            merged["quota"] += detail["quota"]
            merged["available"] += detail["available"]
    return {"requested": requested, "drawn": first["drawn"] + second["drawn"],
            "strata": dict(sorted(strata.items())), "shortCells": short}


def draw_split(cards: dict[str, str], frame: list[dict], size: int,
               criteria: TargetCriteria) -> tuple[list[dict], set, dict, dict]:
    """`(뽑힌 행, 타겟 pid 집합, sampling, targeting)`.

    타겟 프레임이 얕으면 **죽이지 않고** 부족분을 비타겟에서 채운 뒤 `shortfall` 에 남긴다.
    조건이 좁다는 것 자체가 읽어야 할 정보이지 실패가 아니다.
    """
    target_pids = {pid for pid in cards if matches(parse_profile(cards[pid]), criteria)}
    target_frame = [row for row in frame if row["pid_hash"] in target_pids]
    other_frame = [row for row in frame if row["pid_hash"] not in target_pids]

    wanted = math.ceil(size * TARGET_SHARE)
    target_size = min(wanted, len(target_frame))
    other_size = min(size - target_size, len(other_frame))

    target_rows, target_report = stratified_sample(target_frame, target_size)
    other_rows, other_report = stratified_sample(other_frame, other_size)

    drawn = sorted(target_rows + other_rows, key=lambda row: row["pid_hash"])
    targeting = {
        "criteria": criteria.model_dump(),
        "criteriaText": criteria_text(criteria),
        "targetRequested": wanted,
        "nonTargetRequested": size - wanted,
        "targetDrawn": len(target_rows),
        "nonTargetDrawn": len(other_rows),
        "shortfall": size - len(drawn),
        "targetShortCells": target_report["shortCells"],
        "nonTargetShortCells": other_report["shortCells"],
    }
    return drawn, {row["pid_hash"] for row in target_rows}, \
        _merge(target_report, other_report, size), targeting
