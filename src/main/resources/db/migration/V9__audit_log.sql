-- Who did what through the API (PROJECT.md §11 — the API can dial real subscribers and
-- opt them out of future contact, and both are decisions someone has to answer for).
--
-- Not a general-purpose event log: only state-changing API actions are recorded, which
-- keeps it small enough to read by eye during an incident.
CREATE TABLE audit_log (
    id         BIGSERIAL PRIMARY KEY,
    actor      VARCHAR(64)  NOT NULL,   -- authenticated principal (api-key role)
    action     VARCHAR(64)  NOT NULL,   -- CAMPAIGN_CREATE, CAMPAIGN_START, CALL_ORIGINATE, ...
    entity     VARCHAR(32),             -- campaign, target, call
    entity_id  VARCHAR(64),             -- id or phone/channel, as text: not every entity is a bigint
    detail     TEXT,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_created ON audit_log(created_at DESC);
CREATE INDEX idx_audit_action ON audit_log(action, created_at DESC);
