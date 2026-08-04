-- StoredFile catalog: files stay in MinIO, this table is metadata only (id, original
-- name, MinIO object key/"path", format, size, owning company, category). Backend
-- streams the bytes through GET /api/files/{id} instead of ever handing the browser a
-- raw MinIO URL (docker-internal hostnames like "minio:9000" are not resolvable from
-- the browser).
CREATE TABLE stored_file (
    id            BIGSERIAL PRIMARY KEY,
    company_id    BIGINT       NOT NULL REFERENCES company(id),
    category      VARCHAR(20)  NOT NULL,   -- IMAGE | AUDIO | DOCUMENT
    original_name VARCHAR(255) NOT NULL,
    path          VARCHAR(500) NOT NULL,   -- MinIO object key: com-{companyId}/{category}/<uuid>.<ext>
    bucket        VARCHAR(100) NOT NULL,
    format        VARCHAR(100),            -- content-type, e.g. image/png
    size_bytes    BIGINT       NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_stored_file_company ON stored_file(company_id);
CREATE INDEX idx_stored_file_category_created ON stored_file(category, created_at);

ALTER TABLE company ADD COLUMN logo_file_id BIGINT REFERENCES stored_file(id) ON DELETE SET NULL;
ALTER TABLE company DROP COLUMN logo_url;

ALTER TABLE app_user ADD COLUMN avatar_file_id BIGINT REFERENCES stored_file(id) ON DELETE SET NULL;
ALTER TABLE app_user DROP COLUMN avatar_url;

-- ON DELETE SET NULL: the retention sweep (RetentionService) deletes a stored_file row
-- once its recording ages out; the referencing call_attempt just loses the pointer
-- instead of needing a separate "clear url" cleanup query.
ALTER TABLE call_attempt ADD COLUMN recording_file_id BIGINT REFERENCES stored_file(id) ON DELETE SET NULL;
ALTER TABLE call_attempt DROP COLUMN recording_url;
