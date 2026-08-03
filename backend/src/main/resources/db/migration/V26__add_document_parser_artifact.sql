ALTER TABLE document_versions
    ADD COLUMN parser_artifact_stored_file_id BIGINT;
ALTER TABLE document_versions
    ADD COLUMN parser_artifact_status VARCHAR(30);
ALTER TABLE document_versions
    ADD COLUMN parser_block_count INTEGER;
ALTER TABLE document_versions
    ADD COLUMN parser_artifact_checksum_sha256 VARCHAR(64);
ALTER TABLE document_versions
    ADD COLUMN parser_artifact_schema_version VARCHAR(100);
ALTER TABLE document_versions
    ADD COLUMN parsed_at TIMESTAMP;

ALTER TABLE document_versions
    ADD CONSTRAINT fk_document_version_parser_artifact
    FOREIGN KEY (parser_artifact_stored_file_id) REFERENCES stored_files(id);
ALTER TABLE document_versions
    ADD CONSTRAINT uk_document_version_parser_artifact
    UNIQUE (parser_artifact_stored_file_id);
ALTER TABLE document_versions
    ADD CONSTRAINT ck_document_version_parser_checksum
    CHECK (
        parser_artifact_checksum_sha256 IS NULL
        OR parser_artifact_checksum_sha256 ~ '^[0-9a-f]{64}$'
    );
ALTER TABLE document_versions
    ADD CONSTRAINT ck_document_version_parser_block_count
    CHECK (
        (parser_artifact_stored_file_id IS NULL AND parser_block_count IS NULL)
        OR (parser_artifact_stored_file_id IS NOT NULL AND parser_block_count > 0)
    );
ALTER TABLE document_versions
    ADD CONSTRAINT ck_document_version_parser_metadata
    CHECK (
        (
            parser_artifact_stored_file_id IS NULL
            AND parser_artifact_checksum_sha256 IS NULL
            AND parser_artifact_schema_version IS NULL
            AND parsed_at IS NULL
        )
        OR (
            parser_artifact_stored_file_id IS NOT NULL
            AND parser_artifact_checksum_sha256 IS NOT NULL
            AND parser_artifact_schema_version IS NOT NULL
            AND parsed_at IS NOT NULL
        )
    );

CREATE INDEX idx_document_version_parser_status
    ON document_versions(parser_artifact_status, parsed_at);
