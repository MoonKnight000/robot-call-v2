-- The V2 seed created the MANUAL placeholder campaign as ACTIVE, so the dialer picks
-- it up on every tick (default: every 5 seconds) and scans it for due targets. It
-- never has any — its only target is the permanent 'MANUAL' placeholder — so the work
-- is pure noise. The campaign is only a parent row for manual/auto-started calls;
-- it is not meant to be dialled.
UPDATE campaign SET status = 'DRAFT' WHERE name = 'MANUAL';
