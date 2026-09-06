CREATE TABLE knowledge_base_item (
    id                 BIGSERIAL PRIMARY KEY,
    company_id         BIGINT       NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    item_key           VARCHAR(100) NOT NULL,
    topic              VARCHAR(50)  NOT NULL,
    title              VARCHAR(255) NOT NULL,
    answer_uz          TEXT         NOT NULL,
    answer_ru          TEXT,
    answer_en          TEXT,
    keywords           TEXT         NOT NULL DEFAULT '',
    is_active          BOOLEAN      NOT NULL DEFAULT true,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_knowledge_company ON knowledge_base_item(company_id);
CREATE INDEX idx_knowledge_key ON knowledge_base_item(company_id, item_key);
CREATE INDEX idx_knowledge_active ON knowledge_base_item(company_id, is_active);

-- Seed initial knowledge items for default company
INSERT INTO knowledge_base_item (company_id, item_key, topic, title, answer_uz, answer_ru, answer_en, keywords, is_active)
VALUES
(1, 'payment_methods', 'payment', 'To''lov usullari',
 'To''lovni Click, Payme, Uzum Bank ilovalari orqali yoki bank kassalarida shartnoma raqamingizni ko''rsatib amalga oshirishingiz mumkin.',
 'Оплату можно произвести через приложения Click, Payme, Uzum Bank или в кассах банков, указав номер договора.',
 'You can make payments via Click, Payme, Uzum Bank mobile apps or at bank branches using your contract number.',
 'click,payme,to''lash,qayerga,qanday to''layman,оплатить,как оплатить,how to pay', true),
(1, 'court_mib', 'legal', 'MIB va Sud choralari',
 'To''lov kechiktirilsa, qonunchilikka asosan ish Majburiy ijro byurosiga (MIB) yoki sudga oshirilishi va hisob raqamlarga taqiq qo''yilishi mumkin.',
 'В случае задержки оплаты дело в соответствии с законом может быть передано в БПИ или суд с наложением ареста на счета.',
 'In case of prolonged non-payment, the case may be escalated to the enforcement bureau or court with account freezes.',
 'mib,sud,qonun,sudga,бпи,суд,court', true),
(1, 'restructuring', 'terms', 'Qayta ko''rib chiqish va restrukturizatsiya',
 'Agar moliyaviy qiyinchilik bo''lsa, bank filialiga ariza bilan murojaat qilib, to''lov muddatini uzaytirish yoki qayta ko''rib chiqishni so''rashingiz mumkin.',
 'При финансовых трудностях вы можете обратиться в филиал банка с заявлением о реструктуризации или продлении срока долга.',
 'If experiencing financial distress, you can visit a branch to request loan restructuring or installment adjustments.',
 'bo''lib to''lash,imtiyoz,sharoit,qiyin,рассрочка,реструктуризация', true),
(1, 'branch_locations', 'locations', 'Filial manzillari va ish tartibi',
 'Barcha filiallar dushanbadan jumagacha soat 9:00 dan 18:00 gacha ishlaydi. Eng yaqin filialni rasmiy veb-saytdan topishingiz mumkin.',
 'Все филиалы работают с понедельника по пятницу с 9:00 до 18:00. Ближайший филиал можно найти на официальном сайте.',
 'All branches operate Monday through Friday from 9:00 to 18:00. The nearest branch can be found on our official website.',
 'filial,manzil,ofis,филиал,адрес,branch,office', true);
