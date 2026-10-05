-- Voices pulled from a provider's own list API (TtsVoiceSyncScheduler) sit beside the seeded
-- ones. A seeded row has synced_at NULL and is never touched by the sync. A synced voice the
-- provider no longer lists is switched off rather than deleted: ai_agent, ai_agent_voice and
-- campaign_variant still reference it, and an agent saved with it keeps speaking with it.
ALTER TABLE tts_voice ADD COLUMN active    BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE tts_voice ADD COLUMN synced_at TIMESTAMPTZ;
