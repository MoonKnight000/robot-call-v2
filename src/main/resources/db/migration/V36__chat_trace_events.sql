-- The agent's step-by-step trace (blocks entered, values recorded, functions run, HTTP sent, tokens)
-- for chat conversations too. One table for both: the trace event types, payloads, retention purge and
-- the panel's reader are the same; a row belongs to exactly one call attempt or one chat session.
ALTER TABLE call_event ALTER COLUMN call_attempt_id DROP NOT NULL;
ALTER TABLE call_event ADD COLUMN chat_session_id BIGINT REFERENCES chat_session(id) ON DELETE CASCADE;
ALTER TABLE call_event ADD CONSTRAINT call_event_one_owner
    CHECK ((call_attempt_id IS NULL) <> (chat_session_id IS NULL));
CREATE INDEX idx_call_event_chat ON call_event(chat_session_id, at) WHERE chat_session_id IS NOT NULL;
