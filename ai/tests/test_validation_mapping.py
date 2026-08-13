# -*- coding: utf-8 -*-
"""칸 → 근거 매핑 — 근거 id·출처 라벨·상태를 **기계가 확정한다.** LLM 0회.

실측(2026-08-13, 프로젝트 3 HMR)에서 캔버스의 인용이 0건인데 관측 3칸이 `VERIFIED` 였다.
모델에게 인용을 부탁하는 대신 카드에서 직접 파생해 그 구멍을 구조적으로 막는다.

⚠ 계획 5칸은 **손대지 않는다.** 근거 id 가 붙는 순간 `serialize._stamp_user_plan()` 이
그 칸을 건너뛰어 PLAN 도장과 「사용자가 입력한 실행 계획이다 — 관측이 아니다」가 사라진다.
"""
from __future__ import annotations

import json
from pathlib import Path

from app.research.bm.contracts import BMAnalysisResult, BMCanvasItem, CanvasStatus
from app.validation.gate import PLANNED_CELLS
from app.validation.mapping import (CELL_NAME_KO, CLAIM_TYPE_CELL, CLAIM_TYPE_LABEL,
                                    apply, derive)

_ALL_CELLS = ("CUSTOMER_SEGMENTS", "VALUE_PROPOSITIONS", "CHANNELS", "CUSTOMER_RELATIONSHIPS",
              "REVENUE_STREAMS", "KEY_RESOURCES", "KEY_ACTIVITIES", "KEY_PARTNERS",
              "COST_STRUCTURE")

VOCAB = (Path(__file__).parents[1] / "app" / "research" / "research2"
         / "harness" / "vocab.json")


def _card(card_id: str, cell: str, **over) -> dict:
    base = {"카드_id": card_id, "종류": "관측", "칸": cell, "등급": "gov_stat"}
    base.update(over)
    return base


def _item(cell: str, **over) -> BMCanvasItem:
    base = {"canvas_cell": cell, "content": ["내용"], "source_labels": ["concept_snapshot"],
            "market_evidence_ids": [], "status": "VERIFIED", "reason": "사유",
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


def _by_cell(analysis, cell) -> BMCanvasItem:
    return next(i for i in analysis.canvas if str(i.canvas_cell) == cell)


# ── ㉠ claim_type → 칸 · 라벨 ────────────────────────────────────────────────
def test_claim_type_에서_칸과_라벨이_나온다():
    cards = [_card("C-F001", "TAM"), _card("C-F002", "SAM"), _card("C-F003", "PAIN"),
             _card("C-F004", "CHANNEL"), _card("C-F005", "PRICE")]
    out = derive(cards)
    assert out["CUSTOMER_SEGMENTS"]["marketEvidenceIds"] == ["C-F001", "C-F002"]
    assert out["CUSTOMER_SEGMENTS"]["sourceLabels"] == ["market_size"]
    assert out["VALUE_PROPOSITIONS"]["sourceLabels"] == ["demand_evidence"]
    assert out["REVENUE_STREAMS"]["marketEvidenceIds"] == ["C-F005"]
    assert out["REVENUE_STREAMS"]["sourceLabels"] == ["price_analysis"]
    # CHANNEL 에 맞는 라벨은 화이트리스트에 없다 — 새로 만들지 않는다.
    assert out["CHANNELS"]["marketEvidenceIds"] == ["C-F004"]
    assert out["CHANNELS"]["sourceLabels"] == []


def test_모델이_인용을_안_했어도_기계가_붙인다():
    """실측에서 본 모양: 근거는 있는데 캔버스 인용이 0건이었다."""
    fixed = apply(_analysis(), [_card("C-F001", "TAM"), _card("C-F002", "SAM")])
    cell = _by_cell(fixed, "CUSTOMER_SEGMENTS")
    assert cell.market_evidence_ids == ["C-F001", "C-F002"]
    assert cell.source_labels == ["market_size"]
    assert cell.status is CanvasStatus.VERIFIED


def test_근거가_0건이면_확인됨을_유지하지_않는다():
    fixed = apply(_analysis(), [])
    assert _by_cell(fixed, "CHANNELS").status is CanvasStatus.UNVERIFIED
    assert _by_cell(fixed, "CHANNELS").market_evidence_ids == []


# ── ㉡ 계산 카드의 한글 칸 경로 ──────────────────────────────────────────────
def test_계산_카드는_한글_칸_이름으로_온다():
    cards = [_card("C-CALC-TAM", "고객 세그먼트", 종류="계산"),
             _card("C-CALC-성장률", "고객 세그먼트", 종류="계산")]
    out = derive(cards)
    assert out["CUSTOMER_SEGMENTS"]["marketEvidenceIds"] == ["C-CALC-TAM", "C-CALC-성장률"]
    assert out["CUSTOMER_SEGMENTS"]["sourceLabels"] == ["market_size", "growth_rate"]


def test_관측_카드의_한글_칸은_안_받는다_시끄럽게_실패한다():
    """**전제가 뒤집히는 날의 모양을 못박는다.**

    관측 카드의 `칸` 이 claim_type 인 것은 `run.py:42 mk_slot` 이 `_` 접두 키를 버려
    `_canvas_cell`(한글, 슬롯 40개 중 37개가 싣는다)이 원장까지 못 오기 때문이다.
    `Slot` 에 `canvas_cell` 이 승격되면 `칸` 이 한글로 온다.

    그때 한글을 관측 카드에도 받아 주면 **근거 id 는 붙는데 라벨이 0건**이 되고
    `_labels_for` 폴백이 모델의 `concept_snapshot` 을 되살린다 — 이 층이 없애려던 상태로
    조용히 되돌아간다. 대신 카드를 아예 안 붙여 G1 이 걸리게 둔다(fail-closed).
    """
    out = derive([_card("C-F001", "고객 세그먼트")])
    assert out["CUSTOMER_SEGMENTS"]["marketEvidenceIds"] == []
    assert out["CUSTOMER_SEGMENTS"]["status"] is CanvasStatus.UNVERIFIED


def test_수익원_칸은_수요근거_라벨을_못_만든다():
    """`gate.G5` 와 짝이다 — 이 층이 `demand_evidence` 를 수익원 칸에 만들 수 없으므로
    게이트는 그것을 **캔버스 전체**에서 세야 한다. 자기 칸만 보면 상시 발동한다.
    """
    every = [_card(f"C-F{i:03d}", claim_type) for i, claim_type in enumerate(CLAIM_TYPE_CELL)]
    assert "demand_evidence" not in derive(every)["REVENUE_STREAMS"]["sourceLabels"]
    assert "demand_evidence" in derive(every)["VALUE_PROPOSITIONS"]["sourceLabels"]


# ── ㉢ 계획 5칸은 그대로 ────────────────────────────────────────────────────
def test_계획_5칸은_입력_그대로다():
    """근거 id 가 붙으면 PLAN 도장과 USER_PLAN_CAVEAT 이 통째로 사라진다."""
    before = _analysis()
    fixed = apply(before, [_card("C-F001", "TAM"), _card("C-F002", "PRICE")])
    for name in PLANNED_CELLS:
        assert _by_cell(fixed, name) is _by_cell(before, name)
        assert _by_cell(fixed, name).market_evidence_ids == []


# ── ㉣ content 가 있으면 라벨을 비우지 않는다 ────────────────────────────────
def test_파생_라벨이_0건이어도_라벨을_비우지_않는다():
    """content 가 비어 있지 않은데 sourceLabels 가 0건이면 자바 계약이 거부한다."""
    fixed = apply(_analysis(_item("CHANNELS", source_labels=["concept_snapshot", "made_up"])),
                  [_card("C-F004", "CHANNEL")])
    assert _by_cell(fixed, "CHANNELS").source_labels == ["concept_snapshot"]


def test_content_가_비면_라벨도_빈다():
    fixed = apply(_analysis(_item("CHANNELS", content=[],
                                  source_labels=["concept_snapshot"])), [])
    assert _by_cell(fixed, "CHANNELS").source_labels == []


# ── 도장 ────────────────────────────────────────────────────────────────────
def _verdict(**stamps) -> dict:
    return {"판정": {key: {"도장": value} for key, value in stamps.items()}}


def test_도장이_검증됨이면_VERIFIED_아니면_PARTIAL():
    cards = [_card("C-F004", "CHANNEL"), _card("C-F005", "PRICE")]
    out = derive(cards, _verdict(**{"7_채널": "검증됨", "6_수익_가격": "미검증"}))
    assert out["CHANNELS"]["status"] is CanvasStatus.VERIFIED
    assert out["REVENUE_STREAMS"]["status"] is CanvasStatus.PARTIAL


def test_도장이_없는_칸은_근거_개수로_정한다():
    """`CUSTOMER_SEGMENTS` 에 대응하는 도장이 verdict 에 없다."""
    assert derive([_card("C-F001", "TAM")])["CUSTOMER_SEGMENTS"]["status"] \
        is CanvasStatus.PARTIAL
    assert derive([_card("C-F001", "TAM"), _card("C-F002", "SAM")]) \
        ["CUSTOMER_SEGMENTS"]["status"] is CanvasStatus.VERIFIED


def test_법률이_낸_BLOCKED_는_덮지_않는다():
    fixed = apply(_analysis(_item("REVENUE_STREAMS", status="BLOCKED")),
                  [_card("C-F005", "PRICE")], _verdict(**{"6_수익_가격": "검증됨"}))
    assert _by_cell(fixed, "REVENUE_STREAMS").status is CanvasStatus.BLOCKED


def test_status_는_enum_이라_직렬화가_안_터진다():
    """`model_copy(update=...)` 는 검증을 안 거친다 — 평문 문자열이면 `.value` 에서 터진다."""
    fixed = apply(_analysis(), [_card("C-F001", "TAM")])
    for item in fixed.canvas:
        assert item.status.value == str(item.status)


# ── ㉤ vocab.json 과 갈라지지 않는다 ─────────────────────────────────────────
def test_칸_표가_vocab_json_과_일치한다():
    """사본 금지 검사(`test_no_duplicate_research2.py`)는 파일명만 세므로 여기서 묶는다."""
    vocab = json.loads(VOCAB.read_text(encoding="utf-8"))["canvas"]
    measured = vocab["측정판정"]["cells"]

    assert set(CELL_NAME_KO) == set(measured), "한글 칸 이름이 vocab 과 갈렸다"
    assert set(CELL_NAME_KO.values()) | set(PLANNED_CELLS) == set(_ALL_CELLS)

    expected: dict[str, str] = {}
    for ko, spec in measured.items():
        for claim_type in spec["claim_types"]:
            expected[claim_type] = CELL_NAME_KO[ko]
        for types in (spec.get("claim_types_by_formula") or {}).values():
            for claim_type in types:
                expected[claim_type] = CELL_NAME_KO[ko]
    assert CLAIM_TYPE_CELL == expected, "claim_type → 칸 표가 vocab 과 갈렸다"


def test_라벨_표는_칸_표와_같은_claim_type_을_다룬다():
    """라벨이 없는 claim_type 은 CHANNEL 하나뿐이다 — 화이트리스트에 자리가 없다."""
    assert set(CLAIM_TYPE_CELL) - set(CLAIM_TYPE_LABEL) == {"CHANNEL"}
    assert not set(CLAIM_TYPE_LABEL) - set(CLAIM_TYPE_CELL)
