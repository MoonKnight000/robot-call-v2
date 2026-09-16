-- UNIVERSAL-OPERATOR-PLAN D.3–D.4: what a day of an agent's calls says it should change, and how
-- far that change has got. The daily job proposes; a person accepts or rejects; and a prompt
-- change that was accepted waits behind an evaluation run before it is allowed to stay.
CREATE TABLE agent_insight (
    id              BIGSERIAL    PRIMARY KEY,
    company_id      BIGINT       NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    ai_agent_id     BIGINT       NOT NULL REFERENCES ai_agent(id) ON DELETE CASCADE,
    type            VARCHAR(30)  NOT NULL,
    title           VARCHAR(200) NOT NULL,
    -- What was seen, in the operator's language, and the one concrete change it suggests.
    detail          TEXT,
    suggestion      TEXT,
    call_count      INT          NOT NULL DEFAULT 0,
    -- The calls it was read from: the evidence a person opens before deciding.
    sample_call_ids JSONB        NOT NULL DEFAULT '[]',
    status          VARCHAR(20)  NOT NULL,
    decision_reason TEXT,
    decided_by      BIGINT       REFERENCES app_user(id) ON DELETE SET NULL,
    decided_at      TIMESTAMPTZ,
    -- The gate: the evaluation run an accepted prompt change is waiting on.
    eval_run_id     BIGINT       REFERENCES agent_eval_run(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_agent_insight_type CHECK (type IN
        ('KB_GAP', 'PROMPT_FIX', 'TOOL_FIX', 'NEW_TEST_CASE', 'PRONUNCIATION', 'HANDOFF_PATTERN')),
    CONSTRAINT ck_agent_insight_status CHECK (status IN ('PROPOSED', 'EVALUATING', 'ACCEPTED', 'REJECTED'))
);
CREATE INDEX idx_agent_insight_inbox ON agent_insight(company_id, status, created_at DESC);
-- The job runs every day over an overlapping window, so the same finding would be proposed
-- again tomorrow. One open proposal per agent, type and title is all the inbox may hold.
CREATE UNIQUE INDEX uq_agent_insight_open ON agent_insight(ai_agent_id, type, title) WHERE status = 'PROPOSED';
CREATE INDEX idx_agent_insight_eval_run ON agent_insight(eval_run_id) WHERE eval_run_id IS NOT NULL;
