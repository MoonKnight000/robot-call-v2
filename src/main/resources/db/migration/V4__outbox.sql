-- One outbox for the writes a finished call owes other systems (docs/AUDIT-2026-09-13.md
-- §4.12, §10.10). OutboxDeliveryService claims due rows with FOR UPDATE SKIP LOCKED in a short
-- transaction, calls the remote system outside it, then marks the row SENT or schedules the
-- next try with exponential backoff; after voice-agent.outbox.max-attempts the row is DEAD and
-- an alert is raised.
--
-- idempotency_key is '<kind>:<call_attempt_id>': a call gets at most one message of each kind,
-- and a SENT row is never sent again. next_attempt_at doubles as the claim's lease — a worker
-- that takes a row pushes it past the time its delivery may take.
CREATE TABLE outbox_message (
    id              BIGSERIAL PRIMARY KEY,
    company_id      BIGINT       NOT NULL REFERENCES company(id),
    kind            VARCHAR(40)  NOT NULL,
    call_attempt_id BIGINT       NOT NULL REFERENCES call_attempt(id) ON DELETE CASCADE,
    payload         JSONB        NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    status          VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
    attempts        INT          NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_error      TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at         TIMESTAMPTZ,
    -- Mirror callrecord.domain.enums.OutboxMessageKind and OutboxMessageStatus.
    CONSTRAINT ck_outbox_message_kind CHECK (kind IN ('CRM_NOTE', 'CRM_CALL_HISTORY')),
    CONSTRAINT ck_outbox_message_status CHECK (status IN ('PENDING', 'SENT', 'DEAD'))
);
-- The worker's claim reads due PENDING rows only.
CREATE INDEX idx_outbox_message_due ON outbox_message(next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX idx_outbox_message_company ON outbox_message(company_id);
CREATE INDEX idx_outbox_message_call_attempt ON outbox_message(call_attempt_id);

-- The summary retry claims a call instead of only reading it: the claim counts the attempt and
-- hides the call from every instance's sweep until this time, so two instances never
-- summarize the same call at once. NULL: never claimed.
ALTER TABLE call_attempt ADD COLUMN summary_lease_until TIMESTAMPTZ;

-- Notes the old repair sweep still owed. That sweep is gone, so without this a note that
-- failed shortly before the upgrade would never be posted. Limited to results of the last day
-- (the old sweep's tries were minutes apart, so anything older had used them up), to results
-- it still had tries left for (its default max of 5), and to calls with a CRM lead — an
-- inbound or manual call has no target and nothing to file a note under.
--
-- The text is rebuilt from the stored columns, as the old sweep rebuilt its summary:
-- CrmClient#composeNoteText without the callback time and QA score, which call_result does
-- not keep.
INSERT INTO outbox_message (company_id, kind, call_attempt_id, payload, idempotency_key)
SELECT a.company_id,
       'CRM_NOTE',
       a.id,
       jsonb_build_object(
           'leadId', t.client_id,
           'noteText', left(COALESCE(NULLIF(concat_ws(E'\n',
               NULLIF(btrim(r.summary), ''),
               'Sabab: ' || NULLIF(btrim(r.outcome ->> 'reasonCode'), ''),
               'Va''da qilingan summa: ' || NULLIF(btrim(r.outcome ->> 'promisedAmount'), ''),
               'Va''da qilingan sana: ' || NULLIF(btrim(r.outcome ->> 'promisedDate'), ''),
               'Kayfiyat: ' || r.sentiment,
               CASE WHEN r.needs_follow_up THEN 'Kuzatuv kerak: ' || COALESCE(r.follow_up_note, 'ha') END
           ), ''), 'AI operator bilan suhbat bo''lib o''tdi.'), 4000)),
       'CRM_NOTE:' || a.id
FROM call_result r
JOIN call_attempt a ON a.id = r.call_id
JOIN campaign_target t ON t.id = a.target_id
WHERE r.crm_note_id IS NULL
  AND r.crm_attempts < 5
  AND r.created_at >= now() - INTERVAL '1 day'
  AND t.client_id > 0
ON CONFLICT (idempotency_key) DO NOTHING;
