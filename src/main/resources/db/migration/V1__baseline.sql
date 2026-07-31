-- Baseline schema for the AI voice agent (PROJECT.md §6). Consolidates what used to
-- be 15 incremental migrations (V1-V15) into the single starting point below — safe
-- because no release has shipped yet, so there is no production history to preserve
-- step by step. If you have a local/dev database that already ran the old V1-V15
-- history, drop it and let this recreate the schema from scratch (see docs/RUN.md).

-- Company/tenant (ROADMAP Bosqich B). Every other table below is scoped to one.
CREATE TABLE company (
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(255) NOT NULL,
    status            VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, SUSPENDED
    default_language  VARCHAR(10)  NOT NULL DEFAULT 'uz-UZ',
    timezone          VARCHAR(64)  NOT NULL DEFAULT 'Asia/Tashkent',
    caller_id         VARCHAR(20),
    dial_window_start TIME         NOT NULL DEFAULT '09:00',
    dial_window_end   TIME         NOT NULL DEFAULT '20:00',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

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
    dial_window_start    TIME         NOT NULL DEFAULT '09:00',
    dial_window_end      TIME         NOT NULL DEFAULT '20:00',
    -- Comma-separated java.time.DayOfWeek names (§11.2) — without this a campaign
    -- would call debtors on a Sunday morning, since the window above is time-only.
    dial_days            VARCHAR(64)  NOT NULL DEFAULT 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY',
    max_attempts         INT          NOT NULL DEFAULT 3,
    retry_interval_hours INT          NOT NULL DEFAULT 24,
    max_concurrent_calls INT          NOT NULL DEFAULT 20,
    -- Catalog id from voice-agent.tts.catalog (PROJECT.md §2.5); NULL keeps the
    -- configured provider/voice routing.
    tts_voice            VARCHAR(64),
    -- Cost cap (§13.2): dialer stops dispatching this campaign once it has made this
    -- many attempts today. 0 = unlimited.
    daily_call_cap       INT          NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by           BIGINT
);
CREATE INDEX idx_campaign_company ON campaign(company_id);

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
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_attempt_target ON call_attempt(target_id);
-- Partial index: the finalize sweep only ever scans finished attempts.
CREATE INDEX idx_attempt_ended ON call_attempt(ended_at) WHERE ended_at IS NOT NULL;
CREATE INDEX idx_call_attempt_company ON call_attempt(company_id);

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
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_created ON audit_log(created_at DESC);
CREATE INDEX idx_audit_action ON audit_log(action, created_at DESC);
CREATE INDEX idx_audit_log_company ON audit_log(company_id);

-- Scenario storage (ROADMAP A.4). company_id is nullable: NULL means "global
-- built-in template", visible to every company (the 5 seed templates below).
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
    created_by   VARCHAR(64)
);
-- Exactly one active version per scenario_key (ROADMAP A.4 versioning).
CREATE UNIQUE INDEX idx_scenario_active_key ON scenario(scenario_key) WHERE is_active;
CREATE INDEX idx_scenario_key_version ON scenario(scenario_key, version DESC);
CREATE INDEX idx_scenario_company ON scenario(company_id);

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

-- ---------------------------------------------------------------------------------
-- Seed data
-- ---------------------------------------------------------------------------------

-- Bootstrap tenant every other row below belongs to. Explicit id=1 so it matches
-- voice-agent.company.default-id's own default.
INSERT INTO company (id, name) VALUES (1, 'Default');
SELECT setval(pg_get_serial_sequence('company', 'id'), 1, true);

-- Placeholder campaign + target so manually/auto-started calls (Stages 7-9) have a
-- call_attempt parent before a real campaign exists. Looked up by phone = 'MANUAL'.
-- DRAFT (not ACTIVE): it is only a parent row for manual/test calls, never meant to
-- be picked up by the dialer's own scan.
INSERT INTO campaign (company_id, name, type, status, goal_prompt, script_config)
VALUES (1, 'MANUAL', 'DEBT_COLLECTION', 'DRAFT',
        'Qo''lda/avtomatik test qo''ng''iroqlari (Bosqich 9)', '{}');

INSERT INTO campaign_target (company_id, campaign_id, client_id, phone, context_data, status)
VALUES (1, (SELECT id FROM campaign WHERE name = 'MANUAL' ORDER BY id LIMIT 1),
        0, 'MANUAL', '{}', 'IN_PROGRESS');

-- Built-in scenario templates (ROADMAP A.2). Dollar-quoted throughout — the Uzbek
-- text here is full of apostrophes (bo'ling, qo'ng'iroq...), and escaping every one
-- with '' would make these definitions unreadable and easy to break.
--
-- The JSON literal starts on its own line after the opening $def$ tag on purpose:
-- Flyway's placeholder scanner looks for the literal two-character sequence "${"
-- anywhere in the raw script, and a tag glued directly to a "{" (e.g. "$def${")
-- forms exactly that sequence — Flyway then reads everything up to the next "}" as
-- an undefined placeholder name and fails with "No value provided for placeholder:
-- ${...". Keeping a line break between the tag and the brace avoids the false match
-- regardless of the placeholder-replacement setting below.
INSERT INTO scenario(scenario_key, version, name, description, is_builtin, is_active, definition, created_by)
VALUES (
    'debt-collection', 1,
    $$Qarzdorlik bo'yicha qo'ng'iroq$$,
    $$Hozirgi ssenariy, aynan shu xatti-harakat bilan: mijozga qarzi haqida xabar berish, to'lov sanasini kelishish yoki rad sababini yozib olish.$$,
    true, true,
$def$
{
  "stages": [
    {"id": "GREETING", "purpose": "Salomlashish va mijozni kutish", "allowedTransitions": ["IDENTITY_CHECK", "ESCALATE_TO_HUMAN", "END_CALL"]},
    {"id": "IDENTITY_CHECK", "purpose": "Mijoz shaxsini tasdiqlash", "allowedTransitions": ["DEBT_NOTICE", "ESCALATE_TO_HUMAN", "END_CALL"]},
    {"id": "DEBT_NOTICE", "purpose": "Qarz summasi va sababi haqida xabar berish", "allowedTransitions": ["REASON_INQUIRY", "PAYMENT_DATE", "CONFIRMATION", "CLOSING", "ESCALATE_TO_HUMAN", "END_CALL"]},
    {"id": "REASON_INQUIRY", "purpose": "To'lov kechikishi sababini so'rash", "allowedTransitions": ["PAYMENT_DATE", "CONFIRMATION", "CLOSING", "ESCALATE_TO_HUMAN", "END_CALL"]},
    {"id": "PAYMENT_DATE", "purpose": "To'lov sanasini kelishish", "allowedTransitions": ["CONFIRMATION", "CLOSING", "ESCALATE_TO_HUMAN", "END_CALL"]},
    {"id": "CONFIRMATION", "purpose": "Kelishilgan shartlarni tasdiqlash", "allowedTransitions": ["CLOSING", "ESCALATE_TO_HUMAN", "END_CALL"]},
    {"id": "CLOSING", "purpose": "Suhbatni yakunlash", "allowedTransitions": ["END_CALL"]},
    {"id": "ESCALATE_TO_HUMAN", "purpose": "Operatorga uzatish", "allowedTransitions": ["END_CALL"]},
    {"id": "END_CALL", "purpose": "Qo'ng'iroqni tugatish", "allowedTransitions": []}
  ],
  "factSchema": [
    {"name": "clientName", "type": "string", "required": true},
    {"name": "debtAmount", "type": "number", "required": true},
    {"name": "currency", "type": "string", "required": true},
    {"name": "dueDate", "type": "date", "required": false},
    {"name": "contractNumber", "type": "string", "required": false}
  ],
  "tools": [
    {"name": "recordPaymentPromise", "description": "Mijoz to'lov sanasini va'da qilganda chaqiring", "params": [
      {"name": "date", "type": "date", "required": true, "constraint": "kelajakda bo'lishi shart"},
      {"name": "amount", "type": "number", "required": false, "constraint": null},
      {"name": "note", "type": "string", "required": false, "constraint": null}
    ]},
    {"name": "recordRefusalReason", "description": "Mijoz to'lovni rad etganda sababini yozib oling", "params": [
      {"name": "reason", "type": "string", "required": true, "constraint": null}
    ]}
  ],
  "outcomeSchema": [
    {"name": "promisedDate", "type": "date", "description": "Mijoz va'da qilgan to'lov sanasi"},
    {"name": "reasonCode", "type": "string", "description": "Rad etish sababi kodi"}
  ],
  "rolePrompt": "Siz Uysot kompaniyasining qarz undirish agentisiz. Mijozga qarzi haqida xabar bering, to'lov sanasini kelishib oling yoki rad javobini sabab bilan yozib oling. Har doim xushmuomala va qat'iy bo'ling.",
  "guardrails": [
    "Faktlarda berilmagan pul summasini hech qachon aytmang",
    "Mijozni haqorat qilmang yoki tahdid qilmang"
  ],
  "disclosureText": "Bu qo'ng'iroq avtomatik tizim tomonidan amalga oshirilmoqda va yozib olinmoqda."
}
$def$::jsonb,
    'system'
);

INSERT INTO scenario(scenario_key, version, name, description, is_builtin, is_active, definition, created_by)
VALUES (
    'lead-qualification', 1,
    $$Potensial mijozni aniqlash$$,
    $$Potensial mijozning qiziqishi, byudjeti, muddati va uchrashuv vaqtini aniqlash.$$,
    true, true,
$def$
{
  "stages": [
    {"id": "GREETING", "purpose": "Salomlashish va qo'ng'iroq maqsadini tushuntirish", "allowedTransitions": ["INTEREST_CHECK", "ESCALATE_TO_HUMAN", "END_CALL"]},
    {"id": "INTEREST_CHECK", "purpose": "Mahsulotga qiziqish darajasini aniqlash", "allowedTransitions": ["BUDGET_TIMELINE", "CLOSING", "ESCALATE_TO_HUMAN", "END_CALL"]},
    {"id": "BUDGET_TIMELINE", "purpose": "Byudjet va muddatni so'rash", "allowedTransitions": ["MEETING_SCHEDULING", "CLOSING", "ESCALATE_TO_HUMAN", "END_CALL"]},
    {"id": "MEETING_SCHEDULING", "purpose": "Uchrashuv vaqtini kelishish", "allowedTransitions": ["CLOSING", "ESCALATE_TO_HUMAN", "END_CALL"]},
    {"id": "CLOSING", "purpose": "Suhbatni yakunlash", "allowedTransitions": ["END_CALL"]},
    {"id": "ESCALATE_TO_HUMAN", "purpose": "Operatorga uzatish", "allowedTransitions": ["END_CALL"]},
    {"id": "END_CALL", "purpose": "Qo'ng'iroqni tugatish", "allowedTransitions": []}
  ],
  "factSchema": [
    {"name": "clientName", "type": "string", "required": false},
    {"name": "productName", "type": "string", "required": false}
  ],
  "tools": [
    {"name": "recordInterest", "description": "Mijozning qiziqish darajasini yozib oling", "params": [
      {"name": "level", "type": "string", "required": true, "constraint": null}
    ]},
    {"name": "recordBudget", "description": "Mijoz aytgan taxminiy byudjetni yozib oling", "params": [
      {"name": "amount", "type": "number", "required": false, "constraint": null}
    ]},
    {"name": "recordMeeting", "description": "Kelishilgan uchrashuv vaqtini yozib oling", "params": [
      {"name": "dateTime", "type": "string", "required": true, "constraint": "kelajakda bo'lishi shart"}
    ]}
  ],
  "outcomeSchema": [
    {"name": "interest", "type": "string", "description": "Qiziqish darajasi"},
    {"name": "budget", "type": "number", "description": "Taxminiy byudjet"},
    {"name": "meetingAt", "type": "string", "description": "Kelishilgan uchrashuv vaqti"}
  ],
  "rolePrompt": "Siz mahsulotga qiziqish bildirgan potensial mijoz bilan suhbatlashadigan savdo agentisiz. Qiziqish darajasini, byudjetni va uchrashuv vaqtini aniqlang.",
  "guardrails": [
    "Narx yoki chegirma bo'yicha rasmiy bo'lmagan va'da bermang"
  ],
  "disclosureText": "Bu qo'ng'iroq avtomatik tizim tomonidan amalga oshirilmoqda va yozib olinmoqda."
}
$def$::jsonb,
    'system'
);

INSERT INTO scenario(scenario_key, version, name, description, is_builtin, is_active, definition, created_by)
VALUES (
    'notification', 1,
    $$Bir tomonlama xabar$$,
    $$Xabar yetkazish va tasdiqlash (to'lov eslatmasi, TDS va boshqalar).$$,
    true, true,
$def$
{
  "stages": [
    {"id": "GREETING", "purpose": "Salomlashish", "allowedTransitions": ["DELIVER_MESSAGE", "END_CALL"]},
    {"id": "DELIVER_MESSAGE", "purpose": "Xabarni yetkazish", "allowedTransitions": ["CONFIRM", "CLOSING", "END_CALL"]},
    {"id": "CONFIRM", "purpose": "Xabar tushunilganini tasdiqlash", "allowedTransitions": ["CLOSING", "END_CALL"]},
    {"id": "CLOSING", "purpose": "Suhbatni yakunlash", "allowedTransitions": ["END_CALL"]},
    {"id": "END_CALL", "purpose": "Qo'ng'iroqni tugatish", "allowedTransitions": []}
  ],
  "factSchema": [
    {"name": "messageText", "type": "string", "required": true}
  ],
  "tools": [
    {"name": "recordAcknowledged", "description": "Mijoz xabarni tasdiqlaganda yoki rad etganda chaqiring", "params": [
      {"name": "acknowledged", "type": "boolean", "required": true, "constraint": null}
    ]}
  ],
  "outcomeSchema": [
    {"name": "acknowledged", "type": "boolean", "description": "Mijoz xabarni tasdiqladimi"}
  ],
  "rolePrompt": "Siz mijozga muhim xabarni yetkazadigan avtomatik agentisiz. Xabarni aniq va qisqa yetkazing, keyin tushunganini tasdiqlang.",
  "guardrails": [
    "Berilgan xabar matnidan tashqari qo'shimcha va'da bermang"
  ],
  "disclosureText": "Bu qo'ng'iroq avtomatik tizim tomonidan amalga oshirilmoqda va yozib olinmoqda."
}
$def$::jsonb,
    'system'
);

INSERT INTO scenario(scenario_key, version, name, description, is_builtin, is_active, definition, created_by)
VALUES (
    'survey', 1,
    $$So'rovnoma$$,
    $$3-5 savollik so'rovnoma yoki NPS o'tkazish.$$,
    true, true,
$def$
{
  "stages": [
    {"id": "GREETING", "purpose": "Salomlashish va so'rovnoma haqida qisqacha ma'lumot", "allowedTransitions": ["QUESTION_1", "END_CALL"]},
    {"id": "QUESTION_1", "purpose": "Birinchi savolni berish", "allowedTransitions": ["QUESTION_2", "CLOSING", "END_CALL"]},
    {"id": "QUESTION_2", "purpose": "Ikkinchi savolni berish", "allowedTransitions": ["QUESTION_3", "CLOSING", "END_CALL"]},
    {"id": "QUESTION_3", "purpose": "Uchinchi savolni berish", "allowedTransitions": ["CLOSING", "END_CALL"]},
    {"id": "CLOSING", "purpose": "Rahmat aytish va yakunlash", "allowedTransitions": ["END_CALL"]},
    {"id": "END_CALL", "purpose": "Qo'ng'iroqni tugatish", "allowedTransitions": []}
  ],
  "factSchema": [
    {"name": "topicName", "type": "string", "required": false}
  ],
  "tools": [
    {"name": "recordAnswer", "description": "Har bir savolga javobni yozib oling", "params": [
      {"name": "question", "type": "string", "required": true, "constraint": null},
      {"name": "answer", "type": "string", "required": true, "constraint": null}
    ]},
    {"name": "recordScore", "description": "NPS yoki umumiy bahoni yozib oling", "params": [
      {"name": "score", "type": "number", "required": false, "constraint": null}
    ]}
  ],
  "outcomeSchema": [
    {"name": "answers", "type": "array", "description": "Har bir savol-javob jufti"},
    {"name": "score", "type": "number", "description": "Umumiy baho"}
  ],
  "rolePrompt": "Siz qisqa so'rovnoma o'tkazadigan agentisiz. Savollarni neytral tarzda bering va javoblarni aniq yozib oling.",
  "guardrails": [
    "Savollarni yetakchilik qilmasdan, neytral tarzda bering",
    "Mijozning javobini talqin qilib o'zgartirmang"
  ],
  "disclosureText": "Bu qo'ng'iroq avtomatik tizim tomonidan amalga oshirilmoqda va yozib olinmoqda."
}
$def$::jsonb,
    'system'
);

INSERT INTO scenario(scenario_key, version, name, description, is_builtin, is_active, definition, created_by)
VALUES (
    'appointment-reminder', 1,
    $$Uchrashuvni eslatish$$,
    $$Uchrashuvni eslatish, tasdiqlash yoki ko'chirish so'rovini yozib olish.$$,
    true, true,
$def$
{
  "stages": [
    {"id": "GREETING", "purpose": "Salomlashish", "allowedTransitions": ["REMIND", "END_CALL"]},
    {"id": "REMIND", "purpose": "Uchrashuv vaqtini eslatish", "allowedTransitions": ["CONFIRM_OR_RESCHEDULE", "CLOSING", "END_CALL"]},
    {"id": "CONFIRM_OR_RESCHEDULE", "purpose": "Tasdiqlash yoki ko'chirish so'rovini olish", "allowedTransitions": ["CLOSING", "END_CALL"]},
    {"id": "CLOSING", "purpose": "Suhbatni yakunlash", "allowedTransitions": ["END_CALL"]},
    {"id": "END_CALL", "purpose": "Qo'ng'iroqni tugatish", "allowedTransitions": []}
  ],
  "factSchema": [
    {"name": "appointmentTime", "type": "date", "required": true},
    {"name": "clientName", "type": "string", "required": false}
  ],
  "tools": [
    {"name": "recordConfirmation", "description": "Mijoz uchrashuvni tasdiqlaganda yoki rad etganda chaqiring", "params": [
      {"name": "confirmed", "type": "boolean", "required": true, "constraint": null}
    ]},
    {"name": "recordReschedule", "description": "Mijoz uchrashuvni ko'chirishni so'raganda yangi vaqtni yozib oling", "params": [
      {"name": "newTime", "type": "string", "required": true, "constraint": "kelajakda bo'lishi shart"}
    ]}
  ],
  "outcomeSchema": [
    {"name": "confirmed", "type": "boolean", "description": "Mijoz uchrashuvni tasdiqladimi"},
    {"name": "newTime", "type": "string", "description": "So'ralgan yangi uchrashuv vaqti, bo'lsa"}
  ],
  "rolePrompt": "Siz mijozga uchrashuvini eslatadigan agentisiz. Uchrashuv vaqtini tasdiqlang yoki ko'chirish so'rovini aniq yozib oling.",
  "guardrails": [
    "Uchrashuvni o'zingiz bekor qilmang, faqat ko'chirish so'rovini yozib oling"
  ],
  "disclosureText": "Bu qo'ng'iroq avtomatik tizim tomonidan amalga oshirilmoqda va yozib olinmoqda."
}
$def$::jsonb,
    'system'
);
