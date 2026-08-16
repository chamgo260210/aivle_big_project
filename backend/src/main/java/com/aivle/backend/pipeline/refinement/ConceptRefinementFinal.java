package com.aivle.backend.pipeline.refinement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 다듬기의 <b>최종 산물</b> — 한 선택에 하나.
 *
 * <p>라운드({@link ConceptRefinementRound})가 「어떻게 왔나」라면 이것은 「무엇으로 끝났나」다.
 *
 * <p>두 칸이 든다:
 * <ul>
 *   <li><b>오버레이</b> — 다듬기가 고쳤지만 가설도 BM 계획도 아닌 칸
 *       ({@code targetUsers}·{@code featureSet}). 갈 문이 없어 지금까지 조용히 버려지던 것이다.
 *       최종 확정 때 시드 스냅샷에 얹힌다.</li>
 *   <li><b>서술문</b> — 최종 컨셉 한 문단. <b>검증을 통과한 것만</b> 들어온다.</li>
 * </ul>
 *
 * <p>⚠ 서술문이 없으면 {@code null} 로 둔다. 반쯤 맞는 문장을 컨셉 원문 자리에 세우면
 * 그것이 곧 지어낸 근거가 된다 — 화면은 칸 나열로 폴백한다.
 */
@Entity
@Table(name = "concept_refinement_finals")
public class ConceptRefinementFinal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "selection_id", nullable = false)
    private Long selectionId;

    @Column(name = "overlay_json", columnDefinition = "TEXT")
    private String overlayJson;

    @Column(name = "narrative_json", columnDefinition = "TEXT")
    private String narrativeJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected ConceptRefinementFinal() {
    }

    public static ConceptRefinementFinal of(Long projectId, Long selectionId) {
        ConceptRefinementFinal value = new ConceptRefinementFinal();
        value.projectId = projectId;
        value.selectionId = selectionId;
        value.createdAt = LocalDateTime.now();
        value.updatedAt = value.createdAt;
        return value;
    }

    /**
     * 오버레이를 <b>누적</b>한다.
     *
     * <p>왜 덮지 않고 합치나. 라운드마다 다른 칸이 올 수 있다 — 1라운드가 {@code targetUsers} 를,
     * 2라운드가 {@code featureSet} 을 고치면 덮어쓰기는 앞의 것을 지운다.
     * 같은 칸이 다시 오면 <b>나중 것이 이긴다</b>(그것이 더 다듬어진 값이다).
     */
    public void mergeOverlay(String overlayJson) {
        this.overlayJson = overlayJson;
        this.updatedAt = LocalDateTime.now();
    }

    /** 검증을 통과한 서술문만 들어온다. 통과 못 했으면 부르지 않는다. */
    public void recordNarrative(String narrativeJson) {
        this.narrativeJson = narrativeJson;
        this.updatedAt = LocalDateTime.now();
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

    public String getOverlayJson() {
        return overlayJson;
    }

    public String getNarrativeJson() {
        return narrativeJson;
    }
}
