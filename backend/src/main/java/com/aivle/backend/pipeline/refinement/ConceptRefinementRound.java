package com.aivle.backend.pipeline.refinement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 컨셉 다듬기 한 라운드. <b>durable cursor</b> 다.
 *
 * <p>워커는 폴링마다 새로 깨어난다. 라운드 번호와 직전 기각 사유를 메모리에 두면 재시작
 * 한 번에 루프가 처음부터 다시 돌고, 그것은 LLM 값을 다시 내는 일이다.
 *
 * <p>기각 사유를 남기는 이유는 <b>다음 라운드 입력으로 되먹이기</b> 때문이다. 왜 막혔는지를
 * 모델에 돌려주지 않으면 같은 제안을 3라운드 내내 반복한다.
 */
@Entity
@Table(name = "concept_refinement_rounds")
public class ConceptRefinementRound {

    /** 라운드 상한. 넘으면 「이건 못 풀었다」로 사유와 함께 멈춘다. */
    public static final int MAX_ROUNDS = 3;

    public enum LegalOutcome { PASSED, BLOCKED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "selection_id", nullable = false)
    private Long selectionId;

    @Column(nullable = false)
    private Integer round;

    /** 계약으로 거르기 <b>전</b> 제안 원본. 무엇을 걸렀는지 되짚으려면 이것이 있어야 한다. */
    @Column(name = "proposal_json", nullable = false, columnDefinition = "TEXT")
    private String proposalJson;

    @Column(name = "drift_rejections_json", columnDefinition = "TEXT")
    private String driftRejectionsJson;

    @Column(name = "legal_outcome", length = 20)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private LegalOutcome legalOutcome;

    @Column(name = "legal_reasons_json", columnDefinition = "TEXT")
    private String legalReasonsJson;

    /**
     * 사람이 <b>고른 칸</b>. {@code null} 이면 아직 안 골랐다, {@code "[]"} 면 전부 넘겼다.
     *
     * <p>⚠ 「아직 안 골랐다」와 「전부 넘겼다」는 <b>다른 사실</b>이라 한 값으로 뭉개지 않는다.
     * 앞은 사용자 차례고, 뒤는 사용자가 이미 답한 것이다.
     */
    @Column(name = "accepted_fields_json", columnDefinition = "TEXT")
    private String acceptedFieldsJson;

    /**
     * 이 라운드가 <b>어느 조사판을 근거로</b> 만들어졌나({@code MarketResearchVersion.versionNumber}).
     *
     * <p>{@code null} 이면 V31 이전 행이라 <b>모른다</b> — 새 조사판이 오면 물러난다.
     * 이 칸이 없던 동안 다듬기는 「선택당 한 번」만 걸려서, 조사를 다섯 번 다시 돌려도
     * 화면의 제안은 <b>이틀 전 조사 기준</b>에 멈춰 있었다(2026-08-16 실측).
     */
    @Column(name = "research_version")
    private Integer researchVersion;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected ConceptRefinementRound() {
    }

    public static ConceptRefinementRound of(Long projectId, Long selectionId, int round,
            String proposalJson, String driftRejectionsJson, Integer researchVersion) {
        ConceptRefinementRound value = new ConceptRefinementRound();
        value.projectId = projectId;
        value.selectionId = selectionId;
        value.round = round;
        value.proposalJson = proposalJson;
        value.driftRejectionsJson = driftRejectionsJson;
        value.researchVersion = researchVersion;
        value.createdAt = LocalDateTime.now();
        return value;
    }

    /**
     * 새 조사판이 왔다 — 이 라운드는 <b>물러난다</b>.
     *
     * <p>지우는 것이 아니라 <b>현재에서 뺀다.</b> 행은 그대로 남고 조회만 비껴간다
     * (이 표의 조회는 전부 {@code DeletedAtIsNull} 이다). 그래야 라운드 번호가 1부터 다시
     * 서고 상한 3도 새 주기에 온전히 주어진다.
     *
     * <p>⚠ 물러난 라운드가 <b>이미 적용한 변경은 되돌아가지 않는다.</b> 컨셉은 고쳐진 채다 —
     * 그 기록은 오버레이·확정 가설·서술문에 남는다. 여기서 물러나는 것은 <b>제안 카드</b>다.
     */
    public void supersede() {
        this.deletedAt = LocalDateTime.now();
    }

    /** 법률 결과가 오면 한 번 적는다. 라운드는 그 뒤에 끝난다. */
    public void recordLegal(LegalOutcome outcome, String reasonsJson) {
        this.legalOutcome = outcome;
        this.legalReasonsJson = reasonsJson;
    }

    /**
     * 사람의 결정을 <b>한 번</b> 적는다. 이미 적혔으면 {@code false} 를 돌려주고 아무것도 안 한다.
     *
     * <p>두 번 받으면 {@code apply()} 가 두 번 돌고 가설 확정이 두 번 걸린다. 「한 번만」을
     * 호출자 규율이 아니라 <b>여기서</b> 막는다.
     */
    public boolean recordDecision(String acceptedFieldsJson) {
        if (this.acceptedFieldsJson != null) return false;
        this.acceptedFieldsJson = acceptedFieldsJson;
        return true;
    }

    public String getAcceptedFieldsJson() {
        return acceptedFieldsJson;
    }

    public Integer getResearchVersion() {
        return researchVersion;
    }

    /** 물러났나. {@code null} 이 아니면 옛 주기다 — 조회는 전부 이것을 비껴간다. */
    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public Long getSelectionId() {
        return selectionId;
    }

    public Integer getRound() {
        return round;
    }

    public String getProposalJson() {
        return proposalJson;
    }

    public String getDriftRejectionsJson() {
        return driftRejectionsJson;
    }

    public LegalOutcome getLegalOutcome() {
        return legalOutcome;
    }

    public String getLegalReasonsJson() {
        return legalReasonsJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
