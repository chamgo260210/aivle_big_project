package com.aivle.backend.pipeline.module;

public enum PipelineModuleType {
    IDEA,
    CONCEPT_PORTFOLIO,
    CONCEPT_FACTORY,
    CONCEPT_SELECTION,
    MARKET_ANALYSIS,
    BUSINESS_MODEL,
    /**
     * 사업 검증의 <b>셋째 칸</b> — 컨셉 다듬기. (2026-08-16 신설)
     *
     * <p>⚠ <b>값 이름은 상태 API 계약이다.</b> 프론트가 이 이름으로 칸을 찾는다
     * ({@code projectModuleModel.js}). 기존 값 이름은 하나도 건드리지 않는다 —
     * 라벨과 경로만 옮기는 것이 이 저장소의 선례다(MARKET_INTERVIEW: 이름은 {@code TWIN_SURVEY}).
     */
    CONCEPT_REFINEMENT,
    TECH_OPS,
    FINANCE,
    TWIN_SURVEY,
    MARKETING
}
