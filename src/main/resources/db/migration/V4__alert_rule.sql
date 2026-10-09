-- A threshold a company sets on its own calls (AlertRuleScheduler evaluates the enabled ones
-- every few minutes and notifies ALERT_TRIGGERED). last_* is the evaluation state the panel
-- shows; last_triggered_at also drives the cooldown.
CREATE TABLE alert_rule (
    id                BIGSERIAL     PRIMARY KEY,
    company_id        BIGINT        NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    name              VARCHAR(100)  NOT NULL,
    metric            VARCHAR(32)   NOT NULL,
    comparator        VARCHAR(8)    NOT NULL,
    threshold         DOUBLE PRECISION NOT NULL,
    window_minutes    INT           NOT NULL,
    min_calls         INT           NOT NULL,
    cooldown_minutes  INT           NOT NULL,
    ai_agent_id       BIGINT        REFERENCES ai_agent(id) ON DELETE CASCADE,
    enabled           BOOLEAN       NOT NULL DEFAULT true,
    last_value        DOUBLE PRECISION,
    last_evaluated_at TIMESTAMPTZ,
    last_triggered_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_alert_rule_metric CHECK (metric IN ('SUCCESS_RATE', 'TRANSFER_RATE', 'FAILED_CALL_RATE',
        'NEGATIVE_SENTIMENT_RATE', 'AVG_TURN_LATENCY_MS', 'TOOL_ERROR_COUNT', 'CALL_COUNT')),
    CONSTRAINT ck_alert_rule_comparator CHECK (comparator IN ('ABOVE', 'BELOW'))
);
CREATE INDEX idx_alert_rule_company ON alert_rule (company_id);
CREATE INDEX idx_alert_rule_enabled ON alert_rule (id) WHERE enabled;
-- The rate metrics count a company's calls by when they ended.
CREATE INDEX idx_call_attempt_company_ended ON call_attempt (company_id, ended_at) WHERE ended_at IS NOT NULL;
