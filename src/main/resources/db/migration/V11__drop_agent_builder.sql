-- Agent Builder is gone; a scenario now carries its own agent profile.
--
-- The `agent` table held five settings — system prompt, greeting, voice, temperature,
-- max tokens — every one of which already had an owner elsewhere (scenario.rolePrompt,
-- campaign.tts_voice, ai_model_config), and nothing outside its own package ever read it.
-- Those five now live inside scenario.definition -> agentProfile, which is JSONB, so this
-- migration only removes what the merge left behind.

ALTER TABLE campaign_variant DROP COLUMN IF EXISTS agent_id;
ALTER TABLE campaign DROP COLUMN IF EXISTS agent_id;

DROP TABLE IF EXISTS agent;

-- Permission.AGENT_READ / AGENT_EDIT no longer exist in the enum, and a row naming a
-- permission the enum cannot parse breaks role loading — so the grants go with them.
DELETE FROM app_role_permission WHERE permission IN ('AGENT_READ', 'AGENT_EDIT');
