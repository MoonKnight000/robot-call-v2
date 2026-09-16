-- UNIVERSAL-OPERATOR-PLAN D.1–D.2: one row per finished AI call — the quality judge's verdict,
-- the signals read without an LLM, and a person's review of it. The judge's answer used to go
-- to the metrics only; kept here, it is what the review inbox and the daily insight job read.
CREATE TABLE call_quality (
    id                  BIGSERIAL    PRIMARY KEY,
    company_id          BIGINT       NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    call_attempt_id     BIGINT       NOT NULL UNIQUE REFERENCES call_attempt(id) ON DELETE CASCADE,
    ai_agent_id         BIGINT       REFERENCES ai_agent(id) ON DELETE SET NULL,
    agent_version       INT,
    disposition         VARCHAR(50),
    -- The verdict. NULL throughout when the judge did not score the call: off, not sampled, or failed.
    score               INT,
    guardrail_violation BOOLEAN,
    language_mismatch   BOOLEAN,
    talked_over_client  BOOLEAN,
    outcome_correct     BOOLEAN,
    goal_reached        BOOLEAN,
    unanswered_question BOOLEAN,
    tool_misuse         BOOLEAN,
    note                TEXT,
    needs_review        BOOLEAN      NOT NULL DEFAULT false,
    -- Signals read without an LLM. repeat_call is set later, by the caller's next call within a day.
    turn_count          INT          NOT NULL DEFAULT 0,
    repair_count        INT          NOT NULL DEFAULT 0,
    tool_error_count    INT          NOT NULL DEFAULT 0,
    repeat_call         BOOLEAN      NOT NULL DEFAULT false,
    -- The review. NULL status: nothing flagged and nobody looked.
    review_status       VARCHAR(20),
    review_reason       TEXT,
    reviewed_by         BIGINT       REFERENCES app_user(id) ON DELETE SET NULL,
    reviewed_at         TIMESTAMPTZ,
    knowledge_item_id   BIGINT       REFERENCES knowledge_base_item(id) ON DELETE SET NULL,
    test_case_id        BIGINT       REFERENCES agent_test_case(id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_call_quality_review_status CHECK (review_status IN ('PENDING', 'AGENT_CORRECT', 'AGENT_WRONG'))
);
CREATE INDEX idx_call_quality_review ON call_quality(company_id, created_at DESC) WHERE review_status IS NOT NULL;
CREATE INDEX idx_call_quality_agent ON call_quality(ai_agent_id, created_at DESC);
