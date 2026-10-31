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
| Vazifa (prompt) | `script.systemPrompt` |
| Til | `language` |
| Ovoz | `voice.defaultVoice` (ro'yxat: `GET /api/ai-agents/engine-options`) |
| Birinchi gap | `script.firstMessage` |

Shablon tanlangan bo'lsa qo'shimcha `script.templateId`; bo'sh `script.systemPrompt` shablonnikiga tushadi.
Agent maydonlari guruhlangan — to'liq jadval: [ai-agents.md](ai-agents.md#4-post-apiai-agents--agent-yaratish).
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
| Jadval | `schedule.dialWindowStart`, `schedule.dialWindowEnd`, `schedule.dialDays` |
| Urinishlar | `retry.maxAttempts` |

Berilmasa default: oyna 09:00–20:00, har kuni, `retry.maxAttempts` 3, `limits.maxConcurrentCalls` 20,
`dialing.mode` AI, `priority` 3, `recurrence.type` ONCE. Guruhlar jadvali:
[campaigns.md](campaigns.md#post-apicampaigns--yangi-kampaniya-yaratish).

Kontaktlar: `POST /api/campaigns/{id}/targets/csv` (`text/csv`); avval
`.../targets/csv/preview` bilan ustunlarni ko'rsatish mumkin.

## 5. Ishga tushirish — `POST /api/campaigns/{id}/start`

**Yoki 4–5-qadam bitta so'rovda:** `POST /api/campaigns/batch-call` —
`{ name, type, aiAgentId, csv, schedule?, immediate? }`. Kampaniya yaratiladi, CSV yuklanadi va
boshlanadi; biror qadam rad etilsa hech narsa saqlanmaydi
([campaigns.md](campaigns.md#post-apicampaignsbatch-call--bitta-sorovda-agent--csv--boshlash)).

## Oddiy va Bosqichli ssenariy

Foydalanuvchi ikki variantni ko'radi; `scenarioId` / `definition` farqi UI'da ko'rinmaydi.

| UI rejimi | Qachon (agent qatoridan, `AiAgentRow`) | Tahrirlash |
|---|---|---|
| **Oddiy** | `script.scenarioId == null` va `script.definition` yo'q yoki bitta bosqichli | `script.systemPrompt`, `script.firstMessage` — `PUT /api/ai-agents/{id}` |
| **Bosqichli (o'zining)** | `script.scenarioId == null` va `script.definition.stages` bittadan ko'p | `PUT /api/ai-agents/{id}/scenario` |
| **Bosqichli (umumiy)** | `script.scenarioId != null` | Umumiy ssenariy (`scenarios.md`); ogohlantiring: u shu ssenariyni gapiradigan barcha agentlarga ta'sir qiladi. Agentga xos qilish uchun `script.scenarioId: null` |

`script.mode` maydoniga tayanmang — u qo'ng'iroqda o'qilmaydigan yorliq
([ai-agents.md](ai-agents.md#8-agent-scenario--behavior--apiai-agentsidscenario)).
Oddiydan Bosqichliga o'tish: `PUT /api/ai-agents/{id}/scenario` ga `stages` yuboriladi
(agentning `systemPrompt` i `rolePrompt` bo'lib qoladi).

A/B variantlar (`CampaignVariant`) kampaniya sahifasida emas, alohida "Tajriba" bo'limida.

## "Qo'shimcha" ichiga yashiriladigan maydonlar

**Kampaniya:** butun `recurrence`, `limits` va `dialing` guruhlari; `retry.intervalMinutes`,
`retry.policy`, `schedule.startAt`, `schedule.endAt`, `priority`; A/B variantlar (`CampaignVariant`).

**Agent:** butun `speechEngine`, `voice` (`defaultVoice` dan tashqari), `ambience`,
`callBehaviour`, `limits`, `dataPolicy` guruhlari; `script.scenarioId`, `script.mode`,
`script.definition`; `persona`, `llmModel`, `fastLlmModel`, `temperature`,
`maxOutputTokens` (profil bilan almashadi), `dataNeeded`, `dataEvaluation`, webhook'lar,
`useRag`.

> Ro'yxat `CreateAiAgentRequest` / `CreateCampaignRequest` dan olingan; yangi maydon
> qo'shilganda bu yerga ham yoziladi.

Qo'ng'iroqqa ta'sir qilmaydigan oltita maydon API'dan olib tashlandi — ro'yxati:
[ai-agents.md](ai-agents.md#4-post-apiai-agents--agent-yaratish) ("O'chirilgan maydonlar").
