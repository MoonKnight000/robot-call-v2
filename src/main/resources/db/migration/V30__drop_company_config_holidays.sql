-- 1. Copy any remaining dates in company_config.holidays to the holiday table
INSERT INTO holiday (company_id, holiday_date, name, kind, created_at)
SELECT cc.company_id, cast(trim(h.d) AS date), 'Dam olish kuni', 'CLOSED', now()
FROM company_config cc
CROSS JOIN LATERAL unnest(string_to_array(cc.holidays, ',')) AS h(d)
WHERE cc.holidays IS NOT NULL AND trim(h.d) <> ''
ON CONFLICT (company_id, holiday_date) DO NOTHING;

-- 2. Drop the redundant denormalized column
ALTER TABLE company_config DROP COLUMN holidays;
