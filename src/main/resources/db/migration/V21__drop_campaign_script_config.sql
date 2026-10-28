-- campaign.script_config has been written as '{}' and never read since the script moved to the
-- AI agent (Campaign → AiAgent → Scenario). A campaign decides whom and when, not what is said.
ALTER TABLE campaign DROP COLUMN IF EXISTS script_config;
