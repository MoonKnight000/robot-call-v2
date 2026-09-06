-- Seed data: bootstrap tenant, TTS voice catalog, built-in scenario templates, and the
-- MANUAL/INBOUND placeholder campaigns. Flyway repeatable migration — reruns whenever
-- this file's checksum changes, so every statement below guards against re-insertion.

-- Bootstrap tenant every other row below belongs to. Explicit id=1 so it matches
-- voice-agent.company.default-id's own default.
INSERT INTO company (id, name, status) VALUES (1, 'Default', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;
SELECT setval(pg_get_serial_sequence('company', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 0) FROM company), 1), true);

-- disclosure_text: the §11.1 notice this company's calls open with. {company} is
-- filled in at call time, so the same wording serves every tenant; blank would fall back
-- to the platform's own line, but every company is provisioned with it explicitly so an
-- owner can see and edit what their callers hear.
INSERT INTO company_config (id, company_id, dial_window_start, dial_window_end, timezone, default_language,
                            disclosure_text)
VALUES (1, 1, '07:00:00', '23:00:00', 'Asia/Tashkent', 'uz-UZ',
        'Assalomu alaykum! Bu {company} kompaniyasining avtomatik ovozli xizmati. Suhbat yozib olinmoqda.')
ON CONFLICT (company_id) DO NOTHING;
SELECT setval(pg_get_serial_sequence('company_config', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 0) FROM company_config), 1), true);

INSERT INTO company_config_language (company_config_id, ord, language)
SELECT id, 0, 'uz-UZ' FROM company_config WHERE company_id = 1
ON CONFLICT (company_config_id, ord) DO NOTHING;

INSERT INTO company_config_language (company_config_id, ord, language)
SELECT id, 1, 'ru-RU' FROM company_config WHERE company_id = 1
ON CONFLICT (company_config_id, ord) DO NOTHING;
-- System roles for the bootstrap tenant (V10). The admin below needs one to point at, and
-- on a fresh database this file runs after V10, when no company existed for V10 to seed.
INSERT INTO app_role (company_id, code, name, is_system)
VALUES (1, 'DEVELOPER', 'Developer', true),
       (1, 'ADMIN', 'Administrator', true),
       (1, 'OPERATOR', 'Operator', true),
       (1, 'VIEWER', 'Viewer', true),
       (1, 'SUPERADMIN', 'Superadmin', true)
ON CONFLICT (company_id, code) DO NOTHING;
SELECT setval(pg_get_serial_sequence('app_role', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 0) FROM app_role), 1), true);

-- First admin account (ROADMAP E.1) — without this nobody can call POST /api/auth/login
-- to create the rest through the API. password_hash is a BCrypt digest of 'murodjon'
-- (BCryptPasswordEncoder, matching SecurityConfig.passwordEncoder), never stored plaintext.
INSERT INTO app_user (company_id, name, email, username, password_hash, role_id, status)
SELECT 1, 'Murodjon', 'murodjon000@softex.uz', 'murodjon',
       '$2a$10$6cGgfpkqI8lHvRx7rlHJKuW7PhH1XttNnYBGhe.lc4rqSMORrizFO', r.id, 'ACTIVE'
FROM app_role r
WHERE r.company_id = 1 AND r.code = 'ADMIN'
ON CONFLICT (username) DO NOTHING;
SELECT setval(pg_get_serial_sequence('app_user', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 0) FROM app_user), 1), true);

-- TTS Voice Catalog: Yandex, Aisha, Gemini Live, and Gemini TTS voices
INSERT INTO tts_voice (id, provider, language, name, label, role) VALUES
    ('nigora',           'yandex',      'uz-UZ', 'nigora',   'Nigora — o''zbek, ayol', NULL),
    ('zamira',           'yandex',      'uz-UZ', 'zamira',   'Zamira — o''zbek, ayol', NULL),
    ('yulduz',           'yandex',      'uz-UZ', 'yulduz',   'Yulduz — o''zbek, ayol', NULL),
    ('alena',            'yandex',      'ru-RU', 'alena',    'Alena — rus, ayol', NULL),
    ('jane',             'yandex',      'ru-RU', 'jane',     'Jane — rus, ayol', NULL),
    ('omazh',            'yandex',      'ru-RU', 'omazh',    'Omazh — rus, ayol', NULL),
    ('filipp',           'yandex',      'ru-RU', 'filipp',   'Filipp — rus, erkak', NULL),
    ('ermil',            'yandex',      'ru-RU', 'ermil',    'Ermil — rus, erkak', NULL),
    ('zahar',            'yandex',      'ru-RU', 'zahar',    'Zahar — rus, erkak', NULL),
    -- Aisha (Toshkent)
    ('gulnoza',          'aisha',       'uz-UZ', 'neutral',  'Gulnoza — o''zbek, moslashuvchan (avto-hissiyot)', NULL),
    ('gulnoza-neutral',  'aisha',       'uz-UZ', 'neutral',  'Gulnoza — o''zbek, neytral', NULL),
    ('gulnoza-cheerful', 'aisha',       'uz-UZ', 'cheerful', 'Gulnoza — o''zbek, quvnoq', NULL),
    ('gulnoza-happy',    'aisha',       'uz-UZ', 'happy',    'Gulnoza — o''zbek, xursand', NULL),
    ('gulnoza-sad',      'aisha',       'uz-UZ', 'sad',      'Gulnoza — o''zbek, xafa', NULL),
    -- Gemini Live
    ('gemini-aoede-uz',  'gemini-live', 'uz-UZ', 'Aoede',    'Aoede (ayol)', NULL),
    ('gemini-kore-uz',   'gemini-live', 'uz-UZ', 'Kore',     'Kore (ayol)', NULL),
    ('gemini-puck-uz',   'gemini-live', 'uz-UZ', 'Puck',     'Puck (erkak)', NULL),
    ('gemini-charon-uz', 'gemini-live', 'uz-UZ', 'Charon',   'Charon (erkak)', NULL),
    ('gemini-fenrir-uz', 'gemini-live', 'uz-UZ', 'Fenrir',   'Fenrir (erkak)', NULL),
    ('gemini-aoede-ru',  'gemini-live', 'ru-RU', 'Aoede',    'Aoede (женский)', NULL),
    ('gemini-kore-ru',   'gemini-live', 'ru-RU', 'Kore',     'Kore (женский)', NULL),
    ('gemini-puck-ru',   'gemini-live', 'ru-RU', 'Puck',     'Puck (мужской)', NULL),
    ('gemini-charon-ru', 'gemini-live', 'ru-RU', 'Charon',   'Charon (мужской)', NULL),
    ('gemini-fenrir-ru', 'gemini-live', 'ru-RU', 'Fenrir',   'Fenrir (женский)', NULL),
    -- Gemini TTS (Cascade mode: gemini-3.1-flash-tts-preview)
    ('gemini-tts-aoede-uz',  'gemini',   'uz-UZ', 'Aoede',    'Aoede (ayol, Gemini TTS)', NULL),
    ('gemini-tts-kore-uz',   'gemini',   'uz-UZ', 'Kore',     'Kore (ayol, Gemini TTS)', NULL),
    ('gemini-tts-puck-uz',   'gemini',   'uz-UZ', 'Puck',     'Puck (erkak, Gemini TTS)', NULL),
    ('gemini-tts-charon-uz', 'gemini',   'uz-UZ', 'Charon',   'Charon (erkak, Gemini TTS)', NULL),
    ('gemini-tts-fenrir-uz', 'gemini',   'uz-UZ', 'Fenrir',   'Fenrir (erkak, Gemini TTS)', NULL),
    ('gemini-tts-aoede-ru',  'gemini',   'ru-RU', 'Aoede',    'Aoede (женский, Gemini TTS)', NULL),
    ('gemini-tts-kore-ru',   'gemini',   'ru-RU', 'Kore',     'Kore (женский, Gemini TTS)', NULL),
    ('gemini-tts-puck-ru',   'gemini',   'ru-RU', 'Puck',     'Puck (мужской, Gemini TTS)', NULL),
    ('gemini-tts-charon-ru', 'gemini',   'ru-RU', 'Charon',   'Charon (мужской, Gemini TTS)', NULL),
    ('gemini-tts-fenrir-ru', 'gemini',   'ru-RU', 'Fenrir',   'Fenrir (мужской, Gemini TTS)', NULL)
ON CONFLICT (id) DO NOTHING;

-- Built-in scenario templates. They are read-only through the API
-- (SCENARIO_BUILTIN_READONLY), so this file owns their text: an already-seeded row has
-- its definition refreshed instead of being left on the wording it first got.
INSERT INTO scenario(scenario_key, version, name, description, is_builtin, is_active, definition, created_by)
VALUES (
    'debt-collection', 1,
    $$Qarzdorlik bo'yicha qo'ng'iroq$$,
    $$Hozirgi ssenariy, aynan shu xatti-harakat bilan: mijozga qarzi haqida xabar berish, to'lov sanasini kelishish yoki rad sababini yozib olish.$$,
    true, true,
$def$
{
  "stages": [
    {"id": "GREETING", "purpose": "Salomlash, tizim ekaningni ayt, suhbat yozib olinishini bildiring.", "allowedTransitions": ["IDENTITY_CHECK", "END_CALL", "ESCALATE_TO_HUMAN"], "allowedTools": []},
    {"id": "IDENTITY_CHECK", "purpose": "Mijozning shaxsini tasdiqla (masalan: 'Men [Ism] aka bilan gaplashayapmanmi?').", "allowedTransitions": ["DEBT_NOTICE", "END_CALL", "ESCALATE_TO_HUMAN"], "allowedTools": []},
    {"id": "DEBT_NOTICE", "purpose": "Qarz miqdori va muddatini xushmuomala yetkaz VA o'sha javobning o'zida nima uchun to'lanmaganini so'ra. Faqat xabar aytib, tasdiq so'rab ('bu haqda xabaringiz bormidi?') alohida turn sarflama.", "allowedTransitions": ["REASON_INQUIRY", "ESCALATE_TO_HUMAN", "END_CALL"], "allowedTools": ["recordPaymentPromise", "recordRefusalReason"]},
    {"id": "REASON_INQUIRY", "purpose": "To'lov nega kechikayotganini bilib ol.", "allowedTransitions": ["PAYMENT_DATE", "ESCALATE_TO_HUMAN", "END_CALL"], "allowedTools": ["recordPaymentPromise", "recordRefusalReason"]},
    {"id": "PAYMENT_DATE", "purpose": "Mijozdan aniq to'lov sanasini ol.", "allowedTransitions": ["CONFIRMATION", "ESCALATE_TO_HUMAN", "END_CALL"], "allowedTools": ["recordPaymentPromise", "recordRefusalReason"]},
    {"id": "CONFIRMATION", "purpose": "Kelishilgan sana va summani BITTA xabar gapi bilan takrorla (savol emas, qayta tasdiqlatma) va xayrlash.", "allowedTransitions": ["CLOSING", "PAYMENT_DATE", "ESCALATE_TO_HUMAN"], "allowedTools": ["recordPaymentPromise", "recordRefusalReason"]},
    {"id": "CLOSING", "purpose": "Xushmuomala xayrlash.", "allowedTransitions": ["END_CALL"], "allowedTools": []},
    {"id": "ESCALATE_TO_HUMAN", "purpose": "Operatorga o'tkazishni bildirib xayrlash.", "allowedTransitions": ["END_CALL"], "allowedTools": []},
    {"id": "END_CALL", "purpose": "Qo'ng'iroqni yakunlash.", "allowedTransitions": [], "allowedTools": []}
  ],
  "factSchema": [
    {"name": "clientName", "type": "string", "required": true},
    {"name": "debtAmount", "type": "number", "required": true},
    {"name": "currency", "type": "string", "required": true},
    {"name": "dueDate", "type": "date", "required": false},
    {"name": "contractNumber", "type": "string", "required": false}
  ],
  "tools": [
    {"name": "recordPaymentPromise", "description": "Mijoz to'lov sanasini va'da qilganda chaqiring", "params": [
      {"name": "date", "type": "date", "required": true, "constraint": "kelajakda bo'lishi shart"},
      {"name": "amount", "type": "number", "required": false, "constraint": null},
      {"name": "note", "type": "string", "required": false, "constraint": null}
    ]},
    {"name": "recordRefusalReason", "description": "Mijoz to'lovni rad etganda sababini yozib oling", "params": [
      {"name": "reason", "type": "string", "required": true, "constraint": null}
    ]}
  ],
  "outcomeSchema": [
    {"name": "promisedDate", "type": "date", "description": "Mijoz va'da qilgan to'lov sanasi"},
    {"name": "promisedAmount", "type": "number", "description": "Mijoz va'da qilgan summa"},
    {"name": "reasonCode", "type": "string", "description": "Rad etish sababi"}
  ],
  "rolePrompt": "Siz \"Uysot\" kompaniyasining avtomatik qarz undirish ovozli agentisiz.",
  "guardrails": [
    "Qarz summasini HECH QACHON o'zgartirma. Faqat berilgan raqamni ayt.",
    "Chegirma, imtiyoz yoki qarz kechirishni HECH QACHON taklif qilma.",
    "To'lov muddatini o'zing uzaytirma — faqat mijoz aytgan sanani yozib ol.",
    "Mijoz nisbiy sana aytsa (\"ertaga\", \"dushanba\", \"kelasi oyning 5-sanasi\") — uni BUGUNGI SANAdan hisoblab yyyy-MM-dd ko'rinishida recordPaymentPromise'ga ber. Yilni o'zingdan to'qima.",
    "Huquqiy oqibatlar, sud, jarima yoki ijro haqida o'zingdan gapirma, qo'rqitma.",
    "Mijozga 'suhbatdoshim' yoki 'mijoz' deb gapirma. 'Siz [Ism]misiz?' deb so'rama — xuddi tirik operatordek: 'Men [Ism] aka bilan gaplashayapmanmi?' yoki '[Ism] aka, sizmisiz?' deb so'ra."
  ]
}
$def$::jsonb,
    NULL
)
ON CONFLICT (scenario_key) WHERE is_active DO UPDATE
    SET definition = EXCLUDED.definition WHERE scenario.is_builtin;

INSERT INTO scenario(scenario_key, version, name, description, is_builtin, is_active, definition, created_by)
VALUES (
    'lead-qualification', 1,
    $$Potensial mijozni aniqlash$$,
    $$Potensial mijozning qiziqishi, byudjeti, muddati va uchrashuv vaqtini aniqlash.$$,
    true, true,
$def$
{
  "stages": [
    {"id": "GREETING", "purpose": "Salomlashish va qo'ng'iroq maqsadini tushuntirish", "allowedTransitions": ["INTEREST_CHECK", "ESCALATE_TO_HUMAN", "END_CALL"]},
    {"id": "INTEREST_CHECK", "purpose": "Mahsulotga qiziqish darajasini aniqlash", "allowedTransitions": ["BUDGET_TIMELINE", "CLOSING", "ESCALATE_TO_HUMAN", "END_CALL"]},
    {"id": "BUDGET_TIMELINE", "purpose": "Byudjet va muddatni so'rash", "allowedTransitions": ["MEETING_SCHEDULING", "CLOSING", "ESCALATE_TO_HUMAN", "END_CALL"]},
    {"id": "MEETING_SCHEDULING", "purpose": "Uchrashuv vaqtini kelishish", "allowedTransitions": ["CLOSING", "ESCALATE_TO_HUMAN", "END_CALL"]},
    {"id": "CLOSING", "purpose": "Suhbatni yakunlash", "allowedTransitions": ["END_CALL"]},
    {"id": "ESCALATE_TO_HUMAN", "purpose": "Operatorga uzatish", "allowedTransitions": ["END_CALL"]},
    {"id": "END_CALL", "purpose": "Qo'ng'iroqni tugatish", "allowedTransitions": []}
  ],
  "factSchema": [
    {"name": "clientName", "type": "string", "required": false},
    {"name": "productName", "type": "string", "required": false}
  ],
  "tools": [
    {"name": "recordInterest", "description": "Mijozning qiziqish darajasini yozib oling", "params": [
      {"name": "level", "type": "string", "required": true, "constraint": null}
    ]},
    {"name": "recordBudget", "description": "Mijoz aytgan taxminiy byudjetni yozib oling", "params": [
      {"name": "amount", "type": "number", "required": false, "constraint": null}
    ]},
    {"name": "recordMeeting", "description": "Kelishilgan uchrashuv vaqtini yozib oling", "params": [
      {"name": "dateTime", "type": "string", "required": true, "constraint": "kelajakda bo'lishi shart"}
    ]}
  ],
  "outcomeSchema": [
    {"name": "interest", "type": "string", "description": "Qiziqish darajasi"},
    {"name": "budget", "type": "number", "description": "Taxminiy byudjet"},
    {"name": "meetingAt", "type": "string", "description": "Kelishilgan uchrashuv vaqti"}
  ],
  "rolePrompt": "Siz mahsulotga qiziqish bildirgan potensial mijoz bilan suhbatlashadigan savdo agentisiz. Qiziqish darajasini, byudjetni va uchrashuv vaqtini aniqlang.",
  "guardrails": [
    "Narx yoki chegirma bo'yicha rasmiy bo'lmagan va'da bermang"
  ]
}
$def$::jsonb,
    NULL
)
ON CONFLICT (scenario_key) WHERE is_active DO UPDATE
    SET definition = EXCLUDED.definition WHERE scenario.is_builtin;

INSERT INTO scenario(scenario_key, version, name, description, is_builtin, is_active, definition, created_by)
VALUES (
    'notification', 1,
    $$Bir tomonlama xabar$$,
    $$Xabar yetkazish va tasdiqlash (to'lov eslatmasi, TDS va boshqalar).$$,
    true, true,
$def$
{
  "stages": [
    {"id": "GREETING", "purpose": "Salomlashish", "allowedTransitions": ["DELIVER_MESSAGE", "END_CALL"]},
    {"id": "DELIVER_MESSAGE", "purpose": "Xabarni yetkazish", "allowedTransitions": ["CONFIRM", "CLOSING", "END_CALL"]},
    {"id": "CONFIRM", "purpose": "Xabar tushunilganini tasdiqlash", "allowedTransitions": ["CLOSING", "END_CALL"]},
    {"id": "CLOSING", "purpose": "Suhbatni yakunlash", "allowedTransitions": ["END_CALL"]},
    {"id": "END_CALL", "purpose": "Qo'ng'iroqni tugatish", "allowedTransitions": []}
  ],
  "factSchema": [
    {"name": "messageText", "type": "string", "required": true}
  ],
  "tools": [
    {"name": "recordAcknowledged", "description": "Mijoz xabarni tasdiqlaganda yoki rad etganda chaqiring", "params": [
      {"name": "acknowledged", "type": "boolean", "required": true, "constraint": null}
    ]}
  ],
  "outcomeSchema": [
    {"name": "acknowledged", "type": "boolean", "description": "Mijoz xabarni tasdiqladimi"}
  ],
  "rolePrompt": "Siz mijozga muhim xabarni yetkazadigan avtomatik agentisiz. Xabarni aniq va qisqa yetkazing, keyin tushunganini tasdiqlang.",
  "guardrails": [
    "Berilgan xabar matnidan tashqari qo'shimcha va'da bermang"
  ]
}
$def$::jsonb,
    NULL
)
ON CONFLICT (scenario_key) WHERE is_active DO UPDATE
    SET definition = EXCLUDED.definition WHERE scenario.is_builtin;

INSERT INTO scenario(scenario_key, version, name, description, is_builtin, is_active, definition, created_by)
VALUES (
    'survey', 1,
    $$So'rovnoma$$,
    $$3-5 savollik so'rovnoma yoki NPS o'tkazish.$$,
    true, true,
$def$
{
  "stages": [
    {"id": "GREETING", "purpose": "Salomlashish va so'rovnoma haqida qisqacha ma'lumot", "allowedTransitions": ["QUESTION_1", "END_CALL"]},
    {"id": "QUESTION_1", "purpose": "Birinchi savolni berish", "allowedTransitions": ["QUESTION_2", "CLOSING", "END_CALL"]},
    {"id": "QUESTION_2", "purpose": "Ikkinchi savolni berish", "allowedTransitions": ["QUESTION_3", "CLOSING", "END_CALL"]},
    {"id": "QUESTION_3", "purpose": "Uchinchi savolni berish", "allowedTransitions": ["CLOSING", "END_CALL"]},
    {"id": "CLOSING", "purpose": "Rahmat aytish va yakunlash", "allowedTransitions": ["END_CALL"]},
    {"id": "END_CALL", "purpose": "Qo'ng'iroqni tugatish", "allowedTransitions": []}
  ],
  "factSchema": [
    {"name": "topicName", "type": "string", "required": false}
  ],
  "tools": [
    {"name": "recordAnswer", "description": "Har bir savolga javobni yozib oling", "params": [
      {"name": "question", "type": "string", "required": true, "constraint": null},
      {"name": "answer", "type": "string", "required": true, "constraint": null}
    ]},
    {"name": "recordScore", "description": "NPS yoki umumiy bahoni yozib oling", "params": [
      {"name": "score", "type": "number", "required": false, "constraint": null}
    ]}
  ],
  "outcomeSchema": [
    {"name": "answers", "type": "array", "description": "Har bir savol-javob jufti"},
    {"name": "score", "type": "number", "description": "Umumiy baho"}
  ],
  "rolePrompt": "Siz qisqa so'rovnoma o'tkazadigan agentisiz. Savollarni neytral tarzda bering va javoblarni aniq yozib oling.",
  "guardrails": [
    "Savollarni yetakchilik qilmasdan, neytral tarzda bering",
    "Mijozning javobini talqin qilib o'zgartirmang"
  ]
}
$def$::jsonb,
    NULL
)
ON CONFLICT (scenario_key) WHERE is_active DO UPDATE
    SET definition = EXCLUDED.definition WHERE scenario.is_builtin;

INSERT INTO scenario(scenario_key, version, name, description, is_builtin, is_active, definition, created_by)
VALUES (
    'appointment-reminder', 1,
    $$Uchrashuvni eslatish$$,
    $$Uchrashuvni eslatish, tasdiqlash yoki ko'chirish so'rovini yozib olish.$$,
    true, true,
$def$
{
  "stages": [
    {"id": "GREETING", "purpose": "Salomlashish", "allowedTransitions": ["REMIND", "END_CALL"]},
    {"id": "REMIND", "purpose": "Uchrashuv vaqtini eslatish", "allowedTransitions": ["CONFIRM_OR_RESCHEDULE", "CLOSING", "END_CALL"]},
    {"id": "CONFIRM_OR_RESCHEDULE", "purpose": "Tasdiqlash yoki ko'chirish so'rovini olish", "allowedTransitions": ["CLOSING", "END_CALL"]},
    {"id": "CLOSING", "purpose": "Suhbatni yakunlash", "allowedTransitions": ["END_CALL"]},
    {"id": "END_CALL", "purpose": "Qo'ng'iroqni tugatish", "allowedTransitions": []}
  ],
  "factSchema": [
    {"name": "appointmentTime", "type": "date", "required": true},
    {"name": "clientName", "type": "string", "required": false}
  ],
  "tools": [
    {"name": "recordConfirmation", "description": "Mijoz uchrashuvni tasdiqlaganda yoki rad etganda chaqiring", "params": [
      {"name": "confirmed", "type": "boolean", "required": true, "constraint": null}
    ]},
    {"name": "recordReschedule", "description": "Mijoz uchrashuvni ko'chirishni so'raganda yangi vaqtni yozib oling", "params": [
      {"name": "newTime", "type": "string", "required": true, "constraint": "kelajakda bo'lishi shart"}
    ]}
  ],
  "outcomeSchema": [
    {"name": "confirmed", "type": "boolean", "description": "Mijoz uchrashuvni tasdiqladimi"},
    {"name": "newTime", "type": "string", "description": "So'ralgan yangi uchrashuv vaqti, bo'lsa"}
  ],
  "rolePrompt": "Siz mijozga uchrashuvini eslatadigan agentisiz. Uchrashuv vaqtini tasdiqlang yoki ko'chirish so'rovini aniq yozib oling.",
  "guardrails": [
    "Uchrashuvni o'zingiz bekor qilmang, faqat ko'chirish so'rovini yozib oling"
  ]
}
$def$::jsonb,
    NULL
)
ON CONFLICT (scenario_key) WHERE is_active DO UPDATE
    SET definition = EXCLUDED.definition WHERE scenario.is_builtin;

INSERT INTO scenario(scenario_key, version, name, description, is_builtin, is_active, definition, created_by)
VALUES (
    'reception', 1,
    $$Qabulxona$$,
    $$Kiruvchi qo'ng'iroqqa javob: savolga mavjud ma'lumotlar asosida javob, kerak bo'lsa operatorga.$$,
    true, true,
$def$
{
  "stages": [
    {"id": "GREETING", "purpose": "Salomlashish va qo'ng'iroq sababini so'rash", "allowedTransitions": ["ANSWER_QUESTION", "ESCALATE_TO_HUMAN", "END_CALL"]},
    {"id": "ANSWER_QUESTION", "purpose": "Mijozning savoliga faktlar asosida javob berish", "allowedTransitions": ["CLOSING", "ESCALATE_TO_HUMAN", "END_CALL"]},
    {"id": "CLOSING", "purpose": "Suhbatni yakunlash", "allowedTransitions": ["END_CALL"]},
    {"id": "ESCALATE_TO_HUMAN", "purpose": "Operatorga uzatish", "allowedTransitions": ["END_CALL"]},
    {"id": "END_CALL", "purpose": "Qo'ng'iroqni tugatish", "allowedTransitions": []}
  ],
  "factSchema": [
    {"name": "companyName", "type": "string", "required": false}
  ],
  "tools": [
    {"name": "recordUnansweredQuestion", "description": "Javob berolmagan savolni operator uchun yozib oling", "params": [
      {"name": "question", "type": "string", "required": true, "constraint": null}
    ]}
  ],
  "outcomeSchema": [
    {"name": "resolved", "type": "boolean", "description": "Savolga javob berildimi"},
    {"name": "escalated", "type": "boolean", "description": "Operatorga uzatildimi"}
  ],
  "rolePrompt": "Siz qabulxona agentisiz. Qo'ng'iroq qiluvchining savoliga mavjud ma'lumotlar asosida javob bering, bilmasangiz operatorga o'tkazing.",
  "guardrails": [
    "Bilmagan savolingizga o'zingizdan javob to'qimang — requestHumanTransfer chaqiring"
  ]
}
$def$::jsonb,
    NULL
)
ON CONFLICT (scenario_key) WHERE is_active DO UPDATE
    SET definition = EXCLUDED.definition WHERE scenario.is_builtin;

INSERT INTO scenario(scenario_key, version, name, description, is_builtin, is_active, definition, created_by)
VALUES (
    'inbound-lead', 1,
    $$Kiruvchi lead$$,
    $$Reklamadan kelgan qo'ng'iroq: qiziqishni aniqlash, aloqa ma'lumotini olish, uchrashuvga yozish.$$,
    true, true,
$def$
{
  "stages": [
    {"id": "GREETING", "purpose": "Salomlashish va qo'ng'iroq sababini aniqlash", "allowedTransitions": ["QUALIFY", "ESCALATE_TO_HUMAN", "END_CALL"]},
    {"id": "QUALIFY", "purpose": "Mahsulotga qiziqishini aniqlash", "allowedTransitions": ["CONTACT_CAPTURE", "CLOSING", "ESCALATE_TO_HUMAN", "END_CALL"]},
    {"id": "CONTACT_CAPTURE", "purpose": "Ism va aloqa raqamini yozib olish", "allowedTransitions": ["MEETING_SCHEDULING", "CLOSING", "END_CALL"]},
    {"id": "MEETING_SCHEDULING", "purpose": "Uchrashuv vaqtini kelishish", "allowedTransitions": ["CLOSING", "END_CALL"]},
    {"id": "CLOSING", "purpose": "Suhbatni yakunlash", "allowedTransitions": ["END_CALL"]},
    {"id": "ESCALATE_TO_HUMAN", "purpose": "Operatorga uzatish", "allowedTransitions": ["END_CALL"]},
    {"id": "END_CALL", "purpose": "Qo'ng'iroqni tugatish", "allowedTransitions": []}
  ],
  "factSchema": [
    {"name": "companyName", "type": "string", "required": false},
    {"name": "productName", "type": "string", "required": false}
  ],
  "tools": [
    {"name": "recordContact", "description": "Mijozning ismi va aloqa raqamini yozib oling", "params": [
      {"name": "name", "type": "string", "required": true, "constraint": null},
      {"name": "phone", "type": "string", "required": true, "constraint": null}
    ]},
    {"name": "recordMeeting", "description": "Kelishilgan uchrashuv vaqtini yozib oling", "params": [
      {"name": "dateTime", "type": "string", "required": true, "constraint": "kelajakda bo'lishi shart"}
    ]}
  ],
  "outcomeSchema": [
    {"name": "contactName", "type": "string", "description": "Mijoz ismi"},
    {"name": "contactPhone", "type": "string", "description": "Mijoz aloqa raqami"},
    {"name": "meetingAt", "type": "string", "description": "Kelishilgan uchrashuv vaqti, bo'lsa"}
  ],
  "rolePrompt": "Siz reklama orqali kelgan qo'ng'iroqlarga javob beradigan sotuv agentisiz. Mijozning qiziqishini aniqlang, aloqa ma'lumotlarini yozib oling va imkon bo'lsa uchrashuvga taklif qiling.",
  "guardrails": [
    "Narx yoki chegirma bo'yicha rasmiy bo'lmagan va'da bermang"
  ]
}
$def$::jsonb,
    NULL
)
ON CONFLICT (scenario_key) WHERE is_active DO UPDATE
    SET definition = EXCLUDED.definition WHERE scenario.is_builtin;

INSERT INTO scenario(scenario_key, version, name, description, is_builtin, is_active, definition, created_by)
VALUES (
    'callback-request', 1,
    $$Qayta qo'ng'iroq so'rovi$$,
    $$Operator band bo'lganda: qulay vaqtni so'rab yozib olish (natija sifatida — avtomatik navbatga qo'yilmaydi).$$,
    true, true,
$def$
{
  "stages": [
    {"id": "GREETING", "purpose": "Salomlashish va operator band ekanini tushuntirish", "allowedTransitions": ["COLLECT_CALLBACK", "END_CALL"]},
    {"id": "COLLECT_CALLBACK", "purpose": "Qayta qo'ng'iroq uchun qulay vaqtni so'rash", "allowedTransitions": ["CLOSING", "END_CALL"]},
    {"id": "CLOSING", "purpose": "Suhbatni yakunlash", "allowedTransitions": ["END_CALL"]},
    {"id": "END_CALL", "purpose": "Qo'ng'iroqni tugatish", "allowedTransitions": []}
  ],
  "factSchema": [],
  "tools": [
    {"name": "recordCallbackRequest", "description": "Mijoz so'ragan qayta qo'ng'iroq vaqtini yozib oling", "params": [
      {"name": "preferredTime", "type": "string", "required": true, "constraint": null},
      {"name": "note", "type": "string", "required": false, "constraint": null}
    ]}
  ],
  "outcomeSchema": [
    {"name": "preferredTime", "type": "string", "description": "Mijoz so'ragan qayta qo'ng'iroq vaqti"},
    {"name": "note", "type": "string", "description": "Qo'shimcha izoh"}
  ],
  "rolePrompt": "Siz hozir band bo'lgan operator o'rniga qo'ng'iroqni qabul qiladigan agentisiz. Mijozdan qachon qayta qo'ng'iroq qilish qulayligini so'rang va yozib oling.",
  "guardrails": [
    "Qachon operator qo'ng'iroq qilishini aniq va'da qilmang — faqat so'rovni yozib oling"
  ]
}
$def$::jsonb,
    NULL
)
ON CONFLICT (scenario_key) WHERE is_active DO UPDATE
    SET definition = EXCLUDED.definition WHERE scenario.is_builtin;

INSERT INTO scenario(scenario_key, version, name, description, is_builtin, is_active, definition, created_by)
VALUES (
    'order-confirmation', 1,
    $$Buyurtma va yetkazib berishni tasdiqlash$$,
    $$Mijoz joylagan buyurtmani, yetkazish manzili va qulay vaqtini tasdiqlash.$$,
    true, true,
$def$
{
  "stages": [
    {"id": "GREETING", "purpose": "Salomlashish va yangi buyurtma kelib tushganini aytish", "allowedTransitions": ["ORDER_DETAILS", "END_CALL"]},
    {"id": "ORDER_DETAILS", "purpose": "Buyurtma raqami, tovarlar va summani aytib tasdiqlash", "allowedTransitions": ["ADDRESS_TIME", "CANCEL_ORDER", "END_CALL"]},
    {"id": "ADDRESS_TIME", "purpose": "Yetkazish manzili va qulay vaqtni aniqlash", "allowedTransitions": ["CLOSING", "CANCEL_ORDER", "END_CALL"]},
    {"id": "CANCEL_ORDER", "purpose": "Buyurtmani bekor qilish sababini yozib olish", "allowedTransitions": ["CLOSING", "END_CALL"]},
    {"id": "CLOSING", "purpose": "Rahmat aytish va buyurtma tez orada yetkazilishini bildirish", "allowedTransitions": ["END_CALL"]},
    {"id": "END_CALL", "purpose": "Qo'ng'iroqni tugatish", "allowedTransitions": []}
  ],
  "factSchema": [
    {"name": "clientName", "type": "string", "required": false},
    {"name": "orderNumber", "type": "string", "required": true},
    {"name": "orderAmount", "type": "number", "required": false},
    {"name": "deliveryAddress", "type": "string", "required": false}
  ],
  "tools": [
    {"name": "confirmOrder", "description": "Mijoz buyurtmani va yetkazish ma'lumotlarini tasdiqlaganda chaqiring", "params": [
      {"name": "address", "type": "string", "required": false, "constraint": null},
      {"name": "deliveryTime", "type": "string", "required": false, "constraint": null}
    ]},
    {"name": "cancelOrder", "description": "Mijoz buyurtmani bekor qilganda chaqiring", "params": [
      {"name": "reason", "type": "string", "required": true, "constraint": null}
    ]}
  ],
  "outcomeSchema": [
    {"name": "isConfirmed", "type": "boolean", "description": "Buyurtma tasdiqlandimi"},
    {"name": "deliveryAddress", "type": "string", "description": "Tasdiqlangan manzil"},
    {"name": "deliveryTime", "type": "string", "description": "Yetkazish vaqti"},
    {"name": "cancelReason", "type": "string", "description": "Bekor qilish sababi"}
  ],
  "rolePrompt": "Siz do'konning buyurtmalarni tasdiqlovchi xushmuomala ovozli agentisiz. Buyurtma tafsilotlarini, yetkazish manzili va vaqtini tasdiqlang.",
  "guardrails": [
    "Buyurtma summasini o'zboshimchalik bilan o'zgartirmang",
    "Yetkazib berish xizmati narxi bo'yicha asossiz va'da bermang"
  ]
}
$def$::jsonb,
    NULL
)
ON CONFLICT (scenario_key) WHERE is_active DO UPDATE
    SET definition = EXCLUDED.definition WHERE scenario.is_builtin;

INSERT INTO scenario(scenario_key, version, name, description, is_builtin, is_active, definition, created_by)
VALUES (
    'welcome-onboarding', 1,
    $$Yangi mijozni qutlash va yo'naltirish (Onboarding)$$,
    $$Yangi ro'yxatdan o'tgan mijoz bilan aloqaga chiqib, tizimdan foydalanishda yordam taklif qilish.$$,
    true, true,
$def$
{
  "stages": [
    {"id": "GREETING", "purpose": "Salomlashish va yangi a'zolik bilan tabriklash", "allowedTransitions": ["DISCOVERY", "END_CALL"]},
    {"id": "DISCOVERY", "purpose": "Tizimdan foydalanishda savollari yoki qiyinchiliklari borligini so'rash", "allowedTransitions": ["ASSIST", "SCHEDULE_DEMO", "CLOSING", "END_CALL"]},
    {"id": "ASSIST", "purpose": "Oddiy savollarga javob berish yoki video-qo'llanma SMS yuborishni taklif qilish", "allowedTransitions": ["CLOSING", "END_CALL"]},
    {"id": "SCHEDULE_DEMO", "purpose": "Mutaxassis bilan demo uchrashuv vaqtini belgilash", "allowedTransitions": ["CLOSING", "END_CALL"]},
    {"id": "CLOSING", "purpose": "Xayrlashish va muvaffaqiyat tilash", "allowedTransitions": ["END_CALL"]},
    {"id": "END_CALL", "purpose": "Qo'ng'iroqni tugatish", "allowedTransitions": []}
  ],
  "factSchema": [
    {"name": "clientName", "type": "string", "required": false},
    {"name": "serviceName", "type": "string", "required": false}
  ],
  "tools": [
    {"name": "recordOnboardingFeedback", "description": "Mijozning fikrini va kerakli yordam turini yozib oling", "params": [
      {"name": "needsDemo", "type": "boolean", "required": true, "constraint": null},
      {"name": "notes", "type": "string", "required": false, "constraint": null}
    ]}
  ],
  "outcomeSchema": [
    {"name": "needsDemo", "type": "boolean", "description": "Demo taqdimot kerakmi"},
    {"name": "onboardingNotes", "type": "string", "description": "Mijoz xohishlari"}
  ],
  "rolePrompt": "Siz yangi ro'yxatdan o'tgan foydalanuvchilarga xizmatdan to'liq foydalanishda yordam beruvchi do'stona Customer Success agentisiz.",
  "guardrails": [
    "Mijozga bosim o'tkazmang, xushmuomala bo'ling"
  ]
}
$def$::jsonb,
    NULL
)
ON CONFLICT (scenario_key) WHERE is_active DO UPDATE
    SET definition = EXCLUDED.definition WHERE scenario.is_builtin;

-- The agent every seeded placeholder speaks as (V12). One per company, on the built-in
-- collections scenario: the placeholder campaigns below exist only to give manual and
-- inbound call attempts a parent row, and campaign.ai_agent_id is NOT NULL.
INSERT INTO ai_agent (company_id, name, description, scenario_id, language)
SELECT 1, 'Default', 'Seeded default agent',
       (SELECT id FROM scenario WHERE scenario_key = 'debt-collection' AND is_active LIMIT 1),
       'uz-UZ'
WHERE NOT EXISTS (SELECT 1 FROM ai_agent WHERE company_id = 1 AND name = 'Default');

-- Placeholder campaign + target so manually/auto-started calls (Stages 7-9) have a
-- call_attempt parent before a real campaign exists. Looked up by phone = 'MANUAL'.
-- DRAFT (not ACTIVE): it is only a parent row for manual/test calls, never meant to
-- be picked up by the dialer's own scan. No unique constraint on campaign.name, so
-- guarded with WHERE NOT EXISTS instead of ON CONFLICT.
INSERT INTO campaign (company_id, name, type, status, script_config, ai_agent_id)
SELECT 1, 'MANUAL', 'DEBT_COLLECTION', 'DRAFT',
       '{}',
       (SELECT id FROM ai_agent WHERE company_id = 1 AND name = 'Default' LIMIT 1)
WHERE NOT EXISTS (SELECT 1 FROM campaign WHERE name = 'MANUAL');

INSERT INTO campaign_dial_day (campaign_id, day)
SELECT id, day FROM campaign, unnest(ARRAY['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY']) AS day
WHERE name = 'MANUAL'
ON CONFLICT (campaign_id, day) DO NOTHING;

INSERT INTO campaign_target (company_id, campaign_id, client_id, phone, context_data, status)
SELECT 1, id, 0, 'MANUAL', '{}', 'IN_PROGRESS'
FROM campaign WHERE name = 'MANUAL' AND NOT EXISTS (SELECT 1 FROM campaign_target WHERE phone = 'MANUAL')
ORDER BY id LIMIT 1;

-- Placeholder campaign/target for inbound calls (mirrors the MANUAL seed above) so
-- every inbound call_attempt has a parent row, distinct from manual REST test calls
-- (which use 'MANUAL') so reports can tell the two apart. No dial days: inbound calls
-- are never picked up by the dialer's own scan.
INSERT INTO campaign (company_id, name, type, status, script_config, ai_agent_id)
SELECT 1, 'INBOUND', 'DEBT_COLLECTION', 'DRAFT',
       '{}',
       (SELECT id FROM ai_agent WHERE company_id = 1 AND name = 'Default' LIMIT 1)
WHERE NOT EXISTS (SELECT 1 FROM campaign WHERE name = 'INBOUND');

INSERT INTO campaign_target (company_id, campaign_id, client_id, phone, context_data, status)
SELECT 1, id, 0, 'INBOUND', '{}', 'IN_PROGRESS'
FROM campaign WHERE name = 'INBOUND' AND NOT EXISTS (SELECT 1 FROM campaign_target WHERE phone = 'INBOUND')
ORDER BY id LIMIT 1;
