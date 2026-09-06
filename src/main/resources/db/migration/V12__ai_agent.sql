-- The AI agent: one row that says WHO speaks, pointing at the scenario that says WHAT is
-- said. Before this, "who speaks" lived in four places at once — the company's voice and
-- model settings, the scenario's own agentProfile (V11 folded the Agent Builder table into
-- it), the campaign's voice/persona/ambient columns, and the A/B variant — and each of
-- them overrode the next in an order only the code knew. Inbound calls made it worse:
-- having no campaign, they could not be given a voice or a persona at all without
-- inventing one, which is why the settings had been pushed into the scenario in the first
-- place. A scenario is a shared, versioned script; the voice a company reads it in is not
-- part of it.
--
-- The shape every hosted platform converged on (Retell agent -> conversation flow,
-- Vapi campaign -> assistant, Bolna agent_config + agent_prompts):
--
--     campaign      -> ai_agent -> scenario     (who to call, when)
--     inbound_route -> ai_agent -> scenario     (which DID answers with which agent)
--
-- so a call resolves its voice, persona and model in one read, whichever direction it came
-- from, and one scenario can be spoken by an Uzbek agent and a Russian one without being
-- copied.

CREATE TABLE ai_agent (
    id                     BIGSERIAL PRIMARY KEY,
    company_id             BIGINT       NOT NULL REFERENCES company(id),
    name                   VARCHAR(255) NOT NULL,
    description            VARCHAR(500),
    scenario_id            BIGINT       NOT NULL REFERENCES scenario(id),
    language               VARCHAR(10)  NOT NULL DEFAULT 'uz-UZ',
    tts_voice              VARCHAR(64)  REFERENCES tts_voice(id),
    persona                VARCHAR(30)  NOT NULL DEFAULT 'AI_ASSISTANT', -- AI_ASSISTANT, HUMAN_LIKE
    llm_model              VARCHAR(120),          -- NULL = the company's ai_model_config
    temperature            DOUBLE PRECISION,      -- NULL = the company's
    max_output_tokens      INT,                   -- NULL = the company's
    ambient_sound          VARCHAR(30)  NOT NULL DEFAULT 'OFF',
    emotion_adaptive_voice BOOLEAN      NOT NULL DEFAULT true,
    dtmf_input_enabled     BOOLEAN      NOT NULL DEFAULT false,
    voicemail_action       VARCHAR(30)  NOT NULL DEFAULT 'HANGUP',
    voicemail_message      VARCHAR(500),
    mid_call_sms_enabled   BOOLEAN      NOT NULL DEFAULT false,
    mid_call_sms_template  VARCHAR(500),
    enabled                BOOLEAN      NOT NULL DEFAULT true,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by             BIGINT
);
CREATE INDEX idx_ai_agent_company ON ai_agent(company_id);
CREATE INDEX idx_ai_agent_scenario ON ai_agent(scenario_id);

-- Voice per call language, for an agent that answers more than one: a ru-RU caller is
-- spoken to by a Russian voice and an uz-UZ one by an Uzbek voice, from the same agent.
CREATE TABLE ai_agent_language_voice (
    ai_agent_id BIGINT      NOT NULL REFERENCES ai_agent(id) ON DELETE CASCADE,
    language    VARCHAR(10) NOT NULL,
    tts_voice   VARCHAR(64) NOT NULL REFERENCES tts_voice(id),
    PRIMARY KEY (ai_agent_id, language)
);

-- Which trunks this agent may dial out from. Empty means the dialer balances across every
-- enabled trunk of the company, which is what campaign_sip_trunk meant before it moved
-- here: the line a customer sees a call arrive on belongs with the agent placing it, not
-- with the list of people being called.
CREATE TABLE ai_agent_sip_trunk (
    ai_agent_id  BIGINT NOT NULL REFERENCES ai_agent(id) ON DELETE CASCADE,
    sip_trunk_id BIGINT NOT NULL REFERENCES sip_trunk(id) ON DELETE CASCADE,
    PRIMARY KEY (ai_agent_id, sip_trunk_id)
);

-- ---------------------------------------------------------------------------
-- Backfill. Every campaign, inbound route and A/B variant that configured a call
-- becomes one agent carrying exactly the settings it had, so nothing that was
-- running changes behaviour. The correlation columns exist only for this file.
-- ---------------------------------------------------------------------------

ALTER TABLE ai_agent ADD COLUMN migrated_from_campaign_id BIGINT;
ALTER TABLE ai_agent ADD COLUMN migrated_from_route_id BIGINT;
ALTER TABLE ai_agent ADD COLUMN migrated_from_variant_id BIGINT;

ALTER TABLE campaign ADD COLUMN ai_agent_id BIGINT REFERENCES ai_agent(id);
ALTER TABLE inbound_route ADD COLUMN ai_agent_id BIGINT REFERENCES ai_agent(id);
ALTER TABLE campaign_variant ADD COLUMN ai_agent_id BIGINT REFERENCES ai_agent(id);

INSERT INTO ai_agent (company_id, name, description, scenario_id, language, tts_voice, persona,
                      ambient_sound, emotion_adaptive_voice, dtmf_input_enabled,
                      voicemail_action, voicemail_message, mid_call_sms_enabled,
                      mid_call_sms_template, created_by, migrated_from_campaign_id)
SELECT c.company_id, c.name, 'Migrated from campaign #' || c.id, c.scenario_id, c.default_language,
       c.tts_voice, c.agent_persona, c.ambient_sound, c.emotion_adaptive_voice,
       c.dtmf_input_enabled, c.voicemail_action, c.voicemail_message, c.mid_call_sms_enabled,
       c.mid_call_sms_template, c.created_by, c.id
FROM campaign c;

UPDATE campaign c SET ai_agent_id = a.id
FROM ai_agent a WHERE a.migrated_from_campaign_id = c.id;

INSERT INTO ai_agent_language_voice (ai_agent_id, language, tts_voice)
SELECT a.id, v.language, v.tts_voice
FROM campaign_language_voice v
         JOIN ai_agent a ON a.migrated_from_campaign_id = v.campaign_id;

INSERT INTO ai_agent_sip_trunk (ai_agent_id, sip_trunk_id)
SELECT a.id, t.sip_trunk_id
FROM campaign_sip_trunk t
         JOIN ai_agent a ON a.migrated_from_campaign_id = t.campaign_id;

-- A route that answered with a scenario gets its own agent; one that rings a queue, an IVR
-- or a user never spoke, so it gets none.
INSERT INTO ai_agent (company_id, name, description, scenario_id, language, migrated_from_route_id)
SELECT r.company_id, 'Inbound ' || r.did_number, 'Migrated from inbound route #' || r.id,
       r.scenario_id, r.language, r.id
FROM inbound_route r
WHERE r.scenario_id IS NOT NULL;

UPDATE inbound_route r SET ai_agent_id = a.id
FROM ai_agent a WHERE a.migrated_from_route_id = r.id;

-- A variant only ever overrode the scenario and the voice. It keeps its voice override
-- (campaign_variant.tts_voice_id) and its prompt override; the scenario override becomes a
-- second agent, which is also what makes "test the whole agent, not just its script"
-- possible from here on.
INSERT INTO ai_agent (company_id, name, description, scenario_id, language, tts_voice, persona,
                      ambient_sound, emotion_adaptive_voice, dtmf_input_enabled,
                      voicemail_action, voicemail_message, mid_call_sms_enabled,
                      mid_call_sms_template, migrated_from_variant_id)
SELECT c.company_id, c.name || ' / ' || v.name, 'Migrated from A/B variant #' || v.id,
       v.scenario_id, c.default_language, c.tts_voice, c.agent_persona, c.ambient_sound,
       c.emotion_adaptive_voice, c.dtmf_input_enabled, c.voicemail_action, c.voicemail_message,
       c.mid_call_sms_enabled, c.mid_call_sms_template, v.id
FROM campaign_variant v
         JOIN campaign c ON c.id = v.campaign_id
WHERE v.scenario_id IS NOT NULL;

UPDATE campaign_variant v SET ai_agent_id = a.id
FROM ai_agent a WHERE a.migrated_from_variant_id = v.id;

ALTER TABLE ai_agent DROP COLUMN migrated_from_campaign_id;
ALTER TABLE ai_agent DROP COLUMN migrated_from_route_id;
ALTER TABLE ai_agent DROP COLUMN migrated_from_variant_id;

ALTER TABLE campaign ALTER COLUMN ai_agent_id SET NOT NULL;
CREATE INDEX idx_campaign_ai_agent ON campaign(ai_agent_id);
CREATE INDEX idx_inbound_route_ai_agent ON inbound_route(ai_agent_id);

-- ---------------------------------------------------------------------------
-- The old homes of "who speaks", now that the agent is the only one.
-- ---------------------------------------------------------------------------

DROP TABLE campaign_language_voice;
DROP TABLE campaign_sip_trunk;

ALTER TABLE campaign
    DROP COLUMN scenario_id,
    DROP COLUMN default_language,
    DROP COLUMN tts_voice,
    DROP COLUMN ambient_sound,
    DROP COLUMN mid_call_sms_enabled,
    DROP COLUMN mid_call_sms_template,
    DROP COLUMN voicemail_action,
    DROP COLUMN voicemail_message,
    DROP COLUMN dtmf_input_enabled,
    DROP COLUMN emotion_adaptive_voice,
    DROP COLUMN agent_persona;

ALTER TABLE inbound_route
    DROP COLUMN scenario_id,
    DROP COLUMN language;

ALTER TABLE campaign_variant DROP COLUMN scenario_id;

-- The scenario's own copy of the five agent settings (V11's AgentProfile). Removing the
-- key rather than the whole definition: everything else in there is the script itself.
UPDATE scenario SET definition = definition - 'agentProfile'
WHERE definition ? 'agentProfile';
