-- Follow-ups from docs/AUDIT-2026-09-13.md.

-- §4.17: a new company starts with a contact-frequency policy (CompanyConfig.defaults()
-- writes the same numbers). Existing companies keep the settings they have.
ALTER TABLE company_config ALTER COLUMN max_attempts_per_phone_per_week SET DEFAULT 3;
ALTER TABLE company_config ALTER COLUMN quiet_days_after_conversation SET DEFAULT 1;

-- §3 H5: the summary retry now reads only calls that ended in the last day
-- (CallAttemptJpaRepository#attemptsAwaitingSummary). The old partial index matched nearly
-- every finished call: finalize_attempts stays 0 on a call summarized the first time.
DROP INDEX IF EXISTS idx_call_attempt_awaiting_summary;
CREATE INDEX idx_call_attempt_ended_at ON call_attempt(ended_at) WHERE ended_at IS NOT NULL;

-- §3 M8: the queue and operator reports group call_event rows by these ids, which were
-- cast out of the JSON on every run. A value that is not a number becomes NULL here rather
-- than failing the insert that carries it.
ALTER TABLE call_event
    ADD COLUMN queue_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN payload ->> 'queueId' ~ '^[0-9]{1,18}$' THEN (payload ->> 'queueId')::bigint END) STORED,
    ADD COLUMN user_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN payload ->> 'userId' ~ '^[0-9]{1,18}$' THEN (payload ->> 'userId')::bigint END) STORED;
