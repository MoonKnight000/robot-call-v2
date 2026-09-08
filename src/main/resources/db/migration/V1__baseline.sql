-- Baseline schema for the AI voice agent (PROJECT.md §6). This is the whole schema in one
-- file: nothing has shipped yet, so there is no production history worth replaying step by
-- step, and a fresh database is created from this file plus R__seed_data.sql alone. Every
-- migration written so far is folded in here rather than kept as its own step — a column
-- added and then re-defaulted twice is noise once no database anywhere replays it.
--
-- Tables are declared in dependency order so every REFERENCES points at something that
-- already exists; the single exception is company.logo_file_id, which is added by ALTER
-- below because company and stored_file reference each other.

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

-- Company operational settings — working hours window, timezone, and default language.
CREATE TABLE company_config (
    id                 BIGSERIAL PRIMARY KEY,
    company_id         BIGINT      NOT NULL REFERENCES company(id),
    dial_window_start  TIME        NOT NULL,
    dial_window_end    TIME        NOT NULL,
    timezone           VARCHAR(64) NOT NULL,
    default_language   VARCHAR(10) NOT NULL,
    disclosure_text    VARCHAR(500),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
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
CREATE TABLE tts_voice (
    id       VARCHAR(64)  PRIMARY KEY,   -- stable id stored on ai_agent.tts_voice, e.g. 'nigora'
    provider VARCHAR(50)  NOT NULL,      -- yandex | aisha | gemini | gemini-live
    language VARCHAR(10)  NOT NULL,      -- BCP-47, e.g. uz-UZ
    name     VARCHAR(100) NOT NULL,      -- provider-side voice name sent with synthesis
    label    VARCHAR(255) NOT NULL,      -- human-readable name for the agent form
    role     VARCHAR(32)                 -- voice mood or role nuance (optional)
);

-- LLM model catalog. What ai_model_config.model and ai_agent.llm_model may be set to,
-- seeded in R__seed_data.sql — a table rather than a YAML list so the agent form asks the
-- same source the save-time check does, and a wrong model id is a 400 instead of a call
-- that fails when it is already ringing.
--
-- mode splits two sets that never overlap: a CASCADE model reads text and writes text,
-- a REALTIME one hears and speaks, and neither provider recognises the other's ids.
CREATE TABLE ai_model (
    id       VARCHAR(120) PRIMARY KEY,   -- model id sent to the provider, e.g. 'gemini-3.8-flash'
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

-- ---------------------------------------------------------------------------
-- Telephony endpoints
-- ---------------------------------------------------------------------------

-- SIP Trunks — connects Asterisk to telecom providers or virtual PBX endpoints.
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
    code        VARCHAR(50),             -- DEVELOPER, ADMIN, OPERATOR, VIEWER, SUPERADMIN; NULL for a company's own role
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
    temperature            DOUBLE PRECISION,      -- NULL = the company's
    max_output_tokens      INT,                   -- NULL = the company's

    -- How it sounds.
    tts_voice              VARCHAR(64)  REFERENCES tts_voice(id),  -- NULL = the configured provider's own default voice
    voice_speed            DOUBLE PRECISION DEFAULT 1.0,
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
    interruption_sensitivity VARCHAR(32) NOT NULL DEFAULT 'MEDIUM', -- LOW, MEDIUM, HIGH
    endpointing_delay_ms     INT         NOT NULL DEFAULT 700,
    transfer_phone_number    VARCHAR(32),          -- NULL = the platform's operator endpoint
    transfer_message         TEXT,
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

-- ---------------------------------------------------------------------------
-- Agent tool library and website widgets
-- ---------------------------------------------------------------------------

-- An HTTP endpoint the model may call mid-conversation, defined once per company and
-- bound to as many agents as want it. The four *_params columns are the OpenAPI-ish
-- description of the request; dynamic_variables names what the call may substitute in.
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
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now()
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

-- ---------------------------------------------------------------------------
-- Inbound routing
-- ---------------------------------------------------------------------------

-- Maps incoming phone numbers (DIDs) to an AI agent, IVR, or operator queue.
CREATE TABLE inbound_route (
    id                       BIGSERIAL PRIMARY KEY,
    company_id               BIGINT       NOT NULL REFERENCES company(id),
    did_number               VARCHAR(32)  NOT NULL,
    ai_agent_id              BIGINT       REFERENCES ai_agent(id),
    route_type               VARCHAR(32)  NOT NULL DEFAULT 'SCENARIO', -- SCENARIO, QUEUE, IVR, DIRECT_USER
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
    enabled                  BOOLEAN      NOT NULL DEFAULT true,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX idx_inbound_route_did ON inbound_route(did_number) WHERE enabled;
CREATE INDEX idx_inbound_route_company ON inbound_route(company_id);
CREATE INDEX idx_inbound_route_ai_agent ON inbound_route(ai_agent_id);

-- ---------------------------------------------------------------------------
-- Campaigns — who to call and when
-- ---------------------------------------------------------------------------

CREATE TABLE campaign (
    id                     BIGSERIAL PRIMARY KEY,
    company_id             BIGINT       NOT NULL REFERENCES company(id),
    ai_agent_id            BIGINT       NOT NULL REFERENCES ai_agent(id),
    name                   VARCHAR(255) NOT NULL,
    type                   VARCHAR(50)  NOT NULL,   -- DEBT_COLLECTION, SURVEY, NOTIFICATION, etc.
    status                 VARCHAR(50)  NOT NULL,   -- DRAFT, ACTIVE, PAUSED, COMPLETED, ARCHIVED
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
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by             BIGINT
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
CREATE TABLE campaign_variant (
    id              BIGSERIAL PRIMARY KEY,
    campaign_id     BIGINT       NOT NULL REFERENCES campaign(id) ON DELETE CASCADE,
    company_id      BIGINT       NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL,
    ai_agent_id     BIGINT       REFERENCES ai_agent(id),
    prompt_override TEXT,
    tts_voice_id    VARCHAR(64)  REFERENCES tts_voice(id),
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
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_target_dial ON campaign_target(campaign_id, status, next_attempt_at);
CREATE INDEX idx_campaign_target_company ON campaign_target(company_id);

-- Where a campaign's call list comes from, when it does not come from a CSV somebody
-- uploads by hand. A DAILY campaign with a source configured fetches the list from the
-- company's own API on each recurrence sweep and dials whatever it answers with. One
-- source per campaign, so the shape stays a settings page and not a pipeline builder.
CREATE TABLE campaign_target_source (
    campaign_id        BIGINT PRIMARY KEY REFERENCES campaign(id) ON DELETE CASCADE,
    url                VARCHAR(1000) NOT NULL,
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
    last_sync_at       TIMESTAMPTZ,
    last_sync_added    INT,
    last_sync_error    VARCHAR(1000),
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Calls
-- ---------------------------------------------------------------------------

-- Call attempt (PROJECT.md §7) — tracks Asterisk channel lifecycle and telephony disposition.
CREATE TABLE call_attempt (
    id                 BIGSERIAL PRIMARY KEY,
    company_id         BIGINT       NOT NULL REFERENCES company(id),
    target_id          BIGINT       NOT NULL REFERENCES campaign_target(id),
    variant_id         BIGINT       REFERENCES campaign_variant(id) ON DELETE SET NULL,
    phone              VARCHAR(20),
    sip_call_id        VARCHAR(255),
    asterisk_channel   VARCHAR(255),
    language           VARCHAR(10)  NOT NULL,
    started_at         TIMESTAMPTZ,
    answered_at        TIMESTAMPTZ,
    ended_at           TIMESTAMPTZ,
    duration_sec       INT,
    disposition        VARCHAR(50),
    hangup_cause       VARCHAR(50),
    recording_file_id  BIGINT       REFERENCES stored_file(id) ON DELETE SET NULL,
    error_message      TEXT,
    finalize_attempts  INT          NOT NULL DEFAULT 0,
    inbound_route_id   BIGINT       REFERENCES inbound_route(id),
    operator_user_id   BIGINT       REFERENCES app_user(id),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_attempt_target ON call_attempt(target_id);
CREATE INDEX idx_attempt_ended ON call_attempt(ended_at) WHERE ended_at IS NOT NULL;
CREATE INDEX idx_call_attempt_company ON call_attempt(company_id);
CREATE INDEX idx_call_attempt_inbound_route ON call_attempt(inbound_route_id);

-- Call technical details (latencies, token consumption, provider info).
CREATE TABLE call_technical (
    call_id             BIGINT PRIMARY KEY REFERENCES call_attempt(id) ON DELETE CASCADE,
    channel_name        VARCHAR(255),
    trunk               VARCHAR(100),
    amd_result          VARCHAR(20),
    stt_provider        VARCHAR(50),
    tts_provider        VARCHAR(50),
    tts_voice           VARCHAR(50),
    llm_model           VARCHAR(100),
    prompt_tokens       INT,
    completion_tokens   INT,
    cached_tokens       INT,
    turn_count          INT,
    avg_turn_latency_ms INT,
    max_turn_latency_ms INT,
    avg_llm_latency_ms  INT,
    max_llm_latency_ms  INT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
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
    crm_note_id     BIGINT,
    crm_attempts    INT          NOT NULL DEFAULT 0,
    crm_last_error  TEXT,
    outcome         JSONB        NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_result_crm_pending ON call_result(crm_attempts) WHERE crm_note_id IS NULL;

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
    source     VARCHAR(20)  NOT NULL DEFAULT 'CALL',   -- CALL, MANUAL, IMPORT
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
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_knowledge_source_company ON knowledge_source(company_id);
CREATE INDEX idx_knowledge_source_agent ON knowledge_source(agent_id);
CREATE INDEX idx_knowledge_source_status ON knowledge_source(status);
CREATE INDEX idx_knowledge_source_file ON knowledge_source(stored_file_id);

-- The chunks a call actually searches.
--
-- The embedding is a BYTEA of big-endian float32s rather than a pgvector column: one
-- company's knowledge base is thousands of chunks, not millions, so the cosine runs in
-- Java over a per-agent cache and the database needs no extension (and therefore no
-- change of Postgres image).
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
-- Sessions, schedules, per-user UI state
-- ---------------------------------------------------------------------------

-- Tracks active tokens and refresh sessions per browser/device.
CREATE TABLE user_session (
    id                 BIGSERIAL PRIMARY KEY,
    company_id         BIGINT      NOT NULL REFERENCES company(id),
    user_id            BIGINT      NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    refresh_token_hash VARCHAR(64) NOT NULL,
    device             VARCHAR(255),
    ip_address         VARCHAR(64),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_activity_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at         TIMESTAMPTZ NOT NULL,
    revoked_at         TIMESTAMPTZ
);
CREATE UNIQUE INDEX idx_user_session_refresh_token_hash ON user_session(refresh_token_hash);
CREATE INDEX idx_user_session_user ON user_session(user_id);

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
-- Per-company settings (§11). A missing row means "use the config/*.yml default", so none
-- of these is seeded — writing a row would pin the value in the database and make the YAML
-- default unreachable.
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

-- Per-company TTS tuning overrides (§11 "GET/PUT /api/settings/voice").
CREATE TABLE voice_settings (
    id          BIGSERIAL PRIMARY KEY,
    company_id  BIGINT NOT NULL UNIQUE REFERENCES company(id),
    speed       DOUBLE PRECISION,
    pitch       DOUBLE PRECISION,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Per-company Uysot CRM OAuth connection (§11 "GET/PUT /api/settings/integrations").
CREATE TABLE crm_integration (
    id                BIGSERIAL PRIMARY KEY,
    company_id        BIGINT      NOT NULL UNIQUE REFERENCES company(id),
    provider          VARCHAR(20) NOT NULL DEFAULT 'UYSOT',
    app_name          VARCHAR(255),
    grants_json       TEXT,
    access_token_enc  TEXT,
    refresh_token_enc TEXT,
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
-- Billing & invoicing. Every row here is created on demand by BillingService
-- (CompanyBilling.defaultFor / BillingUsage.defaultFor), so nothing is seeded.
-- ---------------------------------------------------------------------------

CREATE TABLE company_billing (
    id                 BIGSERIAL PRIMARY KEY,
    company_id         BIGINT NOT NULL UNIQUE REFERENCES company(id) ON DELETE CASCADE,
    plan_code          VARCHAR(64) NOT NULL DEFAULT 'PRO_MONTHLY',
    plan_name          VARCHAR(128) NOT NULL DEFAULT 'Professional (Pro)',
    balance_uzs        BIGINT NOT NULL DEFAULT 0,
    auto_recharge      BOOLEAN NOT NULL DEFAULT FALSE,
    next_billing_date  TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_company_billing_company ON company_billing(company_id);

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
CREATE INDEX idx_billing_usage_company_period ON billing_usage(company_id, billing_period);

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
    status         VARCHAR(32) NOT NULL DEFAULT 'PENDING', -- PENDING, COMPLETED, FAILED, CANCELLED
    checkout_url   VARCHAR(1000),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    paid_at        TIMESTAMPTZ
);
CREATE INDEX idx_payment_topup_company ON payment_topup(company_id, created_at DESC);
