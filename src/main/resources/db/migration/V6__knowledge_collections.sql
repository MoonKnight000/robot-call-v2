-- A named knowledge base: a set of sources any number of agents can be attached to. A source
-- inside one is answered from only by the agents attached to it — never company-wide.
CREATE TABLE knowledge_collection (
    id         BIGSERIAL    PRIMARY KEY,
    company_id BIGINT       NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    name       VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_knowledge_collection_company_name ON knowledge_collection(company_id, lower(name));

ALTER TABLE knowledge_source
    ADD COLUMN collection_id BIGINT REFERENCES knowledge_collection(id) ON DELETE CASCADE;
CREATE INDEX idx_knowledge_source_collection ON knowledge_source(collection_id);

CREATE TABLE ai_agent_knowledge_collection (
    agent_id      BIGINT NOT NULL REFERENCES ai_agent(id) ON DELETE CASCADE,
    collection_id BIGINT NOT NULL REFERENCES knowledge_collection(id) ON DELETE CASCADE,
    PRIMARY KEY (agent_id, collection_id)
);
CREATE INDEX idx_agent_knowledge_collection_collection ON ai_agent_knowledge_collection(collection_id);
