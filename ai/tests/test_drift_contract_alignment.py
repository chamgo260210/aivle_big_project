# -*- coding: utf-8 -*-
"""드리프트 계약이 두 층에서 같은지 대조한다.

정본은 `ai/app/validation/drift.py`, 사본은 `ConceptDriftContract.java` 다.
갈리면 AI 가 통과시킨 제안을 Java 가 막거나 그 반대가 된다 — 조용한 무한 루프이거나,
**사업안이 바뀐 채로 검증이 끝난다.**
"""
import re
from pathlib import Path

import pytest

from app.validation import drift


JAVA = (
    Path(__file__).resolve().parents[2]
    / "backend/src/main/java/com/aivle/backend/taskrun/contract/ConceptDriftContract.java"
).read_text(encoding="utf-8")


def _quoted(block: str) -> set[str]:
    return set(re.findall(r'"([A-Za-z_]+)"', block))


def _block(name: str) -> str:
    start = JAVA.index(name)
    return JAVA[start:JAVA.index(";", start)]


def test_frozen_fields_match():
    assert _quoted(_block("FROZEN_FIELDS")) == set(drift.FROZEN_FIELDS)


def test_refinable_fields_and_rules_match():
    block = _block("REFINABLE_FIELDS")
    pairs = dict(re.findall(r'"([A-Za-z]+)",\s*"([A-Z_]+)"', block))
    assert pairs == dict(drift.REFINABLE_FIELDS)


def test_free_field_lists_match():
    assert _quoted(_block("FREE_WITH_EVIDENCE_FIELDS")) == set(drift.FREE_WITH_EVIDENCE_FIELDS)
    assert _quoted(_block("FREE_BM_FIELDS")) == set(drift.FREE_BM_FIELDS)


def test_numeric_allowances_match():
    tolerance = re.search(r"PRICE_TOLERANCE\s*=\s*([0-9.]+)", JAVA)
    allowance = re.search(r"LIST_CHANGE_ALLOWANCE\s*=\s*([0-9]+)", JAVA)
    assert float(tolerance.group(1)) == pytest.approx(drift.PRICE_TOLERANCE)
    assert int(allowance.group(1)) == drift.LIST_CHANGE_ALLOWANCE


def test_frozen_field_change_is_rejected():
    with pytest.raises(drift.DriftRejection):
        drift.check("operatingModel", "중개", "직접 판매")


def test_price_band_edges():
    drift.check("price", 20000, 26000)                      # +30% 는 통과
    with pytest.raises(drift.DriftRejection):
        drift.check("price", 20000, 26001)                  # 넘으면 기각


def test_narrowing_only_rejects_new_nouns():
    drift.check("targetUsers", "서울 20대 여성", "20대 여성")
    with pytest.raises(drift.DriftRejection):
        drift.check("targetUsers", "20대 여성", "20대 여성 직장인")


def test_key_partners_overlapping_frozen_requirements_is_rejected():
    concept = {"partnerRequirements": "OEM 공장 계약 필수"}
    with pytest.raises(drift.DriftRejection):
        drift.check("keyPartners", ["물류 대행사"], ["OEM 공장"], concept)


def test_unknown_field_is_rejected():
    with pytest.raises(drift.DriftRejection):
        drift.check("somethingNew", 1, 2)


def test_filter_keeps_rejection_reasons_for_the_next_round():
    concept = {"price": 20000, "operatingModel": "중개"}
    passed, rejected = drift.filter_proposals([
        {"fieldKey": "price", "proposedValue": 22000},
        {"fieldKey": "operatingModel", "proposedValue": "직접 판매"},
    ], concept)
    assert [item["fieldKey"] for item in passed] == ["price"]
    assert rejected[0]["rejectionReason"]
