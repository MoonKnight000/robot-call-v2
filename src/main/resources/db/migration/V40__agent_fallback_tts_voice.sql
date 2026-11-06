-- The voice an agent falls back to when its own TTS provider fails mid-call (AiAgentVoice.fallbackVoice).
-- NULL keeps the automatic choice: any other provider that speaks the language, with its default voice.
ALTER TABLE ai_agent ADD COLUMN fallback_tts_voice VARCHAR(64) REFERENCES tts_voice(id);
