package com.aivle.backend.journey;

import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.service.TaskRunWorker;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 시장조사·BM 큐 폴러.
 *
 * <p><b>이 빈이 없으면 TaskRun 이 영원히 QUEUED 로 남는다.</b> 무제한 폴러
 * ({@code worker.executeOne(workerId)}) 를 부르는 스케줄러는 저장소에 하나도 없다 —
 * 타입 지정 폴러만 있고, 새 TaskType 은 자기 것을 만들어야 한다.
 *
 * <p>주기가 법률(1초)보다 긴 이유: 한 번 잡히면 <b>90~266초</b> 도는 작업이라
 * 짧게 폴링해 봐야 빈 조회만 늘어난다.
 */
@Component
public class MarketResearchWorkerScheduler {
    private final TaskRunWorker worker;

    public MarketResearchWorkerScheduler(TaskRunWorker worker) {
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "${app.task-run.market-research-poll-interval-ms:2000}")
    public void poll() {
        worker.executeOne(TaskType.MARKET_RESEARCH, "market-research-worker");
    }
}
