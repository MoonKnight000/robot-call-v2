-- ai_model held only the text LLM a turn is answered with. The agent form has two more
-- model fields next to it — ai_agent.stt_model and ai_agent.tts_model — and both were
-- free text, which is exactly the state llm_model was in before this table existed: a
-- wrong id is accepted at save and only discovered by the call that cannot hear or speak.
--
-- kind splits the catalog into the three families. They never mix: what Deepgram calls
-- nova-3 is not a model Gemini answers for, and neither is a chat model id.
ALTER TABLE ai_model
    ADD COLUMN kind VARCHAR(10) NOT NULL DEFAULT 'LLM';

-- The rows seeded so far are all chat models; the default above already says so, and the
-- column keeps it for rows a future seed adds without naming a kind.
