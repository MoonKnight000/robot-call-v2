-- Extra calling rules per campaign (time zone, hours per weekday, holidays) as JSON.
-- NULL means none: the plain dial window and days apply as before.
ALTER TABLE campaign ADD COLUMN dial_rules JSONB;
