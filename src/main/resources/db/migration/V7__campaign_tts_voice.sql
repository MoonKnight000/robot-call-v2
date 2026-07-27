-- Voice the bot speaks with for this campaign (PROJECT.md §2.5). NULL keeps the
-- configured routing (preferred provider + its per-language voice), so existing
-- campaigns sound exactly as they did.
--
-- Stores a catalog id from voice-agent.tts.catalog, not a provider-side voice name:
-- the catalog is what pins the provider too, and it is validated on create so a typo
-- fails at campaign creation rather than silently falling back mid-call.
ALTER TABLE campaign
    ADD COLUMN tts_voice VARCHAR(64);
