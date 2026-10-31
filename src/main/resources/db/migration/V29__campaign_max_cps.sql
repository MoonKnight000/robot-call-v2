-- A campaign's own calls-per-second cap (Retell "Calls per second (CPS)"): how many new calls it may start in one
-- second, on top of each SIP trunk's max_cps. NULL — no cap of its own, the trunks' full CPS.
ALTER TABLE campaign ADD COLUMN max_cps INTEGER;
