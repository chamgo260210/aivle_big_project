package com.aivle.backend.taskrun.contract;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 드리프트 계약의 <b>Java 쪽 사본</b>. 정본은 {@code ai/app/validation/drift.py} 다.
 *
 * <p>왜 사본을 두나. 다듬기 루프를 도는 것은 Spring 이고, 「이 제안이 계약 안인가」를
 * 물어야 하는 자리가 Java 에도 있다. 두 목록이 갈리면 AI 가 통과시킨 제안을 Java 가
 * 막거나(조용한 무한 루프) 그 반대가 된다 — <b>사업안이 바뀐 채로 검증이 끝난다.</b>
 *
 * <p>두 목록이 같은지는 {@code ai/tests/test_drift_contract_alignment.py} 가 대조한다.
 * <b>한쪽만 고치면 그 테스트가 깬다.</b>
 */
public final class ConceptDriftContract {

    /** 동결 — 이건 구체화가 아니라 <b>다른 사업</b>이다. */
    public static final Set<String> FROZEN_FIELDS = Set.of(
        "sellerRole", "providerRole", "intermediaryRole", "transactionFlow", "paymentFlow",
        "personalDataUsage", "physicalActivities", "partnerRequirements",
        "qualificationRequirements", "advertisingClaims",
        "conceptName", "conceptDefinition", "coreValue", "operatingModel", "platformRole");

    /** 가격이 움직일 수 있는 폭. 원본 대비 ±30%. */
    public static final double PRICE_TOLERANCE = 0.30;

    /** 목록 한 칸당 더하거나 갈아 끼울 수 있는 개수. */
    public static final int LIST_CHANGE_ALLOWANCE = 1;

    /** 다듬을 수 있는 면 → 판정 방식. */
    public static final Map<String, String> REFINABLE_FIELDS = Map.of(
        "price", "PRICE_BAND",
        "channels", "LIST_ADD_OR_SWAP",
        "differentiators", "LIST_ADD_OR_SWAP",
        "targetRegion", "NARROW_ONLY",
        "targetUsers", "NARROW_ONLY",
        "featureSet", "SUBSET_ONLY",
        "revenueModel", "STRUCTURE_ONLY");

    /**
     * SOM 가설 둘은 <b>법률 중립</b>이라 근거 인용이 붙는 한 자유다.
     *
     * <p>⚠ 나머지 가설 5개는 법률 민감이라 여기 없다 — 그쪽을 바꾸면 {@code DELTA_LEGAL} 을
     * 거쳐야 하고, 우회로를 만들면 법률 검토를 건너뛴 컨셉이 하류로 나간다.
     */
    public static final List<String> FREE_WITH_EVIDENCE_FIELDS = List.of(
        "preMarketSomShare", "preMarketSom");

    /** BM 4칸은 자유. 단 {@code keyPartners} 는 동결된 파트너 요건과 어휘가 겹치면 기각된다. */
    public static final List<String> FREE_BM_FIELDS = List.of(
        "keyActivities", "keyResources", "keyPartners", "customerRelationships");

    private ConceptDriftContract() {
    }
}
