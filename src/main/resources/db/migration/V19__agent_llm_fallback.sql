-- The agent's backup LLM (cascade pipeline): the model a turn runs on when the main one fails
-- (SEQUENTIAL) or has sent no first token in time (CONCURRENT). NULL model = no fallback.
ALTER TABLE ai_agent
    ADD COLUMN fallback_llm_model VARCHAR(120),
    ADD COLUMN fallback_llm_mode  VARCHAR(20);
