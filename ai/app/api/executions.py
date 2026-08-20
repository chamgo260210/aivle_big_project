import asyncio
import hashlib
import json
import os
import re
import logging
from datetime import datetime, timezone
from typing import Any

from fastapi import APIRouter, Request
from fastapi.responses import JSONResponse

from pydantic import ValidationError

from app.models.executions import InternalExecutionRequestV1, InternalExecutionSuccessResponseV1
from app.providers import ProviderFailure
from app.canonical_json import canonical_input_hash
from app import demo_replay


router = APIRouter(prefix="/internal/v1/ai", tags=["Internal AI Executions"])
logger = logging.getLogger(__name__)
TASK_TYPES = {
    "IDEA_BRIEF_DERIVATION",
    "CONCEPT_PORTFOLIO_V2_RUN",
    "CONCEPT_PORTFOLIO_V2_CONTINUE",
    "CONCEPT_PORTFOLIO_V2_SELECTION_ACTION",
    "CONCEPT_CANDIDATE", "CONCEPT_DISTINCTNESS_JUDGE",
    "CONCEPT_LEGAL_REVIEW",
    "CONCEPT_REDESIGN",
    "CONCEPT_HYPOTHESIS_ALTERNATIVE",
    "CONCEPT_DELTA_LEGAL_REVIEW",
    "TECH_OPS_PROPOSAL",
    "TECH_OPS_ADVISORY",
    "FINANCE_ESTIMATE",
    "FINANCE_ANALYSIS_REPORT",
    "LAUNCH_TECHNOLOGY_READINESS",
    "LAUNCH_OPERATIONS_READINESS",
    "LAUNCH_READINESS",
    "MARKETING_CONTENT_GENERATION",
    "MARKETING_STRATEGY_GENERATION",
    "FINAL_BUSINESS_PROPOSAL_GENERATION",
    "FINAL_BUSINESS_PROPOSAL_REVIEW",
    "MARKETING_VISUAL_GENERATION",
    "MARKET_RESEARCH",
    # 사업 검증 — FULL+BM 을 한 실행으로 잇는다. 봉투는 MARKET_RESEARCH 와 같다.
    "BUSINESS_VALIDATION",
    "TWIN_SURVEY",
    "TWIN_STIMULUS_DRAFT",
    "MARKET_INTERVIEW",
}


def internal_error(correlation_id: str, code: str, reason: str, status_code: int,
                   retryable: bool, task_run_id: str | None = None,
                   task_attempt_id: str | None = None,
                   validation_fields: list[dict[str, str]] | None = None,
                   retry_after_ms: int | None = None) -> JSONResponse:
    detail: dict[str, Any] = {"reason": reason}
    if validation_fields:
        detail["fields"] = validation_fields[:12]
    if retry_after_ms is not None:
        detail["retryAfterMs"] = retry_after_ms
    return JSONResponse(status_code=status_code, content={"error": {
        "code": code, "message": "Internal execution request could not be processed.",
        "correlationId": correlation_id, "taskRunId": task_run_id,
        "taskAttemptId": task_attempt_id, "retryable": retryable,
        "details": [detail],
    }})


def safe_validation_fields(failure: ValidationError, prefix: str = "input") -> list[dict[str, str]]:
    expected_types = {
        "missing": "required", "int_type": "integer", "int_parsing": "integer",
        "string_type": "string", "list_type": "array", "dict_type": "object",
        "model_type": "object", "literal_error": "allowed literal", "extra_forbidden": "no extra field",
        "bool_type": "boolean",
    }
    fields = []
    for issue in failure.errors()[:12]:
        location = ".".join(str(part) for part in issue.get("loc", ()))
        path = f"{prefix}.{location}" if location else prefix
        category = str(issue.get("type", "invalid"))[:80]
        fields.append({
            "path": path[:200],
            "expectedType": expected_types.get(category, "valid contract value"),
            "category": category,
        })
    return fields


def validate_text_contents(task_input: dict[str, Any]) -> str | None:
    """`textContents` 봉투 검사.

    ⚠ **`MARKET_RESEARCH` 는 더 이상 이 봉투를 안 쓴다.** main 이 제품 경로를
    `conceptSnapshotJson` 문자열로 갈아탔고(`product_pipeline.py:236`), 그 입력에 이 검사를
    걸면 전부 400 이 된다. 그래서 지금 이 함수를 타는 것은 **`BUSINESS_VALIDATION` 뿐**이다.
    두 TaskType 의 입력 계약이 실제로 다르다 — 하나로 묶지 말 것.
    """
    contents = task_input.get("textContents")
    if not isinstance(contents, list) or not 1 <= len(contents) <= 64:
        return "FIELD_CONSTRAINT_VIOLATION"
    total_chunks = 0
    for content in contents:
        if not isinstance(content, dict) or set(content) != {"contentKey", "contentType", "language", "totalCharacters", "contentHash", "chunks"}:
            return "UNKNOWN_FIELD"
        if content["contentType"] != "TEXT" or content["language"] != "ko-KR":
            return "FIELD_CONSTRAINT_VIOLATION"
        chunks = content["chunks"]
        if not isinstance(chunks, list) or not 1 <= len(chunks) <= 64:
            return "CHUNK_COUNT_EXCEEDED"
        total_chunks += len(chunks)
        joined = ""
        for expected, chunk in enumerate(chunks):
            if chunk.get("index") != expected:
                return "CHUNK_SEQUENCE_INVALID"
            text = chunk.get("text")
            if not isinstance(text, str) or not text or len(text) > 16384 or chunk.get("characterCount") != len(text):
                return "FIELD_CONSTRAINT_VIOLATION"
            if chunk.get("chunkHash") != "sha256:" + hashlib.sha256(text.encode("utf-8")).hexdigest():
                return "HASH_MISMATCH"
            joined += text
        if content.get("totalCharacters") != len(joined) or content.get("contentHash") != "sha256:" + hashlib.sha256(joined.encode("utf-8")).hexdigest():
            return "HASH_MISMATCH"
    return "CHUNK_COUNT_EXCEEDED" if total_chunks > 64 else None


def canonical_hash(body: InternalExecutionRequestV1) -> str:
    input_value = body.input
    if body.taskType == "MARKETING_VISUAL_GENERATION":
        input_value = dict(body.input)
        input_value.pop("resolvedSourceImage", None)
    return canonical_input_hash(
        contract_version=body.contractVersion,
        task_type=body.taskType,
        task_schema_version=body.taskSchemaVersion,
        locale=body.locale,
        input_value=input_value,
    )


@router.post("/executions", response_model=InternalExecutionSuccessResponseV1)
async def execute(request: Request, body: InternalExecutionRequestV1):
    correlation = request.headers.get("X-Correlation-Id") or body.correlationId
    token = os.getenv("AI_INTERNAL_SERVICE_TOKEN", "")
    authorization = request.headers.get("Authorization", "")
    if not authorization:
        return internal_error(correlation, "UNAUTHORIZED_INTERNAL_CALL", "SERVICE_TOKEN_MISSING", 401, False)
    if not token or authorization != f"Bearer {token}":
        return internal_error(correlation, "UNAUTHORIZED_INTERNAL_CALL", "SERVICE_TOKEN_INVALID", 401, False)
    if correlation != body.correlationId:
        return internal_error(correlation, "INVALID_REQUEST", "HEADER_BODY_CORRELATION_MISMATCH", 400, False,
                              body.taskRunId, body.taskAttemptId)
    if body.contractVersion != "1.0":
        return internal_error(correlation, "UNSUPPORTED_CONTRACT_VERSION", "CONTRACT_VERSION_UNSUPPORTED", 422, False,
                              body.taskRunId, body.taskAttemptId)
    if body.taskSchemaVersion != "1.0":
        return internal_error(correlation, "UNSUPPORTED_TASK_SCHEMA_VERSION", "TASK_SCHEMA_VERSION_UNSUPPORTED", 422, False,
                              body.taskRunId, body.taskAttemptId)
    if body.taskType not in TASK_TYPES:
        return internal_error(correlation, "UNSUPPORTED_TASK_TYPE", "TASK_TYPE_UNSUPPORTED", 422, False,
                              body.taskRunId, body.taskAttemptId)
    try:
        if not re.fullmatch(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z", body.deadlineAt):
            raise ValueError
        deadline = datetime.fromisoformat(body.deadlineAt.replace("Z", "+00:00"))
        if deadline.tzinfo is None or deadline <= datetime.now(timezone.utc):
            raise ValueError
    except ValueError:
        return internal_error(correlation, "DEADLINE_EXCEEDED", "REQUEST_DEADLINE_EXCEEDED", 504, True,
                              body.taskRunId, body.taskAttemptId)
    try:
        calculated_hash = canonical_hash(body)
    except ValueError:
        return internal_error(correlation, "INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", 400, False,
                              body.taskRunId, body.taskAttemptId)
    if calculated_hash != body.canonicalInputHash:
        return internal_error(correlation, "INVALID_REQUEST", "HASH_MISMATCH", 400, False,
                              body.taskRunId, body.taskAttemptId)
    if body.taskType == "BUSINESS_VALIDATION":
        # 사업 검증만 textContents 봉투를 쓴다 (MarketResearchInputFactory 가 그렇게 싼다).
        # ⚠ MARKET_RESEARCH 는 여기 안 온다 — 제품 경로가 conceptSnapshotJson 으로 갈아탔다.
        reason = validate_text_contents(body.input)
        if reason:
            return internal_error(correlation, "INVALID_REQUEST", reason, 400, False,
                                  body.taskRunId, body.taskAttemptId)
        text = "\n".join(chunk["text"] for content in body.input["textContents"] for chunk in content["chunks"])
        source_keys = [content["contentKey"] for content in body.input["textContents"]]
    elif body.taskType == "CONCEPT_PORTFOLIO_V2_RUN":
        from app.tasks.concept_portfolio_v2.models import ConceptPortfolioProductionInput
        try:
            portfolio_input = ConceptPortfolioProductionInput.model_validate(body.input)
        except ValidationError as failure:
            return internal_error(correlation, "INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION",
                                  400, False, body.taskRunId, body.taskAttemptId,
                                  safe_validation_fields(failure))
        text = json.dumps(portfolio_input.model_dump(mode="json"), ensure_ascii=False,
                          sort_keys=True, separators=(",", ":"))
        source_keys = ["concept-portfolio-v2-input"]
    elif body.taskType == "CONCEPT_PORTFOLIO_V2_CONTINUE":
        from app.tasks.concept_portfolio_v2 import ConceptPortfolioContinuationInput
        try:
            continuation_input = ConceptPortfolioContinuationInput.model_validate(body.input)
        except ValidationError as failure:
            return internal_error(correlation, "INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION",
                                  400, False, body.taskRunId, body.taskAttemptId,
                                  safe_validation_fields(failure))
        text = json.dumps(continuation_input.model_dump(mode="json"), ensure_ascii=False,
                          sort_keys=True, separators=(",", ":"))
        source_keys = ["concept-portfolio-v2-continuation-input"]
    elif body.taskType == "CONCEPT_PORTFOLIO_V2_SELECTION_ACTION":
        from app.tasks.concept_portfolio_v2 import ConceptPortfolioSelectionActionInput
        try:
            selection_input = ConceptPortfolioSelectionActionInput.model_validate(body.input)
        except ValidationError as failure:
            return internal_error(correlation, "INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION",
                                  400, False, body.taskRunId, body.taskAttemptId,
                                  safe_validation_fields(failure))
        text = json.dumps(selection_input.model_dump(mode="json"), ensure_ascii=False,
                          sort_keys=True, separators=(",", ":"))
        source_keys = ["concept-portfolio-v2-selection-action-input"]
    elif body.taskType in {"CONCEPT_CANDIDATE", "CONCEPT_DISTINCTNESS_JUDGE", "CONCEPT_LEGAL_REVIEW", "CONCEPT_REDESIGN", "CONCEPT_HYPOTHESIS_ALTERNATIVE", "CONCEPT_DELTA_LEGAL_REVIEW"}:
        text = json.dumps(body.input, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
        source_keys = ["concept-factory-input"]
    elif body.taskType == "MARKETING_CONTENT_GENERATION":
        text = json.dumps(body.input, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
        source_hash = body.input.get("source", {}).get("hash", body.input.get("source", {}).get("sourceSnapshotHash", "unknown"))
        source_keys = [f"finalized-planning:{source_hash}"]
    elif body.taskType == "MARKETING_STRATEGY_GENERATION":
        text = json.dumps(body.input, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
        source_keys = [f"marketing-strategy-source:{body.input.get('sourceManifestHash', 'unknown')}"]
    elif body.taskType in {"FINAL_BUSINESS_PROPOSAL_GENERATION", "FINAL_BUSINESS_PROPOSAL_REVIEW"}:
        text = json.dumps(body.input, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
        source_keys = [f"final-business-proposal:{body.input.get('sourceManifestHash', 'unknown')}"]
    elif body.taskType == "MARKETING_VISUAL_GENERATION":
        from app.tasks.marketing_visual.models import MarketingVisualInput
        try:
            visual_input = MarketingVisualInput.model_validate(body.input)
        except ValidationError as failure:
            return internal_error(correlation, "INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION",
                                  400, False, body.taskRunId, body.taskAttemptId,
                                  safe_validation_fields(failure))
        text = json.dumps(visual_input.model_dump(mode="json"), ensure_ascii=False,
                          sort_keys=True, separators=(",", ":"))
        source_keys = [
            f"marketing-content:{visual_input.marketingContentId}",
            f"marketing-revision:{visual_input.marketingRevisionId}",
            f"source-artifact:{visual_input.sourceImage.artifactId}",
        ]
    elif body.taskType == "IDEA_BRIEF_DERIVATION":
        from app.tasks.idea_brief.models import IdeaBriefDerivationInput
        try:
            idea_brief_input = IdeaBriefDerivationInput.model_validate(body.input)
        except ValidationError as failure:
            return internal_error(correlation, "INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION",
                                  400, False, body.taskRunId, body.taskAttemptId,
                                  safe_validation_fields(failure))
        text = json.dumps(idea_brief_input.model_dump(mode="json"), ensure_ascii=False,
                          sort_keys=True, separators=(",", ":"))
        source_keys = ["idea-brief-input"]
    else:
        text = json.dumps(body.input, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
        source_keys = ["pipeline-input"]
    generated_at = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
    provenance = {"category": "AI_PROPOSAL", "statementKey": "interpretation-1", "sourceKeys": source_keys,
                  "externalSourceReferences": [], "generatedAt": generated_at, "verificationNeeded": True}
    execution_warnings: list[dict[str, Any]] = []
    # ⚠ **시연 재생 — 기본은 꺼져 있다.** `AI_DEMO_REPLAY_DIR` 가 설정된 경우에만 탄다.
    #   느린 유료 호출 한 곳만 녹화본으로 바꾸고, 봉투 조립과 그 아래(백엔드·DB·계보
    #   검증)는 전부 평소대로 돈다. 자세한 제약은 app/demo_replay.py 주석 참조.
    replayed = demo_replay.load(body.taskType)
    if replayed is not None:
        await asyncio.sleep(demo_replay.delay_seconds())
        return InternalExecutionSuccessResponseV1(
            contractVersion="1.0", taskType=body.taskType, taskSchemaVersion="1.0",
            taskRunId=body.taskRunId, taskAttemptId=body.taskAttemptId,
            correlationId=body.correlationId, canonicalInputHash=body.canonicalInputHash,
            resultSchemaVersion="1.0", result=replayed, warnings=execution_warnings,
            provenance=[provenance], usage=None)
    try:
        if body.taskType == "BUSINESS_VALIDATION":
            # 사업 검증은 시장조사(FULL)와 BM 을 **한 실행**으로 잇는다. 새 엔진이 아니라
            # 기존 파이프라인을 두 번 부르고 봉투를 합치는 오케스트레이션이다.
            # 봉투는 MARKET_RESEARCH 와 같고 `mode` 만 `VALIDATION` 이다.
            # ⚠ 이 경로는 `pipeline.py` 를 직접 부른다 — 제품 경로(`product_pipeline`)의
            #   워크스페이스 격리·원장 아티팩트를 안 탄다. 화면은 지금 이걸 안 쓴다.
            from app.validation import execute_business_validation
            budget = (deadline - datetime.now(timezone.utc)).total_seconds()
            result = await execute_business_validation(body.input, body.taskAttemptId, budget)
        elif body.taskType == "MARKET_INTERVIEW":
            # 시장 인터뷰도 다단계다 — n 명 수집(1인 1셀) + 주제 코딩 1회. 남은 deadline 을
            # 예산으로 넘기면 오케스트레이터가 코딩 몫을 떼어 두고 수집에 쓴다.
            from app.interview import execute_market_interview
            budget = (deadline - datetime.now(timezone.utc)).total_seconds()
            result = await execute_market_interview(body.input, budget)
        elif body.taskType == "CONCEPT_PORTFOLIO_V2_RUN":
            from app.tasks.concept_portfolio_v2 import (
                ConceptPortfolioProductionContractError,
                execute_concept_portfolio_v2,
            )
            from app.tasks.concept_portfolio_v2.progress_sender import (
                progress_sender_from_environment,
            )
            try:
                async with progress_sender_from_environment(
                    task_run_id=body.taskRunId,
                    task_attempt_id=body.taskAttemptId,
                    correlation_id=correlation,
                ) as progress:
                    result = await execute_concept_portfolio_v2(
                        body.input,
                        event_sink=progress.emit if progress.enabled else None,
                    )
            except ConceptPortfolioProductionContractError as failure:
                logger.warning(
                    "CPV2 production result contract invalid taskRunId=%s taskAttemptId=%s correlationId=%s",
                    body.taskRunId,
                    body.taskAttemptId,
                    correlation,
                    exc_info=True,
                )
                return internal_error(
                    correlation, "RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False,
                    body.taskRunId, body.taskAttemptId, failure.validation_fields,
                )
        elif body.taskType == "CONCEPT_PORTFOLIO_V2_CONTINUE":
            from app.tasks.concept_portfolio_v2 import (
                ConceptPortfolioProductionContractError,
                execute_concept_portfolio_v2_continuation,
            )
            try:
                result = await execute_concept_portfolio_v2_continuation(body.input)
            except ConceptPortfolioProductionContractError as failure:
                logger.warning(
                    "CPV2 continuation result contract invalid taskRunId=%s taskAttemptId=%s correlationId=%s",
                    body.taskRunId, body.taskAttemptId, correlation, exc_info=True,
                )
                return internal_error(
                    correlation, "RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False,
                    body.taskRunId, body.taskAttemptId, failure.validation_fields,
                )
        elif body.taskType == "CONCEPT_PORTFOLIO_V2_SELECTION_ACTION":
            from app.tasks.concept_portfolio_v2 import (
                ConceptPortfolioProductionContractError,
                execute_concept_portfolio_v2_selection_action,
            )
            try:
                result = await execute_concept_portfolio_v2_selection_action(body.input)
            except ConceptPortfolioProductionContractError as failure:
                logger.warning(
                    "CPV2 selection action result contract invalid taskRunId=%s taskAttemptId=%s correlationId=%s",
                    body.taskRunId, body.taskAttemptId, correlation, exc_info=True,
                )
                return internal_error(
                    correlation, "RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False,
                    body.taskRunId, body.taskAttemptId, failure.validation_fields,
                )
        elif body.taskType == "CONCEPT_CANDIDATE":
            from app.tasks.concept_candidate import execute_concept_candidate
            result = await execute_concept_candidate(body.input)
        elif body.taskType == "CONCEPT_DISTINCTNESS_JUDGE":
            from app.tasks.concept_distinctness_judge import execute_concept_distinctness_judge
            result = await execute_concept_distinctness_judge(body.input)
        elif body.taskType == "CONCEPT_LEGAL_REVIEW":
            from app.tasks.concept_legal_review import execute_concept_legal_review
            result = await execute_concept_legal_review(body.input)
        elif body.taskType == "CONCEPT_REDESIGN":
            from app.tasks.concept_redesign import execute_concept_redesign
            result = await execute_concept_redesign(body.input)
        elif body.taskType == "CONCEPT_HYPOTHESIS_ALTERNATIVE":
            from app.tasks.concept_hypothesis_alternative import execute_concept_hypothesis_alternative
            result = await execute_concept_hypothesis_alternative({
                "hypothesisType": body.input.get("hypothesisType"),
                "rejectedValue": body.input.get("rejectedValue"),
                "proposalVersion": body.input.get("proposalVersion"),
                "candidate": body.input.get("candidate"),
            })
        elif body.taskType == "CONCEPT_DELTA_LEGAL_REVIEW":
            from app.tasks.concept_legal_review import execute_concept_legal_review
            result = await execute_concept_legal_review({
                "legalFactPattern": body.input.get("legalFactPattern"),
                "factPatternHash": body.input.get("factPatternHash"),
                "externalFactContext": body.input.get("externalFactContext"),
            })
        elif body.taskType == "TECH_OPS_PROPOSAL":
            from app.tasks.tech_ops_proposal import execute_tech_ops_proposal
            result = await execute_tech_ops_proposal({
                "contextJson": body.input.get("contextJson"),
                "proposalVersion": body.input.get("proposalVersion"),
                "rejectedProposalJson": body.input.get("rejectedProposalJson", ""),
            })
        elif body.taskType == "FINANCE_ESTIMATE":
            from app.tasks.finance_estimate import execute_finance_estimate
            result = await execute_finance_estimate({
                "contextJson": body.input.get("contextJson"),
                "fieldKey": body.input.get("fieldKey"),
                "proposalVersion": body.input.get("proposalVersion"),
                "rejectedProposalJson": body.input.get("rejectedProposalJson", ""),
            })
        elif body.taskType == "TECH_OPS_ADVISORY":
            from app.tasks.tech_ops_advisor.runtime_adapter import execute_tech_ops_advisory
            from app.progress.safe_task_progress import progress_sender_from_environment
            async with progress_sender_from_environment(
                task_run_id=body.taskRunId, task_attempt_id=body.taskAttemptId,
                correlation_id=correlation,
            ) as progress:
                result = await execute_tech_ops_advisory(
                    body.input, event_sink=progress.emit if progress.enabled else None,
                )
        elif body.taskType == "FINANCE_ANALYSIS_REPORT":
            from app.tasks.finance_analysis_report import execute_finance_analysis_report
            result = await execute_finance_analysis_report(body.input)
        elif body.taskType in {"LAUNCH_TECHNOLOGY_READINESS", "LAUNCH_OPERATIONS_READINESS", "LAUNCH_READINESS"}:
            from app.tasks.launch_readiness.professional import analyze_professional_readiness
            result = await analyze_professional_readiness({
                "moduleType": ({"LAUNCH_TECHNOLOGY_READINESS": "TECHNOLOGY",
                                "LAUNCH_OPERATIONS_READINESS": "OPERATIONS"}.get(body.taskType, "LAUNCH")),
                "input": body.input.get("professionalInput", {}),
            })
        elif body.taskType == "MARKETING_CONTENT_GENERATION":
            from app.tasks.marketing_content import execute_marketing_content
            result = await execute_marketing_content(body.input)
        elif body.taskType == "MARKETING_STRATEGY_GENERATION":
            from app.tasks.marketing_strategy import execute_marketing_strategy
            result = await execute_marketing_strategy(body.input)
        elif body.taskType == "FINAL_BUSINESS_PROPOSAL_GENERATION":
            from app.tasks.final_business_proposal import execute_final_business_proposal
            result = await execute_final_business_proposal(body.input)
        elif body.taskType == "FINAL_BUSINESS_PROPOSAL_REVIEW":
            from app.tasks.final_business_proposal.review import execute_final_business_proposal_review
            result = await execute_final_business_proposal_review(body.input)
        elif body.taskType == "MARKETING_VISUAL_GENERATION":
            from app.tasks.marketing_visual import execute_marketing_visual
            result = await execute_marketing_visual(body.input)
        elif body.taskType == "MARKET_RESEARCH":
            from app.research.product_pipeline import run_market_research
            from app.progress.safe_task_progress import progress_sender_from_environment
            remaining = max(1.0, (deadline - datetime.now(timezone.utc)).total_seconds())
            async with progress_sender_from_environment(
                task_run_id=body.taskRunId, task_attempt_id=body.taskAttemptId,
                correlation_id=correlation,
            ) as progress:
                result = await run_market_research(
                    body.input, body.taskAttemptId, remaining,
                    event_sink=progress.emit if progress.enabled else None,
                    diagnostic_context={
                        "taskRunId": body.taskRunId,
                        "taskAttemptId": body.taskAttemptId,
                        "correlationId": correlation,
                        "canonicalInputHash": body.canonicalInputHash,
                    },
                )
        elif body.taskType == "TWIN_STIMULUS_DRAFT":
            from app.twin.stimulus_draft import execute_twin_stimulus_draft
            result = await execute_twin_stimulus_draft(body.input)
        elif body.taskType == "TWIN_SURVEY":
            from app.twin import execute_twin_survey
            from app.progress.safe_task_progress import progress_sender_from_environment
            remaining = max(1.0, (deadline - datetime.now(timezone.utc)).total_seconds())
            async with progress_sender_from_environment(
                task_run_id=body.taskRunId, task_attempt_id=body.taskAttemptId,
                correlation_id=correlation,
            ) as progress:
                result = await execute_twin_survey(
                    body.input, remaining, event_sink=progress.emit if progress.enabled else None,
                )
        elif body.taskType == "IDEA_BRIEF_DERIVATION":
            from app.tasks.idea_brief import execute_idea_brief_derivation
            result = await execute_idea_brief_derivation(body.input)
        else:
            return internal_error(correlation, "UNSUPPORTED_TASK_TYPE", "TASK_TYPE_UNSUPPORTED", 422, False,
                                  body.taskRunId, body.taskAttemptId)
    except ProviderFailure as failure:
        # ⚠ `detail` 을 반드시 찍는다. 코드·사유 두 낱말만 남기면 무엇이 왜 죽었는지
        #   **어디에서도** 알 수 없다 — 유료 실행이 실패해도 원인을 못 밝힌다(2026-08-13 실측).
        #   화면에는 안 간다(`MarketResearchService.safeErrorReason` 이 막는다). 여기가 유일한 자리다.
        logger.warning(
            "AI execution failed taskType=%s taskRunId=%s taskAttemptId=%s correlationId=%s "
            "code=%s reason=%s retryable=%s detail=%s schemaName=%s upstreamStatus=%s "
            "providerErrorType=%s providerErrorParam=%s retryAfterMs=%s validationFields=%s "
            "safeDiagnostics=%s",
            body.taskType, body.taskRunId, body.taskAttemptId, correlation,
            failure.code, failure.reason, failure.retryable,
            getattr(failure, "safe_provider_message", None),
            failure.schema_name,
            failure.upstream_status, failure.provider_error_type, failure.provider_error_param,
            failure.retry_after_ms,
            failure.validation_fields,
            failure.safe_diagnostics,
        )
        return internal_error(correlation, failure.code, failure.reason, failure.status_code, failure.retryable,
                              body.taskRunId, body.taskAttemptId, failure.validation_fields,
                              failure.retry_after_ms)
    except Exception:
        logger.exception(
            "Unexpected internal AI execution failure taskType=%s taskRunId=%s "
            "taskAttemptId=%s correlationId=%s",
            body.taskType, body.taskRunId, body.taskAttemptId, correlation,
        )
        return internal_error(
            correlation, "INTERNAL_ERROR", "UNEXPECTED_INTERNAL_ERROR", 500, True,
            body.taskRunId, body.taskAttemptId,
        )
    return InternalExecutionSuccessResponseV1(contractVersion="1.0", taskType=body.taskType,
        taskSchemaVersion="1.0", taskRunId=body.taskRunId, taskAttemptId=body.taskAttemptId,
        correlationId=body.correlationId, canonicalInputHash=body.canonicalInputHash,
        resultSchemaVersion="1.0", result=result, warnings=execution_warnings,
        provenance=[provenance], usage=None)
