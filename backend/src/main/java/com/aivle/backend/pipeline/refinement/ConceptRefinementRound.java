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

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected ConceptRefinementRound() {
    }

    public static ConceptRefinementRound of(Long projectId, Long selectionId, int round,
            String proposalJson, String driftRejectionsJson) {
        ConceptRefinementRound value = new ConceptRefinementRound();
        value.projectId = projectId;
        value.selectionId = selectionId;
        value.round = round;
        value.proposalJson = proposalJson;
        value.driftRejectionsJson = driftRejectionsJson;
        value.createdAt = LocalDateTime.now();
        return value;
    }

    /** 법률 결과가 오면 한 번 적는다. 라운드는 그 뒤에 끝난다. */
    public void recordLegal(LegalOutcome outcome, String reasonsJson) {
        this.legalOutcome = outcome;
        this.legalReasonsJson = reasonsJson;
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
