# Kampaniyalar API

`uz.murodjon.uysotvoice.campaign` · rol: **OPERATOR** (barcha endpoint — ADMIN ham kiradi, rol ierarxiyasi bo'yicha)

Kampaniya yaratish → nishonlarni (targets) yuklash → `start` qilish oqimi.
Dialer navbatdagi tikida o'zi qo'ng'iroq qila boshlaydi. Shuningdek, takroriy (avtomatik davriy) kampaniyalar, ilg'or xususiyatlar (fon shovqini, mid-call SMS, avtojavoblagich xatti-harakati, DTMF, hissiyotga moslashuvchan ovoz) va mijozlar xotirasini qo'lda boshqarish to'liq qo'llab-quvvatlanadi.

Umumiy javob shakli, xatolar va pagination konventsiyasi uchun
[README.md](README.md)ga qarang.

---

## `POST /api/campaigns` — kampaniya yaratish

**Request body** (`CreateCampaignRequest`):

```json
{
  "name": "Iyul qarzdorlik",
  "type": "DEBT_COLLECTION",
  "goalPrompt": "Qarzni undirish, to'lov va'dasini olish",
  "defaultLanguage": "uz-UZ",
  "dialWindowStart": "09:00",
  "dialWindowEnd": "18:00",
  "dialDays": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"],
  "maxAttempts": 3,
  "retryIntervalMinutes": 0,
  "maxConcurrentCalls": 5,
  "ttsVoice": "nigora",
  "dailyCallCap": 500,
  "scenarioId": 1,
  "disclosureEnabled": true,
  "recurrenceType": "WEEKLY",
  "recurringDayOfMonth": null,
  "cronExpression": null,
  "autoResetTargets": true,
  "ambientSound": "CALL_CENTER",
  "midCallSmsEnabled": true,
  "midCallSmsTemplate": "Hurmatli {client_name}, to'lov havolasi: https://pay.uz/bill/123",
  "voicemailAction": "LEAVE_MESSAGE",
  "voicemailMessage": "Assalomu alaykum! {company_name} kompaniyasidan qo'ng'iroq qildik. Iltimos, biz bilan bog'laning.",
  "dtmfInputEnabled": true,
  "emotionAdaptiveVoice": true
}
```

| Maydon | Turi | Majburiymi | Standart qiymat | Izoh |
|---|---|---|---|---|
| `name` | string | ✅ (`@NotBlank`) | — | Kampaniya nomi |
| `type` | `"DEBT_COLLECTION"` \| `"SURVEY"` | ✅ (`@NotNull`) | — | Qat'iy enum, faqat shu ikki qiymat. |
| `goalPrompt` | string | ❌ | `""` | Agentga maqsad sifatida beriladi |
| `defaultLanguage` | string | ❌ | `uz-UZ` | BCP-47, masalan `uz-UZ`, `ru-RU`. Kompaniyaning `supportedLanguages` ro'yxatida bo'lishi shart. |
| `dialWindowStart` / `dialWindowEnd` | `LocalTime` (`HH:mm`) | ❌ | `09:00` / `20:00` | Qo'ng'iroq qilish mumkin bo'lgan soat oralig'i. Kompaniya oralig'i bilan cheklanadi. |
| `dialDays` | `DayOfWeek[]` | ❌ | `["MONDAY", ..., "FRIDAY"]` | Qo'ng'iroq qilish mumkin bo'lgan hafta kunlari |
| `maxAttempts` | int | ❌ | `3` | Bitta nishonga necha marta urinish |
| `retryIntervalMinutes` | int | ❌ | `0` | Javob bermagan/uzilib qolgan mijozga qayta qo'ng'iroq qilishgacha kutiladigan daqiqa. `0` = avtomatik adaptiv kechikish. |
| `maxConcurrentCalls` | int | ❌ | `5` | Bir vaqtda nechta parallel qo'ng'iroq |
| `ttsVoice` | string | ❌ | `null` | `GET /api/tts/voices` dagi ovoz `id`si |
| `dailyCallCap` | int | ❌ | `0` | Kunlik qo'ng'iroqlar soni chegarasi (`0` = cheksiz) |
| `scenarioId` | long | ✅ (`@NotNull`) | — | Ssenariy ID si |
| `disclosureEnabled` | boolean | ❌ | `true` | Qo'ng'iroq boshida avtomatlashtirilgan xizmat ekanligi haqidagi rasmiy eslatma aytilishi |
| `recurrenceType` | enum | ❌ | `ONCE` | Takrorlanish tartibi: `ONCE`, `DAILY`, `WEEKLY`, `MONTHLY`, `CRON` |
| `recurringDayOfMonth` | int (1–31) | ❌ | `null` | `MONTHLY` turi uchun oyning qaysi sanasida qayta ishga tushishi |
| `cronExpression` | string | ❌ | `null` | `CRON` turi uchun 6-qismli cron ifodasi |
| `autoResetTargets` | boolean | ❌ | `false` | Takroriy ishga tushganda barcha tugagan nishonlarni qayta `PENDING` holatiga o'tkazish |
| `ambientSound` | enum | ❌ | `OFF` | Fon tovushi: `OFF`, `OFFICE`, `CALL_CENTER`, `NATURAL_LINE`, `CAFE` |
| `midCallSmsEnabled` | boolean | ❌ | `false` | Suhbat davomida AI tomonidan to'lov/ma'lumot SMS yuborish imkoniyati |
| `midCallSmsTemplate` | string (max 500) | ❌ | `null` | Suhbat davomida yuboriladigan SMS shabloni (`{client_name}`, `{company_name}` teglari bilan) |
| `voicemailAction` | enum | ❌ | `HANGUP` | Avtojavoblagich/AMD aniqlangandagi harakat: `HANGUP`, `LEAVE_MESSAGE`, `IGNORE` |
| `voicemailMessage` | string (max 500) | ❌ | `null` | `LEAVE_MESSAGE` tanlanganda avtojavoblagichga o'qib eshittiriladigan matn |
| `dtmfInputEnabled` | boolean | ❌ | `false` | Mijoz telefon klaviaturasida tugma (DTMF 0-9, *, #) bosganda AI dialogiga uzatish |
| `emotionAdaptiveVoice` | boolean | ❌ | `true` | Mijoz jahli chiqqanda yoki asabiylashganda bot ovozining ohang va tezligini yumshatish |

**Response** (`CreateCampaignResponse`):

```json
{ "data": { "id": 42, "status": "DRAFT" }, "message": null, "messageCode": null, "accept": true, "errors": null }
```

---

## `POST /api/campaigns/list` — ro'yxat

Body — `CampaignFilter` (`page`/`size`/`orders`, [README §3](README.md#3-royxatfiltr-endpointlari-pagination)ga qarang).
Saralanadigan ustunlar: `ID`, `NAME`, `TYPE`, `STATUS`. Standart: `ID ASC`.
Ixtiyoriy `status` maydoni berilsa, faqat shu holatdagi kampaniyalar
qaytariladi (masalan dashboard "faol kampaniyalar" bloki uchun `"status": "ACTIVE"`).

**Javob qatori** (`CampaignRow`, `PageableData<CampaignRow>` ichida):

```json
{
  "id": 42,
  "name": "Iyul qarzdorlik",
  "type": "DEBT_COLLECTION",
  "status": "ACTIVE",
  "goalPrompt": "...",
  "defaultLanguage": "uz-UZ",
  "dialWindowStart": "09:00",
  "dialWindowEnd": "18:00",
  "dialDays": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"],
  "maxAttempts": 3,
  "retryIntervalMinutes": 0,
  "maxConcurrentCalls": 5,
  "ttsVoice": "nigora",
  "dailyCallCap": 500,
  "scenarioId": 1,
  "scenarioName": "Qarz undirish (standart)",
  "companyId": 1,
  "disclosureEnabled": true,
  "recurrenceType": "WEEKLY",
  "recurringDayOfMonth": null,
  "cronExpression": null,
  "autoResetTargets": true,
  "nextRunAt": "2026-08-31T09:00:00Z",
  "lastRunAt": "2026-08-24T09:00:00Z",
  "ambientSound": "CALL_CENTER",
  "midCallSmsEnabled": true,
  "midCallSmsTemplate": "Hurmatli {client_name}, to'lov havolasi: https://pay.uz/bill/123",
  "voicemailAction": "LEAVE_MESSAGE",
  "voicemailMessage": "Assalomu alaykum! {company_name} kompaniyasidan qo'ng'iroq qildik...",
  "dtmfInputEnabled": true,
  "emotionAdaptiveVoice": true,
  "createdBy": 7,
  "createdByName": "Aziz Karimov"
}
```

`status` — `DRAFT` / `ACTIVE` / `PAUSED` / `COMPLETED` / `ARCHIVED`.

---

## `GET /api/campaigns/{id}` — bitta kampaniya

Javob — bitta `CampaignRow` (yuqoridagi to'liq shakl). Topilmasa `404`.

---

## `PUT /api/campaigns/{id}` — tahrirlash

**Request body** (`UpdateCampaignRequest`):

```json
{
  "name": "Iyul qarzdorlik (yangilangan)",
  "goalPrompt": "Qarzni undirish, to'lov va'dasini olish",
  "defaultLanguage": "uz-UZ",
  "dialWindowStart": "09:00",
  "dialWindowEnd": "18:00",
  "dialDays": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"],
  "maxAttempts": 3,
  "retryIntervalMinutes": 0,
  "maxConcurrentCalls": 5,
  "ttsVoice": "nigora",
  "dailyCallCap": 500,
  "disclosureEnabled": true,
  "recurrenceType": "DAILY",
  "recurringDayOfMonth": null,
  "cronExpression": null,
  "autoResetTargets": true,
  "ambientSound": "OFFICE",
  "midCallSmsEnabled": true,
  "midCallSmsTemplate": "To'lov cheki: https://pay.uz/bill/123",
  "voicemailAction": "HANGUP",
  "voicemailMessage": null,
  "dtmfInputEnabled": true,
  "emotionAdaptiveVoice": true
}
```

---

## `DELETE /api/campaigns/{id}` — arxivlash

Holatni `ARCHIVED` ga o'tkazadi. Javob (`CampaignStatusResponse`):

```json
{ "campaignId": 42, "status": "ARCHIVED" }
```

---

## `POST /api/campaigns/{id}/clone` — nusxalash

Barcha parametrlar va ilg'or sozlamalarni yangi `DRAFT` kampaniyaga nusxalaydi (nishonlar ko'chirilmaydi). Javob — yangi `CampaignRow`.

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

---

## `POST /api/campaigns/{id}/targets/csv/preview` — CSV oldindan ko'rish {#csv-preview}

CSV faylni tahlil qilib, sarlavha maydonlari moslashuvi va namuna qatorlarni qaytaradi (bazaga yozmaydi).

---

## `POST /api/campaigns/{id}/targets/list` — nishonlar ro'yxati

Body — `TargetFilter`. Saralash: `ID`, `PHONE`, `STATUS`, `ATTEMPTS`.

**Javob qatori** (`CampaignTarget`):

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

---

## `GET /api/campaigns/{campaignId}/targets/{targetId}/memory` — nishon xotirasi va eslatmalarini ko'rish

Mijozning o'tgan suhbatlar xotirasi, AI dialog xulosasi, operator eslatmalari va qo'lda kiritilgan faktlarini olish.

**Response** (`TargetMemoryDto`):

```json
{
  "data": {
    "targetId": 501,
    "clientId": 1001,
    "phone": "998901234567",
    "clientName": "Aziz Karimov",
    "dialogMemorySummary": "Mijoz 25-sanada maosh olishini va to'liq to'lashini aytdi.",
    "operatorNotes": "Mijoz bilan xushmuomala gaplashish kerak, ertalab band bo'ladi.",
    "lastInteractions": "2026-08-20: Qisman to'lov va'da qildi; 2026-08-27: Bandligini bildirdi",
    "manualFacts": {
      "preferredTime": "14:00-18:00",
      "discountOffered": true
    }
  },
  "message": null,
  "messageCode": null,
  "accept": true,
  "errors": null
}
```

---

## `PUT /api/campaigns/{campaignId}/targets/{targetId}/memory` — nishon xotirasi va eslatmalarini yangilash

Operator yoki CRM integratsiyasi tomonidan mijoz xotirasi, xulosa yoki eslatmalarni to'g'ridan-to'g'ri yangilash.

**Request body** (`UpdateTargetMemoryRequest`):

```json
{
  "operatorNotes": "Mijoz faqat SMS orqali to'lov havolasini so'radi",
  "dialogMemorySummary": "O'tgan suhbatda ijobiy munosabat bildirdi",
  "manualFacts": {
    "preferredTime": "15:00",
    "specialAgreement": "50% chegirma kutilmoqda"
  }
}
```

---

## `POST /api/campaigns/{id}/start` / `POST /api/campaigns/{id}/pause`

Body yo'q. Javob (`CampaignStatusResponse`):

```json
{ "campaignId": 42, "status": "ACTIVE" }
```

---

## `POST /api/targets/{id}/do-not-call` — nishonni DNC qilish {#post-apitargetsiddo-not-call}

Bitta nishonni "qo'ng'iroq qilinmasin" deb belgilaydi.
