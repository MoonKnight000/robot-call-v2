-- Which kind of list a campaign's target source is.
--
-- GENERIC is what campaign_target_source was built for: one request to the company's own
-- API, whose answer is already an array of rows carrying a phone each (TargetApiImporter).
--
-- UYSOT_DEBTORS cannot be expressed that way. Uysot's Open API has no endpoint that
-- answers "today's debtors with their phone numbers": POST /v1/open-api/contract/filter
-- knows which contracts are overdue (`delay` days) but carries no phone and no lead,
-- GET /v1/open-api/contract/{id} adds the lead, and only GET /v1/open-api/lead/{leadId}
-- has the contact phones. So that provider is a chain of three reads rather than a URL,
-- and the column below is what picks it.
ALTER TABLE campaign_target_source
    ADD COLUMN provider VARCHAR(24) NOT NULL DEFAULT 'GENERIC';

-- A UYSOT_DEBTORS source has no URL of its own: where it reads from is voice-agent.crm.*,
-- the same settings the rest of the CRM write-back already uses, so prod and dev switch
-- hosts in one place. GENERIC sources still require one — PUT /api/campaigns/{id}/target-source
-- rejects a blank url.
ALTER TABLE campaign_target_source
    ALTER COLUMN url DROP NOT NULL;
