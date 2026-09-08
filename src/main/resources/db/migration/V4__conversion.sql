-- Did the call actually work? Until now the answer was the disposition the bot recorded —
-- "PROMISE_TO_PAY" — which is a promise, not a payment. These three tables let a company's
-- own system say what really happened afterwards, and tie it back to the call, the
-- campaign and the A/B variant that produced it.

-- What counts as a conversion for this company, and how long after a call it still counts.
-- Company-scoped rather than per campaign: the system posting the event knows a customer
-- paid, not which campaign called them — that is what attribution works out.
CREATE TABLE conversion_goal (
    id                      BIGSERIAL PRIMARY KEY,
    company_id              BIGINT       NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    -- What the posting system names it: 'payment', 'appointment', 'renewal'.
    goal_key                VARCHAR(64)  NOT NULL,
    name                    VARCHAR(128) NOT NULL,
    -- How long after a call a conversion may still be credited to it. Past this the
    -- customer is assumed to have acted for some other reason.
    attribution_window_hours INT         NOT NULL DEFAULT 72,
    -- LAST_CALL or FIRST_CALL: which call in the window gets the credit.
    attribution_model       VARCHAR(24)  NOT NULL DEFAULT 'LAST_CALL',
    enabled                 BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_conversion_goal_key UNIQUE (company_id, goal_key)
);
CREATE INDEX idx_conversion_goal_company ON conversion_goal(company_id);

-- One thing that happened out in the world, as reported to us.
CREATE TABLE conversion_event (
    id                BIGSERIAL PRIMARY KEY,
    company_id        BIGINT       NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    goal_key          VARCHAR(64)  NOT NULL,
    -- The customer, matched to a call by the number that was dialled. A phone is what
    -- both sides already agree on; an internal id would need a mapping table that has to
    -- be kept in step with somebody else's CRM.
    phone             VARCHAR(20)  NOT NULL,
    occurred_at       TIMESTAMPTZ  NOT NULL,
    value_uzs         BIGINT,
    -- Where it came from: 'crm', 'payme', an integration's name.
    source            VARCHAR(64),
    -- Whatever the poster wants kept for later argument: an order id, a receipt.
    evidence          JSONB,
    -- The poster's own id for the thing. A retried webhook writes the same key and is
    -- refused by the index rather than counted twice.
    dedupe_key        VARCHAR(128) NOT NULL,
    -- Kept, not dropped: "no call in the window" is a useful answer, and deleting the row
    -- would make the same event arrive again on the next retry.
    rejected          BOOLEAN      NOT NULL DEFAULT FALSE,
    rejection_reason  VARCHAR(255),
    ingested_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_conversion_event_dedupe UNIQUE (company_id, dedupe_key)
);
CREATE INDEX idx_conversion_event_company_occurred ON conversion_event(company_id, occurred_at DESC);
CREATE INDEX idx_conversion_event_goal ON conversion_event(company_id, goal_key);

-- Which call earned it. One row per attributed event; an event that matched nothing has
-- no row here and carries its reason on conversion_event instead.
CREATE TABLE conversion_attribution (
    id                    BIGSERIAL PRIMARY KEY,
    conversion_event_id   BIGINT      NOT NULL UNIQUE REFERENCES conversion_event(id) ON DELETE CASCADE,
    company_id            BIGINT      NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    campaign_id           BIGINT      REFERENCES campaign(id) ON DELETE SET NULL,
    variant_id            BIGINT      REFERENCES campaign_variant(id) ON DELETE SET NULL,
    call_attempt_id       BIGINT      REFERENCES call_attempt(id) ON DELETE SET NULL,
    -- Stamped so an old attribution can still be explained after the goal is retuned.
    attribution_model     VARCHAR(24) NOT NULL,
    window_hours          INT         NOT NULL,
    attributed_value_uzs  BIGINT,
    computed_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_conversion_attribution_campaign ON conversion_attribution(campaign_id, computed_at DESC);
CREATE INDEX idx_conversion_attribution_variant ON conversion_attribution(variant_id);

-- Attribution looks up a company's answered calls to one number. Without this it is a
-- sequential scan of every call the platform ever placed, on every event posted.
CREATE INDEX idx_call_attempt_company_phone_answered
    ON call_attempt(company_id, phone, answered_at)
    WHERE answered_at IS NOT NULL;
