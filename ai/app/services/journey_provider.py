import json
import logging
import os
import re
from copy import deepcopy
from pathlib import Path
from typing import Any

import httpx
from pydantic import ValidationError

from app.models.journey import (
    ConceptGenerationResult,
    DetailedAnalysisResult,
    IdeaInterpretationResult,
    InterviewSynthesisResult,
    LegalReviewResult,
    MarketingComparisonResult,
    MarketingGenerationResult,
    PersonaCardGenerationResult,
    PersonaInterviewResult,
    QuickAssessmentResult,
    FinalReportResult,
)


PROMPT_ROOT = Path(__file__).resolve().parents[2] / "prompts"
logger = logging.getLogger(__name__)


class ProviderFailure(Exception):
    def __init__(self, code: str, reason: str, status_code: int, retryable: bool):
        super().__init__(reason)
        self.code = code
        self.reason = reason
        self.status_code = status_code
        self.retryable = retryable


def _configuration(model_override: str | None = None) -> tuple[str, str, str]:
    provider = os.getenv("AI_PROVIDER", "").strip().lower()
    api_key = os.getenv("AI_API_KEY", "").strip()
    model = (model_override or "").strip() or os.getenv("AI_MODEL", "").strip()
    base_url = os.getenv("AI_BASE_URL", "").strip().rstrip("/")
    if provider not in {"openai", "openai-compatible"} or not api_key or not model:
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "AI_CONFIGURATION_INVALID", 503, False)
    if provider == "openai" and not base_url:
        base_url = "https://api.openai.com/v1"
    if not base_url.startswith(("http://", "https://")):
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "AI_CONFIGURATION_INVALID", 503, False)
    return api_key, model, base_url


def _load_prompts(task_type: str, text: str) -> tuple[str, str]:
    folders = {
        "IDEA_INTERPRETATION": "idea_interpretation",
        "LEGAL_REVIEW": "legal_review",
        "CONCEPT_GENERATION": "concept_generation",
        "QUICK_ASSESSMENT": "quick_assessment",
        "DETAILED_ANALYSIS": "detailed_analysis",
        "PERSONA_CARD_GENERATION": "persona_card_generation",
        "PERSONA_INTERVIEW": "persona_interview",
        "INTERVIEW_SYNTHESIS": "interview_synthesis",
        "MARKETING_GENERATION": "marketing_generation",
        "MARKETING_COMPARISON": "marketing_comparison",
        "FINAL_REPORT_GENERATION": "final_report_generation",
    }
    folder = folders.get(task_type)
    if folder is None:
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "AI_CONFIGURATION_INVALID", 503, False)
    try:
        system = (PROMPT_ROOT / folder / "system.md").read_text(encoding="utf-8")
        template = (PROMPT_ROOT / folder / "user.md").read_text(encoding="utf-8")
    except OSError as failure:
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "AI_CONFIGURATION_INVALID", 503, False) from failure
    return system, template.replace("{{input}}", text)


def _extract_json(content: str) -> dict[str, Any]:
    fenced = re.search(r"```(?:json)?\s*([\s\S]*?)```", content, flags=re.IGNORECASE)
    candidate = fenced.group(1).strip() if fenced else content.strip()
    start = candidate.find("{")
    if start < 0:
        raise ValueError("JSON object not found")
    value, end = json.JSONDecoder().raw_decode(candidate[start:])
    if not isinstance(value, dict) or candidate[start + end:].strip():
        raise ValueError("Provider result is not one JSON object")
    return value


async def execute_journey_task(task_type: str, text: str) -> dict[str, Any]:
    system, user = _load_prompts(task_type, text)
    try:
        raw_result = await execute_structured_prompt(system, user)
    except ProviderFailure as first_failure:
        if (
            task_type != "IDEA_INTERPRETATION"
            or first_failure.code != "RESULT_SCHEMA_INVALID"
        ):
            raise
        logger.warning(
            "Journey provider JSON regeneration taskType=%s phase=initial",
            task_type,
        )
        raw_result = await execute_structured_prompt(system, user)
    model_types = {
        "IDEA_INTERPRETATION": IdeaInterpretationResult,
        "LEGAL_REVIEW": LegalReviewResult,
        "CONCEPT_GENERATION": ConceptGenerationResult,
        "QUICK_ASSESSMENT": QuickAssessmentResult,
        "DETAILED_ANALYSIS": DetailedAnalysisResult,
        "PERSONA_CARD_GENERATION": PersonaCardGenerationResult,
        "PERSONA_INTERVIEW": PersonaInterviewResult,
        "INTERVIEW_SYNTHESIS": InterviewSynthesisResult,
        "MARKETING_GENERATION": MarketingGenerationResult,
        "MARKETING_COMPARISON": MarketingComparisonResult,
        "FINAL_REPORT_GENERATION": FinalReportResult,
    }
    try:
        model_type = model_types[task_type]
        return model_type.model_validate(raw_result).model_dump(
            by_alias=True,
            # Spring validates the Idea Origin object as a closed contract and
            # therefore requires nullable/defaulted properties to be present.
            exclude_unset=task_type != "IDEA_INTERPRETATION",
        )
    except KeyError as failure:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False) from failure
    except ValidationError as first_failure:
        issues = _validation_issues(first_failure, model_type)
        if task_type != "IDEA_INTERPRETATION":
            logger.warning(
                "Journey provider result schema invalid taskType=%s phase=initial issues=%s",
                task_type,
                issues,
            )
            raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False) from first_failure

        missing_fields = _only_missing_clarification_fields(first_failure)
        if missing_fields:
            logger.info(
                "Journey provider clarification auto-completed taskType=%s fields=%s",
                task_type,
                missing_fields,
            )
            completed_result = _complete_missing_clarifications(
                raw_result, missing_fields
            )
            return model_type.model_validate(completed_result).model_dump(
                by_alias=True, exclude_unset=False
            )

        logger.warning(
            "Journey provider result schema invalid taskType=%s phase=initial issues=%s",
            task_type,
            issues,
        )
        repaired_result = await _repair_idea_interpretation_result(
            model_type, raw_result, issues
        )
        try:
            return model_type.model_validate(repaired_result).model_dump(
                by_alias=True, exclude_unset=False
            )
        except ValidationError as repair_failure:
            missing_fields = _only_missing_clarification_fields(repair_failure)
            if missing_fields:
                completed_result = _complete_missing_clarifications(
                    repaired_result, missing_fields
                )
                return model_type.model_validate(completed_result).model_dump(
                    by_alias=True, exclude_unset=False
                )
            logger.warning(
                "Journey provider result schema invalid taskType=%s phase=repair issues=%s",
                task_type,
                _validation_issues(repair_failure, model_type),
            )
            raise ProviderFailure(
                "RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False
            ) from repair_failure


def _validation_issues(failure: ValidationError, model_type) -> list[dict[str, str]]:
    """Return bounded diagnostics without input values or provider output."""
    known_fields = _schema_property_names(model_type.model_json_schema())
    issues = []
    for error in failure.errors(
        include_url=False, include_context=True, include_input=False
    )[:20]:
        issue = {
            "path": ".".join(
                str(part) if isinstance(part, int)
                else part if part in known_fields
                else "<unknown-field>"
                for part in error["loc"]
            ),
            "type": error["type"],
        }
        if error["type"] == "idea_missing_clarification":
            fields = error.get("ctx", {}).get("fields", "")
            safe_fields = [field for field in fields.split(",") if field in known_fields]
            issue["fields"] = ",".join(safe_fields)
        issues.append(issue)
    return issues


def _only_missing_clarification_fields(failure: ValidationError) -> list[str]:
    errors = failure.errors(
        include_url=False, include_context=True, include_input=False
    )
    if not errors or any(
        error["type"] != "idea_missing_clarification" for error in errors
    ):
        return []
    allowed_fields = {
        "productServiceDescription", "problem", "target", "solution",
        "coreValue", "primaryCategory", "targetRegion", "fixedValues",
    }
    return sorted({
        field
        for error in errors
        for field in error.get("ctx", {}).get("fields", "").split(",")
        if field in allowed_fields
    })


def _complete_missing_clarifications(
    raw_result: dict[str, Any], missing_fields: list[str]
) -> dict[str, Any]:
    questions = {
        "productServiceDescription": "제공하려는 제품 또는 서비스를 한 문장으로 설명해 주세요.",
        "problem": "이 아이디어가 해결하려는 핵심 문제는 무엇입니까?",
        "target": "이 제품 또는 서비스를 가장 먼저 사용할 고객은 누구입니까?",
        "solution": "핵심 문제를 어떤 방식으로 해결합니까?",
        "coreValue": "고객에게 제공하는 가장 중요한 가치는 무엇입니까?",
        "primaryCategory": "이 아이디어의 주된 제품·서비스 카테고리는 무엇입니까?",
        "targetRegion": "서비스를 처음 제공할 국가 또는 지역은 어디입니까?",
        "fixedValues": "향후 Concept에서도 반드시 변경하지 않고 유지할 조건이나 값이 있습니까? 없다면 '없음'이라고 답해 주세요.",
    }
    completed = deepcopy(raw_result)
    clarification_items = completed.setdefault("clarificationQuestions", [])
    existing_targets = {
        item.get("targetField") for item in clarification_items
        if isinstance(item, dict)
    }
    for field in missing_fields:
        if field not in existing_targets:
            clarification_items.append({
                "targetField": field,
                "requirement": "REQUIRED_FOR_IDEA_ORIGIN",
                "question": questions[field],
                "reason": "Idea Origin 확정에 필요한 필수 입력입니다.",
            })
    completed["openQuestions"] = [
        item["question"] for item in clarification_items
    ]

    metadata_items = completed.setdefault("fieldMetadata", [])
    metadata_keys = {
        item.get("key") for item in metadata_items if isinstance(item, dict)
    }
    for field in missing_fields:
        if field not in metadata_keys:
            metadata_items.append({
                "key": field,
                "sourceType": "AI_PROPOSED",
                "requiredForStages": ["IDEA_ORIGIN"],
                "status": "MISSING",
                "locked": False,
                "fallbackPolicy": "BLOCK_STAGE",
            })
    return completed


def _schema_property_names(value: Any) -> set[str]:
    if isinstance(value, dict):
        names = set(value.get("properties", {}))
        for nested in value.values():
            names.update(_schema_property_names(nested))
        return names
    if isinstance(value, list):
        names: set[str] = set()
        for nested in value:
            names.update(_schema_property_names(nested))
        return names
    return set()


async def _repair_idea_interpretation_result(
    model_type, raw_result: dict[str, Any], issues: list[dict[str, str]]
) -> dict[str, Any]:
    system = (
        "당신은 JSON Contract 복구기입니다. 제공된 invalidResult의 의미를 바꾸거나 "
        "새로운 사실을 추가하지 말고 requiredSchema에 정확히 맞는 JSON 객체 하나만 반환하세요. "
        "모든 required 필드를 포함하고, 값이 없으면 스키마가 허용하는 null 또는 빈 배열을 "
        "사용하세요. extra field는 제거하세요. fieldMetadata의 status가 MISSING이면 "
        "sourceType은 AI_PROPOSED이고 locked는 false입니다. Idea Origin 필수 필드 "
        "productServiceDescription, problem, target, solution, coreValue, primaryCategory, "
        "targetRegion, fixedValues 중 null, 빈 문자열, 빈 배열인 각 필드에는 그 field 이름을 "
        "targetField로 쓰는 REQUIRED_FOR_IDEA_ORIGIN clarificationQuestions 항목을 반드시 "
        "추가하고, openQuestions에는 그 question 문자열을 같은 순서로 넣으세요. "
        "설명과 Markdown을 출력하지 마세요."
    )
    user = json.dumps(
        {
            "validationIssues": issues,
            "requiredSchema": model_type.model_json_schema(),
            "invalidResult": raw_result,
        },
        ensure_ascii=False,
        separators=(",", ":"),
    )
    return await execute_structured_prompt(system, user)


async def execute_structured_prompt(
    system: str, user: str, model_override: str | None = None
) -> dict[str, Any]:
    api_key, model, base_url = _configuration(model_override)
    try:
        timeout_seconds = float(os.getenv("AI_PROVIDER_TIMEOUT_SECONDS", "60"))
        if timeout_seconds <= 0:
            raise ValueError
    except ValueError as failure:
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "AI_CONFIGURATION_INVALID", 503, False) from failure
    body = {
        "model": model,
        "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": user},
        ],
        "temperature": 0.1,
        "response_format": {"type": "json_object"},
    }
    try:
        async with httpx.AsyncClient(timeout=timeout_seconds) as client:
            response = await client.post(
                f"{base_url}/chat/completions",
                headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
                json=body,
            )
    except (httpx.TimeoutException, httpx.NetworkError) as failure:
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "MODEL_DEPENDENCY_UNAVAILABLE", 503, True) from failure
    if response.status_code in (401, 403):
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "AI_CONFIGURATION_INVALID", 503, False)
    if response.status_code == 429:
        raise ProviderFailure("RATE_LIMITED", "DEPENDENCY_RATE_LIMITED", 429, True)
    if response.status_code >= 500:
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "MODEL_DEPENDENCY_UNAVAILABLE", 503, True)
    if response.status_code >= 400:
        raise ProviderFailure("EXECUTION_FAILED", "PERMANENT_EXECUTION_FAILURE", 500, False)
    if len(response.content) > 2 * 1024 * 1024:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False)
    try:
        payload = response.json()
        content = payload["choices"][0]["message"]["content"]
        if isinstance(content, list):
            content = "".join(part.get("text", "") for part in content if isinstance(part, dict))
        return _extract_json(content)
    except (KeyError, IndexError, TypeError, AttributeError, ValueError, json.JSONDecodeError) as failure:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False) from failure
