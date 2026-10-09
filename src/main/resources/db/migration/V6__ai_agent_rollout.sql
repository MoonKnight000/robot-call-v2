-- A candidate version running beside the live one (AiAgentVersionService.startRollout): part
-- of the callers, split by number, and every staging number get the candidate until it is
-- promoted or cancelled. One per agent.
CREATE TABLE ai_agent_rollout (
    agent_id          BIGINT      PRIMARY KEY REFERENCES ai_agent (id) ON DELETE CASCADE,
    company_id        BIGINT      NOT NULL REFERENCES company (id) ON DELETE CASCADE,
    candidate_version INT         NOT NULL,
    traffic_percent   INT         NOT NULL,
    -- Normalised test numbers that always get the candidate.
    staging_phones    JSONB       NOT NULL DEFAULT '[]',
    started_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_by        BIGINT      REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT ck_ai_agent_rollout_traffic CHECK (traffic_percent BETWEEN 0 AND 100)
);

-- ai_agent_version.status gains CANDIDATE; at most one per agent, like PUBLISHED.
CREATE UNIQUE INDEX idx_ai_agent_version_candidate
    ON ai_agent_version (agent_id) WHERE status = 'CANDIDATE';
