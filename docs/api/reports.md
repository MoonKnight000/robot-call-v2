# Hisobotlar API

`uz.murodjon.uysotvoice.report` (+ `uz.murodjon.uysotvoice.audit`) · rol:
**endpointga qarab har xil — pastdagi jadvalga qarang**

Kampaniyalar nima qildi — qo'ng'iroq bo'yicha. Dashboard KPI/grafiklari, to'liq
qo'ng'iroqlar tarixi, bitta qo'ng'iroq tafsiloti + yozuv (recording), va audit
jurnali.

Umumiy javob shakli, xatolar va pagination konventsiyasi uchun
[README.md](README.md)ga qarang.

> **Muhim, sezilmaydigan holat:** "faqat o'qish" (`VIEWER`) roli
> `SecurityConfig`da faqat **`GET`** metodlari uchun `/api/reports/**`ga
> ochilgan. Ro'yxat endpointlari (`POST /api/reports/calls/list`,
> `POST /api/reports/campaigns/{id}/calls/list`,
> `POST /api/reports/audit/list`) **`POST` bo'lgani uchun bu qoidaga tushmaydi**
> va standart bo'yicha **ADMIN** talab qiladi — "hisobot o'qish uchun
> `read-api-key` yetarli" degan taxmin bu uchta endpoint uchun **noto'g'ri**.
> Har bir endpoint qatorida rol aniq ko'rsatilgan.

| Endpoint | Metod | Rol |
|---|---|---|
| `/api/reports/campaigns/{id}` | GET | VIEWER |
| `/api/reports/campaigns/{id}/calls/list` | POST | **ADMIN** |
| `/api/reports/calls/list` | POST | **ADMIN** |
| `/api/reports/calls/{callId}` | GET | VIEWER |
| `/api/reports/calls/{callId}/recording` | GET | VIEWER |
| `/api/reports/audit/list` | POST | **ADMIN** |
| `/api/reports/dashboard/kpi` | GET | VIEWER |
| `/api/reports/dashboard/timeseries` | GET | VIEWER |
| `/api/reports/dashboard/outcomes` | GET | VIEWER |

---

## `GET /api/reports/campaigns/{id}` — kampaniya statistikasi

**Response** (`CampaignStats`):

```json
{
  "campaignId": 42,
  "name": "Iyul qarzdorlik",
  "status": "ACTIVE",
  "targetsByStatus": { "PENDING": 120, "DONE": 98, "IN_PROGRESS": 5 },
  "dispositions": { "PROMISE_TO_PAY": 40, "NO_ANSWER": 30, "REFUSED": 10 },
  "answeredCalls": 78,
  "promiseRate": 0.51,
  "avgDurationSec": 94.3,
  "escalatedCalls": 3
}
```

| Maydon | Izoh |
|---|---|
| `targetsByStatus` | nishonlar soni har bir lifecycle holati bo'yicha |
| `dispositions` | tugagan urinishlar soni natija (disposition) bo'yicha |
| `promiseRate` | tugagan urinishlarning necha qismi `PROMISE_TO_PAY` bilan tugagani, `0..1` (kampaniya shu ko'rsatkich bo'yicha baholanadi) |
| `avgDurationSec` | tugagan qo'ng'iroqlarning o'rtacha davomiyligi; hisoblaydigan narsa bo'lmasa `null` |
| `escalatedCalls` | odam operatoriga uzatilgan qo'ng'iroqlar soni |

---

## `POST /api/reports/campaigns/{id}/calls/list` — bitta kampaniyaning qo'ng'iroqlari

**Rol: ADMIN** (yuqoridagi eslatmaga qarang). Body — `CallFilter`
([README §3](README.md#3-royxatfiltr-endpointlari-pagination)). Saralanadigan
ustunlar: `CALL_ID`, `STARTED_AT`, `ENDED_AT`, `DURATION_SEC`, `DISPOSITION`.
Standart: `CALL_ID DESC` (yangisi birinchi). Javob qatori (`CallRow`) — pastga
qarang.

---

## `POST /api/reports/calls/list` — barcha kampaniyalar bo'yicha qo'ng'iroqlar

**Rol: ADMIN.** Xuddi yuqoridagi kabi, faqat kampaniya bilan cheklanmagan —
"nima bo'ldi" umumiy jadvali uchun.

**Javob qatori** (`CallRow`, `PageableData<CallRow>` ichida):

```json
{
  "callId": 501,
  "targetId": 501,
  "phone": "998901234567",
  "language": "uz-UZ",
  "startedAt": "2026-07-31T10:15:00Z",
  "endedAt": "2026-07-31T10:16:34Z",
  "durationSec": 94,
  "disposition": "PROMISE_TO_PAY",
  "hangupCause": "NORMAL_CLEARING",
  "hasRecording": true,
  "summary": "Mijoz 5 avgustgacha 1 500 000 so'm to'lashga va'da berdi.",
  "promisedDate": "2026-08-05",
  "promisedAmount": 1500000,
  "crmNoteId": 98213
}
```

| Maydon | Izoh |
|---|---|
| `callId` | `call_attempt` id — tafsilot va recording endpointlari shuni oladi |
| `endedAt`/`durationSec` | qo'ng'iroq hali davom etayotgan bo'lsa `null` |
| `hangupCause` | Asterisk sabab kodi — `NO_ANSWER` nima uchun sodir bo'lganini tushuntiradi |
| `hasRecording` | shu qo'ng'iroq uchun audio olish mumkinmi |
| `summary` | CRM eslatma matni (LLM tomonidan yaratilgan), hali yaratilmagan bo'lsa `null` |
| `crmNoteId` | CRM bergan id; `null` — hali CRM tomonidan qabul qilinmagan (outbox hali qayta urinmoqda yoki CRM o'chiq) |

---

## `GET /api/reports/calls/{callId}` — bitta qo'ng'iroq + to'liq transkript

**Response** (`CallDetail`):

```json
{
  "call": { /* CallRow, yuqoriga qarang */ },
  "transcript": [
    { "seq": 1, "role": "AGENT", "text": "Assalomu alaykum...", "dialogState": "GREETING", "tsOffsetMs": 0, "confidence": null },
    { "seq": 2, "role": "CLIENT", "text": "Ha, tinglayapman", "dialogState": null, "tsOffsetMs": 3200, "confidence": 0.94 }
  ],
  "reasonCode": "TEMPORARY_HARDSHIP",
  "sentiment": "NEUTRAL",
  "needsFollowUp": false,
  "followUpNote": null,
  "escalated": false,
  "errorMessage": null
}
```

| Maydon | Izoh |
|---|---|
| `transcript[].role` | `AGENT` yoki `CLIENT` |
| `transcript[].dialogState` | qatordagi FSM holati; mijoz qatorlari uchun `null` |
| `transcript[].confidence` | tanish ishonchi (faqat mijoz qatorlarida); past qiymat g'alati transkriptning tushuntirishi bo'lishi mumkin |
| `reasonCode` | nega qarz to'lanmagani, yig'ma xulosa tomonidan tasniflangan |
| `sentiment` | mijozning kayfiyati qanday chiqqani |
| `needsFollowUp`/`followUpNote` | xulosa follow-up belgilaganmi va nima haqida |
| `escalated` | qo'ng'iroq odamga uzatilganmi |
| `errorMessage` | urinishda qayd etilgan texnik xato, bo'lsa |

---

## `GET /api/reports/calls/{callId}/recording` — qo'ng'iroq yozuvi

**`ResponseData`ga o'ralmagan** — xom audio qaytaradi (`ResponseEntity<Resource>`).
Faylni to'g'ridan-to'g'ri serverdan beradi (mahalliy diskda bo'lsa) yoki
object storage'ga redirect qiladi (`RecordingRedirect`) — ikkalasi ham
frontend uchun bir xil ko'rinadi: `<audio src="/api/reports/calls/{id}/recording">`
kabi to'g'ridan-to'g'ri ishlaydi, redirectni brauzer o'zi bosib o'tadi.

Yozuv yo'q bo'lsa (`hasRecording: false` bo'lgan qo'ng'iroq) — `404`.

---

## `POST /api/reports/audit/list` — audit jurnali {#audit-log}

**Rol: ADMIN** (yuqoridagi eslatmaga qarang). Body — `AuditFilter`. Saralanadigan
ustunlar: `ID`, `CREATED_AT`, `ACTION`, `ENTITY`. Standart: `ID DESC` (eng
so'nggisi birinchi).

**Javob qatori** (`AuditRow`, `PageableData<AuditRow>` ichida):

```json
{
  "id": 981,
  "actor": "api-key",
  "action": "CAMPAIGN_START",
  "entity": "campaign",
  "entityId": "42",
  "detail": "maxConcurrentCalls=5",
  "createdAt": "2026-07-31T09:00:00Z"
}
```

| Maydon | Izoh |
|---|---|
| `actor` | so'rovni qilgan autentifikatsiyalangan principal (`api-key`/`read-api-key`), yoki dilerning o'z rejalashtirilgan ishi uchun `system` |
| `action` | qisqa kod, masalan `CAMPAIGN_START`, `DNC_REMOVE` |
| `entity`/`entityId` | nima ustida amal bajarilgani (`campaign`, `target`, `call`) va uning id'si (matn sifatida) |
| `detail` | erkin matnli qo'shimcha kontekst |

---

## Dashboard endpointlari (`GET /api/reports/dashboard/...`)

Uchalasi ham bir xil ikkita ixtiyoriy query parametrni qabul qiladi:

| Parametr | Turi | Izoh |
|---|---|---|
| `from` | ISO-8601 instant | berilmasa `to` minus 7 kun |
| `to` | ISO-8601 instant | berilmasa hozir |
| `campaignId` | long, ixtiyoriy | berilsa faqat shu kampaniya bo'yicha filtrlaydi |

`from` >= `to` yoki oraliq 366 kundan katta bo'lsa — `400`.

### `GET /api/reports/dashboard/kpi` — 4 ta KPI kartochka

**Response** (`DashboardKpi`):

```json
{
  "totalCalls": { "value": 342, "changePct": 12.4, "sparkline": [40, 55, 38, 61, 70, 48, 30] },
  "answeredRate": { "value": 0.78, "changePct": -3.1, "sparkline": [0.8, 0.75, 0.79, "..."] },
  "avgDurationSec": { "value": 94.3, "changePct": null, "sparkline": [90, 95, 94, "..."] },
  "promises": { "value": 40, "changePct": 25.0, "sparkline": [5, 6, 4, "..."] }
}
```

Har bir maydon `DashboardMetric`: `value` (joriy qiymat), `changePct` (oldingi
teng uzunlikdagi davrga nisbatan foiz o'zgarish — oldingi davr `0` bo'lsa va
joriy ham `0` bo'lsa `0.0`, aks holda `null`, chunki o'zgarish aniqlanmagan),
`sparkline` (bucket'langan qiymatlar ketma-ketligi, granularity pastga qarang).

### `GET /api/reports/dashboard/timeseries` — "Qo'ng'iroqlar dinamikasi" stacked-area

**Response** — `List<DashboardBucket>`:

```json
[
  { "bucketStart": "2026-07-31T00:00:00Z", "total": 50, "answered": 38, "noAnswer": 10, "error": 2, "avgDurationSec": 92.1, "promises": 15 }
]
```

Bucket kattaligi oraliqqa qarab avtomatik: ≤2 kun → soat, ≤62 kun → kun,
undan katta → hafta.

### `GET /api/reports/dashboard/outcomes` — "Natijalar taqsimoti"

**Response** — `List<DashboardOutcome>`, ko'pdan kamga saralangan, bucket'siz
(butun davr uchun bitta agregatsiya):

```json
[ { "disposition": "PROMISE_TO_PAY", "count": 120 }, { "disposition": "NO_ANSWER", "count": 80 } ]
```
