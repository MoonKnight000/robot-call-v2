# Ssenariylar API

`uz.murodjon.robotcallv2.scenario` · huquq: **SCENARIO_READ** (o'qish) / **SCENARIO_EDIT** (yozish va sinash)

Suhbatning bosqichli (FSM) tavsifi: qaysi bosqichlar bor, qaysi faktlar yig'iladi, qaysi
tool'lar chaqirilishi mumkin, qanday guardrail'lar amal qiladi. Ssenariyga **AI agent**
`scenarioId` orqali bog'lanadi ([ai-agents.md](ai-agents.md)); kampaniya
([campaigns.md](campaigns.md)) ham, kiruvchi marshrut ([inbound-routes.md](inbound-routes.md))
ham ssenariyni to'g'ridan-to'g'ri emas, `aiAgentId` orqali agentdan oladi.

Simulyator va persona testlari alohida faylda:
[scenario-testing.md](scenario-testing.md).

Umumiy javob shakli, xatolar va pagination konventsiyasi uchun
[README.md](README.md)ga qarang.

---

## Versiyalash

Ssenariy `scenario_key` bo'yicha versiyalanadi: bir vaqtda bitta `is_active` versiya
bo'ladi (`idx_scenario_active_key`), eski versiyalar tarixda qoladi. `ScenarioRow` da
`scenarioKey` va `version` shuning uchun bor.

---

## Built-in (Tayyor) Ssenariy Shablonlari

Tizimda biznesning eng ko'p talab qilinadigan sohalari uchun 10 ta built-in stsenariy mavjud:

1. **`debt-collection`** — Qarzdorlikni eslatish, to'lov sanasi va summasini kelishish (`promisedDate`, `promisedAmount`), rad etish sababini yozib olish.
2. **`order-confirmation`** — Yangi buyurtmani tasdiqlash, yetkazib berish manzili va qulay vaqtini aniqlash yoki bekor qilish sababini qayd qilish.
3. **`welcome-onboarding`** — Yangi ro'yxatdan o'tgan mijoz bilan salomlashish, tizimdan foydalanishda yordam ko'rsatish va demo uchrashuv belgilash.
4. **`appointment-reminder`** — Shifokor qabuli, klinika yoki servis uchrashuvlarini eslatish va tasdiqlash.
5. **`lead-qualification`** — Savdo so'rovlarini saralash, qiziqish darajasi va byudjetni aniqlab, mutaxassisga yo'naltirish.
6. **`survey`** — CSAT / NPS xizmat ko'rsatish sifati so'rovnomasi.
7. **`notification`** — Muhim yangilik yoki shaxsiy bildirishnomalarni yetkazish.
8. **`reception`** — Kiruvchi qo'ng'iroqlarni qabul qiluvchi aqlli AI kotiba (FAQ va tegishli bo'limga uzatish).
9. **`inbound-lead`** — Reklamadan kirib kelgan qo'ng'iroqlarni qabul qilish va kontakt ma'lumotlarini bazaga saqlash.
10. **`callback-request`** — Operatorlar band bo'lganda kiruvchi mijozdan qulay qayta qo'ng'iroq vaqtini so'rab olish.

---

## 🪄 Dinamik Prompt Shablon Sintaksisi (Double Curly Braces)

Stsenariylar matnlarida (`rolePrompt`, `stages.purpose`, `disclosureText`) CSV yoki API orqali keladigan har qanday faktlarni dinamik ineksiya qilish mumkin:
- Oddiy o'zgaruvchi: `{{clientName}}`, `{{debtAmount}}`, `{{orderNumber}}`, `{{deliveryAddress}}`
- Zaxira qiymat (fallback): `{{clientName | "Hurmatli mijoz"}}`, `{{currency | "so'm"}}`

---

## Endpointlar

### `POST /api/scenarios/list` va `POST /api/scenarios/filter` — Ro'yxat

Huquq: **SCENARIO_READ**. Ikkala path bir xil metodga boradi.

Body — `ScenarioFilter`:

```json
{ "page": 0, "size": 20, "orders": { "NAME": "ASC" }, "builtinOnly": false }
```

Saralanadigan ustunlar: `ID`, `SCENARIO_KEY`, `NAME`, `VERSION`, `CREATED_AT`.
Standart: `NAME ASC`. `builtinOnly: true` — faqat platformaning tayyor shablonlari.

> ⚠️ `activeOnly` degan filtr **yo'q**, `UPDATED_AT` degan saralash ustuni ham yo'q.

Javob — `PageableData<ScenarioRow>`.

### `ScenarioRow`

```json
{
  "id": 7,
  "scenarioKey": "debt-collection",
  "version": 3,
  "name": "Qarz undirish v2",
  "description": "Kechikkan to'lovlar uchun",
  "builtin": false,
  "active": true,
  "definition": { /* ScenarioDefinition */ },
  "createdAt": "2026-09-01T09:00:00Z",
  "createdByName": "Aziz Bekmurodov"
}
```

### `POST /api/scenarios` — Yangi ssenariy

Huquq: **SCENARIO_EDIT**. Body (`CreateScenarioRequest`):

```json
{
  "scenarioKey": "debt-collection-soft",
  "name": "Qarz undirish — yumshoq",
  "description": "Birinchi eslatma uchun",
  "definition": { /* ScenarioDefinition, majburiy */ }
}
```

`scenarioKey` ixtiyoriy (berilmasa generatsiya qilinadi), `name` va `definition` —
majburiy. Javob — `ScenarioRow`.

### `GET /api/scenarios/{id}` — Bitta ssenariy

Huquq: **SCENARIO_READ**. Javob — `ScenarioRow`. Topilmasa — `404`.

### `PUT /api/scenarios/{id}` — Tahrirlash

Huquq: **SCENARIO_EDIT**. Body (`UpdateScenarioRequest`): `name`, `description`,
`definition` (`name` va `definition` majburiy). `scenarioKey` o'zgarmaydi.

### `POST /api/scenarios/{id}/clone` — Nusxa olish

Huquq: **SCENARIO_EDIT**. Body (`CloneScenarioRequest`):

```json
{ "scenarioKey": "debt-collection-copy", "name": "Qarz undirish (nusxa)" }
```

`name` majburiy, `scenarioKey` ixtiyoriy. Javob — yangi `ScenarioRow`.

### `POST /api/scenarios/validate` — Definitionni tekshirish

Huquq: **SCENARIO_EDIT**. Body — `ScenarioDefinition` ning **o'zi** (o'ramsiz).

**Response** (`ScenarioValidationResult`):

```json
{ "accept": true, "data": { "valid": false, "errors": ["stage 'CLOSING' is unreachable"] }, "errors": null }
```

Saqlamaydi — muharrirdagi "Tekshirish" tugmasi uchun.

### `ScenarioDefinition` tarkibi

| Maydon | Turi | Izoh |
|---|---|---|
| `stages` | `StageDef[]` | FSM bosqichlari: `id`, `purpose`, `allowedTransitions`, `allowedTools`, `emotion` (`cheerful`/`strict`/`friendly`/`whisper`/`sad`, [voices.md](voices.md)) |
| `factSchema` | `FactField[]` | Yig'iladigan faktlar: `{ "name", "type", "required" }` |
| `tools` | `ToolDef[]` | LLM chaqira oladigan tool'lar: `name`, `description`, `params`, `preToolSpeech` (tool ishlayotganda aytiladigan gap). Maxsus nom `sendDtmfTones` — IVR menyusida tugma bosish, pastga qarang |
| `outcomeSchema` | `OutcomeField[]` | Qo'ng'iroq natijasi maydonlari: `{ "name", "type", "description" }` |
| `rolePrompt` | string | Agentning roli va ohangi |
| `guardrails` | `string[]` | Taqiqlar (§4.4) |
| `disclosureText` | string | Shu ssenariy uchun §11.1 ochiqlik matni — kompaniya matnidan ustun turadi |
| `factWebhook` | `FactWebhook` | Qo'ng'iroqdan oldin faktlarni yangilash — pastga qarang |
| — | — | Ovoz, persona va AI model sozlamalari ssenariyda **emas** — ular [AI agent](ai-agents.md)da. Bitta senariyni bir nechta agent turli ovoz va personada gapirishi mumkin |

---

## 🔄 `factWebhook` — Qo'ng'iroqdan oldin faktlarni yangilash

`ScenarioDefinition` ichidagi ixtiyoriy blok. To'ldirilgan bo'lsa, **liniyada hali hech kim gapirmasidan oldin** kompaniyaning o'z tizimidan shu qo'ng'iroq faktlari so'raladi.

**Nima uchun kerak.** Kampaniya faktlari CSV yuklangan payt bir marta keladi va eskiradi: dushanba yuklangan ro'yxat jumada ham 1 500 000 so'm deydi — chorshanba to'lab bo'lgan odamga. CRM overlay faqat CRM modellagan maydonlarni qoplaydi; bu qolganini va CRM adapteri yo'q tizimlarni qoplaydi.

**Chiquvchi va kiruvchi qo'ng'iroqda ham ishlaydi**, lekin har birida boshqa paytda — ikkalasida ham mijoz jimlik eshitmasligi uchun:

| Yo'nalish | Qachon chaqiriladi | Mijoz o'sha payt nima eshitadi |
|---|---|---|
| Chiquvchi | Raqam terilishidan oldin (`CallTaskConsumer`) | Hech narsa — qo'ng'iroq hali boshlanmagan |
| Kiruvchi | Javob berishdan oldin (`AriService`) | Gudok |

```json
{
  "factSchema": [
    { "name": "clientName", "type": "text",   "required": true },
    { "name": "debtAmount", "type": "number", "required": true },
    { "name": "dueDate",    "type": "date",   "required": false }
  ],
  "factWebhook": {
    "url": "https://api.mijoz.uz/robot/facts",
    "method": "POST",
    "headers": { "Authorization": "Bearer <shu endpoint uchun token>" },
    "timeoutMs": 1500
  }
}
```

| Maydon | Turi | Majburiymi | Izoh |
|---|---|---|---|
| `url` | `string` | ✅ | `http://` yoki `https://`. Qo'ng'iroq paytida **private/loopback/link-local manzillarga qarshi tekshiriladi** — URL tenant tomonidan yoziladi, tekshirilmagani server o'z ichki tarmog'iga so'rov yuborishiga olib keladi. Redirect kuzatilmaydi |
| `method` | `string` | ❌ | `POST` (sukut) yoki `GET` |
| `headers` | `Map<string,string>` | ❌ | O'zgarishsiz yuboriladi. **Ssenariy definitionʼi ichida saqlanadi**, ya'ni kompaniyada `SCENARIO_READ` huquqi borlar ko'ra oladi — umumiy API kalit emas, faqat shu endpointga cheklangan token qo'ying |
| `timeoutMs` | `number` | ❌ | Sukut 1500, maksimum **2000**. Chiquvchida bu kutish dispatch tsikli ichida, boshqa nishonlarning qo'ng'irog'i oldida turadi; kiruvchida esa mijozning gudok eshitish vaqtiga qo'shiladi |

**So'rov.** `POST` da body (bo'sh maydonlar `null`), `GET` da o'sha qiymatlar query parametr:

```json
// chiquvchi
{ "direction": "outbound", "phone": "+998901234567", "dialedNumber": null, "clientId": 200, "campaignId": 12 }
// kiruvchi
{ "direction": "inbound", "phone": "+998901234567", "dialedNumber": "+998712000000", "clientId": null, "campaignId": null }
```

| Maydon | Izoh |
|---|---|
| `direction` | `inbound` yoki `outbound` — `phone` kim ekanini shundan bilinadi |
| `phone` | Narigi tomon raqami: kim qo'ng'iroq qildi yoki kimga qo'ng'iroq qilinmoqda |
| `dialedNumber` | Faqat kiruvchida — mijoz kompaniyaning qaysi raqamiga qo'ng'iroq qilgani. Bir nechta liniyaga xizmat qiladigan endpoint ularni shu bilan ajratadi |
| `clientId`, `campaignId` | Faqat chiquvchida — kampaniya nishoni olib yuradigan qiymatlar |

**Javob.** Tekis JSON obyekt, kalitlari — fakt nomlari:
```json
{ "debtAmount": "250000", "dueDate": "2026-09-15" }
```

Qoidalar:
- **Faqat `factSchema` da e'lon qilingan kalitlar o'qiladi.** Endpoint ssenariy rejalashtirmagan faktni prompt'ga kirita olmaydi.
- E'lon qilingan turiga mos kelmagan qiymat (`"debtAmount": "to'landi"`) **tashlab yuboriladi** — eski to'g'ri qiymat joyida qoladi.
- Qabul qilingan matnlar `PromptSafeText` dan o'tadi, ya'ni har qanday boshqa ishonchsiz fakt bilan bir xil muomala.
- Javobning maksimal hajmi 64 KB.

**Xatolik = qo'ng'iroq to'xtamaydi.** Timeout, 2xx bo'lmagan status, host topilmasligi, buzuq JSON — hammasi warn logga yoziladi va qo'ng'iroq mavjud faktlar bilan ketaveradi. Kimningdir API si ishlamagani uchun kampaniya to'xtashi — bu avariya, eski summa esa shunchaki yomonroq qo'ng'iroq.

> ℹ️ Qo'lda (`/manual`) va brauzer test qo'ng'iroqlarida chaqirilmaydi — ular test kontekstida ishlaydi.

---

## ☎️ `sendDtmfTones` — IVR menyusida tugma bosish

Ssenariyning `tools` ro'yxatiga qo'shiladigan **maxsus nomli** `ToolDef`. Qo'shilsa, bot narigi tomondagi avtomat menyuni (IVR) eshitib, kerakli tugmani o'zi bosadi.

```json
{
  "tools": [
    { "name": "sendDtmfTones", "description": "Buxgalteriya bo'limiga ulanish uchun menyudan tanlaydi", "params": [] }
  ]
}
```

`description` va `params` **e'tiborga olinmaydi** — tool'ning o'zi kodda (`DialogTools`), ssenariydagi yozuv faqat "shu ssenariyda yoqilsin" degan belgi. Prompt matni ham kodda: menyuni oxirigacha tinglash, odamdan tugma bosishni so'ramaslik, raqam aniq bo'lmasa umuman yubormaslik.

Ikki cheklov:

- **Faqat chiquvchi qo'ng'iroqda.** Kiruvchida narigi uchda odam turadi; u yerda tool LLM ga berilsa ham "bu qo'ng'iroqda tugma bosib bo'lmaydi" deb javob qaytaradi.
- **Faqat e'lon qilinganda.** `tools` da bu nom yo'q bo'lsa, LLM tool'ni umuman ko'rmaydi — odamga qo'ng'iroq qiladigan ssenariyda mavjud emas.

Yuboriladigan belgilar: `0-9`, `*`, `#` (boshqasi tashlanadi), maksimum 12 ta. Har toni 100 ms, oraliq 100 ms.

Mijozning **o'zi** bosgan raqamlarni qabul qilish bu emas — u kampaniyaning `dtmfInputEnabled` sozlamasi ([campaigns.md](campaigns.md)).
