-- Retry bookkeeping for the post-call pipeline, and a per-campaign daily call cap.
--
-- Until now a failed CRM note post or a summary the LLM refused to produce was only a
-- WARN log: the call ended, nothing retried, and the note was lost. CallOutboxService
-- sweeps for that work, and it needs somewhere to count attempts so a permanently
-- broken row is not retried forever.

-- Times the outbox tried (and failed) to post this result's note to the CRM, and why.
-- crm_note_id IS NULL + crm_attempts < max is the sweep's work queue.
ALTER TABLE call_result ADD COLUMN crm_attempts    INT NOT NULL DEFAULT 0;
ALTER TABLE call_result ADD COLUMN crm_last_error  TEXT;

-- Times the outbox tried to summarize this attempt. An ended attempt with transcripts
-- but no call_result row is a summary that failed (usually an LLM quota error).
ALTER TABLE call_attempt ADD COLUMN finalize_attempts INT NOT NULL DEFAULT 0;

-- Partial index: the sweep only ever looks at results with no note id.
CREATE INDEX idx_result_crm_pending ON call_result(crm_attempts) WHERE crm_note_id IS NULL;
-- The other sweep scans finished attempts.
CREATE INDEX idx_attempt_ended ON call_attempt(ended_at) WHERE ended_at IS NOT NULL;

-- Cost cap (§13.2 volume question): the dialer stops dispatching a campaign once it
-- has made this many attempts today. 0 = unlimited, which is how every existing
-- campaign keeps behaving.
ALTER TABLE campaign ADD COLUMN daily_call_cap INT NOT NULL DEFAULT 0;
