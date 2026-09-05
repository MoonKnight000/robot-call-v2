-- Remove redundant disclosure_enabled column now that agent_persona governs disclosure
ALTER TABLE campaign DROP COLUMN IF EXISTS disclosure_enabled;
