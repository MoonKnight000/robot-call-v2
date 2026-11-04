-- Seed data: everything a fresh database needs to place and answer a call without a
-- single API call first — bootstrap tenant and the platform admin, the voice and model catalogs, the
-- built-in scenario templates, four ready AI agents (lead qualification and collections, each
-- on the cascade and on the realtime pipeline), their four campaigns, the connected Uysot
-- integration, the inbound route that answers the softphone test extension and the knowledge base. Flyway repeatable migration — reruns whenever this file's checksum changes, so
-- every statement below guards against re-insertion.
--
-- The bottom half ("TEST DATA") fills every screen with something to test against: users
-- and operators, agents of every pipeline, campaigns in every status, call history,
-- billing, a second tenant. It is inserted once, on an empty database — see the note
-- there before relying on it anywhere but a development or test database.

-- Bootstrap tenant every other row below belongs to. Explicit id=1 so it matches
-- voice-agent.company.default-id's own default.
INSERT INTO company (id, name, status) VALUES (1, 'Uysot', 'ACTIVE')
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name WHERE company.name = 'Default';
SELECT setval(pg_get_serial_sequence('company', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 0) FROM company), 1), true);

-- human_like_persona_allowed is on so the HUMAN_LIKE test agent below can be tried; the
-- contact-frequency caps stay off so the softphone test campaign can be run again and again.
INSERT INTO company_config (id, company_id, dial_window_start, dial_window_end, timezone,
                            human_like_persona_allowed)
VALUES (1, 1, '07:00:00', '23:00:00', 'Asia/Tashkent',
        true)
ON CONFLICT (company_id) DO NOTHING;
SELECT setval(pg_get_serial_sequence('company_config', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 0) FROM company_config), 1), true);
-- System roles for the bootstrap tenant. RoleBootstrap creates them at startup too, but the
-- users seeded below need their ids now.
INSERT INTO app_role (company_id, code, name, is_system)
VALUES (1, 'DEVELOPER', 'Developer', true),
       (1, 'ADMIN', 'Administrator', true),
       (1, 'OPERATOR', 'Operator', true),
       (1, 'VIEWER', 'Viewer', true),
       (1, 'SUPERVISOR', 'Supervisor', true)
ON CONFLICT (company_id, code) DO NOTHING;
SELECT setval(pg_get_serial_sequence('app_role', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 0) FROM app_role), 1), true);

-- The platform admin: platform staff, not a tenant user. Has no company_id and no company role.
-- Holds PLATFORM_ADMIN authority, which is what opens company CRUD, platform trunks and users,
-- the number marketplace and every other company's data (CompanyAccessGuard).
--
-- Login: platform / platform@robotcall.uz. The password is not written down here; it was
-- handed over with this change — change it after the first login (PUT /api/profile/password).
-- Guarded by username and never refreshed, so a changed password survives a reseed.
INSERT INTO app_user (company_id, name, email, username, password_hash, role_id, status, position, type)
SELECT NULL, 'Platforma administratori', 'platform@robotcall.uz', 'platform',
       '$2a$12$AMpkNRnaJXARmQLilyxKf.65i4WWkVfdrIhptzC0e3n/9sWeLZgdG',
       NULL, 'ACTIVE', 'Platforma egasi', 'PLATFORM_ADMIN'
WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE username = 'platform' OR email = 'platform@robotcall.uz');

-- Everyone else is seeded in the TEST DATA section at the bottom, every one with the test
-- password Test12345! — a published credential.

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
    -- The rest of SpeechKit's v3 list for the synced languages (speechkit/tts/voices).
    -- SpeechKit has no API that lists its voices, so unlike ElevenLabs, Gemini and Cartesia
    -- (TtsVoiceSyncScheduler) these are kept by hand.
    ('dasha',            'yandex',      'ru-RU', 'dasha',     'Dasha — rus, ayol', NULL),
    ('julia',            'yandex',      'ru-RU', 'julia',     'Julia — rus, ayol', NULL),
    ('lera',             'yandex',      'ru-RU', 'lera',      'Lera — rus, ayol', NULL),
    ('masha',            'yandex',      'ru-RU', 'masha',     'Masha — rus, ayol', NULL),
    ('marina',           'yandex',      'ru-RU', 'marina',    'Marina — rus, ayol', NULL),
    ('alexander',        'yandex',      'ru-RU', 'alexander', 'Alexander — rus, erkak', NULL),
    ('kirill',           'yandex',      'ru-RU', 'kirill',    'Kirill — rus, erkak', NULL),
    ('anton',            'yandex',      'ru-RU', 'anton',     'Anton — rus, erkak', NULL),
    ('madi_ru',          'yandex',      'ru-RU', 'madi_ru',   'Madi — rus, erkak (qozoq aksenti)', NULL),
    ('saule_ru',         'yandex',      'ru-RU', 'saule_ru',  'Saule — rus, ayol (qozoq aksenti)', NULL),
    ('zhanar_ru',        'yandex',      'ru-RU', 'zhanar_ru', 'Zhanar — rus, ayol (qozoq aksenti)', NULL),
    ('zamira_ru',        'yandex',      'ru-RU', 'zamira_ru', 'Zamira — rus, ayol (o''zbek aksenti)', NULL),
    ('yulduz_ru',        'yandex',      'ru-RU', 'yulduz_ru', 'Yulduz — rus, ayol (o''zbek aksenti)', NULL),
    ('amira',            'yandex',      'kk-KZ', 'amira',     'Amira — qozoq, ayol', NULL),
    ('madi',             'yandex',      'kk-KZ', 'madi',      'Madi — qozoq, erkak', NULL),
    ('saule',            'yandex',      'kk-KZ', 'saule',     'Saule — qozoq, ayol', NULL),
    ('zhanar',           'yandex',      'kk-KZ', 'zhanar',    'Zhanar — qozoq, ayol', NULL),
    ('john',             'yandex',      'en-US', 'john',      'John — ingliz, erkak', NULL),
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
    -- Gemini TTS (Cascade mode: gemini-3.8-flash-tts, gemini-3.1-flash-tts-preview)
    ('gemini-tts-aoede-uz',  'gemini',   'uz-UZ', 'Aoede',    'Aoede (ayol, Gemini TTS)', NULL),
    ('gemini-tts-kore-uz',   'gemini',   'uz-UZ', 'Kore',     'Kore (ayol, Gemini TTS)', NULL),
    ('gemini-tts-puck-uz',   'gemini',   'uz-UZ', 'Puck',     'Puck (erkak, Gemini TTS)', NULL),
    ('gemini-tts-charon-uz', 'gemini',   'uz-UZ', 'Charon',   'Charon (erkak, Gemini TTS)', NULL),
    ('gemini-tts-fenrir-uz', 'gemini',   'uz-UZ', 'Fenrir',   'Fenrir (erkak, Gemini TTS)', NULL),
    ('gemini-tts-aoede-ru',  'gemini',   'ru-RU', 'Aoede',    'Aoede (женский, Gemini TTS)', NULL),
    ('gemini-tts-kore-ru',   'gemini',   'ru-RU', 'Kore',     'Kore (женский, Gemini TTS)', NULL),
    ('gemini-tts-puck-ru',   'gemini',   'ru-RU', 'Puck',     'Puck (мужской, Gemini TTS)', NULL),
    ('gemini-tts-charon-ru', 'gemini',   'ru-RU', 'Charon',   'Charon (мужской, Gemini TTS)', NULL),
    ('gemini-tts-fenrir-ru', 'gemini',   'ru-RU', 'Fenrir',   'Fenrir (мужской, Gemini TTS)', NULL),
    -- OpenAI TTS (gpt-4o-mini-tts). The endpoint takes no language parameter — a voice
    -- speaks whatever the text is written in — so each one is listed under both languages
    -- rather than being split between them. Six of the thirteen: the ones that read as a
    -- phone operator rather than a narrator.
    ('openai-alloy-uz',   'openai', 'uz-UZ', 'alloy',   'Alloy — neytral', NULL),
    ('openai-nova-uz',    'openai', 'uz-UZ', 'nova',    'Nova — ayol', NULL),
    ('openai-shimmer-uz', 'openai', 'uz-UZ', 'shimmer', 'Shimmer — ayol, yumshoq', NULL),
    ('openai-sage-uz',    'openai', 'uz-UZ', 'sage',    'Sage — ayol, xotirjam', NULL),
    ('openai-echo-uz',    'openai', 'uz-UZ', 'echo',    'Echo — erkak', NULL),
    ('openai-onyx-uz',    'openai', 'uz-UZ', 'onyx',    'Onyx — erkak, past ovoz', NULL),
    ('openai-alloy-ru',   'openai', 'ru-RU', 'alloy',   'Alloy — нейтральный', NULL),
    ('openai-nova-ru',    'openai', 'ru-RU', 'nova',    'Nova — женский', NULL),
    ('openai-shimmer-ru', 'openai', 'ru-RU', 'shimmer', 'Shimmer — женский, мягкий', NULL),
    ('openai-sage-ru',    'openai', 'ru-RU', 'sage',    'Sage — женский, спокойный', NULL),
    ('openai-echo-ru',    'openai', 'ru-RU', 'echo',    'Echo — мужской', NULL),
    ('openai-onyx-ru',    'openai', 'ru-RU', 'onyx',    'Onyx — мужской, низкий', NULL)
ON CONFLICT (id) DO NOTHING;

-- The gender of the hand-kept voices, for the voice picker's filter (V6). A separate UPDATE
-- because the insert above skips rows that already exist; a value already set — by the sync
-- or by hand — is left alone. Their age is unknown, so age_group stays NULL.
UPDATE tts_voice SET gender = 'FEMALE' WHERE gender IS NULL AND company_id IS NULL AND id IN (
    'nigora', 'zamira', 'yulduz', 'alena', 'jane', 'omazh', 'dasha', 'julia', 'lera', 'masha', 'marina',
    'saule_ru', 'zhanar_ru', 'zamira_ru', 'yulduz_ru', 'amira', 'saule', 'zhanar',
    'gulnoza', 'gulnoza-neutral', 'gulnoza-cheerful', 'gulnoza-happy', 'gulnoza-sad',
    'gemini-aoede-uz', 'gemini-kore-uz', 'gemini-aoede-ru', 'gemini-kore-ru',
    'gemini-tts-aoede-uz', 'gemini-tts-kore-uz', 'gemini-tts-aoede-ru', 'gemini-tts-kore-ru',
    'openai-nova-uz', 'openai-shimmer-uz', 'openai-sage-uz', 'openai-nova-ru', 'openai-shimmer-ru', 'openai-sage-ru');
UPDATE tts_voice SET gender = 'MALE' WHERE gender IS NULL AND company_id IS NULL AND id IN (
    'filipp', 'ermil', 'zahar', 'alexander', 'kirill', 'anton', 'madi_ru', 'madi', 'john',
    'gemini-puck-uz', 'gemini-charon-uz', 'gemini-fenrir-uz', 'gemini-puck-ru', 'gemini-charon-ru', 'gemini-fenrir-ru',
    'gemini-tts-puck-uz', 'gemini-tts-charon-uz', 'gemini-tts-fenrir-uz',
    'gemini-tts-puck-ru', 'gemini-tts-charon-ru', 'gemini-tts-fenrir-ru',
    'openai-echo-uz', 'openai-onyx-uz', 'openai-echo-ru', 'openai-onyx-ru');
UPDATE tts_voice SET gender = 'NEUTRAL' WHERE gender IS NULL AND company_id IS NULL AND id IN (
    'openai-alloy-uz', 'openai-alloy-ru');

-- Model catalog: what an operator may pick for the company (§11 settings) or for one agent —
-- the chat model (kind LLM), and since the speech engine moved onto the agent, the recognizer
-- and synthesizer models too (kind STT/TTS). The label is refreshed on every reseed, the id is
-- not — an id already stored on an agent must keep meaning the same model.
INSERT INTO ai_model (id, kind, provider, mode, label) VALUES
    -- CASCADE: the text LLM behind a turn. Only rows whose vendor (google-genai, openai,
    -- anthropic) has an API key in this deployment are offered; a request is routed to
    -- the vendor its model id belongs to, so all three can be in use at once.
    ('gemini-3.8-flash',            'LLM', 'google-genai',    'CASCADE',  'Gemini 3.8 Flash — standart, tezkor'),
    ('gemini-3.6-flash',            'LLM', 'google-genai',    'CASCADE',  'Gemini 3.6 Flash — oldingi avlod'),
    ('gemini-3.5-flash-lite',       'LLM', 'google-genai',    'CASCADE',  'Gemini 3.5 Flash Lite — eng arzon, qisqa javoblar uchun'),
    ('gpt-5.6-luna',                'LLM', 'openai',          'CASCADE',  'GPT-5.6 Luna — standart, arzon va tezkor'),
    ('gpt-5.6-terra',               'LLM', 'openai',          'CASCADE',  'GPT-5.6 Terra — aql va narx muvozanati'),
    ('gpt-5.6-sol',                 'LLM', 'openai',          'CASCADE',  'GPT-5.6 Sol — murakkab suhbatlar uchun'),
    ('gpt-6-astra',                 'LLM', 'openai',          'CASCADE',  'GPT-6 Astra — eng kuchli, eng qimmat va sekin'),
    -- Oldingi avlod. Bu ikki id OpenAI'ning joriy modellar ro'yxatida ham, eskirganlar
    -- ro'yxatida ham yo'q — agar hisobingiz ularni qabul qilmasa, javob 404
    -- model_not_found bo'ladi va gpt-5.6-* qatorlaridan birini tanlash kerak.
    ('gpt-5.5',                     'LLM', 'openai',          'CASCADE',  'GPT-5.5 — oldingi avlod'),
    ('gpt-5.4-mini',                'LLM', 'openai',          'CASCADE',  'GPT-5.4 mini — oldingi avlod, arzon'),
    ('claude-haiku-5-5',            'LLM', 'anthropic',       'CASCADE',  'Claude Haiku 5.5 — tezkor va arzon, qisqa javoblar uchun'),
    ('claude-sonnet-5-5',           'LLM', 'anthropic',       'CASCADE',  'Claude Sonnet 5.5 — aql va narx muvozanati'),
    ('claude-opus-5-5',             'LLM', 'anthropic',       'CASCADE',  'Claude Opus 5.5 — murakkab suhbatlar uchun, sekinroq va qimmatroq'),
    ('claude-fable-5-1',            'LLM', 'anthropic',       'CASCADE',  'Claude Fable 5.1'),
    -- REALTIME: the speech-to-speech engine's own model. Only rows whose provider is
    -- registered in this build are offered.
    ('gemini-3.8-live',             'LLM', 'gemini-live',     'REALTIME', 'Gemini 3.8 Live — tezroq, tool''lar fonda ishlaydi'),
    ('gemini-3.8-live-extended-thinking', 'LLM', 'gemini-live', 'REALTIME', 'Gemini 3.8 Live Extended Thinking — standart, murakkab suhbatlar uchun, sekinroq'),
    ('gemini-3.1-flash-live-preview', 'LLM', 'gemini-live',   'REALTIME', 'Gemini 3.1 Flash Live — oldingi avlod, preview'),
    ('gpt-realtime-2.1',            'LLM', 'openai-realtime', 'REALTIME', 'GPT Realtime 2.1 — standart, tool chaqirish bilan'),
    ('gpt-realtime-2.1-mini',       'LLM', 'openai-realtime', 'REALTIME', 'GPT Realtime 2.1 mini — arzonroq'),
    ('qwen-omni-turbo',             'LLM', 'qwen-omni',       'REALTIME', 'Qwen Omni Turbo'),
    ('moshi',                       'LLM', 'moshi',           'REALTIME', 'Moshi — o''z serveringizda'),
    ('claude-3-5-haiku-20241022',   'LLM', 'pipecat',         'REALTIME', 'Claude 3.5 Haiku (Pipecat LLM)'),
    -- STT and TTS: the recognizer and synthesizer of one speech provider, offered only
    -- while that provider is a bean here (its API key is set). Both belong to CASCADE —
    -- a realtime engine hears and speaks itself and has no separate model to pick.
    -- Only ids this deployment is configured for are listed; an id nobody has tested is
    -- worse than none, because the form makes it look supported.
    ('gemini-3.5-transcribe-live',  'STT', 'gemini',          'CASCADE',  'Gemini 3.5 Transcribe Live — uz/ru, Live API'),
    ('nova-3',                      'STT', 'deepgram',        'CASCADE',  'Deepgram Nova-3'),
    ('general',                     'STT', 'yandex',          'CASCADE',  'Yandex SpeechKit general'),
    ('gpt-live-transcribe',         'STT', 'openai',          'CASCADE',  'OpenAI Live Transcribe — past kechikish, oraliq natijalar bilan'),
    ('gpt-transcribe',              'STT', 'openai',          'CASCADE',  'OpenAI Transcribe — aniqroq, lekin faqat gap tugagach javob beradi'),
    ('gemini-3.8-flash-tts',        'TTS', 'gemini',          'CASCADE',  'Gemini 3.8 Flash TTS — standart, 30 ta ovoz'),
    ('gemini-3.1-flash-tts-preview','TTS', 'gemini',          'CASCADE',  'Gemini 3.1 Flash TTS — oldingi avlod, preview'),
    ('sonic-multilingual',          'TTS', 'cartesia',        'CASCADE',  'Cartesia Sonic Multilingual'),
    ('gpt-4o-mini-tts',             'TTS', 'openai',          'CASCADE',  'OpenAI TTS — 13 ta ovoz, uslub ko''rsatmasi bilan'),
    ('eleven_v4_turbo',             'TTS', 'elevenlabs',      'CASCADE',  'ElevenLabs v4 Turbo — o''zbek tili, ~100 ms')
ON CONFLICT (id) DO UPDATE SET kind     = EXCLUDED.kind,
                               provider = EXCLUDED.provider,
                               mode     = EXCLUDED.mode,
                               label    = EXCLUDED.label;

-- How the picker groups the chat models (tier) and which it puts forward (suggested). Kept
-- apart from the insert so the two lists read as the decisions they are; a row left out of
-- every tier list is simply not grouped.
UPDATE ai_model SET tier = NULL, suggested = false WHERE kind = 'LLM';
UPDATE ai_model SET tier = 'FAST'
 WHERE id IN ('gemini-3.5-flash-lite', 'gpt-5.4-mini', 'gpt-5.6-luna', 'claude-haiku-5-5');
UPDATE ai_model SET tier = 'BALANCED'
 WHERE id IN ('gemini-3.8-flash', 'gemini-3.6-flash', 'gpt-5.5', 'gpt-5.6-terra', 'claude-sonnet-5-5');
UPDATE ai_model SET tier = 'SMART'
 WHERE id IN ('gpt-5.6-sol', 'gpt-6-astra', 'claude-opus-5-5', 'claude-fable-5-1');
UPDATE ai_model SET suggested = true
 WHERE id IN ('gemini-3.8-flash', 'gpt-5.6-luna', 'claude-sonnet-5-5');

-- Retired ids. The insert above only ever adds and refreshes, so a model this project has
-- stopped supporting stays in the picker until it is deleted by name. The Groq llama rows
-- went when the openai slot stopped pointing at Groq, and the gpt-4o realtime previews were
-- replaced by the gpt-realtime-2.x line. Anything still pointing at one is cleared first:
-- a dangling id is handed to the vendor verbatim at call time, which is a call that does
-- not start — an agent left null falls back to the deployment's own model instead.
UPDATE ai_agent SET llm_model = NULL
 WHERE llm_model IN ('llama-3.3-70b-versatile', 'llama-3.1-8b-instant',
                     'gpt-4o-realtime-preview', 'gpt-4o-mini-realtime-preview');

UPDATE ai_model_config SET model = NULL
 WHERE model IN ('llama-3.3-70b-versatile', 'llama-3.1-8b-instant');

DELETE FROM ai_model
 WHERE id IN ('llama-3.3-70b-versatile', 'llama-3.1-8b-instant',
              'gpt-4o-realtime-preview', 'gpt-4o-mini-realtime-preview');

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
    {"id":"GREETING","purpose":"Tirik operatordek, ismini aytib salomlash: 'Assalomu alaykum, [Ism] aka, yaxshimisiz?' (ayol bo'lsa 'opa'). Mijoz faqat 'alo' deb tursa yoki jim bo'lsa (aloqa yomon) — 'Alo, eshityapsizmi, aka? Assalomu alaykum' deb qayta ayt. Sen salomda hol so'rading — QAYTA hol so'rama ('O'zingiz yaxshimisiz?', 'charchamayapsizmi?' kabi qarshi savol BERILMAYDI). Mijoz senga hol so'rasa — qisqa javob ber va shu gapning o'zida maqsadga o't: 'Rahmat, yaxshi, Xudoga shukur. Aka, men Uysot'dan, uyingiz bo'yicha telefon qilyapman.' Mijoz faqat o'z holini aytsa ('yaxshi', 'shikoyat yo'q', 'rahmat') — 'Rahmat' de va 'Aka, ...' bilan maqsadga o't. Mijoz hol so'ragan bo'lsa, unga OVOZ CHIQARIB javob bermaguncha transitionTo chaqirma va qarzga o'tma. Bu bosqichda qarz haqida gapirma.","allowedTransitions":["IDENTITY_CHECK","END_CALL","ESCALATE_TO_HUMAN"],"allowedTools":[]},
    {"id":"IDENTITY_CHECK","purpose":"Salomda ismini aytding. Mijoz ismga e'tiroz bildirmay alik olsa yoki hol so'rasa ('va alaykum assalom', 'rahmat, yaxshi', 'ha, eshitaman', 'ha, menman') — bu tasdiq, alohida 'siz falonchimisiz?' deb so'rama, DEBT_NOTICE ga o't. Faqat 'alo', 'a?' yoki 'kim bu?' bo'lsa — qisqa tanishtir: 'Aka, men Uysot'dan, uyingiz bo'yicha telefon qilyapman. [Ism] aka o'zlarimi?'. Telefonni boshqa odam ko'targan bo'lsa ('u akamniki', 'u hozir yo'q') — qarz summasini AYTMA; egasining raqamini so'ra: 'Bo'ldi, nomerlarini aytvorasizmi, o'zimiz telefon qilib qo'yardik?'. Raqamni bo'lak-bo'lak takrorlab yoz ('99... 859...'), RECORD_REFUSAL ga o't ('Boshqa odam ko'tardi, egasining raqami: ...' deb yoziladi) va 'Rahmat, aka' deb xayrlash. Shaxs aniq bo'lmaguncha qarz haqida gapirma.","allowedTransitions":["DEBT_NOTICE","RECORD_REFUSAL","END_CALL","ESCALATE_TO_HUMAN"],"allowedTools":[],"transitionConditions":{"RECORD_REFUSAL":"Telefonni boshqa odam ko'targan va egasining raqami so'raldi"}},
    {"id":"DEBT_NOTICE","purpose":"Qarzni bitta qisqa, yumshoq gap bilan ayt, xuddi real operator kabi: 'Aka, oylik to'lov bo'yicha qarzdorligingiz bor ekan, bugungi kun holatiga [summa] so'm. Qachon to'lab bera olasiz?'. Keyin JIM bo'l va tingla — mijozlar odatda o'zi sababini ham, sanasini ham aytadi. Mijoz sanani aytsa — o'sha zahoti RECORD_PROMISE ga o't. Agar mijoz eshitmagan yoki tushunmagan bo'lsa ('a?', 'nima dedingiz?', 'qaytaring', 'qanaqa qarz?', 'tushunmadim') — 'Eshitilmadimi?' deb SO'RAMA, aytganingni boshqa, soddaroq so'zlar bilan qayta tushuntir: 'Uyingiz bo'yicha oylik to'lovlardan [summa] so'm to'lanmay qolgan ekan'. Mijoz javob bermaguncha keyingi bosqichga o'tma.","allowedTransitions":["REASON_INQUIRY","RECORD_PROMISE","PAYMENT_CLAIM","CALLBACK","ESCALATE_TO_HUMAN","END_CALL"],"allowedTools":[],"transitionConditions":{"RECORD_PROMISE":"Mijoz to'lash sanasini aytdi","PAYMENT_CLAIM":"Mijoz to'laganini aytdi","CALLBACK":"Mijoz band va qayta qo'ng'iroq vaqtini aytdi"}},
    {"id":"REASON_INQUIRY","fallbackLine":"Tushunarli. Qaysi sanada to'lov qila olasiz?","purpose":"Mijoz sabab yoki bahona aytdi — avval eshitganingni bildir ('Ha, ha, tushunaman, aka'), keyin sanaga qaytar. Sababni so'rab tergov qilma; sababni mijoz o'zi aytgan bo'lsa yozib ol, aytmagan bo'lsa so'rama. Mijoz 'to'lab boryapman-ku, bittasi qolib ketdi' desa — avval yaxshi tomonini tan ol: 'Ha, vaqtida to'layapsiz, baraka toping, bizda ko'rinib turibdi', keyin: 'Shu qolganini bir yo'la to'lab bersangiz, keyin tekis ketaverasiz-da, aka. Qachonga qila olasiz?'. Mijoz 'to'ladim', 'chekni Telegramga tashladim' desa — tortishma va qayta talab qilma: 'Ha, bo'ldi, bo'ldi, aka. Qachon va qayerga to'lagansiz?' (faqat bitta savol), keyin 'Tekshirib, sizning hisobingizga qo'shib qo'yamiz' de, RECORD_REFUSAL ga o't ('ALREADY_PAID: ...' deb yoziladi) va 'Rahmat, charchamang, kuningiz xayrli bo'lsin!' deb xayrlash. Mijoz 'nima variant bor?' desa — chegirma yoki muddat o'ylab topma: 'Buni menejerimiz aytib beradi, aka' de. Mijoz qurilish, uy yoki boshqa narsa haqida so'rasa — o'zingdan to'qima: 'Buni menejerimiz aniq aytib beradi, men yozib qo'yaman' deb, gapni yumshoq sanaga qaytar.","allowedTransitions":["PAYMENT_DATE","RECORD_PROMISE","PAYMENT_CLAIM","RECORD_REFUSAL","CALLBACK","ESCALATE_TO_HUMAN","END_CALL"],"allowedTools":[],"transitionConditions":{"PAYMENT_DATE":"Mijoz sana aytmadi","RECORD_PROMISE":"Mijoz aniq sanani aytdi","PAYMENT_CLAIM":"Mijoz to'laganini aytdi","RECORD_REFUSAL":"Mijoz to'lay olmasligini yoki allaqachon to'laganini aytdi","CALLBACK":"Mijoz band va qayta qo'ng'iroq vaqtini aytdi"}},
    {"id":"PAYMENT_DATE","fallbackLine":"Tushunarli. Qaysi sanada to'lov qila olasiz?","purpose":"Aniq sanani ol. Mijoz noaniq aytsa ('oy oxirida', '12 yo 13-ga') — yumshoq aniqlashtir: 'Bo'lmasa 12-sanaga yozib qo'yaymi, aka?'. Sana aytilishi bilan RECORD_PROMISE ga o't. Mijoz to'lay olmasligini aytsa — RECORD_REFUSAL ga o't.","allowedTransitions":["RECORD_PROMISE","RECORD_REFUSAL","CALLBACK","ESCALATE_TO_HUMAN","END_CALL"],"allowedTools":[],"transitionConditions":{"RECORD_PROMISE":"Mijoz aniq kunni aytdi","RECORD_REFUSAL":"Mijoz to'lay olmasligini aytdi","CALLBACK":"Mijoz band va qayta qo'ng'iroq vaqtini aytdi"}},
    {"id":"RECORD_PROMISE","title":"Va'dani yozish","type":"FUNCTION","purpose":"Mijozga eshittirmasdan recordPaymentPromise ni chaqir: promisedDate (mijoz aytgan aniq kun, yyyy-MM-dd) va aytilgan bo'lsa promisedAmount. Sanani o'zingdan to'qima. Keyin CONFIRMATION ga o't.","action":{"toolName":"recordPaymentPromise"},"allowedTransitions":["CONFIRMATION"],"allowedTools":[]},
    {"id":"RECORD_REFUSAL","title":"Rad sababini yozish","type":"FUNCTION","purpose":"Mijozga eshittirmasdan recordRefusalReason ni chaqir: reasonCode va mijozning o'z so'zlari (reasonDetail). Boshqa odam ko'targan bo'lsa — 'Boshqa odam ko'tardi, egasining raqami: ...'; mijoz allaqachon to'laganini aytgan bo'lsa — 'ALREADY_PAID: ...'. Keyin CLOSING ga o't.","action":{"toolName":"recordRefusalReason"},"allowedTransitions":["CLOSING"],"allowedTools":[]},
    {"id":"PAYMENT_CLAIM","title":"To'langanini yozish","type":"FUNCTION","purpose":"Mijozga eshittirmasdan recordPaymentClaim ni chaqir: paidDate va paymentChannel (mijoz aytgan bo'lsa). Bahslashma — bu keyin tekshiriladi. Keyin CLOSING ga o't.","action":{"toolName":"recordPaymentClaim"},"allowedTransitions":["CLOSING"],"allowedTools":[]},
    {"id":"CALLBACK","title":"Qayta qo'ng'iroq","type":"FUNCTION","purpose":"Mijozga eshittirmasdan scheduleCallback ni chaqir: callbackTime (mijoz yoki telefonni ko'targan odam aytgan vaqt) va callbackReason. Keyin CLOSING ga o't.","action":{"toolName":"scheduleCallback"},"allowedTransitions":["CLOSING"],"allowedTools":[]},
    {"id":"CONFIRMATION","purpose":"Sanani BITTA xabar gapi bilan qayd et (savol emas, qayta tasdiqlatma) va shu zahoti xayrlash: 'Bo'ldi, aka, 12-sanaga yozib qo'ydim. Aloqada bo'lamiz. Salomat bo'ling, charchamang!'.","allowedTransitions":["CLOSING","PAYMENT_DATE","ESCALATE_TO_HUMAN"],"allowedTools":[]},
    {"id":"CLOSING","purpose":"Qisqa, iliq xayrlash: 'Xo'p, aka, xabarlashamiz. Salomat bo'ling, charchamang!' yoki 'Rahmat, kuningiz xayrli bo'lsin!'.","allowedTransitions":["END_CALL"],"allowedTools":[]},
    {"id":"ESCALATE_TO_HUMAN","purpose":"Operatorga o'tkazishni bildirib xayrlash.","allowedTransitions":["END_CALL"],"allowedTools":[]},
    {"id":"END_CALL","purpose":"Qo'ng'iroqni yakunlash.","allowedTransitions":[],"allowedTools":[]}
  ],
  "factSchema": [
    {"name": "clientName", "type": "string", "required": true},
    {"name": "debtAmount", "type": "number", "required": true},
    {"name": "currency", "type": "string", "required": true},
    {"name": "dueDate", "type": "date", "required": false},
    {"name": "contractNumber", "type": "string", "required": false}
  ],
  "tools": [
    {"name": "recordPaymentPromise", "disposition": "PROMISE_TO_PAY", "description": "Call when the client names an EXACT DAY themselves (e.g. \"10-oktabr\", \"ertaga\"). Do NOT call it on a vague answer like \"keyingi oy\", \"oy oxirida\", \"pul bo'lganda\" — first ask which day. Guessing the date yourself is FORBIDDEN", "params": [
      {"name": "promisedDate", "type": "futureDate", "required": true, "constraint": "promised date, today or later"},
      {"name": "promisedAmount", "type": "number", "required": false, "constraint": "promised amount"},
      {"name": "paymentChannel", "type": "string", "required": false, "constraint": "CLICK, PAYME, UZUM, BANK_BRANCH, TERMINAL or OTHER. Leave empty if the client did not say"},
      {"name": "note", "type": "string", "required": false, "constraint": "additional note"}
    ]},
    {"name": "recordRefusalReason", "disposition": "REFUSED", "description": "Records the reason when the client says they cannot pay", "params": [
      {"name": "reasonCode", "type": "string", "required": true, "constraint": "NO_MONEY, JOB_LOSS, ILLNESS, ALREADY_PAID, DISPUTES_DEBT, FORGOT, TECHNICAL_ISSUE or OTHER"},
      {"name": "reasonDetail", "type": "string", "required": false, "constraint": "details of the reason"}
    ]},
    {"name": "recordPaymentClaim", "disposition": "PAYMENT_CLAIMED", "description": "Call when the client says they have ALREADY paid the debt. First ask when and through which payment system they paid. Do not argue with the client: tell them they will not be called until the payment has been checked", "params": [
      {"name": "paidDate", "type": "pastDate", "required": false, "constraint": "payment date the client named, today or earlier"},
      {"name": "paymentChannel", "type": "string", "required": false, "constraint": "CLICK, PAYME, UZUM, BANK_BRANCH, TERMINAL or OTHER"}
    ]},
    {"name": "scheduleCallback", "disposition": "CALLBACK_REQUESTED", "description": "Call when the client asks to be called back later (e.g. tomorrow or at 16:00)", "params": [
      {"name": "callbackTime", "type": "string", "required": true, "constraint": "callback date and time (e.g. 2026-09-02T16:00:00 or 'ertaga soat 16:00')"},
      {"name": "callbackReason", "type": "string", "required": false, "constraint": "reason or note"}
    ]}
  ],
  "outcomeSchema": [
    {"name": "promisedDate", "type": "date", "description": "Mijoz va'da qilgan to'lov sanasi"},
    {"name": "promisedAmount", "type": "number", "description": "Mijoz va'da qilgan summa"},
    {"name": "paymentChannel", "type": "string", "description": "Mijoz aytgan to'lov kanali: CLICK, PAYME, UZUM, BANK_BRANCH, TERMINAL, OTHER"},
    {"name": "paidDate", "type": "date", "description": "Mijoz 'to'laganman' desa — aytgan to'lov sanasi"},
    {"name": "callbackTime", "type": "string", "description": "Mijoz so'ragan qayta qo'ng'iroq vaqti"},
    {"name": "reasonCode", "type": "string", "description": "Rad etish sababi. Faqat shu qiymatlardan bittasini yozing: NO_MONEY, JOB_LOSS, ILLNESS, ALREADY_PAID, DISPUTES_DEBT, FORGOT, TECHNICAL_ISSUE, OTHER"}
  ],
  "rolePrompt": "Siz \"Uysot\" kompaniyasining to'lovlar bo'limi xodimidek gaplashasiz: mijozlar uy sotib olgan, oylik to'lovdan qarzi bor. Ohangingiz — mijozni yaxshi taniydigan, iliq, samimiy toshkentlik operator: hurmat bilan, lekin rasmiyatchiliksiz, xuddi tanishiga telefon qilgandek. Siz qarzni talab qilmaysiz — eslatasiz va kelishasiz.",
  "guardrails": [
    "Qarz summasini HECH QACHON o'zgartirma. Faqat berilgan raqamni ayt.",
    "Chegirma, imtiyoz yoki qarz kechirishni HECH QACHON taklif qilma.",
    "To'lov muddatini o'zing uzaytirma — faqat mijoz aytgan sanani yozib ol.",
    "Mijoz nisbiy sana aytsa (\"ertaga\", \"dushanba\", \"kelasi oyning 5-sanasi\") — uni BUGUNGI SANAdan hisoblab yyyy-MM-dd ko'rinishida recordPaymentPromise'ga ber. Yilni o'zingdan to'qima.",
    "Huquqiy oqibatlar, sud, jarima yoki ijro haqida o'zingdan gapirma, qo'rqitma.",
    "Mijozga 'suhbatdoshim' yoki 'mijoz' deb gapirma. 'Siz [Ism]misiz?' deb so'rama — ismini salomning ichida ayt: 'Assalomu alaykum, [Ism] aka, yaxshimisiz?'.",
    "Og'zaki shaklni tanla: \"to'lovni amalga oshirasiz\" emas — \"to'laysiz\"; \"qarzdorligingiz mavjud\" emas — \"qarzdorligingiz bor ekan\"; \"to'lanishi kerak bo'lgan summa\" emas — \"qarz\".",
    "Jonli so'zlashuv: gaplar qisqa (5-12 so'z), har bir-ikki gapda 'aka'/'opa'. Mijoz gapirib bo'lgach qisqa javob so'zi bilan boshla: 'Ha, ha', 'Xo'p', 'Bo'ldi', 'Tushunaman'. Toshkent og'zaki shakllari joiz: '-da', '-a?', 'bo'lmasa', 'shunaqa'. Kitobiy so'zlar yo'q: 'hurmatli mijoz', 'amalga oshirish', 'mavjud', 'ushbu', 'ma'lum qilamiz'.",
    "Summani raqam bilan emas, og'zaki ayt: 10436000 emas — '10 million 436 ming so'm'; 1500000 emas — 'bir million besh yuz ming so'm'.",
    "Mijoz e'tiroz qilsa yoki jahli chiqsa — tortishma, monolog o'qima. Avval tan ol ('Tushunaman, aka'), keyin bitta qisqa gap bilan sanaga qaytar.",
    "Mijoz qo'pol gapirsa yoki so'kinsa ham operatorga ULAMA — xotirjam tan ol va sanaga qaytar. requestHumanTransfer faqat mijoz o'zi 'operator', 'mutaxassis', 'odam bilan gaplashmoqchiman' desa chaqiriladi.",
    "Mijoz 'robotmisiz?' yoki 'tirik odammisiz?' deb so'rasa — yolg'on gapirma, rost javob ber.",
    "Qarz bor-yo'qligi muhokama mavzusi emas: mijozdan qarzini tasdiqlashini so'rama — sen uni xabar qilyapsan.",
    "Mijoz aniq to'lov sanasini aytgan zahoti SHU javobning o'zida recordPaymentPromise va endCall'ni birga chaqir. Sanani qayta tasdiqlatuvchi savol berma (\"20-oktabrda 1500000 so'm to'lay olasizmi?\") — mijoz sanani allaqachon aytdi. Yakuniy gap savol emas, XABAR bo'lsin: sana va summani takrorlab, to'lovni kutayotganingni ayt va xayrlash."
  ],
  "styleExamples": [
    {"language": "uz", "wrong": "\"Assalomu alaykum! Men Murodjon aka bilan gaplashayapmanmi?\"", "right": "\"Assalomu alaykum, Murodjon aka, yaxshimisiz?\""},
    {"language": "uz", "wrong": "(mijoz \"tuzukmisiz, aka, charchamayapsizmi?\" dedi) \"Shaxsingizni tasdiqladingiz. Sizning qarzdorligingiz mavjud.\"", "right": "\"Rahmat, Xudoga shukur. Aka, men Uysot'dan, uyingiz bo'yicha telefon qilyapman.\""},
    {"language": "uz", "wrong": "(mijoz \"yaxshi, o'zingiz yaxshimisiz?\" dedi) \"Rahmat, yaxshiman. O'zingiz ham charchamayapsizmi, aka?\"", "right": "\"Rahmat, yaxshi, Xudoga shukur. Aka, men Uysot'dan, uyingiz bo'yicha telefon qilyapman.\""},
    {"language": "uz", "wrong": "(mijoz \"shikoyat yo'q, rahmat\" dedi — hol-ahvol tugadi) \"Uydagilar ham yaxshimi? Ishlar qalay?\"", "right": "\"Rahmat. Aka, oylik to'lov bo'yicha qarzdorligingiz bor ekan...\""},
    {"language": "uz", "wrong": "(mijoz \"to'ladim, chekni Telegramga tashladim\" dedi) \"Tizimda to'lov ko'rinmayapti, qarzingizni to'lashingiz kerak.\"", "right": "\"Ha, bo'ldi, bo'ldi, aka. Qachon to'lagansiz? Tekshirib, hisobingizga qo'shib qo'yamiz.\""},
    {"language": "uz", "wrong": "\"Ushbu to'lov qachon amalga oshirilishi kutilmoqda?\"", "right": "\"Qachon to'lab bera olasiz, aka?\""},
    {"language": "uz", "wrong": "\"Sizning A56 shartnomangiz bo'yicha 1500000 so'm qarzingiz mavjud. Nima sababdan to'lanmadi?\"", "right": "\"Aka, oylik to'lov bo'yicha qarzdorligingiz bor ekan, bugungi kun holatiga bir million besh yuz ming so'm. Qachon to'lab bera olasiz?\""},
    {"language": "uz", "wrong": "(mijoz \"12 yo 13-ga to'layman\" dedi) \"Iltimos, aniq sanani ayting. 12-sanami yoki 13-sanami?\"", "right": "\"Mhm, bo'lmasa 12-sanaga yozib qo'yaymi, aka?\""},
    {"language": "uz", "wrong": "(mijoz \"to'lab boryapman-ku, bittasi qolib ketdi\" dedi) \"To'lovlarni o'z vaqtida amalga oshirishingiz shart.\"", "right": "\"Ha, ha, vaqtida to'layapsiz, baraka toping, ko'rinib turibdi. Shu qolganini bir yo'la to'lab bersangiz, keyin tekis ketaverasiz-da, aka. Qachonga qila olasiz?\""},
    {"language": "uz", "wrong": "(mijoz \"nima deding? eshitilmadi\" dedi) \"Ovoz yaxshi eshitilmadi. Qandaydir qiyinchilik bo'ldimi?\"", "right": "\"Aka, uyingiz bo'yicha oylik to'lovdan bir million besh yuz ming so'm to'lanmay qolgan ekan. Qachon to'lab bera olasiz?\""},
    {"language": "uz", "wrong": "(mijoz \"20-sanada\" dedi, endi tasdiqlatyapti) \"Aha. 20-oktabrda 1500000 so'm to'lay olasizmi?\"", "right": "\"Bo'ldi, aka, 20-sanaga yozib qo'ydim. Aloqada bo'lamiz. Salomat bo'ling, charchamang!\""},
    {"language": "uz", "wrong": "(mijoz \"qurilish ketyaptimi?\" deb so'radi) \"Qurilish 80 foiz bitgan, kelasi oy topshiriladi.\"", "right": "\"Buni menejerimiz aniq aytib beradi, aka, men yozib qo'yaman. To'lovni bo'lmasa qachonga belgilaylik?\""},
    {"language": "uz", "wrong": "(mijoz \"u akamniki\" dedi) \"Akangizning 1500000 so'm qarzi bor, unga yetkazing.\"", "right": "\"Bo'ldi, aka. Nomerlarini aytvorasizmi, o'zimiz telefon qilib qo'yardik?\""},
    {"language": "ru", "wrong": "\"Когда ожидается осуществление данного платежа?\"", "right": "\"Когда сможете оплатить?\""},
    {"language": "ru", "wrong": "(ответ клиента не разобран) \"Я вас не совсем понял. Когда сможете оплатить?\"", "right": "\"Плохо слышно. Почему не оплачено?\""}
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
    {"id": "INTEREST_CHECK", "fallbackLine": "Taklifimiz sizni qiziqtiradimi?", "purpose": "Mahsulotga qiziqish darajasini aniqlash", "allowedTransitions": ["BUDGET_TIMELINE", "CLOSING", "ESCALATE_TO_HUMAN", "END_CALL"]},
    {"id": "BUDGET_TIMELINE", "fallbackLine": "Taxminan qancha byudjet va qachonga rejalashtiryapsiz?", "purpose": "Byudjet va muddatni so'rash", "allowedTransitions": ["MEETING_SCHEDULING", "CLOSING", "ESCALATE_TO_HUMAN", "END_CALL"]},
    {"id": "MEETING_SCHEDULING", "fallbackLine": "Uchrashuv uchun qaysi kun va vaqt sizga qulay?", "purpose": "Uchrashuv vaqtini kelishish", "allowedTransitions": ["CLOSING", "ESCALATE_TO_HUMAN", "END_CALL"]},
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

-- Before the due date, not after it: the payment is not late yet, so the call is a courtesy
-- reminder and never says "debt". Same outcome tools as debt-collection, so the campaign's
-- retry policy and the sequence outcomes read both scenarios the same way.
INSERT INTO scenario(scenario_key, version, name, description, is_builtin, is_active, definition, created_by)
VALUES (
    'debt-pre-due-reminder', 1,
    $$To'lov sanasi eslatmasi (muddatidan oldin)$$,
    $$Oylik to'lov sanasi yaqinlashgan mijozga xushmuomala eslatma: qachon to'lashini yozib olish, to'lagan bo'lsa qayd etish.$$,
    true, true,
$def$
{
  "callPurpose": "Mijozning uy bo'yicha oylik to'lov sanasi yaqinlashdi, to'lov hali kechikmagan. Maqsad — xushmuomalalik bilan eslatish va qachon to'lashini bilib olish.",
  "stages": [
    {"id":"GREETING","purpose":"Ismini aytib salomlash: 'Assalomu alaykum, [Ism] aka, yaxshimisiz?' (ayol bo'lsa 'opa'). Mijoz alik olsa yoki hol so'rasa — qisqa javob berib REMINDER ga o't. Telefonni boshqa odam ko'targan bo'lsa — summa va sanani AYTMA: egasi qachon bo'lishini so'ra va CALLBACK ga o't.","allowedTransitions":["REMINDER","CALLBACK","END_CALL"],"allowedTools":[],"transitionConditions":{"REMINDER":"Mijoz telefon egasi ekani aniq va salomlashish tugadi","CALLBACK":"Telefonni boshqa odam ko'targan va u telefon egasi qachon bo'lishini aytdi"}},
    {"id":"REMINDER","purpose":"Bitta yumshoq gap bilan eslat: 'Aka, men Uysot'dan. Uyingiz bo'yicha oylik to'lov sanasi [dueDate], [summa] so'm — shuni eslatib qo'yay degandim. Vaqtida to'lay olasizmi?'. Bu QARZ EMAS — 'qarz', 'qarzdorlik', 'kechikish' so'zlarini ishlatma. Mijoz 'ha, to'layman' desa — sanani qayta so'rama, RECORD_PROMISE ga o't (dueDate ni beradi). Boshqa sana aytsa — o'sha sanani yozish uchun RECORD_PROMISE ga o't. 'To'lab qo'ydim' desa — PAYMENT_CLAIM ga o't. Band bo'lsa — CALLBACK ga o't. To'lay olmasligini aytsa — RECORD_REFUSAL ga o't.","allowedTransitions":["RECORD_PROMISE","PAYMENT_CLAIM","RECORD_REFUSAL","CALLBACK","ESCALATE_TO_HUMAN","END_CALL"],"allowedTools":[],"transitionConditions":{"RECORD_PROMISE":"Mijoz to'lash sanasini aytdi yoki 'to'layman' dedi","PAYMENT_CLAIM":"Mijoz to'laganini aytdi","RECORD_REFUSAL":"Mijoz to'lay olmasligini aytdi","CALLBACK":"Mijoz band va qayta qo'ng'iroq vaqtini aytdi","ESCALATE_TO_HUMAN":"Mijoz o'zi operator yoki menejer bilan gaplashmoqchi ekanini aytdi"}},
    {"id":"RECORD_PROMISE","title":"Va'dani yozish","type":"FUNCTION","purpose":"Mijozga eshittirmasdan recordPaymentPromise ni chaqir: promisedDate (mijoz aytgan aniq kun, yyyy-MM-dd) va aytilgan bo'lsa promisedAmount. Sanani o'zingdan to'qima. Keyin CLOSING ga o't.","action":{"toolName":"recordPaymentPromise"},"allowedTransitions":["CLOSING"],"allowedTools":[]},
    {"id":"PAYMENT_CLAIM","title":"To'langanini yozish","type":"FUNCTION","purpose":"Mijozga eshittirmasdan recordPaymentClaim ni chaqir: paidDate va paymentChannel (mijoz aytgan bo'lsa). Bahslashma — bu keyin tekshiriladi. Keyin CLOSING ga o't.","action":{"toolName":"recordPaymentClaim"},"allowedTransitions":["CLOSING"],"allowedTools":[]},
    {"id":"RECORD_REFUSAL","title":"Rad sababini yozish","type":"FUNCTION","purpose":"Mijozga eshittirmasdan recordRefusalReason ni chaqir: reasonCode va mijozning o'z so'zlari (reasonDetail). Keyin CLOSING ga o't.","action":{"toolName":"recordRefusalReason"},"allowedTransitions":["CLOSING"],"allowedTools":[]},
    {"id":"CALLBACK","title":"Qayta qo'ng'iroq","type":"FUNCTION","purpose":"Mijozga eshittirmasdan scheduleCallback ni chaqir: callbackTime (mijoz yoki telefonni ko'targan odam aytgan vaqt) va callbackReason. Keyin CLOSING ga o't.","action":{"toolName":"scheduleCallback"},"allowedTransitions":["CLOSING"],"allowedTools":[]},
    {"id":"CLOSING","purpose":"Qisqa, iliq xayrlash: 'Rahmat, aka, vaqtida to'layotganingiz uchun katta rahmat. Salomat bo'ling!'.","allowedTransitions":["END_CALL"],"allowedTools":[]},
    {"id":"ESCALATE_TO_HUMAN","purpose":"Operatorga ulashni bildirib xayrlash.","allowedTransitions":["END_CALL"],"allowedTools":[]},
    {"id":"END_CALL","purpose":"Qo'ng'iroqni yakunlash.","allowedTransitions":[],"allowedTools":[]}
  ],
  "factSchema": [
    {"name": "clientName", "type": "string", "required": true},
    {"name": "paymentAmount", "type": "number", "required": true},
    {"name": "currency", "type": "string", "required": true},
    {"name": "dueDate", "type": "date", "required": true},
    {"name": "contractNumber", "type": "string", "required": false}
  ],
  "tools": [
    {"name": "recordPaymentPromise", "disposition": "PROMISE_TO_PAY", "description": "Call when the client confirms they will pay: on a plain 'yes' pass dueDate, on another day pass the day they named. Never guess a date yourself", "params": [
      {"name": "promisedDate", "type": "futureDate", "required": true, "constraint": "promised date, today or later"},
      {"name": "paymentChannel", "type": "string", "required": false, "constraint": "CLICK, PAYME, UZUM, BANK_BRANCH, TERMINAL or OTHER. Leave empty if the client did not say"}
    ]},
    {"name": "recordPaymentClaim", "disposition": "PAYMENT_CLAIMED", "description": "Call when the client says they have ALREADY paid this month's instalment. Do not argue", "params": [
      {"name": "paidDate", "type": "pastDate", "required": false, "constraint": "payment date the client named, today or earlier"},
      {"name": "paymentChannel", "type": "string", "required": false, "constraint": "CLICK, PAYME, UZUM, BANK_BRANCH, TERMINAL or OTHER"}
    ]},
    {"name": "recordRefusalReason", "disposition": "REFUSED", "description": "Records why the client says they will not be able to pay on time", "params": [
      {"name": "reasonCode", "type": "string", "required": true, "constraint": "NO_MONEY, JOB_LOSS, ILLNESS, DISPUTES_DEBT, TECHNICAL_ISSUE or OTHER"},
      {"name": "reasonDetail", "type": "string", "required": false, "constraint": "details of the reason"}
    ]},
    {"name": "scheduleCallback", "disposition": "CALLBACK_REQUESTED", "description": "Call when the client (or whoever answered) asks to be called back later", "params": [
      {"name": "callbackTime", "type": "string", "required": true, "constraint": "callback date and time (e.g. 2026-09-02T16:00:00 or 'ertaga soat 16:00')"},
      {"name": "callbackReason", "type": "string", "required": false, "constraint": "reason or note"}
    ]}
  ],
  "outcomeSchema": [
    {"name": "promisedDate", "type": "date", "description": "Mijoz to'lashini aytgan sana"},
    {"name": "paymentChannel", "type": "string", "description": "To'lov kanali: CLICK, PAYME, UZUM, BANK_BRANCH, TERMINAL, OTHER"},
    {"name": "paidDate", "type": "date", "description": "Mijoz 'to'ladim' desa — aytgan sanasi"},
    {"name": "callbackTime", "type": "string", "description": "Qayta qo'ng'iroq vaqti"},
    {"name": "reasonCode", "type": "string", "description": "Vaqtida to'lay olmaslik sababi: NO_MONEY, JOB_LOSS, ILLNESS, DISPUTES_DEBT, TECHNICAL_ISSUE, OTHER"}
  ],
  "rolePrompt": "Siz \"Uysot\" kompaniyasining mijozlar bilan ishlash bo'limi xodimidek gaplashasiz. Mijoz uy sotib olgan va oylik to'lovni vaqtida qilib kelyapti — siz uni hurmat qilasiz va faqat do'stona eslatma qilasiz. Ohang: iliq, qisqa, rasmiyatchiliksiz.",
  "guardrails": [
    "Summa va sanani HECH QACHON o'zgartirma — faqat berilgan raqamni ayt.",
    "'Qarz', 'qarzdorlik', 'kechikish', 'jarima', 'sud' so'zlarini ishlatma: to'lov hali kechikmagan.",
    "Chegirma, imtiyoz yoki muddat uzaytirishni o'zingdan taklif qilma.",
    "Summani og'zaki ayt: 2500000 emas — 'ikki million besh yuz ming so'm'.",
    "Mijoz 'robotmisiz?' deb so'rasa — rost javob ber.",
    "Bitta qo'ng'iroq — bitta maqsad: eslatma va sana. Boshqa mavzuda uzoq gaplashma."
  ],
  "globalNodes": [
    {"question": "Siz kimsiz? Qayerdan qo'ng'iroq qilyapsiz?", "answer": "Uysot kompaniyasidan, uyingiz bo'yicha oylik to'lov haqida eslatma uchun qo'ng'iroq qilyapman."},
    {"question": "Qayerga, qanday to'lasam bo'ladi?", "answer": "Click, Payme, Uzum ilovalari orqali yoki bank filialida shartnoma raqamingiz bilan to'lashingiz mumkin."}
  ],
  "styleExamples": [
    {"language": "uz", "wrong": "\"Sizning qarzdorligingiz yuzaga kelishi mumkin.\"", "right": "\"Aka, oylik to'lov sanasi yaqinlashib qoldi, shuni eslatib qo'yay degandim.\""},
    {"language": "ru", "wrong": "\"Уведомляем вас о необходимости погашения задолженности.\"", "right": "\"Напоминаю, что скоро дата ежемесячного платежа. Успеете оплатить вовремя?\""}
  ]
}
$def$::jsonb,
    NULL
)
ON CONFLICT (scenario_key) WHERE is_active DO UPDATE
    SET definition = EXCLUDED.definition WHERE scenario.is_builtin;

-- The second call after a promise: the client named a day and the day has passed. The
-- campaign sequence below routes a PROMISE_TO_PAY here a few days later. It asks, it does
-- not accuse — the payment may well have gone through and simply not reached the books yet.
INSERT INTO scenario(scenario_key, version, name, description, is_builtin, is_active, definition, created_by)
VALUES (
    'debt-promise-follow-up', 1,
    $$To'lov va'dasini nazorat qilish$$,
    $$Va'da qilingan sana o'tgach: to'lov bo'lganini aniqlash, bo'lmagan bo'lsa yangi aniq sanani yozib olish.$$,
    true, true,
$def$
{
  "callPurpose": "Mijoz avvalgi qo'ng'iroqda qarzini ma'lum sanada to'lashga va'da bergan edi, o'sha sana o'tdi. Maqsad — to'lov bo'lganini aniqlash, bo'lmagan bo'lsa yangi aniq sana olish.",
  "stages": [
    {"id":"GREETING","purpose":"Ismini aytib salomlash: 'Assalomu alaykum, [Ism] aka, yaxshimisiz?'. Telefonni boshqa odam ko'targan bo'lsa — summani AYTMA, egasi qachon bo'lishini so'rab CALLBACK ga o't.","allowedTransitions":["PROMISE_CHECK","CALLBACK","END_CALL"],"allowedTools":[],"transitionConditions":{"PROMISE_CHECK":"Mijoz telefon egasi ekani aniq va salomlashish tugadi","CALLBACK":"Telefonni boshqa odam ko'targan va u telefon egasi qachon bo'lishini aytdi"}},
    {"id":"PROMISE_CHECK","purpose":"Ayblamasdan so'ra: 'Aka, men Uysot'dan. O'tgan safar [promisedDate]-ga to'lov qilaman degan edingiz, shuni bilay degandim — to'lov bo'ldimi?'. promisedDate ma'lum bo'lmasa: 'O'tgan safar to'lov qilaman degan edingiz'. To'lagan bo'lsa — qachon va qayerga to'laganini BITTA savol bilan so'ra va PAYMENT_CLAIM ga o't. To'lamagan bo'lsa — NEW_DATE ga o't.","allowedTransitions":["NEW_DATE","PAYMENT_CLAIM","CALLBACK","ESCALATE_TO_HUMAN","END_CALL"],"allowedTools":[],"transitionConditions":{"NEW_DATE":"Mijoz hali to'lamaganini aytdi","PAYMENT_CLAIM":"Mijoz to'laganini aytdi va qachon, qayerga to'lagani so'raldi","CALLBACK":"Mijoz band va qayta qo'ng'iroq vaqtini aytdi"}},
    {"id":"NEW_DATE","purpose":"Tushunish bildir va yangi sanani so'ra: 'Tushunaman, aka. Bo'lmasa qaysi kunga yozib qo'yay?'. Qolgan summani faqat mijoz so'rasa ayt: [debtAmount] so'm. Aniq kun aytilishi bilan RECORD_PROMISE ga o't. Noaniq javobga ('oy oxirida', 'pul bo'lganda') — yumshoq aniqlashtir: 'Bo'lmasa 25-sanaga yozib qo'yaymi?'. To'lay olmasligini aytsa — sababini so'ramasdan RECORD_REFUSAL ga o't.","allowedTransitions":["RECORD_PROMISE","RECORD_REFUSAL","CALLBACK","ESCALATE_TO_HUMAN","END_CALL"],"allowedTools":[],"transitionConditions":{"RECORD_PROMISE":"Mijoz aniq kunni aytdi","RECORD_REFUSAL":"Mijoz to'lay olmasligini aytdi","CALLBACK":"Mijoz band va qayta qo'ng'iroq vaqtini aytdi"}},
    {"id":"RECORD_PROMISE","title":"Va'dani yozish","type":"FUNCTION","purpose":"Mijozga eshittirmasdan recordPaymentPromise ni chaqir: promisedDate (mijoz aytgan aniq kun, yyyy-MM-dd) va aytilgan bo'lsa promisedAmount. Sanani o'zingdan to'qima. Keyin CLOSING ga o't.","action":{"toolName":"recordPaymentPromise"},"allowedTransitions":["CLOSING"],"allowedTools":[]},
    {"id":"PAYMENT_CLAIM","title":"To'langanini yozish","type":"FUNCTION","purpose":"Mijozga eshittirmasdan recordPaymentClaim ni chaqir: paidDate va paymentChannel (mijoz aytgan bo'lsa). Bahslashma — bu keyin tekshiriladi. Keyin CLOSING ga o't.","action":{"toolName":"recordPaymentClaim"},"allowedTransitions":["CLOSING"],"allowedTools":[]},
    {"id":"RECORD_REFUSAL","title":"Rad sababini yozish","type":"FUNCTION","purpose":"Mijozga eshittirmasdan recordRefusalReason ni chaqir: reasonCode va mijozning o'z so'zlari (reasonDetail). Keyin CLOSING ga o't.","action":{"toolName":"recordRefusalReason"},"allowedTransitions":["CLOSING"],"allowedTools":[]},
    {"id":"CALLBACK","title":"Qayta qo'ng'iroq","type":"FUNCTION","purpose":"Mijozga eshittirmasdan scheduleCallback ni chaqir: callbackTime (mijoz yoki telefonni ko'targan odam aytgan vaqt) va callbackReason. Keyin CLOSING ga o't.","action":{"toolName":"scheduleCallback"},"allowedTransitions":["CLOSING"],"allowedTools":[]},
    {"id":"CLOSING","purpose":"Bitta xabar gapi bilan xayrlash: 'Bo'ldi, aka, yozib qo'ydim. Salomat bo'ling, charchamang!'.","allowedTransitions":["END_CALL"],"allowedTools":[]},
    {"id":"ESCALATE_TO_HUMAN","purpose":"Operatorga ulashni bildirib xayrlash.","allowedTransitions":["END_CALL"],"allowedTools":[]},
    {"id":"END_CALL","purpose":"Qo'ng'iroqni yakunlash.","allowedTransitions":[],"allowedTools":[]}
  ],
  "factSchema": [
    {"name": "clientName", "type": "string", "required": true},
    {"name": "debtAmount", "type": "number", "required": true},
    {"name": "currency", "type": "string", "required": true},
    {"name": "promisedDate", "type": "date", "required": false},
    {"name": "contractNumber", "type": "string", "required": false}
  ],
  "tools": [
    {"name": "recordPaymentPromise", "disposition": "PROMISE_TO_PAY", "description": "Call when the client names a NEW exact day to pay. Do NOT call it on a vague answer — ask which day first. Guessing the date yourself is FORBIDDEN", "params": [
      {"name": "promisedDate", "type": "futureDate", "required": true, "constraint": "new promised date, today or later"},
      {"name": "promisedAmount", "type": "number", "required": false, "constraint": "promised amount"},
      {"name": "paymentChannel", "type": "string", "required": false, "constraint": "CLICK, PAYME, UZUM, BANK_BRANCH, TERMINAL or OTHER"}
    ]},
    {"name": "recordPaymentClaim", "disposition": "PAYMENT_CLAIMED", "description": "Call when the client says the promised payment has been made. Do not argue: it will be checked", "params": [
      {"name": "paidDate", "type": "pastDate", "required": false, "constraint": "payment date the client named, today or earlier"},
      {"name": "paymentChannel", "type": "string", "required": false, "constraint": "CLICK, PAYME, UZUM, BANK_BRANCH, TERMINAL or OTHER"}
    ]},
    {"name": "recordRefusalReason", "disposition": "REFUSED", "description": "Records the reason when the client says they cannot pay", "params": [
      {"name": "reasonCode", "type": "string", "required": true, "constraint": "NO_MONEY, JOB_LOSS, ILLNESS, DISPUTES_DEBT, FORGOT, TECHNICAL_ISSUE or OTHER"},
      {"name": "reasonDetail", "type": "string", "required": false, "constraint": "details of the reason"}
    ]},
    {"name": "scheduleCallback", "disposition": "CALLBACK_REQUESTED", "description": "Call when the client asks to be called back later", "params": [
      {"name": "callbackTime", "type": "string", "required": true, "constraint": "callback date and time"},
      {"name": "callbackReason", "type": "string", "required": false, "constraint": "reason or note"}
    ]}
  ],
  "outcomeSchema": [
    {"name": "promisedDate", "type": "date", "description": "Yangi va'da qilingan sana"},
    {"name": "promisedAmount", "type": "number", "description": "Va'da qilingan summa"},
    {"name": "paidDate", "type": "date", "description": "Mijoz 'to'ladim' desa — aytgan sanasi"},
    {"name": "paymentChannel", "type": "string", "description": "To'lov kanali: CLICK, PAYME, UZUM, BANK_BRANCH, TERMINAL, OTHER"},
    {"name": "reasonCode", "type": "string", "description": "Rad sababi: NO_MONEY, JOB_LOSS, ILLNESS, DISPUTES_DEBT, FORGOT, TECHNICAL_ISSUE, OTHER"}
  ],
  "rolePrompt": "Siz \"Uysot\" kompaniyasining to'lovlar bo'limi xodimidek gaplashasiz. Mijoz avval to'lashga va'da bergan — siz uni ayblamaysiz, faqat aniqlashtirasiz va yangi sanani kelishasiz. Ohang: iliq, sabrli, toshkentcha og'zaki.",
  "guardrails": [
    "Qarz summasini HECH QACHON o'zgartirma. Faqat berilgan raqamni ayt.",
    "Chegirma, imtiyoz yoki qarz kechirishni HECH QACHON taklif qilma.",
    "Va'dani buzgani uchun ayblama, uyaltirma, qo'rqitma: sud, jarima, ijro haqida gapirma.",
    "Mijoz nisbiy sana aytsa ('ertaga', 'dushanba') — BUGUNGI SANAdan hisoblab yyyy-MM-dd ko'rinishida ber.",
    "Mijoz 'to'ladim' desa — tortishma, recordPaymentClaim bilan yoz.",
    "Summani og'zaki ayt: '10 million 436 ming so'm'.",
    "Mijoz 'robotmisiz?' deb so'rasa — rost javob ber."
  ],
  "globalNodes": [
    {"question": "Siz kimsiz? Qayerdan qo'ng'iroq qilyapsiz?", "answer": "Uysot kompaniyasining to'lovlar bo'limidan, uyingiz bo'yicha qo'ng'iroq qilyapman."},
    {"question": "Qayerga, qanday to'lasam bo'ladi?", "answer": "Click, Payme, Uzum ilovalari orqali yoki bank filialida shartnoma raqamingiz bilan to'lashingiz mumkin."}
  ]
}
$def$::jsonb,
    NULL
)
ON CONFLICT (scenario_key) WHERE is_active DO UPDATE
    SET definition = EXCLUDED.definition WHERE scenario.is_builtin;

-- Lead qualification for a lead just created in Uysot (the LEAD_CREATED webhook queues the call,
-- see the lead campaigns below). The whole sale step, not just two questions: which flat and in
-- which residential complex (TJM), what is on sale there and at what price (searchFlats), an agreed
-- visit or manager call; then the lead — the call's leadId fact, else found by phone, else created
-- (createLead is asynchronous, so the new lead is looked up again) — gets a note, a task for the
-- manager at the agreed time and its funnel stage. Every CRM step is a FUNCTION stage of its own:
-- a stage's job gets no tool-result round, so a tool that needs another's answer (searchFlats a
-- complex id, moveLeadStage a stage id) runs in the stage after it. The agents copy the answers
-- into the contact's custom fields after the call (ai_agent.data_needed[].contactField). The CRM
-- tools come from the company's Uysot connection (IntegrationHttpToolFactory).
INSERT INTO scenario(scenario_key, version, name, description, is_builtin, is_active, definition, created_by)
VALUES (
    'lead-qualification-crm', 1,
    $$Lid kvalifikatsiyasi (Uysot CRM bilan)$$,
    $$Yangi lidga qo'ng'iroq: ehtiyojni aniqlash, sotuvdagi xonadonlar va narxlarni aytish, uchrashuv yoki menejer qo'ng'irog'ini kelishish; CRM'da lidni yangilash (yo'q bo'lsa yaratish), izoh va menejerga topshiriq yozish.$$,
    true, true,
$def$
{
  "callPurpose": "Mijoz Uysot CRM'da yangi lid bo'lib tushdi (reklama, sayt yoki ariza orqali). Maqsad — ehtiyojini (necha xonali, qaysi TJM, byudjet) bilish, sotuvdagi xonadonlar va narxlarni bazadan aytib berish, menejer bilan uchrashuv yoki qo'ng'iroq vaqtini kelishish, so'ng CRM'dagi lidni yangilash (yo'q bo'lsa yaratish), izoh va menejerga topshiriq yozish.",
  "stages": [
    {"id": "GREETING", "title": "Salomlashish", "purpose": "Salom hali aytilmagan bo'lsa — salomlash va sababini bitta gapda ayt: 'Assalomu alaykum, [Ism] aka! Men Uysot'dan, yangi uylarimizga ariza qoldirgan ekansiz — ikki daqiqa gaplasha olamizmi?' (ayol bo'lsa 'opa'). Salom allaqachon aytilgan bo'lsa — qayta salomlashma, mijoz javobini kut. Mijoz rozi bo'lsa yoki darrov savol bersa — NEEDS ga o't.", "allowedTransitions": ["NEEDS"], "allowedTools": [],
     "transitionConditions": {"NEEDS": "Mijoz gaplashishga rozi bo'ldi yoki xonadon haqida savol berdi"}},
    {"id": "NEEDS", "fallbackLine": "Qanaqa xonadon qarayapsiz — necha xonali?", "title": "Ehtiyoj", "skipWhenKnown": true, "purpose": "Bitta savol: 'Qanaqa xonadon qarayapsiz, aka — necha xonali?'. Mijoz o'zi qo'shimcha aytsa (byudjet, qavat, naqd/ipoteka/muddatli to'lov) — eslab qol, lekin so'roq qilma. Oldingi suhbatdan ma'lum bo'lsa — BIR MARTA qisqa tasdiqlat ('Avvalgidek 2 xonali qarayapsizmi?') va 'ha' desa darhol o't. Xonalar soni ma'lum bo'lgach — LOAD_COMPLEXES ga o't.", "allowedTransitions": ["LOAD_COMPLEXES"], "allowedTools": [],
     "transitionConditions": {"LOAD_COMPLEXES": "Mijoz necha xonali xonadon kerakligini aytdi yoki tasdiqladi"}},
    {"id": "LOAD_COMPLEXES", "title": "TJM ro'yxati", "type": "FUNCTION", "purpose": "Mijozga eshittirmasdan listResidentialComplexes ni chaqir. Keyin COMPLEX ga o't.", "action": {"toolName": "listResidentialComplexes"}, "allowedTransitions": ["COMPLEX"], "allowedTools": []},
    {"id": "COMPLEX", "fallbackLine": "Qaysi majmuamiz sizga qulay?", "title": "TJM tanlash", "skipWhenKnown": true, "purpose": "Mijoz majmuani hali aytmagan bo'lsa — bitta savol: 'Qaysi majmuamiz sizga qulay, aka? Bizda [ro'yxatdan sotuvdagi (sale=true) ko'pi bilan uchta nom] bor.' Nomlarni faqat listResidentialComplexes natijasidan ol. Mijoz shahar yoki viloyatni aytsa ('Navoiyda nima bor?') — ro'yxatdagi address bo'yicha o'sha joydagi majmualarni ayt; u yerda yo'q bo'lsa rostini ayt va sotuvdagi boshqalarini taklif qil. Oldingi suhbatdan ma'lum bo'lsa — qayta so'rama. Mijoz majmuani tanlasa yoki 'farqi yo'q' desa — SEARCH ga o't.", "allowedTransitions": ["SEARCH"], "allowedTools": [],
     "transitionConditions": {"SEARCH": "TJM ma'lum (mijoz tanladi, tasdiqladi yoki 'farqi yo'q' dedi)"}},
    {"id": "SEARCH", "title": "Xonadon qidirish", "type": "FUNCTION", "purpose": "Mijozga eshittirmasdan searchFlats ni chaqir: rooms (masalan [\"2\"]), houseIds — mijoz tanlagan TJM'ning listResidentialComplexes'dagi id si ('farqi yo'q' bo'lsa berma), mijoz byudjet aytgan bo'lsa priceTo. Keyin OFFER ga o't.", "action": {"toolName": "searchFlats"}, "allowedTransitions": ["OFFER"], "allowedTools": []},
    {"id": "OFFER", "fallbackLine": "Menejerimiz xonadonni ko'rsatib bersa — qachon qulay, bugunmi, ertagami?", "title": "Variantlar va narx", "purpose": "searchFlats natijasidan 1-2 variantni qisqa ayt: majmua, xonalar, maydon, qavat va narx — og'zaki, yaxlitlab ('taxminan 58 ming dollardan', '650 million so'mdan'). Natija bo'sh bo'lsa — rostini ayt va boshqa xonalar soni, TJM yoki byudjetni taklif qil; mijoz rozi bo'lsa searchFlats ni yangi qiymatlar bilan qayta chaqir. Mijoz savollariga (manzil, topshirish muddati, boshqa qavat yoki narx) faqat searchFlats / listResidentialComplexes natijasidan javob ber. So'ng keyingi qadamni taklif qil: 'Menejerimiz batafsil tushuntirib, xonadonni ko'rsatib bersa — qachon qulay, bugunmi, ertagami, soat nechada?'. Aniq kun va soat aytilsa yoki mijoz 'menejer o'zi telefon qilsin' desa — LEAD_CHECK ga o't.", "allowedTransitions": ["LEAD_CHECK"], "allowedTools": ["searchFlats", "listResidentialComplexes"],
     "transitionConditions": {"LEAD_CHECK": "Keyingi qadam kelishildi (uchrashuv yoki menejer qo'ng'irog'i vaqti) yoki mijoz qiziqib, suhbatni yakunlamoqchi"}},
    {"id": "LEAD_CHECK", "title": "Lid bormi?", "type": "LOGIC_SPLIT", "globalCondition": "Mijoz xonadonga qiziqishini bildirgan va endi xayrlashyapti yoki suhbatni tugatmoqchi ('xo'p, rahmat', 'bo'ldi', 'xayr', 'kutaman')",
     "allowedTransitions": ["RECORD_INTEREST", "GET_PROFILE"],
     "branches": [
       {"fact": "leadId", "operator": "EXISTS", "goTo": "RECORD_INTEREST"},
       {"fact": "crmLeadId", "operator": "EXISTS", "goTo": "RECORD_INTEREST"},
       {"operator": "ALWAYS", "goTo": "GET_PROFILE"}]},
    {"id": "GET_PROFILE", "title": "Lidni topish", "type": "FUNCTION", "purpose": "Mijozga eshittirmasdan getClientProfile ni chaqir. Lid topilsa — RECORD_INTEREST ga, topilmasa — CREATE_LEAD ga o't.", "action": {"toolName": "getClientProfile"}, "allowedTransitions": ["RECORD_INTEREST", "CREATE_LEAD"], "allowedTools": [],
     "transitionConditions": {"RECORD_INTEREST": "Lid CRM'da topildi", "CREATE_LEAD": "Lid CRM'da topilmadi yoki CRM javob bermadi"}},
    {"id": "CREATE_LEAD", "title": "Lid yaratish", "type": "FUNCTION", "purpose": "Mijozga eshittirmasdan createLead ni chaqir (name — mijoz ismi, aytmagan bo'lsa telefon raqami). Keyin REFETCH_PROFILE ga o't.", "action": {"toolName": "createLead"}, "allowedTransitions": ["REFETCH_PROFILE"], "allowedTools": []},
    {"id": "REFETCH_PROFILE", "title": "Yangi lidni topish", "type": "FUNCTION", "purpose": "Mijozga eshittirmasdan getClientProfile ni yana bir marta chaqir — yangi lidning id sini olish uchun. Topilsa — RECORD_INTEREST ga, topilmasa — RECORD_ONLY ga o't.", "action": {"toolName": "getClientProfile"}, "allowedTransitions": ["RECORD_INTEREST", "RECORD_ONLY"], "allowedTools": [],
     "transitionConditions": {"RECORD_INTEREST": "Lid CRM'da topildi", "RECORD_ONLY": "Lid hali ko'rinmadi yoki CRM javob bermadi"}},
    {"id": "RECORD_INTEREST", "title": "Qiziqishni yozish", "type": "FUNCTION", "purpose": "Mijozga eshittirmasdan recordLeadInterest ni chaqir: faqat mijoz aytgan yoki tasdiqlagan qiymatlar. Keyin SAVE_NOTE ga o't.", "action": {"toolName": "recordLeadInterest"}, "allowedTransitions": ["SAVE_NOTE"], "allowedTools": []},
    {"id": "SAVE_NOTE", "title": "Lidga izoh", "type": "FUNCTION", "purpose": "Mijozga eshittirmasdan addLeadNote bilan 1-3 gapli izoh yoz: 'Xonadon: [xonalar], TJM: [majmua], byudjet: [aytgan bo'lsa]. Aytilgan variant: [majmua, maydon, narx]. Kelishuv: [uchrashuv/qo'ng'iroq, vaqti]. [mijozning savoli yoki e'tirozi, bo'lsa]'. Keyin CREATE_TASK ga o't.", "action": {"toolName": "addLeadNote"}, "allowedTransitions": ["CREATE_TASK"], "allowedTools": []},
    {"id": "CREATE_TASK", "title": "Menejerga topshiriq", "type": "FUNCTION", "purpose": "Mijozga eshittirmasdan createFollowUpTask ni chaqir: text — menejer nima qilishi kerak ('Mijoz bilan uchrashuv: 2 xonali, Saroy TJM' yoki 'Mijozga qo'ng'iroq qilib variantlarni tushuntirish: ...'); deadlineFinishTimestamp — mijoz bilan kelishilgan kun va soat (yyyy-MM-dd HH:mm, bugungi sanadan hisobla); vaqt kelishilmagan bo'lsa — keyingi ish kuni soat 10:00. Keyin LOAD_STAGES ga o't.", "action": {"toolName": "createFollowUpTask"}, "allowedTransitions": ["LOAD_STAGES"], "allowedTools": []},
    {"id": "LOAD_STAGES", "title": "Voronka bosqichlari", "type": "FUNCTION", "purpose": "Mijozga eshittirmasdan listLeadStages ni chaqir. Keyin MOVE_STAGE ga o't.", "action": {"toolName": "listLeadStages"}, "allowedTransitions": ["MOVE_STAGE"], "allowedTools": []},
    {"id": "MOVE_STAGE", "title": "Lidni bosqichga o'tkazish", "type": "FUNCTION", "purpose": "Mijozga eshittirmasdan moveLeadStage ni chaqir: uchrashuv kelishilgan bo'lsa — nomi uchrashuv yoki tashrifga o'xshash bosqichning id si, aks holda — nomi 'Kvalifikatsiya' yoki 'Qiziqdi'ga o'xshash bosqichning id si (listLeadStages natijasidan). Mos bosqich bo'lmasa chaqirma. Keyin CLOSING ga o't.", "action": {"toolName": "moveLeadStage"}, "allowedTransitions": ["CLOSING"], "allowedTools": []},
    {"id": "RECORD_ONLY", "title": "Qiziqishni yozish (lidsiz)", "type": "FUNCTION", "purpose": "Mijozga eshittirmasdan recordLeadInterest ni chaqir: faqat mijoz aytgan yoki tasdiqlagan qiymatlar. Keyin CLOSING ga o't.", "action": {"toolName": "recordLeadInterest"}, "allowedTransitions": ["CLOSING"], "allowedTools": []},
    {"id": "CLOSING", "title": "Xayrlashish", "type": "ENDING", "purpose": "Qisqa xayrlash, mijoz tilida, savolsiz: 'Rahmat, aka! Menejerimiz kelishilgan vaqtda siz bilan bog'lanadi. Salomat bo'ling!' / 'Спасибо! Менеджер свяжется с вами в оговорённое время. Всего доброго!'."},
    {"id": "NOT_INTERESTED", "fallbackLine": "Boshqa joydan oldingizmi yoki hozircha rejada yo'qmi?", "title": "Qiziqmadi", "globalCondition": "Mijoz qiziqmasligini, boshqa joydan olganini yoki ariza qoldirmaganini aytdi", "purpose": "Bosim qilma. Sababini bitta savol bilan so'ra: 'Boshqa joydan oldingizmi yoki hozircha rejada yo'qmi?'. Mijoz javob bersa yoki aytishni istamasa — REJECT_CHECK ga o't.", "allowedTransitions": ["REJECT_CHECK"], "allowedTools": [],
     "transitionConditions": {"REJECT_CHECK": "Mijoz rad sababini aytdi yoki aytishni istamadi"}},
    {"id": "REJECT_CHECK", "title": "Lid bormi? (rad)", "type": "LOGIC_SPLIT",
     "allowedTransitions": ["RECORD_REJECT", "RECORD_REJECT_ONLY"],
     "branches": [
       {"fact": "leadId", "operator": "EXISTS", "goTo": "RECORD_REJECT"},
       {"fact": "crmLeadId", "operator": "EXISTS", "goTo": "RECORD_REJECT"},
       {"operator": "ALWAYS", "goTo": "RECORD_REJECT_ONLY"}]},
    {"id": "RECORD_REJECT", "title": "Rad sababini yozish", "type": "FUNCTION", "purpose": "Mijozga eshittirmasdan recordNotInterested ni chaqir: reason (BOUGHT_ELSEWHERE, NO_BUDGET, NOT_NOW, NEVER_APPLIED yoki OTHER) va mijozning o'z so'zlari (detail). Keyin REJECT_NOTE ga o't.", "action": {"toolName": "recordNotInterested"}, "allowedTransitions": ["REJECT_NOTE"], "allowedTools": []},
    {"id": "REJECT_NOTE", "title": "Rad izohi", "type": "FUNCTION", "purpose": "Mijozga eshittirmasdan addLeadNote bilan rad sababini mijozning o'z so'zlari bilan lidga yoz. Keyin REJECT_LOAD_STAGES ga o't.", "action": {"toolName": "addLeadNote"}, "allowedTransitions": ["REJECT_LOAD_STAGES"], "allowedTools": []},
    {"id": "REJECT_LOAD_STAGES", "title": "Voronka bosqichlari (rad)", "type": "FUNCTION", "purpose": "Mijozga eshittirmasdan listLeadStages ni chaqir. Keyin REJECT_STAGE ga o't.", "action": {"toolName": "listLeadStages"}, "allowedTransitions": ["REJECT_STAGE"], "allowedTools": []},
    {"id": "REJECT_STAGE", "title": "Lidni rad bosqichiga o'tkazish", "type": "FUNCTION", "purpose": "Mijozga eshittirmasdan moveLeadStage ni chaqir: nomi 'Rad etdi' yoki 'Qiziqmadi'ga o'xshash bosqichning id si (listLeadStages natijasidan). Mos bosqich bo'lmasa chaqirma. Keyin BYE ga o't.", "action": {"toolName": "moveLeadStage"}, "allowedTransitions": ["BYE"], "allowedTools": []},
    {"id": "RECORD_REJECT_ONLY", "title": "Rad sababini yozish (lidsiz)", "type": "FUNCTION", "purpose": "Mijozga eshittirmasdan recordNotInterested ni chaqir: reason va mijozning o'z so'zlari (detail). Keyin BYE ga o't.", "action": {"toolName": "recordNotInterested"}, "allowedTransitions": ["BYE"], "allowedTools": []},
    {"id": "BYE", "title": "Xayrlashish (rad)", "type": "ENDING", "purpose": "Qisqa, hurmatli xayrlash, mijoz tilida, savolsiz: 'Tushunarli, aka, vaqt ajratganingiz uchun rahmat. Salomat bo'ling!' / 'Понял, спасибо за время. Всего доброго!'."},
    {"id": "CALLBACK", "fallbackLine": "Qachon qo'ng'iroq qilsam qulay — bugun kechroqmi, ertagami?", "title": "Qayta qo'ng'iroq", "globalCondition": "Mijoz hozir band va boshqa vaqtda gaplashishni so'radi", "purpose": "Qulay vaqtni bitta savol bilan so'ra: 'Qachon qo'ng'iroq qilsam qulay — bugun kechroqmi, ertagami?'. Kun va soat aytilgach — SCHEDULE_CALLBACK ga o't.", "allowedTransitions": ["SCHEDULE_CALLBACK"], "allowedTools": [],
     "transitionConditions": {"SCHEDULE_CALLBACK": "Mijoz qayta qo'ng'iroq vaqtini aytdi"}},
    {"id": "SCHEDULE_CALLBACK", "title": "Qayta qo'ng'iroqni yozish", "type": "FUNCTION", "purpose": "Mijozga eshittirmasdan scheduleCallback ni chaqir: callbackTime (mijoz aytgan vaqt) va callbackReason. Keyin CALLBACK_BYE ga o't.", "action": {"toolName": "scheduleCallback"}, "allowedTransitions": ["CALLBACK_BYE"], "allowedTools": []},
    {"id": "CALLBACK_BYE", "title": "Xayrlashish (qayta qo'ng'iroq)", "type": "ENDING", "purpose": "Qisqa xayrlash, mijoz tilida, savolsiz: 'Xo'p, aka, aytgan vaqtingizda qo'ng'iroq qilaman. Salomat bo'ling!' / 'Хорошо, перезвоню в удобное вам время. Всего доброго!'."},
    {"id": "ESCALATE_TO_HUMAN", "fallbackLine": "Menejerimiz sizga qo'ng'iroq qiladi — qachon qulay?", "title": "Menejer so'radi", "globalCondition": "Mijoz o'zi tirik menejer yoki operator bilan gaplashmoqchi", "purpose": "Mijozning savolini bitta gapda aniqlab ol va menejer o'zi qo'ng'iroq qilishini ayt: 'Xo'p, aka, menejerimiz sizga qo'ng'iroq qiladi — qachon qulay?'. Javobdan keyin LEAD_CHECK ga o't.", "allowedTransitions": ["LEAD_CHECK"], "allowedTools": [],
     "transitionConditions": {"LEAD_CHECK": "Mijoz savolini aytdi va qulay vaqtni aytdi yoki aytishni istamadi"}}
  ],
  "factSchema": [
    {"name": "clientName", "type": "string", "required": false},
    {"name": "leadId", "type": "number", "required": false},
    {"name": "source", "type": "string", "required": false},
    {"name": "responsibleName", "type": "string", "required": false}
  ],
  "tools": [
    {"name": "recordLeadInterest", "disposition": "GOAL_REACHED", "description": "Records what the client wants and what was agreed. Pass only what the client actually said or confirmed", "params": [
      {"name": "flatType", "type": "string", "required": false, "constraint": "flat the client wants as they said it, e.g. '2 xonali', '3 xonali, 80 kvadrat atrofida'"},
      {"name": "residentialComplex", "type": "string", "required": false, "constraint": "residential complex (TJM) name the client chose, or 'Farqi yo'q' when they have no preference"},
      {"name": "budget", "type": "string", "required": false, "constraint": "the client's budget as they said it, e.g. '600 million so'm'"},
      {"name": "offeredFlat", "type": "string", "required": false, "constraint": "the flat you named from searchFlats: complex, rooms, area, floor, price"},
      {"name": "nextStep", "type": "string", "required": false, "constraint": "what was agreed: office visit, viewing or manager call, with the day and hour, e.g. 'ofisga tashrif, 2026-11-05 15:00'"},
      {"name": "comment", "type": "string", "required": false, "constraint": "anything else the client volunteered: floor, payment method, timeline, objection"}
    ]},
    {"name": "recordNotInterested", "disposition": "GOAL_NOT_REACHED", "description": "Call when the client clearly is not interested", "params": [
      {"name": "reason", "type": "string", "required": true, "constraint": "BOUGHT_ELSEWHERE, NO_BUDGET, NOT_NOW, NEVER_APPLIED or OTHER"},
      {"name": "detail", "type": "string", "required": false, "constraint": "the client's own words"}
    ]},
    {"name": "scheduleCallback", "disposition": "CALLBACK_REQUESTED", "description": "Call when the client asks to be called back later", "params": [
      {"name": "callbackTime", "type": "string", "required": true, "constraint": "callback date and time"},
      {"name": "callbackReason", "type": "string", "required": false, "constraint": "reason or note"}
    ]}
  ],
  "outcomeSchema": [
    {"name": "flatType", "type": "string", "description": "Mijoz qidirayotgan xonadon turi (xonalar soni)"},
    {"name": "residentialComplex", "type": "string", "description": "Mijoz tanlagan turar joy majmuasi (TJM)"},
    {"name": "budget", "type": "string", "description": "Mijoz aytgan byudjet"},
    {"name": "offeredFlat", "type": "string", "description": "Mijozga aytilgan variant va narxi"},
    {"name": "nextStep", "type": "string", "description": "Kelishilgan keyingi qadam va vaqti"},
    {"name": "comment", "type": "string", "description": "Mijoz qo'shimcha aytgan narsa"},
    {"name": "reason", "type": "string", "description": "Qiziqmaslik sababi"},
    {"name": "callbackTime", "type": "string", "description": "Qayta qo'ng'iroq vaqti"}
  ],
  "rolePrompt": "Siz \"Uysot\" ko'chmas mulk kompaniyasining sotuv menejeridek gaplashasiz. Mijoz yangi uylarimizga ariza qoldirgan. Ishingiz: ehtiyojini bilish, sotuvdagi xonadonlar va narxlarni bazadan aniq aytib berish, savollariga javob berish va menejer bilan uchrashuv yoki qo'ng'iroq vaqtini kelishish. Ohang: samimiy, ishonchli, qisqa; ortiqcha maqtovsiz, savollar bittadan.",
  "guardrails": [
    "Bo'sh xonadonlar, narx, maydon va qavatni FAQAT searchFlats natijasidan, majmua nomi, manzili va topshirish muddatini FAQAT listResidentialComplexes natijasidan ayt. Natijada yo'q narsani to'qima — 'buni menejerimiz aniqlab beradi' de.",
    "Chegirma, bo'lib to'lash, ipoteka shartlari va boshlang'ich to'lovni o'zingdan va'da qilma — 'buni menejerimiz aniq hisoblab beradi' de.",
    "Narxni og'zaki va yaxlitlab ayt ('taxminan 58 ming dollardan', '650 million so'mdan'); valyutani natijadagi currencyCcy dan ol, raqamlarni ketma-ket o'qima.",
    "Mijoz xonalar soni va TJM aniqlanmasdan narx yoki bo'sh uylar haqida so'rasa — 'Hozir qarab aytaman' de, yetishmayotganini bitta savol bilan ol va keyingi bosqichga o't. Qidiruv faqat SEARCH bosqichida; narxni hech qachon taxminan to'qima.",
    "'TJM' qisqartmasini ovoz chiqarib aytma — 'majmua' yoki 'turar joy majmuasi' de. Mijoz 'TJM' desa ham shunday tushun.",
    "Bir gapda bitta savol. Mijoz aytgan yoki tasdiqlagan narsani qayta so'rama va ikkinchi marta tasdiqlatma.",
    "Javobni 'Tushunarli', 'Bir daqiqa', 'Hozir tekshiraman' bilan boshlama — to'g'ridan-to'g'ri mazmunga o't. Mijozning ismini har gapda takrorlama.",
    "'Saqlab qo'ydim', 'mutaxassis bog'lanadi', 'xayr' kabi yakuniy gaplarni faqat xayrlashish bosqichida ayt — ungacha suhbat davom etadi.",
    "Mijoz 'xo'p, rahmat', 'bo'ldi', 'xayr' desa — suhbatni cho'zma va qayta savol berma: qiziqishi bo'lsa LEAD_CHECK ga, bo'lmasa NOT_INTERESTED ga o't.",
    "Mijoz qiziqmasa — ikkinchi marta ko'ndirishga urinma.",
    "CRM'ga yozish (createLead, addLeadNote, createFollowUpTask, moveLeadStage) haqida mijozga gapirma — jim bajar.",
    "Mijoz 'robotmisiz?' deb so'rasa — rost javob ber."
  ],
  "globalNodes": [
    {"question": "Siz kimsiz? Qayerdan qo'ng'iroq qilyapsiz?", "answer": "Uysot kompaniyasining sotuv bo'limidan. Yangi uylarimizga ariza qoldirgan ekansiz, shuning uchun qo'ng'iroq qildim."},
    {"question": "Raqamimni qayerdan oldingiz?", "answer": "Siz reklama yoki saytimiz orqali ariza qoldirgansiz. Agar xato bo'lsa, uzr so'rayman — boshqa bezovta qilmaymiz."}
  ],
  "styleExamples": [
    {"language": "uz", "wrong": "\"Iltimos, xonalar soni, majmua, byudjet va to'lov usulini ayting.\"", "right": "\"Necha xonali uy qarayapsiz, aka?\""},
    {"language": "uz", "wrong": "\"Sizga 'Navro'z' majmuasini tavsiya qilaman, u yerda narxlar arzon.\" (tool chaqirilmagan)", "right": "\"Saroy'da 2 xonali bor: 64 kvadrat, 5-qavat, taxminan 58 ming dollardan. Menejerimiz ko'rsatib bersa, ertaga qulaymi?\""},
    {"language": "uz", "wrong": "(mijoz \"xo'p, rahmat\" dedi) \"Yana savollaringiz bormi? Qaysi qavat yoqadi?\"", "right": "\"Rahmat, aka! Menejerimiz kelishilgan vaqtda bog'lanadi. Salomat bo'ling!\""},
    {"language": "ru", "wrong": "\"Предоставьте, пожалуйста, информацию о желаемом объекте недвижимости.\"", "right": "\"Какую квартиру ищете — сколько комнат?\""}
  ]
}
$def$::jsonb,
    NULL
)
ON CONFLICT (scenario_key) WHERE is_active DO UPDATE
    SET definition = EXCLUDED.definition WHERE scenario.is_builtin;

-- The daily collections call the two debt agents speak: greet, remind of the debt, find out WHY
-- it is not paid, get an exact day. The reason and the day go to the contact's custom fields
-- after the call (ai_agent.data_needed[].contactField); the day also becomes a task on the
-- client's Uysot lead during the call (createFollowUpTask), due on the day promised. The facts
-- are the ones the UYSOT_DEBTORS target source writes (CampaignTargetService#fetchDebtors).
-- Kept apart from 'debt-collection', which never asks for the reason.
INSERT INTO scenario(scenario_key, version, name, description, is_builtin, is_active, definition, created_by)
VALUES (
    'debt-collection-crm', 1,
    $$Qarzdorlik: sabab va to'lov sanasi (Uysot CRM bilan)$$,
    $$Qarzdorga qo'ng'iroq: qarzni eslatish, nega to'lanmayotganini aniqlash, aniq to'lov sanasini olish; sabab va sanani kontakt kartasiga, sanani Uysot'dagi lidga topshiriq qilib yozish.$$,
    true, true,
$def$
{
  "callPurpose": "Mijoz Uysot'dan uy olgan, oylik to'lovdan qarzi bor. Maqsad — qarzni yumshoq eslatish, nega to'lanmayotganini bilib olish va qachon to'lashini aniq kunga kelishish.",
  "stages": [
    {"id": "GREETING", "title": "Salomlashish", "purpose": "Tirik operatordek, ismini aytib salomlash: 'Assalomu alaykum, [Ism] aka, yaxshimisiz?' (ayol bo'lsa 'opa'). Mijoz hol so'rasa — qisqa javob ber ('Rahmat, yaxshi') va QAYTA hol so'rama. Bu bosqichda qarz haqida gapirma.", "allowedTransitions": ["IDENTITY_CHECK", "DEBT_NOTICE", "END_CALL", "ESCALATE_TO_HUMAN"], "allowedTools": [],
     "transitionConditions": {"DEBT_NOTICE": "Mijoz ismga e'tiroz bildirmay alik oldi", "IDENTITY_CHECK": "Mijoz kim qo'ng'iroq qilayotganini so'radi yoki telefonni boshqa odam ko'targani bilindi"}},
    {"id": "IDENTITY_CHECK", "title": "Shaxsni aniqlash", "purpose": "Faqat 'alo', 'kim bu?' bo'lsa — qisqa tanishtir: 'Aka, men Uysot'dan, uyingiz bo'yicha telefon qilyapman. [Ism] aka o'zlarimi?'. Telefonni boshqa odam ko'targan bo'lsa — qarz summasini AYTMA: egasi qachon bo'lishini so'ra va CALLBACK ga o't.", "allowedTransitions": ["DEBT_NOTICE", "CALLBACK", "END_CALL", "ESCALATE_TO_HUMAN"], "allowedTools": [],
     "transitionConditions": {"DEBT_NOTICE": "Mijoz telefon egasi ekani aniq bo'ldi", "CALLBACK": "Telefonni boshqa odam ko'targan va u telefon egasi qachon bo'lishini aytdi"}},
    {"id": "DEBT_NOTICE", "title": "Qarzni eslatish", "purpose": "Qarzni bitta qisqa, yumshoq gap bilan eslat: 'Aka, men Uysot'dan, uyingiz bo'yicha oylik to'lovdan [summa] so'm qarzdorlik bor ekan, shuni eslatib qo'yay degandim.' Keyin JIM bo'l va tingla. Mijoz eshitmagan bo'lsa — boshqa, soddaroq so'zlar bilan qayta ayt. Mijoz 'to'ladim' desa — qachon va qayerga to'laganini BITTA savol bilan so'ra, keyin PAYMENT_CLAIM ga o't. Mijoz darrov sanani aytsa — PAYMENT_DATE ga o't.", "allowedTransitions": ["REASON_INQUIRY", "PAYMENT_DATE", "PAYMENT_CLAIM", "CALLBACK", "ESCALATE_TO_HUMAN", "END_CALL"], "allowedTools": [],
     "transitionConditions": {"REASON_INQUIRY": "Mijoz qarzni eshitdi va sana aytmadi", "PAYMENT_DATE": "Mijoz o'zi to'lash sanasini aytdi", "PAYMENT_CLAIM": "Mijoz to'laganini aytdi va qachon, qayerga to'lagani so'raldi", "CALLBACK": "Mijoz band va qayta qo'ng'iroq vaqtini aytdi"}},
    {"id": "REASON_INQUIRY", "fallbackLine": "Tushunarli. To'lov kechikishining sababi nimada ekanligini ayta olasizmi?", "title": "Sababini so'rash", "purpose": "Ayblamasdan, hamdardlik bilan sababini so'ra: 'To'lov nega kechikib qoldi, aka? Biror muammo chiqdimi?'. Mijoz aytgan sababni tingla va tan ol ('Tushunaman, aka'). Mijoz sababni aytishni istamasa — tergov qilma, RECORD_REASON ga o't (reasonCode OTHER). Mijoz umuman to'lay olmasligini aytsa — RECORD_REFUSAL ga o't.", "allowedTransitions": ["RECORD_REASON", "RECORD_REFUSAL", "PAYMENT_CLAIM", "CALLBACK", "ESCALATE_TO_HUMAN", "END_CALL"], "allowedTools": [],
     "transitionConditions": {"RECORD_REASON": "Mijoz sababni aytdi yoki aytishni istamadi", "RECORD_REFUSAL": "Mijoz umuman to'lay olmasligini aytdi", "PAYMENT_CLAIM": "Mijoz to'laganini aytdi", "CALLBACK": "Mijoz band va qayta qo'ng'iroq vaqtini aytdi"}},
    {"id": "PAYMENT_DATE", "fallbackLine": "Tushunarli. Qaysi sanada to'lov qila olasiz?", "title": "To'lov sanasi", "purpose": "Aniq kunni so'ra: 'Qachonga to'lab bera olasiz, aka?'. Noaniq javobga ('oy oxirida', 'pul bo'lganda', '12 yo 13-ga') yumshoq aniqlashtir: 'Bo'lmasa 12-sanaga yozib qo'yaymi?'. Aniq kun aytilishi bilan RECORD_PROMISE ga o't. Mijoz to'lay olmasligini aytsa — RECORD_REFUSAL ga o't.", "allowedTransitions": ["RECORD_PROMISE", "RECORD_REFUSAL", "CALLBACK", "ESCALATE_TO_HUMAN", "END_CALL"], "allowedTools": [],
     "transitionConditions": {"RECORD_PROMISE": "Mijoz aniq kunni aytdi", "RECORD_REFUSAL": "Mijoz to'lay olmasligini aytdi va kun aytmadi", "CALLBACK": "Mijoz band va qayta qo'ng'iroq vaqtini aytdi"}},
    {"id": "RECORD_REASON", "title": "Sababni yozish", "type": "FUNCTION", "purpose": "Mijozga eshittirmasdan recordDelayReason ni chaqir: reasonCode va mijozning o'z so'zlari (reasonDetail). Keyin PAYMENT_DATE ga o't.", "action": {"toolName": "recordDelayReason"}, "allowedTransitions": ["PAYMENT_DATE"], "allowedTools": []},
    {"id": "RECORD_PROMISE", "title": "Va'dani yozish", "type": "FUNCTION", "purpose": "Mijozga eshittirmasdan recordPaymentPromise ni chaqir: promisedDate (mijoz aytgan aniq kun, yyyy-MM-dd) va aytilgan bo'lsa promisedAmount. Sanani o'zingdan to'qima. Keyin CREATE_TASK ga o't.", "action": {"toolName": "recordPaymentPromise"}, "allowedTransitions": ["CREATE_TASK"], "allowedTools": []},
    {"id": "CREATE_TASK", "title": "CRM topshirig'i", "type": "FUNCTION", "purpose": "Mijozga eshittirmasdan createFollowUpTask bilan Uysot'dagi lidga topshiriq och: title 'To'lov va'dasi: [summa] so'm', dueTime '[promisedDate] 10:00', details — mijoz aytgan sabab va summa. Keyin CONFIRMATION ga o't.", "action": {"toolName": "createFollowUpTask"}, "allowedTransitions": ["CONFIRMATION"], "allowedTools": []},
    {"id": "RECORD_REFUSAL", "title": "Rad sababini yozish", "type": "FUNCTION", "purpose": "Mijozga eshittirmasdan recordRefusalReason ni chaqir: reasonCode va mijozning o'z so'zlari (reasonDetail). Keyin CLOSING ga o't.", "action": {"toolName": "recordRefusalReason"}, "allowedTransitions": ["CLOSING"], "allowedTools": []},
    {"id": "PAYMENT_CLAIM", "title": "To'langanini yozish", "type": "FUNCTION", "purpose": "Mijozga eshittirmasdan recordPaymentClaim ni chaqir: paidDate va paymentChannel (mijoz aytgan bo'lsa). Bahslashma — bu keyin tekshiriladi. Keyin CLOSING ga o't.", "action": {"toolName": "recordPaymentClaim"}, "allowedTransitions": ["CLOSING"], "allowedTools": []},
    {"id": "CALLBACK", "title": "Qayta qo'ng'iroq", "type": "FUNCTION", "purpose": "Mijozga eshittirmasdan scheduleCallback ni chaqir: callbackTime (mijoz yoki telefonni ko'targan odam aytgan vaqt) va callbackReason. Keyin CLOSING ga o't.", "action": {"toolName": "scheduleCallback"}, "allowedTransitions": ["CLOSING"], "allowedTools": []},
    {"id": "CONFIRMATION", "title": "Sanani tasdiqlash", "purpose": "Sanani BITTA xabar gapi bilan qayd et (savol emas, qayta tasdiqlatma) va shu zahoti xayrlash: 'Bo'ldi, aka, 12-sanaga yozib qo'ydim. Salomat bo'ling, charchamang!'.", "allowedTransitions": ["CLOSING", "END_CALL"], "allowedTools": []},
    {"id": "CLOSING", "title": "Xayrlashish", "purpose": "Qisqa, iliq xayrlash: 'Xo'p, aka, rahmat. Salomat bo'ling, charchamang!'.", "allowedTransitions": ["END_CALL"], "allowedTools": []},
    {"id": "ESCALATE_TO_HUMAN", "title": "Operatorga ulash", "purpose": "Mijoz o'zi operator so'rasa: savolini bir gapda aniqlab ol va operatorga ulashni bildir. Keyin ESCALATE_NOTE ga o't.", "allowedTransitions": ["ESCALATE_NOTE", "END_CALL"], "allowedTools": []},
    {"id": "ESCALATE_NOTE", "title": "Savolni yozish", "type": "FUNCTION", "purpose": "Mijozga eshittirmasdan addLeadNote bilan mijozning savolini lidga yoz. Keyin END_CALL ga o't.", "action": {"toolName": "addLeadNote"}, "allowedTransitions": ["END_CALL"], "allowedTools": []},
    {"id": "END_CALL", "title": "Qo'ng'iroqni tugatish", "purpose": "Qo'ng'iroqni yakunlash.", "allowedTransitions": [], "allowedTools": []}
  ],
  "factSchema": [
    {"name": "clientName", "type": "string", "required": true},
    {"name": "debtAmount", "type": "number", "required": true},
    {"name": "currency", "type": "string", "required": true},
    {"name": "contractNumber", "type": "string", "required": false},
    {"name": "delayDays", "type": "number", "required": false}
  ],
  "tools": [
    {"name": "recordDelayReason", "description": "Records WHY the payment is late, in the client's own words. Call it as soon as the client explains; it does not end the call", "params": [
      {"name": "reasonCode", "type": "string", "required": true, "constraint": "NO_MONEY, SALARY_DELAY, JOB_LOSS, ILLNESS, FORGOT, DISPUTES_DEBT, TECHNICAL_ISSUE or OTHER"},
      {"name": "reasonDetail", "type": "string", "required": false, "constraint": "the reason as the client said it, one sentence"}
    ]},
    {"name": "recordPaymentPromise", "disposition": "PROMISE_TO_PAY", "description": "Call when the client names an EXACT DAY themselves (e.g. \"10-oktabr\", \"ertaga\"). Do NOT call it on a vague answer like \"oy oxirida\" or \"pul bo'lganda\" — first ask which day. Guessing the date yourself is FORBIDDEN", "params": [
      {"name": "promisedDate", "type": "futureDate", "required": true, "constraint": "promised date, today or later, yyyy-MM-dd"},
      {"name": "promisedAmount", "type": "number", "required": false, "constraint": "promised amount, only if the client named one"}
    ]},
    {"name": "recordRefusalReason", "disposition": "REFUSED", "description": "Call when the client says they cannot pay at all and gives no day", "params": [
      {"name": "reasonCode", "type": "string", "required": true, "constraint": "NO_MONEY, JOB_LOSS, ILLNESS, DISPUTES_DEBT or OTHER"},
      {"name": "reasonDetail", "type": "string", "required": false, "constraint": "details of the reason"}
    ]},
    {"name": "recordPaymentClaim", "disposition": "PAYMENT_CLAIMED", "description": "Call when the client says they have ALREADY paid. Ask when and where first. Do not argue: it will be checked", "params": [
      {"name": "paidDate", "type": "pastDate", "required": false, "constraint": "payment date the client named, today or earlier"},
      {"name": "paymentChannel", "type": "string", "required": false, "constraint": "CLICK, PAYME, UZUM, BANK_BRANCH, TERMINAL or OTHER"}
    ]},
    {"name": "scheduleCallback", "disposition": "CALLBACK_REQUESTED", "description": "Call when the client (or whoever answered) asks to be called back later", "params": [
      {"name": "callbackTime", "type": "string", "required": true, "constraint": "callback date and time (e.g. 2026-09-02T16:00:00 or 'ertaga soat 16:00')"},
      {"name": "callbackReason", "type": "string", "required": false, "constraint": "reason or note"}
    ]}
  ],
  "outcomeSchema": [
    {"name": "reasonCode", "type": "string", "description": "To'lov kechikish sababi: NO_MONEY, SALARY_DELAY, JOB_LOSS, ILLNESS, FORGOT, DISPUTES_DEBT, TECHNICAL_ISSUE, OTHER"},
    {"name": "reasonDetail", "type": "string", "description": "Sabab mijozning o'z so'zlari bilan"},
    {"name": "promisedDate", "type": "date", "description": "Mijoz va'da qilgan to'lov sanasi"},
    {"name": "promisedAmount", "type": "number", "description": "Mijoz va'da qilgan summa"},
    {"name": "paidDate", "type": "date", "description": "Mijoz 'to'laganman' desa — aytgan sanasi"},
    {"name": "callbackTime", "type": "string", "description": "Mijoz so'ragan qayta qo'ng'iroq vaqti"}
  ],
  "rolePrompt": "Siz \"Uysot\" kompaniyasining to'lovlar bo'limi xodimidek gaplashasiz: mijozlar uy sotib olgan, oylik to'lovdan qarzi bor. Ohangingiz — iliq, samimiy toshkentlik operator: hurmat bilan, lekin rasmiyatchiliksiz. Siz qarzni talab qilmaysiz — eslatasiz, sababini tushunasiz va sanani kelishasiz.",
  "guardrails": [
    "Qarz summasini HECH QACHON o'zgartirma. Faqat berilgan raqamni ayt.",
    "Chegirma, imtiyoz, muddat uzaytirish yoki qarz kechirishni HECH QACHON taklif qilma.",
    "Huquqiy oqibatlar, sud, jarima yoki ijro haqida o'zingdan gapirma, qo'rqitma.",
    "Sababni bir marta so'ra; tergov qilma, mijozni ayblama va uyaltirma.",
    "Mijoz nisbiy sana aytsa (\"ertaga\", \"dushanba\", \"kelasi oyning 5-sanasi\") — uni BUGUNGI SANAdan hisoblab yyyy-MM-dd ko'rinishida ber. Yilni o'zingdan to'qima.",
    "Telefonni boshqa odam ko'targan bo'lsa — qarz summasini aytma.",
    "CRM'ga yozish (createFollowUpTask, addLeadNote) haqida mijozga gapirma — jim bajar.",
    "Summani og'zaki ayt: 10436000 emas — '10 million 436 ming so'm'.",
    "Mijoz 'robotmisiz?' deb so'rasa — rost javob ber.",
    "Operatorga faqat mijoz o'zi 'operator', 'odam bilan gaplashmoqchiman' desa ula."
  ],
  "globalNodes": [
    {"question": "Siz kimsiz? Qayerdan qo'ng'iroq qilyapsiz?", "answer": "Uysot kompaniyasining to'lovlar bo'limidan, uyingiz bo'yicha qo'ng'iroq qilyapman."},
    {"question": "Qayerga, qanday to'lasam bo'ladi?", "answer": "Click, Payme, Uzum ilovalari orqali yoki bank filialida shartnoma raqamingiz bilan to'lashingiz mumkin."}
  ],
  "styleExamples": [
    {"language": "uz", "wrong": "\"Nima sababdan to'lovni amalga oshirmadingiz? Izoh bering.\"", "right": "\"To'lov nega kechikib qoldi, aka? Biror muammo chiqdimi?\""},
    {"language": "uz", "wrong": "(mijoz \"maosh kechikdi\" dedi) \"Bu sizning muammoingiz, to'lashingiz shart.\"", "right": "\"Tushunaman, aka, bo'lib turadi. Bo'lmasa qachonga to'lab bera olasiz?\""},
    {"language": "uz", "wrong": "(mijoz \"20-sanada\" dedi) \"20-oktabrda 1500000 so'm to'lay olasizmi?\"", "right": "\"Bo'ldi, aka, 20-sanaga yozib qo'ydim. Salomat bo'ling, charchamang!\""},
    {"language": "ru", "wrong": "\"Укажите причину неоплаты задолженности.\"", "right": "\"Подскажите, почему задержался платёж? Что-то случилось?\""}
  ]
}
$def$::jsonb,
    NULL
)
ON CONFLICT (scenario_key) WHERE is_active DO UPDATE
    SET definition = EXCLUDED.definition WHERE scenario.is_builtin;

-- ---------------------------------------------------------------------------
-- AI agents. Four, each one ready to place a real call the day the database is created —
-- speech engine, voices per language, greeting, handbook, pronunciation, data to extract
-- and judge, limits and retention all filled in:
--
--   Lid kvalifikatsiya (cascade)    outbound · CASCADE  · lead-qualification-crm
--   Lid kvalifikatsiya (realtime)   outbound · REALTIME · lead-qualification-crm
--   Qarzdorlik (cascade)            outbound · CASCADE  · debt-collection-crm
--   Qarzdorlik (realtime)           outbound · REALTIME · debt-collection-crm
--
-- Each pair differs in nothing but the engine: the same scenario, the same guardrails, the
-- same data to extract, so a call placed by one can be compared with a call placed by the
-- other and the difference is the pipeline alone.
--
-- data_needed[].contactField is what lands on the contact card after the call
-- (CallMemoryWriter#saveContactFields): the lead agents write xonadon_turi and tjm, the debt
-- agents tolamaslik_sababi and tolov_sanasi.
--
-- The Uysot tools (getClientProfile, addLeadNote, moveLeadStage, createFollowUpTask,
-- createLead, listResidentialComplexes, the debt lookups...) are not attached per agent:
-- every agent of a company connected to Uysot is offered the tools the company has switched
-- on (IntegrationHttpToolFactory), which is why the integration is seeded CONNECTED further down.
--
-- Looked up by name (there is no unique constraint on it), so a rerun adds nothing and
-- overwrites nothing an operator has since edited.
-- ---------------------------------------------------------------------------

-- Lead qualification, cascade pipeline: Yandex recognition, Gemini for the turn, Yandex
-- synthesis. HIGH interruption sensitivity: a prospect who starts talking is interested and
-- should not be talked over.
INSERT INTO ai_agent (company_id, name, description, channel, agent_mode, scenario_id, scenario_mode, language,
                      speech_languages, persona, greeting, pipeline_mode, stt_provider, tts_provider,
                      llm_model, fast_llm_model, temperature, tts_voice, speaking_warmth, interruption_sensitivity,
                      use_rag, voicemail_action, voicemail_message, transfer_message, boosted_keywords, timezone,
                      max_conversation_duration_seconds, silence_end_call_timeout_seconds, concurrent_calls_limit,
                      daily_calls_limit, conversation_retention_days, data_needed, data_evaluation)
SELECT 1, 'Lid kvalifikatsiya (cascade)',
       'Uysot''da yangi lid yaratilganda qo''ng''iroq qiladi: qanday xonadon va qaysi TJM''dan olmoqchiligini aniqlab kontaktga yozadi, CRM''dagi lidni yangilab izoh qoldiradi. STT + LLM + TTS (Yandex + Gemini).',
       'VOICE', 'CONVERSATIONAL_FLOW',
       (SELECT id FROM scenario WHERE scenario_key = 'lead-qualification-crm' AND is_active LIMIT 1),
       'STRUCTURED_STEPS', 'uz-UZ', '["uz-UZ"]'::jsonb, 'AI_ASSISTANT',
       -- A fixed opening line per language, spoken the moment the call connects: left to the
       -- model, the greeting waited on two LLM rounds and a CRM lookup (9s of dead air, then a
       -- "let me check" filler before the hello). The client's name comes from the call's facts.
       '{"mode": "AI_CUSTOM", "pauseBeforeSpeakingMs": 400, "lines": [
          {"direction": "OUTBOUND", "language": "uz", "text": "Assalomu alaykum, {{clientName | hurmatli mijoz}}! Men Uysot kompaniyasidanman, yangi uylarimizga ariza qoldirgan ekansiz — ikki daqiqa gaplashishga vaqtingiz bormi?"},
          {"direction": "OUTBOUND", "language": "ru", "text": "Здравствуйте! Это Uysot, вы оставили заявку на квартиру в наших новостройках — удобно поговорить пару минут?"}]}'::jsonb,
       'CASCADE', 'yandex', 'yandex', 'gemini-3.8-flash', 'gemini-3.5-flash-lite', 0.5, 'zamira', 'WARM', 'HIGH',
       false, 'LEAVE_MESSAGE',
       'Assalomu alaykum! Uysot''dan, yangi uylarimizga ariza qoldirgan ekansiz. Qulay vaqtda qayta qo''ng''iroq qilamiz.',
       'Hozir sizni menejerimizga ulayman, bir daqiqa kuting.',
       '["Uysot", "xonadon", "xonali", "TJM", "majmua", "kvadrat", "ipoteka"]'::jsonb, 'Asia/Tashkent',
       300, 20, 10, 500, 180,
       '[{"id": "xonadon_turi", "type": "String", "name": "Xonadon turi", "description": "Mijoz qidirayotgan xonadon: necha xonali (masalan ''2 xonali''); maydon yoki qavat aytgan bo''lsa qo''shib yoz. Aytmagan bo''lsa bo''sh", "contactField": "xonadon_turi"},
         {"id": "tjm", "type": "String", "name": "TJM", "description": "Mijoz olmoqchi bo''lgan turar joy majmuasining nomi; tanlamagan bo''lsa ''Farqi yo''q''. Aytmagan bo''lsa bo''sh", "contactField": "tjm"},
         {"id": "tolov_turi", "type": "String", "name": "To''lov turi", "description": "Mijoz qanday to''lamoqchi: ''naqd'', ''ipoteka'' yoki ''muddatli''. Aytmagan bo''lsa bo''sh", "contactField": "tolov_turi"},
         {"id": "byudjet", "type": "String", "name": "Byudjet", "description": "Mijoz aytgan taxminiy byudjet, o''zi aytganidek (masalan ''600 million so''m''). Aytmagan bo''lsa bo''sh", "contactField": "byudjet"}]'::jsonb,
       '[{"id": "qualified", "name": "Kvalifikatsiyadan o''tdi", "criteria": "Xonadon turi va TJM ikkalasi ham aniqlandimi?"},
         {"id": "crm_updated", "name": "CRM yangilandi", "criteria": "Agent addLeadNote bilan lidga izoh yozdimi va moveLeadStage bilan bosqichini o''zgartirdimi?"},
         {"id": "no_invented_facts", "name": "Faktlar to''qilmadi", "criteria": "Agent narx, maydon yoki muddat haqida o''zidan gapirmadimi, majmua nomlari faqat tool natijasidanmi?"}]'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM ai_agent WHERE company_id = 1 AND name = 'Lid kvalifikatsiya (cascade)');

-- The same call on a speech-to-speech engine. llm_model is the realtime model here, not
-- a text one (RealtimeCallConfig#modelOr), and tts_voice must be a catalog id owned by
-- the same provider or the engine falls back to the voice it was deployed with.
-- realtime_facts_delivery TOOL: the lead's facts are handed over when the model asks, never
-- written into the vendor-side instructions.
INSERT INTO ai_agent (company_id, name, description, channel, agent_mode, scenario_id, scenario_mode, language,
                      speech_languages, persona, pipeline_mode, realtime_provider, llm_model, tts_voice,
                      realtime_facts_delivery, temperature, speaking_warmth, interruption_sensitivity,
                      use_rag, voicemail_action, voicemail_message, transfer_message, timezone,
                      max_conversation_duration_seconds, silence_end_call_timeout_seconds, concurrent_calls_limit,
                      daily_calls_limit, conversation_retention_days, data_needed, data_evaluation)
SELECT 1, 'Lid kvalifikatsiya (realtime)',
       'Uysot''da yangi lid yaratilganda qo''ng''iroq qiladi: Gemini Live nativ audio (speech-to-speech). Cascade agent bilan bir xil ssenariy.',
       'VOICE', 'CONVERSATIONAL_FLOW',
       (SELECT id FROM scenario WHERE scenario_key = 'lead-qualification-crm' AND is_active LIMIT 1),
       'STRUCTURED_STEPS', 'uz-UZ', '["uz-UZ", "ru-RU"]'::jsonb, 'AI_ASSISTANT',
       'REALTIME', 'gemini-live', 'gemini-3.8-live', 'gemini-kore-uz',
       'TOOL', 0.5, 'WARM', 'HIGH',
       false, 'LEAVE_MESSAGE',
       'Assalomu alaykum! Uysot''dan, yangi uylarimizga ariza qoldirgan ekansiz. Qulay vaqtda qayta qo''ng''iroq qilamiz.',
       'Hozir sizni menejerimizga ulayman, bir daqiqa kuting.', 'Asia/Tashkent',
       300, 20, 10, 500, 180,
       (SELECT data_needed FROM ai_agent WHERE company_id = 1 AND name = 'Lid kvalifikatsiya (cascade)' LIMIT 1),
       (SELECT data_evaluation FROM ai_agent WHERE company_id = 1 AND name = 'Lid kvalifikatsiya (cascade)' LIMIT 1)
WHERE NOT EXISTS (SELECT 1 FROM ai_agent WHERE company_id = 1 AND name = 'Lid kvalifikatsiya (realtime)');

-- Outbound collections, cascade pipeline. Low temperature: the wording of a debt call is the
-- scenario's, not the model's.
INSERT INTO ai_agent (company_id, name, description, channel, agent_mode, scenario_id, scenario_mode, language,
                      speech_languages, persona, greeting, pipeline_mode, stt_provider, tts_provider,
                      llm_model, fast_llm_model, temperature, tts_voice, speaking_warmth, interruption_sensitivity,
                      use_rag, voicemail_action, voicemail_message, transfer_message, boosted_keywords, timezone,
                      max_conversation_duration_seconds, silence_end_call_timeout_seconds, concurrent_calls_limit,
                      daily_calls_limit, conversation_retention_days, data_needed, data_evaluation, post_call_actions)
SELECT 1, 'Qarzdorlik (cascade)',
       'Har kuni Uysot''dagi qarzdorlarga qo''ng''iroq: qarzni eslatadi, nega to''lanmayotganini va qachon to''lashini aniqlab kontaktga yozadi, CRM''da o''sha sanaga topshiriq ochadi. STT + LLM + TTS (Yandex + Gemini).',
       'VOICE', 'CONVERSATIONAL_FLOW',
       (SELECT id FROM scenario WHERE scenario_key = 'debt-collection-crm' AND is_active LIMIT 1),
       'STRUCTURED_STEPS', 'uz-UZ', '["uz-UZ", "ru-RU"]'::jsonb, 'AI_ASSISTANT',
       '{"mode": "AI_CUSTOM", "pauseBeforeSpeakingMs": 500, "lines": [
          {"direction": "OUTBOUND", "language": "ru", "text": "Здравствуйте, {{clientName}}! Это компания Uysot, по вашей квартире."}]}'::jsonb,
       'CASCADE', 'yandex', 'yandex', 'gemini-3.8-flash', 'gemini-3.5-flash-lite', 0.3, 'nigora', 'WARM', 'MEDIUM',
       false, 'LEAVE_MESSAGE',
       -- No amount and no deadline in the machine message: the scenario guardrails forbid
       -- saying either without the client on the line to confirm who is listening.
       'Assalomu alaykum. Shartnomangiz bo''yicha savol bilan qo''ng''iroq qildik. Iltimos, qulay vaqtda qayta bog''laning.',
       'Hozir sizni operatorga ulayman, bir daqiqa kuting.',
       '["Uysot", "shartnoma", "to''lov", "qarzdorlik", "maosh", "Click", "Payme", "Uzum", "terminal"]'::jsonb, 'Asia/Tashkent',
       300, 15, 10, 500, 90,
       '[{"id": "tolamaslik_sababi", "type": "String", "name": "To''lamaslik sababi", "description": "Mijoz to''lov nega kechikkanini qanday tushuntirgan bo''lsa — qisqa, o''z so''zlari bilan (masalan ''maosh kechikdi''). Aytmagan bo''lsa bo''sh", "contactField": "tolamaslik_sababi"},
         {"id": "tolov_sanasi", "type": "String", "name": "To''lov sanasi", "description": "Mijoz to''lashini aytgan aniq sana, yyyy-MM-dd. Aniq kun aytmagan bo''lsa bo''sh", "contactField": "tolov_sanasi"}]'::jsonb,
       '[{"id": "clear_commitment", "name": "Aniq va''da olindi", "criteria": "Mijoz aniq kun aytdimi (\"oy oxirida\" emas, aniq sana)?"},
         {"id": "reason_found", "name": "Sabab aniqlandi", "criteria": "Agent to''lov nega kechikkanini so''radimi va mijoz javobini recordDelayReason bilan yozdimi?"},
         {"id": "crm_task_created", "name": "CRM topshirig''i ochildi", "criteria": "Sana kelishilgan bo''lsa, agent createFollowUpTask bilan o''sha sanaga topshiriq ochdimi?"},
         {"id": "guardrails_kept", "name": "Qoidalar buzilmadi", "criteria": "Agent summani o''zgartirmadi, chegirma va''da qilmadi, qo''rqitmadi va sud/jarima haqida gapirmadimi?"}]'::jsonb,
       '[{"id": "promise-sms", "name": "Va''da tasdig''i SMS", "actionType": "SEND_SMS", "triggerDisposition": "PROMISE_TO_PAY", "onlyOnSuccess": true,
          "template": "{{client_name}}, to''lov sanasini kelishganimiz uchun rahmat. To''lovni Click, Payme yoki Uzum orqali qilishingiz mumkin. Uysot", "webhookUrl": null, "headers": {}, "enabled": true}]'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM ai_agent WHERE company_id = 1 AND name = 'Qarzdorlik (cascade)');

-- The same call on Gemini Live. realtime_facts_delivery TOOL: the debt figures are handed
-- over one at a time when the model asks, never written into the vendor-side instructions.
INSERT INTO ai_agent (company_id, name, description, channel, agent_mode, scenario_id, scenario_mode, language,
                      speech_languages, persona, pipeline_mode, realtime_provider, llm_model, tts_voice,
                      realtime_facts_delivery, temperature, speaking_warmth, interruption_sensitivity,
                      use_rag, voicemail_action, voicemail_message, transfer_message, timezone,
                      max_conversation_duration_seconds, silence_end_call_timeout_seconds, concurrent_calls_limit,
                      daily_calls_limit, conversation_retention_days, data_needed, data_evaluation, post_call_actions)
SELECT 1, 'Qarzdorlik (realtime)',
       'Har kuni Uysot''dagi qarzdorlarga qo''ng''iroq: Gemini Live nativ audio (speech-to-speech). Cascade agent bilan bir xil ssenariy.',
       'VOICE', 'CONVERSATIONAL_FLOW',
       (SELECT id FROM scenario WHERE scenario_key = 'debt-collection-crm' AND is_active LIMIT 1),
       'STRUCTURED_STEPS', 'uz-UZ', '["uz-UZ", "ru-RU"]'::jsonb, 'AI_ASSISTANT',
       'REALTIME', 'gemini-live', 'gemini-3.8-live', 'gemini-aoede-uz',
       'TOOL', 0.3, 'WARM', 'MEDIUM',
       false, 'LEAVE_MESSAGE',
       'Assalomu alaykum. Shartnomangiz bo''yicha savol bilan qo''ng''iroq qildik. Iltimos, qulay vaqtda qayta bog''laning.',
       'Hozir sizni operatorga ulayman, bir daqiqa kuting.', 'Asia/Tashkent',
       300, 15, 10, 500, 90,
       (SELECT data_needed FROM ai_agent WHERE company_id = 1 AND name = 'Qarzdorlik (cascade)' LIMIT 1),
       (SELECT data_evaluation FROM ai_agent WHERE company_id = 1 AND name = 'Qarzdorlik (cascade)' LIMIT 1),
       (SELECT post_call_actions FROM ai_agent WHERE company_id = 1 AND name = 'Qarzdorlik (cascade)' LIMIT 1)
WHERE NOT EXISTS (SELECT 1 FROM ai_agent WHERE company_id = 1 AND name = 'Qarzdorlik (realtime)');

-- A database seeded before data_needed named a contactField keeps agents whose extracted values
-- never reach the contact card (the INSERTs above skip an agent that already exists). Written only
-- while no field of the agent names a contactField, so a list an operator has edited stays.
UPDATE ai_agent SET data_needed = '[
    {"id": "xonadon_turi", "type": "String", "name": "Xonadon turi", "description": "Mijoz qidirayotgan xonadon: necha xonali (masalan ''2 xonali''); maydon yoki qavat aytgan bo''lsa qo''shib yoz. Aytmagan bo''lsa bo''sh", "contactField": "xonadon_turi"},
    {"id": "tjm", "type": "String", "name": "TJM", "description": "Mijoz olmoqchi bo''lgan turar joy majmuasining nomi; tanlamagan bo''lsa ''Farqi yo''q''. Aytmagan bo''lsa bo''sh", "contactField": "tjm"},
    {"id": "tolov_turi", "type": "String", "name": "To''lov turi", "description": "Mijoz qanday to''lamoqchi: ''naqd'', ''ipoteka'' yoki ''muddatli''. Aytmagan bo''lsa bo''sh", "contactField": "tolov_turi"},
    {"id": "byudjet", "type": "String", "name": "Byudjet", "description": "Mijoz aytgan taxminiy byudjet, o''zi aytganidek (masalan ''600 million so''m''). Aytmagan bo''lsa bo''sh", "contactField": "byudjet"}
  ]'::jsonb
 WHERE company_id = 1 AND name IN ('Lid kvalifikatsiya (cascade)', 'Lid kvalifikatsiya (realtime)')
   AND NOT EXISTS (SELECT 1 FROM jsonb_array_elements(data_needed) f WHERE f ? 'contactField');

UPDATE ai_agent SET data_needed = '[
    {"id": "tolamaslik_sababi", "type": "String", "name": "To''lamaslik sababi", "description": "Mijoz to''lov nega kechikkanini qanday tushuntirgan bo''lsa — qisqa, o''z so''zlari bilan (masalan ''maosh kechikdi''). Aytmagan bo''lsa bo''sh", "contactField": "tolamaslik_sababi"},
    {"id": "tolov_sanasi", "type": "String", "name": "To''lov sanasi", "description": "Mijoz to''lashini aytgan aniq sana, yyyy-MM-dd. Aniq kun aytmagan bo''lsa bo''sh", "contactField": "tolov_sanasi"}
  ]'::jsonb
 WHERE company_id = 1 AND name IN ('Qarzdorlik (cascade)', 'Qarzdorlik (realtime)')
   AND NOT EXISTS (SELECT 1 FROM jsonb_array_elements(data_needed) f WHERE f ? 'contactField');

-- The developer's own number, so a test call to the agents above has a contact card to land its
-- extracted fields on. Outside the demo-contacts block, which returns early on an existing database.
INSERT INTO contact (company_id, name, phone, created_at)
SELECT 1, 'Murodjon', '+998953692029', now()
WHERE NOT EXISTS (SELECT 1 FROM contact WHERE company_id = 1 AND phone = '+998953692029');

-- Each agent's own function list (Retell "Functions"): only the Uysot tools its call needs, so the
-- editor shows what the agent can do and a lead call is not offered the debt lookups (or the
-- reverse). The HTTP "_LIVE" twins are left out: the built-in tool of the same job is listed.
-- Written only while an agent has never saved a list, so an edit made in the studio stays.
UPDATE ai_agent SET functions = '[
    {"id": "fn_end_call", "type": "END_CALL", "name": "endCall", "speakDuringExecution": false},
    {"id": "fn_transfer_call", "type": "TRANSFER_CALL", "name": "requestHumanTransfer", "speakDuringExecution": false},
    {"id": "fn_int_getClientProfile", "type": "INTEGRATION", "name": "getClientProfile", "speakDuringExecution": false, "integrationTool": "getClientProfile"},
    {"id": "fn_int_listResidentialComplexes", "type": "INTEGRATION", "name": "listResidentialComplexes", "speakDuringExecution": false, "integrationTool": "listResidentialComplexes"},
    {"id": "fn_int_searchFlats", "type": "INTEGRATION", "name": "searchFlats", "speakDuringExecution": false, "integrationTool": "searchFlats"},
    {"id": "fn_extract_rememberFlatInterest", "type": "EXTRACT_VARIABLE", "name": "rememberFlatInterest", "description": "Remembers the flat the client liked or chose, so the next call or chat continues from it. Call it when the client says a specific flat suits them, asks to hold it, or wants to see it.", "speakDuringExecution": false, "extract": {"variables": [{"name": "interestedFlat", "type": "STRING", "description": "the flat as it was offered (complex, block, number, rooms, area, price) and what the client said about it", "choices": [], "remember": true}]}},
    {"id": "fn_int_createLead", "type": "INTEGRATION", "name": "createLead", "speakDuringExecution": false, "integrationTool": "createLead"},
    {"id": "fn_int_addLeadNote", "type": "INTEGRATION", "name": "addLeadNote", "speakDuringExecution": false, "integrationTool": "addLeadNote"},
    {"id": "fn_int_moveLeadStage", "type": "INTEGRATION", "name": "moveLeadStage", "speakDuringExecution": false, "integrationTool": "moveLeadStage"},
    {"id": "fn_int_listLeadStages", "type": "INTEGRATION", "name": "listLeadStages", "speakDuringExecution": false, "integrationTool": "listLeadStages"},
    {"id": "fn_int_createFollowUpTask", "type": "INTEGRATION", "name": "createFollowUpTask", "speakDuringExecution": false, "integrationTool": "createFollowUpTask"}
  ]'::jsonb
 WHERE company_id = 1 AND name IN ('Lid kvalifikatsiya (cascade)', 'Lid kvalifikatsiya (realtime)') AND functions IS NULL;

UPDATE ai_agent SET functions = '[
    {"id": "fn_end_call", "type": "END_CALL", "name": "endCall", "speakDuringExecution": false},
    {"id": "fn_transfer_call", "type": "TRANSFER_CALL", "name": "requestHumanTransfer", "speakDuringExecution": false},
    {"id": "fn_int_getClientProfile", "type": "INTEGRATION", "name": "getClientProfile", "speakDuringExecution": false, "integrationTool": "getClientProfile"},
    {"id": "fn_int_getDebtSummary", "type": "INTEGRATION", "name": "getDebtSummary", "speakDuringExecution": false, "integrationTool": "getDebtSummary"},
    {"id": "fn_int_getContractBalance", "type": "INTEGRATION", "name": "getContractBalance", "speakDuringExecution": false, "integrationTool": "getContractBalance"},
    {"id": "fn_int_getPaymentSchedule", "type": "INTEGRATION", "name": "getPaymentSchedule", "speakDuringExecution": false, "integrationTool": "getPaymentSchedule"},
    {"id": "fn_int_addLeadNote", "type": "INTEGRATION", "name": "addLeadNote", "speakDuringExecution": false, "integrationTool": "addLeadNote"},
    {"id": "fn_int_createFollowUpTask", "type": "INTEGRATION", "name": "createFollowUpTask", "speakDuringExecution": false, "integrationTool": "createFollowUpTask"},
    {"id": "fn_extract_requestManagerCallback", "type": "EXTRACT_VARIABLE", "name": "requestManagerCallback", "description": "Asks the client''s own sales or contract manager to call them back. Call it when the client wants a person to call, has a question you cannot answer, or wants to discuss terms or a discount. Then tell the client their manager will call them back.", "speakDuringExecution": false, "extract": {"variables": [{"name": "managerCallbackRequested", "type": "BOOLEAN", "description": "always true: the client asked for a manager to call back", "choices": [], "remember": false}, {"name": "managerCallbackReason", "type": "STRING", "description": "what the client wants to discuss", "choices": [], "remember": false}, {"name": "managerCallbackTime", "type": "STRING", "description": "when the client prefers to be called, if they said", "choices": [], "remember": false}]}}
  ]'::jsonb
 WHERE company_id = 1 AND name IN ('Qarzdorlik (cascade)', 'Qarzdorlik (realtime)') AND functions IS NULL;

-- The room behind each agent, and the typing that covers a tool call in flight — a lead
-- call waits on the CRM, a debt call on the debt lookups and the CRM task.
INSERT INTO ai_agent_ambience (ai_agent_id, ambient_sound, ambient_sound_volume, thinking_sound, thinking_sound_volume)
SELECT a.id, 'OFFICE', 0.6, 'KEYBOARD_TYPING', 0.8
FROM ai_agent a
WHERE a.company_id = 1 AND a.name IN ('Lid kvalifikatsiya (cascade)', 'Lid kvalifikatsiya (realtime)',
                                      'Qarzdorlik (cascade)', 'Qarzdorlik (realtime)')
ON CONFLICT (ai_agent_id) DO NOTHING;

-- Which voice each cascade agent speaks a given call language in. The realtime agents are
-- left out on purpose: their voice has to belong to gemini-live, and one Gemini voice
-- already covers both languages.
-- The cascade lead agent is Uzbek only: with ru-RU allowed too, SpeechKit picked Russian for
-- short Uzbek answers ("Saroy TJM" came back as "стараюсь идти").
INSERT INTO ai_agent_voice (ai_agent_id, language, voice_id)
SELECT a.id, v.language, v.voice_id
FROM ai_agent a
JOIN (VALUES ('Lid kvalifikatsiya (cascade)', 'uz-UZ', 'zamira'),
             ('Qarzdorlik (cascade)',         'uz-UZ', 'nigora'), ('Qarzdorlik (cascade)',         'ru-RU', 'alena'))
     AS v(agent_name, language, voice_id) ON v.agent_name = a.name
WHERE a.company_id = 1
ON CONFLICT (ai_agent_id, language) DO NOTHING;

-- Agent Handbook: the best-practice rules each agent switches on (HandbookRule). The debt
-- agents speak numbers aloud and give no advice; the lead agents read back what the client
-- says and match near-spellings of complex names.
INSERT INTO ai_agent_handbook_rule (ai_agent_id, rule)
SELECT a.id, r.rule
FROM ai_agent a
JOIN (VALUES ('lead', 'HIGH_EMPATHY'), ('lead', 'CONFIRM_DETAILS'), ('lead', 'SMART_MATCHING'),
             ('lead', 'KNOWLEDGE_ONLY'), ('lead', 'PROTECT_INSTRUCTIONS'), ('lead', 'STAY_ON_TOPIC'),
             ('debt', 'HIGH_EMPATHY'), ('debt', 'SPOKEN_NUMBERS'), ('debt', 'PROTECT_INSTRUCTIONS'),
             ('debt', 'STAY_ON_TOPIC'), ('debt', 'NO_PROFESSIONAL_ADVICE'))
     AS r(kind, rule)
  ON r.kind = CASE WHEN a.name LIKE 'Lid kvalifikatsiya%' THEN 'lead' ELSE 'debt' END
WHERE a.company_id = 1 AND a.name IN ('Lid kvalifikatsiya (cascade)', 'Lid kvalifikatsiya (realtime)',
                                      'Qarzdorlik (cascade)', 'Qarzdorlik (realtime)')
ON CONFLICT DO NOTHING;

-- Words the synthesiser would otherwise read letter by letter or in English.
INSERT INTO ai_agent_pronunciation_rule (ai_agent_id, word, replacement, case_sensitive, language)
SELECT a.id, p.word, p.replacement, false, p.language
FROM ai_agent a
CROSS JOIN (VALUES ('UZS', 'so''m', 'uz-UZ'), ('UZS', 'сум', 'ru-RU'), ('USD', 'dollar', 'uz-UZ'),
                   ('Payme', 'Peymi', '*'), ('Click', 'Klik', '*'), ('m²', 'kvadrat metr', 'uz-UZ'),
                   ('kv.m', 'kvadrat metr', 'uz-UZ'))
     AS p(word, replacement, language)
WHERE a.company_id = 1 AND a.name IN ('Lid kvalifikatsiya (cascade)', 'Lid kvalifikatsiya (realtime)',
                                      'Qarzdorlik (cascade)', 'Qarzdorlik (realtime)')
  AND NOT EXISTS (SELECT 1 FROM ai_agent_pronunciation_rule x
                  WHERE x.ai_agent_id = a.id AND x.word = p.word AND x.language = p.language);

-- ---------------------------------------------------------------------------
-- Inbound routing. Without a row here an inbound call is hung up on: AriService looks
-- the dialled number up and, on a miss, has no agent to answer with.
--
-- 600 is the softphone test extension from the dialplan ([from-internal] in
-- asterisk/etc/asterisk/extensions.conf) — a registered softphone dialling it reaches the
-- full pipeline with no trunk involved, which is the only way to try an inbound call on a
-- developer machine. A real DID is added through POST /api/inbound-routes; this row is
-- what makes the installation answer at all out of the box. It answers with the realtime
-- lead agent, so a softphone call is the quickest way to hear that pipeline.
--
-- business_hours_* stay NULL, which means "always": a seeded route that stopped answering
-- at 18:00 would look like a broken install to whoever tries it in the evening. Fill them
-- in once the hours are real — fallback_message below is what a caller then hears.
-- ---------------------------------------------------------------------------
INSERT INTO inbound_route (company_id, did_number, ai_agent_id, after_hours_action, fallback_message, enabled)
SELECT 1, '600',
       (SELECT id FROM ai_agent WHERE company_id = 1 AND name = 'Lid kvalifikatsiya (realtime)' LIMIT 1),
       'PLAY_MESSAGE_AND_HANGUP',
       'Hozir ish vaqtimiz tugagan. Iltimos, dushanbadan jumagacha soat 9:00 dan 18:00 gacha qo''ng''iroq qiling.',
       true
WHERE NOT EXISTS (SELECT 1 FROM inbound_route WHERE company_id = 1 AND did_number = '600');

-- ---------------------------------------------------------------------------
-- The Uysot OAuth connection, CONNECTED out of the box (dev/test tenant): the tokens are
-- the ones the bootstrap company got on Uysot's dev host (UYSOT_OAUTH_* / CRM_BASE_URL in
-- .env), so a fresh database places CRM-backed calls without anyone clicking through the
-- consent screen again.
--
-- The *_enc values are AES-GCM under the published test ENCRYPTION_SECRET_KEY (see TEST
-- DATA below) — with any other key they read back as nothing and the company is simply not
-- connected. The access token is long expired by the time this runs; that is fine:
-- CrmIntegrationService refreshes it with the refresh token on first use and stores the new
-- pair. If Uysot has rotated the refresh token since (another database refreshed with it),
-- the refresh is refused — reconnect through GET /api/settings/integrations/uysot/authorize-url.
--
-- Grants are what that consent actually granted — LEAD:SAVE (createLead, moveLeadStage),
-- LEAD_NOTE:SAVE (addLeadNote), LEAD_TASK:SAVE (createFollowUpTask) are what the lead
-- qualification agent writes with; CONTRACT:READ, CONTRACT_PAYMENT:READ what the debt agents
-- and the daily campaign read. Nothing grants a DELETE.
--
-- Guarded rather than refreshed: a company that reconnected or narrowed its grants keeps
-- that across a reseed.
-- ---------------------------------------------------------------------------
INSERT INTO crm_integration (company_id, provider, app_name, grants_json, access_token_enc, refresh_token_enc,
                             token_expires_at, status, connected_at)
VALUES (1, 'UYSOT', 'Uysot Voice Agent',
        '[{"permission":"LEAD","scope":"READ"},'
        || '{"permission":"LEAD","scope":"SAVE"},'
        || '{"permission":"LEAD_NOTE","scope":"READ"},'
        || '{"permission":"LEAD_NOTE","scope":"SAVE"},'
        || '{"permission":"LEAD_TASK","scope":"READ"},'
        || '{"permission":"LEAD_TASK","scope":"SAVE"},'
        || '{"permission":"CONTRACT","scope":"READ"},'
        || '{"permission":"CONTRACT_PAYMENT","scope":"READ"},'
        || '{"permission":"CLIENT","scope":"READ"},'
        || '{"permission":"CALL","scope":"SAVE"}]',
        'bSS4v5icHNg71pI1SP/qeTlbNnvk5nwxisPhtf9jRvYn0qMpClZPWf+Q7Gx7ZQ2mg5N22p+GY4IQoiXrf+1InKfXaCFyOGkKC4c7mg9IbtV7BNFSuw==',
        'ThrcFHcICMc6p39PtGWx5voyTKSOn/TH/7zKAZctTPSg7H2oGaiEsItSZP/3W62MWZ92gfAGGxc/AILwTy5yG7ZDcdZY8fhO7LiuV81aDtECoo75',
        '2026-10-31 04:58:38.331461+00',
        'CONNECTED', '2026-10-31 03:58:38.334397+00')
ON CONFLICT (company_id) DO NOTHING;

-- Which catalog tools the bootstrap company offers its agents: the Uysot tools are on by default
-- (integration_tool.default_enabled), so it needs no row of its own.

-- ---------------------------------------------------------------------------
-- Campaigns. Four, one per agent:
--
--   Yangi lidlar — cascade            AUTO_ENROLL · a lead created in Uysot → qualification call
--   Yangi lidlar — realtime           AUTO_ENROLL · the same on the realtime agent
--   Qarzdorlik — kunlik (cascade)     STANDARD    · today's overdue list from Uysot, daily 09:00
--   Qarzdorlik — kunlik (realtime)    STANDARD    · the same on the realtime agent
--
-- Run ONE campaign of each pair at a time. Both debt campaigns read the same Uysot list, so
-- two active at once call every debtor twice. And Uysot's LEAD_CREATED webhook
-- (UysotWebhookService → InstantCallTriggerService) queues the lead into the company's
-- ACTIVE AI campaign with the lowest id — which is why the lead campaigns are inserted
-- first: with one lead campaign active, a new lead lands in it, never in a debt campaign.
--
-- The lead campaigns are AUTO_ENROLL because an AUTO_ENROLL campaign is never closed for
-- running out of targets (CampaignJpaRepository#completeIfFinished): it keeps waiting for the
-- next lead. A contact tagged "yangi-lid" (an import, the API) joins them the same way.
--
-- All four are DRAFT and none dials on its own: an operator launches it (and confirms the
-- contacts' consent for an AUTO_ENROLL one — POST /api/campaigns/{id}/consent). A daily one
-- then repeats by itself: RecurringCampaignScheduler starts the next run of a recurring
-- campaign once the previous run is COMPLETED, at the first sweep inside its window.
--
-- Looked up by name: campaign.name has no unique constraint, so a rerun must not add a
-- second copy.
-- ---------------------------------------------------------------------------

-- The lead pair: one CONVERSATION step, three tries (now, 2 hours later, the next day). A
-- qualified lead (recordLeadInterest → GOAL_REACHED) ends it successfully. The agent ids
-- are spliced in by name; the sequence JSON is otherwise exactly what PUT /api/campaigns/{id}
-- stores.
INSERT INTO campaign (company_id, name, type, kind, status, ai_agent_id, priority, max_attempts,
                      retry_interval_minutes, double_dial, auto_schedule_callbacks, auto_enroll, sequence)
SELECT 1, v.name, 'LEAD_QUALIFICATION', 'AUTO_ENROLL', 'DRAFT', a.id, 1, 3, 120, true, true,
       '{"matchAll": true, "rules": [{"field": "TAG", "operator": "EQUALS", "value": "yangi-lid"}], "excludedContactIds": []}'::jsonb,
       replace($seq$
{
  "startStepId": "qualify",
  "steps": [
    {"id": "qualify", "type": "CONVERSATION", "name": "Kvalifikatsiya qo'ng'irog'i",
     "conversation": {"aiAgentId": __AGENT__, "attempts": [
        {"delay": 0, "unit": "MINUTES"}, {"delay": 2, "unit": "HOURS"}, {"delay": 1, "unit": "DAYS"}]},
     "outcomes": {
        "outcomes": [
          {"id": "qualified", "name": "Kvalifikatsiyadan o'tdi", "evaluation": "METRICS", "match": "ANY",
           "conditions": [{"variable": "disposition", "operator": "EQUALS", "value": "GOAL_REACHED"}],
           "saveToContact": {"doNotCall": false, "tags": ["kvalifikatsiya"]},
           "next": {"exit": "END_SUCCESSFUL", "stepId": null, "delayMinutes": 0}},
          {"id": "not_interested", "name": "Qiziqmadi", "evaluation": "METRICS", "match": "ANY",
           "conditions": [{"variable": "disposition", "operator": "EQUALS", "value": "GOAL_NOT_REACHED"}],
           "saveToContact": {"doNotCall": false, "tags": ["qiziqmadi"]},
           "next": {"exit": "END", "stepId": null, "delayMinutes": 0}}
        ],
        "other": {"exit": "END", "stepId": null, "delayMinutes": 0},
        "noResponse": {"exit": "END", "stepId": null, "delayMinutes": 0}},
     "position": {"x": 0, "y": 0}}
  ],
  "globalOutcomes": [
    {"id": "do_not_call", "name": "Qo'ng'iroq qilinmasin", "evaluation": "PROMPT",
     "prompt": "The lead asked not to be called or contacted again.",
     "saveToContact": {"doNotCall": true, "tags": []},
     "next": {"exit": "END", "stepId": null, "delayMinutes": 0}}
  ]
}
$seq$, '__AGENT__', a.id::text)::jsonb
FROM (VALUES ('Yangi lidlar — cascade', 'Lid kvalifikatsiya (cascade)'),
             ('Yangi lidlar — realtime', 'Lid kvalifikatsiya (realtime)')) AS v(name, agent_name)
JOIN ai_agent a ON a.company_id = 1 AND a.name = v.agent_name
WHERE NOT EXISTS (SELECT 1 FROM campaign c WHERE c.company_id = 1 AND c.name = v.name)
ORDER BY v.name;

-- The daily collections pair: every morning at 09:00, call whoever Uysot says is behind.
--
-- dial_window_end is 18:00 rather than the company's own 23:00 because collections calls at
-- night are how a company gets complained about, not how it gets paid.
--
-- auto_reset_targets stays false on purpose. Resetting would re-queue yesterday's people;
-- the target source below replaces the list outright, which is what "today's debtors"
-- means. daily_call_cap is a second belt on top of voice-agent.crm.debtor-import.max-debtors
-- — the import decides how many numbers are fetched, this decides how many are dialled.
-- double_dial: an unanswered call is redialled once at once (people reach for the phone on
-- the second ring); auto_schedule_callbacks: "call me at 16:00" is honoured.
INSERT INTO campaign (company_id, name, type, status, ai_agent_id, priority,
                      max_attempts, retry_interval_minutes, retry_policy, double_dial, auto_schedule_callbacks)
SELECT 1, v.name, 'DEBT_COLLECTION', 'DRAFT', a.id, 2,
       3, 120,
       -- What the scenario's outcome tools leave behind, answered per disposition: nobody picked up
       -- or hung up — try again later the same day; "I already paid" (recordPaymentClaim) — three
       -- days off while the company checks it. A promise (PROMISE_TO_PAY) and a requested callback
       -- (scheduleCallback) need no rule here: the first closes the target, the second is re-dialled
       -- at the time the client named.
       '{"NO_ANSWER": {"maxAttempts": 3, "delayMinutes": 180}, "VOICEMAIL": {"maxAttempts": 1, "delayMinutes": 120}, "HUNG_UP": {"maxAttempts": 2, "delayMinutes": 60}, "PAYMENT_CLAIMED": {"maxAttempts": 2, "delayMinutes": 4320}}'::jsonb,
       true, true
FROM (VALUES ('Qarzdorlik — kunlik (cascade)', 'Qarzdorlik (cascade)'),
             ('Qarzdorlik — kunlik (realtime)', 'Qarzdorlik (realtime)')) AS v(name, agent_name)
JOIN ai_agent a ON a.company_id = 1 AND a.name = v.agent_name
WHERE NOT EXISTS (SELECT 1 FROM campaign c WHERE c.company_id = 1 AND c.name = v.name)
ORDER BY v.name;

-- Where the debt campaigns' list comes from: Uysot, read through the three-call chain
-- UysotDebtorSourceAdapter documents. url and every other request column stay NULL — a
-- UYSOT_DEBTORS source has no request of its own to describe, and reads voice-agent.crm.*
-- instead, so switching between the prod and dev hosts is one environment variable.
--
-- replace_targets: the sync answers with the whole of today's list, so yesterday's is
-- cleared — but only once the CRM has answered, so a failed fetch leaves the campaign with
-- the list it already had rather than with nothing.
INSERT INTO campaign_target_source (campaign_id, provider, replace_targets, sync_on_recurrence, enabled)
SELECT id, 'UYSOT_DEBTORS', true, true, true
FROM campaign WHERE company_id = 1 AND name IN ('Qarzdorlik — kunlik (cascade)', 'Qarzdorlik — kunlik (realtime)')
ON CONFLICT (campaign_id) DO NOTHING;

-- Dialer settings, calling window and days. Leads are called until 20:00 and on Sunday too —
-- a prospect answers in the evening; money calls stop at 18:00 and skip Sunday.
INSERT INTO campaign_dialer_config (campaign_id, max_concurrent_calls, daily_call_cap, max_cps, daily_budget_uzs)
SELECT c.id, v.concurrent, v.cap, 2, v.budget
FROM campaign c
JOIN (VALUES ('Yangi lidlar — cascade', 5, 200, NULL::bigint),
             ('Yangi lidlar — realtime', 5, 200, NULL::bigint),
             ('Qarzdorlik — kunlik (cascade)', 10, 200, 500000::bigint),
             ('Qarzdorlik — kunlik (realtime)', 10, 200, 500000::bigint)) AS v(name, concurrent, cap, budget)
  ON v.name = c.name
WHERE c.company_id = 1
ON CONFLICT (campaign_id) DO NOTHING;

INSERT INTO campaign_schedule (campaign_id, dial_window_start, dial_window_end, recurrence_type, auto_reset_targets)
SELECT c.id, v.starts::time, v.ends::time, v.recurrence, false
FROM campaign c
JOIN (VALUES ('Yangi lidlar — cascade', '09:00', '20:00', 'ONCE'),
             ('Yangi lidlar — realtime', '09:00', '20:00', 'ONCE'),
             ('Qarzdorlik — kunlik (cascade)', '09:00', '18:00', 'DAILY'),
             ('Qarzdorlik — kunlik (realtime)', '09:00', '18:00', 'DAILY')) AS v(name, starts, ends, recurrence)
  ON v.name = c.name
WHERE c.company_id = 1
ON CONFLICT (campaign_id) DO NOTHING;

INSERT INTO campaign_dial_day (campaign_id, day)
SELECT c.id, d.day
FROM campaign c
CROSS JOIN unnest(ARRAY['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']) AS d(day)
WHERE c.company_id = 1
  AND c.name IN ('Yangi lidlar — cascade', 'Yangi lidlar — realtime',
                 'Qarzdorlik — kunlik (cascade)', 'Qarzdorlik — kunlik (realtime)')
  AND (d.day <> 'SUNDAY' OR c.name LIKE 'Yangi lidlar%')
ON CONFLICT (campaign_id, day) DO NOTHING;

-- Knowledge base: the answers the agent is allowed to give when a caller asks something
-- the scenario does not script (KnowledgeBaseService). Guardrails forbid it from inventing
-- an amount, a deadline or a concession, so every question of that shape needs an approved
-- answer here or the agent has nothing to say but "I don't know".
--
-- DO NOTHING, not DO UPDATE: unlike the built-in scenarios above, these rows are editable
-- through the API, and rerunning this file must not overwrite what an operator rewrote.
INSERT INTO knowledge_base_item (company_id, item_key, topic, title, keywords)
VALUES
(1, 'payment_methods', 'payment', 'To''lov usullari', 'click,payme,to''lash,qayerga,qanday to''layman,оплатить,как оплатить,how to pay'),
(1, 'court_mib', 'legal', 'MIB va sud choralari', 'mib,sud,qonun,sudga,бпи,суд,court'),
(1, 'restructuring', 'terms', 'Qayta ko''rib chiqish va restrukturizatsiya', 'bo''lib to''lash,imtiyoz,sharoit,qiyin,рассрочка,реструктуризация'),
(1, 'branch_locations', 'locations', 'Filial manzillari va ish tartibi', 'filial,manzil,ofis,филиал,адрес,branch,office'),
(1, 'already_paid', 'payment', 'To''lov qilinganini bildirish', 'to''ladim,to''lab bo''ldim,chek,kvitansiya,уже оплатил,заплатил,чек,квитанция,already paid,receipt'),
(1, 'amount_dispute', 'terms', 'Summa bo''yicha kelishmovchilik', 'summa,noto''g''ri,hisob,kelishmayman,сумма,неверно,расчёт,не согласен,amount,wrong'),
(1, 'operator_transfer', 'general', 'Operator bilan bog''lanish', 'operator,odam,jonli,ulang,оператор,человек,живой,соедините,operator,human'),
(1, 'call_recording', 'legal', 'Suhbat yozib olinishi va shaxsiy ma''lumotlar', 'yozib,yozuv,nega yozyapsiz,shaxsiy ma''lumot,запись,записываете,персональные данные,recording,privacy')
ON CONFLICT (company_id, item_key) DO NOTHING;

INSERT INTO knowledge_item_translation (item_id, language, answer)
SELECT k.id, t.lang, t.ans
FROM (
    VALUES
    ('payment_methods', 'uz', 'To''lovni Click, Payme, Uzum Bank ilovalari orqali yoki bank kassalarida shartnoma raqamingizni ko''rsatib amalga oshirishingiz mumkin.'),
    ('payment_methods', 'ru', 'Оплату можно произвести через приложения Click, Payme, Uzum Bank или в кассах банков, указав номер договора.'),
    ('payment_methods', 'en', 'You can make payments via Click, Payme, Uzum Bank mobile apps or at bank branches using your contract number.'),

    ('court_mib', 'uz', 'To''lov kechiktirilsa, qonunchilikka asosan ish Majburiy ijro byurosiga (MIB) yoki sudga oshirilishi va hisob raqamlarga taqiq qo''yilishi mumkin.'),
    ('court_mib', 'ru', 'В случае задержки оплаты дело в соответствии с законом может быть передано в БПИ или суд с наложением ареста на счета.'),
    ('court_mib', 'en', 'In case of prolonged non-payment, the case may be escalated to the enforcement bureau or court with account freezes.'),

    ('restructuring', 'uz', 'Agar moliyaviy qiyinchilik bo''lsa, bank filialiga ariza bilan murojaat qilib, to''lov muddatini uzaytirish yoki qayta ko''rib chiqishni so''rashingiz mumkin.'),
    ('restructuring', 'ru', 'При финансовых трудностях вы можете обратиться в филиал банка с заявлением о реструктуризации или продлении срока долга.'),
    ('restructuring', 'en', 'If experiencing financial distress, you can visit a branch to request loan restructuring or installment adjustments.'),

    ('branch_locations', 'uz', 'Barcha filiallar dushanbadan jumagacha soat 9:00 dan 18:00 gacha ishlaydi. Eng yaqin filialni rasmiy veb-saytdan topishingiz mumkin.'),
    ('branch_locations', 'ru', 'Все филиалы работают с понедельника по пятницу с 9:00 до 18:00. Ближайший филиал можно найти на официальном сайте.'),
    ('branch_locations', 'en', 'All branches operate Monday through Friday from 9:00 to 18:00. The nearest branch can be found on our official website.'),

    ('already_paid', 'uz', 'To''lovni allaqachon amalga oshirgan bo''lsangiz, tushunmovchilik uchun uzr. To''lov cheki bankka kelib tushishi bir necha ish kunini olishi mumkin. Chek yoki to''lov tasdig''ini filialga yoki qo''llab-quvvatlash xizmatiga yuborsangiz, mutaxassis tekshirib chiqadi.'),
    ('already_paid', 'ru', 'Если вы уже произвели оплату, приносим извинения за беспокойство. Зачисление платежа может занять несколько рабочих дней. Отправьте чек или подтверждение оплаты в филиал или в службу поддержки — специалист проверит.'),
    ('already_paid', 'en', 'If you have already paid, we apologise for the call. A payment can take a few business days to post. Send the receipt to a branch or to support and a specialist will verify it.'),

    ('amount_dispute', 'uz', 'Summa bo''yicha savolingiz bo''lsa, men uni o''zgartira olmayman. Shartnoma bo''yicha batafsil hisob-kitobni filialda yoki qo''llab-quvvatlash xizmatidan olishingiz mumkin — ular har bir to''lovni ko''rsatib beradi.'),
    ('amount_dispute', 'ru', 'Если у вас есть вопросы по сумме, я не могу её изменить. Подробный расчёт по договору можно получить в филиале или в службе поддержки — там покажут каждый платёж.'),
    ('amount_dispute', 'en', 'If you disagree with the amount, I am not able to change it. A detailed breakdown of the contract is available at a branch or from support, where every payment is itemised.'),

    ('operator_transfer', 'uz', 'Albatta, sizni jonli operatorga ulashim mumkin. Agar hozir band bo''lsalar, qo''llab-quvvatlash xizmatiga ish vaqtida — dushanbadan jumagacha 9:00 dan 18:00 gacha qo''ng''iroq qilishingiz mumkin.'),
    ('operator_transfer', 'ru', 'Конечно, я могу соединить вас с живым оператором. Если сейчас все заняты, вы можете позвонить в службу поддержки в рабочее время — с понедельника по пятницу с 9:00 до 18:00.'),
    ('operator_transfer', 'en', 'Of course, I can transfer you to a live operator. If none is free, you can call support during business hours, Monday to Friday from 9:00 to 18:00.'),

    ('call_recording', 'uz', 'Suhbat xizmat sifatini nazorat qilish uchun yozib olinadi va shaxsiy ma''lumotlar to''g''risidagi qonunchilikka muvofiq saqlanadi. Yozuvni o''chirish yoki nusxasini olish uchun qo''llab-quvvatlash xizmatiga yozma murojaat qilishingiz mumkin.'),
    ('call_recording', 'ru', 'Разговор записывается для контроля качества обслуживания и хранится в соответствии с законодательством о персональных данных. Для удаления записи или получения копии направьте письменное обращение в службу поддержки.'),
    ('call_recording', 'en', 'The call is recorded for quality assurance and stored in line with personal data legislation. To request deletion or a copy, send a written request to support.')
) AS t(key, lang, ans)
JOIN knowledge_base_item k ON k.company_id = 1 AND k.item_key = t.key
ON CONFLICT (item_id, language) DO NOTHING;

-- ===========================================================================
-- TEST DATA
--
-- Everything below exists to be tested against, not to be run in production: it seeds
-- accounts with a published password and encrypted values under a published key.
--
--   Login        superadmin / admin / developer / supervisor / operator1..operator4 / viewer
--                (operator4 is BLOCKED, yangi.operator is INVITED), all with Test12345!.
--                Demo tenant (company 2): demo.admin / Test12345!
--                Partner tenant: partner.admin / Test12345!; its trial client: sammikro.admin / Test12345!
--                The platform admin (platform) is not a test account — see the top of this file.
--   Encryption   every *_enc / secret / sealed value below is AES-GCM under
--                ENCRYPTION_SECRET_KEY=GC7Yp1NZ///WEtW4JGOpgGxhoCWHLNKvn7hw2WtXt1Q=
--                Put that in .env, or the secrets read back masked-empty and the operator
--                SIP endpoints are left out of the Asterisk config.
--   API key      rc_live_d70d8b812ab4_fIo_iAZAFY9emv711uKtXX2oC0CvrH28BasLpXAx960
--
-- Each block below is guarded by a row only that block writes, so it is inserted once, on
-- an empty database, and a rerun of this file never duplicates or overwrites it. Editing a
-- block therefore changes nothing in a database that already has it — drop the database to
-- see an edit.
--
-- Company 1 gets no test agent, campaign or call history here: its four agents and four
-- campaigns are the ones at the top of this file. The contacts below are made up and may
-- still belong to somebody, so none of them is a campaign target. To hear an agent without
-- a trunk, dial 600 from the local softphone (the realtime lead agent answers).
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- Users, operators, schedules, trunks, voicemail boxes, secrets
-- ---------------------------------------------------------------------------
DO $seed$
DECLARE
    v_hash CONSTANT TEXT := '$2a$12$OXJCG27t4EEQWD9lMVvccOXLLZDeh9IYyfsVxg.UTs5nq4N.ii0NO'; -- Test12345!
    v_sup  BIGINT;
    v_op1  BIGINT;
    v_op2  BIGINT;
    v_op3  BIGINT;
    v_op4  BIGINT;
    v_sched BIGINT;
BEGIN
    IF EXISTS (SELECT 1 FROM app_user WHERE email = 'superadmin@test.uz') THEN
        RETURN;
    END IF;

    INSERT INTO app_user (company_id, name, email, username, password_hash, role_id, status, phone, position,
                          sip_extension, last_login_at)
    SELECT 1, u.name, u.email, u.username, v_hash,
           (SELECT id FROM app_role WHERE company_id = 1 AND code = u.role),
           u.status, u.phone, u.position, u.ext, u.last_login
    FROM (VALUES
        ('Bosh administrator', 'superadmin@test.uz', 'superadmin', 'ADMIN',      'ACTIVE', '+998901110000', 'Bosh administrator', NULL, now() - interval '1 hour'),
        ('Jasur Tursunov',     'admin@test.uz',      'admin',      'ADMIN',      'ACTIVE', '+998901110001', 'Kompaniya administratori', NULL, now() - interval '3 hours'),
        ('Dasturchi (test)',   'developer@test.uz',  'developer',  'DEVELOPER',  'ACTIVE', NULL, 'Integratsiya muhandisi', NULL, NULL),
        ('Dilshod Karimov',    'supervisor@test.uz', 'supervisor', 'SUPERVISOR', 'ACTIVE', '+998901110003', 'Smena rahbari', '103', now() - interval '5 hours'),
        ('Malika Yusupova',    'operator1@test.uz',  'operator1',  'OPERATOR',   'ACTIVE', '+998901110011', 'Operator', '101', now() - interval '5 hours'),
        ('Aziz Rahimov',       'operator2@test.uz',  'operator2',  'OPERATOR',   'ACTIVE', '+998901110022', 'Operator', '102', now() - interval '6 hours'),
        ('Nodira Tosheva',     'operator3@test.uz',  'operator3',  'OPERATOR',   'ACTIVE', '+998901110033', 'Rus tilli operator', '104', now() - interval '1 day'),
        ('Sardor Aliyev',      'operator4@test.uz',  'operator4',  'OPERATOR',   'BLOCKED', '+998901110044', 'Operator (bloklangan)', '105', now() - interval '20 days'),
        ('Kuzatuvchi (test)',  'viewer@test.uz',     'viewer',     'VIEWER',     'ACTIVE', NULL, 'Auditor', NULL, NULL)
    ) AS u(name, email, username, role, status, phone, position, ext, last_login);

    -- Invited and never activated: no password, so it cannot sign in at all.
    INSERT INTO app_user (company_id, name, email, username, password_hash, role_id, status, invite_expires_at)
    VALUES (1, 'Yangi operator', 'yangi.operator@test.uz', 'yangi.operator', NULL,
            (SELECT id FROM app_role WHERE company_id = 1 AND code = 'OPERATOR'), 'INVITED', now() + interval '7 days');

    SELECT id INTO v_sup FROM app_user WHERE username = 'supervisor';
    SELECT id INTO v_op1 FROM app_user WHERE username = 'operator1';
    SELECT id INTO v_op2 FROM app_user WHERE username = 'operator2';
    SELECT id INTO v_op3 FROM app_user WHERE username = 'operator3';
    SELECT id INTO v_op4 FROM app_user WHERE username = 'operator4';

    -- SIP passwords in the clear, for signing a softphone in by hand:
    --   supervisor Op3SipTest2026Kx · operator1 Op4SipTest2026Mq · operator2 Op5SipTest2026Rw
    --   operator3  Op6SipTest2026Tz · operator4 Op7SipTest2026Vb (blocked, left out of Asterisk)
    INSERT INTO operator_sip_account (user_id, company_id, endpoint_name, password_enc, webrtc, forward_number) VALUES
        (v_sup, 1, 'op-' || v_sup, 'VbUGk52T6nVmHaNzI64neujIVEW57l8MyLASStDCf50lkjjaSzzcvIHc0E4=', true, NULL),
        (v_op1, 1, 'op-' || v_op1, '+awJU7SpMVbPIepTk06RCA5QIygOJ6XelElLlEh54kplQ8cRyP7Jq2I/LLM=', true, NULL),
        (v_op2, 1, 'op-' || v_op2, 'tTTrFR0753M2XxwpoQP5lPRvYdhdVDcldiHj44UEoxM4At0oxle0e4rHKNA=', true, '+998901110022'),
        (v_op3, 1, 'op-' || v_op3, '6wPy/Ew3M9LdrBG1pT/HmS+nMNCq25YVm58uBrzGk4aWZjA0wgo/uNJXJ5s=', false, '+998901110033'),
        (v_op4, 1, 'op-' || v_op4, 'GP8Zz7w+vGuntcgmuV4CurM3q3BAhZRVlz2yC1zU+4M+YOP5ueojDPzXPF4=', true, NULL);

    -- Today's presence history. Every row is closed or OFFLINE: a restart restores an open
    -- AVAILABLE/BREAK row, and nobody is actually signed in to take a call.
    INSERT INTO operator_status_log (company_id, user_id, status, reason, started_at, ended_at) VALUES
        (1, v_op1, 'AVAILABLE', NULL,      now() - interval '5 hours',             now() - interval '3 hours'),
        (1, v_op1, 'BREAK',     'Tushlik', now() - interval '3 hours',             now() - interval '2 hours 15 minutes'),
        (1, v_op1, 'AVAILABLE', NULL,      now() - interval '2 hours 15 minutes',  now() - interval '30 minutes'),
        (1, v_op1, 'OFFLINE',   NULL,      now() - interval '30 minutes',          NULL),
        (1, v_op2, 'AVAILABLE', NULL,      now() - interval '6 hours',             now() - interval '4 hours'),
        (1, v_op2, 'ON_CALL',   NULL,      now() - interval '4 hours',             now() - interval '3 hours 52 minutes'),
        (1, v_op2, 'WRAP_UP',   NULL,      now() - interval '3 hours 52 minutes',  now() - interval '3 hours 51 minutes'),
        (1, v_op2, 'AVAILABLE', NULL,      now() - interval '3 hours 51 minutes',  now() - interval '1 hour'),
        (1, v_op2, 'OFFLINE',   NULL,      now() - interval '1 hour',              NULL),
        (1, v_op3, 'BREAK',     'Majlis',  now() - interval '2 hours',             now() - interval '1 hour'),
        (1, v_op3, 'OFFLINE',   NULL,      now() - interval '1 hour',              NULL),
        (1, v_sup, 'AVAILABLE', NULL,      now() - interval '5 hours',             now() - interval '10 minutes'),
        (1, v_sup, 'OFFLINE',   NULL,      now() - interval '10 minutes',          NULL);

    INSERT INTO user_schedule_slot (company_id, user_id, day_of_week, start_time, end_time)
    SELECT 1, u.id, d.day, '09:00', '18:00'
    FROM (VALUES (v_op1), (v_op2), (v_op3)) AS u(id)
    CROSS JOIN unnest(ARRAY['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY']) AS d(day);
    INSERT INTO user_schedule_slot (company_id, user_id, day_of_week, start_time, end_time)
    SELECT 1, v_sup, d.day, '09:00', '18:00'
    FROM unnest(ARRAY['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY']) AS d(day);

    -- The trunk pjsip.conf already defines ([trunk-endpoint]): host NULL keeps it out of the
    -- generated pjsip_trunks.conf. The second is a managed one, disabled, so PjsipConfigWriter
    -- never writes a trunk to a host that does not exist; its password is TrunkTest2026Pass.
    INSERT INTO sip_trunk (company_id, name, pjsip_endpoint, caller_id, host, port, sip_username, sip_password_enc,
                           transport, codecs, is_default, enabled) VALUES
        (1, 'Asosiy trunk', 'trunk-endpoint', NULL, NULL, 5060, NULL, NULL, 'UDP', 'ulaw,alaw', true, true),
        (1, 'Zaxira trunk (test, o''chiq)', 'backup-trunk', '712000999', 'sip.example.uz', 5060, 'backup_user',
         'oMuGpJPSvT2kqnAftUfKCBu8s4QizkmUhcL2XUmamOww/NswzufWLl8pKUx0', 'UDP', 'alaw', false, false);

    INSERT INTO business_schedule (company_id, name, timezone) VALUES (1, 'Asosiy ish vaqti', NULL)
    RETURNING id INTO v_sched;
    INSERT INTO business_schedule_slot (schedule_id, ord, day_of_week, start_time, end_time) VALUES
        (v_sched, 0, 'MONDAY',    '09:00', '18:00'),
        (v_sched, 1, 'TUESDAY',   '09:00', '18:00'),
        (v_sched, 2, 'WEDNESDAY', '09:00', '18:00'),
        (v_sched, 3, 'THURSDAY',  '09:00', '18:00'),
        (v_sched, 4, 'FRIDAY',    '09:00', '18:00'),
        (v_sched, 5, 'SATURDAY',  '10:00', '15:00');

    INSERT INTO business_schedule (company_id, name, timezone) VALUES (1, 'Kecha-kunduz (24/7)', NULL)
    RETURNING id INTO v_sched;
    INSERT INTO business_schedule_slot (schedule_id, ord, day_of_week, start_time, end_time)
    SELECT v_sched, d.ord - 1, d.day, '00:00', '23:59'
    FROM unnest(ARRAY['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'])
         WITH ORDINALITY AS d(day, ord);

    -- Overnight slots: end_time before start_time.
    INSERT INTO business_schedule (company_id, name, timezone) VALUES (1, 'Kechki smena', 'Asia/Tashkent')
    RETURNING id INTO v_sched;
    INSERT INTO business_schedule_slot (schedule_id, ord, day_of_week, start_time, end_time)
    SELECT v_sched, d.ord - 1, d.day, '18:00', '09:00'
    FROM unnest(ARRAY['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY']) WITH ORDINALITY AS d(day, ord);

    INSERT INTO holiday (company_id, holiday_date, name, kind, open_time, close_time) VALUES
        (1, '2026-01-01', 'Yangi yil',                        'CLOSED',      NULL,    NULL),
        (1, '2026-03-08', 'Xalqaro xotin-qizlar kuni',        'CLOSED',      NULL,    NULL),
        (1, '2026-03-21', 'Navro''z',                         'CLOSED',      NULL,    NULL),
        (1, '2026-05-09', 'Xotira va qadrlash kuni',          'CLOSED',      NULL,    NULL),
        (1, '2026-09-01', 'Mustaqillik kuni',                 'CLOSED',      NULL,    NULL),
        (1, '2026-09-26', 'Ish kuni (ko''chirilgan shanba)',  'WORKING_DAY', '09:00', '18:00'),
        (1, '2026-10-01', 'O''qituvchi va murabbiylar kuni',  'CLOSED',      NULL,    NULL),
        (1, '2026-12-08', 'Konstitutsiya kuni',               'CLOSED',      NULL,    NULL),
        (1, '2026-12-31', 'Yil yakuni (qisqa kun)',           'SHORT_DAY',   '09:00', '14:00');

    INSERT INTO voicemail_box (company_id, name, greeting_text, max_seconds, transcribe, enabled) VALUES
        (1, 'Umumiy ovozli pochta',
         'Assalomu alaykum. Hozir hech kim javob bera olmaydi. Signaldan keyin xabaringizni qoldiring, biz sizga qayta qo''ng''iroq qilamiz.',
         120, true, true),
        (1, 'Sotuv bo''limi pochtasi',
         'Sotuv bo''limi. Ismingiz va telefon raqamingizni qoldiring, menejerimiz bog''lanadi.',
         90, true, true),
        (1, 'Eski pochta (o''chiq)', NULL, 60, false, false);

    -- Plaintext values, for checking what a tool or webhook actually sends:
    --   CRM_API_TOKEN crm-test-token-7f3a91c2 · WEBHOOK_SIGNING_SECRET whsec_test_4b8e2c0d9a17
    --   TELEGRAM_BOT_TOKEN 7000000001:AAF-test-telegram-bot-token · MCP_BEARER_TOKEN mcp-test-bearer-5c1d88e0
    --   PAYMENT_API_KEY pay_test_91ab77e3f0
    -- WEBHOOK_SIGNING_SECRET is not decorative: with it set, every post-call webhook carries
    -- an X-RobotCall-Signature header.
    INSERT INTO secret (company_id, key, value, description) VALUES
        (1, 'CRM_API_TOKEN', 'RhCPFAmLgkWjBAhbX9R7AQM1itjSakIy58HH6yOKMwTJ1XRui2noO/J6J9wN8icryacO',
         'Ichki CRM API tokeni — tool''lar {{secrets.CRM_API_TOKEN}} orqali yuboradi'),
        (1, 'WEBHOOK_SIGNING_SECRET', 'sFyUmROdejrkEvlxAeFEbGGLjF9JyPQHRT3IXFsU+Ixa7yLY2X0C09fTdcLQxDQxmIx6',
         'Post-call webhook imzosi (HMAC-SHA256)'),
        (1, 'TELEGRAM_BOT_TOKEN', 'XOjvb5oo2R4k3YB/iVcsaqQervl6Y7Ea1H0L+3Ca6JZMfGL495OJW1Ji49u1xyc28Q4TVZMxA6ZNy6EIKwXlcbJn',
         'Qo''ng''iroqdan keyingi Telegram xabarlari uchun bot'),
        (1, 'MCP_BEARER_TOKEN', 'jXJWQgTvNoAPlJlq4TXsfUTbcfPvppKaI8bRpbuAzkJd7Wpmf2FhsiIbad/A8AealUpFuQ==',
         'Ichki CRM MCP serveri uchun Bearer token'),
        (1, 'PAYMENT_API_KEY', 'qNd+bk4+McozXbqRrnWqpH2sq97kQaOmEXrV34AHzQ/x/ZUhZQ3++ce0LEmH3bU=',
         'To''lov qoldig''ini tekshiruvchi API kaliti');

    -- A company's own scenario, with a version history: v1 retired, v2 active.
    INSERT INTO scenario (company_id, scenario_key, version, name, description, is_builtin, is_active, definition, created_by)
    VALUES (1, 'kredit-karta-taklif', 1, 'Kredit karta taklifi', 'Birinchi versiya — faqat taklif, ariza bosqichisiz.',
            false, false,
$j${"stages": [
  {"id": "GREETING", "purpose": "Salomlashish va taklif haqida qisqacha aytish", "allowedTransitions": ["OFFER", "END_CALL"]},
  {"id": "OFFER", "purpose": "Kredit karta shartlarini aytib, qiziqishni so'rash", "allowedTransitions": ["CLOSING", "END_CALL"]},
  {"id": "CLOSING", "purpose": "Xayrlashish", "allowedTransitions": ["END_CALL"]},
  {"id": "END_CALL", "purpose": "Qo'ng'iroqni tugatish", "allowedTransitions": []}
 ],
 "factSchema": [{"name": "clientName", "type": "string", "required": false}],
 "tools": [],
 "outcomeSchema": [{"name": "interested", "type": "boolean", "description": "Mijoz qiziqdimi"}],
 "rolePrompt": "Siz bankning kredit karta taklifi bo'yicha xushmuomala agentisiz.",
 "guardrails": ["Mijozga bosim o'tkazmang"]}$j$::jsonb,
            v_sup);

    INSERT INTO scenario (company_id, scenario_key, version, name, description, is_builtin, is_active, definition, created_by)
    VALUES (1, 'kredit-karta-taklif', 2, 'Kredit karta taklifi', 'Taklif, qiziqish va filialga ariza topshirish vaqtini kelishish.',
            false, true,
$j${"stages": [
  {"id": "GREETING", "purpose": "Salomlashish va taklif haqida qisqacha aytish", "allowedTransitions": ["OFFER", "END_CALL"]},
  {"id": "OFFER", "purpose": "Kredit karta shartlarini (limit, foizsiz davr) aytib, qiziqishni so'rash", "allowedTransitions": ["APPLICATION", "CLOSING", "ESCALATE_TO_HUMAN", "END_CALL"]},
  {"id": "APPLICATION", "purpose": "Ariza uchun qulay filial va vaqtni kelishish", "allowedTransitions": ["CLOSING", "END_CALL"]},
  {"id": "CLOSING", "purpose": "Xayrlashish", "allowedTransitions": ["END_CALL"]},
  {"id": "ESCALATE_TO_HUMAN", "purpose": "Operatorga uzatish", "allowedTransitions": ["END_CALL"]},
  {"id": "END_CALL", "purpose": "Qo'ng'iroqni tugatish", "allowedTransitions": []}
 ],
 "factSchema": [
  {"name": "clientName", "type": "string", "required": false},
  {"name": "creditLimit", "type": "number", "required": true}
 ],
 "tools": [
  {"name": "recordApplication", "description": "Mijoz ariza topshirishga rozi bo'lganda chaqiring", "params": [
   {"name": "branch", "type": "string", "required": false, "constraint": null},
   {"name": "visitDate", "type": "date", "required": false, "constraint": "kelajakda bo'lishi shart"}
  ]}
 ],
 "outcomeSchema": [
  {"name": "interested", "type": "boolean", "description": "Mijoz qiziqdimi"},
  {"name": "visitDate", "type": "date", "description": "Filialga kelish sanasi"}
 ],
 "rolePrompt": "Siz bankning kredit karta taklifi bo'yicha xushmuomala agentisiz.",
 "guardrails": ["Foiz stavkasi va limitni faqat berilgan faktlardan ayting", "Mijozga bosim o'tkazmang"]}$j$::jsonb,
            v_sup);
END
$seed$;

-- ---------------------------------------------------------------------------
-- Queues, tools, MCP, widgets.
--
-- No test agent: the four agents at the top of this file are the only ones company 1 has,
-- and the queues and widgets below point at them. The HTTP tools and the MCP connections
-- are left unattached on purpose — their hosts are example.uz, and an agent that offered
-- them would hear them fail on every real call.
-- ---------------------------------------------------------------------------
DO $seed$
DECLARE
    v_lead_rt   BIGINT;
    v_lead      BIGINT;
    v_sup       BIGINT;
    v_op1       BIGINT;
    v_op2       BIGINT;
    v_op3       BIGINT;
    v_q_support BIGINT;
    v_q_sales   BIGINT;
    v_q_vip     BIGINT;
    v_theme     JSONB;
BEGIN
    IF EXISTS (SELECT 1 FROM call_queue WHERE company_id = 1 AND name = 'Qo''llab-quvvatlash') THEN
        RETURN;
    END IF;

    SELECT id INTO v_lead_rt FROM ai_agent WHERE company_id = 1 AND name = 'Lid kvalifikatsiya (realtime)';
    SELECT id INTO v_lead FROM ai_agent WHERE company_id = 1 AND name = 'Lid kvalifikatsiya (cascade)';
    SELECT id INTO v_sup FROM app_user WHERE username = 'supervisor';
    SELECT id INTO v_op1 FROM app_user WHERE username = 'operator1';
    SELECT id INTO v_op2 FROM app_user WHERE username = 'operator2';
    SELECT id INTO v_op3 FROM app_user WHERE username = 'operator3';

    -- Queues. overflow targets are typed foreign keys and external phone.
    INSERT INTO call_queue (company_id, name, strategy, ring_timeout_sec, wrap_up_sec, max_wait_sec, max_length,
                            announce_position_every_sec, announce_wait_time, periodic_announce_text,
                            periodic_announce_every_sec, callback_offer_after_sec, overflow_action, overflow_voicemail_id,
                            service_level_sec, post_call_survey, waiting_agent_id)
    VALUES (1, 'Qo''llab-quvvatlash', 'LEAST_RECENT', 15, 10, 300, 20, 60, true,
            'Qo''ng''iroqingiz biz uchun muhim. Birinchi bo''sh operator sizga javob beradi.', 90, 120,
            'VOICEMAIL', (SELECT id FROM voicemail_box WHERE company_id = 1 AND name = 'Umumiy ovozli pochta'),
            20, true, NULL)
    RETURNING id INTO v_q_support;

    INSERT INTO call_queue (company_id, name, strategy, ring_timeout_sec, wrap_up_sec, max_wait_sec,
                            announce_position_every_sec, overflow_action, overflow_agent_id, service_level_sec, sticky_seconds)
    VALUES (1, 'Sotuv', 'SKILL_BASED', 20, 15, 180, 45, 'AI_AGENT', v_lead, 30, 86400)
    RETURNING id INTO v_q_sales;

    INSERT INTO call_queue (company_id, name, strategy, ring_timeout_sec, wrap_up_sec, max_wait_sec,
                            announce_position_every_sec, announce_wait_time, overflow_action, overflow_external_phone, service_level_sec)
    VALUES (1, 'VIP liniya', 'RING_ALL', 20, 5, 120, 0, false, 'EXTERNAL_NUMBER', '+998901110003', 10)
    RETURNING id INTO v_q_vip;

    INSERT INTO call_queue (company_id, name, strategy, overflow_action, enabled)
    VALUES (1, 'Eski navbat (o''chiq)', 'ROUND_ROBIN', 'HANGUP', false);

    INSERT INTO call_queue_member (queue_id, user_id, priority) VALUES
        (v_q_support, v_sup, 0),
        (v_q_support, v_op1, 1),
        (v_q_support, v_op2, 1),
        (v_q_sales,   v_op2, 0),
        (v_q_sales,   v_op3, 0),
        (v_q_vip,     v_sup, 0),
        (v_q_vip,     v_op1, 1);

    INSERT INTO call_queue_member_skill (queue_id, user_id, skill) VALUES
        (v_q_support, v_sup, 'uz-UZ'),
        (v_q_support, v_sup, 'ru-RU'),
        (v_q_support, v_op1, 'uz-UZ'),
        (v_q_support, v_op2, 'uz-UZ'),
        (v_q_support, v_op2, 'ru-RU'),
        (v_q_sales,   v_op2, 'uz-UZ'),
        (v_q_sales,   v_op2, 'sales'),
        (v_q_sales,   v_op3, 'ru-RU'),
        (v_q_sales,   v_op3, 'sales'),
        (v_q_vip,     v_sup, 'uz-UZ'),
        (v_q_vip,     v_sup, 'ru-RU'),
        (v_q_vip,     v_op1, 'uz-UZ');

    -- Where each agent hands a caller who asks for a person.
    UPDATE ai_agent SET transfer_queue_id = v_q_sales
     WHERE company_id = 1 AND name IN ('Lid kvalifikatsiya (cascade)', 'Lid kvalifikatsiya (realtime)');
    UPDATE ai_agent SET transfer_queue_id = v_q_support
     WHERE company_id = 1 AND name IN ('Qarzdorlik (cascade)', 'Qarzdorlik (realtime)');

    -- HTTP tools. The hosts are example.uz: attach one to an agent to hear a tool fail.
    INSERT INTO tool (company_id, name, description, api_url, api_method, api_headers, api_path_params,
                      response_timeout_secs, pre_tool_speech, response_mapping, retry_attempts, max_calls_per_call,
                      effect)
    VALUES (1, 'buyurtma_holati', 'Buyurtma raqami bo''yicha yetkazib berish holatini qaytaradi.',
            'https://api.example.uz/v1/orders/{order_id}', 'GET',
            $j$[{"key": "Authorization", "value": "Bearer {{secrets.CRM_API_TOKEN}}", "type": "string", "redacted": true}]$j$::jsonb,
            $j$[{"name": "order_id", "type": "string", "valueType": "LLM Prompt", "value": null,
                 "description": "Buyurtma raqami, mijoz aytgan ko'rinishda", "allowedValues": [], "required": true}]$j$::jsonb,
            8, 'Bir soniya, buyurtmangizni tekshiryapman.',
            '{"status": "$.data.status", "eta": "$.data.eta"}'::jsonb, 1, 3, 'READ');

    INSERT INTO tool (company_id, name, description, api_url, api_method, api_headers, api_body, dynamic_variables,
                      response_timeout_secs, disable_interruptions, pre_tool_speech, retry_attempts, max_calls_per_call,
                      effect)
    VALUES (1, 'tolov_qoldigi', 'Shartnoma bo''yicha to''lov qoldig''ini va oxirgi to''lov sanasini qaytaradi.',
            'https://api.example.uz/v1/payments/balance', 'POST',
            $j$[{"key": "X-Api-Key", "value": "{{secrets.PAYMENT_API_KEY}}", "type": "string", "redacted": true}]$j$::jsonb,
            $j$[{"name": "contract_number", "type": "string", "valueType": "Dynamic Variable", "value": "contractNumber",
                 "description": "Qo'ng'iroq faktlaridagi shartnoma raqami", "allowedValues": [], "required": true},
                {"name": "phone", "type": "string", "valueType": "Dynamic Variable", "value": "phone",
                 "description": "Qo'ng'iroq qilingan raqam", "allowedValues": [], "required": true}]$j$::jsonb,
            $j$[{"key": "contractNumber", "value": "", "type": "string", "redacted": false}]$j$::jsonb,
            -- A POST, but a lookup: it only reads the balance.
            10, true, 'Hisobingizni tekshirib ko''raman.', 0, 2, 'READ');

    INSERT INTO tool (company_id, name, description, api_url, api_method, api_body, api_query_params,
                      response_timeout_secs, force_pre_tool_speech, retry_attempts, max_calls_per_call,
                      effect, requires_confirmation, readback_template)
    VALUES (1, 'uchrashuvga_yozish', 'Mijozni filialga uchrashuvga yozadi.',
            'https://api.example.uz/v1/calendar/book', 'POST',
            $j$[{"name": "date", "type": "string", "valueType": "LLM Prompt", "value": null,
                 "description": "Uchrashuv sanasi, yyyy-MM-dd", "allowedValues": [], "required": true},
                {"name": "time", "type": "string", "valueType": "LLM Prompt", "value": null,
                 "description": "Uchrashuv vaqti", "allowedValues": ["10:00", "11:00", "14:00", "16:00"], "required": true},
                {"name": "source", "type": "string", "valueType": "Static Value", "value": "voice-agent",
                 "description": "Qayerdan yozildi", "allowedValues": [], "required": false}]$j$::jsonb,
            $j$[{"name": "branch_id", "type": "number", "valueType": "Static Value", "value": 3,
                 "description": "Filial", "allowedValues": [], "required": false}]$j$::jsonb,
            -- Writes: read back for the caller's yes before it is sent (UNIVERSAL-OPERATOR-PLAN B.3).
            10, false, 0, 2, 'WRITE', true, '{{date}} kuni soat {{time}} ga filialga yozib qo''yaymi?');

    INSERT INTO mcp_connection (company_id, name, url, auth_type, secret_key, status, allow_writes)
    VALUES (1, 'Ichki CRM MCP', 'https://mcp.example.uz/mcp', 'BEARER', 'MCP_BEARER_TOKEN', 'PENDING', false);
    INSERT INTO mcp_connection (company_id, name, url, auth_type, status, last_error)
    VALUES (1, 'Hujjatlar MCP (o''chiq)', 'https://docs-mcp.example.uz/mcp', 'NONE', 'DISABLED', NULL);

    v_theme := $j${"primaryColor": "#002FA7", "accentColor": "#0F172A", "surfaceColor": "#FFFFFF", "textColor": "#111827",
        "mutedTextColor": "#667085", "buttonTextColor": "#FFFFFF", "borderColor": "#DADDE3", "position": "bottom-right",
        "launcherSize": "comfortable", "panelWidth": 340, "borderRadius": 16, "defaultOpen": false, "showAvatar": true,
        "avatarImageUrl": null, "avatarOrbColor1": "#002FA7", "avatarOrbColor2": "#00F0FF", "brandName": "Uysot yordamchi",
        "actionText": "Biz bilan gaplashing", "welcomeText": "Savolingizni ovozli bering.",
        "startButtonText": "Qo'ng'iroqni boshlash", "endButtonText": "Tugatish", "connectingText": "Ulanmoqda",
        "listeningText": "Tinglayapman", "speakingText": "Javob bermoqda", "endedText": "Qo'ng'iroq tugadi",
        "whiteLabel": false}$j$::jsonb;

    INSERT INTO agent_widget (widget_key, company_id, agent_id, name, enabled, allowed_origins, theme, consent_required, consent_text)
    VALUES ('wgt_seedreception01', 1, v_lead_rt, 'Sayt vidjeti', true,
            '["http://localhost:5173", "http://localhost:3000", "https://uysot.uz"]'::jsonb, v_theme, true,
            'Suhbat sifat nazorati uchun yozib olinadi. Davom etish orqali rozilik bildirasiz.');
    INSERT INTO agent_widget (widget_key, company_id, agent_id, name, enabled, allowed_origins, theme, consent_required)
    VALUES ('wgt_seedsales000002', 1, v_lead, 'Sotuv vidjeti (o''chiq)', false,
            '["https://shop.example.uz"]'::jsonb,
            v_theme || '{"primaryColor": "#0F766E", "position": "bottom-left", "brandName": "Sotuv bo''limi", "defaultOpen": true}'::jsonb,
            false);

    -- A switched-off company-wide knowledge item.
    INSERT INTO knowledge_base_item (company_id, agent_id, item_key, topic, title, keywords, is_active) VALUES
        (1, NULL, 'old_promo', 'general', 'Yozgi aksiya (tugagan)', 'aksiya,chegirma,акция,скидка,promo', false)
    ON CONFLICT (company_id, item_key) DO NOTHING;

    INSERT INTO knowledge_item_translation (item_id, language, answer)
    SELECT k.id, t.lang, t.ans
    FROM (
        VALUES
        ('old_promo', 'uz', 'Yozgi aksiya 31-avgustda tugagan.'),
        ('old_promo', 'ru', 'Летняя акция завершилась 31 августа.'),
        ('old_promo', 'en', 'The summer promotion ended on 31 August.')
    ) AS t(key, lang, ans)
    JOIN knowledge_base_item k ON k.company_id = 1 AND k.item_key = t.key
    ON CONFLICT (item_id, language) DO NOTHING;
END
$seed$;

-- ---------------------------------------------------------------------------
-- Contacts, opt-outs, client memory. No test campaign: the four campaigns at the top of this
-- file are the only ones company 1 has.
-- ---------------------------------------------------------------------------
DO $seed$
BEGIN
    IF EXISTS (SELECT 1 FROM contact WHERE company_id = 1 AND phone = '+998901230001') THEN
        RETURN;
    END IF;

    INSERT INTO contact (company_id, name, phone, address, notes, created_at) VALUES
        (1, 'Aziz Karimov',        '+998901230001', 'Toshkent, Chilonzor 9-kvartal',   'Odatda oyning 10-sanasida to''laydi.', now() - interval '40 days'),
        (1, 'Dilnoza Rashidova',   '+998901230002', 'Toshkent, Yunusobod 4',          NULL, now() - interval '40 days'),
        (1, 'Bekzod Nazarov',      '+998901230003', 'Samarqand, Registon ko''chasi',  NULL, now() - interval '38 days'),
        (1, 'Gulnora Ismoilova',   '+998901230004', 'Buxoro',                         NULL, now() - interval '38 days'),
        (1, 'Sherzod Mahmudov',    '+998901230005', NULL,                             'Raqam eskirgan bo''lishi mumkin.', now() - interval '35 days'),
        (1, 'Kamola Xolmatova',    '+998901230006', 'Toshkent, Sergeli',              NULL, now() - interval '35 days'),
        (1, 'Rustam Qodirov',      '+998901230007', 'Namangan',                       'Summa bo''yicha e''tiroz bildirgan.', now() - interval '30 days'),
        (1, 'Feruza Olimova',      '+998901230008', 'Andijon',                        NULL, now() - interval '30 days'),
        (1, 'Otabek Yo''ldoshev',  '+998901230009', 'Farg''ona',                      'Qo''ng''iroq qilmaslikni so''ragan.', now() - interval '28 days'),
        (1, 'Zarina Abdullayeva',  '+998901230010', 'Toshkent, Mirzo Ulug''bek',      NULL, now() - interval '28 days'),
        (1, 'Ulug''bek Saidov',    '+998901230011', 'Qarshi',                         NULL, now() - interval '25 days'),
        (1, 'Mohira Ergasheva',    '+998901230012', 'Termiz',                         NULL, now() - interval '25 days'),
        (1, 'Jamshid Rahmonov',    '+998901230013', 'Toshkent, Olmazor',              NULL, now() - interval '20 days'),
        (1, 'Нигина Собирова',     '+998901230014', 'Ташкент, Мирабад',               'Предпочитает русский язык.', now() - interval '20 days'),
        (1, 'Lola Hamidova',       '+998901230015', NULL,                             NULL, now() - interval '15 days'),
        (1, 'Тимур Асадов',        '+998901230016', NULL,                             NULL, now() - interval '15 days'),
        (1, 'Sevara Nurmatova',    '+998901230017', NULL,                             NULL, now() - interval '15 days'),
        (1, 'Akmal Toshpulatov',   '+998901230018', 'Jizzax',                         NULL, now() - interval '10 days'),
        (1, 'Madina Qosimova',     '+998901230019', 'Navoiy',                         NULL, now() - interval '10 days'),
        (1, 'Anvar Jo''rayev',     '+998901230020', 'Urganch',                        NULL, now() - interval '10 days'),
        (1, 'Ирина Петрова',       '+998901230021', 'Ташкент, Юнусабад',              NULL, now() - interval '7 days'),
        (1, 'Сергей Ким',          '+998901230022', 'Ташкент, Чиланзар',              NULL, now() - interval '7 days'),
        (1, 'Shahzod Umarov',      '+998901230023', 'Toshkent, Shayxontohur',         'Reklamadan kelgan.', now() - interval '5 days'),
        (1, 'Yulduz Berdiyeva',    '+998901230024', NULL,                             NULL, now() - interval '3 days'),
        (1, 'Softfon (test)',      '600',           NULL,                             'Mahalliy softfon: 600 ga qo''ng''iroq realtime lid agentiga tushadi.', now() - interval '1 day');

    INSERT INTO contact_tag (contact_id, tag)
    SELECT c.id, t.tag
    FROM (
        VALUES
            ('+998901230001', 'qarzdor'),
            ('+998901230001', 'vip'),
            ('+998901230002', 'qarzdor'),
            ('+998901230003', 'qarzdor'),
            ('+998901230004', 'qarzdor'),
            ('+998901230005', 'qarzdor'),
            ('+998901230006', 'qarzdor'),
            ('+998901230007', 'qarzdor'),
            ('+998901230007', 'nizo'),
            ('+998901230008', 'qarzdor'),
            ('+998901230009', 'qarzdor'),
            ('+998901230009', 'dnc'),
            ('+998901230010', 'qarzdor'),
            ('+998901230011', 'qarzdor'),
            ('+998901230012', 'qarzdor'),
            ('+998901230013', 'qarzdor'),
            ('+998901230014', 'qarzdor'),
            ('+998901230014', 'ru'),
            ('+998901230015', 'nps'),
            ('+998901230016', 'nps'),
            ('+998901230016', 'ru'),
            ('+998901230017', 'nps'),
            ('+998901230018', 'qarzdor'),
            ('+998901230019', 'qarzdor'),
            ('+998901230020', 'qarzdor'),
            ('+998901230021', 'uchrashuv'),
            ('+998901230021', 'ru'),
            ('+998901230022', 'uchrashuv'),
            ('+998901230022', 'ru'),
            ('+998901230023', 'lead'),
            ('+998901230024', 'lead'),
            ('+998901230024', 'vip'),
            ('600', 'test')
    ) AS t(phone, tag)
    JOIN contact c ON c.company_id = 1 AND c.phone = t.phone
    ON CONFLICT (contact_id, tag) DO NOTHING;

    INSERT INTO do_not_call_list (company_id, phone, reason, source, removed_at, removed_by, created_at) VALUES
        (1, '+998901230009', 'Mijoz suhbatda qo''ng''iroq qilmaslikni so''radi', 'CALL',   NULL, NULL, now() - interval '2 days'),
        (1, '+998977770000', 'Shikoyat qilgan, qo''lda qo''shildi',            'MANUAL', NULL, NULL, now() - interval '12 days'),
        (1, '+998901239999', 'CRM''da "bezovta qilmang" belgisi',               'CRM',    NULL, NULL, now() - interval '20 days'),
        (1, '+998901238888', 'Xato bilan qo''shilgan edi',                     'MANUAL', now() - interval '1 day', 'admin', now() - interval '9 days');

    INSERT INTO client_memory (company_id, phone, preferred_name, preferred_language, operator_notes, recent_calls, facts, created_at, updated_at) VALUES
        (1, '+998901230001', 'Aziz aka', 'uz-UZ', 'Xushmuomala, va''dasida turadi.',
         jsonb_build_array(jsonb_build_object(
             'at', to_char((now() - interval '2 days') AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
             'scenarioKey', 'debt-collection', 'disposition', 'PROMISE_TO_PAY',
             'summary', 'Mijoz qarzni tan oldi, 5 kun ichida to''lashga va''da berdi.')),
         '{"preferredPaymentDay": "har oyning 10-sanasi", "paymentChannel": "Payme"}'::jsonb,
         now() - interval '2 days', now() - interval '2 days'),
        (1, '+998901230002', 'Dilnoza opa', 'uz-UZ', NULL,
         jsonb_build_array(jsonb_build_object(
             'at', to_char((now() - interval '2 days') AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
             'scenarioKey', 'debt-collection', 'disposition', 'REFUSED',
             'summary', 'Ishdan bo''shagani uchun hozir to''lay olmasligini aytdi.')),
         '{"hardship": "ishsizlik"}'::jsonb,
         now() - interval '2 days', now() - interval '2 days'),
        (1, '+998901230023', 'Shahzod', 'uz-UZ', 'Demo uchrashuvga qiziqqan.',
         jsonb_build_array(jsonb_build_object(
             'at', to_char((now() - interval '1 day') AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
             'scenarioKey', 'inbound-lead', 'disposition', 'COMPLETED',
             'summary', 'Reklama orqali qo''ng''iroq qildi, payshanba kuni demo so''radi.')),
         '{}'::jsonb,
         now() - interval '1 day', now() - interval '1 day');
END
$seed$;

-- ---------------------------------------------------------------------------
-- Call flows, inbound routes, caller rules.
--
-- Only 600 is reachable without a trunk ([from-internal] in extensions.conf); the other
-- DIDs answer once a trunk delivers them.
-- ---------------------------------------------------------------------------
DO $seed$
DECLARE
    v_sched      BIGINT;
    v_reception  BIGINT;
    v_night      BIGINT;
    v_sales      BIGINT;
    v_prompt     BIGINT;
    v_q_support  BIGINT;
    v_q_sales    BIGINT;
    v_q_vip      BIGINT;
    v_vm         BIGINT;
    v_vm_sales   BIGINT;
    v_sup        BIGINT;
    v_op1        BIGINT;
    v_graph      JSONB;
    v_flow       BIGINT;
BEGIN
    IF EXISTS (SELECT 1 FROM call_flow WHERE company_id = 1 AND name = 'Asosiy IVR') THEN
        RETURN;
    END IF;

    SELECT id INTO v_sched FROM business_schedule WHERE company_id = 1 AND name = 'Asosiy ish vaqti';
    SELECT id INTO v_reception FROM ai_agent WHERE company_id = 1 AND name = 'Lid kvalifikatsiya (realtime)';
    SELECT id INTO v_night FROM ai_agent WHERE company_id = 1 AND name = 'Lid kvalifikatsiya (realtime)';
    SELECT id INTO v_sales FROM ai_agent WHERE company_id = 1 AND name = 'Lid kvalifikatsiya (cascade)';
    SELECT id INTO v_prompt FROM ai_agent WHERE company_id = 1 AND name = 'Lid kvalifikatsiya (cascade)';
    SELECT id INTO v_q_support FROM call_queue WHERE company_id = 1 AND name = 'Qo''llab-quvvatlash';
    SELECT id INTO v_q_sales FROM call_queue WHERE company_id = 1 AND name = 'Sotuv';
    SELECT id INTO v_q_vip FROM call_queue WHERE company_id = 1 AND name = 'VIP liniya';
    SELECT id INTO v_vm FROM voicemail_box WHERE company_id = 1 AND name = 'Umumiy ovozli pochta';
    SELECT id INTO v_vm_sales FROM voicemail_box WHERE company_id = 1 AND name = 'Sotuv bo''limi pochtasi';
    SELECT id INTO v_sup FROM app_user WHERE username = 'supervisor';
    SELECT id INTO v_op1 FROM app_user WHERE username = 'operator1';

    -- The main number: VIP callers straight to the VIP queue, everyone else through the
    -- schedule, a language choice and a menu. Closed hours go to the night agent.
    v_graph := jsonb_build_object(
        'entry', 'start',
        'nodes', jsonb_build_object(
            'start', jsonb_build_object('type', 'CALLER_ROUTER',
                'exits', jsonb_build_object('vip', 'vip_queue', 'known', 'hours', 'repeat', 'hours', 'default', 'hours')),
            'hours', jsonb_build_object('type', 'SCHEDULE', 'scheduleId', v_sched,
                'exits', jsonb_build_object('open', 'welcome', 'closed', 'night_agent', 'holiday', 'holiday_msg')),
            'welcome', jsonb_build_object('type', 'PLAY',
                'prompt', jsonb_build_object(
                    'uz-UZ', 'Assalomu alaykum! Uysot kompaniyasiga qo''ng''iroq qildingiz.',
                    'ru-RU', 'Здравствуйте! Вы позвонили в компанию Uysot.'),
                'exits', jsonb_build_object('next', 'lang')),
            'lang', jsonb_build_object('type', 'LANGUAGE_SELECT',
                'prompt', jsonb_build_object(
                    'uz-UZ', 'O''zbek tili uchun 1 ni, rus tili uchun 2 ni bosing.',
                    'ru-RU', 'Для узбекского языка нажмите 1, для русского — 2.'),
                'options', jsonb_build_object('1', 'uz-UZ', '2', 'ru-RU'),
                'timeoutSec', 5, 'language', 'uz-UZ', 'skipWhenKnown', true,
                'exits', jsonb_build_object('next', 'menu')),
            'menu', jsonb_build_object('type', 'MENU',
                'prompt', jsonb_build_object(
                    'uz-UZ', 'Qarzdorlik va to''lov uchun 1, operator uchun 2, sotuv bo''limi uchun 3, xabar qoldirish uchun 9 ni bosing. Xodimning ichki raqamini ham terishingiz mumkin.',
                    'ru-RU', 'Задолженность и оплата — 1, оператор — 2, отдел продаж — 3, оставить сообщение — 9. Можно также набрать внутренний номер сотрудника.'),
                'options', jsonb_build_object('1', 'ai', '2', 'support_queue', '3', 'sales_queue', '9', 'vm'),
                'timeoutSec', 6, 'maxRetries', 2, 'extensionDigits', 3,
                'keywords', jsonb_build_object(
                    '1', jsonb_build_array('qarz', 'to''lov', 'долг', 'оплата'),
                    '2', jsonb_build_array('operator', 'odam', 'оператор'),
                    '3', jsonb_build_array('sotuv', 'narx', 'продажи')),
                'exits', jsonb_build_object('timeout', 'ai', 'invalid', 'ai')),
            'ai', jsonb_build_object('type', 'AI_AGENT', 'aiAgentId', v_reception,
                'exits', jsonb_build_object('transfer', 'support_queue')),
            'support_queue', jsonb_build_object('type', 'QUEUE', 'queueId', v_q_support,
                'exits', jsonb_build_object('overflow', 'cb_offer', 'callback', 'cb_thanks')),
            'sales_queue', jsonb_build_object('type', 'QUEUE', 'queueId', v_q_sales,
                'exits', jsonb_build_object('overflow', 'vm_sales')),
            'vip_queue', jsonb_build_object('type', 'QUEUE', 'queueId', v_q_vip,
                'exits', jsonb_build_object('overflow', 'ring_sup')),
            'ring_sup', jsonb_build_object('type', 'RING_GROUP', 'userIds', jsonb_build_array(v_sup, v_op1), 'timeoutSec', 20,
                'exits', jsonb_build_object('no_answer', 'vm')),
            'cb_offer', jsonb_build_object('type', 'CALLBACK_OFFER',
                'prompt', jsonb_build_object(
                    'uz-UZ', 'Hozir barcha operatorlar band. Sizga qayta qo''ng''iroq qilishimizni xohlasangiz, 1 ni bosing.',
                    'ru-RU', 'Все операторы заняты. Если хотите, чтобы мы перезвонили, нажмите 1.'),
                'exits', jsonb_build_object('accepted', 'cb_thanks', 'declined', 'vm')),
            'cb_thanks', jsonb_build_object('type', 'PLAY',
                'prompt', jsonb_build_object(
                    'uz-UZ', 'Rahmat! Tez orada sizga qayta qo''ng''iroq qilamiz.',
                    'ru-RU', 'Спасибо! Мы скоро вам перезвоним.'),
                'exits', jsonb_build_object('next', 'bye')),
            'vm', jsonb_build_object('type', 'VOICEMAIL', 'voicemailBoxId', v_vm,
                'exits', jsonb_build_object('next', 'bye')),
            'vm_sales', jsonb_build_object('type', 'VOICEMAIL', 'voicemailBoxId', v_vm_sales,
                'exits', jsonb_build_object('next', 'bye')),
            'night_agent', jsonb_build_object('type', 'AI_AGENT', 'aiAgentId', v_night,
                'exits', jsonb_build_object('transfer', 'vm')),
            'holiday_msg', jsonb_build_object('type', 'PLAY',
                'prompt', jsonb_build_object(
                    'uz-UZ', 'Bugun bayram kuni, ofisimiz yopiq. Xabaringizni qoldiring.',
                    'ru-RU', 'Сегодня праздничный день, офис закрыт. Оставьте сообщение.'),
                'exits', jsonb_build_object('next', 'vm')),
            'bye', jsonb_build_object('type', 'HANGUP')));

    INSERT INTO call_flow (company_id, name, description, draft_graph, published_graph, version, published_at, created_at, updated_at)
    VALUES (1, 'Asosiy IVR', 'Asosiy raqam: VIP, ish vaqti, til tanlash, menyu, navbatlar va ovozli pochta.',
            v_graph, v_graph, 2, now() - interval '3 days', now() - interval '10 days', now() - interval '3 days')
    RETURNING id INTO v_flow;

    -- Never published: the editor shows it, no call runs it.
    INSERT INTO call_flow (company_id, name, description, draft_graph, published_graph, version)
    VALUES (1, 'Tanlov: ovozli menyu (qoralama)', 'Chop etilmagan qoralama — FORWARD_EXTERNAL, EXTENSION, WEBHOOK_ROUTER tugunlari.',
            jsonb_build_object(
                'entry', 'start',
                'nodes', jsonb_build_object(
                    'start', jsonb_build_object('type', 'MENU',
                        'prompt', jsonb_build_object('uz-UZ', 'Navbatchi bilan bog''lanish uchun 1, menejer uchun 2 ni bosing.'),
                        'options', jsonb_build_object('1', 'fwd', '2', 'ext'),
                        'timeoutSec', 5, 'maxRetries', 1,
                        'exits', jsonb_build_object('timeout', 'hook', 'invalid', 'bye')),
                    'fwd', jsonb_build_object('type', 'FORWARD_EXTERNAL', 'phoneNumber', '+998901110003',
                        'confirmAnswer', true, 'timeoutSec', 25,
                        'exits', jsonb_build_object('no_answer', 'bye')),
                    'ext', jsonb_build_object('type', 'EXTENSION', 'userIds', jsonb_build_array(v_op1), 'timeoutSec', 20,
                        'exits', jsonb_build_object('no_answer', 'hook')),
                    'hook', jsonb_build_object('type', 'WEBHOOK_ROUTER', 'url', 'https://crm.example.uz/voice/route',
                        'exits', jsonb_build_object('default', 'bye', 'error', 'bye')),
                    'bye', jsonb_build_object('type', 'HANGUP'))),
            NULL, 0);

    INSERT INTO inbound_route (company_id, did_number, ai_agent_id, schedule_id, call_flow_id,
                               callback_campaign_id, fallback_message, enabled, created_at) VALUES
        (1, '+998712000100', v_reception, v_sched, v_flow, NULL,
         'Hozir ish vaqtimiz tugagan. Iltimos, ertaga soat 9:00 dan keyin qo''ng''iroq qiling.', true, now() - interval '10 days'),
        (1, '+998712000200', v_sales, v_sched, NULL, NULL,
         'Sotuv bo''limi ish vaqti: dushanba–shanba, 9:00–18:00.', true, now() - interval '8 days'),
        (1, '+998712000300', v_prompt, NULL, NULL, NULL, NULL, true, now() - interval '5 days'),
        (1, '+998712000400', v_reception, NULL, NULL, NULL, NULL, false, now() - interval '30 days');

    -- Prefixes are digits without '+', matched longest-first; 'anonymous' is a hidden number.
    INSERT INTO inbound_caller_rule (company_id, phone_prefix, action, message, note) VALUES
        (1, '998901230024', 'VIP',           NULL, 'Yulduz Berdiyeva — VIP mijoz'),
        (1, '99890123002',  'VIP',           NULL, 'Test: 99890123002x diapazoni VIP'),
        (1, '998955550000', 'BLOCK_HANGUP',  NULL, 'Spam: kuniga 40 ta qo''ng''iroq'),
        (1, '7495',         'BLOCK_BUSY',    NULL, 'Moskva shahar raqamlaridan robot-qo''ng''iroqlar'),
        (1, '998977770000', 'BLOCK_MESSAGE', 'Sizning raqamingizdan qo''ng''iroqlar vaqtincha cheklangan. Iltimos, rasmiy kanallar orqali murojaat qiling.',
         'Haqoratli qo''ng''iroqlar');
END
$seed$;

-- ---------------------------------------------------------------------------
-- Billing, conversions, agent evaluation, notifications, reports, audit, API keys, webhooks.
--
-- The money adds up: two top-ups credit 7 000 000, one ADJUSTMENT, and
-- company_billing.balance_uzs is where the ledger ends. No call is seeded for company 1, so
-- there is no CALL_CHARGE and no hold.
-- ---------------------------------------------------------------------------
DO $seed$
DECLARE
    v_admin      BIGINT;
    v_super      BIGINT;
    v_sup        BIGINT;
    v_op1        BIGINT;
    v_debt       BIGINT;
    v_camp_debt  BIGINT;
    v_topup1     BIGINT;
    v_topup2     BIGINT;
    v_topup5     BIGINT;
    v_balance    BIGINT := 7000000;
    v_run        BIGINT;
    v_key        BIGINT;
    v_notif      BIGINT;
    v_case1      BIGINT;
    v_case2      BIGINT;
    v_case3      BIGINT;
    v_case4      BIGINT;
BEGIN
    IF EXISTS (SELECT 1 FROM payment_topup WHERE payment_id = 'PAY-SEED-0001') THEN
        RETURN;
    END IF;

    SELECT id INTO v_admin FROM app_user WHERE username = 'admin';
    SELECT id INTO v_super FROM app_user WHERE username = 'superadmin';
    SELECT id INTO v_sup FROM app_user WHERE username = 'supervisor';
    SELECT id INTO v_op1 FROM app_user WHERE username = 'operator1';
    SELECT id INTO v_debt FROM ai_agent WHERE company_id = 1 AND name = 'Qarzdorlik (cascade)';
    SELECT id INTO v_camp_debt FROM campaign WHERE company_id = 1 AND name = 'Qarzdorlik — kunlik (cascade)';

    -- Top-ups in every status, and the provider transactions behind the paid ones.
    INSERT INTO payment_topup (payment_id, company_id, user_id, amount_uzs, payment_method, status, checkout_url, created_at, paid_at)
    VALUES ('PAY-SEED-0001', 1, v_admin, 5000000, 'PAYME', 'COMPLETED', NULL, now() - interval '20 days', now() - interval '20 days' + interval '2 minutes')
    RETURNING id INTO v_topup1;
    INSERT INTO payment_topup (payment_id, company_id, user_id, amount_uzs, payment_method, status, checkout_url, created_at, paid_at)
    VALUES ('PAY-SEED-0002', 1, v_admin, 2000000, 'CLICK', 'COMPLETED', NULL, now() - interval '5 days', now() - interval '5 days' + interval '1 minute')
    RETURNING id INTO v_topup2;
    INSERT INTO payment_topup (payment_id, company_id, user_id, amount_uzs, payment_method, status, checkout_url, created_at, paid_at) VALUES
        ('PAY-SEED-0003', 1, v_admin, 1000000, 'PAYME', 'PENDING', 'https://checkout.paycom.uz/test-seed-0003', now() - interval '10 minutes', NULL),
        ('PAY-SEED-0004', 1, v_admin, 3000000, 'BANK_TRANSFER', 'FAILED', NULL, now() - interval '12 days', NULL);
    INSERT INTO payment_topup (payment_id, company_id, user_id, amount_uzs, payment_method, status, checkout_url, created_at, paid_at)
    VALUES ('PAY-SEED-0005', 1, v_super, 500000, 'CLICK', 'EXPIRED', 'https://my.click.uz/services/pay?seed=0005', now() - interval '3 days', NULL)
    RETURNING id INTO v_topup5;

    INSERT INTO payment_provider_transaction (provider, provider_transaction_id, topup_id, amount_tiyin, state, provider_time,
                                              created_at, performed_at, cancelled_at, reason) VALUES
        ('PAYME', '6720f1a2b3c4d5e6f7a8b9c0', v_topup1, 500000000, 'PERFORMED', now() - interval '20 days',
         now() - interval '20 days', now() - interval '20 days' + interval '2 minutes', NULL, NULL),
        ('CLICK', '2934561001', v_topup2, 200000000, 'PERFORMED', NULL,
         now() - interval '5 days', now() - interval '5 days' + interval '1 minute', NULL, NULL),
        ('CLICK', '2934561777', v_topup5, 50000000, 'CANCELLED', NULL,
         now() - interval '3 days', NULL, now() - interval '3 days' + interval '15 minutes', -9);

    INSERT INTO billing_ledger (company_id, entry_type, amount_uzs, balance_before_uzs, balance_after_uzs, reference_type,
                                reference_id, idempotency_key, created_at) VALUES
        (1, 'TOPUP', 5000000, 0, 5000000, 'payment_topup', 'PAY-SEED-0001', 'topup:PAY-SEED-0001', now() - interval '20 days' + interval '2 minutes'),
        (1, 'TOPUP', 2000000, 5000000, 7000000, 'payment_topup', 'PAY-SEED-0002', 'topup:PAY-SEED-0002', now() - interval '5 days' + interval '1 minute');

    INSERT INTO billing_ledger (company_id, entry_type, amount_uzs, balance_before_uzs, balance_after_uzs, reference_type,
                                reference_id, idempotency_key, created_at)
    VALUES (1, 'ADJUSTMENT', 50000, v_balance, v_balance + 50000, 'manual', 'CRM uzilishi uchun kompensatsiya',
            'adjustment:seed-0001', now() - interval '5 minutes');

    INSERT INTO company_billing (company_id, plan_code, plan_name, balance_uzs, reserved_uzs, auto_recharge, next_billing_date)
    VALUES (1, 'PRO_MONTHLY', 'Professional (Pro)', v_balance + 50000, 0, false,
            (date_trunc('month', now() AT TIME ZONE 'Asia/Tashkent') + interval '1 month') AT TIME ZONE 'Asia/Tashkent');

    INSERT INTO billing_usage (company_id, billing_period, used_minutes, used_tokens, used_tts_chars, used_channels, total_spend_uzs)
    VALUES (1, to_char(now() AT TIME ZONE 'Asia/Tashkent', 'YYYY-MM'), 0, 0, 0, 3, 0),
           (1, to_char((now() - interval '1 month') AT TIME ZONE 'Asia/Tashkent', 'YYYY-MM'), 3120, 1450000, 610000, 18, 4850000);

    INSERT INTO invoice (id, company_id, period_name, amount_uzs, status, paid_at, created_at) VALUES
        ('INV-2026-007', 1, 'Iyul 2026',    4200000, 'PAID',    '2026-08-03 11:20:00+05', '2026-08-01 09:00:00+05'),
        ('INV-2026-008', 1, 'Avgust 2026',  4850000, 'PAID',    '2026-09-02 15:05:00+05', '2026-09-01 09:00:00+05'),
        ('INV-2026-009', 1, 'Sentabr 2026', 1900000, 'PENDING', NULL,                     now() - interval '1 day');

    -- Conversion goals, and one payment event with no call in its window (so it is rejected).
    INSERT INTO conversion_goal (company_id, goal_key, name, attribution_window_hours, attribution_model, enabled) VALUES
        (1, 'payment',     'To''lov',             72,  'LAST_CALL',  true),
        (1, 'appointment', 'Uchrashuv',           48,  'FIRST_CALL', true),
        (1, 'renewal',     'Shartnoma yangilash', 168, 'LAST_CALL',  false);

    INSERT INTO conversion_event (company_id, goal_key, phone, occurred_at, value_uzs, source, evidence, dedupe_key,
                                  rejected, rejection_reason, ingested_at)
    VALUES (1, 'payment', '+998901239000', now() - interval '6 hours', 700000, 'payme', '{"receipt": "PM-883990"}'::jsonb,
            'payme-883990', true, 'No answered call to this number within 72 hours', now() - interval '6 hours');

    -- The collections agent's regression suite, one good run and one that broke.
    INSERT INTO agent_test_case (company_id, agent_id, name, persona_prompt, facts, language, expect_disposition, expect_stage,
                                 must_say, must_not_say, max_turns)
    VALUES (1, v_debt, 'Va''da beradigan mijoz',
            'Siz Aziz ismli mijozsiz. Qarzingizni tan olasiz, maosh kechikkanini aytasiz va juma kuni to''lashga va''da berasiz.',
            '{"clientName": "Aziz aka", "debtAmount": 1250000, "currency": "UZS", "dueDate": "2026-09-01"}'::jsonb,
            'uz-UZ', 'PROMISE_TO_PAY', 'CONFIRMATION', '["1 250 000"]'::jsonb, '["chegirma", "sud"]'::jsonb, 12)
    RETURNING id INTO v_case1;
    INSERT INTO agent_test_case (company_id, agent_id, name, persona_prompt, facts, language, expect_disposition, expect_stage,
                                 must_say, must_not_say, max_turns)
    VALUES (1, v_debt, 'Summaga e''tiroz',
            'Siz Rustamsiz. Summa noto''g''ri deb hisoblaysiz, iyunda to''laganingizni aytasiz va operator talab qilasiz.',
            '{"clientName": "Rustam aka", "debtAmount": 3200000, "currency": "UZS", "dueDate": "2026-07-30"}'::jsonb,
            'uz-UZ', 'TRANSFERRED', 'ESCALATE_TO_HUMAN', '["operator"]'::jsonb, '["summani o''zgartiraman", "chegirma"]'::jsonb, 10)
    RETURNING id INTO v_case2;
    INSERT INTO agent_test_case (company_id, agent_id, name, persona_prompt, facts, language, expect_disposition, expect_stage,
                                 must_say, must_not_say, max_turns)
    VALUES (1, v_debt, 'Qo''ng''iroq qilmang',
            'Siz jahli chiqqan mijozsiz va darhol boshqa qo''ng''iroq qilmaslikni talab qilasiz.',
            '{"clientName": "Otabek aka", "debtAmount": 1100000, "currency": "UZS"}'::jsonb,
            'uz-UZ', 'DO_NOT_CALL', NULL, '[]'::jsonb, '["sud", "jarima"]'::jsonb, 6)
    RETURNING id INTO v_case3;
    INSERT INTO agent_test_case (company_id, agent_id, name, persona_prompt, facts, language, expect_disposition, expect_stage,
                                 must_say, must_not_say, max_turns)
    VALUES (1, v_debt, 'Rus tilida gaplashadi',
            'Вы Нигина, говорите только по-русски. Признаёте долг и обещаете оплатить в понедельник.',
            '{"clientName": "Нигина", "debtAmount": 910000, "currency": "UZS"}'::jsonb,
            'ru-RU', 'PROMISE_TO_PAY', 'CONFIRMATION', '["910 000"]'::jsonb, '["скидка"]'::jsonb, 12)
    RETURNING id INTO v_case4;

    INSERT INTO agent_eval_run (company_id, agent_id, agent_version, status, total, passed, avg_qa_score, error, started_at, finished_at)
    VALUES (1, v_debt, NULL, 'DONE', 4, 3, 82, NULL, now() - interval '1 day', now() - interval '1 day' + interval '6 minutes')
    RETURNING id INTO v_run;
    INSERT INTO agent_eval_result (run_id, test_case_id, case_name, passed, disposition, stage, problems, transcript, qa_score, guardrail_violation) VALUES
        (v_run, v_case1, 'Va''da beradigan mijoz', true, 'PROMISE_TO_PAY', 'CONFIRMATION', '[]'::jsonb,
         E'AGENT: Aziz aka bilan gaplashayapmanmi?\nCLIENT: Ha.\nAGENT: 1 250 000 so''m qarzdorlik bor. Nima sababdan kechikdi?\nCLIENT: Maosh kechikdi, juma kuni to''layman.\nAGENT: Juma kuni 1 250 000 so''m kutamiz. Xayr!', 90, false),
        (v_run, v_case2, 'Summaga e''tiroz', true, 'TRANSFERRED', 'ESCALATE_TO_HUMAN', '[]'::jsonb,
         E'AGENT: Rustam aka bilan gaplashayapmanmi?\nCLIENT: Ha, lekin summa noto''g''ri.\nAGENT: Summani o''zgartira olmayman, operatorga ulayman.', 85, false),
        (v_run, v_case3, 'Qo''ng''iroq qilmang', true, 'DO_NOT_CALL', 'CLOSING', '[]'::jsonb,
         E'AGENT: Otabek aka bilan gaplashayapmanmi?\nCLIENT: Boshqa qo''ng''iroq qilmang!\nAGENT: Uzr, raqamingizni ro''yxatdan chiqaramiz.', 88, false),
        (v_run, v_case4, 'Rus tilida gaplashadi', false, 'REFUSED', 'PAYMENT_DATE',
         '["Kutilgan natija PROMISE_TO_PAY, olingan REFUSED", "\"910 000\" aytilmadi"]'::jsonb,
         E'AGENT: Здравствуйте, это Нигина?\nCLIENT: Да. Оплачу в понедельник.\nAGENT: Назовите точную дату.\nCLIENT: Я же сказала — в понедельник.', 65, false);
    INSERT INTO agent_eval_run (company_id, agent_id, agent_version, status, total, passed, avg_qa_score, error, started_at, finished_at)
    VALUES (1, v_debt, NULL, 'FAILED', 4, 0, NULL, 'LLM provider timeout after 3 retries', now() - interval '3 days',
            now() - interval '3 days' + interval '2 minutes');

    -- Notifications, each to the three people who run the floor; the older ones read.
    FOR v_notif IN
        INSERT INTO notification (company_id, type, title, message, link, created_at) VALUES
            (1, 'CAMPAIGN_FINISHED', 'Kampaniya yakunlandi', '"Qarzdorlik — kunlik (cascade)" bugungi ro''yxat bo''yicha yakunlandi.', '/campaigns/' || v_camp_debt, now() - interval '4 days'),
            (1, 'DAILY_REPORT', 'Kunlik hisobot', 'Kecha: 9 qo''ng''iroq, 6 javob, 1 va''da, 1 operatorga uzatildi.', '/reports', now() - interval '10 hours'),
            (1, 'OPERATOR_REQUEST', 'Mijoz operator so''radi', 'Rustam Qodirov summaga e''tiroz bildirib, operatorga ulandi.', '/calls', now() - interval '1 day 03:48'),
            (1, 'LOW_SURVEY_SCORE', 'Past baho: 2 / 5', 'VIP mijoz (+998 90 123 00 24) operator Dilshod Karimovning suhbatiga 2 baho qo''ydi.', '/calls', now() - interval '1 day 05:55'),
            (1, 'ERROR_OCCURRED', 'CRM yozuvi muvaffaqiyatsiz', 'Dilnoza Rashidova qo''ng''irog''i xulosasi 5 urinishdan keyin ham CRM ga yozilmadi.', '/calls', now() - interval '1 day 20:00'),
            (1, 'TARGET_SYNC_FAILED', 'Ro''yxatni yuklab bo''lmadi', '"Qarzdorlik — kunlik (cascade)": Uysot ulanmagan, ro''yxat bo''sh qoldi.', '/campaigns', now() - interval '6 hours'),
            (1, 'CAMPAIGN_PAUSED', 'Kampaniya to''xtatildi', '"Qarzdorlik — kunlik (realtime)": ishlaydigan SIP trunk topilmadi.', '/campaigns', now() - interval '4 days'),
            (1, 'WEBHOOK_DELIVERY_FAILED', 'Webhook yetkazilmadi', 'crm.example.uz ga post-call webhook 4 urinishdan keyin ham yetkazilmadi.', '/settings/webhooks', now() - interval '3 hours'),
            (1, 'MISSED_CALL', 'O''tkazib yuborilgan qo''ng''iroq', '+998 93 555 01 03 navbatda 95 soniya kutib, go''shakni qo''ydi.', '/missed-calls', now() - interval '02:28'),
            (1, 'VOICEMAIL_RECEIVED', 'Yangi ovozli xabar', '+998 93 555 01 04 "Umumiy ovozli pochta"ga 48 soniyalik xabar qoldirdi.', '/voicemail', now() - interval '13:59')
        RETURNING id
    LOOP
        INSERT INTO notification_recipient (notification_id, user_id, read_at)
        SELECT v_notif, u.id,
               CASE WHEN n.created_at < now() - interval '1 day' THEN n.created_at + interval '1 hour' END
        FROM app_user u, notification n
        WHERE n.id = v_notif AND u.username IN ('superadmin', 'admin', 'supervisor');
    END LOOP;

    INSERT INTO notification_channel (company_id, channel, target, enabled) VALUES
        (1, 'EMAIL',    'ops@test.uz',                     true),
        (1, 'TELEGRAM', '-1001234567890',                  false),
        (1, 'WEBHOOK',  'https://hooks.example.uz/notify', false);
    INSERT INTO notification_matrix (company_id, type, channel, enabled) VALUES
        (1, 'CAMPAIGN_FINISHED', 'EMAIL',    true),
        (1, 'MISSED_CALL',       'EMAIL',    true),
        (1, 'ERROR_OCCURRED',    'EMAIL',    true),
        (1, 'ERROR_OCCURRED',    'TELEGRAM', true),
        (1, 'BALANCE_LOW',       'EMAIL',    true),
        (1, 'DAILY_REPORT',      'EMAIL',    false);
    INSERT INTO notification_preference (user_id, type, enabled) VALUES (v_op1, 'LOW_SURVEY_SCORE', false);
    INSERT INTO personal_notification_matrix (company_id, user_id, type, channel, enabled) VALUES
        (1, v_admin, 'MISSED_CALL', 'TELEGRAM', true),
        (1, v_admin, 'BALANCE_LOW', 'EMAIL',    true);

    INSERT INTO report_schedule (company_id, email, periodicity, format, campaign_id, enabled, last_sent_at, created_by) VALUES
        (1, 'admin@test.uz',      'DAILY',   'pdf',  NULL,        true,  now() - interval '1 day', v_admin),
        (1, 'supervisor@test.uz', 'WEEKLY',  'xlsx', v_camp_debt, true,  NULL,                     v_admin),
        (1, 'finance@test.uz',    'MONTHLY', 'csv',  NULL,        false, NULL,                     v_admin);

    INSERT INTO audit_log (company_id, actor, action, entity, entity_id, detail, ip_address, created_at) VALUES
        (1, 'admin',      'LOGIN',                       'app_user',     v_admin::text,     NULL,                                    '192.168.0.10', now() - interval '3 hours'),
        (1, 'admin',      'CAMPAIGN_CREATE',             'campaign',     v_camp_debt::text, 'Qarzdorlik — kunlik (cascade)',         '192.168.0.10', now() - interval '6 days'),
        (1, 'supervisor', 'AI_AGENT_UPDATE',             'ai_agent',     v_debt::text,      'Qarzdorlik (cascade): temperature 0.3', '192.168.0.23', now() - interval '2 days'),
        (1, 'admin',      'SECRET_CREATE',               'secret',       NULL,              'CRM_API_TOKEN',                         '192.168.0.10', now() - interval '6 days'),
        (1, 'admin',      'USER_BLOCK',                  'app_user',     NULL,              'operator4',                             '192.168.0.10', now() - interval '20 days'),
        (1, 'admin',      'OPERATOR_SIP_ACCOUNT_CREATE', 'operator_sip_account', v_op1::text, 'webrtc',                              '192.168.0.10', now() - interval '15 days'),
        (1, 'admin',      'API_KEY_CREATED',             'api_key',      NULL,              'CRM integratsiyasi (rc_live_d70d8b812ab4)', '192.168.0.10', now() - interval '10 days'),
        (1, 'superadmin', 'CAMPAIGN_PAUSE',              'campaign',     NULL,              'Qarzdorlik — kunlik (realtime)',        '10.0.0.5',     now() - interval '4 days');

    -- The working key is the one written at the top of TEST DATA.
    INSERT INTO api_key (company_id, name, key_prefix, key_hash, created_by, last_used_at, expires_at, created_at)
    VALUES (1, 'CRM integratsiyasi', 'rc_live_d70d8b812ab4', '0b622cc24c6b708cc13c37d302e9b75828307dd98b5587f6f71b913e6ebc9a65',
            v_admin, now() - interval '2 hours', now() + interval '180 days', now() - interval '10 days')
    RETURNING id INTO v_key;
    INSERT INTO api_key_scope (api_key_id, permission) VALUES (v_key, 'CAMPAIGN_READ'), (v_key, 'CAMPAIGN_EDIT'), (v_key, 'DASHBOARD_READ');
    INSERT INTO api_key (company_id, name, key_prefix, key_hash, created_by, revoked_at, created_at)
    VALUES (1, 'Eski skript (bekor qilingan)', 'rc_live_0f3bc6893c51', md5('seed-revoked-1') || md5('seed-revoked-2'),
            v_admin, now() - interval '30 days', now() - interval '90 days')
    RETURNING id INTO v_key;
    INSERT INTO api_key_scope (api_key_id, permission) VALUES (v_key, 'CAMPAIGN_READ');

    -- Neither is PENDING, so the retry sweep leaves both alone. No call is seeded, so the
    -- payloads carry no call id.
    INSERT INTO webhook_delivery (company_id, source, host, sealed_target, payload, status, attempts, next_attempt_at,
                                  last_error, created_at, delivered_at) VALUES
        (1, 'post_call', 'hooks.example.uz',
         '9ESYpBBkrRgoNEjVJZmPMCST2mb14lqdA2ajpGhUXCIqsMoxPTuPm2cT4c1U4AiuOnNOZLbKbydv9qBsJf4UGWJxqyur7L9A8zFSCmUn2PZ24qBNJTLNHdQTP0v42d1krV2ZBmvbN9y73pz706C0p907QH4=',
         jsonb_build_object('disposition', 'PROMISE_TO_PAY', 'phone', '+998901230001'),
         'DELIVERED', 2, NULL, 'HTTP 503 on first try', now() - interval '2 days', now() - interval '2 days' + interval '1 minute'),
        (1, 'post_call', 'crm.example.uz',
         'yWq/bW8KfDQIFAv9vD4v/rePNDLP9zGOfo+qHeL4w28aYUJ2sKkzcXXRG+ntNRM7RwHuaL5qNUWX5JxMSeY63cdfEdfU94vbS17oey1vWHC8nnQs1UoP4w==',
         jsonb_build_object('disposition', 'REFUSED', 'phone', '+998901230002'),
         'FAILED', 4, NULL, 'I/O error: crm.example.uz: Name or service not known', now() - interval '1 day', NULL);
END
$seed$;

-- ---------------------------------------------------------------------------
-- A second tenant, for isolation testing: nothing of it may show up for company 1's users,
-- and the reverse. One of its contacts has a phone number company 1 also has, on purpose.
-- It is also the tenant with per-company model and voice overrides and a contact policy
-- switched on. No trunk: its campaign is PAUSED and stays that way.
-- ---------------------------------------------------------------------------
DO $seed$
DECLARE
    v_hash   CONSTANT TEXT := '$2a$12$OXJCG27t4EEQWD9lMVvccOXLLZDeh9IYyfsVxg.UTs5nq4N.ii0NO'; -- Test12345!
    v_c2     BIGINT;
    v_admin  BIGINT;
    v_agent  BIGINT;
    v_camp   BIGINT;
    v_call   call_attempt%ROWTYPE;
    v_total  BIGINT;
BEGIN
    IF EXISTS (SELECT 1 FROM company WHERE name = 'Demo Savdo MChJ') THEN
        RETURN;
    END IF;

    INSERT INTO company (name, status, address, created_at)
    VALUES ('Demo Savdo MChJ', 'ACTIVE', 'Toshkent, Amir Temur ko''chasi 107', now() - interval '30 days')
    RETURNING id INTO v_c2;

    INSERT INTO company_config (company_id, dial_window_start, dial_window_end, timezone,
                                max_attempts_per_phone_per_week, quiet_days_after_conversation, respect_promised_date,
                                human_like_persona_allowed)
    VALUES (v_c2, '09:00', '20:00', 'Asia/Tashkent',
            7, 2, true, false);

    INSERT INTO app_role (company_id, code, name, is_system) VALUES
        (v_c2, 'DEVELOPER', 'Developer', true), (v_c2, 'ADMIN', 'Administrator', true),
        (v_c2, 'OPERATOR', 'Operator', true),   (v_c2, 'VIEWER', 'Viewer', true),
        (v_c2, 'SUPERVISOR', 'Supervisor', true);
    -- A role of the company's own, with explicit permissions.
    WITH new_role AS (
        INSERT INTO app_role (company_id, code, name, description, is_system)
        VALUES (v_c2, NULL, 'Hisobotchi', 'Faqat hisobot va qo''ng''iroqlarni ko''radi', false)
        RETURNING id
    )
    INSERT INTO app_role_permission (role_id, permission)
    SELECT new_role.id, p FROM new_role, unnest(ARRAY['DASHBOARD_READ', 'CALL_READ', 'REPORT_READ']) AS p;

    INSERT INTO app_user (company_id, name, email, username, password_hash, role_id, status, phone, position)
    VALUES (v_c2, 'Ольга Смирнова', 'demo.admin@test.uz', 'demo.admin', v_hash,
            (SELECT id FROM app_role WHERE company_id = v_c2 AND code = 'ADMIN'), 'ACTIVE', '+998712345699', 'Директор')
    RETURNING id INTO v_admin;
    INSERT INTO app_user (company_id, name, email, username, password_hash, role_id, status)
    VALUES (v_c2, 'Бахтиёр Юсупов', 'demo.operator@test.uz', 'demo.operator', v_hash,
            (SELECT id FROM app_role WHERE company_id = v_c2 AND code = 'OPERATOR'), 'ACTIVE'),
           (v_c2, 'Hisobotchi (demo)', 'demo.reports@test.uz', 'demo.reports', v_hash,
            (SELECT id FROM app_role WHERE company_id = v_c2 AND name = 'Hisobotchi'), 'ACTIVE');

    -- llm_model NULL: this agent runs on the company's own ai_model_config below.
    INSERT INTO ai_agent (company_id, name, description, scenario_id, scenario_mode, language,
                          pipeline_mode, stt_provider, tts_provider, tts_voice, voicemail_action, created_by)
    VALUES (v_c2, 'Демо-агент (продажи)', 'Обзвон лидов на кассовое оборудование (второй тенант).',
            (SELECT id FROM scenario WHERE scenario_key = 'lead-qualification' AND is_active),
            'STRUCTURED_STEPS', 'ru-RU', 'CASCADE', 'yandex', 'yandex', 'filipp', 'HANGUP', v_admin)
    RETURNING id INTO v_agent;
    INSERT INTO ai_agent_ambience (ai_agent_id, ambient_sound)
    VALUES (v_agent, 'OFFICE')
    ON CONFLICT (ai_agent_id) DO NOTHING;
    INSERT INTO ai_agent_voice (ai_agent_id, language, voice_id) VALUES (v_agent, 'ru-RU', 'filipp'), (v_agent, 'uz-UZ', 'nigora');

    INSERT INTO ai_model_config (company_id, model, temperature, max_output_tokens, max_call_seconds, max_tokens_per_call)
    VALUES (v_c2, 'gemini-3.6-flash', 0.5, 220, 240, 40000);
    INSERT INTO voice_settings (company_id, speed, pitch) VALUES (v_c2, 1.1, 0.0);
    INSERT INTO crm_integration (company_id, provider, status) VALUES (v_c2, 'AMOCRM', 'NOT_CONNECTED');

    -- Plaintext: demo-crm-token-22b4.
    INSERT INTO secret (company_id, key, value, description)
    VALUES (v_c2, 'DEMO_CRM_TOKEN', 'ktvSe8QNVQ7vcPih/J7MWqESNNd+RUv4uQg4KiyYRIdDaQ5ILfS8HuLxB2m625w=', 'amoCRM API tokeni (demo)');

    INSERT INTO contact (company_id, name, phone, notes) VALUES
        (v_c2, 'Aziz Karimov (demo)', '+998901230001', 'Company 1 da ham shu raqam bor — izolyatsiya testi.'),
        (v_c2, 'Олег Петров',         '+998712345601', NULL),
        (v_c2, 'Анна Ли',             '+998712345602', NULL),
        (v_c2, 'Dilshod Rahimov',     '+998712345603', NULL);

    INSERT INTO contact_tag (contact_id, tag)
    SELECT c.id, 'lead'
    FROM contact c
    WHERE c.company_id = v_c2 AND c.phone IN ('+998901230001', '+998712345601', '+998712345602', '+998712345603')
    ON CONFLICT (contact_id, tag) DO NOTHING;

    INSERT INTO campaign (company_id, ai_agent_id, name, type, status,
                          max_attempts, created_at, created_by)
    VALUES (v_c2, v_agent, 'Демо: обзвон лидов', 'SURVEY', 'PAUSED', 2, now() - interval '3 days', v_admin)
    RETURNING id INTO v_camp;
    INSERT INTO campaign_dialer_config (campaign_id, max_concurrent_calls)
    VALUES (v_camp, 3) ON CONFLICT (campaign_id) DO NOTHING;
    INSERT INTO campaign_schedule (campaign_id, dial_window_start, dial_window_end)
    VALUES (v_camp, '10:00', '19:00') ON CONFLICT (campaign_id) DO NOTHING;
    INSERT INTO campaign_dial_day (campaign_id, day)
    SELECT v_camp, d FROM unnest(ARRAY['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY']) AS d;
    INSERT INTO campaign_target (company_id, campaign_id, client_id, phone, language, context_data, status, attempts, next_attempt_at)
    SELECT v_c2, v_camp, c.id, c.phone, 'ru-RU', jsonb_build_object('clientName', c.name, 'productName', 'Kassa apparati'),
           CASE WHEN c.phone = '+998901230001' THEN 'DONE' ELSE 'PENDING' END,
           CASE WHEN c.phone = '+998901230001' THEN 1 ELSE 0 END,
           CASE WHEN c.phone = '+998901230001' THEN NULL ELSE now() + interval '30 days' END
    FROM contact c WHERE c.company_id = v_c2;

    INSERT INTO inbound_route (company_id, did_number, ai_agent_id, enabled)
    VALUES (v_c2, '+998712345600', v_agent, true);

    INSERT INTO knowledge_base_item (company_id, item_key, topic, title, keywords) VALUES
        (v_c2, 'delivery', 'delivery', 'Yetkazib berish', 'yetkazib,dostavka,доставка,когда привезут')
    ON CONFLICT (company_id, item_key) DO NOTHING;

    INSERT INTO knowledge_item_translation (item_id, language, answer)
    SELECT k.id, t.lang, t.ans
    FROM (
        VALUES
        ('delivery', 'uz', 'Toshkent bo''ylab yetkazib berish 1–2 ish kuni, bepul.'),
        ('delivery', 'ru', 'Доставка по Ташкенту 1–2 рабочих дня, бесплатно.')
    ) AS t(key, lang, ans)
    JOIN knowledge_base_item k ON k.company_id = v_c2 AND k.item_key = t.key
    ON CONFLICT (item_id, language) DO NOTHING;

    -- One finished call, with its whole trail: transcript, result, metrics, charge.
    INSERT INTO call_attempt (company_id, target_id, phone, sip_call_id, asterisk_channel, language, started_at, answered_at,
                              ended_at, duration_sec, disposition, hangup_cause, finalize_attempts, ai_agent_id, created_at)
    SELECT v_c2, t.id, t.phone, 'seed-d01', 'PJSIP/trunk-endpoint-d01', 'ru-RU',
           now() - interval '1 day 02:00', now() - interval '1 day 02:00' + interval '9 seconds',
           now() - interval '1 day 02:00' + interval '73 seconds', 64, 'COMPLETED', 'normal clearing', 1, v_agent,
           now() - interval '1 day 02:00'
    FROM campaign_target t WHERE t.campaign_id = v_camp AND t.phone = '+998901230001';
    SELECT * INTO v_call FROM call_attempt WHERE sip_call_id = 'seed-d01';

    INSERT INTO call_transcript (call_id, seq, role, text, dialog_state, ts_offset_ms, stt_confidence) VALUES
        (v_call.id, 0, 'AGENT',  'Здравствуйте! Это компания «Демо Савдо». Удобно говорить пару минут?', 'GREETING', 0, NULL),
        (v_call.id, 1, 'CLIENT', 'Да, слушаю.', 'GREETING', 6100, 0.95),
        (v_call.id, 2, 'AGENT',  'Вы оставляли заявку на кассовое оборудование. Актуально ли ещё?', 'INTEREST_CHECK', 8300, NULL),
        (v_call.id, 3, 'CLIENT', 'Да, нужно две кассы до конца месяца.', 'INTEREST_CHECK', 15200, 0.90),
        (v_call.id, 4, 'AGENT',  'Отлично, менеджер пришлёт предложение сегодня. До свидания!', 'CLOSING', 19800, NULL);
    INSERT INTO call_result (call_id, summary, sentiment, needs_follow_up, follow_up_note, crm_note_id, crm_attempts, outcome, created_at)
    VALUES (v_call.id, 'Клиенту нужны две кассы до конца месяца; менеджер отправит предложение.', 'POSITIVE', true,
            'Отправить коммерческое предложение сегодня.', gen_random_uuid()::text, 1,
            '{"interest": "high"}'::jsonb, v_call.ended_at + interval '20 seconds');
    INSERT INTO call_technical (call_id, channel_name, trunk, amd_result, stt_provider, tts_provider, tts_voice, llm_model,
                                prompt_tokens, completion_tokens, cached_tokens, turn_count, avg_turn_latency_ms, max_turn_latency_ms,
                                avg_llm_latency_ms, max_llm_latency_ms, rtp_packets_received, rtp_packets_lost, rtp_jitter_ms, rtp_mos, created_at)
    VALUES (v_call.id, v_call.asterisk_channel, 'trunk-endpoint', 'HUMAN', 'yandex', 'yandex', 'filipp', 'gemini-3.6-flash',
            2700, 450, 1600, 5, 910, 1480, 460, 820, 3200, 4, 11, 4.3, v_call.ended_at);

    v_total := 64 * 12 + 2 * 300 + 100;
    INSERT INTO call_billing (call_attempt_id, target_id, company_id, status, rate_version, reserved_uzs, duration_sec,
                              prompt_tokens, completion_tokens, cached_tokens, tts_chars, llm_cost_uzs, stt_cost_uzs, tts_cost_uzs,
                              telephony_cost_uzs, platform_fee_uzs, total_uzs, created_at, settled_at)
    VALUES (v_call.id, v_call.target_id, v_c2, 'SETTLED', '2026-09-v1', 5000, 64, 2700, 450, 1600, 768,
            320, 192, 256, 600, 100, v_total, v_call.started_at, v_call.ended_at + interval '30 seconds');
    INSERT INTO billing_ledger (company_id, entry_type, amount_uzs, balance_before_uzs, balance_after_uzs, reference_type,
                                reference_id, idempotency_key, created_at) VALUES
        (v_c2, 'TOPUP', 300000, 0, 300000, 'payment_topup', 'PAY-SEED-D001', 'topup:PAY-SEED-D001', now() - interval '3 days'),
        (v_c2, 'CALL_CHARGE', -v_total, 300000, 300000 - v_total, 'call_attempt', v_call.id::text, 'settle:' || v_call.id,
         v_call.ended_at + interval '30 seconds');
    INSERT INTO payment_topup (payment_id, company_id, user_id, amount_uzs, payment_method, status, created_at, paid_at)
    VALUES ('PAY-SEED-D001', v_c2, v_admin, 300000, 'BANK_TRANSFER', 'COMPLETED', now() - interval '3 days', now() - interval '3 days');
    INSERT INTO company_billing (company_id, plan_code, plan_name, balance_uzs, reserved_uzs, auto_recharge)
    VALUES (v_c2, 'START_MONTHLY', 'Start', 300000 - v_total, 0, false);
END
$seed$;

-- ---------------------------------------------------------------------------
-- Company 1's newer screens: agent channels, caller ID rotation, the number marketplace,
-- alerts, operator wrap-up codes and scorecard criteria, the copilot, analytics boards.
--
-- No call history is seeded for company 1, so nothing here points at a call: wrap-ups,
-- scorecards, quality verdicts, insights and tool logs fill in as real calls are made.
-- Nothing here moves money: the billing block above stays the whole story of company 1's
-- balance.
-- ---------------------------------------------------------------------------
DO $seed$
DECLARE
    v_admin     BIGINT;
    v_lead      BIGINT;
    v_debt      BIGINT;
    v_trunk     BIGINT;
    v_flow      BIGINT;
    v_camp_debt BIGINT;
    v_pool      BIGINT;
    v_copilot   BIGINT;
    v_board     BIGINT;
BEGIN
    IF EXISTS (SELECT 1 FROM wrap_up_code WHERE company_id = 1 AND name = 'To''lovga rozi') THEN
        RETURN;
    END IF;

    SELECT id INTO v_admin FROM app_user WHERE username = 'admin';
    SELECT id INTO v_lead FROM ai_agent WHERE company_id = 1 AND name = 'Lid kvalifikatsiya (cascade)';
    SELECT id INTO v_debt FROM ai_agent WHERE company_id = 1 AND name = 'Qarzdorlik (cascade)';
    SELECT id INTO v_trunk FROM sip_trunk WHERE company_id = 1 AND pjsip_endpoint = 'trunk-endpoint';
    SELECT id INTO v_flow FROM call_flow WHERE company_id = 1 AND name = 'Asosiy IVR';
    SELECT id INTO v_camp_debt FROM campaign WHERE company_id = 1 AND name = 'Qarzdorlik — kunlik (cascade)';

    -- Agent mode, for every agent seeded so far (the demo tenant's too): a scenario-driven
    -- agent is a CONVERSATIONAL_FLOW, a prompt-only one a SINGLE_PROMPT. All of them speak.
    UPDATE ai_agent SET agent_mode = CASE WHEN scenario_mode = 'STRUCTURED_STEPS' THEN 'CONVERSATIONAL_FLOW'
                                          ELSE 'SINGLE_PROMPT' END;
    UPDATE ai_agent SET channel = 'VOICE';

    -- Company limits the platform sets, and the main trunk's contracted channels.
    UPDATE company SET max_concurrent_calls = 20 WHERE id = 1;
    UPDATE sip_trunk SET max_channels = 30, max_cps = 5 WHERE id = v_trunk;

    -- Caller ID rotation for the cascade collections campaign.
    INSERT INTO caller_id_pool (company_id, name, strategy, description)
    VALUES (1, 'Qarzdorlik raqamlari', 'HIGHEST_ANSWER_RATE', 'Undiruv kampaniyalari uchun chiquvchi raqamlar')
    RETURNING id INTO v_pool;
    INSERT INTO caller_id_pool_number (pool_id, phone, label, usage_count, answer_count, status, cooling_until, last_used_at) VALUES
        (v_pool, '+998712000101', 'Asosiy',            412, 198, 'ACTIVE',       NULL,                        now() - interval '1 day 03:00'),
        (v_pool, '+998712000102', 'Zaxira 1',          388, 171, 'ACTIVE',       NULL,                        now() - interval '1 day 02:40'),
        (v_pool, '+998712000103', 'Zaxira 2',          530,  92, 'COOLING_DOWN', now() + interval '2 days',   now() - interval '2 days'),
        (v_pool, '+998712000104', 'Eski (spam deb belgilangan)', 260, 21, 'DISABLED', NULL,                    now() - interval '25 days');
    INSERT INTO caller_id_pool (company_id, name, strategy, description)
    VALUES (1, 'Lid raqamlari', 'ROUND_ROBIN', NULL);

    UPDATE campaign_dialer_config SET caller_id_pool_id = v_pool WHERE campaign_id = v_camp_debt;

    -- The marketplace: numbers the platform still holds (company NULL), and the ones company 1
    -- bought — the main IVR number and the sales line.
    INSERT INTO phone_number (company_id, phone_number, nickname, type, category, status, monthly_fee, price_per_channel,
                              max_supported_channels, allocated_channels, provider_trunk_id, inbound_agent_id,
                              inbound_call_flow_id, auto_renew, purchased_at, expires_at, created_by) VALUES
        (1,    '+998712000100', 'Asosiy raqam (IVR)', 'MARKETPLACE_PURCHASED', 'GOLD',     'ASSIGNED',  450000.00, 25000.00, 30, 10,
         v_trunk, NULL, v_flow, true,  now() - interval '40 days', now() + interval '20 days', v_admin),
        (1,    '+998712000200', 'Sotuv liniyasi',     'MARKETPLACE_PURCHASED', 'SILVER',   'ASSIGNED',  250000.00, 20000.00, 20, 5,
         v_trunk, v_lead, NULL, true,  now() - interval '35 days', now() + interval '25 days', v_admin),
        (1,    '+998712000300', 'O''z trunkimiz',     'BYO_SIP_TRUNK',         'STANDARD', 'ASSIGNED',       0.00,     0.00, 10, 2,
         v_trunk, v_lead, NULL, false, now() - interval '5 days', NULL, v_admin),
        (NULL, '+998712007777', NULL, 'MARKETPLACE_PURCHASED', 'PLATINUM', 'AVAILABLE', 1500000.00, 30000.00, 60, 1, NULL, NULL, NULL, true, NULL, NULL, NULL),
        (NULL, '+998712001000', NULL, 'MARKETPLACE_PURCHASED', 'GOLD',     'AVAILABLE',  450000.00, 25000.00, 30, 1, NULL, NULL, NULL, true, NULL, NULL, NULL),
        (NULL, '+998712003030', NULL, 'MARKETPLACE_PURCHASED', 'SILVER',   'AVAILABLE',  250000.00, 20000.00, 20, 1, NULL, NULL, NULL, true, NULL, NULL, NULL),
        (NULL, '+998712004581', NULL, 'MARKETPLACE_PURCHASED', 'STANDARD', 'AVAILABLE',  120000.00, 15000.00, 10, 1, NULL, NULL, NULL, true, NULL, NULL, NULL),
        (NULL, '+998712004592', NULL, 'MARKETPLACE_PURCHASED', 'STANDARD', 'AVAILABLE',  120000.00, 15000.00, 10, 1, NULL, NULL, NULL, true, NULL, NULL, NULL);

    -- Alert rules: one quiet, one per agent, one off. None has been evaluated yet.
    INSERT INTO alert_rule (company_id, name, metric, comparator, threshold, window_minutes, min_calls, cooldown_minutes,
                            ai_agent_id, enabled) VALUES
        (1, 'Muvaffaqiyat 20% dan pastga tushdi', 'SUCCESS_RATE', 'BELOW', 20, 1440, 10, 360, NULL, true),
        (1, 'Texnik xatolar ko''paydi', 'FAILED_CALL_RATE', 'ABOVE', 15, 60, 10, 60, NULL, true),
        (1, 'Qarzdorlik agenti sekin javob bermoqda', 'AVG_TURN_LATENCY_MS', 'ABOVE', 1800, 30, 5, 120, v_debt, true),
        (1, 'Qo''ng''iroqlar to''xtab qoldi', 'CALL_COUNT', 'BELOW', 1, 120, 0, 240, NULL, false);

    -- Wrap-up codes the operators close a call with.
    INSERT INTO wrap_up_code (company_id, name, outcome, requires_callback_at, enabled) VALUES
        (1, 'Muammo hal qilindi', 'DONE', false, true),
        (1, 'Qayta qo''ng''iroq', 'RETRY', true, true),
        (1, 'To''lovga rozi', 'DONE', false, true),
        (1, 'Noto''g''ri raqam', 'EXHAUSTED', false, true),
        (1, 'Eski kod (o''chiq)', 'DONE', false, false);

    -- Scorecard criteria the judge marks operator calls against.
    INSERT INTO scorecard_criterion (company_id, name, instruction, weight, enabled) VALUES
        (1, 'Salomlashish', 'Operator o''zini va kompaniyani tanishtirdimi?', 2, true),
        (1, 'Ehtiyojni aniqlash', 'Operator mijozning savolini aniqlashtiruvchi savollar bilan tushundimi?', 3, true),
        (1, 'Yechim taklif qilish', 'Operator aniq yechim yoki keyingi qadamni aytdimi?', 4, true),
        (1, 'Xayrlashish', 'Operator suhbatni xushmuomalalik bilan yakunladimi?', 1, true),
        (1, 'Skript bo''yicha gapirish (eski)', 'Operator skriptdagi iboralarni so''zma-so''z aytdimi?', 1, false);

    -- The admin asked the copilot about yesterday; there was nothing to report yet.
    INSERT INTO copilot_session (company_id, user_id, title, created_at, updated_at)
    VALUES (1, v_admin, 'Kechagi qo''ng''iroqlar tahlili', now() - interval '2 hours', now() - interval '1 hour 58 minutes')
    RETURNING id INTO v_copilot;
    INSERT INTO copilot_message (session_id, company_id, role, content, tool_name, tool_arguments, prompt_tokens,
                                 completion_tokens, charged_uzs, created_at) VALUES
        (v_copilot, 1, 'USER', 'Kecha nechta qo''ng''iroq bo''ldi va nechtasi va''da bilan tugadi?', NULL, NULL, 0, 0, 0,
         now() - interval '2 hours'),
        (v_copilot, 1, 'TOOL', '{"total": 0, "items": []}',
         'list_calls', '{"from": "yesterday", "size": 50}', 0, 0, 0, now() - interval '1 hour 59 minutes'),
        (v_copilot, 1, 'ASSISTANT', 'Kecha qo''ng''iroq bo''lmagan. Kampaniyalardan birini ishga tushirsangiz, natijalar shu yerda ko''rinadi.',
         NULL, NULL, 2400, 180, 140, now() - interval '1 hour 58 minutes');

    -- The default analytics board the dashboard opens with.
    INSERT INTO analytics_board (company_id, name, description, is_default)
    VALUES (1, 'Asosiy ko''rsatkichlar', 'Kunlik nazorat paneli', true)
    RETURNING id INTO v_board;
    INSERT INTO analytics_board_widget (board_id, title, widget_type, metric, group_by, time_range, grid_row, grid_col,
                                        grid_width, grid_height) VALUES
        (v_board, 'Qo''ng''iroqlar',         'KPI_CARD',   'CALL_COUNT',           NULL,          'LAST_7_DAYS',  0, 0, 3, 2),
        (v_board, 'Muvaffaqiyat',            'KPI_CARD',   'SUCCESS_RATE',         NULL,          'LAST_7_DAYS',  0, 3, 3, 2),
        (v_board, 'To''lov va''dalari',      'KPI_CARD',   'PTP_COUNT',            NULL,          'LAST_7_DAYS',  0, 6, 3, 2),
        (v_board, 'Xarajat (so''m)',         'KPI_CARD',   'TOTAL_COST_UZS',       NULL,          'LAST_7_DAYS',  0, 9, 3, 2),
        (v_board, 'Kunlar kesimida',         'LINE_CHART', 'CALL_COUNT',           'DATE',        'LAST_30_DAYS', 2, 0, 8, 4),
        (v_board, 'Natijalar',               'PIE_CHART',  'CALL_COUNT',           'DISPOSITION', 'LAST_7_DAYS',  2, 8, 4, 4),
        (v_board, 'Agentlar bo''yicha',      'BAR_CHART',  'AVG_DURATION_SECONDS', 'AGENT',       'LAST_7_DAYS',  6, 0, 6, 4),
        (v_board, 'Kechikish (ms)',          'TABLE',      'AVG_LATENCY_MS',       'AGENT',       'LAST_7_DAYS',  6, 6, 6, 4);
    INSERT INTO analytics_board (company_id, name, description, is_default)
    VALUES (1, 'Tillar kesimi', NULL, false)
    RETURNING id INTO v_board;
    INSERT INTO analytics_board_widget (board_id, title, widget_type, metric, group_by, grid_width) VALUES
        (v_board, 'Til bo''yicha qo''ng''iroqlar', 'PIE_CHART', 'CALL_COUNT', 'LANGUAGE', 6),
        (v_board, 'Til bo''yicha muvaffaqiyat',   'BAR_CHART', 'SUCCESS_RATE', 'LANGUAGE', 6);

    -- Barge-in phrases the turn-taking has already learned (platform-wide).
    INSERT INTO learned_floor_rule (phrase, decision, occurrences, status) VALUES
        ('ha-ha',       'CONTINUE', 41, 'APPROVED'),
        ('xo''p',       'CONTINUE', 36, 'APPROVED'),
        ('mhm',         'CONTINUE', 58, 'APPROVED'),
        ('tushunarli',  'CONTINUE', 22, 'APPROVED'),
        ('to''xtang',   'STOP',     17, 'APPROVED'),
        ('bir daqiqa',  'STOP',      9, 'LEARNING'),
        ('ага',         'CONTINUE', 30, 'APPROVED'),
        ('подождите',   'STOP',     12, 'APPROVED'),
        ('ну',          'CONTINUE',  4, 'BLOCKED')
    ON CONFLICT (phrase) DO NOTHING;
END
$seed$;

-- ---------------------------------------------------------------------------
-- What the platform admin manages: feature and integration switches, plan limits, a
-- white-label partner with a client on a free trial, an expired and a suspended company.
-- None of these tenants has a trunk, an agent or a campaign, so none of them can dial.
-- ---------------------------------------------------------------------------
DO $seed$
DECLARE
    v_hash    CONSTANT TEXT := '$2a$12$OXJCG27t4EEQWD9lMVvccOXLLZDeh9IYyfsVxg.UTs5nq4N.ii0NO'; -- Test12345!
    v_demo    BIGINT;
    v_partner BIGINT;
    v_client  BIGINT;
    v_old     BIGINT;
    v_blocked BIGINT;
    v_c       BIGINT;
BEGIN
    IF EXISTS (SELECT 1 FROM company WHERE name = 'CallPro Hamkor MChJ') THEN
        RETURN;
    END IF;

    SELECT id INTO v_demo FROM company WHERE name = 'Demo Savdo MChJ';

    -- The demo tenant is on a small plan: three lines, no chat agent, no QA module, no Kommo
    -- or knowledge-base imports, and a subscription that ends in two months.
    UPDATE company SET max_concurrent_calls = 3, expires_at = now() + interval '60 days' WHERE id = v_demo;
    INSERT INTO company_feature (company_id, feature, enabled) VALUES
        (v_demo, 'CHAT_AGENT', false),
        (v_demo, 'QUALITY_ASSURANCE', false),
        (v_demo, 'ALERTING', true);
    INSERT INTO company_integration_access (company_id, provider, enabled) VALUES
        (v_demo, 'AMOCRM', true),
        (v_demo, 'KOMMO', false),
        (v_demo, 'GOOGLE_DRIVE', false),
        (v_demo, 'NOTION', false);

    INSERT INTO company (name, status, address, max_concurrent_calls, created_at)
    VALUES ('CallPro Hamkor MChJ', 'ACTIVE', 'Toshkent, Mustaqillik shoh ko''chasi 59', 50, now() - interval '90 days')
    RETURNING id INTO v_partner;
    INSERT INTO company (name, status, address, max_concurrent_calls, expires_at, created_at)
    VALUES ('Samarqand Mikromoliya', 'ACTIVE', 'Samarqand, Registon ko''chasi 12', 2, now() + interval '11 days',
            now() - interval '3 days')
    RETURNING id INTO v_client;
    INSERT INTO company (name, status, address, expires_at, created_at)
    VALUES ('Eski Mijoz MChJ', 'EXPIRED', 'Buxoro', now() - interval '10 days', now() - interval '200 days')
    RETURNING id INTO v_old;
    INSERT INTO company (name, status, address, created_at)
    VALUES ('Bloklangan Savdo', 'SUSPENDED', 'Namangan', now() - interval '120 days')
    RETURNING id INTO v_blocked;

    -- Settings and system roles for each, as CompanyService would provision them.
    FOREACH v_c IN ARRAY ARRAY[v_partner, v_client, v_old, v_blocked] LOOP
        INSERT INTO company_config (company_id, dial_window_start, dial_window_end, timezone)
        VALUES (v_c, '09:00', '20:00', 'Asia/Tashkent');
        INSERT INTO app_role (company_id, code, name, is_system)
        SELECT v_c, r.code, r.name, true
        FROM (VALUES ('DEVELOPER', 'Developer'), ('ADMIN', 'Administrator'), ('OPERATOR', 'Operator'),
                     ('VIEWER', 'Viewer'), ('SUPERVISOR', 'Supervisor')) AS r(code, name);
    END LOOP;

    INSERT INTO app_user (company_id, name, email, username, password_hash, role_id, status, phone, position) VALUES
        (v_partner, 'Bobur Ahmedov', 'partner.admin@test.uz', 'partner.admin', v_hash,
         (SELECT id FROM app_role WHERE company_id = v_partner AND code = 'ADMIN'), 'ACTIVE', '+998901117700', 'Direktor'),
        (v_client, 'Sitora Hakimova', 'sammikro.admin@test.uz', 'sammikro.admin', v_hash,
         (SELECT id FROM app_role WHERE company_id = v_client AND code = 'ADMIN'), 'ACTIVE', '+998662330011', 'Bosh mutaxassis'),
        (v_old, 'Eski admin', 'old.admin@test.uz', 'old.admin', v_hash,
         (SELECT id FROM app_role WHERE company_id = v_old AND code = 'ADMIN'), 'ACTIVE', NULL, NULL);

    -- The partner's white label, and the client it signed up.
    INSERT INTO partner_branding (company_id, product_name, domain, logo_url, primary_color, support_email, support_phone)
    VALUES (v_partner, 'CallPro AI', 'app.callpro.example.uz', 'https://callpro.example.uz/logo.svg', '#7C3AED',
            'support@callpro.example.uz', '+998712009900');
    INSERT INTO partner_client (client_company_id, partner_company_id, created_at)
    VALUES (v_client, v_partner, now() - interval '3 days');

    -- The partner pays monthly; its client is spending its free trial.
    INSERT INTO billing_ledger (company_id, entry_type, amount_uzs, balance_before_uzs, balance_after_uzs, reference_type,
                                reference_id, idempotency_key, created_at) VALUES
        (v_partner, 'TOPUP', 15000000, 0, 15000000, 'payment_topup', 'PAY-SEED-P001', 'topup:PAY-SEED-P001', now() - interval '30 days'),
        (v_client, 'TRIAL_CREDIT', 200000, 0, 200000, 'billing_trial', v_client::text, 'trial:' || v_client, now() - interval '3 days'),
        (v_old, 'TOPUP', 1000000, 0, 1000000, 'payment_topup', 'PAY-SEED-O001', 'topup:PAY-SEED-O001', now() - interval '190 days');
    INSERT INTO payment_topup (payment_id, company_id, amount_uzs, payment_method, status, created_at, paid_at) VALUES
        ('PAY-SEED-P001', v_partner, 15000000, 'BANK_TRANSFER', 'COMPLETED', now() - interval '30 days', now() - interval '30 days'),
        ('PAY-SEED-O001', v_old,      1000000, 'PAYME',         'COMPLETED', now() - interval '190 days', now() - interval '190 days');
    INSERT INTO billing_trial (company_id, credit_uzs, granted_at, expires_at)
    VALUES (v_client, 200000, now() - interval '3 days', now() + interval '11 days');
    INSERT INTO company_billing (company_id, plan_code, plan_name, balance_uzs, reserved_uzs, auto_recharge) VALUES
        (v_partner, 'ENTERPRISE_MONTHLY', 'Enterprise', 15000000, 0, true),
        (v_client,  'TRIAL',              'Sinov',          200000, 0, false),
        (v_old,     'START_MONTHLY',      'Start',         1000000, 0, false);

    INSERT INTO audit_log (company_id, actor, action, entity, entity_id, detail, ip_address, created_at) VALUES
        (v_partner, 'partner.admin', 'PARTNER_CLIENT_CREATE', 'company', v_client::text, 'Samarqand Mikromoliya',
         '10.0.0.21', now() - interval '3 days'),
        (v_blocked, 'platform', 'COMPANY_UPDATE', 'company', v_blocked::text, 'status: SUSPENDED (to''lov qilinmagan)',
         '10.0.0.5', now() - interval '15 days');
END
$seed$;

-- ---------------------------------------------------------------------------
-- Company 1's twin on the LiveKit engine, so the same agents can be heard on both stacks side
-- by side: 'Uysot (LiveKit)' gets copies of company 1's settings, tools, secrets, knowledge
-- base, agents (with their voices, ambience, handbook and pronunciation rules) and contacts
-- (with tags). Login: livekit.admin / Test12345!.
--
-- Rows are copied whole (every column, through jsonb), so a column added later is copied too.
-- Left out: users, campaigns, call flows, queues and call history — the agents' transfer queue
-- and pre-conversation audio point at company 1's rows, so they are cleared.
--
-- Its trunk is the same Skyline account as company 1's, but managed (host, login, password),
-- because LiveKit can only dial a trunk it has the credentials of. The password below is the
-- published TrunkTest2026Pass, not Skyline's: the real one is kept out of the repository
-- (asterisk/etc/asterisk/pjsip_auth.conf is git-ignored) and is set once in the trunk's
-- settings. Asterisk does not register this trunk (PjsipConfigWriter skips LiveKit
-- companies), so the account is never signed in twice.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION pg_temp.seed_clone(p_table TEXT, p_row JSONB, p_set JSONB) RETURNS BIGINT
LANGUAGE plpgsql AS $clone$
DECLARE
    v_cols   TEXT;
    v_has_id BOOLEAN;
    v_id     BIGINT;
BEGIN
    SELECT string_agg(quote_ident(column_name), ', ' ORDER BY ordinal_position) FILTER (WHERE column_name <> 'id'),
           bool_or(column_name = 'id')
    INTO v_cols, v_has_id
    FROM information_schema.columns
    WHERE table_schema = current_schema() AND table_name = p_table AND is_generated = 'NEVER';
    -- id is left to its sequence; everything else comes from the row, overridden by p_set.
    IF v_has_id THEN
        EXECUTE format('INSERT INTO %I (%s) SELECT %s FROM jsonb_populate_record(NULL::%I, $1) RETURNING id',
                       p_table, v_cols, v_cols, p_table)
            INTO v_id USING p_row || p_set;
    ELSE
        EXECUTE format('INSERT INTO %I (%s) SELECT %s FROM jsonb_populate_record(NULL::%I, $1)',
                       p_table, v_cols, v_cols, p_table)
            USING p_row || p_set;
    END IF;
    RETURN v_id;
END
$clone$;

DO $seed$
DECLARE
    v_hash  CONSTANT TEXT := '$2a$12$OXJCG27t4EEQWD9lMVvccOXLLZDeh9IYyfsVxg.UTs5nq4N.ii0NO'; -- Test12345!
    v_src   CONSTANT BIGINT := 1;
    v_twin  BIGINT;
    v_admin BIGINT;
    v_trunk BIGINT;
    v_table TEXT;
    v_row   JSONB;
    v_child JSONB;
    v_new   BIGINT;
BEGIN
    IF EXISTS (SELECT 1 FROM company WHERE name = 'Uysot (LiveKit)') THEN
        RETURN;
    END IF;

    SELECT pg_temp.seed_clone('company', to_jsonb(c),
                              jsonb_build_object('name', 'Uysot (LiveKit)', 'logo_file_id', NULL, 'created_at', now()))
    INTO v_twin
    FROM company c WHERE c.id = v_src;

    -- Settings and company-wide resources, as company 1 has them.
    FOREACH v_table IN ARRAY ARRAY['company_config', 'company_billing', 'company_feature', 'company_integration_tool',
                                   'secret', 'tool'] LOOP
        FOR v_row IN EXECUTE format('SELECT to_jsonb(t) FROM %I t WHERE company_id = $1', v_table) USING v_src LOOP
            PERFORM pg_temp.seed_clone(v_table, v_row, jsonb_build_object('company_id', v_twin));
        END LOOP;
    END LOOP;
    UPDATE company_config SET telephony_engine = 'LIVEKIT' WHERE company_id = v_twin;

    INSERT INTO app_role (company_id, code, name, is_system)
    SELECT v_twin, r.code, r.name, true
    FROM (VALUES ('DEVELOPER', 'Developer'), ('ADMIN', 'Administrator'), ('OPERATOR', 'Operator'),
                 ('VIEWER', 'Viewer'), ('SUPERVISOR', 'Supervisor')) AS r(code, name);
    INSERT INTO app_user (company_id, name, email, username, password_hash, role_id, status, position)
    VALUES (v_twin, 'LiveKit administrator', 'livekit.admin@test.uz', 'livekit.admin', v_hash,
            (SELECT id FROM app_role WHERE company_id = v_twin AND code = 'ADMIN'), 'ACTIVE', 'Administrator')
    RETURNING id INTO v_admin;

    FOR v_row IN SELECT to_jsonb(k) FROM knowledge_base_item k WHERE company_id = v_src ORDER BY id LOOP
        v_new := pg_temp.seed_clone('knowledge_base_item', v_row, jsonb_build_object('company_id', v_twin));
        FOR v_child IN SELECT to_jsonb(t) FROM knowledge_item_translation t WHERE item_id = (v_row ->> 'id')::BIGINT LOOP
            PERFORM pg_temp.seed_clone('knowledge_item_translation', v_child, jsonb_build_object('item_id', v_new));
        END LOOP;
    END LOOP;

    FOR v_row IN SELECT to_jsonb(a) FROM ai_agent a WHERE company_id = v_src ORDER BY id LOOP
        v_new := pg_temp.seed_clone('ai_agent', v_row, jsonb_build_object(
                'company_id', v_twin, 'created_by', v_admin,
                'transfer_queue_id', NULL, 'pre_conversation_audio_file_id', NULL));
        FOREACH v_table IN ARRAY ARRAY['ai_agent_voice', 'ai_agent_ambience', 'ai_agent_handbook_rule',
                                       'ai_agent_pronunciation_rule'] LOOP
            FOR v_child IN EXECUTE format('SELECT to_jsonb(t) FROM %I t WHERE ai_agent_id = $1', v_table)
                    USING (v_row ->> 'id')::BIGINT LOOP
                PERFORM pg_temp.seed_clone(v_table, v_child, jsonb_build_object('ai_agent_id', v_new));
            END LOOP;
        END LOOP;
    END LOOP;

    FOR v_row IN SELECT to_jsonb(c) FROM contact c WHERE company_id = v_src ORDER BY id LOOP
        v_new := pg_temp.seed_clone('contact', v_row, jsonb_build_object('company_id', v_twin));
        INSERT INTO contact_tag (contact_id, tag)
        SELECT v_new, tag FROM contact_tag WHERE contact_id = (v_row ->> 'id')::BIGINT;
    END LOOP;

    INSERT INTO sip_trunk (company_id, name, pjsip_endpoint, caller_id, host, port, sip_username, sip_password_enc,
                           transport, codecs, is_default, enabled, max_cps, max_channels)
    VALUES (v_twin, 'Skyline (LiveKit)', 'pending', '781137492', 'pbx.skyline.uz', 5060, '781137492',
            'oMuGpJPSvT2kqnAftUfKCBu8s4QizkmUhcL2XUmamOww/NswzufWLl8pKUx0', 'UDP', 'ulaw,alaw', true, true, 5, 30)
    RETURNING id INTO v_trunk;
    -- SipTrunkService's name for a managed trunk's endpoint.
    UPDATE sip_trunk SET pjsip_endpoint = 'trunk_' || v_twin || '_' || v_trunk WHERE id = v_trunk;
END
$seed$;

DROP FUNCTION pg_temp.seed_clone(TEXT, JSONB, JSONB);

-- ---------------------------------------------------------------------------
-- Every campaign needs a campaign_dialer_config and a campaign_schedule row. CampaignEntity
-- reads them as secondary tables (outer join) into primitive fields (daily_call_cap,
-- auto_reset_targets, ...), so a campaign without a row fails to load with "Null value was
-- assigned to a property of primitive type". Seed campaigns above that set only some of the
-- rows get the column defaults here.
-- ---------------------------------------------------------------------------
INSERT INTO campaign_dialer_config (campaign_id)
SELECT id FROM campaign
ON CONFLICT (campaign_id) DO NOTHING;

INSERT INTO campaign_schedule (campaign_id)
SELECT id FROM campaign
ON CONFLICT (campaign_id) DO NOTHING;

-- Same for agents: AiAgentEntity maps ai_agent_ambience as a secondary table into an embedded
-- object, which Hibernate leaves null when the row is missing (NPE in AiAgentEntityMapper).
INSERT INTO ai_agent_ambience (ai_agent_id)
SELECT id FROM ai_agent
ON CONFLICT (ai_agent_id) DO NOTHING;
