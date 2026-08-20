from __future__ import annotations

import asyncio

import httpx
import pytest

from app.concept_portfolio_v2.models import (
    BusinessRoleSemanticBatch, LegalFactDependencySemanticBatch, PlanDraftPool,
    SemanticArchitectureBatch, SemanticDistinctnessResult, SemanticFidelityResult,
    SemanticHypothesisBatch,
)
from app.models.legal_source import RoutingResult, ScreeningProviderResult
from app.providers import ProviderFailure
from app.providers import structured
from app.providers.schema_compatibility import strict_schema_failures
from app.tasks.concept_candidate.models import ConceptCandidateDraft
from app.tasks.concept_distinctness_judge.models import ConceptDistinctnessJudgeResult
from app.tasks.concept_hypothesis_alternative.models import ConceptHypothesisAlternativeResult
from app.tasks.concept_legal_review.models import (
    ConceptLegalReviewProviderResult, LegalQuestionClassificationBatch,
)
from app.tasks.concept_legal_review.service import _runtime_provider_schema
from app.tasks.finance_analysis_report.models import FinanceAnalysisReportResult
from app.tasks.finance_estimate.models import FinanceEstimateResult
from app.tasks.idea_brief.models import IdeaBriefProviderResult
from app.tasks.marketing_content.models import MarketingContentResult
from app.tasks.tech_ops_proposal.models import TechOpsProposalResult
from app.twin.stimulus_draft import DraftProviderResult


SCHEMAS = {
    model.__name__: model.model_json_schema() for model in (
        BusinessRoleSemanticBatch, ConceptCandidateDraft, LegalFactDependencySemanticBatch,
        PlanDraftPool, SemanticArchitectureBatch, SemanticDistinctnessResult,
        SemanticFidelityResult, SemanticHypothesisBatch, RoutingResult,
        ScreeningProviderResult, DraftProviderResult, ConceptDistinctnessJudgeResult,
        ConceptHypothesisAlternativeResult, ConceptLegalReviewProviderResult,
        LegalQuestionClassificationBatch, FinanceAnalysisReportResult,
        FinanceEstimateResult, IdeaBriefProviderResult, MarketingContentResult,
        TechOpsProposalResult,
    )
}
SCHEMAS["ConceptLegalReviewRuntime"] = _runtime_provider_schema([1, 2])


@pytest.mark.parametrize("schema_name,schema", SCHEMAS.items())
def test_all_structured_provider_schemas_are_offline_strict_compatible(schema_name, schema):
    assert strict_schema_failures(schema) == [], schema_name


def _configure(monkeypatch) -> None:
    monkeypatch.setenv("AI_PROVIDER", "openai")
    monkeypatch.setenv("AI_API_KEY", "test-only")
    monkeypatch.setenv("AI_MODEL", "test-model")
    monkeypatch.setenv("AI_BASE_URL", "https://provider.invalid/v1")


def _client(monkeypatch, response: httpx.Response, captured: list[dict]) -> None:
    class Client:
        async def __aenter__(self): return self
        async def __aexit__(self, *_args): return None
        async def post(self, _url, **kwargs):
            captured.append(kwargs["json"])
            return response
    monkeypatch.setattr(structured.httpx, "AsyncClient", lambda **_kwargs: Client())


def _valid_response() -> dict:
    return {"choices": [{"message": {"content": '{"value":"ok"}'}}]}


def test_provider_receives_accepted_strict_schema_and_valid_result(monkeypatch):
    _configure(monkeypatch)
    captured = []
    response = httpx.Response(200, json=_valid_response(), request=httpx.Request("POST", "https://provider.invalid"))
    _client(monkeypatch, response, captured)
    schema = {"type": "object", "properties": {"value": {"type": "string"}},
              "required": ["value"], "additionalProperties": False}
    result = asyncio.run(structured.execute_structured_prompt(
        "system", "user", response_schema=schema, schema_name="accepted_v1"))
    assert result == {"value": "ok"}
    assert captured[0]["response_format"]["json_schema"] == {
        "name": "accepted_v1", "strict": True, "schema": schema,
    }


def test_provider_response_format_rejection_is_safely_classified(monkeypatch):
    _configure(monkeypatch)
    captured = []
    response = httpx.Response(400, json={"error": {
        "type": "invalid_request_error", "param": "response_format.json_schema.schema",
        "message": "Invalid schema; bearer secret-must-not-leak",
    }}, request=httpx.Request("POST", "https://provider.invalid"))
    _client(monkeypatch, response, captured)
    schema = {"type": "object", "properties": {"value": {"type": "string"}},
              "required": ["value"], "additionalProperties": False}
    with pytest.raises(ProviderFailure) as raised:
        asyncio.run(structured.execute_structured_prompt(
            "system", "user", response_schema=schema, schema_name="rejected_v1"))
    assert raised.value.reason == "PROVIDER_RESPONSE_SCHEMA_REJECTED"
    assert raised.value.provider_error_param == "response_format.json_schema.schema"
    assert "secret-must-not-leak" not in (raised.value.safe_provider_message or "")


def test_malformed_provider_json_is_rejected(monkeypatch):
    _configure(monkeypatch)
    captured = []
    response = httpx.Response(200, json={"choices": [{"message": {"content": "not-json"}}]},
                              request=httpx.Request("POST", "https://provider.invalid"))
    _client(monkeypatch, response, captured)
    with pytest.raises(ProviderFailure) as raised:
        asyncio.run(structured.execute_structured_prompt("system", "user"))
    assert raised.value.reason == "PROVIDER_JSON_INVALID"


def test_incompatible_schema_fails_before_provider_http(monkeypatch):
    _configure(monkeypatch)
    called = []
    invalid = {"type": "object", "properties": {"optional": {
        "type": "null", "default": None}}, "required": [], "additionalProperties": False}
    with pytest.raises(ProviderFailure) as raised:
        asyncio.run(structured.execute_structured_prompt(
            "system", "user", response_schema=invalid, schema_name="invalid_v1"))
    assert raised.value.reason == "PROVIDER_RESPONSE_SCHEMA_REJECTED"
    assert raised.value.safe_diagnostics == {"stage": "OFFLINE_SCHEMA_PREFLIGHT"}
    assert called == []


def test_unsupported_one_of_is_rejected_offline():
    schema = {
        "type": "object",
        "properties": {
            "value": {
                "oneOf": [{"type": "string"}, {"type": "integer"}],
            },
        },
        "required": ["value"],
        "additionalProperties": False,
    }

    assert strict_schema_failures(schema) == [{
        "path": "$.properties.value.oneOf",
        "reason": "UNSUPPORTED_SCHEMA_KEYWORD",
    }]


def test_provider_schema_enum_limits_are_rejected_offline():
    schema = {
        "type": "object",
        "properties": {
            "value": {"type": "string", "enum": [f"value-{index:04d}" for index in range(1001)]},
        },
        "required": ["value"],
        "additionalProperties": False,
    }

    reasons = {failure["reason"] for failure in strict_schema_failures(schema)}
    assert "SCHEMA_ENUM_COUNT_EXCEEDED" in reasons


def test_large_single_string_enum_is_rejected_offline():
    schema = {
        "type": "object",
        "properties": {
            "value": {"type": "string", "enum": ["x" * 61 + str(index) for index in range(251)]},
        },
        "required": ["value"],
        "additionalProperties": False,
    }

    reasons = {failure["reason"] for failure in strict_schema_failures(schema)}
    assert "SCHEMA_ENUM_STRING_SIZE_EXCEEDED" in reasons


def test_provider_schema_property_limit_is_rejected_offline():
    properties = {f"property{index}": {"type": "string"} for index in range(5001)}
    schema = {
        "type": "object",
        "properties": properties,
        "required": list(properties),
        "additionalProperties": False,
    }

    reasons = {failure["reason"] for failure in strict_schema_failures(schema)}
    assert "SCHEMA_PROPERTY_COUNT_EXCEEDED" in reasons


def test_provider_schema_string_limit_is_rejected_offline():
    long_property_name = "x" * 120_001
    schema = {
        "type": "object",
        "properties": {long_property_name: {"type": "string"}},
        "required": [long_property_name],
        "additionalProperties": False,
    }

    reasons = {failure["reason"] for failure in strict_schema_failures(schema)}
    assert "SCHEMA_STRING_SIZE_EXCEEDED" in reasons


def test_generic_provider_rejection_preserves_safe_diagnostics(monkeypatch):
    _configure(monkeypatch)
    captured = []
    response = httpx.Response(400, json={"error": {
        "type": "invalid_request_error", "param": "messages",
        "message": "Input too large; bearer secret-must-not-leak",
    }}, request=httpx.Request("POST", "https://provider.invalid"))
    _client(monkeypatch, response, captured)

    with pytest.raises(ProviderFailure) as raised:
        asyncio.run(structured.execute_structured_prompt("system", "user"))

    assert raised.value.reason == "PERMANENT_EXECUTION_FAILURE"
    assert raised.value.upstream_status == 400
    assert raised.value.provider_error_type == "invalid_request_error"
    assert raised.value.provider_error_param == "messages"
    assert "secret-must-not-leak" not in (raised.value.safe_provider_message or "")


@pytest.mark.parametrize("status", [401, 403])
def test_provider_authorization_rejection_preserves_safe_diagnostics(monkeypatch, status):
    _configure(monkeypatch)
    captured = []
    response = httpx.Response(status, json={"error": {
        "type": "authentication_error", "param": None,
        "message": "Authorization rejected; bearer secret-must-not-leak",
    }}, request=httpx.Request("POST", "https://provider.invalid"))
    _client(monkeypatch, response, captured)

    with pytest.raises(ProviderFailure) as raised:
        asyncio.run(structured.execute_structured_prompt(
            "system", "user", schema_name="authorization_probe_v1"))

    assert raised.value.reason == "AI_CONFIGURATION_INVALID"
    assert raised.value.upstream_status == status
    assert raised.value.provider_error_type == "authentication_error"
    assert raised.value.schema_name == "authorization_probe_v1"
    assert raised.value.safe_diagnostics == {"stage": "PROVIDER_AUTHORIZATION"}
    assert "secret-must-not-leak" not in (raised.value.safe_provider_message or "")


def test_missing_provider_configuration_identifies_safe_stage(monkeypatch):
    monkeypatch.delenv("AI_PROVIDER", raising=False)
    monkeypatch.delenv("AI_API_KEY", raising=False)
    monkeypatch.delenv("AI_MODEL", raising=False)

    with pytest.raises(ProviderFailure) as raised:
        asyncio.run(structured.execute_structured_prompt("system", "user"))

    assert raised.value.safe_diagnostics == {
        "stage": "PROVIDER_CONFIGURATION",
        "providerSupported": False,
        "apiKeyConfigured": False,
        "modelConfigured": False,
    }
