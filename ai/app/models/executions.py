from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class InternalExecutionRequestV1(StrictModel):
    contractVersion: str
    taskType: str
    taskSchemaVersion: str
    taskRunId: str = Field(min_length=1, max_length=128)
    taskAttemptId: str = Field(min_length=1, max_length=128)
    correlationId: str = Field(min_length=1, max_length=128)
    deadlineAt: str
    canonicalInputHash: str = Field(pattern=r"^sha256:[0-9a-f]{64}$")
    locale: str
    input: dict[str, Any]


class InternalExecutionSuccessResponseV1(StrictModel):
    contractVersion: str
    taskType: str
    taskSchemaVersion: str
    taskRunId: str
    taskAttemptId: str
    correlationId: str
    canonicalInputHash: str
    resultSchemaVersion: str
    result: dict[str, Any]
    warnings: list[dict[str, Any]]
    provenance: list[dict[str, Any]]
    usage: dict[str, Any] | None
