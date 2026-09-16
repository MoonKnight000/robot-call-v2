# Hisobotlar va Tahlillar (Reports & Analytics) API

`uz.murodjon.robotcallv2.report` · huquq: **REPORT_READ** / **REPORT_EDIT** (bulk); dashboard — **DASHBOARD_READ**, audit — **AUDIT_READ**; kontakt-markaz (navbat/operator) hisobotlari — **REPORT_READ**, qo'ng'iroq timeline'i — **CALL_READ**

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
  "ids": null,
  "afterId": null
}
```

| Maydon | Turi | Izoh |
|---|---|---|
| `page` / `size` | number | Standart `0` / `20`, maksimum `size` — `500` |
| `orders` | map | Saralanadigan ustunlar: `CALL_ID`, `STARTED_AT`, `ENDED_AT`, `DURATION_SEC`, `DISPOSITION`. Standart: `CALL_ID DESC` |
| `q` | string | Telefon raqami bo'yicha erkin qidiruv (raqam ichida uchrasa mos). `%` va `_` oddiy belgi sifatida qidiriladi, wildcard emas. **`search` emas** |
| `campaignId` | number | Kampaniya bo'yicha cheklash |
| `disposition` | `Disposition` | Yakuniy natija bo'yicha filtr ([calls.md](calls.md#-qongiroq-yakuniy-natijalari-dispositions)) |
| `dateFrom` / `dateTo` | ISO instant | Qo'ng'iroq boshlanish vaqti oralig'i. **`from`/`to` emas** |
| `durationMinSec` / `durationMaxSec` | number | Suhbat davomiyligi oralig'i (soniya) |
| `ids` | `array<number>` | Aniq qo'ng'iroq id'lari (bulk oqimida tanlanganlarni qayta o'qish uchun). Berilsa, boshqa filtr maydonlari e'tiborga olinmaydi |
| `afterId` | number \| null | Keyset kursori (`≥ 1`) — pastdagi ["Kursor bilan sahifalash"](#kursor-bilan-sahifalash-afterid) ga qarang. Eksportda e'tiborga olinmaydi |

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
| `targetId` | Kampaniya nishoni id'si; kampaniyasiz qo'ng'iroq (qo'lda, test, widget, operator, kiruvchi) uchun `0` |
| `durationSec` | Ulanmagan urinishda `null` |
| `hangupCause` | Q.850 sababi. Skyline'da `18` — tarmoq nosozligi, `NO_ANSWER` emas |
| `hasRecording` | `true` bo'lsa `GET /api/reports/calls/{callId}/recording` ishlaydi |
| `operatorName` | Qo'ng'iroq operatorga uzatilgan bo'lsa — qabul qilgan xodim |

---

### Kursor bilan sahifalash (`afterId`)

Chuqur sahifalarda `page` (OFFSET) sekinlashadi: baza oldingi barcha qatorlarni o'qib tashlaydi,
har bir sahifa esa jami sonni (`count(*)`) qayta sanaydi. Uzun ro'yxatni (cheksiz scroll) varaqlash
uchun `afterId` ishlating:

1. Birinchi sahifa — `afterId` siz (`page: 0`), odatdagidek. Jami son shu yerda keladi.
2. Keyingi sahifa — `afterId` = oldingi sahifadagi **oxirgi** qatorning `callId` si.
3. `data` dagi qatorlar soni `size` dan kam bo'lsa — ro'yxat tugadi.

`afterId` berilganda:

| | |
|---|---|
| Tartib | Doim `callId` bo'yicha kamayish (eng yangisi birinchi). `orders` yubormang (yoki faqat `{"CALL_ID": "DESC"}`); boshqa har qanday tartib — `400 CALL_CURSOR_ORDER_UNSUPPORTED` |
| `page` | E'tiborga olinmaydi (OFFSET yo'q) |
| Filtrlar | `q`, `campaignId`, `disposition`, sana va davomiylik — odatdagidek qo'llanadi |
| `totalElements` / `totalPages` | **`-1`** — sanalmaydi (jami son kerak bo'lsa, birinchi sahifadan oling) |
| `currentPage` | `0` |

```json
{ "size": 50, "q": "99890", "afterId": 1052 }
```

`afterId` siz so'rov avvalgidek ishlaydi (`page` + `orders` + jami son). Kursor
`POST /api/reports/campaigns/{id}/calls/list` da ham xuddi shunday ishlaydi.

> **O'zgarish:** ilgari `afterId` bilan birga yuborilgan boshqa `orders` (masalan
> `{"STARTED_AT": "ASC"}`) jimgina tashlab yuborilardi — ro'yxat baribir `callId` bo'yicha
> kamayib kelardi. Endi bunday so'rov `400 CALL_CURSOR_ORDER_UNSUPPORTED` oladi.

---

### `POST /api/reports/campaigns/{id}/calls/list` — Bitta kampaniya qo'ng'iroqlari

So'rov shakli yuqoridagi `CallFilter` bilan bir xil; kampaniya path'dagi `id` dan olinadi va
doim qo'llanadi (`ids` berilganda ham).

> **O'zgarish:** ilgari bu endpoint `page`/`size`/`orders` dan boshqa filtr maydonlarini
> (`q`, `disposition`, sana, davomiylik, `ids`) jimgina e'tiborsiz qoldirardi — ro'yxat ham,
> `totalElements` ham butun kampaniya bo'yicha edi. Endi ular `/calls/list` dagidek qo'llanadi,
> shuning uchun filtr berilgan so'rovda son kamayadi. Body'dagi `campaignId` path'dagi bilan
> **birga** qo'llanadi (ikkalasi farq qilsa — bo'sh ro'yxat).

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
    "escalatedCalls": 12,
    "connectedCalls": 610,
    "asr": 0.61,
    "contactRate": 0.72,
    "connectedByAttemptNo": { "1": 480, "2": 95, "3": 35 },
    "spentUzs": 1830000,
    "costPerConnectedUzs": 3000,
    "costPerConversionUzs": 6100
  },
  "errors": null
}
```

| Maydon | Izoh |
|---|---|
| `answeredCalls` | Tugagan urinishlar soni (tarixiy nom, panel shuni ishlatadi) |
| `connectedCalls` | Ko'tarilgan urinishlar (`answered_at` bor) |
| `asr` | Answer-seizure ratio — `connectedCalls / answeredCalls` (0..1). Ro'yxat sog'lom bo'lsa-yu bu past bo'lsa — trunk yoki caller ID muammosi, skript emas |
| `contactRate` | Urinilgan nishonlarning kamida bir marta ulangan ulushi (0..1) — ro'yxatning o'zi qanchalik "tirik" |
| `connectedByAttemptNo` | Nishonlar birinchi marta nechanchi urinishda ulangani: `{"1": 480, "2": 95, "3": 35}` — 3-urinish o'zini oqlayaptimi |
| `spentUzs` | Kampaniyaning settled qo'ng'iroqlari uchun jami hisoblangan summa (`call_billing.total_uzs`) |
| `costPerConnectedUzs` / `costPerConversionUzs` | Ulangan qo'ng'iroq / konversiya (`PROMISE_TO_PAY` + `GOAL_REACHED` + `COMPLETED`) boshiga xarajat; hech biri bo'lmasa `null` |

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
      "maxLlmLatencyMs": 650,
      "rtpPacketsReceived": 4210,
      "rtpPacketsLost": 12,
      "rtpJitterMs": 6,
      "rtpMos": 4.28
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

> REALTIME qo'ng'iroqlarda `promptTokens`/`completionTokens`/`cachedTokens` — engine o'zi
> xabar qilgan token soni (audio tokenlar ham kiradi; Gemini'da thinking tokenlar
> `completionTokens` ga qo'shiladi). `avgTurnLatencyMs`/`maxTurnLatencyMs` — mijoz gapirib
> bo'lganidan (bizning VAD o'lchaydi, engine'niki emas) engine javobining birinchi audiosi
> navbatga qo'yilgunicha; shu sababli cascade qo'ng'iroq bilan to'g'ridan-to'g'ri
> solishtirsa bo'ladi. `avgLlmLatencyMs`/`maxLlmLatencyMs` REALTIME'da doim `null` — tanish,
> o'ylash va gapirish bitta kutish.

**Audio sifati (`rtp*`) — ikkala rejimda ham:**

| Maydon | Turi | Izoh |
|---|---|---|
| `rtpPacketsReceived` | number \| null | Mijoz tomonidan kelgan RTP paketlar. `0` — media yo'li umuman ochilmagan (NAT/`RTP_LOCAL_IP` xatosi) |
| `rtpPacketsLost` | number \| null | Kelmay qolgan paketlar |
| `rtpJitterMs` | number \| null | Paketlar kelishidagi tebranish, ms |
| `rtpMos` | number \| null | Taxminiy MOS (1.0–4.4, soddalashtirilgan ITU-T G.107 E-model). `< 3.5` — tarmoq/trunk muammosi, suhbat emas. Hech narsa kelmagan qo'ng'iroqda `null` |

V11 migratsiyasidan oldingi qo'ng'iroqlarda `rtp*` maydonlari `null`.

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

`page`/`size` eksportda e'tiborga olinmaydi — filtrga mos **barcha** qo'ng'iroqlar `orders`
tartibida chiqadi, eng ko'pi **50 000** ta. Filtr bundan ko'p qo'ng'iroqqa mos kelsa, fayl
jimgina kesilmaydi: `400 REPORT_EXPORT_TOO_LARGE` — sana oralig'ini yoki filtrlarni toraytiring.
(Ilgari eksport 500 qatorda ogohlantirishsiz to'xtardi.)

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
        "hint": "Javob berilgan har bir qo'ng'iroqning o'rtacha uzunligi",
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

> **"Muvaffaqiyat" nimani sanaydi.** Barcha ekranlarda bitta ta'rif: disposition'i
> `PROMISE_TO_PAY`, `GOAL_REACHED` yoki `COMPLETED` bo'lgan qo'ng'iroq (`Disposition.isConversion()` — A/B
> test ham shuni sanaydi). `successRate`, `completed`, `success_rate` KPI, agentlar reytingi
> va muvaffaqiyat alerti (`AlertingService`, [notifications.md](notifications.md)) shu bilan
> hisoblanadi. `promises` — alohida ko'rsatkich: faqat `PROMISE_TO_PAY`. Kampaniya
> statistikasidagi `costPerConversionUzs` ning maxraji ham shu ta'rif.
>
> **Bitta asos.** `dashboard/kpi`, `timeseries`, `outcomes`, `outcomes-distribution`,
> `hourly-heatmap`, `duration-histogram`, `funnel`, `dynamics` va `dashboard/summary` endi bitta
> join zanjiri va bitta WHERE (kompaniya + davr + ixtiyoriy `campaignId`/`scenarioId`) ustida
> hisoblanadi; disposition qiymatlari SQL ichida yozilmaydi, `Disposition` enum'idan parametr
> sifatida beriladi. `dashboard/outcomes` va `dashboard/summary` dagi `statusBreakdown` —
> bitta so'rov. Bu endpointlarning raqamlari o'zgarmadi — faqat `dynamics` ning `scenarioId`
> filtri (pastda).
>
> **"Muvaffaqiyatsiz" va "o'rtacha davomiylik" — ikkala oila uchun bitta ta'rif.** KPI/timeseries
> oilasi (`dashboard/kpi`, `timeseries`, `dynamics`, `campaign-comparison`, `summary` va
> eksport) hamda `dashboard/summary` oilasi bir xil sanaydi:
>
> | Ko'rsatkich | Ta'rif |
> |---|---|
> | Muvaffaqiyatsiz (`error`, `failed`, `failed_calls`) | disposition `FAILED` **yoki** `CARRIER_REJECTED` |
> | O'rtacha davomiylik (`avgDurationSec`, `avg_duration`) | faqat **javob berilgan** qo'ng'iroqlar (`answered_at` bor) bo'yicha `duration_sec` o'rtachasi |
>
> Javob berilgan qo'ng'iroq bo'lmasa: `null` bo'la oladigan maydonda `null` (`timeseries`/`dynamics`
> dagi `avgDurationSec`, `campaign-comparison`, `summary.totals`), qolganlarida `0`
> (`dashboard/kpi` dagi `value`, `dashboard/summary` KPI kartasi, `timeline`, `directionMix`) —
> JSON turlari o'zgarmadi. `outcomes`/`statusBreakdown` avvalgidek har bir disposition'ni alohida
> qator qilib beradi (`FAILED` va `CARRIER_REJECTED` — ikki qator).
>
> **O'zgarish (raqamlar):** `timeseries`/`dynamics` dagi `error` ilgari faqat `FAILED` ni sanardi —
> endi `CARRIER_REJECTED` ham qo'shiladi, son oshishi mumkin. `avgDurationSec` ilgari KPI oilasida
> `duration_sec` bor barcha urinishlar bo'yicha, `dashboard/summary` da esa `duration_sec > 0`
> bo'yicha edi — endi ikkalasida ham javob berilganlar bo'yicha: KPI oilasida (ulanmay `0` bilan
> yopilgan urinishlar chiqib ketgani uchun) odatda oshadi, summary'da `0` soniyali javob berilgan
> qo'ng'iroqlar qo'shilgani uchun biroz kamayishi mumkin. `avg_duration` kartasining `hint` matni
> ham yangilandi.

---

### `GET /api/reports/dashboard/timeseries` — Vaqt bo'yicha dinamika

Huquq: **DASHBOARD_READ**. **Response** (`List<DashboardBucket>`):

```json
[
  { "bucketStart": "2026-09-01T00:00:00Z", "total": 180, "answered": 151,
    "noAnswer": 22, "error": 7, "avgDurationSec": 138.5, "promises": 42 }
]
```

`error` — `FAILED` + `CARRIER_REJECTED`; `avgDurationSec` — bucket'ning javob berilgan
qo'ng'iroqlari bo'yicha, bo'lmasa `null` (yuqoridagi ta'riflar jadvaliga qarang).

---

> **"Javob berilgan" (`answered`, `answeredRate`, `answeredCalls`) nimani sanaydi.** KPI,
> timeseries, dynamics, hourly-heatmap, campaign-comparison, voronkaning `ANSWERED` /
> `PERSON_CONFIRMED` / `CONVERSATION` bosqichlari va kiruvchi yo'nalish statistikasi —
> hammasi **ko'tarilgan** urinishni (`answered_at` bor) sanaydi. Ilgari `duration_sec`
> borligi sanalardi, terilmasdan yopilgan urinishlar esa `duration_sec = 0` bilan yozilardi —
> ular "javob berilgan" bo'lib chiqardi. O'rtacha davomiylik (`avgDurationSec`) ham endi shu
> javob berilgan qo'ng'iroqlar bo'yicha hisoblanadi.

### `GET /api/reports/dynamics` — Kengaytirilgan dinamika

Huquq: **REPORT_READ**. Query: `from`, `to`, `campaignId`, `scenarioId`, `operator` (boolean —
`true` faqat operatorga eskalatsiya qilinganlar, `false` — qilinmaganlar). Javob shakli
`dashboard/timeseries` bilan bir xil (`List<DashboardBucket>`).

> **O'zgarish:** `scenarioId` ilgari faqat kampaniya agentining ssenariysini tekshirardi —
> shu ssenariyda gaplashgan **kiruvchi** qo'ng'iroqlar (inbound route agenti) tushib qolardi.
> Endi `dashboard/summary` dagidek ikkala agent ham hisobga olinadi, shuning uchun kiruvchi
> qo'ng'iroqlari bor kompaniyada `scenarioId` bilan so'ralgan sonlar oshadi.

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

---

## 5. Kontakt-markaz hisobotlari (navbatlar, operatorlar, qo'ng'iroq timeline'i)

Kiruvchi PBX ishining sifati: har bir navbat mijozlarga qanday xizmat qildi (service level,
javob tezligi, tashlab ketish) va har bir operator kunini qanday o'tkazdi. Ma'lumot manbalari:
`call_attempt` ning navbat maydonlari (`queue_id`, `enqueued_at`, `wait_seconds`, `abandoned`,
`operator_answered_at`, `operator_user_id`, `survey_score`), `call_event` timeline'i va
`operator_status_log`.

### Davr — `from` / `to`

Ikkala hisobot bir xil query parametrlarini oladi. Dashboard'dagi `range` preseti bu yerda **yo'q**.

| Parametr | Standart | Izoh |
|---|---|---|
| `from` | `to` − 7 kun | ISO instant (`2026-09-01T00:00:00Z`) yoki `YYYY-MM-DD` — Toshkent vaqti bilan kun boshi |
| `to` | hozir | ISO instant yoki `YYYY-MM-DD` — Toshkent vaqti bilan kun oxiri (23:59:59.999…) |

Oraliq yarim ochiq: `from ≤ t < to`. **Xatolar:** `400 DATE_RANGE_INVALID` (`from` `to` dan keyin
yoki teng), `400 DATE_RANGE_TOO_LONG` (366 kundan uzun), `400 INSTANT_PARSE_FAILED`.

---

### `GET /api/reports/queues` — Navbatlar bo'yicha hisobot

Huquq: **REPORT_READ**. Misol: `GET /api/reports/queues?from=2026-09-01&to=2026-09-07`

Kompaniyaning **har bir** navbati qaytadi — davrda qo'ng'iroq bo'lmagani ham (nollar va `null`
bilan), nomi bo'yicha saralangan. Qo'ng'iroq davrga **navbatga kirgan vaqti** (`enqueued_at`)
bo'yicha tushadi.

**Response** (`List<QueueReportRow>`):

```json
{
  "accept": true,
  "data": [
    {
      "queueId": 3,
      "queueName": "Sotuv bo'limi",
      "offered": 240,
      "answered": 212,
      "abandoned": 22,
      "shortAbandoned": 6,
      "answeredWithinSla": 180,
      "serviceLevelPercent": 76.92307692307692,
      "abandonRatePercent": 6.837606837606837,
      "averageWaitSeconds": 17.4,
      "maxWaitSeconds": 185,
      "averageTalkSeconds": 142.8,
      "overflowed": 4,
      "callbacksRequested": 3,
      "averageSurveyScore": 4.6
    }
  ],
  "message": null,
  "messageCode": null,
  "errors": null
}
```

| Maydon | Turi | Ma'nosi |
|---|---|---|
| `queueId` / `queueName` | number / string | Navbat |
| `offered` | number | Davr ichida navbatga tushgan qo'ng'iroqlar |
| `answered` | number | Operator ko'targanlari (`operator_answered_at` bor) |
| `abandoned` | number | Mijoz kutib turib go'shakni qo'yganlari. Qisqa tashlab ketishlar ham shu songa kiradi |
| `shortAbandoned` | number | `abandoned` ning 5 soniyadan kam kutganlari (kutish vaqti noma'lum bo'lsa ham shu yerga). Odatdagi amaliyotga ko'ra service level va abandon rate'dan chiqarib tashlanadi |
| `answeredWithinSla` | number | Navbatning `serviceLevelSec` chegarasi ichida ko'tarilganlari (`wait_seconds ≤ serviceLevelSec`) |
| `serviceLevelPercent` | number \| null | `answeredWithinSla × 100 ÷ (offered − shortAbandoned)`. Qiymat **0–100 oralig'ida foiz** (0–1 ulush emas) va yaxlitlanmagan. Maxraj `0` bo'lsa — `null` |
| `abandonRatePercent` | number \| null | `(abandoned − shortAbandoned) × 100 ÷ (offered − shortAbandoned)`; maxraj `0` bo'lsa — `null` |
| `averageWaitSeconds` | number \| null | ASA — faqat **ko'tarilgan** qo'ng'iroqlarning o'rtacha kutish vaqti. Ko'tarilgan qo'ng'iroq bo'lmasa — `null` |
| `maxWaitSeconds` | number \| null | Eng uzoq kutish — **barcha** tushgan qo'ng'iroqlar bo'yicha (tashlab ketilganlar ham kiradi). Qo'ng'iroq bo'lmasa — `null` |
| `averageTalkSeconds` | number \| null | Operator ko'targanidan qo'ng'iroq tugaguncha o'rtacha vaqt (faqat tugaganlari) |
| `overflowed` | number | Navbatning overflow qoidasi boshqa joyga yuborganlari: hech kim tizimda emas, navbat to'la yoki kutish chegarasi o'tgan (`OVERFLOW` hodisalari) |
| `callbacksRequested` | number | Navbatda kutayotganda qayta qo'ng'iroq so'raganlar (`queueId` li `CALLBACK_REQUESTED` hodisalari) |
| `averageSurveyScore` | number \| null | So'rovnoma bahosining o'rtachasi (faqat baho qo'yganlar). Baho bo'lmasa — `null` |

> `overflowed` va `callbacksRequested` davrga **hodisa vaqti** (`call_event.at`) bo'yicha,
> qolgan ustunlar esa `enqueued_at` bo'yicha tushadi — shuning uchun davr chegarasida ular
> `offered` bilan biroz farq qilishi mumkin. `answered + abandoned` doim ham `offered` ga teng
> bo'lavermaydi: overflow bo'lganlar va hali kutayotganlar ikkalasiga ham kirmaydi.

---

### `GET /api/reports/operators` — Operatorlar bo'yicha hisobot

Huquq: **REPORT_READ**. Misol: `GET /api/reports/operators?from=2026-09-06&to=2026-09-06`

Kompaniyada SIP akkaunti bor **har bir** foydalanuvchi qaytadi ([operators.md](operators.md)),
ismi bo'yicha saralangan.

**Response** (`List<OperatorReportRow>`):

```json
{
  "accept": true,
  "data": [
    {
      "userId": 17,
      "name": "Dilnoza Karimova",
      "answered": 58,
      "missed": 3,
      "averageTalkSeconds": 131.5,
      "totalTalkSeconds": 7627,
      "averageSurveyScore": 4.8,
      "availableSeconds": 9000,
      "onCallSeconds": 7627,
      "wrapUpSeconds": 580,
      "breakSeconds": 1800,
      "occupancyPercent": 47.695705
    }
  ],
  "message": null,
  "messageCode": null,
  "errors": null
}
```

| Maydon | Turi | Ma'nosi |
|---|---|---|
| `userId` / `name` | number / string | Operator |
| `answered` | number | Davr ichida ko'targan **va tugagan** qo'ng'iroqlari (`operator_answered_at` davr ichida). Hozir davom etayotgan suhbat hisoblanmaydi |
| `missed` | number | RONA — unga taklif qilinib, jiringlab tugagan yoki o'zi rad etgan qo'ng'iroqlar (`OPERATOR_MISSED` hodisalari, hodisa vaqti bo'yicha) |
| `averageTalkSeconds` | number \| null | Ko'targanidan tugaguncha o'rtacha vaqt; `answered = 0` bo'lsa — `null` |
| `totalTalkSeconds` | number | Shu vaqtlar yig'indisi (butun soniya) |
| `averageSurveyScore` | number \| null | Uning qo'ng'iroqlariga qo'yilgan so'rovnoma bahosining o'rtachasi |
| `availableSeconds` | number | `AVAILABLE` va `RINGING` holatlarida o'tgan vaqt |
| `onCallSeconds` | number | `ON_CALL` holatidagi vaqt |
| `wrapUpSeconds` | number | `WRAP_UP` — qo'ng'iroqdan keyingi yakunlash vaqti |
| `breakSeconds` | number | `BREAK` holatidagi vaqt |
| `occupancyPercent` | number \| null | `(onCall + wrapUp) × 100 ÷ (available + onCall + wrapUp)`, 0–100 foiz, yaxlitlanmagan. `BREAK` va `OFFLINE` maxrajga kirmaydi. Davrda umuman tizimga kirmagan bo'lsa — `null` |

Holat vaqtlari `operator_status_log` dan olinadi va `[from, to)` oralig'iga kesiladi; hali
tugamagan joriy holat hozirgi vaqtgacha hisoblanadi. `OFFLINE` vaqti qaytarilmaydi.

---

### `GET /api/reports/calls/{callId}/events` — Qo'ng'iroq timeline'i

Huquq: **CALL_READ**. `callId` — `call_attempt.id` (`CallRow.callId` bilan bir xil). Qo'ng'iroq
bo'yicha yozilgan barcha qadamlarni vaqt tartibida (`at`, keyin `id` bo'yicha o'sib boruvchi)
qaytaradi: call flow tugunlari, navbat, operatorga taklif, hold, uzatish, monitoring va h.k.

**Xato:** `404 CALL_NOT_FOUND` (qo'ng'iroq yo'q yoki boshqa kompaniyaniki).

**Response** (`List<CallEvent>`):

```json
{
  "accept": true,
  "data": [
    { "id": 901, "companyId": 1, "callAttemptId": 1052, "at": "2026-09-05T10:15:02Z", "type": "FLOW_ENTERED", "payload": { "flowId": 4 } },
    { "id": 902, "companyId": 1, "callAttemptId": 1052, "at": "2026-09-05T10:15:09Z", "type": "ENQUEUED", "payload": { "queueId": 3, "position": 2 } },
    { "id": 903, "companyId": 1, "callAttemptId": 1052, "at": "2026-09-05T10:15:30Z", "type": "OPERATOR_RINGING", "payload": { "userId": 17, "channelId": "1710000000.40", "forwarded": false } },
    { "id": 904, "companyId": 1, "callAttemptId": 1052, "at": "2026-09-05T10:15:34Z", "type": "OPERATOR_ANSWERED", "payload": { "userId": 17, "waitSeconds": 25, "channelId": "1710000000.40" } },
    { "id": 905, "companyId": 1, "callAttemptId": 1052, "at": "2026-09-05T10:17:10Z", "type": "TRANSFER", "payload": { "from": 17, "to": "queue 5", "mode": "BLIND" } }
  ],
  "message": null,
  "messageCode": null,
  "errors": null
}
```

| Maydon | Turi | Izoh |
|---|---|---|
| `id` | number | Hodisa id'si |
| `companyId` | number | Kompaniya |
| `callAttemptId` | number | Qo'ng'iroq (`callId`) |
| `at` | ISO instant | Hodisa vaqti |
| `type` | `CallEventType` | Pastdagi jadval |
| `payload` | object | Turga xos maydonlar; bo'lmasa — `{}` (hech qachon `null` emas) |

**`CallEventType` qiymatlari va `payload` maydonlari:**

| `type` | Qachon | `payload` |
|---|---|---|
| `REJECTED` | Kiruvchi caller qoidasi (qora ro'yxat) qo'ng'iroqni rad etdi | `action`, `prefix` |
| `FLOW_ENTERED` | Qo'ng'iroq call flow'ni boshladi | `flowId` |
| `NODE_ENTERED` | Flow tuguniga o'tdi | `node`, `type` |
| `DTMF` | Mijoz tugma bosdi | `key`; ichki raqam terilganda qo'shimcha `extension: true` |
| `LANGUAGE_SET` | Til tanlandi | `language` |
| `ENQUEUED` | Navbatga tushdi | `queueId`, `position` |
| `POSITION_ANNOUNCED` | Mijozga navbatdagi o'rni aytildi | `position`, `waitMinutes` |
| `OPERATOR_RINGING` | Operator telefoni jiringlay boshladi | `userId`, `channelId`, `forwarded`; tashqi raqamga bo'lsa — `number`, `channelId` |
| `OPERATOR_MISSED` | Operator ko'tarmadi yoki rad etdi (RONA) | `userId`, `cause` |
| `OPERATOR_ANSWERED` | Operator ko'tardi | `userId`, `waitSeconds`, `channelId` |
| `WHISPER` | Ko'tarishdan oldin operatorga qisqa ma'lumot (handover) o'qildi | `userId`, `seconds` |
| `HOLD` / `UNHOLD` | Operator hold qildi / qaytardi | `userId` |
| `TRANSFER` | Uzatish | `from` (uzatgan operatorning userId'si), `to` (matn: `"queue 5"`, `"user 17"` yoki telefon raqami), `mode`: `BLIND`, `CONSULT` (konsultatsiya boshlandi) yoki `ATTENDED` (hamkasb qo'ng'iroqni oldi) |
| `ABANDONED` | Mijoz navbatda kutib turib go'shakni qo'ydi | `queueId`, `waitSeconds` |
| `OVERFLOW` | Navbatning overflow qoidasi ishladi | `queueId`, `reason` (`"nobody signed in"`, `"queue full"`, `"waited 120s"`) |
| `VOICEMAIL` | Ovozli xabar qoldirildi | `boxId`, `seconds` |
| `CALLBACK_REQUESTED` | Mijoz qayta qo'ng'iroq so'radi | Navbatdan — `queueId`; flow tugunidan — `{}` |
| `SURVEY_SCORE` | So'rovnoma bahosi qo'yildi | `score`, `userId` |
| `MONITOR` | Supervayzer qo'shildi (telefoni jiringlagan payt) | `userId`, `mode` (`LISTEN` / `WHISPER` / `BARGE`) |
