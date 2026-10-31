-- A caller ID pool member is now one of the company's registered phone numbers, not free text: the
-- number's trunk is the trunk its calls go out on, so the caller ID and the trunk always belong
-- together (docs/PHONE-NUMBER-BINDING.md, phase 2). Removing the number removes it from every pool.
ALTER TABLE caller_id_pool_number
    ADD COLUMN phone_number_id BIGINT REFERENCES phone_number (id) ON DELETE CASCADE;

-- Links each member to its company's phone_number row (V31 created one for every pool member it
-- could). The same rules as PhoneNumbers.normalize; a member left unlinked — not a number, or
-- given by V31 to another company — is never chosen to dial from.
UPDATE caller_id_pool_number n
SET phone_number_id = pn.id
FROM caller_id_pool p,
     phone_number pn
WHERE p.id = n.pool_id
  AND pn.company_id = p.company_id
  AND pn.phone_number = CASE
          WHEN btrim(n.phone) ~ '^\d{3,4}$' THEN btrim(n.phone)
          WHEN n.phone ~ '[@&#/?!a-zA-Z]' THEN NULL
          WHEN regexp_replace(n.phone, '[^0-9+]', '', 'g') ~ '^\+\d{9,15}$'
              THEN regexp_replace(n.phone, '[^0-9+]', '', 'g')
          WHEN regexp_replace(n.phone, '[^0-9+]', '', 'g') ~ '^[389]\d{8}$'
              THEN '+998' || regexp_replace(n.phone, '[^0-9+]', '', 'g')
          WHEN regexp_replace(n.phone, '[^0-9+]', '', 'g') ~ '^8[389]\d{8}$'
              THEN '+998' || substr(regexp_replace(n.phone, '[^0-9+]', '', 'g'), 2)
          WHEN regexp_replace(n.phone, '[^0-9+]', '', 'g') ~ '^\d{10,15}$'
              THEN '+' || regexp_replace(n.phone, '[^0-9+]', '', 'g')
      END;

CREATE INDEX idx_caller_id_pool_number_phone_number ON caller_id_pool_number (phone_number_id);
