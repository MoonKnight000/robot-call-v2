-- 1. Create campaign_dialer_config table (1:1 with campaign)
CREATE TABLE campaign_dialer_config (
    campaign_id             BIGINT PRIMARY KEY REFERENCES campaign(id) ON DELETE CASCADE,
    dial_mode               VARCHAR(20)  NOT NULL DEFAULT 'AI',
    call_queue_id           BIGINT       REFERENCES call_queue(id) ON DELETE SET NULL,
    max_ai_overflow_percent INT          NOT NULL DEFAULT 20,
    max_concurrent_calls    INT          NOT NULL DEFAULT 20,
    daily_call_cap          INT          NOT NULL DEFAULT 0,
    max_cps                 INTEGER,
    target_ordering         VARCHAR(16)  NOT NULL DEFAULT 'FIFO',
    caller_id_pool_id       BIGINT       REFERENCES caller_id_pool(id) ON DELETE SET NULL,
    from_number_id          BIGINT       REFERENCES phone_number(id) ON DELETE SET NULL,
    local_presence          BOOLEAN      NOT NULL DEFAULT FALSE,
    budget_uzs             BIGINT,
    daily_budget_uzs       BIGINT,
    CONSTRAINT ck_campaign_dialer_mode
        CHECK (dial_mode IN ('AI', 'PREVIEW', 'PROGRESSIVE', 'PREDICTIVE')),
    CONSTRAINT ck_campaign_dialer_target_ordering
        CHECK (target_ordering IN ('FIFO', 'SCORE')),
    CONSTRAINT ck_campaign_dialer_max_ai_overflow
        CHECK (max_ai_overflow_percent BETWEEN 0 AND 100)
);

CREATE INDEX idx_campaign_dialer_queue ON campaign_dialer_config(call_queue_id);
CREATE INDEX idx_campaign_dialer_caller_id_pool ON campaign_dialer_config(caller_id_pool_id);
CREATE INDEX idx_campaign_dialer_from_number ON campaign_dialer_config(from_number_id);

-- 2. Create campaign_schedule table (1:1 with campaign)
CREATE TABLE campaign_schedule (
    campaign_id             BIGINT PRIMARY KEY REFERENCES campaign(id) ON DELETE CASCADE,
    dial_window_start       TIME        NOT NULL DEFAULT '07:00',
    dial_window_end         TIME        NOT NULL DEFAULT '23:00',
    recurrence_type         VARCHAR(20) NOT NULL DEFAULT 'ONCE',
    recurring_day_of_month  INTEGER,
    cron_expression         VARCHAR(100),
    auto_reset_targets      BOOLEAN     NOT NULL DEFAULT false,
    start_at                TIMESTAMPTZ,
    end_at                  TIMESTAMPTZ
);

-- 3. Data migration from campaign to the new tables
INSERT INTO campaign_dialer_config (
    campaign_id, dial_mode, call_queue_id, max_ai_overflow_percent,
    max_concurrent_calls, daily_call_cap, max_cps, target_ordering,
    caller_id_pool_id, from_number_id, local_presence, budget_uzs, daily_budget_uzs
)
SELECT
    id, dial_mode, call_queue_id, max_ai_overflow_percent,
    max_concurrent_calls, daily_call_cap, max_cps, target_ordering,
    caller_id_pool_id, from_number_id, local_presence, budget_uzs, daily_budget_uzs
FROM campaign
ON CONFLICT (campaign_id) DO NOTHING;

INSERT INTO campaign_schedule (
    campaign_id, dial_window_start, dial_window_end, recurrence_type,
    recurring_day_of_month, cron_expression, auto_reset_targets,
    start_at, end_at
)
SELECT
    id, dial_window_start, dial_window_end, recurrence_type,
    recurring_day_of_month, cron_expression, auto_reset_targets,
    start_at, end_at
FROM campaign
ON CONFLICT (campaign_id) DO NOTHING;

-- 4. Drop old constraints from campaign table
ALTER TABLE campaign
    DROP CONSTRAINT IF EXISTS ck_campaign_ai_agent,
    DROP CONSTRAINT IF EXISTS ck_campaign_dial_mode,
    DROP CONSTRAINT IF EXISTS ck_campaign_target_ordering,
    DROP CONSTRAINT IF EXISTS campaign_max_ai_overflow_percent_check,
    DROP CONSTRAINT IF EXISTS campaign_call_queue_id_fkey,
    DROP CONSTRAINT IF EXISTS campaign_caller_id_pool_id_fkey,
    DROP CONSTRAINT IF EXISTS campaign_from_number_id_fkey;

-- 5. Drop moved columns from campaign table
ALTER TABLE campaign
    DROP COLUMN IF EXISTS dial_mode,
    DROP COLUMN IF EXISTS call_queue_id,
    DROP COLUMN IF EXISTS max_ai_overflow_percent,
    DROP COLUMN IF EXISTS max_concurrent_calls,
    DROP COLUMN IF EXISTS daily_call_cap,
    DROP COLUMN IF EXISTS max_cps,
    DROP COLUMN IF EXISTS target_ordering,
    DROP COLUMN IF EXISTS caller_id_pool_id,
    DROP COLUMN IF EXISTS from_number_id,
    DROP COLUMN IF EXISTS local_presence,
    DROP COLUMN IF EXISTS budget_uzs,
    DROP COLUMN IF EXISTS daily_budget_uzs,
    DROP COLUMN IF EXISTS dial_window_start,
    DROP COLUMN IF EXISTS dial_window_end,
    DROP COLUMN IF EXISTS recurrence_type,
    DROP COLUMN IF EXISTS recurring_day_of_month,
    DROP COLUMN IF EXISTS cron_expression,
    DROP COLUMN IF EXISTS auto_reset_targets,
    DROP COLUMN IF EXISTS start_at,
    DROP COLUMN IF EXISTS end_at;
