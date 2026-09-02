-- Baseline schema for the AI voice agent (PROJECT.md §6). Consolidates every migration
-- to date into one starting point — safe because no release has shipped yet, so there
-- is no production history to preserve step by step. If you have a local/dev database
-- that already ran the old incremental history, drop it and let this recreate the
-- schema from scratch (see docs/RUN.md). Seed data lives in R__seed_data.sql.

-- Company/tenant (ROADMAP Bosqich B). Every other table below is scoped to one.
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

-- Company operational settings (ROADMAP B.1/B.3) — working hours window, timezone, and default language.
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

-- TTS voice catalog (PROJECT.md §2.5). Seeded in R__seed_data.sql.
CREATE TABLE tts_voice (
    id       VARCHAR(64)  PRIMARY KEY,   -- stable id stored on campaign.tts_voice, e.g. 'nigora'
    provider VARCHAR(50)  NOT NULL,      -- yandex | aisha | gemini-live
    language VARCHAR(10)  NOT NULL,      -- BCP-47, e.g. uz-UZ
    name     VARCHAR(100) NOT NULL,      -- provider-side voice name sent with synthesis
    label    VARCHAR(255) NOT NULL,      -- human-readable name for the campaign form
    role     VARCHAR(32)                 -- voice mood or role nuance (optional)
);

-- Scenario storage (ROADMAP A.4) — holds both built-in templates (is_builtin = true,
-- company_id = null) and company-owned custom scenarios (company_id != null).
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

-- Inbound routes (Virtual ATS & Inbound Routing — ROADMAP C.1).
-- Maps incoming phone numbers (DIDs) to an AI scenario, IVR, or operator queue.
CREATE TABLE inbound_route (
    id                       BIGSERIAL PRIMARY KEY,
    company_id               BIGINT       NOT NULL REFERENCES company(id),
    did_number               VARCHAR(32)  NOT NULL,
    scenario_id              BIGINT       REFERENCES scenario(id),
    route_type               VARCHAR(32)  NOT NULL DEFAULT 'SCENARIO', -- SCENARIO, QUEUE, IVR, DIRECT_USER
    target_destination       VARCHAR(128),
    queue_strategy           VARCHAR(32)  NOT NULL DEFAULT 'RING_ALL',
    ring_timeout_sec         INT          NOT NULL DEFAULT 20,
    failover_action          VARCHAR(32)  NOT NULL DEFAULT 'SCENARIO',
    failover_destination     VARCHAR(128),
    after_hours_action       VARCHAR(32)  NOT NULL DEFAULT 'PLAY_MESSAGE_AND_HANGUP',
    after_hours_destination  VARCHAR(128),
    ivr_menu_config          TEXT,
    language                 VARCHAR(10)  NOT NULL DEFAULT 'uz-UZ',
    business_hours_start     TIME,
    business_hours_end       TIME,
    fallback_message         TEXT,
    enabled                  BOOLEAN      NOT NULL DEFAULT true,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX idx_inbound_route_did ON inbound_route(did_number) WHERE enabled;
CREATE INDEX idx_inbound_route_company ON inbound_route(company_id);

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

-- Users (ROADMAP E.1) — authentication and authorization for operators, admins, viewers, superadmins.
CREATE TABLE app_user (
    id                BIGSERIAL PRIMARY KEY,
    company_id        BIGINT       NOT NULL REFERENCES company(id),
    name              VARCHAR(255) NOT NULL,
    email             VARCHAR(255) NOT NULL,
    username          VARCHAR(100) NOT NULL,
    password_hash     VARCHAR(255),
    role              VARCHAR(20)  NOT NULL,  -- ADMIN, OPERATOR, VIEWER, SUPERADMIN
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
CREATE INDEX idx_app_user_invite_token ON app_user(invite_token_hash);
CREATE INDEX idx_app_user_reset_token ON app_user(reset_token_hash);

-- Campaign (PROJECT.md §3, §10) — batch outbound dialing jobs and their operational rules.
CREATE TABLE campaign (
    id                     BIGSERIAL PRIMARY KEY,
    company_id             BIGINT       NOT NULL REFERENCES company(id),
    name                   VARCHAR(255) NOT NULL,
    type                   VARCHAR(50)  NOT NULL,   -- DEBT_COLLECTION, SURVEY, NOTIFICATION, etc.
    status                 VARCHAR(50)  NOT NULL,   -- DRAFT, ACTIVE, PAUSED, COMPLETED, ARCHIVED
    goal_prompt            TEXT         NOT NULL,
    script_config          JSONB        NOT NULL,
    default_language       VARCHAR(10)  NOT NULL DEFAULT 'uz-UZ',
    dial_window_start      TIME         NOT NULL DEFAULT '07:00',
    dial_window_end        TIME         NOT NULL DEFAULT '23:00',
    max_attempts           INT          NOT NULL DEFAULT 3,
    retry_interval_minutes INT          NOT NULL DEFAULT 0,
    max_concurrent_calls   INT          NOT NULL DEFAULT 20,
    tts_voice              VARCHAR(64)  REFERENCES tts_voice(id),
    daily_call_cap         INT          NOT NULL DEFAULT 0,
    scenario_id            BIGINT       NOT NULL REFERENCES scenario(id),
    disclosure_enabled     BOOLEAN      NOT NULL DEFAULT true,
    recurrence_type        VARCHAR(20)  NOT NULL DEFAULT 'ONCE', -- ONCE, DAILY, WEEKLY, MONTHLY, CRON
    recurring_day_of_month INTEGER,
    cron_expression        VARCHAR(100),
    auto_reset_targets     BOOLEAN      NOT NULL DEFAULT false,
    last_run_at            TIMESTAMPTZ,
    ambient_sound          VARCHAR(30)  NOT NULL DEFAULT 'OFF', -- OFF, OFFICE, CALL_CENTER, NATURAL_LINE, CAFE
    mid_call_sms_enabled   BOOLEAN      NOT NULL DEFAULT false,
    mid_call_sms_template  VARCHAR(500),
    voicemail_action       VARCHAR(30)  NOT NULL DEFAULT 'HANGUP', -- HANGUP, LEAVE_MESSAGE, IGNORE
    voicemail_message      VARCHAR(500),
    dtmf_input_enabled     BOOLEAN      NOT NULL DEFAULT false,
    emotion_adaptive_voice BOOLEAN      NOT NULL DEFAULT true,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by             BIGINT
);
CREATE INDEX idx_campaign_company ON campaign(company_id);
CREATE INDEX idx_campaign_scenario ON campaign(scenario_id);

-- Campaign allowed dial days (e.g., MONDAY, TUESDAY, etc.)
CREATE TABLE campaign_dial_day (
    campaign_id BIGINT      NOT NULL REFERENCES campaign(id) ON DELETE CASCADE,
    day         VARCHAR(20) NOT NULL,
    PRIMARY KEY (campaign_id, day)
);

-- Per-language voice override for campaign (e.g., uz-UZ -> 'nigora', ru-RU -> 'alena')
CREATE TABLE campaign_language_voice (
    campaign_id BIGINT      NOT NULL REFERENCES campaign(id) ON DELETE CASCADE,
    language    VARCHAR(10) NOT NULL,          -- BCP-47, e.g. uz-UZ
    tts_voice   VARCHAR(64) NOT NULL REFERENCES tts_voice(id),
    PRIMARY KEY (campaign_id, language)
);

-- Campaign SIP trunks mapping
CREATE TABLE campaign_sip_trunk (
    campaign_id  BIGINT NOT NULL REFERENCES campaign(id) ON DELETE CASCADE,
    sip_trunk_id BIGINT NOT NULL REFERENCES sip_trunk(id) ON DELETE CASCADE,
    PRIMARY KEY (campaign_id, sip_trunk_id)
);
CREATE INDEX idx_campaign_sip_trunk_campaign ON campaign_sip_trunk(campaign_id);

-- Campaign target (client) — individual row in a campaign dial queue.
CREATE TABLE campaign_target (
    id              BIGSERIAL PRIMARY KEY,
    company_id      BIGINT       NOT NULL REFERENCES company(id),
    campaign_id     BIGINT       NOT NULL REFERENCES campaign(id) ON DELETE CASCADE,
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

-- Call attempt (PROJECT.md §7) — tracks Asterisk channel lifecycle and telephony disposition.
CREATE TABLE call_attempt (
    id                 BIGSERIAL PRIMARY KEY,
    company_id         BIGINT       NOT NULL REFERENCES company(id),
    target_id          BIGINT       NOT NULL REFERENCES campaign_target(id),
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

-- Do Not Call List (ROADMAP §12.3) — global opt-out list per company.
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

-- Audit Log — system-wide audit trail for compliance and activity tracking.
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

-- Contacts (ROADMAP §12) — directory of known customers/contacts.
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

-- Notifications (ROADMAP §13) — in-app notifications.
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

-- User Sessions (API-REQUIREMENTS §15) — tracks active tokens and refresh sessions per browser/device.
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

-- Personal channel x event matrix (API-REQUIREMENTS §15, UI-DESIGN §8.3 "Bildirishnomalar" tab).
CREATE TABLE personal_notification_matrix (
    id         BIGSERIAL PRIMARY KEY,
    company_id BIGINT      NOT NULL REFERENCES company(id),
    user_id    BIGINT      NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    type       VARCHAR(30) NOT NULL, -- notification.enums.NotificationType
    channel    VARCHAR(20) NOT NULL, -- notification.enums.NotificationChannelType
    enabled    BOOLEAN     NOT NULL DEFAULT false
);
CREATE UNIQUE INDEX idx_personal_notification_matrix_unique ON personal_notification_matrix(user_id, type, channel);

-- Operator work schedule (API-REQUIREMENTS §15, UI-DESIGN §8.3 "Ish jadvali" tab) — weekly hours.
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

-- Per-company AI model overrides (§11 "GET/PUT /api/settings/ai-model").
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

-- Per-company TTS tuning overrides (§11 "GET/PUT /api/settings/voice").
CREATE TABLE voice_settings (
    id          BIGSERIAL PRIMARY KEY,
    company_id  BIGINT NOT NULL UNIQUE REFERENCES company(id),
    speed       DOUBLE PRECISION,
    pitch       DOUBLE PRECISION,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Engine Config (CASCADE vs REALTIME STT/TTS engine selection per tenant).
CREATE TABLE engine_config (
    id                BIGSERIAL PRIMARY KEY,
    company_id        BIGINT NOT NULL UNIQUE REFERENCES company(id),
    mode              VARCHAR(20) NOT NULL DEFAULT 'CASCADE', -- CASCADE | REALTIME
    stt_provider      VARCHAR(50),
    tts_provider      VARCHAR(50),
    realtime_provider VARCHAR(50),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

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
