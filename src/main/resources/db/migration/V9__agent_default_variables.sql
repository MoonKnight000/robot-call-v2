ALTER TABLE ai_agent ADD COLUMN default_variables JSONB NOT NULL DEFAULT '{}'::jsonb;
