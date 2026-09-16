-- §3 M6 (docs/AUDIT-2026-09-13.md): optimistic locking for the rows an editor loads, changes
-- and saves. JPA @Version on CampaignEntity and AiAgentEntity; an edit that read an older
-- value is answered 409 ENTITY_VERSION_CONFLICT instead of overwriting what was saved since.
--
-- Only edits bump it. The dialer's own statements on campaign (status, last_run_at) are
-- targeted UPDATEs that neither check nor bump the version: an edit never writes those
-- columns (@DynamicUpdate), so there is nothing to lose between the two, and a dialer tick
-- must never fail because someone had the form open.
--
-- scenario gets no column: its rows are copy-on-write and it already has a version of its
-- own (scenario_key, version), which is the token ScenarioService checks.
ALTER TABLE campaign ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE ai_agent ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
