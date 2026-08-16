# -*- coding: utf-8 -*-
"""**한국어 배율 표기를 바르게 읽는가.** (판 ㊹ 2단계)

⚠ **왜 `ai/tests/` 에 두나** — `ai/pytest.ini` 의 `norecursedirs` 가
`app/research/research2` 를 통째로 제외한다. 엔진 안에 검사를 두면 **CI 가 한 번도 안 돈다.**
판 ㊸ 이 `test_promote_cards.py` 로 쓴 방법과 같다.

## 이 검사가 지키는 것

「6조 8천억」이 봉투에 **`6,000,800,000,000`** 으로 앉아 있었다 — 이 판의 왕관 사실이
자릿수 하나로 조용히 틀린 값이 됐다. 옛 구현은 조·억·만·천을 **한 줄에 세워** 각 자리에서
첫 숫자 하나만 집어서, **`8천억` 의 `8천` 에서 `8`만** 집었다.

**틀리는 방향이 언제나 «작게»가 아니다** — `3천만` 은 30,000 이 되어 1,000배 작았고,
같은 표에 든 다른 값과 나란히 놓이면 **크기 비교가 뒤집힌다.**
"""
from __future__ import annotations

import os
import sys

import pytest

_ENGINE = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                       "app", "research", "research2")
for _p in (_ENGINE, os.path.join(_ENGINE, "tools")):
    if _p not in sys.path:
        sys.path.insert(0, _p)

synthesize = pytest.importorskip("synthesize", reason="시장조사 엔진이 없는 환경")


#: (표기, 참값). **원문에 실제로 나온 표기만 쓴다** — 지어낸 표기로 통과시키지 않는다.
CASES = [
    ("6조 8천억", 6.8e12),          # ★ 판 ㊹ 의 왕관 사실 (간편식 판매액)
    ("8천억", 8e11),                # 배율말 둘이 겹치는 최소 꼴
    ("3천만", 3e7),                 # ⚠ 옛 구현이 **1,000배 작게** 읽던 것
    ("1억 2천만", 1.2e8),
    ("2조 7,421억", 2.7421e12),
    ("1조 1,666억", 1.1666e12),     # 냉동간편식 시장 규모
    ("804만 5천", 8.045e6),         # 1인 가구 수
    ("38조", 3.8e13),
    ("6,513", 6513.0),              # 배율말 없는 순수 수 (냉동식품 판매단가)
    ("1,140,941백만원", 1.140941e12),  # 공시 표기 — `백`이 자리 «안»에 온다
    ("십억", 1e9),                  # 수 없이 배율말만 — 계수는 1이다
    ("백만", 1e6),
]


@pytest.mark.parametrize(("표기", "참값"), CASES)
def test_배율말이_겹쳐도_바르게_읽는다(표기: str, 참값: float) -> None:
    got = synthesize._수값(표기)
    assert got == pytest.approx(참값, rel=1e-9), f"{표기!r} → {got:,.0f} (기대 {참값:,.0f})"


@pytest.mark.parametrize("표기", ["", None, "없음", "미확보"])
def test_못_읽으면_추측하지_않는다(표기) -> None:
    """⚠ **`-1` 이지 `None` 이 아니다.** 그대로 흘리면 화면에 「−1원」이 값처럼 앉는다 —
    부르는 쪽이 반드시 걸러야 한다는 뜻이고, 이 검사는 그 계약을 고정한다."""
    assert synthesize._수값(표기) == -1.0


def test_같은_값의_다른_표기는_같은_수가_된다() -> None:
    """중복 접기가 이 성질에 서 있다 — 판 ㊵ 의 「804만 5천 ×4 중복」이 이 자리의 병이었다."""
    assert synthesize._수값("804만 5천") == synthesize._수값("804만5,000")
    assert synthesize._수값("6조 8천억") == synthesize._수값("6,800,000,000,000")
