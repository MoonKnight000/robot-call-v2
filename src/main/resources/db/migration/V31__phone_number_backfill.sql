-- Numbers that so far lived only as text — an inbound route's DID, a trunk's caller ID, a caller ID
-- pool member — become phone_number rows of their company (BYO_SIP_TRUNK, ASSIGNED), so that a number
-- is one row that knows its trunk and its inbound agent (docs/PHONE-NUMBER-BINDING.md, phase 1).
-- The source rows are not touched; nothing reads phone_number at call time yet.
--
-- provider_trunk_id is set only where it is certain: the one trunk of the company whose caller ID the
-- number is, else the company's only trunk. Anything else stays NULL for an admin to choose
-- (PUT /api/phone-numbers/{id}/trunk).
-- A number already in phone_number (bought from the marketplace) is left as it is. A number several
-- companies name goes to the one whose enabled inbound route answers it, then a disabled route, then a
-- trunk caller ID, then a pool member; the lowest company id breaks a tie. The others get no row.

WITH source AS (
    SELECT company_id, did_number AS raw_phone, NULL::BIGINT AS trunk_id, ai_agent_id, call_flow_id,
           CASE WHEN enabled THEN 0 ELSE 1 END AS priority
    FROM inbound_route
    UNION ALL
    SELECT company_id, caller_id, id, NULL, NULL, 2
    FROM sip_trunk
    WHERE caller_id IS NOT NULL AND btrim(caller_id) <> ''
    UNION ALL
    SELECT p.company_id, n.phone, NULL, NULL, NULL, 3
    FROM caller_id_pool_number n
    JOIN caller_id_pool p ON p.id = n.pool_id
),
-- The same rules as PhoneNumbers.normalize: E.164, or a 3-4 digit internal extension as it is.
-- NULL is a value that is not a phone number; it gets no row.
normalized AS (
    SELECT company_id, trunk_id, ai_agent_id, call_flow_id, priority,
           CASE
               WHEN btrim(raw_phone) ~ '^\d{3,4}$' THEN btrim(raw_phone)
               WHEN raw_phone ~ '[@&#/?!a-zA-Z]' THEN NULL
               WHEN digits ~ '^\+\d{9,15}$' THEN digits
               WHEN digits ~ '^[389]\d{8}$' THEN '+998' || digits
               WHEN digits ~ '^8[389]\d{8}$' THEN '+998' || substr(digits, 2)
               WHEN digits ~ '^\d{10,15}$' THEN '+' || digits
           END AS phone
    FROM (SELECT s.*, regexp_replace(s.raw_phone, '[^0-9+]', '', 'g') AS digits FROM source s) cleaned
),
owner AS (
    SELECT DISTINCT ON (n.phone) n.phone, n.company_id
    FROM normalized n
    WHERE n.phone IS NOT NULL
      AND NOT EXISTS (SELECT 1 FROM phone_number pn WHERE pn.phone_number = n.phone)
    ORDER BY n.phone, n.priority, n.company_id
),
caller_id_trunk AS (
    SELECT n.phone, n.company_id, min(n.trunk_id) AS trunk_id, count(DISTINCT n.trunk_id) AS trunks
    FROM normalized n
    JOIN owner o ON o.phone = n.phone AND o.company_id = n.company_id
    WHERE n.trunk_id IS NOT NULL
    GROUP BY n.phone, n.company_id
),
only_trunk AS (
    SELECT company_id, min(id) AS trunk_id
    FROM sip_trunk
    GROUP BY company_id
    HAVING count(*) = 1
),
route AS (
    SELECT DISTINCT ON (n.phone) n.phone, n.ai_agent_id, n.call_flow_id
    FROM normalized n
    JOIN owner o ON o.phone = n.phone AND o.company_id = n.company_id
    WHERE n.priority <= 1
    ORDER BY n.phone, n.priority
)
INSERT INTO phone_number (company_id, phone_number, nickname, type, status, monthly_fee, price_per_channel,
                          allocated_channels, provider_trunk_id, inbound_agent_id, inbound_call_flow_id,
                          auto_renew)
SELECT o.company_id,
       o.phone,
       o.phone,
       'BYO_SIP_TRUNK',
       'ASSIGNED',
       0,
       0,
       1,
       CASE WHEN t.trunks = 1 THEN t.trunk_id WHEN t.trunks IS NULL THEN ot.trunk_id END,
       r.ai_agent_id,
       r.call_flow_id,
       false
FROM owner o
LEFT JOIN caller_id_trunk t ON t.phone = o.phone AND t.company_id = o.company_id
LEFT JOIN only_trunk ot ON ot.company_id = o.company_id
LEFT JOIN route r ON r.phone = o.phone
ON CONFLICT (phone_number) DO NOTHING;
