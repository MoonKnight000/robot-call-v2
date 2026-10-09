-- How the dialer picks the next due targets of a campaign: FIFO (oldest due first, as
-- before) or SCORE (most likely to pay first, with a bonus for the hour they usually answer).
ALTER TABLE campaign
    ADD COLUMN target_ordering VARCHAR(16) NOT NULL DEFAULT 'FIFO',
    ADD CONSTRAINT ck_campaign_target_ordering CHECK (target_ordering IN ('FIFO', 'SCORE'));

-- Written by TargetScoringScheduler from the company's history with the number; null until
-- the target is scored, and then read as neutral.
ALTER TABLE campaign_target
    ADD COLUMN score          INT,
    ADD COLUMN best_call_hour INT,
    ADD COLUMN scored_at      TIMESTAMPTZ,
    ADD CONSTRAINT ck_campaign_target_score CHECK (score IS NULL OR score BETWEEN 0 AND 100),
    ADD CONSTRAINT ck_campaign_target_best_call_hour
        CHECK (best_call_hour IS NULL OR best_call_hour BETWEEN 0 AND 23);

-- The scheduler walks a campaign's waiting targets that were never scored, or not lately.
CREATE INDEX idx_campaign_target_to_score ON campaign_target (campaign_id, scored_at NULLS FIRST, id)
    WHERE status = 'PENDING';
