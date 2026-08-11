# -*- coding: utf-8 -*-
"""오케스트레이터가 내는 **봉투**가 계약과 같은 모양인지 본다.

정본은 `backend/.../MarketResearchContract.java` 이고, **골든 픽스처가 두 언어의 접점**이다.
그래서 여기서는 자바 검증기를 파이썬으로 **다시 구현하지 않는다**(그러면 「같은 물음을 두
곳이 각자 푼다」의 일곱 번째가 된다). 대신 **픽스처와 키 집합을 대조**한다 — 한쪽이
스키마를 바꾸면 이 검사가 먼저 빨개진다.

⚠ 원장(`runs/`)은 저장소에 없다(`.gitignore`). 원장을 요구하는 검사는 **건너뛴다** —
   없는 것을 있는 척 통과시키지 않고, 건너뛴 사실이 보이게 둔다.
"""
from __future__ import annotations

import asyncio
import io
import json
import os
import sys

import pytest

HERE = os.path.dirname(os.path.abspath(__file__))
AI_ROOT = os.path.dirname(HERE)
sys.path.insert(0, AI_ROOT)

from app.research import pipeline, serialize  # noqa: E402
from app.research.bm.contracts import (  # noqa: E402
    BMAnalysisResult,
    BMCanvasItem,
    BMDecision,
    BMFinalResult,
    CanvasCell,
    CanvasStatus,
)

FIXTURES = os.path.join(HERE, "fixtures", "market_research")
#: ⚠ `beauty-13b` 는 이름과 달리 `CPT-CAFE-INV` 로 기록돼 있다 — 표에 든 원장을 쓴다.
SEED_RUN = "beauty-13"
HAS_LEDGER = os.path.isdir(os.path.join(pipeline.RESEARCH_HOME, "runs", SEED_RUN))
needs_ledger = pytest.mark.skipif(
    not HAS_LEDGER, reason=f"원장 runs/{SEED_RUN} 없음 — 저장소에 원장을 담지 않는다")


def _golden(name: str) -> dict:
    with io.open(os.path.join(FIXTURES, name), encoding="utf-8") as handle:
        return json.load(handle)


def _keys(node: dict) -> set[str]:
    """문서용 `_` 접두 칸은 계약이 아니다 — 픽스처에만 있고 결과에는 없다."""
    return {key for key in node if not key.startswith("_")}


# ══════════════════════════════════════════════════════════════
def test_envelope_rejects_a_field_the_contract_does_not_have():
    with pytest.raises(serialize.ContractDrift):
        serialize.envelope(runId="r", conceptId="c", madeUpField=1)


def test_envelope_fills_every_contract_field_even_when_unused():
    """빠진 칸은 **`null` 이지 없는 칸이 아니다** — 그래야 봉투를 한 번에 못박을 수 있다."""
    out = serialize.envelope(runId="r", conceptId="c")
    assert set(out) == set(serialize.ENVELOPE)
    assert out["market"] is None and out["canvas"] is None


def test_envelope_matches_both_golden_fixtures():
    assert set(serialize.ENVELOPE) == _keys(_golden("full.json"))
    assert set(serialize.ENVELOPE) == _keys(_golden("bm.json"))


# ══════════════════════════════════════════════════════════════
@needs_ledger
def test_full_mode_matches_the_golden_shape():
    out = asyncio.run(pipeline.run_market_research(
        {"mode": "RESCORE", "sourceRun": SEED_RUN, "conceptId": "smoke"},
        "test-envelope-full", 600))
    golden = _golden("full.json")

    assert set(out) == _keys(golden)
    assert out["mode"] == "FULL"
    assert out["canvas"] is None and out["bm"] is None

    assert {s["subject"] for s in out["scorecard"]} == {s["subject"] for s in golden["scorecard"]}
    for row in out["scorecard"]:
        assert set(row) == set(golden["scorecard"][0])
        assert row["state"] in serialize.SCORE_STATES
        assert row["detail"].strip()

    assert set(out["market"]) == set(golden["market"])
    for stage in out["stages"]:
        assert set(stage) == set(golden["stages"][0])
        assert stage["status"] in ("OK", "SKIPPED", "FAILED")
    for item in out["degradations"]:
        assert set(item) == {"stage", "code", "detail"}


@needs_ledger
def test_evidence_carries_exactly_the_contract_keys_and_nothing_else():
    """**allowlist 검사.** 원장의 `슬롯`·`채택`·`연도` 는 나가면 안 된다."""
    out = asyncio.run(pipeline.run_market_research(
        {"mode": "RESCORE", "sourceRun": SEED_RUN, "conceptId": "smoke"},
        "test-envelope-allowlist", 600))
    expected = set(_golden("full.json")["evidence"][0])
    assert out["evidence"], "근거가 0건이면 이 검사는 아무것도 못 본다"
    for item in out["evidence"]:
        assert set(item) == expected
        assert isinstance(item["caveats"], list)
        assert item["grade"] in serialize.GRADES
    ids = [item["id"] for item in out["evidence"]]
    assert len(ids) == len(set(ids)), "근거 id 는 유일해야 한다"


@needs_ledger
def test_market_figures_only_cite_evidence_that_exists():
    out = asyncio.run(pipeline.run_market_research(
        {"mode": "RESCORE", "sourceRun": SEED_RUN, "conceptId": "smoke"},
        "test-envelope-refs", 600))
    known = {item["id"] for item in out["evidence"]}
    for name in ("tam", "sam", "som", "growth", "price"):
        figure = out["market"][name]
        if figure is None:
            continue
        assert set(figure["evidenceIds"]) <= known, f"{name} 이 없는 근거를 인용했다"


# ══════════════════════════════════════════════════════════════
# 사용자가 채운 실행 계획 — 요청에서 컨셉으로 들어가는 문
# ══════════════════════════════════════════════════════════════
def test_plan_material_keeps_only_the_four_cells_the_concept_never_gives():
    """컨셉 계약(입구계약서 §1)이 주지 않는 넷만 받는다.

    수익모델·채널·차별점을 여기서 또 받으면 **가설 4가 이미 사용자 승인을 거친 값**을
    두 번 묻는 것이 된다.
    """
    out = pipeline._plan_material({
        "key_activities": ["예약 통합"], "key_resources": ["결제 연동"],
        "key_partners": ["PG"], "customer_relationship": "자동 알림",
        "revenue_model": "월 구독", "channel": ["아웃바운드"],
    })
    assert set(out) == set(pipeline.PLAN_KEYS)
    assert "revenue_model" not in out and "channel" not in out


def test_empty_plan_values_are_dropped_not_sent_as_empty():
    """⚠ 빈 값을 실어 보내지 않는다.

    빈 배열·빈 문자열을 보내면 「사용자가 안 썼다」와 「사용자가 비웠다」가 같아지고,
    뒷단(`_bm_plan`·컨셉 파생)이 채울 기회를 조용히 뺏는다.
    """
    out = pipeline._plan_material({
        "key_activities": ["", "  "], "key_resources": [],
        "key_partners": ["PG"], "customer_relationship": "   ",
    })
    assert out == {"key_partners": ["PG"]}
    assert pipeline._plan_material(None) == {}


def test_execution_constraints_take_integers_only():
    """부동소수점은 canonical hash 가 거부한다 — 반올림해서 통과시키지 않는다."""
    out = pipeline._plan_constraints(
        {"budget_krw": 5000000, "months": 10.5, "team": 2, "extra": 9})
    assert out == {"budget_krw": 5000000, "team": 2}
    # bool 은 파이썬에서 int 지만 개월 수가 아니다.
    assert pipeline._plan_constraints({"months": True}) == {}


@needs_ledger
def test_user_plan_reaches_the_bm_model_payload(monkeypatch):
    """⭐ **끝에서 끝까지.** 요청에 실은 계획이 실제 BM 모델 payload 까지 간다.

    층마다 따로 통과해도 사이가 끊기면 화면은 그대로 빈다 — 실제로 `execution_constraints`
    가 그렇게 오래 비어 있었다. 그래서 모델 호출을 가로채 **거기 무엇이 도착했는지** 본다.
    """
    seen: dict = {}

    async def fake_flow(source):
        seen["payload"] = source.model_dump(mode="json") if hasattr(source, "model_dump") else {}
        raise RuntimeError("여기까지만 본다 — 모델은 부르지 않는다")

    monkeypatch.setattr("app.research.bm.flow.run_bm_pipeline_flow", fake_flow)

    with pytest.raises(Exception):
        asyncio.run(pipeline.run_market_research({
            "mode": "BM", "sourceRun": SEED_RUN, "conceptId": "beauty-noshow",
            "llmBudget": 1,
            "planMaterial": {"key_partners": ["사용자가 쓴 결제 대행사"],
                             "customer_relationship": "사용자가 쓴 고객 관계"},
            "executionConstraints": {"budget_krw": 7000000, "months": 6, "team": 3},
        }, "test-user-plan", 600))

    snapshot = seen["payload"]["market_join_data"]["concept_snapshot"]
    assert snapshot["key_partners"] == ["사용자가 쓴 결제 대행사"]
    assert snapshot["customer_relationship"] == "사용자가 쓴 고객 관계"
    # 견본이 들고 있던 스텁을 사용자가 이긴다.
    assert seen["payload"]["execution_constraints"]["budget_krw"] == 7000000
    assert seen["payload"]["execution_constraints"]["months"] == 6


@needs_ledger
def test_market_figures_carry_the_factor_ledger_key_for_key():
    """계산식의 항이 **값으로** 나간다 — 산문이 아니라.

    옛 봉투는 「무엇이 관측이고 무엇이 가정인가」를 `assumptions` 문장 안에만 담았다.
    문장은 옮기다 빠지고, 화면은 그것을 다시 한 줄로 이어 붙였다.
    """
    out = asyncio.run(pipeline.run_market_research(
        {"mode": "RESCORE", "sourceRun": SEED_RUN, "conceptId": "smoke"},
        "test-envelope-factors", 600))
    expected = set(_golden("full.json")["market"]["tam"]["factors"][0])

    seen = 0
    for name in ("tam", "sam", "som", "growth"):
        figure = out["market"][name]
        if figure is None:
            continue
        assert isinstance(figure["factors"], list), f"{name}: factors 는 항상 배열이다"
        for factor in figure["factors"]:
            seen += 1
            assert set(factor) == expected, f"{name}: 요인 키 집합이 계약과 다르다"
            assert factor["basis"] in serialize._FACTOR_BASES
            assert factor["name"].strip()
            assert isinstance(factor["sourceDomains"], list)
            assert isinstance(factor["caveats"], list)
            # 관측이라면서 출처가 0곳이면 표가 거짓말을 한다 (자바 계약도 같은 것을 막는다).
            if factor["basis"] == "관측":
                assert factor["sourceCount"] > 0, f"{name}/{factor['name']}"
    assert seen, "요인이 0개면 이 검사는 아무것도 못 본다"


@needs_ledger
def test_factor_note_is_the_whole_basis_not_a_prefix():
    """**자르지 않는다.** 옛 `_SEG_WARN` 은 `basis[:100]` 으로 잘라 문장 한가운데가 끊긴
    채로 화면까지 보냈다("… 두발 미"). 규칙 파일의 서술과 **글자 그대로 같아야** 한다."""
    rules = os.path.join(pipeline.RESEARCH_HOME, "rules", "assumptions.v1.json")
    with io.open(rules, encoding="utf-8") as handle:
        by_role = json.load(handle)["by_role"]

    out = asyncio.run(pipeline.run_market_research(
        {"mode": "RESCORE", "sourceRun": SEED_RUN, "conceptId": "smoke"},
        "test-envelope-untruncated", 600))

    checked = 0
    for name in ("tam", "sam", "som", "growth"):
        figure = out["market"][name]
        if figure is None:
            continue
        for factor in figure["factors"]:
            role = by_role.get(factor["name"])
            # 단가는 규칙이 아니라 **컨셉의 가격 가설**에서 온다 — 대조 대상이 아니다.
            if not role or factor["basis"] == "가설":
                continue
            checked += 1
            assert factor["note"] == role["basis"], f"{name}/{factor['name']} 이 잘렸다"
    assert checked, "규칙에서 온 요인이 0개면 이 검사는 아무것도 못 본다"


@needs_ledger
def test_rescore_spends_nothing():
    out = asyncio.run(pipeline.run_market_research(
        {"mode": "RESCORE", "sourceRun": SEED_RUN, "conceptId": "smoke"},
        "test-envelope-free", 600))
    assert sum(stage["llmCalls"] for stage in out["stages"]) == 0
    assert any(item["code"] == "MODE_RESCORE" for item in out["degradations"])


@needs_ledger
def test_stages_that_did_not_run_say_so_instead_of_vanishing():
    """안 돈 단계를 목록에서 빼면 「안 돌았다」가 「돌았다」로 읽힌다."""
    out = asyncio.run(pipeline.run_market_research(
        {"mode": "RESCORE", "sourceRun": SEED_RUN, "conceptId": "smoke"},
        "test-envelope-skipped", 600))
    names = {stage["name"] for stage in out["stages"]}
    assert {"harness", "dryrun", "collect"} <= names
    assert {item["stage"] for item in out["degradations"]} >= {"harness", "dryrun", "collect"}


# ══════════════════════════════════════════════════════════════
# BM — 경계 파생(층 1)
# ══════════════════════════════════════════════════════════════
def _canvas_items(evidence_id: str) -> list[BMCanvasItem]:
    return [
        BMCanvasItem(
            canvas_cell=cell,
            content=["값"] if index == 0 else [],
            source_labels=["market_size"] if index == 0 else [],
            market_evidence_ids=[evidence_id] if index == 0 else [],
            status=CanvasStatus.PARTIAL, reason="사유")
        for index, cell in enumerate(CanvasCell)
    ]


def test_cell_caveats_are_derived_from_the_evidence_it_cites():
    """**이 프로젝트의 대표 검사.** 모델이 경계를 안 실어도 기계가 끌어온다(판 ㉜-b 0/2)."""
    boundary = "전사 매출 — 시장 매출 아님. 용도는 DART 경로 검증이다."
    evidence = [{"id": "C-F010", "caveats": [boundary]},
                {"id": "C-F999", "caveats": ["인용되지 않은 경계"]}]

    cells = serialize.canvas_cells(_canvas_items("C-F010"), evidence)

    cited = next(c for c in cells if c["marketEvidenceIds"])
    assert boundary in cited["caveats"], "인용한 근거의 경계가 칸에 없다"
    assert "인용되지 않은 경계" not in cited["caveats"], "인용하지 않은 경계까지 끌어오면 안 된다"
    for cell in cells:
        if not cell["marketEvidenceIds"]:
            assert cell["caveats"] == []


def _verified_cell(cell_name: str) -> list[BMCanvasItem]:
    """모델이 **VERIFIED 를 냈다고** 가정한 칸 하나. 근거 인용은 없다.

    파트너 칸에서 실제로 일어날 수 있는 일이다 — 프롬프트 §9 는 「입력 또는 시장분석에
    실제 파트너 정보가 있을 때만 작성」이라고만 하고 PLAN 을 지시하지 않는다.
    """
    return [BMCanvasItem(canvas_cell=cell, content=["사용자가 쓴 값"] if cell.value == cell_name else [],
                         source_labels=["concept_snapshot"] if cell.value == cell_name else [],
                         market_evidence_ids=[],
                         status=CanvasStatus.VERIFIED, reason="사유")
            for cell in CanvasCell]


def test_user_written_plan_is_stamped_plan_not_verified():
    """⭐ **사용자가 쓴 것은 계획이지 관측이 아니다.**

    「꽉 찬 캔버스」가 「검증된 캔버스」로 읽히면 이 제품이 지키려는 선을 넘는다.
    모델에게 다시 부탁하지 않고 기계로 내린다(경계 파생과 같은 수).
    """
    cells = serialize.canvas_cells(_verified_cell("KEY_PARTNERS"), [],
                                   {"KEY_PARTNERS": ["결제 대행사"]})
    partners = next(c for c in cells if c["canvasCell"] == "KEY_PARTNERS")
    assert partners["status"] == "PLAN", "모델이 VERIFIED 를 내도 계획으로 내려야 한다"
    assert serialize.USER_PLAN_CAVEAT in partners["caveats"]


def test_cells_the_user_did_not_fill_keep_the_model_verdict():
    """안 쓴 칸까지 내리면 컨셉 서술이 채운 칸이 「사용자가 썼다」로 표시된다."""
    cells = serialize.canvas_cells(_verified_cell("KEY_PARTNERS"), [],
                                   {"KEY_PARTNERS": ["결제 대행사"]})
    others = [c for c in cells if c["canvasCell"] != "KEY_PARTNERS"]
    assert all(c["status"] == "VERIFIED" for c in others)
    assert all(serialize.USER_PLAN_CAVEAT not in c["caveats"] for c in others)


def test_a_cell_that_cites_market_evidence_is_not_demoted():
    """근거가 붙었으면 판정은 모델과 근거의 몫이다 — 사용자가 썼다고 깎지 않는다."""
    items = _verified_cell("KEY_PARTNERS")
    partners = next(i for i in items if i.canvas_cell.value == "KEY_PARTNERS")
    partners.market_evidence_ids = ["C-F010"]
    cells = serialize.canvas_cells(items, [{"id": "C-F010", "caveats": []}],
                                   {"KEY_PARTNERS": ["결제 대행사"]})
    kept = next(c for c in cells if c["canvasCell"] == "KEY_PARTNERS")
    assert kept["status"] == "VERIFIED"


def test_a_cell_the_model_left_empty_gets_the_users_own_words():
    """⭐ **실측으로 잡은 것.** 입력을 다 받고도 모델이 `content=[]` 를 냈다.

    실스택 스모크에서 `CUSTOMER_RELATIONSHIPS`·`COST_STRUCTURE` 가 그랬다 — payload 에는
    사용자의 문장이 글자 그대로 있었다. 계획 칸에서 모델이 할 일은 창업자가 쓴 계획을
    다시 쓰는 것이 아니다. LLM 왕복에 맡기면 조용히 사라진다.
    """
    items = _verified_cell("__none__")           # 모든 칸이 content=[] 인 상태
    cells = serialize.canvas_cells(items, [], {
        "CUSTOMER_RELATIONSHIPS": ["예약 확인 자동 발송으로 접점 유지"],
        "COST_STRUCTURE": ["예산 7,000,000원", "기간 6개월"],
    })
    relation = next(c for c in cells if c["canvasCell"] == "CUSTOMER_RELATIONSHIPS")
    assert relation["content"] == ["예약 확인 자동 발송으로 접점 유지"]
    # content 가 있으면 출처 라벨도 있어야 한다(자바 계약). 새 라벨은 만들지 않는다.
    assert relation["sourceLabels"] == ["concept_snapshot"]

    cost = next(c for c in cells if c["canvasCell"] == "COST_STRUCTURE")
    assert cost["content"] == ["예산 7,000,000원", "기간 6개월"]
    assert cost["sourceLabels"] == ["execution_constraints"]


def test_model_written_content_is_not_overwritten():
    """모델이 정리해 놓은 것을 뭉개지 않는다 — **비었을 때만** 채운다."""
    items = _verified_cell("KEY_PARTNERS")       # 이 칸에는 모델이 쓴 내용이 있다
    cells = serialize.canvas_cells(items, [], {"KEY_PARTNERS": ["원문 그대로"]})
    partners = next(c for c in cells if c["canvasCell"] == "KEY_PARTNERS")
    assert partners["content"] == ["사용자가 쓴 값"], "모델이 쓴 것을 덮었다"
    assert "원문 그대로" not in partners["content"]


def test_constraint_cell_restates_the_numbers_without_judging():
    """비용 칸은 사용자가 넣은 **숫자를 다시 적을 뿐**이다 — 「부족하다」는 판단문이다."""
    content = pipeline._user_planned_cells({}, {"budget_krw": 7000000, "months": 6, "team": 3})
    assert content["COST_STRUCTURE"] == ["예산 7,000,000원", "기간 6개월", "인원 3명"]
    assert not any("부족" in line for line in content["COST_STRUCTURE"])


def test_canvas_cell_matches_the_golden_key_set():
    cells = serialize.canvas_cells(_canvas_items("C-F010"),
                                   [{"id": "C-F010", "caveats": []}])
    expected = set(_golden("bm.json")["canvas"]["cells"][0])
    assert len(cells) == 9
    for cell in cells:
        assert set(cell) == expected
    assert {cell["canvasCell"] for cell in cells} == {c.value for c in CanvasCell}


def test_bm_block_matches_the_golden_key_set():
    final = BMFinalResult(
        concept_id="c1", decision=BMDecision.CONDITIONAL, confidence="MEDIUM",
        summary="요약", canvas=_canvas_items("C-F010"),
        strengths=["s"], weaknesses=["w"], risks=["r"],
        market_fit_summary="a", consistency_summary="b",
        legal_context_used=False, legal_status="UNVERIFIED")
    analysis = BMAnalysisResult(
        concept_id="c1", concept_name="n", canvas=_canvas_items("C-F010"),
        market_fit_status="PARTIAL", consistency_status="PASS",
        market_fit_summary="a", consistency_summary="b")
    block = serialize.bm(final, analysis)
    golden = _golden("bm.json")["bm"]
    assert set(block) == set(golden)
    assert set(block["legal"]) == set(golden["legal"])


@needs_ledger
def test_bm_mode_derives_caveats_end_to_end(monkeypatch):
    """모델은 스텁이다 — 검사 대상은 **경계가 칸까지 도달하는가**이지 모델 품질이 아니다."""
    captured = {}

    async def _stub_flow(bm_input, **_):
        captured["evidence_ids"] = [
            item["id"] for item in bm_input.market_join_data.evidence_list]
        first = captured["evidence_ids"][0]
        analysis = BMAnalysisResult(
            concept_id=bm_input.concept_id, concept_name="n",
            canvas=_canvas_items(first),
            market_fit_status="PARTIAL", consistency_status="PASS",
            market_fit_summary="a", consistency_summary="b")
        final = BMFinalResult(
            concept_id=bm_input.concept_id, decision=BMDecision.CONDITIONAL,
            confidence="MEDIUM", summary="요약", canvas=analysis.canvas,
            strengths=[], weaknesses=[], risks=[],
            market_fit_summary="a", consistency_summary="b",
            legal_context_used=False, legal_status="UNVERIFIED")
        return {"bm_analysis": analysis, "final_result": final}

    import app.research.bm.flow as flow_module
    monkeypatch.setattr(flow_module, "run_bm_pipeline_flow", _stub_flow)

    out = asyncio.run(pipeline.run_market_research(
        {"mode": "BM", "sourceRun": SEED_RUN, "conceptId": "smoke", "llmBudget": 2},
        "test-envelope-bm", 600))

    assert out["mode"] == "BM"
    assert out["scorecard"] is None and out["market"] is None
    assert set(out) == _keys(_golden("bm.json"))

    by_id = {item["id"]: item for item in out["evidence"]}
    for cell in out["canvas"]["cells"]:
        want = {c for ref in cell["marketEvidenceIds"] for c in by_id[ref]["caveats"]}
        assert want <= set(cell["caveats"]), "인용한 근거의 경계가 칸에 도달하지 않았다"
    assert sum(stage["llmCalls"] for stage in out["stages"]) == 1


def test_layer_two_refuses_a_cell_that_dropped_a_boundary():
    """층 2. 파생이 회귀하면 **결과를 내지 않는다** — 조용한 소실은 출력이 멀쩡해 보인다."""
    cells = [{"canvasCell": "CHANNELS", "marketEvidenceIds": ["C-F010"], "caveats": []}]
    with pytest.raises(serialize.ContractDrift):
        serialize.assert_caveats_reached(
            cells, [{"id": "C-F010", "caveats": ["전사 매출 — 시장 매출 아님"]}])
