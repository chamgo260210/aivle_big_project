# -*- coding: utf-8 -*-
"""이관된 BM 파이프라인의 **동작** 검사 — 노트북 셀 10·12·14·26.

계약 동일성은 `test_bm_contract_parity.py` 가 본다. 여기서 보는 것은 **판정 규칙이
같이 옮겨졌는가**다. 스키마만 맞고 판정표가 어긋나면 컴파일도 파싱도 안 깨진다.

⚠ 네트워크 0회. 모델 호출은 스텁으로 가른다 — 진짜 호출은 유료이고, 유료 실행은
   이 검사의 목적이 아니다.
"""
from __future__ import annotations

import asyncio
import os
import sys
from types import SimpleNamespace

import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.research.bm import analyze  # noqa: E402
from app.research.bm.analyze import (  # noqa: E402
    run_bm_analysis,
    validate_canvas_source_labels,
)
from app.research.bm.contracts import (  # noqa: E402
    BMAnalysisResult,
    BMCanvasItem,
    BMDecision,
    CanvasCell,
    CanvasStatus,
    ConceptSnapshot,
    GrowthRateData,
    LegalContext,
    MarketJoinData,
    MarketSizeData,
    PriceAnalysisData,
)
from app.research.bm.finalize import finalize_bm_analysis  # noqa: E402
from app.research.bm.flow import run_bm_pipeline_flow  # noqa: E402
from app.research.bm.handoff import (  # noqa: E402
    build_financial_handoff,
    get_required_financial_inputs,
)
from app.research.bm.normalize import (  # noqa: E402
    create_bm_analysis_input,
    resolve_bm_input,
)


def _market(**over) -> MarketJoinData:
    base = dict(
        concept_id="c1",
        concept_snapshot=ConceptSnapshot(concept_name="컨셉", revenue_model="구독"),
        market_size=MarketSizeData(tam=100.0, sam=10.0, unit="KRW"),
        growth_rate=GrowthRateData(value=7.85, unit="%/년"),
        competitor_analysis=[],
        price_analysis=PriceAnalysisData(price_min=1.0, price_base=2.0,
                                         price_max=3.0, currency="KRW"),
        demand_evidence=[],
        market_size_calculation={},
        evidence_list=[{"id": "C-F001"}],
    )
    base.update(over)
    return MarketJoinData(**base)


def _analysis(*, fit="PASS", consistency="PASS", blocked_cell=False,
              evidence_ids=(), labels=("market_size",), content=("x",)) -> BMAnalysisResult:
    cells = list(CanvasCell)
    canvas = []
    for index, cell in enumerate(cells):
        first = index == 0
        canvas.append(BMCanvasItem(
            canvas_cell=cell,
            content=list(content) if first else [],
            source_labels=list(labels) if first else [],
            market_evidence_ids=list(evidence_ids) if first else [],
            status=(CanvasStatus.BLOCKED if (blocked_cell and first)
                    else CanvasStatus.PARTIAL),
            reason="테스트",
        ))
    return BMAnalysisResult(
        concept_id="c1", concept_name="컨셉", canvas=canvas,
        market_fit_status=fit, consistency_status=consistency,
        market_fit_summary="", consistency_summary="",
    )


# ══════════════════════════════ 정규화 ══════════════════════════════
def test_concept_id_mismatch_stops_before_the_model():
    """3자 일치는 **모델 호출 전에** 막힌다 — 어긋난 채 들어가면 260초를 태우고 죽는다."""
    with pytest.raises(ValueError):
        create_bm_analysis_input(
            market_data=_market(),
            legal_data=LegalContext(concept_id="다른-컨셉"),
        )


def test_resolve_drops_legal_context():
    """`ResolvedBMInput` 에 법률이 없어야 한다 — 핵심 판정은 시장 데이터만 본다."""
    source = create_bm_analysis_input(
        market_data=_market(),
        legal_data=LegalContext(concept_id="c1", status="BLOCKED", summary="막힘"),
    )
    resolved = resolve_bm_input(source)
    assert "legal" not in resolved.model_dump(mode="json")
    assert "막힘" not in str(resolved.model_dump(mode="json"))


def test_execution_constraints_reach_the_model_payload():
    """비용 구조 칸의 **유일한 원천**이다.

    빠뜨려도 아무것도 안 깨진다 — 프롬프트 §8 이 「예산·기간·비용 정보가 전혀 없으면
    content=[]」 이라 모델이 지시대로 빈 칸을 낸다. 그래서 그것이 정상처럼 보인다.
    실제로 `pipeline._bm` 이 이 인자를 안 넘겨 계획 칸이 오래 비어 있었다.
    """
    source = create_bm_analysis_input(
        market_data=_market(),
        execution_constraints={"budget_krw": 5000000, "months": 10, "team": 2})
    payload = resolve_bm_input(source).model_dump(mode="json")

    assert payload["execution_constraints"] == {
        "budget_krw": 5000000, "months": 10, "team": 2}
    # 부동소수점이 섞이면 canonical hash 가 런타임에 거부한다(CLAUDE.md §5-2).
    assert all(isinstance(v, int) for v in payload["execution_constraints"].values())


def test_concept_snapshot_extra_fields_survive_to_the_model():
    """계획 5칸의 재료는 `concept_snapshot` 의 **확장 필드**로 간다.

    `extra="allow"` 가 언젠가 `forbid`/`ignore` 로 바뀌면 핵심 활동·자원·파트너·고객 관계가
    **조용히** 빈다 — 모델은 「입력에 없다」로 읽고 규칙대로 빈 칸을 낸다. 그래서 불변식이다.
    """
    snapshot = ConceptSnapshot(
        concept_name="컨셉", revenue_model="구독",
        key_activities=["예약 채널 통합 운영"],
        key_resources=["예약 데이터 통합 처리"],
        key_partners=["예약 플랫폼"],
        customer_relationship="구독 유지 중심의 자동 알림 운영")
    source = create_bm_analysis_input(market_data=_market(concept_snapshot=snapshot))
    payload = resolve_bm_input(source).model_dump(mode="json")

    reached = payload["market_join_data"]["concept_snapshot"]
    assert reached["key_activities"] == ["예약 채널 통합 운영"]
    assert reached["key_resources"] == ["예약 데이터 통합 처리"]
    assert reached["key_partners"] == ["예약 플랫폼"]
    assert reached["customer_relationship"] == "구독 유지 중심의 자동 알림 운영"


# ══════════════════════════════ 판정표 ══════════════════════════════
@pytest.mark.parametrize("fit,consistency,blocked,expected", [
    ("PASS", "PASS", False, BMDecision.PASS),
    ("PARTIAL", "PASS", False, BMDecision.CONDITIONAL),
    ("PASS", "PARTIAL", False, BMDecision.CONDITIONAL),
    ("FAIL", "PASS", False, BMDecision.REVISION_REQUIRED),
    ("PASS", "FAIL", False, BMDecision.REVISION_REQUIRED),
    ("PASS", "PASS", True, BMDecision.REVISION_REQUIRED),
])
def test_bm_decision_table_without_legal(fit, consistency, blocked, expected):
    resolved = resolve_bm_input(create_bm_analysis_input(market_data=_market()))
    final = finalize_bm_analysis(
        bm_analysis=_analysis(fit=fit, consistency=consistency, blocked_cell=blocked),
        resolved=resolved, legal_context=None)
    assert final.decision is expected
    assert final.legal_context_used is False
    assert final.legal_status == "UNVERIFIED"
    assert final.confidence == "MEDIUM"


@pytest.mark.parametrize("legal_status,fit,expected_decision,expected_confidence", [
    ("BLOCKED", "PASS", BMDecision.BLOCKED, "HIGH"),
    ("CONDITIONAL", "PASS", BMDecision.CONDITIONAL, "HIGH"),
    ("CONDITIONAL", "FAIL", BMDecision.REVISION_REQUIRED, "MEDIUM"),
    ("PASS", "PASS", BMDecision.PASS, "HIGH"),
])
def test_legal_only_adjusts_the_final_state(legal_status, fit,
                                            expected_decision, expected_confidence):
    """법률은 **최종 상태와 설명**만 건드린다. 시장 판정 자체는 그대로 남아야 한다."""
    resolved = resolve_bm_input(create_bm_analysis_input(market_data=_market()))
    analysis = _analysis(fit=fit)
    final = finalize_bm_analysis(
        bm_analysis=analysis, resolved=resolved,
        legal_context=LegalContext(concept_id="c1", status=legal_status,
                                   summary="법률 요약", risks=["r"],
                                   required_actions=["a"]))
    assert final.decision is expected_decision
    assert final.confidence == expected_confidence
    assert final.legal_context_used is True
    assert final.market_fit_summary == analysis.market_fit_summary
    assert final.legal_risks == ["r"] and final.required_legal_actions == ["a"]


def test_conditional_legal_appends_rather_than_replaces_a_failing_summary():
    resolved = resolve_bm_input(create_bm_analysis_input(market_data=_market()))
    final = finalize_bm_analysis(
        bm_analysis=_analysis(fit="FAIL"), resolved=resolved,
        legal_context=LegalContext(concept_id="c1", status="CONDITIONAL"))
    assert final.summary.startswith("시장 적합성 또는 BM 내부 구조에")
    assert "법률·규제 조건의 확인이 필요합니다." in final.summary


# ══════════════════════════════ 검증기 ══════════════════════════════
def test_unlisted_source_label_is_dropped_and_content_is_cleared():
    """출처 없는 내용은 **남기지 않는다** — 근거 없이 나타난 문장이 캔버스에 서면 안 된다."""
    result = validate_canvas_source_labels(
        _analysis(labels=("made_up_label",), evidence_ids=("C-F001",)))
    first = result.canvas[0]
    assert first.source_labels == []
    assert first.content == []
    assert first.market_evidence_ids == []
    assert first.status is CanvasStatus.UNVERIFIED
    assert "Canvas 내용의 입력 출처 라벨" in first.missing_evidence


def test_allowed_label_survives_and_content_is_kept():
    result = validate_canvas_source_labels(
        _analysis(labels=("market_size", "made_up_label")))
    assert result.canvas[0].source_labels == ["market_size"]
    assert result.canvas[0].content == ["x"]


def _계획칸_라벨없음(cell_name: str) -> BMAnalysisResult:
    """그 칸만 «내용은 있는데 라벨이 없는» 상태로 만든다."""
    base = _analysis()
    canvas = []
    for item in base.canvas:
        if str(item.canvas_cell) == cell_name:
            item = item.model_copy(update={"content": ["1인분 정량 레시피 개발"],
                                           "source_labels": []})
        canvas.append(item)
    return base.model_copy(update={"canvas": canvas})


def test_계획_칸의_문장은_라벨이_없어도_안_지운다():
    """★ 실측: 성공 3회 중 2회 **사용자가 쓴 계획 문장이 통째로 사라졌다.**

    모델이 라벨을 안 붙인 것은 모델의 실수이지 「그 문장이 근거 없다」는 뜻이 아니다 —
    계획 5칸의 내용은 사용자가 직접 쓴 것이고 출처가 무엇인지 우리가 이미 안다.
    """
    result = validate_canvas_source_labels(_계획칸_라벨없음("KEY_ACTIVITIES"))
    cell = next(i for i in result.canvas if str(i.canvas_cell) == "KEY_ACTIVITIES")
    assert cell.content == ["1인분 정량 레시피 개발"], "사용자가 쓴 문장이 사라지면 안 된다"
    assert cell.source_labels == ["concept_snapshot"], "자바 계약이 라벨 0건을 거부한다"


def test_비용_구조는_실행_제약에서_왔다고_적는다():
    """예산·기간은 `execution_constraints` 가 유일한 원천이다 — 컨셉 서술이 아니다."""
    result = validate_canvas_source_labels(_계획칸_라벨없음("COST_STRUCTURE"))
    cell = next(i for i in result.canvas if str(i.canvas_cell) == "COST_STRUCTURE")
    assert cell.source_labels == ["execution_constraints"]


def test_관측_칸은_라벨이_없으면_여전히_지운다():
    """관측 칸에서 라벨을 못 붙였다는 것은 **실제로 시장 근거가 없다**는 뜻이다.

    그 빈칸이 곧 이 단계의 산출(「아직 당신 말뿐인 칸」)이라 살리면 안 된다.
    """
    result = validate_canvas_source_labels(_계획칸_라벨없음("CHANNELS"))
    cell = next(i for i in result.canvas if str(i.canvas_cell) == "CHANNELS")
    assert cell.content == []
    assert cell.status is CanvasStatus.UNVERIFIED


# ══════════════════════════════ 흐름 (LLM 스텁) ══════════════════════════════
class _StubResponses:
    def __init__(self, result):
        self._result = result
        self.calls = []

    async def parse(self, **kwargs):
        self.calls.append(kwargs)

        class _R:
            output_parsed = self._result
        return _R()


class _StubClient:
    def __init__(self, result):
        self.responses = _StubResponses(result)


def test_flow_calls_the_model_once_and_filters_hallucinated_evidence():
    source = create_bm_analysis_input(market_data=_market())
    client = _StubClient(_analysis(evidence_ids=("C-F001", "C-없는근거")))

    out = asyncio.run(run_bm_pipeline_flow(source, client=client, model="stub-model"))

    assert len(client.responses.calls) == 1, "모델 호출은 정확히 1회다"
    assert out["bm_analysis"].canvas[0].market_evidence_ids == ["C-F001"]
    assert out["final_result"].concept_id == "c1"


def test_model_returning_a_different_concept_id_is_rejected():
    resolved = resolve_bm_input(create_bm_analysis_input(market_data=_market()))
    wrong = _analysis().model_copy(update={"concept_id": "다른-컨셉"})
    with pytest.raises(ValueError):
        asyncio.run(run_bm_analysis(resolved=resolved, client=_StubClient(wrong)))


def test_unparsed_response_is_an_error_not_an_empty_canvas():
    """구조화 파싱 실패를 빈 캔버스로 삼키지 않는다 — 조용한 실패가 시끄러운 실패보다 나쁘다."""
    resolved = resolve_bm_input(create_bm_analysis_input(market_data=_market()))
    with pytest.raises(RuntimeError):
        asyncio.run(run_bm_analysis(resolved=resolved, client=_StubClient(None)))


def test_구조화_응답이_안_오면_다시_묻지_않는다():
    """`None` 은 스키마 문제가 아니라 provider 문제다 — 재요청은 돈만 두 배로 쓴다."""
    resolved = resolve_bm_input(create_bm_analysis_input(market_data=_market()))
    client = _StubClient(None)
    with pytest.raises(RuntimeError):
        asyncio.run(run_bm_analysis(resolved=resolved, client=client))
    assert len(client.responses.calls) == 1


def test_온도를_고정해서_부른다():
    """제품 정책은 온도 0.1 고정인데 이 모듈만 밖에 있었다 — 흔들림의 가장 큰 몫이다."""
    resolved = resolve_bm_input(create_bm_analysis_input(market_data=_market()))
    client = _StubClient(_analysis())
    asyncio.run(run_bm_analysis(resolved=resolved, client=client, model="stub-model"))
    assert client.responses.calls[0]["temperature"] == 0.1


def test_생각의_양을_주면_온도를_안_보낸다(monkeypatch):
    """★ 추론 모델(gpt-5.x)은 **온도와 `reasoning_effort` 를 같이 못 받는다.**

    2026-08-15 실측: `effort=none` 이면 온도가 200 이고 `low` 이상이면 400 이다.
    둘 다 보내면 BM 이 **호출 즉시 죽는다** — 이 모듈은 `providers/structured.py` 의
    사다리를 안 타므로(자기 `AsyncOpenAI` 를 만든다) 여기서 갈라야 한다.
    """
    monkeypatch.setenv("BM_REASONING_EFFORT", "low")   # ⚠ 상수가 아니라 «환경변수»를 재야 실제 배선을 잰다
    resolved = resolve_bm_input(create_bm_analysis_input(market_data=_market()))
    client = _StubClient(_analysis())
    asyncio.run(run_bm_analysis(resolved=resolved, client=client, model="stub-model"))
    sent = client.responses.calls[0]
    assert sent["reasoning"] == {"effort": "low"}
    assert "temperature" not in sent, "온도를 같이 보내면 400 이다"


def test_생각을_끄면_온도가_돌아온다(monkeypatch):
    """`none` 은 예외다 — 추론이 꺼지므로 옛 샘플링 인자를 다시 받는다."""
    monkeypatch.setenv("BM_REASONING_EFFORT", "none")
    resolved = resolve_bm_input(create_bm_analysis_input(market_data=_market()))
    client = _StubClient(_analysis())
    asyncio.run(run_bm_analysis(resolved=resolved, client=client, model="stub-model"))
    sent = client.responses.calls[0]
    assert sent["reasoning"] == {"effort": "none"} and sent["temperature"] == 0.1


class _RejectsTemperature:
    """온도를 안 받는 모델. 첫 호출만 400 을 내고, 그다음은 받는다."""

    def __init__(self, result):
        self._result, self.calls = result, []

    async def parse(self, **kwargs):
        self.calls.append(kwargs)
        if "temperature" in kwargs:
            raise ValueError(
                "Error code: 400 - unsupported parameter: 'temperature' is not supported")
        return SimpleNamespace(output_parsed=self._result)


def test_온도를_거절하는_모델을_실행_중에_배운다(monkeypatch):
    """모델 목록을 손으로 관리하지 않는다 — 목록은 반드시 낡는다.

    ⚠ 그 밖의 400(스키마·길이)까지 재시도하면 **엉뚱한 데 돈을 쓴다.** 아래 시험이 그 경계다.
    """
    monkeypatch.setattr(analyze, "_NO_TEMPERATURE", set())
    resolved = resolve_bm_input(create_bm_analysis_input(market_data=_market()))
    stub = _RejectsTemperature(_analysis())
    client = SimpleNamespace(responses=stub)
    asyncio.run(run_bm_analysis(resolved=resolved, client=client, model="새-추론모델"))
    assert len(stub.calls) == 2, "한 번 배우고 한 번만 다시 보낸다"
    assert "temperature" not in stub.calls[1]
    assert "새-추론모델" in analyze._NO_TEMPERATURE


def test_샘플링과_무관한_400_은_다시_안_보낸다(monkeypatch):
    monkeypatch.setattr(analyze, "_NO_TEMPERATURE", set())
    resolved = resolve_bm_input(create_bm_analysis_input(market_data=_market()))

    class _Broken:
        def __init__(self):
            self.calls = []

        async def parse(self, **kwargs):
            self.calls.append(kwargs)
            raise ValueError("Error code: 400 - context_length_exceeded")

    stub = _Broken()
    with pytest.raises(ValueError):
        asyncio.run(run_bm_analysis(resolved=resolved, client=SimpleNamespace(responses=stub),
                                    model="stub-model"))
    # ⚠ 9칸 재요청이 그 위에서 한 번 더 부른다 — 온도 재시도는 **안** 일어난다는 뜻이다.
    assert all("temperature" in call for call in stub.calls)


class _StubFailsOnce:
    """첫 호출은 9칸을 못 맞춰 터지고, 두 번째는 제대로 낸다."""

    def __init__(self, result):
        self._result = result
        self.calls = []

    async def parse(self, **kwargs):
        self.calls.append(kwargs)
        if len(self.calls) == 1:
            raise ValueError("BM Canvas 9개 칸을 각각 정확히 한 번 포함해야 합니다.")

        class _R:
            output_parsed = self._result
        return _R()


def test_9칸을_못_맞추면_한_번만_다시_묻는다():
    """실측: 시도 6회 중 3회가 9칸 미충족으로 통째로 실패했고, TaskRun 재시도로 통과했다.

    fail-closed 는 유지한다(빈 칸을 지어내지 않는다). 다만 **한 판을 두 번 태우는 대신**
    같은 실행 안에서 빠진 칸을 알려 주고 한 번만 더 묻는다.
    """
    resolved = resolve_bm_input(create_bm_analysis_input(market_data=_market()))
    client = _StubClient(None)
    client.responses = _StubFailsOnce(_analysis())

    out = asyncio.run(run_bm_analysis(resolved=resolved, client=client, model="stub-model"))

    assert len(client.responses.calls) == 2, "정확히 한 번만 더 묻는다"
    assert out.concept_id == "c1"
    # 재요청에는 빠진 칸을 알려 주는 말이 붙는다 — 그냥 다시 부르는 것이 아니다.
    assert any("9개 칸" in str(m.get("content", ""))
               for m in client.responses.calls[1]["input"])


def test_두_번째도_9칸을_못_맞추면_실패한다():
    """fail-closed — 무한 재요청으로 돈을 태우지 않는다."""

    class _알실패:
        def __init__(self):
            self.calls = []

        async def parse(self, **kwargs):
            self.calls.append(kwargs)
            raise ValueError("BM Canvas 9개 칸을 각각 정확히 한 번 포함해야 합니다.")

    resolved = resolve_bm_input(create_bm_analysis_input(market_data=_market()))
    client = _StubClient(None)
    client.responses = _알실패()
    with pytest.raises(ValueError):
        asyncio.run(run_bm_analysis(resolved=resolved, client=client, model="stub-model"))
    assert len(client.responses.calls) == 2


# ══════════════════════════════ handoff ══════════════════════════════
@pytest.mark.parametrize("revenue_model,expected", [
    ("제품 판매", ["price_base", "unit_cost"]),
    ("월 구독", ["price_base"]),
    ("SaaS", ["price_base"]),
    ("중개 수수료", ["price_base"]),
    ("광고", []),
    (None, []),
])
def test_required_financial_inputs_by_revenue_model(revenue_model, expected):
    assert get_required_financial_inputs(revenue_model) == expected


def test_handoff_is_partial_while_unit_cost_is_unknown():
    """`unit_cost` 는 시장조사가 만들 수 없는 값이다 — 없는 것을 없다고 표시하고 넘긴다."""
    market = _market(concept_snapshot=ConceptSnapshot(concept_name="컨셉",
                                                      revenue_model="제품 판매"))
    resolved = resolve_bm_input(create_bm_analysis_input(market_data=market))
    final = finalize_bm_analysis(bm_analysis=_analysis(), resolved=resolved,
                                 legal_context=None)
    handoff = build_financial_handoff(final_result=final, resolved=resolved)
    assert handoff.missing_financial_inputs == ["unit_cost"]
    assert handoff.handoff_status == "PARTIAL"
    assert handoff.tam == 100.0 and handoff.market_growth_rate == 7.85


def test_handoff_is_blocked_when_legal_blocks():
    resolved = resolve_bm_input(create_bm_analysis_input(market_data=_market()))
    final = finalize_bm_analysis(
        bm_analysis=_analysis(), resolved=resolved,
        legal_context=LegalContext(concept_id="c1", status="BLOCKED"))
    handoff = build_financial_handoff(final_result=final, resolved=resolved)
    assert handoff.handoff_status == "BLOCKED"
