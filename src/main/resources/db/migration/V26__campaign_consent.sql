-- When somebody confirmed that the contacts this campaign calls were collected lawfully (consent, DNC laws,
-- opt-ins). NULL until then; a DRAFT campaign cannot be launched without it. Campaigns that already run
-- are not asked again.
ALTER TABLE campaign ADD COLUMN consent_confirmed_at TIMESTAMPTZ;
