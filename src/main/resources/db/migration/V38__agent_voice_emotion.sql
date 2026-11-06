-- The mood an agent speaks in by default (AiAgentVoice.emotion, VoiceEmotion): one provider-neutral
-- name that each TTS engine turns into its own controls and the LLM prompt turns into wording.
ALTER TABLE ai_agent ADD COLUMN voice_emotion VARCHAR(16) NOT NULL DEFAULT 'NEUTRAL';
