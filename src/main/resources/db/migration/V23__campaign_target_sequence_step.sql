-- Where a target is in its campaign's sequence. step_id NULL = the sequence's start step (also every
-- target of a campaign without a sequence); step_attempts counts the calls spent on the current step.
ALTER TABLE campaign_target ADD COLUMN step_id VARCHAR(64);
ALTER TABLE campaign_target ADD COLUMN step_attempts INT NOT NULL DEFAULT 0;
