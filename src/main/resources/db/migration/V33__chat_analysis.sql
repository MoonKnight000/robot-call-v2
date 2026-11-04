-- Post-chat analysis (Retell's chat_analysis): written once by ChatAnalysisJob after a chat ends
-- or falls silent past its TTL. analyzed_at NULL = not analysed yet.
ALTER TABLE chat_session
    ADD COLUMN analysis_summary         TEXT,
    ADD COLUMN analysis_sentiment       VARCHAR(20),
    ADD COLUMN analysis_needs_follow_up BOOLEAN,
    ADD COLUMN analysis_outcome         JSONB,
    ADD COLUMN analyzed_at              TIMESTAMPTZ;

CREATE INDEX idx_chat_session_to_analyze ON chat_session (last_message_at) WHERE analyzed_at IS NULL;
