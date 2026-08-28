-- Adds recurrence support to campaigns (PROJECT §10 / Recurring Schedules)
-- recurrence_type: ONCE (default), DAILY, WEEKLY, MONTHLY, CRON
ALTER TABLE campaign ADD COLUMN recurrence_type VARCHAR(20) NOT NULL DEFAULT 'ONCE';
ALTER TABLE campaign ADD COLUMN recurring_day_of_month INTEGER;
ALTER TABLE campaign ADD COLUMN cron_expression VARCHAR(100);
ALTER TABLE campaign ADD COLUMN auto_reset_targets BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE campaign ADD COLUMN last_run_at TIMESTAMPTZ;
