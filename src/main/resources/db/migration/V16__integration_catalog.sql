-- The integration catalog, kept by the platform admin: what companies are offered to connect, the
-- heading each is listed under and how it connects. It replaces the fixed IntegrationProvider enum.

CREATE TABLE integration_category (
    category_key VARCHAR(32)  PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    sort_order   INT          NOT NULL DEFAULT 0
);

-- auth_methods: comma-separated OAUTH / API_TOKEN / LOGIN_PASSWORD, the preferred one first.
-- available: a connector for it exists in the code; set by a release, never by the admin. false —
-- companies see "coming soon". built_in: shipped with the platform, cannot be deleted.
CREATE TABLE integration_provider (
    provider_key VARCHAR(32)  PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    category_key VARCHAR(32)  NOT NULL REFERENCES integration_category(category_key),
    auth_methods VARCHAR(100) NOT NULL,
    available    BOOLEAN      NOT NULL DEFAULT false,
    sort_order   INT          NOT NULL DEFAULT 0,
    built_in     BOOLEAN      NOT NULL DEFAULT false
);

INSERT INTO integration_category (category_key, name, sort_order) VALUES
    ('CRM',            'CRM tizimlari',   10),
    ('FILE_MANAGER',   'Fayl menejeri',   20),
    ('KNOWLEDGE_BASE', 'Bilimlar bazasi', 30),
    ('SMS',            'SMS',             40),
    ('TELEPHONY',      'Telefoniya',      50);

INSERT INTO integration_provider (provider_key, name, category_key, auth_methods, available, sort_order, built_in) VALUES
    ('UYSOT',        'Uysot CRM',                  'CRM',            'OAUTH',          true,  10, true),
    ('AMOCRM',       'amoCRM',                     'CRM',            'API_TOKEN',      true,  20, true),
    ('KOMMO',        'Kommo CRM (Global amoCRM)',  'CRM',            'API_TOKEN',      true,  30, true),
    ('BITRIX24',     'Bitrix24',                   'CRM',            'API_TOKEN',      true,  40, true),
    ('GOOGLE_DRIVE', 'Google Drive',               'FILE_MANAGER',   'OAUTH',          false, 50, true),
    ('NOTION',       'Notion',                     'KNOWLEDGE_BASE', 'OAUTH',          false, 60, true),
    ('ESKIZ',        'Eskiz SMS',                  'SMS',            'LOGIN_PASSWORD', false, 70, true),
    ('ASTERISK',     'Asterisk',                   'TELEPHONY',      'LOGIN_PASSWORD', false, 80, true);

-- A deleted catalog entry takes the switches and the logo that named it.
ALTER TABLE company_integration_access
    ADD CONSTRAINT fk_company_integration_access_provider
    FOREIGN KEY (provider) REFERENCES integration_provider(provider_key) ON DELETE CASCADE;
ALTER TABLE integration_provider_icon
    ADD CONSTRAINT fk_integration_provider_icon_provider
    FOREIGN KEY (provider) REFERENCES integration_provider(provider_key) ON DELETE CASCADE;
