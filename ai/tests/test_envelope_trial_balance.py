"""봉투 시산표 — **한 값은 한 표기로만 존재한다.**

분개(각 층의 계산)는 맞는데 시산표를 안 뽑던 자리다. 판 ㊳ 실측:
같은 성장률이 `15.1464 %/년`(봉투)과 `0.1514642 %`(계산 카드)로 **한 산출물에 공존**했다.
둘 다 같은 근거를 인용했으므로 읽는 사람은 어느 쪽이 참인지 알 수 없다.

그때는 사람이 정규식으로 한 번 훑어 잡았다. **한 번 훑은 것은 장치가 아니다** —
다음 판에서 재발하면 아무도 모른다. 그래서 여기서 굳힌다.
"""
from __future__ import annotations

import asyncio
import os
import re

import pytest

from app.research import pipeline

#: ⚠ **컨셉이 맞는 원장이라야 한다.** `p38-regrade-01` 은 `--from a4` 를 `--concept` 없이
#: 돌려 작업용 `data/concept.json`(카페)이 박혔고, 그러면 계열이 C→A 로 갈려 TAM_추정이
#: 통째로 `None` 이 된다 — 아래 검사들이 조용히 공허해진다. `p38-regrade-02` 는
#: `--concept data/concept_hmr-product.json` 을 명시해 만든 것이다.
SEED_RUN = "p38-regrade-02"

needs_ledger = pytest.mark.skipif(
    not os.path.isdir(os.path.join(pipeline.RESEARCH_HOME, "runs-generated", SEED_RUN)),
    reason=f"원장 {SEED_RUN} 이 없다 (`.gitignore` 대상이라 이 PC 에만 있다)",
)


def _numbers(node, out: list, path: str = "") -> list:
    """봉투 안의 **모든 숫자**를 경로와 함께 편다."""
    if isinstance(node, dict):
        for key, value in node.items():
            _numbers(value, out, f"{path}.{key}")
    elif isinstance(node, list):
        for index, value in enumerate(node):
            _numbers(value, out, f"{path}[{index}]")
    elif isinstance(node, (int, float)) and not isinstance(node, bool):
        out.append((path, float(node)))
    return out


def _envelope():
    return asyncio.run(pipeline.run_market_research(
        {"mode": "RESCORE", "sourceRun": SEED_RUN, "conceptId": "smoke"},
        "trial-balance", 600))


@needs_ledger
def test_the_seed_ledger_still_carries_a_market_size_figure():
    """★ **공허해지지 않게 막는 검사.**

    아래 검사들은 대부분 `figure is None` 이면 조용히 통과한다. 그래서 원장이 어떤
    이유로든 시장 크기 칸을 통째로 잃으면 **이 파일 전체가 아무것도 안 보면서 초록**이 된다.
    실제로 그런 일이 있었다 — `--from a4` 를 `--concept` 없이 돌린 원장은 계열이 갈려
    `TAM_추정` 이 통째로 `None` 이었고, 그 위에서 잰 판정이 사실과 달랐다(2026-08-14).
    """
    figure = _envelope()["market"]["tam"]
    assert figure is not None, (
        f"{SEED_RUN} 의 tam 칸이 통째로 비었다 — 이 원장 위의 검사는 전부 공허하다. "
        "원장의 컨셉이 관측과 맞는지부터 확인할 것")
    assert figure["factors"], "요인 표가 비었다 — 분해표를 잃었다"


@needs_ledger
def test_no_value_appears_at_two_magnitudes():
    """같은 값이 **100배·1000배로 갈려** 두 자리에 있으면 안 된다.

    비율(0.1514642)과 퍼센트(15.1464)가 한 봉투에 같이 있던 사고를 막는다.
    ⚠ 우연한 배수(2와 200 같은 서로 다른 사실)를 잡지 않도록, **유효숫자가 같은 것**만 본다.
    """
    numbers = [(p, v) for p, v in _numbers(_envelope(), []) if v not in (0.0, 1.0)]
    seen: dict[str, list] = {}
    for path, value in numbers:
        # 배수를 벗긴 가수(mantissa). 0.1514642 와 15.1464 는 같은 열쇠가 된다.
        mantissa = abs(value)
        while mantissa >= 10:
            mantissa /= 10
        while mantissa < 1:
            mantissa *= 10
        seen.setdefault(f"{mantissa:.4f}", []).append((path, value))

    collisions = {
        key: rows for key, rows in seen.items()
        if len({round(v, 6) for _, v in rows}) > 1
    }
    assert not collisions, (
        "같은 수가 배수만 다르게 두 자리에 있다 — 어느 쪽이 참인지 읽는 사람이 알 수 없다:\n"
        + "\n".join(f"  {key}: {rows}" for key, rows in collisions.items()))


@needs_ledger
def test_a_null_value_never_carries_a_confident_grade():
    """값이 없으면 **자신 있는 등급을 달지 않는다.**

    「추정 불가」와 「확정」이 같은 칸에 있으면 화면이 둘 중 하나를 골라 그린다.
    """
    market = _envelope()["market"]
    for name in ("tam", "sam", "som", "growth"):
        figure = market.get(name)
        if figure is None:
            continue
        if figure["value"] is None:
            assert figure["grade"] == "근거 없음", (
                f"{name}: 값이 없는데 등급이 {figure['grade']!r} 이다")


@needs_ledger
def test_a_null_value_says_why_and_what_would_revive_it():
    """값이 없으면 **왜 없는지**를 같이 낸다. 빈 칸만 보내면 「조사를 안 했다」로 읽힌다."""
    market = _envelope()["market"]
    for name in ("tam", "sam"):
        figure = market.get(name)
        if figure is None or figure["value"] is not None:
            continue
        joined = " ".join(figure["assumptions"])
        assert joined.strip(), f"{name}: 값도 없고 사유도 없다"
        assert ("관측" in joined), f"{name}: 사유가 무엇이 모자란지 말하지 않는다 — {joined!r}"


@needs_ledger
def test_report_prose_carries_no_foreign_company_names():
    """**화석 금지.** 옛 판의 실측 문장이 규칙 파일에 굳어 모든 보고서에 복사되던 자리다.

    카페 POS 판의 회사 실명이 냉동식품·미용실·반려동물 원장에 각 9회씩 실렸다.
    """
    fossils = ("코케비즈", "토스플레이스", "카페24", "스포카",
               "한국결제네트웍스", "비바리퍼블리카", "플래텀")
    import json
    text = json.dumps(_envelope(), ensure_ascii=False)
    hits = {word: text.count(word) for word in fossils if word in text}
    assert not hits, f"봉투에 다른 판의 회사 실명이 실렸다: {hits}"


@needs_ledger
def test_card_and_envelope_agree_on_the_same_number():
    """★ 실제 사고가 난 자리 — **카드와 봉투 사이.**

    계산 카드는 비율 `0.1514642` 에 단위 「%」를 달았고 봉투는 같은 것을 `15.1464 %/년`
    으로 냈다. 둘 다 같은 근거를 인용했다. 카드는 봉투에 실리지 않으므로 봉투만 훑는
    검사로는 **이 사고를 못 잡는다** — 두 산출을 나란히 놓아야 잡힌다.
    """
    import sys
    home = pipeline.RESEARCH_HOME
    if home not in sys.path:
        sys.path.insert(0, home)
    from service import cards as CARDS                      # noqa: N812

    # 원장이 자기 컨셉을 안다 — 손으로 고르면 오늘 같은 사고(카페 잣대로 HMR 채점)가 난다.
    built = CARDS.build(SEED_RUN, os.path.join(home, pipeline._concept_path_of(SEED_RUN)))

    def walk(node):
        if isinstance(node, dict):
            if str(node.get("카드_id", "")).startswith("C-CALC"):
                yield node
            for value in node.values():
                yield from walk(value)
        elif isinstance(node, list):
            for value in node:
                yield from walk(value)

    by_name = {"TAM": "tam", "SAM": "sam", "성장률": "growth"}
    market = _envelope()["market"]
    checked = 0
    for card in walk(built):
        field = by_name.get(str(card["카드_id"]).replace("C-CALC-", ""))
        figure = market.get(field) if field else None
        if figure is None or card.get("값") is None or figure["value"] is None:
            continue
        checked += 1
        assert abs(float(card["값"]) - float(figure["value"])) < 1e-6, (
            f"{card['카드_id']}: 카드는 {card['값']}{card.get('단위')} 인데 "
            f"봉투 {field} 는 {figure['value']} {figure['unit']} 다 — 같은 값의 두 표기")
    assert checked, "대조한 계산 카드가 0개면 이 검사는 아무것도 못 본다"


@needs_ledger
def test_scorecard_detail_is_prose_not_python_repr():
    """성적표 한 줄은 **사람 문장**이다. `['확정']` 같은 코드 표기가 새면 안 된다."""
    bad = re.compile(r"[\[\]{}]|None|True|False")
    for row in _envelope()["scorecard"]:
        assert not bad.search(row["detail"]), (
            f"{row['subject']}: 코드 표기가 화면 문구에 샜다 — {row['detail']!r}")
