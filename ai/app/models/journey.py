from typing import Annotated, Any, Literal

from pydantic import BaseModel, ConfigDict, Field, StringConstraints


class StrictResult(BaseModel):
    model_config = ConfigDict(extra="forbid")


NonBlankMarketingText = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1)]


class IdeaInterpretationResult(StrictResult):
    originalSourceSummary: str
    normalizedDescription: str
    facts: list[str]
    assumptions: list[str]
    constraints: list[str]
    openQuestions: list[str]
    readiness: Literal["UNDER_SPECIFIED", "APPROPRIATE", "OVER_SPECIFIED"]
    warnings: list[str]
    evidenceNeeds: list[str]


class LegalReviewResult(StrictResult):
    status: Literal[
        "PASS", "PASS_WITH_CONDITIONS", "REVISION_REQUIRED", "PROHIBITED",
        "INSUFFICIENT_INFORMATION", "EXPERT_REVIEW_REQUIRED",
    ]
    summary: str
    issues: list[str]
    conditions: list[str]
    prohibitedElements: list[str]
    researchNeeds: list[str]
    sourceVerified: Literal[False]
    disclaimer: str


class ConceptCandidate(StrictResult):
    name: str
    oneLineSummary: str
    targetCustomer: str
    problem: str
    solution: str
    valueProposition: str
    revenueModel: str
    keyFeatures: list[str]
    differentiators: list[str]
    assumptions: list[str]
    risks: list[str]


class ConceptGenerationResult(StrictResult):
    concepts: list[ConceptCandidate]


class QuickAssessmentItem(StrictResult):
    conceptVersionId: int
    market: int
    customerValue: int
    feasibility: int
    differentiation: int
    revenuePotential: int
    legalRisk: int
    overallScore: float
    summary: str
    strengths: list[str]
    weaknesses: list[str]


class QuickAssessmentResult(StrictResult):
    assessments: list[QuickAssessmentItem]


class DetailedAnalysisItem(StrictResult):
    conceptVersionId: int
    marketAnalysis: str
    customerAnalysis: str
    businessModelAnalysis: str
    operationAnalysis: str
    riskAnalysis: str
    recommendation: str
    assumptions: list[str]
    researchNeeds: list[str]


class DetailedAnalysisResult(StrictResult):
    analyses: list[DetailedAnalysisItem]


class RoleAndContext(StrictResult):
    role: str
    situation: str
    goals: list[str]
    constraints: list[str]


class ProblemAndNeeds(StrictResult):
    problems: list[str]
    unmetNeeds: list[str]
    desiredOutcomes: list[str]


class BehaviorAndDecision(StrictResult):
    currentBehavior: list[str]
    decisionCriteria: list[str]
    barriers: list[str]
    informationSources: list[str]


class PersonaCardItem(StrictResult):
    name: str
    shortLabel: str
    roleAndContext: RoleAndContext
    problemAndNeeds: ProblemAndNeeds
    behaviorAndDecision: BehaviorAndDecision
    interviewFocus: list[str]


class PersonaCardGenerationResult(StrictResult):
    personas: list[PersonaCardItem]


class PersonaInterviewMessage(StrictResult):
    category: Literal["ROLE_AND_CONTEXT", "PROBLEM_AND_NEEDS", "BEHAVIOR_AND_DECISION"]
    question: str
    answer: str


class PersonaInterviewResult(StrictResult):
    messages: list[PersonaInterviewMessage]


class InterviewSynthesisResult(StrictResult):
    commonThemes: list[str]
    conflictingViews: list[str]
    criticalNeeds: list[str]
    decisionBarriers: list[str]
    implications: list[str]
    researchNeeds: list[str]


class PersonaMessage(StrictResult):
    personaId: int = Field(strict=True)
    personaName: NonBlankMarketingText
    message: NonBlankMarketingText
    rationale: NonBlankMarketingText


class ChannelPlanItem(StrictResult):
    channel: NonBlankMarketingText
    objective: NonBlankMarketingText
    message: NonBlankMarketingText


class LandingHero(StrictResult):
    headline: NonBlankMarketingText
    subheadline: NonBlankMarketingText
    cta: NonBlankMarketingText


class MarketingGenerationResult(StrictResult):
    positioning: NonBlankMarketingText
    coreMessage: NonBlankMarketingText
    slogans: list[str] = Field(min_length=1)
    personaMessages: list[PersonaMessage] = Field(min_length=1)
    channelPlan: list[ChannelPlanItem] = Field(min_length=1)
    socialCopies: list[str] = Field(min_length=1)
    emailCopies: list[str] = Field(default_factory=list)
    landingHero: LandingHero
    assumptions: list[str]
    warnings: list[str]


class PersonaFit(StrictResult):
    personaId: int = Field(strict=True)
    personaName: NonBlankMarketingText
    fit: Literal["LOW", "MEDIUM", "HIGH"]
    rationale: NonBlankMarketingText


class MarketingComparisonItem(StrictResult):
    assetId: int = Field(strict=True)
    assetVersionId: int = Field(strict=True)
    assetType: Literal["POSITIONING", "CORE_MESSAGE", "SLOGAN", "SOCIAL_COPY", "LANDING_HERO", "EMAIL_COPY", "CHANNEL_PLAN"]
    personaFit: list[PersonaFit] = Field(min_length=1)
    strengths: list[NonBlankMarketingText] = Field(min_length=1)
    risks: list[NonBlankMarketingText] = Field(min_length=1)
    recommendedContexts: list[NonBlankMarketingText] = Field(min_length=1)
    selectionSuggestion: NonBlankMarketingText


class MarketingComparisonResult(StrictResult):
    comparisons: list[MarketingComparisonItem] = Field(min_length=1)


class FinalReportResult(StrictResult):
    executiveSummary: NonBlankMarketingText
    idea: dict[str, Any]
    legalReview: dict[str, Any]
    selectedConcept: dict[str, Any]
    analysis: dict[str, Any]
    personaInsights: dict[str, Any]
    marketingStrategy: dict[str, Any]
    facts: list[NonBlankMarketingText]
    assumptions: list[NonBlankMarketingText]
    researchNeeds: list[NonBlankMarketingText]
    risks: list[NonBlankMarketingText]
    decision: Literal["GO", "CONDITIONAL_GO", "REWORK", "HOLD", "STOP"]
    decisionReasons: list[NonBlankMarketingText] = Field(min_length=1)
    nextActions: list[NonBlankMarketingText] = Field(min_length=1)
