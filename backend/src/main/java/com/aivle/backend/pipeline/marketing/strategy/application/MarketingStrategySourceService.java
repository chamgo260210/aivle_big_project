package com.aivle.backend.pipeline.marketing.strategy.application;

import com.aivle.backend.pipeline.finalreport.application.FinalReportService;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@lombok.extern.slf4j.Slf4j
public class MarketingStrategySourceService {

    private static final Set<String> INCLUDED = Set.of(
        "PROJECT",
        "CURRENT_CONCEPT",
        "MARKET",
        "BUSINESS_MODEL",
        "LAUNCH_TECHNOLOGY",
        "LAUNCH_OPERATIONS",
        "FINANCE",
        "FINANCE_REPORT",
        "MARKET_INTERVIEW"
    );

    private static final List<String> REQUIRED = List.of(
        "CURRENT_CONCEPT"
    );

    private static final List<String> OPTIONAL = List.of(
        "MARKET", "BUSINESS_MODEL", "LAUNCH_TECHNOLOGY", "LAUNCH_OPERATIONS",
        "FINANCE", "FINANCE_REPORT", "MARKET_INTERVIEW"
    );

    private final FinalReportService finalReports;
    private final ObjectMapper mapper;

    public SourceBundle inspect(
        Long ownerId,
        Long projectId
    ) {
        var catalog = finalReports.currentSourceCatalog(ownerId, projectId);

        ArrayNode manifest = mapper.createArrayNode();

        JsonNode sourceItems = catalog.manifest();
        if (sourceItems.isArray()) {
            for (JsonNode item : sourceItems) {
                String type = item.path("type").asText();

                if (INCLUDED.contains(type)) {
                    manifest.add(item.deepCopy());
                }
            }
        }

        ObjectNode sources = mapper.createObjectNode();
        int droppedEvidence = 0;
        for (String type : INCLUDED) {
            if (catalog.sources().has(type)) {
                JsonNode copy = catalog.sources().path(type).deepCopy();
                droppedEvidence += stripRawEvidence(copy);
                sources.set(type, copy);
            }
        }
        if (droppedEvidence > 0) log.info(
            "Marketing strategy source trimmed projectId={} droppedEvidence={}", projectId, droppedEvidence);

        List<String> requiredMissing = REQUIRED.stream()
            .filter(type -> !sources.has(type))
            .toList();
        List<String> missing = java.util.stream.Stream.concat(
            requiredMissing.stream(), OPTIONAL.stream().filter(type -> !sources.has(type))
        ).distinct().toList();

        String hash = catalog.strategySourceHash();

        return new SourceBundle(
            manifest,
            sources,
            hash,
            missing,
            requiredMissing.isEmpty()
        );
    }

    /**
     * 시장조사와 BM 결과는 각자 **같은** 근거 목록(이 판에서 1,086건 · 각 1,089 kB)을 물고 온다.
     * 그대로 실으면 `MarketingStrategyService.MAX_INPUT_BYTES`(1,800,000)를 넘어 413 으로 막힌다.
     *
     * <p>전략이 인용하는 것은 **`sourceManifest` 의 `TYPE:id`** 이지 개별 근거 레코드가 아니다
     * (`marketing_strategy/service.py` 의 `allowed_refs`). 그래서 원자료 목록을 빼도 인용은 깨지지 않고,
     * 판단에 쓰는 알맹이(`market`·`scorecard`·`report`·`synthesis` 등 46 kB)는 그대로 남는다.
     *
     * <p>⚠ 최종 보고서(모듈 6)는 다르다 — 거기서는 `evidenceKey` 단위로 인용하고 키가 없으면
     * `FINAL_REPORT_EVIDENCE_KEY_INVALID` 로 죽는다. 그래서 이 정리는 **마케팅 경로에서만** 한다.
     * 공용인 `FinalReportService` 의 카탈로그는 건드리지 않는다.
     */
    private int stripRawEvidence(JsonNode source) {
        // CurrentSourceCatalog 는 type -> source.data() 를 **그대로** 담는다
        // (FinalReportService:148). 한 겹 더 있다고 넘겨짚으면 조용히 아무것도 안 지운다.
        //
        // 그리고 맨 위만 훑어서는 모자란다 — 재무 스냅샷의 `upstreamReferences` 가
        // 시장 근거 목록과 **BM 결과 전체**를 다시 품고 있어, 같은 뭉치가 네 벌이 된다
        // (실측: FINANCE 소스 하나가 2,118 kB). 그래서 트리 전체를 훑는다.
        if (!source.isObject() && !source.isArray()) return 0;
        int dropped = 0;
        if (source.isArray()) {
            for (JsonNode child : source) dropped += stripRawEvidence(child);
            return dropped;
        }
        ObjectNode object = (ObjectNode) source;
        for (String name : List.copyOf(object.propertyNames())) {
            JsonNode child = object.path(name);
            if ("evidence".equals(name) && child.isArray() && !child.isEmpty()) {
                dropped += child.size();
                object.set("evidence", mapper.createArrayNode());
                object.put("evidenceOmitted", child.size());
                continue;
            }
            dropped += stripRawEvidence(child);
        }
        return dropped;
    }

    public record SourceBundle(
        ArrayNode manifest,
        ObjectNode sources,
        String hash,
        List<String> missing,
        boolean ready
    ) {
        public boolean ready() {
            return ready;
        }

        public ObjectNode toInput(
            ObjectMapper mapper,
            Long projectId
        ) {
            ObjectNode input = mapper.createObjectNode();

            input.put(
                "contract",
                "marketing-strategy-input-v1"
            );
            input.put("projectId", projectId);
            input.put("sourceManifestHash", hash);
            input.set(
                "sourceManifest",
                manifest.deepCopy()
            );
            input.set(
                "sources",
                sources.deepCopy()
            );

            return input;
        }
    }
}
