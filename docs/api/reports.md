# Hisobotlar va Tahlillar (Reports & Analytics) API

`uz.murodjon.robotcallv2.report` · huquq: **REPORT_READ** / **REPORT_EDIT** (bulk); dashboard — **DASHBOARD_READ**, audit — **AUDIT_READ**

Qo'ng'iroqlar tarixi, to'liq transkriptlar, audio yozuvlar, dashboard KPI ko'rsatkichlari,
dinamika, soatlik issiqlik xaritasi (Heatmap), kampaniyalarni taqqoslash, voronka, audit
jurnallari va avtomatlashtirilgan davriy hisobotlar.

Umumiy javob shakli, xatolar va pagination konventsiyasi uchun
[README.md](README.md)ga qarang.

---

## 1. Qo'ng'iroqlar ro'yxati va tafsilotlari

### `POST /api/reports/calls/list` — Barcha qo'ng'iroqlar ro'yxati

**Request Body** (`CallFilter`):

```json
{
  "page": 0,
  "size": 20,
  "orders": { "STARTED_AT": "DESC" },
  "q": "998901234567",
  "campaignId": 12,
  "disposition": "PROMISE_TO_PAY",
  "dateFrom": "2026-09-01T00:00:00Z",
  "dateTo": "2026-09-05T23:59:59Z",
  "durationMinSec": 10,
  "durationMaxSec": 600,
  "ids": null
}
```

| Maydon | Turi | Izoh |
|---|---|---|
| `page` / `size` | number | Standart `0` / `20`, maksimum `size` — `500` |
| `orders` | map | Saralanadigan ustunlar: `CALL_ID`, `STARTED_AT`, `ENDED_AT`, `DURATION_SEC`, `DISPOSITION`. Standart: `CALL_ID DESC` |
| `q` | string | Telefon raqami bo'yicha erkin qidiruv. **`search` emas** |
| `campaignId` | number | Kampaniya bo'yicha cheklash |
| `disposition` | `Disposition` | Yakuniy natija bo'yicha filtr ([calls.md](calls.md#-qongiroq-yakuniy-natijalari-dispositions)) |
| `dateFrom` / `dateTo` | ISO instant | Qo'ng'iroq boshlanish vaqti oralig'i. **`from`/`to` emas** |
| `durationMinSec` / `durationMaxSec` | number | Suhbat davomiyligi oralig'i (soniya) |
| `ids` | `array<number>` | Aniq qo'ng'iroq id'lari (bulk oqimida tanlanganlarni qayta o'qish uchun) |

> ⚠️ `CallFilter` da `sentiment` filtri **yo'q**.

**Response** (`PageableData<CallRow>`):

```json
{
  "accept": true,
  "data": {
    "totalPages": 1,
    "currentPage": 0,
    "totalElements": 1,
    "data": [
      {
        "callId": 1052,
        "targetId": 501,
        "phone": "+998901234567",
        "language": "uz-UZ",
        "startedAt": "2026-09-05T10:15:00Z",
        "endedAt": "2026-09-05T10:17:22Z",
        "durationSec": 142,
        "disposition": "PROMISE_TO_PAY",
        "hangupCause": "16",
        "hasRecording": true,
        "summary": "Mijoz qarzini tan oldi va 10-sentabrda to'lashga va'da berdi.",
        "promisedDate": "2026-09-10",
        "promisedAmount": 1500000.00,
        "crmNoteId": 88123,
        "clientName": "Sardor Alimov",
        "campaignName": "Kechikkan to'lovlar - Mart 2026",
        "operatorName": null
      }
    ]
  },
  "message": null,
  "messageCode": null,
  "errors": null
}
```

| Maydon | Izoh |
|---|---|
| `callId` | `call_attempt.id` — bu qiymat `GET /api/reports/calls/{callId}` ga beriladi |
| `targetId` | Kampaniya nishoni id'si |
| `durationSec` | Ulanmagan urinishda `null` |
| `hangupCause` | Q.850 sababi. Skyline'da `18` — tarmoq nosozligi, `NO_ANSWER` emas |
| `hasRecording` | `true` bo'lsa `GET /api/reports/calls/{callId}/recording` ishlaydi |
| `operatorName` | Qo'ng'iroq operatorga uzatilgan bo'lsa — qabul qilgan xodim |

---

### `POST /api/reports/campaigns/{id}/calls/list` — Bitta kampaniya qo'ng'iroqlari

So'rov shakli yuqoridagi `CallFilter` bilan bir xil; `campaignId` path'dan olinadi.

---

### `GET /api/reports/campaigns/{id}` — Kampaniya statistikasi

**Response** (`CampaignStats`):

```json
{
  "accept": true,
  "data": {
    "campaignId": 12,
    "name": "Kechikkan to'lovlar - Mart 2026",
    "status": "ACTIVE",
    "targetsByStatus": { "PENDING": 450, "DONE": 700, "EXHAUSTED": 50 },
    "dispositions": { "PROMISE_TO_PAY": 300, "NO_ANSWER": 220, "REFUSED": 90 },
    "answeredCalls": 1000,
    "promiseRate": 0.3,
    "avgDurationSec": 118.4,
    "escalatedCalls": 12
  },
  "errors": null
}
```

---

### `GET /api/reports/calls/{callId}` — Bitta qo'ng'iroq + to'liq transkript

**Response** (`CallDetail`):

```json
{
  "accept": true,
  "data": {
    "call": { /* CallRow — yuqoridagi shakl */ },
    "transcript": [
      { "seq": 1, "role": "AGENT", "text": "Assalomu alaykum Sardor aka!...", "dialogState": "GREETING", "tsOffsetMs": 0, "confidence": null },
      { "seq": 2, "role": "CLIENT", "text": "Assalomu alaykum, eshitaman", "dialogState": null, "tsOffsetMs": 3200, "confidence": 0.96 }
    ],
    "reasonCode": "NO_MONEY",
    "sentiment": "POSITIVE",
    "needsFollowUp": false,
    "followUpNote": null,
    "escalated": false,
    "errorMessage": null,
    "technical": {
      "channelName": "PJSIP/trunk-00000012",
      "trunk": "trunk-uztelecom",
      "amdResult": "HUMAN",
      "sttProvider": "yandex",
      "ttsProvider": "yandex",
      "ttsVoice": "dilnavoz",
      "llmModel": "gemini-2.5-flash",
      "promptTokens": 1840,
      "completionTokens": 320,
      "cachedTokens": 1200,
      "turnCount": 6,
      "avgTurnLatencyMs": 780,
      "maxTurnLatencyMs": 1120,
      "avgLlmLatencyMs": 410,
      "maxLlmLatencyMs": 650
    }
  },
  "message": null,
  "messageCode": null,
  "errors": null
}
```

| Maydon | Turi | Izoh |
|---|---|---|
| `reasonCode` | enum \| null | To'lanmaslik sababi: `NO_MONEY`, `JOB_LOSS`, `ILLNESS`, `ALREADY_PAID`, `DISPUTES_DEBT`, `FORGOT`, `TECHNICAL_ISSUE`, `OTHER` |
| `sentiment` | enum \| null | `POSITIVE`, `NEUTRAL`, `NEGATIVE`, `HOSTILE` |
| `needsFollowUp` / `followUpNote` | boolean / string | AI keyingi qadam kerakligini belgilaganmi |
| `escalated` | boolean | Qo'ng'iroq operatorga eskalatsiya qilinganmi |
| `errorMessage` | string \| null | Texnik nosozlik matni |
| `transcript[].role` | string | `"AGENT"` yoki `"CLIENT"` |
| `transcript[].tsOffsetMs` | number | Qo'ng'iroq boshidan millisekund — audio pleyerni shu qatordan boshlash uchun |
| `transcript[].confidence` | float \| null | STT ishonchi; `AGENT` qatorlarida `null` |

> ⚠️ `qaScore`, `commitmentScore` va `callbackAt` **javobda yo'q**. `qaScore`/
> `commitmentScore` LLM tomonidan hisoblanadi (`CallSummary`), lekin `call_result`
> jadvalida ustuni yo'q va API orqali qaytarilmaydi. Qayta qo'ng'iroq vaqti
> `CALLBACK_REQUESTED` disposition'i va nishonning `nextAttemptAt` i orqali yuradi.

---

### `GET /api/reports/calls/{callId}/recording` — Qo'ng'iroq audio yozuvi

Xom WAV oqimi (`ResponseEntity<Resource>`), `ResponseData` konvertisiz. `Range:
bytes=<start>-<end>` to'liq qo'llab-quvvatlanadi.

* **Content-Type**: `audio/wav` · **Content-Disposition**: `inline`
* **Accept-Ranges**: `bytes` · **Status**: `200` yoki `206 Partial Content`

Fayl `/api/files/{id}` ga redirect qilinmaydi — redirect pleyerdan sarlavhalarni yo'qotib,
yozuvni ijro etib bo'lmaydigan qilardi.

---

### `GET /api/reports/calls/{callId}/transcript.txt` — Transkriptni matn ko'rinishida

`text/plain` fayl, `ResponseData` konvertisiz.

---

### `POST /api/reports/calls/export` — Qo'ng'iroqlarni CSV eksport qilish

Body — `CallFilter` (yuqoridagi shakl). Javob — **`calls.csv`** fayli (`byte[]`,
`ResponseData` konvertisiz). Format tanlanmaydi: bu endpoint faqat CSV qaytaradi;
PDF/XLSX kerak bo'lsa `GET /api/reports/export` ishlatiladi.

---

### `POST /api/reports/calls/bulk` — Ommaviy amallar

Huquq: **REPORT_EDIT**.

**Request Body** (`BulkCallActionRequest`):

```json
{ "action": "retry", "ids": [1051, 1052, 1053] }
```

| Maydon | Majburiymi | Izoh |
|---|---|---|
| `action` | ✅ (`@NotBlank`) | **kichik harfda**: `"retry"` — nishonni qayta terish navbatiga qo'yish; `"dnc"` — raqamni DNC ro'yxatiga qo'shish. Boshqasi — `400 REPORT_BULK_ACTION_UNKNOWN` |
| `ids` | ✅ (`@NotEmpty`) | Qo'ng'iroq id'lari. **`callIds` emas** |

**Response** (`BulkCallActionResult`):

```json
{ "accept": true, "data": { "processed": 2, "failed": [1053] }, "errors": null }
```

Har bir amal audit jurnaliga `CALLS_BULK_RETRY` / `CALLS_BULK_DNC` sifatida yoziladi.

---

## 2. Analitika va Dashboard

Quyidagi barcha endpointlar bir xil query parametrlarni oladi:

| Parametr | Izoh |
|---|---|
| `from` / `to` | ISO-8601 instant (`2026-09-01T00:00:00Z`). Berilmasa — oxirgi **7 kun**. Oraliq **366 kundan** oshsa `400 DATE_RANGE_TOO_LONG`; `from >= to` bo'lsa `400 DATE_RANGE_INVALID`; format buzuq bo'lsa `400 INSTANT_PARSE_FAILED` |
| `campaignId` | Ixtiyoriy — bitta kampaniya bilan cheklash |

### `GET /api/reports/dashboard/kpi` — Asosiy KPI ko'rsatkichlari

Huquq: **DASHBOARD_READ**.

**Response** (`DashboardKpi`) — to'rtta ko'rsatkich, har biri `DashboardMetric`
(`value`, oldingi shu uzunlikdagi davrga nisbatan `changePct`, va `sparkline`):

```json
{
  "accept": true,
  "data": {
    "totalCalls":    { "value": 1250, "changePct": 12.4, "sparkline": [110, 130, 180, 210, 190, 220, 210] },
    "answeredRate":  { "value": 0.84, "changePct": -2.1, "sparkline": [0.81, 0.85, 0.83, 0.84] },
    "avgDurationSec":{ "value": 140.2, "changePct": 4.0, "sparkline": [132, 138, 141, 140] },
    "promises":      { "value": 300, "changePct": 18.0, "sparkline": [30, 42, 55, 61] }
  },
  "message": null,
  "messageCode": null,
  "errors": null
}
```

`changePct` — oldingi davr `0` bo'lsa `null` (o'sishni hisoblab bo'lmaydi), ikkalasi ham
`0` bo'lsa `0.0`.

> ⚠️ `completedCalls`, `successRate`, `avgQaScore`, `totalCost` kabi maydonlar **yo'q**.

### `GET /api/reports/dashboard/timeseries` — Vaqt bo'yicha dinamika

Huquq: **DASHBOARD_READ**. **Response** (`List<DashboardBucket>`):

```json
[
  { "bucketStart": "2026-09-01T00:00:00Z", "total": 180, "answered": 151,
    "noAnswer": 22, "error": 7, "avgDurationSec": 138.5, "promises": 42 }
]
```

| Maydon | Nimani sanaydi |
|---|---|
| `total` | Davrda boshlangan barcha urinishlar |
| `answered` | `duration_sec` to'ldirilganlar (ya'ni suhbat bo'lgan) |
| `noAnswer` | `disposition = NO_ANSWER` |
| `error` | `disposition = FAILED` |
| `avgDurationSec` | Faqat javob berilganlar bo'yicha o'rtacha; hech biri bo'lmasa `null` |
| `promises` | `disposition = PROMISE_TO_PAY` |

Bucket kengligi oraliqdan avtomatik tanlanadi: ≤ 2 kun — **soat**, ≤ 62 kun — **kun**,
undan uzun — **hafta**.

### `GET /api/reports/dynamics` — Kengaytirilgan dinamika

Huquq: **REPORT_READ**. `timeseries` bilan bir xil javob (`List<DashboardBucket>`),
lekin qo'shimcha filtrlar bilan:

| Parametr | Izoh |
|---|---|
| `scenarioId` | Ssenariy bo'yicha cheklash |
| `operator` | Aslida **eskalatsiya** filtri (`call_result.escalated`): `true` — faqat operatorga eskalatsiya qilingan qo'ng'iroqlar, `false` — faqat qilinmaganlar, berilmasa — hammasi |

### `GET /api/reports/dashboard/outcomes` va `GET /api/reports/outcomes-distribution`

Birinchisi **DASHBOARD_READ**, ikkinchisi **REPORT_READ** huquqini talab qiladi; javob
bir xil (`List<DashboardOutcome>`) — Pie/Donut diagramma uchun:

```json
[ { "disposition": "PROMISE_TO_PAY", "count": 300 }, { "disposition": "NO_ANSWER", "count": 220 } ]
```

### `GET /api/reports/hourly-heatmap` — Soatlik issiqlik xaritasi

**Response** (`List<HourlyHeatmapCell>`):

```json
[ { "dayOfWeek": 1, "hour": 10, "total": 84, "answered": 71, "answerRate": 0.845 } ]
```

`dayOfWeek` — PostgreSQL `extract(dow ...)` qiymati: **`0` = yakshanba**, `1` = dushanba,
… `6` = shanba. `hour` — server timezone'idagi soat (0–23). `answered` — `duration_sec`
to'ldirilgan qo'ng'iroqlar soni.

### `GET /api/reports/campaign-comparison` — Kampaniyalarni taqqoslash

Bu endpoint `campaignId` o'rniga **`campaignIds`** (ro'yxat) oladi:
`?campaignIds=12&campaignIds=13`. Berilmasa — davrdagi barcha kampaniyalar.

**Response** (`List<CampaignComparisonRow>`):

```json
[ { "campaignId": 12, "campaignName": "Mart", "totalCalls": 1250, "answeredCalls": 1050,
    "answerRate": 0.84, "avgDurationSec": 140.2, "promises": 300 } ]
```

### `GET /api/reports/duration-histogram` — Davomiylik taqsimoti

**Response** (`List<DurationHistogramBucket>`):

```json
[ { "rangeLabel": "0-30",   "rangeStartSec": 0,   "rangeEndSec": 30,   "count": 210 },
  { "rangeLabel": "30-60",  "rangeStartSec": 30,  "rangeEndSec": 60,   "count": 180 },
  { "rangeLabel": "60-120", "rangeStartSec": 60,  "rangeEndSec": 120,  "count": 240 },
  { "rangeLabel": "120-300","rangeStartSec": 120, "rangeEndSec": 300,  "count": 160 },
  { "rangeLabel": "300-600","rangeStartSec": 300, "rangeEndSec": 600,  "count": 40 },
  { "rangeLabel": "600+",   "rangeStartSec": 600, "rangeEndSec": null, "count": 18 } ]
```

Chegaralar qat'iy: `30, 60, 120, 300, 600` soniya. Oxirgi bucket'da `rangeEndSec` —
`null` (yuqori chegara yo'q). Bucket'lar har doim oltitasi ham qaytadi, bo'sh bo'lsa
`count: 0`.

### `GET /api/reports/funnel` — Voronka

**Response** (`List<FunnelStage>`):

```json
[ { "stage": "CALL",             "count": 1250, "rate": 1.0 },
  { "stage": "ANSWERED",         "count": 1050, "rate": 0.84 },
  { "stage": "PERSON_CONFIRMED", "count": 910,  "rate": 0.728 },
  { "stage": "CONVERSATION",     "count": 780,  "rate": 0.624 },
  { "stage": "RESULT",           "count": 300,  "rate": 0.24 } ]
```

Bosqichlar qat'iy beshta va shu tartibda. `rate` — har doim birinchi bosqichga
(`CALL`) nisbatan ulush, oldingi bosqichga emas.

### `GET /api/reports/export` — Umumiy hisobotni faylga chiqarish

Huquq: **REPORT_READ**. `from`/`to`/`campaignId` ga qo'shimcha:

| Parametr | Standart | Izoh |
|---|---|---|
| `format` | `csv` | `csv`, `pdf` yoki `xlsx`. Boshqasi — `400 REPORT_EXPORT_FORMAT_UNKNOWN` |

Javob — `report.csv` / `report.pdf` / `report.xlsx` fayli (`byte[]`, `ResponseData`
konvertisiz). Ichida `ReportSummary`: davr, umumiy ko'rsatkichlar, natijalar taqsimoti,
voronka va kampaniyalar taqqoslamasi.

---

## 3. Rejalashtirilgan hisobotlar (`/api/reports/schedule`)

Menejer yoki tahlilchi elektron pochtasiga davriy avtomatik hisobot jo'natish.

### `POST /api/reports/schedule` — Yangi jadval

Huquq: **REPORT_EDIT**. **Request Body** (`CreateReportScheduleRequest`):

```json
{ "email": "director@uysot.uz", "periodicity": "WEEKLY", "format": "xlsx", "campaignId": 12 }
```

| Maydon | Turi | Majburiymi | Izoh |
|---|---|---|---|
| `email` | string | ✅ (`@NotBlank @Email`) | Hisobot yuboriladigan pochta |
| `periodicity` | `ReportPeriodicity` | ✅ (`@NotNull`) | `DAILY` (1 kun), `WEEKLY` (7 kun), `MONTHLY` (30 kun) |
| `format` | string | ❌ | `csv`, `pdf`, `xlsx` |
| `campaignId` | number | ❌ | Bitta kampaniya uchun; berilmasa — barchasi |

**Response** (`ReportSchedule`):

```json
{
  "accept": true,
  "data": {
    "id": 4,
    "companyId": 1,
    "email": "director@uysot.uz",
    "periodicity": "WEEKLY",
    "format": "xlsx",
    "campaignId": 12,
    "enabled": true,
    "lastSentAt": null,
    "createdAt": "2026-09-05T10:00:00Z"
  },
  "errors": null
}
```

### `POST /api/reports/schedule/list` — Jadvallar ro'yxati

Huquq: **REPORT_READ**. Body — `ReportScheduleFilter` (`page`/`size`/`orders`).
Saralanadigan ustunlar: `ID`, `EMAIL`, `PERIODICITY`, `ENABLED`, `CREATED_AT`.
Standart: `ID ASC`. Javob — `PageableData<ReportSchedule>`.

### `DELETE /api/reports/schedule/{id}` — Jadvalni to'xtatish

Huquq: **REPORT_EDIT**. Qatorni o'chirmaydi — `enabled=false` qiladi. Javob —
yangilangan `ReportSchedule`.

---

## 4. Tizim audit jurnali (`POST /api/reports/audit/list`) {#audit-log}

Huquq: **AUDIT_READ**.

**Request Body** (`AuditFilter`):

```json
{
  "page": 0,
  "size": 20,
  "orders": { "CREATED_AT": "DESC" },
  "actor": "aziz.b",
  "action": "CAMPAIGN_UPDATE",
  "entity": "campaign"
}
```

Saralanadigan ustunlar: `ID`, `CREATED_AT`, `ACTION`, `ENTITY`. Standart: `ID DESC`.

> ⚠️ `AuditFilter` da sana oralig'i (`from`/`to`) filtri **yo'q**.

**Response** (`PageableData<AuditLog>`):

```json
{
  "accept": true,
  "data": {
    "totalPages": 3,
    "currentPage": 0,
    "totalElements": 52,
    "data": [
      {
        "id": 910,
        "companyId": 1,
        "actor": "aziz.b",
        "action": "CAMPAIGN_UPDATE",
        "entity": "campaign",
        "entityId": "12",
        "detail": "sipTrunkIds: [1] -> [1, 2]",
        "createdAt": "2026-09-05T10:20:00Z",
        "ipAddress": "10.0.0.14"
      }
    ]
  },
  "errors": null
}
```
