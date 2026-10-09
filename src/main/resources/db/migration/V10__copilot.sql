-- The panel's AI copilot: one conversation per row, owned by the user who started it.
CREATE TABLE copilot_session (
    id          BIGSERIAL    PRIMARY KEY,
    company_id  BIGINT       NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    user_id     BIGINT       NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    title       VARCHAR(200) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_copilot_session_user ON copilot_session (company_id, user_id, updated_at DESC);

-- What was said, and every tool the copilot called on the way — kept for the audit trail.
-- A TOOL row carries the tool's name, its arguments and what it answered (cut short when long).
CREATE TABLE copilot_message (
    id                BIGSERIAL    PRIMARY KEY,
    session_id        BIGINT       NOT NULL REFERENCES copilot_session(id) ON DELETE CASCADE,
    company_id        BIGINT       NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    role              VARCHAR(16)  NOT NULL,
    content           TEXT         NOT NULL,
    tool_name         VARCHAR(100),
    tool_arguments    TEXT,
    tool_error        BOOLEAN      NOT NULL DEFAULT false,
    prompt_tokens     BIGINT       NOT NULL DEFAULT 0,
    completion_tokens BIGINT       NOT NULL DEFAULT 0,
    charged_uzs       BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_copilot_message_role CHECK (role IN ('USER', 'ASSISTANT', 'TOOL'))
);
CREATE INDEX idx_copilot_message_session ON copilot_message (session_id, id);

-- The copilot's tokens are charged like a call's, under their own entry type: they are no call.
ALTER TABLE billing_ledger DROP CONSTRAINT ck_billing_ledger_entry_type;
ALTER TABLE billing_ledger ADD CONSTRAINT ck_billing_ledger_entry_type
    CHECK (entry_type IN ('TOPUP', 'RESERVE', 'RELEASE', 'CALL_CHARGE', 'ADJUSTMENT', 'AI_CHARGE'));
