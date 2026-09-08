-- The cascade pipeline answers a very short caller turn on a cheaper model than the rest
-- of the call (dialog.fast-model), which until now was one id for the whole installation.
-- That is the wrong granularity for the same reason llm_model was: a survey agent and a
-- collections agent share a deployment and not a tolerance for a weaker model. On a
-- recorded collections call every single turn fell under the word cut, so the configured
-- model never answered once and a lite model wrote the whole conversation in Uzbek.
--
-- NULL keeps the installation default. An agent that wants no downgrade at all sets this
-- to the same id as llm_model.
ALTER TABLE ai_agent
    ADD COLUMN fast_llm_model VARCHAR(120);
