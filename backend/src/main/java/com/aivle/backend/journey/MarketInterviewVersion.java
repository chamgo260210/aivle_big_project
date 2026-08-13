package com.aivle.backend.journey;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 물질화된 시장 인터뷰 결과. {@link TwinSurveyVersion} 과 같은 규칙을 따른다 —
 * <b>결과를 쪼개지 않는다.</b> {@code resultJson} 이 정본이고 스칼라는 사본이다.
 *
 * <p>{@code misunderstoodCount} 를 세는 이유: 오해한 사람 수는 「컨셉이 나쁘다」가 아니라
 * 「컨셉보드가 안 읽힌다」의 지표다. 세어 두지 않으면 설명을 고쳐야 할 실행과 제품을
 * 고쳐야 할 실행이 목록에서 같아 보인다. {@code caveatCount} 는 경계 소실을 눈으로 보기
 * 위한 것이고, 0 이면 계약이 이미 막았어야 한다.
 */
@Entity @Table(name = "market_interview_versions") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketInterviewVersion extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "source_run_id", nullable = false) private MarketInterviewRun sourceRun;
    @Column(nullable = false) private Integer versionNumber;

    // @Lob 을 쓰지 않는 이유는 형제 엔티티와 같다 — Postgres 에서 oid 를 기대해
    // ddl-auto=validate 가 부팅에서 죽는다.
    @Column(nullable = false, columnDefinition = "TEXT") private String resultJson;

    @Column(nullable = false) private Integer sampleSize;
    @Column(nullable = false) private Integer answeredCount;
    @Column(nullable = false) private Integer themeCount;
    @Column(nullable = false) private Integer misunderstoodCount;
    @Column(nullable = false) private Integer caveatCount;

    public static MarketInterviewVersion of(Project project, MarketInterviewRun run, int versionNumber,
                                            String resultJson, Summary summary) {
        MarketInterviewVersion value = new MarketInterviewVersion();
        value.project = project;
        value.sourceRun = run;
        value.versionNumber = versionNumber;
        value.resultJson = resultJson;
        value.sampleSize = summary.sampleSize();
        value.answeredCount = summary.answeredCount();
        value.themeCount = summary.themeCount();
        value.misunderstoodCount = summary.misunderstoodCount();
        value.caveatCount = summary.caveatCount();
        return value;
    }

    /** 목록용 스칼라 묶음. 판정의 근거가 아니다 — 판정은 언제나 {@code resultJson} 을 읽는다. */
    public record Summary(int sampleSize, int answeredCount, int themeCount,
                          int misunderstoodCount, int caveatCount) { }
}
