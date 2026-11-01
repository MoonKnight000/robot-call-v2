-- Retell-style function list of an agent; NULL means never saved, the call then runs on the older per-feature fields.
ALTER TABLE ai_agent ADD COLUMN functions JSONB;
