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

import pytest

from app.research.bm.contracts import BMAnalysisResult, BMCanvasItem, CanvasStatus
from app.validation import mapping
from app.validation.gate import PLANNED_CELLS
from app.validation.mapping import (CELL_NAME_KO, CLAIM_TYPE_CELL, CLAIM_TYPE_LABEL,
                                    apply, derive)

#: 절 → 칸 표가 **켜져 있던 때**의 값. `mapping.SECTION_CELL` 은 2026-08-15 에 비웠다
#: (자료가 그 칸의 주장이 아니었다 — 그쪽 주석 참조). 기구 자체는 계속 시험한다:
#: 시장조사 판이 절 배정을 손보면 이 표를 되돌리는 것으로 다시 켜지기 때문이다.
_켠_표 = {"CHANNEL": "CHANNELS", "PRICE": "REVENUE_STREAMS", "DEMAND": "VALUE_PROPOSITIONS"}


@pytest.fixture
def 절_칸_연결(monkeypatch):
    """이 시험 동안만 절→칸 연결을 켠다."""
    monkeypatch.setattr(mapping, "SECTION_CELL", _켠_표)
    return _켠_표

_ALL_CELLS = ("CUSTOMER_SEGMENTS", "VALUE_PROPOSITIONS", "CHANNELS", "CUSTOMER_RELATIONSHIPS",
              "REVENUE_STREAMS", "KEY_RESOURCES", "KEY_ACTIVITIES", "KEY_PARTNERS",
              "COST_STRUCTURE")

VOCAB = (Path(__file__).parents[1] / "app" / "research" / "research2"
         / "harness" / "vocab.json")


def _card(card_id: str, cell: str, **over) -> dict:
    base = {"카드_id": card_id, "종류": "관측", "칸": cell, "등급": "gov_stat"}
    base.update(over)
    return base


def _승격(card_id: str, 절: str, **over) -> dict:
    """**절 조사가 승격시킨 카드**(`tools/promote_cards.py`)의 모양.

    ⚠ 슬롯 카드와 다르다 — `칸`(claim_type)이 **없고** `_절` 만 있다. 이 차이 때문에
    승격 카드가 어느 칸에도 안 붙어, 절 조사가 찾은 사실 128건이 캔버스에서 인용 0건이었다.
    """
    base = {"카드_id": card_id, "종류": "관측", "_절": 절, "등급": "gov_stat"}
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
    # ⚠ 2026-08-15 정정: 이 자리는 「CHANNEL 에 맞는 라벨은 화이트리스트에 없다」로
    #   `sourceLabels == []` 을 못박고 있었다. 그것이 **결함이었다** — 파생 라벨이 늘 0건이라
    #   `_labels_for` 폴백이 모델이 쓴 `concept_snapshot`(사용자가 쓴 컨셉 서술문)을 되살려
    #   채널 칸만 「자기 입력을 자기가 확인」이 통과했다. `channel_analysis` 를 화이트리스트
    #   넷에 더해 자리를 만들었다.
    assert out["CHANNELS"]["marketEvidenceIds"] == ["C-F004"]
    assert out["CHANNELS"]["sourceLabels"] == ["channel_analysis"]


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


# ── ㉢-2 절(section) → 칸 · 라벨 (승격 카드) ─────────────────────────────────
def test_절_칸_표가_지금은_비어_있다():
    """★ **2026-08-15 — 표를 비웠다. 자료가 그 칸의 주장이 아니었다.**

    유료 실행(`p46-bm-01`)으로 실제 자료를 붙여 놓고 사람이 읽은 결과: 가치 제안 칸에 붙은
    105건이 「가업승계 비율」·「기부 경험」·「국민의 취침 시각」·「자가 소유 필요성」 같은
    것이었고, 수익원 칸에는 「전세보증금 평균」이 판매가와 나란히 섰다. 미리 못박아 둔
    실패선(「한 칸에서 «아니다»가 1/3 이상이면 배지가 거짓」)을 **세 칸 모두 넘겼다.**

    ⚠ 이 시험은 **기능이 꺼져 있다는 사실**을 못박는다. 병은 이 층이 아니라 절 배정
    (`tools/publish_gate.절()` — 시장조사 판 소유)에 있고, 그쪽이 고쳐지면 표를 되돌린다.
    아래 시험들이 `절_칸_연결` 로 기구를 계속 재는 이유가 그것이다.
    """
    assert mapping.SECTION_CELL == {}
    out = derive([_승격("sec-0001", "CHANNEL"), _승격("sec-0002", "PRICE"),
                  _승격("sec-0003", "DEMAND")])
    assert all(not out[name]["marketEvidenceIds"] for name in out)


def test_승격_카드가_칸에_붙는다(절_칸_연결):
    """표를 되돌리면 절 조사가 찾은 사실이 캔버스에 닿는다 — 기구는 살아 있다."""
    out = derive([_승격("P-0001", "CHANNEL"), _승격("P-0002", "PRICE"),
                  _승격("P-0003", "DEMAND")])
    assert out["CHANNELS"]["marketEvidenceIds"] == ["P-0001"]
    assert out["CHANNELS"]["sourceLabels"] == ["channel_analysis"]
    assert out["REVENUE_STREAMS"]["sourceLabels"] == ["price_analysis"]
    assert out["VALUE_PROPOSITIONS"]["sourceLabels"] == ["demand_evidence"]


def test_시장크기_성장률_경쟁_절은_칸에_안_붙는다():
    """★ **일부러 안 잇는다.**

    시장이 11조라는 것과 「수도권 25~44세 1인 가구」라는 세그먼트 정의가 맞다는 것은
    다른 주장이다. 이으면 고객 세그먼트 칸의 근거 수가 불어 배지가 「근거 있음」으로 굳고,
    승격 카드를 슬롯 판정에 안 넣기로 한 규율이 **배지 층에서 도로 뚫린다.**
    """
    out = derive([_승격("P-0010", "MARKET_SIZE"), _승격("P-0011", "GROWTH"),
                  _승격("P-0012", "COMPETITOR")])
    assert out["CUSTOMER_SEGMENTS"]["marketEvidenceIds"] == []
    assert out["VALUE_PROPOSITIONS"]["marketEvidenceIds"] == []


def test_원가_규제_절도_칸에_안_붙는다():
    """대응하는 관측 칸이 없다. 계획 5칸에 붙이면 경계 문구가 사라진다."""
    out = derive([_승격("P-0020", "UNIT_ECONOMICS"), _승격("P-0021", "REGULATION")])
    assert all(not out[name]["marketEvidenceIds"] for name in out)


def test_슬롯_카드와_승격_카드가_한_칸에서_섞인다(절_칸_연결):
    """둘 다 채널이면 한 칸에 나란히 선다 — 모집단이 갈리지 않는다."""
    out = derive([_card("C-F004", "CHANNEL"), _승격("P-0001", "CHANNEL")])
    assert out["CHANNELS"]["marketEvidenceIds"] == ["C-F004", "P-0001"]
    assert out["CHANNELS"]["sourceLabels"] == ["channel_analysis"]


# ── ㉢-3 승격 카드는 «붙되» 판정을 올리지 못한다 (2026-08-15) ─────────────────
def test_승격_카드만_있으면_확인됨이_되지_않는다(절_칸_연결):
    """★ **실측으로 잡은 거짓 확신.**

    승격 카드를 개수에 같이 세니 채널 칸이 `UNVERIFIED`(근거 0건) → `VERIFIED`(근거 4건)로
    뒤집혔는데, 그 4건이 **귀촌 전 거주지역 구성비 · 우체국 택배 배송기간 2건 ·
    온라인몰 구입 비율 1건**이었다. 화면은 「채널은 시장이 확인해 줬어요」라고 적는다.

    **0건 `UNVERIFIED` 는 참말이었고 4건 `VERIFIED` 는 거짓말이다 — 빈손보다 나쁘다.**
    승격 카드는 「그 절에 실을 만한 사실」이지 「이 칸의 주장을 겨냥해 모은 근거」가 아니다.
    """
    out = derive([_승격(f"sec-{n:04d}", "CHANNEL") for n in range(1, 5)])
    assert out["CHANNELS"]["status"] is CanvasStatus.UNVERIFIED
    # ⚠ **근거는 그대로 붙어 있어야 한다.** 떼면 화면 근거표가 비고, 자바 계약
    #   (`marketEvidenceIds ⊆ evidence[].id`)과도 무관하게 사용자가 볼 것이 사라진다.
    assert len(out["CHANNELS"]["marketEvidenceIds"]) == 4
    assert out["CHANNELS"]["sourceLabels"] == ["channel_analysis"]


def test_슬롯_카드가_둘이면_승격과_무관하게_확인됨이다(절_칸_연결):
    """반대 방향도 잰다 — 규칙을 조인 것이 아니라 **모집단을 가른 것**이다."""
    out = derive([_card("C-F004", "CHANNEL"), _card("C-F005", "CHANNEL"),
                  _승격("sec-0001", "CHANNEL")])
    assert out["CHANNELS"]["status"] is CanvasStatus.VERIFIED
    assert len(out["CHANNELS"]["marketEvidenceIds"]) == 3


def test_슬롯_카드_한_장은_승격이_아무리_많아도_부분이다(절_칸_연결):
    """승격 105장이 붙어도 「부분」을 「확인됨」으로 밀어 올리지 못한다."""
    out = derive([_card("C-F010", "PRICE")]
                 + [_승격(f"sec-{n:04d}", "PRICE") for n in range(1, 106)])
    assert out["REVENUE_STREAMS"]["status"] is CanvasStatus.PARTIAL
    assert len(out["REVENUE_STREAMS"]["marketEvidenceIds"]) == 106


# ── ㉣ content 가 있으면 라벨을 비우지 않는다 ────────────────────────────────
def test_파생_라벨이_0건이어도_라벨을_비우지_않는다():
    """content 가 비어 있지 않은데 sourceLabels 가 0건이면 자바 계약이 거부한다.

    ⚠ 카드를 **주지 않는다.** 카드를 주면 이제 파생 라벨이 나와(`channel_analysis`)
    폴백 자체를 안 탄다 — 그러면 이 시험은 아무것도 안 재는 시험이 된다.
    """
    fixed = apply(_analysis(_item("CHANNELS", source_labels=["concept_snapshot", "made_up"])),
                  [])
    assert _by_cell(fixed, "CHANNELS").source_labels == ["concept_snapshot"]


def test_폴백은_시장_라벨을_되살리지_않는다():
    """★ 게이트 G1 이 라벨만 보고 통과하던 구멍.

    G1 은 ①근거 id 가 있다 ②시장 라벨이 하나라도 있다 — 둘 중 하나만 통과하면 안 걸린다.
    폴백이 모델이 쓴 `market_size` 를 되살리면 **근거 0건인데 반증을 피한다.** 모델이
    「이 칸은 market_size 에서 왔다」고 쓰기만 하면 되던 것이다.
    """
    fixed = apply(_analysis(_item("CUSTOMER_SEGMENTS", source_labels=["market_size"])), [])
    labels = _by_cell(fixed, "CUSTOMER_SEGMENTS").source_labels
    assert labels == ["concept_snapshot"], "시장 라벨이 되살아나면 안 된다"
    assert labels, "그렇다고 비우면 자바 계약이 거부한다"


def test_폴백은_시장_라벨이_아닌_것은_남긴다():
    """`execution_constraints` 는 사용자 입력이라 남긴다 — 그것이 사실이다."""
    fixed = apply(_analysis(_item("CHANNELS",
                                  source_labels=["execution_constraints", "price_analysis"])), [])
    assert _by_cell(fixed, "CHANNELS").source_labels == ["execution_constraints"]


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
    """모든 claim_type 에 라벨이 있다.

    ⚠ 2026-08-15 정정: 이 시험은 *"라벨이 없는 claim_type 은 CHANNEL 하나뿐"* 을 못박고
    있었다. 그 빈자리가 채널 칸의 자기확인 회로를 열어 두던 원인이라 `channel_analysis`
    를 화이트리스트에 더했다. **이제 빈자리는 0개여야 한다** — 새 빈자리가 생기면 같은
    병이 다른 칸에서 재발한다.
    """
    assert not set(CLAIM_TYPE_CELL) - set(CLAIM_TYPE_LABEL)
    assert not set(CLAIM_TYPE_LABEL) - set(CLAIM_TYPE_CELL)
