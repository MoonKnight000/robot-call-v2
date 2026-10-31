-- A campaign is STANDARD (a hand-built call list) or AUTO_ENROLL (contacts join by rule), and may carry
-- a sequence: the conversation / SMS / wait steps a contact goes through. NULL sequence = a campaign
-- from before sequences, read as one conversation step run by campaign.ai_agent_id.
ALTER TABLE campaign ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'STANDARD';
ALTER TABLE campaign ADD COLUMN sequence JSONB;
