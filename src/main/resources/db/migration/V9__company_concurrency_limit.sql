-- How many dialer calls one company may hold at once, set by the platform (its plan). Null:
-- the platform-wide default, voice-agent.dialer.max-concurrent-calls-per-company.
ALTER TABLE company
    ADD COLUMN max_concurrent_calls INT,
    ADD CONSTRAINT ck_company_max_concurrent_calls CHECK (max_concurrent_calls IS NULL OR max_concurrent_calls > 0);
