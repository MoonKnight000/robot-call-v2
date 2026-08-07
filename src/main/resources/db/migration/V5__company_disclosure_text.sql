-- The §11.1 opening disclosure becomes a company setting.
--
-- It was a constant in the code (DialogPhrases.disclosure) with a per-scenario override.
-- Neither is where it belongs: the notice says who is calling and that they are a machine
-- recording the call, which is a fact about the *tenant*, not about what the conversation
-- is for. A company owner has to be able to read the sentence their calls open with, and
-- change its wording, without a redeploy.
--
-- Every existing company is provisioned with the platform's own wording so nothing about
-- what callers hear changes here. {company} is filled in with the company name at call
-- time, which is why one text serves every tenant.
ALTER TABLE company_config ADD COLUMN disclosure_text VARCHAR(500);

UPDATE company_config
SET disclosure_text =
        'Assalomu alaykum! Bu {company} kompaniyasining avtomatik ovozli xizmati. Suhbat yozib olinmoqda.'
WHERE disclosure_text IS NULL;

-- The builtin scenarios carried their own disclosure ("Bu qo'ng'iroq avtomatik tizim
-- tomonidan amalga oshirilmoqda va yozib olinmoqda."). A builtin is shared by every
-- tenant, so a disclosure baked into one cannot name the company that is calling — and it
-- would now win over the company setting above and hide it. Dropping the key leaves the
-- scenario override available for a scenario that genuinely needs different wording
-- (a custom one an operator writes), while the default comes from the company.
UPDATE scenario
SET definition = definition - 'disclosureText'
WHERE is_builtin = TRUE
  AND definition ->> 'disclosureText' IS NOT NULL;
