-- UNIVERSAL-OPERATOR-PLAN E.4: the second text channel. A company's own Telegram bot answers
-- with one of its agents — the same agent, scenario and tools the widget chat and the phone use.
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

-- A Telegram conversation is a chat_session like any other; these two say which one.
ALTER TABLE chat_session ADD COLUMN telegram_bot_id  BIGINT REFERENCES telegram_bot(id) ON DELETE CASCADE;
ALTER TABLE chat_session ADD COLUMN telegram_chat_id VARCHAR(64);
-- One open conversation per Telegram chat, enforced here rather than hoped for in code: the
-- person writes again and continues where they left off, and two updates arriving together
-- cannot open two conversations for the same chat.
CREATE UNIQUE INDEX uq_chat_session_telegram_open ON chat_session(telegram_bot_id, telegram_chat_id)
    WHERE telegram_chat_id IS NOT NULL AND ended = false;
