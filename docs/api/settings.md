# Tizim Sozlamalari API (Settings)

`uz.murodjon.robotcallv2` · huquq: sozlama bo'yicha — **AI_MODEL_***, **VOICE_***, **NOTIFICATION_SETTINGS_***, **INTEGRATION_***

Ushbu modul kompaniya darajasidagi barcha global texnik parametrlarni boshqaradi:
1. **Speech Engine Variantlari** (`GET /api/ai-agents/engine-options`) — Nutqni aniqlash va sintezlash provayderlari katalogi. Pipeline konfiguratsiyasining o'zi har bir AI Agent darajasida saqlanadi.
2. **AI Model & Token Limitlari** (`/api/settings/ai-model`) — LLM modeli, harorat (temperature), tokenlar va qo'ng'iroq vaqti cheklovlari.
3. **TTS Ovoz Sozlamalari** (`/api/settings/voice`) — Tezlik (speed) va ohang (pitch) korreksiyasi.
4. **Kompaniya Bildirishnomalari** (`/api/settings/notifications`) — Telegram, Email va Webhook kanallari bo'yicha hodisalar matritsasi.
5. **CRM Integratsiyalari** (`/api/settings/integrations`) — Uysot CRM, amoCRM, Kommo va Bitrix24 ulanishlari.

---

## 1. Speech Engine Variantlari (`/api/ai-agents/engine-options`)

> [!IMPORTANT]
> **Arxitektura o'zgarishi:** Speech Engine sozlamalari (`pipelineMode`, `sttProvider`, `ttsProvider`, `realtimeProvider`, `pipecat*`) kompaniya darajasidan to'liq **AI Agent darajasiga** ko'chirildi. Har bir agent mustaqil ravishda o'zining `CASCADE` yoki `REALTIME` rejimiga va shaxsiy provayderlariga ega bo'ladi. Batafsil: [ai-agents.md](ai-agents.md).

### Pipeline Rejimlari (`PipelineMode`):
- `CASCADE`: STT (Deepgram / Yandex / Aisha / Whisper) ➔ LLM (Gemini / Claude / OpenAI) ➔ TTS (ElevenLabs / Yandex / Aisha / Google).
- `REALTIME`: Speech-to-speech to'g'ridan-to'g'ri multimodal oqim (Gemini Live, OpenAI Realtime, Qwen-Omni, Pipecat). Minimal kechikish (<500ms).

### `GET /api/ai-agents/engine-options` — Mavjud variantlar katalogi
Platformada joriy vaqtda API kalitlari ulangan va ishlatish mumkin bo'lgan provayderlar ro'yxatini qaytaradi.

**Response** (`EngineOptionsResponse`, huquq: `AI_AGENT_READ`):
```json
{
  "accept": true,
  "data": {
    "stt": ["aisha", "deepgram", "gemini", "yandex"],
    "tts": ["aisha", "cartesia", "elevenlabs", "gemini", "yandex"],
    "realtime": ["gemini-live", "moshi", "openai-realtime", "pipecat", "qwen-omni"],
    "pipecatStt": ["deepgram", "gemini", "soniox", "speechmatics", "yandex", "whisper"],
    "pipecatLlm": ["claude-3-5-haiku", "claude-3-5-sonnet", "gemini-3.5-flash-lite", "gemini-3.8-flash", "gpt-4o-mini", "groq-llama-3.3-70b"],
    "pipecatTts": ["cartesia", "elevenlabs", "gemini", "yandex", "google-chirp"]
  },
  "message": null,
  "messageCode": null
}
```

`pipecat*` ro'yxatlari faqat `realtimeProvider = pipecat` tanlanganda ma'noga ega — Pipecat ichida qaysi STT/LLM/TTS ishlashini belgilaydi.

`realtime` ro'yxatidagi `moshi` tool chaqira olmaydi (qo'ng'iroqni tugatish, operatorga o'tkazish, faktlar yo'q): agentga saqlashda `400 ENGINE_REALTIME_TOOLS_UNSUPPORTED` qaytadi, unga tushib qolgan qo'ng'iroq esa `CASCADE` da davom etadi — batafsil [ai-agents.md](ai-agents.md).

---

## 2. AI Model & Limit Sozlamalari (`/api/settings/ai-model`)

Dialoglarni boshqaruvchi asosiy til modeli (LLM) va xavfsizlik limitlarini kompaniya miqyosida sozlash.

`model` maydoni erkin matn emas: mavjud modellar katalogi [`GET /api/ai-models`](ai-models.md)
dan olinadi va saqlashda ham aynan shu ro'yxatga solishtiriladi. Shuning uchun noto'g'ri
yozilgan model qo'ng'iroq paytida emas, PUT javobida `400 AI_MODEL_UNKNOWN` bilan bilinadi.

### `GET /api/settings/ai-model` — Joriy AI konfiguratsiyasi
**Response** (`AiModelConfig`):
```json
{
  "accept": true,
  "data": {
    "companyId": 1,
    "model": "gemini-3.8-flash",
    "temperature": 0.3,
    "maxOutputTokens": 250,
    "maxCallSeconds": 300,
    "maxTokensPerCall": 15000,
    "createdAt": "2026-08-01T12:00:00Z",
    "ownLlm": null
  },
  "messageCode": null
}
```

`ownLlm` — kompaniyaning o'z LLM'i ([2.1](#21-kompaniyaning-oz-llmi-own-llm)); platforma modelida ishlasa `null`.

### `PUT /api/settings/ai-model` — AI sozlamalarini yangilash
Ixtiyoriy maydon bo'sh (`null`) qoldirilsa, tizim standarti tiklanadi.

**Request Body** (`UpdateAiModelConfigRequest`):
```json
{
  "model": "gemini-3.8-flash",
  "temperature": 0.2,
  "maxOutputTokens": 300,
  "maxCallSeconds": 600,
  "maxTokensPerCall": 20000
}
```

| Maydon | Turi | Cheklov | Izoh |
|---|---|---|---|
| `model` | `string` | katalogdagi id | Ishlatiladigan LLM id'si. Faqat [`GET /api/ai-models`](ai-models.md) qaytargan id'lar qabul qilinadi — boshqasi `400 AI_MODEL_UNKNOWN` |
| `temperature` | `number` | `0.0` - `2.0` | Javob erkinligi/kreativligi (ovozli agentlar uchun `0.2 - 0.4` tavsiya etiladi) |
| `maxOutputTokens` | `number` | min: `1` | Bot bitta gapida qaytishi mumkin bo'lgan maksimal token soni |
| `maxCallSeconds` | `number` | min: `1` | Bitta qo'ng'iroqning maksimal davomiyligi (soniyada). Chegara yetganda bot muloyim xayrlashadi |
| `maxTokensPerCall` | `number` | min: `1` | Bitta qo'ng'iroqda sarflanishi mumkin bo'lgan jami tokenlar limiti |

---

**Xatolar:** `400 AI_MODEL_UNKNOWN`, `400 AI_MODEL_TEMPERATURE_OUT_OF_RANGE` (`temperature` 0.0–2.0 dan tashqarida),
`400 AI_MODEL_MAX_OUTPUT_TOKENS_NOT_POSITIVE`, `400 AI_MODEL_MAX_CALL_SECONDS_NOT_POSITIVE`,
`400 AI_MODEL_MAX_TOKENS_PER_CALL_NOT_POSITIVE` (qiymat 0 yoki manfiy). Oxirgi to'rttasi ilgari umumiy
`400 VALIDATION_FAILED` edi.

### 2.1. Kompaniyaning o'z LLM'i (own LLM)

Kompaniya o'zining OpenAI-compatible LLM endpoint'ini ulaydi (bank, on-prem, vLLM/Ollama/Azure va h.k.).
Ulangandan keyin quyidagilar **faqat shu modelda** ishlaydi:
- kompaniyaning cascade qo'ng'iroqlari — har bir turn, qisqa javoblar uchun fast model, barge-in qarori va
  fact-guard qayta urinishi;
- qo'ng'iroqdan keyingi xulosa (summary) va qayta tahlil (reanalysis).

**Platforma modeliga zaxira (fallback) yo'q.** Kalit o'chirilgan yoki o'qib bo'lmasa, qo'ng'iroq boshlanmaydi
(`OWN_LLM_API_KEY_MISSING`, qo'ng'iroq xatosi sifatida yoziladi), xulosa esa outbox orqali qayta urinadi.
Suhbat platforma modeliga hech qachon jim ketmaydi.

**Qamrovdan tashqari** — platforma modelida qoladi: agent simulyatsiyasi va eval'lar, vidjet text chat,
sifat bahosi (judge), copilot va `REALTIME` pipeline'dagi agentlar (speech-to-speech engine o'z modelida ishlaydi).
Bank "ma'lumot chiqmasin" desa, agentlar cascade pipeline'da bo'lishi kerak.

**Narx:** o'z LLM'idagi qo'ng'iroq va xulosa tokenlari `call_attempt` da statistika uchun yoziladi, lekin
billing'da LLM qatori **0** — kompaniya o'z provayderiga to'laydi. STT, TTS, telefoniya va platforma to'lovi
odatdagidek olinadi.

**Xavfsizlik:** API kalit so'rovda yuborilmaydi va bu yerda saqlanmaydi — avval
[`POST /api/secrets`](secrets.md) bilan secret yaratiladi, bu yerda faqat uning nomi beriladi. Har bir so'rov
SSRF himoyali client orqali ketadi (DNS faqat public manzilga, redirect'siz), manzil saqlashda ham tekshiriladi.

### `PUT /api/settings/ai-model/own-llm` — o'z LLM'ini ulash yoki o'zgartirish
Huquq: **AI_MODEL_EDIT**.

**Request Body** (`UpdateOwnLlmRequest`):
```json
{
  "baseUrl": "https://llm.bank.uz/v1",
  "model": "qwen3-32b",
  "fastModel": "qwen3-8b",
  "apiKeySecret": "OWN_LLM_KEY"
}
```

| Maydon | Turi | Majburiy | Izoh |
|---|---|---|---|
| `baseUrl` | `string` (≤500) | ✅ | OpenAI-compatible ildiz, `/v1` bilan birga (so'rovlar `<baseUrl>/chat/completions` ga ketadi). Faqat public `https`. Oxiridagi `/` olib tashlanadi |
| `model` | `string` (≤100) | ✅ | Endpoint beradigan model id'si. Katalog bilan solishtirilmaydi — endpoint'niki |
| `fastModel` | `string` (≤100) | ❌ | Bir-ikki so'zli javoblar va barge-in qarori uchun arzonroq model. `null` — hammasi `model` da. Agentning `fastLlmModel` i va bosqich (stage) modellari o'z LLM'ida e'tiborga olinmaydi |
| `apiKeySecret` | `string` (≤100) | ✅ | Kalit saqlangan secret nomi. Kalit almashtirilsa, keyingi qo'ng'iroqdan kuchga kiradi |

`temperature` va `maxOutputTokens` — yuqoridagi umumiy sozlamalardan (agent override'lari bilan) olinadi.

**Response** — yangilangan `AiModelConfig`:
```json
{
  "accept": true,
  "data": {
    "companyId": 1,
    "model": "gemini-3.8-flash",
    "temperature": 0.3,
    "maxOutputTokens": 250,
    "maxCallSeconds": 300,
    "maxTokensPerCall": 15000,
    "createdAt": "2026-08-01T12:00:00Z",
    "ownLlm": {
      "baseUrl": "https://llm.bank.uz/v1",
      "model": "qwen3-32b",
      "fastModel": "qwen3-8b",
      "apiKeySecret": "OWN_LLM_KEY"
    }
  },
  "messageCode": null
}
```

`model` (katalogdagi) saqlanib qoladi — o'z LLM'i o'chirilganda va qamrovdan tashqari ishlarda shu ishlatiladi.

**Xatolar:**

| Status | `messageCode` | Qachon |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Majburiy maydon bo'sh yoki juda uzun |
| 400 | `OWN_LLM_BASE_URL_INVALID` | `baseUrl` public `https` manzil emas (ichki IP, `http`, noto'g'ri URL) |
| 400 | `OWN_LLM_API_KEY_SECRET_UNKNOWN` | Bunday nomli secret yo'q |

Audit: `AI_MODEL_OWN_LLM_UPDATE` (detail — `baseUrl`, kalitsiz).

### `DELETE /api/settings/ai-model/own-llm` — platforma modeliga qaytish
Huquq: **AI_MODEL_EDIT**. Javob — `ownLlm: null` bilan `AiModelConfig`. Secret o'chirilmaydi. Audit:
`AI_MODEL_OWN_LLM_DELETE`.

## 3. TTS Ovoz Sozlamalari (`/api/settings/voice`)

Kompaniya qo'ng'iroqlarida bot ovozining gapirish tezligi va ohangini nozik sozlash (tuning).

### `GET /api/settings/voice` — Ovoz sozlamalarini olish
**Response** (`VoiceSettings`):
```json
{
  "accept": true,
  "data": {
    "companyId": 1,
    "speed": 1.05,
    "pitch": 0.0,
    "createdAt": "2026-08-01T12:00:00Z"
  },
  "messageCode": null
}
```

### `PUT /api/settings/voice` — Ovoz sozlamalarini yangilash
**Request Body** (`UpdateVoiceSettingsRequest`):
```json
{
  "speed": 1.05,
  "pitch": -1.5
}
```

| Maydon | Turi | Cheklov | Izoh |
|---|---|---|---|
| `speed` | `number` | `0.25` - `3.0` | Ovoz tezligi ko'paytuvchisi (`1.0` — normal, `1.1` — 10% tezroq) |
| `pitch` | `number` | `-20.0` - `20.0` | Ovoz balandligi/ohangi (yarim tonlarda) |

`speed` chegaradan tashqarida bo'lsa (`0.25` dan kichik yoki `3.0` dan katta) —
`400 VOICE_SPEED_OUT_OF_RANGE`; chegaradan tashqari `pitch` — `400 VALIDATION_FAILED`.
Agentning `voiceSpeed` / `languageVoiceSpeeds` ham aynan shu chegara bilan tekshiriladi
([ai-agents.md](ai-agents.md)). `0.25`–`3.0` — tezlik yetib boradigan barcha sintezatorlar
qabul qiladigan oraliq (Yandex ko'pi bilan `3.0`, Google/OpenAI kamida `0.25`). `V38`
migratsiyasi eski qatorlardagi chegaradan tashqari qiymatlarni chegaraga keltiradi.

---

## 4. Kompaniya Bildirishnomalar Matritsasi (`/api/settings/notifications`)

Kompaniya darajasida qaysi hodisalar qaysi tashqi kanallarga (Telegram bot, Webhook, Email) yetkazilishini sozlash.

Ikki ro'yxatdan iborat: **kanallar** (kanal qayerga yetkazadi va yoqilganmi) va
**matritsa** (qaysi hodisa qaysi kanalga ketadi).

| Enum | Qiymatlar |
|---|---|
| `NotificationChannelType` | `EMAIL`, `WEBHOOK`, `TELEGRAM` |
| `NotificationType` | `CAMPAIGN_FINISHED`, `ERROR_OCCURRED`, `OPERATOR_REQUEST`, `DAILY_REPORT`, `MISSED_CALL`, `VOICEMAIL_RECEIVED`, `LOW_SURVEY_SCORE`, `CAMPAIGN_PAUSED`, `BALANCE_LOW`, `TARGET_SYNC_FAILED`, `SCORECARD_REVIEW`, `ALERT_TRIGGERED` ([notifications.md](notifications.md)) |

### `GET /api/settings/notifications` — Matritsani olish

**Response** (`NotificationSettings`):
```json
{
  "accept": true,
  "data": {
    "channels": [
      { "channel": "EMAIL",    "target": "alerts@company.uz", "enabled": true },
      { "channel": "TELEGRAM", "target": "-100192837465",     "enabled": true },
      { "channel": "WEBHOOK",  "target": "https://crm.example.com/api/voice-events", "enabled": false }
    ],
    "matrix": [
      { "type": "OPERATOR_REQUEST",  "channel": "TELEGRAM", "enabled": true },
      { "type": "ERROR_OCCURRED",    "channel": "EMAIL",    "enabled": true },
      { "type": "CAMPAIGN_FINISHED", "channel": "EMAIL",    "enabled": false },
      { "type": "DAILY_REPORT",      "channel": "EMAIL",    "enabled": false }
    ]
  },
  "message": null,
  "messageCode": null
}
```

| Maydon | Turi | Izoh |
|---|---|---|
| `channels[].channel` | enum | Kanal turi |
| `channels[].target` | string | Kanal manzili: email, Telegram chat id yoki webhook URL |
| `channels[].enabled` | boolean | Kanal umuman ishlaydimi |
| `matrix[].type` / `.channel` / `.enabled` | — | Hodisa × kanal katagi |

### `PUT /api/settings/notifications` — Matritsani saqlash

**Request Body** (`UpdateNotificationSettingsRequest`) — ikkala ro'yxat ham majburiy
(`@NotNull`), ular **to'liq almashtiriladi** (delta emas):

```json
{
  "channels": [
    { "channel": "EMAIL",    "target": "alerts@company.uz", "enabled": true },
    { "channel": "TELEGRAM", "target": "-100192837465",     "enabled": true }
  ],
  "matrix": [
    { "type": "OPERATOR_REQUEST", "channel": "TELEGRAM", "enabled": true },
    { "type": "ERROR_OCCURRED",   "channel": "EMAIL",    "enabled": true }
  ]
}
```

Javob — yangilangan `NotificationSettings`.

Yoqilgan (yoki `target`i to'ldirilgan) har bir kanal tekshiriladi: `EMAIL` — `@` bor manzil,
`TELEGRAM` — bo'sh emas, `WEBHOOK` — **ommaviy** `http(s)` URL (ichki tarmoq, `localhost`,
`169.254.169.254` va h.k. rad etiladi — server bu manzilga POST yuboradi). Mos kelmasa —
`400 NOTIFICATION_CHANNEL_TARGET_INVALID`. Webhook yuborish paytida ham qayta tekshiriladi.

**`WEBHOOK` kanali nima yuboradi:** `POST <target>`, `Content-Type: application/json`,
tanasi `{"type": "<NotificationType>", "title": "...", "message": "...", "link": "..."}`.
Har bir so'rovda `X-RobotCall-Delivery` (UUID) bor; kompaniyada `WEBHOOK_SIGNING_SECRET`
secret'i bo'lsa `X-RobotCall-Signature` bilan imzolanadi — post-call webhook'lar bilan bir
xil format ([webhooks.md](webhooks.md) §3). Taymaut 5 s, redirect'ga ergashilmaydi, qayta
urinish yo'q (bir marta, best-effort).

> ℹ️ Bu **kompaniya darajasidagi tashqi** yetkazish. Panel ichidagi qo'ng'iroq
> ikonkasi (in-app bell) va foydalanuvchining shaxsiy toggle'lari alohida:
> [notifications.md](notifications.md) va
> [profile.md](profile.md#get-apiprofilenotifications--bildirishnomalar-tab).

---

## 5. CRM Integratsiyalari (`/api/settings/integrations`)

Kompaniyalar tashqi CRM tizimlarini o'z hisoblariga to'liq ulashi mumkin (Google Drive, Notion va Asterisk katalogda "tez orada" holatida): **Uysot CRM**, **amoCRM**, **Kommo (Global amoCRM)** va **Bitrix24**.

### `GET /api/settings/integrations/catalog` — Mavjud integratsiyalar katalogi

**Response** (`List<IntegrationCatalogEntry>`, `ResponseData` ichida) — katalog **shu kompaniya ko'zi bilan**:
platforma o'chirgan provayder yashirilmaydi, `DISABLED_BY_PLATFORM` deb belgilanadi.

| Maydon | Izoh |
|---|---|
| `provider` | `UYSOT`, `AMOCRM`, `KOMMO`, `BITRIX24`, `GOOGLE_DRIVE`, `NOTION`, `ASTERISK` |
| `category` | `CRM`, `KNOWLEDGE_BASE`, `TELEPHONY` — katalogni guruhlash uchun |
| `authMethod` | `OAUTH` yoki `API_TOKEN`; `null` — hali `COMING_SOON` |
| `status` | `AVAILABLE` — ulash mumkin; `COMING_SOON` — rejada, konnektor yo'q; `DISABLED_BY_PLATFORM` — platforma bu kompaniya uchun o'chirgan |
| `available` | `status == AVAILABLE` (eski maydon, moslik uchun) |

```json
[
  { "provider": "UYSOT", "displayName": "Uysot CRM", "category": "CRM", "authMethod": "OAUTH", "status": "AVAILABLE", "available": true },
  { "provider": "BITRIX24", "displayName": "Bitrix24", "category": "CRM", "authMethod": "API_TOKEN", "status": "DISABLED_BY_PLATFORM", "available": false },
  { "provider": "GOOGLE_DRIVE", "displayName": "Google Drive", "category": "KNOWLEDGE_BASE", "authMethod": null, "status": "COMING_SOON", "available": false }
]
```

**Platforma tomonidan o'chirilsa** ([companies.md](companies.md), `disabledIntegrations`): shu provayderni ulash
(`PUT /uysot`, `authorize-url`, `PUT /account`) `403 COMPANY_INTEGRATION_DISABLED` beradi; allaqachon ulangan bo'lsa
ham u CRM'ga yozmaydi va o'qimaydi (qo'ng'iroq CRM'siz davom etadi), ulanish ma'lumoti saqlanadi va provayder qayta
yoqilsa ishga tushadi. Uysot o'chirilsa, Uysot webhook ham e'tiborsiz qoladi. Butun `INTEGRATIONS` feature'i o'chirilsa
bu bo'lim umuman yopiladi.

Kompaniyaga bir vaqtda **bitta** CRM ulanadi: Uysot — OAuth orqali (`PUT /uysot` →
`authorize-url` → `callback`), amoCRM / Kommo / Bitrix24 — manzil va token bilan
(`PUT /account`, 5.2–5.3). Qaysi biri ulangan bo'lsa, qo'ng'iroq quvuri o'sha CRM bilan
ishlaydi: kiruvchi qo'ng'iroqda raqam bo'yicha mijozni topish, suhbatdan keyingi izoh va
qo'ng'iroq tarixi. `PUT /account` oldingi ulanishni almashtiradi; Uysot'ga qaytish —
`PUT /uysot`.

### 5.1 Uysot CRM Integratsiyasi
- **Protokol**: Uysot Open API v1 & OAuth 2.0 (`https://apidoc.app.uysot.uz`).
- **Autentifikatsiya**: `X-Open-Api-Token` sarlavhasi bilan so'rovlar yuboriladi.
- **Funksiyalar**:
  - `GET /v1/open-api/lead/{id}` — mijoz/qarz ma'lumotlarini jonli olish.
  - `POST /v1/open-api/lead/{id}/note` — AI xulosa va QA baholarini (`qaScore`, `commitmentScore`) yozish.
  - `POST /v1/open-api/call-history` — audio yozuv va davomiylikni biriktirish.

### `GET /api/settings/integrations` — Joriy integratsiya holati
**Response** (`CrmIntegrationRow`):
```json
{
  "companyId": 1,
  "provider": "UYSOT",
  "appName": "Bizning CRM integratsiyamiz",
  "grants": [
    { "permission": "LEAD", "scope": "READ" },
    { "permission": "CALL", "scope": "SAVE" }
  ],
  "status": "CONNECTED",
  "connectedAt": "2026-08-02T10:00:00Z",
  "accountHost": null
}
```

`accountHost` — ulangan amoCRM / Kommo / Bitrix24 hisobining faqat **hosti**
(`acme.amocrm.ru`); to'liq manzil hech qachon qaytmaydi, chunki Bitrix24 da u maxfiy
kalit. Uysot uchun `null`.

### `PUT /api/settings/integrations/uysot` — Uysot App & Grants sozlash
```json
{
  "appName": "Bizning CRM integratsiyamiz",
  "grants": [
    { "permission": "LEAD", "scope": "READ" },
    { "permission": "CALL", "scope": "SAVE" }
  ]
}
```

`grants[].permission` — `LEAD`, `LEAD_NOTE`, `LEAD_TASK`, `CONTRACT`,
`CONTRACT_PAYMENT`, `CALL`. `grants[].scope` — `READ`, `SAVE`, `DELETE`.
`status` (`CrmIntegrationStatus`) — `NOT_CONNECTED`, `CONNECTED`, `ERROR`.

**Standart holat.** `R__seed_data.sql` birinchi kompaniya uchun bu qatorni allaqachon
yaratadi — `appName: "Uysot Voice Agent"`, status `NOT_CONNECTED` (token yo'q) va platforma
haqiqatan chaqiradigan to'rtta grant:

| Grant | Nima uchun |
|---|---|
| `LEAD:READ` | `GET /lead/{id}`, `POST /lead/filter` — kimga qo'ng'iroq qilinayotgani; kiruvchi qo'ng'iroqda raqam bo'yicha mijozni aniqlash |
| `LEAD_NOTE:SAVE` | `POST /lead-note/{leadId}/list` — har bir suhbatdan keyingi izoh |
| `CONTRACT:READ` | `POST /contract/filter`, `GET /contract/{id}` — kunlik qarzdorlar ro'yxati |
| `CALL:SAVE` | `POST /call-history` — qo'ng'iroqning o'zi lead tarixiga |

`LEAD_TASK`, `CONTRACT_PAYMENT` va hech qanday `DELETE` so'ralmaydi — bu integratsiya
mijozning CRM'idan hech nima o'chirmaydi. Rozilik ekrani shu ro'yxatni kompaniyaga
ko'rsatadi, shuning uchun ishlatilmaydigan ruxsatni so'rash — rad javobini so'rash.

Ro'yxatni shu `PUT` bilan o'zgartirsangiz bo'ladi; reseed uni qayta yozmaydi.

### `GET /api/settings/integrations/uysot/authorize-url` — OAuth avtorizatsiya havolasi

**Response** (`AuthorizeUrlResponse`):

```json
{
  "authorizeUrl": "https://crm.uysot.uz/oauth/authorize?response_type=code&client_id=uysot_app_...&redirect_uri=https%3A%2F%2Fvoice.app.uysot.uz%2Fsettings%2Fintegrations&scope=PERMISSION_OPEN_API_LEAD%3AREAD%20PERMISSION_OPEN_API_CONTRACT%3AREAD&state=..."
}
```

Parametrlar RFC 6749 bo'yicha: `response_type=code`, `client_id`, `redirect_uri`, `scope`,
`state`. `scope` — bo'sh joy (`%20`) bilan ajratilgan `PERMISSION_OPEN_API_<X>:<SCOPE>`
juftliklari; ular `PUT /uysot` da saqlangan `grants` dan yasaladi. Kompaniya so'ralganidan
**kamrog'ini** tasdiqlashi mumkin — haqiqiy ruxsatlar token javobidagi `scope` da keladi.

Frontend bu havolani shunchaki brauzerda ochadi.

### `GET /api/settings/integrations/uysot/callback` — OAuth qaytish nuqtasi

**Permissionsiz** (Uysot brauzerni shu manzilga qaytaradi, foydalanuvchi sessiyasi bilan
emas — kompaniya `state` dan aniqlanadi). `state` — tasodifiy, bir martalik qiymat: server uni
`authorize-url` berilganda kompaniya bilan birga Redis'da saqlaydi (1 soat), callback'da esa
o'qib darhol o'chiradi — ikkinchi marta yoki muddati o'tgach ishlatilgan `state` → `crm=error&reason=OAUTH_STATE_INVALID`.
`authorize-url` Redis ishlamasa `502 OAUTH_STATE_STORE_UNAVAILABLE`, shifrlash kaliti yo'q bo'lsa
`409 ENCRYPTION_KEY_NOT_SET`. Query parametrlar: `code` va `state` —
ikkalasi ham majburiy.

Uysot brauzerni **frontend sahifasiga** (`redirect_uri`) `?code=&state=` bilan qaytaradi;
frontend ikkalasini o'zgartirmasdan shu manzilga **to'liq navigatsiya** bilan uzatadi
(`window.location.replace`, `fetch` emas, `Authorization` header'siz). Batafsil oqim va
namuna kod — [uysot-oauth.md](uysot-oauth.md).

**Javob — `ResponseData` emas, `302 Found`.** (Fayl yuklab olish va SSE bilan bir qatorda
uchinchi istisno: bu yerdagi mijoz — odamning brauzeri.) Token almashtirilgach, brauzer
`voice-agent.integration.uysot.callback-redirect-url` ga yuboriladi:

| Natija | Location |
|---|---|
| Muvaffaqiyat | `http://localhost:5173/settings/integrations?crm=connected` |
| Xato | `http://localhost:5173/settings/integrations?crm=error&reason=<ErrorCode>` |

`reason` — API'ning boshqa joylaridagi bilan bir xil `ErrorCode` nomi
(`UYSOT_OAUTH_TOKEN_EXCHANGE_HTTP_ERROR`, `OAUTH_STATE_INVALID`, …), ya'ni frontend uni
xuddi boshqa xatolar kabi tarjima qiladi. Sahifa ochilganda `GET /api/settings/integrations`
bilan haqiqiy holatni (`status`, `connectedAt`) o'qib olish kerak — query parametr faqat
nima bo'lganini aytadi.

`callback-redirect-url` bo'sh bo'lsa brauzer ilova ildiziga (`/`) yuboriladi.

> ⚠️ **Uysot'da OAuth application yaratayotganda `Redirect URI` sifatida frontend'ning
> integratsiya sahifasi yoziladi**, masalan:
> `https://<frontend-host>/settings/integrations`
>
> Uysot uni **belgi-belgi** solishtiradi (oxirgi `/` ham ahamiyatli) va HTTPS talab qiladi;
> mos kelmasa `6902 — invalid redirect_uri` qaytadi. Xuddi shu qiymat serverda
> `UYSOT_OAUTH_REDIRECT_URI` ga qo'yilishi kerak — token almashishda ham o'sha yuboriladi.
>
> Avtorizatsiya sahifasining hosti ro'yxatdan o'tishda beriladi; agar u
> `https://crm.uysot.uz/oauth/authorize` bo'lmasa — `UYSOT_OAUTH_AUTHORIZE_URL` bilan
> almashtiring.

### `PUT /api/settings/integrations/account` — amoCRM / Kommo / Bitrix24 ulash

`INTEGRATION_EDIT`. Kompaniyaning joriy ulanishini (Uysot bo'lsa ham) almashtiradi va
darhol `CONNECTED` qiladi — rozilik ekrani yo'q. Keyingi qo'ng'iroqdan boshlab quvur shu
CRM bilan ishlaydi.

**Request** (`ConnectCrmAccountRequest`):

```json
{ "provider": "AMOCRM", "baseUrl": "acme.amocrm.ru", "accessToken": "eyJ0eXAiOiJKV1Qi..." }
```

| Maydon | Majburiy | Izoh |
|---|---|---|
| `provider` | ha | `AMOCRM`, `KOMMO` yoki `BITRIX24` |
| `baseUrl` | ha | amoCRM/Kommo: hisob manzili (`acme.amocrm.ru`, `https://acme.kommo.com`); Bitrix24: kiruvchi webhook URL (`https://<portal>/rest/<user>/<kod>/`). Sxemasiz yozilsa `https://` qo'shiladi, oxirgi `/` olib tashlanadi |
| `accessToken` | amoCRM/Kommo uchun ha | amoCRM/Kommo: private integratsiyaning uzoq muddatli tokeni; Bitrix24: kerak emas — webhook URL'ning o'zi kalit |

Manzil ham, token ham `SecretCipher` bilan shifrlangan holda saqlanadi va hech qachon
qaytarilmaydi — javobda faqat `accountHost`.

**Response** — `CrmIntegrationRow`: `provider`, `status: "CONNECTED"`, `accountHost`;
`appName` — `null`, `grants` — `[]`.

**Xatolar:**

| Status | `messageCode` | Qachon |
|---|---|---|
| 400 | `VALIDATION_FAILED` | `provider` yoki `baseUrl` yo'q |
| 400 | `CRM_PROVIDER_USES_OAUTH` | `provider: "UYSOT"` — Uysot faqat OAuth orqali ulanadi |
| 400 | `CRM_ACCESS_TOKEN_REQUIRED` | amoCRM/Kommo uchun `accessToken` bo'sh |
| 400 | `CRM_BASE_URL_INVALID` | manzil `https` emas, host topilmadi yoki ichki tarmoqqa qaraydi (localhost, 10.x, 172.16–31.x, 192.168.x, 169.254.x, Docker tarmog'i …) |
| 409 | `ENCRYPTION_KEY_NOT_SET` | serverda shifrlash kaliti sozlanmagan |

Manzil saqlashda ham, har bir so'rovdan oldin ham tekshiriladi (host keyinroq boshqa IP'ga
qarab qolishi mumkin); redirect'lar kuzatilmaydi. Loglarga faqat host yoziladi.

### `DELETE /api/settings/integrations/uysot` — Integratsiyani uzish
Qaysi provayder ulangan bo'lishidan qat'i nazar, saqlangan token, manzil va ruxsatlarni
o'chiradi (Uysot token'i Uysot tomonida ham bekor qilinadi). `provider` o'zgarmaydi: uzilgan
amoCRM/Kommo/Bitrix24 kompaniyasining qo'ng'iroqlari hech qaysi CRM'ga yozilmaydi —
platformaning Uysot hisobiga ham. Javob — `data: null`.

### `GET /api/settings/integrations/tools` — Agentga beriladigan CRM vositalari
`INTEGRATION_READ`. Integratsiya oynasining **Vositalar** tabi shu ro'yxatni ko'rsatadi.
Ro'yxatni **platforma admini** yuritadi ([platform-integration-tools.md](platform-integration-tools.md)):
platforma o'chirgan vosita bu yerda ko'rinmaydi, har bir vositaning sukut holati (`defaultEnabled`)
ham admin belgilaydi. Kompaniya o'zgartirmagan vosita sukut holatiga **ergashadi** (`inherited: true`),
sukut o'zgarsa u ham o'zgaradi; kompaniya o'zi tanlagan holat esa hech qachon bosilmaydi.

```json
{
  "accept": true,
  "data": [
    {
      "tool": "SEARCH_FLATS",
      "group": "FLATS",
      "label": "Xonadon qidirish va narxlar",
      "description": "Xona soni, byudjet, qavat va majmua bo'yicha sotuvdagi xonadonlar hamda ularning narxi.",
      "permission": "CONTRACT",
      "scope": "READ",
      "writes": false,
      "enabled": true,
      "inherited": true,
      "granted": true
    }
  ]
}
```

| Maydon | Ma'nosi |
|---|---|
| `tool` | vosita kaliti, platforma katalogidan (`PUT` ham shuni ishlatadi) |
| `group` | `FLATS` · `DEBTS` · `LEADS` · `BOOKINGS` — tabdagi guruhlash |
| `permission` / `scope` | vosita uchun CRM tokenida kerak bo'lgan ruxsat; CRM'ga murojaat qilmaydigan vositada `null` |
| `writes` | vosita CRM'da nimadir o'zgartiradi (lid, izoh, vazifa, bosqich) |
| `enabled` | kalit shuni ko'rsatadi: kompaniyaning o'z tanlovi, tanlamagan bo'lsa platforma sukuti |
| `inherited` | kompaniya hali tanlamagan — `enabled` sukut holati va u o'zgarsa birga o'zgaradi |
| `granted` | kompaniyaning Uysot ruxsatlari vositani qoplaydi. `false` bo'lsa agentga vosita **berilmaydi** (`enabled` ga qaramay) — «Ulash» tabida ruxsat berib, qayta ulash kerak |

| `tool` | Guruh | Ruxsat | Nima qiladi |
|---|---|---|---|
| `SEARCH_FLATS` | FLATS | CONTRACT:READ | Sotuvdagi xonadonlar va narxlari |
| `LIST_COMPLEXES` | FLATS | CONTRACT:READ | Majmualar: manzil, qavat, bloklar, topshirish muddati |
| `REMEMBER_FLAT_INTEREST` | FLATS | — | Yoqqan xonadonni mijoz xotirasiga yozadi |
| `GET_CONTRACT_BALANCE` | DEBTS | CONTRACT:READ | Shartnoma summasi, to'langani, qoldiq, kechikish |
| `GET_PAYMENT_SCHEDULE` | DEBTS | CONTRACT:READ | To'lanmagan oylik to'lovlar |
| `GET_DEBT_SUMMARY` | DEBTS | CONTRACT:READ | Barcha faol shartnomalar bo'yicha qarz va kechikkan to'lovlar |
| `GET_CLIENT_PROFILE` | LEADS | LEAD:READ | Raqam bo'yicha mijoz: ismi, lidi, faol shartnomalari |
| `CREATE_LEAD` | LEADS | LEAD:SAVE | CRM'da yo'q mijoz uchun yangi lid |
| `ADD_LEAD_NOTE` | LEADS | LEAD_NOTE:SAVE | Lidga izoh |
| `CREATE_FOLLOW_UP_TASK` | LEADS | LEAD_TASK:SAVE | Menejerga aniq vaqtga vazifa |
| `MOVE_LEAD_STAGE` | LEADS | LEAD:SAVE | Lidni voronkaning boshqa bosqichiga o'tkazadi |
| `REQUEST_MANAGER_CALLBACK` | LEADS | — | Menejer qo'ng'iroq qilishini so'rash (qo'ng'iroq natijasiga yoziladi) |
| `GET_BOOKING_STATUS` | BOOKINGS | BOOKING:READ | Mijozning bronlari ⚠️ Uysot `booking/filter` so'rov sxemasi hujjatda yo'q — real hisobda tekshirilmagan |

> Agentga vositalar faqat kompaniya **Uysot'ga ulangan** bo'lsa beriladi. amoCRM / Kommo /
> Bitrix24 ulangan kompaniyada ro'yxat ko'rinadi, lekin agentga hech narsa berilmaydi.

### `PUT /api/settings/integrations/tools` — Vositalarni tanlash
`INTEGRATION_EDIT`. Tanlangan vositalar yoqiq qoladi, ro'yxatda **yo'q** har biri o'chadi;
bo'sh ro'yxat hammasini o'chiradi.

Saqlashda faqat **sukutdan farq qiladigan** tanlov yoziladi: sukut bilan bir xil qiymat saqlanmaydi, ya'ni
kompaniya o'sha vositada yana sukutga ergashadi.

```json
{ "enabledTools": ["SEARCH_FLATS", "GET_CONTRACT_BALANCE", "CREATE_LEAD"] }
```

Javob — `GET` bilan bir xil ro'yxat (yangilangan holatda). Noma'lum `tool` → `400`
(`INTEGRATION_TOOL_UNKNOWN`). Platforma Uysot'ni o'chirgan kompaniyada — `403
COMPANY_INTEGRATION_DISABLED`. O'zgarish **yangi qo'ng'iroqlardan** boshlab amal qiladi.

### 5.2 amoCRM & Kommo CRM Integratsiyasi

**Kompaniya nima sozlaydi:** amoCRM (Kommo) hisobida private integratsiya yaratadi va uning
*Ключи и доступы* bo'limidan **uzoq muddatli token** (долгосрочный токен) oladi. `baseUrl` —
hisob manzili, `https://<subdomain>.amocrm.ru` (Kommo: `https://<subdomain>.kommo.com`).

Har bir so'rov `Authorization: Bearer <token>` bilan yuboriladi:

- `GET /api/v4/contacts?query={phone}&with=leads` — kiruvchi qo'ng'iroqda raqam bo'yicha kontakt ismi.
- `POST /api/v4/leads/{id}/notes` — suhbatdan keyingi AI izoh (`common`). `{id}` —
  `campaign_target.client_id`, ya'ni kampaniya amoCRM **lead** id'lari bilan import qilinadi.
- `POST /api/v4/calls` — qo'ng'iroq tarixi; raqam bo'yicha kontaktga bog'lanadi, `uniq` —
  platformaning qo'ng'iroq id'si.

Lead'ni id bo'yicha o'qish (qarz summasi va h.k.) yo'q — chiquvchi qo'ng'iroq kampaniya
import qilgan ma'lumotlar bilan ketadi. Qayta qo'ng'iroq vazifasi (`/api/v4/tasks`) ochilmaydi;
qayta qo'ng'iroq vaqti izoh matnida yoziladi.

### 5.3 Bitrix24 Integratsiyasi

**Kompaniya nima sozlaydi:** Bitrix24'da *Разработчикам → Другое → Входящий вебхук*, huquqlar:
**CRM** (`crm`) va **Телефония** (`telephony`). Berilgan URL
`https://<portal>/rest/<user_id>/<kod>/` to'liqligicha `baseUrl` ga yoziladi; `accessToken`
kerak emas. URL ichidagi kod — maxfiy kalit: u shifrlab saqlanadi va hech qayerda
ko'rsatilmaydi.

- `crm.contact.list` (`FILTER[PHONE]`) — kiruvchi qo'ng'iroqda raqam bo'yicha kontakt ismi.
- `crm.timeline.comment.add` — suhbatdan keyingi AI izoh kontakt taymlayniga. `ENTITY_ID` —
  `campaign_target.client_id`, ya'ni kampaniya Bitrix24 **kontakt** id'lari bilan import qilinadi.
- `telephony.externalcall.register` + `telephony.externalcall.finish` — qo'ng'iroq tarixi;
  `USER_ID` — webhook URL'idagi foydalanuvchi.
