-- Learned floor rules for barge-in turn-taking.
-- Captures regional/colloquial words and phrases evaluated by the LLM
-- so they can be decided with zero latency from in-memory cache without recurring LLM costs.
CREATE TABLE learned_floor_rule (
    id          BIGSERIAL    PRIMARY KEY,
    phrase      VARCHAR(100) NOT NULL UNIQUE,
    decision    VARCHAR(20)  NOT NULL, -- 'STOP' (taking the floor) or 'CONTINUE' (backchannel)
    occurrences INT          NOT NULL DEFAULT 1,
    status      VARCHAR(20)  NOT NULL DEFAULT 'LEARNING', -- 'LEARNING', 'APPROVED', 'BLOCKED'
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_learned_floor_rule_status ON learned_floor_rule (status, decision);
