package com.aivle.backend.document.application.processing;

import com.aivle.backend.document.parsing.ParsedDocument;
import com.aivle.backend.job.entity.AnalysisJob;
import com.aivle.backend.job.repository.AnalysisJobRepository;
import com.aivle.backend.job.runner.JobClaim;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DocumentJobProgressService {
    private final AnalysisJobRepository jobRepository;
    private final Clock jobClock;

    @Transactional
    public void parsed(JobClaim claim, ParsedDocument parsed, String metadataJson) {
        AnalysisJob job = requireCurrent(claim);
        job.getSourceDocumentVersion().recordParsed(
            parsed.parserName(),
            parsed.parserVersion(),
            metadataJson
        );
        job.advance(
            claim.claimToken(),
            claim.attempt(),
            30,
            "PARSED",
            LocalDateTime.now(jobClock)
        );
    }

    @Transactional
    public void aiResponded(JobClaim claim, String providerRequestId) {
        AnalysisJob job = requireCurrent(claim);
        job.setExternalRequestId(claim.claimToken(), claim.attempt(), providerRequestId);
        job.advance(
            claim.claimToken(),
            claim.attempt(),
            70,
            "AI_RESPONDED",
            LocalDateTime.now(jobClock)
        );
    }

    private AnalysisJob requireCurrent(JobClaim claim) {
        AnalysisJob job = jobRepository.findByIdForUpdate(claim.jobId())
            .orElseThrow(() -> new IllegalStateException("job does not exist"));
        if (!job.hasCurrentClaim(claim.claimToken(), claim.attempt())) {
            throw new IllegalStateException("job claim is no longer current");
        }
        return job;
    }
}
