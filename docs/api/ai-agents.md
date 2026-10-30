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
│     • templateId: APPOINTMENT_BOOKING | BUSINESS | SUPPORT |│
│       LEAD_QUALIFICATION | DEBT_REMINDER | SURVEY | MEDICAL │
│       INBOUND_RECEPTIONIST | ORDER_TRACKING | BLANK         │
│     • firstMessage: 0ms kechikish bilan ochilish salomi     │
│     • systemPrompt: agentning roli, skripti va qoidalari    │
│     • scenarioId: ixtiyoriy (tashqi FSM senariy yoki null)  │
│     • useRag                                                │
│                                                             │
│  2. SPEECH & VOICE (Agent darajasida)                       │
│     • sttProvider, sttModel (yandex/gemini/aisha/           │
│       deepgram/openai)                                      │
│     • ttsProvider, ttsModel, ttsVoice                       │
│     • voiceSpeed                                            │
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
| `GET` | `/api/ai-agents/{id}/versions/diff?from=&to=` | `AI_AGENT_READ` | Ikki versiya (yoki versiya va qoralama) orasidagi farq (§14.1) |
| `POST` | `/api/ai-agents/{id}/rollout` | `AI_AGENT_EDIT` | Qoralamani nomzod versiya sifatida e'lon qilib, trafikning bir qismini unga berish (§14.2) |
| `PUT` | `/api/ai-agents/{id}/rollout` | `AI_AGENT_EDIT` | Foiz va staging raqamlarini o'zgartirish |
| `GET` | `/api/ai-agents/{id}/rollout` | `AI_AGENT_READ` | Rollout holati va ikki versiyaning natijalari |
| `POST` | `/api/ai-agents/{id}/rollout/promote` | `AI_AGENT_EDIT` | Nomzodni hamma uchun live qilish |
| `DELETE` | `/api/ai-agents/{id}/rollout` | `AI_AGENT_EDIT` | Rolloutni bekor qilish |
| `GET` | `/api/ai-agents/{id}/estimate` | `AI_AGENT_READ` | Bir daqiqa suhbat narxi (so'm), kechikish va tokenlar — studiyaning yuqori qatori (§14.3) |
| `POST` | `/api/ai-agents/{id}/test-chat` | `AI_AGENT_EDIT` yoki `AGENT_EVAL_RUN` | Studiyaning "LLM test"i: agent qoralamasi bilan matnli suhbatning bitta navbati (§14.4) |
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
      "templateId": "BUSINESS",
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

Ro'yxat tartibi: `APPOINTMENT_BOOKING` (qabulga yozish), `SUPPORT` (qo'llab-quvvatlash), `LEAD_QUALIFICATION`
(ariza bo'yicha qayta qo'ng'iroq), `DEBT_REMINDER` (qarz eslatma), `INBOUND_RECEPTIONIST` (virtual qabulxona),
`SURVEY` (CSAT/NPS so'rovnoma), `MEDICAL` (klinika qabuli), `ORDER_TRACKING` (buyurtma holati), oxirida `BLANK`.
`BUSINESS` ro'yxatda yo'q, lekin `templateId` sifatida hali qabul qilinadi. Shablon faqat `systemPrompt`, `firstMessage`, `dataNeeded` va `dataEvaluation`
ni to'ldiradi; ovoz va model o'zgarmaydi. `POST /api/ai-agents` da `templateId` berilsa va bu
maydonlar bo'sh bo'lsa, shablon qiymatlari tushadi — alohida "shablondan yaratish" endpointi yo'q.
`DEBT_REMINDER` summa, muddat va chegirmani faqat qo'ng'iroq ma'lumotlaridan olishni buyuradi.

### 3.1. `GET /api/ai-agents/engine-profiles` — Tayyor dvigatel to'plamlari

Permission: `AI_AGENT_READ`. Tezlik / narx / sifat bo'yicha tayyor to'plamlar. Faqat shu build
to'liq ishga tushira oladigan profillar qaytadi (provayder kaliti yo'q yoki model katalogda yo'q
bo'lsa profil ro'yxatda chiqmaydi).

```json
{
  "accept": true,
  "data": [
    {
      "id": "balanced",
      "label": "Muvozanatli",
      "description": "Odatiy qo'ng'iroqlar uchun narx va aql muvozanati",
      "sttProvider": "yandex",
      "sttModel": "general",
      "llmModel": "gemini-3.8-flash",
      "fastLlmModel": "gemini-3.5-flash-lite",
      "temperature": 0.3,
      "maxOutputTokens": 300
    }
  ]
}
```

`POST /api/ai-agents` da `engineProfileId` berilsa, profil **faqat so'rovda berilmagan**
maydonlarni to'ldiradi: `sttProvider`, `sttModel`, `llmModel`, `fastLlmModel`, `temperature`,
`maxOutputTokens`. Ovoz profilga kirmaydi — sintezatorni ovozning o'zi belgilaydi. So'rov o'z
`sttProvider` ini bersa, profilning `sttModel` i olinmaydi (u o'z provayderiga tegishli).
Ro'yxatda yo'q id — `400 ENGINE_PROFILE_UNKNOWN`. Profillar `voice-agent.engine-profiles.items`
(`config/misc.yml`) da, o'zgartirish uchun kod kerak emas.

`PUT /api/ai-agents/{id}` da ham `engineProfileId` qabul qilinadi: profil agentning shu olti
maydonini **almashtiradi** (bo'sh qoldirilganlarini emas, hammasini); so'rovda o'sha maydonlardan
biri ham kelsa — so'rovniki ustun. `engineProfileId` yuborilmasa — agentning dvigatel sozlamalari
o'zgarmaydi.

Agent javobida (`AiAgentRow`) `engineProfileId` — agentning olti maydoni aynan mos keladigan
profil id si, mos kelmasa `null` (qo'lda sozlangan — UI "Maxsus" deb ko'rsatadi). Bu saqlanmaydi,
har safar hisoblanadi: profil `misc.yml` da o'zgarsa, eski agentlar `null` bo'lib qoladi.

---

## 4. `POST /api/ai-agents` — Agent yaratish

```json
{
  "name": "Operator Dilnoza (Savdo va Qabul)",
  "description": "Kiruvchi mijozlarni kutib oluvchi va savollariga javob beruvchi bot",
  "language": "uz-UZ",
  "persona": "AI_ASSISTANT",
  "engineProfileId": null,
  "script": {
    "templateId": "BUSINESS",
    "scenarioId": null,
    "mode": "PROMPT",
    "definition": null,
    "firstMessage": "Assalomu alaykum! Kompaniyamizga xush kelibsiz. Sizga qanday yordam bera olaman?",
    "systemPrompt": "Siz kompaniyaning samimiy savdo menejerisiz. Mijozning ismini va qiziqayotgan mahsulotini aniqlang."
  },
  "speechEngine": {
    "mode": "CASCADE",
    "realtimeProvider": null,
    "pipecatStt": null,
    "pipecatLlm": null,
    "pipecatTts": null,
    "sttProvider": "gemini",
    "sttModel": "gemini-3.5-transcribe-live",
    "ttsProvider": "gemini",
    "ttsModel": "gemini-3.8-flash-tts"
  },
  "voice": {
    "defaultVoice": "gemini-tts-aoede-uz",
    "speed": 1.05,
    "perLanguage": {
      "uz-UZ": "gemini-tts-aoede-uz",
      "ru-RU": "gemini-tts-aoede-ru"
    },
    "perLanguageSpeed": { "ru-RU": 1.1 },
    "emotionAdaptive": true,
    "warmth": "BALANCED"
  },
  "llmModel": "gemini-3.8-flash",
  "fastLlmModel": "gemini-3.5-flash-lite",
  "temperature": 0.6,
  "maxOutputTokens": 300,
  "ambience": {
    "background": "OFFICE",
    "backgroundVolume": 0.35,
    "backgroundFadeInSeconds": 1.5,
    "thinking": "OFF",
    "thinkingVolume": 0.35,
    "noiseCancellationEnabled": true,
    "noiseCancellationMode": "BACKGROUND_NOISE_SUPPRESSION"
  },
  "greeting": {
    "mode": "AI_CUSTOM",
    "pauseBeforeSpeakingMs": null,
    "lines": []
  },
  "callBehaviour": {
    "interruptionSensitivity": "MEDIUM",
    "dtmfInputEnabled": true,
    "voicemailAction": "LEAVE_MESSAGE",
    "voicemailMessage": "Assalomu alaykum, siz bilan bog'lana olmadik. Iltimos qaytib qo'ng'iroq qiling.",
    "midCallSmsEnabled": false,
    "midCallSmsTemplate": null,
    "transferPhoneNumber": null,
    "transferMessage": null,
    "transferQueueId": 4,
    "preConversationAudioFileId": null
  },
  "limits": {
    "concurrentCallsLimit": 15,
    "dailyCallsLimit": 500,
    "maxConversationDurationSeconds": 600,
    "silenceEndCallTimeoutSeconds": 25
  },
  "dataPolicy": {
    "zeroPiiRetention": false,
    "storeCallAudio": true,
    "conversationRetentionDays": 60,
    "realtimeFactsDelivery": null
  },
  "useRag": true,
  "dataNeeded": [
    { "id": "client_name", "type": "string", "name": "Mijoz ismi", "description": "Mijozning to'liq ismi" },
    { "id": "product_name", "type": "string", "name": "Mahsulot nomi", "description": "Mijoz qiziqqan tovar" }
  ],
  "dataEvaluation": [
    { "id": "satisfaction", "name": "Mijoz roziligi", "criteria": "Mijoz ijobiy kayfiyatda xayrlashdimi" }
  ],
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
  "sipTrunkIds": [1, 2],
  "enabled": true
}
```

> **Maydon guruhlari (2026-10-28 dan, breaking change).** Agent sozlamalari domain'dagi
> guruhlar bo'yicha ichma-ich obyektlarda keladi: `script`, `speechEngine`, `voice`, `ambience`,
> `greeting`, `callBehaviour`, `limits`, `dataPolicy`. Shu shakl `POST`, `PUT` va javob (`AiAgentRow`) da bir
> xil, shuning uchun forma `GET` da o'qiganini `PUT` ga qaytara oladi. Har qanday guruhni butunlay
> tashlab ketish mumkin: `POST` da u standart qiymatlarni oladi, `PUT` da agentniki saqlanadi.
> Guruh ichida `null` maydon ham xuddi shunday ishlaydi. Quyidagi matnda maydonlar qisqa nomi
> bilan tilga olinadi. Eski tekis nomlar quyidagi yangi yo'llarga o'tdi:
>
> | Eski (tekis) | Yangi yo'l |
> |---|---|
> | `templateId`, `scenarioId`, `firstMessage`, `systemPrompt` | `script.templateId`, `script.scenarioId`, `script.firstMessage`, `script.systemPrompt` |
> | `scenarioMode`, `scenarioDefinition` | `script.mode`, `script.definition` |
> | `pipelineMode` | `speechEngine.mode` |
> | `realtimeProvider`, `pipecatStt/Llm/Tts`, `sttProvider`, `sttModel`, `ttsProvider`, `ttsModel` | `speechEngine.<o'sha nom>` |
> | `ttsVoice` | `voice.defaultVoice` |
> | `voiceSpeed` | `voice.speed` |
> | `languageVoices`, `languageVoiceSpeeds` | `voice.perLanguage`, `voice.perLanguageSpeed` |
> | `emotionAdaptiveVoice`, `speakingWarmth` | `voice.emotionAdaptive`, `voice.warmth` |
> | `ambientSound`, `ambientSoundVolume`, `ambientSoundFadeInSeconds` | `ambience.background`, `ambience.backgroundVolume`, `ambience.backgroundFadeInSeconds` |
> | `thinkingSound`, `thinkingSoundVolume` | `ambience.thinking`, `ambience.thinkingVolume` |
> | `noiseCancellationEnabled`, `noiseCancellationMode` | `ambience.<o'sha nom>` |
> | `dtmfInputEnabled`, `voicemail*`, `midCallSms*`, `transfer*`, `interruptionSensitivity`, `preConversationAudioFileId` | `callBehaviour.<o'sha nom>` |
> | `maxConversationDurationSeconds`, `silenceEndCallTimeoutSeconds`, `concurrentCallsLimit`, `dailyCallsLimit` | `limits.<o'sha nom>` |
> | `zeroPiiRetention`, `storeCallAudio`, `conversationRetentionDays`, `realtimeFactsDelivery` | `dataPolicy.<o'sha nom>` |
>
> Yuqori darajada qolganlar: `name`, `description`, `language`, `persona`, `engineProfileId`,
> `llmModel`, `fastLlmModel`, `temperature`, `maxOutputTokens`, `useRag`, `dataNeeded`, `dataEvaluation`, `initiationWebhook`,
> `postCallWebhook`, `handbook`, `timezone`, `sipTrunkIds`, `enabled` (`PUT` da yana `version`). Javobda qo'shimcha:
> `id`, `companyId`, `disclosureEnabled`, `scenarioName`, `pronunciationRules`, `postCallActions`,
> `createdAt`, `createdBy`, `createdByName`, `publishedVersion`, `hasDraftChanges`, `version`.

> **`handbook`** (Agent Handbook, `POST`/`PUT /api/ai-agents` va javob qatorida) — agent yoqqan
> tayyor qoidalar ro'yxati (`HandbookRule` nomlari). Har biri promptga platformaning o'z qoidalari
> (`platform-guardrails.txt`) **ustiga** bitta qator qo'shadi — birortasi platforma qoidasini takrorlamaydi
> yoki yumshatmaydi. Ikkala pipeline'da (`CASCADE` va `REALTIME`) ishlaydi. Standart — `[]`, ya'ni agent
> avvalgidek gapiradi. `PUT` da `null` — o'zgarmaydi, `[]` — hammasini o'chiradi; ro'yxat to'liq
> almashtiriladi (qo'shib bo'lmaydi). Noma'lum nom — `400` (buzuq JSON kabi).
>
> | Qiymat | Studiya tabi | Agent nima qiladi |
> |---|---|---|
> | `HIGH_EMPATHY` | Shaxsiyat va ohang | Mijoz xafa bo'lsa, avval u aytgan aniq narsani bitta qisqa samimiy gap bilan tan oladi, keyin yechimga o'tadi (faqat `persona: HUMAN_LIKE` da ishlaydi, `AI_ASSISTANT` da qo'shilmaydi). Sahnaviy hamdardlik baribir taqiqlangan |
> | `CONFIRM_DETAILS` | Aniqlik va format | Mijoz aytgan ism, telefon, sana, summa yoki manzilni bir marta qaytarib aytib, "ha" olgandan keyingina yozadi |
> | `SPELL_OUT_CODES` | Aniqlik va format | Kod, buyurtma raqami va telefonni sekin, 2–3 raqamdan guruhlab aytadi |
> | `PHONETIC_SPELLING` | Aniqlik va format | Ism, email, ko'cha yoki ID harflarini aytganda har bir harfni tanish so'z bilan beradi (uz: "A — Anor", ru: "А — Анна"), keyin to'liq yozilishini qaytarib aytib, "ha" oladi |
> | `SPOKEN_NUMBERS` | Aniqlik va format | Raqam, sana, vaqt va pul summasini raqam/belgi bilan emas, og'zaki aytiladigan ko'rinishda (so'z bilan) yozadi — ovoz ularni tabiiy o'qiydi |
> | `SMART_MATCHING` | Aniqlik va format | Bir narsaning yaqin variantlarini (imlo, lotin/kirill, qisqa/to'liq shakl: Alisher/Алишер, ko'cha/улица) bitta narsa deb biladi, yangi shaxs yoki joy deb olmaydi; haqiqatan noaniq bo'lsa so'raydi |
> | `KNOWLEDGE_ONLY` | Aniqlik va format | Narx, shart, mavjudlik, muddatni faqat promptdan, bilimlar bazasidan yoki tool natijasidan aytadi; topilmasa tekshirib berishini aytib, operatorga uzatadi |
> | `PROTECT_INSTRUCTIONS` | Ishonch va xavfsizlik | Ko'rsatmalari, tool'lari yoki skript bo'yicha ishlashini hech qachon oshkor qilmaydi |
> | `STAY_ON_TOPIC` | Ishonch va xavfsizlik | Mavzudan tashqari so'rovni bir gap bilan rad etib, qo'ng'iroq maqsadiga qaytadi |
> | `NO_PROFESSIONAL_ADVICE` | Ishonch va xavfsizlik | Kompaniya shartlaridan tashqari tibbiy, yuridik yoki moliyaviy maslahat bermaydi |
>
> Ohang — alohida qoida emas, mavjud `voice.warmth` (`RESERVED` / `BALANCED` / `WARM`). Tabiiy
> to'ldiruvchi so'zlar («Qarang...», «Xo'sh...», «Kelishdik») va yuqori hamdardlik (`HIGH_EMPATHY`) faqat **`persona == HUMAN_LIKE`** («Insondek»)
> rejimida ishlaydi — `AI_ASSISTANT` rejimida bu to'ldiruvchilar ulanmaydi, agent AI yordamchisi sifatida qisqa va aniq javob beradi.
> Agent versiyasi (publish) va klonida `handbook` ham saqlanadi.
>
> **`speechLanguages`** (Retell'ning "Speech Languages" tanlagichi; `POST`, `PUT` va javob qatorida; javobda yana `multilingual: boolean`) —
> agent qo'ng'iroq o'tkaza oladigan tillar (BCP-47: `uz-UZ`, `ru-RU`, `en-US`, `en-GB`, `kk-KZ`, `tr-TR`, `es-ES`).
> Ro'yxat `GET /api/ai-agents/speech-languages` dan olinadi (`AI_AGENT_READ`): `[{ code, name, nativeName, country, flag }]`.
>
> - Agentning asosiy tili (`language`) ro'yxatga doim birinchi bo'lib kiradi, takrorlar olib tashlanadi. Standart — `[language]`.
> - **Bitta til** (`multilingual = false`): qo'ng'iroq shu tilda o'tadi, mijoz javobiga qarab avtomatik almashish **o'chiq** (Retell'ning single-select rejimi, eng aniq STT). `multilingual` agent haqiqatda gapira oladigan tillar bo'yicha hisoblanadi: ro'yxatda bitta til bo'lsa ham, boshqa tilning ovozi (`voice.perLanguage`) bo'lsa agent shu tilga o'tadi va `multilingual = true` qaytadi. Bitta tilli rejim uchun `voice.perLanguage` ham faqat shu tilni o'z ichiga olishi kerak.
> - **Bir nechta til** (`multilingual = true`): STT shu tillarni tanlaydi; mijoz boshqa tilda ketma-ket javob bersa yoki tilni so'rasa, agent shu ro'yxat ichidagi tilga o'tadi. Promptga "multilingual call" qatori qo'shiladi.
> - Tilning ovozi `voice.perLanguage` da bo'lmasa, shu tilda ham `voice.defaultVoice` gapiradi.
> - Avtomatik aniqlash (mijoz javobiga qarab) hozir faqat `uz`, `ru`, `en` uchun ishlaydi. `kk`, `tr`, `es` ro'yxatda bo'lsa, ular faqat mijoz tilni **so'raganda** (`switchLanguage`) yoqiladi, lotin yozuvidagi matnga qarab Uzbek deb taxmin qilinmaydi.
> - `PUT` da `null` — o'zgarmaydi; `[]` yoki `clearFields: ["SPEECH_LANGUAGES"]` — faqat asosiy til qoladi (ikkalasini birga yuborish — `400 AI_AGENT_CLEAR_FIELD_CONFLICT`).
> - Noma'lum til kodi — `400 LANGUAGE_CODE_INVALID`. Agent versiyasi (publish) va klonida `speechLanguages` ham saqlanadi.

> **`timezone`** (studiyadagi soat tugmasi, "Current Time Awareness") — agentning IANA vaqt zonasi
> (`Asia/Tashkent`, `Europe/Moscow`). Promptdagi "bugun / hozir soat" va eslab qolingan qo'ng'iroqlar
> sanasi shu zonada hisoblanadi. `null` — platforma zonasi `Asia/Tashkent` (standart). `PUT` da `null` —
> o'zgarmaydi, `""` — standartga qaytaradi. Noto'g'ri zona — `400 AI_AGENT_TIMEZONE_INVALID`.

> **`voicemailAction`** — avtootvetchik aniqlanganda: `HANGUP` (go'shakni qo'yish),
> `LEAVE_MESSAGE` (bipdan keyin `voicemailMessage` aytiladi), `LEAVE_MESSAGE_AND_SMS`
> (xuddi shunday, qo'ng'iroq tugagach shu matn mijozga SMS bo'lib ham ketadi — summary
> bo'lmasa ham) yoki `IGNORE` (oddiy suhbat davom etadi).

> **`preConversationAudioFileId`** (ixtiyoriy, `POST`/`PUT /api/ai-agents` va javob qatorida) —
> `POST /api/files/upload` (`category=AUDIO`, [files.md](files.md)) orqali yuklangan **WAV** fayl id'si. Qo'ng'iroq ulanishi bilan,
> agent hali hech narsa demasdan chalinadi (yozib olingan ogohlantirish, brend jingle) va dialog u
> tugaguncha kutadi. Har qanday sample rate qabul qilinadi (8 kHz ga keltiriladi), stereo — chap kanal.
> Fayl qo'ng'iroq kompaniyasiniki bo'lishi shart; o'qib bo'lmasa (o'chirilgan, boshqa kompaniyaniki,
> WAV emas) qo'ng'iroq usiz davom etadi va serverda WARN log yoziladi. `null` — yo'q. U
> `callBehaviour` guruhida, shuning uchun `PUT /api/ai-agents/{id}/advanced` orqali ham o'zgaradi.

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
>   o'ylaydi va o'z ovozida gapiradi) yoki `HALF_CASCADE` (mijozni **bizning STT** eshitadi — `sttProvider`,
>   matnini engine oladi, o'ylaydi va **engine'ning o'z ovozida** — `ttsVoice` — gapiradi).
>   `HALF_CASCADE` uchun `ttsVoice` va `llmModel` `REALTIME` ovoz/modellaridan, `sttModel` `CASCADE` dan
>   tanlanadi. Agentning STT si ishga tushmasa qo'ng'iroq boshlanmaydi (`STT_START_FAILED`).
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
>   arzonroq modelda javoblanadi; qolgan hamma turn `llmModel` da qoladi. Agentning
>   `pipelineMode` idan qat'i nazar **doim `CASCADE` katalogidan** tanlanadi
>   (`/api/ai-models?kind=LLM&mode=CASCADE`, masalan `gemini-3.5-flash-lite`): `REALTIME`
>   agentda ham u barge-in qarorlarida va realtime ulanmay cascade'ga tushgan qo'ng'iroqda
>   ishlaydi. Realtime (Live) model id'si yoki noma'lum id → `400 AI_MODEL_UNKNOWN`. Bo'sh qoldirilsa o'rnatmaning umumiy sozlamasi
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
> - `speakingWarmth` — agent gapining iliqlik darajasi (standart `BALANCED`). O'zbek Yandex ovozlarida
>   (`nigora`, `zarina`) hissiyot rejimlari yo'q, shuning uchun jonlilikni faqat **matn** beradi:
>   - `RESERVED` — bir so'zli munosabat ("Tushunarli"), hamdardlik ko'pi bilan ikki so'z. Eski uslub; qat'iy
>     ohang kerak bo'lgan agent uchun.
>   - `BALANCED` — mijoz aytgan narsaga bog'langan qisqa munosabat ("Ha, ish bo'lmasa qiyin"), jonli og'zaki
>     so'zlar ("mayli", "xo'sh", "unda"), xotirjam do'stona ohang; asabiy mijozga bitta samimiy gap, iliq
>     mijozga sal iliqroq javob.
>   - `WARM` — bir-ikki qisqa iliq gap, keyin ishga qaytadi.
>
>   Har uchala darajada sahnaviy hamdardlik ("juda afsusdaman", "sizni juda yaxshi tushunib turibman")
>   taqiqlangan. Ikkala pipeline'da ishlaydi (realtime'da bir martalik ko'rsatma sifatida).
> - `languageVoiceSpeeds` — til → tezlik (`{"ru-RU": 1.1}`). Qo'ng'iroq shu tilda boshlansa yoki
>   shu tilga o'tsa shu tezlikda gapiradi; ro'yxatda yo'q til `voiceSpeed`da qoladi. Tahrirda `null`
>   — o'zgarmaydi, `{}` — tozalaydi. Chegaradan tashqari qiymat (`voiceSpeed` ham) —
>   `400 VOICE_SPEED_OUT_OF_RANGE`. Javobda (`AiAgentRow`) ham qaytadi.
> - `ttsProvider` — ovoz tanlanmagan (yoki ovozning provayderi build'dan chiqib ketgan)
>   qo'ng'iroq qaysi vendor bilan gapirishini belgilaydi; kompaniya sozlamasi shunda zaxira
>   bo'lib qoladi. `engine-options` dagi `tts` ro'yxatida yo'q id — `400 ENGINE_TTS_PROVIDER_UNKNOWN`.
> - `interruptionSensitivity` — mijoz bot gapirayotganda gap boshlasa (barge-in) sezgirlik darajasi:
>   - `MEDIUM` (standart): **Semantik Barge-In**. Bot nutqi darhol kesib tashlanmaydi, balki kutiladi. Agar mijoz shunchaki tinglayotganini bildiruvchi tasdiq so'zlarini ("aha", "xo'p", "tushunarli", "ha", "mayli", "eshitvoman", "да", "понятно" va h.k.) aytsa, bot o'z nutqini to'xtatmasdan davom ettiradi. Agar mijoz savol, e'tiroz yoki ko'rsatma aytsa ("to'xtang", "yo'q", "qancha?", "nima?", "operatorga ulang") — bot o'z so'zini to'xtatib, yangi savolga javob beradi.
>   - `LOW`: Ko'proq so'z yoki aniq e'tiroz eshitilmaguncha bot nutqini davom ettiradi.
>   - `HIGH`: Har qanday tovushda bot darhol to'xtatiladi.
>   - `OFF`: Barge-in o'chirilgan — bot o'z gapini tugatmaguncha gapirilgan so'zlar inobatga olinmaydi.

> ⚠️ **Webhook manzillari ommaviy bo'lishi shart.** `initiationWebhook.url` va
> `postCallWebhook.url` xususiy tarmoq manziliga (`127.0.0.1`, `localhost`, `10/8`,
> `172.16/12`, `192.168/16`, `169.254/16`, `fc00::/7`) qaralsa agent saqlanmaydi —
> `400 WEBHOOK_URL_INVALID`. Ichida `{{secrets.KEY}}` bo'lgan manzil saqlashda
> tekshirilmaydi, lekin yuborish paytida tekshiriladi. Imzo (`X-RobotCall-Signature`)
> va tekshirish tartibi — [webhooks.md](webhooks.md) §3.

> **Qo'ng'iroq ichidagi webhook eventlari** `postCallWebhook.events` orqali tanlanadi; bo'sh
> ro'yxat — faqat mavjud `CALL_COMPLETED` callback'i yuboriladi. Eventlar bir xil URL,
> header, secret resolution, imzo va retry mexanizmidan foydalanadi; yuborish qo'ng'iroq
> oqimini kutdirmaydi. Tanlanadigan qiymatlar: `SENTIMENT_CHANGE`, `FACT_EXTRACTED`,
> `TOOL_EXECUTED`, `STAGE_TRANSITION`, `TRANSFER_REQUESTED`, `VOICEMAIL_DETECTED` va
> `INTENT_DETECTED`. Event payload'ida `event`, `companyId`, `callAttemptId`, `channelId`,
> `agentId`, `agentName`, `occurredAt` hamda tegishli `data` bo'ladi. `FACT_EXTRACTED`
> da scalar `value` yuboriladi; matndagi karta/passport/PINFL/CVV/email PII qiymatlari
> redakt qilinadi. Caller transkripti event payload'iga kiritilmaydi. `INTENT_DETECTED`
> faqat intent aniqlagich mavjud va yoqilgan bo'lsa yuboriladi.

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
>
> **Navbatsiz transfer** (`transferPhoneNumber` yoki `voice-agent.operator.endpoint`): operator
> ko'targach unga brifing (mijoz, holat, summa) o'qiladi. Server `voice-agent.operator.require-acceptance:
> true` bo'lsa, operator keyin **1** ni bosishi kerak (`accept-timeout-sec`, standart 10 s). Operator
> `answer-timeout-sec + 5` soniyada ko'tarmasa yoki 1 dan boshqa tugma bossa/bosmasa — mijozga "barcha
> operatorlar band, birozdan so'ng qayta bog'laning" aytiladi, qo'ng'iroq `CALLBACK_REQUESTED` bilan
> yopiladi (kampaniya nishoni qayta qo'ng'iroq navbatiga tushadi). Supervisor takeover'da tasdiq
> so'ralmaydi.
>
> **LiveKit dvigatelidagi kompaniya** ([../LIVEKIT.md](../LIVEKIT.md)): uzatish faqat **oddiy raqamga**
> ishlaydi — `transferPhoneNumber` (yoki bosqichning raqami), `PJSIP/` prefiksi bo'lsa olib tashlanadi.
> Raqam kompaniyaning default trunk'i orqali chaqiriladi; brifing, "1 ni bosing" tasdig'i, `transferQueueId`
> va `voice-agent.operator.endpoint` LiveKit'da yo'q. Javob bo'lmasa — yuqoridagidek `CALLBACK_REQUESTED`.
> - Javobda (`AiAgentRow`, `GET .../advanced`) ham qaytadi.

> **O'chirilgan maydonlar (breaking change).** Qo'ng'iroq hech qachon o'qimagan oltita maydon
> API'dan va bazadan olib tashlandi (`V23__drop_ai_agent_phantom_settings.sql`). Ular endi javobda
> (`AiAgentRow`, `GET .../limits`, `GET .../advanced`) qaytmaydi; so'rovda yuborilsa e'tiborsiz
> qoldiriladi (xato bermaydi):
>
> | Maydon | O'rniga |
> |---|---|
> | `preemptiveGeneration` | Oldindan generatsiyani umumiy `voice-agent.dialog.preemptive` boshqaradi |
> | `ivrNavigationEnabled` | IVR'da tugma bosish ssenariydagi `ToolDef` orqali |
> | `voice.stability`, `voice.similarityBoost` | Hech bir TTS provayder ularni olmasdi |
> | `callBehaviour.endpointingDelayMs` | Gap tugashini umumiy `voice-agent.stt.endpointing` aniqlaydi |
> | `limits.turnTimeoutSeconds` | Navbat vaqt chegarasi agent bo'yicha qo'llanmasdi |

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

**Salomlashish (`greeting`).** Qo'ng'iroq qanday ochilishini boshqaradi. Javobda (`AiAgentRow`) va `PUT` da
keladi; `POST` da ham yuboriladi (tashlab ketilsa standart: `AI_CUSTOM`, pauza standart, qo'shimcha satrlarsiz).
Asosiy matn `script.firstMessage` bo'lib qoladi; `greeting` unga qo'shimcha.

| Maydon | Ma'nosi |
|---|---|
| `mode` | `AI_CUSTOM` (standart) — agent birinchi gapiradi, matni belgilangan (matn bo'lmasa LLM o'zi ochadi); `AI_DYNAMIC` — agent birinchi gapiradi, ochilishni LLM har safar yangidan yozadi; `USER_FIRST` — avval mijoz gapiradi, agent kutadi |
| `pauseBeforeSpeakingMs` | 0..10000. Javob berilgandan keyin agent birinchi so'zgacha kutadigan vaqt. `null` — platforma standarti (`greeting-delay-ms`). Qaytarish: `clearFields: ["GREETING_PAUSE"]` |
| `lines[]` | `{ direction?, language?, text }` — `firstMessage` dan tashqari qo'shimcha ochilish matnlari (ko'pi bilan 20). `direction`: `INBOUND`/`OUTBOUND`/`null` (ikkalasi). `language`: `ru`, `uz-UZ` kabi; `null` — istalgan til. `text` da `{{o'zgaruvchi}}` qo'ng'iroq faktlaridan to'ldiriladi. `PUT` da ro'yxat **butunlay almashtiriladi** (`[]` — hammasini o'chiradi) |

Qo'ng'iroq uchun matn tanlash: `firstMessage` ham bitta "satr" sifatida qatnashadi. Qo'ng'iroqning yo'nalishi va tiliga mos
satrlar orasidan **eng aniq mo'ljallangani** olinadi (yo'nalish ham, til ham ko'rsatilgan satr — bittasi ko'rsatilgandan,
u esa hech biri ko'rsatilmagandan ustun); teng aniqlikdagilar orasidan tasodifiy bittasi tanlanadi — shu tufayli bir nechta
satr "variant" vazifasini bajaradi. Mos satr topilmasa (yoki `mode` = `AI_DYNAMIC`) ochilishni LLM yozadi. `USER_FIRST`
da agent kutadi (kaskad va realtime ikkalasida); majburiy ochiq-oydin aytish matni (disclosure) bundan mustasno —
u baribir aytiladi. Mijoz ham, agent ham gapirmasa, jimlik nazoratchisi odatdagidek "alo?" deb so'raydi.
Kontaktdagi `agentOverrides.firstMessage`, initiation webhook'ning `firstMessage` i va uzilgan ulanishdan keyingi
uzr — bularning hammasi shu qo'ng'iroq uchun salomlashishdan ustun turadi.

**Qiymatni o'chirish (`clearFields`).** `PUT` da `null` maydon "o'zgartirma" degani, shuning uchun
ixtiyoriy sozlamani bo'shatishning yagona yo'li — uni `clearFields` ro'yxatida yuborish (ixtiyoriy,
faqat `PUT`):

```json
{ "name": "Dilnoza", "clearFields": ["POST_CALL_WEBHOOK", "FAST_LLM_MODEL"] }
```

| Qiymat | Nima bo'shatiladi |
|---|---|
| `INITIATION_WEBHOOK` | `initiationWebhook` |
| `POST_CALL_WEBHOOK` | `postCallWebhook` |
| `PRE_CONVERSATION_AUDIO_FILE_ID` | `callBehaviour.preConversationAudioFileId` |
| `MID_CALL_SMS_TEMPLATE` | `callBehaviour.midCallSmsTemplate` |
| `VOICEMAIL_MESSAGE` | `callBehaviour.voicemailMessage` |
| `FAST_LLM_MODEL` | `fastLlmModel` |
| `GREETING_PAUSE` | `greeting.pauseBeforeSpeakingMs` (platform defaultiga qaytadi) |
| `SPEECH_LANGUAGES` | `speechLanguages` (faqat agentning asosiy tili qoladi — bitta til rejimi) |

Bir xil so'rovda maydonga qiymat berib, uni `clearFields` ga ham qo'shish —
`400 AI_AGENT_CLEAR_FIELD_CONFLICT` (hech narsa yozilmaydi). Ro'yxatda yo'q maydonlar avvalgidek
birlashtiriladi; `clearFields` yuborilmasa — hech narsa o'zgarmaydi (eski klientlar buzilmaydi).

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
| `AI_AGENT_CLEAR_FIELD_CONFLICT` | faqat `PUT`: maydon ham qiymat bilan, ham `clearFields` da keldi |

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

`callPurpose` (ixtiyoriy) — qo'ng'iroq nima haqida, bir gap; ssenariydagi `callPurpose` bilan bir xil
([scenarios.md](scenarios.md#-qongiroq-turi-va-sababi--ai-mavzuni-adashtirmasligi-uchun)).

`stages[]` — ssenariydagi `StageDef` ning aynan o'zi: `branches` va mijoz javobiga qarab
o'tish shartlari (`transitionConditions`) ham shu yerda yuboriladi —
[scenarios.md](scenarios.md#-transitionconditions--mijoz-javobiga-qarab-tarmoqlanish).

**Qoidalar:**

- **Umumiy ssenariydagi agent.** Agentda `scenarioId` bo'lsa (u umumiy `Scenario` qatorini
  gapiradi), `PUT` — `409 AI_AGENT_SCENARIO_SHARED`: qo'ng'iroqda `scenarioId` agentning o'z
  `definition` idan ustun, shuning uchun bu yerdagi tahrir saqlanib, hech qachon aytilmas edi.
  Avval `PUT /api/ai-agents/{id}` da `scenarioId: null` yuboring (agent o'z ssenariysiga o'tadi),
  yoki umumiy ssenariyni `scenarios.md` orqali tahrirlang — u shu ssenariyni gapiradigan **barcha**
  agentlarga ta'sir qiladi. Bunday agentda `GET` bo'sh `stages` qaytaradi (u faqat agentning o'z
  `definition` ini o'qiydi); umumiy ssenariy `scenarioId` / `scenarioName` bilan agent qatorida (`AiAgentRow`).
- **Forma olib yurmaydigan narsalar saqlanadi.** `PUT` faqat ushbu javobdagi maydonlarni almashtiradi;
  agentning mavjud `definition` idagi `tools`, `outcomeSchema`, `factWebhook`, `styleExamples`,
  `globalNodes` o'zgarmaydi.
- **`scenarioMode` — faqat yorliq.** Qo'ng'iroqda u o'qilmaydi: ssenariy `scenarioId` → `definition` →
  `systemPrompt` tartibida tanlanadi. Mijoz yuborgan qiymat haqiqatga mos bo'lmasligi mumkin, shuning
  uchun UI rejimni undan emas, [quickstart.md](quickstart.md#oddiy-va-bosqichli-ssenariy) dagi qoidadan aniqlasin.

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
      },
      {
        "id": "callback_number",
        "type": "phone",
        "name": "Qayta qo'ng'iroq raqami",
        "description": "Mijoz aytgan telefon raqami"
      },
      {
        "id": "email",
        "type": "email",
        "name": "Email",
        "description": "Mijoz aytgan email manzil"
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

`dataNeeded[].type` qiymatlari registrga sezgir emas: `string`, `number`, `boolean`, `date`, `enum`, `phone` yoki `email`.
`phone` qiymati UZ mahalliy raqami yoki xalqaro E.164 raqami sifatida, `email` esa odatiy email sintaksisi bo'yicha tekshiriladi.
Noto'g'ri format topilsa, modeldan bir marta qayta ajratish so'raladi; qiymat yana yaroqsiz bo'lsa, natijaga kiritilmaydi va kaliti `extractionErrors` ro'yxatiga yoziladi.

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
    "silenceEndCallTimeoutSeconds": 25
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
    "initiationWebhook": {
      "url": "https://api.example.com/webhooks/call-started",
      "headers": { "Authorization": "Bearer secret_token" },
      "timeoutSeconds": 5
    },
    "postCallWebhook": {
      "url": "https://api.example.com/webhooks/call-ended",
      "headers": { "Authorization": "Bearer secret_token" },
      "timeoutSeconds": 10,
      "events": ["STAGE_TRANSITION", "TRANSFER_REQUESTED"]
    },
    "emotionAdaptiveVoice": true,
    "speakingWarmth": "BALANCED",
    "ambience": {
      "background": "OFFICE",
      "backgroundVolume": 0.35,
      "backgroundFadeInSeconds": 1.5,
      "thinking": "KEYBOARD_TYPING",
      "thinkingVolume": 0.30,
      "noiseCancellationEnabled": true,
      "noiseCancellationMode": "BACKGROUND_NOISE_SUPPRESSION"
    },
    "callBehaviour": {
      "interruptionSensitivity": "MEDIUM",
      "dtmfInputEnabled": true,
      "voicemailAction": "LEAVE_MESSAGE",
      "voicemailMessage": "Assalomu alaykum, siz bilan bog'lana olmadik. Iltimos qaytib qo'ng'iroq qiling.",
      "midCallSmsEnabled": false,
      "midCallSmsTemplate": null,
      "transferPhoneNumber": null,
      "transferMessage": null,
      "transferQueueId": 4,
      "preConversationAudioFileId": null
    },
    "dataPolicy": {
      "zeroPiiRetention": false,
      "storeCallAudio": true,
      "conversationRetentionDays": 60,
      "realtimeFactsDelivery": "TOOL"
    }
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

Qo'ng'iroq yakunlangach (suhbat muvaffaqiyatli bo'ladimi yoki javobsiz/band `NO_ANSWER`, `BUSY`, `FAILED` bo'ladimi) `triggerDisposition` mos kelganda avtomatik ishga tushuvchi amallar: mijozga SMS yuborish (masalan: ko'tarmagan mijozga qayta aloqa havolasi yuborish), CRM webhook chaqirish yoki Telegram operator guruhiga xabar berish. `onlyOnSuccess: false` qilib qo'yilsa va `triggerDisposition: NO_ANSWER` berilsa, LLM summary'si bo'lmagan qo'ng'iroqlarda ham SMS/webhook ishonchli ishlaydi (`{{phone}}` va `{{disposition}}` to'ldiriladi).

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

**Publish gate** (`AGENT_EVAL_PUBLISH_GATE=TEXT|VOICE`, standart — o'chiq). Yoqilgan bo'lsa va agentda
test holatlari bo'lsa, qoralama faqat **aynan shu konfiguratsiyaning** (agent + u gapiradigan
ssenariy, `configHash` bo'yicha) eval yugurishi hamma holatdan o'tgan bo'lsagina e'lon qilinadi.
`VOICE` gate faqat `mode: VOICE` yugurishni qabul qiladi, `TEXT` — ikkalasini ham. Aks holda
`409 AI_AGENT_EVAL_REQUIRED`. Qoralama evaldan keyin o'zgarsa, eval qaytadan kerak bo'ladi.

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
qoralama shu versiyadan farq qiladimi). Ikki versiyani solishtirish — §14.1 dagi `diff` endpoint.

### 14.1. `GET /api/ai-agents/{id}/versions/diff?from=3&to=5`

`from` versiyadan `to` versiyagacha o'zgargan sozlamalar. `to` berilmasa — `from` qoralama bilan
solishtiriladi, ya'ni "publish nimani o'zgartiradi" degan savolga javob beradi.

```json
{
  "success": true,
  "data": [
    { "path": "firstMessage", "before": "Assalomu alaykum", "after": "Salom, men Dilfuza" },
    { "path": "voice.ttsVoiceId", "before": "uz-dilfuza", "after": "uz-madina" },
    { "path": "dataNeeded", "before": [ { "key": "promised_date", "type": "date" } ], "after": [] }
  ]
}
```

- `path` — agent ichidagi nuqtali yo'l. Obyektlar ichiga kirib solishtiriladi, ro'yxat esa butunligicha
  bitta qiymat deb olinadi.
- `before`/`after` — JSON qiymat; sozlama yo'q bo'lsa `null`.
- `enabled`, `createdAt`, `createdBy` solishtirilmaydi — ular qo'ng'iroqni o'zgartirmaydi.
- Ro'yxat yo'l bo'yicha tartiblangan, farq bo'lmasa bo'sh.
- Versiya topilmasa — `404 AI_AGENT_VERSION_NOT_FOUND`.

### 14.2. Rollout — nomzod versiya, trafik bo'linishi va staging

Oddiy `publish` yangi versiyani darhol 100% qo'ng'iroqqa chiqaradi. **Rollout** esa qoralamani
**nomzod** (`CANDIDATE`) sifatida e'lon qiladi:
- nomzod trafikning `trafficPercent` foiziga va har bir **staging raqami**ga beriladi;
- qolgan qo'ng'iroqlar live (`PUBLISHED`) versiyada qoladi;
- natijalar solishtirilgach, nomzod `promote` qilinadi (100%) yoki bekor qilinadi.

Qaysi qo'ng'iroqlarga ta'sir qiladi:
- **Kiruvchi** (inbound route va call flow ichidagi agent) — qo'ng'iroq qilgan raqam bo'yicha.
- **Kampaniya** va **qo'lda** qilingan qo'ng'iroqlar — terilgan raqam bo'yicha.
- Chat/widget va brauzerdagi test (`web-test`) bundan mustasno — ular avvalgidek live versiya yoki
  so'ralgan versiyada qoladi.

Trafik qanday bo'linadi:
- **Raqam bo'yicha**, qo'ng'iroq bo'yicha emas: bitta mijoz rollout davomida doim bitta versiyaga
  tushadi. Shuning uchun natija ikkala versiyani ham eshitgan mijozlar bilan aralashmaydi.
- Raqami yashirilgan qo'ng'iroq doim live versiyada qoladi.
- Qo'ng'iroqda ishlatilgan versiya `call_attempt.agent_version` ga yoziladi, shuning uchun hisobot,
  sifat va `GET /versions` natijalari versiya bo'yicha avtomatik ajraladi.

Bitta agentda bir vaqtda bitta rollout bo'ladi. Rollout davomida `publish` va `rollback` —
`409 AGENT_ROLLOUT_ACTIVE`: avval `promote` yoki bekor qiling. Qoralamani tahrirlash mumkin, lekin bu
nomzodga ta'sir qilmaydi — nomzod rollout boshlangandagi snapshot.

#### `POST /api/ai-agents/{id}/rollout`

```json
{ "note": "yangi salomlashish", "trafficPercent": 10, "stagingPhones": ["+998901112233"] }
```

| Maydon | Turi | Majburiy | Cheklov |
|---|---|---|---|
| `note` | string | yo'q | ≤ 500; versiya tarixida ko'rinadi |
| `trafficPercent` | int | ha | 0–100. `0` — faqat staging raqamlari (production'ga chiqmasdan sinash) |
| `stagingPhones` | string[] | yo'q | ≤ 20 ta, istalgan formatda — normallashtiriladi |

`trafficPercent: 0` va bo'sh `stagingPhones` birga bo'lishi mumkin emas — nomzodga hech kim tushmaydi.

Shartlar:
- Agentning live versiyasi bo'lishi kerak, aks holda `409 AGENT_ROLLOUT_NOT_PUBLISHED` — hech qachon
  e'lon qilinmagan agentni avval oddiy `publish` qiling.
- Qoralama live versiyadan farq qilishi kerak, aks holda `409 AI_AGENT_NOTHING_TO_PUBLISH`.
- Eval gate yoqilgan bo'lsa, `publish` dagi kabi `409 AI_AGENT_EVAL_REQUIRED` qaytishi mumkin (§15).

Audit: `AI_AGENT_ROLLOUT_START`. Javob — `AgentRolloutRow`:

```json
{
  "success": true,
  "data": {
    "candidateVersion": 6,
    "liveVersion": 5,
    "trafficPercent": 10,
    "stagingPhones": ["+998901112233"],
    "startedAt": "2026-10-09T10:00:00Z",
    "startedBy": 3,
    "live":      { "version": 5, "calls": 412, "conversions": 118, "conversionRate": 28.6 },
    "candidate": { "version": 6, "calls": 47,  "conversions": 16,  "conversionRate": 34.0 }
  }
}
```

`live` va `candidate` — rollout **boshlangandan beri** boshlangan qo'ng'iroqlar, ikkala tomon uchun bir
xil oyna. Konversiya — `PROMISE_TO_PAY`, `GOAL_REACHED` yoki `COMPLETED`. `conversionRate` qo'ng'iroq
bo'lmasa `null`.

#### `PUT /api/ai-agents/{id}/rollout`

`{ "trafficPercent": 50, "stagingPhones": [] }` — foizni oshirish/kamaytirish yoki staging ro'yxatini
almashtirish. Nomzod va boshlangan vaqt o'zgarmaydi. Foiz oshirilganda avval nomzodga tushgan raqamlar
nomzodda qoladi, faqat yangilari qo'shiladi. Audit: `AI_AGENT_ROLLOUT_UPDATE`.

#### `GET /api/ai-agents/{id}/rollout`

Joriy `AgentRolloutRow`. Rollout yo'q bo'lsa — `404 AGENT_ROLLOUT_NOT_FOUND`.

#### `POST /api/ai-agents/{id}/rollout/promote`

Nomzod `PUBLISHED` bo'ladi, oldingi live versiya `ARCHIVED`, rollout tugaydi. Javob — yangi live
versiyaning `AiAgentVersionRow`i. Audit: `AI_AGENT_ROLLOUT_PROMOTE`.

#### `DELETE /api/ai-agents/{id}/rollout`

Nomzod `ARCHIVED` bo'ladi, hamma yana live versiyada. Versiya tarixda qoladi — keyin `rollback` bilan
qaytarish mumkin. Audit: `AI_AGENT_ROLLOUT_CANCEL`.

| Status | `messageCode` | Qachon |
|---|---|---|
| 400 | `AGENT_ROLLOUT_INVALID` | Foiz 0–100 dan tashqarida, 20 tadan ko'p yoki o'qib bo'lmaydigan raqam, nomzodga hech kim tushmaydi — `errors` ro'yxatida |
| 404 | `AGENT_ROLLOUT_NOT_FOUND` | Agentda rollout yo'q |
| 409 | `AGENT_ROLLOUT_ACTIVE` | Rollout ketayotganda yangi rollout, `publish` yoki `rollback` |
| 409 | `AGENT_ROLLOUT_NOT_PUBLISHED` | Agent hali hech qachon e'lon qilinmagan |

### 14.3. `GET /api/ai-agents/{id}/estimate` — narx, kechikish va tokenlar

Permission: `AI_AGENT_READ`. Agent studiyasining yuqori qatori uchun ("Narx 471 so'm/daq ·
Kechikish 1280–1610ms · Tokenlar 733–2.9k"). Har bir qiymat **alohida** tanlanadi: agentning
oxirgi 50 ta tugagan qo'ng'iroqida o'lchangan bo'lsa — o'sha, aks holda agentning prompti va
joriy narxlardan (`config/billing.yml`) taxmin.

```json
{
  "accept": true,
  "data": {
    "costPerMinuteUzs": 471,
    "costEstimated": true,
    "latencyFromMs": null,
    "latencyToMs": null,
    "tokensFrom": 1150,
    "tokensTo": 2350,
    "tokensEstimated": true,
    "sampleCalls": 0
  }
}
```

| Maydon | Izoh |
|---|---|
| `costPerMinuteUzs` | Bir daqiqa suhbat narxi, so'm. O'lchanganda: oxirgi qo'ng'iroqlarning `call_billing` (`SETTLED`) jami summasi ÷ ularning davomiyligi × 60 — summary va tahlil to'lovlari ham ichida |
| `costEstimated` | `true` — agentning hali birorta qo'ng'irog'i hisoblanmagan, narx bir daqiqalik taxminiy sarfdan billing kalkulyatori bilan hisoblangan (pastda) |
| `latencyFromMs` / `latencyToMs` | Qo'ng'iroqlarning o'rtacha turn kechikishi (`call_technical.avg_turn_latency_ms`): mediana va 90-persentil. Kechikish taxmin qilinmaydi — o'lchangan qo'ng'iroq bo'lmasa `null` (UI "—" ko'rsatadi) |
| `tokensFrom` / `tokensTo` | Bir turn'dagi tokenlar (prompt + javob). O'lchanganda: `(prompt_tokens + completion_tokens) / turn_count` ning 10- va 90-persentili |
| `tokensEstimated` | `true` — token oralig'i qo'ng'iroqlardan emas, promptdan hisoblangan |
| `sampleCalls` | O'lchangan qiymatlar olingan qo'ng'iroqlar soni (≤ 50) |

**Taxmin qanday hisoblanadi** (`AgentCostEstimator`):

- Prompt tokenlari = 700 (freymvork: qo'ng'iroq ramkasi, guardrail'lar, javob qoidalari) +
  (`systemPrompt` + `firstMessage` + ssenariyning `rolePrompt` va bosqichlar `purpose` i) belgilari ÷ 3.
- `tokensFrom` = prompt + 40 (birinchi turn, tarix yo'q); `tokensTo` = prompt + 20 turn tarixi × 60 + 40.
- Bir daqiqa (`CASCADE`): 6 turn, har biri o'rtacha prompt bilan, 40 token javob, 450 belgi TTS va 60 s liniya.
  `REALTIME` / `HALF_CASCADE`: 60 s realtime engine + liniya — tokenlar narxlanmaydi.
- Narx — `CallBillingUseCase.findUsageCostUzs`, ya'ni haqiqiy qo'ng'iroq hisoblanadigan kalkulyatorning o'zi
  (ustama va platforma to'lovi bilan). Kompaniyaning o'z LLM'i hisobga olinmaydi — taxmin platforma narxida.

| Status | `messageCode` | Qachon |
|---|---|---|
| 404 | `AI_AGENT_NOT_FOUND` | Agent yo'q yoki boshqa kompaniyaniki |

### 14.4. `POST /api/ai-agents/{id}/test-chat` — LLM test

Agentning **qoralamasi** (oxirgi saqlangani) bilan matnli suhbatning bitta navbati. Javob qo'ng'iroqdagi
bilan bir xil yo'ldan o'tadi (`TextTurnEngine`): o'sha prompt, model, `fastLlmModel`, persona,
`handbook`, `timezone`, ssenariy bosqichlari, tool'lar va fact guard. Server hech narsa saqlamaydi —
sahifa har navbatda butun suhbatni, oldingi javobdagi `stage` va `outcome` ni qaytarib yuboradi.

- MCP tool'lar (CRM) **ishlamaydi** — test haqiqiy CRM'ga lid yoki izoh yozmasligi uchun.
- Kompaniyaning REST tool'lari haqiqatan chaqiriladi, agar `toolMocks` berilmagan bo'lsa. `toolMocks`
  berilsa, hech bir tool chaqirilmaydi: ro'yxatdagisi shu matn bilan javob beradi, ro'yxatda yo'qi
  "mavjud emas" deydi. Mock chaqiruvlari `toolCalls` da qaytadi.
- Model tokenlari sarflanadi, qo'ng'iroq billing'iga yozilmaydi.

So'rov:

```json
{
  "messages": [
    { "role": "AGENT", "text": "Assalomu alaykum, Aziz aka! ..." },
    { "role": "CLIENT", "text": "Ha, eshitaman" }
  ],
  "clientText": "Qancha qarzim bor?",
  "stage": "IDENTIFY",
  "outcome": {},
  "variables": { "clientName": "Aziz", "debtAmount": 450000 },
  "toolMocks": null
}
```

| Maydon | Izoh |
|---|---|
| `messages` | Hozirgacha aytilganlar, eskidan yangiga (`role`: `AGENT` / `CLIENT`). Bo'sh — suhbat boshlanmoqda. Ko'pi bilan 200 ta |
| `clientText` | Mijozning yangi gapi. `null`/bo'sh faqat bo'sh suhbatda ruxsat: agent salomlashadi (`firstMessage` bo'lsa — aynan o'sha, LLM'siz). Aks holda `400 AGENT_TEST_CHAT_TEXT_REQUIRED` |
| `stage`, `outcome` | Oldingi javobdan qaytariladi; birinchi navbatda `null` |
| `variables` | Dinamik o'zgaruvchilar — haqiqiy qo'ng'iroqda target'dan keladigan faktlar (`{{clientName}}`) |
| `toolMocks` | Tool nomi → tayyor javob matni; `null` — haqiqiy tool'lar |

Javob (`ResponseData` ichida):

```json
{
  "reply": "Aziz aka, sizda 450 000 so'm qarzdorlik bor. Qachon to'lay olasiz?",
  "stage": "NEGOTIATE",
  "outcome": {},
  "toolCalls": [],
  "violations": [],
  "ended": false,
  "disposition": null,
  "latencyMs": 1240
}
```

`violations` — fact guard ushlagan raqamlar (qo'ng'iroqda ular aytilmasdi). `ended` bo'lsa `disposition` —
agent suhbatni qanday yakunlagani. Server'da LLM sozlanmagan bo'lsa — `409 AGENT_TEST_CHAT_UNAVAILABLE`;
model bo'sh javob qaytarsa — `502 CHAT_NO_REPLY`.

### `POST /api/ai-agents/{id}/clone`

Qoralamaning nusxasi: `"<nom> (nusxa)"`, e'lon qilinmagan, o'sha tool bog'lanishlari bilan.
Bilim manbalari (knowledge sources) nusxalanmaydi — ular yangi agentga qayta yuklanadi.
Javob — yangi agentning `AiAgentRow`i. Audit: `AI_AGENT_CLONE`.

### Qoralamani sinash

`POST /api/calls/web-test` `{ "campaignId": 12, "agentVersion": null }` — kampaniya
agentining **qoralamasi** bilan brauzerdan gaplashish (standart); `"agentVersion": 3` —
aynan 3-versiya. Kampaniyasiz, agentning o'zi: `{ "aiAgentId": 5, "agentVersion": null }`
(faktlar — `voice-agent.dialog.test-context`). Batafsil — [calls.md](calls.md).

---

## 15. Eval — test holatlari va sinov yugurishlari

Agentning o'z regressiya to'plami. **Test holati** — ikkinchi model o'ynaydigan qo'ng'iroq
qiluvchi (persona) va qo'ng'iroq nima bilan tugashi kerakligi. **Eval** har bir holatni
agentga qarshi simulyatsiya qiladi (matnli, audiosiz). Agent tomoni haqiqiy qo'ng'iroqdagidek
quriladi: o'sha prompt, bosqich tool'lari, FactGuard va `firstMessage` bilan ochilish.
Kompaniyaning REST tool'lari faqat holat `toolMocks` yoki `expectTools` bersa taklif qilinadi va
**mock javob** bilan javob beradi (§ "Tool mock"); MCP tool'lari simulyatsiyada yo'q. Eval hech qachon
haqiqiy CRM'ga so'rov yubormaydi. Promptni e'lon qilishdan oldin qoralamani tekshirish uchun.

| Metod | URL | Permission | Tavsif |
|---|---|---|---|
| `GET` | `/api/ai-agents/{id}/test-cases` | `AI_AGENT_READ` | Test holatlari |
| `POST` | `/api/ai-agents/{id}/test-cases` | `AI_AGENT_EDIT` | Yangi holat |
| `PUT` | `/api/ai-agents/{id}/test-cases/{caseId}` | `AI_AGENT_EDIT` | Holatni to'liq almashtirish |
| `DELETE` | `/api/ai-agents/{id}/test-cases/{caseId}` | `AI_AGENT_EDIT` | O'chirish |
| `POST` | `/api/ai-agents/{id}/evaluate?version=&mode=&repeat=` | `AI_AGENT_EDIT` yoki `AGENT_EVAL_RUN` | Evalni boshlash (fonda); `version` yo'q — qoralama; `mode` — `TEXT` (standart) yoki `VOICE`; `repeat` — har holat necha marta (1–10, standart 1) |
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
  "maxTurns": 12,
  "toolMocks": { "crm_lookup": "{\"debt\": 1500000, \"dueDate\": \"2026-09-30\"}" },
  "expectTools": ["crm_lookup"]
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
| `toolMocks` | Agentning kompaniya tool'i nomi → simulyatsiyada qaytaradigan javob matni (≤ 20 ta, javob ≤ 4000 belgi). Faqat `TEXT` rejimda |
| `expectTools` | Agent qo'ng'iroq davomida chaqirishi shart bo'lgan tool nomlari (≤ 20 ta) |

### Holat qachon o'tadi

Hammasi bajarilsa: kutilgan `disposition`/`stage`, har bir `mustSay` aytilgan, birorta
`mustNotSay` aytilmagan, har bir `expectTools` tool'i chaqirilgan, FactGuard faktlarda yo'q raqamni ushlamagan, va sifat hakami
(`CallQualityJudge`, har holatda, namunasiz) guardrail buzilishini topmagan. Hakamning
`score` bahosi natijaga yoziladi; eval ishlab chiqarish QA metrikalariga ta'sir qilmaydi.

### Tool mock

Holat `toolMocks` yoki `expectTools` bersa, `TEXT` simulyatsiyada agentga uning kompaniya
tool'lari haqiqiy qo'ng'iroqdagidek taklif qilinadi: o'sha nom, tavsif va argument sxemasi,
bosqich bo'yicha cheklov ham bir xil. Lekin hech qanday so'rov yuborilmaydi:
- `toolMocks` da bor tool o'sha matnni javob sifatida qaytaradi;
- yo'q tool `{"error":"service unavailable"}` qaytaradi — agentning xatoni qanday hal qilishi ham sinaladi;
- secret o'qilmaydi, `tool_execution` ga yozilmaydi, `DESTRUCTIVE` tool'lar real qo'ng'iroqdagidek berilmaydi.

Har bir chaqiruv transkriptga undan keyingi `AGENT:` qatoridan oldin yoziladi:
`TOOL: crm_lookup {"phone":"+998901234567"}`. `expectTools` shu qatorlar bo'yicha tekshiriladi.

Ikkala maydon ham bo'sh holat avvalgidek ishlaydi — kompaniya tool'lari umuman taklif qilinmaydi.
`VOICE` rejimda tool mock yo'q: u yerda kompaniya tool'lari berilmaydi.

### Takroriy yurgizish va beqaror holatlar

`repeat=5` — har holat 5 marta o'ynaladi. LLM javobi har safar farq qiladi, shuning uchun bitta
o'tish ishonchlilikni ko'rsatmaydi. Har urinish alohida natija (`attempt`: 1…5) bo'lib yoziladi.
`run.total` = holatlar × `repeat`, `run.passed` — o'tgan **urinishlar** soni. Publish oldidagi eval
gate barcha urinishlar o'tishini talab qiladi.

### CI'dan ishga tushirish

`AGENT_EVAL_RUN` huquqi berilgan API kalit ([api-keys.md](api-keys.md)) faqat `evaluate` ni
chaqira oladi. Natijani `AI_AGENT_READ` bilan o'qiydi. Agentni tahrirlash yoki publish qilish
kalitga baribir yopiq:

```bash
RUN=$(curl -s -X POST -H "X-Api-Key: $KEY" \
  "$HOST/api/ai-agents/7/evaluate?mode=TEXT&repeat=3" | jq .data.id)
until [ "$(curl -s -H "X-Api-Key: $KEY" "$HOST/api/ai-agents/7/eval-runs/$RUN" | jq -r .data.run.status)" != "RUNNING" ]; do
  sleep 10
done
curl -s -H "X-Api-Key: $KEY" "$HOST/api/ai-agents/7/eval-runs/$RUN" \
  | jq -e '.data.run.status == "DONE" and .data.run.passed == .data.run.total'
```

### Rejim: `TEXT` va `VOICE`

- `TEXT` (standart) — persona gaplari agentga toza matn bo'lib boradi. Tez va arzon, agent
  **qarorlarini** sinaydi.
- `VOICE` — persona gapi TTS bilan ovozga aylanadi, ustiga fon shovqini qo'shiladi
  (`SIMULATION_VOICE_SNR_DB`, standart 15 dB) va RTP orqali **haqiqiy** qo'ng'iroq pipeline'idan
  o'tadi: VAD, speech gate, STT, turn detection, barge-in, botning TTS'i. Har holat real vaqtda
  yuradi (bir necha daqiqa) va standart STT/TTS provayderlariga pul sarflaydi. `transcript` —
  bot **eshitgan** matn (STT natijasi), persona aytgan matn emas. Kompaniya REST/MCP tool'lari
  va mid-call SMS berilmaydi; billing, CRM, webhook ishlamaydi. Har holat `call_attempt`
  qatorini `phone: "VOICE-SIM"` bilan qoldiradi.

### Yugurish

`POST .../evaluate` darhol `AgentEvalRun` qaytaradi (`status: RUNNING`); holatlar fonda
ketma-ket o'ynaladi, `GET .../eval-runs/{runId}` bilan kuzatiladi:

```json
{
  "accept": true,
  "data": {
    "run": {
      "id": 31, "agentId": 7, "agentVersion": null, "mode": "VOICE",
      "configHash": "9f2c…e1", "status": "DONE",
      "total": 5, "passed": 4, "avgQaScore": 82, "error": null,
      "startedAt": "2026-09-13T09:00:00Z", "finishedAt": "2026-09-13T09:01:40Z",
      "repeat": 1
    },
    "cases": [
      { "testCaseId": 3, "caseName": "Chegirma so'raydi", "attempts": 1, "passed": 0, "passRate": 0.0, "flaky": false }
    ],
    "results": [
      {
        "id": 120, "runId": 31, "testCaseId": 3, "caseName": "Chegirma so'raydi",
        "passed": false, "disposition": "PROMISE_TO_PAY", "stage": "CLOSING",
        "problems": ["expected disposition REFUSED, got PROMISE_TO_PAY", "said what it must not: 50%"],
        "transcript": "AGENT: Assalomu alaykum...\nCLIENT: ...",
        "qaScore": 41, "guardrailViolation": true, "attempt": 1
      }
    ]
  }
}
```

Bitta agentda bir vaqtda bitta yugurish: ikkinchisi `409 AGENT_EVAL_ALREADY_RUNNING`
(server qayta ishga tushsa, 30 daqiqadan eski `RUNNING` hisobga olinmaydi). Holat yo'q
bo'lsa `409 AGENT_EVAL_NO_TEST_CASES`; noma'lum `version` — `404 AI_AGENT_VERSION_NOT_FOUND`.
LLM sozlanmagan bo'lsa har holat `problems`da sababi bilan yiqiladi.

`cases` — har holatning barcha urinishlari bo'yicha xulosasi: `passRate` foizda, `flaky: true` — ba'zi
urinishlar o'tgan, ba'zilari yo'q (agent bu holatda ishonchsiz). `repeat` 1–10 dan tashqarida —
`400 AGENT_EVAL_REPEAT_INVALID`.

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
