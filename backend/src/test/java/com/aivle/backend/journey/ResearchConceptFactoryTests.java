package com.aivle.backend.journey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aivle.backend.common.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Market Seed → {@code concept.json} 변환. <b>엔진이 받아들이는 모양인지</b>를 잰다.
 *
 * <p>여기서 지키는 것은 셋이다.
 * <ol>
 *   <li><b>최상위 키가 아홉 개를 넘지 않는다.</b> AI 쪽 {@code run.py:37} 이
 *       {@code Concept(**{언더스코어 아닌 키})} 를 만들고 {@code schema.py:53-64} 의
 *       필드가 정확히 아홉이다 — 하나만 늘려도 <b>수집이 {@code TypeError} 로 죽는다.</b>
 *       그리고 그것은 <b>런타임에만</b> 터진다.</li>
 *   <li><b>{@code hypotheses} 가 빈 배열이다.</b> {@code Concept.research_view()} 가 이
 *       필드를 수집 프롬프트에 그대로 넘긴다 — 채우면 자기확인 회로가 된다(절대 규칙 6).</li>
 *   <li><b>못 읽는 값을 지어내지 않는다.</b> 가격·비교축·업종 코드.</li>
 * </ol>
 */
class ResearchConceptFactoryTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** 정본은 `ConceptPortfolioSelection.conceptId`(UUID)다 — 원장 이름이 되므로 겹치면 안 된다. */
    private static final String CONCEPT_ID = "7b1f0c2e-9a44-4d31-8f0e-2c5b6d7a1e90";
    private final ResearchConceptFactory factory = new ResearchConceptFactory(MAPPER);

    /** {@code schema.py:53-64} 의 {@code Concept} dataclass 필드. <b>이 목록이 계약이다.</b> */
    private static final List<String> ENGINE_FIELDS = List.of(
        "concept_id", "name", "problem", "target", "solution", "region",
        "hypotheses", "price_hypothesis_krw", "constraint");

    private static JsonNode seed(String price, String differentiators, String channels) {
        return MAPPER.readTree("""
            {
              "contract": "market-analysis-seed-snapshot-v1",
              "schemaVersion": "2.0",
              "conceptId": "CPT-STORE-OPS",
              "selectedConcept": {
                "identity": {
                  "conceptName": "소상공인 매장 운영 SaaS",
                  "coreValue": "주문·재고·직원 관리를 한 화면에서 끝낸다",
                  "targetUsers": "직원 5인 이하 외식업 매장을 직접 운영하는 사업주",
                  "industryCategory": "외식업 대상 매장 운영 소프트웨어",
                  "researchScope": "대한민국 전국"
                },
                "solution": {
                  "problemScenario": "주문·재고·근태가 서로 다른 도구에 흩어져 마감마다 손으로 맞춘다",
                  "solutionMechanism": "POS 주문을 재고와 직원 일정에 자동으로 연결한다",
                  "featureSet": ["주문 연동", "재고 차감", "근태 집계"]
                },
                "operation": {
                  "operatingModel": "월 구독 SaaS 직판",
                  "transactionFlow": ["매장이 구독한다", "월 정액을 자동 결제한다"],
                  "platformRole": "매장 데이터 통합 처리",
                  "partnerModel": "POS 제조사 제휴",
                  "partnerRequirements": ["POS 연동 규격 공개"],
                  "sellerRole": "직접 판매"
                },
                "canonicalHash": "sha256:0"
              },
              "finalHypotheses": {
                "targetRegion": {"value": "대한민국"},
                "revenueModel": {"value": "월 정액 구독"},
                "price": {"value": %s},
                "channels": {"value": %s},
                "differentiators": {"value": %s},
                "preMarketSomShare": {"value": {
                  "targetSharePercent": 0.5, "horizonYears": 3,
                  "assumptions": ["초기 1년은 수도권 중심"]}}
              }
            }""".formatted(price, channels, differentiators));
    }

    private static JsonNode seed() {
        return seed("\"월 49,000원\"", "\"마감 자동화가 경쟁 제품보다 빠르다\"",
            "\"POS 제조사 제휴 영업\"");
    }

    private static ObjectNode constraints(String json) {
        return (ObjectNode) MAPPER.readTree(json);
    }

    @Test
    @DisplayName("엔진 dataclass 필드 밖의 최상위 키는 전부 언더스코어다 — 아니면 수집이 TypeError 로 죽는다")
    void topLevelKeysNeverExceedTheEngineDataclass() {
        ObjectNode concept = factory.build(CONCEPT_ID, seed(), constraints("{}"));

        List<String> plain = new ArrayList<>();
        for (String name : concept.propertyNames()) if (!name.startsWith("_")) plain.add(name);
        assertThat(plain).containsExactlyInAnyOrderElementsOf(ENGINE_FIELDS);
    }

    @Test
    @DisplayName("hypotheses 는 빈 배열이다 — 채우면 수집 프롬프트로 들어가 자기확인 회로가 된다")
    void hypothesesStayEmpty() {
        ObjectNode concept = factory.build(CONCEPT_ID, seed(), constraints("{}"));

        assertThat(concept.get("hypotheses").isArray()).isTrue();
        assertThat(concept.get("hypotheses")).isEmpty();
    }

    @Test
    @DisplayName("사업안의 일곱 가정과 서술이 제자리에 실린다")
    void seedFieldsLandInTheirEngineSlots() {
        ObjectNode concept = factory.build(CONCEPT_ID, seed(), constraints("{}"));

        assertThat(concept.get("concept_id").stringValue()).isEqualTo(CONCEPT_ID);
        assertThat(concept.get("name").stringValue()).isEqualTo("소상공인 매장 운영 SaaS");
        assertThat(concept.get("problem").stringValue()).contains("흩어져");
        assertThat(concept.get("solution").stringValue()).contains("POS 주문");
        assertThat(concept.get("region").stringValue()).isEqualTo("대한민국");
        assertThat(concept.get("price_hypothesis_krw").intValue()).isEqualTo(49_000);
        assertThat(concept.at("/_다듬기5/3_핵심_가치").stringValue()).contains("한 화면");
        assertThat(concept.at("/_hypotheses_v2/6_수익_가격/수익_방식").stringValue())
            .isEqualTo("월 정액 구독");
        assertThat(concept.at("/_hypotheses_v2/6_수익_가격/제안값_krw_월").intValue()).isEqualTo(49_000);
        assertThat(concept.at("/_hypotheses_v2/7_채널/주_채널_가정").stringValue())
            .isEqualTo("POS 제조사 제휴 영업");
    }

    @Test
    @DisplayName("target 은 지역이 이미 들어 있으면 두 번 붙이지 않는다")
    void regionIsAppendedOnlyWhenMissing() {
        assertThat(factory.build(CONCEPT_ID, seed(), constraints("{}")).get("target").stringValue())
            .isEqualTo("직원 5인 이하 외식업 매장을 직접 운영하는 사업주 (대한민국)");

        ObjectNode withRegion = (ObjectNode) seed();
        ((ObjectNode) withRegion.at("/selectedConcept/identity"))
            .put("targetUsers", "대한민국 전역의 외식업 사업주");
        assertThat(factory.build(CONCEPT_ID, withRegion, constraints("{}")).get("target").stringValue())
            .isEqualTo("대한민국 전역의 외식업 사업주");
    }

    @Test
    @DisplayName("가격이 깨끗하게 안 읽히면 null 이다 — 「3만원」을 3 으로 읽지 않는다")
    void priceIsNullWhenItCannotBeReadCleanly() {
        ObjectNode concept = factory.build(CONCEPT_ID,
            seed("\"월 3만원 수준\"", "\"빠르다\"", "\"직판\""), constraints("{}"));

        assertThat(concept.get("price_hypothesis_krw").isNull()).isTrue();
        assertThat(concept.at("/_hypotheses_v2/6_수익_가격/제안값_krw_월").isNull()).isTrue();
        assertThat(concept.at("/_hypotheses_v2/6_수익_가격/_확정_가격_원문").stringValue())
            .isEqualTo("월 3만원 수준");
    }

    @Test
    @DisplayName("비교축은 빈 배열이다 — 한 문장을 축으로 쪼개면 지어낸 축을 검증하게 된다")
    void comparisonAxesStayEmpty() {
        ObjectNode concept = factory.build(CONCEPT_ID, seed(), constraints("{}"));

        assertThat(concept.at("/_hypotheses_v2/8_차별점/비교축").isArray()).isTrue();
        assertThat(concept.at("/_hypotheses_v2/8_차별점/비교축")).isEmpty();
        assertThat(concept.at("/_hypotheses_v2/8_차별점/_확정_차별점_원문").stringValue())
            .isEqualTo("마감 자동화가 경쟁 제품보다 빠르다");
    }

    @Test
    @DisplayName("업종은 명칭만 나른다 — KSIC 코드는 사업안이 주지 않으므로 만들지 않는다")
    void industryCarriesNameOnlyWithoutAGuessedKsicCode() {
        JsonNode industry = factory.build(CONCEPT_ID, seed(), constraints("{}")).at("/_다듬기5/4_업종_분류");

        assertThat(industry.get("명칭").stringValue()).isEqualTo("외식업 대상 매장 운영 소프트웨어");
        assertThat(industry.has("코드")).isFalse();
        assertThat(industry.has("표준")).isFalse();
    }

    @Test
    @DisplayName("침투율은 퍼센트를 100 으로 나눈 값이다 — 0.5% 는 0.005 다")
    void sharePercentBecomesARatio() {
        ObjectNode concept = factory.build(CONCEPT_ID, seed(), constraints("{}"));

        assertThat(concept.at("/_hypotheses_v2/9_SOM_초기점유/가정_침투율").decimalValue())
            .isEqualByComparingTo("0.005");
        assertThat(concept.at("/_hypotheses_v2/9_SOM_초기점유/가정_기간").stringValue())
            .isEqualTo("출시 3년차");
    }

    @Test
    @DisplayName("계획 5칸의 재료가 _bm_plan 으로 간다 — camelCase 최상위 키는 수집을 죽인다")
    void planMaterialGoesUnderTheUnderscoreKey() {
        JsonNode plan = factory.build(CONCEPT_ID, seed(), constraints("{}")).get("_bm_plan");

        assertThat(plan.get("revenue_model").stringValue()).isEqualTo("월 정액 구독");
        assertThat(plan.get("channel").get(0).stringValue()).isEqualTo("POS 제조사 제휴 영업");
        assertThat(plan.get("key_activities")).hasSize(3);       // operatingModel + transactionFlow 2
        assertThat(plan.get("key_resources")).hasSize(4);        // platformRole + featureSet 3
        assertThat(plan.get("key_partners")).hasSize(2);         // partnerModel + partnerRequirements
        // 사업안에 대응 필드가 없다. 유추하면 BM 프롬프트 §5(「입력에 명시된 것만」)를 어긴다.
        assertThat(plan.has("customer_relationship")).isFalse();
    }

    @Test
    @DisplayName("계열은 C 로 고정이다 — 판별 관문을 만들지 않았다")
    void seriesIsPinnedToC() {
        JsonNode series = factory.build(CONCEPT_ID, seed(), constraints("{}")).get("_계열");

        // **왜 C 인가 — 「식품이라서」가 아니라 「채울 수 있어서」다.**
        //   T2(계열 A·B) 는 자리가 다섯(사업체수·세그먼트비중·침투율·단가·연환산)이고
        //   T7(계열 C) 는 둘(시장거래액·추정점유율)이다. 판 ㉔ 에서 계열 E 가 T2 를 받아
        //   **자리 4개를 못 채우고 죽었다**(백로그 71). 고객이 개인이면 B 가 더 곧지만
        //   B 도 T2 라 같은 데서 죽는다.
        assertThat(series.get("계열").stringValue()).isEqualTo("C");
        assertThat(series.get("왜").stringValue()).isNotBlank();
        // 고정에는 **대가**가 있고, 그 대가가 적혀 있어야 다음 사람이 재검토할 수 있다.
        // (개인 대상 **서비스** 신사업이면 거래액 통계가 없어 TAM 이 미확보로 남는다.)
        assertThat(series.get("_고정_사유").stringValue()).contains("서비스");
        // 거래액(GMV) ≠ 매출 — 이 경계는 캔버스까지 따라가야 한다.
        assertThat(series.get("_고정_사유").stringValue()).contains("거래액");
    }

    @Test
    @DisplayName("비용 세 칸은 정수만 옮긴다 — 없으면 빈 객체이고 지어내지 않는다")
    void constraintCarriesIntegersOnly() {
        assertThat(factory.build(CONCEPT_ID, seed(), constraints("{}")).get("constraint")).isEmpty();

        JsonNode constraint = factory.build(CONCEPT_ID, seed(), constraints(
            "{\"budget_krw\":50000000,\"months\":10,\"team\":2,\"unknown\":5}")).get("constraint");
        assertThat(constraint.propertyNames())
            .containsExactlyInAnyOrder("budget_krw", "months", "team");
        assertThat(constraint.get("budget_krw").longValue()).isEqualTo(50_000_000L);
    }

    @Test
    @DisplayName("이름 없는 스냅샷은 재료가 아니다 — 조용히 빈 컨셉을 만들지 않는다")
    void anUnnamedSnapshotIsRejected() {
        ObjectNode broken = (ObjectNode) seed();
        ((ObjectNode) broken.at("/selectedConcept/identity")).put("conceptName", "  ");

        assertThatThrownBy(() -> factory.build(CONCEPT_ID, broken, constraints("{}")))
            .isInstanceOf(BusinessException.class);
    }
}
