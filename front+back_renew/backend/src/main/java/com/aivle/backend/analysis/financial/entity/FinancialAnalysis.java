package com.aivle.backend.analysis.financial.entity;

import com.aivle.backend.common.entity.*;
import com.aivle.backend.job.entity.AnalysisJob;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity @Table(name = "financial_analyses")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinancialAnalysis extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "analysis_job_id", nullable = false, unique = true) private AnalysisJob analysisJob;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private JobStatus status;
    @Column(nullable = false, length = 3) private String currency;
    private Integer analysisPeriodMonths;
    @Column(precision = 19, scale = 2) private BigDecimal expectedRevenue;
    @Column(precision = 19, scale = 2) private BigDecimal expectedCost;
    private Integer breakEvenPointMonths;
    @Column(precision = 10, scale = 4) private BigDecimal roi;
    @Column(precision = 19, scale = 2) private BigDecimal npv;
    @Column(precision = 10, scale = 4) private BigDecimal irr;
    @Column(columnDefinition = "TEXT") private String summary;
    @Column(columnDefinition = "TEXT") private String assumptionsJson;
    @Column(columnDefinition = "TEXT") private String resultJson;
}
