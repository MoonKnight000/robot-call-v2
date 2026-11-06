-- Contact → CRM write-back (Retell's "Sync to CRM"): which contact fields go to which CRM fields,
-- and, per imported contact, whether its values still owe the CRM a write.
ALTER TABLE crm_contact_sync ADD COLUMN push_mappings JSONB NOT NULL DEFAULT '[]'::jsonb;

-- push_requested_at: when the contact last changed in a way the CRM should see; NULL = nothing owed.
-- push_next_at: when the next attempt is due. pushed_at / push_error: the last outcome.
ALTER TABLE contact_crm_link ADD COLUMN push_requested_at TIMESTAMPTZ;
ALTER TABLE contact_crm_link ADD COLUMN push_next_at      TIMESTAMPTZ;
ALTER TABLE contact_crm_link ADD COLUMN push_attempts     INT NOT NULL DEFAULT 0;
ALTER TABLE contact_crm_link ADD COLUMN push_error        VARCHAR(500);
ALTER TABLE contact_crm_link ADD COLUMN pushed_at         TIMESTAMPTZ;
CREATE INDEX idx_contact_crm_link_push_due ON contact_crm_link(push_next_at) WHERE push_requested_at IS NOT NULL;
