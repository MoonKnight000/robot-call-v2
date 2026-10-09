-- Baseline schema for the AI voice agent (PROJECT.md §6). This is the whole schema in one
-- file: nothing has shipped yet, so there is no production history worth replaying step by
-- step, and a fresh database is created from this file plus R__seed_data.sql alone. Every
-- migration written so far is folded in here rather than kept as its own step — a column
-- added and then re-defaulted twice is noise once no database anywhere replays it.
--
-- Tables are declared in dependency order so every REFERENCES points at something that
-- already exists. Two pairs reference each other and are closed with an ALTER once both
-- exist: company <-> stored_file (company.logo_file_id), ai_agent <-> call_queue
-- (ai_agent.transfer_queue_id) and campaign <-> call_queue (campaign.call_queue_id).

-- pgvector backs catalog_item's embedding index. The database image has to carry the
-- extension: docker-compose runs pgvector/pgvector:pg18 (docs/RUN.md).
CREATE EXTENSION IF NOT EXISTS vector;

-- ---------------------------------------------------------------------------
-- Tenant, files, company settings
-- ---------------------------------------------------------------------------

-- Company/tenant. Every other table below is scoped to one.
CREATE TABLE company (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,
    status       VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, SUSPENDED
    logo_file_id BIGINT,
    address      VARCHAR(500),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- StoredFile catalog: files stay in MinIO, this table is metadata only (id, original
-- name, MinIO object key/"path", format, size, owning company, category).
CREATE TABLE stored_file (
    id            BIGSERIAL PRIMARY KEY,
    company_id    BIGINT       NOT NULL REFERENCES company(id),
    category      VARCHAR(20)  NOT NULL,   -- IMAGE | AUDIO | DOCUMENT
    original_name VARCHAR(255) NOT NULL,
    path          VARCHAR(500) NOT NULL,   -- MinIO object key: com-{companyId}/{category}/<uuid>.<ext>
    bucket        VARCHAR(100) NOT NULL,
    format        VARCHAR(100),            -- content-type, e.g. image/png
    size_bytes    BIGINT       NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_stored_file_company ON stored_file(company_id);
CREATE INDEX idx_stored_file_category_created ON stored_file(category, created_at);

ALTER TABLE company ADD CONSTRAINT fk_company_logo_file
    FOREIGN KEY (logo_file_id) REFERENCES stored_file(id) ON DELETE SET NULL;

-- Company operational settings — working hours window, timezone, default language, and the
-- contact policy.
--
-- Contact policy: how often one phone number may be called, across every campaign the
-- company runs — not per campaign, which is the only cap there was. Collection rules
-- elsewhere (US Regulation F: more than 7 attempts in 7 days, or a call within 7 days of a
-- conversation, is presumed harassment) are written in these terms; the numbers are the
-- company's to set against Uzbek law.
--
--   max_attempts_per_phone_per_week  placed outbound attempts to one number in any 7 days; 0 = no cap
--   quiet_days_after_conversation    days after a real conversation before the next outbound call; 0 = none
--   respect_promised_date            no call while a promise to pay is still in its term
--
-- human_like_persona_allowed: whether this company's agents may present as a person and
-- skip the §11.1 "this is an automated call" disclosure. A legal decision, not a voice
-- setting — so it lives with the company and is audited there, and a new company starts
-- without it until a platform admin allows it.
--
-- holidays: days the company does not call at all — public holidays, Navruz, a company day
-- off. ISO dates, comma-separated; empty means every allowed weekday dials. The dialer skips
-- them in the company's own time zone and the retry schedule steps over them.
--
-- scorecard_review_threshold: an operator call scored below it goes to the review inbox.
--
-- The column defaults match CompanyConfig.defaults(), which the application writes
-- explicitly; they apply to a row inserted without the columns, such as the seeded tenant.
CREATE TABLE company_config (
    id                              BIGSERIAL PRIMARY KEY,
    company_id                      BIGINT      NOT NULL REFERENCES company(id),
    dial_window_start               TIME        NOT NULL DEFAULT '09:00',
    dial_window_end                 TIME        NOT NULL DEFAULT '20:00',
    timezone                        VARCHAR(64) NOT NULL,
    default_language                VARCHAR(10) NOT NULL,
    disclosure_text                 VARCHAR(500),
    max_attempts_per_phone_per_week INT         NOT NULL DEFAULT 3,
    quiet_days_after_conversation   INT         NOT NULL DEFAULT 1,
    respect_promised_date           BOOLEAN     NOT NULL DEFAULT true,
    human_like_persona_allowed      BOOLEAN     NOT NULL DEFAULT false,
    holidays                        TEXT        NOT NULL DEFAULT '',
    scorecard_review_threshold      INT         NOT NULL DEFAULT 60
        CHECK (scorecard_review_threshold BETWEEN 0 AND 100),
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX idx_company_config_company ON company_config(company_id);

-- Ordered list backing CompanyConfig.supportedLanguages (@ElementCollection + @OrderColumn).
CREATE TABLE company_config_language (
    company_config_id BIGINT      NOT NULL REFERENCES company_config(id),
    ord                INT         NOT NULL,
    language           VARCHAR(10) NOT NULL,
    PRIMARY KEY (company_config_id, ord)
);

-- ---------------------------------------------------------------------------
-- Voice catalog and scenarios — what an agent says and what it sounds like
-- ---------------------------------------------------------------------------

-- TTS voice catalog (PROJECT.md §2.5). Seeded in R__seed_data.sql.
--
-- Voices pulled from a provider's own list API (TtsVoiceSyncScheduler) sit beside the seeded
-- ones. A seeded row has synced_at NULL and is never touched by the sync. A synced voice the
-- provider no longer lists is switched off rather than deleted: ai_agent, ai_agent_voice and
-- campaign_variant still reference it, and an agent saved with it keeps speaking with it.
CREATE TABLE tts_voice (
    id        VARCHAR(64)  PRIMARY KEY,   -- stable id stored on ai_agent.tts_voice, e.g. 'nigora'
    provider  VARCHAR(50)  NOT NULL,      -- yandex | aisha | gemini | gemini-live
    language  VARCHAR(10)  NOT NULL,      -- BCP-47, e.g. uz-UZ
    name      VARCHAR(100) NOT NULL,      -- provider-side voice name sent with synthesis
    label     VARCHAR(255) NOT NULL,      -- human-readable name for the agent form
    role      VARCHAR(32),                -- voice mood or role nuance (optional)
    active    BOOLEAN      NOT NULL DEFAULT true,
    synced_at TIMESTAMPTZ
);

-- Model catalog. What ai_model_config.model and ai_agent.llm_model / stt_model / tts_model
-- may be set to, seeded in R__seed_data.sql — a table rather than a YAML list so the agent
-- form asks the same source the save-time check does, and a wrong model id is a 400 instead
-- of a call that fails when it is already ringing.
--
-- kind splits the catalog into the three families (LLM, STT, TTS). They never mix: what
-- Deepgram calls nova-3 is not a model Gemini answers for, and neither is a chat model id.
-- mode splits two sets that never overlap: a CASCADE model reads text and writes text,
-- a REALTIME one hears and speaks, and neither provider recognises the other's ids.
CREATE TABLE ai_model (
    id       VARCHAR(120) PRIMARY KEY,   -- model id sent to the provider, e.g. 'gemini-3.8-flash'
    kind     VARCHAR(10)  NOT NULL DEFAULT 'LLM', -- LLM | STT | TTS
    provider VARCHAR(50)  NOT NULL,      -- google-genai | openai | gemini-live | openai-realtime | qwen-omni | moshi | pipecat
    mode     VARCHAR(20)  NOT NULL,      -- CASCADE | REALTIME
    label    VARCHAR(255) NOT NULL       -- human-readable name for the settings and agent forms
);

-- Scenario storage — holds both built-in templates (is_builtin = true, company_id = null)
-- and company-owned custom scenarios (company_id != null). A scenario is WHAT is said;
-- WHO says it is ai_agent below.
CREATE TABLE scenario (
    id           BIGSERIAL PRIMARY KEY,
    company_id   BIGINT       REFERENCES company(id),
    scenario_key VARCHAR(64)  NOT NULL,
    version      INT          NOT NULL DEFAULT 1,
    name         VARCHAR(255) NOT NULL,
    description  TEXT,
    is_builtin   BOOLEAN      NOT NULL DEFAULT false,
    is_active    BOOLEAN      NOT NULL DEFAULT true,
    definition   JSONB        NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by   BIGINT
);
-- Only active scenarios need uniqueness on scenario_key; historical versions do not conflict.
CREATE UNIQUE INDEX idx_scenario_active_key ON scenario(scenario_key) WHERE is_active;
CREATE INDEX idx_scenario_key_version ON scenario(scenario_key, version DESC);
CREATE INDEX idx_scenario_company ON scenario(company_id);
-- ScenarioService.update writes maxVersion + 1, so two concurrent edits could both write the
-- same version; with this index the second one fails cleanly instead of leaving two rows
-- that claim one version.
CREATE UNIQUE INDEX uq_scenario_key_version ON scenario(scenario_key, version);

-- ---------------------------------------------------------------------------
-- Telephony endpoints
-- ---------------------------------------------------------------------------

-- SIP Trunks — connects Asterisk to telecom providers or virtual PBX endpoints.
-- max_cps: calls per second the trunk accepts (docs/VOICE-PIPELINE-PLAN.md 4.4) — the
-- provider rejects or drops INVITEs past its limit; NULL falls back to
-- voice-agent.dialer.default-trunk-cps.
CREATE TABLE sip_trunk (
    id               BIGSERIAL PRIMARY KEY,
    company_id       BIGINT       NOT NULL REFERENCES company(id),
    name             VARCHAR(255) NOT NULL,
    pjsip_endpoint   VARCHAR(128) NOT NULL,
    caller_id        VARCHAR(20),
    host             VARCHAR(255),
    port             INT          NOT NULL DEFAULT 5060,
    sip_username     VARCHAR(255),
    sip_password_enc TEXT,
    transport        VARCHAR(10)  NOT NULL DEFAULT 'UDP',
    codecs           VARCHAR(255),
    is_default       BOOLEAN      NOT NULL DEFAULT false,
    enabled          BOOLEAN      NOT NULL DEFAULT true,
    max_cps          INT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_sip_trunk_company ON sip_trunk(company_id);
CREATE UNIQUE INDEX idx_sip_trunk_default ON sip_trunk(company_id) WHERE is_default;

-- ---------------------------------------------------------------------------
-- Roles and users
-- ---------------------------------------------------------------------------

-- Roles are data, not a hard-coded tier on app_user: every panel page has its own
-- READ/EDIT permission (Permission.java) and a company composes them into roles — the
-- seeded system roles plus up to ten of its own. A system role stores no permission rows
-- at all; they are computed from its code in SystemRole.java, so a permission added in a
-- later release reaches DEVELOPER and ADMIN without a data migration and can never drift
-- from the code.
CREATE TABLE app_role (
    id          BIGSERIAL PRIMARY KEY,
    company_id  BIGINT       NOT NULL REFERENCES company(id),
    code        VARCHAR(50),             -- DEVELOPER, ADMIN, OPERATOR, VIEWER, SUPERVISOR, SUPERADMIN; NULL for a company's own role
    name        VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    is_system   BOOLEAN      NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- NULLs are distinct in Postgres, so this constrains system role codes only.
CREATE UNIQUE INDEX idx_app_role_company_code ON app_role(company_id, code);
CREATE UNIQUE INDEX idx_app_role_company_name ON app_role(company_id, lower(name));
CREATE INDEX idx_app_role_company ON app_role(company_id);

CREATE TABLE app_role_permission (
    role_id    BIGINT      NOT NULL REFERENCES app_role(id) ON DELETE CASCADE,
    permission VARCHAR(60) NOT NULL,
    PRIMARY KEY (role_id, permission)
);

-- Users — authentication and authorization for operators, admins, viewers, superadmins.
CREATE TABLE app_user (
    id                BIGSERIAL PRIMARY KEY,
    company_id        BIGINT       NOT NULL REFERENCES company(id),
    name              VARCHAR(255) NOT NULL,
    email             VARCHAR(255) NOT NULL,
    username          VARCHAR(100) NOT NULL,
    password_hash     VARCHAR(255),
    role_id           BIGINT       NOT NULL REFERENCES app_role(id),
    status            VARCHAR(20)  NOT NULL DEFAULT 'INVITED', -- INVITED, ACTIVE, BLOCKED
    invite_token_hash VARCHAR(64),
    invite_expires_at TIMESTAMPTZ,
    reset_token_hash  VARCHAR(64),
    reset_expires_at  TIMESTAMPTZ,
    phone             VARCHAR(32),
    position          VARCHAR(120),
    avatar_file_id    BIGINT       REFERENCES stored_file(id) ON DELETE SET NULL,
    sip_extension     VARCHAR(120),
    call_columns      VARCHAR(500),
    last_login_at     TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX idx_app_user_email ON app_user(email);
CREATE UNIQUE INDEX idx_app_user_username ON app_user(username);
CREATE INDEX idx_app_user_company ON app_user(company_id);
CREATE INDEX idx_app_user_role ON app_user(role_id);
CREATE INDEX idx_app_user_invite_token ON app_user(invite_token_hash);
CREATE INDEX idx_app_user_reset_token ON app_user(reset_token_hash);

-- ---------------------------------------------------------------------------
-- Machine access: a company's own systems calling this API without a person logging in.
-- An integration used to either run as somebody's account — inheriting whatever that
-- person may do, and dying when they leave — or not run at all.
-- ---------------------------------------------------------------------------

CREATE TABLE api_key (
    id            BIGSERIAL PRIMARY KEY,
    company_id    BIGINT       NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    name          VARCHAR(128) NOT NULL,
    -- The readable half of the key, shown in the console so a key can be recognised
    -- after it has been issued, and used to find the row before the hash is compared.
    key_prefix    VARCHAR(32)  NOT NULL UNIQUE,
    -- SHA-256 of the whole key. The key itself is shown once, at creation, and never
    -- stored: a leaked database must not hand anyone a working credential.
    key_hash      VARCHAR(64)  NOT NULL,
    created_by    BIGINT       REFERENCES app_user(id) ON DELETE SET NULL,
    last_used_at  TIMESTAMPTZ,
    expires_at    TIMESTAMPTZ,
    revoked_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_api_key_company ON api_key(company_id, created_at DESC);

-- Permission names this key was granted. What it may actually do is this list intersected
-- at authentication time with the hardcoded allowlist in ApiKeyScopes, so a key cannot
-- gain a right by having its rows edited.
CREATE TABLE api_key_scope (
    api_key_id BIGINT      NOT NULL REFERENCES api_key(id) ON DELETE CASCADE,
    permission VARCHAR(64) NOT NULL,
    PRIMARY KEY (api_key_id, permission)
);

-- ---------------------------------------------------------------------------
-- AI agent — WHO speaks, pointing at the scenario that says WHAT is said
-- ---------------------------------------------------------------------------

-- The shape every hosted platform converged on (Retell agent -> conversation flow,
-- Vapi campaign -> assistant, Bolna agent_config + agent_prompts):
--
--     campaign      -> ai_agent -> scenario     (who to call, when)
--     inbound_route -> ai_agent -> scenario     (which DID answers with which agent)
--
-- so a call resolves its voice, persona and model in one read whichever direction it came
-- from, and one scenario can be spoken by an Uzbek agent and a Russian one without being
-- copied.
CREATE TABLE ai_agent (
    id                     BIGSERIAL PRIMARY KEY,
    company_id             BIGINT       NOT NULL REFERENCES company(id),
    name                   VARCHAR(255) NOT NULL,
    description            VARCHAR(500),

    -- What is said. Three ways, in the order resolveScenario reads them: a shared
    -- scenario row, an embedded definition, or nothing but system_prompt (mode PROMPT),
    -- in which case the prompt itself is the whole script. All three are nullable
    -- because an agent needs exactly one of them, not all.
    scenario_id            BIGINT       REFERENCES scenario(id),
    scenario_mode          VARCHAR(32)  NOT NULL DEFAULT 'PROMPT', -- PROMPT, STRUCTURED_STEPS
    scenario_definition    JSONB,
    template_id            VARCHAR(50),           -- BUSINESS, SUPPORT, MEDICAL, BLANK
    first_message          TEXT,                  -- spoken verbatim; NULL lets the model open the call
    -- An uploaded WAV played as soon as the call is up, before the agent says anything —
    -- a recorded notice in a person's voice, or a jingle.
    pre_conversation_audio_file_id BIGINT     REFERENCES stored_file(id) ON DELETE SET NULL,
    system_prompt          TEXT,
    preemptive_generation  BOOLEAN      NOT NULL DEFAULT false,
    ivr_navigation_enabled BOOLEAN      NOT NULL DEFAULT true,
    use_rag                BOOLEAN      NOT NULL DEFAULT false,

    language               VARCHAR(10)  NOT NULL DEFAULT 'uz-UZ',
    persona                VARCHAR(30)  NOT NULL DEFAULT 'AI_ASSISTANT', -- AI_ASSISTANT, HUMAN_LIKE

    -- Which engines run the call. pipeline_mode decides which of the rest apply: an
    -- stt_provider on a REALTIME agent is a leftover, not a setting. NULL anywhere here
    -- means "the deployment's configured default" (config/speech.yml).
    pipeline_mode          VARCHAR(20)  NOT NULL DEFAULT 'CASCADE', -- CASCADE, REALTIME
    realtime_provider      VARCHAR(50),           -- gemini-live, openai-realtime, qwen-omni, moshi, pipecat
    pipecat_stt            VARCHAR(50),
    pipecat_llm            VARCHAR(50),
    pipecat_tts            VARCHAR(50),
    stt_provider           VARCHAR(64),           -- yandex, aisha, gemini, deepgram
    stt_model              VARCHAR(120),
    tts_provider           VARCHAR(64),           -- yandex, aisha, gemini, cartesia
    tts_model              VARCHAR(120),
    llm_model              VARCHAR(120),          -- NULL = the company's ai_model_config
    -- The cascade pipeline answers a very short caller turn on a cheaper model than the
    -- rest of the call. NULL keeps the installation default (dialog.fast-model); an agent
    -- that wants no downgrade at all sets this to the same id as llm_model.
    fast_llm_model         VARCHAR(120),
    temperature            DOUBLE PRECISION,      -- NULL = the company's
    max_output_tokens      INT,                   -- NULL = the company's
    -- How a REALTIME call is given its facts: written into the engine's instructions
    -- (PROMPT) or fetched one at a time with getCallFact (TOOL). NULL keeps the
    -- installation default (voice-agent.realtime.facts-in-prompt).
    realtime_facts_delivery VARCHAR(10),

    -- How it sounds.
    tts_voice              VARCHAR(64)  REFERENCES tts_voice(id),  -- NULL = the configured provider's own default voice
    voice_speed            DOUBLE PRECISION DEFAULT 1.0,           -- 0.25–3.0 (VoiceSettingsValidator)
    -- Language code -> speed as JSON, for an agent whose Russian voice reads faster than its
    -- Uzbek one at the same setting; NULL keeps voice_speed for every language.
    voice_speed_per_language JSONB,
    voice_stability        DOUBLE PRECISION DEFAULT 0.5,
    voice_similarity_boost DOUBLE PRECISION DEFAULT 0.75,
    emotion_adaptive_voice BOOLEAN      NOT NULL DEFAULT true,

    -- Ambience. The two volumes are multipliers on the level AmbientSoundGenerator
    -- already calibrated each soundscape to (~ -38 dBFS, about 20 dB under the bot's
    -- voice), so 1.0 is that tuned level, not "maximum".
    ambient_sound                 VARCHAR(30)      NOT NULL DEFAULT 'OFFICE', -- OFF, OFFICE, CALL_CENTER, NATURAL_LINE, CAFE, KEYBOARD_TYPING
    ambient_sound_volume          DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    ambient_sound_fade_in_seconds DOUBLE PRECISION NOT NULL DEFAULT 1.5,
    thinking_sound                VARCHAR(50)      NOT NULL DEFAULT 'OFF',
    thinking_sound_volume         DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    noise_cancellation_enabled    BOOLEAN          NOT NULL DEFAULT true,
    noise_cancellation_mode       VARCHAR(50)      NOT NULL DEFAULT 'BACKGROUND_NOISE_SUPPRESSION', -- OFF, BACKGROUND_NOISE_SUPPRESSION, VOICE_ISOLATION

    -- Call dynamics, human handoff, voicemail and mid-call SMS.
    dtmf_input_enabled       BOOLEAN     NOT NULL DEFAULT true,
    interruption_sensitivity VARCHAR(32) NOT NULL DEFAULT 'MEDIUM', -- LOW, MEDIUM, HIGH, OFF
    endpointing_delay_ms     INT         NOT NULL DEFAULT 700,
    transfer_phone_number    VARCHAR(32),          -- NULL = the platform's operator endpoint
    transfer_message         TEXT,
    -- Where this agent's handover to a human goes. Wins over transfer_phone_number. The
    -- foreign key is added below, once call_queue exists.
    transfer_queue_id        BIGINT,
    voicemail_action         VARCHAR(30) NOT NULL DEFAULT 'LEAVE_MESSAGE', -- HANGUP, LEAVE_MESSAGE, IGNORE
    voicemail_message        TEXT,
    mid_call_sms_enabled     BOOLEAN     NOT NULL DEFAULT false,
    mid_call_sms_template    TEXT,
    pronunciation_rules      JSONB       DEFAULT '[]'::jsonb,
    post_call_actions        JSONB       DEFAULT '[]'::jsonb,

    -- Post-call analysis: what to pull out of the conversation and how to grade it.
    data_needed            JSONB        DEFAULT '[]'::jsonb,
    data_evaluation        JSONB        DEFAULT '[]'::jsonb,

    -- Retention and privacy.
    zero_pii_retention          BOOLEAN NOT NULL DEFAULT false,
    store_call_audio            BOOLEAN NOT NULL DEFAULT true,
    conversation_retention_days INT     NOT NULL DEFAULT 30,

    -- Limits and timeouts. NULL means the platform's own limit applies instead.
    max_conversation_duration_seconds INT,
    silence_end_call_timeout_seconds  INT,
    turn_timeout_seconds              INT,
    concurrent_calls_limit            INT,
    daily_calls_limit                 INT,

    -- Webhooks: one asked before the call for facts and overrides, one told after it.
    initiation_webhook     JSONB,
    post_call_webhook      JSONB,

    enabled                BOOLEAN      NOT NULL DEFAULT true,
    -- Optimistic locking (JPA @Version): an edit that read an older value is answered 409
    -- ENTITY_VERSION_CONFLICT instead of overwriting what was saved since.
    version                BIGINT       NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by             BIGINT
);
CREATE INDEX idx_ai_agent_company ON ai_agent(company_id);
CREATE INDEX idx_ai_agent_scenario ON ai_agent(scenario_id);

-- Voice per call language, for an agent that answers more than one: a ru-RU caller is
-- spoken to by a Russian voice and an uz-UZ one by an Uzbek voice, from the same agent.
CREATE TABLE ai_agent_voice (
    ai_agent_id BIGINT      NOT NULL REFERENCES ai_agent(id) ON DELETE CASCADE,
    language    VARCHAR(10) NOT NULL,
    voice_id    VARCHAR(64) NOT NULL REFERENCES tts_voice(id),
    PRIMARY KEY (ai_agent_id, language)
);

-- Which trunks this agent may dial out from. Empty means the dialer balances across every
-- enabled trunk of the company: the line a customer sees a call arrive on belongs with the
-- agent placing it, not with the list of people being called.
CREATE TABLE ai_agent_sip_trunk (
    ai_agent_id  BIGINT NOT NULL REFERENCES ai_agent(id) ON DELETE CASCADE,
    sip_trunk_id BIGINT NOT NULL REFERENCES sip_trunk(id) ON DELETE CASCADE,
    PRIMARY KEY (ai_agent_id, sip_trunk_id)
);

-- An agent's edits do not reach running campaigns the moment they are saved. The ai_agent
-- row is the DRAFT every editor writes to; a call runs on the latest PUBLISHED snapshot from
-- this table, and an agent that was never published keeps running on its row. Publishing
-- copies the row here as version N and archives N-1; rollback re-applies an old snapshot to
-- the row and publishes it again as N+1, so the history is append-only and a version number
-- never means two different things.
CREATE TABLE ai_agent_version (
    id           BIGSERIAL PRIMARY KEY,
    agent_id     BIGINT      NOT NULL REFERENCES ai_agent (id) ON DELETE CASCADE,
    company_id   BIGINT      NOT NULL REFERENCES company (id) ON DELETE CASCADE,
    version      INT         NOT NULL,
    status       VARCHAR(10) NOT NULL,
    snapshot     JSONB       NOT NULL,
    note         VARCHAR(500),
    published_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_by BIGINT      REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT uq_ai_agent_version UNIQUE (agent_id, version)
);
-- Exactly one live version per agent.
CREATE UNIQUE INDEX idx_ai_agent_version_published
    ON ai_agent_version (agent_id) WHERE status = 'PUBLISHED';

-- An agent's own regression suite. A test case is a scripted caller (a persona prompt for a
-- second model) plus what the call must come to; an evaluation run plays every case against
-- the agent — draft or a published version — and keeps each transcript and verdict, so a
-- prompt change is checked before it is published.
CREATE TABLE agent_test_case (
    id                 BIGSERIAL PRIMARY KEY,
    company_id         BIGINT       NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    agent_id           BIGINT       NOT NULL REFERENCES ai_agent(id) ON DELETE CASCADE,
    name               VARCHAR(200) NOT NULL,
    persona_prompt     TEXT         NOT NULL,
    facts              JSONB        NOT NULL DEFAULT '{}',
    language           VARCHAR(10),
    expect_disposition VARCHAR(40),
    expect_stage       VARCHAR(100),
    must_say           JSONB        NOT NULL DEFAULT '[]',
    must_not_say       JSONB        NOT NULL DEFAULT '[]',
    max_turns          INT          NOT NULL DEFAULT 12,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_agent_test_case_agent ON agent_test_case (agent_id);

-- mode: TEXT, or VOICE — played as audio through the real call pipeline.
-- config_hash: fingerprint of the configuration evaluated, so publishing can tell whether
--   the draft changed after its last passing run.
CREATE TABLE agent_eval_run (
    id            BIGSERIAL PRIMARY KEY,
    company_id    BIGINT      NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    agent_id      BIGINT      NOT NULL REFERENCES ai_agent(id) ON DELETE CASCADE,
    agent_version INT,
    mode          VARCHAR(8)  NOT NULL DEFAULT 'TEXT',
    config_hash   VARCHAR(64),
    status        VARCHAR(16) NOT NULL,
    total         INT         NOT NULL DEFAULT 0,
    passed        INT         NOT NULL DEFAULT 0,
    avg_qa_score  INT,
    error         VARCHAR(500),
    started_at    TIMESTAMPTZ NOT NULL,
    finished_at   TIMESTAMPTZ
);
CREATE INDEX idx_agent_eval_run_agent ON agent_eval_run (agent_id, started_at DESC);

CREATE TABLE agent_eval_result (
    id                  BIGSERIAL PRIMARY KEY,
    run_id              BIGINT       NOT NULL REFERENCES agent_eval_run(id) ON DELETE CASCADE,
    test_case_id        BIGINT       REFERENCES agent_test_case(id) ON DELETE SET NULL,
    case_name           VARCHAR(200) NOT NULL,
    passed              BOOLEAN      NOT NULL,
    disposition         VARCHAR(40),
    stage               VARCHAR(100),
    problems            JSONB        NOT NULL DEFAULT '[]',
    transcript          TEXT         NOT NULL,
    qa_score            INT,
    guardrail_violation BOOLEAN      NOT NULL DEFAULT false,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_agent_eval_result_run ON agent_eval_result (run_id);

-- ---------------------------------------------------------------------------
-- Agent tool library, MCP servers and website widgets
-- ---------------------------------------------------------------------------

-- An HTTP endpoint the model may call mid-conversation, defined once per company and
-- bound to as many agents as want it. The four *_params columns are the OpenAPI-ish
-- description of the request; dynamic_variables names what the call may substitute in.
--
--   response_mapping   {"order_status": "$.data.status"}: the model gets these fields only,
--                      not the raw body; null keeps the raw (truncated) body
--   retry_attempts     extra tries after a 5xx or a network failure. 0, the default, never
--                      retries: a POST that half-succeeded must not run twice unasked
--   max_calls_per_call how often one conversation may call the tool; 0 means no limit
--   body_schema        a JSON Schema for a body with nested objects and arrays, next to the
--                      flat api_body
--   effect             what a call to the tool does: READ, WRITE, DESTRUCTIVE. The default
--                      is a net for rows written by plain SQL; the application always sets it
--   auth               an OAuth 2.0 client_credentials setup: the token endpoint and the names
--                      of the secrets holding the client id and secret, never their values.
--                      NULL: the headers and address carry whatever authentication it needs
CREATE TABLE tool (
    id                    BIGSERIAL PRIMARY KEY,
    company_id            BIGINT        NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    name                  VARCHAR(100)  NOT NULL,
    description           VARCHAR(500)  NOT NULL,
    api_url               VARCHAR(1000) NOT NULL,
    api_method            VARCHAR(10)   NOT NULL DEFAULT 'POST',
    api_headers           JSONB         DEFAULT '[]'::jsonb,
    api_body              JSONB         DEFAULT '[]'::jsonb,
    api_query_params      JSONB         DEFAULT '[]'::jsonb,
    api_path_params       JSONB         DEFAULT '[]'::jsonb,
    response_timeout_secs INT           DEFAULT 10,
    dynamic_variables     JSONB         DEFAULT '[]'::jsonb,
    disable_interruptions BOOLEAN       NOT NULL DEFAULT false,
    -- On: the agent says pre_tool_speech before the request goes out, so the caller is
    -- not left listening to silence while an API answers.
    force_pre_tool_speech BOOLEAN       NOT NULL DEFAULT true,
    pre_tool_speech       VARCHAR(500),
    response_mapping      JSONB,
    retry_attempts        INT           NOT NULL DEFAULT 0,
    max_calls_per_call    INT           NOT NULL DEFAULT 3,
    body_schema           JSONB,
    effect                VARCHAR(16)   NOT NULL DEFAULT 'WRITE',
    requires_confirmation BOOLEAN       NOT NULL DEFAULT false,
    readback_template     VARCHAR(500),
    auth                  JSONB,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_tool_effect CHECK (effect IN ('READ', 'WRITE', 'DESTRUCTIVE'))
);
CREATE INDEX idx_tool_company ON tool(company_id);

CREATE TABLE ai_agent_tool (
    ai_agent_id BIGINT      NOT NULL REFERENCES ai_agent(id) ON DELETE CASCADE,
    tool_id     BIGINT      NOT NULL REFERENCES tool(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (ai_agent_id, tool_id)
);
CREATE INDEX idx_ai_agent_tool_agent ON ai_agent_tool(ai_agent_id);
CREATE INDEX idx_ai_agent_tool_tool ON ai_agent_tool(tool_id);

-- MCP: tools a company hosts somewhere else, offered to its agent during a call. The `tool`
-- table covers "call this REST endpoint with these parameters", which a company has to
-- describe field by field; an MCP server describes its own tools, so a company that already
-- runs one gets them all by pasting a URL.
CREATE TABLE mcp_connection (
    id            BIGSERIAL    PRIMARY KEY,
    company_id    BIGINT       NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    name          VARCHAR(128) NOT NULL,
    -- HTTPS only, and screened against internal addresses before every call.
    url           VARCHAR(500) NOT NULL,
    -- NONE or BEARER. A bearer token is not stored here: secret_key names a row in the
    -- company's secret store, so the credential lives in one place with the rest.
    auth_type     VARCHAR(16)  NOT NULL DEFAULT 'NONE',
    secret_key    VARCHAR(128),
    -- PENDING -> CONNECTED, or AUTH_REQUIRED / ERROR / DISABLED.
    status        VARCHAR(24)  NOT NULL DEFAULT 'PENDING',
    -- How many tools the last refresh found, and how many of those the model is allowed
    -- to see. The second number is the one worth looking at: a server offering forty
    -- tools of which two are usable is a server that needs allow_writes turning on.
    tool_count    INT          NOT NULL DEFAULT 0,
    usable_tool_count INT      NOT NULL DEFAULT 0,
    -- Off by default: a tool the server does not mark read-only can change something at
    -- the other end, and a model on a live call is talkable-into things.
    allow_writes  BOOLEAN      NOT NULL DEFAULT FALSE,
    -- The tool list as the last refresh read it, so a call builds its tools from a row
    -- instead of waiting on somebody else's server between the caller answering and the
    -- agent speaking.
    tools_json    JSONB,
    last_error    VARCHAR(500),
    refreshed_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_mcp_connection_name UNIQUE (company_id, name)
);
CREATE INDEX idx_mcp_connection_company ON mcp_connection(company_id);

-- Which agents may use which connection. Not every agent should reach every tool: a
-- collections agent and a support agent share a company, not a toolbox.
CREATE TABLE ai_agent_mcp_connection (
    ai_agent_id       BIGINT NOT NULL REFERENCES ai_agent(id) ON DELETE CASCADE,
    mcp_connection_id BIGINT NOT NULL REFERENCES mcp_connection(id) ON DELETE CASCADE,
    PRIMARY KEY (ai_agent_id, mcp_connection_id)
);

-- A browser-side entry point to one agent: the widget_key is public, so allowed_origins
-- is what stops a copied key from being embedded on somebody else's page.
CREATE TABLE agent_widget (
    id               BIGSERIAL PRIMARY KEY,
    widget_key       VARCHAR(64)  NOT NULL UNIQUE,
    company_id       BIGINT       NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    agent_id         BIGINT       NOT NULL REFERENCES ai_agent(id) ON DELETE CASCADE,
    name             VARCHAR(100) NOT NULL,
    enabled          BOOLEAN      NOT NULL DEFAULT true,
    allowed_origins  JSONB        DEFAULT '[]'::jsonb,
    theme            JSONB        DEFAULT '{}'::jsonb,
    consent_required BOOLEAN      NOT NULL DEFAULT true,
    consent_text     VARCHAR(500),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_agent_widget_company ON agent_widget(company_id);
CREATE INDEX idx_agent_widget_agent ON agent_widget(agent_id);
CREATE INDEX idx_agent_widget_key ON agent_widget(widget_key);

-- The second text channel: a company's own Telegram bot answers with one of its agents — the
-- same agent, scenario and tools the widget chat and the phone use.
CREATE TABLE telegram_bot (
    id               BIGSERIAL    PRIMARY KEY,
    company_id       BIGINT       NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    ai_agent_id      BIGINT       NOT NULL REFERENCES ai_agent(id) ON DELETE CASCADE,
    name             VARCHAR(120) NOT NULL,
    -- The bot token is never stored here: this names the company's own secret, which is where
    -- encrypted values live (the same way a tool names {{secrets.KEY}}).
    token_secret_key VARCHAR(64)  NOT NULL,
    -- Ours, not Telegram's: it goes to setWebhook and comes back on every update in the
    -- X-Telegram-Bot-Api-Secret-Token header. Without it the webhook address is the only
    -- thing between a stranger and the company's agent.
    webhook_secret   VARCHAR(64)  NOT NULL,
    language         VARCHAR(10),
    -- Where Telegram was told to send updates, once the panel has registered it. Shown back
    -- to the operator: a bot pointed at an address nobody remembers is a bot nobody can move.
    webhook_url      VARCHAR(500),
    enabled          BOOLEAN      NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_telegram_bot_company ON telegram_bot(company_id);

-- The same agent over text. A chat is a conversation with the agent that has no audio in it,
-- so what a call keeps in memory for its lifetime is kept here instead: the transcript and the
-- stage the conversation is in. Every message rebuilds the session from these rows, which is
-- what lets the next message land on another instance.
CREATE TABLE chat_session (
    id               BIGSERIAL    PRIMARY KEY,
    -- What the browser holds and sends back. Not the id: a guessable handle to somebody
    -- else's conversation is somebody else's conversation.
    session_key      VARCHAR(64)  NOT NULL UNIQUE,
    company_id       BIGINT       NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    ai_agent_id      BIGINT       NOT NULL REFERENCES ai_agent(id) ON DELETE CASCADE,
    widget_id        BIGINT       REFERENCES agent_widget(id) ON DELETE SET NULL,
    -- A Telegram conversation is a chat_session like any other; these two say which one.
    telegram_bot_id  BIGINT       REFERENCES telegram_bot(id) ON DELETE CASCADE,
    telegram_chat_id VARCHAR(64),
    language         VARCHAR(10)  NOT NULL,
    -- Where the scenario's state machine stands, and what its tools have recorded so far.
    stage            VARCHAR(100) NOT NULL,
    outcome          JSONB        NOT NULL DEFAULT '{}',
    disposition      VARCHAR(50),
    ended            BOOLEAN      NOT NULL DEFAULT false,
    message_count    INT          NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_message_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_chat_session_company ON chat_session(company_id, created_at DESC);
CREATE INDEX idx_chat_session_agent ON chat_session(ai_agent_id, created_at DESC);
-- One open conversation per Telegram chat, enforced here rather than hoped for in code: the
-- person writes again and continues where they left off, and two updates arriving together
-- cannot open two conversations for the same chat.
CREATE UNIQUE INDEX uq_chat_session_telegram_open ON chat_session(telegram_bot_id, telegram_chat_id)
    WHERE telegram_chat_id IS NOT NULL AND ended = false;

CREATE TABLE chat_message (
    id              BIGSERIAL   PRIMARY KEY,
    chat_session_id BIGINT      NOT NULL REFERENCES chat_session(id) ON DELETE CASCADE,
    role            VARCHAR(10) NOT NULL,
    text            TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_chat_message_role CHECK (role IN ('CLIENT', 'AGENT'))
);
-- Read back in order, always for one session: the id is the order.
CREATE INDEX idx_chat_message_session ON chat_message(chat_session_id, id);

-- ---------------------------------------------------------------------------
-- Campaigns — who to call and when
-- ---------------------------------------------------------------------------

-- priority: campaigns compete for the dialer's batch by it (1 low … 5 urgent).
-- retry_policy: per-disposition override of the retry delay / attempt cap instead of one
--   retry_interval_minutes for everything:
--   {"NO_ANSWER": {"maxAttempts": 3, "delayMinutes": 180}, "VOICEMAIL": {"maxAttempts": 1, "delayMinutes": 120}}
-- start_at: in the future it parks the campaign as SCHEDULED, and the recurrence sweep flips
--   it to ACTIVE when the moment comes; end_at closes an ACTIVE campaign as COMPLETED
--   whether or not every target was reached.
-- budget_uzs / daily_budget_uzs: a spend ceiling. When settled calls reach it (for the whole
--   run, or since the company's midnight) the dialer parks the campaign as PAUSED with a
--   BUDGET_CAP notice instead of dialling on.
-- dial_mode: AI, PREVIEW, PROGRESSIVE, PREDICTIVE — fixed once the campaign exists. A
--   campaign is dialled either by its AI agent or for operators of a queue.
-- ai_agent_id: required only for AI (an operator campaign may still name one, for the AI
--   overflow of PREDICTIVE).
-- call_queue_id: the queue whose members take a non-AI campaign's calls. A deleted queue
--   leaves it null, and the operator dialer skips such a campaign instead of failing the
--   delete. The foreign key is added below, once call_queue exists.
-- max_ai_overflow_percent: how much of a PREDICTIVE campaign's answered calls may go to the
--   AI before the dialer slows to one call per free operator. 0 dials no faster than
--   PROGRESSIVE.
-- version: optimistic locking (JPA @Version). Only edits bump it. The dialer's own
--   statements (status, last_run_at) are targeted UPDATEs that neither check nor bump it: an
--   edit never writes those columns (@DynamicUpdate), and a dialer tick must never fail
--   because someone had the form open.
CREATE TABLE campaign (
    id                     BIGSERIAL PRIMARY KEY,
    company_id             BIGINT       NOT NULL REFERENCES company(id),
    ai_agent_id            BIGINT       REFERENCES ai_agent(id),
    dial_mode              VARCHAR(20)  NOT NULL DEFAULT 'AI',
    call_queue_id          BIGINT,
    max_ai_overflow_percent INT         NOT NULL DEFAULT 20
        CHECK (max_ai_overflow_percent BETWEEN 0 AND 100),
    name                   VARCHAR(255) NOT NULL,
    type                   VARCHAR(50)  NOT NULL,   -- DEBT_COLLECTION, SURVEY
    status                 VARCHAR(50)  NOT NULL,   -- DRAFT, SCHEDULED, ACTIVE, PAUSED, COMPLETED, ARCHIVED
    script_config          JSONB        NOT NULL,
    dial_window_start      TIME         NOT NULL DEFAULT '07:00',
    dial_window_end        TIME         NOT NULL DEFAULT '23:00',
    max_attempts           INT          NOT NULL DEFAULT 3,
    retry_interval_minutes INT          NOT NULL DEFAULT 0,
    max_concurrent_calls   INT          NOT NULL DEFAULT 20,
    daily_call_cap         INT          NOT NULL DEFAULT 0,
    recurrence_type        VARCHAR(20)  NOT NULL DEFAULT 'ONCE', -- ONCE, DAILY, WEEKLY, MONTHLY, CRON
    recurring_day_of_month INTEGER,
    cron_expression        VARCHAR(100),
    auto_reset_targets     BOOLEAN      NOT NULL DEFAULT false,
    last_run_at            TIMESTAMPTZ,
    priority               INT          NOT NULL DEFAULT 3,
    retry_policy           JSONB,
    start_at               TIMESTAMPTZ,
    end_at                 TIMESTAMPTZ,
    budget_uzs             BIGINT,
    daily_budget_uzs       BIGINT,
    version                BIGINT       NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by             BIGINT,
    -- Native SQL in the report and dialer code compares this column with string literals,
    -- so a value outside CampaignStatus would silently drop out of every count. A new enum
    -- constant needs a migration that widens the constraint, or its first write fails.
    CONSTRAINT ck_campaign_status
        CHECK (status IN ('DRAFT', 'SCHEDULED', 'ACTIVE', 'PAUSED', 'COMPLETED', 'ARCHIVED')),
    CONSTRAINT ck_campaign_dial_mode
        CHECK (dial_mode IN ('AI', 'PREVIEW', 'PROGRESSIVE', 'PREDICTIVE')),
    CONSTRAINT ck_campaign_ai_agent
        CHECK (dial_mode <> 'AI' OR ai_agent_id IS NOT NULL)
);
CREATE INDEX idx_campaign_company ON campaign(company_id);
CREATE INDEX idx_campaign_ai_agent ON campaign(ai_agent_id);

-- Campaign allowed dial days (e.g., MONDAY, TUESDAY, etc.)
CREATE TABLE campaign_dial_day (
    campaign_id BIGINT      NOT NULL REFERENCES campaign(id) ON DELETE CASCADE,
    day         VARCHAR(20) NOT NULL,
    PRIMARY KEY (campaign_id, day)
);

-- A/B testing: two agents (or one agent and a prompt override) split a campaign's traffic.
-- pipeline_mode lets a variant run its calls on a different pipeline than the campaign's
-- agent — the same agent, script and voice budget, once through the cascade pipeline and
-- once through a speech-to-speech engine: the only fair comparison of the two. NULL keeps
-- the agent's own pipeline.
CREATE TABLE campaign_variant (
    id              BIGSERIAL PRIMARY KEY,
    campaign_id     BIGINT       NOT NULL REFERENCES campaign(id) ON DELETE CASCADE,
    company_id      BIGINT       NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL,
    ai_agent_id     BIGINT       REFERENCES ai_agent(id),
    prompt_override TEXT,
    tts_voice_id    VARCHAR(64)  REFERENCES tts_voice(id),
    pipeline_mode   VARCHAR(16),
    traffic_weight  INT          NOT NULL DEFAULT 50,
    calls_count     INT          NOT NULL DEFAULT 0,
    answered_count  INT          NOT NULL DEFAULT 0,
    converted_count INT          NOT NULL DEFAULT 0,
    is_active       BOOLEAN      NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_variant_campaign ON campaign_variant(campaign_id);
CREATE INDEX idx_variant_company ON campaign_variant(company_id);

-- Campaign target (client) — individual row in a campaign dial queue.
--
-- claimed_by_user_id: a PREVIEW campaign hands its next target to one operator, who looks at
--   the card and then dials or skips it. It says whose card an IN_PROGRESS target is and is
--   read only together with that status, so a stale value on a settled target means nothing.
--   The claim's lease stays next_attempt_at, as for the AI dialer, only longer
--   (CampaignTarget.OPERATOR_CLAIM_LEASE): the operator talks and wraps up before it runs out.
CREATE TABLE campaign_target (
    id              BIGSERIAL PRIMARY KEY,
    company_id      BIGINT       NOT NULL REFERENCES company(id),
    campaign_id     BIGINT       NOT NULL REFERENCES campaign(id) ON DELETE CASCADE,
    variant_id      BIGINT       REFERENCES campaign_variant(id) ON DELETE SET NULL,
    client_id       BIGINT       NOT NULL,
    phone           VARCHAR(20)  NOT NULL,
    language        VARCHAR(10),
    context_data    JSONB        NOT NULL,
    status          VARCHAR(50)  NOT NULL,   -- PENDING, IN_PROGRESS, DONE, FAILED, EXHAUSTED
    attempts        INT          NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    do_not_call     BOOLEAN      NOT NULL DEFAULT false,
    claimed_by_user_id BIGINT    REFERENCES app_user(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_campaign_target_status
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'DONE', 'FAILED', 'EXHAUSTED'))
);
CREATE INDEX idx_target_dial ON campaign_target(campaign_id, status, next_attempt_at);
CREATE INDEX idx_campaign_target_company ON campaign_target(company_id);
CREATE INDEX idx_campaign_target_variant ON campaign_target(variant_id);
CREATE INDEX idx_campaign_target_operator_claim ON campaign_target(campaign_id, claimed_by_user_id)
    WHERE status = 'IN_PROGRESS';
-- One queued row per number per campaign. The same CSV uploaded twice, or a target source
-- synced without replaceTargets, used to insert every number again and dial every person
-- twice. Partial: finished rows (DONE / EXHAUSTED / FAILED) do not count, so a callback or
-- an instant call to a number the campaign already finished with is still a new target.
CREATE UNIQUE INDEX idx_campaign_target_queued_phone
    ON campaign_target (campaign_id, phone)
    WHERE status IN ('PENDING', 'IN_PROGRESS');

-- Where a campaign's call list comes from, when it does not come from a CSV somebody
-- uploads by hand. A DAILY campaign with a source configured fetches the list on each
-- recurrence sweep and dials whatever it answers with. One source per campaign, so the
-- shape stays a settings page and not a pipeline builder.
--
-- provider picks the kind of list:
--   GENERIC        one request to the company's own API, whose answer is already an array
--                  of rows carrying a phone each (TargetApiImporter). Requires url.
--   UYSOT_DEBTORS  Uysot's Open API has no endpoint that answers "today's debtors with
--                  their phone numbers" — contract/filter knows who is overdue, contract/{id}
--                  adds the lead, lead/{id} has the phones — so it is a chain of three reads
--                  compiled into the platform. No url of its own: it reads voice-agent.crm.*,
--                  so prod and dev switch hosts in one place.
--   CHAINED        that same shape as configuration: steps holds an ordered array of
--                  requests, the first lists rows, each later one is called per row to add
--                  what the list did not carry. NULL for every other provider.
CREATE TABLE campaign_target_source (
    campaign_id        BIGINT PRIMARY KEY REFERENCES campaign(id) ON DELETE CASCADE,
    provider           VARCHAR(24)   NOT NULL DEFAULT 'GENERIC', -- GENERIC, UYSOT_DEBTORS, CHAINED
    url                VARCHAR(1000),
    http_method        VARCHAR(10)   NOT NULL DEFAULT 'GET',   -- GET, POST
    request_body       TEXT,                                   -- POST only, sent as-is
    auth_header_name   VARCHAR(100),
    auth_header_value  TEXT,                                   -- AES-GCM (SecretCipher)
    items_path         VARCHAR(200),                           -- dot path to the array; NULL = the body is the array
    phone_field        VARCHAR(100)  NOT NULL DEFAULT 'phone',
    client_id_field    VARCHAR(100),
    language_field     VARCHAR(100),
    replace_targets    BOOLEAN       NOT NULL DEFAULT false,   -- clear the list before importing
    sync_on_recurrence BOOLEAN       NOT NULL DEFAULT true,
    enabled            BOOLEAN       NOT NULL DEFAULT true,
    steps              JSONB,
    last_sync_at       TIMESTAMPTZ,
    last_sync_added    INT,
    last_sync_error    VARCHAR(1000),
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Business hours as data (docs/INBOUND-PBX-PLAN.md §5). A schedule is a company's weekly
-- slots, reused by any number of routes, call flows and queues; a holiday is a dated
-- exception to all of them.
--
-- business_schedule.timezone NULL means the company's own (company_config.timezone).
-- business_schedule_slot: end_time < start_time is an overnight slot (22:00-06:00).
-- holiday.kind: CLOSED (whole day), SHORT_DAY / WORKING_DAY (open within open_time-close_time).
-- Uzbekistan moves days off by decree, so a worked Saturday is a WORKING_DAY row.
-- ---------------------------------------------------------------------------

CREATE TABLE business_schedule (
    id         BIGSERIAL PRIMARY KEY,
    company_id BIGINT       NOT NULL REFERENCES company(id),
    name       VARCHAR(255) NOT NULL,
    timezone   VARCHAR(64),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_business_schedule_company ON business_schedule(company_id);

CREATE TABLE business_schedule_slot (
    schedule_id BIGINT      NOT NULL REFERENCES business_schedule(id) ON DELETE CASCADE,
    ord         INT         NOT NULL,
    day_of_week VARCHAR(10) NOT NULL,
    start_time  TIME        NOT NULL,
    end_time    TIME        NOT NULL,
    PRIMARY KEY (schedule_id, ord)
);

CREATE TABLE holiday (
    id           BIGSERIAL PRIMARY KEY,
    company_id   BIGINT       NOT NULL REFERENCES company(id),
    holiday_date DATE         NOT NULL,
    name         VARCHAR(255) NOT NULL,
    kind         VARCHAR(20)  NOT NULL DEFAULT 'CLOSED',
    open_time    TIME,
    close_time   TIME,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (company_id, holiday_date)
);

-- ---------------------------------------------------------------------------
-- Human operators as telephony endpoints, and the queue that feeds them
-- (docs/INBOUND-PBX-PLAN.md §7).
-- ---------------------------------------------------------------------------

-- The PJSIP endpoint an operator's softphone signs in as. One per user; endpoint_name
-- (op-<userId>) is what Asterisk sees, app_user.sip_extension what colleagues dial.
-- webrtc=true is the browser softphone in the operator panel; false is a desk or mobile SIP
-- phone on the LAN transport. forward_number rings the operator's mobile over the trunk
-- when the softphone is offline.
CREATE TABLE operator_sip_account (
    user_id        BIGINT PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    company_id     BIGINT      NOT NULL REFERENCES company(id),
    endpoint_name  VARCHAR(64) NOT NULL UNIQUE,
    password_enc   TEXT        NOT NULL,
    webrtc         BOOLEAN     NOT NULL DEFAULT true,
    forward_number VARCHAR(20),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_operator_sip_account_company ON operator_sip_account(company_id);

-- Every presence change (AVAILABLE, RINGING, ON_CALL, WRAP_UP, BREAK, OFFLINE). The live
-- state is held in memory; this is the history "time in status" reports are built from,
-- and what a restart restores AVAILABLE/BREAK from. ended_at NULL = the current status.
CREATE TABLE operator_status_log (
    id         BIGSERIAL PRIMARY KEY,
    company_id BIGINT      NOT NULL REFERENCES company(id),
    user_id    BIGINT      NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    status     VARCHAR(20) NOT NULL,
    reason     VARCHAR(255),
    started_at TIMESTAMPTZ NOT NULL,
    ended_at   TIMESTAMPTZ
);
CREATE INDEX idx_operator_status_log_user ON operator_status_log(user_id, started_at);
CREATE INDEX idx_operator_status_log_company ON operator_status_log(company_id, started_at);

-- An operator queue (ACD). Waiting callers hear hold music and their position; operators
-- are offered calls by `strategy`. overflow_target is read by overflow_action:
--   QUEUE -> call_queue id, AI_AGENT -> ai_agent id, VOICEMAIL -> voicemail_box id,
--   EXTERNAL_NUMBER -> E.164 number, CALLBACK / HANGUP -> none.
-- 0 switches a timer off (announcements, callback offer, sticky routing).
CREATE TABLE call_queue (
    id                          BIGSERIAL PRIMARY KEY,
    company_id                  BIGINT       NOT NULL REFERENCES company(id),
    name                        VARCHAR(255) NOT NULL,
    strategy                    VARCHAR(20)  NOT NULL DEFAULT 'LEAST_RECENT',
    ring_timeout_sec            INT          NOT NULL DEFAULT 15,
    wrap_up_sec                 INT          NOT NULL DEFAULT 10,
    max_wait_sec                INT          NOT NULL DEFAULT 300,
    max_length                  INT          NOT NULL DEFAULT 0,
    announce_position_every_sec INT          NOT NULL DEFAULT 60,
    announce_wait_time          BOOLEAN      NOT NULL DEFAULT true,
    periodic_announce_text      TEXT,
    periodic_announce_every_sec INT          NOT NULL DEFAULT 0,
    hold_music_file_id          BIGINT REFERENCES stored_file(id) ON DELETE SET NULL,
    callback_offer_after_sec    INT          NOT NULL DEFAULT 0,
    callback_campaign_id        BIGINT REFERENCES campaign(id) ON DELETE SET NULL,
    overflow_action             VARCHAR(20)  NOT NULL DEFAULT 'CALLBACK',
    overflow_target             VARCHAR(64),
    service_level_sec           INT          NOT NULL DEFAULT 20,
    sticky_seconds              INT          NOT NULL DEFAULT 0,
    post_call_survey            BOOLEAN      NOT NULL DEFAULT false,
    waiting_agent_id            BIGINT REFERENCES ai_agent(id) ON DELETE SET NULL,
    auto_callback               BOOLEAN      NOT NULL DEFAULT false,
    enabled                     BOOLEAN      NOT NULL DEFAULT true,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_call_queue_company ON call_queue(company_id);
CREATE INDEX idx_call_queue_callback_campaign ON call_queue(callback_campaign_id);

-- skills: comma-separated tags (uz-UZ,ru-RU,sales); SKILL_BASED prefers a member holding the
-- caller's language. priority: lower rings first.
CREATE TABLE call_queue_member (
    queue_id BIGINT NOT NULL REFERENCES call_queue(id) ON DELETE CASCADE,
    user_id  BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    priority INT    NOT NULL DEFAULT 0,
    skills   VARCHAR(255),
    PRIMARY KEY (queue_id, user_id)
);

ALTER TABLE ai_agent ADD FOREIGN KEY (transfer_queue_id) REFERENCES call_queue(id) ON DELETE SET NULL;
ALTER TABLE campaign ADD FOREIGN KEY (call_queue_id) REFERENCES call_queue(id) ON DELETE SET NULL;

-- ---------------------------------------------------------------------------
-- Call flows and inbound routing (docs/INBOUND-PBX-PLAN.md §4, §9)
-- ---------------------------------------------------------------------------

-- A call flow is a graph of nodes (PLAY, SCHEDULE, MENU, LANGUAGE_SELECT, AI_AGENT, QUEUE, ...)
-- an inbound call walks. Edits go to draft_graph; calls only ever run published_graph, which
-- publishing copies from the draft after validation — so a half-finished edit never reaches
-- a caller. version counts publications.
CREATE TABLE call_flow (
    id              BIGSERIAL PRIMARY KEY,
    company_id      BIGINT       NOT NULL REFERENCES company(id),
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    draft_graph     JSONB        NOT NULL,
    published_graph JSONB,
    version         INT          NOT NULL DEFAULT 0,
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_call_flow_company ON call_flow(company_id);

-- Maps incoming phone numbers (DIDs) to an AI agent, IVR, or operator queue.
--
-- callback_campaign_id: where an inbound caller who asks to be called back goes — the caller
--   becomes a target there, due at the time they asked for. NULL records the request on the
--   call and nothing more.
-- schedule_id: a route may point at a schedule instead of its own daily window; the schedule wins.
-- call_flow_id: a route with a call flow runs the flow; without one it answers with its AI agent.
CREATE TABLE inbound_route (
    id                       BIGSERIAL PRIMARY KEY,
    company_id               BIGINT       NOT NULL REFERENCES company(id),
    did_number               VARCHAR(32)  NOT NULL,
    ai_agent_id              BIGINT       REFERENCES ai_agent(id),
    route_type               VARCHAR(32)  NOT NULL DEFAULT 'SCENARIO', -- SCENARIO, OPERATOR_QUEUE, EXTENSION, EXTERNAL_NUMBER, STICKY_AGENT, IVR_MENU, VOICEMAIL
    target_destination       VARCHAR(128),
    queue_strategy           VARCHAR(32)  NOT NULL DEFAULT 'RING_ALL',
    ring_timeout_sec         INT          NOT NULL DEFAULT 20,
    failover_action          VARCHAR(32)  NOT NULL DEFAULT 'SCENARIO',
    failover_destination     VARCHAR(128),
    after_hours_action       VARCHAR(32)  NOT NULL DEFAULT 'PLAY_MESSAGE_AND_HANGUP',
    after_hours_destination  VARCHAR(128),
    ivr_menu_config          TEXT,
    business_hours_start     TIME,
    business_hours_end       TIME,
    fallback_message         TEXT,
    callback_campaign_id     BIGINT       REFERENCES campaign(id) ON DELETE SET NULL,
    schedule_id              BIGINT       REFERENCES business_schedule(id) ON DELETE SET NULL,
    call_flow_id             BIGINT       REFERENCES call_flow(id) ON DELETE SET NULL,
    enabled                  BOOLEAN      NOT NULL DEFAULT true,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX idx_inbound_route_did ON inbound_route(did_number) WHERE enabled;
CREATE INDEX idx_inbound_route_company ON inbound_route(company_id);
CREATE INDEX idx_inbound_route_ai_agent ON inbound_route(ai_agent_id);
CREATE INDEX idx_inbound_route_callback_campaign ON inbound_route(callback_campaign_id);

-- Who is refused or put first before any flow runs. Matched on the longest digit prefix of the
-- caller's number. action: BLOCK_HANGUP, BLOCK_BUSY, BLOCK_MESSAGE (speaks `message`), VIP.
-- Separate from do_not_call_list on purpose: that is outbound consent, this is spam and abuse.
CREATE TABLE inbound_caller_rule (
    id           BIGSERIAL PRIMARY KEY,
    company_id   BIGINT       NOT NULL REFERENCES company(id),
    phone_prefix VARCHAR(20)  NOT NULL,
    action       VARCHAR(20)  NOT NULL,
    message      TEXT,
    note         VARCHAR(255),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (company_id, phone_prefix)
);

-- ---------------------------------------------------------------------------
-- Calls
-- ---------------------------------------------------------------------------

-- Call attempt (PROJECT.md §7) — tracks Asterisk channel lifecycle and telephony disposition.
--
-- target_id is NULL for a call no campaign placed — manual, test, widget, operator and
-- inbound calls.
-- queue_id … survey_score: the queue facts reports aggregate over, kept on the call itself
-- so SLA / ASA / abandon rate are one scan of call_attempt rather than a reconstruction from
-- call_event rows.
-- ai_agent_id / agent_version: which agent, and which published version of it, ran the
-- call — so a version's results can be read next to the one before it. NULL for a call no
-- agent spoke on; agent_version NULL for a call that ran on a never-published draft.
-- summary_lease_until: the summary retry claims a call instead of only reading it — the claim
-- counts the attempt and hides the call from every instance's sweep until this time, so two
-- instances never summarize the same call at once. NULL: never claimed.
-- ai_overflow: a PREDICTIVE call the AI agent took because no operator did.
CREATE TABLE call_attempt (
    id                   BIGSERIAL PRIMARY KEY,
    company_id           BIGINT       NOT NULL REFERENCES company(id),
    target_id            BIGINT       REFERENCES campaign_target(id),
    variant_id           BIGINT       REFERENCES campaign_variant(id) ON DELETE SET NULL,
    phone                VARCHAR(20),
    sip_call_id          VARCHAR(255),
    asterisk_channel     VARCHAR(255),
    language             VARCHAR(10)  NOT NULL,
    started_at           TIMESTAMPTZ,
    answered_at          TIMESTAMPTZ,
    ended_at             TIMESTAMPTZ,
    duration_sec         INT,
    disposition          VARCHAR(50),
    hangup_cause         VARCHAR(50),
    recording_file_id    BIGINT       REFERENCES stored_file(id) ON DELETE SET NULL,
    error_message        TEXT,
    finalize_attempts    INT          NOT NULL DEFAULT 0,
    inbound_route_id     BIGINT       REFERENCES inbound_route(id),
    operator_user_id     BIGINT       REFERENCES app_user(id),
    queue_id             BIGINT       REFERENCES call_queue(id) ON DELETE SET NULL,
    enqueued_at          TIMESTAMPTZ,
    operator_answered_at TIMESTAMPTZ,
    wait_seconds         INT,
    abandoned            BOOLEAN      NOT NULL DEFAULT false,
    survey_score         SMALLINT,
    ai_agent_id          BIGINT,
    agent_version        INT,
    summary_lease_until  TIMESTAMPTZ,
    ai_overflow          BOOLEAN      NOT NULL DEFAULT false,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Mirrors shared.dialog.Disposition; NULL until the call is settled. GOAL_REACHED /
    -- GOAL_NOT_REACHED are the scenario-neutral outcomes of a scenario that marks one outcome
    -- field as its goal. PAYMENT_CLAIMED: the debtor says the debt is already paid; the
    -- target is not called again for three days while the company checks.
    CONSTRAINT ck_call_attempt_disposition
        CHECK (disposition IS NULL OR disposition IN (
            'PROMISE_TO_PAY', 'REFUSED', 'NO_ANSWER', 'CARRIER_REJECTED', 'WRONG_NUMBER', 'DO_NOT_CALL',
            'HUNG_UP', 'TRANSFERRED', 'VOICEMAIL', 'CALLBACK_REQUESTED', 'FAILED', 'COMPLETED',
            'GOAL_REACHED', 'GOAL_NOT_REACHED', 'PAYMENT_CLAIMED'))
);
CREATE INDEX idx_attempt_target ON call_attempt(target_id);
-- Also serves the summary retry, which reads only calls that ended in the last day
-- (CallAttemptJpaRepository#attemptsAwaitingSummary).
CREATE INDEX idx_attempt_ended ON call_attempt(ended_at) WHERE ended_at IS NOT NULL;
CREATE INDEX idx_call_attempt_company ON call_attempt(company_id);
CREATE INDEX idx_call_attempt_inbound_route ON call_attempt(inbound_route_id);
CREATE INDEX idx_call_attempt_queue ON call_attempt(queue_id, enqueued_at) WHERE queue_id IS NOT NULL;
CREATE INDEX idx_call_attempt_agent_version ON call_attempt (ai_agent_id, agent_version)
    WHERE ai_agent_id IS NOT NULL;
-- recordHangupCause / finishUnanswered / findIdsByChannel look a row up by channel on
-- every ARI channel event.
CREATE INDEX idx_call_attempt_channel ON call_attempt(asterisk_channel);
-- Conversion attribution looks up a company's answered calls to one number.
CREATE INDEX idx_call_attempt_company_phone_answered
    ON call_attempt(company_id, phone, answered_at)
    WHERE answered_at IS NOT NULL;
-- findRecentPlacedStartTimes (every outbound dial, contact frequency policy) and
-- findLastOperatorUserId (every inbound call); the partial index above cannot serve either.
CREATE INDEX idx_call_attempt_company_phone_started
    ON call_attempt(company_id, phone, started_at DESC);
-- Every dashboard query filters one company over a started_at period.
CREATE INDEX idx_call_attempt_company_started ON call_attempt(company_id, started_at);
-- dashboardLiveCalls: the calls of a company that are still on the line.
CREATE INDEX idx_call_attempt_company_live
    ON call_attempt(company_id, started_at) WHERE ended_at IS NULL;
-- operatorReport joins on it; also backs the FK to app_user.
CREATE INDEX idx_call_attempt_operator_user ON call_attempt(operator_user_id);
CREATE INDEX idx_call_attempt_variant ON call_attempt(variant_id);
CREATE INDEX idx_call_attempt_recording_file ON call_attempt(recording_file_id);

-- Call technical details (latencies, token consumption, provider info) and inbound audio
-- quality. rtp_mos is an estimate (MosEstimator, simplified ITU-T G.107), NULL when the call
-- received no RTP at all — a silent media path is a fault, not a quality score.
CREATE TABLE call_technical (
    call_id              BIGINT PRIMARY KEY REFERENCES call_attempt(id) ON DELETE CASCADE,
    channel_name         VARCHAR(255),
    trunk                VARCHAR(100),
    amd_result           VARCHAR(20),
    stt_provider         VARCHAR(50),
    tts_provider         VARCHAR(50),
    tts_voice            VARCHAR(50),
    llm_model            VARCHAR(100),
    prompt_tokens        INT,
    completion_tokens    INT,
    cached_tokens        INT,
    turn_count           INT,
    avg_turn_latency_ms  INT,
    max_turn_latency_ms  INT,
    avg_llm_latency_ms   INT,
    max_llm_latency_ms   INT,
    rtp_packets_received INT,
    rtp_packets_lost     INT,
    rtp_jitter_ms        INT,
    rtp_mos              DOUBLE PRECISION,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Call transcript — speech turns recorded turn-by-turn.
CREATE TABLE call_transcript (
    id             BIGSERIAL PRIMARY KEY,
    call_id        BIGINT       NOT NULL REFERENCES call_attempt(id) ON DELETE CASCADE,
    seq            INT          NOT NULL,
    role           VARCHAR(20)  NOT NULL,   -- AGENT, CLIENT
    text           TEXT         NOT NULL,
    dialog_state   VARCHAR(50),
    ts_offset_ms   INT          NOT NULL,
    stt_confidence REAL
);
CREATE INDEX idx_transcript_call ON call_transcript(call_id, seq);

-- Call result (PROJECT.md §7.3) — AI summary, extracted facts, sentiment, and CRM status.
--
-- crm_note_id is the CRM's own reference for the posted note, in whatever shape that CRM
-- issues it — Uysot's Open API answers a note write with a requestId (a UUID), not a number.
-- It is only ever read as "did this note land" (the outbox sweep queues rows where it is NULL).
CREATE TABLE call_result (
    id              BIGSERIAL PRIMARY KEY,
    call_id         BIGINT       NOT NULL UNIQUE REFERENCES call_attempt(id) ON DELETE CASCADE,
    summary         TEXT         NOT NULL,
    reason_code     VARCHAR(50),
    promised_date   DATE,
    promised_amount NUMERIC(18,2),
    sentiment       VARCHAR(20),
    needs_follow_up BOOLEAN      NOT NULL DEFAULT false,
    follow_up_note  TEXT,
    escalated       BOOLEAN      NOT NULL DEFAULT false,
    crm_note_id     VARCHAR(64),
    crm_attempts    INT          NOT NULL DEFAULT 0,
    crm_last_error  TEXT,
    outcome         JSONB        NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_result_crm_pending ON call_result(crm_attempts) WHERE crm_note_id IS NULL;
-- RetentionService purges by age, in batches.
CREATE INDEX idx_call_result_created ON call_result(created_at);

-- The timeline of one call: which flow nodes it went through, the digits pressed, when it was
-- queued, which operators rang and who answered, transfers, the survey score. Append-only;
-- queue and operator reports are computed from these rows plus the call_attempt columns.
--
-- queue_id / user_id: the reports group rows by these, so they are pulled out of the JSON
-- once, on write. A value that is not a number becomes NULL rather than failing the insert
-- that carries it.
CREATE TABLE call_event (
    id              BIGSERIAL PRIMARY KEY,
    company_id      BIGINT      NOT NULL REFERENCES company(id),
    call_attempt_id BIGINT      NOT NULL REFERENCES call_attempt(id) ON DELETE CASCADE,
    at              TIMESTAMPTZ NOT NULL,
    type            VARCHAR(32) NOT NULL,
    payload         JSONB,
    queue_id        BIGINT GENERATED ALWAYS AS (
        CASE WHEN payload ->> 'queueId' ~ '^[0-9]{1,18}$' THEN (payload ->> 'queueId')::bigint END) STORED,
    user_id         BIGINT GENERATED ALWAYS AS (
        CASE WHEN payload ->> 'userId' ~ '^[0-9]{1,18}$' THEN (payload ->> 'userId')::bigint END) STORED
);
CREATE INDEX idx_call_event_attempt ON call_event(call_attempt_id, at);
CREATE INDEX idx_call_event_company ON call_event(company_id, type, at);

-- Every MCP tool call, with what it cost. Somebody's server answering slowly is felt as
-- silence on a live call, so this is where that gets proved rather than argued about.
CREATE TABLE mcp_tool_execution (
    id                BIGSERIAL    PRIMARY KEY,
    company_id        BIGINT       NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    mcp_connection_id BIGINT       REFERENCES mcp_connection(id) ON DELETE SET NULL,
    call_attempt_id   BIGINT       REFERENCES call_attempt(id) ON DELETE SET NULL,
    tool_name         VARCHAR(128) NOT NULL,
    status            VARCHAR(16)  NOT NULL,
    latency_ms        INT          NOT NULL DEFAULT 0,
    -- Truncated on the way in. These are for reading a failure back, not for keeping a
    -- copy of everything a customer's server was told.
    args_preview      VARCHAR(500),
    result_preview    VARCHAR(500),
    error             VARCHAR(500),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_mcp_execution_company_created ON mcp_tool_execution(company_id, created_at DESC);
CREATE INDEX idx_mcp_execution_call ON mcp_tool_execution(call_attempt_id);

-- One row per request a company tool sent. The arguments are kept as a hash — they are the
-- caller's name, phone and address, and telling equal requests apart is all this log needs.
-- A refused request keeps the start of the endpoint's answer.
CREATE TABLE tool_execution (
    id              BIGSERIAL    PRIMARY KEY,
    company_id      BIGINT       NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    tool_id         BIGINT       REFERENCES tool(id) ON DELETE SET NULL,
    call_attempt_id BIGINT       REFERENCES call_attempt(id) ON DELETE SET NULL,
    tool_name       VARCHAR(100) NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    http_status     INT,
    latency_ms      INT          NOT NULL DEFAULT 0,
    args_hash       VARCHAR(64),
    error           VARCHAR(500),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_tool_execution_status CHECK (status IN ('OK', 'HTTP_ERROR', 'FAILED'))
);
CREATE INDEX idx_tool_execution_company_created ON tool_execution(company_id, created_at DESC);
CREATE INDEX idx_tool_execution_tool_created ON tool_execution(tool_id, created_at DESC);
CREATE INDEX idx_tool_execution_call ON tool_execution(call_attempt_id);

-- One outbox for the writes a finished call owes other systems. OutboxDeliveryService claims
-- due rows with FOR UPDATE SKIP LOCKED in a short transaction, calls the remote system outside
-- it, then marks the row SENT or schedules the next try with exponential backoff; after
-- voice-agent.outbox.max-attempts the row is DEAD and an alert is raised.
--
-- idempotency_key is '<kind>:<call_attempt_id>': a call gets at most one message of each kind,
-- and a SENT row is never sent again. next_attempt_at doubles as the claim's lease — a worker
-- that takes a row pushes it past the time its delivery may take.
CREATE TABLE outbox_message (
    id              BIGSERIAL PRIMARY KEY,
    company_id      BIGINT       NOT NULL REFERENCES company(id),
    kind            VARCHAR(40)  NOT NULL,
    call_attempt_id BIGINT       NOT NULL REFERENCES call_attempt(id) ON DELETE CASCADE,
    payload         JSONB        NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    status          VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
    attempts        INT          NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_error      TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at         TIMESTAMPTZ,
    -- Mirror callrecord.domain.enums.OutboxMessageKind and OutboxMessageStatus.
    CONSTRAINT ck_outbox_message_kind CHECK (kind IN ('CRM_NOTE', 'CRM_CALL_HISTORY')),
    CONSTRAINT ck_outbox_message_status CHECK (status IN ('PENDING', 'SENT', 'DEAD'))
);
-- The worker's claim reads due PENDING rows only.
CREATE INDEX idx_outbox_message_due ON outbox_message(next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX idx_outbox_message_company ON outbox_message(company_id);
CREATE INDEX idx_outbox_message_call_attempt ON outbox_message(call_attempt_id);
-- Finished messages are purged by age; PENDING ones are never touched.
CREATE INDEX idx_outbox_message_finished_created ON outbox_message(created_at)
    WHERE status IN ('SENT', 'DEAD');

-- ---------------------------------------------------------------------------
-- Operator calls: wrap-up, talk metrics, scorecards (docs/OPERATOR-DIALER-ANALYTICS-PLAN.md)
-- ---------------------------------------------------------------------------

-- Operators close each call with a wrap-up code.
--
-- wrap_up_code: one list per company, shared by every queue and campaign. outcome says what
--   the code means for a campaign target: DONE (finished), RETRY (call again — at
--   callback_at when the code asks for one), EXHAUSTED (do not call again).
-- call_wrap_up: at most one per call; sent again, it replaces the earlier one. The code's name
--   and outcome are copied in, so renaming or deleting the code keeps past calls as they were.
CREATE TABLE wrap_up_code (
    id                   BIGSERIAL PRIMARY KEY,
    company_id           BIGINT       NOT NULL REFERENCES company(id),
    name                 VARCHAR(100) NOT NULL,
    outcome              VARCHAR(20)  NOT NULL,
    requires_callback_at BOOLEAN      NOT NULL DEFAULT false,
    enabled              BOOLEAN      NOT NULL DEFAULT true,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_wrap_up_code_outcome CHECK (outcome IN ('DONE', 'RETRY', 'EXHAUSTED'))
);
CREATE INDEX idx_wrap_up_code_company ON wrap_up_code(company_id);

CREATE TABLE call_wrap_up (
    call_attempt_id  BIGINT       PRIMARY KEY REFERENCES call_attempt(id) ON DELETE CASCADE,
    company_id       BIGINT       NOT NULL REFERENCES company(id),
    wrap_up_code_id  BIGINT       REFERENCES wrap_up_code(id) ON DELETE SET NULL,
    code_name        VARCHAR(100) NOT NULL,
    outcome          VARCHAR(20)  NOT NULL,
    operator_user_id BIGINT       NOT NULL REFERENCES app_user(id),
    note             TEXT,
    callback_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_call_wrap_up_outcome CHECK (outcome IN ('DONE', 'RETRY', 'EXHAUSTED'))
);
CREATE INDEX idx_call_wrap_up_company ON call_wrap_up(company_id, created_at);

-- Who talked how much on a client's call with an operator, measured from the two sides' own
-- audio while the operator was on the call.
CREATE TABLE call_talk_metrics (
    call_id            BIGINT PRIMARY KEY REFERENCES call_attempt(id) ON DELETE CASCADE,
    analysed_ms        INT         NOT NULL,
    client_talk_ms     INT         NOT NULL,
    operator_talk_ms   INT         NOT NULL,
    overtalk_ms        INT         NOT NULL,
    silence_ms         INT         NOT NULL,
    longest_silence_ms INT         NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Operator call scorecards: the company's criteria, and each operator call marked on them by
-- the judge.
CREATE TABLE scorecard_criterion (
    id          BIGSERIAL PRIMARY KEY,
    company_id  BIGINT        NOT NULL REFERENCES company(id),
    name        VARCHAR(100)  NOT NULL,
    instruction VARCHAR(1000) NOT NULL,
    weight      INT           NOT NULL CHECK (weight BETWEEN 1 AND 10),
    enabled     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_scorecard_criterion_company ON scorecard_criterion(company_id);

-- What the judge said (judged_score) stays next to what the supervisor settled on (score):
-- the difference is how far the judge can be trusted. The *_tokens are the scorecard
-- analysis charge.
CREATE TABLE call_scorecard (
    call_id           BIGINT      PRIMARY KEY REFERENCES call_attempt(id) ON DELETE CASCADE,
    company_id        BIGINT      NOT NULL REFERENCES company(id),
    operator_user_id  BIGINT      REFERENCES app_user(id) ON DELETE SET NULL,
    score             INT,
    judged_score      INT,
    sentiment         VARCHAR(20),
    needs_review      BOOLEAN     NOT NULL DEFAULT FALSE,
    review_status     VARCHAR(20) CHECK (review_status IN ('PENDING', 'CONFIRMED', 'CORRECTED')),
    review_note       VARCHAR(2000),
    reviewed_by       BIGINT      REFERENCES app_user(id) ON DELETE SET NULL,
    reviewed_at       TIMESTAMPTZ,
    prompt_tokens     BIGINT      NOT NULL DEFAULT 0,
    completion_tokens BIGINT      NOT NULL DEFAULT 0,
    cached_tokens     BIGINT      NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- The quality reports read a company's scorecards by when they were made.
CREATE INDEX idx_call_scorecard_company ON call_scorecard(company_id, created_at);
CREATE INDEX idx_call_scorecard_review ON call_scorecard(company_id, review_status);

-- The criterion's name and weight are copied in: editing or deleting it leaves scored calls as
-- they were. judged_answer is the judge's, answer the supervisor's correction of it.
CREATE TABLE call_scorecard_answer (
    id             BIGSERIAL PRIMARY KEY,
    call_id        BIGINT       NOT NULL REFERENCES call_scorecard(call_id) ON DELETE CASCADE,
    criterion_id   BIGINT       REFERENCES scorecard_criterion(id) ON DELETE SET NULL,
    criterion_name VARCHAR(100) NOT NULL,
    weight         INT          NOT NULL,
    answer         VARCHAR(20)  NOT NULL CHECK (answer IN ('YES', 'NO', 'NOT_APPLICABLE')),
    judged_answer  VARCHAR(20)  NOT NULL,
    evidence       VARCHAR(500),
    CONSTRAINT ck_call_scorecard_answer_judged CHECK (judged_answer IN ('YES', 'NO', 'NOT_APPLICABLE'))
);
CREATE INDEX idx_call_scorecard_answer_call ON call_scorecard_answer(call_id);

-- What each scored call was about, as the scorecard judge tagged it — one to three short
-- lowercase tags.
CREATE TABLE call_scorecard_topic (
    call_id BIGINT      NOT NULL REFERENCES call_scorecard(call_id) ON DELETE CASCADE,
    topic   VARCHAR(60) NOT NULL,
    PRIMARY KEY (call_id, topic)
);
CREATE INDEX idx_call_scorecard_topic_topic ON call_scorecard_topic(topic);

-- ---------------------------------------------------------------------------
-- Voicemail and missed calls (docs/INBOUND-PBX-PLAN.md §8)
-- ---------------------------------------------------------------------------

-- Where a caller can leave a message. greeting_text is spoken with TTS unless a recorded
-- greeting_file_id is set. transcribe=true runs the message through STT as it is recorded,
-- so the operator reads it instead of listening to it.
CREATE TABLE voicemail_box (
    id               BIGSERIAL PRIMARY KEY,
    company_id       BIGINT       NOT NULL REFERENCES company(id),
    name             VARCHAR(255) NOT NULL,
    greeting_text    TEXT,
    greeting_file_id BIGINT REFERENCES stored_file(id) ON DELETE SET NULL,
    max_seconds      INT          NOT NULL DEFAULT 120,
    transcribe       BOOLEAN      NOT NULL DEFAULT true,
    enabled          BOOLEAN      NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_voicemail_box_company ON voicemail_box(company_id);

-- status: NEW, HEARD, ARCHIVED
CREATE TABLE voicemail_message (
    id              BIGSERIAL PRIMARY KEY,
    company_id      BIGINT      NOT NULL REFERENCES company(id),
    box_id          BIGINT      NOT NULL REFERENCES voicemail_box(id) ON DELETE CASCADE,
    call_attempt_id BIGINT      REFERENCES call_attempt(id) ON DELETE SET NULL,
    caller_phone    VARCHAR(32),
    stored_file_id  BIGINT      REFERENCES stored_file(id) ON DELETE SET NULL,
    duration_sec    INT         NOT NULL DEFAULT 0,
    transcript      TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'NEW',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_voicemail_message_company ON voicemail_message(company_id, created_at DESC);

-- One open row per company and phone — a caller who rings three times after hours is one
-- item on the operators' list with call_count 3, not three. reason is the latest:
-- AFTER_HOURS, ABANDONED, NO_OPERATOR, CALLBACK_REQUESTED, VOICEMAIL. status: NEW,
-- IN_PROGRESS, CALLED_BACK, RESOLVED. A later answered call from the same number closes it.
CREATE TABLE missed_call (
    id                   BIGSERIAL PRIMARY KEY,
    company_id           BIGINT      NOT NULL REFERENCES company(id),
    phone                VARCHAR(32) NOT NULL,
    reason               VARCHAR(20) NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'NEW',
    call_count           INT         NOT NULL DEFAULT 1,
    first_at             TIMESTAMPTZ NOT NULL,
    last_at              TIMESTAMPTZ NOT NULL,
    queue_id             BIGINT REFERENCES call_queue(id) ON DELETE SET NULL,
    inbound_route_id     BIGINT REFERENCES inbound_route(id) ON DELETE SET NULL,
    last_call_attempt_id BIGINT REFERENCES call_attempt(id) ON DELETE SET NULL,
    voicemail_message_id BIGINT REFERENCES voicemail_message(id) ON DELETE SET NULL,
    assigned_user_id     BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    callback_attempts    INT         NOT NULL DEFAULT 0,
    last_callback_at     TIMESTAMPTZ,
    resolved_at          TIMESTAMPTZ,
    resolved_by_user_id  BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    note                 TEXT
);
CREATE UNIQUE INDEX uq_missed_call_open ON missed_call(company_id, phone) WHERE status IN ('NEW', 'IN_PROGRESS');
CREATE INDEX idx_missed_call_company ON missed_call(company_id, last_at DESC);
CREATE INDEX idx_missed_call_queue ON missed_call(queue_id);
CREATE INDEX idx_missed_call_inbound_route ON missed_call(inbound_route_id);
CREATE INDEX idx_missed_call_last_call_attempt ON missed_call(last_call_attempt_id);
CREATE INDEX idx_missed_call_voicemail_message ON missed_call(voicemail_message_id);
CREATE INDEX idx_missed_call_assigned_user ON missed_call(assigned_user_id);

-- ---------------------------------------------------------------------------
-- Client-facing data: contacts, opt-out, cross-call memory, knowledge base
-- ---------------------------------------------------------------------------

-- Contacts — directory of known customers/contacts.
CREATE TABLE contact (
    id         BIGSERIAL PRIMARY KEY,
    company_id BIGINT       NOT NULL REFERENCES company(id),
    name       VARCHAR(255) NOT NULL,
    phone      VARCHAR(20)  NOT NULL,
    address    VARCHAR(500),
    tags       VARCHAR(500),
    notes      TEXT,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX idx_contact_company_phone ON contact(company_id, phone);
CREATE INDEX idx_contact_company_name ON contact(company_id, name);

-- Do Not Call List — global opt-out list per company.
CREATE TABLE do_not_call_list (
    id         BIGSERIAL PRIMARY KEY,
    company_id BIGINT       NOT NULL REFERENCES company(id),
    phone      VARCHAR(20)  NOT NULL,
    reason     TEXT,
    source     VARCHAR(20)  NOT NULL DEFAULT 'CALL',   -- CALL, CRM, MANUAL
    removed_at TIMESTAMPTZ,
    removed_by VARCHAR(64),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX idx_do_not_call_list_company_phone ON do_not_call_list(company_id, phone);

-- Cross-call memory per client: one row per (company, phone), read into the system prompt
-- before every call and written back after every summarized conversation.
CREATE TABLE client_memory (
    id                 BIGSERIAL PRIMARY KEY,
    company_id         BIGINT       NOT NULL REFERENCES company(id),
    phone              VARCHAR(20)  NOT NULL,
    preferred_name     VARCHAR(100),
    preferred_language VARCHAR(10),
    operator_notes     TEXT,
    recent_calls       JSONB        NOT NULL DEFAULT '[]',
    facts              JSONB        NOT NULL DEFAULT '{}',
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX idx_client_memory_company_phone ON client_memory(company_id, phone);
-- RetentionService purges by age, in batches.
CREATE INDEX idx_client_memory_updated ON client_memory(updated_at);

-- Knowledge base — answers to the questions a scenario does not script, looked up by
-- item_key, so the key is unique per company (KnowledgeItemJpaRepository returns one row).
CREATE TABLE knowledge_base_item (
    id         BIGSERIAL PRIMARY KEY,
    company_id BIGINT       NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    agent_id   BIGINT       REFERENCES ai_agent(id) ON DELETE CASCADE, -- NULL = company-wide
    item_key   VARCHAR(100) NOT NULL,
    topic      VARCHAR(50)  NOT NULL,
    title      VARCHAR(255) NOT NULL,
    answer_uz  TEXT         NOT NULL,
    answer_ru  TEXT,
    answer_en  TEXT,
    keywords   TEXT         NOT NULL DEFAULT '',
    is_active  BOOLEAN      NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX idx_knowledge_company_key ON knowledge_base_item(company_id, item_key);
CREATE INDEX idx_knowledge_active ON knowledge_base_item(company_id, is_active);
CREATE INDEX idx_knowledge_base_item_agent ON knowledge_base_item(agent_id);

-- Uploaded knowledge: a file or a URL the agent may answer from, extracted, split into
-- chunks and embedded. agent_id NULL keeps a source company-wide.
--
-- feed_items_path / feed_key_field / refresh_minutes: a STRUCTURED_FEED source — where its
-- rows sit in a JSON answer, which column is the key, and how often a URL feed is fetched
-- again (NULL = only by hand).
CREATE TABLE knowledge_source (
    id                 BIGSERIAL PRIMARY KEY,
    company_id         BIGINT       NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    agent_id           BIGINT       REFERENCES ai_agent(id) ON DELETE SET NULL,
    name               VARCHAR(200) NOT NULL,
    source_type        VARCHAR(32)  NOT NULL,
    original_file_name VARCHAR(255),
    -- The file is addressed by id, not by a raw path: the bucket, the object key and the
    -- content type all live in stored_file, and the indexer needs all three to read the
    -- bytes back.
    stored_file_id     BIGINT       REFERENCES stored_file(id) ON DELETE SET NULL,
    url                VARCHAR(2048),
    status             VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    chunk_count        INT          NOT NULL DEFAULT 0,
    error_code         VARCHAR(100),
    error_message      VARCHAR(1000),
    last_indexed_at    TIMESTAMPTZ,
    feed_items_path    VARCHAR(200),
    feed_key_field     VARCHAR(100),
    refresh_minutes    INT,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_knowledge_source_refresh
        CHECK (refresh_minutes IS NULL OR refresh_minutes >= 15)
);
CREATE INDEX idx_knowledge_source_company ON knowledge_source(company_id);
CREATE INDEX idx_knowledge_source_agent ON knowledge_source(agent_id);
CREATE INDEX idx_knowledge_source_status ON knowledge_source(status);
CREATE INDEX idx_knowledge_source_file ON knowledge_source(stored_file_id);

-- The chunks a call actually searches.
--
-- The embedding is a BYTEA of big-endian float32s rather than a pgvector column: one
-- company's knowledge base is thousands of chunks, not millions, so the cosine runs in
-- Java over a per-agent cache. Only catalog_item rows live in the vector index.
CREATE TABLE knowledge_chunk (
    id         BIGSERIAL PRIMARY KEY,
    source_id  BIGINT      NOT NULL REFERENCES knowledge_source(id) ON DELETE CASCADE,
    ordinal    INT         NOT NULL,
    content    TEXT        NOT NULL,
    -- NULL when the embedding model was unreachable while indexing: the chunk is still
    -- stored and still found by the keyword fallback, and a retry fills it in.
    embedding  BYTEA,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_knowledge_chunk_source_ordinal UNIQUE (source_id, ordinal)
);
CREATE INDEX idx_knowledge_chunk_source ON knowledge_chunk(source_id);

-- A product catalog the agent searches during a call: one row per item of a STRUCTURED_FEED
-- source. A catalog is tens of thousands of rows — too many for the in-process cosine
-- knowledge_chunk uses — so these live in a pgvector index. company_id is repeated from the
-- source so a search narrows on this table before it joins. The embedding is 768 floats —
-- what CatalogItemRepositoryAdapter writes — and NULL when the embedding model was
-- unreachable: such a row is still found by its words. search_text drops apostrophes first,
-- so "ko'k" and "kok" are one word, the way CatalogRanker reads them.
CREATE TABLE catalog_item (
    id           BIGSERIAL    PRIMARY KEY,
    source_id    BIGINT       NOT NULL REFERENCES knowledge_source(id) ON DELETE CASCADE,
    company_id   BIGINT       NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    sku          VARCHAR(200) NOT NULL,
    content      TEXT         NOT NULL,
    attributes   JSONB        NOT NULL DEFAULT '{}'::jsonb,
    content_hash VARCHAR(64)  NOT NULL,
    embedding    vector(768),
    search_text  tsvector GENERATED ALWAYS AS
                 (to_tsvector('simple', translate(lower(content), '''`ʻʼ‘’', ''))) STORED,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_catalog_item_source_sku UNIQUE (source_id, sku)
);
CREATE INDEX idx_catalog_item_company ON catalog_item(company_id);
CREATE INDEX idx_catalog_item_embedding ON catalog_item USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_catalog_item_search ON catalog_item USING gin (search_text);

-- ---------------------------------------------------------------------------
-- AI call quality and the agent's improvement loop
-- ---------------------------------------------------------------------------

-- One row per finished AI call — the quality judge's verdict, the signals read without an
-- LLM, and a person's review of it. What the review inbox and the daily insight job read.
CREATE TABLE call_quality (
    id                    BIGSERIAL    PRIMARY KEY,
    company_id            BIGINT       NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    call_attempt_id       BIGINT       NOT NULL UNIQUE REFERENCES call_attempt(id) ON DELETE CASCADE,
    ai_agent_id           BIGINT       REFERENCES ai_agent(id) ON DELETE SET NULL,
    agent_version         INT,
    disposition           VARCHAR(50),
    -- The verdict. NULL throughout when the judge did not score the call: off, not sampled, or failed.
    score                 INT,
    guardrail_violation   BOOLEAN,
    language_mismatch     BOOLEAN,
    talked_over_client    BOOLEAN,
    outcome_correct       BOOLEAN,
    goal_reached          BOOLEAN,
    unanswered_question   BOOLEAN,
    tool_misuse           BOOLEAN,
    note                  TEXT,
    needs_review          BOOLEAN      NOT NULL DEFAULT false,
    -- Signals read without an LLM, counted by the dialog while the call runs. repeat_call is
    -- set later, by the caller's next call within a day.
    turn_count            INT          NOT NULL DEFAULT 0,
    repair_count          INT          NOT NULL DEFAULT 0,
    tool_error_count      INT          NOT NULL DEFAULT 0,
    barge_in_count        INT          NOT NULL DEFAULT 0,
    no_input_prompt_count INT          NOT NULL DEFAULT 0,
    low_confidence_count  INT          NOT NULL DEFAULT 0,
    repeat_call           BOOLEAN      NOT NULL DEFAULT false,
    -- The review. NULL status: nothing flagged and nobody looked.
    review_status         VARCHAR(20),
    review_reason         TEXT,
    reviewed_by           BIGINT       REFERENCES app_user(id) ON DELETE SET NULL,
    reviewed_at           TIMESTAMPTZ,
    knowledge_item_id     BIGINT       REFERENCES knowledge_base_item(id) ON DELETE SET NULL,
    test_case_id          BIGINT       REFERENCES agent_test_case(id) ON DELETE SET NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_call_quality_review_status CHECK (review_status IN ('PENDING', 'AGENT_CORRECT', 'AGENT_WRONG'))
);
CREATE INDEX idx_call_quality_review ON call_quality(company_id, created_at DESC) WHERE review_status IS NOT NULL;
CREATE INDEX idx_call_quality_agent ON call_quality(ai_agent_id, created_at DESC);

-- What a day of an agent's calls says it should change, and how far that change has got. The
-- daily job proposes; a person accepts or rejects; and a prompt change that was accepted waits
-- behind an evaluation run before it is allowed to stay.
CREATE TABLE agent_insight (
    id              BIGSERIAL    PRIMARY KEY,
    company_id      BIGINT       NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    ai_agent_id     BIGINT       NOT NULL REFERENCES ai_agent(id) ON DELETE CASCADE,
    type            VARCHAR(30)  NOT NULL,
    title           VARCHAR(200) NOT NULL,
    -- What was seen, in the operator's language, and the one concrete change it suggests.
    detail          TEXT,
    suggestion      TEXT,
    call_count      INT          NOT NULL DEFAULT 0,
    -- The calls it was read from: the evidence a person opens before deciding.
    sample_call_ids JSONB        NOT NULL DEFAULT '[]',
    status          VARCHAR(20)  NOT NULL,
    decision_reason TEXT,
    decided_by      BIGINT       REFERENCES app_user(id) ON DELETE SET NULL,
    decided_at      TIMESTAMPTZ,
    -- The gate: the evaluation run an accepted prompt change is waiting on.
    eval_run_id     BIGINT       REFERENCES agent_eval_run(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_agent_insight_type CHECK (type IN
        ('KB_GAP', 'PROMPT_FIX', 'TOOL_FIX', 'NEW_TEST_CASE', 'PRONUNCIATION', 'HANDOFF_PATTERN')),
    CONSTRAINT ck_agent_insight_status CHECK (status IN ('PROPOSED', 'EVALUATING', 'ACCEPTED', 'REJECTED'))
);
CREATE INDEX idx_agent_insight_inbox ON agent_insight(company_id, status, created_at DESC);
-- The job runs every day over an overlapping window, so the same finding would be proposed
-- again tomorrow. One open proposal per agent, type and title is all the inbox may hold.
CREATE UNIQUE INDEX uq_agent_insight_open ON agent_insight(ai_agent_id, type, title) WHERE status = 'PROPOSED';
CREATE INDEX idx_agent_insight_eval_run ON agent_insight(eval_run_id) WHERE eval_run_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- Conversions. Did the call actually work? The disposition the bot recorded —
-- "PROMISE_TO_PAY" — is a promise, not a payment. These three tables let a company's own
-- system say what really happened afterwards, and tie it back to the call, the campaign
-- and the A/B variant that produced it.
-- ---------------------------------------------------------------------------

-- What counts as a conversion for this company, and how long after a call it still counts.
-- Company-scoped rather than per campaign: the system posting the event knows a customer
-- paid, not which campaign called them — that is what attribution works out.
CREATE TABLE conversion_goal (
    id                      BIGSERIAL PRIMARY KEY,
    company_id              BIGINT       NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    -- What the posting system names it: 'payment', 'appointment', 'renewal'.
    goal_key                VARCHAR(64)  NOT NULL,
    name                    VARCHAR(128) NOT NULL,
    -- How long after a call a conversion may still be credited to it. Past this the
    -- customer is assumed to have acted for some other reason.
    attribution_window_hours INT         NOT NULL DEFAULT 72,
    -- LAST_CALL or FIRST_CALL: which call in the window gets the credit.
    attribution_model       VARCHAR(24)  NOT NULL DEFAULT 'LAST_CALL',
    enabled                 BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_conversion_goal_key UNIQUE (company_id, goal_key)
);
CREATE INDEX idx_conversion_goal_company ON conversion_goal(company_id);

-- One thing that happened out in the world, as reported to us.
CREATE TABLE conversion_event (
    id                BIGSERIAL PRIMARY KEY,
    company_id        BIGINT       NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    goal_key          VARCHAR(64)  NOT NULL,
    -- The customer, matched to a call by the number that was dialled. A phone is what
    -- both sides already agree on; an internal id would need a mapping table that has to
    -- be kept in step with somebody else's CRM.
    phone             VARCHAR(20)  NOT NULL,
    occurred_at       TIMESTAMPTZ  NOT NULL,
    value_uzs         BIGINT,
    -- Where it came from: 'crm', 'payme', an integration's name.
    source            VARCHAR(64),
    -- Whatever the poster wants kept for later argument: an order id, a receipt.
    evidence          JSONB,
    -- The poster's own id for the thing. A retried webhook writes the same key and is
    -- refused by the index rather than counted twice.
    dedupe_key        VARCHAR(128) NOT NULL,
    -- Kept, not dropped: "no call in the window" is a useful answer, and deleting the row
    -- would make the same event arrive again on the next retry.
    rejected          BOOLEAN      NOT NULL DEFAULT FALSE,
    rejection_reason  VARCHAR(255),
    ingested_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_conversion_event_dedupe UNIQUE (company_id, dedupe_key)
);
CREATE INDEX idx_conversion_event_company_occurred ON conversion_event(company_id, occurred_at DESC);
CREATE INDEX idx_conversion_event_goal ON conversion_event(company_id, goal_key);

-- Which call earned it. One row per attributed event; an event that matched nothing has
-- no row here and carries its reason on conversion_event instead.
CREATE TABLE conversion_attribution (
    id                    BIGSERIAL PRIMARY KEY,
    conversion_event_id   BIGINT      NOT NULL UNIQUE REFERENCES conversion_event(id) ON DELETE CASCADE,
    company_id            BIGINT      NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    campaign_id           BIGINT      REFERENCES campaign(id) ON DELETE SET NULL,
    variant_id            BIGINT      REFERENCES campaign_variant(id) ON DELETE SET NULL,
    call_attempt_id       BIGINT      REFERENCES call_attempt(id) ON DELETE SET NULL,
    -- Stamped so an old attribution can still be explained after the goal is retuned.
    attribution_model     VARCHAR(24) NOT NULL,
    window_hours          INT         NOT NULL,
    attributed_value_uzs  BIGINT,
    computed_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_conversion_attribution_campaign ON conversion_attribution(campaign_id, computed_at DESC);
CREATE INDEX idx_conversion_attribution_variant ON conversion_attribution(variant_id);
CREATE INDEX idx_conversion_attribution_call_attempt ON conversion_attribution(call_attempt_id);

-- ---------------------------------------------------------------------------
-- Notifications
-- ---------------------------------------------------------------------------

CREATE TABLE notification (
    id         BIGSERIAL PRIMARY KEY,
    company_id BIGINT       NOT NULL REFERENCES company(id),
    type       VARCHAR(30)  NOT NULL,
    title      VARCHAR(255) NOT NULL,
    message    VARCHAR(1000),
    link       VARCHAR(255),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_notification_company ON notification(company_id, created_at DESC);

CREATE TABLE notification_recipient (
    id               BIGSERIAL PRIMARY KEY,
    notification_id  BIGINT NOT NULL REFERENCES notification(id) ON DELETE CASCADE,
    user_id          BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    read_at          TIMESTAMPTZ
);
CREATE UNIQUE INDEX idx_notification_recipient_unique ON notification_recipient(notification_id, user_id);
CREATE INDEX idx_notification_recipient_user ON notification_recipient(user_id, read_at);

-- Per-user notification preferences: absence of a row means the user accepts the notification.
CREATE TABLE notification_preference (
    id      BIGSERIAL PRIMARY KEY,
    user_id BIGINT      NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    type    VARCHAR(30) NOT NULL,
    enabled BOOLEAN     NOT NULL
);
CREATE UNIQUE INDEX idx_notification_preference_unique ON notification_preference(user_id, type);

-- Company-level notification channel x event matrix (§11 "GET/PUT /api/settings/notifications").
CREATE TABLE notification_channel (
    id         BIGSERIAL PRIMARY KEY,
    company_id BIGINT      NOT NULL REFERENCES company(id),
    channel    VARCHAR(20) NOT NULL, -- EMAIL, WEBHOOK, TELEGRAM
    target     VARCHAR(500),
    enabled    BOOLEAN     NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX idx_notification_channel_unique ON notification_channel(company_id, channel);

CREATE TABLE notification_matrix (
    id         BIGSERIAL PRIMARY KEY,
    company_id BIGINT      NOT NULL REFERENCES company(id),
    type       VARCHAR(30) NOT NULL, -- notification.enums.NotificationType
    channel    VARCHAR(20) NOT NULL, -- EMAIL, WEBHOOK, TELEGRAM
    enabled    BOOLEAN     NOT NULL DEFAULT false
);
CREATE UNIQUE INDEX idx_notification_matrix_unique ON notification_matrix(company_id, type, channel);

-- Personal channel x event matrix (UI-DESIGN §8.3 "Bildirishnomalar" tab).
CREATE TABLE personal_notification_matrix (
    id         BIGSERIAL PRIMARY KEY,
    company_id BIGINT      NOT NULL REFERENCES company(id),
    user_id    BIGINT      NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    type       VARCHAR(30) NOT NULL, -- notification.enums.NotificationType
    channel    VARCHAR(20) NOT NULL, -- notification.enums.NotificationChannelType
    enabled    BOOLEAN     NOT NULL DEFAULT false
);
CREATE UNIQUE INDEX idx_personal_notification_matrix_unique ON personal_notification_matrix(user_id, type, channel);

-- ---------------------------------------------------------------------------
-- Sessions, two-factor, schedules, per-user UI state
-- ---------------------------------------------------------------------------

-- Tracks active tokens and refresh sessions per browser/device.
--
-- previous_refresh_token_hash: refresh-token reuse detection. A rotation keeps the hash it
-- replaced, so a refresh token presented a second time — one that was stolen and then
-- rotated by its thief, or vice versa — is recognised and the whole session revoked,
-- instead of being indistinguishable from a random invalid token.
CREATE TABLE user_session (
    id                          BIGSERIAL PRIMARY KEY,
    company_id                  BIGINT      NOT NULL REFERENCES company(id),
    user_id                     BIGINT      NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    refresh_token_hash          VARCHAR(64) NOT NULL,
    previous_refresh_token_hash VARCHAR(64),
    device                      VARCHAR(255),
    ip_address                  VARCHAR(64),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_activity_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at                  TIMESTAMPTZ NOT NULL,
    revoked_at                  TIMESTAMPTZ
);
CREATE UNIQUE INDEX idx_user_session_refresh_token_hash ON user_session(refresh_token_hash);
CREATE INDEX idx_user_session_user ON user_session(user_id);
CREATE INDEX idx_user_session_previous_hash ON user_session(previous_refresh_token_hash)
    WHERE previous_refresh_token_hash IS NOT NULL;

-- Two-factor login with an authenticator app (TOTP, RFC 6238). One row per user who started
-- setting it up; enabled flips once they prove a code. The secret is encrypted with the
-- secret-vault key. last_used_step makes a code count once: a code read over someone's
-- shoulder cannot be replayed within its 30 seconds.
CREATE TABLE user_totp (
    user_id          BIGINT      PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    company_id       BIGINT      NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    secret_encrypted TEXT        NOT NULL,
    enabled          BOOLEAN     NOT NULL DEFAULT false,
    last_used_step   BIGINT      NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    enabled_at       TIMESTAMPTZ
);

-- Operator work schedule (UI-DESIGN §8.3 "Ish jadvali" tab) — weekly hours.
CREATE TABLE user_schedule_slot (
    id          BIGSERIAL PRIMARY KEY,
    company_id  BIGINT      NOT NULL REFERENCES company(id),
    user_id     BIGINT      NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    day_of_week VARCHAR(10) NOT NULL, -- java.time.DayOfWeek name, e.g. MONDAY
    start_time  TIME        NOT NULL,
    end_time    TIME        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_user_schedule_slot_user ON user_schedule_slot(user_id);

-- Generic per-user, per-table UI preference store (API-REQUIREMENTS §4 "Ustunlar ⚙").
CREATE TABLE user_table_config (
    id           BIGSERIAL PRIMARY KEY,
    company_id   BIGINT       NOT NULL REFERENCES company(id),
    user_id      BIGINT       NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    config_key   VARCHAR(100) NOT NULL,
    config_value JSONB        NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_table_config UNIQUE (user_id, config_key)
);

-- Scheduled email report delivery (§10.10 "📅 Jadval bo'yicha yuborish").
CREATE TABLE report_schedule (
    id           BIGSERIAL PRIMARY KEY,
    company_id   BIGINT       NOT NULL REFERENCES company(id),
    email        VARCHAR(255) NOT NULL,
    periodicity  VARCHAR(20)  NOT NULL,               -- DAILY, WEEKLY, MONTHLY
    format       VARCHAR(10)  NOT NULL DEFAULT 'pdf', -- csv, pdf, xlsx
    campaign_id  BIGINT       REFERENCES campaign(id) ON DELETE CASCADE, -- null = every campaign
    enabled      BOOLEAN      NOT NULL DEFAULT true,
    last_sent_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by   BIGINT
);
CREATE INDEX idx_report_schedule_company ON report_schedule(company_id);
CREATE INDEX idx_report_schedule_due ON report_schedule(enabled, last_sent_at) WHERE enabled;

-- ---------------------------------------------------------------------------
-- Per-company settings (§11). A missing row means "use the config/*.yml default": a row
-- pins the value in the database and makes the YAML default unreachable, so the seed
-- writes these only where it means to show an override.
-- ---------------------------------------------------------------------------

CREATE TABLE ai_model_config (
    id                  BIGSERIAL PRIMARY KEY,
    company_id          BIGINT NOT NULL UNIQUE REFERENCES company(id),
    model               VARCHAR(100),
    temperature         DOUBLE PRECISION,
    max_output_tokens   INTEGER,
    max_call_seconds    INTEGER,
    max_tokens_per_call BIGINT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Per-company TTS tuning overrides (§11 "GET/PUT /api/settings/voice"). speed is capped at
-- 0.25–3.0 (VoiceSettingsValidator): the range every synthesizer a per-call speed reaches
-- accepts — Yandex takes at most 3.0, Google and OpenAI at least 0.25.
CREATE TABLE voice_settings (
    id          BIGSERIAL PRIMARY KEY,
    company_id  BIGINT NOT NULL UNIQUE REFERENCES company(id),
    speed       DOUBLE PRECISION,
    pitch       DOUBLE PRECISION,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Per-company CRM connection (§11 "GET/PUT /api/settings/integrations"). Uysot's address
-- is platform configuration (voice-agent.crm.base-url); base_url_enc is where a
-- token-connected CRM lives per company — an amoCRM/Kommo account address, or a Bitrix24
-- inbound webhook URL. The webhook URL carries its secret in its path, so the column holds
-- SecretCipher ciphertext like the tokens beside it, never plaintext.
CREATE TABLE crm_integration (
    id                BIGSERIAL PRIMARY KEY,
    company_id        BIGINT      NOT NULL UNIQUE REFERENCES company(id),
    provider          VARCHAR(20) NOT NULL DEFAULT 'UYSOT',
    app_name          VARCHAR(255),
    grants_json       TEXT,
    access_token_enc  TEXT,
    refresh_token_enc TEXT,
    base_url_enc      TEXT,
    token_expires_at  TIMESTAMPTZ,
    status            VARCHAR(20) NOT NULL DEFAULT 'NOT_CONNECTED',
    connected_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Secret vault: the credentials an agent's tools and webhooks send, kept out of the
-- rows that describe them. value is AES-GCM (SecretCipher), never plaintext.
CREATE TABLE secret (
    id          BIGSERIAL PRIMARY KEY,
    company_id  BIGINT       NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    key         VARCHAR(120) NOT NULL,
    value       TEXT         NOT NULL,
    description VARCHAR(255),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_secret_company_key UNIQUE (company_id, key)
);
CREATE INDEX idx_secret_company_id ON secret(company_id);

-- A post-call webhook that failed is kept and tried again (1 min, 5 min, 30 min) instead of
-- being lost to a log line. sealed_target is the resolved URL and headers — credentials by
-- then — encrypted with the same key as the secret vault; host is what may be shown.
-- Delivered rows are deleted after a week.
--
-- delivery_id is sent as X-RobotCall-Delivery and stays the same across the delivery's
-- retries, so a receiver can drop a duplicate. The first try is made before the row exists,
-- so the application generates it and stores it with the row.
CREATE TABLE webhook_delivery (
    id              BIGSERIAL PRIMARY KEY,
    company_id      BIGINT       NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    delivery_id     UUID         NOT NULL DEFAULT gen_random_uuid(),
    source          VARCHAR(32)  NOT NULL,
    host            VARCHAR(255),
    sealed_target   TEXT         NOT NULL,
    payload         JSONB        NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    attempts        INT          NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    last_error      VARCHAR(500),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    delivered_at    TIMESTAMPTZ
);
CREATE INDEX idx_webhook_delivery_due ON webhook_delivery (next_attempt_at) WHERE status = 'PENDING';

-- ---------------------------------------------------------------------------
-- Audit
-- ---------------------------------------------------------------------------

CREATE TABLE audit_log (
    id         BIGSERIAL PRIMARY KEY,
    company_id BIGINT       NOT NULL REFERENCES company(id),
    actor      VARCHAR(255) NOT NULL,
    action     VARCHAR(100) NOT NULL,
    entity     VARCHAR(64),
    entity_id  VARCHAR(255),
    detail     TEXT,
    ip_address VARCHAR(45),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_created ON audit_log(created_at DESC);
CREATE INDEX idx_audit_action ON audit_log(action, created_at DESC);
CREATE INDEX idx_audit_log_company ON audit_log(company_id);

-- ---------------------------------------------------------------------------
-- Billing & invoicing. company_billing and billing_usage rows are created on demand by
-- BillingService (CompanyBilling.defaultFor / BillingUsage.defaultFor).
--
-- What makes the figures the customer sees reproducible: call_billing says what one call
-- was charged and at which price list, billing_ledger says how every balance got to where
-- it is, and company_billing.reserved_uzs holds money for calls that are still in the air.
-- ---------------------------------------------------------------------------

-- The balance itself is not touched until a call is settled, so a company can see what it
-- has left to spend as balance_uzs - reserved_uzs. (At what balance auto_recharge fires and
-- by how much is platform-wide, in config/billing.yml.)
CREATE TABLE company_billing (
    id                 BIGSERIAL PRIMARY KEY,
    company_id         BIGINT NOT NULL UNIQUE REFERENCES company(id) ON DELETE CASCADE,
    plan_code          VARCHAR(64) NOT NULL DEFAULT 'PRO_MONTHLY',
    plan_name          VARCHAR(128) NOT NULL DEFAULT 'Professional (Pro)',
    balance_uzs        BIGINT NOT NULL DEFAULT 0,
    reserved_uzs       BIGINT NOT NULL DEFAULT 0,
    auto_recharge      BOOLEAN NOT NULL DEFAULT FALSE,
    next_billing_date  TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Resource usage per monthly billing period.
CREATE TABLE billing_usage (
    id                   BIGSERIAL PRIMARY KEY,
    company_id           BIGINT NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    billing_period       VARCHAR(7) NOT NULL, -- e.g. '2026-03'
    used_minutes         INT NOT NULL DEFAULT 0,
    limit_minutes        INT NOT NULL DEFAULT 5000,
    used_tokens          BIGINT NOT NULL DEFAULT 0,
    limit_tokens         BIGINT NOT NULL DEFAULT 2000000,
    used_tts_chars       BIGINT NOT NULL DEFAULT 0,
    limit_tts_chars      BIGINT NOT NULL DEFAULT 1000000,
    used_channels        INT NOT NULL DEFAULT 0,
    limit_channels       INT NOT NULL DEFAULT 30,
    overage_price_minute DOUBLE PRECISION NOT NULL DEFAULT 400.0,
    overage_price_token  DOUBLE PRECISION NOT NULL DEFAULT 0.05,
    overage_price_tts    DOUBLE PRECISION NOT NULL DEFAULT 0.02,
    total_spend_uzs      BIGINT NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_company_billing_period UNIQUE (company_id, billing_period)
);

CREATE TABLE invoice (
    id            VARCHAR(64) PRIMARY KEY, -- e.g. 'INV-2026-003'
    company_id    BIGINT NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    period_name   VARCHAR(64) NOT NULL,    -- e.g. 'Mart 2026'
    amount_uzs    BIGINT NOT NULL,
    status        VARCHAR(32) NOT NULL DEFAULT 'PENDING', -- PAID, PENDING, CANCELLED
    paid_at       TIMESTAMPTZ,
    pdf_file_path VARCHAR(255),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_invoice_company_created ON invoice(company_id, created_at DESC);

CREATE TABLE payment_topup (
    id             BIGSERIAL PRIMARY KEY,
    payment_id     VARCHAR(64) NOT NULL UNIQUE, -- e.g. 'PAY-883921'
    company_id     BIGINT NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    user_id        BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    amount_uzs     BIGINT NOT NULL,
    payment_method VARCHAR(32) NOT NULL, -- PAYME, CLICK, BANK_TRANSFER
    status         VARCHAR(32) NOT NULL DEFAULT 'PENDING', -- PENDING, COMPLETED, FAILED, CANCELLED, EXPIRED
    checkout_url   VARCHAR(1000),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    paid_at        TIMESTAMPTZ
);
CREATE INDEX idx_payment_topup_company ON payment_topup(company_id, created_at DESC);
-- The expiry sweep and auto-recharge both look for PENDING top-ups by age.
CREATE INDEX idx_payment_topup_pending_created ON payment_topup(created_at) WHERE status = 'PENDING';

-- Real Payme / Click top-ups: one row per payment a provider starts against a payment_topup.
-- A provider repeats its callbacks whenever it gets no answer, so what it started is
-- recorded here under its own transaction id — unique per provider — and a repeated
-- callback finds the row it already wrote instead of starting, or crediting, the same
-- payment twice. The credit itself goes through billing_ledger with the key
-- 'topup:<payment_id>', which refuses a second one on its own as well.
CREATE TABLE payment_provider_transaction (
    id                       BIGSERIAL PRIMARY KEY,
    -- PAYME or CLICK (PaymentMethod).
    provider                 VARCHAR(16)  NOT NULL,
    -- Payme's transaction id, Click's click_trans_id.
    provider_transaction_id  VARCHAR(64)  NOT NULL,
    topup_id                 BIGINT       NOT NULL REFERENCES payment_topup(id) ON DELETE CASCADE,
    -- What the provider is paying, in tiyin (UZS * 100) — always the top-up's whole amount.
    amount_tiyin             BIGINT       NOT NULL,
    -- CREATED -> PERFORMED (balance credited) or CREATED -> CANCELLED (ProviderTransactionState).
    state                    VARCHAR(16)  NOT NULL,
    -- When the provider says it created the transaction (Payme's `time`); Payme's
    -- GetStatement filters on it. Null for Click.
    provider_time            TIMESTAMPTZ,
    -- Payme's 12-hour timeout runs from here; also its create_time.
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    performed_at             TIMESTAMPTZ,
    cancelled_at             TIMESTAMPTZ,
    -- Why it was cancelled: Payme's reason code, or the negative error Click reported.
    reason                   INT,
    CONSTRAINT uq_payment_provider_transaction UNIQUE (provider, provider_transaction_id),
    CONSTRAINT ck_payment_provider_transaction_provider CHECK (provider IN ('PAYME', 'CLICK')),
    CONSTRAINT ck_payment_provider_transaction_state CHECK (state IN ('CREATED', 'PERFORMED', 'CANCELLED'))
);
-- "Does this top-up already have a payment under way?" on every new one; also backs the FK.
CREATE INDEX idx_payment_provider_transaction_topup ON payment_provider_transaction(topup_id, state);
-- Payme's GetStatement: a provider's transactions over a time window.
CREATE INDEX idx_payment_provider_transaction_provider_time
    ON payment_provider_transaction(provider, provider_time);

-- What one call cost, and at which price list version.
--
-- A realtime (speech-to-speech) call is priced per connected minute of the engine, not per
-- token, second of recognition and character of synthesis: one vendor did all three and
-- bills its own way, and its audio tokens are an order of magnitude more numerous than a
-- text LLM's. realtime_seconds is stored next to the rest of the usage so a disputed
-- charge can still be recomputed from the row.
--
-- summary_*_tokens: the post-call summary is an LLM call of its own, charged as a separate
-- ledger entry ('summary-charge:<call_attempt_id>') because it can run after the call was
-- settled. Its tokens are kept apart from the conversation's so the charge can still be
-- recomputed: on a realtime call the conversation's tokens are informational only, while the
-- summary's are priced at the text-LLM rate. Their price is added to llm_cost_uzs and total_uzs.
-- analysis_*: an operator call's analysis on top of its settled charge — the scorecard's
-- tokens, and the second recognition stream that transcribes the operator's side.
CREATE TABLE call_billing (
    id                   BIGSERIAL PRIMARY KEY,
    -- Null while the hold is open: the money is put aside before Asterisk gives the
    -- call a channel, so the attempt row it will be charged to does not exist yet.
    call_attempt_id      BIGINT      UNIQUE REFERENCES call_attempt(id) ON DELETE CASCADE,
    -- What the hold was taken for. A hold and the call that spends it are paired through
    -- this rather than carried along the call itself, which would mean threading an
    -- amount through the queue, the originate and the media session to no other purpose.
    target_id            BIGINT      REFERENCES campaign_target(id) ON DELETE SET NULL,
    company_id           BIGINT      NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    -- RESERVED -> SETTLED (the call was charged) or RELEASED (it never connected).
    status               VARCHAR(16) NOT NULL DEFAULT 'RESERVED',
    rate_version         VARCHAR(32) NOT NULL,
    reserved_uzs         BIGINT      NOT NULL DEFAULT 0,
    -- Measured usage, kept so a disputed charge can be recomputed from the same inputs.
    duration_sec         INT         NOT NULL DEFAULT 0,
    prompt_tokens        BIGINT      NOT NULL DEFAULT 0,
    completion_tokens    BIGINT      NOT NULL DEFAULT 0,
    cached_tokens        BIGINT      NOT NULL DEFAULT 0,
    tts_chars            BIGINT      NOT NULL DEFAULT 0,
    realtime_seconds     INT         NOT NULL DEFAULT 0,
    summary_prompt_tokens      BIGINT NOT NULL DEFAULT 0,
    summary_completion_tokens  BIGINT NOT NULL DEFAULT 0,
    summary_cached_tokens      BIGINT NOT NULL DEFAULT 0,
    analysis_prompt_tokens     BIGINT NOT NULL DEFAULT 0,
    analysis_completion_tokens BIGINT NOT NULL DEFAULT 0,
    analysis_cached_tokens     BIGINT NOT NULL DEFAULT 0,
    analysis_stt_seconds       INT    NOT NULL DEFAULT 0,
    -- The same usage priced, split the way an invoice line is read.
    llm_cost_uzs         BIGINT      NOT NULL DEFAULT 0,
    stt_cost_uzs         BIGINT      NOT NULL DEFAULT 0,
    tts_cost_uzs         BIGINT      NOT NULL DEFAULT 0,
    telephony_cost_uzs   BIGINT      NOT NULL DEFAULT 0,
    realtime_cost_uzs    BIGINT      NOT NULL DEFAULT 0,
    platform_fee_uzs     BIGINT      NOT NULL DEFAULT 0,
    total_uzs            BIGINT      NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    settled_at           TIMESTAMPTZ,
    CONSTRAINT ck_call_billing_status CHECK (status IN ('RESERVED', 'SETTLED', 'RELEASED'))
);
CREATE INDEX idx_call_billing_company_created ON call_billing(company_id, created_at DESC);
CREATE INDEX idx_call_billing_open_hold ON call_billing(company_id, target_id, created_at)
    WHERE status = 'RESERVED';
-- idx_call_billing_open_hold is partial and leads with company_id; ON DELETE SET NULL from
-- campaign_target needs a plain one.
CREATE INDEX idx_call_billing_target ON call_billing(target_id);

-- Every movement of money, append-only. Application code never updates or deletes a row
-- here: a balance that can be edited in place cannot be audited, and the before/after
-- columns are what lets a disagreement about a balance be settled by reading the table.
CREATE TABLE billing_ledger (
    id                  BIGSERIAL PRIMARY KEY,
    company_id          BIGINT      NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    -- TOPUP, RESERVE, RELEASE, CALL_CHARGE, ADJUSTMENT (LedgerEntryType).
    entry_type          VARCHAR(24) NOT NULL,
    -- Signed: what this entry did to the balance. A reservation does not move the
    -- balance, so it carries 0 and moves reserved_uzs instead.
    amount_uzs          BIGINT      NOT NULL,
    balance_before_uzs  BIGINT      NOT NULL,
    balance_after_uzs   BIGINT      NOT NULL,
    -- What caused it: 'call_attempt' + its id, 'payment_topup' + its payment id, etc.
    reference_type      VARCHAR(32),
    reference_id        VARCHAR(64),
    -- Makes replay safe: a retried settlement or a redelivered payment callback writes
    -- the same key and is rejected by the index rather than charged twice.
    idempotency_key     VARCHAR(128) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_billing_ledger_idempotency UNIQUE (company_id, idempotency_key),
    CONSTRAINT ck_billing_ledger_entry_type
        CHECK (entry_type IN ('TOPUP', 'RESERVE', 'RELEASE', 'CALL_CHARGE', 'ADJUSTMENT'))
);
CREATE INDEX idx_billing_ledger_company_created ON billing_ledger(company_id, created_at DESC);
CREATE INDEX idx_billing_ledger_reference ON billing_ledger(reference_type, reference_id);
