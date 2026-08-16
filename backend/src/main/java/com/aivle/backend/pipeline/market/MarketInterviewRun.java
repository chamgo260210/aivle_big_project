package com.aivle.backend.pipeline.market;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.taskrun.domain.TaskRun;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 시장 인터뷰 실행 1회. {@link TwinSurveyRun} 의 형제(패턴 B)다.
 *
 * <p>{@code sampleSize} 를 컬럼으로 두는 이유는 목록 때문이 아니다. 정성 조사에서 n 은
 * <b>결과를 읽는 단위</b>다 — 「20명 중 7명」과 「80명 중 7명」은 전혀 다른 이야기이고,
 * 그 분모가 값과 떨어지면 언급 수는 해석할 수 없는 숫자가 된다.
 */
@Entity @Table(name = "market_interview_runs") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketInterviewRun extends BaseEntity {

    public enum State { QUEUED, RUNNING, SUCCEEDED, FAILED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "task_run_id", nullable = false) private TaskRun taskRun;
    @Column(nullable = false, length = 71) private String inputSnapshotHash;
    @Column(nullable = false) private Integer sampleSize;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private State state;
    @Column(length = 80) private String errorCode;
    private LocalDateTime completedAt;

    public static MarketInterviewRun create(Project project, TaskRun taskRun, String inputHash, int sampleSize) {
        MarketInterviewRun value = new MarketInterviewRun();
        value.project = project;
        value.taskRun = taskRun;
        value.inputSnapshotHash = inputHash;
        value.sampleSize = sampleSize;
        value.state = State.QUEUED;
        return value;
    }

    public void running() { if (state == State.QUEUED) state = State.RUNNING; }
    public void succeed() { state = State.SUCCEEDED; errorCode = null; completedAt = LocalDateTime.now(); }
    public void fail(String errorCode) {
        state = State.FAILED;
        this.errorCode = errorCode;
        completedAt = LocalDateTime.now();
    }
}
