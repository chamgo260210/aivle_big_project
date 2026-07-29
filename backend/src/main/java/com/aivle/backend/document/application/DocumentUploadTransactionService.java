package com.aivle.backend.document.application;

import com.aivle.backend.common.entity.*;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.document.entity.DocumentVersion;
import com.aivle.backend.document.entity.ProjectDocument;
import com.aivle.backend.document.repository.DocumentVersionRepository;
import com.aivle.backend.document.repository.ProjectDocumentRepository;
import com.aivle.backend.file.entity.StoredFile;
import com.aivle.backend.file.repository.StoredFileRepository;
import com.aivle.backend.file.storage.FileStorage;
import com.aivle.backend.file.validation.ValidatedUpload;
import com.aivle.backend.job.entity.AnalysisJob;
import com.aivle.backend.job.repository.AnalysisJobRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DocumentUploadTransactionService {
    private final ProjectRepository projectRepository;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final StoredFileRepository storedFileRepository;
    private final AnalysisJobRepository analysisJobRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public void authorizeUpload(Long projectId, Long userId, DocumentType documentType) {
        Project project = projectRepository.findByIdAndDeletedAtIsNull(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        assertOwner(project, userId);
        assertUploadAllowed(project, documentType);
    }

    @Transactional(readOnly = true)
    public Optional<DocumentUploadResult> findExisting(
        Long projectId,
        String idempotencyKey,
        String fingerprint
    ) {
        if (idempotencyKey == null) {
            return Optional.empty();
        }
        return analysisJobRepository
            .findByProjectIdAndJobTypeAndIdempotencyKeyAndDeletedAtIsNull(
                projectId,
                JobType.DOCUMENT_PARSE,
                idempotencyKey
            )
            .map(job -> reuseOrConflict(job, fingerprint));
    }

    @Transactional
    public DocumentUploadResult create(
        DocumentUploadCommand command,
        ValidatedUpload upload,
        FileStorage.StoredFileResult stored,
        String idempotencyKey,
        String fingerprint
    ) {
        Project project = projectRepository.findByIdForUpdate(command.projectId())
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        assertOwner(project, command.userId());
        assertUploadAllowed(project, command.documentType());

        if (idempotencyKey != null) {
            Optional<AnalysisJob> existing = analysisJobRepository
                .findByProjectIdAndJobTypeAndIdempotencyKeyAndDeletedAtIsNull(
                    command.projectId(),
                    JobType.DOCUMENT_PARSE,
                    idempotencyKey
                );
            if (existing.isPresent()) {
                return reuseOrConflict(existing.get(), fingerprint);
            }
        }

        ProjectDocument document = resolveActiveDocument(project, command.documentType());
        StoredFile storedFile = storedFileRepository.save(StoredFile.available(
            stored.storageKey(),
            upload.originalFilename(),
            stored.storedFilename(),
            upload.extension(),
            upload.contentType(),
            stored.sizeBytes(),
            stored.checksumSha256()
        ));
        int versionNumber = document.allocateNextVersion();
        DocumentVersion version = documentVersionRepository.save(
            DocumentVersion.uploaded(document, versionNumber, storedFile, project.getOwner())
        );
        documentVersionRepository.flush();

        AnalysisJob job = analysisJobRepository.save(AnalysisJob.queuedDocumentParse(
            project,
            version,
            requestJson(project.getId(), document.getId(), version.getId()),
            idempotencyKey,
            fingerprint
        ));
        analysisJobRepository.flush();
        eventPublisher.publishEvent(new DocumentProcessingRequested(job.getId()));

        return new DocumentUploadResult(
            project.getId(),
            document.getId(),
            version.getId(),
            job.getId(),
            job.getStatus(),
            true
        );
    }

    private ProjectDocument resolveActiveDocument(Project project, DocumentType documentType) {
        List<ProjectDocument> active = projectDocumentRepository
            .findAllByProjectIdAndDocumentTypeAndStatusAndDeletedAtIsNull(
                project.getId(),
                documentType,
                DocumentStatus.ACTIVE
            );
        if (active.size() > 1) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        if (active.size() == 1) {
            return active.get(0);
        }
        return projectDocumentRepository.save(ProjectDocument.create(project, documentType));
    }

    private DocumentUploadResult reuseOrConflict(AnalysisJob job, String fingerprint) {
        if (!job.hasSameIdempotentRequest(fingerprint)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }
        DocumentVersion version = job.getSourceDocumentVersion();
        if (version == null) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        return new DocumentUploadResult(
            job.getProject().getId(),
            version.getDocument().getId(),
            version.getId(),
            job.getId(),
            job.getStatus(),
            false
        );
    }

    private void assertOwner(Project project, Long userId) {
        if (userId == null || !project.getOwner().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
    }

    private void assertUploadAllowed(Project project, DocumentType documentType) {
        boolean supportedType = documentType == DocumentType.BUSINESS_PLAN;
        boolean statusAllowed = project.getStatus() == ProjectStatus.DRAFT
            || project.getStatus() == ProjectStatus.ACTIVE;
        boolean stageAllowed = project.getStage() == ProjectStage.DOCUMENT
            || project.getStage() == ProjectStage.STRUCTURING;
        if (!supportedType || !statusAllowed || !stageAllowed) {
            throw new BusinessException(ErrorCode.DOCUMENT_UPLOAD_NOT_ALLOWED);
        }
    }

    private String requestJson(Long projectId, Long documentId, Long versionId) {
        return "{\"projectId\":" + projectId
            + ",\"documentId\":" + documentId
            + ",\"documentVersionId\":" + versionId
            + "}";
    }
}
