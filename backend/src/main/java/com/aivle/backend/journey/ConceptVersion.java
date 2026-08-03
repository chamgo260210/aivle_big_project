package com.aivle.backend.journey;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "concept_versions") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConceptVersion extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "idea_version_id", nullable = false) private IdeaVersion ideaVersion;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "concept_id", nullable = false) private Concept concept;
    @Column(nullable = false) private int versionNumber;
    @Column(nullable = false, length = 200) private String name;
    @Column(nullable = false, columnDefinition = "TEXT") private String oneLineSummary;
    @Column(nullable = false, columnDefinition = "TEXT") private String targetCustomer;
    @Column(nullable = false, columnDefinition = "TEXT") private String problem;
    @Column(nullable = false, columnDefinition = "TEXT") private String solution;
    @Column(nullable = false, columnDefinition = "TEXT") private String valueProposition;
    @Column(nullable = false, columnDefinition = "TEXT") private String revenueModel;
    @Column(name = "key_features_json", nullable = false, columnDefinition = "TEXT") private String keyFeaturesJson;
    @Column(name = "differentiators_json", nullable = false, columnDefinition = "TEXT") private String differentiatorsJson;
    @Column(name = "assumptions_json", nullable = false, columnDefinition = "TEXT") private String assumptionsJson;
    @Column(name = "risks_json", nullable = false, columnDefinition = "TEXT") private String risksJson;
    public static ConceptVersion create(Project project, IdeaVersion ideaVersion, Concept concept, String name,
            String summary, String customer, String problem, String solution, String value, String revenue,
            String features, String differentiators, String assumptions, String risks) {
        ConceptVersion v = new ConceptVersion(); v.project = project; v.ideaVersion = ideaVersion; v.concept = concept; v.versionNumber = 1;
        v.name = name; v.oneLineSummary = summary; v.targetCustomer = customer; v.problem = problem; v.solution = solution;
        v.valueProposition = value; v.revenueModel = revenue; v.keyFeaturesJson = features;
        v.differentiatorsJson = differentiators; v.assumptionsJson = assumptions; v.risksJson = risks; return v;
    }
}
