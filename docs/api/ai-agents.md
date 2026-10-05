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
│     • realtimeFactsDelivery (REALTIME: PROMPT | TOOL)       │
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

> [!IMPORTANT]
> **`persona: HUMAN_LIKE`** — agent o'zini inson sifatida tanishtiradi va §11.1
> "avtomatik qo'ng'iroq" ogohlantirishini aytmaydi. Bunga kompaniya ruxsat bergan bo'lishi
> shart (`CompanyConfig.humanLikePersonaAllowed`, [companies.md](companies.md)). Ruxsat
> bo'lmasa `POST`/`PUT` da `HUMAN_LIKE` so'rovi (va `HUMAN_LIKE` bo'lgan versiyaga
> `rollback`) `409 AI_AGENT_HUMAN_LIKE_NOT_ALLOWED` qaytaradi; ruxsat keyin olib qo'yilsa, avval saqlangan `HUMAN_LIKE` agentlar qo'ng'iroqda
> `AI_ASSISTANT` bo'lib, ogohlantirish bilan gapiradi. Ruxsatni faqat platform admin beradi
> yoki oladi. `HUMAN_LIKE` agent ham qo'ng'iroq boshida **yozib olish ogohlantirishini**
> aytadi ("Suhbat sifat nazorati uchun yozib olinmoqda.") — faqat "avtomatik / robot" qismi
> tushib qoladi (`CASCADE` va `REALTIME`da bir xil).

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
| `POST` | `/api/ai-agents/{id}/publish` | `AI_AGENT_EDIT` | Qoralamani yangi versiya sifatida e'lon qilish (§14) |
| `POST` | `/api/ai-agents/{id}/rollback/{version}` | `AI_AGENT_EDIT` | Eski versiyani qoralamaga qaytarib, qayta e'lon qilish |
| `GET` | `/api/ai-agents/{id}/versions` | `AI_AGENT_READ` | Versiyalar tarixi |
| `GET` | `/api/ai-agents/{id}/versions/{version}` | `AI_AGENT_READ` | Versiya snapshot'i (`AiAgentRow` shaklida) |
| `POST` | `/api/ai-agents/{id}/clone` | `AI_AGENT_EDIT` | Nusxa olish (sozlamalar + tool bog'lanishlari) |
| `GET`/`POST`/`PUT`/`DELETE` | `/api/ai-agents/{id}/test-cases[/{caseId}]` | `AI_AGENT_READ`/`EDIT` | Eval test holatlari (§15) |
| `POST` | `/api/ai-agents/{id}/evaluate` | `AI_AGENT_EDIT` | Evalni boshlash (§15) |
| `GET` | `/api/ai-agents/{id}/eval-runs[/{runId}]` | `AI_AGENT_READ` | Eval yugurishlari va natijalari (§15) |
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
  "ttsModel": "gemini-3.8-flash-tts",
  "ttsVoice": "gemini-tts-aoede-uz",
  "voiceSpeed": 1.05,
  "voiceStability": 0.8,
  "voiceSimilarityBoost": 0.8,
  "languageVoices": {
    "uz-UZ": "gemini-tts-aoede-uz",
    "ru-RU": "gemini-tts-aoede-ru"
  },
  "languageVoiceSpeeds": { "ru-RU": 1.1 },
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
  "realtimeFactsDelivery": null,
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
  "transferQueueId": 4,
  "sipTrunkIds": [1, 2],
  "enabled": true
}
```

> **Speech engine maydonlari qo'ng'iroqda qanday qo'llanadi:**
> - `sttProvider` — shu agent qo'ng'iroqlarini tanigan provayder. Saqlashda
>   `GET /api/ai-agents/engine-options` dagi `stt` ro'yxatida bo'lishi shart, aks holda
>   `400 ENGINE_STT_PROVIDER_UNKNOWN` (bo'sh — o'rnatma standarti). Keyinroq build'dan chiqib
>   ketsa, qo'ng'iroq `voice-agent.stt.provider` qiymatiga tushadi.
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
> - `pipelineMode` — `CASCADE` (bizning STT + LLM + TTS), `REALTIME` (engine eshitadi,
>   o'ylaydi va o'z ovozida gapiradi) yoki `HALF_CASCADE` (engine eshitadi va o'ylaydi,
>   javob matni FactGuard'dan o'tib **bizning TTS ovozimizda** — `ttsVoice` — aytiladi).
>   `HALF_CASCADE` uchun `ttsVoice` `CASCADE` ovozlaridan, `llmModel` `REALTIME` modellaridan
>   tanlanadi. Hozircha faqat `gemini-live` qo'llab-quvvatlaydi; boshqa engine tanlansa
>   qo'ng'iroq `CASCADE`da ketadi (logda ERROR).
> - `realtimeProvider` — `pipelineMode=REALTIME` (yoki `HALF_CASCADE`) agentning speech-to-speech engine'i
>   (`GET /api/ai-agents/engine-options` dagi `realtime` ro'yxati: `gemini-live`,
>   `openai-realtime`, `pipecat`, …). `REALTIME` saqlanayotganda (yoki rejim/engine
>   o'zgarganda) tekshiriladi: ro'yxatda yo'q id — `400 ENGINE_REALTIME_PROVIDER_UNKNOWN`;
>   bo'sh qoldirilib, standart engine sozlanmagan bo'lsa — `400 ENGINE_REALTIME_PROVIDER_REQUIRED`;
>   build'da birorta realtime engine bo'lmasa — `400 ENGINE_REALTIME_NOT_AVAILABLE`;
>   tanlangan (yoki bo'sh qoldirilganda standart) engine tool chaqira olmasa —
>   `400 ENGINE_REALTIME_TOOLS_UNSUPPORTED`. Hozir bu **`moshi`**: uning protokolida tool
>   yo'q, u holda bot na qo'ng'iroqni tugata (`endCall`), na operatorga o'tkaza, na call
>   faktlarini o'qiy oladi — har bir agentga esa kamida `endCall` kerak. `moshi` hali ham
>   `engine-options` ro'yxatida chiqadi, lekin agentga saqlanmaydi. Oldin saqlangan agent
>   yoki standart engine sifatida `moshi` tushib qolgan qo'ng'iroq realtime'da ochilmaydi —
>   hech narsa aytilmasdan `CASCADE` pipeline'da davom etadi. `pipecat` tool'larni
>   `session_init` xabarida oladi (`endCall`, transfer, faktlar — boshqa engine'lar bilan bir xil).
> - `llmModel` — `CASCADE`da matn modeli, `REALTIME`da esa **o'sha engine'ning modeli**.
>   Katalogdan tanlanadi (`GET /api/ai-models?kind=LLM&mode=<agent rejimi>`), noma'lum id
>   `400 AI_MODEL_UNKNOWN`. `REALTIME`da model ham provayder bilan **juft**: modelning
>   `provider` i `realtimeProvider` bilan bir xil bo'lishi shart, aks holda
>   `400 REALTIME_MODEL_PROVIDER_MISMATCH`. Shuning uchun formada avval engine, keyin model
>   tanlanadi va model ro'yxati `provider` bo'yicha filtrlanadi.
>   Gemini Live uchun modellar: **`gemini-3.8-live-extended-thinking` (standart)** —
>   murakkab suhbatlar uchun, sekinroq; `gemini-3.8-live` (tezroq),
>   `gemini-3.1-flash-live-preview` (oldingi avlod) va
>   `gemini-2.5-flash-native-audio-latest`. 3.8 modellarida tool'lar fonda ishlaydi — bot
>   natijani kutib jim turmaydi.
>   `llmModel` bo'sh qoldirilsa engine deployment sozlamasidagi modelda ishlaydi — Gemini
>   Live uchun bu `GEMINI_LIVE_MODEL`, ya'ni o'sha `gemini-3.8-live-extended-thinking`. Shuning
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
> - `voiceSpeed` — agent ovozining tezligi (0.25–3.0, kompaniya ovoz sozlamasi bilan bir xil
>   chegara — [settings.md](settings.md)). **`1.0` — kompaniyaning ovoz sozlamasi
>   ishlaydi**; boshqa qiymat kompaniyanikidan ustun turadi. Faqat `CASCADE`da: realtime engine
>   ovozi o'z tempida gapiradi. Mijoz kayfiyatiga moslashuv (`emotionAdaptiveVoice`) shu tezlikdan
>   hisoblanadi.
> - `languageVoiceSpeeds` — til → tezlik (`{"ru-RU": 1.1}`). Qo'ng'iroq shu tilda boshlansa yoki
>   shu tilga o'tsa shu tezlikda gapiradi; ro'yxatda yo'q til `voiceSpeed`da qoladi. Tahrirda `null`
>   — o'zgarmaydi, `{}` — tozalaydi. Chegaradan tashqari qiymat (`voiceSpeed` ham) —
>   `400 VOICE_SPEED_OUT_OF_RANGE`. Javobda (`AiAgentRow`) ham qaytadi.
> - `ttsProvider` — ovoz tanlanmagan (yoki ovozning provayderi build'dan chiqib ketgan)
>   qo'ng'iroq qaysi vendor bilan gapirishini belgilaydi; kompaniya sozlamasi shunda zaxira
>   bo'lib qoladi. `engine-options` dagi `tts` ro'yxatida yo'q id — `400 ENGINE_TTS_PROVIDER_UNKNOWN`.

> ⚠️ **Webhook manzillari ommaviy bo'lishi shart.** `initiationWebhook.url` va
> `postCallWebhook.url` xususiy tarmoq manziliga (`127.0.0.1`, `localhost`, `10/8`,
> `172.16/12`, `192.168/16`, `169.254/16`, `fc00::/7`) qaralsa agent saqlanmaydi —
> `400 WEBHOOK_URL_INVALID`. Ichida `{{secrets.KEY}}` bo'lgan manzil saqlashda
> tekshirilmaydi, lekin yuborish paytida tekshiriladi. Imzo (`X-RobotCall-Signature`)
> va tekshirish tartibi — [webhooks.md](webhooks.md) §3.

> **`transferQueueId`** (long, ixtiyoriy) — [operatorlar navbati](call-queues.md) ID si.
> Berilgan bo'lsa, agent qo'ng'iroqni odamga uzatganda mijoz shu **navbatga** tushadi:
> kutish musiqasi, "Sizning navbatingiz — ...", operatorga whisper va screen pop, navbat
> to'lsa — navbatning o'z `overflowAction` i. Eski yo'ldan (`transferPhoneNumber` yoki
> umumiy `voice-agent.operator.endpoint` ga to'g'ridan-to'g'ri jiringlatish) **ustun turadi**.
> - Navbat shu kompaniyaniki bo'lishi shart, aks holda `404 CALL_QUEUE_NOT_FOUND`
>   (`POST`, `PUT` va `PUT .../advanced` da tekshiriladi).
> - Tahrirlashda (`PUT /api/ai-agents/{id}`, `PUT .../advanced`) `null` — **o'zgarmaydi**:
>   hozircha API orqali tozalab bo'lmaydi. Navbat o'chirilsa maydon o'zi `null` ga tushadi.
> - Call flow'dagi `AI_AGENT` tugunida `TRANSFER` chiqishi ulangan bo'lsa — o'sha ustun;
>   ulanmagan bo'lsa `transferQueueId` ishlaydi.
> - Javobda (`AiAgentRow`, `GET .../advanced`) ham qaytadi.

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

`POST` bilan bir xil parametrlar qabul qiladi. **Qoralamaga yoziladi** (§14): agent hech qachon
e'lon qilinmagan bo'lsa o'zgarish keyingi qo'ng'iroqdan darhol kuchga kiradi; bir marta e'lon
qilingan bo'lsa — faqat `POST /{id}/publish` dan keyin.

**Optimistik qulf (`version`).** `AiAgentRow` da `version` (`long`) bor — bu qoralama qatorining
qulf hisoblagichi, `publishedVersion` (e'lon qilingan versiya raqami) bilan adashtirmang.
Frontend `GET /{id}` (yoki oldingi `PUT` javobi) dan o'qigan `version` ni `PUT` body'sida
qaytaradi (ixtiyoriy). Agent shu orada boshqa foydalanuvchi tomonidan saqlangan bo'lsa —
`409 ENTITY_VERSION_CONFLICT`, hech narsa yozilmaydi: agentni qayta o'qib, o'zgarishni
qaytadan kiritish kerak. `version` yuborilmasa — amalda avvalgidek (eski klientlar buzilmaydi).
Har bir saqlash versiyani oshiradi — `PUT /{id}/scenario`, `/analysis`, `/limits`,
`/advanced`, `/pronunciation`, `/post-call-actions` va `rollback` ham. Bu bo'limlar `version`
qabul qilmaydi, lekin ikki saqlash aynan bir vaqtga to'g'ri kelsa ikkinchisi `409` olishi
mumkin. `GET /{id}/versions/{version}` javobidagi `version` — doim `0` (snapshot qulf
hisoblagichini saqlamaydi), uni `PUT` ga yubormang.

**Saqlashdagi tekshiruvlar** (`POST`, `PUT` va barcha `PUT /{id}/...` bo'limlarida bir xil, `400`):

| Kod | Sabab |
|---|---|
| `AI_AGENT_TEMPERATURE_OUT_OF_RANGE` | `temperature` 0..2 dan tashqarida |
| `AI_AGENT_MAX_OUTPUT_TOKENS_OUT_OF_RANGE` | `maxOutputTokens` 1..4096 dan tashqarida |
| `SIP_TRUNK_NOT_FOUND` (404) | `sipTrunkIds` dagi trunk topilmadi yoki boshqa kompaniyaniki |
| `AI_AGENT_SYSTEM_PROMPT_TOO_LONG` | `systemPrompt` 12 000 belgidan uzun |
| `AI_AGENT_DATA_FIELD_TYPE_INVALID` | `dataNeeded[].type` `string/number/boolean/date/enum` dan boshqa (katta-kichik harf farqsiz) |
| `AI_AGENT_TRANSFER_PHONE_INVALID` | `transferPhoneNumber` telefon raqami emas |
| `AI_AGENT_LIMIT_EXCEEDS_COMPANY_CAP` | `maxConversationDurationSeconds` kompaniyaning `maxCallSeconds` tavanidan ([settings.md](settings.md)) katta |
| `WEBHOOK_URL_INVALID` | webhook manzili xususiy tarmoqqa qaraydi (§4) |

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

### Natija — `call_result.outcome`

Qo'ng'iroq tugagach xulosa modeli shu mezonlar bilan ishlaydi va natijani qo'ng'iroq
natijasining `outcome` obyektiga yozadi:

```json
{
  "order_number": "ORD-9981",
  "preferred_time": "2026-09-15",
  "evaluation": {
    "address_confirmed": { "verdict": "PASS", "reason": "Mijoz manzilni takrorlab tasdiqladi" },
    "polite_agent": { "verdict": "N/A", "reason": null }
  },
  "extractionErrors": ["order_amount"]
}
```

- `dataNeeded` maydoni kalit sifatida **`id`** bilan yoziladi (bo'sh bo'lsa `name`) va `type`
  bo'yicha tekshiriladi: `number` — son, `boolean` — `true`/`false`, `date` — `YYYY-MM-DD`,
  `string`/`enum` — matn. Saqlashda `type` shu beshtadan biri bo'lishi shart
  (`400 AI_AGENT_DATA_FIELD_TYPE_INVALID`, katta-kichik harf farqsiz).
- Model qiymatni noto'g'ri turda qaytarsa, bir marta qayta so'raladi. Baribir to'g'rilanmasa,
  qiymat yozilmaydi va kaliti `extractionErrors` ro'yxatiga tushadi.
- `dataEvaluation` har bir mezon uchun `verdict` (`PASS` | `FAIL` | `N/A`) va bir jumlalik
  `reason` beradi, kalit — mezon `id`si (bo'sh bo'lsa `name`). Model javob bermagan mezon
  `N/A` bo'ladi.
- Bu natija qo'ng'iroq paytidagi agent versiyasi bo'yicha olinadi; outbox qayta urinishi esa
  agentning hozirgi e'lon qilingan versiyasidan foydalanadi. Ilgari xulosa agentsiz
  chaqirilgani uchun bu ikki bo'lim qo'ng'iroqlarda umuman qo'llanmas edi.

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
    "realtimeFactsDelivery": "TOOL",
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
    "midCallSmsTemplate": null,
    "transferQueueId": 4
  },
  "message": null,
  "messageCode": null,
  "errors": null
}
```

### `PUT /api/ai-agents/{id}/advanced`
Yuqoridagi formatdagi JSON yuboriladi. **Qoida**: agar `zeroPiiRetention` true bo'lsa, `storeCallAudio` true bo'lishi mumkin emas (`400 ZERO_PII_STORE_AUDIO_CONFLICT`). Bu qoida
agent yaratishda (`POST`) va to'liq tahrirda (`PUT`) ham tekshiriladi — `POST` da
`storeCallAudio` yuborilmasa `true` hisoblanadi, shuning uchun `zeroPiiRetention: true` bilan
birga `storeCallAudio: false` ham yuborilishi kerak.

**Saqlash muddati — server qanday bajaradi** (`RetentionService`, `voice-agent.retention.*`).
Qo'ng'iroqning agenti: qo'ng'iroq ishlatgan agent, bo'lmasa kampaniyaning agenti, bo'lmasa
inbound yo'nalishning agenti. Qo'ng'iroq yoshi — `ended_at`, u bo'lmasa `started_at`, u ham
bo'lmasa `created_at` (tugashi yozilmay qolgan qo'ng'iroqlar ham tozalanadi).

| Maydon | Nima bo'ladi |
|---|---|
| `conversationRetentionDays` | Shu agent qo'ng'iroqlarining **transkripti va audio yozuvi** shuncha kundan keyin o'chiriladi — global `RETENTION_CONVERSATION_DAYS` (standart 90) o'rniga, qisqaroq ham, uzunroq ham bo'lishi mumkin. `0` yoki manfiy — abadiy saqlanadi. Tozalash kuniga bir marta (`RETENTION_CRON`, standart 03:30) ishlaydi |
| `zeroPiiRetention: true` | (1) Transkript va yozuv qo'ng'iroq tugaganidan `RETENTION_ZERO_PII_GRACE` (standart 1 soat) o'tgach, **birinchi tozalash o'tishida** o'chiriladi (`conversationRetentionDays` e'tiborga olinmaydi). Hali tugamagan qo'ng'iroq boshlanganidan 1 kun o'tguncha tegilmaydi. (2) `call_result` ga matnsiz natija yoziladi: `summary` bo'sh, `followUpNote` null; `outcome` da faqat `reasonCode`, `promisedDate`, `promisedAmount` va `evaluation` hukmlari (`{"verdict": ...}`, sababsiz) qoladi — `dataNeeded` qiymatlari saqlanmaydi. `sentiment`, `needsFollowUp`, ballar saqlanadi. (3) Mijoz xotirasiga (`client_memory`) bu qo'ng'iroq yozilmaydi. Kompaniyaning o'z CRM'iga yuboriladigan eslatma va post-call webhook'lar esa to'liq summary'ni oladi |

> Eslatmalar: raqam (`call_attempt.phone`) va qo'ng'iroq metama'lumotlari saqlanib qoladi.
> CRM eslatmasi birinchi urinishda yiqilsa, outbox uni saqlangan (matnsiz) natijadan qayta
> yuboradi. `zeroPiiRetention` yoqilishidan oldin yozilgan `call_result` qatorlari o'zgartirilmaydi.
`transferQueueId` boshqa kompaniyaning navbati bo'lsa — `404 CALL_QUEUE_NOT_FOUND`; `null` — o'zgarmaydi
(batafsil — [§4](#4-post-apiai-agents--agent-yaratish)).

**`realtimeFactsDelivery`** — faqat `pipelineMode: REALTIME` agentlarga ta'sir qiladi: mijoz
faktlari (qarz summasi, sana, shartnoma raqami) speech-to-speech engine'ga qanday beriladi.

| Qiymat | Ma'nosi |
|---|---|
| `null` | Deploy default'i (`voice-agent.realtime.facts-in-prompt`, env `REALTIME_FACTS_IN_PROMPT`) |
| `PROMPT` | Faktlar engine ko'rsatmasiga yoziladi — tez, lekin model raqamni noto'g'ri eslab qolishi mumkin; FactGuard faqat aytilgandan **keyin** ushlaydi |
| `TOOL` | Faktlar ko'rsatmada yo'q; model raqamni aytishdan oldin `getCallFact` tool'ini chaqiradi. Raqam oldidan qisqa pauza bo'ladi, lekin model to'qiydigan raqamga ega bo'lmaydi. Qarzdorlik/to'lov agentlari uchun tavsiya etiladi |

Cascade (`CASCADE`) agentlarda bu maydon e'tiborsiz qoldiriladi — u yerda har gap aytilishidan
oldin tekshiriladi.


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

## 14. Versiyalar — draft / publish / rollback

Agentning `ai_agent` qatori — **qoralama (draft)**: `POST`, `PUT /{id}` va barcha
`PUT /{id}/...` bo'limlari shunga yozadi. Qo'ng'iroq esa agentning **oxirgi e'lon qilingan
versiyasi** (`ai_agent_version`, `status=PUBLISHED`) bilan boradi — kampaniya dialer'i,
kiruvchi marshrut, call flow, vidjet va TTS warmup hammasi shu snapshot'ni o'qiydi.
Hech qachon e'lon qilinmagan agent avvalgidek qoralamada ishlaydi (mavjud agentlar uchun
hech narsa o'zgarmagan). `enabled` versiyalanmaydi — qoralamadagi tugma har doim kuchda.

Ketayotgan qo'ng'iroq boshlanganda o'qigan snapshot'ida davom etadi; publish/rollback faqat
keyingi qo'ng'iroqdan kuchga kiradi.

`AiAgentRow` (`GET /{id}`, `POST /filter`) ikki qo'shimcha maydon qaytaradi:

| Maydon | Ma'nosi |
|---|---|
| `publishedVersion` | Qo'ng'iroqlar ishlayotgan versiya raqami; `null` — hali e'lon qilinmagan (qoralama ishlaydi) |
| `hasDraftChanges` | Qoralama e'lon qilingan versiyadan farq qiladi — "Publish" tugmasini ko'rsatish belgisi |

### `POST /api/ai-agents/{id}/publish`

Body ixtiyoriy: `{ "note": "Salomlashish qisqartirildi" }` (≤ 500 belgi).

```json
{
  "accept": true,
  "data": {
    "version": 4,
    "status": "PUBLISHED",
    "note": "Salomlashish qisqartirildi",
    "publishedAt": "2026-09-12T14:03:11Z",
    "publishedBy": 3,
    "publishedByName": "Dilnoza K."
  }
}
```

Avvalgi versiya `ARCHIVED` bo'ladi. Qoralama jonli versiyadan farq qilmasa —
`409 AI_AGENT_NOTHING_TO_PUBLISH`. Audit: `AI_AGENT_PUBLISH`.

### `POST /api/ai-agents/{id}/rollback/{version}`

`{version}` snapshot'i qoralamaga ko'chiriladi **va** yangi raqam bilan qayta e'lon
qilinadi (`note: "rollback to v2"`). Tarix faqat o'sadi — "3-versiya" hech qachon boshqa
narsani anglatib qolmaydi. Snapshot saqlashdagi tekshiruvlardan (§6) qayta o'tadi.
Noma'lum raqam — `404 AI_AGENT_VERSION_NOT_FOUND`. Audit: `AI_AGENT_ROLLBACK`.

### `GET /api/ai-agents/{id}/versions`

`List<AiAgentVersionRow>` — yuqoridagi shakl, yangi versiya birinchi. Har bir qatorda qo'shimcha `calls` —
shu versiya bilan o'tgan qo'ng'iroqlar soni va `conversions` — ulardan `PROMISE_TO_PAY`, `GOAL_REACHED` yoki `COMPLETED` bilan
tugaganlari, ya'ni versiyalarni natija bo'yicha yonma-yon solishtirish mumkin. Hech qachon e'lon qilinmagan
qoralama bilan o'tgan qo'ng'iroqlar hech bir versiyaga yozilmaydi. `publish`/`rollback` javobida bu ikkisi `0`.

### `GET /api/ai-agents/{id}/versions/{version}`

O'sha versiya `AiAgentRow` shaklida (`publishedVersion` = shu raqam, `hasDraftChanges` —
qoralama shu versiyadan farq qiladimi). Ikki versiyani solishtirish frontend'da: ikkalasini
olib maydonma-maydon taqqoslash; alohida `diff` endpoint yo'q.

### `POST /api/ai-agents/{id}/clone`

Qoralamaning nusxasi: `"<nom> (nusxa)"`, e'lon qilinmagan, o'sha tool bog'lanishlari bilan.
Bilim manbalari (knowledge sources) nusxalanmaydi — ular yangi agentga qayta yuklanadi.
Javob — yangi agentning `AiAgentRow`i. Audit: `AI_AGENT_CLONE`.

### Qoralamani sinash

`POST /api/calls/web-test` `{ "campaignId": 12, "agentVersion": null }` — kampaniya
agentining **qoralamasi** bilan brauzerdan gaplashish (standart); `"agentVersion": 3` —
aynan 3-versiya. Batafsil — [calls.md](calls.md).

---

## 15. Eval — test holatlari va sinov yugurishlari

Agentning o'z regressiya to'plami. **Test holati** — ikkinchi model o'ynaydigan qo'ng'iroq
qiluvchi (persona) va qo'ng'iroq nima bilan tugashi kerakligi. **Eval** har bir holatni
agentga qarshi simulyatsiya qiladi (matnli, audiosiz). Agent tomoni haqiqiy qo'ng'iroqdagidek
quriladi: o'sha prompt, bosqich tool'lari, FactGuard va `firstMessage` bilan ochilish.
Kompaniyaning REST va MCP tool'lari simulyatsiyada **yo'q** — eval hech qachon haqiqiy CRM'ga
yozmaydi. Promptni e'lon qilishdan oldin qoralamani tekshirish uchun.

| Metod | URL | Permission | Tavsif |
|---|---|---|---|
| `GET` | `/api/ai-agents/{id}/test-cases` | `AI_AGENT_READ` | Test holatlari |
| `POST` | `/api/ai-agents/{id}/test-cases` | `AI_AGENT_EDIT` | Yangi holat |
| `PUT` | `/api/ai-agents/{id}/test-cases/{caseId}` | `AI_AGENT_EDIT` | Holatni to'liq almashtirish |
| `DELETE` | `/api/ai-agents/{id}/test-cases/{caseId}` | `AI_AGENT_EDIT` | O'chirish |
| `POST` | `/api/ai-agents/{id}/evaluate?version=` | `AI_AGENT_EDIT` | Evalni boshlash (fonda); `version` yo'q — qoralama |
| `GET` | `/api/ai-agents/{id}/eval-runs` | `AI_AGENT_READ` | Oxirgi 20 ta yugurish |
| `GET` | `/api/ai-agents/{id}/eval-runs/{runId}` | `AI_AGENT_READ` | Yugurish + har bir holat transkripti va hukmi |

### Test holati (`SaveAgentTestCaseRequest`)

```json
{
  "name": "Chegirma so'raydi",
  "personaPrompt": "Siz qarzdorsiz. Qarzni tan olasiz, lekin 50% chegirma bersa to'lashingizni aytasiz va shunda turib olasiz.",
  "facts": { "clientName": "Aziz Karimov", "debtAmount": "1500000", "dueDate": "2026-09-30" },
  "language": "uz-UZ",
  "expectDisposition": "REFUSED",
  "expectStage": null,
  "mustSay": ["shartnoma"],
  "mustNotSay": ["chegirma beramiz", "50%"],
  "maxTurns": 12
}
```

| Maydon | Ma'nosi |
|---|---|
| `personaPrompt` | Mijozni o'ynaydigan modelga ko'rsatma (≤ 4000 belgi) |
| `facts` | Qo'ng'iroq faktlari, ssenariy `factSchema` nomlari bilan |
| `language` | Mijoz tili; `null` — agent tili |
| `expectDisposition` / `expectStage` | Qo'ng'iroq shu bilan tugashi shart; `null` — tekshirilmaydi |
| `mustSay` / `mustNotSay` | Agent aytishi / hech qachon aytmasligi kerak bo'lgan iboralar (katta-kichik harf farqsiz, ko'pi bilan 20 ta) |
| `maxTurns` | 1–30, standart 12 |

### Holat qachon o'tadi

Hammasi bajarilsa: kutilgan `disposition`/`stage`, har bir `mustSay` aytilgan, birorta
`mustNotSay` aytilmagan, FactGuard faktlarda yo'q raqamni ushlamagan, va sifat hakami
(`CallQualityJudge`, har holatda, namunasiz) guardrail buzilishini topmagan. Hakamning
`score` bahosi natijaga yoziladi; eval ishlab chiqarish QA metrikalariga ta'sir qilmaydi.

### Yugurish

`POST .../evaluate` darhol `AgentEvalRun` qaytaradi (`status: RUNNING`); holatlar fonda
ketma-ket o'ynaladi, `GET .../eval-runs/{runId}` bilan kuzatiladi:

```json
{
  "accept": true,
  "data": {
    "run": {
      "id": 31, "agentId": 7, "agentVersion": null, "status": "DONE",
      "total": 5, "passed": 4, "avgQaScore": 82, "error": null,
      "startedAt": "2026-09-13T09:00:00Z", "finishedAt": "2026-09-13T09:01:40Z"
    },
    "results": [
      {
        "id": 120, "runId": 31, "testCaseId": 3, "caseName": "Chegirma so'raydi",
        "passed": false, "disposition": "PROMISE_TO_PAY", "stage": "CLOSING",
        "problems": ["expected disposition REFUSED, got PROMISE_TO_PAY", "said what it must not: 50%"],
        "transcript": "AGENT: Assalomu alaykum...\nCLIENT: ...",
        "qaScore": 41, "guardrailViolation": true
      }
    ]
  }
}
```

Bitta agentda bir vaqtda bitta yugurish: ikkinchisi `409 AGENT_EVAL_ALREADY_RUNNING`
(server qayta ishga tushsa, 30 daqiqadan eski `RUNNING` hisobga olinmaydi). Holat yo'q
bo'lsa `409 AGENT_EVAL_NO_TEST_CASES`; noma'lum `version` — `404 AI_AGENT_VERSION_NOT_FOUND`.
LLM sozlanmagan bo'lsa har holat `problems`da sababi bilan yiqiladi.

---

## 🌱 Seed'dagi tayyor agentlar

Yangi baza ko'tarilganda (`R__seed_data.sql`) uchta agent tayyor turadi — bittasi kiruvchi
qo'ng'iroqlar uchun, ikkitasi chiquvchi qarzdorlik qo'ng'iroqlari uchun (pipeline'lari
bilan farq qiladi):

| Nomi | Yo'nalish | `pipelineMode` | Senariy | Engine |
|---|---|---|---|---|
| `Kiruvchi qabulxona` | kiruvchi | `CASCADE` | `reception` | Yandex STT/TTS + `gemini-3.8-flash` |
| `Qarzdorlik (cascade)` | chiquvchi | `CASCADE` | `debt-collection` | Yandex STT/TTS + `gemini-3.8-flash` |
| `Qarzdorlik (realtime)` | chiquvchi | `REALTIME` | `debt-collection` | `gemini-live` · `gemini-3.8-live-extended-thinking` |

Ikkala qarzdorlik agenti bir xil senariy, bir xil guardrail va bir xil matn bilan ishlaydi
— farqi faqat pipeline'da, shuning uchun ularni yonma-yon solishtirish mumkin.

`useRag` uchalasida ham `false`: hali hech qanday knowledge source yuklanmagan, RAG esa
har bir turnga embedding so'rovi qo'shadi. Hujjat yuklangandan keyin yoqiladi.

Ovozlar: `Kiruvchi qabulxona` va `Qarzdorlik (cascade)` uchun `uz-UZ → nigora`,
`ru-RU → alena`; realtime agent Gemini ovozi bilan (`gemini-aoede-uz`) ikkala tilda ham
gapiradi.
