-- Per-agent chat settings (inactivity timeout, message limit, closing words); NULL = platform defaults.
ALTER TABLE ai_agent ADD COLUMN chat_settings JSONB;
