-- The functions an agent runs before it speaks (workflow pre-call slot): a JSON list of
-- WorkflowFunction — tool name, inputs, outputs and what it waits for.
ALTER TABLE ai_agent
    ADD COLUMN pre_call_functions JSONB NOT NULL DEFAULT '[]'::jsonb;
