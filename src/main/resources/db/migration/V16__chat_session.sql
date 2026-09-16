-- UNIVERSAL-OPERATOR-PLAN E.4: the same agent over text. A chat is a conversation with the
-- agent that has no audio in it, so what a call keeps in memory for its lifetime is kept here
-- instead: the transcript and the stage the conversation is in. Every message rebuilds the
-- session from these rows, which is what lets the next message land on another instance.
CREATE TABLE chat_session (
    id              BIGSERIAL    PRIMARY KEY,
    -- What the browser holds and sends back. Not the id: a guessable handle to somebody
    -- else's conversation is somebody else's conversation.
    session_key     VARCHAR(64)  NOT NULL UNIQUE,
    company_id      BIGINT       NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    ai_agent_id     BIGINT       NOT NULL REFERENCES ai_agent(id) ON DELETE CASCADE,
    widget_id       BIGINT       REFERENCES agent_widget(id) ON DELETE SET NULL,
    language        VARCHAR(10)  NOT NULL,
    -- Where the scenario's state machine stands, and what its tools have recorded so far.
    stage           VARCHAR(100) NOT NULL,
    outcome         JSONB        NOT NULL DEFAULT '{}',
    disposition     VARCHAR(50),
    ended           BOOLEAN      NOT NULL DEFAULT false,
    message_count   INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_message_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_chat_session_company ON chat_session(company_id, created_at DESC);
CREATE INDEX idx_chat_session_agent ON chat_session(ai_agent_id, created_at DESC);

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
