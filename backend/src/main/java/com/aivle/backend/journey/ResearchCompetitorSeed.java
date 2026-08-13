package com.aivle.backend.journey;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 경쟁 씨앗 — 사업안 화면이 받는 「경쟁/현재 대안」 한 줄.
 *
 * <p><b>씨앗이지 진실이 아니다.</b> 엔진이 발굴한 경쟁과 병합해 같은 잣대로 검증한다.
 * 슬롯 하네스가 F_COMP 슬롯의 {@code subject} 를 여기서 가져오고
 * ({@code harness/slot_harness.py:_seed_lines}), 비워 두면 모델이 실명을 지어내거나
 * 자리표시자를 만든다 — 2026-08-08 실측이다.
 *
 * <p>⚠ {@code operatorName} 은 <b>법인명</b>이지 서비스명이 아니다. DART 조회가 법인명으로만
 * 되고, 코드가 corpCode 사전과 대조해 「공시법인」인 씨앗에만 {@code corp_name} 을 허용한다.
 * 비상장이면 비워 두고 web 계량으로 관측한다.
 */
@Entity
@Table(name = "research_competitor_seeds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResearchCompetitorSeed extends BaseEntity {

    @Id @Column(length = 64) private String id;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "display_order", nullable = false) private int displayOrder;
    @Column(nullable = false, length = 200) private String name;
    @Column(nullable = false, length = 500) private String reason;
    @Column(name = "operator_name", length = 200) private String operatorName;
    @Column(name = "created_by_user_id", nullable = false) private Long createdByUserId;

    public static ResearchCompetitorSeed create(String id, Long projectId, int displayOrder,
                                                String name, String reason, String operatorName,
                                                Long userId) {
        if (blank(id) || projectId == null || displayOrder < 1 || blank(name) || blank(reason)
                || userId == null) {
            throw new IllegalArgumentException("경쟁 씨앗 값이 올바르지 않습니다.");
        }
        ResearchCompetitorSeed value = new ResearchCompetitorSeed();
        value.id = id;
        value.projectId = projectId;
        value.displayOrder = displayOrder;
        value.name = name;
        value.reason = reason;
        // 빈 문자열과 「안 적었다」를 같게 둔다 — 빈 법인명으로 DART 를 치면 안 된다.
        value.operatorName = operatorName == null || operatorName.isBlank() ? null : operatorName;
        value.createdByUserId = userId;
        return value;
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
