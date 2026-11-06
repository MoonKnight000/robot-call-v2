-- How much the agent's LLM thinks before it answers (NONE/MINIMAL/LOW/MEDIUM/HIGH), one setting for every
-- vendor; agent/provider maps it to Gemini's thinkingLevel, OpenAI's reasoning_effort and Claude's thinking
-- budget. NULL keeps each vendor's deployment default.
ALTER TABLE ai_agent ADD COLUMN reasoning_effort VARCHAR(16);
