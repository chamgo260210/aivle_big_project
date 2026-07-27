package com.aivle.backend.document.application.processing;

import com.aivle.backend.common.entity.FileStatus;

public record DocumentJobContext(
    Long jobId,
    Long projectId,
    Long documentVersionId,
    String storageKey,
    String originalFileName,
    String mimeType,
    long sizeBytes,
    String checksumSha256,
    FileStatus fileStatus,
    boolean encrypted
) {
}
