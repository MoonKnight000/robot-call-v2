# Hisobotlar va Tahlillar (Reports & Analytics) API

`uz.murodjon.robotcallv2.report` · rol: **VIEWER / OPERATOR / ADMIN**

Qo'ng'iroqlar tarixi, to'liq transkriptlar, audio yozuvlar, dashboard KPI ko'rsatkichlari, dinamika, soatlik issiqlik xaritasi (Heatmap), kampaniyalarni taqqoslash, audit jurnallari va avtomatlashtirilgan davriy hisobotlar.

---

## 1. Qo'ng'iroqlar Ro'yxati va Tafsilotlari

### `POST /api/reports/calls/list` — Barcha qo'ng'iroqlar ro'yxati
Kompaniya bo'yicha qo'ng'iroqlarni sana, kampaniya, disposition, sentiment va muddat bo'yicha filtrlash.

**Request Body** (`CallFilter`):
```json
{
  "page": 0,
  "size": 20,
  "campaignId": 12,
  "disposition": "PROMISE_TO_PAY",
  "sentiment": "POSITIVE",
  "from": "2026-09-01T00:00:00Z",
  "to": "2026-09-05T23:59:59Z",
  "search": "998901234567"
}
```

**Response** (`PageableData<CallRow>`):
```json
{
  "accept": true,
  "data": {
    "items": [
      {
        "id": 1052,
        "campaignId": 12,
        "campaignName": "Kechikkan to'lovlar - Mart 2026",
        "phone": "+998901234567",
        "clientName": "Sardor Alimov",
        "disposition": "PROMISE_TO_PAY",
        "durationSeconds": 142,
        "sentiment": "POSITIVE",
        "qaScore": 95,
        "startedAt": "2026-09-05T10:15:00Z",
        "recordingAvailable": true
      }
    ],
    "page": 0,
    "size": 20,
    "total": 1,
    "totalPages": 1
  },
  "messageCode": "SUCCESS",
  "errors": null
}
```

---

### `POST /api/reports/campaigns/{id}/calls/list` — Bitta kampaniya qo'ng'iroqlari
Faqat ko'rsatilgan kampaniya ID'si bo'yicha qo'ng'iroqlarni filtrlash. So'rov shakli yuqoridagi `CallFilter` bilan bir xil.

---

### `GET /api/reports/calls/{callId}` — Bitta qo'ng'iroq + to'liq transkript

**Response** (`CallDetail`):
```json
{
  "accept": true,
  "data": {
    "call": {
      "id": 1052,
      "campaignId": 12,
      "campaignName": "Kechikkan to'lovlar - Mart 2026",
      "phone": "+998901234567",
      "clientName": "Sardor Alimov",
      "disposition": "PROMISE_TO_PAY",
      "durationSeconds": 142,
      "sentiment": "POSITIVE",
      "startedAt": "2026-09-05T10:15:00Z"
    },
    "transcript": [
      { "seq": 1, "role": "AGENT", "text": "Assalomu alaykum Sardor aka! Men Uysot kompaniyasidan qo'ng'iroq qilyapman...", "dialogState": "GREETING", "tsOffsetMs": 0, "confidence": null },
      { "seq": 2, "role": "CLIENT", "text": "Assalomu alaykum, eshitaman", "dialogState": null, "tsOffsetMs": 3200, "confidence": 0.96 }
    ],
    "reasonCode": "TEMPORARY_HARDSHIP",
    "sentiment": "POSITIVE",
    "qaScore": 95,
    "commitmentScore": 90,
    "callbackAt": "2026-09-07T14:00:00Z",
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
  "messageCode": "SUCCESS",
  "errors": null
}
```

| Maydon | Turi | Izoh |
|---|---|---|
| `qaScore` | int (0–100) | AI Agent suhbat sifati va qoidalariga rioya qilish bahosi |
| `commitmentScore` | int (0–100) | Mijozning to'lov/kelishuvga sodiqlik va rozilik darajasi |
| `sentiment` | enum | `POSITIVE`, `NEUTRAL`, `NEGATIVE`, `ANGRY` |
| `callbackAt` | ISO timestamp | Mijoz so'ragan qayta qo'ng'iroq vaqti |

---

### `GET /api/reports/calls/{callId}/recording` — Qo'ng'iroq audio yozuvi

Xom WAV fayl oqimini qaytaradi (`ResponseEntity<Resource>`). HTTP `Range: bytes=<start>-<end>` sarlavhasini to'liq qo'llab-quvvatlaydi (audio pleerda vaqt bo'ylab o'tish imkoniyati).

* **Content-Type**: `audio/wav`
* **Accept-Ranges**: `bytes`
* **Status**: `200 OK` yoki `206 Partial Content`

---

### `GET /api/reports/calls/{callId}/transcript.txt` — Transkriptni matn ko'rinishida yuklab olish

Suhbat dialogini qatorba-qator toza `.txt` formatida qaytaradi.

---

### `POST /api/reports/calls/export` — Qo'ng'iroqlarni Excel/CSV eksport qilish

Ko'rsatilgan filtrlar bo'yicha barcha qo'ng'iroqlar hisobotini fayl ko'rinishida yuklab olish.

---

### `POST /api/reports/calls/bulk` — Ommaviy amallar bajarish

Bir nechta qo'ng'iroqlar bo'yicha guruhli amallarni bajarish (masalan qayta terish navbatiga qo'yish yoki DNC ro'yxatiga olish).

**Request Body** (`BulkCallActionRequest`):
```json
{
  "callIds": [1051, 1052, 1053],
  "action": "RETRY"
}
```

---

## 2. Analitika va Dashboard Vizualizatsiyasi

### `GET /api/reports/dashboard/kpi` — Asosiy biznes KPI ko'rsatkichlari

**Query Parametrlari:**
* `from` — Boshlanish sanasi (`ISO-8601`)
* `to` — Tugash sanasi (`ISO-8601`)
* `campaignId` — (ixtiyoriy) kampaniya bo'yicha cheklash

**Response** (`DashboardKpi`):
```json
{
  "accept": true,
  "data": {
    "totalCalls": 1250,
    "completedCalls": 980,
    "answeredCalls": 1050,
    "totalDurationSeconds": 147000,
    "avgDurationSeconds": 140,
    "answerRate": 0.84,
    "successRate": 0.68,
    "avgQaScore": 92.4,
    "avgCommitmentScore": 81.2,
    "totalCost": 425000.0
  },
  "messageCode": "SUCCESS",
  "errors": null
}
```

---

### `GET /api/reports/dashboard/timeseries` — Vaqt bo'yicha qo'ng'iroqlar dinamikasi

Grafiklar uchun kunlik yoki soatlik kesimdagi qo'ng'iroqlar soni va muvaffaqiyat darajasi.

---

### `GET /api/reports/dashboard/outcomes` va `GET /api/reports/outcomes-distribution` — Yakuniy natijalar taqsimoti

Qo'ng'iroq natijalari (dispositions: `PROMISE_TO_PAY`, `REFUSED`, `NO_ANSWER`, `VOICEMAIL` va h.k.) foiz va miqdor bo'yicha prugoviy (Pie/Donut chart) diagramma uchun.

---

### `GET /api/reports/hourly-heatmap` — Soatlik issiqlik xaritasi (Heatmap)

Hafta kunlari va soatlar kesimida qaysi vaqtda mijozlar go'shakni ko'proq ko'tarishi va to'lovga rozi bo'lishini ko'rsatuvchi matritsa. Eng samarali qo'ng'iroq oynalarini tanlash uchun ishlatiladi.

---

### `GET /api/reports/campaign-comparison` — Kampaniyalarni taqqoslash

Bir nechta kampaniyalarning KPI va samaradorlik parametrlarini yonma-yon solishtirish.

---

### `GET /api/reports/duration-histogram` — Davomiylik taqsimoti gistogrammasi

Suhbatlarning davomiylik intervallari (0-30s, 30-60s, 1-2 daqiqa, 2-5 daqiqa) bo'yicha taqsimoti.

---

## 3. Rejalashtirilgan Hisobotlar (`/api/reports/schedule`)

Menejer yoki tahlilchilar elektron pochtasiga davriy (kunlik, haftalik, oylik) avtomatik hisobot jo'natish.

### `POST /api/reports/schedule` — Yangi hisobot jadvalini yaratish

**Request Body** (`CreateReportScheduleRequest`):
```json
{
  "email": "director@uysot.uz",
  "periodicity": "WEEKLY",
  "format": "xlsx",
  "campaignId": 12
}
```

| Maydon | Turi | Majburiymi | Izoh |
|---|---|---|---|
| `email` | `string` | ✅ | Hisobot yuboriladigan elektron pochta |
| `periodicity` | `string` | ✅ | Davriylik: `DAILY`, `WEEKLY`, `MONTHLY` |
| `format` | `string` | ❌ | Fayl formati: `xlsx`, `pdf`, `csv` (sukut bo'yicha `pdf`) |
| `campaignId` | `number` | ❌ | Ma'lum bir kampaniya uchun; ko'rsatilmasa barcha kampaniyalar bo'yicha |

---

### `POST /api/reports/schedule/list` — Rejalashtirilgan hisobotlar ro'yxati

Mavjud barcha faol hisobot jo'natish jadvallarini ko'rish.

**Request Body** (`ReportScheduleFilter`):
```json
{
  "page": 0,
  "size": 20
}
```

---

### `DELETE /api/reports/schedule/{id}` — Hisobot jadvalini to'xtatish / o'chirish

Ko'rsatilgan ID'li avtomatik hisobot jo'natish jadvalini bekor qiladi.

---

## 4. Tizim Audit Jurnali (`/api/reports/audit/list`)

Foydalanuvchilar va operatorlar tomonidan tizimda amalga oshirilgan barcha xavfsizlik va boshqaruv harakatlari (kampaniya yaratish, o'chirish, sozlamalarni o'zgartirish, foydalanuvchi qo'shish).

**Request Body** (`AuditFilter`):
```json
{
  "page": 0,
  "size": 20,
  "action": "CAMPAIGN_UPDATE",
  "from": "2026-09-01T00:00:00Z",
  "to": "2026-09-05T23:59:59Z"
}
```
