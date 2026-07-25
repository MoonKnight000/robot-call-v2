-- Initial schema for the AI voice agent. See PROJECT.md §6.

-- Campaign
CREATE TABLE campaign (
    id                   BIGSERIAL PRIMARY KEY,
    name                 VARCHAR(255) NOT NULL,
    type                 VARCHAR(50)  NOT NULL,   -- DEBT_COLLECTION, SURVEY, ...
    status               VARCHAR(50)  NOT NULL,   -- DRAFT, ACTIVE, PAUSED, COMPLETED
    goal_prompt          TEXT         NOT NULL,   -- part of the LLM system prompt
    script_config        JSONB        NOT NULL,   -- FSM settings, questions
    default_language     VARCHAR(10)  NOT NULL DEFAULT 'uz-UZ',
    dial_window_start    TIME         NOT NULL DEFAULT '09:00',
    dial_window_end      TIME         NOT NULL DEFAULT '20:00',
    max_attempts         INT          NOT NULL DEFAULT 3,
    retry_interval_hours INT          NOT NULL DEFAULT 24,
    max_concurrent_calls INT          NOT NULL DEFAULT 20,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by           BIGINT
);

-- Campaign target (client)
CREATE TABLE campaign_target (
    id              BIGSERIAL PRIMARY KEY,
    campaign_id     BIGINT       NOT NULL REFERENCES campaign(id),
    client_id       BIGINT       NOT NULL,   -- client in CRM
    phone           VARCHAR(20)  NOT NULL,
    language        VARCHAR(10),             -- null => campaign default
    context_data    JSONB        NOT NULL,   -- debt amount, due date, contract no.
    status          VARCHAR(50)  NOT NULL,   -- PENDING, IN_PROGRESS, DONE, FAILED, EXHAUSTED
    attempts        INT          NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_target_dial ON campaign_target(campaign_id, status, next_attempt_at);

-- Call attempt
CREATE TABLE call_attempt (
    id               BIGSERIAL PRIMARY KEY,
    target_id        BIGINT       NOT NULL REFERENCES campaign_target(id),
    sip_call_id      VARCHAR(255),
    asterisk_channel VARCHAR(255),
    language         VARCHAR(10)  NOT NULL,
    started_at       TIMESTAMPTZ,
    answered_at      TIMESTAMPTZ,
    ended_at         TIMESTAMPTZ,
    duration_sec     INT,
    disposition      VARCHAR(50),             -- Disposition enum
    hangup_cause     VARCHAR(50),             -- Asterisk cause code
    recording_url    VARCHAR(500),            -- MinIO
    error_message    TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_attempt_target ON call_attempt(target_id);

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
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
