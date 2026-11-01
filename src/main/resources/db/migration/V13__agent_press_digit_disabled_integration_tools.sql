ALTER TABLE ai_agent ADD COLUMN press_digit_enabled BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE ai_agent ADD COLUMN disabled_integration_tools JSONB NOT NULL DEFAULT '[]'::jsonb;
