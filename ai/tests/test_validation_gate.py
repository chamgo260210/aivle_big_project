# -*- coding: utf-8 -*-
"""판정 게이트 — LLM 이 쓴 status 를 안 믿고 **근거 개수**로 반증한다.

정본 픽스처(`fixtures/market_research/bm.json`)가 이 게이트에 걸려야 한다. 걸리지 않으면
규칙이 무력한 것이다 — 그 픽스처는 CHANNELS 칸의 자료가 0건인데 `CONDITIONAL` 로 통과한다.
"""
from __future__ import annotations

import json
from pathlib import Path

import pytest

from app.validation.gate import OBSERVED_CELLS, apply_decision, evaluate

FIXTURE = Path(__file__).parent / "fixtures" / "market_research" / "bm.json"


def _cells() -> list[dict]:
    return json.loads(FIXTURE.read_text(encoding="utf-8"))["canvas"]["cells"]


def _cell(canvas_cell: str, **over) -> dict:
    base = {"canvasCell": canvas_cell, "status": "VERIFIED", "content": ["내용"],
            "reason": "사유", "sourceLabels": ["market_size"],
            "marketEvidenceIds": ["C-F001"], "missingEvidence": [], "caveats": []}
    base.update(over)
    return base


def _healthy() -> list[dict]:
    """모든 규칙을 통과하는 캔버스 9칸."""
    cells = [_cell(name) for name in OBSERVED_CELLS]
    for cell in cells:
        if cell["canvasCell"] == "REVENUE_STREAMS":
            cell["sourceLabels"] = ["price_analysis", "demand_evidence"]
    for name in ("CUSTOMER_RELATIONSHIPS", "KEY_RESOURCES", "KEY_ACTIVITIES",
                 "KEY_PARTNERS", "COST_STRUCTURE"):
        cells.append(_cell(name, status="PLAN"))
    # 계획 5칸이 전부 PLAN 이면 G4 가 걸리므로 하나는 관측이 닿은 것으로 둔다.
    cells[-1]["status"] = "VERIFIED"
    return cells


# ── 정본 픽스처가 실제로 걸리는가 (이 판의 합격선) ──────────────────────────
def test_정본_픽스처가_게이트에_걸린다():
    reasons = evaluate(_cells())
    codes = {reason["code"] for reason in reasons}
    assert "G1" in codes, "CHANNELS 자료가 0건인데 G1 이 안 걸렸다"


def test_정본_픽스처는_수요근거가_있어_G5_가_안_걸린다():
    """⚠ 픽스처에 저장된 `bm.gateReasons` 에는 아직 G5 가 남아 있다 — 그 필드는 화면·자바가
    읽는 **저장된 값**이고 여기서 다시 재지 않는다. 픽스처가 파생 규칙대로 다시 구워지는
    것은 계획서 1-2 다(관측 4칸의 status·labels 도 같이 갈린다).

    수요 근거(`demand_evidence`, C-F011 노쇼 피해 경험률)는 가치 제안 칸에 실려 있다.
    G5 는 그것을 캔버스 전체에서 세므로 이 픽스처에서는 안 걸리는 것이 맞다.
    """
    assert "G5" not in {reason["code"] for reason in evaluate(_cells())}


def test_정본_픽스처의_판정이_실제로_내려간다():
    """픽스처는 지금 CONDITIONAL 로 통과한다. 게이트 뒤에는 아니어야 한다."""
    reasons = evaluate(_cells())
    assert apply_decision("CONDITIONAL", reasons) == "REVISION_REQUIRED"


def test_G1_은_걸린_칸을_이름으로_지목한다():
    reasons = [r for r in evaluate(_cells()) if r["code"] == "G1"]
    assert [r["cell"] for r in reasons] == ["CHANNELS"]


# ── 규칙별 ────────────────────────────────────────────────────────────────
def test_G1_관측칸_자료가_0건이면_수정필요():
    cells = _healthy()
    for cell in cells:
        if cell["canvasCell"] == "CHANNELS":
            cell.update(sourceLabels=[], marketEvidenceIds=[])
    assert apply_decision("PASS", evaluate(cells)) == "REVISION_REQUIRED"


def test_G1_은_컨셉_서술만_있는_칸을_잡는다():
    """실측(2026-08-13, 프로젝트 3 HMR)에서 게이트가 놓쳤던 바로 그 모양이다.

    관측 3칸이 `labels=['concept_snapshot']`·`marketEvidenceIds=[]` 인데 상태는 VERIFIED,
    최종 판정은 PASS 였다. 모델이 **자기 입력을 자기가 확인했다**고 도장 찍은 것이다.
    라벨이 비어 있는지만 보면(옛 규칙) 이걸 못 잡는다 — 실제 데이터는 항상
    `concept_snapshot` 을 달고 온다.
    """
    cells = _healthy()
    for cell in cells:
        if cell["canvasCell"] == "CHANNELS":
            cell.update(status="VERIFIED", sourceLabels=["concept_snapshot"],
                        marketEvidenceIds=[])
    reasons = [r for r in evaluate(cells) if r["code"] == "G1"]
    assert [r["cell"] for r in reasons] == ["CHANNELS"]
    assert apply_decision("PASS", reasons) == "REVISION_REQUIRED"


def test_G1_은_입력_제약도_관측으로_안_센다():
    """`execution_constraints` 는 사용자가 입력한 예산·기간·인원이다."""
    cells = _healthy()
    for cell in cells:
        if cell["canvasCell"] == "REVENUE_STREAMS":
            cell.update(sourceLabels=["execution_constraints"], marketEvidenceIds=[])
    assert "G1" in {r["code"] for r in evaluate(cells)}


def test_G1_은_참고_근거만_붙은_미확인_칸도_잡는다():
    """★ **2026-08-15 실측으로 잡은 구멍.**

    승격 절 사실이 근거로 붙기 시작하면서 채널 칸이 `UNVERIFIED` 인데도 id 가 4건 있어
    G1 이 **조용해졌고**, BM 판정이 `REVISION_REQUIRED` → `CONDITIONAL` 로 저절로
    완화됐다(유료 실행 `p46-bm-01` 실측). 붙은 4건은 「그 절에 실을 만한 사실」이지 이 칸을
    확인해 준 것이 아니다 — 그러면 **화면에 아무 사유도 없이 빈 칸만 남는다.**
    """
    cells = _healthy()
    for cell in cells:
        if cell["canvasCell"] == "CHANNELS":
            cell.update(status="UNVERIFIED", content=[],
                        sourceLabels=["channel_analysis"],
                        marketEvidenceIds=["sec-0294", "sec-0097", "sec-0356", "sec-0357"])
    hit = [r for r in evaluate(cells) if r["code"] == "G1" and r["cell"] == "CHANNELS"]
    assert hit, "참고 근거만 붙은 미확인 칸을 G1 이 놓쳤다"
    # 「하나도 못 찾았다」와 갈라 적는다 — 사용자가 할 다음 행동이 다르다.
    assert "4건" in hit[0]["message"], hit[0]["message"]
    assert hit[0]["evidenceIds"] == ["sec-0294", "sec-0097", "sec-0356", "sec-0357"]


def test_G1_은_법률이_막은_칸을_건드리지_않는다():
    """★ **위 강화가 만든 회귀**(2026-08-15 감사로 잡음).

    상태를 같이 보게 만든 순간, 근거가 붙어 있는데 `BLOCKED` 인 칸이 그물에 걸려
    **「이 칸을 확인해 준 근거가 없다」는 거짓말**을 하게 됐다. `BLOCKED` 는 시장 근거의
    문제가 아니라 법률의 문제다 — 판정 등급은 안 바뀌지만(BLOCKED 가 더 무겁다)
    **화면 문구가 거짓이 된다.**
    """
    cells = _healthy()
    for cell in cells:
        if cell["canvasCell"] == "REVENUE_STREAMS":
            cell.update(status="BLOCKED", marketEvidenceIds=["C-F015", "C-F016"])
    assert not [r for r in evaluate(cells) if r["code"] == "G1"]


def test_G1_은_확인된_칸은_그대로_통과시킨다():
    """반대 방향 — 규칙을 조인 것이지 전부 걸리게 만든 것이 아니다."""
    assert not [r for r in evaluate(_healthy()) if r["code"] == "G1"]


def test_G1_은_시장_라벨이_하나라도_있으면_안_건다():
    """근거 id 가 없어도 시장 라벨이 붙었으면 조사는 닿은 것이다."""
    cells = _healthy()
    for cell in cells:
        if cell["canvasCell"] == "CHANNELS":
            cell.update(sourceLabels=["concept_snapshot", "competitor_analysis"],
                        marketEvidenceIds=[])
    assert "G1" not in {r["code"] for r in evaluate(cells)}


def test_G1_은_계획칸의_자료_0건은_안_센다():
    """계획 칸은 원래 관측이 없다. 여기까지 세면 모든 사업안이 수정필요가 된다."""
    cells = _healthy()
    for cell in cells:
        if cell["canvasCell"] == "KEY_ACTIVITIES":
            cell.update(sourceLabels=[], marketEvidenceIds=[])
    assert {r["code"] for r in evaluate(cells)} == set()


def test_G4_계획_5칸이_전부_관측_미달이면_PASS_금지():
    cells = _healthy()
    for cell in cells:
        if cell["canvasCell"] not in OBSERVED_CELLS:
            cell["status"] = "PLAN"
    reasons = evaluate(cells)
    assert "G4" in {r["code"] for r in reasons}
    assert apply_decision("PASS", reasons) == "CONDITIONAL"


def test_G5_캔버스에_수요근거가_0건이면_PASS_금지():
    cells = _healthy()
    for cell in cells:
        if cell["canvasCell"] == "REVENUE_STREAMS":
            cell["sourceLabels"] = ["price_analysis"]
    reasons = evaluate(cells)
    assert "G5" in {r["code"] for r in reasons}
    assert [r["cell"] for r in reasons if r["code"] == "G5"] == ["REVENUE_STREAMS"]
    assert apply_decision("PASS", reasons) == "CONDITIONAL"


def test_G5_는_수요근거가_다른_칸에_있으면_걷힌다():
    """**반증 가능해야 한다.** `validation.mapping` 이 근거를 기계로 확정하면 수익원 칸의
    라벨은 어떤 입력에서도 `price_analysis` 뿐이다(`demand_evidence` 는 PAIN 카드에서만
    나오고 PAIN 은 가치 제안 칸으로 간다). 자기 칸만 보면 G5 가 상시 발동해 BM 판정 상한이
    영구 CONDITIONAL 이 된다.
    """
    cells = _healthy()
    for cell in cells:
        if cell["canvasCell"] == "REVENUE_STREAMS":
            cell["sourceLabels"] = ["price_analysis"]
        if cell["canvasCell"] == "VALUE_PROPOSITIONS":
            cell["sourceLabels"] = ["demand_evidence"]
    assert "G5" not in {r["code"] for r in evaluate(cells)}


# ── 게이트는 내리기만 한다 ────────────────────────────────────────────────
def test_게이트는_판정을_올리지_않는다():
    """규칙이 하나도 안 걸려도 원래 판정을 그대로 둔다."""
    assert evaluate(_healthy()) == []
    for decision in ("PASS", "CONDITIONAL", "REVISION_REQUIRED", "BLOCKED"):
        assert apply_decision(decision, []) == decision


def test_법률_BLOCKED_는_게이트가_못_낮춘다():
    """BLOCKED 이 가장 무겁다. G1(수정필요)이 걸려도 BLOCKED 를 덮으면 안 된다."""
    cells = _healthy()
    for cell in cells:
        if cell["canvasCell"] == "CHANNELS":
            cell.update(sourceLabels=[], marketEvidenceIds=[])
    assert apply_decision("BLOCKED", evaluate(cells)) == "BLOCKED"


def test_이미_수정필요면_그대로다():
    cells = _healthy()
    for cell in cells:
        if cell["canvasCell"] == "REVENUE_STREAMS":
            cell["sourceLabels"] = ["price_analysis"]
    assert apply_decision("REVISION_REQUIRED", evaluate(cells)) == "REVISION_REQUIRED"


# ── 사유의 모양 (계약) ────────────────────────────────────────────────────
def test_사유는_계약_다섯_칸을_정확히_갖는다():
    for reason in evaluate(_cells()):
        assert set(reason) == {"code", "cell", "message", "evidenceIds", "cause"}
        assert reason["message"].strip()
        assert isinstance(reason["evidenceIds"], list)
        assert reason["cause"] in {"UNCOLLECTED", "UNCITED", "UNMAPPED"}


# ── 갈래(cause) — 「컨셉을 고쳐서 될 일인가」 (계획서 §5) ──────────────────
def _score(**states):
    return [{"subject": name, "state": state} for name, state in states.items()]


def test_성적표가_없으면_갈래를_추측하지_않는다():
    """추측해서 `UNCOLLECTED` 라고 적으면 「재수집하면 된다」는 거짓 지시가 된다."""
    for reason in evaluate(_cells()):
        assert reason["cause"] == "UNMAPPED"


def test_성적표가_못_찾았으면_A급_미수집이다():
    cells = _cells()
    for cell in cells:
        if cell["canvasCell"] == "CUSTOMER_SEGMENTS":
            cell["marketEvidenceIds"] = []
            cell["sourceLabels"] = ["concept_snapshot"]
    reasons = evaluate(cells, _score(MARKET_SIZE="MISSING"))
    segment = [r for r in reasons if r["cell"] == "CUSTOMER_SEGMENTS"]
    assert segment and segment[0]["cause"] == "UNCOLLECTED"


def test_찾아_놓고_인용을_안_했으면_B급이다():
    """1-1(기계 매핑)이 없애야 할 갈래다 — 사용자가 컨셉을 고칠 일이 아니다."""
    cells = _cells()
    for cell in cells:
        if cell["canvasCell"] == "CUSTOMER_SEGMENTS":
            cell["marketEvidenceIds"] = []
            cell["sourceLabels"] = ["concept_snapshot"]
    reasons = evaluate(cells, _score(MARKET_SIZE="FILLED"))
    segment = [r for r in reasons if r["cell"] == "CUSTOMER_SEGMENTS"]
    assert segment and segment[0]["cause"] == "UNCITED"


def test_성적표가_재지_않는_칸은_모른다고_적는다():
    """그 실행의 성적표에 채널 줄이 **없으면** 갈래를 모른다고 적는다.

    ⚠ 2026-08-15 정정: 이 시험은 *"채널 과목이 성적표에 **없다**"* 를 전제로 적혀 있었다.
    판 ㊸ 이 `CHANNEL`·`UNIT_ECONOMICS`·`REGULATION` 세 과목을 넣어 그 전제는 깨졌다.
    남은 참인 말은 **「이 실행이 채널을 안 쟀으면 모른다」**이고, 그것을 시험한다.
    """
    cells = _cells()
    for cell in cells:
        if cell["canvasCell"] == "CHANNELS":
            cell["marketEvidenceIds"] = []
            cell["sourceLabels"] = ["concept_snapshot"]
    reasons = evaluate(cells, _score(MARKET_SIZE="FILLED", DEMAND="FILLED", PRICE="FILLED"))
    channels = [r for r in reasons if r["cell"] == "CHANNELS"]
    assert channels and channels[0]["cause"] == "UNMAPPED"


def test_성적표에_채널_줄이_있으면_갈래가_나온다():
    """★ 화면이 「조사 항목에 없어서 갈래를 알 수 없어요」라고 거짓말하던 자리.

    성적표에 채널 과목이 생겼는데 `_CELL_SUBJECT` 만 안 따라와서, 채널 사유는 언제나
    `UNMAPPED` 이었다. 갈래가 없으면 사용자는 **다음 행동**(재조사인가 배선인가)을 못 받는다.
    """
    cells = _cells()
    for cell in cells:
        if cell["canvasCell"] == "CHANNELS":
            cell["marketEvidenceIds"] = []
            cell["sourceLabels"] = ["concept_snapshot"]
    못찾음 = evaluate(cells, _score(MARKET_SIZE="FILLED", DEMAND="FILLED",
                                 PRICE="FILLED", CHANNEL="MISSING"))
    assert [r for r in 못찾음 if r["cell"] == "CHANNELS"][0]["cause"] == "UNCOLLECTED"
    인용누락 = evaluate(cells, _score(MARKET_SIZE="FILLED", DEMAND="FILLED",
                                  PRICE="FILLED", CHANNEL="FILLED"))
    assert [r for r in 인용누락 if r["cell"] == "CHANNELS"][0]["cause"] == "UNCITED"


def test_모델이_시장_라벨을_써_넣어도_G1_을_못_피한다():
    """★ 게이트에 뚫려 있던 구멍.

    G1 은 ①근거 id ②시장 라벨 둘 중 하나만 있으면 안 걸린다. 그런데 근거가 0건이면
    `mapping._labels_for` 폴백이 **모델이 쓴 라벨을 되살렸고**, 거기 `market_size` 가
    섞여 있으면 ②로 통과했다 — 모델이 「이 칸은 market_size 에서 왔다」고 쓰기만 하면
    반증을 피한 것이다. 이 시험은 그 문이 닫혔는지 **게이트 쪽에서** 확인한다.
    """
    cells = _cells()
    for cell in cells:
        if cell["canvasCell"] == "CHANNELS":
            cell["marketEvidenceIds"] = []
            # 폴백을 지난 뒤의 모양(시장 라벨이 걸러진 상태)
            cell["sourceLabels"] = ["concept_snapshot"]
    assert any(r["cell"] == "CHANNELS" and r["code"] == "G1" for r in evaluate(cells))


def test_알_수_없는_판정은_거부한다():
    with pytest.raises(ValueError):
        apply_decision("MAYBE", [])
