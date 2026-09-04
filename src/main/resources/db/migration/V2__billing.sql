-- Billing & Invoicing Module (BACKEND_AUDIT_AND_SPECS.md §2, §3, §5)

-- 1. Company Billing & Plan Overview
CREATE TABLE company_billing (
    id                 BIGSERIAL PRIMARY KEY,
    company_id         BIGINT NOT NULL UNIQUE REFERENCES company(id) ON DELETE CASCADE,
    plan_code          VARCHAR(64) NOT NULL DEFAULT 'PRO_MONTHLY',
    plan_name          VARCHAR(128) NOT NULL DEFAULT 'Professional (Pro)',
    balance_uzs        BIGINT NOT NULL DEFAULT 0,
    auto_recharge      BOOLEAN NOT NULL DEFAULT FALSE,
    next_billing_date  TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_company_billing_company ON company_billing(company_id);

-- 2. Resource Usage Records (per monthly billing period)
CREATE TABLE billing_usage (
    id                   BIGSERIAL PRIMARY KEY,
    company_id           BIGINT NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    billing_period       VARCHAR(7) NOT NULL, -- e.g. '2026-03'
    used_minutes         INT NOT NULL DEFAULT 0,
    limit_minutes        INT NOT NULL DEFAULT 5000,
    used_tokens          BIGINT NOT NULL DEFAULT 0,
    limit_tokens         BIGINT NOT NULL DEFAULT 2000000,
    used_tts_chars       BIGINT NOT NULL DEFAULT 0,
    limit_tts_chars      BIGINT NOT NULL DEFAULT 1000000,
    used_channels        INT NOT NULL DEFAULT 0,
    limit_channels       INT NOT NULL DEFAULT 30,
    overage_price_minute DOUBLE PRECISION NOT NULL DEFAULT 400.0,
    overage_price_token  DOUBLE PRECISION NOT NULL DEFAULT 0.05,
    overage_price_tts    DOUBLE PRECISION NOT NULL DEFAULT 0.02,
    total_spend_uzs      BIGINT NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_company_billing_period UNIQUE (company_id, billing_period)
);
CREATE INDEX idx_billing_usage_company_period ON billing_usage(company_id, billing_period);

-- 3. Invoices
CREATE TABLE invoice (
    id            VARCHAR(64) PRIMARY KEY, -- e.g. 'INV-2026-003'
    company_id    BIGINT NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    period_name   VARCHAR(64) NOT NULL,    -- e.g. 'Mart 2026'
    amount_uzs    BIGINT NOT NULL,
    status        VARCHAR(32) NOT NULL DEFAULT 'PENDING', -- PAID, PENDING, CANCELLED
    paid_at       TIMESTAMPTZ,
    pdf_file_path VARCHAR(255),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_invoice_company_created ON invoice(company_id, created_at DESC);

-- 4. Topup Transactions
CREATE TABLE payment_topup (
    id             BIGSERIAL PRIMARY KEY,
    payment_id     VARCHAR(64) NOT NULL UNIQUE, -- e.g. 'PAY-883921'
    company_id     BIGINT NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    user_id        BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    amount_uzs     BIGINT NOT NULL,
    payment_method VARCHAR(32) NOT NULL, -- PAYME, CLICK, BANK_TRANSFER
    status         VARCHAR(32) NOT NULL DEFAULT 'PENDING', -- PENDING, COMPLETED, FAILED, CANCELLED
    checkout_url   VARCHAR(1000),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    paid_at        TIMESTAMPTZ
);
CREATE INDEX idx_payment_topup_company ON payment_topup(company_id, created_at DESC);
