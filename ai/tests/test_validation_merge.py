# -*- coding: utf-8 -*-
"""**두 봉투를 합치는 자리.** (판 ㊸ 보완)

`ai/app/validation/runner.py::_merge` 는 사용자가 실제로 누르는 「사업 검증」이 지나는
길인데 **테스트가 0개였다.** 계획서가 열세 자리 중 5번으로 「VALIDATION 에서만 조용히
사라진다」고 스스로 적어 둔 자리이고, 실제로 두 번 그 일이 났다:

1. `evidence` 를 통째로 갈아끼워 FULL 의 승격 카드가 지워질 뻔했다(고쳐 둔 것)
2. 성적표에서 **BM 의 `MISSING` 이 FULL 의 `MISSING` 을 이겨**, 절 체인이 돌아
   「재고 0건」인 과목이 화면에서 **「안 쟀다」로 뒤집혔다**(VALIDATION 실측)

두 번째는 특히 고약하다 — 그 문장은 **재서 없는 것과 안 잰 것을 가르려고** 만든 것인데
정반대로 쓰였다.
"""
from __future__ import annotations

from app.validation.runner import _merge


def _봉투(**칸) -> dict:
    바탕 = {"runId": "r", "conceptId": "c", "asOf": "2026-08-15", "generatedAt": "t",
            "mode": "FULL", "stages": [], "degradations": [], "scorecard": [],
            "market": None, "canvas": None, "bm": None, "evidence": [],
            "summary": None, "notes": [],
            "judgment": None, "prescriptions": None, "synthesis": None}
    return {**바탕, **칸}


def _줄(subject: str, state: str, detail: str) -> dict:
    return {"subject": subject, "state": state, "detail": detail}


def test_둘_다_미확보면_FULL_의_사유가_남는다():
    """**BM 의 `MISSING` 은 정보가 아니다** — 절 체인을 안 돌아 구조적으로 그렇게 나온다."""
    full = _봉투(scorecard=[_줄("UNIT_ECONOMICS", "MISSING",
                              "한 개 팔면 얼마가 남나 — **한 건도 못 구했다.** 8절 처방을 보라")])
    bm = _봉투(mode="BM", scorecard=[_줄("UNIT_ECONOMICS", "MISSING",
                                        "이 실행은 절 조사를 돌리지 않았다 — 0건이 아니라 «안 쟀다»다")])

    out = _merge(full, bm)["scorecard"]
    assert len(out) == 1
    assert "못 구했다" in out[0]["detail"]
    assert "안 쟀다" not in out[0]["detail"], "재고 0건이 「안 쟀다」로 뒤집혔다"


def test_FULL_이_채운_과목을_BM_이_지우지_않는다():
    full = _봉투(scorecard=[_줄("CHANNEL", "PARTIAL", "채널 — 실린 사실 1건")])
    bm = _봉투(mode="BM", scorecard=[_줄("CHANNEL", "MISSING", "안 쟀다")])
    assert _merge(full, bm)["scorecard"][0]["state"] == "PARTIAL"


def test_BM_이_실제로_채웠으면_BM_이_이긴다():
    """**「뒤 걸음이 이긴다」는 원래 규칙**은 BM 이 정말 잰 경우에만 산다."""
    full = _봉투(scorecard=[_줄("DEMAND", "MISSING", "근거 0건")])
    bm = _봉투(mode="BM", scorecard=[_줄("DEMAND", "FILLED", "근거 4건")])
    assert _merge(full, bm)["scorecard"][0]["state"] == "FILLED"


def test_승격_카드가_BM_봉투에_지워지지_않는다():
    """FULL 은 승격 카드를, BM 은 슬롯 카드만 들고 온다. 합집합이어야 한다."""
    full = _봉투(evidence=[{"id": "sec-0001", "section": "PRICE"},
                          {"id": "C-F001", "section": "MARKET_SIZE"}])
    bm = _봉투(mode="BM", evidence=[{"id": "C-F001", "section": None}])

    out = _merge(full, bm)["evidence"]
    ids = {e["id"] for e in out}
    assert ids == {"sec-0001", "C-F001"}
    # 절이 붙은 것을 절 없는 것으로 덮지 않는다 — 덮으면 화면이 그 카드를 못 건다.
    assert next(e for e in out if e["id"] == "C-F001")["section"] == "MARKET_SIZE"


def test_2_8_9절은_BM_의_null_이_이기지_않는다():
    full = _봉투(judgment={"결론": "x"}, prescriptions=[{"a": 1}], synthesis=[{"b": 2}])
    bm = _봉투(mode="BM")
    out = _merge(full, bm)
    assert out["judgment"] == {"결론": "x"}
    assert out["prescriptions"] == [{"a": 1}]
    assert out["synthesis"] == [{"b": 2}]
    assert out["mode"] == "VALIDATION"
