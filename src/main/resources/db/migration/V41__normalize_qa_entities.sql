-- Phase 2: QA Entity Normalization (3NF)
-- Remove redundant denormalized columns from call_quality that are already stored in call_attempt
DROP INDEX IF EXISTS idx_call_quality_agent;

ALTER TABLE call_quality
    DROP COLUMN IF EXISTS ai_agent_id,
    DROP COLUMN IF EXISTS agent_version,
    DROP COLUMN IF EXISTS disposition;
