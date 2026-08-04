from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


LegalCategory = Literal[
    "BUSINESS_REGISTRATION", "LICENSE_AND_PERMIT", "PRIVACY_AND_DATA",
    "TERMS_AND_CONTRACT", "INTELLECTUAL_PROPERTY", "CONSUMER_PROTECTION",
    "ADVERTISING_AND_MARKETING", "LABOR_AND_EMPLOYMENT", "INDUSTRY_SPECIFIC",
    "TAX_AND_FINANCIAL",
]


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class RouteDecision(StrictModel):
    routeId: str = Field(min_length=1)
    status: Literal["APPLIES", "POSSIBLE", "NOT_APPLICABLE", "UNKNOWN"]
    evidenceQuotes: list[str]
    reason: str = Field(min_length=1)
    confidence: float = Field(ge=0, le=1)


class MissingInformation(StrictModel):
    question: str = Field(min_length=1)
    relatedRouteIds: list[str]


class RoutingResult(StrictModel):
    routes: list[RouteDecision]
    additionalRouteCandidates: list[str]
    missingInformation: list[MissingInformation]


class Screening(StrictModel):
    citationId: str = Field(min_length=1)
    role: Literal["REQUIREMENT", "SANCTION", "SCOPE", "SUPPORTING", "EXCLUDE"]
    plainSummary: str
    whyRelevant: str


class ScreeningResult(StrictModel):
    screenings: list[Screening]


class LegalRouteResult(StrictModel):
    routeId: str = Field(min_length=1)
    topic: str = Field(min_length=1)
    status: Literal["APPLIES", "POSSIBLE", "NOT_APPLICABLE", "UNKNOWN"]
    evidenceQuotes: list[str]
    reason: str = Field(min_length=1)
    categories: list[LegalCategory]


class LegalEvidence(StrictModel):
    evidenceId: str = Field(min_length=1)
    routeId: str = Field(min_length=1)
    category: LegalCategory
    registryVersion: str = Field(min_length=1)
    lawName: str = Field(min_length=1)
    article: str = Field(min_length=1)
    title: str
    role: Literal["REQUIREMENT", "SANCTION", "SCOPE", "SUPPORTING"]
    plainSummary: str = Field(min_length=1)
    whyRelevant: str = Field(min_length=1)
    excerpt: str = Field(min_length=1)
    effectiveDate: str | None
    lawUrl: str = Field(min_length=1)
    verifiedAt: str = Field(min_length=1)


class LegalReasoning(StrictModel):
    category: LegalCategory
    inputBasis: list[str]
    regulatoryArea: str = Field(min_length=1)
    obligation: str = Field(min_length=1)
    consequence: str = Field(min_length=1)
    requiredAction: str = Field(min_length=1)
    evidenceIds: list[str]


class LegalFinding(StrictModel):
    category: LegalCategory
    applicability: Literal["APPLIES", "POSSIBLE"]
    summary: str = Field(min_length=1)
    evidenceIds: list[str]
    reasoning: LegalReasoning


class LegalSourcePipelineResult(StrictModel):
    taskType: Literal["IDEA_LEGAL_PRECHECK", "CONCEPT_LEGAL_VALIDATION"]
    sourceStatus: Literal["SOURCE_COMPLETE", "SOURCE_PARTIAL", "REGISTRY_GAP"]
    registryVersion: str = Field(min_length=1)
    routes: list[LegalRouteResult]
    findings: list[LegalFinding]
    evidence: list[LegalEvidence]
    requiredUserInputs: list[MissingInformation]
    sourceWarnings: list[str]
