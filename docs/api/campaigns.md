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
  "dialWindowStart": "09:00:00",
  "dialWindowEnd": "18:00:00",
  "dialDays": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"],
  "maxAttempts": 3,
  "retryIntervalMinutes": 0,
  "maxConcurrentCalls": 20,
  "dailyCallCap": 0,
  "recurrenceType": "CRON",
  "recurringDayOfMonth": null,
  "cronExpression": "0 0 9 ? * MON-FRI",
  "autoResetTargets": false
}
```

| Maydon | Turi | Majburiymi | Izoh |
|---|---|---|---|
| `name` | `string` | ✅ (`@NotBlank`) | Kampaniya nomi |
| `type` | `CampaignType` | ✅ (`@NotNull`) | `DEBT_COLLECTION` yoki `SURVEY`. **Majburiy** — berilmasa `400` (avval jimgina `DEBT_COLLECTION` ga tushardi) |
| `dialMode` | `DialMode` | ❌ | Mijoz bilan kim gaplashadi: `AI` (standart) — kampaniya agenti; `PREVIEW` — operator mijoz kartasini ko'rib o'zi teradi; `PROGRESSIVE` — har bo'sh operatorga bitta qo'ng'iroq; `PREDICTIVE` — bo'sh operatorlardan ko'proq qo'ng'iroq, operator yetmasa mijozni AI agent qabul qiladi. **Yaratilgandan keyin o'zgarmaydi.** `AI` kampaniyalarni dialer teradi, `PREVIEW` ni operatorlar ([preview-dialing.md](preview-dialing.md)), `PROGRESSIVE` ni dialer navbatning bo'sh operatorlari uchun ([pastda](#progressive-kampaniya-qanday-teriladi)); `PREDICTIVE` ni dialer javob ulushiga qarab oldindan ([pastda](#predictive-kampaniya-qanday-teriladi)) |
| `aiAgentId` | `number` | `AI` da ✅ | [`GET /api/ai-agents`](ai-agents.md) dagi agent. **Senariy, ovoz, persona, LLM model va SIP trunklar shu agentdan olinadi.** Keyin `PUT` bilan boshqa agentga o'tkazsa bo'ladi. `dialMode=AI` da berilmasa — `400 CAMPAIGN_AI_AGENT_REQUIRED`; operator rejimlarida ixtiyoriy |
| `callQueueId` | `number` | `AI` dan boshqasida ✅ | Qo'ng'iroqlarni oladigan operatorlar navbati ([call-queues.md](call-queues.md)). `dialMode` `AI` bo'lmasa va berilmasa — `400 CAMPAIGN_CALL_QUEUE_REQUIRED`; boshqa kompaniyaning yoki mavjud bo'lmagan navbat — `404 CALL_QUEUE_NOT_FOUND`. Navbat o'chirilsa, maydon `null` bo'lib qoladi |
| `dialWindowStart` / `dialWindowEnd` | `string` (`HH:mm:ss`) | ❌ | Kunlik qo'ng'iroq oynasi. Kompaniyaning `company_config` oynasidan **tashqariga chiqa olmaydi** ([companies.md](companies.md)). Shuningdek, `DialerService` har bir qabul qiluvchining mahalliy vaqt zonasini (quiet hours / TCPA: 08:00–20:00) hisobga oladi — uning mahalliy vaqtida tun bo'lsa, nishon avtomatik +30 daqiqaga kechiktiriladi |
| `dialDays` | `array<DayOfWeek>` | ❌ | `["MONDAY", ...]`. Bo'sh/berilmasa — barcha kunlar |
| `maxAttempts` | `number` | ❌ | Bitta nishonga urinishlar soni |
| `retryIntervalMinutes` | `number` | ❌ | Javob bo'lmagan nishonni qayta terishgacha daqiqa. `0` — disposition bo'yicha standartlar (avtootvetchikdan keyin uzoqroq, bandlikdan keyin qisqaroq) |
| `maxConcurrentCalls` | `number` | ❌ | Parallel kanallar chegarasi |
| `dailyCallCap` | `number` | ❌ | Kunlik qo'ng'iroq chegarasi; `0` — cheksiz. Sarf shifti: har bir qo'ng'iroq STT/LLM/TTS va trunk daqiqasini yeydi |
| `recurrenceType` | `RecurrenceType` | ❌ | `ONCE` (standart), `DAILY`, `WEEKLY`, `MONTHLY`, `CRON` |
| `recurringDayOfMonth` | `number` (1–31) | ❌ | `MONTHLY` uchun oyning kuni |
| `cronExpression` | `string` | ❌ | `CRON` uchun Spring/Quartz cron ifodasi |
| `autoResetTargets` | `boolean` | ❌ | Takror ishga tushganda nishonlar `PENDING` ga qaytariladimi. Standart `false` |
| `priority` | `number` (1–5) | ❌ | Dialer har tickdagi batch'ni faol kampaniyalar orasida ustuvorlikka **proporsional** bo'ladi: 5 dagi kampaniya 1 dagiga nisbatan 5 barobar ko'p liniya oladi; har biri kamida 1 ta. Standart `3` |
| `retryPolicy` | `object` | ❌ | Disposition bo'yicha retry qoidalari: `{"NO_ANSWER": {"maxAttempts": 3, "delayMinutes": 180}, "VOICEMAIL": {"maxAttempts": 1, "delayMinutes": 120}}`. `delayMinutes` — shu natijadan keyingi kutish (`retryIntervalMinutes` va standartlardan ustun); `maxAttempts` — oxirgi natija shu bo'lsa, shuncha urinishdan keyin nishon `EXHAUSTED` (kampaniya `maxAttempts`idan oshmaydi). Qoida bo'lmagan disposition uchun `retryIntervalMinutes`, u `0` bo'lsa standartlar (`NO_ANSWER` 180 min, `FAILED` 15 min, `VOICEMAIL` 120 min, `CALLBACK_REQUESTED` 60 min) |
| `startAt` | `string` (ISO-8601, UTC) | ❌ | Rejalashtirilgan boshlanish. `start` shu vaqtdan **oldin** bosilsa kampaniya `SCHEDULED` bo'lib turadi va daqiqalik sweep vaqti kelganda `ACTIVE` qiladi (`startAt` o'tib ketgan yoki `null` bo'lsa — darhol `ACTIVE`). `start?immediate=true` rejani chetlab hozir boshlaydi |
| `endAt` | `string` (ISO-8601, UTC) | ❌ | Rejalashtirilgan tugash: `ACTIVE` kampaniya shu vaqtda nishonlar qolgan bo'lsa ham `COMPLETED` qilinadi (`CAMPAIGN_FINISHED` bildirishnomasi). `endAt` `startAt`dan keyin bo'lishi shart — `400 CAMPAIGN_SCHEDULE_INVALID`; o'tib ketgan `endAt` bilan `start` — `409 CAMPAIGN_END_PASSED` |
| `budgetUzs` | `number` | ❌ | Butun kampaniya uchun xarajat tavani (so'm). Yakunlangan (`SETTLED`) qo'ng'iroqlar summasi shunga yetganda dialer kampaniyani `PAUSED` qiladi va `CAMPAIGN_PAUSED` bildirishnomasi (`BUDGET_CAP`) yuboradi; davom ettirish — `start`. Tekshiruv daqiqada bir marta, shuning uchun bitta tick'ning batch'i qadar oshib ketishi mumkin |
| `dailyBudgetUzs` | `number` | ❌ | Xuddi shu, lekin kompaniya vaqt zonasidagi bir kun uchun (yarim tundan boshlab) |
| `maxAiOverflowPercent` | `number` (0–100) | ❌ | Faqat `PREDICTIVE`: so'nggi 30 daqiqada javob berilgan qo'ng'iroqlarning necha foizini AI agent olishi mumkin. Shunga yetganda dialer har bo'sh operatorga bittadan teradi (PROGRESSIVE tezligi); 75% iga yetganda oldindan terish yarimga tushadi. `0` — hech qachon oldindan termaydi. Standart `20`. `PUT` da berilmasa — avvalgi qiymat qoladi |
| `targetOrdering` | `string` | ❌ | Kutayotgan nishonlardan qaysilari birinchi teriladi: `FIFO` (standart — navbatga eng oldin tushgani) yoki `SCORE` (to'lash ehtimoli yuqorisi birinchi, raqam odatda gaplashadigan soatda bonus bilan) — [SCORE tartibi](#score-tartibi--nishonlar-qaysi-tartibda-teriladi). `AI` va `PREDICTIVE` kampaniyalarda ishlaydi. `PUT` da berilmasa — avvalgi qiymat qoladi |

> ⚠️ **Muhim o'zgarish.** Kampaniya endi faqat *kimga va qachon* qo'ng'iroq qilishni
> ushlaydi. `scenarioId`, `defaultLanguage`, `ttsVoice`, `languageVoices`, `agentPersona`,
> `ambientSound`, `emotionAdaptiveVoice`, `dtmfInputEnabled`, `voicemailAction`,
> `voicemailMessage`, `midCallSmsEnabled`, `midCallSmsTemplate`, `sipTrunkIds` maydonlari
> kampaniyadan olib tashlandi va [AI agent](ai-agents.md)ga ko'chdi. Ular hozir
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

Body (`UpdateCampaignRequest`) — `CreateCampaignRequest` bilan bir xil, faqat **`type` va
`dialMode` yo'q** (ular yaratilgandan keyin o'zgarmaydi). `aiAgentId` va `callQueueId` esa
o'zgartirilishi mumkin: kampaniyani boshqa ovoz yoki boshqa skript bilan yuritish uchun uni
klonlash shart emas. Ular kampaniyaning `dialMode`iga qarab yaratishdagidek tekshiriladi.

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
    "dialWindowStart": "09:00:00",
    "dialWindowEnd": "18:00:00",
    "dialDays": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"],
    "maxAttempts": 3,
    "retryIntervalMinutes": 0,
    "maxConcurrentCalls": 20,
    "dailyCallCap": 0,
    "aiAgentId": 7,
    "aiAgentName": "Qarz undirish — o'zbekcha",
    "companyId": 1,
    "createdBy": 3,
    "createdByName": "Aziz Bekmurodov",
    "priority": 3,
    "retryPolicy": { "VOICEMAIL": { "maxAttempts": 1, "delayMinutes": 120 } },
    "startAt": "2026-09-15T04:00:00Z",
    "endAt": null,
    "budgetUzs": 5000000,
    "dailyBudgetUzs": null,
    "recurrenceType": "CRON",
    "recurringDayOfMonth": null,
    "cronExpression": "0 0 9 ? * MON-FRI",
    "autoResetTargets": false,
    "lastRunAt": "2026-03-05T09:00:00Z",
    "totalTargets": 1200,
    "calledTargets": 750,
    "pendingTargets": 450,
    "completedTargets": 700,
    "version": 4,
    "dialMode": "AI",
    "callQueueId": null,
    "maxAiOverflowPercent": 20,
    "targetOrdering": "FIFO"
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

### SCORE tartibi — nishonlar qaysi tartibda teriladi

`targetOrdering: "SCORE"` bo'lgan `ACTIVE` kampaniyaning kutayotgan (`PENDING`) nishonlari har 10 daqiqada
baholanadi (bitta yurishda kampaniya uchun 20 000 tagacha; avval hech baholanmaganlar, keyin 12 soatdan
oldin baholanganlar). Ball kompaniyaning shu raqam bilan **so'nggi 180 kunlik** tarixidan, qoida bo'yicha:

| Omil | Ta'siri |
|---|---|
| Tarix yo'q | `50` (neytral) |
| Gaplashish ulushi — `(gaplashilgan + 1) ÷ (terilgan + 2)` | ±20 (50% atrofida) |
| Har `REFUSED` qo'ng'iroq | −5, ko'pi bilan −15 |
| Har bajarilgan (`KEPT`) to'lov va'dasi | +15, ko'pi bilan +30 |
| Har buzilgan (`BROKEN`) va'da | −15, ko'pi bilan −30 |

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
`dailyCallCap`), so'ng undirishni [conversion](conversions.md) va [to'lov va'dalari](payment-promises.md)
hisobotlarida kampaniya bo'yicha solishtiring.

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
tushadi.

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
  "bestCallHour": 10
}
```

`status` (`TargetStatus`): `PENDING`, `IN_PROGRESS`, `DONE`, `FAILED`, `EXHAUSTED`
(urinishlar limiti tugadi). `contextData` — **string** ichidagi JSON, obyekt emas.
`score` (0–100) va `bestCallHour` (0–23) — `SCORE` kampaniyada nishon baholangandan keyin to'ladi
([SCORE tartibi](#score-tartibi--nishonlar-qaysi-tartibda-teriladi)), aks holda `null`.

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
| `provider` | ❌ | `GENERIC` (standart) yoki `CHAINED`. Pastdagi bo'limga qarang |
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

#### `UYSOT_DEBTORS`

Uysot'da qarzdorning telefoniga yetish uchun uchta o'qish kerak:
`POST /v1/open-api/contract/filter` (qaysi shartnomalar `delay` kun kechikkan) →
`GET /v1/open-api/contract/{id}` (shartnomaning `lead` i) →
`GET /v1/open-api/lead/{leadId}` (kontakt telefonlari). Nishonning `client_id` si — Uysot
`leadId` si, suhbatdan keyingi izoh ham o'shanga yoziladi.

Yuqoridagi `CHAINED` misoli aynan shu zanjirni ifodalaydi. `UYSOT_DEBTORS` alohida qolgani
sababi bitta: u tokenni Integratsiyalar bo'limidagi **Uysot OAuth ulanishidan** oladi,
formaga qo'lda yozilgan headerdan emas.

Nechta qarzdor olinishi `voice-agent.crm.debtor-import.*` bilan cheklanadi
(`min-delay-days`, `max-debtors`, `page-size`, `max-pages`) — Open API daqiqasiga 60 so'rovga
ruxsat beradi, bitta qarzdor esa uchta so'rovga tushadi.

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
