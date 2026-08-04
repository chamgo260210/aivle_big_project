import json
import os
import re
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
    raw_result = await execute_structured_prompt(system, user)
    try:
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
        model_type = model_types[task_type]
        return model_type.model_validate(raw_result).model_dump(by_alias=True, exclude_unset=True)
    except (KeyError, ValidationError) as failure:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False) from failure


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
    if response.status_code >= 400:
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "MODEL_DEPENDENCY_UNAVAILABLE", 503, response.status_code >= 500)
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
