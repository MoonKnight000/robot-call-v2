# Tizim Sozlamalari API (Settings)

`uz.murodjon.robotcallv2` · huquq: sozlama bo'yicha — **AI_MODEL_***, **ENGINE_***, **VOICE_***, **NOTIFICATION_SETTINGS_***, **INTEGRATION_***

Ushbu modul kompaniya darajasidagi barcha global texnik parametrlarni boshqaradi:
1. **Speech Engine** (`/api/settings/engine`) — Nutqni aniqlash va sintezlash pipeline arxitekturasi (`CASCADE` vs `REALTIME`), Pipecat provayderlari.
2. **AI Model & Token Limitlari** (`/api/settings/ai-model`) — LLM modeli, harorat (temperature), tokenlar va qo'ng'iroq vaqti cheklovlari.
3. **TTS Ovoz Sozlamalari** (`/api/settings/voice`) — Tezlik (speed) va ohang (pitch) korreksiyasi.
4. **Kompaniya Bildirishnomalari** (`/api/settings/notifications`) — Telegram, Email va Webhook kanallari bo'yicha hodisalar matritsasi.
5. **CRM Integratsiyalari** (`/api/settings/integrations`) — Uysot CRM, amoCRM, Kommo va Bitrix24 ulanishlari.

---

## 1. Speech Engine Sozlamalari (`/api/settings/engine`)

Kompaniya qo'ng'iroqlari qaysi pipeline va provayderlar orqali ishlashini belgilaydi.

### Pipeline Rejimlari (`PipelineMode`):
- `CASCADE`: STT (Aisha / Yandex / Whisper) ➔ LLM (Gemini / Claude / OpenAI) ➔ TTS (Aisha / Yandex / Google). DTMF klaviatura terishni qo'llab-quvvatlaydi.
- `REALTIME`: Speech-to-speech to'g'ridan-to'g'ri multimodal oqim (Gemini Live, OpenAI Realtime, Qwen-Omni, Pipecat). Minimal kechikish (<500ms).

### `GET /api/settings/engine` — Kompaniya override sozlamalari
Kompaniya tomonidan qo'lda o'rnatilgan sozlamalarni qaytaradi (o'rnatilmagan parametrlar `null`).

**Response** (`EngineConfig`):
```json
{
  "accept": true,
  "data": {
    "companyId": 1,
    "mode": "CASCADE",
    "sttProvider": "aisha",
    "ttsProvider": "yandex",
    "realtimeProvider": null,
    "pipecatStt": null,
    "pipecatLlm": null,
    "pipecatTts": null,
    "createdAt": "2026-08-01T12:00:00Z"
  },
  "messageCode": null
}
```

### `GET /api/settings/engine/effective` — Haqiqiy faol sozlamalar
Kompaniya override'lari va tizim standart (fallback) konfiguratsiyasini birlashtirib, aynan hozir qo'ng'iroqda nima ishlatilishini ko'rsatadi.

**Response** (`EffectiveEngineConfig`):
```json
{
  "accept": true,
  "data": {
    "mode": "CASCADE",
    "sttProvider": "aisha",
    "ttsProvider": "yandex",
    "realtimeProvider": "gemini-live",
    "pipecatStt": null,
    "pipecatLlm": null,
    "pipecatTts": null
  },
  "message": null,
  "messageCode": null
}
```

### `GET /api/settings/engine/options` — Mavjud variantlar katalogi
Platformada joriy vaqtda API kalitlari ulangan va ishlatish mumkin bo'lgan provayderlar ro'yxatini qaytaradi.

**Response** (`EngineOptions`) — maydon nomlari qisqa (`modes`/`sttProviders` **emas**),
va `PipelineMode` ro'yxati umuman qaytarilmaydi (u ikkita qat'iy qiymat: `CASCADE`,
`REALTIME`):

```json
{
  "accept": true,
  "data": {
    "stt": ["aisha", "yandex", "whisper"],
    "tts": ["yandex", "aisha", "google"],
    "realtime": ["gemini-live", "openai-realtime", "pipecat"],
    "pipecatStt": ["deepgram", "whisper"],
    "pipecatLlm": ["openai", "gemini"],
    "pipecatTts": ["cartesia", "elevenlabs"]
  },
  "message": null,
  "messageCode": null
}
```

`pipecat*` ro'yxatlari faqat `realtimeProvider = pipecat` tanlanganda ma'noga ega —
Pipecat ichida qaysi STT/LLM/TTS ishlashini belgilaydi (`V3__pipecat_sub_engines.sql`).

### `PUT /api/settings/engine` — Sozlamalarni yangilash
Maydon qiymati `null` yuborilsa — o'sha parametr tizim standartiga (default) qaytariladi.

**Request Body** (`UpdateEngineConfigRequest`):
```json
{
  "mode": "CASCADE",
  "sttProvider": "aisha",
  "ttsProvider": "aisha",
  "realtimeProvider": null,
  "pipecatStt": null,
  "pipecatLlm": null,
  "pipecatTts": null
}
```

Javob — yangilangan `EngineConfig` (yuqoridagi `GET` shakli).

---

## 2. AI Model & Limit Sozlamalari (`/api/settings/ai-model`)

Dialoglarni boshqaruvchi asosiy til modeli (LLM) va xavfsizlik limitlarini kompaniya miqyosida sozlash.

### `GET /api/settings/ai-model` — Joriy AI konfiguratsiyasi
**Response** (`AiModelConfig`):
```json
{
  "accept": true,
  "data": {
    "companyId": 1,
    "model": "gemini-2.5-flash",
    "temperature": 0.3,
    "maxOutputTokens": 250,
    "maxCallSeconds": 300,
    "maxTokensPerCall": 15000,
    "createdAt": "2026-08-01T12:00:00Z"
  },
  "messageCode": null
}
```

### `PUT /api/settings/ai-model` — AI sozlamalarini yangilash
Ixtiyoriy maydon bo'sh (`null`) qoldirilsa, tizim standarti tiklanadi.

**Request Body** (`UpdateAiModelConfigRequest`):
```json
{
  "model": "gemini-2.5-flash",
  "temperature": 0.2,
  "maxOutputTokens": 300,
  "maxCallSeconds": 600,
  "maxTokensPerCall": 20000
}
```

| Maydon | Turi | Cheklov | Izoh |
|---|---|---|---|
| `model` | `string` | — | Ishlatiladigan LLM nomi (`gemini-2.5-flash`, `gpt-4o-mini`) |
| `temperature` | `number` | `0.0` - `2.0` | Javob erkinligi/kreativligi (ovozli agentlar uchun `0.2 - 0.4` tavsiya etiladi) |
| `maxOutputTokens` | `number` | min: `1` | Bot bitta gapida qaytishi mumkin bo'lgan maksimal token soni |
| `maxCallSeconds` | `number` | min: `1` | Bitta qo'ng'iroqning maksimal davomiyligi (soniyada). Chegara yetganda bot muloyim xayrlashadi |
| `maxTokensPerCall` | `number` | min: `1` | Bitta qo'ng'iroqda sarflanishi mumkin bo'lgan jami tokenlar limiti |

---

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
| `speed` | `number` | `0.1` - `3.0` | Ovoz tezligi ko'paytuvchisi (`1.0` — normal, `1.1` — 10% tezroq) |
| `pitch` | `number` | `-20.0` - `20.0` | Ovoz balandligi/ohangi (yarim tonlarda) |

---

## 4. Kompaniya Bildirishnomalar Matritsasi (`/api/settings/notifications`)

Kompaniya darajasida qaysi hodisalar qaysi tashqi kanallarga (Telegram bot, Webhook, Email) yetkazilishini sozlash.

Ikki ro'yxatdan iborat: **kanallar** (kanal qayerga yetkazadi va yoqilganmi) va
**matritsa** (qaysi hodisa qaysi kanalga ketadi).

| Enum | Qiymatlar |
|---|---|
| `NotificationChannelType` | `EMAIL`, `WEBHOOK`, `TELEGRAM` |
| `NotificationType` | `CAMPAIGN_FINISHED`, `ERROR_OCCURRED`, `OPERATOR_REQUEST`, `DAILY_REPORT` |

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

> ℹ️ Bu **kompaniya darajasidagi tashqi** yetkazish. Panel ichidagi qo'ng'iroq
> ikonkasi (in-app bell) va foydalanuvchining shaxsiy toggle'lari alohida:
> [notifications.md](notifications.md) va
> [profile.md](profile.md#get-apiprofilenotifications--bildirishnomalar-tab).

---

## 5. CRM Integratsiyalari (`/api/settings/integrations`)

Kompaniyalar tashqi CRM tizimlarini o'z hisoblariga to'liq ulashi mumkin: **Uysot CRM**, **amoCRM**, **Kommo (Global amoCRM)** va **Bitrix24**.

### `GET /api/settings/integrations/catalog` — Mavjud integratsiyalar katalogi

**Response** (`List<CrmCatalogEntry>`, `ResponseData` ichida). `authMethod` —
`OAUTH` yoki `API_TOKEN`; `available: false` — provayder katalogda ko'rinadi, lekin hali
ulanmaydi.

```json
[
  { "provider": "UYSOT", "displayName": "Uysot CRM", "authMethod": "OAUTH", "available": true },
  { "provider": "AMOCRM", "displayName": "amoCRM", "authMethod": "OAUTH", "available": true },
  { "provider": "KOMMO", "displayName": "Kommo CRM (Global amoCRM)", "authMethod": "OAUTH", "available": true },
  { "provider": "BITRIX24", "displayName": "Bitrix24", "authMethod": "OAUTH", "available": true }
]
```

> ⚠️ Yozish/uzish endpointlari **faqat Uysot uchun** mavjud (`/uysot`). amoCRM, Kommo va
> Bitrix24 uchun REST endpoint hali yo'q — quyidagi 5.2/5.3 bo'limlari rejalashtirilgan
> integratsiya doirasini tasvirlaydi.

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
  "connectedAt": "2026-08-02T10:00:00Z"
}
```

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

### `GET /api/settings/integrations/uysot/authorize-url` — OAuth avtorizatsiya havolasi

**Response** (`AuthorizeUrlResponse`):

```json
{
  "authorizeUrl": "https://app.uysot.uz/oauth/authorize?client_id=...&app_name=...&redirect_url=...&grants=...&state=..."
}
```

### `GET /api/settings/integrations/uysot/callback` — OAuth qaytish nuqtasi

**Permissionsiz** (Uysot brauzerni shu manzilga qaytaradi, foydalanuvchi sessiyasi bilan
emas). Query parametrlar: `code` va `state` — ikkalasi ham majburiy. Tokenni almashtirib
saqlaydi; javob `data: null`.

Bu manzil frontend tomonidan chaqirilmaydi — `authorize-url` dagi `redirect_url` sifatida
Uysot'ga beriladi.

### `DELETE /api/settings/integrations/uysot` — Integratsiyani uzish
Tizimdan saqlangan token va ruxsatlarni bekor qiladi. Javob — `data: null`.

### 5.2 amoCRM & Kommo CRM Integratsiyasi
- **Funksiyalar**:
  - `/api/v4/contacts?query={phone}&with=leads` — Telefon orqali kontakt va faol bitimni aniqlash.
  - `/api/v4/leads/{id}/notes` — AI qo'ng'iroq xulosasi, sentiment va QA bahosini yozish.
  - `/api/v4/calls` — Telefoniya pleyeriga audio yozuvni biriktirish.
  - `/api/v4/tasks` — Qayta qo'ng'iroq vazifalarini avtomatik ochish.

### 5.3 Bitrix24 Integratsiyasi
- **Funksiyalar**:
  - `crm.contact.list` / `crm.lead.list` — Telefon orqali kontakt topish.
  - `crm.timeline.comment.add` — Bitim kartochkasi taymlayniga AI sharhini qoldirish.
  - `telephony.externalcall.register/finish` — Audio yozuv va davomiylikni biriktirish.
  - `tasks.task.add` — Qayta qo'ng'iroq vazifasini yaratish.
