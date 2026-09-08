-- A chained target source: several requests instead of one.
--
-- V7 added the provider column with two values — GENERIC (one request to a URL) and
-- UYSOT_DEBTORS (a chain compiled into the platform). The second one is the useful shape
-- and the wrong way to offer it: every company whose CRM also splits "who is overdue" from
-- "what is their phone number" would need its own provider written for it.
--
-- CHAINED is that shape as configuration. steps holds an ordered array of requests: the
-- first lists rows, each later one is called per row to add what the list did not carry.
-- Null for every other provider.
ALTER TABLE campaign_target_source
    ADD COLUMN steps JSONB;
