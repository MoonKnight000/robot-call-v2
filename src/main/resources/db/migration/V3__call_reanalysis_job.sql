-- One run of an agent's current analysis settings over its finished calls in a range
-- (CallReanalysisService). The worker goes through the calls in id order after last_call_id,
-- a batch per tick, so a restart picks up where it stopped.
CREATE TABLE call_reanalysis_job (
    id                BIGSERIAL    PRIMARY KEY,
    company_id        BIGINT       NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    ai_agent_id       BIGINT       NOT NULL REFERENCES ai_agent(id) ON DELETE CASCADE,
    range_from        TIMESTAMPTZ  NOT NULL,
    range_to          TIMESTAMPTZ  NOT NULL,
    status            VARCHAR(16)  NOT NULL,
    total_calls       INT          NOT NULL,
    processed_calls   INT          NOT NULL DEFAULT 0,
    updated_calls     INT          NOT NULL DEFAULT 0,
    failed_calls      INT          NOT NULL DEFAULT 0,
    prompt_tokens     BIGINT       NOT NULL DEFAULT 0,
    completion_tokens BIGINT       NOT NULL DEFAULT 0,
    charged_uzs       BIGINT       NOT NULL DEFAULT 0,
    last_call_id      BIGINT       NOT NULL DEFAULT 0,
    last_error        VARCHAR(500),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    started_at        TIMESTAMPTZ,
    finished_at       TIMESTAMPTZ,
    CONSTRAINT ck_call_reanalysis_job_status
        CHECK (status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'CANCELLED', 'FAILED'))
);
CREATE INDEX idx_call_reanalysis_job_company_created ON call_reanalysis_job (company_id, created_at DESC);
-- One job at a time per company: each is a stream of paid model requests.
CREATE UNIQUE INDEX uq_call_reanalysis_job_active ON call_reanalysis_job (company_id)
    WHERE status IN ('QUEUED', 'RUNNING');
