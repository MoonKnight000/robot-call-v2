# Kampaniyalar API

`uz.murodjon.uysotvoice.campaign` · rol: **ADMIN** (barcha endpoint)

Kampaniya yaratish → nishonlarni (targets) yuklash → `start` qilish oqimi.
Dialer navbatdagi tikida o'zi qo'ng'iroq qila boshlaydi.

Umumiy javob shakli, xatolar va pagination konventsiyasi uchun
[README.md](README.md)ga qarang.

---

## `POST /api/campaigns` — kampaniya yaratish

**Request body** (`CreateCampaignRequest`):

```json
{
  "name": "Iyul qarzdorlik",
  "type": "debt_collection",
  "goalPrompt": "Qarzni undirish, to'lov va'dasini olish",
  "defaultLanguage": "uz-UZ",
  "dialWindowStart": "09:00:00",
  "dialWindowEnd": "18:00:00",
  "dialDays": "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY",
  "maxAttempts": 3,
  "retryIntervalHours": 24,
  "maxConcurrentCalls": 5,
  "ttsVoice": "nigora",
  "dailyCallCap": 500
}
```

| Maydon | Turi | Majburiymi | Izoh |
|---|---|---|---|
| `name` | string | ✅ (`@NotBlank`) | — |
| `type` | string | ❌ | erkin matn, masalan `debt_collection` |
| `goalPrompt` | string | ❌ | agentga maqsad sifatida beriladi |
| `defaultLanguage` | string | ❌ | BCP-47, masalan `uz-UZ`, `ru-RU` |
| `dialWindowStart` / `dialWindowEnd` | `LocalTime` (`HH:mm:ss`) | ❌ | qo'ng'iroq qilish mumkin bo'lgan soat oralig'i |
| `dialDays` | string | ❌ | vergul bilan ajratilgan `DayOfWeek` nomlari (`MONDAY,...`); berilmasa Dush-Juma |
| `maxAttempts` | int | ❌ | bitta nishonga necha marta urinish |
| `retryIntervalHours` | int | ❌ | urinishlar orasidagi soat |
| `maxConcurrentCalls` | int | ❌ | bir vaqtda nechta qo'ng'iroq |
| `ttsVoice` | string | ❌ | `GET /api/tts/voices`dagi `id`; noma'lum id rad etiladi; bo'sh bo'lsa standart provayder ishlaydi |
| `dailyCallCap` | int | ❌ | kunlik qo'ng'iroq chegarasi (xarajat nazorati); `0` = cheksiz |

**Response** (`CreateCampaignResponse`):

```json
{ "data": { "id": 42, "status": "DRAFT" }, "message": null, "accept": true, "errors": null }
```

---

## `POST /api/campaigns/list` — ro'yxat

Body — `CampaignFilter` (`page`/`size`/`orders`, [README §3](README.md#3-royxatfiltr-endpointlari-pagination)ga qarang).
Saralanadigan ustunlar: `ID`, `NAME`, `TYPE`, `STATUS`. Standart: `ID ASC`.

**Javob qatori** (`CampaignRow`, `PageableData<CampaignRow>` ichida):

```json
{
  "id": 42,
  "name": "Iyul qarzdorlik",
  "type": "debt_collection",
  "status": "ACTIVE",
  "goalPrompt": "...",
  "defaultLanguage": "uz-UZ",
  "dialWindowStart": "09:00:00",
  "dialWindowEnd": "18:00:00",
  "dialDays": "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY",
  "maxAttempts": 3,
  "retryIntervalHours": 24,
  "maxConcurrentCalls": 5,
  "ttsVoice": "nigora",
  "dailyCallCap": 500
}
```

`status` — `DRAFT` / `ACTIVE` / `PAUSED` / `COMPLETED`.

---

## `GET /api/campaigns/{id}` — bitta kampaniya

Javob — bitta `CampaignRow` (yuqoridagi shakl). Topilmasa `404`.

---

## `POST /api/campaigns/{id}/targets` — nishon qo'shish (JSON)

**Request body** — `AddTargetRequest` ro'yxati:

```json
[
  {
    "clientId": 1001,
    "phone": "998901234567",
    "language": "uz-UZ",
    "contextData": { "clientName": "Aziz Karimov", "debtAmount": 1500000, "currency": "so'm", "dueDate": "2026-07-01", "contractNumber": "UY-2026-00123" }
  }
]
```

| Maydon | Turi | Majburiymi | Izoh |
|---|---|---|---|
| `clientId` | long | ✅ (`@Positive`) | tashqi (CRM) mijoz id |
| `phone` | string | ✅ (`@NotBlank`) | — |
| `language` | string | ❌ | bo'sh bo'lsa kampaniyaning `defaultLanguage`si ishlatiladi |
| `contextData` | erkin JSON obyekt | ❌ | agentga fakt sifatida beriladi (qarz summasi, muddat va h.k.) |

**Response** (`AddTargetsResponse`):

```json
{ "campaignId": 42, "added": 1, "targetIds": [501] }
```

---

## `POST /api/campaigns/{id}/targets/csv` — nishonlarni CSV'dan yuklash {#csv-import}

`Content-Type: text/csv` (yoki `text/plain`), body — CSV faylning o'zi:

```csv
clientId,phone,language,clientName,debtAmount,currency,dueDate,contractNumber
1001,998901234567,uz-UZ,Aziz Karimov,1500000,so'm,2026-07-01,UY-2026-00123
```

Ustunlar sarlavha nomi bo'yicha moslashtiriladi (tartib muhim emas).
`clientName`/`debtAmount`/`currency`/`dueDate`/`contractNumber` kabi ustunlar
`contextData` ichiga faktlar sifatida yig'iladi.

**Response** (`TargetImportResult`):

```json
{
  "campaignId": 42,
  "added": 98,
  "targetIds": [501, 502, "..."],
  "errors": [ { "line": 15, "message": "phone: must not be blank" } ],
  "unknownColumns": ["extraColumn"]
}
```

Xato qatorlar (`errors`) import qilinmaydi, qolgan hammasi yuklanadi — "hammasi
yoki hech narsa" emas.

---

## `POST /api/campaigns/{id}/targets/list` — nishonlar ro'yxati

Body — `TargetFilter`. Saralanadigan ustunlar: `ID`, `PHONE`, `STATUS`,
`ATTEMPTS`. Standart: `ID ASC`.

**Javob qatori** (`TargetRow`):

```json
{
  "id": 501,
  "campaignId": 42,
  "clientId": 1001,
  "phone": "998901234567",
  "language": "uz-UZ",
  "contextData": "{\"clientName\":\"Aziz Karimov\", ...}",
  "status": "PENDING",
  "attempts": 0,
  "doNotCall": false
}
```

`contextData` — JSON **string** sifatida keladi (obyekt emas) — kerak bo'lsa
`JSON.parse` qiling.

---

## `POST /api/campaigns/{id}/start` / `POST /api/campaigns/{id}/pause`

Body yo'q. Javob (`CampaignStatusResponse`):

```json
{ "campaignId": 42, "status": "ACTIVE" }
```

(`pause`da `status: "PAUSED"`.)

---

## `POST /api/targets/{id}/do-not-call` — nishonni DNC qilish {#post-apitargetsiddo-not-call}

Bitta nishonni "qo'ng'iroq qilinmasin" deb belgilaydi (kampaniyaning o'zi emas,
faqat shu nishon). Body yo'q.

**Response** (`DoNotCallResponse`):

```json
{ "targetId": 501, "doNotCall": true }
```

Butun DNC (kompaniya darajasidagi opt-out) ro'yxati uchun
[do-not-call.md](do-not-call.md)ga qarang — bu ikkisi bog'liq, lekin bir xil
emas: bu endpoint faqat shu bitta nishonni kampaniya ichida belgilaydi.
