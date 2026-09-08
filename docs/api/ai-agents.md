# AI Agentlar — `/api/ai-agents`

**Permission:** `AI_AGENT_READ` (ko'rish) · `AI_AGENT_EDIT` (yaratish/tahrirlash/o'chirish)

---

## 1. QuickVoice AI Agent Arxitekturasi

Tizimda AI Agentlar **QuickVoice** arxitekturasi asosida to'liq yangilandi. Endi AI Agent o'zining mustaqil **Behavior** (xulq-atvor), **Speech/Voice** (STT/TTS sozlamalari), **Analysis** (ma'lumot yig'ish va baholash), **Tools** (tashqi API chaqiruvlari) va **Limits & Webhooks** bo'limlariga ega:

```
┌─────────────────────────────────────────────────────────────┐
│                       AI AGENT                              │
│                                                             │
│  1. BEHAVIOR & TEMPLATE                                     │
│     • templateId: BUSINESS | SUPPORT | MEDICAL | BLANK      │
│     • firstMessage: 0ms kechikish bilan ochilish salomi     │
│     • systemPrompt: agentning roli, skripti va qoidalari    │
│     • scenarioId: ixtiyoriy (tashqi FSM senariy yoki null)  │
│     • preemptiveGeneration / ivrNavigation / useRag         │
│                                                             │
│  2. SPEECH & VOICE (Agent darajasida)                       │
│     • sttProvider, sttModel (yandex/gemini/aisha/           │
│       deepgram/openai)                                      │
│     • ttsProvider, ttsModel, ttsVoice                       │
│     • voiceSpeed, voiceStability, voiceSimilarityBoost      │
│     • languageVoices (ko'p tilli moslashuv)                 │
│                                                             │
│  3. ANALYSIS & DATA EXTRACTION                              │
│     • dataNeeded (suhbat davomida yig'iladigan maydonlar)   │
│     • dataEvaluation (qo'ng'iroq sifatini baholash mezonlari│
│                                                             │
│  4. TOOLS (Agentga biriktirilgan funksiyalar)               │
│     • dynamic REST API tool chaqiruvlari                    │
│     • preToolSpeech (foydalanuvchiga bildirish jumlasi)     │
│                                                             │
│  5. RETENTION & LIMITS                                      │
│     • zeroPiiRetention, storeCallAudio, retentionDays       │
│     • maxDuration, silenceTimeout, turnTimeout              │
│     • concurrentCallsLimit, dailyCallsLimit                 │
│                                                             │
│  6. WEBHOOKS                                                │
│     • initiationWebhook (qo'ng'iroq boshlanishida)          │
│     • postCallWebhook (qo'ng'iroq tugagach tahlil yuborish) │
└─────────────────────────────────────────────────────────────┘
```

> [!NOTE]
> **Senariy va Agent bog'liqligi:**
> Agentning o'zida `systemPrompt` va `firstMessage` belgilangan bo'lsa, tashqi senariy yaratish shart emas (`scenarioId: null`). Tizim agent uchun avtomatik ravishda dinamik senariy hosil qiladi (`resolveScenario`). Agar agentga tashqi murakkab FSM senariy kerak bo'lsa, `scenarioId` ko'rsatilishi mumkin.

---

## 2. Endpointlar

| Metod | URL | Permission | Tavsif |
|---|---|---|---|
| `GET` | `/api/ai-agents/templates` | `AI_AGENT_READ` | Tayyor shablonlar (Business, Support, Medical, Blank) |
| `POST` | `/api/ai-agents` | `AI_AGENT_EDIT` | Yangi AI agent yaratish |
| `POST` | `/api/ai-agents/filter` (yoki `/list`) | `AI_AGENT_READ` | Qidiruv va sahifalangan ro'yxat |
| `GET` | `/api/ai-agents/{id}` | `AI_AGENT_READ` | Agent ma'lumotlarini olish |
| `PUT` | `/api/ai-agents/{id}` | `AI_AGENT_EDIT` | Agentni to'liq tahrirlash |
| `DELETE` | `/api/ai-agents/{id}` | `AI_AGENT_EDIT` | O'chirish (bog'langan kampaniyalar bo'lsa `409`) |
| `GET` | `/api/ai-agents/{id}/scenario` | `AI_AGENT_READ` | Agent ssenariysi va xulq-atvori (`AgentScenarioDto`) |
| `PUT` | `/api/ai-agents/{id}/scenario` | `AI_AGENT_EDIT` | Agent ssenariysini yangilash |
| `GET` | `/api/ai-agents/{id}/analysis` | `AI_AGENT_READ` | Agent tahlil mezonlari (`dataNeeded`, `dataEvaluation`) |
| `PUT` | `/api/ai-agents/{id}/analysis` | `AI_AGENT_EDIT` | Agent tahlil mezonlarini yangilash |
| `GET` | `/api/ai-agents/{id}/tools` | `AI_AGENT_READ` | Agentga biriktirilgan amallar (Tools) ro'yxati |
| `POST` | `/api/ai-agents/{id}/tools/{toolId}` | `AI_AGENT_EDIT` | Toolni agentga biriktirish |
| `DELETE` | `/api/ai-agents/{id}/tools/{toolId}` | `AI_AGENT_EDIT` | Toolni agentdan ajratish |

---

## 3. `GET /api/ai-agents/templates`

Agent yaratishdan oldin mavjud shablonlar va ularning default parametrlarini olish:

**Query parametr:**
- `language` (ixtiyoriy): `uz-UZ` (default), `ru-RU`, `en-US`.

**Response:**
```json
{
  "accept": true,
  "data": [
    {
      "id": "BUSINESS",
      "name": "Business Agent",
      "description": "General purpose business calls, lead qualification, and customer questions",
      "defaults": {
        "systemPrompt": "Siz kompaniyaning rasmiy savdo va xizmat ko'rsatish bo'yicha maslahatchisisiz...",
        "firstMessage": "Assalomu alaykum! Sizga qanday yordam bera olaman?",
        "dataNeeded": [
          { "id": "client_name", "type": "string", "name": "Mijoz ismi", "description": "Mijozning to'liq ismi" },
          { "id": "interest_level", "type": "string", "name": "Qiziqish darajasi", "description": "HIGH, MEDIUM, LOW" }
        ],
        "dataEvaluation": [
          { "id": "goal_achieved", "name": "Maqsadga erishildi", "criteria": "Mijoz talabi aniqlandimi" },
          { "id": "politeness", "name": "Xushmuomalalik", "criteria": "Agent va mijoz ohangi" }
        ]
      }
    }
  ]
}
```

---

## 4. `POST /api/ai-agents` — Agent yaratish

```json
{
  "name": "Operator Dilnoza (Savdo va Qabul)",
  "description": "Kiruvchi mijozlarni kutib oluvchi va savollariga javob beruvchi bot",
  "templateId": "BUSINESS",
  "scenarioId": null,
  "firstMessage": "Assalomu alaykum! Kompaniyamizga xush kelibsiz. Sizga qanday yordam bera olaman?",
  "systemPrompt": "Siz kompaniyaning samimiy savdo menejerisiz. Mijozning ismini va qiziqayotgan mahsulotini aniqlang.",
  "preemptiveGeneration": true,
  "ivrNavigationEnabled": true,
  "useRag": true,
  "language": "uz-UZ",
  "sttProvider": "gemini",
  "sttModel": "gemini-3.5-transcribe-live",
  "ttsProvider": "gemini",
  "ttsModel": "gemini-3.1-flash-tts-preview",
  "ttsVoice": "gemini-tts-aoede-uz",
  "voiceSpeed": 1.05,
  "voiceStability": 0.8,
  "voiceSimilarityBoost": 0.8,
  "languageVoices": {
    "uz-UZ": "gemini-tts-aoede-uz",
    "ru-RU": "gemini-tts-aoede-ru"
  },
  "persona": "AI_ASSISTANT",
  "llmModel": "gemini-3.8-flash",
  "fastLlmModel": "gemini-3.5-flash-lite",
  "temperature": 0.6,
  "maxOutputTokens": 300,
  "dataNeeded": [
    { "id": "client_name", "type": "string", "name": "Mijoz ismi", "description": "Mijozning to'liq ismi" },
    { "id": "product_name", "type": "string", "name": "Mahsulot nomi", "description": "Mijoz qiziqqan tovar" }
  ],
  "dataEvaluation": [
    { "id": "satisfaction", "name": "Mijoz roziligi", "criteria": "Mijoz ijobiy kayfiyatda xayrlashdimi" }
  ],
  "zeroPiiRetention": false,
  "storeCallAudio": true,
  "conversationRetentionDays": 60,
  "maxConversationDurationSeconds": 600,
  "silenceEndCallTimeoutSeconds": 25,
  "turnTimeoutSeconds": 10,
  "concurrentCallsLimit": 15,
  "dailyCallsLimit": 500,
  "initiationWebhook": {
    "url": "https://api.example.com/webhooks/call-started",
    "headers": { "Authorization": "Bearer secret_token" },
    "timeoutSeconds": 5
  },
  "postCallWebhook": {
    "url": "https://api.example.com/webhooks/call-ended",
    "headers": { "Authorization": "Bearer secret_token" },
    "timeoutSeconds": 10
  },
  "ambientSound": "OFFICE",
  "ambientSoundVolume": 0.35,
  "ambientSoundFadeInSeconds": 1.5,
  "thinkingSound": "OFF",
  "thinkingSoundVolume": 0.35,
  "noiseCancellationEnabled": true,
  "noiseCancellationMode": "BACKGROUND_NOISE_SUPPRESSION",
  "emotionAdaptiveVoice": true,
  "dtmfInputEnabled": true,
  "voicemailAction": "LEAVE_MESSAGE",
  "voicemailMessage": "Assalomu alaykum, siz bilan bog'lana olmadik. Iltimos qaytib qo'ng'iroq qiling.",
  "midCallSmsEnabled": false,
  "midCallSmsTemplate": null,
  "sipTrunkIds": [1, 2],
  "enabled": true
}
```

> **Speech engine maydonlari qo'ng'iroqda qanday qo'llanadi:**
> - `sttProvider` — shu agent qo'ng'iroqlarini tanigan provayder. Build'da yo'q bo'lsa
>   (`GET /api/ai-agents/engine-options` dagi `stt` ro'yxatida yo'q), `voice-agent.stt.provider`
>   qiymatiga tushadi.
> - `sttModel` — o'sha provayderning model nomi. Katalogdan tanlanadi
>   (`GET /api/ai-models?kind=STT`), noma'lum id `400 STT_MODEL_UNKNOWN` beradi. Faqat agent
>   tanlagan provayder ishlayotganda yuboriladi: provayder topilmasa yoki qo'ng'iroq o'rtasida
>   boshqa vendorga o'tib ketsa, o'sha vendorning o'z modeli ishlatiladi. Aisha'da model
>   tanlash yo'q — e'tiborsiz qoldiriladi.
> - `ttsModel` — sintez modeli, xuddi shunday katalogdan (`?kind=TTS`), noma'lum id
>   `400 TTS_MODEL_UNKNOWN`. Yandex va Aisha TTS da tanlanadigan model yo'q.
> - Model va provayder **juft** saqlanadi: `sttModel` tanlansa, `sttProvider` o'sha modelning
>   egasi bo'lishi shart (`nova-3` → `deepgram`), aks holda `400 STT_MODEL_PROVIDER_MISMATCH`
>   (TTS uchun `TTS_MODEL_PROVIDER_MISMATCH`). Sababi — ikkala maydon qo'ng'iroqda birga
>   o'qiladi, va mos kelmagan juft vendorga u bilmaydigan id bo'lib boradi.
> - `realtimeProvider` — `pipelineMode=REALTIME` agentning speech-to-speech engine'i
>   (`GET /api/ai-agents/engine-options` dagi `realtime` ro'yxati: `gemini-live`,
>   `openai-realtime`, `pipecat`, …).
> - `llmModel` — `CASCADE`da matn modeli, `REALTIME`da esa **o'sha engine'ning modeli**.
>   Katalogdan tanlanadi (`GET /api/ai-models?kind=LLM&mode=<agent rejimi>`), noma'lum id
>   `400 AI_MODEL_UNKNOWN`. `REALTIME`da model ham provayder bilan **juft**: modelning
>   `provider` i `realtimeProvider` bilan bir xil bo'lishi shart, aks holda
>   `400 REALTIME_MODEL_PROVIDER_MISMATCH`. Shuning uchun formada avval engine, keyin model
>   tanlanadi va model ro'yxati `provider` bo'yicha filtrlanadi.
>   Gemini Live uchun ikkita model bor: **`gemini-3.1-flash-live-preview` (standart)** va
>   `gemini-2.5-flash-native-audio-latest` (barqarorroq, tool chaqirish ishonchliroq).
>   `llmModel` bo'sh qoldirilsa engine deployment sozlamasidagi modelda ishlaydi — Gemini
>   Live uchun bu `GEMINI_LIVE_MODEL`, ya'ni o'sha `gemini-3.1-flash-live-preview`. Shuning
>   uchun forma yangi agentda shu qatorni oldindan tanlangan qilib ko'rsatishi mumkin.
> - `fastLlmModel` — mijoz juda qisqa javob bergan turn (`ha`, `xo'p`, `tushunarli`) shu
>   arzonroq modelda javoblanadi; qolgan hamma turn `llmModel` da qoladi. Faqat
>   `CASCADE` uchun — `REALTIME` engine'da turn-ma-turn model tanlash yo'q. Katalogdan
>   tanlanadi va `llmModel` bilan bir xil tekshiruvdan o'tadi (noma'lum id →
>   `400 AI_MODEL_UNKNOWN`). Bo'sh qoldirilsa o'rnatmaning umumiy sozlamasi
>   (`DIALOG_FAST_MODEL`) ishlaydi. **Alohida o'chirish tugmasi yo'q:** arzon modelni
>   umuman xohlamagan agent `fastLlmModel` ga `llmModel` bilan bir xil id yozadi.
>   Raqam aytilgan javob ("yetti", "20") qisqa bo'lsa ham doim `llmModel` ga boradi — o'sha
>   turn to'lov sanasini belgilaydi.
> - `ttsVoice` / `languageVoices` — tanlangan ovoz **o'z provayderi** bilan gapiradi
>   (`tts_voice.provider`), chunki bir vendorning ovoz id'si boshqasida mavjud emas.
> - `ttsProvider` — ovoz tanlanmagan (yoki ovozning provayderi build'dan chiqib ketgan)
>   qo'ng'iroq qaysi vendor bilan gapirishini belgilaydi; kompaniya sozlamasi shunda zaxira
>   bo'lib qoladi.

> ⚠️ **Webhook manzillari ommaviy bo'lishi shart.** `initiationWebhook.url` va
> `postCallWebhook.url` xususiy tarmoq manziliga (`127.0.0.1`, `localhost`, `10/8`,
> `172.16/12`, `192.168/16`, `169.254/16`, `fc00::/7`) qaralsa agent saqlanmaydi —
> `400 WEBHOOK_URL_INVALID`. Ichida `{{secrets.KEY}}` bo'lgan manzil saqlashda
> tekshirilmaydi, lekin yuborish paytida tekshiriladi. Imzo (`X-RobotCall-Signature`)
> va tekshirish tartibi — [webhooks.md](webhooks.md) §3.

---

## 5. `POST /api/ai-agents/filter` — Ro'yxat va qidiruv

```json
{
  "page": 0,
  "size": 20,
  "orders": { "CREATED_AT": "DESC" },
  "search": "savdo",
  "templateId": "BUSINESS",
  "enabled": true
}
```

---

## 6. `PUT /api/ai-agents/{id}` — Tahrirlash

`POST` bilan bir xil parametrlar qabul qiladi. O'zgartirilgan maydonlar keyingi qo'ng'iroqlardan darhol kuchga kiradi.

---

## 7. `DELETE /api/ai-agents/{id}` — O'chirish

Agent faol kampaniyalar yoki kiruvchi marshrutlarga bog'langan bo'lsa o'chirilmaydi va `409 AI_AGENT_IN_USE` qaytaradi.

---

## 8. Agent Scenario & Behavior — `/api/ai-agents/{id}/scenario`

AI Agentning bevosita o'ziga biriktirilgan ssenariy va suhbat yo'nalishi (Behavior / Scenario).
Agent prompt orqali (`PROMPT` rejimi) yoki bosqichma-bosqich FSM holat mashinasi (`STRUCTURED_STEPS` rejimi) asosida boshqariladi.

### `GET /api/ai-agents/{id}/scenario`
```json
{
  "accept": true,
  "data": {
    "scenarioMode": "PROMPT",
    "firstMessage": "Assalomu alaykum! Sizga qanday yordam bera olaman?",
    "systemPrompt": "Siz kompaniyaning professional AI maslahatchisisiz. Mijoz bilan xushmuomala so'zlashib, savollariga aniq va lo'nda javob bering.",
    "stages": [
      {
        "id": "GREETING",
        "purpose": "Mijoz bilan salomlashish va ehtiyojini aniqlash",
        "allowedTransitions": ["OFFER_SERVICES", "SUPPORT"],
        "allowedTools": []
      }
    ],
    "guardrails": [
      "Kompaniya ichki narx siyosatidan tashqari va'dalar bermang",
      "Raqobatchilar haqida salbiy fikr bildirmang"
    ],
    "factSchema": [
      {
        "name": "client_name",
        "type": "STRING",
        "description": "Mijozning ismi",
        "required": false
      }
    ],
    "disclosureText": "Suhbat sifatini oshirish maqsadida yozib olinadi."
  },
  "message": null,
  "messageCode": null,
  "errors": null
}
```

### `PUT /api/ai-agents/{id}/scenario`
Yuqoridagi maydonlarni yuborish orqali agentning ssenariy va xulq-atvorini yangilash mumkin.

---

## 9. Agent Analysis — `/api/ai-agents/{id}/analysis`

QuickVoice uslubidagi mustaqil `Analysis` bo'limi. Agent butun konfiguratsiyasini qayta yubormasdan faqat uning suhbatdan ma'lumot ajratish (`dataNeeded`) va suhbat sifatini baholash (`dataEvaluation`) mezonlarini boshqarish:

### `GET /api/ai-agents/{id}/analysis`
```json
{
  "accept": true,
  "data": {
    "dataNeeded": [
      {
        "id": "order_number",
        "type": "string",
        "name": "Buyurtma raqami",
        "description": "Mijoz aytgan buyurtma yoki shartnoma kodi"
      },
      {
        "id": "preferred_time",
        "type": "string",
        "name": "Qulay vaqt",
        "description": "Mijozga yetkazib berish uchun qulay vaqt oralig'i"
      }
    ],
    "dataEvaluation": [
      {
        "id": "address_confirmed",
        "name": "Manzil tasdiqlandi",
        "criteria": "Mijoz o'z manzilini tasdiqlagan yoki yo'qligi"
      },
      {
        "id": "polite_agent",
        "name": "Agent odobi",
        "criteria": "Agent xushmuomala so'zlashgani"
      }
    ]
  },
  "message": null,
  "messageCode": null,
  "errors": null
}
```

### `PUT /api/ai-agents/{id}/analysis`
Yuqoridagi formatdagi JSON obyekt yuboriladi va agentning tahlil mezonlari yangilanadi.

---

## 10. Agent Tools — `/api/ai-agents/{id}/tools`

QuickVoice uslubida agentga biriktirilgan tashqi API amallarini (Tools) ko'rish va biriktirish:

### `GET /api/ai-agents/{id}/tools`
Agentga biriktirilgan barcha Tool'lar ro'yxatini qaytaradi (`List<ToolRow>`).

### `POST /api/ai-agents/{id}/tools/{toolId}`
Ko'rsatilgan `toolId` ni agentga biriktiradi. Keyingi qo'ng'iroqda LLM kerak bo'lganda ushbu toolni chaqira oladi.

### `DELETE /api/ai-agents/{id}/tools/{toolId}`
Toolni agentdan ajratadi (tool o'zi o'chib ketmaydi, faqat agentdan ajratiladi).


---

## 10. Agent Limits — `/api/ai-agents/{id}/limits`

Agent darajasidagi xavfsizlik va qo'ng'iroq cheklovlari (QuickVoice Limits bo'limi):

### `GET /api/ai-agents/{id}/limits`
```json
{
  "accept": true,
  "data": {
    "concurrentCallsLimit": 15,
    "dailyCallsLimit": 500,
    "maxConversationDurationSeconds": 600,
    "silenceEndCallTimeoutSeconds": 25,
    "turnTimeoutSeconds": 10
  },
  "message": null,
  "messageCode": null,
  "errors": null
}
```

### `PUT /api/ai-agents/{id}/limits`
Yuqoridagi formatdagi JSON yuborilib cheklovlar tahrirlanadi.

---

## 11. Agent Advanced Settings — `/api/ai-agents/{id}/advanced`

Agentning chuqur sozlamalari (QuickVoice Advanced bo'limi: RAG, audio saqlash, maxfiylik, webhooklar, DTMF, SMS, ambient shovqin):

### `GET /api/ai-agents/{id}/advanced`

`useRag` — agent kompaniya hujjatlaridan javob bera oladimi. Yoqilsa har bir turda
mijozning savoli bo'yicha [bilim manbalari](knowledge-base.md) qidiriladi va topilgan
parchalar modelning promptiga qo'yiladi; REALTIME rejimida esa model `searchKnowledgeBase`
tool'ini o'zi chaqiradi. O'chirilgan bo'lsa qidiruv umuman ishlamaydi va bitta ham
qo'shimcha so'rov ketmaydi.

```json
{
  "accept": true,
  "data": {
    "useRag": true,
    "preemptiveGeneration": true,
    "ivrNavigationEnabled": false,
    "storeCallAudio": true,
    "zeroPiiRetention": false,
    "conversationRetentionDays": 60,
    "initiationWebhook": {
      "url": "https://api.example.com/webhooks/call-started",
      "headers": { "Authorization": "Bearer secret_token" },
      "timeoutSeconds": 5
    },
    "postCallWebhook": {
      "url": "https://api.example.com/webhooks/call-ended",
      "headers": { "Authorization": "Bearer secret_token" },
      "timeoutSeconds": 10
    },
    "ambientSound": "OFFICE",
    "ambientSoundVolume": 0.35,
    "ambientSoundFadeInSeconds": 1.5,
    "thinkingSound": "KEYBOARD_TYPING",
    "thinkingSoundVolume": 0.30,
    "noiseCancellationEnabled": true,
    "noiseCancellationMode": "BACKGROUND_NOISE_SUPPRESSION",
    "emotionAdaptiveVoice": true,
    "dtmfInputEnabled": true,
    "voicemailAction": "LEAVE_MESSAGE",
    "voicemailMessage": "Assalomu alaykum, siz bilan bog'lana olmadik. Iltimos qaytib qo'ng'iroq qiling.",
    "midCallSmsEnabled": false,
    "midCallSmsTemplate": null
  },
  "message": null,
  "messageCode": null,
  "errors": null
}
```

### `PUT /api/ai-agents/{id}/advanced`
Yuqoridagi formatdagi JSON yuboriladi. **Qoida**: agar `zeroPiiRetention` true bo'lsa, `storeCallAudio` true bo'lishi mumkin emas (`400 ZERO_PII_STORE_AUDIO_CONFLICT`).


---

## 12. Agent Pronunciation & Phonetic Dictionary — `/api/ai-agents/{id}/pronunciation`

TTS modeli so'zlashayotganda qisqartmalar, xorijiy atamalar yoki sohaviy so'zlarni xatosiz talaffuz qilishi uchun maxsus fonetik almashtirish lug'ati (QuickVoice Pronunciation).

### `GET /api/ai-agents/{id}/pronunciation`
```json
{
  "accept": true,
  "data": {
    "rules": [
      {
        "word": "MCHJ",
        "replacement": "mas'uliyati cheklangan jamiyat",
        "caseSensitive": false,
        "language": "uz"
      },
      {
        "word": "100k",
        "replacement": "yuz ming",
        "caseSensitive": false,
        "language": "uz"
      },
      {
        "word": "Uzcard",
        "replacement": "uz kard",
        "caseSensitive": false,
        "language": "*"
      }
    ]
  },
  "message": null,
  "messageCode": null,
  "errors": null
}
```

### `PUT /api/ai-agents/{id}/pronunciation`
```json
{
  "rules": [
    {
      "word": "Humo",
      "replacement": "xumo",
      "caseSensitive": false,
      "language": "uz"
    }
  ]
}
```

---

## 13. Post-Call Actions & Automated Follow-ups — `/api/ai-agents/{id}/post-call-actions`

Qo'ng'iroq muvaffaqiyatli yakunlangach yoki ma'lum bir natijaga erishilgach avtomatik ishga tushuvchi amallar: SMS yuborish, CRM webhook chaqirish yoki Telegram operator guruhiga xabar berish.

### `GET /api/ai-agents/{id}/post-call-actions`
```json
{
  "accept": true,
  "data": {
    "actions": [
      {
        "id": "order-sms",
        "name": "Buyurtma tasdiq SMS",
        "actionType": "SEND_SMS",
        "triggerDisposition": "COMPLETED",
        "onlyOnSuccess": true,
        "template": "Assalomu alaykum! Buyurtmangiz qabul qilindi. Xulosa: {{summary}}",
        "webhookUrl": null,
        "headers": null,
        "enabled": true
      },
      {
        "id": "crm-webhook",
        "name": "CRM Sync Webhook",
        "actionType": "WEBHOOK",
        "triggerDisposition": null,
        "onlyOnSuccess": false,
        "template": null,
        "webhookUrl": "https://crm.example.com/api/call-completed",
        "headers": {
          "Authorization": "Bearer crm_secret"
        },
        "enabled": true
      }
    ]
  },
  "message": null,
  "messageCode": null,
  "errors": null
}
```

### `PUT /api/ai-agents/{id}/post-call-actions`
Yuqoridagi formatdagi amallar massivini yuborish orqali yangilanadi.

---

## 🌱 Seed'dagi tayyor agentlar

Yangi baza ko'tarilganda (`R__seed_data.sql`) uchta agent tayyor turadi — bittasi kiruvchi
qo'ng'iroqlar uchun, ikkitasi chiquvchi qarzdorlik qo'ng'iroqlari uchun (pipeline'lari
bilan farq qiladi):

| Nomi | Yo'nalish | `pipelineMode` | Senariy | Engine |
|---|---|---|---|---|
| `Kiruvchi qabulxona` | kiruvchi | `CASCADE` | `reception` | Yandex STT/TTS + `gemini-3.8-flash` |
| `Qarzdorlik (cascade)` | chiquvchi | `CASCADE` | `debt-collection` | Yandex STT/TTS + `gemini-3.8-flash` |
| `Qarzdorlik (realtime)` | chiquvchi | `REALTIME` | `debt-collection` | `gemini-live` · `gemini-3.1-flash-live-preview` |

Ikkala qarzdorlik agenti bir xil senariy, bir xil guardrail va bir xil matn bilan ishlaydi
— farqi faqat pipeline'da, shuning uchun ularni yonma-yon solishtirish mumkin.

`useRag` uchalasida ham `false`: hali hech qanday knowledge source yuklanmagan, RAG esa
har bir turnga embedding so'rovi qo'shadi. Hujjat yuklangandan keyin yoqiladi.

Ovozlar: `Kiruvchi qabulxona` va `Qarzdorlik (cascade)` uchun `uz-UZ → nigora`,
`ru-RU → alena`; realtime agent Gemini ovozi bilan (`gemini-aoede-uz`) ikkala tilda ham
gapiradi.
