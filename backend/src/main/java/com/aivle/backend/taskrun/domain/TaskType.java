package com.aivle.backend.taskrun.domain;
/**
 * ⚠ <b>본문에 주석을 쓰지 않는다.</b> {@code ai/tests/test_internal_task_type_alignment.py} 가
 * enum 본문을 콤마로 쪼개 값 이름을 뽑는다 — 본문 주석은 값 이름에 그대로 달라붙는다.
 *
 * <p>{@code BUSINESS_VALIDATION} 은 사업 검증이다. 시장조사(FULL)와 BM 캔버스를 한 실행으로
 * 잇고, AI 쪽 봉투는 {@code MARKET_RESEARCH} 와 같다({@code mode} 만 {@code VALIDATION}).
 */
public enum TaskType {
    IDEA_ATTACHMENT_PARSE,
    IDEA_BRIEF_DERIVATION,
    CONCEPT_PORTFOLIO_V2_RUN,
    CONCEPT_PORTFOLIO_V2_CONTINUE,
    CONCEPT_PORTFOLIO_V2_SELECTION_ACTION,
    CONCEPT_FACTORY_RUN,
    CONCEPT_CANDIDATE,
    CONCEPT_DISTINCTNESS_JUDGE,
    CONCEPT_LEGAL_REVIEW,
    CONCEPT_REDESIGN,
    CONCEPT_HYPOTHESIS_ALTERNATIVE,
    CONCEPT_DELTA_LEGAL_REVIEW,
    TECH_OPS_PROPOSAL,
    FINANCE_ESTIMATE,
    MARKETING_CONTENT_GENERATION,
    MARKET_RESEARCH,
    BUSINESS_VALIDATION,
    TWIN_SURVEY,
    TWIN_STIMULUS_DRAFT,
    MARKET_INTERVIEW
}
