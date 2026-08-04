import asyncio
import logging

import pytest

from app.services import journey_provider


def valid_idea_result():
    return {
        "originalSourceSummary": "입력 요약",
        "normalizedDescription": "정규화 설명",
        "facts": [],
        "assumptions": [],
        "constraints": [],
        "openQuestions": ["초기 지역은 어디입니까?"],
        "readiness": "UNDER_SPECIFIED",
        "warnings": [],
        "evidenceNeeds": [],
        "originDraft": {
            "productServiceDescription": "서비스 설명",
            "problem": ["문제"],
            "target": {"customerTypes": [], "segment": None, "situation": None, "needs": []},
            "solution": ["해결책"],
            "coreValue": ["핵심 가치"],
            "primaryCategory": "기타",
            "targetRegion": None,
            "fixedValues": [{
                "field": "productServiceDescription",
                "value": "서비스 설명",
            }],
            "confirmedValues": {},
            "assumptions": [],
            "pricingIntent": None,
            "revenueModelIntent": None,
            "salesChannelIntent": None,
            "knownUnitCost": None,
            "alternatives": [],
            "knownCompetitors": [],
            "differentiationIntent": None,
            "internalConstraints": [],
        },
        "fieldMetadata": [{
            "key": "targetRegion",
            "sourceType": "AI_PROPOSED",
            "requiredForStages": ["IDEA_ORIGIN"],
            "status": "MISSING",
            "locked": False,
            "fallbackPolicy": "BLOCK_STAGE",
        }],
        "clarificationQuestions": [{
            "targetField": "targetRegion",
            "requirement": "REQUIRED_FOR_IDEA_ORIGIN",
            "question": "초기 지역은 어디입니까?",
            "reason": "Idea Origin 확정에 필요합니다.",
        }],
    }


def test_idea_interpretation_repairs_one_invalid_provider_result(monkeypatch, caplog):
    responses = [
        {"originalSourceSummary": "입력 요약", "unexpected": True},
        valid_idea_result(),
    ]

    async def fake_prompt(system, user):
        return responses.pop(0)

    monkeypatch.setattr(journey_provider, "execute_structured_prompt", fake_prompt)
    result = asyncio.run(
        journey_provider.execute_journey_task("IDEA_INTERPRETATION", "아이디어")
    )

    assert result["readiness"] == "UNDER_SPECIFIED"
    assert responses == []
    assert "<unknown-field>" in caplog.text
    assert "unexpected" not in caplog.text


def test_idea_interpretation_rejects_invalid_repair(monkeypatch):
    async def invalid_prompt(system, user):
        return {"originalSourceSummary": "입력 요약"}

    monkeypatch.setattr(journey_provider, "execute_structured_prompt", invalid_prompt)

    with pytest.raises(journey_provider.ProviderFailure) as failure:
        asyncio.run(
            journey_provider.execute_journey_task("IDEA_INTERPRETATION", "아이디어")
        )

    assert failure.value.code == "RESULT_SCHEMA_INVALID"
    assert failure.value.retryable is False


def test_idea_interpretation_regenerates_once_after_invalid_json(monkeypatch):
    responses = [
        journey_provider.ProviderFailure(
            "RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False
        ),
        valid_idea_result(),
    ]

    async def fake_prompt(system, user):
        response = responses.pop(0)
        if isinstance(response, Exception):
            raise response
        return response

    monkeypatch.setattr(journey_provider, "execute_structured_prompt", fake_prompt)

    result = asyncio.run(
        journey_provider.execute_journey_task("IDEA_INTERPRETATION", "아이디어")
    )

    assert result["normalizedDescription"] == "정규화 설명"
    assert responses == []


def test_idea_interpretation_repairs_missing_required_questions(monkeypatch, caplog):
    caplog.set_level(logging.INFO)
    missing_question_result = valid_idea_result()
    missing_question_result["clarificationQuestions"] = []
    missing_question_result["openQuestions"] = []
    responses = [missing_question_result]

    async def fake_prompt(system, user):
        return responses.pop(0)

    monkeypatch.setattr(journey_provider, "execute_structured_prompt", fake_prompt)

    result = asyncio.run(
        journey_provider.execute_journey_task("IDEA_INTERPRETATION", "아이디어")
    )

    assert result["clarificationQuestions"][0]["targetField"] == "targetRegion"
    assert "clarification auto-completed" in caplog.text
    assert "targetRegion" in caplog.text
    assert responses == []


def test_idea_interpretation_serializes_all_closed_contract_fields(monkeypatch):
    provider_result = valid_idea_result()
    for field in (
        "pricingIntent",
        "revenueModelIntent",
        "salesChannelIntent",
        "knownUnitCost",
        "differentiationIntent",
    ):
        provider_result["originDraft"].pop(field)
    provider_result["originDraft"]["target"].pop("situation")

    async def fake_prompt(system, user):
        return provider_result

    monkeypatch.setattr(journey_provider, "execute_structured_prompt", fake_prompt)

    result = asyncio.run(
        journey_provider.execute_journey_task("IDEA_INTERPRETATION", "아이디어")
    )

    origin = result["originDraft"]
    assert origin["pricingIntent"] is None
    assert origin["revenueModelIntent"] is None
    assert origin["salesChannelIntent"] is None
    assert origin["knownUnitCost"] is None
    assert origin["differentiationIntent"] is None
    assert origin["target"]["situation"] is None
