package com.aivle.backend.document.entity;

import com.aivle.backend.common.entity.*;
import com.aivle.backend.file.entity.StoredFile;
import com.aivle.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "document_versions", uniqueConstraints = @UniqueConstraint(name = "uk_document_version", columnNames = {"document_id", "version_number"}))
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentVersion extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "document_id", nullable = false) private ProjectDocument document;
    @Column(nullable = false) private Integer versionNumber;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "stored_file_id", nullable = false) private StoredFile storedFile;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private JobStatus parseStatus;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "uploaded_by", nullable = false) private User uploadedBy;
    @Column(nullable = false) private LocalDateTime uploadedAt;
    @Column(length = 100) private String parserName;
    @Column(length = 100) private String parserVersion;
    @Column(columnDefinition = "TEXT") private String parseMetadataJson;

    private DocumentVersion(ProjectDocument document, int versionNumber, StoredFile storedFile, User uploadedBy) {
        this.document = document;
        this.versionNumber = versionNumber;
        this.storedFile = storedFile;
        this.parseStatus = JobStatus.QUEUED;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = LocalDateTime.now();
    }

    public static DocumentVersion uploaded(
        ProjectDocument document,
        int versionNumber,
        StoredFile storedFile,
        User uploadedBy
    ) {
        return new DocumentVersion(document, versionNumber, storedFile, uploadedBy);
    }

    public void markRunning() {
        if (parseStatus != JobStatus.QUEUED) {
            throw new IllegalStateException("document version must be QUEUED");
        }
        this.parseStatus = JobStatus.RUNNING;
    }

    public void recordParsed(String parserName, String parserVersion, String metadataJson) {
        if (parseStatus != JobStatus.RUNNING) {
            throw new IllegalStateException("document version must be RUNNING");
        }
        this.parserName = parserName;
        this.parserVersion = parserVersion;
        this.parseMetadataJson = metadataJson;
    }

    public void markQueuedForRetry() {
        if (parseStatus != JobStatus.RUNNING) {
            throw new IllegalStateException("document version must be RUNNING");
        }
        this.parseStatus = JobStatus.QUEUED;
    }

    public void completeProcessing(JobStatus completionStatus) {
        if (parseStatus != JobStatus.RUNNING) {
            throw new IllegalStateException("document version must be RUNNING");
        }
        if (completionStatus != JobStatus.SUCCEEDED && completionStatus != JobStatus.PARTIAL) {
            throw new IllegalArgumentException("completion status must be SUCCEEDED or PARTIAL");
        }
        this.parseStatus = completionStatus;
    }

    public void failProcessing() {
        if (parseStatus != JobStatus.RUNNING) {
            throw new IllegalStateException("document version must be RUNNING");
        }
        this.parseStatus = JobStatus.FAILED;
    }
}
