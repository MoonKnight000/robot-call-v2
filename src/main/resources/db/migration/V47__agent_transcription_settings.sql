-- Retell-style turn-taking and recogniser tuning on the agent (cascade pipeline).
-- NULL in every column means "the deployment default".
ALTER TABLE ai_agent ADD COLUMN response_wait_ms INTEGER;
ALTER TABLE ai_agent ADD COLUMN transcription_mode VARCHAR(16);
ALTER TABLE ai_agent ADD COLUMN boosted_keywords JSONB;
