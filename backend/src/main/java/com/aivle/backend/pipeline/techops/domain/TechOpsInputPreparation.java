package com.aivle.backend.pipeline.techops.domain;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tech_ops_input_preparations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TechOpsInputPreparation extends BaseEntity {
    @Id @Column(length = 64) private String id;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "source_market_seed_snapshot_id", length = 64) private String sourceMarketSeedSnapshotId;
    @Column(name = "source_snapshot_hash", length = 71) private String sourceSnapshotHash;
    @Column(name = "required_facts_json", nullable = false, columnDefinition = "TEXT") private String requiredFactsJson;
    @Column(name = "proposal_decisions_json", nullable = false, columnDefinition = "TEXT") private String proposalDecisionsJson;
    @Column(nullable = false) private int revision;
    @Column(name = "updated_by_user_id", nullable = false) private Long updatedByUserId;
    @Column(name = "proposal_generation_status", nullable = false, length = 40)
    private String proposalGenerationStatus;
    @Column(name = "active_proposal_task_run_id", length = 64) private String activeProposalTaskRunId;
    @Column(name = "safe_proposal_error", length = 100) private String safeProposalError;

    public static TechOpsInputPreparation create(String id, Long projectId, String sourceId, String sourceHash,
            String requiredFactsJson, String proposalDecisionsJson, Long userId) {
        if (blank(id) || projectId == null || (sourceId == null ? sourceHash != null : !hash(sourceHash)) || blank(requiredFactsJson)
                || blank(proposalDecisionsJson) || userId == null) throw new IllegalArgumentException("기술·운영 준비값이 올바르지 않습니다.");
        TechOpsInputPreparation value = new TechOpsInputPreparation();
        value.id = id; value.projectId = projectId; value.sourceMarketSeedSnapshotId = sourceId;
        value.sourceSnapshotHash = sourceHash; value.requiredFactsJson = requiredFactsJson;
        value.proposalDecisionsJson = proposalDecisionsJson; value.revision = 1; value.updatedByUserId = userId;
        value.proposalGenerationStatus = "IDLE";
        return value;
    }

    public void updateRequiredFacts(String json, Long userId) {
        if (blank(json) || userId == null) throw new IllegalArgumentException("기술·운영 사용자 입력이 올바르지 않습니다.");
        requiredFactsJson = json; updatedByUserId = userId; revision++;
    }

    public void updateProposalDecisions(String json, Long userId) {
        if (blank(json) || userId == null) throw new IllegalArgumentException("기술·운영 제안 결정이 올바르지 않습니다.");
        proposalDecisionsJson = json; updatedByUserId = userId; revision++;
        activeProposalTaskRunId = null; proposalGenerationStatus = "IDLE"; safeProposalError = null;
    }

    /**
     * 컨셉을 다시 확정하면 상위 시드가 새로 발급된다. 결속을 옮기지 않으면 준비값이 죽은 시드를
     * 계속 가리켜, 자문 실행이 시장조사 결과를 못 찾고 「빈 객체」로 진행한다 — 실패가 아니라
     * 「자료 없이 성공」이라 화면에 경고가 안 뜬다. 진행 중인 작업이 없을 때만 옮긴다.
     */
    public void rebindSource(String sourceId, String sourceHash, String factsJson, String decisionsJson, Long userId) {
        if (blank(sourceId) || !hash(sourceHash) || blank(factsJson) || blank(decisionsJson) || userId == null)
            throw new IllegalArgumentException("기술·운영 상위 스냅샷 재결속 값이 올바르지 않습니다.");
        if (activeProposalTaskRunId != null)
            throw new IllegalStateException("진행 중인 제안 작업이 있어 상위 스냅샷을 다시 결속할 수 없습니다.");
        sourceMarketSeedSnapshotId = sourceId; sourceSnapshotHash = sourceHash;
        requiredFactsJson = factsJson; proposalDecisionsJson = decisionsJson;
        updatedByUserId = userId; revision++;
        proposalGenerationStatus = "IDLE"; safeProposalError = null;
    }

    public void queueInitialProposalTask(String taskRunId) {
        requireNoActiveTask(taskRunId);
        activeProposalTaskRunId = taskRunId; proposalGenerationStatus = "QUEUED"; safeProposalError = null;
    }

    public void queueAlternativeTask(String taskRunId, String proposalJson, Long userId) {
        if (blank(proposalJson) || userId == null) throw new IllegalArgumentException("대안 요청 상태가 올바르지 않습니다.");
        requireNoActiveTask(taskRunId);
        proposalDecisionsJson = proposalJson; updatedByUserId = userId; revision++;
        activeProposalTaskRunId = taskRunId; proposalGenerationStatus = "QUEUED"; safeProposalError = null;
    }

    public void startProposalTask(String taskRunId) {
        requireActiveTask(taskRunId); proposalGenerationStatus = "RUNNING";
    }

    public void completeProposalTask(String taskRunId, String proposalJson, String outcome) {
        requireActiveTask(taskRunId);
        if (blank(proposalJson) || blank(outcome)) throw new IllegalArgumentException("제안 완료 상태가 올바르지 않습니다.");
        proposalDecisionsJson = proposalJson; revision++;
        activeProposalTaskRunId = null; proposalGenerationStatus = outcome; safeProposalError = null;
    }

    public void failProposalTask(String taskRunId, String proposalJson, String outcome, String safeError) {
        requireActiveTask(taskRunId);
        if (!blank(proposalJson)) { proposalDecisionsJson = proposalJson; revision++; }
        activeProposalTaskRunId = null; proposalGenerationStatus = outcome; safeProposalError = safeError;
    }

    public boolean proposalTaskMatches(String taskRunId) {
        return taskRunId != null && taskRunId.equals(activeProposalTaskRunId);
    }

    public boolean proposalTaskActive() { return activeProposalTaskRunId != null; }

    private void requireNoActiveTask(String taskRunId) {
        if (blank(taskRunId)) throw new IllegalArgumentException("TaskRun ID가 필요합니다.");
        if (activeProposalTaskRunId != null && !activeProposalTaskRunId.equals(taskRunId)) {
            throw new IllegalStateException("다른 TechOps 제안 작업이 진행 중입니다.");
        }
    }

    private void requireActiveTask(String taskRunId) {
        if (!proposalTaskMatches(taskRunId)) throw new IllegalStateException("TechOps 제안 작업이 stale 상태입니다.");
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static boolean hash(String value) { return value != null && value.matches("sha256:[0-9a-f]{64}"); }
}
