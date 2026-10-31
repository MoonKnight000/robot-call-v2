| `aiAgentId` | `number` | `AI` va `PREDICTIVE` da ✅ |`dialMode` `AI` yoki `PREDICTIVE` da berilmasa — `400 CAMPAIGN_AI_AGENT_REQUIRED` (`PREDICTIVE` da operator topilmagan mijozni shu agent qabul qiladi); `PREVIEW`/`PROGRESSIVE` da ixtiyoriy |﻿# Kampaniyalar API

`uz.murodjon.robotcallv2.campaign` · huquq: **CAMPAIGN_READ** / **CAMPAIGN_EDIT**

Avtomatlashtirilgan ommaviy qo'ng'iroq kampaniyalarini boshqarish, nishonlar yuklash,
ko'p tilli audio konfiguratsiyasi, SIP trunklarni taqsimlash, dialer tezligini sozlash va
A/B variantlarni sinash.

Umumiy javob shakli, xatolar va pagination konventsiyasi uchun
[README.md](README.md)ga qarang.

---

## 1. Kampaniyalar CRUD

### `POST /api/campaigns` — Yangi kampaniya yaratish

**Request Body** (`CreateCampaignRequest`):

```json
{
  "name": "Kechikkan to'lovlar - Mart 2026",
  "type": "DEBT_COLLECTION",
  "aiAgentId": 7,
  "priority": 3,
  "schedule": {
    "startAt": null,
    "endAt": null,
    "dialWindowStart": "09:00:00",
    "dialWindowEnd": "18:00:00",
    "dialDays": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"]
  },
  "retry": {
    "maxAttempts": 3,
    "intervalMinutes": 0,
    "policy": { "VOICEMAIL": { "maxAttempts": 1, "delayMinutes": 120 } }
  },
  "limits": {
    "maxConcurrentCalls": 20,
    "dailyCallCap": 0,
    "budgetUzs": null,
    "dailyBudgetUzs": null,
    "maxCps": null
  },
  "recurrence": {
    "type": "CRON",
    "dayOfMonth": null,
    "cronExpression": "0 0 9 ? * MON-FRI",
    "autoResetTargets": false
  },
  "dialing": {
    "mode": "AI",
    "callQueueId": null,
    "maxAiOverflowPercent": 20,
    "targetOrdering": "FIFO"
  },
  "numbers": { "callerIdPoolId": 4, "localPresence": true },
  "kind": "STANDARD",
  "sequence": null
}
```

> **Maydon guruhlari (2026-10-28 dan, breaking change).** Kampaniya sozlamalari domain'dagi
> guruhlar bo'yicha ichma-ich keladi: `schedule`, `retry`, `limits`, `recurrence`, `dialing`.
> Shakl `POST`, `PUT` va javob (`CampaignRow`) da bir xil. Guruhni butunlay tashlab ketish
> mumkin: uning maydonlari berilmagan maydonlar kabi o'qiladi (pastdagi standartlar). Eski →
> yangi: `dialWindowStart/End`, `dialDays`, `startAt`, `endAt` → `schedule.*`;
> `maxAttempts` → `retry.maxAttempts`, `retryIntervalMinutes` → `retry.intervalMinutes`,
> `retryPolicy` → `retry.policy`; `maxConcurrentCalls`, `dailyCallCap`, `budgetUzs`,
> `dailyBudgetUzs` → `limits.*`; `recurrenceType` → `recurrence.type`, `recurringDayOfMonth` →
> `recurrence.dayOfMonth`, `cronExpression`, `autoResetTargets` → `recurrence.*`; `dialMode` →
> `dialing.mode`, `callQueueId`, `maxAiOverflowPercent`, `targetOrdering` → `dialing.*`. Javobdagi
> `totalTargets`, `calledTargets`, `pendingTargets`, `completedTargets` → `stats.*`.
> `stats.successfulTargets` — kamida bitta qo'ng'irog'i maqsadga yetgan (`PROMISE_TO_PAY`, `COMPLETED`, `GOAL_REACHED`) nishonlar: ro'yxatdagi "Successful / Total" ning birinchi qismi.
> Yuqori darajada: `name`, `type`, `aiAgentId`, `priority` (`PUT` da yana `version`).

| Maydon | Turi | Majburiymi | Izoh |
|---|---|---|---|
| `name` | `string` | ✅ (`@NotBlank`) | Kampaniya nomi |
| `type` | `CampaignType` | ✅ (`@NotNull`) | `DEBT_COLLECTION` yoki `SURVEY`. **Majburiy** — berilmasa `400` (avval jimgina `DEBT_COLLECTION` ga tushardi) |
| `dialing.mode` | `DialMode` | ❌ | Mijoz bilan kim gaplashadi: `AI` (standart) — kampaniya agenti; `PREVIEW` — operator mijoz kartasini ko'rib o'zi teradi; `PROGRESSIVE` — har bo'sh operatorga bitta qo'ng'iroq; `PREDICTIVE` — bo'sh operatorlardan ko'proq qo'ng'iroq, operator yetmasa mijozni AI agent qabul qiladi. **Yaratilgandan keyin o'zgarmaydi.** `AI` kampaniyalarni dialer teradi, `PREVIEW` ni operatorlar ([preview-dialing.md](preview-dialing.md)), `PROGRESSIVE` ni dialer navbatning bo'sh operatorlari uchun ([pastda](#progressive-kampaniya-qanday-teriladi)); `PREDICTIVE` ni dialer javob ulushiga qarab oldindan ([pastda](#predictive-kampaniya-qanday-teriladi)) |
| `aiAgentId` | `number` | `AI` da ✅ | [`GET /api/ai-agents`](ai-agents.md) dagi agent. **Senariy, ovoz, persona, LLM model va SIP trunklar shu agentdan olinadi.** Keyin `PUT` bilan boshqa agentga o'tkazsa bo'ladi. **DRAFT'ni agentsiz saqlash mumkin** (Retell'dagidek "No agent selected"), lekin `start` da `dialMode` `AI`/`PREDICTIVE` bo'lsa agent shart — `400 CAMPAIGN_AI_AGENT_REQUIRED`; operator rejimlarida ixtiyoriy |
| `dialing.callQueueId` | `number` | `AI` dan boshqasida ✅ | Qo'ng'iroqlarni oladigan operatorlar navbati ([call-queues.md](call-queues.md)). `dialMode` `AI` bo'lmasa va berilmasa — `400 CAMPAIGN_CALL_QUEUE_REQUIRED`; boshqa kompaniyaning yoki mavjud bo'lmagan navbat — `404 CALL_QUEUE_NOT_FOUND`. Navbat o'chirilsa, maydon `null` bo'lib qoladi |
| `schedule.dialWindowStart` / `schedule.dialWindowEnd` | `string` (`HH:mm:ss`) | ❌ | Kunlik qo'ng'iroq oynasi. **Berilmasa — kompaniyaning ish soatlari**; bo'sh AI qoralamada (`sequence` va `aiAgentId` berilmagan) — Retell kabi **09:00–18:00**, kompaniya oynasi ichiga qisilgan (`company_config.dialWindowStart/End`, yaratish paytidagi qiymati nusxalanadi; kompaniyada sozlama bo'lmasa 09:00–20:00). `PUT` da berilmasa — kampaniyaning mavjud oynasi qoladi. UI bu maydonni "Kompaniya ish soatlari" (default) / "Boshqa vaqt" tanlovi sifatida ko'rsatishi mumkin. Kompaniyaning `company_config` oynasidan **tashqariga chiqa olmaydi** ([companies.md](companies.md)). Shuningdek, `DialerService` har bir qabul qiluvchining mahalliy vaqt zonasini (quiet hours / TCPA: 08:00–20:00) hisobga oladi — uning mahalliy vaqtida tun bo'lsa, nishon avtomatik +30 daqiqaga kechiktiriladi |
| `schedule.dialDays` | `array<DayOfWeek>` | ❌ | `["MONDAY", ...]`. Bo'sh/berilmasa — barcha kunlar |
| `retry.maxAttempts` | `number` | ❌ | Bitta nishonga urinishlar soni |
| `retry.intervalMinutes` | `number` | ❌ | Javob bo'lmagan nishonni qayta terishgacha daqiqa. `0` — disposition bo'yicha standartlar (avtootvetchikdan keyin uzoqroq, bandlikdan keyin qisqaroq) |
| `limits.maxConcurrentCalls` | `number` | ❌ | Parallel kanallar chegarasi |
| `limits.dailyCallCap` | `number` | ❌ | Kunlik qo'ng'iroq chegarasi; `0` — cheksiz. Sarf shifti: har bir qo'ng'iroq STT/LLM/TTS va trunk daqiqasini yeydi |
| `limits.maxCps` | `number` (1–100) | ❌ | Kampaniya bir soniyada boshlaydigan yangi qo'ng'iroqlar chegarasi (Retell "Calls per second"). Har trunkning o'z `maxCps` i ustiga qo'shimcha cheklov — dialer ikkalasiga ham bo'ysunadi; chegaradan oshgan qo'ng'iroq urinish sarflamasdan keyingi soniyani kutadi. `null` — trunklarning to'liq CPS i ([concurrency.md](concurrency.md#get-apiconcurrencycampaignscampaignid--kampaniya-dialing-capacity)). `PUT` da berilmasa — `null` bo'ladi |
| `recurrence.type` | `RecurrenceType` | ❌ | `ONCE` (standart), `DAILY`, `WEEKLY`, `MONTHLY`, `CRON` |
| `recurrence.dayOfMonth` | `number` (1–31) | ❌ | `MONTHLY` uchun oyning kuni |
| `recurrence.cronExpression` | `string` | ❌ | `CRON` uchun Spring/Quartz cron ifodasi |
| `recurrence.autoResetTargets` | `boolean` | ❌ | Takror ishga tushganda nishonlar `PENDING` ga qaytariladimi. Standart `false` |
| `priority` | `number` (1–5) | ❌ | Dialer har tickdagi batch'ni faol kampaniyalar orasida ustuvorlikka **proporsional** bo'ladi: 5 dagi kampaniya 1 dagiga nisbatan 5 barobar ko'p liniya oladi; har biri kamida 1 ta. Standart `3` |
| `retry.policy` | `object` | ❌ | Disposition bo'yicha retry qoidalari: `{"NO_ANSWER": {"maxAttempts": 3, "delayMinutes": 180}, "VOICEMAIL": {"maxAttempts": 1, "delayMinutes": 120}}`. `delayMinutes` — shu natijadan keyingi kutish (`retryIntervalMinutes` va standartlardan ustun); `maxAttempts` — oxirgi natija shu bo'lsa, shuncha urinishdan keyin nishon `EXHAUSTED` (kampaniya `maxAttempts`idan oshmaydi). Qoida bo'lmagan disposition uchun `retryIntervalMinutes`, u `0` bo'lsa standartlar (`NO_ANSWER` 180 min, `FAILED` 15 min, `VOICEMAIL` 120 min, `CALLBACK_REQUESTED` 60 min). **Ikkinchi kanal — SMS:** qoidaga `smsText` (va ixtiyoriy `smsOnAttempt`) qo'shilsa, qo'ng'iroq shu natija bilan tugaganda raqamga SMS ketadi: `{"NO_ANSWER": {"delayMinutes": 180, "smsText": "{{clientName | Hurmatli mijoz}}, sizga qo'ng'iroq qildik. Qulay vaqtda qayta qo'ng'iroq qilamiz.", "smsOnAttempt": 1}}` — "javob bermadi → SMS → 3 soatdan keyin qayta qo'ng'iroq". `smsText` da nishon faktlari (`{{fakt}}`, `{{fakt | zaxira}}`) va `{{phone}}`; ko'pi bilan 459 belgi. `smsOnAttempt` — faqat shu urinish shu natija bilan tugaganda yuboriladi (qayta-qayta yubormaslik uchun); `null` — har safar. Yakuniy natijada ham ishlaydi (`PROMISE_TO_PAY` → tasdiq SMS). `WRONG_NUMBER` va `DO_NOT_CALL` ga SMS qo'yib bo'lmaydi (`400 CAMPAIGN_FOLLOW_UP_SMS_NOT_ALLOWED`) — begona odam mijoz ma'lumotini o'qimasligi uchun; nishon `doNotCall` bo'lsa ham yuborilmaydi. SMS platformada yoqilgan bo'lishi kerak (`voice-agent.sms`, Eskiz); yiqilgan SMS nishonni qayta rejalashtirishga xalaqit bermaydi |
| `schedule.startAt` | `string` (ISO-8601, UTC) | ❌ | Rejalashtirilgan boshlanish. `start` shu vaqtdan **oldin** bosilsa kampaniya `SCHEDULED` bo'lib turadi va daqiqalik sweep vaqti kelganda `ACTIVE` qiladi (`startAt` o'tib ketgan yoki `null` bo'lsa — darhol `ACTIVE`). `start?immediate=true` rejani chetlab hozir boshlaydi |
| `schedule.endAt` | `string` (ISO-8601, UTC) | ❌ | Rejalashtirilgan tugash: `ACTIVE` kampaniya shu vaqtda nishonlar qolgan bo'lsa ham `COMPLETED` qilinadi (`CAMPAIGN_FINISHED` bildirishnomasi). `endAt` `startAt`dan keyin bo'lishi shart — `400 CAMPAIGN_SCHEDULE_INVALID`; o'tib ketgan `endAt` bilan `start` — `409 CAMPAIGN_END_PASSED` |
| `limits.budgetUzs` | `number` | ❌ | Butun kampaniya uchun xarajat tavani (so'm). Yakunlangan (`SETTLED`) qo'ng'iroqlar summasi shunga yetganda dialer kampaniyani `PAUSED` qiladi va `CAMPAIGN_PAUSED` bildirishnomasi (`BUDGET_CAP`) yuboradi; davom ettirish — `start`. Tekshiruv daqiqada bir marta, shuning uchun bitta tick'ning batch'i qadar oshib ketishi mumkin |
| `limits.dailyBudgetUzs` | `number` | ❌ | Xuddi shu, lekin kompaniya vaqt zonasidagi bir kun uchun (yarim tundan boshlab) |
| `dialing.maxAiOverflowPercent` | `number` (0–100) | ❌ | Faqat `PREDICTIVE`: so'nggi 30 daqiqada javob berilgan qo'ng'iroqlarning necha foizini AI agent olishi mumkin. Shunga yetganda dialer har bo'sh operatorga bittadan teradi (PROGRESSIVE tezligi); 75% iga yetganda oldindan terish yarimga tushadi. `0` — hech qachon oldindan termaydi. Standart `20`. `PUT` da berilmasa — avvalgi qiymat qoladi |
| `dialing.targetOrdering` | `string` | ❌ | Kutayotgan nishonlardan qaysilari birinchi teriladi: `FIFO` (standart — navbatga eng oldin tushgani) yoki `SCORE` (to'lash ehtimoli yuqorisi birinchi, raqam odatda gaplashadigan soatda bonus bilan) — [SCORE tartibi](#score-tartibi--nishonlar-qaysi-tartibda-teriladi). `AI` va `PREDICTIVE` kampaniyalarda ishlaydi. `PUT` da berilmasa — avvalgi qiymat qoladi |
| `numbers.callerIdPoolId` | `number` | ❌ | Qo'ng'iroqlar qaysi raqamdan chiqishi: [Caller ID pul](caller-id-pools.md) (`GET /api/caller-id-pools`). Kompaniyaning o'z puli bo'lishi shart (`404 CALLER_ID_POOL_NOT_FOUND`). `null` — trunkning o'z caller ID'si. `PUT` da `numbers` berilmasa — avvalgisi qoladi |
| `numbers.localPresence` | `boolean` | ❌ | Local presence dialing: puldan **chaqirilayotgan raqamga eng yaqin** raqam tanlanadi — [Raqamlar](#raqamlar--qaysi-raqamdan-qo'ng'iroq-qilinadi). `callerIdPoolId`siz `400 CAMPAIGN_LOCAL_PRESENCE_NEEDS_POOL` |
| `numbers.fromNumberId` | `number` | ❌ | Pul o'rniga **bitta qat'iy raqam** ([phone-numbers.md](phone-numbers.md), `POST /api/phone-numbers/list` dagi `id`). Kompaniyaniki bo'lishi shart (`404 PHONE_NUMBER_NOT_FOUND`), trunki tanlangan BYON raqam bo'lishi shart (`409 CAMPAIGN_FROM_NUMBER_UNUSABLE` — trunksiz yoki bozordan olingan raqam). `callerIdPoolId` bilan birga — `400 CAMPAIGN_FROM_NUMBER_WITH_POOL` |
| `kind` | `CampaignKind` | ❌ | Kontaktlar kampaniyaga qanday tushadi: `STANDARD` (standart — ro'yxat qo'lda: kontaktlar, CSV yoki target source) yoki `AUTO_ENROLL` (qoidaga mos kontaktlar o'zi qo'shiladi — [Auto-enroll](#auto-enroll--kontaktlar-qoida-bilan-qo'shiladi)). **Faqat yaratishda** beriladi; `PUT` da e'tiborga olinmaydi |
| `sequence` | `object` | ❌ | Kontakt kampaniyaga tushgach o'tadigan qadamlar — [Sequence](#sequence--kontakt-qaysi-qadamlardan-o'tadi). `null` — Sequence'dan oldin yaratilgan kampaniya (bitta Conversation qadami, `aiAgentId` agenti). `PUT` da berilmasa — avvalgisi qoladi. **`POST` da `sequence` ham, `aiAgentId` ham berilmasa** (AI rejim, Retell "Untitled campaign" qoralamasi) — default sequence yoziladi: `{"startStepId":"step1","steps":[{"id":"step1","type":"CONVERSATION","name":"Suhbat 1","conversation":{"aiAgentId":null,"attempts":[{"delay":0,"unit":"MINUTES"},{"delay":3,"unit":"DAYS"},{"delay":7,"unit":"DAYS"},{"delay":10,"unit":"DAYS"}]},"outcomes":{"outcomes":[],"other":{"exit":"END"},"noResponse":{"exit":"END"}}}],"globalOutcomes":[…]}` (`globalOutcomes` — Do Not Call, Success, Not Interested; pastda Sequence bo'limi); agent ishga tushirishdan oldin qadamga tanlanadi (`CAMPAIGN_SEQUENCE_INVALID`). `aiAgentId` berilib `sequence` berilmasa — avvalgidek `null` |
| `autoRetries` | `object` | ❌ | Retell "Automatic retries": `{ "doubleDial": true, "autoScheduleCallbacks": true }` — faqat `sequence` bor kampaniyada ishlaydi ([Sequence](#sequence--kontakt-qaysi-qadamlardan-o'tadi), 2–3-band). `PUT` da berilmasa — avvalgisi qoladi. `POST` da berilmasa: bo'sh AI qoralama (yuqoridagi default sequence holati) — ikkalasi `true`, qolgan hammasi — `false`. V40 dan oldingi kampaniyalarda `doubleDial: false`; `sequence` bori `autoScheduleCallbacks: true` (avvalgi xatti-harakat saqlanadi). Javobda (`CampaignRow`) doim bor |
| `autoEnroll` | `object` | ❌ | Kontaktlar qaysi shart bilan olinadi: `{ "matchAll": true, "rules": [{ "field": "TAG", "operator": "EQUALS", "value": "qarzdor" }] }`. `AUTO_ENROLL` da — doimiy trigger ([Auto-enroll](#auto-enroll--kontaktlar-qoida-bilan-qo'shiladi)); `STANDARD` da — "Filter contacts", ishga tushirishda bir marta olinadi ([pastda](#standard--filter-contacts)), qo'shimcha `excludedContactIds` (ro'yxatda belgisi olingan kontaktlar; `AUTO_ENROLL` da bo'sh bo'lishi shart — `400 CAMPAIGN_AUTO_ENROLL_INVALID`). `PUT` da berilmasa — avvalgisi qoladi |

> ⚠️ **Muhim o'zgarish.** Kampaniya endi faqat *kimga va qachon* qo'ng'iroq qilishni
> ushlaydi. `scenarioId`, `defaultLanguage`, `ttsVoice`, `languageVoices`, `agentPersona`,
> `ambientSound`, `emotionAdaptiveVoice`, `dtmfInputEnabled`, `voicemailAction`,
> `voicemailMessage`, `midCallSmsEnabled`, `midCallSmsTemplate` maydonlari
> kampaniyadan olib tashlandi va [AI agent](ai-agents.md)ga ko'chdi (`sipTrunkIds` ham ko'chgan edi,
> 2026-10-31 da agentdan ham olib tashlandi — trunk endi [raqamdan](#raqamlar--qaysi-raqamdan-qongiroq-qilinadi)). Ular hozir
> `POST /api/ai-agents` tanasida. Mavjud kampaniyalar migratsiya paytida avtomatik
> ravishda o'z sozlamalari bilan bitta agentga bog'landi.
>
> `dailyStartTime` / `dailyEndTime` degan maydonlar hech qachon bo'lmagan — ular
> `dialWindowStart` / `dialWindowEnd`.

**Response** (`CreateCampaignResponse`):

```json
{
  "accept": true,
  "data": { "id": 12, "status": "DRAFT" },
  "message": null,
  "messageCode": null,
  "errors": null
}
```

---

### `PUT /api/campaigns/{id}` — Kampaniyani tahrirlash

Body (`UpdateCampaignRequest`) — `CreateCampaignRequest` bilan bir xil guruhlar, faqat **`type`
yo'q**, `dialing.mode` esa **e'tiborsiz qoldiriladi** (ikkalasi yaratilgandan keyin o'zgarmaydi).
`aiAgentId` va `dialing.callQueueId` esa o'zgartirilishi mumkin: kampaniyani boshqa ovoz yoki
boshqa skript bilan yuritish uchun uni klonlash shart emas. Ular kampaniyaning `dialing.mode`iga
qarab yaratishdagidek tekshiriladi. `PUT` — to'liq tahrir: tashlab ketilgan guruh maydonlari ham
bo'sh (`0` / `null`) yoziladi; istisno — `schedule.dialWindowStart/End`,
`dialing.maxAiOverflowPercent` va `dialing.targetOrdering`, ular berilmasa avvalgi qiymat qoladi.

Javob — yangilangan `CampaignRow`.

**Optimistik qulf (`version`).** Body'da ixtiyoriy `version` (`long`) maydoni bor: frontend
formani ochganda `CampaignRow.version` dan o'qigan qiymatni shu yerda qaytarib yuboradi.
Kampaniya shu orada boshqa foydalanuvchi tomonidan saqlangan bo'lsa (versiya mos kelmasa) —
`409 ENTITY_VERSION_CONFLICT`, hech narsa yozilmaydi: kampaniyani qayta o'qib, o'zgarishni
yana kiritish kerak. `version` yuborilmasa — amalda avvalgidek (eski klientlar buzilmaydi).
Versiyani faqat tahrir oshiradi: dialer yozadigan `status` / `lastRunAt` va
`start`/`pause`/`archive` uni o'zgartirmaydi, shuning uchun ishlayotgan kampaniyani tahrirlash
dialer tufayli `409` olmaydi (tahrir bu ustunlarni yozmaydi ham).

---

### `GET /api/campaigns/{id}` — Kampaniya tafsilotlari

**Response** (`CampaignRow`):

```json
{
  "accept": true,
  "data": {
    "id": 12,
    "name": "Kechikkan to'lovlar - Mart 2026",
    "type": "DEBT_COLLECTION",
    "status": "ACTIVE",
    "aiAgentId": 7,
    "aiAgentName": "Qarz undirish — o'zbekcha",
    "companyId": 1,
    "createdBy": 3,
    "createdByName": "Aziz Bekmurodov",
    "priority": 3,
    "schedule": {
      "startAt": "2026-09-15T04:00:00Z",
      "endAt": null,
      "dialWindowStart": "09:00:00",
      "dialWindowEnd": "18:00:00",
      "dialDays": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"]
    },
    "retry": {
      "maxAttempts": 3,
      "intervalMinutes": 0,
      "policy": { "VOICEMAIL": { "maxAttempts": 1, "delayMinutes": 120 } }
    },
    "limits": {
      "maxConcurrentCalls": 20,
      "dailyCallCap": 0,
      "budgetUzs": 5000000,
      "dailyBudgetUzs": null,
      "maxCps": null
    },
    "recurrence": {
      "type": "CRON",
      "dayOfMonth": null,
      "cronExpression": "0 0 9 ? * MON-FRI",
      "autoResetTargets": false
    },
    "dialing": {
      "mode": "AI",
      "callQueueId": null,
      "maxAiOverflowPercent": 20,
      "targetOrdering": "FIFO"
    },
    "numbers": { "callerIdPoolId": 4, "localPresence": true },
    "kind": "STANDARD",
    "sequence": null,
    "stats": {
      "totalTargets": 1200,
      "calledTargets": 750,
      "pendingTargets": 450,
      "completedTargets": 700,
      "successfulTargets": 310
    },
    "lastRunAt": "2026-03-05T09:00:00Z",
    "consentConfirmedAt": "2026-03-01T08:30:00Z",
    "version": 4
  },
  "message": null,
  "messageCode": null,
  "errors": null
}
```

`version` — optimistik qulf hisoblagichi: `PUT /api/campaigns/{id}` da shu qiymat qaytariladi.
Operator kampaniyasida `aiAgentId` va `aiAgentName` `null` bo'lishi mumkin.

Kampaniya holati (`CampaignStatus`): `DRAFT`, `SCHEDULED` (`startAt` kutilmoqda), `ACTIVE`,
`PAUSED`, `COMPLETED` (barcha nishonlar yakunlandi yoki `endAt` keldi), `ARCHIVED`.

Holat mashinasi (`CampaignStateMachine`) — faqat shu o'tishlar mumkin:
`DRAFT → SCHEDULED | ACTIVE | ARCHIVED`, `SCHEDULED → ACTIVE | ARCHIVED`,
`ACTIVE → PAUSED | COMPLETED | ARCHIVED`,
`PAUSED → ACTIVE | ARCHIVED`, `COMPLETED → ACTIVE | ARCHIVED`; `ARCHIVED`dan chiqish yo'q.
Boshqa o'tish — `409 CAMPAIGN_INVALID_TRANSITION`; joriy holatga qayta o'tish (masalan
ishlayotgan kampaniyaga yana `start`) — xatosiz no-op. `start` nishoni ham, target
source'i ham bo'lmagan kampaniyada — `409 CAMPAIGN_HAS_NO_TARGETS`; agenti o'chirilgan kampaniyada — `409 AI_AGENT_DISABLED`.

Dialer o'zi kampaniyani `PAUSED` qilishi mumkin (yaroqli SIP trunk yo'q) — bunda
`CAMPAIGN_PAUSED` bildirishnomasi sabab bilan keladi. Balans yetmasa kampaniya to'xtamaydi:
nishonlar 15 daqiqaga kutishga qo'yiladi (urinish sarflanmaydi) va soatiga bir marta
`BALANCE_LOW` bildirishnomasi keladi.

---

### `POST /api/campaigns/list` va `POST /api/campaigns/filter` — Kampaniyalar ro'yxati

Ikkala path bir xil metodga boradi. Body — `CampaignFilter`:

```json
{
  "page": 0,
  "size": 20,
  "orders": { "CREATED_AT": "DESC" },
  "search": "Mart",
  "status": "ACTIVE",
  "type": "DEBT_COLLECTION",
  "aiAgentId": 7,
  "createdBy": 3,
  "recurrenceType": "CRON",
  "dateFrom": "2026-03-01T00:00:00Z",
  "dateTo": "2026-03-31T23:59:59Z"
}
```

Saralanadigan ustunlar: `ID`, `NAME`, `TYPE`, `STATUS`, `CREATED_AT`, `LAST_RUN_AT`,
`AI_AGENT_ID`. Standart: `CREATED_AT DESC, ID DESC`. Qaysi saralash berilsa ham
`ID DESC` oxiriga qo'shiladi — sahifalar orasida tartib barqaror bo'lishi uchun.

Javob — `PageableData<CampaignRow>`; har bir elementda nishonlar statistikasi ham bor.

---

### `DELETE /api/campaigns/{id}` — Arxivlash

Qatorni o'chirmaydi — kampaniyani `ARCHIVED` holatiga o'tkazadi.

**Response** (`CampaignStatusResponse`):

```json
{ "accept": true, "data": { "campaignId": 12, "status": "ARCHIVED" }, "errors": null }
```

---

### `POST /api/campaigns/{id}/clone` — Nusxa olish

Barcha sozlamalar (agent, oyna, urinishlar, recurrence) bilan yangi kampaniya
yaratadi. Javob — yangi `CampaignRow`.

---

### `POST /api/campaigns/batch-call` — Bitta so'rovda: agent + CSV + boshlash

"Agentni tanlang → CSV tashlang → Boshlash" oqimi uchun. Ichida aynan uchta mavjud qadam
ketma-ket bajariladi: `POST /api/campaigns` → `POST /api/campaigns/{id}/targets/csv` →
`POST /api/campaigns/{id}/start`. Ruxsat — `CAMPAIGN_EDIT`.

**Request** (`BatchCallRequest`, `application/json`):

```json
{
  "name": "Oktabr qarzdorlari",
  "type": "DEBT_COLLECTION",
  "aiAgentId": 3,
  "csv": "clientId,phone,clientName,debtAmount\n1001,+998901234567,Aziz Karimov,1500000\n",
  "schedule": { "dialWindowStart": "09:00", "dialWindowEnd": "18:00", "dialDays": ["MONDAY", "TUESDAY"] },
  "immediate": false
}
```

| Maydon | Majburiy | Izoh |
|---|---|---|
| `name` | ha | Kampaniya nomi |
| `type` | ha | `DEBT_COLLECTION` \| `SURVEY` |
| `aiAgentId` | ha | Qo'ng'iroq qiladigan agent (`dialing.mode` doim `AI`) |
| `csv` | ha | CSV fayl matni — `targets/csv` bilan bir xil format, JSON satr sifatida (`\n` bilan); ko'pi bilan 5 000 000 belgi |
| `schedule` | yo'q | `POST /api/campaigns` dagi `schedule` guruhi; berilmasa kompaniya oynasi, har kuni |
| `numbers` | yo'q | `POST /api/campaigns` dagi `numbers` guruhi — `fromNumberId` yoki `callerIdPoolId`(+`localPresence`), xatolari ham o'sha. Berilmasa qo'ng'iroqlar agent trunklaridan, trunkning caller ID'si bilan ketadi |
| `immediate` | yo'q | `start?immediate=` bilan bir xil; standart `false` |
| `consentConfirmed` | ha, `true` | `POST /campaigns/{id}/consent` bilan bir xil tasdiq; `false` bo'lsa `400 CAMPAIGN_CONSENT_REQUIRED` |

Qolgan hamma sozlama `POST /api/campaigns` standartida: 3 urinish, 20 parallel qo'ng'iroq,
`priority` 3, `recurrence` `ONCE`. Ularni keyin `PUT /api/campaigns/{id}` bilan o'zgartirish mumkin.

**Response** (`BatchCallResponse`):

```json
{
  "accept": true,
  "data": {
    "campaignId": 12,
    "imported": {
      "campaignId": 12, "added": 45, "targetIds": [501, 502], "skippedDuplicates": 3,
      "skippedDoNotCall": 1, "errors": [ { "line": 8, "message": "phone is empty" } ],
      "unknownColumns": []
    },
    "status": "ACTIVE"
  },
  "errors": null
}
```

`status` — `ACTIVE`, yoki `schedule.startAt` kelajakda bo'lsa (`immediate=false`) `SCHEDULED`.

**Xatolar — hammasi yoki hech narsa:** biror qadam rad etilsa, hech narsa saqlanmaydi (kampaniya
ham, nishonlar ham). Kodlar uchala endpoint'nikidek: agent topilmasa `404 AI_AGENT_NOT_FOUND`,
CSV'da `phone` ustuni yo'q — `400 CSV_PHONE_COLUMN_MISSING`, bo'sh — `400 CSV_EMPTY`, birorta
ham yaroqli qator bo'lmasa — `409 CAMPAIGN_HAS_NO_TARGETS`, agent o'chirilgan — `409 AI_AGENT_DISABLED`.
Ayrim qatorlar xato bo'lsa, qolganlari bilan kampaniya boshlanadi va xato qatorlar `imported.errors` da qaytadi.

---

### PROGRESSIVE kampaniya qanday teriladi

Alohida endpoint yo'q — kampaniya `ACTIVE` bo'lsa, dialer har tick'da (`voice-agent.dialer.tick-seconds`)
navbatning ([call-queues.md](call-queues.md)) har bir **bo'sh** operatori uchun bitta mijozni teradi.
Bo'sh operator — holati `AVAILABLE` va softfoni ulangan bo'lishi kerak. Bir vaqtda terilayotgan
qo'ng'iroqlar soni bo'sh operatorlar sonidan oshmaydi.

```
operator band qilinadi (RINGING) ──► mijoz teriladi
  ├─ ko'tarmadi ──► operator darhol AVAILABLE, target NO_ANSWER/… (retry qoidasi)
  └─ ko'tardi ──┬─► operator softfoni jiringlaydi ──► ko'tardi ──► yozib olinadigan suhbat ──► wrap-up
                └─► shu payt mijoz tomonida AMD (≤5 s)
                      └─ avtojavobgich ──► qo'ng'iroq uziladi, target VOICEMAIL, wrap-up kerak emas
```

- Operator ko'targuncha mijoz **jimlik** eshitadi. Softfonda auto-answer
  (`Call-Info: answer-after=0`) yoqilsa, bu vaqt deyarli nolga tushadi.
- AMD mijoz tomonidagi uzluksiz nutq bo'yicha ishlaydi (`VAD_AMD_*` sozlamalari, AI qo'ng'iroqlar
  bilan bir xil). Operator avtojavobgich salomlashuvining 1–3 soniyasini eshitib qolishi mumkin.
- Operator 15 s ichida ko'tarmasa, u o'tkazib yuborgan hisoblanadi (ketma-ket ikki marta bo'lsa
  `BREAK`). Mijozning qo'ng'irog'i shu bilan tugaydi: `abandoned`, target `FAILED` bilan qayta
  navbatga qo'yiladi.
- Operator ulanmasdan mijoz go'shakni qo'ysa — `abandoned`, `HUNG_UP`.
- Operator ko'targan suhbatlar preview'dagidek [wrap-up](wrap-up-codes.md) bilan yopiladi. Operator
  kutish vaqti `call_attempt` da saqlanadi (mijoz ko'targandan operator ko'targunicha).
- DNC, kontakt chastotasi siyosati, dial oynasi va balans — preview bilan bir xil;
  `maxConcurrentCalls`, `dailyCallCap` va budjet **qo'llanmaydi**.

### PREDICTIVE kampaniya qanday teriladi

Alohida endpoint yo'q — kampaniya `ACTIVE` bo'lsa, dialer har tick'da navbatning bo'sh operatorlari
(`AVAILABLE` + softfon ulangan) sonidan **ko'proq** mijozni teradi. Operator mijoz ko'targandan
keyingina tanlanadi; operator topilmasa mijozga kampaniyaning AI agenti (`aiAgentId`, majburiy) javob beradi.

**Nechta teriladi:** `bo'sh operatorlar × nisbat − hali hal bo'lmagan (jiringlayotgan yoki operator
kutayotgan) qo'ng'iroqlar`. Nisbat = `terilgan ÷ javob berilgan` so'nggi 30 daqiqa bo'yicha, eng ko'pi 3.
20 tadan kam qo'ng'iroq bo'lsa yoki AI olgan ulush `maxAiOverflowPercent` ga yetsa — nisbat 1
(PROGRESSIVE tezligi); ulush chegaraning 75% iga yetganda ortiqcha qism yarimga tushadi. Statistika
xotirada: server qayta ishga tushsa nisbat yana 1 dan boshlanadi.

```
mijoz teriladi (30 s)
  ├─ ko'tarmadi ──► target NO_ANSWER/… (retry qoidasi)
  └─ ko'tardi ──┬─► AMD (≤5 s) ──► avtojavobgich ──► uziladi, target VOICEMAIL
                └─► bo'sh operator bormi?
                      ├─ ha ──► operator 15 s jiringlaydi
                      │          ├─ ko'tardi ──► yozib olinadigan suhbat ──► wrap-up
                      │          └─ ko'tarmadi (RONA) ──► AI agent oladi
                      └─ yo'q ──► AI agent oladi (call_attempt.ai_overflow = true)
```

- AI olgan qo'ng'iroq oddiy AI qo'ng'iroqdek davom etadi: senariy, natija (disposition), xulosa,
  billing va target natijasi — `AI` kampaniyadagidek. Mijoz uchun go'shak qo'yilmaydi.
- Operator 15 s ichida ko'tarmasa — o'tkazib yuborgan hisoblanadi (ketma-ket ikki marta — `BREAK`).
- Operator topilguncha mijoz go'shakni qo'ysa — `abandoned`, `HUNG_UP`.
- Har terilgan raqam dialer'ning parallel kanal chegaralarini (platforma, kompaniya,
  `maxConcurrentCalls`) egallaydi. DNC, kontakt chastotasi siyosati, dial oynasi, balans va agent
  `enabled`/nashr qilinganligi tekshiriladi; `dailyCallCap` va budjet **qo'llanmaydi**.
- Qo'ng'iroqlar AI agentning SIP trunklari orqali emas, kompaniyaning standart trunki orqali ketadi.
- Natijalar: [`GET /api/reports/campaigns/{id}/predictive`](reports.md#get-apireportscampaignscampaignidpredictive--predictive-kampaniya-terish-natijalari).

### Sequence — kontakt qaysi qadamlardan o'tadi

`sequence` — kontakt kampaniyaga tushgach o'tadigan qadamlar grafi (Retell "Sequence" kabi).
`POST`/`PUT /api/campaigns` qabul qiladi, `GET`/ro'yxat javobida qaytaradi.

> **`sequence` bo'lgan kampaniyada dialer shu bo'yicha yuradi** (`sequence: null` — eskicha:
> `aiAgentId` va `retry`). `aiAgentId` berilmasa, AI kampaniyada u birinchi `CONVERSATION`
> qadamining agentidan olinadi; trunklar, TTS isitish va "agent yoqilganmi" tekshiruvi shu
> kampaniya agentiniki bo'lib qoladi.
>
> **Qanday yuriladi.** Nishon sequence'ning `startStepId` qadamida boshlanadi (start qadam
> doim `CONVERSATION`). Har qo'ng'iroq shu qadamning `conversation.aiAgentId` agenti bilan
> teriladi (A/B variant agenti yoki ovozi bo'lsa — u ustun). Qo'ng'iroq tugagach:
>
> 1. **Raqam yaroqsiz yoki DNC** (`DO_NOT_CALL`, `WRONG_NUMBER`, `CARRIER_REJECTED`) — nishon darhol
>    tugaydi, outcome'lar baholanmaydi (`DO_NOT_CALL` raqami go'shak qo'yilganda DNC'ga yozilgan).
> 2. **Double dial** (`autoRetries.doubleDial`, faqat kampaniya `ACTIVE` bo'lsa): `NO_ANSWER` bo'lgan
>    qo'ng'iroq **darhol yana bir marta** teriladi — shu urinishning bir qismi sifatida (urinish sanalmaydi,
>    follow-up SMS yuborilmaydi). Ikkinchi teruv ham javobsiz qolsa — oddiy yo'l (3-band). Avtojavobchik
>    (`VOICEMAIL`), rad etilgan raqam va to'xtatilgan kampaniyada qilinmaydi; bitta urinish bir martadan
>    ortiq ikkilanmaydi.
> 3. **Qayta qo'ng'iroq so'raldi** (`CALLBACK_REQUESTED`): `autoRetries.autoScheduleCallbacks` yoqilgan
>    bo'lsa — agent callback vositasi bilan yozgan vaqtda, vaqt aytilmagan bo'lsa 24 soatdan keyingi
>    birinchi oyna vaqtida qayta teriladi; qadamning urinishlari **1-urinishdan qayta boshlanadi**.
>    Vaqt doim qo'ng'iroq oynasi ichida bo'ladi (Retell'dan farqi: dialer oynadan tashqarida termaydi).
>    O'chiq bo'lsa — oddiy javob berilgan qo'ng'iroq kabi outcome'lar baholanadi (masalan "Callback"
>    outcome'i yo'naltiradi).
> 4. **Javob berilmagan** (`NO_ANSWER`, `VOICEMAIL`, `HUNG_UP`, `FAILED`) —
>    qadamning keyingi urinishi teriladi — `conversation.attempts[N].delay` dan keyin, **oldingi
>    urinishdan hisoblab** (qo'ng'iroq oynasidan tashqariga tushsa — keyingi ochilishda; bayramlar
>    o'tkazib yuboriladi). Kechikishi `0` bo'lgan keyingi urinish `retry.intervalMinutes` va
>    disposition standartlari bilan kutadi. Urinishlar tugagach —
>    qadamning **No Response** yo'nalishi (`outcomes.noResponse`).
> 5. **Javob berilgan** (qolgan hammasi) — **outcome'lar baholanadi** (Retell kabi): avval
>    qadamning o'z `outcomes.outcomes`i, keyin `globalOutcomes`. Ro'yxat tartibida birinchi mos
>    `METRICS` outcome yutadi; hech biri mos kelmasa, barcha `PROMPT` outcome'lar bitta so'rovda
>    LLM'ga (summary modeli; kompaniyaning o'z LLM'i bo'lsa — o'sha) transkript bilan beriladi va u
>    bittasini tanlaydi. Hech biri mos kelmasa — qadamning **Other** yo'nalishi (`outcomes.other`).
>    Transkript faqat AI agent gapirgan qo'ng'iroqda bor; operator qo'ng'iroqlarida faqat `METRICS`
>    ishlaydi.
> 6. **Mos kelgan outcome:** `saveToContact.doNotCall` — raqam DNC ro'yxatiga, `saveToContact.tags` —
>    shu raqamli kontakt(lar)ga teg qo'shiladi (auto-enroll / filtr uchun). Keyin `next`:
>    `STEP` — boshqa qadamga (`delayMinutes` dan keyin), `END` — tugaydi, `END_SUCCESSFUL` — tugaydi
>    va **konversiya** sanaladi. Global outcome nishon turgan qadamning o'ziga olib borsa — tugaydi.
>    Nishon tugaganda holati `DONE` (javob berilgan) yoki `EXHAUSTED` (No Response).
> 7. **SMS qadami** yo'lda yuboriladi (raqam `doNotCall` bo'lsa yuborilmaydi), **WAIT qadami**
>    keyingi qadamning birinchi qo'ng'irog'ini kechiktiradi (dial oynasi va bayramlar hisobga
>    olinadi); nishon doim keyingi `CONVERSATION` qadamiga tushadi. Zanjir `CONVERSATION` ga
>    yetmasa, nishon `DONE` bo'ladi.
>
> Nishon yetgan outcome nomi (`Other`, `No Response` yoki outcome `name`i) va konversiya belgisi
> `campaign_target.outcome` / `converted` ga yoziladi. `stats.successfulTargets` = `converted`
> nishonlar + konversiya disposition'li qo'ng'irog'i bor nishonlar.
>
> `retry.policy` qoidalari (jumladan `smsText`) sequence kampaniyasida ham ishlaydi, `maxAttempts`i
> qadamning urinishlarini kamaytirishi mumkin, oshirolmaydi. Agent qadamda o'chirilgan yoki
> e'lon qilinmagan bo'lsa, nishon 30 daqiqaga ushlab turiladi. Takroriy (recurrence) ishga
> tushishda nishonlar start qadamga qaytadi.

```json
{
  "startStepId": "s1",
  "steps": [
    {
      "id": "s1", "type": "CONVERSATION", "name": "Suhbat 1",
      "conversation": {
        "aiAgentId": 12,
        "attempts": [
          { "delay": 0, "unit": "MINUTES" },
          { "delay": 3, "unit": "DAYS" },
          { "delay": 7, "unit": "DAYS" },
          { "delay": 10, "unit": "DAYS" }
        ]
      },
      "outcomes": {
        "outcomes": [
          {
            "id": "callback", "name": "Qayta qo'ng'iroq", "evaluation": "PROMPT",
            "prompt": "Mijoz keyinroq qo'ng'iroq qilishni so'radi.",
            "saveToContact": { "doNotCall": false, "tags": ["callback"] },
            "next": { "exit": "STEP", "stepId": "s3", "delayMinutes": 1440 }
          }
        ],
        "other": { "exit": "END" },
        "noResponse": { "exit": "STEP", "stepId": "s2" }
      },
      "position": { "x": 0, "y": 120 }
    },
    { "id": "s2", "type": "SMS", "name": "SMS", "smsText": "Salom! Sizga qo'ng'iroq qildik.", "next": "s3" },
    { "id": "s3", "type": "CONVERSATION", "name": "Suhbat 2", "conversation": { "aiAgentId": 12, "attempts": [ { "delay": 0, "unit": "MINUTES" }, { "delay": 2, "unit": "HOURS" } ] } }
  ],
  "globalOutcomes": [
    {
      "id": "do_not_call", "name": "Do Not Call", "evaluation": "PROMPT",
      "prompt": "The lead asked not to be called or contacted again.",
      "saveToContact": { "doNotCall": true, "tags": [] }, "next": { "exit": "END" }
    },
    {
      "id": "big_debt", "name": "Katta qarz", "evaluation": "METRICS", "match": "ALL",
      "conditions": [ { "variable": "debt", "operator": "GREATER_THAN", "value": "10000000" } ],
      "next": { "exit": "END_SUCCESSFUL" }
    }
  ]
}
```

| Maydon | Tur | Izoh |
|---|---|---|
| `startStepId` | `string` | Kontakt boshlaydigan qadam; `steps` ichidagi `id` bo'lishi shart |
| `steps[].id` | `string` | Sequence ichida noyob; yo'nalishlar shunga ishora qiladi |
| `steps[].type` | `SequenceStepType` | `CONVERSATION` (agent qo'ng'iroq qiladi), `SMS`, `WAIT` |
| `steps[].conversation.aiAgentId` | `number` | Faqat `CONVERSATION`: gapiradigan agent. Draft'da bo'sh bo'lishi mumkin, ishga tushirishda majburiy |
| `steps[].conversation.attempts[]` | `{delay, unit}` | Qadamning qo'ng'iroqlari tartib bo'yicha (Retell "Attempt N"): kamida 1, ko'pi bilan 50. `unit`: `MINUTES` \| `HOURS` \| `DAYS` (berilmasa `MINUTES`); `delay` ≥ 0, ko'pi bilan 365 kun — **oldingi urinishdan** keyin. Birinchi urinish kontakt qadamga yetishi bilan teriladi — uning `delay`i doim `0` ga keltiriladi (qadamga kirishdan oldingi kutish — outcome'ning `delayMinutes`i). Keyingi urinishda `0` — kampaniyaning standart retry kechikishi. Default: 0, +3, +7, +10 kun (0/3/10/20-kunlar). `retry.policy[...].maxAttempts` urinishlar sonini kamaytirishi mumkin |
| `steps[].smsText` | `string` | Faqat `SMS`, ko'pi bilan 459 belgi; `{{fakt}}` / `{{fakt \| zaxira}}` shablonlari |
| `steps[].waitMinutes` | `number` | Faqat `WAIT`: 1 … 43200 (30 kun) |
| `steps[].outcomes.outcomes` | `SequenceOutcome[]` | Faqat `CONVERSATION`: qadamning o'z outcome'lari (Retell "node outcomes"), global'lardan oldin baholanadi |
| `steps[].outcomes.other` | `OutcomeRoute` | Javob berilib, hech bir outcome mos kelmaganda. Berilmasa — `END` |
| `steps[].outcomes.noResponse` | `OutcomeRoute` | Oxirgi urinishga ham javob berilmaganda. Berilmasa — `END` (konversiya emas) |
| `steps[].next` | `string` | Faqat `SMS` va `WAIT`: keyingi qadam; `null` — tugaydi |
| `steps[].position` | `{x, y}` | Canvas'dagi joyi; dialer o'qimaydi |
| `globalOutcomes` | `SequenceOutcome[]` | Har bir `CONVERSATION` qadamida baholanadi (Retell "Global outcomes"). Yangi qoralamada: Do Not Call, Success (`END_SUCCESSFUL`), Not Interested |

**`SequenceOutcome`:**

| Maydon | Tur | Izoh |
|---|---|---|
| `id` | `string` | Sequence ichida noyob (qadam outcome'i global bilan bir xil `id` olmaydi) |
| `name` | `string` | Majburiy; nishonga yoziladigan nom |
| `evaluation` | `PROMPT` \| `METRICS` | Berilmasa `PROMPT` |
| `prompt` | `string` | `PROMPT` da majburiy, ko'pi bilan 2000 belgi — qo'ng'iroq qachon shu outcome'ga tushadi |
| `match` | `ALL` \| `ANY` | `METRICS`: shartlarning hammasi yoki bittasi. Berilmasa `ALL` |
| `conditions[]` | `{variable, operator, value}` | `METRICS` da kamida bitta. `variable` — nishon faktlari (`contextData`) nomi, `phone` yoki `disposition`. `operator`: `EQUALS`, `NOT_EQUALS`, `CONTAINS`, `NOT_CONTAINS` (katta-kichik harf farqsiz), `GREATER_THAN`, `GREATER_OR_EQUAL`, `LESS_THAN`, `LESS_OR_EQUAL` (ikkala tomon son bo'lishi shart), `IS_SET`, `IS_NOT_SET` |
| `saveToContact` | `{doNotCall, tags[]}` | Kontaktga yoziladigan qat'iy qiymatlar (qo'ng'iroqdan olingan ma'lumot emas). Teg: 1–50 belgi, vergulsiz |
| `next` | `OutcomeRoute` | Berilmasa `END` |

**`OutcomeRoute`:** `{ "exit": "STEP" | "END" | "END_SUCCESSFUL", "stepId": "...", "delayMinutes": 0 }` —
`stepId` faqat `STEP` da; `delayMinutes` (0 … 43200) faqat qadamning o'z outcome'i `STEP` ga olib borganda.

**Qoidalar** (`400 CAMPAIGN_SEQUENCE_INVALID`, har bir muammo `errors` ro'yxatida):
`steps` bo'sh emas va ko'pi bilan 20 ta; `id` lar noyob; `startStepId` mavjud **`CONVERSATION`** qadamga ishora qiladi;
barcha yo'nalishlar mavjud qadamga olib boradi; `outcomes` faqat `CONVERSATION` da, `next` faqat
`SMS`/`WAIT` da; outcome'lar ro'yxati ko'pi bilan 20 ta, `id`/`name` majburiy; **`saveToContact.doNotCall`
bo'lgan outcome `STEP` ga olib bormaydi**; global outcome `delayMinutes` olmaydi; qadamlarning o'z
yo'nalishlari qaytib kelmaydi (sikl yo'q). Hech narsa olib kelmaydigan qadam xato emas (draft tugun).
`conversation.aiAgentId` kompaniyaning o'z agenti bo'lishi kerak (boshqasiniki — `404`).

> **Migratsiya (V38):** eski `steps[].routes` xaritasi olib tashlandi; mavjud sequence'larga
> default `globalOutcomes` qo'shildi. `routes` bilan yo'naltirilgan qadamlar endi hamma natijada
> tugaydi — qayta sozlash kerak.
>
> **Migratsiya (V39):** `conversation.maxAttempts` + `retryDelayMinutes` → `conversation.attempts`
> (shuncha urinish, birinchisi darhol, qolganlari `retryDelayMinutes` daqiqa oralig'ida). Eski
> maydonlar endi o'qilmaydi.

### Auto-enroll — kontaktlar qoida bilan qo'shiladi

`kind: "AUTO_ENROLL"` kampaniya qo'lda ro'yxat yig'maydi: `ACTIVE` bo'lganda har daqiqada
(`voice-agent.campaign.auto-enroll-check-ms`, o'chirish: `auto-enroll-enabled=false`) kompaniyaning
[kontaktlari](contacts.md) `autoEnroll` qoidalari bo'yicha tekshiriladi va mos kelganlari
nishon bo'lib qo'shiladi. Yangi yaratilgan yoki tahrirlanib mos kelib qolgan kontakt ham,
kampaniya ishga tushirilganda allaqachon bor kontaktlar ham shu tarzda olinadi. Bir sweep'da
kampaniyaga ko'pi bilan 500 kontakt tushadi, qolgani keyingi sweep'larda.

```json
{
  "matchAll": true,
  "rules": [
    { "field": "TAG", "operator": "EQUALS", "value": "qarzdor" },
    { "field": "PHONE", "operator": "STARTS_WITH", "value": "99890" }
  ]
}
```

| Maydon | Qiymatlar | Izoh |
|---|---|---|
| `matchAll` | `boolean` | `true` — kontakt **barcha** qoidaga mos kelsin (standart), `false` — bittasi yetadi |
| `rules[].field` | `TAG`, `NAME`, `PHONE`, `ADDRESS`, `NOTES`, `DO_NOT_CALL` | `TAG` — kontaktning vergul bilan ajratilgan teglaridan biri; `PHONE` faqat raqamlar bo'yicha solishtiriladi (`+998 71…` = `99871…`). `DO_NOT_CALL` — raqam kompaniyaning DNC ro'yxatida bormi: faqat `EQUALS` `true`/`false`. Retell kabi `PHONE`, `DO_NOT_CALL` va `TAG` — har biri **bitta qator**, matn maydonlari bir nechta |
| `rules[].operator` | `EQUALS`, `NOT_EQUALS`, `CONTAINS`, `NOT_CONTAINS`, `STARTS_WITH`, `ENDS_WITH`, `IS_ANY_OF` | Katta-kichik harf farqsiz. `TAG` + `EQUALS` — butun teg; `TAG` + `STARTS_WITH` — biror teg shu bilan boshlansin. `IS_ANY_OF` — faqat `TAG`: qiymat vergul bilan ajratilgan teglar, ulardan **biri** butun teg sifatida mos kelsa yetadi (`"qarzdor, vip"`) |
| `rules[].value` | `string` (1–200) | `PHONE` da kamida bitta raqam |

Ko'pi bilan 10 ta qoida. **Qoidasiz auto-enroll kampaniya hech kimni qo'shmaydi** (hammani emas);
draft holatida qoidasiz saqlash mumkin, ishga tushirish uchun esa qoida (yoki nishon) kerak.

**Ishga tushgandan keyin qoidalar qulflanadi** (Retell kabi): `DRAFT` bo'lmagan kampaniyaning `PUT` ida
boshqa `autoEnroll` — `409 CAMPAIGN_AUTO_ENROLL_LOCKED`. O'zgartirish uchun kampaniyani nusxalab, nusxaning
qoidalarini tahrirlang. Shu `autoEnroll` ni qayta yuborish (yoki umuman yubormaslik) ruxsat.

**Qanday nishon bo'ladi.** Raqam kontaktniki, `clientId = 0`, faktlar: `clientName` (kontakt
nomi), `address`, `tags` — `notes` o'tmaydi. Raqami kampaniyada **istalgan holatda** nishon bo'lgan
kontakt qayta qo'shilmaydi (qo'ng'irog'i tugagan kontakt ham). DNC ro'yxatidagi raqam
odatdagidek belgilab saqlanadi va terilmaydi.

**Tugamaydi.** Auto-enroll kampaniya nishonlari qolmasa ham `COMPLETED` bo'lmaydi — yangi
kontaktlarni kutadi; faqat `schedule.endAt` yoki qo'lda to'xtatish uni yopadi.

#### `POST /api/campaigns/{id}/auto-enroll/preview` — hozir kimlar mos keladi

Huquq: `CAMPAIGN_READ`. Hech kimni qo'shmaydi. Ikkala tur uchun: `AUTO_ENROLL` triggeri va `STANDARD`
kampaniyaning "Filter contacts" ro'yxati. Query: `page` (1 dan, standart `1`), `size` (1–100, standart `10`).

**Body ixtiyoriy.** Berilmasa — saqlangan qoidalar. Berilsa (`{ "matchAll": true, "rules": [...] }`) — hali
saqlanmagan, tahrirlanayotgan qoidalar hisoblanadi (Retell "Enrollment" jonli soni); to'liq to'ldirilmagan
qatorlar (maydon, operator yoki qiymatsiz) hisobga olinmaydi, xato bermaydi.

```json
{
  "success": true,
  "data": {
    "total": 42,
    "sample": [
      { "contactId": 7, "name": "Aziza opa", "phone": "+998901112233", "address": "Toshkent", "tags": "qarzdor, vip" }
    ]
  }
}
```

`total` — qoidalarga mos barcha kontaktlar (kampaniyada bor-yo'qligidan qat'i nazar; `excludedContactIds`
ham sanaladi va ro'yxatda chiqadi — belgini qaytarish shu yerda), `sample` — `page`-sahifa, eng eskilari birinchi.

### Standard — Filter contacts

`STANDARD` kampaniya kontaktlarni ikki manbadan **birini** oladi (Retell kabi, ikkalasi birga emas):

- **Filter contacts** — kompaniya [kontaktlari](contacts.md) `autoEnroll.rules` bo'yicha (yuqoridagi jadval,
  doim AND). Ro'yxat ishga tushguncha jonli: `POST /api/campaigns/{id}/auto-enroll/preview?page=N&size=20`.
  Belgisi olingan kontaktlar `autoEnroll.excludedContactIds` da (`PUT /api/campaigns/{id}`), "Chiqarilganlarni
  tiklash" — bo'sh ro'yxat. **Ishga tushirishda snapshot**: `start` (DRAFT dan) mos kontaktlarni (istisnolarsiz)
  nishon qiladi — rejalashtirilgan kampaniyada ham `start` paytida, `startAt` da emas; keyin mos kelganlar
  qo'shilmaydi. Ko'pi bilan 100 000 kontakt. Filtr hech kimga mos kelmasa `409 CAMPAIGN_HAS_NO_TARGETS`.
- **Fayl** — `POST /api/campaigns/{id}/targets/csv`. Filtr qatori bor kampaniyada `409 CAMPAIGN_CONTACT_FILTER_SET`;
  yuklangan nishonlari bor DRAFT'ga filtr qo'yish — `409 CAMPAIGN_CONTACTS_UPLOADED` (avval ro'yxatni tozalang).

Ishga tushgandan keyin filtr va istisnolar qulflanadi — `409 CAMPAIGN_AUTO_ENROLL_LOCKED`.

#### `DELETE /api/campaigns/{id}/targets` — yuklangan ro'yxatni tozalash

Huquq: `CAMPAIGN_EDIT`. Faqat `DRAFT` (aks holda `409 CAMPAIGN_CONTACTS_LOCKED`): faylni almashtirish yoki filtrga
o'tish uchun. Javob — o'chirilgan nishonlar soni (`data: 120`).

### Raqamlar — qaysi raqamdan qo'ng'iroq qilinadi

Raqam tanlashning uch yo'li bor — **trunk har doim raqamdan kelib chiqadi**, agentdan emas
(`docs/PHONE-NUMBER-BINDING.md`):

| `numbers` | Caller ID | Trunk |
|---|---|---|
| `fromNumberId` | shu raqam, har qo'ng'iroqda | raqamning o'z trunki (`providerTrunkId`); uning bo'sh kanallari partiyani cheklaydi |
| `callerIdPoolId` | pul raqamlaridan biri (pastda) | tanlangan raqamning o'z trunki |
| ikkalasi ham yo'q | trunkning `callerId` si | kompaniyaning barcha yoqilgan trunklari (round-robin) |

Agent (kampaniyaniki ham, sequence qadamining yoki A/B variantning ham) trunkka hech qachon ta'sir
qilmaydi — agentda trunk maydoni yo'q. `fromNumberId` ning raqami endi kompaniyaniki bo'lmasa yoki trunki
o'chirilgan/yo'q bo'lsa, kampaniya `PAUSED` ga o'tadi (sababi bilan) — boshqa raqam bilan
qo'ng'iroq qilinmaydi. Raqam o'chirilsa, `fromNumberId` `null` bo'ladi (pul kabi).

Pul ishlatilganda har qo'ng'iroqda raqam pulning strategiyasi bo'yicha olinadi (`ROUND_ROBIN`,
`RANDOM`, `LEAST_USED`, `HIGHEST_ANSWER_RATE`; dam olayotgan va o'chirilgan raqamlar o'tkazib
yuboriladi), faqat trunki ishlayotgan va bo'sh kanali bor raqamlar orasidan. Bunday raqam bo'lmasa,
nishon 5 daqiqaga kutadi (urinish hisoblanmaydi) — trunkning yoki platformaning raqami bilan
qo'ng'iroq qilinmaydi.

**`numbers.localPresence: true`.** Strategiyadan oldin pul raqamlari chaqirilayotgan raqamga
yaqinligi bo'yicha toraytiriladi: raqamlarning **boshidan eng ko'p mos tushadigan** raqamlari
qoladi, kamida **5 raqam** (davlat kodi + 2 xonali hudud/operator kodi, masalan `99871` —
Toshkent) mos kelsa. Qolganlar orasidan strategiya tanlaydi. 5 raqamdan kam mos kelsa —
chaqirilayotgan hudud pulda yo'q — butun pul ishlatiladi, ya'ni qo'ng'iroq baribir ketadi.
AQSh/NANP (`1415…`) kabi 3 xonali hudud kodlari uchun 5 raqam yetarli darajada qat'iy
emas — bu hozircha O'zbekiston raqamlariga moslangan.

Javob olingan raqam pul statistikasiga (`answerRate`) o'sha qo'ng'iroqda ishlatilgan raqam
bo'yicha yoziladi. Faqat AI rejimi (`dialing.mode: AI`) raqam/pulni ishlatadi; operator
rejimlari (PREVIEW/PROGRESSIVE/PREDICTIVE, jumladan PREDICTIVE'ning AI overflow'i) hozircha
kompaniyaning default trunki va uning caller ID'si bilan teradi.

### SCORE tartibi — nishonlar qaysi tartibda teriladi

`targetOrdering: "SCORE"` bo'lgan `ACTIVE` kampaniyaning kutayotgan (`PENDING`) nishonlari har 10 daqiqada
baholanadi (bitta yurishda kampaniya uchun 20 000 tagacha; avval hech baholanmaganlar, keyin 12 soatdan
oldin baholanganlar). Ball kompaniyaning shu raqam bilan **so'nggi 180 kunlik** tarixidan, qoida bo'yicha:

| Omil | Ta'siri |
|---|---|
| Tarix yo'q | `50` (neytral) |
| Gaplashish ulushi — `(gaplashilgan + 1) ÷ (terilgan + 2)` | ±20 (50% atrofida) |
| Har `REFUSED` qo'ng'iroq | −5, ko'pi bilan −15 |

Natija `0–100`. "Terilgan" — trunk'ka chiqqan chiquvchi qo'ng'iroq; "gaplashilgan" — ulardan
ko'tarilgan va natijasi `VOICEMAIL`, `NO_ANSWER`, `CARRIER_REJECTED`, `FAILED`, `WRONG_NUMBER` bo'lmagani.
`bestCallHour` — gaplashilgan qo'ng'iroqlar eng ko'p bo'lgan soat (kompaniya vaqt zonasida; teng bo'lsa —
ertarog'i).

Dialer (`AI` va `PREDICTIVE`) navbatdan nishonlarni `ball + 20 (agar bestCallHour = hozirgi soat)`
kamayish tartibida oladi; hali baholanmagan nishon `50` hisoblanadi. **Hech kim kutib turilmaydi** —
tartib faqat sig'im cheklangan paytda (`dailyCallCap`, `maxConcurrentCalls`, kanal limitlari) ta'sir
qiladi. DNC, kontakt chastotasi siyosati, dial oynasi va retry qoidalari o'zgarmaydi. `PREVIEW` va
`PROGRESSIVE` kampaniyalarda operator kartasi hali ham `FIFO` tartibida beriladi.

**A/B solishtirish:** ro'yxatni ikki kampaniyaga bo'ling (biri `FIFO`, biri `SCORE`; bir xil agent va
`dailyCallCap`), so'ng undirishni [conversion](conversions.md)
hisobotida kampaniya bo'yicha solishtiring.

---

## 2. Nishonlar (targets)

### `POST /api/campaigns/{id}/targets` — Nishonlarni JSON bilan qo'shish

Body — `AddTargetRequest` **massivi**:

```json
[
  { "clientId": 1001, "phone": "+998901234567", "language": "uz-UZ",
    "contextData": { "clientName": "Aziz Karimov", "debtAmount": 1500000 } },
  { "clientId": 1002, "phone": "+998901234568", "language": null, "contextData": {} }
]
```

| Maydon | Majburiymi | Izoh |
|---|---|---|
| `clientId` | ✅ (`@Positive`) | CRM dagi mijoz id'si |
| `phone` | ✅ (`@NotBlank`) | Teriladigan raqam |
| `language` | ❌ | Nishon uchun til; `null` — kampaniya tili |
| `contextData` | ❌ | Erkin JSON obyekt: promptdagi `{{...}}` o'zgaruvchilari shu yerdan olinadi. Shu nishon uchun agentni moslash: `agentOverrides` (`firstMessage`, `systemPrompt`, `variables`), batafsil [calls.md](calls.md) instant bo'limida |

**Response** (`AddTargetsResponse`):

```json
{ "accept": true, "data": { "campaignId": 12, "added": 2, "targetIds": [501, 502], "skippedDuplicates": 0, "skippedDoNotCall": 0 }, "errors": null }
```

Kampaniya topilmasa yoki boshqa kompaniyaniki bo'lsa — `404 CAMPAIGN_NOT_FOUND` (CSV import
ham shunday). Massivda noto'g'ri raqam bo'lsa (`400 PHONE_INVALID`) hech bir qator yozilmaydi.

Har bir raqam ikki tekshiruvdan o'tadi (CSV import va `targets/sync` ham shunday):
- **Dublikat** — kampaniyada shu raqam allaqachon navbatda (`PENDING`/`IN_PROGRESS`) bo'lsa,
  qayta qo'shilmaydi: `skippedDuplicates` oshadi, mavjud qator o'zgarmaydi. Tugagan
  (`DONE`/`EXHAUSTED`/`FAILED`) raqam yangi nishon bo'la oladi (masalan callback).
  DB darajasida `idx_campaign_target_queued_phone` qisman unique indeks kafolatlaydi.
- **Do-not-call** — raqam kompaniyaning DNC ro'yxatida bo'lsa, nishon `doNotCall: true`
  belgisi bilan saqlanadi (statistikada ko'rinadi, dialer hech qachon termaydi):
  `skippedDoNotCall` oshadi, `added` va `targetIds` ga ham kiradi.

---

### `POST /api/campaigns/{id}/targets/csv` — CSV import

`Content-Type: text/csv` (yoki `text/plain`), body — CSV faylning o'zi (ko'pi bilan 5 000 000
belgi; uzunrog'i → `400 VALIDATION_FAILED`):

```csv
clientId,phone,language,clientName,debtAmount,debtDay,currency,dueDate,orderNumber,deliveryAddress
1001,+998901234567,uz-UZ,Aziz Karimov,1500000,15,so'm,2026-07-01,ORD-1029,Toshkent Chilonzor
```

Ajratuvchi (`,` yoki `;`) sarlavha qatoridan avtomatik aniqlanadi. `phone` ustuni
bo'lmasa — `400 CSV_PHONE_COLUMN_MISSING`; fayl bo'sh bo'lsa — `400 CSV_EMPTY`.

**Response** (`TargetImportResult`):

```json
{
  "accept": true,
  "data": {
    "campaignId": 12,
    "added": 45,
    "targetIds": [501, 502],
    "skippedDuplicates": 3,
    "skippedDoNotCall": 1,
    "errors": [ { "line": 8, "message": "phone is empty" } ],
    "unknownColumns": ["orderNumber", "deliveryAddress"]
  },
  "errors": null
}
```

`unknownColumns` — importer tanimagan sarlavhalar. Ular **tashlab yuborilmaydi**:
har biri `contextData` ga kalit bo'lib tushadi. Ro'yxat sarlavhadagi xatoni ko'rsatish
uchun qaytariladi (`orderNumbr` deb yozilsa, `{{orderNumber}}` promptda bo'sh qoladi).

### `POST /api/campaigns/{id}/targets/csv/preview` — Import oldidan ko'rish

Xuddi shu body, lekin **hech narsa yozilmaydi** — fayl qanday o'qilishini qaytaradi
(§10.6 "ustunni moslashtirish" sehrgari uchun).

**Response** (`TargetCsvPreview`):

```json
{
  "accept": true,
  "data": {
    "columns": [
      { "header": "phone", "mappedField": "phone" },
      { "header": "qarz", "mappedField": "debtAmount" },
      { "header": "orderNumber", "mappedField": null }
    ],
    "sampleRows": [
      { "line": 2, "clientId": 1001, "phone": "+998901234567", "language": "uz-UZ",
        "contextJson": "{\"clientName\":\"Aziz Karimov\",\"debtAmount\":\"1500000\"}" }
    ],
    "totalRows": 45,
    "errors": [],
    "unknownColumns": ["orderNumber"]
  },
  "errors": null
}
```

`mappedField: null` — ustun tanilmadi, ya'ni u `contextData` ga o'z sarlavhasi bilan
tushadi. `mappedField: ""` — ustun qo'lda chiqarib tashlangan (pastda). Javobda yana `knownFields` —
qo'lda tanlash mumkin bo'lgan maydonlar (`["phone","clientId","language","clientName","debtAmount",…]`).

### Ustunlarni qo'lda moslash va XLSX (Retell "Map the columns")

Huquq: `CAMPAIGN_EDIT`. Filtr qatori bor `STANDARD` kampaniyada — `409 CAMPAIGN_CONTACT_FILTER_SET`.

#### `POST /api/campaigns/{id}/targets/xlsx` — Excel varag'ini CSV ga o'girish

`multipart/form-data`: `file` (.xlsx, ko'pi bilan 5 MB), query `sheet` (0 dan, standart `0`; yo'q varaq — birinchisi).
Hech narsa import qilinmaydi. Kataklar Excel'da ko'ringanidek (sana — sana bo'lib) yoziladi, birinchi bo'sh
bo'lmagan qator — sarlavha, bo'sh qatorlar tashlanadi.

```json
{ "accept": true, "data": { "sheetNames": ["Qarzdorlar", "Arxiv"], "sheet": 0, "csv": "Telefon,Ism\n+998901234567,Aziz\n", "rows": 1 } }
```

Xatolar: `400 SPREADSHEET_UNREADABLE` (fayl .xlsx emas yoki buzilgan), `400 SPREADSHEET_TOO_MANY_ROWS`
(100 000 qatordan ko'p). Qaytgan `csv` pastdagi ikki endpoint'ga beriladi.

#### `POST /api/campaigns/{id}/targets/import/preview` va `POST /api/campaigns/{id}/targets/import`

```json
{ "csv": "Mijoz raqami,Ismi,Filial,Izoh\n+998901234567,Aziz,Chilonzor,x\n",
  "columns": { "Mijoz raqami": "phone", "Ismi": "clientName", "Filial": "branchName", "Izoh": "" } }
```

`columns` — sarlavha → maydon: `knownFields` dan biri, yangi fakt nomi (harf bilan boshlanadi, harf/raqam/`_`,
≤ 64; `contextData` ga shu nom bilan tushadi) yoki `""` — ustun yuklanmaydi. `columns` da yo'q sarlavha oddiy
CSV importdagidek avto-moslanadi. Ikki ustunni bitta maydonga qo'lda qo'yish — `400 CSV_COLUMN_MAPPED_TWICE`,
yaroqsiz nom — `400 CSV_COLUMN_FIELD_INVALID`.

- `.../import/preview` — `TargetCsvPreview` (yuqoridagi kabi), hech narsa yozilmaydi. Telefon ustuni hali
  tanlanmagan yoki moslash xato bo'lsa so'rov yiqilmaydi: `totalRows: 0` va `errors: [{ "line": 1, "message": … }]`
  — moslash oynasi ustunlarni ko'rsatishda davom etadi.
- `.../import` — `TargetImportResult`, `targets/csv` bilan bir xil (DNC, takror raqamlar, qator xatolari).

### 🪄 Aqlli Ko'p Tilli CSV Avto-Moslashuvi

Sarlavha kichik harfga keltirilib, harf-raqamdan boshqa belgilar olib tashlanadi
(`Kechikish_kunlari` = `kechikishkunlari`), keyin quyidagi ro'yxat bo'yicha moslanadi:

| Maydon | Qabul qilinadigan sarlavhalar |
|---|---|
| `phone` | `phone`, `tel`, `telefon`, `telefonraqam`, `raqam`, `phonenumber`, `nomer`, `nomertelefona`, `mobile`, `mobil` |
| `clientId` | `clientId`, `id`, `mijozid`, `kod`, `user_id`, `userid`, `customerid` |
| `language` | `language`, `lang`, `til`, `yazyk`, `yazik` |
| `clientName` | `clientName`, `name`, `ism`, `fio`, `fullname`, `mijoz`, `mijozismi`, `imya`, `fio_klienta` |
| `debtAmount` | `debtAmount`, `debt`, `amount`, `summa`, `qarz`, `qarzsummasi`, `qarzdorlik`, `dolg`, `summadolga`, `totaldebt` |
| `debtDay` | `debtDay`, `debtDays`, `overdueDays`, `delayDays`, `daysOverdue`, `qaszkuni`, `kechikish_kunlari`, `kechikish`, `procrochka`, `dneyprosrochki`, `prosrochkadays` |
| `dueDate` | `dueDate`, `deadline`, `muddat`, `tolashmuddati`, `oxirgimuddat`, `srok`, `srokoplaty`, `paydate` |
| `contractNumber` | `contractNumber`, `contract`, `contractNo`, `shartnoma`, `shartnomanomeri`, `shartnomaraqami`, `dogovor`, `nomerdogovora` |
| `penaltyAmount` | `penaltyAmount`, `penalty`, `peniya`, `penya`, `jarima`, `shtraf`, `peni` |
| `contractCancelDays` | `contractCancelDays`, `cancelDays`, `bekorkun`, `bekorqilishkuni`, `shartnomabekor`, `raskhoreniedney` |
| `currency` | `currency`, `valyuta`, `valyutanomi` |
| `goal` | `goal`, `maqsad`, `cel` |
| *(boshqa har qanday ustun)* | O'z sarlavhasi bilan `contextData` ga tushadi va promptda `{{orderNumber}}`, `{{deliveryAddress}}` sifatida ishlatiladi |

### 📝 Dinamik Prompt Shablon Sintaksisi

Stsenariy matnlarida istalgan o'zgaruvchini `{{varName}}` yoki sukut qiymat bilan
`{{varName | "Standart qiymat"}}` ko'rinishida yozish mumkin:

```text
"Assalomu alaykum {{clientName | "Hurmatli mijoz"}}! Sizning {{orderNumber}} raqamli buyurtmangiz {{deliveryAddress}} manziliga yetkazilmoqda."
```

---

### `POST /api/campaigns/{id}/targets/list` — Nishonlar ro'yxati

Body — `TargetFilter` (`page`/`size`/`orders`). Saralanadigan ustunlar: `ID`, `PHONE`,
`STATUS`, `ATTEMPTS`. Standart: `ID ASC`.

Javob — `PageableData<CampaignTarget>`:

```json
{
  "id": 501,
  "campaignId": 12,
  "clientId": 1001,
  "phone": "+998901234567",
  "language": "uz-UZ",
  "contextData": "{\"clientName\":\"Aziz Karimov\",\"debtAmount\":1500000}",
  "status": "PENDING",
  "attempts": 0,
  "doNotCall": false,
  "nextAttemptAt": null,
  "score": 63,
  "bestCallHour": 10,
  "stepId": "step1",
  "stepAttempts": 2,
  "outcome": "Success",
  "converted": true
}
```

`status` (`TargetStatus`): `PENDING`, `IN_PROGRESS`, `DONE`, `FAILED`, `EXHAUSTED`
(urinishlar limiti tugadi). `contextData` — **string** ichidagi JSON, obyekt emas.
`score` (0–100) va `bestCallHour` (0–23) — `SCORE` kampaniyada nishon baholangandan keyin to'ladi
([SCORE tartibi](#score-tartibi--nishonlar-qaysi-tartibda-teriladi)), aks holda `null`.
`stepId` / `stepAttempts` — nishon turgan sequence qadami (`null` — boshlang'ich qadam) va unda sarflangan
qo'ng'iroqlar. `outcome` — oxirgi javob berilgan qo'ng'iroq tushgan sequence natijasining nomi (`"Other"`,
`"No Response"` ham bo'ladi); hali baholanmagan bo'lsa `null`. `converted` — nishon **End successful** yo'li
bilan chiqqan (kampaniyaning "muvaffaqiyatli" hisobiga kiradi).

---

### Target source — ro'yxatni API'dan olish

CSV yuklash o'rniga kampaniya ro'yxatni kompaniyaning **o'z API**'sidan olishi mumkin.
Asosiy foydasi takroriy kampaniyada: `recurrenceType: "DAILY"` allaqachon kampaniyani har
kuni qayta ishga tushiradi, lekin **eski ro'yxat** ustidan. Target source ulangach, har bir
takror ishga tushishdan oldin ro'yxat qaytadan olib kelinadi — "har kuni ertalab bugungi
qarzdorlarga qo'ng'iroq qil" uchun hech kim fayl yuklamaydi.

| Metod | URL | Permission |
|---|---|---|
| `GET` | `/api/campaigns/{id}/target-source` | `CAMPAIGN_READ` |
| `PUT` | `/api/campaigns/{id}/target-source` | `CAMPAIGN_EDIT` |
| `DELETE` | `/api/campaigns/{id}/target-source` | `CAMPAIGN_EDIT` |
| `POST` | `/api/campaigns/{id}/targets/sync` | `CAMPAIGN_EDIT` |

**`PUT` tanasi** (`UpdateTargetSourceRequest`):

```json
{
  "url": "https://crm.company.uz/api/overdue-clients",
  "method": "GET",
  "requestBody": null,
  "authHeaderName": "Authorization",
  "authHeaderValue": "Bearer eyJhbGciOi...",
  "itemsPath": "data.items",
  "phoneField": "phone",
  "clientIdField": "client_id",
  "languageField": "lang",
  "replaceTargets": true,
  "syncOnRecurrence": true,
  "enabled": true
}
```

| Maydon | Majburiy | Izoh |
|---|---|---|
| `provider` | ❌ | `GENERIC` (standart), `CHAINED` yoki `TOOL`. Pastdagi bo'limga qarang |
| `toolName` | `TOOL` uchun ✅ | Integratsiya vositasining nomi (kompaniya uchun yoqilgan va `effect: READ` bo'lishi shart) |
| `toolArguments` | ❌ | `TOOL` uchun: vosita chaqiriladigan argumentlar (`{"status": "OVERDUE"}`) |
| `url` | `GENERIC` uchun ✅ | `http`/`https`. Chaqirish paytida loopback va ichki tarmoq manzillariga qarshi tekshiriladi (SSRF himoyasi) — `400 TARGET_SOURCE_URL_INVALID` |
| `steps` | `CHAINED` uchun ✅ | Ketma-ket so'rovlar. Pastdagi bo'limga qarang |
| `method` | ❌ | `GET` (standart) yoki `POST` |
| `requestBody` | ❌ | `POST` uchun yuboriladigan JSON, o'zgartirilmasdan |
| `authHeaderName` / `authHeaderValue` | ❌ | Masalan `Authorization: Bearer ...`. **Qiymat shifrlanib saqlanadi va API orqali qaytarilmaydi**; tahrirlashda bo'sh qoldirsangiz eski qiymat saqlanib qoladi |
| `itemsPath` | ❌ | Javob ichidagi massivgacha nuqtali yo'l (`"data.items"`). Javobning o'zi massiv bo'lsa — bo'sh qoldiring |
| `phoneField` | ❌ | Har bir qatordagi raqam kaliti; standart `phone` |
| `clientIdField` | ❌ | CRM mijoz id kaliti; bo'lmasa `0` |
| `languageField` | ❌ | Qator uchun til; bo'lmasa agentning tili |
| `replaceTargets` | ❌ | Import oldidan eski ro'yxatni almashtirish. Endpoint javob bergandan **keyin** bajariladi — muvaffaqiyatsiz so'rov ro'yxatni bo'shatib qo'ymaydi. Faqat hali terilmagan (`PENDING`, `attempts = 0`) nishonlar o'chiriladi; qayta urinishni kutayotgan `PENDING` nishonlar `EXHAUSTED` qilinadi; `IN_PROGRESS` va yakunlangan nishonlarga tegilmaydi (ularning urinishlari va natijalari tarix) |
| `syncOnRecurrence` | ❌ | Har bir takror ishga tushishda avtomatik olish. Standart `true` |
| `enabled` | ❌ | `false` — avtomatik ham, qo'lda ham ishlamaydi |

Qatordagi qolgan barcha kalitlar nishonning **faktlari** (`context_data`) bo'lib saqlanadi;
agent ulardan faqat senariyning `factSchema` da e'lon qilinganlarini gapira oladi.

**`GET` javobi** (`TargetSourceRow`) — `authHeaderValue` o'rniga `authHeaderSet: true/false`,
qo'shimcha: `provider`, `steps`, `lastSyncAt`, `lastSyncAdded`, `lastSyncError`.

#### `provider` — ro'yxat qayerdan keladi

| Qiymat | `PUT` bilan qo'yiladimi | Ma'nosi |
|---|---|---|
| `GENERIC` | ✅ (standart) | `url` ga bitta so'rov; javob — telefonli qatorlar massivi |
| `CHAINED` | ✅ | Bir nechta so'rov: birinchisi ro'yxat beradi, keyingilari **har bir qator uchun** chaqirilib, ro'yxatda yo'q ma'lumotni to'ldiradi |
| `TOOL` | ✅ | Integratsiya vositasi (`toolName`): so'rov, manzil va token vositaning o'zida (platforma admini bir marta belgilagan), kampaniya faqat nomini aytadi. Ro'yxat vositaning **xom** javobidan `itemsPath`/`phoneField`/`clientIdField`/`languageField` bilan o'qiladi (`GENERIC` kabi). Faqat o'qiydigan (`READ`) vosita — import CRM'ni o'zgartirmasligi kerak |
| `UYSOT_DEBTORS` | ❌ | Uysot qarzdorlari. `R__seed_data.sql` da beriladi; kredensiallarni Integratsiyalar bo'limidagi Uysot OAuth ulanishidan oladi, formadan emas |

#### `CHAINED` — istalgan servis uchun ko'p bosqichli manba

Ko'p CRM'da "kim qarzdor" degan endpoint bilan "uning telefoni nima" degan endpoint bir xil
emas. `CHAINED` shuni **sozlama** sifatida yozishga imkon beradi — kod yozilmaydi.

```json
{
  "provider": "CHAINED",
  "authHeaderName": "X-Open-Api-Token",
  "authHeaderValue": "…",
  "phoneField": "phone",
  "clientIdField": "leadId",
  "replaceTargets": true,
  "steps": [
    {
      "name": "contracts",
      "method": "POST",
      "url": "https://api.example.uz/v1/contract/filter",
      "body": "{\"page\":{{page}},\"size\":100,\"statuses\":[\"ACTIVE\"]}",
      "itemsPath": "data.data",
      "paginate": true,
      "filters": [
        { "path": "delay",   "operator": "GTE", "value": "1" },
        { "path": "residue", "operator": "GT",  "value": "0" }
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
      "url": "https://api.example.uz/v1/contract/{{contractId}}",
      "extract": { "leadId": "data.lead.id" }
    },
    {
      "name": "lead",
      "method": "GET",
      "url": "https://api.example.uz/v1/lead/{{leadId}}",
      "extract": { "phone": "data.contacts.0.phones.0" }
    }
  ]
}
```

| Step maydoni | Izoh |
|---|---|
| `name` | Xato xabarida ko'rinadi (`step 'lead': …`) |
| `method` | `GET` (standart) yoki `POST` |
| `url` | `{{o'zgaruvchi}}` qo'yiladi va URL-encode qilinadi. Har bir step alohida SSRF tekshiruvidan o'tadi |
| `body` | `POST` uchun JSON shablon; `{{o'zgaruvchi}}` JSON-escape bilan qo'yiladi |
| `itemsPath` | **Faqat 1-step**: javob ichidagi massivgacha nuqtali yo'l. Bo'sh — javobning o'zi massiv |
| `paginate` | **Faqat 1-step**: `{{page}}` ni 1 dan boshlab oshirib, bo'sh sahifa kelguncha yoki chegaraga yetguncha chaqiradi |
| `filters` | **Faqat 1-step**: `path` + `operator` (`GT`,`GTE`,`LT`,`LTE`,`EQ`,`NE`,`PRESENT`,`ABSENT`) + `value`. Barchasi bajarilgan qatorgina keyingi steplarga o'tadi — aynan shu narsa har bir mijoz uchun ortiqcha so'rov ketishini to'xtatadi |
| `extract` | `o'zgaruvchi nomi` → nuqtali yo'l. 1-stepda yo'l **qatorga** nisbatan, keyingilarida — **butun javobga**. Raqamli segment massiv indeksi: `contacts.0.phones.0` |

`phoneField` / `clientIdField` / `languageField` — steplar yig'gan qaysi o'zgaruvchi telefon,
mijoz id va til ekanini aytadi. **Qolgan barcha o'zgaruvchilar** nishonning faktlari
(`context_data`) bo'lib qoladi; agent ulardan faqat senariyning `factSchema` da
e'lon qilinganlarini gapira oladi.

`authHeaderName`/`authHeaderValue` **butun zanjirga** yuboriladi — bitta manba bitta servis.

**Xatoga munosabat ikki xil.** 1-step (ro'yxat) yiqilsa — butun sync yiqiladi va mavjud
nishonlar tegilmaydi; yarim o'qilgan ro'yxat ustidan import qilish kampaniyani jimgina
qisqartirar edi. Keyingi steplardan biri **bitta qator** uchun yiqilsa — o'sha qator
`errors[]` ga tushadi, qolganlari davom etadi.

Chegaralar: 1-stepdan ko'pi bilan 5000 qator, 100 sahifa.

Xuddi shu misol `R__seed_data.sql` da **"Namuna — CHAINED manba"** kampaniyasiga ulangan
holda turadi: `GET /api/campaigns/{id}/target-source` bilan o'qib olib, uchta URL va
`extract` yo'llarini o'zingiznikiga almashtirsangiz bo'ldi. Namuna ataylab ishlamaydigan
qilingan — kampaniya `DRAFT` + `ONCE`, manba `enabled: false`, host esa `example.uz`.

Validatsiya (`400`): `TARGET_SOURCE_STEPS_REQUIRED` (`CHAINED`, lekin step yo'q),
`TARGET_SOURCE_STEP_URL_MISSING` (step'da `url` yo'q), `TARGET_SOURCE_STEP_EXTRACTS_NOTHING` (`extract` bo'sh),
`TARGET_SOURCE_STEP_PAGINATION_NOT_FIRST` (1-stepdan boshqasida
`paginate`), `TARGET_SOURCE_URL_INVALID` (`GENERIC`, lekin `url` yo'q).

#### `TOOL` — integratsiya vositasi manba sifatida

```json
{
  "provider": "TOOL",
  "toolName": "list_overdue_debtors",
  "toolArguments": { "status": "OVERDUE" },
  "itemsPath": "data.items",
  "phoneField": "phone",
  "clientIdField": "leadId",
  "replaceTargets": true
}
```

Sinxronlash (`POST .../sync` va takror ishga tushish) vositani kompaniya nomidan bir marta ishga tushiradi.
Qatorning qolgan kalitlari nishon faktlari bo'ladi (`GENERIC` kabi). Xatolar: `404 TARGET_SOURCE_TOOL_UNAVAILABLE`
(vosita kompaniyaga yoqilmagan yoki Uysot ulanmagan), `400 TARGET_SOURCE_TOOL_NOT_READ_ONLY` (vosita `WRITE`),
`400 TARGET_SOURCE_TOOL_REQUIRED` (`toolName` yo'q). Vosita javobining shaklini (`itemsPath` uchun) platforma
admin ekranidagi "Test" (`raw`) ko'rsatadi. `GET` javobiga `toolName` va `toolArguments` qo'shiladi.

#### `UYSOT_DEBTORS`

Uysot'da qarzdorning telefoniga yetish uchun uchta o'qish kerak:
`POST /v1/open-api/contract/filter` (qaysi shartnomalar `delay` kun kechikkan) →
`GET /v1/open-api/contract/{id}` (shartnomaning `lead` i) →
`GET /v1/open-api/lead/{leadId}` (kontakt telefonlari). Nishonning `client_id` si — Uysot
`leadId` si, suhbatdan keyingi izoh ham o'shanga yoziladi.

Yuqoridagi `CHAINED` misoli aynan shu zanjirni ifodalaydi. `UYSOT_DEBTORS` alohida qolgani
sababi bitta: u tokenni Integratsiyalar bo'limidagi **Uysot OAuth ulanishidan** oladi,
formaga qo'lda yozilgan headerdan emas.

**Qarz summasi.** To'rtinchi o'qish — shartnomaning faol to'lov jadvali
(`voice-agent.crm.catalog.contract-payment-path`). Nishon faktlari:

| Fakt | Ma'nosi |
|---|---|
| `debtAmount` | **Muddati o'tgan** to'lanmagan to'lovlar yig'indisi — agent aynan shuni aytadi. Jadval o'qilmasa — shartnoma qoldig'i (`residue`) |
| `remainingAmount` | Shartnoma bo'yicha umumiy qoldiq (`residue`) |
| `nextPaymentDate`, `nextPaymentAmount` | Bugundan keyingi birinchi to'lanmagan to'lov |
| `responsibleName` | Shartnoma bo'yicha mas'ul menejer |
| `clientName`, `contractNumber`, `currency`, `delayDays` | Avvalgidek |

### `GET /api/campaigns/uysot-debtors/preview?limit=20` — Bugungi qarzdorlarni oldindan ko'rish

`UYSOT_DEBTORS` kampaniyasi bugun kimga qo'ng'iroq qilishini Uysot'dan jonli o'qiydi, hech narsa
import qilinmaydi. `limit` 1–50 (standart 20). Huquq: `CAMPAIGN_READ`. Uysot ulanmagan bo'lsa — bo'sh ro'yxat.
Har bir qarzdor 3–4 ta Uysot so'rovi turadi, shuning uchun bir necha soniya ketishi mumkin.

```json
{
  "accept": true,
  "data": [
    { "leadId": 99436, "phone": "+998901234567", "name": "Ali Valiyev", "contractNumber": "UY-123",
      "debtAmount": 4000, "currency": "USD", "delayDays": 35, "remainingAmount": 50000,
      "nextPaymentDate": "2026-11-01", "nextPaymentAmount": 2000, "responsibleName": "Dilshod Karimov" }
  ]
}
```

Nechta qarzdor olinishi `voice-agent.crm.debtor-import.*` bilan cheklanadi
(`min-delay-days`, `max-debtors`, `page-size`, `max-pages`) — Open API daqiqasiga 60 so'rovga
ruxsat beradi, bitta qarzdor esa to'rtta so'rovga tushadi.

**`POST /targets/sync` javobi** (`TargetSyncResult`):

```json
{
  "accept": true,
  "data": {
    "campaignId": 12,
    "fetched": 340,
    "added": 337,
    "removed": 300,
    "skippedDuplicates": 2,
    "skippedDoNotCall": 0,
    "errors": [{ "line": 41, "message": "no 'phone' in row" }]
  }
}
```

Xatolar: `404 TARGET_SOURCE_NOT_FOUND` (source sozlanmagan), `409 TARGET_SOURCE_DISABLED`
(`enabled: false` — qo'lda ham ishlamaydi), `502 TARGET_SOURCE_FETCH_FAILED` (endpoint javob bermadi),
`502 TARGET_SOURCE_HTTP_STATUS` (javob 2xx emas), `502 TARGET_SOURCE_RESPONSE_EMPTY` (javob bo'sh),
`502 TARGET_SOURCE_RESPONSE_TOO_LARGE` (javob 16 MB dan katta), `502 TARGET_SOURCE_FETCH_INTERRUPTED`,
`400 TARGET_SOURCE_RESPONSE_NOT_JSON` (javob JSON emas), `400 TARGET_SOURCE_RESPONSE_NOT_ARRAY` (javob massiv emas —
`itemsPath` kerak), `400 TARGET_SOURCE_ITEMS_PATH_EMPTY` (`itemsPath` da hech narsa yo'q).
Har holatda ham kampaniyaning mavjud nishonlari **tegilmaydi**; 502 va 400 xatolarning sababi
`lastSyncError` ga yoziladi.

Avtomatik (takror) ishga tushishda xato **kampaniyani to'xtatmaydi**: sweep barcha
tenantlarga xizmat qiladi, shuning uchun xato loglanadi, `lastSyncError` ga yoziladi va
kampaniya eski ro'yxat bilan ishlaydi.

---

### `POST /api/targets/{id}/do-not-call` — Nishonni DNC ga qo'shish

E'tibor bering: path `/api/campaigns/...` **emas**, `/api/targets/{id}/do-not-call`.
Body yo'q. Nishonning raqami kompaniya opt-out ro'yxatiga tushadi
([do-not-call.md](do-not-call.md)).

**Response** (`DoNotCallResponse`):

```json
{ "accept": true, "data": { "targetId": 501, "doNotCall": true }, "errors": null }
```

---

## 3. Kampaniyani boshqarish amallari

| Endpoint | Nima qiladi |
|---|---|
| `POST /api/campaigns/{id}/start?immediate=false` | Kampaniyani ishga tushirish (`ACTIVE`; `startAt` kelajakda bo'lsa — `SCHEDULED`) |
| `POST /api/campaigns/{id}/pause` | Vaqtincha to'xtatish (`PAUSED`) |
| `DELETE /api/campaigns/{id}` | Arxivlash (`ARCHIVED`) |

Uchalasi ham `CampaignStatusResponse` (`{ "campaignId": 12, "status": "ACTIVE" }`)
qaytaradi. Ruxsat etilmagan o'tish — `409 CAMPAIGN_INVALID_TRANSITION` (yuqoridagi holat
mashinasi); `start` nishonsiz va target source'siz — `409 CAMPAIGN_HAS_NO_TARGETS`, agenti o'chirilgan (`enabled=false`) kampaniyada — `409 AI_AGENT_DISABLED` (dialer bunday kampaniyani har tick'da o'tkazib yuboradi).


### Ishga tushirish: consent, tekshiruv, Overview

**Consent.** `DRAFT` kampaniya birinchi marta ishga tushirilishidan oldin kompaniya raqamlarining
qonuniy yig'ilganini (rozilik, DNC qonunlari, opt-in) tasdiqlaydi — Retell'dagi "I confirm this
campaign respects all applicable legal and consent requirements" belgisi:

`POST /api/campaigns/{id}/consent` — ruxsat `CAMPAIGN_EDIT`; javob `CampaignRow`
(`consentConfirmedAt` to'ldiriladi). Qayta chaqirish hech narsani o'zgartirmaydi. Tasdiq
audit logga yoziladi (kim va qachon).

`start` `DRAFT` kampaniyada tasdiqsiz — `400 CAMPAIGN_CONSENT_REQUIRED`. Allaqachon ishga
tushgan (`PAUSED`, `COMPLETED`, `SCHEDULED`) kampaniya qayta so'ralmaydi. Nusxa (`clone`) yangi
`DRAFT`, shuning uchun tasdiqni qayta oladi.

**Start now / Schedule for later** — `schedule.startAt` va `start` orqali: `startAt` kelajakda
bo'lsa kampaniya `SCHEDULED` bo'lib turadi, aks holda (yoki `?immediate=true`) darhol `ACTIVE`.

**Ishga tushirish shartlari** (`start` ham, Overview ham bir xil tekshiradi; birinchi to'sqinlik
qaytadi):

| Shart | Xato |
|---|---|
| `AI`/`PREDICTIVE` da `aiAgentId` bor | `400 CAMPAIGN_AI_AGENT_REQUIRED` |
| Sequence'ning har `CONVERSATION` qadamida agent bor | `400 CAMPAIGN_SEQUENCE_INVALID` |
| Kampaniya va barcha qadam agentlari yoqilgan | `409 AI_AGENT_DISABLED` |
| Nishon, target source yoki auto-enroll qoidasi bor | `409 CAMPAIGN_HAS_NO_TARGETS` |
| `schedule.endAt` o'tib ketmagan | `409 CAMPAIGN_END_PASSED` |

#### `GET /api/campaigns/{id}/overview` — Overview tab

Ruxsat `CAMPAIGN_READ`. Hech narsani o'zgartirmaydi.

```json
{
  "success": true,
  "data": {
    "campaign": { "...": "CampaignRow, GET /api/campaigns/{id} dagidek" },
    "callerIdPoolName": "Toshkent mijozlari puli",
    "callerIdNumberCount": 4,
    "launchBlocker": { "code": "CAMPAIGN_AI_AGENT_REQUIRED", "message": "dialMode AI and PREDICTIVE need aiAgentId — ..." }
  }
}
```

`launchBlocker` — yuqoridagi jadvaldan birinchi to'sqinlik, hech narsa to'smasa `null`. Consent
to'sqinlik emas: u ishga tushirish paytida so'raladi (`campaign.consentConfirmedAt` bilan
tekshiring). Contacts soni — `campaign.stats.totalTargets`, "Successful / Total" —
`stats.successfulTargets` / `stats.totalTargets`.
`maxConcurrentCalls` endi haqiqatan ishlaydi: dialer kampaniya kesimida ham hisoblaydi
(`dialer:active:campaign:{id}`) va shu chegaraga yetganda kampaniyaga yangi qo'ng'iroq
bermaydi (kompaniya va platforma chegaralari o'z kuchida qoladi).

**`immediate` parametri:**
`false` (standart) — har bir nishon o'ziga belgilangan vaqtni kutadi.
`true` — kutayotgan (`PENDING`) nishonlarning `nextAttemptAt` i tozalanadi va ular
keyingi dialer tickda navbatga tushadi. Urinishlar soni (`attempts`) saqlanadi —
limitini tugatgan nishon qayta chaqirilmaydi. Har ikki holatda ham `dialWindowStart/End`
va `dialDays` cheklovi kuchida qoladi: `immediate` "hoziroq" emas, "ruxsat etilgan eng
yaqin paytda" degani.

> ⚠️ `POST /api/campaigns/{id}/cancel`, `/archive` va `/speed` endpointlari **yo'q** —
> arxivlash `DELETE` orqali, tezlik esa `maxConcurrentCalls` / `dailyCallCap` orqali
> boshqariladi.

---

## 4. A/B variantlar (`/api/campaigns/{campaignId}/variants`)

Bitta kampaniya ichida bir nechta ssenariy/prompt/ovoz variantini yonma-yon sinash.

### Variant qo'ng'iroqqa qanday qo'llanadi

Dialer har bir nishonni navbatga qo'yayotganda kampaniyaning **faol** variantlaridan bittasini tanlaydi va qo'ng'iroqni o'sha variant bilan yuboradi:

| Variant maydoni | Ta'siri |
|---|---|
| `aiAgentId` | To'ldirilgan bo'lsa, qo'ng'iroq kampaniya agenti o'rniga shu agent bilan ketadi — boshqa senariy, ovoz yoki persona bilan |
| `ttsVoiceId` | To'ldirilgan bo'lsa, qo'ng'iroq shu ovozda gapiradi. Agentning `languageVoices` xaritasi bu qo'ng'iroq uchun **bekor qilinadi** — aks holda u variant ovozidan ustun kelib, variant faqat nomi bilan farq qilardi |
| `promptOverride` | To'ldirilgan bo'lsa, ssenariyning `rolePrompt` i shu matn bilan almashtiriladi. Faqat shu qo'ng'iroq uchun — ssenariy yozuvi o'zgarmaydi, bosqichlar, faktlar, tool'lar va guardrail'lar tegilmaydi |
| `pipelineMode` | To'ldirilgan bo'lsa (`CASCADE` / `REALTIME` / `HALF_CASCADE`), qo'ng'iroq agentning o'z rejimi o'rniga shu pipeline'da ketadi. **Cascade va realtime'ni solishtirishning yagona adolatli yo'li** — bir ro'yxat, bir kunlar, bir xil taqsimot. `REALTIME` tanlangan, lekin deploy'da realtime engine yo'q bo'lsa, qo'ng'iroq cascade'da ketadi (logda WARN). Natijani solishtirish: shu hisobotdagi `answerRate`/`conversionRate`/`spentUzs` + [reports.md](reports.md) dagi `technical.avgTurnLatencyMs` va `rtpMos` |
| `trafficWeight` | Taqsimotdagi ulush. Yig'indisi 100 bo'lishi shart emas — nisbat muhim |
| `active` | `false` bo'lsa umuman tanlanmaydi |

**Taqsimot deterministik.** Variant `sha256(campaignId + ":" + telefon)` bo'yicha tanlanadi, tasodifiy emas. Sababi: kampaniya bir raqamga qayta qo'ng'iroq qiladi (javob bermadi, "ertaga qo'ng'iroq qiling" dedi), tasodifiy tanlovda esa mijoz dushanba yumshoq ssenariyni, seshanba qattiqroq ssenariyni eshitar edi — natija oxirgi qo'ng'iroq variantiga yozilib, test hech narsa o'lchamas edi. `campaignId` aralashtirilgani uchun bitta raqam har kampaniyada bir xil variantga tushib qolmaydi.

Kampaniyada faol variant bo'lmasa, qo'ng'iroq kampaniya agentining o'z senariysi va ovozi bilan ketadi.

### Hisoblagichlar qachon oshadi

| Hisoblagich | Qachon |
|---|---|
| `callsCount` | Nishon navbatga qo'yilganda (dialer) |
| `answeredCount` | Mijoz go'shakni ko'targanda va suhbat boshlanganda |
| `convertedCount` | Qo'ng'iroq yakunida disposition `PROMISE_TO_PAY`, `GOAL_REACHED` yoki `COMPLETED` bo'lsa. `TRANSFERRED` ataylab hisoblanmaydi — operatorga o'tkazish botning ishni oxiriga yetkaza olmagani, uni konversiya deb sanash eng tez taslim bo'ladigan ssenariyni g'olib qilardi |

- `POST /api/campaigns/{campaignId}/variants` — variant yaratish
- `GET /api/campaigns/{campaignId}/variants` — variantlar ro'yxati
- `GET /api/campaigns/{campaignId}/variants/{variantId}` — bitta variant
- `PUT /api/campaigns/{campaignId}/variants/{variantId}` — tahrirlash
- `DELETE /api/campaigns/{campaignId}/variants/{variantId}` — o'chirish
- `GET /api/campaigns/{campaignId}/variants/report` — A/B hisoboti
- `POST /api/campaigns/{campaignId}/variants/optimize` — Avtonom Multi-Armed Bandit (MAB) optimizatsiyasini ishga tushirish

### Variant tanasi (`CampaignVariantCreateRequest` / `...UpdateRequest`)

```json
{
  "name": "Yumshoq ohang",
  "aiAgentId": 9,
  "promptOverride": "Siz xushmuomala, shoshilmaydigan operatorsiz...",
  "ttsVoiceId": "zamira",
  "pipelineMode": "REALTIME",
  "trafficWeight": 50,
  "active": true
}
```

| Maydon | Turi | Majburiymi | Izoh |
|---|---|---|---|
| `name` | `string` | ✅ | Variant nomi, hisobotda shu ko'rinadi |
| `aiAgentId` | `number` | ❌ | Kampaniya agenti o'rniga ishlatiladigan agent |
| `promptOverride` | `string` | ❌ | Ssenariyning `rolePrompt` i o'rniga (faqat shu variant qo'ng'iroqlarida) |
| `ttsVoiceId` | `string` | ❌ | Variant ovozi |
| `pipelineMode` | `CASCADE` | `REALTIME` | `HALF_CASCADE` | `null` || `REALTIME` \| `null` | ❌ | Variant qo'ng'iroqlari qaysi pipeline'da ketadi; `null` — agentning o'zi |
| `trafficWeight` | `number` | ❌ | Taqsimotdagi ulush; berilmasa `50` |
| `active` | `boolean` | ❌ (`PUT` da) | `false` — variant umuman tanlanmaydi |

Javob (`CampaignVariantResponse`) — o'sha maydonlar plus `id`, `campaignId`, `companyId`, `callsCount`, `answeredCount`, `convertedCount`, `answerRate`, `conversionRate`, `attributedConversions`, `attributedValueUzs`, `spentUzs`, `costPerConversionUzs`, `createdAt`, `updatedAt`.

> ℹ️ **Ikki xil "konversiya".** `convertedCount`/`conversionRate` — bot yozgan
> disposition, ya'ni **va'da**. `attributedConversions`/`attributedValueUzs` — mijozning
> o'z tizimi keyinchalik xabar bergan **haqiqiy natija**
> ([conversions.md](conversions.md)). `spentUzs` — shu variant qo'ng'iroqlariga
> hisoblangan xarajat ([billing.md](billing.md) §6), `costPerConversionUzs` — ularning
> nisbati. Konversiya maqsadi sozlanmagan kompaniyada yangi ustunlar 0 bo'ladi.

> ⚠️ **Breaking change:** `agentId` maydoni so'rovdan ham, javobdan ham **olib tashlandi**. U hech qachon qo'ng'iroqqa ta'sir qilmasdi, faqat bazaga yozilardi; alohida "Agent" tushunchasi bilan birga o'chirildi ([scenarios.md](scenarios.md) → `agentProfile`).

### `GET /api/campaigns/{campaignId}/variants/report`

**Response** (`AbTestReportResponse`):
```json
{
  "accept": true,
  "data": {
    "campaignId": 12,
    "companyId": 3,
    "totalVariants": 2,
    "totalCalls": 1400,
    "totalAnswered": 1000,
    "totalConverted": 300,
    "overallConversionRate": 30.0,
    "conversionRateLow": 27.24,
    "conversionRateHigh": 32.9,
    "leadingVariantName": "Yumshoq ohang",
    "winningVariantName": null,
    "variants": []
  },
  "errors": null
}
```

| Maydon | Turi | Izoh |
|---|---|---|
| `overallConversionRate` | `number` | Kampaniya bo'yicha umumiy konversiya, foizda |
| `conversionRateLow` / `conversionRateHigh` | `number` | Shu konversiyaning 95% ishonch oralig'i (Wilson), foizda. Oraliq qanchalik keng bo'lsa, raqamga shunchalik kam ishonch bor |
| `leadingVariantName` | `string \| null` | Hozircha eng yuqori konversiyali variant — **shunchaki reyting**, xulosa emas |
| `winningVariantName` | `string \| null` | **Haqiqatan yutgan** variant. `null` — test hali yakunlanmagan. Yutuqchi faqat ikkala shart bajarilganda ko'rsatiladi: har bir taqqoslanayotgan variantda kamida **30 ta javob berilgan qo'ng'iroq**, va yetakchining ishonch oralig'i ikkinchi o'rindagining oralig'idan butunlay yuqorida |

> ⚠️ Frontend ssenariyni almashtirishni faqat `winningVariantName` bo'yicha taklif qilsin. `leadingVariantName` kampaniyaning birinchi kunidayoq to'ladi va u yerda hech qanday xulosa yo'q.
>
> ⚠️ `leadingVariantName` va `winningVariantName` hozircha **disposition** ustuni
> (`convertedCount`) bo'yicha hisoblanadi, `attributedConversions` bo'yicha emas. Ularni
> haqiqiy konversiyaga o'tkazish — ma'lumot to'plangandan keyin qabul qilinadigan qaror:
> hozir o'tkazilsa, konversiya maqsadi sozlamagan har bir kompaniyada g'olib doim `null`
> bo'lib qolardi.

---

### `POST /api/campaigns/{campaignId}/variants/optimize` — Avtonom Multi-Armed Bandit (MAB) optimizatori

A/B test davomida past natija ko'rsatayotgan ssenariylarga ortiqcha budjet sarflamaslik va eng ko'p konversiya berayotgan variantga ko'proq mijozlarni avtomatik yo'naltirish uchun dinamik optimallashtirish.

Ushbu operatsiya backend tomonidan har bir konversiya qayd etilganda va har 10 ta javob olingan milestone'larda avtomatik chaqiriladi, shuningdek, istalgan vaqtda ushbu endpoint orqali qo'lda ishga tushirilishi ham mumkin.

**Optimizatsiya strategiyalari:**
1. `COLD_START_EQUAL`: Jami javoblar soni 20 taga yetmaguncha yoki biror faol variantda kamida 5 ta javob bo'lmaguncha, barcha faol variantlar teng ulush (50/50 yoki 33/33/34) oladi.
2. `ADAPTIVE_BANDIT`: Yetarli ma'lumot to'planganda, lekin Wilson 95% ishonch oralig'i hali to'liq ajralmaganda, Bayesian Laplace-tekislangan konversiya `(converted + 1) / (answered + 2)` bo'yicha trafik taqsimlanadi. Har bir variantga kamida 10% doimiy o'rganish (exploration floor) kafolatlanadi.
3. `STATISTICAL_WINNER`: Wilson 95% ishonch oralig'i to'liq ustun bo'lgan g'olib aniqlanganda, g'olib variantga 80–90% trafik beriladi, qolgan variantlarga 10% eksploratsiya kafolati qoldiriladi.

**Response** (`VariantOptimizationPlan`):

```json
{
  "accept": true,
  "data": {
    "campaignId": 12,
    "companyId": 3,
    "strategy": "ADAPTIVE_BANDIT",
    "winningVariantId": null,
    "winningVariantName": null,
    "allocations": [
      {
        "variantId": 101,
        "variantName": "Yumshoq ohang",
        "previousTrafficWeight": 50,
        "optimizedTrafficWeight": 73,
        "answeredCount": 25,
        "convertedCount": 10,
        "conversionRate": 40.0,
        "lowerBound": 23.41,
        "upperBound": 59.27
      },
      {
        "variantId": 102,
        "variantName": "Qat'iy ohang",
        "previousTrafficWeight": 50,
        "optimizedTrafficWeight": 27,
        "answeredCount": 25,
        "convertedCount": 2,
        "conversionRate": 8.0,
        "lowerBound": 2.24,
        "upperBound": 24.97
      }
    ],
    "optimizedAt": "2026-10-27T16:50:00Z"
  },
  "errors": null
}
```
