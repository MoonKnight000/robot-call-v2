-- The agent's own wording for the lines the engine speaks from code (AiAgentPhrases):
-- still-there prompts, the empty-reply line, farewells, thinking and backchannel pools.
-- NULL = every line is the standard one (DialogPhrases).
ALTER TABLE ai_agent ADD COLUMN phrases JSONB;
