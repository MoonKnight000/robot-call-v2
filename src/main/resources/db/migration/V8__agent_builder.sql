CREATE TABLE agent (
    id                      BIGSERIAL PRIMARY KEY,
    company_id              BIGINT       NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    name                    VARCHAR(120) NOT NULL,
    description             VARCHAR(500),
    status                  VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, INACTIVE, DRAFT
    language                VARCHAR(10)  NOT NULL DEFAULT 'uz-UZ',
    system_prompt           TEXT         NOT NULL,
    greeting_text           VARCHAR(1000),
    tts_voice_id            VARCHAR(64)  REFERENCES tts_voice(id),
    scenario_id             BIGINT       REFERENCES scenario(id),
    pre_tool_speech_enabled BOOLEAN      NOT NULL DEFAULT true,
    temperature             REAL         DEFAULT 0.7,
    max_tokens              INT          DEFAULT 1024,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_agent_company ON agent(company_id);
CREATE INDEX idx_agent_status ON agent(company_id, status);

ALTER TABLE campaign ADD COLUMN IF NOT EXISTS agent_id BIGINT REFERENCES agent(id);
