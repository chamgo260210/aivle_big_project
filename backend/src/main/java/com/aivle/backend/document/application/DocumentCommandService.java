package com.aivle.backend.document.application;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.file.storage.FileStorage;
import com.aivle.backend.file.storage.StorageKeyGenerator;
import com.aivle.backend.file.validation.UploadedFileMetadata;
import com.aivle.backend.file.validation.UploadedFilePolicy;
import com.aivle.backend.file.validation.ValidatedUpload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentCommandService {
    private final UploadedFilePolicy uploadedFilePolicy;
    private final FileStorage fileStorage;
    private final StorageKeyGenerator storageKeyGenerator;
    private final IdempotencyKeyPolicy idempotencyKeyPolicy;
    private final DocumentRequestFingerprint fingerprintCalculator;
    private final DocumentUploadTransactionService transactionService;

    public DocumentUploadResult upload(DocumentUploadCommand command) {
        transactionService.authorizeUpload(
            command.projectId(),
            command.userId(),
            command.documentType()
        );
        String idempotencyKey = idempotencyKeyPolicy.normalize(command.idempotencyKey());
        ValidatedUpload upload = validate(command);
        String fingerprint = fingerprintCalculator.calculate(
            command.projectId(),
            command.documentType(),
            upload
        );

        Optional<DocumentUploadResult> existing = transactionService.findExisting(
            command.projectId(),
            idempotencyKey,
            fingerprint
        );
        if (existing.isPresent()) {
            return existing.get();
        }

        String storageKey = storageKeyGenerator.documentKey(upload.extension());
        FileStorage.StoredFileResult stored = store(upload, storageKey);
        try {
            assertStoredIntegrity(upload, stored);
            DocumentUploadResult result = transactionService.create(
                command,
                upload,
                stored,
                idempotencyKey,
                fingerprint
            );
            if (!result.created()) {
                cleanup(storageKey);
            }
            return result;
        } catch (RuntimeException exception) {
            cleanup(storageKey);
            throw exception;
        }
    }

    private ValidatedUpload validate(DocumentUploadCommand command) {
        if (command.content() == null) {
            throw new BusinessException(ErrorCode.FILE_REQUIRED);
        }
        try (InputStream input = command.content().openStream()) {
            return uploadedFilePolicy.validate(
                new UploadedFileMetadata(
                    command.originalFilename(),
                    command.contentType(),
                    command.declaredSize()
                ),
                input
            );
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private FileStorage.StoredFileResult store(ValidatedUpload upload, String storageKey) {
        try (InputStream input = upload.openStream()) {
            return fileStorage.store(
                input,
                upload.sizeBytes(),
                upload.extension(),
                storageKey
            );
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_FAILED);
        }
    }

    private void assertStoredIntegrity(
        ValidatedUpload upload,
        FileStorage.StoredFileResult stored
    ) {
        if (upload.sizeBytes() != stored.sizeBytes()
            || !upload.checksumSha256().equals(stored.checksumSha256())) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_FAILED);
        }
    }

    private void cleanup(String storageKey) {
        try {
            fileStorage.delete(storageKey);
        } catch (IOException | RuntimeException cleanupFailure) {
            log.error("Failed to clean up uncommitted file at storage key {}", storageKey, cleanupFailure);
        }
    }
}
