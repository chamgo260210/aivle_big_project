# -*- coding: utf-8 -*-
"""다듬기 제안의 **표시용 칸**과 오버레이.

여기서 지키려는 것은 하나다: **표시용 문자열이 계약 판정에 끼어들지 않는다.** 제안이 사람이
읽는 말을 함께 싣게 되면서, 모델이 「말만 바꿔서」 계약을 통과시킬 수 있는 길이 생기면 안 된다.
"""
from __future__ import annotations

from app.tasks.concept_portfolio_v2.selection_models import RefinementProposal
from app.tasks.concept_portfolio_v2.selection_service import (
    _apply_refinement_overlay,
    _display_safe,
    _segment_safe,
)
from app.validation import drift


class TestDriftIgnoresDisplayText:
    """계약은 **값**만 본다. 표시 문자열은 판정에 영향이 없다."""

    def test_display_text_does_not_rescue_a_frozen_field(self):
        proposals = [{
            "fieldKey": "conceptName",
            "proposedValue": "다른 이름",
            "title": "이름을 다듬었어요",
            "beforeText": "원래 이름", "afterText": "다른 이름",
        }]
        passed, rejected = drift.filter_proposals(proposals, {"conceptName": "원래 이름"})
        assert passed == []
        assert rejected[0]["rejectionReason"].startswith("동결된 칸")

    def test_display_text_does_not_block_an_allowed_change(self):
        """반대 방향도 본다 — 표시 문자열이 달라도 값이 계약 안이면 통과한다."""
        proposals = [{
            "fieldKey": "targetUsers",
            "proposedValue": "바쁜 1인 가구 직장인",
            "afterText": "완전히 다른 말이 적혀 있어도",
        }]
        passed, _ = drift.filter_proposals(proposals, {"targetUsers": "바쁜 1인 가구 직장인 누구나"})
        assert len(passed) == 1

    def test_passed_proposals_keep_their_display_fields(self):
        """계약을 지난 뒤에도 표시 칸이 살아 있어야 화면이 그린다."""
        proposals = [{"fieldKey": "price", "proposedValue": 9500,
                      "title": "가격을 시장 안으로 옮겼어요", "afterText": "9,500원대"}]
        passed, _ = drift.filter_proposals(proposals, {"price": 10000})
        assert passed[0]["title"] == "가격을 시장 안으로 옮겼어요"


class TestDisplaySafe:
    """모델이 칸을 빠뜨리거나 모르는 낱말을 넣어도 **라운드가 통째로 죽지 않는다**."""

    def test_unknown_keys_are_dropped_so_the_round_survives(self):
        value = _display_safe({"fieldKey": "price", "proposedValue": 9500,
                               "afterText": "9,500원대", "확신도": 0.9})
        assert "확신도" not in value
        assert RefinementProposal.model_validate({**value, "rationale": "근거"})

    def test_unknown_source_falls_back_to_market(self):
        """모르는 값을 LEGAL 로 읽으면 시장 근거 변경이 「법이 시켰다」로 둔갑한다."""
        assert _display_safe({"source": "LAW"})["source"] == "MARKET"
        assert _display_safe({"source": "LEGAL"})["source"] == "LEGAL"

    def test_missing_display_text_becomes_empty_not_none(self):
        value = _display_safe({"fieldKey": "price"})
        assert value["title"] == "" and value["afterText"] == ""
        assert value["legalRef"] is None

    def test_the_value_itself_is_never_touched(self):
        """⚠ 계약이 이미 판정한 값이다. 여기서 다듬으면 판정과 저장이 갈린다."""
        value = _display_safe({"fieldKey": "channels", "proposedValue": ["자사몰", "쿠팡"]})
        assert value["proposedValue"] == ["자사몰", "쿠팡"]


class TestSegmentSafe:
    def test_out_of_range_reference_becomes_unmarked(self):
        assert _segment_safe({"text": "한 문장", "changeRef": 0})["changeRef"] is None
        assert _segment_safe({"text": "한 문장", "changeRef": 99})["changeRef"] is None
        assert _segment_safe({"text": "한 문장", "changeRef": 2})["changeRef"] == 2


class TestOverlay:
    """오버레이는 **시드 스냅샷에만** 얹힌다."""

    def market(self):
        return {"selectedConcept": {
            "identity": {"conceptName": "그대로", "targetUsers": "바쁜 직장인 누구나"},
            "solution": {"featureSet": ["a", "b", "c"]},
        }}

    def test_overlay_lands_on_the_seed(self):
        market = self.market()
        _apply_refinement_overlay(market, {"targetUsers": "바쁜 1인 가구 직장인",
                                           "featureSet": ["a", "b"]})
        assert market["selectedConcept"]["identity"]["targetUsers"] == "바쁜 1인 가구 직장인"
        assert market["selectedConcept"]["solution"]["featureSet"] == ["a", "b"]
        # 얹지 않은 칸은 그대로다 — 오버레이는 덮개지 대체가 아니다.
        assert market["selectedConcept"]["identity"]["conceptName"] == "그대로"

    def test_unknown_fields_are_ignored(self):
        """계약에 없는 칸이 오버레이로 새어 들어와도 시드를 오염시키지 않는다."""
        market = self.market()
        _apply_refinement_overlay(market, {"conceptName": "다른 사업"})
        assert market["selectedConcept"]["identity"]["conceptName"] == "그대로"

    def test_no_overlay_is_a_no_op(self):
        market = self.market()
        _apply_refinement_overlay(market, None)
        _apply_refinement_overlay(market, {})
        assert market == self.market()


class TestContractMatchesRealConceptShapes:
    """계약이 **실제 컨셉의 자료형**을 읽는다.

    ⚠ 2026-08-13 실측: 확정된 컨셉은 `price`·`channels`·`differentiators` 를 **문자열**로 들고
    있다(「1팩 8,900원」, 「자사몰 정기구독, 대형 이커머스 입점(…), …」). 계약이 숫자·배열만
    받던 동안에는 모델이 원본과 같은 모양으로 답해도 전부 기각됐다 —
    「가격이 숫자가 아니다」·「뺀 것 55 · 더한 것 4」.

    ⚠ **규칙을 느슨하게 한 것이 아니다.** ±30% 와 ±1개는 그대로다. 읽는 모양만 맞췄다.
    """

    CONCEPT = {
        "price": "1팩 8,900원",
        "channels": "자사몰 정기구독, 대형 이커머스 입점(쿠팡·마켓컬리·네이버쇼핑), 편의점 냉동 매대",
        "differentiators": "1인분 정량 설계, 10분 이내 단일 조리",
    }

    def test_price_is_read_from_words(self):
        drift.check("price", "1팩 8,900원", "1팩 9,500원", self.CONCEPT)

    def test_package_count_is_not_mistaken_for_the_price(self):
        """「1팩 8,900원」의 첫 수는 포장 단위다. 그것을 금액으로 읽으면 폭 판정이 무너진다."""
        assert drift._amount("1팩 8,900원") == 8900.0

    def test_price_band_still_holds(self):
        try:
            drift.check("price", "1팩 8,900원", "1팩 20,000원", self.CONCEPT)
        except drift.DriftRejection as failure:
            assert "30%" in failure.reason
        else:
            raise AssertionError("±30% 를 넘는 값이 통과했다 — 계약이 죽었다")

    def test_written_list_is_compared_item_by_item(self):
        proposed = ("자사몰 정기구독, 대형 이커머스 입점(쿠팡·마켓컬리·네이버쇼핑), "
                    "편의점 냉동 매대, 오피스 무인 냉동고")
        drift.check("channels", self.CONCEPT["channels"], proposed, self.CONCEPT)

    def test_parenthesised_middots_stay_inside_one_item(self):
        items = drift.as_items("자사몰 정기구독, 대형 이커머스 입점(쿠팡·마켓컬리·네이버쇼핑)")
        assert items == ["자사몰 정기구독", "대형 이커머스 입점(쿠팡·마켓컬리·네이버쇼핑)"]

    def test_a_list_answer_against_a_written_current_value_still_works(self):
        """모델이 배열로 답해도 된다 — 양쪽을 같은 모양으로 편다."""
        proposed = ["1인분 정량 설계", "10분 이내 단일 조리", "급속냉동으로 보존료 최소화"]
        drift.check("differentiators", self.CONCEPT["differentiators"], proposed, self.CONCEPT)

    def test_wholesale_replacement_is_still_rejected(self):
        try:
            drift.check("channels", self.CONCEPT["channels"], "백화점 팝업, 홈쇼핑, 방문판매", self.CONCEPT)
        except drift.DriftRejection as failure:
            # ⚠ **문구를 그대로 못박지 않는다.** 이 사유는 화면 「못 푼 것」에 그대로 서므로
            #   사람 말로 다듬을 일이 생긴다(2026-08-15 에 한 번 다듬었다). 재야 할 것은
            #   「한 번에 몇 개까지인지 말해 주는가」이지 특정 낱말이 아니다.
            assert str(drift.LIST_CHANGE_ALLOWANCE) in failure.reason, failure.reason
        else:
            raise AssertionError("통째로 갈아 끼운 값이 통과했다 — 계약이 죽었다")


class TestFieldAliases:
    """가설 이름(`CHANNELS`)으로 와도 칸 이름(`channels`)으로 판정한다."""

    def test_hypothesis_name_is_canonicalised_before_judging(self):
        passed, rejected = drift.filter_proposals(
            [{"fieldKey": "CHANNELS", "proposedValue": "자사몰, 쿠팡, 편의점 냉동 매대"}],
            {"channels": "자사몰, 쿠팡"})
        assert rejected == []
        assert passed[0]["fieldKey"] == "channels"

    def test_unknown_names_are_still_rejected(self):
        _, rejected = drift.filter_proposals(
            [{"fieldKey": "MYSTERY_FIELD", "proposedValue": "x"}], {})
        assert rejected[0]["rejectionReason"] == "드리프트 계약에 없는 칸이다"
