-- Agent Handbook: the best-practice rules (HandbookRule names) an agent has switched on in the
-- studio. Each adds a line to the prompt on top of the platform's own rules, so the empty
-- default leaves every existing agent speaking exactly as before.
ALTER TABLE ai_agent ADD COLUMN handbook JSONB NOT NULL DEFAULT '[]';
