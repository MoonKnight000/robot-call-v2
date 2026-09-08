-- The CRM's own reference for a posted note, in whatever shape that CRM issues it.
--
-- crm_note_id was a BIGINT because the first CRM this talked to answered a note write with
-- a numeric id. Uysot's Open API does not: POST /v1/open-api/lead-note/{leadId}/list is an
-- asynchronous write and answers with a requestId (a UUID), and polling
-- GET /v1/open-api/request/{requestId} reports PENDING / SUCCESS / FAILED and nothing else
-- — the created note's id is never exposed.
--
-- The column is only ever read as "did this note land" (the outbox sweep queues rows where
-- it is NULL) and is never shown through the API, so widening it to text costs nothing and
-- stops CallFinalizer from having to invent a number for a CRM that issues none.
--
-- idx_result_crm_pending is rebuilt by Postgres as part of the type change; its predicate
-- (crm_note_id IS NULL) means the same thing before and after.
ALTER TABLE call_result
    ALTER COLUMN crm_note_id TYPE VARCHAR(64) USING crm_note_id::text;
