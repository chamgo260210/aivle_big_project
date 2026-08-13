package com.aivle.backend.journey;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Market Seed → 시장조사 엔진의 {@code concept.json}.
 *
 * <p><b>왜 이것이 필요한가.</b> 지금까지 시장조사가 태운 컨셉은 사업안이 아니라 AI 서버가
 * 들고 있는 <b>견본 셋</b>이었다({@code research.pipeline.CONCEPTS}). 화면은
 * {@code startMarketResearch(conceptKey, today(), null)} 로 컨셉 자리에 <b>항상 null</b> 을
 * 보냈고, AI 는 그 자리를 아예 읽지 않았다. 이 클래스가 그 죽은 자리를 채운다.
 *
 * <h2>⚠ 최상위 키를 늘리면 수집이 죽는다</h2>
 * AI 쪽 {@code run.py:37} 이 {@code Concept(**{언더스코어 아닌 키})} 를 만들고
 * {@code schema.py:53-64} 의 필드는 <b>정확히 아홉 개</b>다 —
 * {@code concept_id·name·problem·target·solution·region·hypotheses·price_hypothesis_krw·constraint}.
 * {@code revenueModel} 같은 이름을 최상위에 넣으면 <b>{@code TypeError} 로 죽는다.</b>
 * 그래서 나머지는 전부 <b>언더스코어 키</b>로 간다 — {@code load_concept} 이 걸러내므로
 * 수집 프롬프트에 넘어가지 않고, 판정·계획 층({@code verdict}·{@code canvas}·
 * {@code bm_adapter}·{@code slot_harness})만 읽는다.
 *
 * <h2>⚠ {@code hypotheses} 는 반드시 비운다</h2>
 * {@code Concept.research_view()} 가 이 필드를 <b>A1·A3 수집 프롬프트에 그대로 넘긴다</b>.
 * 가설을 여기 적으면 「그 값 근처만 근거로 모으는」 자기확인 회로가 된다(절대 규칙 6).
 * 가설은 {@code _hypotheses_v2} 로 간다.
 *
 * <h2>⚠ 지어내지 않는다</h2>
 * 가격은 {@link TwinSurveyStimulusDraftService#priceKrw(String)} 로 <b>깨끗하게 읽히는
 * 것만</b> 숫자가 되고 나머지는 {@code null} 이다. 차별점 비교축은 사업안이 한 문장으로만
 * 주는데 엔진은 {@code [{축, 우리_값}]} 을 요구하므로(verdict.py:652-671) <b>빈 배열로 둔다</b> —
 * 그러면 도장이 {@code 축_부재} 로 나가고, 그것이 정직한 결과다. 업종 분류도 KSIC 코드가
 * 아니면 코드 칸을 만들지 않는다.
 */
@Component
public class ResearchConceptFactory {

    /**
     * 계열은 <b>「C」로 고정</b>이다 — 제품 범위를 「대기업 신사업 × 계열 C(시장 거래액 ×
     * 점유율)」 한 줄기로 좁혔다. 계열 판별 관문을 만들지 않는다.
     *
     * <p><b>고른 기준은 고객 단위가 아니라 「채울 수 있는가」다.</b> 자세한 근거와 대가는
     * 아래 {@link #SERIES_NOTE} 에 값으로 실려 나간다 — 이 자리에 두 번 적으면 갈라진다.
     *
     * <p>⚠ <b>알고 두는 위험:</b> 신사업이 개인 대상 <b>서비스</b>(구독 앱 등)면 그 시장의
     * 거래액 통계가 없어 TAM 이 미확보로 남는다. {@code harness/gate.py} 와
     * {@code slot_harness.py} 가 이 값으로 TAM 템플릿을 고른다.
     * 계열을 실제로 판별하게 되는 날 이 상수가 첫 번째로 없어져야 할 것이다.
     */
    private static final String SERIES = "C";
    private static final String SERIES_WHY =
        "시장 거래액 × 점유율로 TAM 을 세운다 — 대기업 신사업은 고객이 개인이고 제품을 "
        + "사는 구조라, 사업체 수는 공급자를 세는 축이지 제품 시장의 크기가 아니다";
    private static final String SERIES_NOTE =
        "계열을 판별하지 않고 C 로 고정한다(제품 결정). "
        // **A 에서 C 로 바꾼 이유는 「식품이라서」가 아니라 「채울 수 있어서」다.**
        //   T2(계열 A·B) 는 자리가 다섯이다 — 사업체수·세그먼트비중·침투율·단가·연환산.
        //   T7(계열 C) 는 둘이다 — 시장거래액·추정점유율.
        //   판 ㉔ 이 그 차이로 죽었다: 계열 E 가 override 없이 T2 를 받아 **자리 4개를
        //   못 채우고** 죽었다(백로그 71). 침투율(도입률)은 web 계량이라 만성적으로 빈다.
        //   반면 판 ㉜ 실측에서 T7 의 두 칸은 실제로 찼다 —
        //   시장거래액 = KOSIS DT_1KE10041 냉동 간편식 38.0조(등급 **확정**),
        //   추정점유율 = 아직 없는 브랜드라 관측 불가이므로 observable=false 가정
        //   (어휘가 그렇게 정해 두었다).
        + "고객이 개인이면 B(인구 × 침투율 × 단가)가 더 곧지만, B 도 T2 라 같은 자리에서 "
        + "죽는다 — 그래서 **고객 단위가 아니라 채울 수 있는 구조**로 골랐다. "
        + "⚠ 알고 두는 위험은 방향이 반대다: 신사업이 **개인 대상 서비스**(구독 앱 등)면 "
        + "그 시장의 거래액 통계가 없어 TAM 이 미확보로 남는다. "
        + "⚠ 거래액(GMV) ≠ 매출이다 — 플랫폼을 거쳐 간 금액이지 누구의 매출도 아니다. "
        + "이 경계는 캔버스까지 따라간다.";

    /**
     * 원장 디렉터리 이름이 될 수 있는 글자. AI 쪽 {@code runner._SAFE_RUN_ID} 와 같은 결이다 —
     * 여기서 안 막으면 {@code conceptId} 가 그대로 경로가 된다.
     */
    private static final java.util.regex.Pattern SAFE_LABEL =
        java.util.regex.Pattern.compile("[A-Za-z0-9._-]{1,64}");

    /** {@code BmPlanPreparationService.CONSTRAINT_KEYS} 와 같아야 한다. 정수만 나른다. */
    private static final List<String> CONSTRAINT_KEYS = List.of("budget_krw", "months", "team");

    private final ObjectMapper mapper;

    public ResearchConceptFactory(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * @param conceptId   컨셉 식별자. <b>원장 디렉터리 이름이 되므로</b> 프로젝트끼리 겹치지
     *                    않아야 하고 경로에 쓸 수 있는 글자여야 한다. 정본은
     *                    {@code ConceptPortfolioSelection.conceptId}(UUID)다 —
     *                    스냅샷 안의 {@code conceptId} 는 AI 후보 id(「C1」 같은 값)라
     *                    <b>다른 프로젝트와 겹친다</b>.
     * @param snapshot    {@code market-analysis-seed-snapshot-v1} 본문
     *                    ({@code MarketAnalysisSeedSnapshot.snapshotJson})
     * @param constraints {@link BmPlanPreparationService} 의 비용 세 칸. 비어 있어도 된다 —
     *                    사용자가 BM 앞 화면을 아직 안 지났으면 비는 것이 정상이고,
     *                    {@code constraint} 는 어차피 수집에 넘어가지 않는다(규칙 6).
     */
    public ObjectNode build(String conceptId, JsonNode snapshot, JsonNode constraints) {
        return build(conceptId, snapshot, constraints, null);
    }

    /**
     * @param competitorSeeds {@code _경쟁_씨앗} 블록. 사용자가 하나도 안 적었으면
     *                        {@code null} 이고, 그때는 <b>칸 자체를 만들지 않는다</b> —
     *                        빈 블록을 실으면 하네스가 「씨앗이 있다」로 읽어
     *                        {@code corp_name} 을 요구하고, 모델이 없는 회사를 지어낸다
     *                        ({@code slot_harness._rule19} · 백로그 39).
     */
    public ObjectNode build(String conceptId, JsonNode snapshot, JsonNode constraints,
                            JsonNode competitorSeeds) {
        JsonNode concept = snapshot.path("selectedConcept");
        JsonNode identity = concept.path("identity");
        JsonNode solution = concept.path("solution");
        JsonNode hypotheses = snapshot.path("finalHypotheses");

        String name = text(identity.path("conceptName"));
        if (conceptId == null || !SAFE_LABEL.matcher(conceptId).matches() || name.isBlank()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                "사업안 스냅샷에 컨셉 이름이 없거나 식별자를 원장 이름으로 쓸 수 없다 — "
                + "시장조사에 태울 재료가 아니다");
        }
        String region = text(hypotheses.path("targetRegion").path("value"));

        ObjectNode root = mapper.createObjectNode();
        // ── 엔진이 dataclass 로 받는 아홉 칸. 이름도 개수도 늘리지 않는다 ──────
        root.put("concept_id", conceptId);
        root.put("name", name);
        root.put("problem", text(solution.path("problemScenario")));
        root.put("target", target(text(identity.path("targetUsers")), region));
        root.put("solution", text(solution.path("solutionMechanism")));
        if (!region.isBlank()) root.put("region", region);
        root.putArray("hypotheses");                       // 규칙 6 — 반드시 빈 배열
        Long price = TwinSurveyStimulusDraftService.priceKrw(
            text(hypotheses.path("price").path("value")));
        if (price == null) root.putNull("price_hypothesis_krw");
        else root.put("price_hypothesis_krw", price);
        root.set("constraint", constraint(constraints));

        // ── 언더스코어 칸. 수집은 못 보고 판정·계획 층만 읽는다 ────────────────
        ObjectNode series = root.putObject("_계열");
        series.put("계열", SERIES);
        series.put("왜", SERIES_WHY);
        series.put("_고정_사유", SERIES_NOTE);
        root.set("_다듬기5", refinement(identity));
        root.set("_hypotheses_v2", hypothesesV2(hypotheses));
        root.set("_bm_plan", bmPlan(concept, hypotheses));
        if (competitorSeeds != null && competitorSeeds.isObject() && !competitorSeeds.isEmpty()) {
            root.set("_경쟁_씨앗", competitorSeeds.deepCopy());
        }
        return root;
    }

    /**
     * 조사 대상. 사업안의 {@code targetUsers} 가 정본이고, <b>지역 문구가 그 안에 없을 때만</b>
     * 뒤에 붙인다. 항상 붙이면 「서울에서 …」로 시작하는 서술에 지역이 두 번 나온다.
     */
    private static String target(String targetUsers, String region) {
        if (region.isBlank() || targetUsers.contains(region)) return targetUsers;
        return targetUsers.isBlank() ? region : targetUsers + " (" + region + ")";
    }

    /** 비용 세 칸. <b>정수만</b> 옮긴다 — 다른 타입은 조용히 버리지 않고 아예 담지 않는다. */
    private ObjectNode constraint(JsonNode constraints) {
        ObjectNode out = mapper.createObjectNode();
        if (constraints == null || !constraints.isObject()) return out;
        for (String key : CONSTRAINT_KEYS) {
            JsonNode value = constraints.get(key);
            if (value != null && value.isIntegralNumber()) out.put(key, value.longValue());
        }
        return out;
    }

    /**
     * {@code _다듬기5} — 핵심 가치({@code canvas.py:200}·{@code bm_adapter.py:258})와
     * 업종 분류({@code slot_harness.py:369}).
     *
     * <p>⚠ <b>KSIC 코드를 지어내지 않는다.</b> 사업안의 {@code industryCategory} 는 자유
     * 서술이고 코드가 아니다. 명칭만 그대로 나르고, 코드 칸은 만들지 않는다 — 코드가 서는지는
     * 드라이런({@code tools/slot_dryrun.py})이 무료로 확인할 자리다.
     */
    private ObjectNode refinement(JsonNode identity) {
        ObjectNode out = mapper.createObjectNode();
        out.put("3_핵심_가치", text(identity.path("coreValue")));
        ObjectNode industry = out.putObject("4_업종_분류");
        industry.put("명칭", text(identity.path("industryCategory")));
        industry.put("_확인_필요", "KSIC 코드는 확정되지 않았다 — 사업안은 업종 서술만 준다. "
            + "코드는 드라이런에서 stat_code 실재 대조로 확정한다(추측 금지)");
        return out;
    }

    /**
     * 가설 4개 — {@code verdict.py} 의 판정 재료. <b>수집에는 넘어가지 않는다.</b>
     *
     * <p>비교축은 <b>빈 배열</b>이다. 사업안의 {@code differentiators} 는 한 문장인데 엔진은
     * {@code [{축, 우리_값}]} 을 요구한다(verdict.py:652-671). 문장을 축으로 쪼개는 것은
     * 추측이고, 추측한 축은 「검증됨」 도장을 받을 수도 있다 — 그게 더 나쁘다.
     * 빈 채로 두면 도장이 {@code 축_부재} 로 나간다.
     */
    private ObjectNode hypothesesV2(JsonNode hypotheses) {
        ObjectNode out = mapper.createObjectNode();

        String priceText = text(hypotheses.path("price").path("value"));
        ObjectNode revenue = out.putObject("6_수익_가격");
        revenue.put("수익_방식", text(hypotheses.path("revenueModel").path("value")));
        Long price = TwinSurveyStimulusDraftService.priceKrw(priceText);
        if (price == null) revenue.putNull("제안값_krw_월");
        else revenue.put("제안값_krw_월", price);
        revenue.put("_확정_가격_원문", priceText);
        if (price == null) {
            revenue.put("_왜_숫자가_없나", "확정 가격이 원 단위 정수로 깨끗하게 읽히지 않았다 — "
                + "「3만원」을 3 으로 읽는 편보다 안 읽는 편이 낫다");
        }

        String channels = text(hypotheses.path("channels").path("value"));
        ObjectNode channel = out.putObject("7_채널");
        ArrayNode proposed = channel.putArray("제안값");
        for (String item : lines(hypotheses.path("channels").path("value"))) proposed.add(item);
        if (!channels.isBlank()) channel.put("주_채널_가정", channels);

        ObjectNode differentiators = out.putObject("8_차별점");
        differentiators.putArray("비교축");
        differentiators.put("_확정_차별점_원문", text(hypotheses.path("differentiators").path("value")));
        differentiators.put("_왜_비었나", "사업안은 차별점을 한 문장으로 준다. 축 이름을 "
            + "추측해 넣으면 판정이 지어낸 축을 검증하게 된다 — 축_부재로 나가는 것이 정직하다");

        JsonNode share = hypotheses.path("preMarketSomShare").path("value");
        ObjectNode som = out.putObject("9_SOM_초기점유");
        JsonNode percent = share.path("targetSharePercent");
        if (percent.isNumber()) {
            som.put("가정_침투율", percent.decimalValue().divide(BigDecimal.valueOf(100)));
        } else {
            som.putNull("가정_침투율");
        }
        JsonNode horizon = share.path("horizonYears");
        if (horizon.isIntegralNumber()) som.put("가정_기간", "출시 " + horizon.intValue() + "년차");
        ArrayNode assumptions = som.putArray("_가정");
        for (JsonNode item : share.path("assumptions")) {
            if (item.isTextual() && !item.stringValue().isBlank()) assumptions.add(item.stringValue().trim());
        }
        som.put("_지어낸_값_표시", "침투율은 관측 근거가 없는 순수 가정이다(사업안의 AI 제안값). "
            + "근거가 아니라 계산 입력으로만 쓴다");
        return out;
    }

    /**
     * {@code _bm_plan} — 캔버스 「계획 5칸」의 재료({@code bm_adapter.PLAN_FIELDS}).
     *
     * <p>AI 쪽 {@code _CONCEPT_TO_PLAN}(bm_adapter.py:198-208)이 {@code revenueModel}·
     * {@code channels}·{@code operatingModel} 같은 <b>camelCase 최상위 키</b>에서 이 칸들을
     * 파생하는데, 그 이름을 최상위에 두면 수집이 {@code TypeError} 로 죽는다. 그래서
     * <b>같은 대응을 여기서 미리 적용해</b> {@code _bm_plan} 에 담는다 — 그쪽이 첫 번째로
     * 읽는 자리다.
     *
     * <p>{@code customer_relationship} 은 <b>의도적으로 비운다.</b> 사업안에 대응 필드가
     * 없고, {@code solutionMechanism} 에서 유추하면 BM 프롬프트 §5(「입력에 명시된 것만」)를
     * 어긴다. 이 칸은 사용자가 BM 앞 화면에서 채우면 {@code _user_bm_plan} 이 이긴다.
     */
    private ObjectNode bmPlan(JsonNode concept, JsonNode hypotheses) {
        JsonNode solution = concept.path("solution");
        JsonNode operation = concept.path("operation");
        ObjectNode out = mapper.createObjectNode();
        put(out, "revenue_model", text(hypotheses.path("revenueModel").path("value")));
        putList(out, "channel", lines(hypotheses.path("channels").path("value")));
        putList(out, "differentiation", lines(hypotheses.path("differentiators").path("value")));
        putList(out, "key_activities", merge(operation.path("operatingModel"), operation.path("transactionFlow")));
        putList(out, "key_resources", merge(operation.path("platformRole"), solution.path("featureSet")));
        putList(out, "key_partners", merge(operation.path("partnerModel"), operation.path("partnerRequirements")));
        out.put("_출처", "사업안(concept portfolio v2)의 확정 가설과 운영 서술에서 파생했다 — "
            + "관측이 아니라 서술이다");
        return out;
    }

    private static void put(ObjectNode target, String key, String value) {
        if (!value.isBlank()) target.put(key, value);
    }

    private void putList(ObjectNode target, String key, List<String> values) {
        if (values.isEmpty()) return;
        ArrayNode array = target.putArray(key);
        for (String value : values) array.add(value);
    }

    /** 문자열이면 한 칸, 배열이면 그대로. 빈 값은 떨어뜨린다. */
    private static List<String> lines(JsonNode node) {
        return merge(node);
    }

    private static List<String> merge(JsonNode... nodes) {
        List<String> out = new ArrayList<>();
        for (JsonNode node : nodes) {
            if (node == null) continue;
            if (node.isArray()) {
                for (JsonNode item : node) {
                    String value = text(item);
                    if (!value.isBlank()) out.add(value);
                }
            } else {
                String value = text(node);
                if (!value.isBlank()) out.add(value);
            }
        }
        return out;
    }

    private static String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return "";
        return (node.isTextual() ? node.stringValue() : node.asText("")).trim();
    }
}
