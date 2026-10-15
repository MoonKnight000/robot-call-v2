-- How many calls the provider lets this trunk carry at once (its channel count, from the
-- contract). Null: not limited by us — the provider still refuses past its own limit, which
-- the dialer then learns from (TrunkCapacityTracker).
ALTER TABLE sip_trunk
    ADD COLUMN max_channels INT,
    ADD CONSTRAINT ck_sip_trunk_max_channels CHECK (max_channels IS NULL OR max_channels > 0);
