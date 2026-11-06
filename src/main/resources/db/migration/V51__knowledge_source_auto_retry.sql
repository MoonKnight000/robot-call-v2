-- A source that failed for a transient reason (the site was down, an unexpected error) is
-- queued again on its own with a growing wait (KnowledgeBaseProperties.autoRetry*).
-- failed_attempts counts consecutive failures and goes back to 0 once the source indexes;
-- failed_at is when the last one happened, the start of the next wait.
ALTER TABLE knowledge_source
    ADD COLUMN failed_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN failed_at       TIMESTAMPTZ;

CREATE INDEX idx_knowledge_source_failed ON knowledge_source(status) WHERE status = 'FAILED';
