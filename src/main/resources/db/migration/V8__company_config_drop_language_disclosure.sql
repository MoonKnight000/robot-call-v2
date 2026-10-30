-- A company no longer has its own languages or disclosure wording. An agent's language and the
-- languages it has voices for decide what a call is held in and detected as; the §11.1 notice
-- comes from the scenario's own text or the platform's line.
DROP TABLE company_config_language;
ALTER TABLE company_config DROP COLUMN default_language;
ALTER TABLE company_config DROP COLUMN disclosure_text;
