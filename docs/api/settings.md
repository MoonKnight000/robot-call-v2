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
    "tts": ["aisha", "cartesia", "gemini", "yandex"],
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
  "authorizeUrl": "https://crm.uysot.uz/oauth/authorize?response_type=code&client_id=uysot_app_...&redirect_uri=https%3A%2F%2Fvoice.app.uysot.uz%2Fapi%2Fsettings%2Fintegrations%2Fuysot%2Fcallback&scope=PERMISSION_OPEN_API_LEAD%3AREAD%20PERMISSION_OPEN_API_CONTRACT%3AREAD&state=..."
}
```

Parametrlar RFC 6749 bo'yicha: `response_type=code`, `client_id`, `redirect_uri`, `scope`,
`state`. `scope` — bo'sh joy (`%20`) bilan ajratilgan `PERMISSION_OPEN_API_<X>:<SCOPE>`
juftliklari; ular `PUT /uysot` da saqlangan `grants` dan yasaladi. Kompaniya so'ralganidan
**kamrog'ini** tasdiqlashi mumkin — haqiqiy ruxsatlar token javobidagi `scope` da keladi.

Frontend bu havolani shunchaki brauzerda ochadi.

### `GET /api/settings/integrations/uysot/callback` — OAuth qaytish nuqtasi

**Permissionsiz** (Uysot brauzerni shu manzilga qaytaradi, foydalanuvchi sessiyasi bilan
emas — kompaniya imzolangan `state` dan aniqlanadi). Query parametrlar: `code` va `state` —
ikkalasi ham majburiy.

Bu manzil frontend tomonidan **chaqirilmaydi** — `authorize-url` dagi `redirect_uri`
sifatida Uysot'ga beriladi va uni brauzer ochadi.

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

> ⚠️ **Uysot'da OAuth application yaratayotganda `Redirect URI` sifatida aynan shu to'liq
> manzil yoziladi**, faqat host o'zinikiga almashtiriladi:
> `https://<backend-host>/api/settings/integrations/uysot/callback`
>
> Uysot uni **belgi-belgi** solishtiradi (oxirgi `/` ham ahamiyatli) va HTTPS talab qiladi;
> mos kelmasa `6902 — invalid redirect_uri` qaytadi. Xuddi shu qiymat serverda
> `UYSOT_OAUTH_REDIRECT_URI` ga qo'yilishi kerak — token almashishda ham o'sha yuboriladi.
>
> Avtorizatsiya sahifasining hosti ro'yxatdan o'tishda beriladi; agar u
> `https://crm.uysot.uz/oauth/authorize` bo'lmasa — `UYSOT_OAUTH_AUTHORIZE_URL` bilan
> almashtiring.

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
