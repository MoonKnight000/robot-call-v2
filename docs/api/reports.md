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
| `/api/reports/calls/export` | POST | **ADMIN** |
| `/api/reports/calls/bulk` | POST | **ADMIN** |
| `/api/reports/calls/{callId}` | GET | VIEWER |
| `/api/reports/calls/{callId}/recording` | GET | VIEWER |
| `/api/reports/calls/{callId}/transcript.txt` | GET | VIEWER |
| `/api/reports/audit/list` | POST | **ADMIN** |
| `/api/reports/dashboard/kpi` | GET | VIEWER |
| `/api/reports/dashboard/timeseries` | GET | VIEWER |
| `/api/reports/dashboard/outcomes` | GET | VIEWER |
| `/api/reports/outcomes-distribution` | GET | VIEWER |
| `/api/reports/hourly-heatmap` | GET | VIEWER |
| `/api/reports/campaign-comparison` | GET | VIEWER |
| `/api/reports/duration-histogram` | GET | VIEWER |
| `/api/reports/dynamics` | GET | VIEWER |
| `/api/reports/funnel` | GET | VIEWER |
| `/api/reports/export` | GET | VIEWER |
| `/api/reports/schedule` | POST | **ADMIN** |
| `/api/reports/schedule/list` | POST | **ADMIN** |
| `/api/reports/schedule/{id}` | DELETE | **ADMIN** |

---

## Disposition (natija) qiymatlari — to'liq, versiyalangan ro'yxat {#disposition-values}

`shared/dialog/Disposition.java`dagi to'liq enum — `disposition` maydoni har doim
shu 10 qiymatdan biri yoki `null` (qo'ng'iroq hali tugamagan/natija yozilmagan)
bo'ladi. Yangi qiymat qo'shilsa, shu jadval **shu commit ichida** yangilanishi
kerak (CLAUDE.md §7) — frontend shu ro'yxatga qarab ishlaydi
(backend-uchun-talablar.md §11).

| Qiymat | Ma'nosi |
|---|---|
| `PROMISE_TO_PAY` | to'lov va'dasi olindi |
| `REFUSED` | rad etdi |
| `NO_ANSWER` | javob bermadi |
| `WRONG_NUMBER` | noto'g'ri raqam / boshqa shaxs |
| `DO_NOT_CALL` | mijoz qayta qo'ng'iroq qilinmasligini so'radi (§11.4) |
| `HUNG_UP` | mijoz suhbat davomida trubkani qo'ydi |
| `TRANSFERRED` | odam operatorga uzatildi |
| `VOICEMAIL` | avtojavobgar (answering machine) aniqlandi |
| `FAILED` | texnik xato (ulanish, STT/TTS/LLM va h.k.) |
| `COMPLETED` | ssenariy qarzga xos bo'lmagan natija bilan normal tugadi (masalan so'rovnoma, ROADMAP A.3) |

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

`CallFilter` `page`/`size`/`orders`dan tashqari quyidagi ixtiyoriy filtr
maydonlarini ham qabul qiladi — hammasi berilmasa oddiy ro'yxat:

| Maydon | Turi | Izoh |
|---|---|---|
| `q` | string | telefon raqami bo'yicha qidiruv (qisman moslik) |
| `campaignId` | long | faqat shu kampaniya qo'ng'iroqlari |
| `disposition` | string | faqat shu natija (masalan `PROMISE_TO_PAY`) |
| `dateFrom` / `dateTo` | ISO-8601 instant | `startedAt` shu oraliqda (`dateFrom` kiritilgan, `dateTo` kiritilmagan) |
| `durationMinSec` / `durationMaxSec` | int | `durationSec` shu oraliqda |
| `ids` | `long[]` | berilsa, boshqa hamma filtr maydoni e'tiborsiz qoldirilib faqat shu `call_attempt` id'lari qaytariladi — asosan `POST /api/reports/calls/export`da tanlangan qatorlarni eksport qilish uchun (pastga qarang), lekin shu ro'yxat endpointida ham bir xil ishlaydi |

**Javob qatori** (`CallRow`, `PageableData<CallRow>` ichida):

```json
{
  "callId": 501,
  "targetId": 501,
  "phone": "998901234567",
  "clientName": "Aziz Karimov",
  "campaignName": "Iyul qarzdorlik",
  "operatorName": "Shohrux Yusupov",
  "language": "uz-UZ",
  "startedAt": "2026-07-31T10:15:00Z",
  "endedAt": "2026-07-31T10:16:34Z",
  "durationSec": 94,
  "disposition": "PROMISE_TO_PAY",
  "hangupCause": "NORMAL_CLEARING",
  "hasRecording": true,
  "summary": "Mijoz 5 avgustgacha 1 500 000 so'm to'lashga va'da berdi.",
  "promisedDate": "05.08.2026",
  "promisedAmount": 1500000,
  "crmNoteId": 98213
}
```

| Maydon | Izoh |
|---|---|
| `callId` | `call_attempt` id — tafsilot va recording endpointlari shuni oladi |
| `clientName` | nishonning `context_data.clientName`sidan (backend-uchun-talablar.md §2) — CSV/API import paytida berilgan bo'lsa; berilmagan bo'lsa `null` |
| `campaignName` | shu nishon tegishli kampaniya nomi |
| `operatorName` | qo'ng'iroq odamga uzatilgan va javob bergan SIP extension ro'yxatdan o'tgan foydalanuvchiga mos kelgan bo'lsa uning ismi; faqat bot ishlagan qo'ng'iroqlarda `null` |
| `endedAt`/`durationSec` | qo'ng'iroq hali davom etayotgan bo'lsa `null` |
| `hangupCause` | Asterisk sabab kodi — `NO_ANSWER` nima uchun sodir bo'lganini tushuntiradi |
| `hasRecording` | shu qo'ng'iroq uchun audio olish mumkinmi |
| `summary` | CRM eslatma matni (LLM tomonidan yaratilgan), hali yaratilmagan bo'lsa `null` |
| `promisedDate` | `LocalDate` (`dd.MM.yyyy`) — mijoz to'lashga va'da bergan sana, bo'lmasa `null` |
| `crmNoteId` | CRM bergan id; `null` — hali CRM tomonidan qabul qilinmagan (outbox hali qayta urinmoqda yoki CRM o'chiq) |

---

## `POST /api/reports/calls/export` — CSV eksport {#calls-export}

**Rol: ADMIN.** Body — xuddi `POST /api/reports/calls/list`dagi kabi
`CallFilter` (yuqoridagi filtr maydonlari); `page`/`size` shart emas, natija
har doim eng ko'p `500` qatorgacha (`FilterInterface.MAX_SIZE`) — undan ko'p
qatorni eksport qilish hozircha qo'llab-quvvatlanmaydi. `ids` berilsa, faqat
o'sha tanlangan qatorlar eksport qilinadi (§ yuqoridagi jadval).

**`ResponseData`ga o'ralmagan** — `Content-Type: text/csv`,
`Content-Disposition: attachment; filename="calls.csv"`. Ustunlar: `call_id,
target_id, phone, client_name, campaign_name, operator_name, language,
started_at, ended_at, duration_sec, disposition, hangup_cause, has_recording,
summary, promised_date, promised_amount, crm_note_id` (`CallRow`ning har bir
maydoni, birma-bir).

---

## `POST /api/reports/calls/bulk` — ommaviy amal {#calls-bulk}

**Rol: ADMIN.** Body (`BulkCallActionRequest`):

```json
{ "action": "retry", "ids": [501, 502, 503] }
```

| Maydon | Turi | Izoh |
|---|---|---|
| `action` | string, `@NotBlank` | `retry` yoki `dnc` (tanlangan qatorlarni eksport qilish uchun `ids` bilan `POST /api/reports/calls/export`ga qarang — u fayl qaytaradi, shuning uchun shu endpointga sig'maydi) |
| `ids` | `long[]`, `@NotEmpty` | `call_attempt` id'lari |

- `retry` — har bir qo'ng'iroqning nishonini darhol qayta navbatga qo'yadi
  (`nextAttemptAt = hozir`, holat `PENDING`) — dialer keyingi tikida oladi.
- `dnc` — har bir qo'ng'iroqning telefon raqamini "qo'ng'iroq qilinmasin"
  ro'yxatiga qo'shadi (kompaniya darajasida, [do-not-call.md](do-not-call.md)
  bilan bir xil ro'yxat).

Boshqa kompaniyaga tegishli yoki mavjud bo'lmagan `id` butun so'rovni
to'xtatmaydi — `failed` ro'yxatiga tushadi.

**Response** (`BulkCallActionResult`):

```json
{ "processed": 2, "failed": [503] }
```

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
  "errorMessage": null,
  "technical": {
    "channelName": "PJSIP/trunk-endpoint-00000012",
    "trunk": "trunk-endpoint",
    "amdResult": "HUMAN",
    "sttProvider": "yandex",
    "ttsProvider": "yandex",
    "ttsVoice": "alena",
    "llmModel": "gemini-3.6-flash",
    "promptTokens": 1840,
    "completionTokens": 320,
    "cachedTokens": 1200,
    "turnCount": 6,
    "avgTurnLatencyMs": 780,
    "maxTurnLatencyMs": 1120,
    "avgLlmLatencyMs": 410,
    "maxLlmLatencyMs": 650
  }
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
| `technical` | "Texnik" tab (§10.5) — `call_technical`da qatori bo'lmagan (bu jadval qo'shilishidan oldingi) qo'ng'iroqlar uchun butunlay `null` |
| `technical.amdResult` | `MACHINE`/`HUMAN`, yoki AMD shu qo'ng'iroq uchun o'chirilgan bo'lsa `null` |
| `technical.ttsProvider`/`ttsVoice` | kampaniyaning sozlangan ovozidan hisoblab olinadi — qo'ng'iroq davomida bir nechta jumla boshqa provayderga fallback qilishi mumkin (til mos kelmasa); bu maydon shu **asosiy** tanlovni ko'rsatadi, jumla darajasidagi fallbackni emas |
| `technical.promptTokens`/`completionTokens`/`cachedTokens` | butun qo'ng'iroq bo'yicha yig'indi (Gemini "thinking" tokenlari `completionTokens`ga qo'shilgan) |
| `technical.avgTurnLatencyMs`/`maxTurnLatencyMs` | mijoz gapirishni to'xtatgandan botning birinchi audiosi navbatga qo'yilgunigacha (§1.3 byudjeti) |
| `technical.avgLlmLatencyMs`/`maxLlmLatencyMs` | faqat LLM chaqiruvining o'zi (TTS/tarmoq vaqtisiz) |

---

## `GET /api/reports/calls/{callId}/recording` — qo'ng'iroq yozuvi

**`ResponseData`ga o'ralmagan** — `302` bilan `GET /api/files/{id}`ga
redirect qiladi ([files.md](files.md)), u yerda audio MinIO'dan olib
streamlanadi — hech qachon xom MinIO URL emas.

Bu endpoint ham, redirect nishoni ham autentifikatsiya talab qiladi
(`X-Api-Key`/Bearer), shuning uchun oddiy `<audio src="...">` ishlamaydi
(brauzer maxsus sarlavha qo'ya olmaydi) — frontend `fetch` bilan blob
sifatida olib, `URL.createObjectURL`ga o'raydi (`requestBlob`,
`src/api/http.ts`); fetch bir xil origin'dagi redirectda sarlavhani
avtomatik saqlab qoladi.

Yozuv yo'q bo'lsa (`hasRecording: false` bo'lgan qo'ng'iroq) — `404`.

---

## `GET /api/reports/calls/{callId}/transcript.txt` — transkriptni TXT sifatida yuklab olish

**`ResponseData`ga o'ralmagan** — `Content-Type: text/plain`,
`Content-Disposition: attachment; filename="call-{id}-transcript.txt"`. Har
bir qator: `[HH:MM:SS] ROLE: matn` (`tsOffsetMs` — qo'ng'iroq boshidan
o'tgan vaqt).

---

## `POST /api/reports/audit/list` — audit jurnali {#audit-log}

**Rol: ADMIN** (yuqoridagi eslatmaga qarang). Body — `AuditFilter`. Saralanadigan
ustunlar: `ID`, `CREATED_AT`, `ACTION`, `ENTITY`. Standart: `ID DESC` (eng
so'nggisi birinchi).

`page`/`size`/`orders`dan tashqari quyidagi ixtiyoriy filtr maydonlarini ham
qabul qiladi (chapdagi filtr paneli uchun) — har biri **aniq moslik**
(`LIKE` emas):

| Maydon | Turi | Izoh |
|---|---|---|
| `actor` | string | masalan `admin-key:ADMIN` — rol qo'shimchasi bilan birga to'liq yozing |
| `action` | string | masalan `CAMPAIGN_START` |
| `entity` | string | masalan `campaign` |

**Javob qatori** (`AuditLog`, `PageableData<AuditLog>` ichida):

```json
{
  "id": 981,
  "actor": "api-key",
  "action": "CAMPAIGN_START",
  "entity": "campaign",
  "entityId": "42",
  "detail": "maxConcurrentCalls=5",
  "createdAt": "2026-07-31T09:00:00Z",
  "ipAddress": "10.0.0.5"
}
```

| Maydon | Izoh |
|---|---|
| `actor` | so'rovni qilgan autentifikatsiyalangan principal (`api-key`/`read-api-key`), yoki dilerning o'z rejalashtirilgan ishi uchun `system` |
| `action` | qisqa kod, masalan `CAMPAIGN_START`, `DNC_REMOVE` |
| `entity`/`entityId` | nima ustida amal bajarilgani (`campaign`, `target`, `call`) va uning id'si (matn sifatida) |
| `detail` | erkin matnli qo'shimcha kontekst |
| `ipAddress` | so'rov qaysi manzildan kelgani; HTTP so'rov tashqarisida (dilerning fon ishi) sodir bo'lgan amallar uchun `null` |

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

---

## Hisobotlar sahifasi grafiklari (`GET /api/reports/...`, §10.10)

`from`/`to`/`campaignId` konventsiyasi yuqoridagi dashboard endpointlari bilan bir xil
(ixtiyoriy, standart oxirgi 7 kun, `from` >= `to` yoki 366 kundan katta oraliq — `400`).

### `GET /api/reports/outcomes-distribution` — "Natijalar taqsimoti" (hisobotlar sahifasi)

`GET /api/reports/dashboard/outcomes` bilan bir xil ma'lumot (bir xil query
parametrlari, bir xil `List<DashboardOutcome>` javob) — hisobotlar sahifasi
o'z nomi ostidagi yo'ldan chaqiradi.

### `GET /api/reports/hourly-heatmap` — "Kun × soat javob foizi"

**Response** — `List<HourlyHeatmapCell>`, har bir kun×soat kombinatsiyasi uchun
bitta qator (bo'sh kataklar qaytarilmaydi):

```json
[ { "dayOfWeek": 1, "hour": 10, "total": 24, "answered": 18, "answerRate": 0.75 } ]
```

| Maydon | Izoh |
|---|---|
| `dayOfWeek` | Postgres `extract(dow)`: `0` = yakshanba .. `6` = shanba |
| `hour` | 0..23, bazada saqlangan (UTC) vaqt bo'yicha |
| `answerRate` | `answered / total`, `total = 0` bo'lsa `0.0` |

### `GET /api/reports/campaign-comparison` — kampaniyalarni solishtirish

Qo'shimcha ixtiyoriy parametr: `campaignIds` (vergul bilan ajratilgan
`long` ro'yxati, masalan `?campaignIds=1,2,3`) — berilmasa, davr ichida
qo'ng'irog'i bo'lgan **barcha** kampaniyalar solishtiriladi.

**Response** — `List<CampaignComparisonRow>`, `totalCalls` bo'yicha kamayish
tartibida:

```json
[
  {
    "campaignId": 42, "campaignName": "Iyul qarzdorlik",
    "totalCalls": 320, "answeredCalls": 250, "answerRate": 0.78,
    "avgDurationSec": 94.3, "promises": 90
  }
]
```

### `GET /api/reports/duration-histogram` — davomiylik taqsimoti

**Response** — `List<DurationHistogramBucket>`, faqat ulangan (`duration_sec`
`null` bo'lmagan) qo'ng'iroqlar bo'yicha, sobit oraliqlar bilan (`0-30, 30-60,
60-120, 120-300, 300-600, 600+` soniya) — hech qanday qo'ng'iroq tushmagan
oraliq ham `count: 0` bilan qaytadi:

```json
[
  { "rangeLabel": "0-30", "rangeStartSec": 0, "rangeEndSec": 30, "count": 12 },
  { "rangeLabel": "600+", "rangeStartSec": 600, "rangeEndSec": null, "count": 3 }
]
```

### `GET /api/reports/dynamics` — "Qo'ng'iroqlar dinamikasi" (Grafik 1) {#get-apireportsdynamics--qongiroqlar-dinamikasi-grafik-1}

Umumiy filtr paneliga mos — `/dashboard/timeseries` bilan bir xil javob
(`List<DashboardBucket>`, yuqoriga qarang), lekin ikkita qo'shimcha ixtiyoriy
parametr bilan:

| Parametr | Turi | Izoh |
|---|---|---|
| `scenarioId` | long | faqat shu ssenariy bo'yicha ishlagan kampaniyalarning qo'ng'iroqlari (`campaign.scenario_id` orqali) |
| `operator` | boolean | `true` — faqat odam operatoriga uzatilgan (`call_result.escalated`) qo'ng'iroqlar, `false` — faqat uzatilmaganlar. Tizimda operatorlar nomma-nom kuzatilmaydi (bitta umumiy operator navbati), shuning uchun bu "qaysi operator" emas, "operatorga uzatildimi" filtri |

### `GET /api/reports/funnel` — voronka (Grafik 6) {#get-apireportsfunnel--voronka-grafik-6}

`from`/`to`/`campaignId` — dashboard konventsiyasi bilan bir xil.

**Response** — `List<FunnelStage>`, doim shu tartibda 5 ta bosqich:

```json
[
  { "stage": "CALL", "count": 500, "rate": 1.0 },
  { "stage": "ANSWERED", "count": 390, "rate": 0.78 },
  { "stage": "PERSON_CONFIRMED", "count": 350, "rate": 0.7 },
  { "stage": "CONVERSATION", "count": 320, "rate": 0.64 },
  { "stage": "RESULT", "count": 210, "rate": 0.42 }
]
```

| Bosqich | Ma'nosi |
|---|---|
| `CALL` | shu oraliqda boshlangan barcha qo'ng'iroqlar |
| `ANSWERED` | o'lchangan davomiylikka ega (ulangan) qo'ng'iroqlar |
| `PERSON_CONFIRMED` | ulangan va `WRONG_NUMBER` deb belgilanmagan (`recordWrongPerson` chaqirilmagan) |
| `CONVERSATION` | yuqoridagisi + kamida bitta LLM navbati bo'lgan (`call_technical.turn_count > 0`) — faqat ochilish xabari suhbat hisoblanmaydi |
| `RESULT` | yakuniy biznes natijasi bilan tugagan: `PROMISE_TO_PAY`, `REFUSED`, `TRANSFERRED`, `COMPLETED`, `DO_NOT_CALL` (`WRONG_NUMBER`, `NO_ANSWER`, `FAILED`, `HUNG_UP`, `VOICEMAIL` kirmaydi) |

`rate` — `count` / `CALL` bosqichining `count`i (0..1), oldingi bosqichga
nisbatan emas — grafik "hammasidan qanchasi shu yergacha yetdi"ni ko'rsatadi.

### `GET /api/reports/export?format=csv|pdf|xlsx` — "⇩ Hisobotni yuklab olish"

Butun hisobotlar sahifasini (KPI, natijalar taqsimoti, voronka, kampaniyalar
taqqoslash) bitta faylga jamlaydi. `from`/`to`/`campaignId` — dashboard
konventsiyasi bilan bir xil; `campaignId` berilsa, kampaniyalar taqqoslash
bo'limi bo'sh qaytadi (kampaniyani o'zi bilan solishtirish ma'nosiz).

| Parametr | Turi | Izoh |
|---|---|---|
| `format` | string, ixtiyoriy | `csv` (standart), `pdf`, yoki `xlsx`; noma'lum format — `400` |

**`ResponseData`ga o'ralmagan** — `Content-Type` va `Content-Disposition`
formatga qarab (`report.csv` / `report.pdf` / `report.xlsx`). `xlsx`da har bir
bo'lim alohida varaqda (`Umumiy`, `Natijalar`, `Voronka`, `Kampaniyalar`).

---

## `📅 Jadval bo'yicha yuborish` (`/api/reports/schedule...`)

Yuqoridagi `export`ning muntazam email orqali avtomatik yuborilishi. Alohida
kontroller (`ReportScheduleController`) — o'qish (`ReportController`)dan
farqli o'laroq bularning barchasi **yozuv** va **ADMIN** talab qiladi.

SMTP `spring.mail.*` (`.env`dagi `SMTP_HOST`/`SMTP_PORT`/`SMTP_USER`/
`SMTP_PASSWORD`) orqali sozlanadi; jo'natish o'zi
`voice-agent.report-schedule.enabled` (standart: **o'chiq**) yoqilmaguncha
ishlamaydi — SMTP sozlanmagan holatda har soatlik tekshiruv shunchaki hech
narsa qilmaydi.

### `POST /api/reports/schedule` — yangi jadval

**Request body** (`CreateReportScheduleRequest`):

```json
{ "email": "boss@uysot.uz", "periodicity": "WEEKLY", "format": "pdf", "campaignId": null }
```

| Maydon | Turi | Majburiymi | Izoh |
|---|---|---|---|
| `email` | string | ✅ (`@Email`) | qabul qiluvchi |
| `periodicity` | `DAILY`\|`WEEKLY`\|`MONTHLY` | ✅ | qanchalik tez-tez yuboriladi — shu davr hisobot qamrab oladigan oyna ham bo'ladi (masalan `WEEKLY` — har safar oxirgi 7 kunlik hisobot) |
| `format` | string | ❌ | `csv`\|`pdf`\|`xlsx`; berilmasa `pdf` |
| `campaignId` | long | ❌ | berilsa hisobot shu kampaniyaga cheklanadi; noma'lum yoki boshqa kompaniyaniki bo'lsa `404` |

**Response** (`ReportSchedule`):

```json
{
  "id": 3, "email": "boss@uysot.uz", "periodicity": "WEEKLY", "format": "pdf",
  "campaignId": null, "enabled": true, "lastSentAt": null,
  "createdAt": "2026-08-02T09:00:00Z"
}
```

### `POST /api/reports/schedule/list` — ro'yxat

Body — `ReportScheduleFilter` (`page`/`size`/`orders`). Saralanadigan
ustunlar: `ID`, `EMAIL`, `PERIODICITY`, `ENABLED`, `CREATED_AT`. Standart:
`ID ASC`.

### `DELETE /api/reports/schedule/{id}` — bekor qilish

Qatorni **o'chirmaydi** — `enabled=false` qiladi (`campaigns`/`inbound-routes`
arxivlash konventsiyasiga mos). Javob — yangilangan `ReportSchedule`
(`enabled: false`). Topilmasa — `404`.
