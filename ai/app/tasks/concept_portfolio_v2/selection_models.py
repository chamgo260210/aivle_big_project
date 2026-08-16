"""Concept Portfolio V2 선택 이후 Production action 계약."""

from __future__ import annotations

from typing import Any, Literal

from pydantic import Field, model_validator

from app.concept_portfolio_v2.models import (
    CandidateEnvelope,
    CanonicalSeed,
    DeltaLegalResult,
    DownstreamHandoff,
    HypothesisDecision,
    LegalReview,
)

from .models import StrictModel


SelectionAction = Literal[
    "PREPARE_HYPOTHESES",
    "CONFIRM_HYPOTHESES",
    "PROPOSE_ALTERNATIVE",
    "DELTA_LEGAL",
    "BUILD_HANDOFF",
    # 시장 근거로 컨셉을 다듬는다. **새 TaskType 을 만들지 않는다** — 워커·타임아웃 배선이
    # 이 액션 위에 이미 있고, 하나 더 만들면 폴러가 또 하나 늘어난다.
    "REFINE_FROM_MARKET",
    # 수렴한 뒤 **한 번만** 돈다 — 최종값으로 컨셉 서술문을 다시 쓴다. 라운드마다 쓰면
    # 중간값이 남은 문장이 최종 문장 자리에 선다.
    "NARRATE_REFINED",
]
HypothesisType = Literal[
    "TARGET_REGION",
    "REVENUE_MODEL",
    "PRICE",
    "CHANNELS",
    "DIFFERENTIATORS",
    "PRE_MARKET_SOM_SHARE",
    "PRE_MARKET_SOM",
]
HYPOTHESIS_TYPES = tuple(HypothesisType.__args__)


class ProductionBinding(StrictModel):
    projectId: int = Field(gt=0)
    portfolioSelectionId: int = Field(gt=0)
    portfolioConceptId: str = Field(min_length=1, max_length=64)
    marketSeedSnapshotId: str = Field(min_length=1, max_length=64)


class RefinementProposal(StrictModel):
    """한 칸을 이렇게 바꾸자는 제안. **근거 없는 제안은 받지 않는다.**

    ⚠ `beforeText`·`afterText` 는 **표시용 문자열**이다. 드리프트 계약은 이것을 보지 않고
    `currentValue`·`proposedValue` 로만 판정한다 — 표시 문자열로 판정하면 모델이 말만
    바꿔서 계약을 통과시킬 수 있다.
    """

    fieldKey: str = Field(min_length=1, max_length=64)
    currentValue: Any | None = None
    proposedValue: Any
    rationale: str = Field(min_length=1, max_length=600)
    evidenceIds: list[str] = Field(default_factory=list, max_length=20)
    #: 「타깃을 좁혔어요」 — 무엇을 했는지 한 마디. 필드 이름(「대상 고객」)만으로는
    #: 무엇이 왜 바뀌었는지가 안 읽힌다.
    title: str = Field(default="", max_length=30)
    #: 사람이 읽는 값. 값이 목록이면 JSON 문자열이 그대로 화면에 뜨는 것을 막는다.
    beforeText: str = Field(default="", max_length=120)
    afterText: str = Field(default="", max_length=120)
    #: 무엇이 시킨 변경인가. `LEGAL` 이면 근거는 시장 근거가 아니라 조항이다.
    source: Literal["MARKET", "LEGAL"] = "MARKET"
    #: `source == "LEGAL"` 일 때 「법령명 제N조」. 화면이 이것으로 법률 카드에 잇는다.
    legalRef: str | None = Field(default=None, max_length=200)


class NarrativeSegment(StrictModel):
    """최종 컨셉 서술문 한 조각.

    ⚠ **offset 을 층 사이로 넘기지 않는다.** 문자 위치를 주고받으면 정규화·인코딩 한 번에
    어긋나서 엉뚱한 구간이 물든다. 조각으로 끊어 주면 화면은 이어 붙이기만 하면 된다.
    """

    text: str = Field(min_length=1, max_length=600)
    #: 1부터 세는 변경 번호. 안 바뀐 구간은 `None`.
    changeRef: int | None = Field(default=None, ge=1, le=20)


class RefinementMaterial(StrictModel):
    """다듬기 한 라운드가 받는 재료.

    ⚠ `driftRejections` 와 `legalRejections` 는 **직전 라운드의 기각 사유**다. 이것을
    돌려주지 않으면 모델이 같은 제안을 3라운드 내내 반복한다 — 라운드 상한만 태우고
    아무것도 안 고친 채 끝난다.
    """

    gateReasons: list[dict[str, Any]] = Field(default_factory=list, max_length=40)
    canvas: dict[str, Any] | None = None
    marketEvidence: list[dict[str, Any]] = Field(default_factory=list, max_length=200)
    frozenFields: list[str] = Field(default_factory=list, max_length=40)
    refinableFields: dict[str, str] = Field(default_factory=dict, max_length=40)
    driftRejections: list[dict[str, Any]] = Field(default_factory=list, max_length=40)
    legalRejections: list[dict[str, Any]] = Field(default_factory=list, max_length=40)
    #: 직전 라운드에서 **사람이 읽어 보고 넘긴** 제안 — `{fieldKey, title, afterText, rationale}`.
    #: ⚠ 위 둘과 **다른 종류**다. 저 둘은 「규칙이 막았다」이고 이것은 「사람이 원하지 않았다」다.
    #: 되돌려 주지 않으면 「다른 제안 받기」를 눌러도 모델이 **같은 것을 다시 낸다** —
    #: 라운드 상한 3을 태우고 아무것도 못 고친 채 끝난다(2026-08-15 에 이 칸을 만든 이유).
    #: ⚠ **왜 넘겼는지는 담기지 않는다.** 화면이 묻지 않기 때문이다 — 지어내면 안 된다.
    userDeclined: list[dict[str, Any]] = Field(default_factory=list, max_length=40)
    #: 법률 검토가 낸 소견 — `{lawName, articleReference, findingType, topic, text}`.
    #: 이것이 없으면 「법이 막은 표현」을 고칠 길이 없어 다듬기가 시장 근거만 본다.
    legalFindings: list[dict[str, Any]] = Field(default_factory=list, max_length=40)
    round: int = Field(default=1, ge=1, le=3)


class NarrationMaterial(StrictModel):
    """수렴 뒤 서술문을 쓸 때 받는 재료. **채택된 변경만** 온다 — 기각분은 최종 컨셉이 아니다."""

    changes: list[dict[str, Any]] = Field(default_factory=list, max_length=20)


class ConceptPortfolioSelectionActionInput(StrictModel):
    action: SelectionAction
    expectedHypothesisRevision: int = Field(default=0, ge=0)
    seed: CanonicalSeed | None = None
    selectedCandidate: CandidateEnvelope | None = None
    baseLegalReview: LegalReview | None = None
    hypotheses: list[HypothesisDecision] = Field(default_factory=list, max_length=7)
    edits: dict[HypothesisType, Any] = Field(default_factory=dict, max_length=7)
    confirmAll: bool = False
    hypothesisType: HypothesisType | None = None
    rejectedValue: Any | None = None
    proposalVersion: int | None = Field(default=None, ge=2, le=20)
    approvedDeltaLegalResults: list[DeltaLegalResult] = Field(default_factory=list, max_length=5)
    productionBinding: ProductionBinding | None = None
    refinementMaterial: RefinementMaterial | None = None
    narrationMaterial: NarrationMaterial | None = None
    #: 다듬기가 고쳤지만 가설도 BM 계획도 아닌 칸(`targetUsers`·`featureSet`).
    #: ⚠ **컨셉 원본 candidate 는 안 덮는다** — 캐노니컬 해시와 계보가 흔들린다.
    #: 이 오버레이는 `BUILD_HANDOFF` 가 만드는 **시드 스냅샷에만** 얹힌다.
    refinementOverlay: dict[str, Any] | None = None

    @model_validator(mode="after")
    def action_payload(self):
        if self.action == "PREPARE_HYPOTHESES":
            self._require(self.seed, self.selectedCandidate, self.baseLegalReview)
        elif self.action == "CONFIRM_HYPOTHESES":
            if len(self.hypotheses) != 7 or not self.confirmAll:
                raise ValueError("7개 가정의 명시적 전체 확인이 필요합니다")
        elif self.action == "PROPOSE_ALTERNATIVE":
            self._require(self.selectedCandidate, self.hypothesisType, self.proposalVersion)
            if self.rejectedValue is None:
                raise ValueError("거절한 가정 값이 필요합니다")
        elif self.action == "DELTA_LEGAL":
            self._require(self.seed, self.selectedCandidate)
            if len(self.hypotheses) != 7:
                raise ValueError("Delta Legal에는 7개 가정이 필요합니다")
        elif self.action == "REFINE_FROM_MARKET":
            # 컨셉과 시장 재료 없이는 다듬을 것이 없다. 게이트 사유가 비어 있는 경우는
            # 정상이다 — 「고칠 것 없음」으로 첫 라운드에 끝난다.
            self._require(self.selectedCandidate, self.refinementMaterial)
        elif self.action == "NARRATE_REFINED":
            # 컨셉과 채택된 변경이 없으면 쓸 것이 없다.
            self._require(self.selectedCandidate, self.narrationMaterial)
        elif self.action == "BUILD_HANDOFF":
            self._require(
                self.seed,
                self.selectedCandidate,
                self.baseLegalReview,
                self.productionBinding,
            )
            if len(self.hypotheses) != 7:
                raise ValueError("Handoff에는 확정된 7개 가정이 필요합니다")
        return self

    @staticmethod
    def _require(*values: object) -> None:
        if any(value is None for value in values):
            raise ValueError("action 입력 계약이 불완전합니다")


class ConceptPortfolioSelectionActionResult(StrictModel):
    contract: Literal["concept-portfolio-v2-selection-action-result-v1"] = (
        "concept-portfolio-v2-selection-action-result-v1"
    )
    schemaVersion: Literal["1.0"] = "1.0"
    action: SelectionAction
    hypotheses: list[HypothesisDecision] = Field(default_factory=list, max_length=7)
    alternative: HypothesisDecision | None = None
    deltaLegalResult: DeltaLegalResult | None = None
    handoff: DownstreamHandoff | None = None
    refinementProposals: list[RefinementProposal] = Field(default_factory=list, max_length=20)
    # 계약이 기각한 제안과 사유. **버리지 않는다** — 다음 라운드 입력으로 되먹인다.
    driftRejections: list[dict[str, Any]] = Field(default_factory=list, max_length=40)
    #: 최종 컨셉 서술문. `NARRATE_REFINED` 만 채운다.
    narrative: list[NarrativeSegment] = Field(default_factory=list, max_length=60)
    marketSeedSnapshotHash: str | None = Field(default=None, pattern=r"^sha256:[0-9a-f]{64}$")
