-- Retell-style CRM contact sync: the company's CRM contacts imported into `contact` on a schedule.
-- One row per company: what to import (enabled, field mappings) and how far the last run got.
CREATE TABLE crm_contact_sync (
    company_id      BIGINT       PRIMARY KEY REFERENCES company(id) ON DELETE CASCADE,
    enabled         BOOLEAN      NOT NULL DEFAULT FALSE,
    -- [{"crmField": "UF_CRM_123", "contactField": "lead_status"}]
    field_mappings  JSONB        NOT NULL DEFAULT '[]'::jsonb,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Run state. The cursor belongs to `provider`: a switch to another CRM starts a full sync.
    provider        VARCHAR(16),
    sync_cursor     TIMESTAMPTZ,
    next_run_at     TIMESTAMPTZ,
    last_run_at     TIMESTAMPTZ,
    last_status     VARCHAR(16),
    last_error      VARCHAR(500),
    last_created    INT          NOT NULL DEFAULT 0,
    last_updated    INT          NOT NULL DEFAULT 0,
    last_skipped    INT          NOT NULL DEFAULT 0
);
CREATE INDEX idx_crm_contact_sync_due ON crm_contact_sync(next_run_at) WHERE enabled;

-- Which CRM record a contact was imported from. One link per contact; a CRM record maps to one contact.
CREATE TABLE contact_crm_link (
    contact_id   BIGINT       PRIMARY KEY REFERENCES contact(id) ON DELETE CASCADE,
    company_id   BIGINT       NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    provider     VARCHAR(16)  NOT NULL,
    external_id  VARCHAR(64)  NOT NULL,
    synced_at    TIMESTAMPTZ  NOT NULL
);
CREATE UNIQUE INDEX idx_contact_crm_link_external ON contact_crm_link(company_id, provider, external_id);
