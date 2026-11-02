-- A provider's logo is a stored file (MinIO object + stored_file row), not bytes in this table.
-- Logos uploaded before this migration lived in integration_provider_icon and are dropped with it:
-- the platform admin uploads them again.
ALTER TABLE integration_provider
    ADD COLUMN icon_file_id BIGINT REFERENCES stored_file(id) ON DELETE SET NULL;

DROP TABLE integration_provider_icon;
