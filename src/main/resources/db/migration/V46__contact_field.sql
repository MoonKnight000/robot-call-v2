-- Retell-style contact fields: the company's typed definitions of the keys a contact's custom_fields may hold.
-- extraction_key names the post-call extraction field that fills the contact field; update_mode says how
-- a new value meets the stored one (OVERWRITE / FILL_IF_EMPTY / ACCUMULATE).
CREATE TABLE contact_field (
    id             BIGSERIAL PRIMARY KEY,
    company_id     BIGINT       NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    field_key      VARCHAR(64)  NOT NULL,
    label          VARCHAR(128) NOT NULL,
    field_type     VARCHAR(16)  NOT NULL,
    options        JSONB        NOT NULL DEFAULT '[]'::jsonb,
    description    TEXT,
    extraction_key VARCHAR(64),
    update_mode    VARCHAR(16)  NOT NULL DEFAULT 'OVERWRITE',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX idx_contact_field_company_key ON contact_field(company_id, field_key);
