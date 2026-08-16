# -*- coding: utf-8 -*-
"""인용 강제 — 근거 없이 「확인됨」이라고 쓴 칸을 사실대로 내린다.

실측(2026-08-13, 프로젝트 3 HMR): 원장에 근거 17건이 있고 BM 에 전달까지 됐는데 캔버스가
인용한 것은 0건이었고, 그러면서 관측 3칸이 `VERIFIED` 였다. 프롬프트가 `source_labels` 만
강제하고 `market_evidence_ids` 는 요구한 적이 없어서 **모델이 규칙을 지키면서** 그럴 수 있었다.
"""
from __future__ import annotations

from app.research.bm.contracts import BMAnalysisResult, BMCanvasItem
from app.validation.citation import enforce

_ALL_CELLS = ("CUSTOMER_SEGMENTS", "VALUE_PROPOSITIONS", "CHANNELS", "CUSTOMER_RELATIONSHIPS",
              "REVENUE_STREAMS", "KEY_RESOURCES", "KEY_ACTIVITIES", "KEY_PARTNERS",
              "COST_STRUCTURE")


def _item(cell: str, **over) -> BMCanvasItem:
    base = {"canvas_cell": cell, "content": ["내용"], "source_labels": ["market_size"],
            "market_evidence_ids": ["C-F001"], "status": "VERIFIED", "reason": "사유",
            "missing_evidence": []}
    base.update(over)
    return BMCanvasItem(**base)


def _analysis(*over: BMCanvasItem) -> BMAnalysisResult:
    changed = {str(item.canvas_cell): item for item in over}
    canvas = [changed.get(name) or _item(name) for name in _ALL_CELLS]
    return BMAnalysisResult(
        concept_id="c1", concept_name="n", canvas=canvas,
        market_fit_status="PASS", consistency_status="PASS",
        market_fit_summary="a", consistency_summary="b")


def _by_cell(analysis, cell):
    return next(i for i in analysis.canvas if str(i.canvas_cell) == cell)


def test_컨셉_서술만으로_확인됨이라_쓰면_내린다():
    """실측에서 본 바로 그 모양이다."""
    fixed, corrections = enforce(_analysis(
        _item("CHANNELS", source_labels=["concept_snapshot"], market_evidence_ids=[])))
    assert [c.cell for c in corrections] == ["CHANNELS"]
    assert [c.was for c in corrections] == ["VERIFIED"]
    assert str(_by_cell(fixed, "CHANNELS").status) == "UNVERIFIED"


def test_무엇이_없는지_적는다():
    fixed, _ = enforce(_analysis(
        _item("CHANNELS", source_labels=["concept_snapshot"], market_evidence_ids=[])))
    cell = _by_cell(fixed, "CHANNELS")
    assert any("인용" in text for text in cell.missing_evidence)
    assert "확인됨" in cell.reason


def test_PARTIAL_도_주장이다():
    _, corrections = enforce(_analysis(
        _item("REVENUE_STREAMS", status="PARTIAL",
              source_labels=["execution_constraints"], market_evidence_ids=[])))
    assert [c.was for c in corrections] == ["PARTIAL"]


def test_근거_id_가_있으면_안_내린다():
    _, corrections = enforce(_analysis(
        _item("CHANNELS", source_labels=["concept_snapshot"], market_evidence_ids=["C-F001"])))
    assert corrections == []


def test_시장_라벨이_있으면_안_내린다():
    """근거 id 가 없어도 시장 라벨이 붙었으면 조사는 닿은 것이다."""
    _, corrections = enforce(_analysis(
        _item("CHANNELS", source_labels=["competitor_analysis"], market_evidence_ids=[])))
    assert corrections == []


def test_계획칸은_안_건드린다():
    """계획 칸은 원래 관측이 없다. 여기까지 내리면 모든 캔버스가 미확인이 된다."""
    _, corrections = enforce(_analysis(
        _item("KEY_PARTNERS", source_labels=["concept_snapshot"], market_evidence_ids=[])))
    assert corrections == []


def test_이미_미확인이면_주장이_없다():
    _, corrections = enforce(_analysis(
        _item("CHANNELS", status="UNVERIFIED",
              source_labels=["concept_snapshot"], market_evidence_ids=[])))
    assert corrections == []


def test_고칠_것이_없으면_원본을_그대로_돌려준다():
    analysis = _analysis()
    fixed, corrections = enforce(analysis)
    assert corrections == []
    assert fixed is analysis


def test_원본을_바꾸지_않는다():
    analysis = _analysis(
        _item("CHANNELS", source_labels=["concept_snapshot"], market_evidence_ids=[]))
    enforce(analysis)
    assert str(_by_cell(analysis, "CHANNELS").status) == "VERIFIED"
