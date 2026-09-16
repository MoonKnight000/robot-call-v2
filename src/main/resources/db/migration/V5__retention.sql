-- Data retention (audit M-12) and billing of the post-call summary (audit §7.6).

-- RetentionService deletes by age, in batches; these back the two purges whose age column
-- had no index. audit_log(created_at) already has idx_audit_created.
CREATE INDEX idx_call_result_created ON call_result(created_at);
CREATE INDEX idx_client_memory_updated ON client_memory(updated_at);
-- Finished outbox messages (V4) are purged by age; PENDING ones are never touched.
CREATE INDEX idx_outbox_message_finished_created ON outbox_message(created_at)
    WHERE status IN ('SENT', 'DEAD');

-- The post-call summary is an LLM call of its own, charged to the call as a separate ledger
-- entry ('summary-charge:<call_attempt_id>') because it can run after the call was settled.
-- Its tokens are kept apart from the conversation's so the charge can still be recomputed:
-- on a realtime call the conversation's tokens are informational only, while the summary's
-- are priced at the text-LLM rate. Their price is added to llm_cost_uzs and total_uzs.
ALTER TABLE call_billing
    ADD COLUMN summary_prompt_tokens     BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN summary_completion_tokens BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN summary_cached_tokens     BIGINT NOT NULL DEFAULT 0;
