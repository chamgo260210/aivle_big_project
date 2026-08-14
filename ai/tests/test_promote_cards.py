# -*- coding: utf-8 -*-
"""**승격 카드의 그물.** (판 ㊸)

봉투 `evidence[]` 147장 중 **132장을 만드는 기계**가 자동 검사 밖에 있었다 —
`ai/pytest.ini` 의 `norecursedirs` 가 `app/research/research2` 를 빼기 때문에
그 폴더에 테스트를 두면 **안 돈다.** 그래서 여기 둔다.

재는 것은 셋이고, 셋 다 **이미 규칙 파일에 있는 것**을 승격이 지키는지 볼 뿐이다:

1. 등급은 `rules/fill.v2.json` 의 `등급표[kind]` 와 같다 — **새 잣대를 만들지 않는다**
2. `채택_불가_부류`(커뮤니티 추측)는 **승격되지 않는다** — 낮은 등급으로도 안 실린다
3. 조회일이 없으면 **승격되지 않는다** — 백필 금지. 오늘 날짜를 넣는 것은 지어내기다
"""
from __future__ import annotations

import os
import sys

import pytest

_HERE = os.path.dirname(os.path.abspath(__file__))
_R2 = os.path.abspath(os.path.join(_HERE, "..", "app", "research", "research2"))
for _dir in (_R2, os.path.join(_R2, "tools"), os.path.join(_R2, "adapters"),
             os.path.join(_R2, "blocks")):
    if _dir not in sys.path:
        sys.path.insert(0, _dir)


@pytest.fixture(scope="module")
def promote():
    return pytest.importorskip("promote_cards")


def _publish(url="https://kind.krx.co.kr/x", 조회일="2026-08-12T07:37:20",
             quote_verified=True):
    """문서 하나 · 실린 사실 하나. **모양은 `publish_gate.build()` 산출 그대로다.**"""
    return {"문서별": [{
        "trace_id": "T-1", "url": url, "조회일": 조회일,
        "items": [{
            "section": "MARKET_SIZE", "subject": "냉동식품 판매단가",
            "number_raw": "6,513", "unit_raw": "원", "year": "2025",
            "quote": "냉동식품 판매단가는 6,513원이다", "quote_verified": quote_verified,
            "table_context": "", "게재": "COMPETITOR_FIRM",
            "게재_사유": "공시 문서의 수", "게재_제자리": False, "게재_발행사": "오뚜기",
        }],
    }]}


def test_등급은_등급표에서_그대로_온다(promote):
    """**새 잣대를 만들지 않는다.** 슬롯 카드가 등급을 받는 자리와 같아야 한다."""
    from runlog import load_rules
    표 = load_rules()["fill"]["등급표"]

    카드 = promote.build(_publish())
    assert len(카드) == 1
    card = 카드[0]
    assert card["kind"] == "public_filing"
    # 공시는 등급표에서 「확정」이다 — 여기서 다른 값이 나오면 잣대가 둘이 된 것이다.
    assert card["등급"] == "확정"
    assert card["등급"] in [k for k in 표 if not k.startswith("_")]


def test_조회일이_없으면_승격되지_않는다(promote):
    """**백필 금지.** 오늘 날짜를 넣는 것은 지어내기다(`fill.v2.json` `_백필_금지`).

    ⚠ 이 검사가 없으면 **「채택 불가」가 「확정」으로 화면에 앉는다.** 실제로 그랬다 —
    조회일을 `a3_candidate` 에서만 걷어 132장 전부 `null` 이었다.
    """
    assert promote.build(_publish(조회일=None)) == []


def test_채택_불가_부류는_낮은_등급으로도_안_실린다(promote):
    """`채택_불가_부류` 는 **등급을 낮게 주는 것이 아니라 받지 않는다**고 규칙이 적어 뒀다.

    ⚠ 이 검사가 없으면 `_등급` 의 `_기본` 폴백이 커뮤니티 추측을 **「추정」으로 살려 준다.**
    이번 컨셉에서 안 터진 것은 막았기 때문이 아니라 출처 분포에 0건이었기 때문이다.
    """
    from runlog import load_rules
    불가 = (load_rules()["fill"].get("채택_불가_부류") or {}).get("kinds") or {}
    assert "community" in 불가, "규칙이 바뀌었으면 이 검사도 같이 본다"

    from a_desk import kind_of
    wl = load_rules()["whitelist"]
    도메인 = (wl["kinds"].get("community") or [])
    assert 도메인, "community 도메인이 사라졌으면 규칙이 바뀐 것이다 — 같이 본다"
    url = f"https://{도메인[0]}/x"
    assert kind_of(url, wl)[0] == "community"
    assert promote.build(_publish(url=url)) == []


def test_url이_없으면_승격되지_않는다(promote):
    """되짚을 수 없는 값은 근거가 아니다(`채택_요건.url`).

    실측: 이 검사 하나가 「2024년 온라인 쇼핑 거래액 242조 · 단위 없음」을 걷어냈다 —
    거래액이 가격 절에 앉아 6천 원대 행들 사이에서 표의 자릿수를 부수던 것이다.
    """
    assert promote.build(_publish(url="")) == []


def test_인용_대조에_떨어진_것은_승격되지_않는다(promote):
    assert promote.build(_publish(quote_verified=False)) == []
