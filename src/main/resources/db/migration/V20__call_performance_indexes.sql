-- Performance indexes for high-volume call records, timelines, reporting and retention sweeps.

-- Faster call list, date range queries, and call history filtering by company and started_at
CREATE INDEX IF NOT EXISTS idx_call_attempt_company_started
    ON call_attempt(company_id, started_at DESC);

-- Faster retention sweep and global date-range lookups on call_attempt
CREATE INDEX IF NOT EXISTS idx_call_attempt_created
    ON call_attempt(created_at DESC);

-- Faster timeline queries and report queries by company and event timestamp
CREATE INDEX IF NOT EXISTS idx_call_event_company_at
    ON call_event(company_id, at DESC);

-- Faster retention purge of old call events by timestamp
CREATE INDEX IF NOT EXISTS idx_call_event_at
    ON call_event(at DESC);
