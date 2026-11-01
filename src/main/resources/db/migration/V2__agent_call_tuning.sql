-- Retell-style per-agent call tuning. NULL in each column means "the deployment default".
ALTER TABLE ai_agent
    ADD COLUMN reminder_after_seconds  INTEGER,
    ADD COLUMN reminder_max_count      INTEGER,
    ADD COLUMN dtmf_timeout_ms         INTEGER,
    ADD COLUMN dtmf_termination_key    VARCHAR(1),
    ADD COLUMN dtmf_digit_limit        INTEGER,
    ADD COLUMN ring_duration_seconds   INTEGER;
