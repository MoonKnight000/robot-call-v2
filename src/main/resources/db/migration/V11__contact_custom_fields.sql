-- Retell-style custom fields on a contact: free-form key -> text value, written by the API
-- or by an AI agent's post-call extraction.
ALTER TABLE contact ADD COLUMN custom_fields JSONB NOT NULL DEFAULT '{}'::jsonb;
