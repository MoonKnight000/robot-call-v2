-- Seed data: everything a fresh database needs to place and answer a call without a
-- single API call first — bootstrap tenant and the platform admin, the voice and model catalogs, the
-- built-in scenario templates, three ready AI agents, the inbound route that answers the
-- softphone test extension and the knowledge base. Flyway repeatable migration — reruns whenever this file's checksum changes, so
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
    ('gemini-2.5-flash-native-audio-latest', 'LLM', 'gemini-live', 'REALTIME', 'Gemini 2.5 Flash Native Audio — barqarorroq, tool chaqirish ishonchli'),
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
    {"id": "GREETING", "purpose": "Tirik operatordek, ismini aytib salomlash: 'Assalomu alaykum, [Ism] aka, yaxshimisiz?' (ayol bo'lsa 'opa'). Mijoz faqat 'alo' deb tursa yoki jim bo'lsa (aloqa yomon) — 'Alo, eshityapsizmi, aka? Assalomu alaykum' deb qayta ayt. Sen salomda hol so'rading — QAYTA hol so'rama ('O'zingiz yaxshimisiz?', 'charchamayapsizmi?' kabi qarshi savol BERILMAYDI). Mijoz senga hol so'rasa — qisqa javob ber va shu gapning o'zida maqsadga o't: 'Rahmat, yaxshi, Xudoga shukur. Aka, men Uysot'dan, uyingiz bo'yicha telefon qilyapman.' Mijoz faqat o'z holini aytsa ('yaxshi', 'shikoyat yo'q', 'rahmat') — 'Rahmat' de va 'Aka, ...' bilan maqsadga o't. Mijoz hol so'ragan bo'lsa, unga OVOZ CHIQARIB javob bermaguncha transitionTo chaqirma va qarzga o'tma. Bu bosqichda qarz haqida gapirma.", "allowedTransitions": ["IDENTITY_CHECK", "END_CALL", "ESCALATE_TO_HUMAN"], "allowedTools": []},
    {"id": "IDENTITY_CHECK", "purpose": "Salomda ismini aytding. Mijoz ismga e'tiroz bildirmay alik olsa yoki hol so'rasa ('va alaykum assalom', 'rahmat, yaxshi', 'ha, eshitaman', 'ha, menman') — bu tasdiq, alohida 'siz falonchimisiz?' deb so'rama, DEBT_NOTICE ga o't. Faqat 'alo', 'a?' yoki 'kim bu?' bo'lsa — qisqa tanishtir: 'Aka, men Uysot'dan, uyingiz bo'yicha telefon qilyapman. [Ism] aka o'zlarimi?'. Telefonni boshqa odam ko'targan bo'lsa ('u akamniki', 'u hozir yo'q') — qarz summasini AYTMA; egasining raqamini so'ra: 'Bo'ldi, nomerlarini aytvorasizmi, o'zimiz telefon qilib qo'yardik?'. Raqamni bo'lak-bo'lak takrorlab yoz ('99... 859...'), recordRefusalReason ga 'Boshqa odam ko'tardi, egasining raqami: ...' deb yoz va 'Rahmat, aka' deb xayrlash. Shaxs aniq bo'lmaguncha qarz haqida gapirma.", "allowedTransitions": ["DEBT_NOTICE", "END_CALL", "ESCALATE_TO_HUMAN"], "allowedTools": ["recordRefusalReason"]},
    {"id": "DEBT_NOTICE", "purpose": "Qarzni bitta qisqa, yumshoq gap bilan ayt, xuddi real operator kabi: 'Aka, oylik to'lov bo'yicha qarzdorligingiz bor ekan, bugungi kun holatiga [summa] so'm. Qachon to'lab bera olasiz?'. Keyin JIM bo'l va tingla — mijozlar odatda o'zi sababini ham, sanasini ham aytadi. Mijoz sanani aytsa — o'sha zahoti recordPaymentPromise. Agar mijoz eshitmagan yoki tushunmagan bo'lsa ('a?', 'nima dedingiz?', 'qaytaring', 'qanaqa qarz?', 'tushunmadim') — 'Eshitilmadimi?' deb SO'RAMA, aytganingni boshqa, soddaroq so'zlar bilan qayta tushuntir: 'Uyingiz bo'yicha oylik to'lovlardan [summa] so'm to'lanmay qolgan ekan'. Mijoz javob bermaguncha keyingi bosqichga o'tma.", "allowedTransitions": ["REASON_INQUIRY", "ESCALATE_TO_HUMAN", "END_CALL"], "allowedTools": ["recordPaymentPromise", "recordRefusalReason", "recordPaymentClaim", "scheduleCallback"]},
    {"id": "REASON_INQUIRY", "purpose": "Mijoz sabab yoki bahona aytdi — avval eshitganingni bildir ('Ha, ha, tushunaman, aka'), keyin sanaga qaytar. Sababni so'rab tergov qilma; sababni mijoz o'zi aytgan bo'lsa yozib ol, aytmagan bo'lsa so'rama. Mijoz 'to'lab boryapman-ku, bittasi qolib ketdi' desa — avval yaxshi tomonini tan ol: 'Ha, vaqtida to'layapsiz, baraka toping, bizda ko'rinib turibdi', keyin: 'Shu qolganini bir yo'la to'lab bersangiz, keyin tekis ketaverasiz-da, aka. Qachonga qila olasiz?'. Mijoz 'to'ladim', 'chekni Telegramga tashladim' desa — tortishma va qayta talab qilma: 'Ha, bo'ldi, bo'ldi, aka. Qachon va qayerga to'lagansiz?' (faqat bitta savol), keyin 'Tekshirib, sizning hisobingizga qo'shib qo'yamiz' de, recordRefusalReason ga 'ALREADY_PAID: ...' deb yoz va 'Rahmat, charchamang, kuningiz xayrli bo'lsin!' deb xayrlash. Mijoz 'nima variant bor?' desa — chegirma yoki muddat o'ylab topma: 'Buni menejerimiz aytib beradi, aka' de. Mijoz qurilish, uy yoki boshqa narsa haqida so'rasa — o'zingdan to'qima: 'Buni menejerimiz aniq aytib beradi, men yozib qo'yaman' deb, gapni yumshoq sanaga qaytar.", "allowedTransitions": ["PAYMENT_DATE", "ESCALATE_TO_HUMAN", "END_CALL"], "allowedTools": ["recordPaymentPromise", "recordRefusalReason", "recordPaymentClaim", "scheduleCallback"]},
    {"id": "PAYMENT_DATE", "purpose": "Aniq sanani ol. Mijoz noaniq aytsa ('oy oxirida', '12 yo 13-ga') — yumshoq aniqlashtir: 'Bo'lmasa 12-sanaga yozib qo'yaymi, aka?'. Sana aytilishi bilan recordPaymentPromise.", "allowedTransitions": ["CONFIRMATION", "ESCALATE_TO_HUMAN", "END_CALL"], "allowedTools": ["recordPaymentPromise", "recordRefusalReason", "recordPaymentClaim", "scheduleCallback"]},
    {"id": "CONFIRMATION", "purpose": "Sanani BITTA xabar gapi bilan qayd et (savol emas, qayta tasdiqlatma) va shu zahoti xayrlash: 'Bo'ldi, aka, 12-sanaga yozib qo'ydim. Aloqada bo'lamiz. Salomat bo'ling, charchamang!'.", "allowedTransitions": ["CLOSING", "PAYMENT_DATE", "ESCALATE_TO_HUMAN"], "allowedTools": ["recordPaymentPromise", "recordRefusalReason"]},
    {"id": "CLOSING", "purpose": "Qisqa, iliq xayrlash: 'Xo'p, aka, xabarlashamiz. Salomat bo'ling, charchamang!' yoki 'Rahmat, kuningiz xayrli bo'lsin!'.", "allowedTransitions": ["END_CALL"], "allowedTools": []},
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

-- ---------------------------------------------------------------------------
-- AI agents. Three, because three is what a working installation needs on day one:
-- one that answers the phone, and one per pipeline for the calls it places.
--
--   Kiruvchi qabulxona      inbound  · CASCADE   · reception scenario
--   Qarzdorlik (cascade)    outbound · CASCADE   · debt-collection scenario
--   Qarzdorlik (realtime)   outbound · REALTIME  · debt-collection scenario
--
-- The two collection agents deliberately differ in nothing but the engine: the same
-- scenario, the same guardrails, the same wording, so a call placed by one can be
-- compared with a call placed by the other and the difference is the pipeline alone.
--
-- Looked up by name (there is no unique constraint on it), so a rerun adds nothing and
-- overwrites nothing an operator has since edited.
-- ---------------------------------------------------------------------------

-- Answers inbound calls. CASCADE: an inbound caller asks unpredictable questions, and
-- the cascade pipeline is the one that can search the knowledge base between turns.
-- voicemail_action IGNORE — a person dialled in, there is no answering machine to detect.
--
-- use_rag off, like the other two: it costs an embedding round trip inside every turn the
-- caller is waiting through, and nothing is uploaded yet for it to find. Turn it on for
-- this agent once a knowledge source is. The keyword knowledge base seeded at the bottom
-- of this file answers without it either way.
-- OMNICHANNEL: the same agent answers the phone, the website widget and the Telegram bot.
INSERT INTO ai_agent (company_id, name, description, channel, agent_mode, scenario_id, scenario_mode, language, persona,
                      first_message, pipeline_mode, stt_provider, tts_provider, llm_model, tts_voice,
                      use_rag, voicemail_action, transfer_message)
SELECT 1, 'Kiruvchi qabulxona',
       'Kiruvchi qo''ng''iroqlarga javob beradi: savolga bilim bazasidan javob, kerak bo''lsa operatorga uzatadi.',
       'OMNICHANNEL', 'CONVERSATIONAL_FLOW',
       (SELECT id FROM scenario WHERE scenario_key = 'reception' AND is_active LIMIT 1),
       'STRUCTURED_STEPS', 'uz-UZ', 'AI_ASSISTANT',
       -- Opens with the question, not with a greeting: the platform's §11.1 disclosure is
       -- spoken from code just before this, and it already says hello.
       'Sizni tinglayapman, qanday yordam bera olaman?',
       'CASCADE', 'yandex', 'yandex', 'gemini-3.8-flash', 'nigora',
       false, 'IGNORE',
       'Hozir sizni operatorga ulayman, bir daqiqa kuting.'
WHERE NOT EXISTS (SELECT 1 FROM ai_agent WHERE company_id = 1 AND name = 'Kiruvchi qabulxona');

INSERT INTO ai_agent_ambience (ai_agent_id, ambient_sound)
SELECT id, 'OFFICE' FROM ai_agent WHERE company_id = 1 AND name = 'Kiruvchi qabulxona'
ON CONFLICT (ai_agent_id) DO UPDATE SET ambient_sound = EXCLUDED.ambient_sound;

-- Outbound collections, cascade pipeline: Yandex recognition, Gemini for the turn,
-- Yandex synthesis. The default for placed calls — every tuning knob in
-- config/speech.yml is calibrated against this chain.
INSERT INTO ai_agent (company_id, name, description, channel, agent_mode, scenario_id, scenario_mode, language, persona,
                      pipeline_mode, stt_provider, tts_provider, llm_model, tts_voice,
                      use_rag, voicemail_action, voicemail_message, transfer_message)
SELECT 1, 'Qarzdorlik (cascade)',
       'Chiquvchi qarzdorlik qo''ng''irog''i: STT + LLM + TTS zanjiri (Yandex + Gemini).',
       'VOICE', 'CONVERSATIONAL_FLOW',
       (SELECT id FROM scenario WHERE scenario_key = 'debt-collection' AND is_active LIMIT 1),
       'STRUCTURED_STEPS', 'uz-UZ', 'AI_ASSISTANT',
       'CASCADE', 'yandex', 'yandex', 'gemini-3.8-flash', 'nigora',
       false, 'LEAVE_MESSAGE',
       -- No amount and no deadline in the machine message: the scenario guardrails forbid
       -- saying either without the client on the line to confirm who is listening.
       'Assalomu alaykum. Shartnomangiz bo''yicha savol bilan qo''ng''iroq qildik. Iltimos, qulay vaqtda qayta bog''laning.',
       'Hozir sizni operatorga ulayman, bir daqiqa kuting.'
WHERE NOT EXISTS (SELECT 1 FROM ai_agent WHERE company_id = 1 AND name = 'Qarzdorlik (cascade)');

INSERT INTO ai_agent_ambience (ai_agent_id, ambient_sound)
SELECT id, 'OFFICE' FROM ai_agent WHERE company_id = 1 AND name = 'Qarzdorlik (cascade)'
ON CONFLICT (ai_agent_id) DO UPDATE SET ambient_sound = EXCLUDED.ambient_sound;

-- The same call on a speech-to-speech engine. llm_model is the realtime model here, not
-- a text one (RealtimeCallConfig#modelOr), and tts_voice must be a catalog id owned by
-- the same provider or the engine falls back to the voice it was deployed with.
INSERT INTO ai_agent (company_id, name, description, channel, agent_mode, scenario_id, scenario_mode, language, persona,
                      pipeline_mode, realtime_provider, llm_model, tts_voice,
                      use_rag, voicemail_action, voicemail_message, transfer_message)
SELECT 1, 'Qarzdorlik (realtime)',
       'Chiquvchi qarzdorlik qo''ng''irog''i: Gemini Live nativ audio (speech-to-speech).',
       'VOICE', 'CONVERSATIONAL_FLOW',
       (SELECT id FROM scenario WHERE scenario_key = 'debt-collection' AND is_active LIMIT 1),
       'STRUCTURED_STEPS', 'uz-UZ', 'AI_ASSISTANT',
       'REALTIME', 'gemini-live', 'gemini-3.8-live', 'gemini-aoede-uz',
       false, 'LEAVE_MESSAGE',
       'Assalomu alaykum. Shartnomangiz bo''yicha savol bilan qo''ng''iroq qildik. Iltimos, qulay vaqtda qayta bog''laning.',
       'Hozir sizni operatorga ulayman, bir daqiqa kuting.'
WHERE NOT EXISTS (SELECT 1 FROM ai_agent WHERE company_id = 1 AND name = 'Qarzdorlik (realtime)');

INSERT INTO ai_agent_ambience (ai_agent_id, ambient_sound)
SELECT id, 'OFFICE' FROM ai_agent WHERE company_id = 1 AND name = 'Qarzdorlik (realtime)'
ON CONFLICT (ai_agent_id) DO UPDATE SET ambient_sound = EXCLUDED.ambient_sound;

-- Which voice each cascade agent speaks a given call language in. The realtime agent is
-- left out on purpose: its voice has to belong to gemini-live, and one Gemini voice
-- already covers both languages.
INSERT INTO ai_agent_voice (ai_agent_id, language, voice_id)
SELECT a.id, v.language, v.voice_id
FROM ai_agent a
CROSS JOIN (VALUES ('uz-UZ', 'nigora'), ('ru-RU', 'alena')) AS v(language, voice_id)
WHERE a.company_id = 1 AND a.name IN ('Kiruvchi qabulxona', 'Qarzdorlik (cascade)')
ON CONFLICT (ai_agent_id, language) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Inbound routing. Without a row here an inbound call is hung up on: AriService looks
-- the dialled number up and, on a miss, has no agent to answer with.
--
-- 600 is the softphone test extension from the dialplan ([from-internal] in
-- asterisk/etc/asterisk/extensions.conf) — a registered softphone dialling it reaches the
-- full pipeline with no trunk involved, which is the only way to try an inbound call on a
-- developer machine. A real DID is added through POST /api/inbound-routes; this row is
-- what makes the installation answer at all out of the box.
--
-- business_hours_* stay NULL, which means "always": a seeded route that stopped answering
-- at 18:00 would look like a broken install to whoever tries it in the evening. Fill them
-- in once the hours are real — fallback_message below is what a caller then hears.
-- ---------------------------------------------------------------------------
INSERT INTO inbound_route (company_id, did_number, ai_agent_id, after_hours_action, fallback_message, enabled)
SELECT 1, '600',
       (SELECT id FROM ai_agent WHERE company_id = 1 AND name = 'Kiruvchi qabulxona' LIMIT 1),
       'PLAY_MESSAGE_AND_HANGUP',
       'Hozir ish vaqtimiz tugagan. Iltimos, dushanbadan jumagacha soat 9:00 dan 18:00 gacha qo''ng''iroq qiling.',
       true
WHERE NOT EXISTS (SELECT 1 FROM inbound_route WHERE company_id = 1 AND did_number = '600');

-- ---------------------------------------------------------------------------
-- The Uysot OAuth connection, half-built: the application's name and the grants its
-- consent screen will ask for. No tokens — those only exist after somebody opens
-- GET /api/settings/integrations/uysot/authorize-url, approves on Uysot's page, and the
-- browser lands back on /api/settings/integrations/uysot/callback. Hence NOT_CONNECTED.
--
-- Seeding it is what makes that button work at all: buildAuthorizeUrl refuses with
-- CRM_INTEGRATION_APP_NOT_CONFIGURED when a company has no row here or an empty grants
-- list, so without this a fresh install has no way to start the flow except by calling
-- PUT /api/settings/integrations/uysot first.
--
-- The four grants are exactly what this platform calls, and nothing else — the consent
-- screen shows them to the company, so asking for more than is used is asking to be
-- refused:
--
--   LEAD:READ        GET /lead/{id} and POST /lead/filter — who is being called, and
--                    identifying an inbound caller by number (CrmClient)
--   LEAD_NOTE:SAVE   POST /lead-note/{leadId}/list — the summary written after every call
--   CONTRACT:READ    POST /contract/filter and GET /contract/{id} — the overdue contracts
--                    the daily campaign below dials, and the lead each one belongs to
--   CALL:SAVE        POST /call-history — the conversation itself on the lead's history
--
-- No LEAD_TASK, no CONTRACT_PAYMENT, and no DELETE anywhere: nothing here removes anything
-- from a customer's CRM. Grants are editable through PUT /api/settings/integrations/uysot,
-- so this row is guarded rather than refreshed — a company that narrowed its own list keeps
-- that list across a reseed.
-- ---------------------------------------------------------------------------
INSERT INTO crm_integration (company_id, provider, app_name, grants_json, status)
VALUES (1, 'UYSOT', 'Uysot Voice Agent',
        '[{"permission":"LEAD","scope":"READ"},'
        || '{"permission":"LEAD_NOTE","scope":"SAVE"},'
        || '{"permission":"CONTRACT","scope":"READ"},'
        || '{"permission":"CALL","scope":"SAVE"}]',
        'NOT_CONNECTED')
ON CONFLICT (company_id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- The daily collections run: every morning at 09:00, call whoever Uysot says is behind.
--
-- DRAFT, not ACTIVE, and that is what makes it run rather than what stops it.
-- RecurringCampaignScheduler only considers a campaign that is *not* currently ACTIVE, so
-- DRAFT is the resting state between runs: the sweep sees 09:00 has arrived, pulls the
-- day's list through the target source below, and flips the campaign to ACTIVE itself. It
-- also means a fresh install does not start dialling before anyone has configured a CRM
-- token — with no token the sync finds nobody and the campaign runs over an empty list.
--
-- dial_window_start is the 09:00 in "starts at 09:00": the sweep runs every minute and a
-- DAILY campaign becomes due at the first sweep inside its window. dial_window_end is
-- 18:00 rather than the company's own 23:00 because collections calls at night are how a
-- company gets complained about, not how it gets paid.
--
-- auto_reset_targets stays false on purpose. Resetting would re-queue yesterday's people;
-- the target source below replaces the list outright, which is what "today's debtors"
-- means. daily_call_cap is a second belt on top of voice-agent.crm.debtor-import.max-debtors
-- — the import decides how many numbers are fetched, this decides how many are dialled.
--
-- Looked up by name, like the placeholder campaigns above: campaign.name has no unique
-- constraint, so a rerun must not add a second copy.
-- ---------------------------------------------------------------------------
INSERT INTO campaign (company_id, name, type, status, ai_agent_id,
                      max_attempts, retry_interval_minutes, retry_policy)
SELECT 1, 'Qarzdorlik — kunlik', 'DEBT_COLLECTION', 'DRAFT',
       (SELECT id FROM ai_agent WHERE company_id = 1 AND name = 'Qarzdorlik (cascade)' LIMIT 1),
       3, 120,
       -- What the scenario's outcome tools leave behind, answered per disposition: nobody picked up
       -- or hung up — try again later the same day; "I already paid" (recordPaymentClaim) — three
       -- days off while the company checks it. A promise (PROMISE_TO_PAY) and a requested callback
       -- (scheduleCallback) need no rule here: the first closes the target, the second is re-dialled
       -- at the time the client named.
       '{"NO_ANSWER": {"maxAttempts": 3, "delayMinutes": 180}, "VOICEMAIL": {"maxAttempts": 1, "delayMinutes": 120}, "HUNG_UP": {"maxAttempts": 2, "delayMinutes": 60}, "PAYMENT_CLAIMED": {"maxAttempts": 2, "delayMinutes": 4320}}'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM campaign WHERE company_id = 1 AND name = 'Qarzdorlik — kunlik');

INSERT INTO campaign_dialer_config (campaign_id, max_concurrent_calls, daily_call_cap)
SELECT id, 10, 200 FROM campaign WHERE company_id = 1 AND name = 'Qarzdorlik — kunlik'
ON CONFLICT (campaign_id) DO UPDATE SET max_concurrent_calls = EXCLUDED.max_concurrent_calls, daily_call_cap = EXCLUDED.daily_call_cap;

INSERT INTO campaign_schedule (campaign_id, dial_window_start, dial_window_end, recurrence_type, auto_reset_targets)
SELECT id, '09:00:00', '18:00:00', 'DAILY', false FROM campaign WHERE company_id = 1 AND name = 'Qarzdorlik — kunlik'
ON CONFLICT (campaign_id) DO UPDATE SET dial_window_start = EXCLUDED.dial_window_start, dial_window_end = EXCLUDED.dial_window_end, recurrence_type = EXCLUDED.recurrence_type;

-- Every day, including the weekend, because that is what was asked for. Drop SATURDAY and
-- SUNDAY from this list to leave the weekend alone; the campaign then simply is not due on
-- those days and picks up again on Monday.
INSERT INTO campaign_dial_day (campaign_id, day)
SELECT id, day FROM campaign,
     unnest(ARRAY['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']) AS day
WHERE company_id = 1 AND name = 'Qarzdorlik — kunlik'
ON CONFLICT (campaign_id, day) DO NOTHING;

-- Where that campaign's list comes from: Uysot, read through the three-call chain
-- UysotDebtorSourceAdapter documents. url and every other request column stay NULL — a
-- UYSOT_DEBTORS source has no request of its own to describe, and reads voice-agent.crm.*
-- instead, so switching between the prod and dev hosts is one environment variable.
--
-- replace_targets: the sync answers with the whole of today's list, so yesterday's is
-- cleared — but only once the CRM has answered, so a failed fetch leaves the campaign with
-- the list it already had rather than with nothing.
INSERT INTO campaign_target_source (campaign_id, provider, replace_targets, sync_on_recurrence, enabled)
SELECT id, 'UYSOT_DEBTORS', true, true, true
FROM campaign WHERE company_id = 1 AND name = 'Qarzdorlik — kunlik'
ON CONFLICT (campaign_id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- A worked CHAINED target source, so the shape is visible without reading the code.
--
-- The campaign above reads Uysot through a provider compiled into the platform. Every
-- other CRM splits the same problem the same way — the endpoint that knows who to call is
-- not the endpoint that knows their number — and CHAINED is that chain as configuration,
-- settable from the campaign screen for any service. This row is the Uysot chain written
-- out that way: a company pointing at their own API copies it, changes the three URLs and
-- the paths in `extract`, and needs nothing from us.
--
-- Deliberately inert. The campaign is DRAFT with recurrence ONCE, which
-- RecurringCampaignScheduler never picks up, and the source is enabled = false, so no
-- sweep touches it. The host is example.uz and resolves nowhere, so even a hand-run
-- POST /api/campaigns/{id}/targets/sync fails on the list step and — by design — leaves
-- the campaign's targets exactly as they were.
--
-- Read it alongside docs/api/campaigns.md: `{{page}}` counts up while `paginate` is set,
-- `{{contractId}}` and `{{leadId}}` come from the `extract` of an earlier step, `filters`
-- is what stops the two per-row requests from being spent on clients who owe nothing, and
-- whichever collected variable is not named as the phone, the client id or the language
-- becomes one of the target's facts.
-- ---------------------------------------------------------------------------
INSERT INTO campaign (company_id, name, type, status, ai_agent_id)
SELECT 1, 'Namuna — CHAINED manba', 'DEBT_COLLECTION', 'DRAFT',
       (SELECT id FROM ai_agent WHERE company_id = 1 AND name = 'Qarzdorlik (cascade)' LIMIT 1)
WHERE NOT EXISTS (SELECT 1 FROM campaign WHERE company_id = 1 AND name = 'Namuna — CHAINED manba');

INSERT INTO campaign_schedule (campaign_id, dial_window_start, dial_window_end, recurrence_type)
SELECT id, '09:00:00', '18:00:00', 'ONCE' FROM campaign WHERE company_id = 1 AND name = 'Namuna — CHAINED manba'
ON CONFLICT (campaign_id) DO UPDATE SET dial_window_start = EXCLUDED.dial_window_start, dial_window_end = EXCLUDED.dial_window_end, recurrence_type = EXCLUDED.recurrence_type;

INSERT INTO campaign_target_source (campaign_id, provider, phone_field, client_id_field,
                                    auth_header_name, replace_targets, sync_on_recurrence, enabled, steps)
SELECT id, 'CHAINED', 'phone', 'leadId', 'X-Open-Api-Token', true, true, false,
       '[
          {
            "name": "contracts",
            "method": "POST",
            "url": "https://crm.example.uz/v1/contract/filter",
            "body": "{\"page\":{{page}},\"size\":100,\"statuses\":[\"ACTIVE\"]}",
            "itemsPath": "data.data",
            "paginate": true,
            "filters": [
              {"path": "delay",   "operator": "GTE", "value": "1"},
              {"path": "residue", "operator": "GT",  "value": "0"}
            ],
            "extract": {
              "contractId": "id",
              "clientName": "client.name",
              "debtAmount": "residue",
              "currency": "currency.ccy",
              "contractNumber": "number"
            }
          },
          {
            "name": "contract",
            "method": "GET",
            "url": "https://crm.example.uz/v1/contract/{{contractId}}",
            "extract": {"leadId": "data.lead.id"}
          },
          {
            "name": "lead",
            "method": "GET",
            "url": "https://crm.example.uz/v1/lead/{{leadId}}",
            "extract": {"phone": "data.contacts.0.phones.0"}
          }
        ]'::jsonb
FROM campaign WHERE company_id = 1 AND name = 'Namuna — CHAINED manba'
ON CONFLICT (campaign_id) DO NOTHING;

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
-- No campaign here can dial a real number on its own. Every ACTIVE or SCHEDULED campaign's
-- PENDING targets are due 30 days out; the numbers are made up and may still belong to
-- somebody. The one campaign meant to be started — "Softfon test" — dials 600, the local
-- softphone (voice-agent.asterisk.local-number-pattern), never the trunk.
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
-- Agents of every shape, queues, tools, MCP, widgets.
--
-- Provider columns name engines that exist only once their API key is set (speech.yml):
-- an agent on gemini / openai / aisha speech needs that provider configured, or its call
-- fails at start. The three agents at the top of this file need only Yandex and Gemini.
-- ---------------------------------------------------------------------------
DO $seed$
DECLARE
    v_reception BIGINT;
    v_debt      BIGINT;
    v_sales     BIGINT;
    v_survey    BIGINT;
    v_reminder  BIGINT;
    v_order     BIGINT;
    v_notify    BIGINT;
    v_night     BIGINT;
    v_prompt    BIGINT;
    v_embedded  BIGINT;
    v_onboard   BIGINT;
    v_sup       BIGINT;
    v_op1       BIGINT;
    v_op2       BIGINT;
    v_op3       BIGINT;
    v_q_support BIGINT;
    v_q_sales   BIGINT;
    v_q_vip     BIGINT;
    v_t_order   BIGINT;
    v_t_balance BIGINT;
    v_t_booking BIGINT;
    v_mcp       BIGINT;
    v_theme     JSONB;
BEGIN
    IF EXISTS (SELECT 1 FROM ai_agent WHERE company_id = 1 AND name = 'Sotuv menejeri') THEN
        RETURN;
    END IF;

    SELECT id INTO v_reception FROM ai_agent WHERE company_id = 1 AND name = 'Kiruvchi qabulxona';
    SELECT id INTO v_debt FROM ai_agent WHERE company_id = 1 AND name = 'Qarzdorlik (cascade)';
    SELECT id INTO v_sup FROM app_user WHERE username = 'supervisor';
    SELECT id INTO v_op1 FROM app_user WHERE username = 'operator1';
    SELECT id INTO v_op2 FROM app_user WHERE username = 'operator2';
    SELECT id INTO v_op3 FROM app_user WHERE username = 'operator3';

    -- Outbound sales on Gemini end to end: Gemini recognition, Gemini TTS, a lite model for
    -- short turns. Everything optional is filled in, so this is the agent to open to see
    -- every field of the form with a value in it.
    INSERT INTO ai_agent (company_id, name, description, scenario_id, scenario_mode, language, persona,
                          pipeline_mode, stt_provider, stt_model, tts_provider, tts_model, tts_voice,
                          llm_model, fast_llm_model, temperature, max_output_tokens,
                          interruption_sensitivity, voicemail_action, voicemail_message,
                          transfer_message, post_call_actions, data_needed, data_evaluation,
                          max_conversation_duration_seconds, concurrent_calls_limit, daily_calls_limit, created_by)
    VALUES (1, 'Sotuv menejeri', 'Chiquvchi sotuv: qiziqish, byudjet va uchrashuv vaqtini aniqlaydi (Gemini STT/LLM/TTS).',
            (SELECT id FROM scenario WHERE scenario_key = 'lead-qualification' AND is_active),
            'STRUCTURED_STEPS', 'uz-UZ', 'AI_ASSISTANT',
            'CASCADE', 'gemini', 'gemini-3.5-transcribe-live', 'gemini', 'gemini-3.8-flash-tts', 'gemini-tts-kore-uz',
            'gemini-3.8-flash', 'gemini-3.5-flash-lite', 0.4, 250,
            'HIGH', 'LEAVE_MESSAGE',
            'Assalomu alaykum. Siz qiziqqan xizmat bo''yicha qo''ng''iroq qildik. Qulay vaqtda qayta bog''laning.',
            'Sizni sotuv bo''limi mutaxassisiga ulayman.',
            $j$[{"id": "sms-demo", "name": "Demo eslatma SMS", "actionType": "SEND_SMS", "triggerDisposition": "COMPLETED",
                 "onlyOnSuccess": true, "template": "{{client_name}}, demo uchrashuvingiz tasdiqlandi. Uysot jamoasi.",
                 "webhookUrl": null, "headers": {}, "enabled": false}]$j$::jsonb,
            $j$[{"id": "budget", "type": "Number", "name": "Byudjet", "description": "Mijoz aytgan taxminiy byudjet, so'mda"},
                {"id": "decision_maker", "type": "Boolean", "name": "Qaror qabul qiluvchi", "description": "Suhbatdosh qarorni o'zi qabul qiladimi"}]$j$::jsonb,
            $j$[{"id": "needs_follow_up", "name": "Qayta aloqa kerak", "criteria": "Mijoz qo'shimcha ma'lumot yoki qayta qo'ng'iroq so'radimi?"}]$j$::jsonb,
            300, 5, 300, v_sup)
    RETURNING id INTO v_sales;
    INSERT INTO ai_agent_ambience (ai_agent_id, ambient_sound, ambient_sound_volume, thinking_sound, thinking_sound_volume)
    VALUES (v_sales, 'CALL_CENTER', 0.8, 'KEYBOARD_TYPING', 0.6)
    ON CONFLICT (ai_agent_id) DO NOTHING;
    INSERT INTO ai_agent_pronunciation_rule (ai_agent_id, word, replacement, case_sensitive, language)
    VALUES (v_sales, 'MChJ', 'mas''uliyati cheklangan jamiyat', false, 'uz-UZ'),
           (v_sales, 'CRM', 'si-ar-em', true, '*');

    -- A short survey, Yandex recognition with Aisha's cheerful Uzbek voice.
    INSERT INTO ai_agent (company_id, name, description, scenario_id, scenario_mode, language, first_message,
                          pipeline_mode, stt_provider, tts_provider, tts_voice, llm_model,
                          emotion_adaptive_voice, voicemail_action, created_by)
    VALUES (1, 'So''rovnoma (NPS)', 'Xizmat sifati bo''yicha 3 savollik so''rovnoma (Yandex STT + Aisha TTS).',
            (SELECT id FROM scenario WHERE scenario_key = 'survey' AND is_active),
            'STRUCTURED_STEPS', 'uz-UZ',
            'Assalomu alaykum! Xizmatimiz haqida bir daqiqalik so''rovnomada qatnashishga rozimisiz?',
            'CASCADE', 'yandex', 'aisha', 'gulnoza-cheerful', 'gemini-3.6-flash',
            false, 'HANGUP', v_sup)
    RETURNING id INTO v_survey;
    INSERT INTO ai_agent_ambience (ai_agent_id, ambient_sound)
    VALUES (v_survey, 'OFF')
    ON CONFLICT (ai_agent_id) DO NOTHING;

    -- Russian first, Uzbek when the call is in Uzbek; each language at its own pace.
    INSERT INTO ai_agent (company_id, name, description, scenario_id, scenario_mode, language, first_message,
                          pipeline_mode, stt_provider, tts_provider, tts_voice, llm_model,
                          voice_speed, voice_speed_per_language, interruption_sensitivity,
                          voicemail_action, voicemail_message, created_by)
    VALUES (1, 'Uchrashuv eslatmasi (ru)', 'Uchrashuvni rus tilida eslatadi, tasdiqlash yoki ko''chirishni yozib oladi.',
            (SELECT id FROM scenario WHERE scenario_key = 'appointment-reminder' AND is_active),
            'STRUCTURED_STEPS', 'ru-RU',
            'Здравствуйте! Напоминаем о вашей записи. Удобно сейчас говорить?',
            'CASCADE', 'yandex', 'yandex', 'alena', 'gemini-3.8-flash',
            1.1, '{"ru-RU": 1.1, "uz-UZ": 0.95}'::jsonb, 'LOW',
            'LEAVE_MESSAGE',
            'Здравствуйте. Напоминаем о вашей записи. Пожалуйста, перезвоните нам для подтверждения.', v_sup)
    RETURNING id INTO v_reminder;
    INSERT INTO ai_agent_ambience (ai_agent_id, ambient_sound, noise_cancellation_mode)
    VALUES (v_reminder, 'NATURAL_LINE', 'VOICE_ISOLATION')
    ON CONFLICT (ai_agent_id) DO NOTHING;

    -- OpenAI recognition and synthesis around a Gemini turn.
    INSERT INTO ai_agent (company_id, name, description, scenario_id, scenario_mode, language,
                          pipeline_mode, stt_provider, stt_model, tts_provider, tts_model, tts_voice, llm_model,
                          voicemail_action, created_by)
    VALUES (1, 'Buyurtma tasdiqlash (OpenAI ovoz)', 'Do''kon buyurtmasini, manzil va vaqtni tasdiqlaydi (OpenAI STT/TTS, Gemini LLM).',
            (SELECT id FROM scenario WHERE scenario_key = 'order-confirmation' AND is_active),
            'STRUCTURED_STEPS', 'uz-UZ',
            'CASCADE', 'openai', 'gpt-live-transcribe', 'openai', 'gpt-4o-mini-tts', 'openai-nova-uz', 'gemini-3.8-flash',
            'HANGUP', v_sup)
    RETURNING id INTO v_order;
    INSERT INTO ai_agent_ambience (ai_agent_id, ambient_sound)
    VALUES (v_order, 'NATURAL_LINE')
    ON CONFLICT (ai_agent_id) DO NOTHING;

    -- Speech-to-speech on OpenAI's realtime engine, facts fetched by tool rather than
    -- written into its instructions. No tts_voice: the catalog has no openai-realtime voice.
    INSERT INTO ai_agent (company_id, name, description, scenario_id, scenario_mode, language,
                          pipeline_mode, realtime_provider, llm_model, realtime_facts_delivery,
                          voicemail_action, created_by)
    VALUES (1, 'Bildirishnoma (OpenAI realtime)', 'Bir tomonlama xabar yetkazadi: OpenAI Realtime, faktlar tool orqali.',
            (SELECT id FROM scenario WHERE scenario_key = 'notification' AND is_active),
            'STRUCTURED_STEPS', 'uz-UZ',
            'REALTIME', 'openai-realtime', 'gpt-realtime-2.1', 'TOOL',
            'HANGUP', v_sup)
    RETURNING id INTO v_notify;
    INSERT INTO ai_agent_ambience (ai_agent_id, ambient_sound)
    VALUES (v_notify, 'OFF')
    ON CONFLICT (ai_agent_id) DO NOTHING;

    -- After hours, presenting as a person: allowed only because company_config says so,
    -- and it skips the automated-call disclosure — which is exactly what to test.
    INSERT INTO ai_agent (company_id, name, description, scenario_id, scenario_mode, language, persona, first_message,
                          pipeline_mode, stt_provider, tts_provider, tts_voice, llm_model,
                          voicemail_action, transfer_message, created_by)
    VALUES (1, 'Kechki navbatchi', 'Ish vaqtidan keyin kiruvchi qo''ng''iroqqa javob beradi va qayta qo''ng''iroq vaqtini yozib oladi (HUMAN_LIKE).',
            (SELECT id FROM scenario WHERE scenario_key = 'callback-request' AND is_active),
            'STRUCTURED_STEPS', 'uz-UZ', 'HUMAN_LIKE',
            'Assalomu alaykum, men Nilufar, navbatchi yordamchiman. Sizga qanday yordam bera olaman?',
            'CASCADE', 'yandex', 'yandex', 'yulduz', 'gemini-3.8-flash',
            'IGNORE', 'Hozir navbatchi mutaxassisga ulayman.', v_sup)
    RETURNING id INTO v_night;
    INSERT INTO ai_agent_ambience (ai_agent_id, ambient_sound)
    VALUES (v_night, 'OFFICE')
    ON CONFLICT (ai_agent_id) DO NOTHING;

    -- PROMPT mode: no scenario at all, the system prompt is the whole script. Engines left
    -- NULL run on whatever speech.yml / the company settings say; RAG is on.
    INSERT INTO ai_agent (company_id, name, description, scenario_mode, template_id, language,
                          system_prompt, first_message, use_rag, pipeline_mode, created_by)
    VALUES (1, 'Erkin suhbat (PROMPT rejimi)', 'Ssenariysiz, faqat system prompt bilan ishlaydigan yordamchi; bilim bazasidan qidiradi.',
            'PROMPT', 'SUPPORT', 'uz-UZ',
            'Siz Uysot kompaniyasining qo''llab-quvvatlash yordamchisisiz. Qisqa va aniq javob bering, bir javobda ikki gapdan oshmang. '
            || 'Bilmagan savolingizga javob to''qimang — bilim bazasidan qidiring, topilmasa operatorga ulashni taklif qiling. '
            || 'Narx, chegirma yoki qarz summasi haqida o''zingizdan hech narsa aytmang.',
            'Assalomu alaykum! Savolingizni bering.', true, 'CASCADE', v_sup)
    RETURNING id INTO v_prompt;

    -- STRUCTURED_STEPS with the definition embedded in the agent instead of a scenario row.
    INSERT INTO ai_agent (company_id, name, description, scenario_mode, scenario_definition, template_id, language,
                          pipeline_mode, stt_provider, tts_provider, tts_voice, created_by)
    VALUES (1, 'Yetkazib berish holati (ichki ssenariy)', 'Ssenariy agentning o''zida saqlangan (scenario_definition): buyurtma holatini aytadi.',
            'STRUCTURED_STEPS',
$j${"stages": [
  {"id": "GREETING", "purpose": "Salomlashish va buyurtma raqamini so'rash", "allowedTransitions": ["STATUS", "END_CALL"]},
  {"id": "STATUS", "purpose": "buyurtma_holati tool'i bilan holatni tekshirib aytish", "allowedTransitions": ["CLOSING", "ESCALATE_TO_HUMAN", "END_CALL"]},
  {"id": "CLOSING", "purpose": "Xayrlashish", "allowedTransitions": ["END_CALL"]},
  {"id": "ESCALATE_TO_HUMAN", "purpose": "Operatorga uzatish", "allowedTransitions": ["END_CALL"]},
  {"id": "END_CALL", "purpose": "Qo'ng'iroqni tugatish", "allowedTransitions": []}
 ],
 "factSchema": [{"name": "clientName", "type": "string", "required": false}],
 "tools": [],
 "outcomeSchema": [{"name": "orderStatus", "type": "string", "description": "Mijozga aytilgan buyurtma holati"}],
 "rolePrompt": "Siz internet-do'konning yetkazib berish bo'yicha yordamchisisiz.",
 "guardrails": ["Yetkazish sanasini tool javobisiz aytmang"]}$j$::jsonb,
            'BUSINESS', 'uz-UZ', 'CASCADE', 'yandex', 'yandex', 'nigora', v_sup)
    RETURNING id INTO v_embedded;

    -- Disabled, and the only agent carrying webhooks and post-call actions: they point at
    -- example.uz, so they are here to be looked at, not to fire after every test call.
    INSERT INTO ai_agent (company_id, name, description, scenario_id, scenario_mode, language,
                          pipeline_mode, stt_provider, tts_provider, tts_voice, llm_model,
                          dtmf_input_enabled, mid_call_sms_enabled, mid_call_sms_template,
                          zero_pii_retention, store_call_audio, conversation_retention_days,
                          silence_end_call_timeout_seconds,
                          initiation_webhook, post_call_webhook, post_call_actions, enabled, created_by)
    VALUES (1, 'Onboarding (o''chirilgan)', 'Yangi mijozni tabriklaydi va demo taklif qiladi. O''chirilgan: webhook va post-call amallar namunasi.',
            (SELECT id FROM scenario WHERE scenario_key = 'welcome-onboarding' AND is_active),
            'STRUCTURED_STEPS', 'uz-UZ',
            'CASCADE', 'yandex', 'yandex', 'zamira', 'gemini-3.6-flash',
            false, true, 'Video qo''llanma: https://uysot.uz/guide — {{client_name}}, savollaringiz bo''lsa yozing.',
            true, false, 7,
            20,
            $j${"webhookUrl": "https://crm.example.uz/voice/initiate", "method": "POST", "headers": {},
                "dynamicVariables": {"source": "voice-agent"}, "transcript": null, "audioUrl": null}$j$::jsonb,
            $j${"webhookUrl": "https://hooks.example.uz/call-finished", "method": "POST",
                "headers": {"X-Api-Key": "{{secrets.CRM_API_TOKEN}}"}, "dynamicVariables": {},
                "transcript": true, "audioUrl": false}$j$::jsonb,
            $j$[{"id": "sms-thanks", "name": "Rahmat SMS", "actionType": "SEND_SMS", "triggerDisposition": "COMPLETED",
                 "onlyOnSuccess": true, "template": "{{client_name}}, qo'ng'iroq uchun rahmat!", "webhookUrl": null,
                 "headers": {}, "enabled": true},
                {"id": "tg-summary", "name": "Telegram xulosa", "actionType": "TELEGRAM", "triggerDisposition": null,
                 "onlyOnSuccess": false, "template": "{{summary}}", "webhookUrl": null, "headers": {}, "enabled": true},
                {"id": "crm-hook", "name": "CRM webhook", "actionType": "WEBHOOK", "triggerDisposition": "CALLBACK_REQUESTED",
                 "onlyOnSuccess": false, "template": null, "webhookUrl": "https://crm.example.uz/webhooks/callback",
                 "headers": {"X-Api-Key": "{{secrets.CRM_API_TOKEN}}"}, "enabled": true}]$j$::jsonb,
            false, v_sup)
    RETURNING id INTO v_onboard;

    INSERT INTO ai_agent_voice (ai_agent_id, language, voice_id) VALUES
        (v_sales,    'uz-UZ', 'gemini-tts-kore-uz'),
        (v_sales,    'ru-RU', 'gemini-tts-kore-ru'),
        (v_survey,   'uz-UZ', 'gulnoza-cheerful'),
        (v_reminder, 'ru-RU', 'alena'),
        (v_reminder, 'uz-UZ', 'zamira'),
        (v_order,    'uz-UZ', 'openai-nova-uz'),
        (v_order,    'ru-RU', 'openai-nova-ru'),
        (v_night,    'uz-UZ', 'yulduz'),
        (v_night,    'ru-RU', 'jane');

    -- Queues. overflow targets are typed foreign keys and external phone.
    INSERT INTO call_queue (company_id, name, strategy, ring_timeout_sec, wrap_up_sec, max_wait_sec, max_length,
                            announce_position_every_sec, announce_wait_time, periodic_announce_text,
                            periodic_announce_every_sec, callback_offer_after_sec, overflow_action, overflow_voicemail_id,
                            service_level_sec, post_call_survey, waiting_agent_id)
    VALUES (1, 'Qo''llab-quvvatlash', 'LEAST_RECENT', 15, 10, 300, 20, 60, true,
            'Qo''ng''iroqingiz biz uchun muhim. Birinchi bo''sh operator sizga javob beradi.', 90, 120,
            'VOICEMAIL', (SELECT id FROM voicemail_box WHERE company_id = 1 AND name = 'Umumiy ovozli pochta'),
            20, true, v_reception)
    RETURNING id INTO v_q_support;

    INSERT INTO call_queue (company_id, name, strategy, ring_timeout_sec, wrap_up_sec, max_wait_sec,
                            announce_position_every_sec, overflow_action, overflow_agent_id, service_level_sec, sticky_seconds)
    VALUES (1, 'Sotuv', 'SKILL_BASED', 20, 15, 180, 45, 'AI_AGENT', v_sales, 30, 86400)
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

    UPDATE ai_agent SET transfer_queue_id = v_q_support WHERE id IN (v_reception, v_debt, v_night, v_prompt);
    UPDATE ai_agent SET transfer_queue_id = v_q_sales WHERE id = v_sales;

    -- HTTP tools. The hosts are example.uz: a test call that uses one hears the tool fail,
    -- which is also worth testing.
    INSERT INTO tool (company_id, name, description, api_url, api_method, api_headers, api_path_params,
                      response_timeout_secs, pre_tool_speech, response_mapping, retry_attempts, max_calls_per_call,
                      effect)
    VALUES (1, 'buyurtma_holati', 'Buyurtma raqami bo''yicha yetkazib berish holatini qaytaradi.',
            'https://api.example.uz/v1/orders/{order_id}', 'GET',
            $j$[{"key": "Authorization", "value": "Bearer {{secrets.CRM_API_TOKEN}}", "type": "string", "redacted": true}]$j$::jsonb,
            $j$[{"name": "order_id", "type": "string", "valueType": "LLM Prompt", "value": null,
                 "description": "Buyurtma raqami, mijoz aytgan ko'rinishda", "allowedValues": [], "required": true}]$j$::jsonb,
            8, 'Bir soniya, buyurtmangizni tekshiryapman.',
            '{"status": "$.data.status", "eta": "$.data.eta"}'::jsonb, 1, 3, 'READ')
    RETURNING id INTO v_t_order;

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
            10, true, 'Hisobingizni tekshirib ko''raman.', 0, 2, 'READ')
    RETURNING id INTO v_t_balance;

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
            10, false, 0, 2, 'WRITE', true, '{{date}} kuni soat {{time}} ga filialga yozib qo''yaymi?')
    RETURNING id INTO v_t_booking;

    INSERT INTO ai_agent_tool (ai_agent_id, tool_id) VALUES
        (v_sales, v_t_booking), (v_sales, v_t_order),
        (v_debt, v_t_balance),
        (v_reception, v_t_order), (v_reception, v_t_balance),
        (v_embedded, v_t_order);

    INSERT INTO mcp_connection (company_id, name, url, auth_type, secret_key, status, allow_writes)
    VALUES (1, 'Ichki CRM MCP', 'https://mcp.example.uz/mcp', 'BEARER', 'MCP_BEARER_TOKEN', 'PENDING', false)
    RETURNING id INTO v_mcp;
    INSERT INTO mcp_connection (company_id, name, url, auth_type, status, last_error)
    VALUES (1, 'Hujjatlar MCP (o''chiq)', 'https://docs-mcp.example.uz/mcp', 'NONE', 'DISABLED', NULL);
    INSERT INTO ai_agent_mcp_connection (ai_agent_id, mcp_connection_id) VALUES (v_sales, v_mcp);

    v_theme := $j${"primaryColor": "#002FA7", "accentColor": "#0F172A", "surfaceColor": "#FFFFFF", "textColor": "#111827",
        "mutedTextColor": "#667085", "buttonTextColor": "#FFFFFF", "borderColor": "#DADDE3", "position": "bottom-right",
        "launcherSize": "comfortable", "panelWidth": 340, "borderRadius": 16, "defaultOpen": false, "showAvatar": true,
        "avatarImageUrl": null, "avatarOrbColor1": "#002FA7", "avatarOrbColor2": "#00F0FF", "brandName": "Uysot yordamchi",
        "actionText": "Biz bilan gaplashing", "welcomeText": "Savolingizni ovozli bering.",
        "startButtonText": "Qo'ng'iroqni boshlash", "endButtonText": "Tugatish", "connectingText": "Ulanmoqda",
        "listeningText": "Tinglayapman", "speakingText": "Javob bermoqda", "endedText": "Qo'ng'iroq tugadi",
        "whiteLabel": false}$j$::jsonb;

    INSERT INTO agent_widget (widget_key, company_id, agent_id, name, enabled, allowed_origins, theme, consent_required, consent_text)
    VALUES ('wgt_seedreception01', 1, v_reception, 'Sayt vidjeti', true,
            '["http://localhost:5173", "http://localhost:3000", "https://uysot.uz"]'::jsonb, v_theme, true,
            'Suhbat sifat nazorati uchun yozib olinadi. Davom etish orqali rozilik bildirasiz.');
    INSERT INTO agent_widget (widget_key, company_id, agent_id, name, enabled, allowed_origins, theme, consent_required)
    VALUES ('wgt_seedsales000002', 1, v_sales, 'Sotuv vidjeti (o''chiq)', false,
            '["https://shop.example.uz"]'::jsonb,
            v_theme || '{"primaryColor": "#0F766E", "position": "bottom-left", "brandName": "Sotuv bo''limi", "defaultOpen": true}'::jsonb,
            false);

    -- Knowledge only the sales agent answers from, and a switched-off company-wide item.
    INSERT INTO knowledge_base_item (company_id, agent_id, item_key, topic, title, keywords, is_active) VALUES
        (1, v_sales, 'sales_pricing', 'pricing', 'Tariflar', 'narx,tarif,qancha,цена,тариф,сколько,price,plan', true),
        (1, v_sales, 'sales_demo', 'sales', 'Demo uchrashuv', 'demo,taqdimot,ko''rsatib bering,демо,презентация,demo', true),
        (1, NULL, 'old_promo', 'general', 'Yozgi aksiya (tugagan)', 'aksiya,chegirma,акция,скидка,promo', false)
    ON CONFLICT (company_id, item_key) DO NOTHING;

    INSERT INTO knowledge_item_translation (item_id, language, answer)
    SELECT k.id, t.lang, t.ans
    FROM (
        VALUES
        ('sales_pricing', 'uz', 'Uchta tarif bor: Start — oyiga 1 000 daqiqa, Pro — 5 000 daqiqa, Enterprise — kelishuv asosida. Aniq narxni menejer taklifnomada yuboradi.'),
        ('sales_pricing', 'ru', 'Есть три тарифа: Start — 1 000 минут в месяц, Pro — 5 000 минут, Enterprise — по договорённости. Точную цену менеджер пришлёт в предложении.'),
        ('sales_pricing', 'en', 'There are three plans: Start (1,000 minutes a month), Pro (5,000 minutes) and Enterprise (by agreement). The manager sends the exact price in a proposal.'),

        ('sales_demo', 'uz', 'Demo 30 daqiqa davom etadi, onlayn yoki ofisimizda. Qulay kun va vaqtni aytsangiz, yozib qo''yaman.'),
        ('sales_demo', 'ru', 'Демо длится 30 минут, онлайн или у нас в офисе. Назовите удобный день и время — я запишу.'),
        ('sales_demo', 'en', 'The demo takes 30 minutes, online or at our office. Tell me a convenient day and time and I will book it.'),

        ('old_promo', 'uz', 'Yozgi aksiya 31-avgustda tugagan.'),
        ('old_promo', 'ru', 'Летняя акция завершилась 31 августа.'),
        ('old_promo', 'en', 'The summer promotion ended on 31 August.')
    ) AS t(key, lang, ans)
    JOIN knowledge_base_item k ON k.company_id = 1 AND k.item_key = t.key
    ON CONFLICT (item_id, language) DO NOTHING;
END
$seed$;

-- ---------------------------------------------------------------------------
-- Contacts, opt-outs, client memory, campaigns in every status, A/B variants, targets
-- ---------------------------------------------------------------------------
DO $seed$
DECLARE
    v_admin    BIGINT;
    v_debt     BIGINT;
    v_debt_rt  BIGINT;
    v_survey   BIGINT;
    v_reminder BIGINT;
    v_notify   BIGINT;
    v_night    BIGINT;
    v_camp     BIGINT;
    v_var_a    BIGINT;
    v_var_b    BIGINT;
    v_callback BIGINT;
    v_later    CONSTANT TIMESTAMPTZ := now() + interval '30 days';
BEGIN
    IF EXISTS (SELECT 1 FROM campaign WHERE company_id = 1 AND name = 'Qarzdorlik — sentabr') THEN
        RETURN;
    END IF;

    SELECT id INTO v_admin FROM app_user WHERE username = 'admin';
    SELECT id INTO v_debt FROM ai_agent WHERE company_id = 1 AND name = 'Qarzdorlik (cascade)';
    SELECT id INTO v_debt_rt FROM ai_agent WHERE company_id = 1 AND name = 'Qarzdorlik (realtime)';
    SELECT id INTO v_survey FROM ai_agent WHERE company_id = 1 AND name = 'So''rovnoma (NPS)';
    SELECT id INTO v_reminder FROM ai_agent WHERE company_id = 1 AND name = 'Uchrashuv eslatmasi (ru)';
    SELECT id INTO v_notify FROM ai_agent WHERE company_id = 1 AND name = 'Bildirishnoma (OpenAI realtime)';
    SELECT id INTO v_night FROM ai_agent WHERE company_id = 1 AND name = 'Kechki navbatchi';

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
        (1, 'Softfon (test)',      '600',           NULL,                             'Mahalliy softfon: "Softfon test" kampaniyasi shu raqamga qo''ng''iroq qiladi.', now() - interval '1 day');

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

    -- ACTIVE, split A/B between the cascade pipeline and the realtime one. The targets still
    -- waiting are due 30 days out, so this campaign is live on every screen without dialling.
    INSERT INTO campaign (company_id, ai_agent_id, name, type, status,
                          max_attempts, retry_interval_minutes, priority, retry_policy,
                          last_run_at, created_at, created_by)
    VALUES (1, v_debt, 'Qarzdorlik — sentabr', 'DEBT_COLLECTION', 'ACTIVE',
            3, 120, 4,
            '{"NO_ANSWER": {"maxAttempts": 3, "delayMinutes": 180}, "VOICEMAIL": {"maxAttempts": 1, "delayMinutes": 120}, "HUNG_UP": {"maxAttempts": 2, "delayMinutes": 60}}'::jsonb,
            now() - interval '1 day', now() - interval '6 days', v_admin)
    RETURNING id INTO v_camp;
    INSERT INTO campaign_dialer_config (campaign_id, max_concurrent_calls, daily_call_cap, budget_uzs, daily_budget_uzs)
    VALUES (v_camp, 10, 150, 2000000, 300000)
    ON CONFLICT (campaign_id) DO NOTHING;
    INSERT INTO campaign_schedule (campaign_id, dial_window_start, dial_window_end)
    VALUES (v_camp, '09:00', '19:00')
    ON CONFLICT (campaign_id) DO NOTHING;
    INSERT INTO campaign_dial_day (campaign_id, day)
    SELECT v_camp, d FROM unnest(ARRAY['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY']) AS d;

    INSERT INTO campaign_variant (campaign_id, company_id, name, ai_agent_id, prompt_override, tts_voice_id, pipeline_mode,
                                  traffic_weight, calls_count, answered_count, converted_count, is_active)
    VALUES (v_camp, 1, 'A — cascade', v_debt, NULL, NULL, NULL, 50, 7, 4, 1, true)
    RETURNING id INTO v_var_a;
    INSERT INTO campaign_variant (campaign_id, company_id, name, ai_agent_id, prompt_override, tts_voice_id, pipeline_mode,
                                  traffic_weight, calls_count, answered_count, converted_count, is_active)
    VALUES (v_camp, 1, 'B — realtime', v_debt, 'Qisqaroq gapiring: har javob bitta gapdan oshmasin.', 'gemini-aoede-uz', 'REALTIME',
            50, 8, 3, 0, true)
    RETURNING id INTO v_var_b;

    INSERT INTO campaign_target (company_id, campaign_id, variant_id, client_id, phone, language, context_data, status,
                                 attempts, next_attempt_at, do_not_call, created_at)
    SELECT 1, v_camp, CASE t.variant WHEN 'A' THEN v_var_a WHEN 'B' THEN v_var_b END, c.id, c.phone, t.lang,
           jsonb_build_object('clientName', t.client_name, 'debtAmount', t.amount, 'currency', 'UZS',
                              'dueDate', t.due, 'contractNumber', t.contract),
           t.status, t.attempts, CASE WHEN t.status = 'PENDING' THEN v_later END, t.dnc, now() - interval '6 days'
    FROM (VALUES
        ('+998901230001', 'A', 'uz-UZ', 'Aziz aka',        1250000, '2026-09-01', 'SH-2025-0141', 'DONE',      1, false),
        ('+998901230002', 'B', 'uz-UZ', 'Dilnoza opa',      840000, '2026-08-25', 'SH-2025-0142', 'DONE',      1, false),
        ('+998901230003', 'A', 'uz-UZ', 'Bekzod aka',      2100000, '2026-08-20', 'SH-2025-0143', 'PENDING',   1, false),
        ('+998901230004', 'B', 'uz-UZ', 'Gulnora opa',      560000, '2026-09-05', 'SH-2025-0144', 'EXHAUSTED', 1, false),
        ('+998901230005', 'A', 'uz-UZ', 'Sherzod aka',      990000, '2026-08-15', 'SH-2025-0145', 'FAILED',    1, false),
        ('+998901230006', 'B', 'uz-UZ', 'Kamola opa',      1500000, '2026-09-03', 'SH-2025-0146', 'DONE',      1, false),
        ('+998901230007', 'A', 'uz-UZ', 'Rustam aka',      3200000, '2026-07-30', 'SH-2025-0147', 'DONE',      1, false),
        ('+998901230008', 'B', 'uz-UZ', 'Feruza opa',       720000, '2026-09-08', 'SH-2025-0148', 'PENDING',   1, false),
        ('+998901230009', 'A', 'uz-UZ', 'Otabek aka',      1100000, '2026-08-28', 'SH-2025-0149', 'DONE',      1, true),
        ('+998901230010', 'B', 'uz-UZ', 'Zarina opa',       450000, '2026-09-10', 'SH-2025-0150', 'PENDING',   1, false),
        ('+998901230011', 'A', 'uz-UZ', 'Ulug''bek aka',   1800000, '2026-08-18', 'SH-2025-0151', 'FAILED',    1, false),
        ('+998901230012', 'B', 'uz-UZ', 'Mohira opa',       630000, '2026-08-22', 'SH-2025-0152', 'EXHAUSTED', 3, false),
        ('+998901230013', 'A', 'uz-UZ', 'Jamshid aka',     2750000, '2026-09-12', 'SH-2025-0153', 'PENDING',   0, false),
        ('+998901230014', 'B', 'ru-RU', 'Нигина',           910000, '2026-09-12', 'SH-2025-0154', 'PENDING',   0, false)
    ) AS t(phone, variant, lang, client_name, amount, due, contract, status, attempts, dnc)
    JOIN contact c ON c.company_id = 1 AND c.phone = t.phone;

    -- A GENERIC source as the campaign screen shows it after one sync; disabled, since the
    -- host resolves nowhere.
    INSERT INTO campaign_target_source (campaign_id, provider, url, http_method, auth_header_name, items_path,
                                        phone_field, client_id_field, language_field, replace_targets,
                                        sync_on_recurrence, enabled, last_sync_at, last_sync_added)
    VALUES (v_camp, 'GENERIC', 'https://api.example.uz/v1/debtors', 'GET', 'Authorization', 'data.items',
            'phone', 'id', 'lang', false, false, false, now() - interval '6 days', 14);

    -- PAUSED on the realtime agent; resuming it dials nothing for 30 days.
    INSERT INTO campaign (company_id, ai_agent_id, name, type, status,
                          max_attempts, retry_interval_minutes, created_at, created_by)
    VALUES (1, v_debt_rt, 'Qarzdorlik — realtime test', 'DEBT_COLLECTION', 'PAUSED',
            2, 60, now() - interval '4 days', v_admin)
    RETURNING id INTO v_camp;
    INSERT INTO campaign_dialer_config (campaign_id, max_concurrent_calls)
    VALUES (v_camp, 5) ON CONFLICT (campaign_id) DO NOTHING;
    INSERT INTO campaign_schedule (campaign_id, dial_window_start, dial_window_end)
    VALUES (v_camp, '09:00', '18:00') ON CONFLICT (campaign_id) DO NOTHING;
    INSERT INTO campaign_dial_day (campaign_id, day)
    SELECT v_camp, d FROM unnest(ARRAY['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY']) AS d;
    INSERT INTO campaign_target (company_id, campaign_id, client_id, phone, language, context_data, status, attempts, next_attempt_at)
    SELECT 1, v_camp, c.id, c.phone, 'uz-UZ',
           jsonb_build_object('clientName', t.client_name, 'debtAmount', t.amount, 'currency', 'UZS', 'dueDate', '2026-09-10'),
           'PENDING', 0, v_later
    FROM (VALUES ('+998901230018', 'Akmal aka', 1340000), ('+998901230019', 'Madina opa', 670000),
                 ('+998901230020', 'Anvar aka', 2200000)) AS t(phone, client_name, amount)
    JOIN contact c ON c.company_id = 1 AND c.phone = t.phone;

    -- The one campaign meant to be started: its only target is 600, which the dialer rings
    -- on the local softphone endpoint instead of the trunk — the whole outbound path, free.
    INSERT INTO campaign (company_id, ai_agent_id, name, type, status,
                          max_attempts, retry_interval_minutes, priority, created_at, created_by)
    VALUES (1, v_debt, 'Softfon test', 'DEBT_COLLECTION', 'PAUSED',
            1, 0, 5, now() - interval '1 day', v_admin)
    RETURNING id INTO v_camp;
    INSERT INTO campaign_dialer_config (campaign_id, max_concurrent_calls)
    VALUES (v_camp, 1) ON CONFLICT (campaign_id) DO NOTHING;
    INSERT INTO campaign_schedule (campaign_id, dial_window_start, dial_window_end)
    VALUES (v_camp, '00:00', '23:59') ON CONFLICT (campaign_id) DO NOTHING;
    INSERT INTO campaign_dial_day (campaign_id, day)
    SELECT v_camp, d FROM unnest(ARRAY['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']) AS d;
    INSERT INTO campaign_target (company_id, campaign_id, client_id, phone, language, context_data, status, attempts)
    SELECT 1, v_camp, c.id, '600', 'uz-UZ',
           '{"clientName": "Test mijoz", "debtAmount": 1250000, "currency": "UZS", "dueDate": "2026-09-01", "contractNumber": "SH-TEST-0001"}'::jsonb,
           'PENDING', 0
    FROM contact c WHERE c.company_id = 1 AND c.phone = '600';

    INSERT INTO campaign (company_id, ai_agent_id, name, type, status,
                          max_attempts, last_run_at, created_at, created_by)
    VALUES (1, v_survey, 'Mijozlar so''rovnomasi (NPS)', 'SURVEY', 'COMPLETED',
            2, now() - interval '4 days', now() - interval '8 days', v_admin)
    RETURNING id INTO v_camp;
    INSERT INTO campaign_dialer_config (campaign_id, max_concurrent_calls)
    VALUES (v_camp, 5) ON CONFLICT (campaign_id) DO NOTHING;
    INSERT INTO campaign_schedule (campaign_id, dial_window_start, dial_window_end)
    VALUES (v_camp, '10:00', '19:00') ON CONFLICT (campaign_id) DO NOTHING;
    INSERT INTO campaign_dial_day (campaign_id, day)
    SELECT v_camp, d FROM unnest(ARRAY['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY']) AS d;
    INSERT INTO campaign_target (company_id, campaign_id, client_id, phone, language, context_data, status, attempts, created_at)
    SELECT 1, v_camp, c.id, c.phone, t.lang, '{"topicName": "Uysot mobil ilovasi"}'::jsonb, 'DONE', 1, now() - interval '8 days'
    FROM (VALUES ('+998901230015', 'uz-UZ'), ('+998901230016', 'ru-RU'), ('+998901230017', 'uz-UZ')) AS t(phone, lang)
    JOIN contact c ON c.company_id = 1 AND c.phone = t.phone;

    -- SCHEDULED: goes ACTIVE by itself at start_at, 30 days from the day the database was seeded.
    INSERT INTO campaign (company_id, ai_agent_id, name, type, status,
                          max_attempts, created_at, created_by)
    VALUES (1, v_reminder, 'Uchrashuv eslatmasi — oktabr', 'SURVEY', 'SCHEDULED',
            2, now() - interval '2 days', v_admin)
    RETURNING id INTO v_camp;
    INSERT INTO campaign_dialer_config (campaign_id, max_concurrent_calls)
    VALUES (v_camp, 3) ON CONFLICT (campaign_id) DO NOTHING;
    INSERT INTO campaign_schedule (campaign_id, dial_window_start, dial_window_end, start_at, end_at)
    VALUES (v_camp, '10:00', '18:00', v_later, v_later + interval '7 days') ON CONFLICT (campaign_id) DO NOTHING;
    INSERT INTO campaign_dial_day (campaign_id, day)
    SELECT v_camp, d FROM unnest(ARRAY['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY']) AS d;
    INSERT INTO campaign_target (company_id, campaign_id, client_id, phone, language, context_data, status, attempts, next_attempt_at)
    SELECT 1, v_camp, c.id, c.phone, 'ru-RU',
           jsonb_build_object('appointmentTime', '2026-10-20', 'clientName', t.client_name), 'PENDING', 0, v_later
    FROM (VALUES ('+998901230021', 'Ирина'), ('+998901230022', 'Сергей')) AS t(phone, client_name)
    JOIN contact c ON c.company_id = 1 AND c.phone = t.phone;

    -- Recurring shapes, none of which can dial: no targets, or ARCHIVED.
    INSERT INTO campaign (company_id, ai_agent_id, name, type, status, created_at, created_by)
    VALUES (1, v_notify, 'Haftalik to''lov eslatmasi', 'DEBT_COLLECTION', 'ARCHIVED', now() - interval '60 days', v_admin)
    RETURNING id INTO v_camp;
    INSERT INTO campaign_schedule (campaign_id, recurrence_type)
    VALUES (v_camp, 'WEEKLY') ON CONFLICT (campaign_id) DO NOTHING;
    INSERT INTO campaign_dial_day (campaign_id, day) VALUES (v_camp, 'MONDAY');

    INSERT INTO campaign (company_id, ai_agent_id, name, type, status, created_at, created_by)
    VALUES (1, v_notify, 'Oylik eslatma (5-sana)', 'SURVEY', 'DRAFT', now() - interval '14 days', v_admin)
    RETURNING id INTO v_camp;
    INSERT INTO campaign_schedule (campaign_id, recurrence_type, recurring_day_of_month, dial_window_start, dial_window_end)
    VALUES (v_camp, 'MONTHLY', 5, '10:00', '17:00') ON CONFLICT (campaign_id) DO NOTHING;
    INSERT INTO campaign_dial_day (campaign_id, day)
    SELECT v_camp, d FROM unnest(ARRAY['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']) AS d;

    INSERT INTO campaign (company_id, ai_agent_id, name, type, status, created_at, created_by)
    VALUES (1, v_debt, 'CRON — dushanba 10:00', 'DEBT_COLLECTION', 'ARCHIVED', now() - interval '30 days', v_admin)
    RETURNING id INTO v_camp;
    INSERT INTO campaign_schedule (campaign_id, recurrence_type, cron_expression)
    VALUES (v_camp, 'CRON', '0 0 10 * * MON') ON CONFLICT (campaign_id) DO NOTHING;

    -- Where callers who ask to be called back land: the queues and the sales route point here.
    INSERT INTO campaign (company_id, ai_agent_id, name, type, status,
                          max_attempts, retry_interval_minutes, created_at, created_by)
    VALUES (1, v_night, 'Qayta qo''ng''iroqlar (callback)', 'SURVEY', 'DRAFT',
            2, 30, now() - interval '10 days', v_admin)
    RETURNING id INTO v_callback;
    INSERT INTO campaign_dialer_config (campaign_id, max_concurrent_calls)
    VALUES (v_callback, 2) ON CONFLICT (campaign_id) DO NOTHING;
    INSERT INTO campaign_schedule (campaign_id, dial_window_start, dial_window_end)
    VALUES (v_callback, '09:00', '18:00') ON CONFLICT (campaign_id) DO NOTHING;
    INSERT INTO campaign_dial_day (campaign_id, day)
    SELECT v_callback, d FROM unnest(ARRAY['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY']) AS d;

    UPDATE call_queue SET callback_campaign_id = v_callback
     WHERE company_id = 1 AND name IN ('Qo''llab-quvvatlash', 'Sotuv');
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
    v_callback   BIGINT;
    v_sup        BIGINT;
    v_op1        BIGINT;
    v_graph      JSONB;
    v_flow       BIGINT;
BEGIN
    IF EXISTS (SELECT 1 FROM call_flow WHERE company_id = 1 AND name = 'Asosiy IVR') THEN
        RETURN;
    END IF;

    SELECT id INTO v_sched FROM business_schedule WHERE company_id = 1 AND name = 'Asosiy ish vaqti';
    SELECT id INTO v_reception FROM ai_agent WHERE company_id = 1 AND name = 'Kiruvchi qabulxona';
    SELECT id INTO v_night FROM ai_agent WHERE company_id = 1 AND name = 'Kechki navbatchi';
    SELECT id INTO v_sales FROM ai_agent WHERE company_id = 1 AND name = 'Sotuv menejeri';
    SELECT id INTO v_prompt FROM ai_agent WHERE company_id = 1 AND name = 'Erkin suhbat (PROMPT rejimi)';
    SELECT id INTO v_q_support FROM call_queue WHERE company_id = 1 AND name = 'Qo''llab-quvvatlash';
    SELECT id INTO v_q_sales FROM call_queue WHERE company_id = 1 AND name = 'Sotuv';
    SELECT id INTO v_q_vip FROM call_queue WHERE company_id = 1 AND name = 'VIP liniya';
    SELECT id INTO v_vm FROM voicemail_box WHERE company_id = 1 AND name = 'Umumiy ovozli pochta';
    SELECT id INTO v_vm_sales FROM voicemail_box WHERE company_id = 1 AND name = 'Sotuv bo''limi pochtasi';
    SELECT id INTO v_callback FROM campaign WHERE company_id = 1 AND name = 'Qayta qo''ng''iroqlar (callback)';
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
        (1, '+998712000100', v_reception, v_sched, v_flow, v_callback,
         'Hozir ish vaqtimiz tugagan. Iltimos, ertaga soat 9:00 dan keyin qo''ng''iroq qiling.', true, now() - interval '10 days'),
        (1, '+998712000200', v_sales, v_sched, NULL, v_callback,
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
-- Call history: every disposition outbound, inbound through routes, queues and operators,
-- with technical metrics, timelines, voicemails and missed calls. sip_call_id 'seed-*'
-- marks them. Every call is finished, so no sweeper treats one as still on the line.
-- ---------------------------------------------------------------------------
DO $seed$
DECLARE
    v_flow      BIGINT;
    v_q_support BIGINT;
    v_q_sales   BIGINT;
    v_q_vip     BIGINT;
    v_vm        BIGINT;
    v_vm_sales  BIGINT;
    v_route     BIGINT;
    v_route_s   BIGINT;
    v_sup       BIGINT;
    v_op1       BIGINT;
    v_op2       BIGINT;
    v_c07       call_attempt%ROWTYPE;
    v_c18       call_attempt%ROWTYPE;
    v_c19       call_attempt%ROWTYPE;
    v_c20       call_attempt%ROWTYPE;
    v_c21       call_attempt%ROWTYPE;
    v_c22       call_attempt%ROWTYPE;
    v_vmsg20    BIGINT;
    v_vmsg22    BIGINT;
BEGIN
    IF EXISTS (SELECT 1 FROM call_attempt WHERE sip_call_id = 'seed-c01') THEN
        RETURN;
    END IF;

    SELECT id INTO v_flow FROM call_flow WHERE company_id = 1 AND name = 'Asosiy IVR';
    SELECT id INTO v_q_support FROM call_queue WHERE company_id = 1 AND name = 'Qo''llab-quvvatlash';
    SELECT id INTO v_q_sales FROM call_queue WHERE company_id = 1 AND name = 'Sotuv';
    SELECT id INTO v_q_vip FROM call_queue WHERE company_id = 1 AND name = 'VIP liniya';
    SELECT id INTO v_vm FROM voicemail_box WHERE company_id = 1 AND name = 'Umumiy ovozli pochta';
    SELECT id INTO v_vm_sales FROM voicemail_box WHERE company_id = 1 AND name = 'Sotuv bo''limi pochtasi';
    SELECT id INTO v_route FROM inbound_route WHERE company_id = 1 AND did_number = '+998712000100';
    SELECT id INTO v_route_s FROM inbound_route WHERE company_id = 1 AND did_number = '+998712000200';
    SELECT id INTO v_sup FROM app_user WHERE username = 'supervisor';
    SELECT id INTO v_op1 FROM app_user WHERE username = 'operator1';
    SELECT id INTO v_op2 FROM app_user WHERE username = 'operator2';

    -- Outbound: `ago` is when the call started, `ring` the seconds before an answer, `talk`
    -- the seconds after it (NULL = never answered). Each target has exactly as many attempts
    -- here as its attempts counter says.
    INSERT INTO call_attempt (company_id, target_id, variant_id, phone, sip_call_id, asterisk_channel, language,
                              started_at, answered_at, ended_at, duration_sec, disposition, hangup_cause, error_message,
                              finalize_attempts, ai_agent_id, created_at)
    SELECT 1, t.id, t.variant_id, t.phone, 'seed-' || v.k, 'PJSIP/trunk-endpoint-' || v.k, v.lang,
           now() - v.ago,
           CASE WHEN v.talk IS NOT NULL THEN now() - v.ago + make_interval(secs => v.ring) END,
           now() - v.ago + make_interval(secs => v.ring + coalesce(v.talk, 0)),
           coalesce(v.talk, 0), v.disp, v.cause, v.err,
           CASE WHEN v.talk IS NOT NULL THEN 1 ELSE 0 END, cp.ai_agent_id, now() - v.ago
    FROM (VALUES
        ('c01',  '+998901230001', 'Qarzdorlik — sentabr', interval '2 days 03:10', 8,  96,   'PROMISE_TO_PAY',     'normal clearing',    NULL, 'uz-UZ'),
        ('c02',  '+998901230002', 'Qarzdorlik — sentabr', '2 days 02:40',          11, 74,   'REFUSED',            'normal clearing',    NULL, 'uz-UZ'),
        ('c03',  '+998901230003', 'Qarzdorlik — sentabr', '2 days 02:00',          30, NULL, 'NO_ANSWER',          'no answer',          NULL, 'uz-UZ'),
        ('c04',  '+998901230004', 'Qarzdorlik — sentabr', '2 days 01:30',          14, 22,   'VOICEMAIL',          'normal clearing',    NULL, 'uz-UZ'),
        ('c05',  '+998901230005', 'Qarzdorlik — sentabr', '1 day 05:00',           2,  NULL, 'WRONG_NUMBER',       'unallocated number', NULL, 'uz-UZ'),
        ('c06',  '+998901230006', 'Qarzdorlik — sentabr', '1 day 04:20',           9,  58,   'CALLBACK_REQUESTED', 'normal clearing',    NULL, 'uz-UZ'),
        ('c07',  '+998901230007', 'Qarzdorlik — sentabr', '1 day 03:50',           6,  142,  'TRANSFERRED',        'normal clearing',    NULL, 'uz-UZ'),
        ('c08',  '+998901230008', 'Qarzdorlik — sentabr', '1 day 03:00',           25, NULL, 'FAILED',             'no user responding', 'SIP 408 Request Timeout from carrier (cause 18)', 'uz-UZ'),
        ('c09',  '+998901230009', 'Qarzdorlik — sentabr', '1 day 02:10',           7,  35,   'DO_NOT_CALL',        'normal clearing',    NULL, 'uz-UZ'),
        ('c10',  '+998901230010', 'Qarzdorlik — sentabr', '05:00',                 10, 19,   'HUNG_UP',            'normal clearing',    NULL, 'uz-UZ'),
        ('c11',  '+998901230011', 'Qarzdorlik — sentabr', '04:30',                 1,  NULL, 'CARRIER_REJECTED',   'call rejected',      'SIP 403 Forbidden: caller id not allowed on this trunk', 'uz-UZ'),
        ('c12a', '+998901230012', 'Qarzdorlik — sentabr', '3 days 02:00',          30, NULL, 'NO_ANSWER',          'no answer',          NULL, 'uz-UZ'),
        ('c12b', '+998901230012', 'Qarzdorlik — sentabr', '2 days 23:00',          30, NULL, 'NO_ANSWER',          'no answer',          NULL, 'uz-UZ'),
        ('c12c', '+998901230012', 'Qarzdorlik — sentabr', '2 days 20:00',          30, NULL, 'NO_ANSWER',          'user busy',          NULL, 'uz-UZ'),
        ('c13',  '+998901230015', 'Mijozlar so''rovnomasi (NPS)', '4 days 05:00',  9,  81,   'COMPLETED',          'normal clearing',    NULL, 'uz-UZ'),
        ('c14',  '+998901230016', 'Mijozlar so''rovnomasi (NPS)', '4 days 04:30',  12, 95,   'COMPLETED',          'normal clearing',    NULL, 'ru-RU'),
        ('c15',  '+998901230017', 'Mijozlar so''rovnomasi (NPS)', '4 days 04:00',  7,  40,   'COMPLETED',          'normal clearing',    NULL, 'uz-UZ')
    ) AS v(k, phone, camp, ago, ring, talk, disp, cause, err, lang)
    JOIN campaign cp ON cp.company_id = 1 AND cp.name = v.camp
    JOIN campaign_target t ON t.campaign_id = cp.id AND t.phone = v.phone;

    -- Inbound: answered by Asterisk at once; `enq` is seconds until the call joined a queue,
    -- `wait` the seconds it then waited.
    INSERT INTO call_attempt (company_id, phone, sip_call_id, asterisk_channel, language, started_at, answered_at, ended_at,
                              duration_sec, disposition, hangup_cause, finalize_attempts, inbound_route_id, operator_user_id,
                              queue_id, enqueued_at, operator_answered_at, wait_seconds, abandoned, survey_score,
                              ai_agent_id, created_at)
    SELECT 1, v.phone, 'seed-' || v.k, 'PJSIP/trunk-endpoint-' || v.k, v.lang,
           now() - v.ago, now() - v.ago, now() - v.ago + make_interval(secs => v.dur),
           v.dur, v.disp, 'normal clearing', CASE WHEN a.id IS NOT NULL THEN 1 ELSE 0 END, r.id, u.id, q.id,
           CASE WHEN q.id IS NOT NULL THEN now() - v.ago + make_interval(secs => v.enq) END,
           CASE WHEN u.id IS NOT NULL THEN now() - v.ago + make_interval(secs => v.enq + v.wait) END,
           CASE WHEN q.id IS NOT NULL THEN v.wait END,
           v.abandoned, v.survey, a.id, now() - v.ago
    FROM (VALUES
        ('c16', '+998901230023', '+998712000200', interval '1 day 01:00', 128, 'COMPLETED',   'Sotuv menejeri',     'uz-UZ', NULL,                  NULL, NULL, NULL,         false, NULL),
        ('c17', '+998935550101', '600',           '01:40',                66,  'COMPLETED',   'Kiruvchi qabulxona', 'uz-UZ', NULL,                  NULL, NULL, NULL,         false, NULL),
        ('c18', '+998935550102', '+998712000100', '04:01:20',             560, 'TRANSFERRED', NULL,                 'uz-UZ', 'Qo''llab-quvvatlash', 40,   40,   'operator2',  false, 5),
        ('c19', '+998935550103', '+998712000100', '02:30',                125, 'HUNG_UP',     NULL,                 'uz-UZ', 'Qo''llab-quvvatlash', 30,   95,   NULL,         true,  NULL),
        ('c20', '+998935550104', '+998712000100', '14:00',                70,  'VOICEMAIL',   NULL,                 'uz-UZ', NULL,                  NULL, NULL, NULL,         false, NULL),
        ('c21', '+998901230024', '+998712000100', '1 day 06:00',          310, 'TRANSFERRED', NULL,                 'uz-UZ', 'VIP liniya',          8,    12,   'supervisor', false, 2),
        ('c22', '+998935550105', '+998712000200', '2 days 01:00',         250, 'VOICEMAIL',   NULL,                 'ru-RU', 'Sotuv',               10,   180,  NULL,         false, NULL)
    ) AS v(k, phone, did, ago, dur, disp, agent, lang, queue, enq, wait, op, abandoned, survey)
    JOIN inbound_route r ON r.company_id = 1 AND r.did_number = v.did AND r.enabled
    LEFT JOIN ai_agent a ON a.company_id = 1 AND a.name = v.agent
    LEFT JOIN call_queue q ON q.company_id = 1 AND q.name = v.queue
    LEFT JOIN app_user u ON u.username = v.op;

    -- The collections call the agent handed to a human.
    UPDATE call_attempt
       SET operator_user_id = v_op1, queue_id = v_q_support,
           enqueued_at = answered_at + interval '95 seconds',
           operator_answered_at = answered_at + interval '113 seconds', wait_seconds = 18
     WHERE sip_call_id = 'seed-c07';

    -- Technical tab. The engine columns come from the agent that ran the call; a variant B
    -- call ran on the realtime engine. Latencies and tokens scale with the call's length.
    INSERT INTO call_technical (call_id, channel_name, trunk, amd_result, stt_provider, tts_provider, tts_voice, llm_model,
                                prompt_tokens, completion_tokens, cached_tokens, turn_count,
                                avg_turn_latency_ms, max_turn_latency_ms, avg_llm_latency_ms, max_llm_latency_ms,
                                rtp_packets_received, rtp_packets_lost, rtp_jitter_ms, rtp_mos, created_at)
    SELECT c.id, c.asterisk_channel, 'trunk-endpoint',
           CASE WHEN c.inbound_route_id IS NOT NULL THEN NULL
                WHEN c.disposition = 'VOICEMAIL' THEN 'MACHINE' ELSE 'HUMAN' END,
           CASE WHEN cv.pipeline_mode = 'REALTIME' THEN 'gemini-live' ELSE coalesce(a.stt_provider, 'yandex') END,
           CASE WHEN cv.pipeline_mode = 'REALTIME' THEN 'gemini-live' ELSE coalesce(a.tts_provider, 'yandex') END,
           CASE WHEN cv.pipeline_mode = 'REALTIME' THEN 'Aoede' ELSE coalesce(a.tts_voice, 'nigora') END,
           CASE WHEN cv.pipeline_mode = 'REALTIME' THEN 'gemini-3.8-live' ELSE coalesce(a.llm_model, 'gemini-3.8-flash') END,
           c.duration_sec * 42, c.duration_sec * 7, c.duration_sec * 25, greatest(2, c.duration_sec / 12),
           850 + (c.id % 7) * 70, 1600 + (c.id % 5) * 180, 420 + (c.id % 6) * 45, 900 + (c.id % 4) * 150,
           c.duration_sec * 50, (c.id % 4) * c.duration_sec / 20, 8 + (c.id % 5) * 3,
           CASE WHEN c.id % 9 = 0 THEN 3.1 ELSE 4.2 + (c.id % 3) * 0.1 END,
           c.ended_at
    FROM call_attempt c
    LEFT JOIN ai_agent a ON a.id = c.ai_agent_id
    LEFT JOIN campaign_variant cv ON cv.id = c.variant_id
    WHERE c.sip_call_id LIKE 'seed-%' AND c.answered_at IS NOT NULL;

    SELECT * INTO v_c07 FROM call_attempt WHERE sip_call_id = 'seed-c07';
    SELECT * INTO v_c18 FROM call_attempt WHERE sip_call_id = 'seed-c18';
    SELECT * INTO v_c19 FROM call_attempt WHERE sip_call_id = 'seed-c19';
    SELECT * INTO v_c20 FROM call_attempt WHERE sip_call_id = 'seed-c20';
    SELECT * INTO v_c21 FROM call_attempt WHERE sip_call_id = 'seed-c21';
    SELECT * INTO v_c22 FROM call_attempt WHERE sip_call_id = 'seed-c22';

    INSERT INTO call_event (company_id, call_attempt_id, at, type, payload) VALUES
        (1, v_c07.id, v_c07.answered_at + interval '93 seconds', 'TRANSFER', jsonb_build_object('to', 'queue ' || v_q_support, 'mode', 'BLIND')),
        (1, v_c07.id, v_c07.enqueued_at, 'ENQUEUED', jsonb_build_object('queueId', v_q_support, 'position', 1)),
        (1, v_c07.id, v_c07.enqueued_at + interval '3 seconds', 'OPERATOR_RINGING', jsonb_build_object('userId', v_op1)),
        (1, v_c07.id, v_c07.operator_answered_at, 'OPERATOR_ANSWERED', jsonb_build_object('userId', v_op1, 'waitSeconds', 18)),
        (1, v_c07.id, v_c07.operator_answered_at + interval '1 second', 'WHISPER', jsonb_build_object('userId', v_op1, 'seconds', 6)),

        (1, v_c18.id, v_c18.started_at, 'FLOW_ENTERED', jsonb_build_object('flowId', v_flow)),
        (1, v_c18.id, v_c18.started_at, 'NODE_ENTERED', jsonb_build_object('node', 'start', 'type', 'CALLER_ROUTER')),
        (1, v_c18.id, v_c18.started_at + interval '1 second', 'NODE_ENTERED', jsonb_build_object('node', 'hours', 'type', 'SCHEDULE')),
        (1, v_c18.id, v_c18.started_at + interval '12 seconds', 'LANGUAGE_SET', jsonb_build_object('language', 'uz-UZ')),
        (1, v_c18.id, v_c18.started_at + interval '14 seconds', 'NODE_ENTERED', jsonb_build_object('node', 'menu', 'type', 'MENU')),
        (1, v_c18.id, v_c18.started_at + interval '31 seconds', 'DTMF', jsonb_build_object('key', '2')),
        (1, v_c18.id, v_c18.enqueued_at, 'ENQUEUED', jsonb_build_object('queueId', v_q_support, 'position', 1)),
        (1, v_c18.id, v_c18.enqueued_at + interval '30 seconds', 'OPERATOR_RINGING', jsonb_build_object('userId', v_op2)),
        (1, v_c18.id, v_c18.operator_answered_at, 'OPERATOR_ANSWERED', jsonb_build_object('userId', v_op2, 'waitSeconds', 40)),
        (1, v_c18.id, v_c18.ended_at - interval '10 seconds', 'SURVEY_SCORE', jsonb_build_object('score', 5)),

        (1, v_c19.id, v_c19.started_at, 'FLOW_ENTERED', jsonb_build_object('flowId', v_flow)),
        (1, v_c19.id, v_c19.started_at + interval '24 seconds', 'DTMF', jsonb_build_object('key', '2')),
        (1, v_c19.id, v_c19.enqueued_at, 'ENQUEUED', jsonb_build_object('queueId', v_q_support, 'position', 3)),
        (1, v_c19.id, v_c19.enqueued_at + interval '20 seconds', 'OPERATOR_RINGING', jsonb_build_object('userId', v_op1)),
        (1, v_c19.id, v_c19.enqueued_at + interval '35 seconds', 'OPERATOR_MISSED', jsonb_build_object('userId', v_op1, 'cause', 'no answer')),
        (1, v_c19.id, v_c19.enqueued_at + interval '60 seconds', 'POSITION_ANNOUNCED', jsonb_build_object('position', 2, 'waitMinutes', 2)),
        (1, v_c19.id, v_c19.ended_at, 'ABANDONED', jsonb_build_object('queueId', v_q_support, 'waitSeconds', 95)),

        (1, v_c20.id, v_c20.started_at, 'FLOW_ENTERED', jsonb_build_object('flowId', v_flow)),
        (1, v_c20.id, v_c20.started_at + interval '1 second', 'NODE_ENTERED', jsonb_build_object('node', 'hours', 'type', 'SCHEDULE')),
        (1, v_c20.id, v_c20.started_at + interval '2 seconds', 'NODE_ENTERED', jsonb_build_object('node', 'vm', 'type', 'VOICEMAIL')),
        (1, v_c20.id, v_c20.ended_at, 'VOICEMAIL', jsonb_build_object('boxId', v_vm, 'seconds', 48)),

        (1, v_c21.id, v_c21.started_at, 'FLOW_ENTERED', jsonb_build_object('flowId', v_flow)),
        (1, v_c21.id, v_c21.started_at, 'NODE_ENTERED', jsonb_build_object('node', 'start', 'type', 'CALLER_ROUTER')),
        (1, v_c21.id, v_c21.enqueued_at, 'ENQUEUED', jsonb_build_object('queueId', v_q_vip, 'position', 1)),
        (1, v_c21.id, v_c21.operator_answered_at, 'OPERATOR_ANSWERED', jsonb_build_object('userId', v_sup, 'waitSeconds', 12)),
        (1, v_c21.id, v_c21.operator_answered_at + interval '90 seconds', 'HOLD', NULL),
        (1, v_c21.id, v_c21.operator_answered_at + interval '150 seconds', 'UNHOLD', NULL),
        (1, v_c21.id, v_c21.ended_at - interval '8 seconds', 'SURVEY_SCORE', jsonb_build_object('score', 2)),

        (1, v_c22.id, v_c22.enqueued_at, 'ENQUEUED', jsonb_build_object('queueId', v_q_sales, 'position', 2)),
        (1, v_c22.id, v_c22.enqueued_at + interval '180 seconds', 'OVERFLOW', jsonb_build_object('queueId', v_q_sales, 'action', 'VOICEMAIL')),
        (1, v_c22.id, v_c22.ended_at, 'VOICEMAIL', jsonb_build_object('boxId', v_vm_sales, 'seconds', 52));

    INSERT INTO voicemail_message (company_id, box_id, call_attempt_id, caller_phone, duration_sec, transcript, status, created_at)
    VALUES (1, v_vm, v_c20.id, v_c20.phone, 48,
            'Assalomu alaykum, men Botir. Shartnoma bo''yicha savolim bor edi, ertaga ertalab qo''ng''iroq qilib qo''ysangiz. Raqamim shu.',
            'NEW', v_c20.ended_at)
    RETURNING id INTO v_vmsg20;
    INSERT INTO voicemail_message (company_id, box_id, call_attempt_id, caller_phone, duration_sec, transcript, status, created_at)
    VALUES (1, v_vm_sales, v_c22.id, v_c22.phone, 52,
            'Здравствуйте, меня зовут Олег. Интересует ваш тариф для колл-центра на 20 операторов, перезвоните, пожалуйста.',
            'HEARD', v_c22.ended_at)
    RETURNING id INTO v_vmsg22;
    INSERT INTO voicemail_message (company_id, box_id, call_attempt_id, caller_phone, duration_sec, transcript, status, created_at)
    VALUES (1, v_vm, NULL, '+998935550107', 17, 'Xato raqamga tushib qoldim, uzr.', 'ARCHIVED', now() - interval '9 days');

    INSERT INTO missed_call (company_id, phone, reason, status, call_count, first_at, last_at, queue_id, inbound_route_id,
                             last_call_attempt_id, voicemail_message_id, assigned_user_id, callback_attempts,
                             last_callback_at, resolved_at, resolved_by_user_id, note) VALUES
        (1, v_c19.phone, 'ABANDONED', 'NEW', 1, v_c19.started_at, v_c19.started_at, v_q_support, v_route,
         v_c19.id, NULL, NULL, 0, NULL, NULL, NULL, NULL),
        (1, v_c20.phone, 'VOICEMAIL', 'NEW', 1, v_c20.started_at, v_c20.started_at, NULL, v_route,
         v_c20.id, v_vmsg20, NULL, 0, NULL, NULL, NULL, NULL),
        (1, '+998935550106', 'AFTER_HOURS', 'IN_PROGRESS', 3, now() - interval '1 day 15:00', now() - interval '13:20', NULL, v_route,
         NULL, NULL, v_op1, 1, now() - interval '02:00', NULL, NULL, 'Birinchi qayta qo''ng''iroqqa javob bermadi.'),
        (1, v_c22.phone, 'VOICEMAIL', 'RESOLVED', 1, v_c22.started_at, v_c22.started_at, v_q_sales, v_route_s,
         v_c22.id, v_vmsg22, v_op2, 1, v_c22.started_at + interval '2 hours', v_c22.started_at + interval '2 hours 5 minutes',
         v_op2, 'Qayta qo''ng''iroq qilindi, taklifnoma yuborildi.');
END
$seed$;

-- ---------------------------------------------------------------------------
-- Transcripts and post-call results of the calls above. Every call with a transcript has a
-- result, so the summary outbox never picks one up; every result carries a CRM note id
-- except one that shows a CRM write-back that ran out of retries (outbox.max-attempts 5).
-- ---------------------------------------------------------------------------
DO $seed$
DECLARE
    v_disclosure CONSTANT TEXT := 'Assalomu alaykum! Bu Default kompaniyasining avtomatik ovozli xizmati. Suhbat yozib olinmoqda. ';
    v_promised   CONSTANT DATE := current_date + 5;
BEGIN
    IF EXISTS (SELECT 1 FROM call_result r JOIN call_attempt c ON c.id = r.call_id WHERE c.sip_call_id = 'seed-c01') THEN
        RETURN;
    END IF;

    INSERT INTO call_transcript (call_id, seq, role, text, dialog_state, ts_offset_ms, stt_confidence)
    SELECT c.id, t.seq, t.role, t.text, t.state, t.off, t.conf
    FROM (VALUES
        ('c01', 0, 'AGENT',  v_disclosure || 'Men Aziz aka bilan gaplashayapmanmi?', 'GREETING', 0, NULL),
        ('c01', 1, 'CLIENT', 'Ha, men Azizman, eshitaman.', 'IDENTITY_CHECK', 7200, 0.94),
        ('c01', 2, 'AGENT',  'Aziz aka, SH-2025-0141 shartnomasi bo''yicha 1 250 000 so''m qarzdorlik bor, muddati 1-sentabrda o''tgan. Nima sababdan to''lov kechikdi?', 'DEBT_NOTICE', 9800, NULL),
        ('c01', 3, 'CLIENT', 'Maosh kechikdi, shu hafta ichida to''layman.', 'REASON_INQUIRY', 21500, 0.90),
        ('c01', 4, 'AGENT',  'Tushunarli. Aniq qaysi kuni to''lay olasiz?', 'PAYMENT_DATE', 25800, NULL),
        ('c01', 5, 'CLIENT', 'Juma kuni, besh kundan keyin.', 'PAYMENT_DATE', 31000, 0.88),
        ('c01', 6, 'AGENT',  'Yaxshi, ' || to_char(v_promised, 'DD.MM.YYYY') || ' sanasida 1 250 000 so''m to''lov kutamiz. Rahmat, Aziz aka, xayr!', 'CONFIRMATION', 35200, NULL),
        ('c01', 7, 'CLIENT', 'Rahmat, xayr.', 'CLOSING', 44000, 0.97),

        ('c02', 0, 'AGENT',  v_disclosure || 'Dilnoza opa bilan gaplashayapmanmi?', 'GREETING', 0, NULL),
        ('c02', 1, 'CLIENT', 'Ha, men.', 'IDENTITY_CHECK', 6900, 0.95),
        ('c02', 2, 'AGENT',  'Dilnoza opa, SH-2025-0142 shartnomasi bo''yicha 840 000 so''m qarzdorlik bor, muddati 25-avgustda o''tgan. To''lov nima sababdan kechikdi?', 'DEBT_NOTICE', 8700, NULL),
        ('c02', 3, 'CLIENT', 'Ishdan bo''shab qoldim, hozir umuman pulim yo''q. To''lay olmayman.', 'REASON_INQUIRY', 19800, 0.87),
        ('c02', 4, 'AGENT',  'Tushundim, vaziyatingiz og''ir ekan. Moliyaviy qiyinchilikda filialga ariza bilan murojaat qilib, to''lov muddatini qayta ko''rib chiqishni so''rashingiz mumkin.', 'REASON_INQUIRY', 27400, NULL),
        ('c02', 5, 'CLIENT', 'Hozir hech qanday sana ayta olmayman.', 'PAYMENT_DATE', 41000, 0.90),
        ('c02', 6, 'AGENT',  'Yaxshi, sababingizni yozib oldim. Ishingiz tez orada yurishib ketsin. Xayr!', 'CLOSING', 46500, NULL),

        ('c04', 0, 'AGENT',  'Assalomu alaykum. Shartnomangiz bo''yicha savol bilan qo''ng''iroq qildik. Iltimos, qulay vaqtda qayta bog''laning.', 'GREETING', 0, NULL),

        ('c06', 0, 'AGENT',  v_disclosure || 'Kamola opa bilan gaplashayapmanmi?', 'GREETING', 0, NULL),
        ('c06', 1, 'CLIENT', 'Ha, lekin hozir ishdaman, gaplasha olmayman.', 'IDENTITY_CHECK', 7100, 0.91),
        ('c06', 2, 'AGENT',  'Tushunarli. Qachon qo''ng''iroq qilsak qulay bo''ladi?', 'IDENTITY_CHECK', 11300, NULL),
        ('c06', 3, 'CLIENT', 'Kechqurun soat yettilarda.', 'IDENTITY_CHECK', 16200, 0.89),
        ('c06', 4, 'AGENT',  'Yaxshi, soat 19:00 da qayta qo''ng''iroq qilamiz. Xayr!', 'CLOSING', 20100, NULL),

        ('c07', 0, 'AGENT',  v_disclosure || 'Rustam aka bilan gaplashayapmanmi?', 'GREETING', 0, NULL),
        ('c07', 1, 'CLIENT', 'Ha, Rustamman.', 'IDENTITY_CHECK', 6800, 0.96),
        ('c07', 2, 'AGENT',  'Rustam aka, SH-2025-0147 shartnomasi bo''yicha 3 200 000 so''m qarzdorlik bor, muddati 30-iyulda o''tgan. Nima sababdan to''lanmagan?', 'DEBT_NOTICE', 8900, NULL),
        ('c07', 3, 'CLIENT', 'Bu summa noto''g''ri! Men iyun oyida ikki million to''laganman, chek ham bor.', 'REASON_INQUIRY', 21000, 0.86),
        ('c07', 4, 'AGENT',  'Tushundim. Summani men o''zgartira olmayman, lekin hisob-kitobni mutaxassis tekshirib beradi. Sizni operatorga ulaymi?', 'REASON_INQUIRY', 30200, NULL),
        ('c07', 5, 'CLIENT', 'Ha, ulang.', 'ESCALATE_TO_HUMAN', 39800, 0.97),
        ('c07', 6, 'AGENT',  'Hozir sizni operatorga ulayman, bir daqiqa kuting.', 'ESCALATE_TO_HUMAN', 42500, NULL),

        ('c09', 0, 'AGENT',  v_disclosure || 'Otabek aka bilan gaplashayapmanmi?', 'GREETING', 0, NULL),
        ('c09', 1, 'CLIENT', 'Menga boshqa qo''ng''iroq qilmanglar! Raqamimni o''chiring.', 'IDENTITY_CHECK', 7400, 0.92),
        ('c09', 2, 'AGENT',  'Uzr, bezovta qilganimiz uchun. Raqamingizni qo''ng''iroqlar ro''yxatidan chiqaramiz. Xayr.', 'CLOSING', 12800, NULL),

        ('c10', 0, 'AGENT',  v_disclosure || 'Zarina opa bilan gaplashayapmanmi?', 'GREETING', 0, NULL),
        ('c10', 1, 'CLIENT', 'Band man hozir.', 'IDENTITY_CHECK', 7800, 0.83),

        ('c13', 0, 'AGENT',  'Assalomu alaykum! Xizmatimiz haqida bir daqiqalik so''rovnomada qatnashishga rozimisiz?', 'GREETING', 0, NULL),
        ('c13', 1, 'CLIENT', 'Ha, mayli.', 'GREETING', 5200, 0.95),
        ('c13', 2, 'AGENT',  'Uysot mobil ilovasidan qanchalik mamnunsiz?', 'QUESTION_1', 7100, NULL),
        ('c13', 3, 'CLIENT', 'Juda qulay, hammasi tushunarli.', 'QUESTION_1', 12900, 0.90),
        ('c13', 4, 'AGENT',  'Nimani yaxshilashimiz kerak deb o''ylaysiz?', 'QUESTION_2', 16000, NULL),
        ('c13', 5, 'CLIENT', 'Bildirishnomalar biroz kech keladi.', 'QUESTION_2', 21500, 0.88),
        ('c13', 6, 'AGENT',  'Bizni do''stlaringizga 0 dan 10 gacha qanchalik tavsiya qilasiz?', 'QUESTION_3', 25400, NULL),
        ('c13', 7, 'CLIENT', 'To''qqiz.', 'QUESTION_3', 31200, 0.93),
        ('c13', 8, 'AGENT',  'Katta rahmat! Fikringiz biz uchun muhim. Xayr!', 'CLOSING', 34000, NULL),

        ('c14', 0, 'AGENT',  'Здравствуйте! Примите участие в коротком опросе о нашем сервисе?', 'GREETING', 0, NULL),
        ('c14', 1, 'CLIENT', 'Да, давайте.', 'GREETING', 5600, 0.94),
        ('c14', 2, 'AGENT',  'Насколько вы довольны мобильным приложением Uysot?', 'QUESTION_1', 7900, NULL),
        ('c14', 3, 'CLIENT', 'В целом нормально, но иногда вылетает.', 'QUESTION_1', 14800, 0.89),
        ('c14', 4, 'AGENT',  'Насколько вероятно, что вы порекомендуете нас друзьям, от 0 до 10?', 'QUESTION_3', 19300, NULL),
        ('c14', 5, 'CLIENT', 'Шесть.', 'QUESTION_3', 25100, 0.95),
        ('c14', 6, 'AGENT',  'Спасибо за ответы! До свидания.', 'CLOSING', 27700, NULL),

        ('c15', 0, 'AGENT',  'Assalomu alaykum! Xizmatimiz haqida bir daqiqalik so''rovnomada qatnashishga rozimisiz?', 'GREETING', 0, NULL),
        ('c15', 1, 'CLIENT', 'Ha.', 'GREETING', 4800, 0.96),
        ('c15', 2, 'AGENT',  'Bizni do''stlaringizga 0 dan 10 gacha qanchalik tavsiya qilasiz?', 'QUESTION_3', 6400, NULL),
        ('c15', 3, 'CLIENT', 'O''n, albatta.', 'QUESTION_3', 11900, 0.94),
        ('c15', 4, 'AGENT',  'Rahmat! Xayr!', 'CLOSING', 14200, NULL),

        ('c16', 0, 'AGENT',  'Assalomu alaykum! Uysot sotuv bo''limi, qanday yordam bera olaman?', 'GREETING', 0, NULL),
        ('c16', 1, 'CLIENT', 'Reklamangizni ko''rdim, qo''ng''iroq markazi uchun robot kerak edi.', 'GREETING', 5100, 0.91),
        ('c16', 2, 'AGENT',  'Ajoyib! Nechta operator bilan ishlaysiz va qanday qo''ng''iroqlar ko''p?', 'INTEREST_CHECK', 11800, NULL),
        ('c16', 3, 'CLIENT', 'O''nta operator, asosan qarzdorlik bo''yicha chiquvchi qo''ng''iroqlar.', 'INTEREST_CHECK', 18600, 0.89),
        ('c16', 4, 'AGENT',  'Tushunarli. Qachondan boshlashni rejalashtiryapsiz va taxminiy byudjetingiz qancha?', 'BUDGET_TIMELINE', 26100, NULL),
        ('c16', 5, 'CLIENT', 'Keyingi oydan, oyiga besh million atrofida.', 'BUDGET_TIMELINE', 34200, 0.87),
        ('c16', 6, 'AGENT',  'Demo uchrashuv o''tkazaylik: payshanba soat 15:00 qulaymi?', 'MEETING_SCHEDULING', 41000, NULL),
        ('c16', 7, 'CLIENT', 'Ha, bo''ladi.', 'MEETING_SCHEDULING', 47300, 0.96),
        ('c16', 8, 'AGENT',  'Yaxshi, payshanba 15:00 ga yozib qo''ydim. Rahmat, xayr!', 'CLOSING', 50400, NULL),

        ('c17', 0, 'AGENT',  v_disclosure || 'Sizni tinglayapman, qanday yordam bera olaman?', 'GREETING', 0, NULL),
        ('c17', 1, 'CLIENT', 'To''lovni qanday qilsam bo''ladi?', 'GREETING', 8300, 0.93),
        ('c17', 2, 'AGENT',  'To''lovni Click, Payme, Uzum Bank ilovalari orqali yoki bank kassalarida shartnoma raqamingizni ko''rsatib amalga oshirishingiz mumkin.', 'ANSWER_QUESTION', 11200, NULL),
        ('c17', 3, 'CLIENT', 'Payme orqali bo''ladimi?', 'ANSWER_QUESTION', 22900, 0.90),
        ('c17', 4, 'AGENT',  'Ha, Payme ilovasida shartnoma raqamingizni kiritib to''lashingiz mumkin.', 'ANSWER_QUESTION', 25600, NULL),
        ('c17', 5, 'CLIENT', 'Rahmat.', 'CLOSING', 33100, 0.97),
        ('c17', 6, 'AGENT',  'Arzimaydi, xayr!', 'CLOSING', 34500, NULL)
    ) AS t(k, seq, role, text, state, off, conf)
    JOIN call_attempt c ON c.sip_call_id = 'seed-' || t.k;

    INSERT INTO call_result (call_id, summary, reason_code, promised_date, promised_amount, sentiment, needs_follow_up,
                             follow_up_note, escalated, crm_note_id, crm_attempts, crm_last_error, outcome, created_at)
    SELECT c.id, r.summary, r.reason, r.promised_date, r.promised_amount, r.sentiment, r.follow_up, r.note, r.escalated,
           CASE WHEN r.crm_ok THEN gen_random_uuid()::text END,
           CASE WHEN r.crm_ok THEN 1 ELSE 5 END,
           CASE WHEN r.crm_ok THEN NULL ELSE 'CRM ulanmagan: Uysot OAuth tokeni yo''q (CRM_INTEGRATION_NOT_CONNECTED)' END,
           r.outcome, c.ended_at + interval '20 seconds'
    FROM (VALUES
        ('c01', 'Mijoz (Aziz aka) qarzni tan oldi: maosh kechikkani uchun to''lov kechikkan. ' || to_char(v_promised, 'DD.MM.YYYY') || ' sanasida 1 250 000 so''m to''lashga va''da berdi.',
                NULL, v_promised, 1250000.00, 'POSITIVE', true, 'To''lov sanasidan keyingi kuni tushumni tekshirish.', false, true,
                jsonb_build_object('promisedDate', v_promised, 'promisedAmount', 1250000, 'reasonCode', NULL)),
        ('c02', 'Mijoz ishdan bo''shagani sababli hozir to''lay olmasligini aytdi. Restrukturizatsiya uchun filialga murojaat qilish tushuntirildi.',
                'JOB_LOSS', NULL, NULL, 'NEGATIVE', true, 'Bir oydan keyin qayta qo''ng''iroq qilish.', false, false,
                '{"reasonCode": "JOB_LOSS"}'::jsonb),
        ('c04', 'Avtojavobgich javob berdi, qayta bog''lanish so''ralgan xabar qoldirildi.',
                NULL, NULL, NULL, NULL, false, NULL, false, true, '{}'::jsonb),
        ('c06', 'Mijoz ishda ekanini aytib, kechqurun soat 19:00 da qayta qo''ng''iroq qilishni so''radi.',
                NULL, NULL, NULL, 'NEUTRAL', true, 'Bugun 19:00 da qayta qo''ng''iroq.', false, true,
                '{"callbackTime": "19:00"}'::jsonb),
        ('c07', 'Mijoz summaga e''tiroz bildirdi: iyunda 2 000 000 so''m to''laganini va cheki borligini aytdi. Operatorga ulandi.',
                'DISPUTES_DEBT', NULL, NULL, 'NEGATIVE', true, 'Iyundagi 2 000 000 so''mlik to''lov chekini tekshirish.', true, true,
                '{"reasonCode": "DISPUTES_DEBT"}'::jsonb),
        ('c09', 'Mijoz qo''ng''iroq qilmaslikni qat''iy talab qildi; raqam DNC ro''yxatiga qo''shildi.',
                NULL, NULL, NULL, 'HOSTILE', false, NULL, false, true, '{}'::jsonb),
        ('c10', 'Mijoz bandligini aytib, go''shakni qo''ydi.',
                NULL, NULL, NULL, 'NEUTRAL', false, NULL, false, true, '{}'::jsonb),
        ('c13', 'Mijoz ilovadan mamnun, bildirishnomalar kech kelishini aytdi. NPS: 9.',
                NULL, NULL, NULL, 'POSITIVE', false, NULL, false, true,
                '{"answers": [{"question": "Ilovadan mamnunmisiz?", "answer": "Juda qulay"}, {"question": "Nimani yaxshilash kerak?", "answer": "Bildirishnomalar kech keladi"}], "score": 9}'::jsonb),
        ('c14', 'Mijoz ilovadan umuman mamnun, lekin ba''zan ishdan chiqishini aytdi. NPS: 6.',
                NULL, NULL, NULL, 'NEUTRAL', true, 'Ilova ishdan chiqishi haqida texnik jamoaga xabar berish.', false, true,
                '{"answers": [{"question": "Ilovadan mamnunmisiz?", "answer": "Normal, ba''zan ishdan chiqadi"}], "score": 6}'::jsonb),
        ('c15', 'Mijoz xizmatni to''liq tavsiya qiladi. NPS: 10.',
                NULL, NULL, NULL, 'POSITIVE', false, NULL, false, true, '{"answers": [], "score": 10}'::jsonb),
        ('c16', 'Reklamadan kelgan lead: 10 operatorli qarzdorlik markazi, oyiga ~5 mln so''m byudjet, keyingi oydan boshlamoqchi. Payshanba 15:00 ga demo belgilandi.',
                NULL, NULL, NULL, 'POSITIVE', true, 'Demo: payshanba 15:00.', false, true,
                '{"interest": "high", "budget": 5000000, "meetingAt": "payshanba 15:00"}'::jsonb),
        ('c17', 'Mijoz to''lov usullarini so''radi, bilim bazasidan javob berildi (Payme).',
                NULL, NULL, NULL, 'POSITIVE', false, NULL, false, true, '{"resolved": true, "escalated": false}'::jsonb)
    ) AS r(k, summary, reason, promised_date, promised_amount, sentiment, follow_up, note, escalated, crm_ok, outcome)
    JOIN call_attempt c ON c.sip_call_id = 'seed-' || r.k;
END
$seed$;

-- ---------------------------------------------------------------------------
-- Billing, conversions, agent evaluation, notifications, reports, audit, API keys, webhooks.
--
-- The money adds up: two top-ups credit 7 000 000, every settled call is a CALL_CHARGE,
-- one ADJUSTMENT, and company_billing.balance_uzs is where the ledger ends. Unanswered
-- calls are RELEASED holds and cost nothing. No hold is left open.
-- ---------------------------------------------------------------------------
DO $seed$
DECLARE
    v_admin      BIGINT;
    v_super      BIGINT;
    v_sup        BIGINT;
    v_op1        BIGINT;
    v_debt       BIGINT;
    v_sales      BIGINT;
    v_camp_sept  BIGINT;
    v_camp_nps   BIGINT;
    v_topup1     BIGINT;
    v_topup2     BIGINT;
    v_topup5     BIGINT;
    v_charged    BIGINT;
    v_balance    BIGINT;
    v_run        BIGINT;
    v_key        BIGINT;
    v_notif      BIGINT;
    v_c01        call_attempt%ROWTYPE;
    v_c02        call_attempt%ROWTYPE;
    v_c16        call_attempt%ROWTYPE;
    v_event      BIGINT;
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
    SELECT id INTO v_sales FROM ai_agent WHERE company_id = 1 AND name = 'Sotuv menejeri';
    SELECT id INTO v_camp_sept FROM campaign WHERE company_id = 1 AND name = 'Qarzdorlik — sentabr';
    SELECT id INTO v_camp_nps FROM campaign WHERE company_id = 1 AND name = 'Mijozlar so''rovnomasi (NPS)';
    SELECT * INTO v_c01 FROM call_attempt WHERE sip_call_id = 'seed-c01';
    SELECT * INTO v_c02 FROM call_attempt WHERE sip_call_id = 'seed-c02';
    SELECT * INTO v_c16 FROM call_attempt WHERE sip_call_id = 'seed-c16';

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

    -- What each seeded call of company 1 cost. A variant B call ran on the realtime engine
    -- and is priced per engine second instead of per token/character.
    INSERT INTO call_billing (call_attempt_id, target_id, company_id, status, rate_version, reserved_uzs, duration_sec,
                              prompt_tokens, completion_tokens, cached_tokens, tts_chars, realtime_seconds,
                              llm_cost_uzs, stt_cost_uzs, tts_cost_uzs, telephony_cost_uzs, realtime_cost_uzs,
                              platform_fee_uzs, total_uzs, created_at, settled_at)
    SELECT c.id, c.target_id, 1,
           CASE WHEN c.answered_at IS NULL THEN 'RELEASED' ELSE 'SETTLED' END,
           '2026-09-v1', 5000, c.duration_sec,
           coalesce(ct.prompt_tokens, 0), coalesce(ct.completion_tokens, 0), coalesce(ct.cached_tokens, 0),
           CASE WHEN rt THEN 0 ELSE c.duration_sec * 12 END,
           CASE WHEN rt THEN c.duration_sec ELSE 0 END,
           CASE WHEN rt OR c.answered_at IS NULL THEN 0 ELSE c.duration_sec * 5 END,
           CASE WHEN rt OR c.answered_at IS NULL THEN 0 ELSE c.duration_sec * 3 END,
           CASE WHEN rt OR c.answered_at IS NULL THEN 0 ELSE c.duration_sec * 4 END,
           CASE WHEN c.answered_at IS NULL THEN 0 ELSE ceil(c.duration_sec / 60.0)::bigint * 300 END,
           CASE WHEN rt AND c.answered_at IS NOT NULL THEN c.duration_sec * 15 ELSE 0 END,
           CASE WHEN c.answered_at IS NULL THEN 0 ELSE 100 END,
           CASE WHEN c.answered_at IS NULL THEN 0
                WHEN rt THEN c.duration_sec * 15 + ceil(c.duration_sec / 60.0)::bigint * 300 + 100
                ELSE c.duration_sec * 12 + ceil(c.duration_sec / 60.0)::bigint * 300 + 100 END,
           c.started_at, c.ended_at + interval '30 seconds'
    FROM call_attempt c
    LEFT JOIN call_technical ct ON ct.call_id = c.id
    LEFT JOIN campaign_variant cv ON cv.id = c.variant_id
    CROSS JOIN LATERAL (SELECT coalesce(cv.pipeline_mode = 'REALTIME', false) AS rt) p
    WHERE c.company_id = 1 AND c.sip_call_id LIKE 'seed-%';

    INSERT INTO billing_ledger (company_id, entry_type, amount_uzs, balance_before_uzs, balance_after_uzs, reference_type,
                                reference_id, idempotency_key, created_at) VALUES
        (1, 'TOPUP', 5000000, 0, 5000000, 'payment_topup', 'PAY-SEED-0001', 'topup:PAY-SEED-0001', now() - interval '20 days' + interval '2 minutes'),
        (1, 'TOPUP', 2000000, 5000000, 7000000, 'payment_topup', 'PAY-SEED-0002', 'topup:PAY-SEED-0002', now() - interval '5 days' + interval '1 minute');

    WITH charges AS (
        SELECT b.id, b.call_attempt_id, b.total_uzs, b.settled_at,
               sum(b.total_uzs) OVER (ORDER BY b.settled_at, b.id) AS running
        FROM call_billing b
        WHERE b.company_id = 1 AND b.status = 'SETTLED'
    )
    INSERT INTO billing_ledger (company_id, entry_type, amount_uzs, balance_before_uzs, balance_after_uzs, reference_type,
                                reference_id, idempotency_key, created_at)
    SELECT 1, 'CALL_CHARGE', -total_uzs, 7000000 - (running - total_uzs), 7000000 - running,
           'call_attempt', call_attempt_id::text, 'settle:' || call_attempt_id, settled_at
    FROM charges;

    SELECT coalesce(sum(total_uzs), 0) INTO v_charged FROM call_billing WHERE company_id = 1 AND status = 'SETTLED';
    v_balance := 7000000 - v_charged;
    INSERT INTO billing_ledger (company_id, entry_type, amount_uzs, balance_before_uzs, balance_after_uzs, reference_type,
                                reference_id, idempotency_key, created_at)
    VALUES (1, 'ADJUSTMENT', 50000, v_balance, v_balance + 50000, 'manual', 'CRM uzilishi uchun kompensatsiya',
            'adjustment:seed-0001', now() - interval '5 minutes');

    INSERT INTO company_billing (company_id, plan_code, plan_name, balance_uzs, reserved_uzs, auto_recharge, next_billing_date)
    VALUES (1, 'PRO_MONTHLY', 'Professional (Pro)', v_balance + 50000, 0, false,
            (date_trunc('month', now() AT TIME ZONE 'Asia/Tashkent') + interval '1 month') AT TIME ZONE 'Asia/Tashkent');

    INSERT INTO billing_usage (company_id, billing_period, used_minutes, used_tokens, used_tts_chars, used_channels, total_spend_uzs)
    SELECT 1, to_char(now() AT TIME ZONE 'Asia/Tashkent', 'YYYY-MM'),
           coalesce(sum(ceil(b.duration_sec / 60.0)), 0)::int,
           coalesce(sum(b.prompt_tokens + b.completion_tokens), 0),
           coalesce(sum(b.tts_chars), 0), 3, v_charged
    FROM call_billing b WHERE b.company_id = 1 AND b.status = 'SETTLED';
    INSERT INTO billing_usage (company_id, billing_period, used_minutes, used_tokens, used_tts_chars, used_channels, total_spend_uzs)
    VALUES (1, to_char((now() - interval '1 month') AT TIME ZONE 'Asia/Tashkent', 'YYYY-MM'), 3120, 1450000, 610000, 18, 4850000);

    INSERT INTO invoice (id, company_id, period_name, amount_uzs, status, paid_at, created_at) VALUES
        ('INV-2026-007', 1, 'Iyul 2026',    4200000, 'PAID',    '2026-08-03 11:20:00+05', '2026-08-01 09:00:00+05'),
        ('INV-2026-008', 1, 'Avgust 2026',  4850000, 'PAID',    '2026-09-02 15:05:00+05', '2026-09-01 09:00:00+05'),
        ('INV-2026-009', 1, 'Sentabr 2026', 1900000, 'PENDING', NULL,                     now() - interval '1 day');

    -- Conversions: a payment and a meeting credited to the calls that earned them, a partial
    -- payment after a refusal, and one event with no call in its window.
    INSERT INTO conversion_goal (company_id, goal_key, name, attribution_window_hours, attribution_model, enabled) VALUES
        (1, 'payment',     'To''lov',             72,  'LAST_CALL',  true),
        (1, 'appointment', 'Uchrashuv',           48,  'FIRST_CALL', true),
        (1, 'renewal',     'Shartnoma yangilash', 168, 'LAST_CALL',  false);

    INSERT INTO conversion_event (company_id, goal_key, phone, occurred_at, value_uzs, source, evidence, dedupe_key, ingested_at)
    VALUES (1, 'payment', v_c01.phone, v_c01.ended_at + interval '1 day', 1250000, 'payme',
            '{"receipt": "PM-883921", "contractNumber": "SH-2025-0141"}'::jsonb, 'payme-883921', v_c01.ended_at + interval '1 day 1 minute')
    RETURNING id INTO v_event;
    INSERT INTO conversion_attribution (conversion_event_id, company_id, campaign_id, variant_id, call_attempt_id,
                                        attribution_model, window_hours, attributed_value_uzs, computed_at)
    VALUES (v_event, 1, v_camp_sept, v_c01.variant_id, v_c01.id, 'LAST_CALL', 72, 1250000, v_c01.ended_at + interval '1 day 1 minute');

    INSERT INTO conversion_event (company_id, goal_key, phone, occurred_at, value_uzs, source, evidence, dedupe_key, ingested_at)
    VALUES (1, 'payment', v_c02.phone, now() - interval '3 hours', 200000, 'click',
            '{"receipt": "CL-5521", "partial": true}'::jsonb, 'click-5521', now() - interval '3 hours')
    RETURNING id INTO v_event;
    INSERT INTO conversion_attribution (conversion_event_id, company_id, campaign_id, variant_id, call_attempt_id,
                                        attribution_model, window_hours, attributed_value_uzs, computed_at)
    VALUES (v_event, 1, v_camp_sept, v_c02.variant_id, v_c02.id, 'LAST_CALL', 72, 200000, now() - interval '3 hours');

    INSERT INTO conversion_event (company_id, goal_key, phone, occurred_at, value_uzs, source, evidence, dedupe_key, ingested_at)
    VALUES (1, 'appointment', v_c16.phone, v_c16.ended_at + interval '20 hours', NULL, 'crm',
            '{"meetingId": 1041}'::jsonb, 'crm-meet-1041', v_c16.ended_at + interval '20 hours')
    RETURNING id INTO v_event;
    INSERT INTO conversion_attribution (conversion_event_id, company_id, campaign_id, variant_id, call_attempt_id,
                                        attribution_model, window_hours, attributed_value_uzs, computed_at)
    VALUES (v_event, 1, NULL, NULL, v_c16.id, 'FIRST_CALL', 48, NULL, v_c16.ended_at + interval '20 hours');

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
    INSERT INTO agent_test_case (company_id, agent_id, name, persona_prompt, facts, language, expect_disposition, must_say, must_not_say)
    VALUES (1, v_sales, 'Qiziqqan lead', 'Siz 10 operatorli call-markaz rahbarisiz, demo so''raysiz.', '{}'::jsonb,
            'uz-UZ', 'COMPLETED', '["demo"]'::jsonb, '["chegirma"]'::jsonb);

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
            (1, 'CAMPAIGN_FINISHED', 'Kampaniya yakunlandi', '"Mijozlar so''rovnomasi (NPS)" yakunlandi: 3 mijozdan 3 tasi bilan gaplashildi.', '/campaigns/' || v_camp_nps, now() - interval '4 days'),
            (1, 'DAILY_REPORT', 'Kunlik hisobot', 'Kecha: 9 qo''ng''iroq, 6 javob, 1 va''da, 1 operatorga uzatildi.', '/reports', now() - interval '10 hours'),
            (1, 'OPERATOR_REQUEST', 'Mijoz operator so''radi', 'Rustam Qodirov summaga e''tiroz bildirib, operatorga ulandi.', '/calls', now() - interval '1 day 03:48'),
            (1, 'LOW_SURVEY_SCORE', 'Past baho: 2 / 5', 'VIP mijoz (+998 90 123 00 24) operator Dilshod Karimovning suhbatiga 2 baho qo''ydi.', '/calls', now() - interval '1 day 05:55'),
            (1, 'ERROR_OCCURRED', 'CRM yozuvi muvaffaqiyatsiz', 'Dilnoza Rashidova qo''ng''irog''i xulosasi 5 urinishdan keyin ham CRM ga yozilmadi.', '/calls', now() - interval '1 day 20:00'),
            (1, 'TARGET_SYNC_FAILED', 'Ro''yxatni yuklab bo''lmadi', '"Qarzdorlik — kunlik": Uysot ulanmagan, ro''yxat bo''sh qoldi.', '/campaigns', now() - interval '6 hours'),
            (1, 'CAMPAIGN_PAUSED', 'Kampaniya to''xtatildi', '"Qarzdorlik — realtime test": ishlaydigan SIP trunk topilmadi.', '/campaigns', now() - interval '4 days'),
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
        (1, 'supervisor@test.uz', 'WEEKLY',  'xlsx', v_camp_sept, true,  NULL,                     v_admin),
        (1, 'finance@test.uz',    'MONTHLY', 'csv',  NULL,        false, NULL,                     v_admin);

    INSERT INTO audit_log (company_id, actor, action, entity, entity_id, detail, ip_address, created_at) VALUES
        (1, 'admin',      'LOGIN',                       'app_user',     v_admin::text,     NULL,                                    '192.168.0.10', now() - interval '3 hours'),
        (1, 'admin',      'CAMPAIGN_CREATE',             'campaign',     v_camp_sept::text, 'Qarzdorlik — sentabr',                  '192.168.0.10', now() - interval '6 days'),
        (1, 'supervisor', 'AI_AGENT_UPDATE',             'ai_agent',     v_sales::text,     'Sotuv menejeri: interruption HIGH',     '192.168.0.23', now() - interval '2 days'),
        (1, 'admin',      'SECRET_CREATE',               'secret',       NULL,              'CRM_API_TOKEN',                         '192.168.0.10', now() - interval '6 days'),
        (1, 'admin',      'USER_BLOCK',                  'app_user',     NULL,              'operator4',                             '192.168.0.10', now() - interval '20 days'),
        (1, 'admin',      'OPERATOR_SIP_ACCOUNT_CREATE', 'operator_sip_account', v_op1::text, 'webrtc',                              '192.168.0.10', now() - interval '15 days'),
        (1, 'admin',      'API_KEY_CREATED',             'api_key',      NULL,              'CRM integratsiyasi (rc_live_d70d8b812ab4)', '192.168.0.10', now() - interval '10 days'),
        (1, 'superadmin', 'CAMPAIGN_PAUSE',              'campaign',     NULL,              'Qarzdorlik — realtime test',            '10.0.0.5',     now() - interval '4 days');

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

    -- Neither is PENDING, so the retry sweep leaves both alone.
    INSERT INTO webhook_delivery (company_id, source, host, sealed_target, payload, status, attempts, next_attempt_at,
                                  last_error, created_at, delivered_at) VALUES
        (1, 'post_call', 'hooks.example.uz',
         '9ESYpBBkrRgoNEjVJZmPMCST2mb14lqdA2ajpGhUXCIqsMoxPTuPm2cT4c1U4AiuOnNOZLbKbydv9qBsJf4UGWJxqyur7L9A8zFSCmUn2PZ24qBNJTLNHdQTP0v42d1krV2ZBmvbN9y73pz706C0p907QH4=',
         jsonb_build_object('callId', v_c01.id, 'disposition', 'PROMISE_TO_PAY', 'phone', v_c01.phone),
         'DELIVERED', 2, NULL, 'HTTP 503 on first try', now() - interval '2 days', now() - interval '2 days' + interval '1 minute'),
        (1, 'post_call', 'crm.example.uz',
         'yWq/bW8KfDQIFAv9vD4v/rePNDLP9zGOfo+qHeL4w28aYUJ2sKkzcXXRG+ntNRM7RwHuaL5qNUWX5JxMSeY63cdfEdfU94vbS17oey1vWHC8nnQs1UoP4w==',
         jsonb_build_object('callId', v_c02.id, 'disposition', 'REFUSED', 'phone', v_c02.phone),
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
-- alerts, promises to pay, operator wrap-up and scorecards, AI call quality and insights,
-- tool logs, the Telegram bot and chats, the copilot, analytics boards.
--
-- Built on the call history above (sip_call_id 'seed-*'), so every row here points at a
-- call that exists. Nothing here creates a call or moves money: the billing block above
-- stays the whole story of company 1's balance.
-- ---------------------------------------------------------------------------
DO $seed$
DECLARE
    v_admin     BIGINT;
    v_sup       BIGINT;
    v_op1       BIGINT;
    v_op2       BIGINT;
    v_reception BIGINT;
    v_debt      BIGINT;
    v_sales     BIGINT;
    v_prompt    BIGINT;
    v_trunk     BIGINT;
    v_flow      BIGINT;
    v_camp_sept BIGINT;
    v_pool      BIGINT;
    v_wu_done   BIGINT;
    v_wu_retry  BIGINT;
    v_wu_ptp    BIGINT;
    v_crit1     BIGINT;
    v_crit2     BIGINT;
    v_crit3     BIGINT;
    v_crit4     BIGINT;
    v_t_order   BIGINT;
    v_t_balance BIGINT;
    v_kb_paid   BIGINT;
    v_bot       BIGINT;
    v_widget    BIGINT;
    v_chat      BIGINT;
    v_copilot   BIGINT;
    v_board     BIGINT;
    v_c01       call_attempt%ROWTYPE;
    v_c02       call_attempt%ROWTYPE;
    v_c07       call_attempt%ROWTYPE;
    v_c17       call_attempt%ROWTYPE;
    v_c18       call_attempt%ROWTYPE;
    v_c21       call_attempt%ROWTYPE;
BEGIN
    IF EXISTS (SELECT 1 FROM wrap_up_code WHERE company_id = 1 AND name = 'To''lovga rozi') THEN
        RETURN;
    END IF;

    SELECT id INTO v_admin FROM app_user WHERE username = 'admin';
    SELECT id INTO v_sup FROM app_user WHERE username = 'supervisor';
    SELECT id INTO v_op1 FROM app_user WHERE username = 'operator1';
    SELECT id INTO v_op2 FROM app_user WHERE username = 'operator2';
    SELECT id INTO v_reception FROM ai_agent WHERE company_id = 1 AND name = 'Kiruvchi qabulxona';
    SELECT id INTO v_debt FROM ai_agent WHERE company_id = 1 AND name = 'Qarzdorlik (cascade)';
    SELECT id INTO v_sales FROM ai_agent WHERE company_id = 1 AND name = 'Sotuv menejeri';
    SELECT id INTO v_prompt FROM ai_agent WHERE company_id = 1 AND name = 'Erkin suhbat (PROMPT rejimi)';
    SELECT id INTO v_trunk FROM sip_trunk WHERE company_id = 1 AND pjsip_endpoint = 'trunk-endpoint';
    SELECT id INTO v_flow FROM call_flow WHERE company_id = 1 AND name = 'Asosiy IVR';
    SELECT id INTO v_camp_sept FROM campaign WHERE company_id = 1 AND name = 'Qarzdorlik — sentabr';
    SELECT id INTO v_t_order FROM tool WHERE company_id = 1 AND name = 'buyurtma_holati';
    SELECT id INTO v_t_balance FROM tool WHERE company_id = 1 AND name = 'tolov_qoldigi';
    SELECT id INTO v_kb_paid FROM knowledge_base_item WHERE company_id = 1 AND topic = 'payment' ORDER BY id LIMIT 1;
    SELECT * INTO v_c01 FROM call_attempt WHERE sip_call_id = 'seed-c01';
    SELECT * INTO v_c02 FROM call_attempt WHERE sip_call_id = 'seed-c02';
    SELECT * INTO v_c07 FROM call_attempt WHERE sip_call_id = 'seed-c07';
    SELECT * INTO v_c17 FROM call_attempt WHERE sip_call_id = 'seed-c17';
    SELECT * INTO v_c18 FROM call_attempt WHERE sip_call_id = 'seed-c18';
    SELECT * INTO v_c21 FROM call_attempt WHERE sip_call_id = 'seed-c21';

    -- Agent channel and mode, for every agent seeded so far (the demo tenant's too): a
    -- scenario-driven agent is a CONVERSATIONAL_FLOW, a prompt-only one a SINGLE_PROMPT.
    -- Outbound agents only ever speak; the reception and PROMPT agents also chat.
    UPDATE ai_agent SET agent_mode = CASE WHEN scenario_mode = 'STRUCTURED_STEPS' THEN 'CONVERSATIONAL_FLOW'
                                          ELSE 'SINGLE_PROMPT' END;
    UPDATE ai_agent SET channel = 'VOICE'
     WHERE id NOT IN (v_reception, v_prompt);
    UPDATE ai_agent SET channel = 'OMNICHANNEL', speaking_warmth = 'WARM' WHERE id = v_prompt;
    UPDATE ai_agent SET speaking_warmth = 'RESERVED' WHERE company_id = 1 AND name = 'Bildirishnoma (OpenAI realtime)';

    -- Company limits the platform sets, and the main trunk's contracted channels.
    UPDATE company SET max_concurrent_calls = 20 WHERE id = 1;
    UPDATE sip_trunk SET max_channels = 30, max_cps = 5 WHERE id = v_trunk;

    -- Caller ID rotation for the collections campaign, which also orders its queue by score.
    INSERT INTO caller_id_pool (company_id, name, strategy, description)
    VALUES (1, 'Qarzdorlik raqamlari', 'HIGHEST_ANSWER_RATE', 'Undiruv kampaniyalari uchun chiquvchi raqamlar')
    RETURNING id INTO v_pool;
    INSERT INTO caller_id_pool_number (pool_id, phone, label, usage_count, answer_count, status, cooling_until, last_used_at) VALUES
        (v_pool, '+998712000101', 'Asosiy',            412, 198, 'ACTIVE',       NULL,                        now() - interval '1 day 03:00'),
        (v_pool, '+998712000102', 'Zaxira 1',          388, 171, 'ACTIVE',       NULL,                        now() - interval '1 day 02:40'),
        (v_pool, '+998712000103', 'Zaxira 2',          530,  92, 'COOLING_DOWN', now() + interval '2 days',   now() - interval '2 days'),
        (v_pool, '+998712000104', 'Eski (spam deb belgilangan)', 260, 21, 'DISABLED', NULL,                    now() - interval '25 days');
    INSERT INTO caller_id_pool (company_id, name, strategy, description)
    VALUES (1, 'So''rovnoma raqamlari', 'ROUND_ROBIN', NULL);

    UPDATE campaign SET target_ordering = 'SCORE', caller_id_pool_id = v_pool WHERE id = v_camp_sept;
    UPDATE campaign_target t
       SET score = s.score, best_call_hour = s.hour, scored_at = now() - interval '6 hours'
      FROM (VALUES ('+998901230003', 64, 18), ('+998901230008', 41, 10), ('+998901230010', 77, 12),
                   ('+998901230013', 58, 19)) AS s(phone, score, hour)
     WHERE t.campaign_id = v_camp_sept AND t.phone = s.phone;

    -- The marketplace: numbers the platform still holds (company NULL), and the two company 1
    -- bought — the main IVR number and the sales line.
    INSERT INTO phone_number (company_id, phone_number, nickname, type, category, status, monthly_fee, price_per_channel,
                              max_supported_channels, allocated_channels, provider_trunk_id, inbound_agent_id,
                              inbound_call_flow_id, auto_renew, purchased_at, expires_at, created_by) VALUES
        (1,    '+998712000100', 'Asosiy raqam (IVR)', 'MARKETPLACE_PURCHASED', 'GOLD',     'ASSIGNED',  450000.00, 25000.00, 30, 10,
         v_trunk, NULL, v_flow, true,  now() - interval '40 days', now() + interval '20 days', v_admin),
        (1,    '+998712000200', 'Sotuv liniyasi',     'MARKETPLACE_PURCHASED', 'SILVER',   'ASSIGNED',  250000.00, 20000.00, 20, 5,
         v_trunk, v_sales, NULL, true,  now() - interval '35 days', now() + interval '25 days', v_admin),
        (1,    '+998712000300', 'O''z trunkimiz',     'BYO_SIP_TRUNK',         'STANDARD', 'ASSIGNED',       0.00,     0.00, 10, 2,
         v_trunk, v_prompt, NULL, false, now() - interval '5 days', NULL, v_admin),
        (NULL, '+998712007777', NULL, 'MARKETPLACE_PURCHASED', 'PLATINUM', 'AVAILABLE', 1500000.00, 30000.00, 60, 1, NULL, NULL, NULL, true, NULL, NULL, NULL),
        (NULL, '+998712001000', NULL, 'MARKETPLACE_PURCHASED', 'GOLD',     'AVAILABLE',  450000.00, 25000.00, 30, 1, NULL, NULL, NULL, true, NULL, NULL, NULL),
        (NULL, '+998712003030', NULL, 'MARKETPLACE_PURCHASED', 'SILVER',   'AVAILABLE',  250000.00, 20000.00, 20, 1, NULL, NULL, NULL, true, NULL, NULL, NULL),
        (NULL, '+998712004581', NULL, 'MARKETPLACE_PURCHASED', 'STANDARD', 'AVAILABLE',  120000.00, 15000.00, 10, 1, NULL, NULL, NULL, true, NULL, NULL, NULL),
        (NULL, '+998712004592', NULL, 'MARKETPLACE_PURCHASED', 'STANDARD', 'AVAILABLE',  120000.00, 15000.00, 10, 1, NULL, NULL, NULL, true, NULL, NULL, NULL);

    -- Alert rules: one fired yesterday (and is cooling down), one quiet, one per agent, one off.
    INSERT INTO alert_rule (company_id, name, metric, comparator, threshold, window_minutes, min_calls, cooldown_minutes,
                            ai_agent_id, enabled, last_value, last_evaluated_at, last_triggered_at) VALUES
        (1, 'Muvaffaqiyat 20% dan pastga tushdi', 'SUCCESS_RATE', 'BELOW', 20, 1440, 10, 360,
         NULL, true, 14.3, now() - interval '5 minutes', now() - interval '1 day 01:00'),
        (1, 'Texnik xatolar ko''paydi', 'FAILED_CALL_RATE', 'ABOVE', 15, 60, 10, 60,
         NULL, true, 9.1, now() - interval '5 minutes', NULL),
        (1, 'Qarzdorlik agenti sekin javob bermoqda', 'AVG_TURN_LATENCY_MS', 'ABOVE', 1800, 30, 5, 120,
         v_debt, true, 1120, now() - interval '5 minutes', NULL),
        (1, 'Qo''ng''iroqlar to''xtab qoldi', 'CALL_COUNT', 'BELOW', 1, 120, 0, 240,
         NULL, false, NULL, NULL, NULL);

    -- Wrap-up codes, and the three operator calls closed with them.
    INSERT INTO wrap_up_code (company_id, name, outcome, requires_callback_at) VALUES (1, 'Muammo hal qilindi', 'DONE', false)
    RETURNING id INTO v_wu_done;
    INSERT INTO wrap_up_code (company_id, name, outcome, requires_callback_at) VALUES (1, 'Qayta qo''ng''iroq', 'RETRY', true)
    RETURNING id INTO v_wu_retry;
    INSERT INTO wrap_up_code (company_id, name, outcome, requires_callback_at) VALUES (1, 'To''lovga rozi', 'DONE', false)
    RETURNING id INTO v_wu_ptp;
    INSERT INTO wrap_up_code (company_id, name, outcome, requires_callback_at, enabled) VALUES
        (1, 'Noto''g''ri raqam', 'EXHAUSTED', false, true),
        (1, 'Eski kod (o''chiq)', 'DONE', false, false);

    INSERT INTO call_wrap_up (call_attempt_id, company_id, wrap_up_code_id, code_name, outcome, operator_user_id, note,
                              callback_at, created_at) VALUES
        (v_c07.id, 1, v_wu_retry, 'Qayta qo''ng''iroq', 'RETRY', v_op1,
         'Mijoz iyundagi chekni yuboradi, buxgalteriya tekshirgach qayta qo''ng''iroq qilamiz.',
         now() + interval '2 days', v_c07.ended_at + interval '40 seconds'),
        (v_c18.id, 1, v_wu_done, 'Muammo hal qilindi', 'DONE', v_op2,
         'Shartnoma bo''yicha savolga javob berildi.', NULL, v_c18.ended_at + interval '25 seconds'),
        (v_c21.id, 1, v_wu_ptp, 'To''lovga rozi', 'DONE', v_sup,
         'VIP mijoz, oy oxirigacha to''laydi. Kutish uzoq bo''lganidan norozi.', NULL, v_c21.ended_at + interval '30 seconds');

    -- Who talked how much on the two inbound operator calls.
    INSERT INTO call_talk_metrics (call_id, analysed_ms, client_talk_ms, operator_talk_ms, overtalk_ms, silence_ms,
                                   longest_silence_ms, created_at) VALUES
        (v_c18.id, 480000, 205000, 231000, 6000, 38000, 7000, v_c18.ended_at + interval '1 minute'),
        (v_c21.id, 290000, 168000,  82000, 14000, 26000, 61000, v_c21.ended_at + interval '1 minute');

    -- Scorecards: the judge's marks, one confirmed, one waiting in the review inbox.
    INSERT INTO scorecard_criterion (company_id, name, instruction, weight) VALUES
        (1, 'Salomlashish', 'Operator o''zini va kompaniyani tanishtirdimi?', 2) RETURNING id INTO v_crit1;
    INSERT INTO scorecard_criterion (company_id, name, instruction, weight) VALUES
        (1, 'Ehtiyojni aniqlash', 'Operator mijozning savolini aniqlashtiruvchi savollar bilan tushundimi?', 3) RETURNING id INTO v_crit2;
    INSERT INTO scorecard_criterion (company_id, name, instruction, weight) VALUES
        (1, 'Yechim taklif qilish', 'Operator aniq yechim yoki keyingi qadamni aytdimi?', 4) RETURNING id INTO v_crit3;
    INSERT INTO scorecard_criterion (company_id, name, instruction, weight) VALUES
        (1, 'Xayrlashish', 'Operator suhbatni xushmuomalalik bilan yakunladimi?', 1) RETURNING id INTO v_crit4;
    INSERT INTO scorecard_criterion (company_id, name, instruction, weight, enabled) VALUES
        (1, 'Skript bo''yicha gapirish (eski)', 'Operator skriptdagi iboralarni so''zma-so''z aytdimi?', 1, false);

    INSERT INTO call_scorecard (call_id, company_id, operator_user_id, score, judged_score, sentiment, needs_review,
                                review_status, review_note, reviewed_by, reviewed_at, prompt_tokens, completion_tokens,
                                cached_tokens, created_at) VALUES
        (v_c18.id, 1, v_op2, 90, 90, 'POSITIVE', false, 'CONFIRMED', NULL, v_sup, v_c18.ended_at + interval '3 hours',
         5400, 620, 1800, v_c18.ended_at + interval '2 minutes'),
        (v_c21.id, 1, v_sup, NULL, 40, 'NEGATIVE', true, 'PENDING', NULL, NULL, NULL,
         3900, 540, 1200, v_c21.ended_at + interval '2 minutes');
    INSERT INTO call_scorecard_answer (call_id, criterion_id, criterion_name, weight, answer, judged_answer, evidence) VALUES
        (v_c18.id, v_crit1, 'Salomlashish',         2, 'YES', 'YES', '"Assalomu alaykum, Uysot, Aziz eshitadi"'),
        (v_c18.id, v_crit2, 'Ehtiyojni aniqlash',   3, 'YES', 'YES', '"Qaysi shartnoma haqida so''rayapsiz?"'),
        (v_c18.id, v_crit3, 'Yechim taklif qilish', 4, 'YES', 'YES', 'To''lov jadvalini SMS bilan yuborishni taklif qildi'),
        (v_c18.id, v_crit4, 'Xayrlashish',          1, 'NO',  'NO',  NULL),
        (v_c21.id, v_crit1, 'Salomlashish',         2, 'YES', 'YES', NULL),
        (v_c21.id, v_crit2, 'Ehtiyojni aniqlash',   3, 'NO',  'NO',  'Mijozni ikki marta kuttirib qo''ydi'),
        (v_c21.id, v_crit3, 'Yechim taklif qilish', 4, 'NO',  'NO',  'Aniq to''lov sanasi kelishilmadi'),
        (v_c21.id, v_crit4, 'Xayrlashish',          1, 'YES', 'YES', NULL);
    INSERT INTO call_scorecard_topic (call_id, topic) VALUES
        (v_c18.id, 'shartnoma'), (v_c18.id, 'to''lov jadvali'),
        (v_c21.id, 'kutish vaqti'), (v_c21.id, 'to''lov'), (v_c21.id, 'shikoyat');

    -- The quality judge's verdict on the AI calls, and what a person made of the flagged ones.
    INSERT INTO call_quality (company_id, call_attempt_id, score,
                              guardrail_violation, language_mismatch, talked_over_client, outcome_correct, goal_reached,
                              unanswered_question, tool_misuse, note, needs_review, turn_count, repair_count,
                              tool_error_count, barge_in_count, no_input_prompt_count, low_confidence_count, repeat_call,
                              review_status, review_reason, reviewed_by, reviewed_at, knowledge_item_id, created_at)
    SELECT 1, c.id, q.score,
           q.guardrail, false, q.talked_over, q.outcome_ok, q.goal, q.unanswered, false, q.note, q.review IS NOT NULL,
           q.turns, q.repairs, 0, q.barge, 0, q.lowconf, false,
           q.review, q.reason,
           CASE WHEN q.review IN ('AGENT_CORRECT', 'AGENT_WRONG') THEN v_sup END,
           CASE WHEN q.review IN ('AGENT_CORRECT', 'AGENT_WRONG') THEN c.ended_at + interval '5 hours' END,
           CASE WHEN q.k = 'c17' THEN v_kb_paid END,
           c.ended_at + interval '1 minute'
    FROM (VALUES
        ('c01', 94, false, false, true,  true,  false, 'Va''da aniq sana va summa bilan olindi.',              NULL,            NULL,                                      8, 0, 1, 0),
        ('c02', 78, false, false, true,  false, false, 'Mijozning holatiga hamdardlik bildirildi, bosim yo''q.', NULL,            NULL,                                      7, 1, 0, 1),
        ('c07', 71, false, true,  true,  false, false, 'Agent mijozning gapini bir marta bo''lib yubordi.',     'AGENT_CORRECT', 'Summaga e''tirozda operatorga to''g''ri uzatdi', 7, 1, 2, 0),
        ('c09', 88, false, false, true,  false, false, NULL,                                                  NULL,            NULL,                                      3, 0, 0, 0),
        ('c10', 52, false, false, false, false, true,  'Mijoz band ekanini aytdi, agent qayta qo''ng''iroq vaqtini so''ramadi.', 'PENDING', 'Callback taklif qilinmagan', 2, 0, 0, 1),
        ('c13', 96, false, false, true,  true,  false, NULL,                                                  NULL,            NULL,                                      9, 0, 0, 0),
        ('c16', 91, false, false, true,  true,  false, 'Byudjet va muddat aniqlandi, demo belgilandi.',        NULL,            NULL,                                      9, 0, 0, 0),
        ('c17', 64, false, false, true,  true,  true,  'Uzum Bank orqali to''lov haqidagi savolga to''liq javob yo''q edi.', 'AGENT_WRONG', 'Bilim bazasida Uzum Bank yo''q edi — qo''shildi', 7, 1, 0, 2)
    ) AS q(k, score, guardrail, talked_over, outcome_ok, goal, unanswered, note, review, reason, turns, repairs, barge, lowconf)
    JOIN call_attempt c ON c.sip_call_id = 'seed-' || q.k;

    INSERT INTO agent_insight (company_id, ai_agent_id, type, title, detail, suggestion, call_count, sample_call_ids, status,
                               decision_reason, decided_by, decided_at, created_at) VALUES
        (1, v_debt, 'PROMPT_FIX', 'Band mijozga qayta qo''ng''iroq vaqti taklif qilinmayapti',
         'Kecha 3 ta qo''ng''iroqda mijoz "band man" deganda agent suhbatni tugatgan.',
         'IDENTITY_CHECK bosqichiga: "Mijoz band bo''lsa, qulay vaqtni so''rab, callback yozing" qoidasini qo''shing.',
         3, jsonb_build_array((SELECT id FROM call_attempt WHERE sip_call_id = 'seed-c10')), 'PROPOSED', NULL, NULL, NULL,
         now() - interval '8 hours'),
        (1, v_reception, 'KB_GAP', 'Uzum Bank orqali to''lov haqida javob yo''q',
         'Mijozlar Uzum Bank ilovasi orqali to''lash mumkinmi deb so''rashmoqda.',
         'Bilim bazasiga Uzum Bank orqali to''lov yo''riqnomasini qo''shing.',
         2, jsonb_build_array(v_c17.id), 'ACCEPTED', 'Qo''shildi', v_sup, now() - interval '20 hours',
         now() - interval '1 day 02:00'),
        (1, v_debt, 'PRONUNCIATION', '"SH-2025" shartnoma raqami harfma-harf o''qilmoqda',
         'Agent shartnoma raqamini "es-ha" deb o''qiydi.', 'Talaffuz qoidasi: "SH" → "sha".',
         4, jsonb_build_array(v_c01.id, v_c02.id), 'REJECTED', 'Mijozlar raqamni shunday tushunadi', v_admin,
         now() - interval '2 days', now() - interval '3 days');

    -- A finished re-analysis of last week's collections calls.
    INSERT INTO call_reanalysis_job (company_id, ai_agent_id, range_from, range_to, status, total_calls, processed_calls,
                                     updated_calls, failed_calls, prompt_tokens, completion_tokens, charged_uzs, last_call_id,
                                     created_at, started_at, finished_at)
    VALUES (1, v_debt, now() - interval '7 days', now() - interval '1 day', 'COMPLETED', 14, 14, 11, 0, 68000, 9400, 3100,
            (SELECT max(id) FROM call_attempt WHERE sip_call_id LIKE 'seed-c%'),
            now() - interval '20 hours', now() - interval '20 hours', now() - interval '19 hours 52 minutes');

    -- What the company tools answered on the calls that used them.
    INSERT INTO tool_execution (company_id, tool_id, call_attempt_id, tool_name, status, http_status, latency_ms, args_hash,
                                error, created_at) VALUES
        (1, v_t_balance, v_c01.id, 'tolov_qoldigi', 'OK',         200,  420, md5('c01-balance') || md5('1'), NULL,
         v_c01.answered_at + interval '12 seconds'),
        (1, v_t_balance, v_c07.id, 'tolov_qoldigi', 'OK',         200,  380, md5('c07-balance') || md5('1'), NULL,
         v_c07.answered_at + interval '25 seconds'),
        (1, v_t_balance, v_c02.id, 'tolov_qoldigi', 'HTTP_ERROR', 503, 1210, md5('c02-balance') || md5('1'),
         'HTTP 503: {"error":"maintenance"}', v_c02.answered_at + interval '14 seconds'),
        (1, v_t_order,   v_c17.id, 'buyurtma_holati', 'FAILED',  NULL, 8000, md5('c17-order') || md5('1'),
         'Read timed out after 8 s', v_c17.answered_at + interval '15 seconds');

    -- The CRM note for c02 that ran out of retries (its call_result says so too).
    INSERT INTO outbox_message (company_id, kind, call_attempt_id, payload, idempotency_key, status, attempts,
                                next_attempt_at, last_error, created_at)
    VALUES (1, 'CRM_NOTE', v_c02.id, jsonb_build_object('callId', v_c02.id, 'phone', v_c02.phone),
            'CRM_NOTE:' || v_c02.id, 'DEAD', 5, v_c02.ended_at + interval '3 hours',
            'CRM ulanmagan: Uysot OAuth tokeni yo''q (CRM_INTEGRATION_NOT_CONNECTED)', v_c02.ended_at + interval '20 seconds');

    -- The reception agent on Telegram (token in the TELEGRAM_BOT_TOKEN secret; no webhook
    -- registered yet), and conversations from both text channels.
    INSERT INTO telegram_bot (company_id, ai_agent_id, name, token_secret_key, webhook_secret, language, webhook_url, enabled)
    VALUES (1, v_reception, 'Uysot yordamchi bot', 'TELEGRAM_BOT_TOKEN', md5('seed-telegram-webhook'), 'uz-UZ', NULL, true)
    RETURNING id INTO v_bot;
    SELECT id INTO v_widget FROM agent_widget WHERE widget_key = 'wgt_seedreception01';

    INSERT INTO chat_session (session_key, company_id, ai_agent_id, widget_id, language, stage, outcome, disposition, ended,
                              message_count, created_at, last_message_at)
    VALUES (md5('seed-chat-1'), 1, v_reception, v_widget, 'uz-UZ', 'CLOSING', '{"resolved": true}'::jsonb, 'COMPLETED', true,
            4, now() - interval '3 hours', now() - interval '2 hours 57 minutes')
    RETURNING id INTO v_chat;
    INSERT INTO chat_message (chat_session_id, role, text, created_at) VALUES
        (v_chat, 'CLIENT', 'Salom, to''lovni kartadan qilsa bo''ladimi?', now() - interval '3 hours'),
        (v_chat, 'AGENT',  'Ha, Click, Payme yoki Uzum Bank ilovasida shartnoma raqamingizni kiritib to''lashingiz mumkin.', now() - interval '2 hours 59 minutes'),
        (v_chat, 'CLIENT', 'Rahmat!', now() - interval '2 hours 58 minutes'),
        (v_chat, 'AGENT',  'Arzimaydi! Yana savolingiz bo''lsa, yozing.', now() - interval '2 hours 57 minutes');

    INSERT INTO chat_session (session_key, company_id, ai_agent_id, telegram_bot_id, telegram_chat_id, language, stage,
                              outcome, ended, message_count, created_at, last_message_at)
    VALUES (md5('seed-chat-2'), 1, v_reception, v_bot, '512340001', 'ru-RU', 'ANSWER_QUESTION', '{}'::jsonb, false,
            3, now() - interval '25 minutes', now() - interval '24 minutes')
    RETURNING id INTO v_chat;
    INSERT INTO chat_message (chat_session_id, role, text, created_at) VALUES
        (v_chat, 'CLIENT', 'Здравствуйте, до скольки вы работаете в субботу?', now() - interval '25 minutes'),
        (v_chat, 'AGENT',  'Здравствуйте! В субботу мы работаем с 10:00 до 15:00.', now() - interval '24 minutes 50 seconds'),
        (v_chat, 'CLIENT', 'А можно приехать без записи?', now() - interval '24 minutes');

    -- The admin asked the copilot about yesterday; it called one tool to answer.
    INSERT INTO copilot_session (company_id, user_id, title, created_at, updated_at)
    VALUES (1, v_admin, 'Kechagi qo''ng''iroqlar tahlili', now() - interval '2 hours', now() - interval '1 hour 58 minutes')
    RETURNING id INTO v_copilot;
    INSERT INTO copilot_message (session_id, company_id, role, content, tool_name, tool_arguments, prompt_tokens,
                                 completion_tokens, charged_uzs, created_at) VALUES
        (v_copilot, 1, 'USER', 'Kecha nechta qo''ng''iroq bo''ldi va nechtasi va''da bilan tugadi?', NULL, NULL, 0, 0, 0,
         now() - interval '2 hours'),
        (v_copilot, 1, 'TOOL', '{"total": 9, "items": [{"disposition": "PROMISE_TO_PAY"}, {"disposition": "TRANSFERRED"}, "…"]}',
         'list_calls', '{"from": "yesterday", "size": 50}', 0, 0, 0, now() - interval '1 hour 59 minutes'),
        (v_copilot, 1, 'ASSISTANT', 'Kecha 9 ta qo''ng''iroq bo''ldi: 6 tasiga javob berildi, 1 tasi to''lov va''dasi bilan tugadi, 1 tasi operatorga uzatildi.',
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
