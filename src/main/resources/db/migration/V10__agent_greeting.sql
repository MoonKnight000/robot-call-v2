-- How a call opens: who speaks first, the pause before speaking, and the lines the agent may
-- open with beyond first_message (per language, per direction, or interchangeable variants).
-- NULL = the default: the agent speaks first, after the platform's greeting delay.
ALTER TABLE ai_agent ADD COLUMN greeting JSONB;
