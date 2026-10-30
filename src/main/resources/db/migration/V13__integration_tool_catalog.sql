-- The tools an integration offers the voice agent, as the platform admin keeps them, and
-- what each company chose from them.
--
-- integration_tool: the catalog. default_enabled is what a company that never chose gets;
-- platform_enabled = false hides the tool from every company. kind BUILT_IN = the handler
-- is code (tool_key names it); permission/scope is the grant the CRM token needs.
CREATE TABLE integration_tool (
    id               BIGSERIAL PRIMARY KEY,
    provider         VARCHAR(20)  NOT NULL,
    tool_key         VARCHAR(64)  NOT NULL,
    kind             VARCHAR(16)  NOT NULL DEFAULT 'BUILT_IN',
    tool_group       VARCHAR(16)  NOT NULL,
    label            VARCHAR(120) NOT NULL,
    description      TEXT         NOT NULL,
    permission       VARCHAR(32),
    scope            VARCHAR(16),
    default_enabled  BOOLEAN      NOT NULL DEFAULT TRUE,
    platform_enabled BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order       INT          NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (provider, tool_key)
);

-- Only a company's own choice is a row; no row = it follows the tool's default.
CREATE TABLE company_integration_tool (
    company_id BIGINT  NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    tool_id    BIGINT  NOT NULL REFERENCES integration_tool(id) ON DELETE CASCADE,
    enabled    BOOLEAN NOT NULL,
    PRIMARY KEY (company_id, tool_id)
);

INSERT INTO integration_tool (provider, tool_key, tool_group, label, description, permission, scope, sort_order) VALUES
('UYSOT', 'SEARCH_FLATS', 'FLATS', 'Xonadon qidirish va narxlar',
 'Xona soni, byudjet, qavat va majmua bo''yicha sotuvdagi xonadonlar hamda ularning narxi.', 'CONTRACT', 'READ', 10),
('UYSOT', 'LIST_COMPLEXES', 'FLATS', 'Turar joy majmualari',
 'Majmualar ro''yxati: manzil, qavatlar, bloklar va topshirish muddati.', 'CONTRACT', 'READ', 20),
('UYSOT', 'REMEMBER_FLAT_INTEREST', 'FLATS', 'Yoqqan xonadonni eslab qolish',
 'Mijoz yoqtirgan xonadon keyingi suhbat uchun mijoz xotirasiga yoziladi.', NULL, NULL, 30),
('UYSOT', 'GET_CONTRACT_BALANCE', 'DEBTS', 'Shartnoma qoldig''i',
 'Shartnoma summasi, to''langani, qolgan qarz, kechikish kunlari va keyingi to''lov.', 'CONTRACT', 'READ', 40),
('UYSOT', 'GET_PAYMENT_SCHEDULE', 'DEBTS', 'To''lov jadvali',
 'To''lanmagan va muddati o''tgan oylik to''lovlar: sana va summa.', 'CONTRACT', 'READ', 50),
('UYSOT', 'GET_DEBT_SUMMARY', 'DEBTS', 'Qarzdorlik',
 'Mijozning barcha faol shartnomalari bo''yicha qarz va kechikish, eng ko''p kechikkanidan boshlab.', 'CONTRACT', 'READ', 60),
('UYSOT', 'GET_CLIENT_PROFILE', 'LEADS', 'Mijoz kartasi',
 'Raqam bo''yicha mijozni topish: ismi, lidi va faol shartnomalari.', 'LEAD', 'READ', 70),
('UYSOT', 'CREATE_LEAD', 'LEADS', 'Lid yaratish',
 'CRM''da yo''q mijoz uchun yangi lid ochadi.', 'LEAD', 'SAVE', 80),
('UYSOT', 'ADD_LEAD_NOTE', 'LEADS', 'Lidga izoh yozish',
 'Suhbat davomida aytilgan muhim narsani lid izohiga yozadi.', 'LEAD_NOTE', 'SAVE', 90),
('UYSOT', 'CREATE_FOLLOW_UP_TASK', 'LEADS', 'Vazifa qo''yish',
 'Menejer uchun qayta qo''ng''iroq yoki uchrashuv vazifasini belgilangan vaqtga qo''yadi.', 'LEAD_TASK', 'SAVE', 100),
('UYSOT', 'MOVE_LEAD_STAGE', 'LEADS', 'Lid bosqichini o''zgartirish',
 'Lidni voronkaning boshqa bosqichiga o''tkazadi.', 'LEAD', 'SAVE', 110),
('UYSOT', 'REQUEST_MANAGER_CALLBACK', 'LEADS', 'Menejer qo''ng''iroq qilishini so''rash',
 'Mijoz o''z menejeri qo''ng''iroq qilishini istasa, so''rov qo''ng''iroq natijasiga yoziladi.', NULL, NULL, 120),
('UYSOT', 'GET_BOOKING_STATUS', 'BOOKINGS', 'Bron holati',
 'Mijozning bronlari: xonadon, holat va muddat.', 'BOOKING', 'READ', 130);

-- V12 kept the choice as a list of switched-off names; it is now a row per switched-off tool.
INSERT INTO company_integration_tool (company_id, tool_id, enabled)
SELECT ci.company_id, t.id, FALSE
FROM crm_integration ci
         CROSS JOIN LATERAL unnest(string_to_array(ci.disabled_tools, ',')) AS x(name)
         JOIN integration_tool t ON t.provider = 'UYSOT' AND t.tool_key = trim(x.name)
WHERE ci.disabled_tools IS NOT NULL
ON CONFLICT DO NOTHING;

ALTER TABLE crm_integration DROP COLUMN disabled_tools;
