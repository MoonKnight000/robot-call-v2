# Tez boshlash — agent yaratib, qo'ng'iroq qilish

Frontend uchun "oddiy rejim" shartnomasi: foydalanuvchi quyidagi 5 qadamni ko'radi, qolgan
hamma sozlama "Qo'shimcha" ichida. Bu yangi endpoint emas — mavjud endpointlarning eng kichik
to'plami ([SIMPLIFICATION-PLAN.md](../SIMPLIFICATION-PLAN.md), 1-bosqich).

## 1. Shablon tanlash (ixtiyoriy)

`GET /api/ai-agents/templates?language=uz-UZ` → `TemplatePresetDto[]`. Tanlangan shablonning
`templateId` si 2-qadamda shu nom bilan yuboriladi ([ai-agents.md](ai-agents.md#3-get-apiai-agentstemplates)).

## 2. Agent yaratish — `POST /api/ai-agents`

Majburiy maydon **faqat `name`**. Oddiy rejimda ko'rsatiladigan 5 tasi:

| Forma maydoni | JSON maydon |
|---|---|
| Nom | `name` |
| Vazifa (prompt) | `systemPrompt` |
| Til | `language` |
| Ovoz | `ttsVoice` (ro'yxat: `GET /api/ai-agents/engine-options`) |
| Birinchi gap | `firstMessage` |

Shablon tanlangan bo'lsa qo'shimcha `templateId`; bo'sh `systemPrompt` shablonnikiga tushadi.
Sifat darajasi uchun `GET /api/ai-agents/engine-profiles` dan profil tanlanadi va `engineProfileId`
yuboriladi: u STT, LLM, `temperature`, `maxOutputTokens` ni bir marta to'ldiradi
([ai-agents.md](ai-agents.md#31-get-apiai-agentsengine-profiles--tayyor-dvigatel-toplamlari)).
Tahrirlash formasi ham shu bitta tanlovni ko'rsatadi: hozirgisi `AiAgentRow.engineProfileId`
(`null` → "Maxsus"), o'zgartirilsa `PUT` ga `engineProfileId` yuboriladi.
Qolgan barcha maydonlar (`CreateAiAgentRequest`) — "Qo'shimcha sozlamalar".

## 3. Sinash — `POST /api/calls/web-test`

Body: `{ "aiAgentId": <id> }` — kampaniyasiz, agentning o'z profili bilan, brauzerdagi mikrofon
orqali. `agentVersion: null` — hali e'lon qilinmagan qoralama bilan gaplashadi
([calls.md](calls.md#post-apicallsweb-test--brauzerdan-test-qongiroqi-mikrofon--ai)).

## 4. Kampaniya — `POST /api/campaigns`

| Wizard qadami | JSON maydon |
|---|---|
| Nom | `name` |
| Maqsad: Qarz / So'rovnoma | `type` — **majburiy** (`DEBT_COLLECTION` \| `SURVEY`) |
| Agent | `aiAgentId` — AI rejimida majburiy |
| Jadval | `dialWindowStart`, `dialWindowEnd`, `dialDays` |
| Urinishlar | `maxAttempts` |

Berilmasa default: oyna 09:00–20:00, har kuni, `maxAttempts` 3, `maxConcurrentCalls` 20,
`dialMode` AI, `priority` 3, `recurrenceType` ONCE.

Kontaktlar: `POST /api/campaigns/{id}/targets/csv` (`text/csv`); avval
`.../targets/csv/preview` bilan ustunlarni ko'rsatish mumkin.

## 5. Ishga tushirish — `POST /api/campaigns/{id}/start`

## Oddiy va Bosqichli ssenariy

Foydalanuvchi ikki variantni ko'radi; `scenarioId` / `definition` farqi UI'da ko'rinmaydi.

| UI rejimi | Qachon (agent qatoridan, `AiAgentRow`) | Tahrirlash |
|---|---|---|
| **Oddiy** | `scenarioId == null` va `scenarioDefinition` yo'q yoki bitta bosqichli | `systemPrompt`, `firstMessage` — `PUT /api/ai-agents/{id}` |
| **Bosqichli (o'zining)** | `scenarioId == null` va `scenarioDefinition.stages` bittadan ko'p | `PUT /api/ai-agents/{id}/scenario` |
| **Bosqichli (umumiy)** | `scenarioId != null` | Umumiy ssenariy (`scenarios.md`); ogohlantiring: u shu ssenariyni gapiradigan barcha agentlarga ta'sir qiladi. Agentga xos qilish uchun `scenarioId: null` |

`scenarioMode` maydoniga tayanmang — u qo'ng'iroqda o'qilmaydigan yorliq
([ai-agents.md](ai-agents.md#8-agent-scenario--behavior--apiai-agentsidscenario)).
Oddiydan Bosqichliga o'tish: `PUT /api/ai-agents/{id}/scenario` ga `stages` yuboriladi
(agentning `systemPrompt` i `rolePrompt` bo'lib qoladi).

A/B variantlar (`CampaignVariant`) kampaniya sahifasida emas, alohida "Tajriba" bo'limida.

## "Qo'shimcha" ichiga yashiriladigan maydonlar

**Kampaniya:** `recurrenceType`, `recurringDayOfMonth`, `cronExpression`, `autoResetTargets`,
`priority`, `retryPolicy`, `retryIntervalMinutes`, `startAt`, `endAt`, `budgetUzs`,
`dailyBudgetUzs`, `dailyCallCap`, `maxConcurrentCalls`, `dialMode`, `callQueueId`,
`maxAiOverflowPercent`, `targetOrdering`; A/B variantlar (`CampaignVariant`).

**Agent:** `pipelineMode`, `realtimeProvider`, `pipecat*`, `stt*`, `ttsProvider`, `ttsModel`,
`voice*`, `languageVoices*`, `persona`, `llmModel`, `fastLlmModel`, `temperature`,
`maxOutputTokens` (profil bilan almashadi), `dataNeeded`, `dataEvaluation`, `zeroPiiRetention`, `storeCallAudio`,
`conversationRetentionDays`, `realtimeFactsDelivery`, `*Limit*`, `*Seconds`, webhook'lar,
`ambient*`, `thinkingSound`, `noiseCancellation*`, `emotionAdaptiveVoice`, `speakingWarmth`,
`dtmfInputEnabled`, `voicemail*`, `midCallSms*`, `transfer*`, `interruptionSensitivity`,
`endpointingDelayMs`, `sipTrunkIds`, `scenarioId`, `scenarioMode`, `scenarioDefinition`,
`preemptiveGeneration`, `ivrNavigationEnabled`, `useRag`, `preConversationAudioFileId`.

> Ro'yxat `CreateAiAgentRequest` / `CreateCampaignRequest` dan olingan; yangi maydon
> qo'shilganda bu yerga ham yoziladi.

**Hech qayerda ko'rsatilmasin (qo'ng'iroqqa ta'sir qilmaydi):** `preemptiveGeneration`,
`ivrNavigationEnabled`, `voiceStability`, `voiceSimilarityBoost`, `endpointingDelayMs`,
`turnTimeoutSeconds` — [ai-agents.md](ai-agents.md#4-post-apiai-agents--agent-yaratish).
