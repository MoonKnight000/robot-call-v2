-- campaign.caller_id_pool_id has existed since V1 but was never read; the campaign now names the pool whose
-- numbers its calls present as caller ID. With local_presence the number closest to the callee's own is
-- picked from that pool instead of the pool's rotation strategy alone.
ALTER TABLE campaign ADD COLUMN local_presence BOOLEAN NOT NULL DEFAULT FALSE;
