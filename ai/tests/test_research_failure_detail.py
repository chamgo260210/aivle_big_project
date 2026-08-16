# -*- coding: utf-8 -*-
"""실패 원인 문장이 버려지지 않는다.

2026-08-13 실측: 유료 BM 실행이 15초 만에 죽었는데 남은 것이
`EXECUTION_FAILED / TRANSIENT_EXECUTION_FAILURE` 두 낱말뿐이라 원인을 못 밝혔다.
`_fail()` 이 `detail` 을 **받아 놓고 버리고** 있었고, 로그도 그걸 안 찍었다.
호출부는 전부(`pipeline.py:685,712,723` …) 원인 문장을 만들어 넘기고 있었다.

⚠ 이 문장은 **서버 로그까지만** 간다. 화면은 `MarketResearchService.safeErrorReason` 이
계약 어휘만 통과시킨다 — 여기서 화면 노출을 테스트하지 않는 이유다.
"""
from __future__ import annotations

import pytest

from app.research.runner import _fail


def test_원인_문장이_실려_나간다():
    failure = _fail("EXECUTION_FAILED", "TRANSIENT_EXECUTION_FAILURE",
                    "bm_model 실패 — ValueError: concept_id 불일치")
    assert failure.safe_provider_message == "bm_model 실패 — ValueError: concept_id 불일치"


def test_상세가_없으면_None_이다():
    """빈 문자열을 실어 「메시지가 있는데 비었다」로 읽히게 하지 않는다."""
    assert _fail("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION").safe_provider_message is None


def test_코드와_사유는_그대로다():
    failure = _fail("DEADLINE_EXCEEDED", "REQUEST_DEADLINE_EXCEEDED", "예산을 넘겼다")
    assert (failure.code, failure.reason) == ("DEADLINE_EXCEEDED", "REQUEST_DEADLINE_EXCEEDED")
    assert (failure.status_code, failure.retryable) == (504, True)


def test_등록되지_않은_어휘는_거부한다():
    with pytest.raises(AssertionError):
        _fail("EXECUTION_FAILED", "MADE_UP_REASON", "…")


def test_로그가_상세를_찍는다():
    """포맷 문자열에 `detail=` 자리가 있어야 한다 — 없으면 다시 눈이 먼다."""
    import inspect

    from app.api import executions

    source = inspect.getsource(executions)
    assert "detail=%s" in source, "실패 로그가 원인 문장을 안 찍는다"
    assert "safe_provider_message" in source
