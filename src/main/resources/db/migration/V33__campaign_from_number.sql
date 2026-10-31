-- A campaign may present one fixed number of the company's instead of a caller ID pool: its calls then
-- go out on that number's own trunk, whichever agent speaks (docs/PHONE-NUMBER-BINDING.md, phase 3).
-- At most one of from_number_id and caller_id_pool_id is set. Like the pool, a number that is deleted
-- leaves the campaign with neither — it then dials as before, over the agent's trunks.
ALTER TABLE campaign
    ADD COLUMN from_number_id BIGINT REFERENCES phone_number (id) ON DELETE SET NULL;
