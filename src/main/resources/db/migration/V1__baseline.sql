-- Baseline schema for the AI voice agent (PROJECT.md §6). Consolidates every migration
-- to date into one starting point — safe because no release has shipped yet, so there
-- is no production history to preserve step by step. If you have a local/dev database
-- that already ran the old incremental history, drop it and let this recreate the
-- schema from scratch (see docs/RUN.md). Seed data lives in R__seed_data.sql.

-- Company/tenant (ROADMAP Bosqich B). Every other table below is scoped to one.
CREATE TABLE company (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    status     VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, SUSPENDED
    -- Identity tab (§11, API-REQUIREMENTS §11), filled in later from the settings page.
    logo_url   VARCHAR(500),
    address    VARCHAR(500),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Company operational settings (ROADMAP B.1/B.3), split out of company's identity
-- row (name/status): dial_window_start/end is a STRICT ceiling DialerService enforces
-- on top of every campaign's own window; the language list is ORDERED, index 0 being
-- the default, and a campaign/inbound route may only declare a language from it
-- (CompanyConfigService.resolveLanguage).
CREATE TABLE company_config (
    id                 BIGSERIAL PRIMARY KEY,
    company_id         BIGINT      NOT NULL REFERENCES company(id),
    dial_window_start  TIME        NOT NULL,
    dial_window_end    TIME        NOT NULL,
    timezone           VARCHAR(64) NOT NULL,
    -- Explicit default language (backend-uchun-talablar.md §13) — previously only
    -- implied by company_config_language's ord=0 row, a fragile convention since it
    -- depended on insertion order rather than a real column.
    default_language   VARCHAR(10) NOT NULL,
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

-- TTS voice catalog (PROJECT.md §2.5) instead of voice-agent.tts.catalog config — same
-- reasoning as `scenario`: selectable data an operator picks from belongs in the DB,
-- not in a YAML file redeployed to change it.
CREATE TABLE tts_voice (
    id       VARCHAR(64)  PRIMARY KEY,   -- stable id stored on campaign.tts_voice, e.g. 'nigora'
    provider VARCHAR(50)  NOT NULL,      -- yandex | google
    language VARCHAR(10)  NOT NULL,      -- BCP-47, e.g. uz-UZ
    name     VARCHAR(100) NOT NULL,      -- provider-side voice name sent with synthesis
    label    VARCHAR(255) NOT NULL       -- human-readable name for the campaign form
);

-- Scenario storage (ROADMAP A.4). company_id is nullable: NULL means "global
-- built-in template", visible to every company (the seed templates in R__seed_data.sql).
CREATE TABLE scenario (
    id           BIGSERIAL PRIMARY KEY,
    company_id   BIGINT       REFERENCES company(id),
    -- Stable slug grouping every version of the same scenario, e.g. 'debt-collection'
    -- or a generated slug for a custom one.
    scenario_key VARCHAR(64)  NOT NULL,
    version      INT          NOT NULL DEFAULT 1,
    name         VARCHAR(255) NOT NULL,
    description  TEXT,
    -- Built-in templates are read-only (cloned, never edited in place).
    is_builtin   BOOLEAN      NOT NULL DEFAULT false,
    -- The version offered when a *new* campaign attaches to scenario_key. A campaign
    -- already bound to an older row keeps it regardless of this flag.
    is_active    BOOLEAN      NOT NULL DEFAULT true,
    -- ScenarioDefinition: stages, factSchema, tools, outcomeSchema, rolePrompt,
    -- guardrails, disclosureText.
    definition   JSONB        NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by   BIGINT
);
-- Exactly one active version per scenario_key (ROADMAP A.4 versioning).
CREATE UNIQUE INDEX idx_scenario_active_key ON scenario(scenario_key) WHERE is_active;
CREATE INDEX idx_scenario_key_version ON scenario(scenario_key, version DESC);
CREATE INDEX idx_scenario_company ON scenario(company_id);

-- ROADMAP Bosqich C — Inbound qo'ng'iroqlar.
-- C.1: DID -> company/scenario/til routing. A DID is a real phone number -- at most one
-- enabled route per number, mirroring scenario's idx_scenario_active_key uniqueness.
CREATE TABLE inbound_route (
    id                    BIGSERIAL PRIMARY KEY,
    company_id            BIGINT      NOT NULL REFERENCES company(id),
    did_number            VARCHAR(32) NOT NULL,
    scenario_id           BIGINT      NOT NULL REFERENCES scenario(id),
    language              VARCHAR(10) NOT NULL DEFAULT 'uz-UZ',
    -- Null on either side means "no restriction, always open".
    business_hours_start  TIME,
    business_hours_end    TIME,
    -- Reserved for a future spoken decline outside business hours / on no match; not
    -- yet wired to actually play (AriService currently just hangs up in both cases).
    fallback_message      TEXT,
    enabled               BOOLEAN     NOT NULL DEFAULT true,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX idx_inbound_route_did ON inbound_route(did_number) WHERE enabled;
CREATE INDEX idx_inbound_route_company ON inbound_route(company_id);

-- ROADMAP B.3: a company may register several outbound PJSIP trunks, one of which is
-- its default. Two modes (report #7): "managed" (host/sip_username/sip_password_enc
-- set, pjsip_endpoint app-generated — PjsipConfigWriter/AmiClient actually register it
-- with the provider) or "manual" (those three NULL, pjsip_endpoint names an endpoint an
-- operator already hand-configured in pjsip.conf — the original ROADMAP B.3 shape).
CREATE TABLE sip_trunk (
    id               BIGSERIAL PRIMARY KEY,
    company_id       BIGINT       NOT NULL REFERENCES company(id),
    name             VARCHAR(255) NOT NULL,
    -- The PJSIP endpoint name, used as PJSIP/<number>@<pjsip_endpoint> — app-generated
    -- (trunk_<company_id>_<id>) for a managed trunk, user-supplied for a manual one.
    pjsip_endpoint   VARCHAR(128) NOT NULL,
    -- NULL falls back to the company's own caller_id, then voice-agent.asterisk.caller-id.
    caller_id        VARCHAR(20),
    -- Managed mode only — the provider's own host/domain and credentials.
    host             VARCHAR(255),
    port             INT          NOT NULL DEFAULT 5060,
    sip_username     VARCHAR(255),
    sip_password_enc TEXT,
    transport        VARCHAR(10)  NOT NULL DEFAULT 'UDP', -- UDP, TCP, TLS (siptrunk.enums.SipTrunkTransport)
    is_default       BOOLEAN      NOT NULL DEFAULT false,
    enabled          BOOLEAN      NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_sip_trunk_company ON sip_trunk(company_id);
-- At most one default trunk per company (mirrors idx_scenario_active_key).
CREATE UNIQUE INDEX idx_sip_trunk_default ON sip_trunk(company_id) WHERE is_default;

-- Real user accounts + login (ROADMAP E.1) — replaces the old two shared X-Api-Key
-- secrets with per-person identity and per-request company resolution for the JWT
-- login path; X-Api-Key callers are untouched, still resolved through the single
-- hardcoded default company.
CREATE TABLE app_user (
    id                BIGSERIAL PRIMARY KEY,
    company_id        BIGINT       NOT NULL REFERENCES company(id),
    name              VARCHAR(255) NOT NULL,
    email             VARCHAR(255) NOT NULL,
    -- Login identifier, separate from email (ROADMAP E.1 follow-up).
    username          VARCHAR(100) NOT NULL,
    password_hash     VARCHAR(255),           -- NULL while INVITED (no password set yet)
    role              VARCHAR(20)  NOT NULL,  -- ADMIN, OPERATOR, VIEWER, SUPERADMIN (platform staff, report #3)
    status            VARCHAR(20)  NOT NULL DEFAULT 'INVITED', -- INVITED, ACTIVE, BLOCKED
    -- One-time activation link (no SMTP integration yet, §API-REQUIREMENTS 12):
    -- the raw token is returned once from POST /api/users/invite and never stored;
    -- only its SHA-256 hex digest lives here, looked up on POST /api/auth/activate.
    invite_token_hash VARCHAR(64),
    invite_expires_at TIMESTAMPTZ,
    -- Same one-time-token-hash pattern as invite_token_hash, for "Parolni unutdingizmi?"
    -- (backend-uchun-talablar.md §9) — kept in separate columns rather than reused: an
    -- invited-but-not-yet-activated user and a password-reset request are independent
    -- flows, and sharing one slot would let one silently invalidate the other.
    reset_token_hash  VARCHAR(64),
    reset_expires_at  TIMESTAMPTZ,
    -- "Umumiy" profil tab (UI-DESIGN §8.3, API-REQUIREMENTS §15).
    phone             VARCHAR(32),
    position          VARCHAR(120),
    avatar_url        VARCHAR(500),
    -- The PJSIP endpoint name (e.g. "operator_asilbek" in "PJSIP/operator_asilbek-00000abc")
    -- that identifies this user's own softphone, set via PUT /api/profile. Null = this
    -- user never takes transferred calls.
    sip_extension     VARCHAR(120),
    -- "Ustunlar ⚙" for the calls table specifically (UI-DESIGN §10.4, API-REQUIREMENTS §4).
    -- Comma-separated column keys (frontend-owned namespace); NULL means "no preference
    -- saved yet — use the default set". Other tables use user_table_config below.
    call_columns      VARCHAR(500),
    last_login_at     TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- Email is the login identifier — unique across the whole platform, not per
-- company, so login doesn't need a company to be selected first.
CREATE UNIQUE INDEX idx_app_user_email ON app_user(email);
CREATE UNIQUE INDEX idx_app_user_username ON app_user(username);
CREATE INDEX idx_app_user_company ON app_user(company_id);
CREATE INDEX idx_app_user_invite_token ON app_user(invite_token_hash);
CREATE INDEX idx_app_user_reset_token ON app_user(reset_token_hash);

-- Campaign
CREATE TABLE campaign (
    id                   BIGSERIAL PRIMARY KEY,
    company_id           BIGINT       NOT NULL REFERENCES company(id),
    name                 VARCHAR(255) NOT NULL,
    type                 VARCHAR(50)  NOT NULL,   -- DEBT_COLLECTION, SURVEY, ...
    status               VARCHAR(50)  NOT NULL,   -- DRAFT, ACTIVE, PAUSED, COMPLETED
    goal_prompt          TEXT         NOT NULL,   -- part of the LLM system prompt
    script_config        JSONB        NOT NULL,   -- FSM settings, questions
    default_language     VARCHAR(10)  NOT NULL DEFAULT 'uz-UZ',
    dial_window_start    TIME         NOT NULL DEFAULT '07:00',
    dial_window_end      TIME         NOT NULL DEFAULT '23:00',
    max_attempts         INT          NOT NULL DEFAULT 3,
    retry_interval_hours INT          NOT NULL DEFAULT 24,
    max_concurrent_calls INT          NOT NULL DEFAULT 20,
    -- Catalog id from tts_voice; NULL keeps the configured provider/voice routing. A
    -- campaign whose voice never resolved to a real row would silently fall back to
    -- default routing (TtsRouter.resolve) forever — the FK makes that typo impossible.
    tts_voice            VARCHAR(64)  REFERENCES tts_voice(id),
    -- Cost cap (§13.2): dialer stops dispatching this campaign once it has made this
    -- many attempts today. 0 = unlimited.
    daily_call_cap       INT          NOT NULL DEFAULT 0,
    -- Binds to a specific scenario row (not a mutable key): editing a scenario later
    -- never changes what a running campaign does (ROADMAP A.3).
    scenario_id          BIGINT       NOT NULL REFERENCES scenario(id),
    -- Spoken disclosure (§11.1) toggle before the model's first turn. Defaults to true
    -- so existing campaigns keep today's behaviour.
    disclosure_enabled   BOOLEAN      NOT NULL DEFAULT true,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by           BIGINT
);
CREATE INDEX idx_campaign_company ON campaign(company_id);
CREATE INDEX idx_campaign_scenario ON campaign(scenario_id);

-- Allowed dial weekdays, one row per day — Set<DayOfWeek> on the Java side via
-- @ElementCollection. Without this a campaign would call debtors on a Sunday
-- morning, since dial_window above is time-only.
CREATE TABLE campaign_dial_day (
    campaign_id BIGINT      NOT NULL REFERENCES campaign(id),
    day         VARCHAR(20) NOT NULL,
    PRIMARY KEY (campaign_id, day)
);

-- Campaign target (client)
CREATE TABLE campaign_target (
    id              BIGSERIAL PRIMARY KEY,
    company_id      BIGINT       NOT NULL REFERENCES company(id),
    campaign_id     BIGINT       NOT NULL REFERENCES campaign(id),
    client_id       BIGINT       NOT NULL,   -- client in CRM
    phone           VARCHAR(20)  NOT NULL,
    language        VARCHAR(10),             -- null => campaign default
    context_data    JSONB        NOT NULL,   -- debt amount, due date, contract no.
    status          VARCHAR(50)  NOT NULL,   -- PENDING, IN_PROGRESS, DONE, FAILED, EXHAUSTED
    attempts        INT          NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    -- Right-to-refuse at the target level (§11.4) — skipped by the dialer and
    -- excluded from future campaigns. See also do_not_call_list below, which is the
    -- phone-level (cross-campaign) version of the same opt-out.
    do_not_call     BOOLEAN      NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_target_dial ON campaign_target(campaign_id, status, next_attempt_at);
CREATE INDEX idx_campaign_target_company ON campaign_target(company_id);

-- Call attempt
CREATE TABLE call_attempt (
    id                 BIGSERIAL PRIMARY KEY,
    company_id         BIGINT       NOT NULL REFERENCES company(id),
    target_id          BIGINT       NOT NULL REFERENCES campaign_target(id),
    sip_call_id        VARCHAR(255),
    asterisk_channel   VARCHAR(255),
    language           VARCHAR(10)  NOT NULL,
    started_at         TIMESTAMPTZ,
    answered_at        TIMESTAMPTZ,
    ended_at           TIMESTAMPTZ,
    duration_sec       INT,
    disposition        VARCHAR(50),             -- Disposition enum
    hangup_cause       VARCHAR(50),             -- Asterisk cause code
    recording_url      VARCHAR(500),            -- MinIO
    error_message      TEXT,
    -- Times the outbox tried to summarize this attempt. An ended attempt with
    -- transcripts but no call_result row is a summary that failed (LLM quota error).
    finalize_attempts  INT          NOT NULL DEFAULT 0,
    -- Which inbound_route (if any) this call landed on, for the "shu raqamga tushgan
    -- qo'ng'iroqlar statistikasi" drawer (§10.9). Null for outbound/manual calls.
    inbound_route_id   BIGINT       REFERENCES inbound_route(id),
    -- Which panel user actually handled a transferred call (§8.3/API-REQUIREMENTS §15),
    -- matched by app_user.sip_extension against the PJSIP endpoint that answered
    -- (AriService.handleOperatorJoin). Null for bot-only calls.
    operator_user_id   BIGINT       REFERENCES app_user(id),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_attempt_target ON call_attempt(target_id);
-- Partial index: the finalize sweep only ever scans finished attempts.
CREATE INDEX idx_attempt_ended ON call_attempt(ended_at) WHERE ended_at IS NOT NULL;
CREATE INDEX idx_call_attempt_company ON call_attempt(company_id);
CREATE INDEX idx_call_attempt_inbound_route ON call_attempt(inbound_route_id);

-- Technical detail tab (§10.5 "Texnik" tab): channel/trunk, AMD result, STT/TTS/LLM
-- identity, token usage, and turn-latency stats, one row per call. Written once at call
-- teardown (CallFinalizer), read alongside call_attempt/call_result for CallDetail.
CREATE TABLE call_technical (
    call_id             BIGINT PRIMARY KEY REFERENCES call_attempt(id),
    channel_name        VARCHAR(255),
    trunk               VARCHAR(100),
    amd_result          VARCHAR(20),   -- MACHINE | HUMAN | NULL (AMD disabled for the call)
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

-- Transcript (per utterance)
CREATE TABLE call_transcript (
    id             BIGSERIAL PRIMARY KEY,
    call_id        BIGINT       NOT NULL REFERENCES call_attempt(id),
    seq            INT          NOT NULL,
    role           VARCHAR(20)  NOT NULL,   -- AGENT, CLIENT
    text           TEXT         NOT NULL,
    dialog_state   VARCHAR(50),
    ts_offset_ms   INT          NOT NULL,   -- from call start
    stt_confidence REAL
);
CREATE INDEX idx_transcript_call ON call_transcript(call_id, seq);

-- Result
CREATE TABLE call_result (
    id              BIGSERIAL PRIMARY KEY,
    call_id         BIGINT       NOT NULL UNIQUE REFERENCES call_attempt(id),
    summary         TEXT         NOT NULL,
    reason_code     VARCHAR(50),
    promised_date   DATE,
    promised_amount NUMERIC(18,2),
    sentiment       VARCHAR(20),
    needs_follow_up BOOLEAN      NOT NULL DEFAULT false,
    follow_up_note  TEXT,
    escalated       BOOLEAN      NOT NULL DEFAULT false,
    crm_note_id     BIGINT,                  -- set after write-back to CRM
    -- Retry bookkeeping for the CRM outbox: crm_note_id IS NULL + crm_attempts < max
    -- is the sweep's work queue.
    crm_attempts    INT          NOT NULL DEFAULT 0,
    crm_last_error  TEXT,
    -- Generic outcome storage (ROADMAP A.3), alongside the typed debt-collection
    -- columns above (kept so CrmClient/report CSV export keep working for
    -- debt-collection unmodified -- CallRecordService.writeResult dual-writes both).
    outcome         JSONB        NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- Partial index: the outbox sweep only ever looks at results with no note id.
CREATE INDEX idx_result_crm_pending ON call_result(crm_attempts) WHERE crm_note_id IS NULL;

-- Do-not-call, phone level (§11.4). Unlike campaign_target.do_not_call above, this
-- opt-out holds across every future campaign, keyed by phone within a company — a
-- client blocked at company A can still be dialled by company B.
CREATE TABLE do_not_call_list (
    id         BIGSERIAL PRIMARY KEY,
    company_id BIGINT       NOT NULL REFERENCES company(id),
    phone      VARCHAR(20)  NOT NULL,
    reason     TEXT,
    source     VARCHAR(20)  NOT NULL DEFAULT 'CALL',   -- CALL, MANUAL, IMPORT
    -- "Ro'yxatdan chiqarish" (§10.8 DNC tab) — soft-delete. A removed opt-out is
    -- excluded from the blocking checks but the row stays for audit history.
    removed_at TIMESTAMPTZ,
    removed_by VARCHAR(64),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX idx_do_not_call_list_company_phone ON do_not_call_list(company_id, phone);

-- Audit log (PROJECT.md §11) — only state-changing API actions, not a general event log.
CREATE TABLE audit_log (
    id         BIGSERIAL PRIMARY KEY,
    company_id BIGINT       NOT NULL REFERENCES company(id),
    actor      VARCHAR(64)  NOT NULL,   -- authenticated principal (api-key role)
    action     VARCHAR(64)  NOT NULL,   -- CAMPAIGN_CREATE, CAMPAIGN_START, CALL_ORIGINATE, ...
    entity     VARCHAR(32),             -- campaign, target, call
    entity_id  VARCHAR(64),             -- id or phone/channel, as text: not every entity is a bigint
    detail     TEXT,
    -- Captured from the HTTP request at record() time; NULL for actions that ran
    -- outside a request (the dialer's own scheduled work).
    ip_address VARCHAR(45),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_created ON audit_log(created_at DESC);
CREATE INDEX idx_audit_action ON audit_log(action, created_at DESC);
CREATE INDEX idx_audit_log_company ON audit_log(company_id);

-- Contact list (§10.8) — independent of campaign_target, which stays
-- transient/campaign-scoped by design. Matched to call history by phone number, not
-- by a foreign key, mirroring do_not_call_list.
CREATE TABLE contact (
    id         BIGSERIAL PRIMARY KEY,
    company_id BIGINT       NOT NULL REFERENCES company(id),
    name       VARCHAR(255) NOT NULL,
    phone      VARCHAR(20)  NOT NULL,
    address    VARCHAR(500),
    tags       VARCHAR(500), -- comma-separated, matching campaign.dial_days's convention
    notes      TEXT,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- Unique per company, not globally — two companies may each have their own contact
-- at the same phone number.
CREATE UNIQUE INDEX idx_contact_company_phone ON contact(company_id, phone);
CREATE INDEX idx_contact_company_name ON contact(company_id, name);

-- Bildirishnomalar (§API-REQUIREMENTS 0.8) — company-wide events fanned out to
-- each recipient's own row so read state is per-user, not shared.
CREATE TABLE notification (
    id         BIGSERIAL PRIMARY KEY,
    company_id BIGINT       NOT NULL REFERENCES company(id),
    type       VARCHAR(30)  NOT NULL, -- CAMPAIGN_FINISHED, ERROR_OCCURRED, OPERATOR_REQUEST, DAILY_REPORT
    title      VARCHAR(255) NOT NULL,
    message    VARCHAR(1000),
    link       VARCHAR(255),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_notification_company ON notification(company_id, created_at DESC);

-- Surrogate PK (project convention, e.g. do_not_call_list) rather than a composite
-- one — keeps this a plain JPA @Entity, no @IdClass/@EmbeddedId needed.
CREATE TABLE notification_recipient (
    id               BIGSERIAL PRIMARY KEY,
    notification_id  BIGINT NOT NULL REFERENCES notification(id),
    user_id          BIGINT NOT NULL REFERENCES app_user(id),
    read_at          TIMESTAMPTZ
);
CREATE UNIQUE INDEX idx_notification_recipient_unique ON notification_recipient(notification_id, user_id);
CREATE INDEX idx_notification_recipient_user ON notification_recipient(user_id, read_at);

-- Per-user toggle (UI-DESIGN §8.2 popover). Absence of a row means "enabled" —
-- a row is only written once a user flips a toggle away from the default, so
-- shipping a new notification type never silently opts existing users out.
CREATE TABLE notification_preference (
    id      BIGSERIAL PRIMARY KEY,
    user_id BIGINT      NOT NULL REFERENCES app_user(id),
    type    VARCHAR(30) NOT NULL,
    enabled BOOLEAN     NOT NULL
);
CREATE UNIQUE INDEX idx_notification_preference_unique ON notification_preference(user_id, type);

-- One row per logged-in device (API-REQUIREMENTS §15 "GET/DELETE /api/profile/sessions")
-- — the panel's "faol sessiyalar" list needs several concurrently-valid tokens per user
-- (one per browser/device).
CREATE TABLE user_session (
    id                 BIGSERIAL PRIMARY KEY,
    company_id         BIGINT      NOT NULL REFERENCES company(id),
    user_id            BIGINT      NOT NULL REFERENCES app_user(id),
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

-- Personal channel x event matrix (API-REQUIREMENTS §15, UI-DESIGN §8.3 "Bildirishnomalar"
-- tab) — per-user, distinct from notification_matrix which is company-wide (§11 settings).
-- Same "absent row = off" convention as notification_matrix: a channel must be opted into
-- explicitly per event type.
CREATE TABLE personal_notification_matrix (
    id         BIGSERIAL PRIMARY KEY,
    company_id BIGINT      NOT NULL REFERENCES company(id),
    user_id    BIGINT      NOT NULL REFERENCES app_user(id),
    type       VARCHAR(30) NOT NULL, -- notification.enums.NotificationType
    channel    VARCHAR(20) NOT NULL, -- notification.enums.NotificationChannelType
    enabled    BOOLEAN     NOT NULL DEFAULT false
);
CREATE UNIQUE INDEX idx_personal_notification_matrix_unique ON personal_notification_matrix(user_id, type, channel);

-- Operator work schedule (API-REQUIREMENTS §15, UI-DESIGN §8.3 "Ish jadvali" tab) — which
-- hours an operator is available for an inbound call transferred to a human (ROADMAP C.4).
-- Weekly-recurring: one row per (day of week, time window) the operator is reachable.
CREATE TABLE user_schedule_slot (
    id          BIGSERIAL PRIMARY KEY,
    company_id  BIGINT      NOT NULL REFERENCES company(id),
    user_id     BIGINT      NOT NULL REFERENCES app_user(id),
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
    periodicity  VARCHAR(20)  NOT NULL,             -- DAILY, WEEKLY, MONTHLY
    format       VARCHAR(10)  NOT NULL DEFAULT 'pdf', -- csv, pdf, xlsx (ReportExportFactory)
    campaign_id  BIGINT       REFERENCES campaign(id), -- null = every campaign
    enabled      BOOLEAN      NOT NULL DEFAULT true,
    last_sent_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by   BIGINT
);
CREATE INDEX idx_report_schedule_company ON report_schedule(company_id);
-- The dispatch sweep scans only enabled rows every tick; narrow the index to those.
CREATE INDEX idx_report_schedule_due ON report_schedule(enabled, last_sent_at) WHERE enabled;

-- Per-company AI model overrides (§11 "GET/PUT /api/settings/ai-model"). Every column
-- is nullable: null means "use the process default from application.yml/DialogProperties".
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

-- Company-level notification channel x event matrix (§11 "GET/PUT
-- /api/settings/notifications") — layered on top of the existing per-user in-app bell
-- (notification/notification_preference above), not a replacement for it.

-- One row per company per external channel: whether it is set up at all, and where to
-- send it (email address / webhook URL / Telegram chat id).
CREATE TABLE notification_channel (
    id         BIGSERIAL PRIMARY KEY,
    company_id BIGINT      NOT NULL REFERENCES company(id),
    channel    VARCHAR(20) NOT NULL, -- EMAIL, WEBHOOK, TELEGRAM
    target     VARCHAR(500),
    enabled    BOOLEAN     NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX idx_notification_channel_unique ON notification_channel(company_id, channel);

-- Which event types fire on which of a company's channels. Absent row = off (unlike
-- notification_preference's "absence means enabled" — an external channel must be
-- opted into explicitly, never silently starts emailing/webhooking on a new event type).
CREATE TABLE notification_matrix (
    id         BIGSERIAL PRIMARY KEY,
    company_id BIGINT      NOT NULL REFERENCES company(id),
    type       VARCHAR(30) NOT NULL, -- notification.enums.NotificationType
    channel    VARCHAR(20) NOT NULL, -- EMAIL, WEBHOOK, TELEGRAM
    enabled    BOOLEAN     NOT NULL DEFAULT false
);
CREATE UNIQUE INDEX idx_notification_matrix_unique ON notification_matrix(company_id, type, channel);

-- Per-company Uysot CRM OAuth connection (§11 "GET/PUT /api/settings/integrations",
-- report #10). client_id/client_secret are NOT here — per Uysot's real OAuth docs one
-- platform-wide app (integration.config.UysotOAuthProperties) serves every company, so
-- each row only carries this company's own app_name/grants declaration.
-- access_token/refresh_token are AES-GCM ciphertext (shared.util.SecretCipher), never
-- plaintext.
CREATE TABLE crm_integration (
    id                BIGSERIAL PRIMARY KEY,
    company_id        BIGINT      NOT NULL UNIQUE REFERENCES company(id),
    provider          VARCHAR(20) NOT NULL DEFAULT 'UYSOT',
    app_name          VARCHAR(255),
    -- JSON array of {"permission":"LEAD","scope":"READ"} (integration.dto.CrmGrant).
    grants_json       TEXT,
    access_token_enc  TEXT,
    refresh_token_enc TEXT,
    token_expires_at  TIMESTAMPTZ,
    status            VARCHAR(20) NOT NULL DEFAULT 'NOT_CONNECTED',
    connected_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Per-company TTS tuning overrides (§11 "GET/PUT /api/settings/voice"). Every column is
-- nullable: null means "use the process default from voice-agent.tts.*".
CREATE TABLE voice_settings (
    id          BIGSERIAL PRIMARY KEY,
    company_id  BIGINT NOT NULL UNIQUE REFERENCES company(id),
    provider    VARCHAR(50),
    speed       DOUBLE PRECISION,
    pitch       DOUBLE PRECISION,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Generic per-user, per-table UI preference store (backend-uchun-talablar.md §1,
-- API-REQUIREMENTS §4 "Ustunlar ⚙") — not limited to the calls table like
-- app_user.call_columns: config_key is a frontend-owned namespace (e.g.
-- "callsTableColumns", "campaignsTableColumns"), config_value's shape is the
-- frontend's own concern too.
CREATE TABLE user_table_config (
    id           BIGSERIAL PRIMARY KEY,
    company_id   BIGINT       NOT NULL REFERENCES company(id),
    user_id      BIGINT       NOT NULL REFERENCES app_user(id),
    config_key   VARCHAR(100) NOT NULL,
    config_value JSONB        NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_table_config UNIQUE (user_id, config_key)
);
