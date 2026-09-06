# Kampaniyalar API

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
| `aiAgentId` | `number` | ✅ (`@NotNull`) | [`GET /api/ai-agents`](ai-agents.md) dagi agent. **Senariy, ovoz, persona, LLM model va SIP trunklar shu agentdan olinadi.** Keyin `PUT` bilan boshqa agentga o'tkazsa bo'ladi |
| `dialWindowStart` / `dialWindowEnd` | `string` (`HH:mm:ss`) | ❌ | Kunlik qo'ng'iroq oynasi. Kompaniyaning `company_config` oynasidan **tashqariga chiqa olmaydi** ([companies.md](companies.md)) |
| `dialDays` | `array<DayOfWeek>` | ❌ | `["MONDAY", ...]`. Bo'sh/berilmasa — barcha kunlar |
| `maxAttempts` | `number` | ❌ | Bitta nishonga urinishlar soni |
| `retryIntervalMinutes` | `number` | ❌ | Javob bo'lmagan nishonni qayta terishgacha daqiqa. `0` — disposition bo'yicha standartlar (avtootvetchikdan keyin uzoqroq, bandlikdan keyin qisqaroq) |
| `maxConcurrentCalls` | `number` | ❌ | Parallel kanallar chegarasi |
| `dailyCallCap` | `number` | ❌ | Kunlik qo'ng'iroq chegarasi; `0` — cheksiz. Sarf shifti: har bir qo'ng'iroq STT/LLM/TTS va trunk daqiqasini yeydi |
| `recurrenceType` | `RecurrenceType` | ❌ | `ONCE` (standart), `DAILY`, `WEEKLY`, `MONTHLY`, `CRON` |
| `recurringDayOfMonth` | `number` (1–31) | ❌ | `MONTHLY` uchun oyning kuni |
| `cronExpression` | `string` | ❌ | `CRON` uchun Spring/Quartz cron ifodasi |
| `autoResetTargets` | `boolean` | ❌ | Takror ishga tushganda nishonlar `PENDING` ga qaytariladimi. Standart `false` |

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

Body (`UpdateCampaignRequest`) — `CreateCampaignRequest` bilan bir xil, faqat **`type`
yo'q** (u yaratilgandan keyin o'zgarmaydi). `aiAgentId` esa o'zgartirilishi mumkin:
kampaniyani boshqa ovoz yoki boshqa skript bilan yuritish uchun uni klonlash shart emas.

Javob — yangilangan `CampaignRow`.

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
    "recurrenceType": "CRON",
    "recurringDayOfMonth": null,
    "cronExpression": "0 0 9 ? * MON-FRI",
    "autoResetTargets": false,
    "lastRunAt": "2026-03-05T09:00:00Z",
    "totalTargets": 1200,
    "calledTargets": 750,
    "pendingTargets": 450,
    "completedTargets": 700
  },
  "message": null,
  "messageCode": null,
  "errors": null
}
```

Kampaniya holati (`CampaignStatus`): `DRAFT`, `ACTIVE`, `PAUSED`, `COMPLETED`
(barcha nishonlar yakunlandi), `ARCHIVED`.

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
| `contextData` | ❌ | Erkin JSON obyekt: promptdagi `{{...}}` o'zgaruvchilari shu yerdan olinadi |

**Response** (`AddTargetsResponse`):

```json
{ "accept": true, "data": { "campaignId": 12, "added": 2, "targetIds": [501, 502] }, "errors": null }
```

---

### `POST /api/campaigns/{id}/targets/csv` — CSV import

`Content-Type: text/csv` (yoki `text/plain`), body — CSV faylning o'zi:

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
  "doNotCall": false
}
```

`status` (`TargetStatus`): `PENDING`, `IN_PROGRESS`, `DONE`, `FAILED`, `EXHAUSTED`
(urinishlar limiti tugadi). `contextData` — **string** ichidagi JSON, obyekt emas.

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
| `url` | ✅ | `http`/`https`. Chaqirish paytida loopback va ichki tarmoq manzillariga qarshi tekshiriladi (SSRF himoyasi) — `400 TARGET_SOURCE_URL_INVALID` |
| `method` | ❌ | `GET` (standart) yoki `POST` |
| `requestBody` | ❌ | `POST` uchun yuboriladigan JSON, o'zgartirilmasdan |
| `authHeaderName` / `authHeaderValue` | ❌ | Masalan `Authorization: Bearer ...`. **Qiymat shifrlanib saqlanadi va API orqali qaytarilmaydi**; tahrirlashda bo'sh qoldirsangiz eski qiymat saqlanib qoladi |
| `itemsPath` | ❌ | Javob ichidagi massivgacha nuqtali yo'l (`"data.items"`). Javobning o'zi massiv bo'lsa — bo'sh qoldiring |
| `phoneField` | ❌ | Har bir qatordagi raqam kaliti; standart `phone` |
| `clientIdField` | ❌ | CRM mijoz id kaliti; bo'lmasa `0` |
| `languageField` | ❌ | Qator uchun til; bo'lmasa agentning tili |
| `replaceTargets` | ❌ | Import oldidan mavjud nishonlarni tozalash. Endpoint javob bergandan **keyin** tozalanadi — muvaffaqiyatsiz so'rov ro'yxatni bo'shatib qo'ymaydi |
| `syncOnRecurrence` | ❌ | Har bir takror ishga tushishda avtomatik olish. Standart `true` |
| `enabled` | ❌ | `false` — avtomatik ham, qo'lda ham ishlamaydi |

Qatordagi qolgan barcha kalitlar nishonning **faktlari** (`context_data`) bo'lib saqlanadi;
agent ulardan faqat senariyning `factSchema` da e'lon qilinganlarini gapira oladi.

**`GET` javobi** (`TargetSourceRow`) — `authHeaderValue` o'rniga `authHeaderSet: true/false`,
qo'shimcha: `lastSyncAt`, `lastSyncAdded`, `lastSyncError`.

**`POST /targets/sync` javobi** (`TargetSyncResult`):

```json
{
  "accept": true,
  "data": {
    "campaignId": 12,
    "fetched": 340,
    "added": 337,
    "removed": 300,
    "errors": [{ "line": 41, "message": "no 'phone' in row" }]
  }
}
```

Xatolar: `404 TARGET_SOURCE_NOT_FOUND` (source sozlanmagan), `502 TARGET_SOURCE_FETCH_FAILED`
(endpoint javob bermadi yoki 2xx emas), `400 TARGET_SOURCE_RESPONSE_INVALID` (javob JSON
massiv emas). Har uch holatda ham kampaniyaning mavjud nishonlari **tegilmaydi**, sabab esa
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
| `POST /api/campaigns/{id}/start?immediate=false` | Kampaniyani ishga tushirish (`ACTIVE`) |
| `POST /api/campaigns/{id}/pause` | Vaqtincha to'xtatish (`PAUSED`) |
| `DELETE /api/campaigns/{id}` | Arxivlash (`ARCHIVED`) |

Uchalasi ham `CampaignStatusResponse` (`{ "campaignId": 12, "status": "ACTIVE" }`)
qaytaradi.

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
| `trafficWeight` | Taqsimotdagi ulush. Yig'indisi 100 bo'lishi shart emas — nisbat muhim |
| `active` | `false` bo'lsa umuman tanlanmaydi |

**Taqsimot deterministik.** Variant `sha256(campaignId + ":" + telefon)` bo'yicha tanlanadi, tasodifiy emas. Sababi: kampaniya bir raqamga qayta qo'ng'iroq qiladi (javob bermadi, "ertaga qo'ng'iroq qiling" dedi), tasodifiy tanlovda esa mijoz dushanba yumshoq ssenariyni, seshanba qattiqroq ssenariyni eshitar edi — natija oxirgi qo'ng'iroq variantiga yozilib, test hech narsa o'lchamas edi. `campaignId` aralashtirilgani uchun bitta raqam har kampaniyada bir xil variantga tushib qolmaydi.

Kampaniyada faol variant bo'lmasa, qo'ng'iroq kampaniya agentining o'z senariysi va ovozi bilan ketadi.

### Hisoblagichlar qachon oshadi

| Hisoblagich | Qachon |
|---|---|
| `callsCount` | Nishon navbatga qo'yilganda (dialer) |
| `answeredCount` | Mijoz go'shakni ko'targanda va suhbat boshlanganda |
| `convertedCount` | Qo'ng'iroq yakunida disposition `PROMISE_TO_PAY` yoki `COMPLETED` bo'lsa. `TRANSFERRED` ataylab hisoblanmaydi — operatorga o'tkazish botning ishni oxiriga yetkaza olmagani, uni konversiya deb sanash eng tez taslim bo'ladigan ssenariyni g'olib qilardi |

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
  "ttsVoiceId": "dilnavoz",
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
| `trafficWeight` | `number` | ❌ | Taqsimotdagi ulush; berilmasa `50` |
| `active` | `boolean` | ❌ (`PUT` da) | `false` — variant umuman tanlanmaydi |

Javob (`CampaignVariantResponse`) — o'sha maydonlar plus `id`, `campaignId`, `companyId`, `callsCount`, `answeredCount`, `convertedCount`, `answerRate`, `conversionRate`, `createdAt`, `updatedAt`.

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
