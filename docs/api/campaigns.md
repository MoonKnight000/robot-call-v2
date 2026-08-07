# Kampaniyalar API

`uz.murodjon.uysotvoice.campaign` · rol: **OPERATOR** (barcha endpoint — ADMIN ham kiradi, rol ierarxiyasi bo'yicha)

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
  "dialWindowStart": "09:00",
  "dialWindowEnd": "18:00",
  "dialDays": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"],
  "maxAttempts": 3,
  "retryIntervalMinutes": 0,
  "maxConcurrentCalls": 5,
  "ttsVoice": "nigora",
  "dailyCallCap": 500,
  "scenarioId": 1,
  "disclosureEnabled": true
}
```

| Maydon | Turi | Majburiymi | Izoh |
|---|---|---|---|
| `name` | string | ✅ (`@NotBlank`) | — |
| `type` | `"DEBT_COLLECTION"` \| `"SURVEY"` | ✅ (`@NotNull`) | qat'iy enum, faqat shu ikki qiymat. Ilgari bo'sh/berilmagan bo'lsa `DEBT_COLLECTION`ga sukut bo'yicha almashtirilar edi (backend-uchun-talablar.md §9a) — bu xatti-harakat olib tashlandi, endi bo'sh qiymat `400` qaytaradi |
| `goalPrompt` | string | ❌ | agentga maqsad sifatida beriladi |
| `defaultLanguage` | string | ❌ | BCP-47, masalan `uz-UZ`, `ru-RU`. Kompaniyaning `CompanyConfig.supportedLanguages` ro'yxatida bo'lishi shart — bo'lmasa `400`; berilmasa shu ro'yxatning birinchisi (default til) ishlatiladi. Batafsil: [companies.md](companies.md). |
| `dialWindowStart` / `dialWindowEnd` | `LocalTime` (`HH:mm`) | ❌ | qo'ng'iroq qilish mumkin bo'lgan soat oralig'i; berilmasa `09:00`/`20:00`. Kompaniyaning `CompanyConfig.dialWindowStart/End` oralig'idan tashqariga chiqmasligi kerak (§B.3 — kompaniya darajasidagi qat'iy shift) — chiqsa `400`. Batafsil: [companies.md](companies.md). |
| `dialDays` | `DayOfWeek[]` | ❌ | qo'ng'iroq qilish mumkin bo'lgan hafta kunlari (`["MONDAY", ...]`); berilmasa yoki bo'sh bo'lsa Dush-Juma |
| `maxAttempts` | int | ❌ | bitta nishonga necha marta urinish |
| `retryIntervalMinutes` | int | ❌ | javob bermagan/uzilib qolgan mijozga qayta qo'ng'iroq qilishgacha necha **daqiqa** kutilsin. Berilgan musbat qiymat barcha natijalar uchun ishlaydi. `0` (default) — kampaniyada tanlov yo'q, tizim natijaga qarab tanlaydi: javob bermadi 180 daqiqa, texnik xato 15 daqiqa, avtojavob 1200 daqiqa (`voice-agent.dialer.retry.*`). Har ikki holda ham hisoblangan vaqt kampaniyaning qo'ng'iroq oynasi ichiga suriladi |
| `maxConcurrentCalls` | int | ❌ | bir vaqtda nechta qo'ng'iroq |
| `ttsVoice` | string | ❌ | `GET /api/tts/voices`dagi `id`; noma'lum id rad etiladi; bo'sh bo'lsa standart provayder ishlaydi |
| `dailyCallCap` | int | ❌ | kunlik qo'ng'iroq chegarasi (xarajat nazorati); `0` = cheksiz |
| `scenarioId` | long | ✅ (`@NotNull`) | `GET/POST /api/scenarios/list`dagi ssenariy `id`si (ROADMAP A.3) — kampaniyaning butun umri davomida o'zgarmaydi; noma'lum yoki boshqa kompaniyaniki bo'lsa `404`. `contextData`dagi maydonlar shu ssenariyning `factSchema`siga mos kelishi kerak — batafsil [scenarios.md](scenarios.md)da |
| `disclosureEnabled` | boolean | ❌ | qo'ng'iroq boshida "Assalomu alaykum! Bu &lt;kompaniya nomi&gt; kompaniyasining avtomatik ovozli xizmati..." xabari aytilsinmi (§11.1). Kompaniya nomi kampaniya egasining `company.name` qiymatidan olinadi — kodda qat'iy yozilmagan. Berilmasa `true` (yoqilgan) |

**Response** (`CreateCampaignResponse`):

```json
{ "data": { "id": 42, "status": "DRAFT" }, "message": null, "messageCode": null, "accept": true, "errors": null }
```

---

## `POST /api/campaigns/list` — ro'yxat

Body — `CampaignFilter` (`page`/`size`/`orders`, [README §3](README.md#3-royxatfiltr-endpointlari-pagination)ga qarang).
Saralanadigan ustunlar: `ID`, `NAME`, `TYPE`, `STATUS`. Standart: `ID ASC`.
Ixtiyoriy `status` maydoni berilsa, faqat shu holatdagi kampaniyalar
qaytariladi (masalan dashboard "faol kampaniyalar" bloki uchun `"status":
"ACTIVE"`).

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
  "createdBy": 7,
  "createdByName": "Aziz Karimov"
}
```

`status` — `DRAFT` / `ACTIVE` / `PAUSED` / `COMPLETED` / `ARCHIVED`. `scenarioName` —
`scenarioId`dan hal qilingan (backend-uchun-talablar.md §16), ssenariy o'chirilgan
bo'lsa `null`. `createdBy`/`createdByName` — kampaniyani yaratgan `app_user`; ikkalasi
ham `null` bo'lishi mumkin: `X-Api-Key` orqali (shaxssiz) yaratilgan yoki bu ustun
ishga tushirilishidan oldin yaratilgan kampaniyalar uchun.

---

## `GET /api/campaigns/{id}` — bitta kampaniya

Javob — bitta `CampaignRow` (yuqoridagi shakl). Topilmasa `404`.

---

## `PUT /api/campaigns/{id}` — tahrirlash

**Request body** (`UpdateCampaignRequest`) — `POST /api/campaigns` bilan bir
xil maydonlar, `type`, boshlang'ich `scriptConfig` va `scenarioId`dan tashqari
(bular faqat yaratishda beriladi — ssenariyni keyinroq almashtirib bo'lmaydi,
boshqa ssenariy uchun yangi kampaniya yarating, ROADMAP A.3):

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
  "disclosureEnabled": true
}
```

`name` majburiy (`@NotBlank`), `ttsVoice` yana bir marta katalog bo'yicha
tekshiriladi (noma'lum id — `400`). To'liq tahrirlash bo'lgani uchun
`disclosureEnabled` har safar aniq yuborilishi kerak (yaratishdan farqli
o'laroq, bu yerda `omit` qilib bo'lmaydi). Javob — yangilangan `CampaignRow`
(yuqoridagi shakl). Topilmasa (yoki boshqa kompaniyaniki bo'lsa) — `404`.

---

## `DELETE /api/campaigns/{id}` — arxivlash

Kartochkadagi `⋯` menyusi. Qatorni **o'chirmaydi** — loyihaning
buzg'unchi-SQL'ga qarshilik konventsiyasiga mos ravishda holatni
`ARCHIVED`ga o'zgartiradi, shunda kampaniyaning nishonlari/qo'ng'iroqlari/
transkriptlari hisobotlarda saqlanib qoladi. Body yo'q. Javob
(`CampaignStatusResponse`):

```json
{ "campaignId": 42, "status": "ARCHIVED" }
```

Topilmasa — `404`.

---

## `POST /api/campaigns/{id}/clone` — nusxalash

Body yo'q. Manba kampaniyaning konfiguratsiyasini (ssenariy, ish oynasi,
til, urinishlar, ovoz va h.k.) yangi kampaniyaga nusxalaydi — **nishonlar
(targets) ko'chirilmaydi**. Yangi kampaniya har doim `status: "DRAFT"`,
nomi manba nomi + `" (nusxa)"`, `createdBy` esa manba yaratuvchisi emas —
nusxalashni bajargan joriy foydalanuvchi. Javob — yangi `CampaignRow`
(yuqoridagi `GET /api/campaigns/{id}` shakli). Manba topilmasa (yoki boshqa
kompaniyaniki bo'lsa) — `404`.

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

## `POST /api/campaigns/{id}/targets/csv/preview` — CSV oldindan ko'rish {#csv-preview}

"Faylni yukla → ustunlarni moslashtir → tasdiqla" ustasining birinchi qadami
(backend-uchun-talablar.md §2, §10.6) — [yuqoridagi](#csv-import) bilan **bir
xil** so'rov shakli (`Content-Type: text/csv`, body — CSV faylning o'zi), lekin
**hech narsani saqlamaydi**: faqat ustun moslashtirish, dastlabki qatorlar va
xatolarni qaytaradi. Operator tasdiqlagach xuddi shu fayl yuqoridagi
`POST /api/campaigns/{id}/targets/csv` ga (haqiqiy import uchun) yuboriladi —
alohida `mapping` parametri kerak emas, ustun moslashtirish ikkalasida ham bir
xil qat'iy qoidalar bilan avtomatik ishlaydi.

**Response** (`TargetCsvPreview`):

```json
{
  "columns": [
    { "header": "clientId", "mappedField": "clientId" },
    { "header": "phone", "mappedField": "phone" },
    { "header": "extraColumn", "mappedField": null }
  ],
  "sampleRows": [
    { "line": 2, "clientId": 1001, "phone": "998901234567", "language": "uz-UZ",
      "contextJson": "{\"clientName\":\"Aziz Karimov\"}" }
  ],
  "totalRows": 98,
  "errors": [ { "line": 15, "message": "phone: must not be blank" } ],
  "unknownColumns": ["extraColumn"]
}
```

`columns` — har bir CSV sarlavhasi qaysi maydonga moslashtirilgani
(`clientId`/`phone`/`language`, yoki `context_data` ichidagi erkin kalit),
moslashtirilmagan bo'lsa `mappedField: null`. `sampleRows` — birinchi 10 ta
muvaffaqiyatli o'qilgan qator (`totalRows` esa hammasi, ko'rsatilganidan
ko'p bo'lishi mumkin). Kampaniya topilmasa (yoki boshqa kompaniyaniki
bo'lsa) — `404`.

---

## `POST /api/campaigns/{id}/targets/list` — nishonlar ro'yxati

Body — `TargetFilter`. Saralanadigan ustunlar: `ID`, `PHONE`, `STATUS`,
`ATTEMPTS`. Standart: `ID ASC`.

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
