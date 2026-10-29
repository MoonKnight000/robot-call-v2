-- A person from the company can take a text conversation over from the agent: while
-- operator_user_id is set the agent stays silent, the client's messages wait for that person,
-- and what they write is stored as OPERATOR lines. Releasing it hands the conversation back.
ALTER TABLE chat_session ADD COLUMN operator_user_id BIGINT REFERENCES app_user(id) ON DELETE SET NULL;

ALTER TABLE chat_message DROP CONSTRAINT ck_chat_message_role;
ALTER TABLE chat_message ADD CONSTRAINT ck_chat_message_role CHECK (role IN ('CLIENT', 'AGENT', 'OPERATOR'));
