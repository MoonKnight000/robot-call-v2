-- Six ai_agent settings were saved and returned but never read by a call: speculation follows
-- the deployment-wide voice-agent.dialog.preemptive, no TTS provider reads stability or
-- similarity boost, end of turn comes from the VAD/turn pipeline, and no turn timeout exists.
ALTER TABLE ai_agent DROP COLUMN IF EXISTS preemptive_generation;
ALTER TABLE ai_agent DROP COLUMN IF EXISTS ivr_navigation_enabled;
ALTER TABLE ai_agent DROP COLUMN IF EXISTS voice_stability;
ALTER TABLE ai_agent DROP COLUMN IF EXISTS voice_similarity_boost;
ALTER TABLE ai_agent DROP COLUMN IF EXISTS endpointing_delay_ms;
ALTER TABLE ai_agent DROP COLUMN IF EXISTS turn_timeout_seconds;
