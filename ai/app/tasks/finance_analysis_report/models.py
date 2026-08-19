from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class FinanceAnalysisReportInput(StrictModel):
    snapshotId: str = Field(min_length=1, max_length=64)
    snapshotHash: str = Field(pattern=r"^sha256:[0-9a-f]{64}$")
    sourceMarketResearchVersionId: int | None
    sourceBusinessModelVersionId: int | None
    sourceTechOpsSnapshotId: str | None = Field(default=None, min_length=1, max_length=64)
    # 백엔드(FinancialAnalysisService:66)는 계보를 sourceBinding 으로 항상 같이 보낸다.
    # StrictModel 이 extra="forbid" 라 이 칸을 선언하지 않으면 요청이 통째로 400 이 되고,
    # 워커의 safeReason 이 그것을 AI_SERVICE_UNAVAILABLE 로 접어 **매번 fallback 보고서**가 나온다.
    # 보고서 본문은 deterministicResult 만 쓰므로 값은 받아두고 프롬프트에는 넣지 않는다.
    sourceBinding: dict | None = None
    deterministicResult: dict


class FinanceAnalysisReportResult(StrictModel):
    headline: str = Field(min_length=1, max_length=300)
    findings: list[str] = Field(min_length=1, max_length=5)
    cautions: list[str] = Field(min_length=1, max_length=5)
    recommendedActions: list[str] = Field(min_length=1, max_length=5)
    disclaimer: str = Field(min_length=1, max_length=500)
    source: Literal["AI_GENERATED_REPORT"]
    providerStatus: Literal["SUCCEEDED"]
    # OpenAI strict structured output requires every property to be required.
    # The success contract therefore carries an explicit JSON null instead of
    # an optional/defaulted field that the provider may omit.
    safeFailureReason: None
