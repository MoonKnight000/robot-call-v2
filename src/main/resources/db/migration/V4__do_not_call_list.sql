-- Right-to-refuse, phone level (PROJECT.md §11.4).
--
-- V3 put do_not_call on campaign_target, which only opts a client out of the
-- campaign they happen to be in: re-importing the same number into a new campaign
-- creates a fresh row with the flag cleared and calling resumes. The spec says an
-- opt-out must hold for FUTURE campaigns too, so the list is keyed by phone.

CREATE TABLE do_not_call_list (
    id         BIGSERIAL PRIMARY KEY,
    phone      VARCHAR(20)  NOT NULL UNIQUE,
    reason     TEXT,
    source     VARCHAR(20)  NOT NULL DEFAULT 'CALL',   -- CALL, MANUAL, IMPORT
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Carry over the opt-outs recorded under the old per-target flag.
INSERT INTO do_not_call_list (phone, reason, source)
SELECT DISTINCT phone, 'migrated from campaign_target.do_not_call', 'MANUAL'
FROM campaign_target
WHERE do_not_call = true
ON CONFLICT (phone) DO NOTHING;
