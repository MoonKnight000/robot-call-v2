-- Real per-call billing: what each call cost, an append-only money ledger, and the
-- balance a call is reserved against before it is dialled.
--
-- Before this migration the billing screens read hard-coded numbers and no call ever
-- moved a balance. The three pieces here are what makes the figures the customer sees
-- reproducible: call_billing says what one call was charged and at which price list,
-- billing_ledger says how every balance got to where it is, and the reservation columns
-- on company_billing hold money for calls that are still in the air.

-- What one call cost, and at which price list version.
CREATE TABLE call_billing (
    id                   BIGSERIAL PRIMARY KEY,
    -- Null while the hold is open: the money is put aside before Asterisk gives the
    -- call a channel, so the attempt row it will be charged to does not exist yet.
    call_attempt_id      BIGINT      UNIQUE REFERENCES call_attempt(id) ON DELETE CASCADE,
    -- What the hold was taken for. A hold and the call that spends it are paired through
    -- this rather than carried along the call itself, which would mean threading an
    -- amount through the queue, the originate and the media session to no other purpose.
    target_id            BIGINT      REFERENCES campaign_target(id) ON DELETE SET NULL,
    company_id           BIGINT      NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    -- RESERVED -> SETTLED (the call was charged) or RELEASED (it never connected).
    status               VARCHAR(16) NOT NULL DEFAULT 'RESERVED',
    rate_version         VARCHAR(32) NOT NULL,
    reserved_uzs         BIGINT      NOT NULL DEFAULT 0,
    -- Measured usage, kept so a disputed charge can be recomputed from the same inputs.
    duration_sec         INT         NOT NULL DEFAULT 0,
    prompt_tokens        BIGINT      NOT NULL DEFAULT 0,
    completion_tokens    BIGINT      NOT NULL DEFAULT 0,
    cached_tokens        BIGINT      NOT NULL DEFAULT 0,
    tts_chars            BIGINT      NOT NULL DEFAULT 0,
    -- The same usage priced, split the way an invoice line is read.
    llm_cost_uzs         BIGINT      NOT NULL DEFAULT 0,
    stt_cost_uzs         BIGINT      NOT NULL DEFAULT 0,
    tts_cost_uzs         BIGINT      NOT NULL DEFAULT 0,
    telephony_cost_uzs   BIGINT      NOT NULL DEFAULT 0,
    platform_fee_uzs     BIGINT      NOT NULL DEFAULT 0,
    total_uzs            BIGINT      NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    settled_at           TIMESTAMPTZ
);
CREATE INDEX idx_call_billing_company_created ON call_billing(company_id, created_at DESC);
CREATE INDEX idx_call_billing_open_hold ON call_billing(company_id, target_id, created_at)
    WHERE status = 'RESERVED';

-- Every movement of money, append-only. Application code never updates or deletes a row
-- here: a balance that can be edited in place cannot be audited, and the before/after
-- columns are what lets a disagreement about a balance be settled by reading the table.
CREATE TABLE billing_ledger (
    id                  BIGSERIAL PRIMARY KEY,
    company_id          BIGINT      NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    -- TOPUP, RESERVE, RELEASE, CALL_CHARGE, ADJUSTMENT.
    entry_type          VARCHAR(24) NOT NULL,
    -- Signed: what this entry did to the balance. A reservation does not move the
    -- balance, so it carries 0 and moves reserved_uzs instead.
    amount_uzs          BIGINT      NOT NULL,
    balance_before_uzs  BIGINT      NOT NULL,
    balance_after_uzs   BIGINT      NOT NULL,
    -- What caused it: 'call_attempt' + its id, 'payment_topup' + its payment id, etc.
    reference_type      VARCHAR(32),
    reference_id        VARCHAR(64),
    -- Makes replay safe: a retried settlement or a redelivered payment callback writes
    -- the same key and is rejected by the index rather than charged twice.
    idempotency_key     VARCHAR(128) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_billing_ledger_idempotency UNIQUE (company_id, idempotency_key)
);
CREATE INDEX idx_billing_ledger_company_created ON billing_ledger(company_id, created_at DESC);
CREATE INDEX idx_billing_ledger_reference ON billing_ledger(reference_type, reference_id);

-- Money held for calls that are still in the air. The balance itself is not touched
-- until the call is settled, so a company can see what it has left to spend as
-- balance_uzs - reserved_uzs. (auto_recharge already exists on this table; at what
-- balance and by how much is platform-wide, in config/billing.yml.)
ALTER TABLE company_billing
    ADD COLUMN reserved_uzs BIGINT NOT NULL DEFAULT 0;
