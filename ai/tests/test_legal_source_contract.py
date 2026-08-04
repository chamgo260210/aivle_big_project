import asyncio

from app.legal.moleg import LawMetadata
from app.legal import pipeline
from app.models.legal_source import LegalSourcePipelineResult


class FakeMolegClient:
    async def search_exact(self, law_name):
        return LawMetadata(law_name, "100", "LAW-100", "20260803", "https://law.example/100")

    async def articles(self, metadata):
        return [{"article": "제1조", "title": "처리방침", "text": "개인정보 처리자는 처리방침을 공개해야 한다."}]


async def fake_prompt(system, user):
    if "규제 경로" in system:
        return {"routes": [{"routeId": "personal_data", "status": "APPLIES",
            "evidenceQuotes": ["고객 이메일을 수집한다"], "reason": "개인정보 수집", "confidence": 0.95}],
            "additionalRouteCandidates": [], "missingInformation": []}
    return {"screenings": [{"citationId": "CIT-001", "role": "REQUIREMENT",
        "plainSummary": "개인정보 처리방침을 공개해야 합니다.",
        "whyRelevant": "고객 이메일을 수집하기 때문입니다."}]}


def test_legal_source_pipeline_contract(monkeypatch):
    monkeypatch.setenv("LEGAL_REGISTRY_VERSION", "legal-registry-v1")
    monkeypatch.setattr(pipeline, "MolegClient", FakeMolegClient)
    monkeypatch.setattr(pipeline, "execute_structured_prompt", fake_prompt)
    result = asyncio.run(pipeline.execute_legal_source_pipeline("IDEA_LEGAL_PRECHECK",
        "고객 이메일을 수집한다", {"mode": "FULL", "rerunCategories": [],
            "confirmedFacts": [], "registryVersion": "legal-registry-v1"}))
    value = LegalSourcePipelineResult.model_validate(result)
    assert value.sourceStatus == "SOURCE_COMPLETE"
    assert value.registryVersion == "legal-registry-v1"
    assert value.evidence[0].registryVersion == value.registryVersion
    assert value.evidence[0].lawName == "개인정보 보호법"
    assert value.findings[0].reasoning.evidenceIds == [value.evidence[0].evidenceId]


def test_registry_contains_reference_route_set(monkeypatch):
    monkeypatch.setenv("LEGAL_REGISTRY_VERSION", "legal-registry-v1")
    registry = pipeline.LegalRegistry()
    assert len(registry.routes) == 27
    assert registry.categories_for_route("online_sales") == [
        "BUSINESS_REGISTRATION", "CONSUMER_PROTECTION", "TERMS_AND_CONTRACT"]
