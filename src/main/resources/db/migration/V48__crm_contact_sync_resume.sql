-- Where an unfinished CRM contact sync pass stopped, so the next run goes on from that page instead of
-- starting over: the `since` the pass reads from and the CRM's page token. Both NULL when no pass is open.
ALTER TABLE crm_contact_sync ADD COLUMN resume_since      TIMESTAMPTZ;
ALTER TABLE crm_contact_sync ADD COLUMN resume_page_token VARCHAR(64);
