-- A company's own OpenAI-compatible LLM (gap analysis 3.14): its calls and their summaries run on
-- it instead of the platform's model. The API key never lands here — only the name of the
-- company secret that holds it.
ALTER TABLE ai_model_config
    ADD COLUMN own_llm_base_url       VARCHAR(500),
    ADD COLUMN own_llm_model          VARCHAR(100),
    ADD COLUMN own_llm_fast_model     VARCHAR(100),
    ADD COLUMN own_llm_api_key_secret VARCHAR(100),
    ADD CONSTRAINT ck_ai_model_config_own_llm CHECK (
        (own_llm_base_url IS NULL AND own_llm_model IS NULL AND own_llm_fast_model IS NULL
            AND own_llm_api_key_secret IS NULL)
        OR (own_llm_base_url IS NOT NULL AND own_llm_model IS NOT NULL AND own_llm_api_key_secret IS NOT NULL));
