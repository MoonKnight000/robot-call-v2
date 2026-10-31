-- The rules an AUTO_ENROLL campaign adds contacts by ({"matchAll": true, "rules": [{field, operator, value}]});
-- NULL for a STANDARD campaign and for an auto-enroll one whose rules are not written yet.
ALTER TABLE campaign ADD COLUMN auto_enroll JSONB;
