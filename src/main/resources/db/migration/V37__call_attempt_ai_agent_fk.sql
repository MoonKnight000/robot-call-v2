-- Clean up orphaned rows if any reference an ai_agent that doesn't exist
UPDATE call_attempt SET ai_agent_id = NULL
WHERE ai_agent_id IS NOT NULL AND ai_agent_id NOT IN (SELECT id FROM ai_agent);

ALTER TABLE call_attempt
    ADD CONSTRAINT fk_call_attempt_ai_agent
    FOREIGN KEY (ai_agent_id) REFERENCES ai_agent(id) ON DELETE SET NULL;
