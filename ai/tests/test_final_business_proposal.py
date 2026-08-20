from types import SimpleNamespace

from app.tasks.final_business_proposal.models import (
    FinalBusinessProposalInput, FinalBusinessProposalResult,
)
from app.tasks.final_business_proposal import service as proposal_service
from app.tasks.final_business_proposal.review import ProposalReviewResult
from app.tasks.final_business_proposal.service import _close_evidence_vocabulary
from app.tasks.final_business_proposal.service import _close_evidence_key_vocabulary
from app.tasks.final_business_proposal.service import execute_final_business_proposal
from app.tasks.final_business_proposal.service import _proposal_model
from app.tasks.final_business_proposal.service import _validate_evidence_types
from app.providers.schema_compatibility import strict_schema_failures
from app.tasks.marketing_content.models import lint_provider_schema


def test_proposal_input_and_structured_output_schema():
    value = FinalBusinessProposalInput.model_validate({
        "contract": "final-business-proposal-input-v1", "projectId": 7, "version": 1,
        "sourceManifestHash": "sha256:" + "a" * 64,
        "sourceManifest": [{"type": "PROJECT", "id": "7", "metadata": {"status": "CURRENT"}},
                           {"type": "CURRENT_CONCEPT", "id": "concept-1"},
                           {"type": "MARKET", "id": "market-1"}],
        "includedSourceTypes": ["PROJECT", "CURRENT_CONCEPT", "MARKET"],
        "omittedSourceTypes": ["FINANCE"], "sources": {"PROJECT": {"name": "자전거 분석"}},
        "evidenceCatalog": [{"evidenceKey": "EV-" + "a" * 24, "sourceType": "PROJECT",
                             "sourceId": "7", "label": "프로젝트 · 사업명",
                             "summary": "자전거 분석", "sourcePath": "프로젝트 · name"}],
        "allowedEvidenceKeys": ["EV-" + "a" * 24],
    })
    schema = FinalBusinessProposalResult.model_json_schema()
    assert value.projectId == 7
    assert value.sourceManifest[0].metadata == {"status": "CURRENT"}
    assert set(schema["required"]) == {"contract", "cover", "executiveDecisionSummary", "sections",
                                       "decisionRequest", "appendix"}


def test_proposal_uses_long_context_model_by_default(monkeypatch):
    monkeypatch.delenv("FINAL_BUSINESS_PROPOSAL_MODEL", raising=False)

    assert _proposal_model() == "gpt-4.1-mini"


def test_proposal_model_can_be_overridden_without_changing_global_model(monkeypatch):
    monkeypatch.setenv("AI_MODEL", "gpt-4o-mini")
    monkeypatch.setenv("FINAL_BUSINESS_PROPOSAL_MODEL", "custom-long-context-model")

    assert _proposal_model() == "custom-long-context-model"


def test_proposal_passes_dedicated_model_to_provider(monkeypatch):
    class ProviderCalled(Exception):
        pass

    async def capture_provider_call(*_args, **kwargs):
        assert kwargs["model_override"] == "gpt-4.1-mini"
        raise ProviderCalled

    monkeypatch.delenv("FINAL_BUSINESS_PROPOSAL_MODEL", raising=False)
    monkeypatch.setattr(proposal_service, "execute_structured_prompt", capture_provider_call)
    payload = {
        "contract": "final-business-proposal-input-v1", "projectId": 7, "version": 1,
        "sourceManifestHash": "sha256:" + "a" * 64,
        "sourceManifest": [{"type": "PROJECT", "id": "7"},
                           {"type": "CURRENT_CONCEPT", "id": "concept-1"},
                           {"type": "MARKET", "id": "market-1"}],
        "includedSourceTypes": ["PROJECT", "CURRENT_CONCEPT", "MARKET"],
        "omittedSourceTypes": [], "sources": {"PROJECT": {"name": "자전거 분석"}},
        "evidenceCatalog": [{"evidenceKey": "EV-" + "a" * 24, "sourceType": "PROJECT",
                             "sourceId": "7", "label": "사업명", "summary": "자료",
                             "sourcePath": "프로젝트 · name"}],
        "allowedEvidenceKeys": ["EV-" + "a" * 24],
    }

    with pytest.raises(ProviderCalled):
        asyncio.run(execute_final_business_proposal(payload))


def test_every_evidence_source_field_is_closed_to_manifest_types():
    schema = FinalBusinessProposalResult.model_json_schema()
    _close_evidence_vocabulary(schema, ["CURRENT_CONCEPT", "MARKET"])

    enums = []
    key_schemas = []
    def visit(node):
        if isinstance(node, dict):
            if "evidenceSourceTypes" in node.get("properties", {}):
                enums.append(node["properties"]["evidenceSourceTypes"]["items"]["enum"])
            if "evidenceKeys" in node.get("properties", {}):
                key_schemas.append(node["properties"]["evidenceKeys"]["items"])
            for value in node.values(): visit(value)
        elif isinstance(node, list):
            for value in node: visit(value)
    visit(schema)
    assert enums and all(value == ["CURRENT_CONCEPT", "MARKET"] for value in enums)
    assert key_schemas
    assert all(value.get("pattern") == r"^EV-[0-9a-f]{24}$" for value in key_schemas)
    assert all("enum" not in value for value in key_schemas)


def test_large_evidence_catalog_uses_shared_chunked_enum_within_provider_limits():
    schema = FinalBusinessProposalResult.model_json_schema()
    _close_evidence_vocabulary(schema, ["PROJECT", "CURRENT_CONCEPT", "MARKET"])
    allowed_keys = [f"EV-{index:024x}" for index in range(604)]
    _close_evidence_key_vocabulary(schema, allowed_keys)

    chunks = [definition["enum"] for name, definition in schema["$defs"].items()
              if name.startswith("AllowedEvidenceKeyChunk")]
    key_item_schemas = []

    def visit(node):
        if isinstance(node, dict):
            evidence_keys = node.get("properties", {}).get("evidenceKeys")
            if isinstance(evidence_keys, dict):
                key_item_schemas.append(evidence_keys["items"])
            for value in node.values():
                visit(value)
        elif isinstance(node, list):
            for value in node:
                visit(value)

    visit(schema)

    assert strict_schema_failures(schema) == []
    assert lint_provider_schema(schema) == []
    assert [len(chunk) for chunk in chunks] == [250, 250, 104]
    assert [key for chunk in chunks for key in chunk] == allowed_keys
    assert key_item_schemas
    assert all(value == {"$ref": "#/$defs/AllowedEvidenceKey"}
               for value in key_item_schemas)


def test_provider_result_evidence_key_still_must_exist_in_catalog():
    allowed_key = "EV-" + "a" * 24
    unknown_key = "EV-" + "b" * 24
    group = SimpleNamespace(evidenceSourceTypes=["PROJECT"], evidenceKeys=[allowed_key])
    result = SimpleNamespace(
        executiveDecisionSummary=SimpleNamespace(
            evidenceSourceTypes=["PROJECT"], evidenceKeys=[unknown_key],
        ),
        decisionRequest=group,
        appendix=group,
        sections=[],
    )

    with pytest.raises(ProviderFailure) as failure:
        _validate_evidence_types(result, {"PROJECT"}, {allowed_key})

    assert failure.value.reason == "AI_EVIDENCE_REFERENCE_INVALID"
    assert failure.value.safe_diagnostics == {
        "invalidEvidenceKeyCount": 1,
        "allowedEvidenceKeyCount": 1,
    }


def test_review_contract_has_traceable_groups():
    schema = ProposalReviewResult.model_json_schema()
    assert {"wellPrepared", "needsImprovement", "requiredBeforeApproval", "followUpActions"}.issubset(
        schema["properties"],
    )


def test_invalid_proposal_input_reports_only_safe_schema_path():
    payload = {
        "contract": "final-business-proposal-input-v1", "projectId": 7, "version": 1,
        "sourceManifestHash": "sha256:" + "a" * 64,
        "sourceManifest": [{"type": "PROJECT", "id": "7", "unexpected": "secret"},
                           {"type": "CURRENT_CONCEPT", "id": "concept-1"},
                           {"type": "MARKET", "id": "market-1"}],
        "includedSourceTypes": ["PROJECT", "CURRENT_CONCEPT", "MARKET"],
        "omittedSourceTypes": [], "sources": {"PROJECT": {}},
        "evidenceCatalog": [{"evidenceKey": "EV-" + "a" * 24, "sourceType": "PROJECT",
                             "sourceId": "7", "label": "사업명", "summary": "자료",
                             "sourcePath": "프로젝트 · name"}],
        "allowedEvidenceKeys": ["EV-" + "a" * 24],
    }
    with pytest.raises(ProviderFailure) as failure:
        asyncio.run(execute_final_business_proposal(payload))
    assert failure.value.validation_fields == [{
        "path": "sourceManifest.0.unexpected", "category": "extra_forbidden",
        "expectedType": "no extra field",
    }]
    assert "secret" not in str(failure.value.validation_fields)
import asyncio

import pytest

from app.providers import ProviderFailure
