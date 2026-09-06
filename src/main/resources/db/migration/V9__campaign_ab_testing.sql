CREATE TABLE campaign_variant (
    id                 BIGSERIAL PRIMARY KEY,
    campaign_id        BIGINT       NOT NULL REFERENCES campaign(id) ON DELETE CASCADE,
    company_id         BIGINT       NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    name               VARCHAR(100) NOT NULL,
    agent_id           BIGINT       REFERENCES agent(id) ON DELETE SET NULL,
    scenario_id        BIGINT       REFERENCES scenario(id) ON DELETE SET NULL,
    prompt_override    TEXT,
    tts_voice_id       VARCHAR(64)  REFERENCES tts_voice(id),
    traffic_weight     INT          NOT NULL DEFAULT 50,
    calls_count        INT          NOT NULL DEFAULT 0,
    answered_count     INT          NOT NULL DEFAULT 0,
    converted_count    INT          NOT NULL DEFAULT 0,
    is_active          BOOLEAN      NOT NULL DEFAULT true,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_variant_campaign ON campaign_variant(campaign_id);
CREATE INDEX idx_variant_company ON campaign_variant(company_id);

ALTER TABLE campaign_target ADD COLUMN IF NOT EXISTS variant_id BIGINT REFERENCES campaign_variant(id) ON DELETE SET NULL;
ALTER TABLE call_attempt ADD COLUMN IF NOT EXISTS variant_id BIGINT REFERENCES campaign_variant(id) ON DELETE SET NULL;
