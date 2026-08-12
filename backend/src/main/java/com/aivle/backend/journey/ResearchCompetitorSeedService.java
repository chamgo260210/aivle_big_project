package com.aivle.backend.journey;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 경쟁 씨앗 — 보관과 정규화.
 *
 * <p>「비었다」의 정의를 <b>여기 한 곳</b>에 둔다({@link BmPlanPreparationService} 와 같은 결).
 * 층마다 다르면 화면은 「썼다」고 하고 하네스는 「없다」고 읽는다.
 *
 * <p>⚠ <b>씨앗 0개를 막지 않는다.</b> 입구계약서가 「수리 대상」으로 남겨 둔 자리라
 * (백로그 39) 하드 게이트로 굳히지 않는다 — 하네스가 씨앗이 없으면 {@code corp_name}
 * 요구를 스스로 끈다({@code slot_harness._rule19}). 대신 <b>경고는 값으로 돌려준다.</b>
 */
@Service
public class ResearchCompetitorSeedService {

    /** 프롬프트 한 판에 실을 수 있는 상한. 넘으면 F_COMP 슬롯이 갈려 분모가 뭉개진다. */
    static final int MAX_SEEDS = 8;

    private static final String EMPTY_WARNING =
        "경쟁 씨앗이 없다 — 슬롯 하네스가 경쟁사 실명을 만들 근거가 없어 F_COMP 를 "
        + "업종 카테고리로만 세운다. 막지는 않지만 경쟁 관측이 얇아진다.";

    private final ResearchCompetitorSeedRepository seeds;
    private final ObjectMapper mapper;

    public ResearchCompetitorSeedService(ResearchCompetitorSeedRepository seeds, ObjectMapper mapper) {
        this.seeds = seeds;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public SeedsView current(Long projectId) {
        return view(seeds.findAllByProjectIdAndDeletedAtIsNullOrderByDisplayOrderAsc(projectId));
    }

    /**
     * <b>통째로 갈아 끼운다.</b> 한 줄씩 고치는 API 를 만들지 않는 이유는 순서가 값이기
     * 때문이다 — 부분 수정으로는 「사용자가 적은 차례」를 지킬 수 없다.
     */
    @Transactional
    public SeedsView replace(Long projectId, Long userId, JsonNode payload) {
        List<ResearchCompetitorSeed> existing =
            seeds.findAllByProjectIdAndDeletedAtIsNullOrderByDisplayOrderAsc(projectId);
        existing.forEach(seed -> seed.softDelete());
        seeds.saveAll(existing);
        seeds.flush();          // 같은 이름을 다시 적을 수 있어야 한다 — 부분 유니크 인덱스가 본다

        List<ResearchCompetitorSeed> saved = new ArrayList<>();
        Set<String> names = new HashSet<>();
        int order = 1;
        for (JsonNode item : payload == null ? mapper.createArrayNode() : payload) {
            String name = text(item, "name");
            String reason = text(item, "reason");
            if (name.isEmpty() && reason.isEmpty()) continue;      // 빈 줄은 칸을 만들지 않는다
            if (name.isEmpty() || reason.isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "경쟁 씨앗은 이름과 「왜」가 둘 다 있어야 한다 — 하네스 프롬프트가 둘을 같이 읽는다");
            }
            if (!names.add(name)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "같은 경쟁을 두 번 적었다: " + name);
            }
            if (order > MAX_SEEDS) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "경쟁 씨앗은 최대 " + MAX_SEEDS + "개다");
            }
            saved.add(seeds.save(ResearchCompetitorSeed.create(UUID.randomUUID().toString(),
                projectId, order++, name, reason, text(item, "operatorName"), userId)));
        }
        return view(saved);
    }

    /**
     * 컨셉 JSON 의 {@code _경쟁_씨앗}. <b>키 이름을 바꾸지 않는다</b> —
     * {@code slot_harness._seed_lines} 가 {@code 이름}·{@code 왜}·{@code 운영사} 를
     * 그대로 읽고, {@code gate.py:450} 이 {@code seeds} 의 존재로 규칙을 가른다.
     *
     * @return 씨앗이 없으면 {@code null} — 빈 블록을 실으면 「안 적었다」와 「비웠다」가 같아진다.
     */
    @Transactional(readOnly = true)
    public ObjectNode conceptBlock(Long projectId) {
        List<ResearchCompetitorSeed> rows =
            seeds.findAllByProjectIdAndDeletedAtIsNullOrderByDisplayOrderAsc(projectId);
        if (rows.isEmpty()) return null;
        ObjectNode block = mapper.createObjectNode();
        block.put("_설명", "사업안 화면에서 사용자가 적은 경쟁·대체재. **씨앗이지 진실이 아니다** — "
            + "엔진 발굴과 병합해 같은 잣대로 검증한다");
        ArrayNode items = block.putArray("seeds");
        for (ResearchCompetitorSeed row : rows) {
            ObjectNode item = items.addObject();
            item.put("이름", row.getName());
            item.put("왜", row.getReason());
            if (row.getOperatorName() == null) item.putNull("운영사");
            else item.put("운영사", row.getOperatorName());
        }
        block.put("_운영사_칸", "DART 조회는 서비스명이 아니라 법인명으로 한다. 코드가 corpCode "
            + "사전과 대조해 «공시법인» 인 씨앗에만 corp_name 을 허용한다");
        return block;
    }

    private SeedsView view(List<ResearchCompetitorSeed> rows) {
        List<SeedView> items = rows.stream()
            .map(row -> new SeedView(row.getId(), row.getDisplayOrder(), row.getName(),
                row.getReason(), row.getOperatorName()))
            .toList();
        return new SeedsView(items, items.isEmpty() ? EMPTY_WARNING : null);
    }

    private static String text(JsonNode item, String field) {
        JsonNode value = item.path(field);
        return value.isTextual() ? value.stringValue().trim() : "";
    }

    public record SeedView(String id, int displayOrder, String name, String reason,
                           String operatorName) { }

    /** {@code warning} 은 <b>막지 않는 경고</b>다 — 화면이 그대로 보여 준다. */
    public record SeedsView(List<SeedView> seeds, String warning) { }
}
