-- The logo of an integration provider, uploaded by the platform admin and shown to every company
-- in the integrations catalog and the agent studio. Kept here and not in stored_file: a stored
-- file belongs to one company and is served only to its owner, while an icon is shared and small.
CREATE TABLE integration_provider_icon (
    provider     VARCHAR(32) PRIMARY KEY,
    content_type VARCHAR(64) NOT NULL,
    data         BYTEA       NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
