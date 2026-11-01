-- Deleting an agent used to null knowledge_source.agent_id, which turned the agent's private
-- documents into company-wide ones that every other agent then answered from.
ALTER TABLE knowledge_source DROP CONSTRAINT knowledge_source_agent_id_fkey;
ALTER TABLE knowledge_source ADD CONSTRAINT knowledge_source_agent_id_fkey
    FOREIGN KEY (agent_id) REFERENCES ai_agent(id) ON DELETE CASCADE;
