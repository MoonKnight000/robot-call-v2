-- Caller ID Pools and Number Rotation (COMPETITOR-GAP-ANALYSIS §3.15)
-- Lets companies define a pool of outbound phone numbers, rotates caller IDs
-- according to strategies (ROUND_ROBIN, RANDOM, LEAST_USED, HIGHEST_ANSWER_RATE),
-- tracks answer rates, and allows cooling down tired numbers.

CREATE TABLE caller_id_pool (
    id           BIGSERIAL PRIMARY KEY,
    company_id   BIGINT       NOT NULL REFERENCES company(id),
    name         VARCHAR(100) NOT NULL,
    strategy     VARCHAR(32)  NOT NULL DEFAULT 'ROUND_ROBIN',
    description  VARCHAR(255),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_caller_id_pool_company ON caller_id_pool(company_id);

CREATE TABLE caller_id_pool_number (
    id            BIGSERIAL PRIMARY KEY,
    pool_id       BIGINT       NOT NULL REFERENCES caller_id_pool(id) ON DELETE CASCADE,
    phone         VARCHAR(32)  NOT NULL,
    label         VARCHAR(100),
    usage_count   BIGINT       NOT NULL DEFAULT 0,
    answer_count  BIGINT       NOT NULL DEFAULT 0,
    status        VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    cooling_until TIMESTAMPTZ,
    last_used_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_caller_id_pool_number_pool ON caller_id_pool_number(pool_id);
CREATE UNIQUE INDEX idx_caller_id_pool_number_unique ON caller_id_pool_number(pool_id, phone);

ALTER TABLE campaign ADD COLUMN IF NOT EXISTS caller_id_pool_id BIGINT REFERENCES caller_id_pool(id) ON DELETE SET NULL;
