-- Per-company Pipecat sub-engine configuration (STT, LLM, TTS selections)
ALTER TABLE engine_config
    ADD COLUMN IF NOT EXISTS pipecat_stt VARCHAR(50),
    ADD COLUMN IF NOT EXISTS pipecat_llm VARCHAR(50),
    ADD COLUMN IF NOT EXISTS pipecat_tts VARCHAR(50);
