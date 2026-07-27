package com.aivle.backend.job.runner;

import com.aivle.backend.common.entity.JobStatus;
import com.aivle.backend.common.entity.JobType;
import com.aivle.backend.job.repository.AnalysisJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class JobRecoveryService {
    private final AnalysisJobRepository jobRepository;
    private final JobExecutionProperties properties;
    private final Clock jobClock;

    @Transactional
    public int recoverStaleJobs() {
        LocalDateTime now = LocalDateTime.now(jobClock);
        LocalDateTime threshold = now.minus(properties.staleRunningTimeout());
        var staleJobs = java.util.stream.Stream.of(
                JobType.DOCUMENT_PARSE, JobType.LEGAL_REVIEW, JobType.FEASIBILITY_ANALYSIS,
                JobType.PERSONA_RECOMMENDATION)
            .flatMap(type -> jobRepository.findRecoveryCandidates(
                type, JobStatus.RUNNING, PageRequest.of(0, properties.batchSize())).stream())
            .filter(job -> job.isStaleBefore(threshold))
            .toList();
        staleJobs.forEach(job -> {
            if (job.getAttemptCount() < properties.maxAttempts()) {
                job.recoverStaleForRetry(now);
                if (job.getJobType() == JobType.DOCUMENT_PARSE) {
                    job.getSourceDocumentVersion().markQueuedForRetry();
                }
            } else {
                job.failStale(now);
                if (job.getJobType() == JobType.DOCUMENT_PARSE) {
                    job.getSourceDocumentVersion().failProcessing();
                }
            }
        });
        return staleJobs.size();
    }
}
