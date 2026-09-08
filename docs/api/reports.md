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
      "ttsVoice": "nigora",
      "llmModel": "gemini-3.8-flash",
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

**`technical` — qo'ng'iroq qaysi rejimda ketganiga qarab o'qiladi:**

* **CASCADE** — yuqoridagi misol: `sttProvider`/`ttsProvider` shu agentning provayderlari
  (agent tanlamagan bo'lsa deploy default'i), `llmModel` — qo'ng'iroqning asosiy modeli
  (kompaniya sozlamasi + agent override'i). Bu **sozlangan** model: mijoz juda qisqa javob
  bergan turn'lar agentning `fastLlmModel` ida ketgan bo'lishi mumkin va bu maydon buni
  ko'rsatmaydi — qaysi turn qaysi modelga ketgani faqat logda.
* **REALTIME** — tanish, o'ylash va gapirish bitta vendor ichida bo'lgani uchun uchalasi
  ham **engine id** bo'ladi: `"sttProvider": "gemini-live"`, `"ttsProvider": "gemini-live"`,
  `"llmModel": "gemini-3.1-flash-live-preview"`. `ttsVoice` — engine qabul qilgan ovoz
  (masalan `Aoede`); campaign ovozi boshqa vendorniki bo'lsa engine o'z default ovozida
  gapiradi va bu maydon `null` bo'ladi.

> Hozircha REALTIME qo'ng'iroqlarda `promptTokens`/`completionTokens`/`cachedTokens` = `0`
> va `avg*/max*LatencyMs` = `null`: engine bu ko'rsatkichlarni bermaydi, `turnCount` esa
> to'ldiriladi.

---

### `GET /api/reports/calls/{callId}/recording` — Qo'ng'iroq audio yozuvi

Xom WAV oqimi (`ResponseEntity<Resource>`), `ResponseData` konvertisiz. `Range:
bytes=<start>-<end>` to'liq qo'llab-quvvatlanadi.

* **Content-Type**: `audio/wav` · **Content-Disposition**: `inline`
* **Accept-Ranges**: `bytes` · **Status**: `200` yoki `206 Partial Content`

---

### `GET /api/reports/calls/{callId}/transcript.txt` — Transkriptni matn ko'rinishida

`text/plain` fayl, `ResponseData` konvertisiz.

---

### `POST /api/reports/calls/export` — Qo'ng'iroqlarni CSV eksport qilish

Body — `CallFilter` (yuqoridagi shakl). Javob — **`calls.csv`** fayli (`byte[]`,
`ResponseData` konvertisiz).

---

### `POST /api/reports/calls/bulk` — Ommaviy amallar

Huquq: **REPORT_EDIT**.

**Request Body** (`BulkCallActionRequest`):

```json
{ "action": "retry", "ids": [1051, 1052, 1053] }
```

---

## 2. Analitika va Dashboard

### `GET /api/reports/dashboard/summary` — Birlashgan Dashboard Ko'rsatkichlari (Unified Dashboard Summary)

Huquq: **DASHBOARD_READ**. Frontend bosh sahifasi (Dashboard) uchun barcha kerakli ma'lumotlarni yagona so'rovda qaytaradi.

**Query Parametrlar:**

| Parametr | Standart | Izoh |
|---|---|---|
| `range` | `24h` | Davr turi: `24h`, `7d`, `30d`, yoki `custom` |
| `from` | *avtomatik* | Boshlanish sanasi/vaqti (ISO Instant yoki `YYYY-MM-DD`) |
| `to` | *hozirgi vaqt* | Tugash sanasi/vaqti (ISO Instant yoki `YYYY-MM-DD`) |
| `campaignId` | null | Kampaniya bo'yicha filter |
| `scenarioId` | null | Ssenariy bo'yicha filter |

**Response** (`DashboardSummaryResponse`):

```json
{
  "accept": true,
  "data": {
    "range": "24h",
    "from": "2026-09-06T00:00:00Z",
    "to": "2026-09-06T23:59:59Z",
    "updatedAt": "14:32:05",
    "kpis": [
      {
        "id": "total_calls",
        "label": "Jami qo'ng'iroqlar",
        "value": "1 482",
        "rawValue": 1482,
        "unit": "ta",
        "delta": "+12.4%",
        "deltaPct": 12.4,
        "up": true,
        "isBad": null,
        "sparkline": [12, 18, 25, 40, 60, 55, 48],
        "hint": "Tanlangan davrdagi barcha terilgan va kelib tushgan qo'ng'iroqlar",
        "targetLink": "/calls"
      },
      {
        "id": "minutes_used",
        "label": "Ishlatilgan daqiqalar",
        "value": "3 240 daq",
        "rawValue": 3240,
        "unit": "daq",
        "delta": "+8.1%",
        "deltaPct": 8.1,
        "up": true,
        "isBad": null,
        "sparkline": [20, 30, 50, 75, 110, 95, 80],
        "hint": "AI ovozli agent va operatorlar suhbat davomiyligi",
        "targetLink": "/reports"
      },
      {
        "id": "avg_duration",
        "label": "O'rtacha davomiylik",
        "value": "2 daq 11 s",
        "rawValue": 131,
        "unit": "soniya",
        "delta": "-3.2%",
        "deltaPct": -3.2,
        "up": false,
        "isBad": null,
        "sparkline": [120, 135, 130, 125, 140, 131],
        "hint": "Har bir muvaffaqiyatli suhbatning o'rtacha uzunligi",
        "targetLink": "/reports"
      },
      {
        "id": "success_rate",
        "label": "Muvaffaqiyat ko'rsatkichi",
        "value": "78.4%",
        "rawValue": 78.4,
        "unit": "%",
        "delta": "+4.2%",
        "deltaPct": 4.2,
        "up": true,
        "isBad": null,
        "sparkline": [72.0, 75.1, 74.0, 78.4],
        "hint": "Ijobiy natija yoki maqsadga erishilgan qo'ng'iroqlar ulushi",
        "targetLink": "/reports"
      },
      {
        "id": "failed_calls",
        "label": "Muvaffaqiyatsiz qo'ng'iroqlar",
        "value": "42",
        "rawValue": 42,
        "unit": "ta",
        "delta": "-15.0%",
        "deltaPct": -15.0,
        "up": false,
        "isBad": true,
        "sparkline": [5, 8, 4, 3, 2, 6],
        "hint": "SIP ulanish xatosi yoki tarmoq xatosi tufayli uzilganlar (ko'rib chiqish tavsiya etiladi)",
        "targetLink": "/calls?disposition=FAILED"
      },
      {
        "id": "missed_calls",
        "label": "Javobsiz qo'ng'iroqlar",
        "value": "118",
        "rawValue": 118,
        "unit": "ta",
        "delta": "+2.0%",
        "deltaPct": 2.0,
        "up": true,
        "isBad": true,
        "sparkline": [15, 20, 18, 22, 19, 24],
        "hint": "Gudok ketgan ammo mijoz javob bermagan yoki band bo'lganlar",
        "targetLink": "/calls?disposition=NO_ANSWER"
      }
    ],
    "timeline": {
      "unit": "hour",
      "peakCalls": 142,
      "peakLabel": "Soat 14:00",
      "totalCalls": 1482,
      "totalMinutes": 3240,
      "points": [
        {
          "t": "09:00",
          "label": "Soat 09:00",
          "calls": 45,
          "minutes": 98,
          "failed": 2,
          "completed": 35,
          "successRate": 77.8
        }
      ]
    },
    "statusBreakdown": [
      {
        "code": "COMPLETED",
        "label": "Suhbat yakunlandi (Muvaffaqiyatli)",
        "count": 940,
        "pct": 63,
        "color": "var(--success, #10b981)"
      }
    ],
    "directionMix": {
      "outbound": {
        "count": 1120,
        "pct": 76,
        "minutes": 2450,
        "avgDurationSec": 131,
        "successRatePct": 79.2
      },
      "inbound": {
        "count": 362,
        "pct": 24,
        "minutes": 790,
        "avgDurationSec": 130,
        "successRatePct": 76.0
      }
    },
    "topAgents": [
      {
        "id": 1,
        "name": "Dilnavoz (AI)",
        "role": "Avtomatlashgan robot",
        "type": "ai",
        "calls": 820,
        "minutes": 1800,
        "successRate": 81.2,
        "relativePct": 100
      }
    ],
    "campaigns": [
      {
        "id": 12,
        "name": "Kechikkan to'lovlar",
        "progress": 65,
        "done": 650,
        "total": 1000
      }
    ],
    "live": [],
    "recentCalls": [
      {
        "id": "1052",
        "name": "Sardor Alimov",
        "phone": "+998901234567",
        "campaign": "Kechikkan to'lovlar",
        "agentName": "Dilnavoz (AI)",
        "direction": "outbound",
        "code": "COMPLETED",
        "dispositionLabel": "Suhbat yakunlandi (Muvaffaqiyatli)",
        "duration": "02:22",
        "durationSec": 142,
        "time": "10:15",
        "date": "2026-09-06",
        "hasRecording": true
      }
    ]
  },
  "message": null,
  "messageCode": null,
  "errors": null
}
```

---

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

---

### `GET /api/reports/dashboard/timeseries` — Vaqt bo'yicha dinamika

Huquq: **DASHBOARD_READ**. **Response** (`List<DashboardBucket>`):

```json
[
  { "bucketStart": "2026-09-01T00:00:00Z", "total": 180, "answered": 151,
    "noAnswer": 22, "error": 7, "avgDurationSec": 138.5, "promises": 42 }
]
```

---

### `GET /api/reports/dynamics` — Kengaytirilgan dinamika

Huquq: **REPORT_READ**.

---

### `GET /api/reports/dashboard/outcomes` va `GET /api/reports/outcomes-distribution`

---

### `GET /api/reports/hourly-heatmap` — Soatlik issiqlik xaritasi

---

### `GET /api/reports/campaign-comparison` — Kampaniyalarni taqqoslash

---

### `GET /api/reports/duration-histogram` — Davomiylik taqsimoti

---

### `GET /api/reports/funnel` — Voronka

---

### `GET /api/reports/export` — Umumiy hisobotni faylga chiqarish

---

## 3. Rejalashtirilgan hisobotlar (`/api/reports/schedule`)

---

## 4. Tizim audit jurnali (`POST /api/reports/audit/list`) {#audit-log}
