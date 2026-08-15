package com.aivle.backend.pipeline.marketseed.domain;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "market_analysis_seed_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketAnalysisSeedSnapshot extends BaseEntity {
    @Id @Column(length = 64) private String id;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "selection_id") private Long selectionId;
    @Column(name = "concept_id", length = 64) private String conceptId;
    @Column(name = "source_type", nullable = false, length = 40) private String sourceType;
    @Column(name = "portfolio_selection_id") private Long portfolioSelectionId;
    @Column(name = "portfolio_concept_id", length = 64) private String portfolioConceptId;
    @Column(name = "legal_report_id", length = 64) private String legalReportId;
    @Column(name = "stale_at") private Instant staleAt;
    @Column(name = "schema_version", nullable = false, length = 20) private String schemaVersion;
    @Column(name = "source_snapshot_hash", nullable = false, length = 71) private String sourceSnapshotHash;
    @Column(name = "snapshot_hash", nullable = false, length = 71) private String snapshotHash;
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "TEXT") private String snapshotJson;
    @Column(name = "created_by_user_id", nullable = false) private Long createdByUserId;
    @Column(name = "finalized_at", nullable = false) private Instant finalizedAt;
    /**
     * 이 시드가 <b>컨셉 다듬기를 지나</b> 확정된 것인가. 시장 인터뷰 게이트가 이것을 본다 —
     * 다듬기 전 사업안으로 소비자 조사가 나가는 것을 막는 유일한 근거다.
     *
     * <p>레거시 경로({@link #create})는 언제나 {@code false} 다. 그 경로에는 다듬기가 없다.
     */
    @Column(name = "refinement_applied", nullable = false) private boolean refinementApplied;

    public static MarketAnalysisSeedSnapshot create(String id, Long projectId, Long selectionId, String conceptId,
            String schemaVersion, String sourceSnapshotHash, String snapshotHash, String snapshotJson,
            Long createdByUserId, Instant finalizedAt) {
        if (blank(id) || projectId == null || selectionId == null || blank(conceptId) || blank(schemaVersion)
            || !hash(sourceSnapshotHash) || !hash(snapshotHash) || blank(snapshotJson)
            || createdByUserId == null || finalizedAt == null) {
            throw new IllegalArgumentException("시장분석 Seed Snapshot 필드가 올바르지 않습니다.");
        }
        MarketAnalysisSeedSnapshot value = new MarketAnalysisSeedSnapshot();
        value.id = id;
        value.projectId = projectId;
        value.selectionId = selectionId;
        value.conceptId = conceptId;
        value.sourceType = "LEGACY";
        value.schemaVersion = schemaVersion;
        value.sourceSnapshotHash = sourceSnapshotHash;
        value.snapshotHash = snapshotHash;
        value.snapshotJson = snapshotJson;
        value.createdByUserId = createdByUserId;
        value.finalizedAt = finalizedAt;
        return value;
    }

    /** 다듬기를 안 지난 시드. 사업안을 고른 직후 발급되는 첫 시드가 이것이다. */
    public static MarketAnalysisSeedSnapshot createPortfolio(String id, Long projectId,
            Long portfolioSelectionId, String portfolioConceptId, String legalReportId,
            String schemaVersion, String sourceSnapshotHash, String snapshotHash,
            String snapshotJson, Long createdByUserId, Instant finalizedAt) {
        return createPortfolio(id, projectId, portfolioSelectionId, portfolioConceptId, legalReportId,
            schemaVersion, sourceSnapshotHash, snapshotHash, snapshotJson, createdByUserId, finalizedAt, false);
    }

    /**
     * @param refinementApplied 이 시드가 <b>다듬기를 지나</b> 확정된 것인가.
     *                          시장 인터뷰 게이트가 보는 값이라 부르는 쪽이 명시한다 —
     *                          기본값을 만들면 그 기본값이 곧 「다듬기 안 지났음」으로 굳는다.
     */
    public static MarketAnalysisSeedSnapshot createPortfolio(String id, Long projectId,
            Long portfolioSelectionId, String portfolioConceptId, String legalReportId,
            String schemaVersion, String sourceSnapshotHash, String snapshotHash,
            String snapshotJson, Long createdByUserId, Instant finalizedAt, boolean refinementApplied) {
        if (blank(id) || projectId == null || portfolioSelectionId == null || blank(portfolioConceptId)
                || blank(legalReportId) || blank(schemaVersion) || !hash(sourceSnapshotHash)
                || !hash(snapshotHash) || blank(snapshotJson) || createdByUserId == null || finalizedAt == null) {
            throw new IllegalArgumentException("V2 시장분석 Seed Snapshot 필드가 올바르지 않습니다.");
        }
        MarketAnalysisSeedSnapshot value = new MarketAnalysisSeedSnapshot();
        value.id = id; value.projectId = projectId; value.sourceType = "CONCEPT_PORTFOLIO_V2";
        value.portfolioSelectionId = portfolioSelectionId; value.portfolioConceptId = portfolioConceptId;
        value.legalReportId = legalReportId; value.schemaVersion = schemaVersion;
        value.sourceSnapshotHash = sourceSnapshotHash; value.snapshotHash = snapshotHash;
        value.snapshotJson = snapshotJson; value.createdByUserId = createdByUserId;
        value.finalizedAt = finalizedAt; value.refinementApplied = refinementApplied;
        return value;
    }

    public void markStale(Instant now) {
        if (staleAt == null) staleAt = now;
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static boolean hash(String value) { return value != null && value.matches("sha256:[0-9a-f]{64}"); }
}
