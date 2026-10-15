-- Free trial money a self-registered company starts with (TrialCreditService). One row per
-- company at most — the unique key is what makes "one trial per company" hold even when two
-- signups race. The money itself moves through billing_ledger (TRIAL_CREDIT / TRIAL_EXPIRE);
-- this row only remembers how much was given, until when, and what was taken back.
CREATE TABLE billing_trial (
    id            BIGSERIAL PRIMARY KEY,
    company_id    BIGINT      NOT NULL UNIQUE REFERENCES company(id) ON DELETE CASCADE,
    credit_uzs    BIGINT      NOT NULL CHECK (credit_uzs > 0),
    granted_at    TIMESTAMPTZ NOT NULL,
    expires_at    TIMESTAMPTZ NOT NULL,
    -- Set once the unspent remainder has been taken back; null while the trial runs.
    settled_at    TIMESTAMPTZ,
    clawback_uzs  BIGINT
);

CREATE INDEX idx_billing_trial_unsettled ON billing_trial (expires_at) WHERE settled_at IS NULL;
