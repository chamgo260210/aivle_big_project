"""Frozen Core public API만 사용하는 선택 이후 thin facade."""

from __future__ import annotations

from typing import Any

from pydantic import ValidationError

from app.concept_portfolio_v2 import ConceptPortfolioEngine, ProviderGateway, ProviderMode
from app.concept_portfolio_v2.adapters import CurrentDownstreamAdapter
from app.concept_portfolio_v2.models import HypothesisDecision
from app.concept_portfolio_v2.snapshot_hash import production_compatible_snapshot_hash
from app.tasks.concept_hypothesis_alternative import execute_concept_hypothesis_alternative

from .selection_models import (
    HYPOTHESIS_TYPES,
    ConceptPortfolioSelectionActionInput,
    ConceptPortfolioSelectionActionResult,
)


class ConceptPortfolioSelectionActionFacade:
    def __init__(self, *, engine: ConceptPortfolioEngine | None = None):
        self.engine = engine or ConceptPortfolioEngine(
            mode=ProviderMode.LIVE,
            gateway=ProviderGateway(ProviderMode.LIVE),
        )

    async def run(
        self, task_input: dict[str, Any] | ConceptPortfolioSelectionActionInput
    ) -> ConceptPortfolioSelectionActionResult:
        value = (
            task_input
            if isinstance(task_input, ConceptPortfolioSelectionActionInput)
            else ConceptPortfolioSelectionActionInput.model_validate(task_input)
        )
        if value.action == "PREPARE_HYPOTHESES":
            hypotheses = self.engine.build_or_load_current_hypothesis_contract(value.selectedCandidate)
            hypotheses = await self.engine.resolve_hypothesis_semantics(hypotheses, use_final=False)
            _require_seven(hypotheses)
            return ConceptPortfolioSelectionActionResult(action=value.action, hypotheses=hypotheses)
        if value.action == "CONFIRM_HYPOTHESES":
            hypotheses = self.engine.confirm_hypotheses(
                value.hypotheses,
                edits=value.edits,
                confirm_all_proposed=True,
            )
            _require_seven(hypotheses)
            return ConceptPortfolioSelectionActionResult(action=value.action, hypotheses=hypotheses)
        if value.action == "PROPOSE_ALTERNATIVE":
            raw = await execute_concept_hypothesis_alternative({
                "hypothesisType": value.hypothesisType,
                "rejectedValue": value.rejectedValue,
                "proposalVersion": value.proposalVersion,
                "candidate": value.selectedCandidate.candidate,
            })
            alternative = HypothesisDecision(
                hypothesisType=raw["hypothesisType"],
                proposedValue=raw["proposedValue"],
                source=raw["source"],
                decisionStatus=raw["decisionStatus"],
                proposalVersion=raw["proposalVersion"],
            )
            return ConceptPortfolioSelectionActionResult(action=value.action, alternative=alternative)
        if value.action == "DELTA_LEGAL":
            result = await self.engine.review_delta_legal(
                value.seed, value.selectedCandidate, value.hypotheses
            )
            hypotheses = (
                self.engine.mark_delta_legal_reviewed(value.hypotheses, result)
                if result.approved
                else value.hypotheses
            )
            return ConceptPortfolioSelectionActionResult(
                action=value.action,
                hypotheses=hypotheses,
                deltaLegalResult=result,
            )

        if value.action == "REFINE_FROM_MARKET":
            # LLM 1회. **판정은 여기서 하지 않는다** — 계약(드리프트) 판정은
            # `app.validation.drift` 가, 법률은 `DELTA_LEGAL` 이 한다. 한 프롬프트에
            # 묶으면 모델이 자기 제안을 자기가 통과시킨다.
            from app.tasks.concept_refinement import propose_refinements
            from app.validation import drift

            material = value.refinementMaterial.model_dump(mode="json")
            # 계약을 **입력에 실어** 준다. 모델에게 「무엇을 건드리면 안 되는지」를 말해 주면
            # 버려질 제안이 줄고, 그만큼 라운드가 덜 든다.
            material.setdefault("frozenFields", [])
            if not material["frozenFields"]:
                material["frozenFields"] = list(drift.FROZEN_FIELDS)
            if not material.get("refinableFields"):
                material["refinableFields"] = dict(drift.REFINABLE_FIELDS)
            # 계약 판정은 **평평한 dict** 위에서 돈다 — 모델 객체에는 `.get` 이 없다.
            concept = value.selectedCandidate.candidate.model_dump(mode="json")
            raw = await propose_refinements(material, concept)
            # 계약으로 거른 뒤에 돌려준다 — 기각분은 Java 가 다음 라운드로 되먹인다.
            passed, rejected = drift.filter_proposals(raw, concept)
            return ConceptPortfolioSelectionActionResult(
                action=value.action,
                refinementProposals=[_display_safe(item) for item in passed],
                driftRejections=rejected,
            )

        if value.action == "NARRATE_REFINED":
            # 수렴 뒤 **한 번**. LLM 1회. 판정(조각이 정말 그 값을 담았나)은 Java 가 한다 —
            # 두 곳에서 보면 규칙이 갈린다.
            from app.tasks.concept_refinement import narrate_refined

            concept = value.selectedCandidate.candidate.model_dump(mode="json")
            segments = await narrate_refined(concept, value.narrationMaterial.changes)
            return ConceptPortfolioSelectionActionResult(
                action=value.action,
                narrative=[_segment_safe(item) for item in segments],
            )

        legal = value.baseLegalReview.model_copy(update={
            "deltaLegalReviews": [
                item.model_dump(mode="json") for item in value.approvedDeltaLegalResults
            ]
        })
        handoff = CurrentDownstreamAdapter().build(
            value.seed,
            value.selectedCandidate.candidateId,
            value.selectedCandidate.candidate,
            value.hypotheses,
            legal,
        )
        if handoff.compatibility != "PASS":
            from .service import ConceptPortfolioProductionContractError
            raise ConceptPortfolioProductionContractError(
                "AI_RESULT_INVALID",
                validation_fields=[{
                    "path": "result.handoff.compatibility",
                    "expectedType": "PASS",
                    "category": "domain_invariant",
                }],
            )
        market = handoff.marketAnalysisSeedSnapshot
        binding = value.productionBinding
        source_hash = production_compatible_snapshot_hash({
            "canonicalSeed": value.seed,
            "selectedCandidate": value.selectedCandidate,
            "finalHypotheses": value.hypotheses,
            "baseLegalReview": value.baseLegalReview,
            "approvedDeltaLegalResults": value.approvedDeltaLegalResults,
        })
        # 다듬기가 고쳤지만 가설도 BM 계획도 아닌 칸을 **여기서** 얹는다.
        # ⚠ 해시를 계산하기 **전**이어야 한다. 뒤에 얹으면 Java 의 재계산과 어긋나 저장이 막힌다.
        _apply_refinement_overlay(market, value.refinementOverlay)
        market.update({
            "snapshotId": binding.marketSeedSnapshotId,
            "projectId": binding.projectId,
            "selectionId": binding.portfolioSelectionId,
            "conceptId": binding.portfolioConceptId,
            "sourceSnapshotHash": source_hash,
        })
        snapshot_hash = production_compatible_snapshot_hash(market)
        rebound = handoff.model_copy(update={"marketAnalysisSeedSnapshot": market})
        return ConceptPortfolioSelectionActionResult(
            action=value.action,
            hypotheses=value.hypotheses,
            handoff=rebound,
            marketSeedSnapshotHash=snapshot_hash,
        )


#: 오버레이가 얹힐 수 있는 자리 — `시드 스냅샷의 칸 이름 → (묶음, 키)`.
#: ⚠ 이 둘뿐이다. 가설 7종은 `CONFIRM_HYPOTHESES` 로, BM 4칸은 계획 저장소로 간다.
_OVERLAY_SLOTS = {
    "targetUsers": ("identity", "targetUsers"),
    "featureSet": ("solution", "featureSet"),
}


def _apply_refinement_overlay(market: dict[str, Any], overlay: dict[str, Any] | None) -> None:
    """다듬기 결과를 **시드 스냅샷에만** 얹는다.

    ⚠ **컨셉 원본 candidate 는 안 덮는다.** 캐노니컬 해시와 계보가 흔들린다 — 바뀐 것은
    가설과 계획이고, 최종 컨셉은 그 둘이 얹힌 시드다.
    """
    if not overlay:
        return
    selected = market.get("selectedConcept")
    if not isinstance(selected, dict):
        return
    for field, (group, key) in _OVERLAY_SLOTS.items():
        if field not in overlay:
            continue
        target = selected.get(group)
        if isinstance(target, dict):
            target[key] = overlay[field]


#: 제안이 가질 수 있는 칸. 모델이 없는 칸을 덧붙여도 라운드 전체가 죽지 않게 여기서 자른다.
_PROPOSAL_KEYS = ("fieldKey", "currentValue", "proposedValue", "rationale", "evidenceIds",
                  "title", "beforeText", "afterText", "source", "legalRef")


def _display_safe(proposal: dict[str, Any]) -> dict[str, Any]:
    """표시용 칸을 안전하게 만든다.

    ⚠ **값(`proposedValue`)은 손대지 않는다** — 계약이 이미 판정한 것이다. 여기서 다듬는 것은
    사람이 읽는 문자열뿐이라, 모델이 한 칸을 빠뜨리거나 모르는 낱말을 넣어도 라운드가
    통째로 `AI_RESULT_INVALID` 로 죽지 않게만 한다.
    """
    value = {key: proposal[key] for key in _PROPOSAL_KEYS if key in proposal}
    for key, limit in (("title", 30), ("beforeText", 120), ("afterText", 120)):
        text = value.get(key)
        value[key] = str(text)[:limit] if isinstance(text, (str, int, float)) else ""
    if value.get("source") not in {"MARKET", "LEGAL"}:
        value["source"] = "MARKET"
    ref = value.get("legalRef")
    value["legalRef"] = str(ref)[:200] if isinstance(ref, str) and ref else None
    return value


def _segment_safe(segment: dict[str, Any]) -> dict[str, Any]:
    """서술문 한 조각. 빈 조각과 범위 밖 참조는 여기서 떨군다 — 판정은 Java 몫이다."""
    text = segment.get("text")
    ref = segment.get("changeRef")
    return {
        "text": str(text)[:600] if isinstance(text, str) and text.strip() else " ",
        "changeRef": ref if isinstance(ref, int) and 1 <= ref <= 20 else None,
    }


def _require_seven(values: list[HypothesisDecision]) -> None:
    if len(values) != 7 or tuple(item.hypothesisType for item in values) != HYPOTHESIS_TYPES:
        raise ValueError("Frozen Core 7개 가정 계약이 일치하지 않습니다")


async def execute_concept_portfolio_v2_selection_action(
    task_input: dict[str, Any], *, engine: ConceptPortfolioEngine | None = None
) -> dict[str, Any]:
    from .service import ConceptPortfolioProductionContractError
    try:
        result = await ConceptPortfolioSelectionActionFacade(engine=engine).run(task_input)
        return result.model_dump(mode="json")
    except ConceptPortfolioProductionContractError:
        raise
    except ValidationError as failure:
        fields = []
        for issue in failure.errors()[:12]:
            fields.append({
                "path": "result." + ".".join(str(part) for part in issue.get("loc", ())),
                "expectedType": "valid contract value",
                "category": str(issue.get("type", "invalid"))[:80],
            })
        raise ConceptPortfolioProductionContractError(
            "AI_RESULT_INVALID", validation_fields=fields
        ) from failure
    except (AttributeError, KeyError, TypeError, ValueError) as failure:
        raise ConceptPortfolioProductionContractError(
            "AI_RESULT_INVALID",
            validation_fields=[{
                "path": "result.handoff",
                "expectedType": "valid production handoff",
                "category": failure.__class__.__name__,
            }],
        ) from failure
