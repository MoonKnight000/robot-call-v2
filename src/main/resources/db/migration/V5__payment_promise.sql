-- A promise to pay a call recorded (PaymentPromiseRecorder), followed until it is kept or
-- broken (PaymentPromiseScheduler). One per call: the outbox may write a call's result twice.
CREATE TABLE payment_promise (
    id               BIGSERIAL     PRIMARY KEY,
    company_id       BIGINT        NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    call_attempt_id  BIGINT        NOT NULL REFERENCES call_attempt(id) ON DELETE CASCADE,
    -- The campaign target a broken promise is called back on; null for an inbound or manual call.
    target_id        BIGINT        REFERENCES campaign_target(id) ON DELETE SET NULL,
    -- Normalised like conversion_event.phone, so a reported payment finds its promise.
    phone            VARCHAR(20)   NOT NULL,
    promised_date    DATE          NOT NULL,
    promised_amount  NUMERIC(18, 2),
    status           VARCHAR(16)   NOT NULL DEFAULT 'OPEN',
    reminder_sent_at TIMESTAMPTZ,
    resolved_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_payment_promise_call UNIQUE (call_attempt_id),
    CONSTRAINT ck_payment_promise_status CHECK (status IN ('OPEN', 'KEPT', 'BROKEN', 'CANCELLED'))
);
CREATE INDEX idx_payment_promise_company_date ON payment_promise (company_id, promised_date);
CREATE INDEX idx_payment_promise_open ON payment_promise (id) WHERE status = 'OPEN';
CREATE INDEX idx_payment_promise_open_phone ON payment_promise (company_id, phone) WHERE status = 'OPEN';

-- How a company follows up its promises; a company with no row gets the defaults
-- (PaymentPromiseSettings.defaultsFor) — both automatic actions off.
CREATE TABLE payment_promise_settings (
    company_id           BIGINT       PRIMARY KEY REFERENCES company(id) ON DELETE CASCADE,
    reminder_enabled     BOOLEAN      NOT NULL,
    reminder_days_before INT          NOT NULL,
    reminder_template    VARCHAR(300) NOT NULL,
    grace_days           INT          NOT NULL,
    recall_on_broken     BOOLEAN      NOT NULL,
    payment_goal_key     VARCHAR(64),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- The scheduler asks, per open promise, whether a payment was reported for the number.
CREATE INDEX idx_conversion_event_phone ON conversion_event (company_id, phone, occurred_at);
