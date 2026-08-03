# Ssenariylar API

`uz.murodjon.uysotvoice.scenario` · rol: **ADMIN** (barcha endpoint)

Ssenariy — bosqichlar (FSM), kerakli faktlar, tool'lar, natija (outcome) shakli
va rol-prompt'ni bitta JSON hujjatda saqlaydigan CRUD. **`DialogEngine` har bir
qo'ng'iroqni aynan shu ta'rifga qarab olib boradi** (ROADMAP A.3): kampaniya
yaratilganda `scenarioId` tanlanadi ([campaigns.md](campaigns.md)ga qarang) va
har bir qo'ng'iroq shu ssenariyning bosqichlari/tool'lari/promptidan foydalanadi.

Umumiy javob shakli, xatolar va pagination konventsiyasi uchun
[README.md](README.md)ga qarang.

---

## `ScenarioDefinition` — asosiy shakl

Har bir ssenariyning `definition` maydoni bu shaklda (yaratish/yangilash/
validatsiya so'rovlarida ham, o'qishda ham):

```json
{
  "stages": [
    { "id": "GREETING", "purpose": "Salomlashish va shaxsni tasdiqlash", "allowedTransitions": ["DEBT_NOTICE"], "allowedTools": [] },
    { "id": "DEBT_NOTICE", "purpose": "Qarz haqida xabar berish", "allowedTransitions": ["CLOSING"], "allowedTools": ["recordPaymentPromise"] },
    { "id": "CLOSING", "purpose": "Yakunlash", "allowedTransitions": [] }
  ],
  "factSchema": [
    { "name": "clientName", "type": "string", "required": true },
    { "name": "debtAmount", "type": "number", "required": true },
    { "name": "dueDate", "type": "date", "required": false }
  ],
  "tools": [
    {
      "name": "recordPaymentPromise",
      "description": "Mijoz to'lov va'dasini berganda chaqiriladi",
      "params": [
        { "name": "date", "type": "date", "required": true, "constraint": "kelajakdagi sana bo'lishi kerak" },
        { "name": "amount", "type": "number", "required": true, "constraint": null }
      ]
    }
  ],
  "outcomeSchema": [
    { "name": "promisedDate", "type": "date", "description": "Mijoz va'da qilgan sana" },
    { "name": "promisedAmount", "type": "number", "description": "Va'da qilingan summa" }
  ],
  "rolePrompt": "Siz UySot kompaniyasining qarz undirish agentisiz...",
  "guardrails": ["Foizlar/jarima haqida hech qachon o'zingizdan gapirmang"],
  "disclosureText": "Assalomu alaykum, bu UySot kompaniyasidan avtomatik qo'ng'iroq..."
}
```

| Maydon | Turi | Izoh |
|---|---|---|
| `stages[].id` | string | barqaror holat id (masalan `GREETING`) |
| `stages[].purpose` | string | promptga qo'shiladigan maqsad tavsifi |
| `stages[].allowedTransitions` | string[] | `transitionTo`ga ruxsat berilgan keyingi holat id'lari; bo'sh/`null` — bu holat terminal (qo'ng'iroq shu yerda tugashi mumkin) |
| `stages[].allowedTools` | string[] \| `null` | shu bosqichda, umumiy tool'lardan tashqari, qaysi `tools[]` chaqirilishi mumkin. `null` (odatiy holat) — ssenariyning **barcha** tool'lari shu bosqichda mavjud; aniq ro'yxat (bo'sh ro'yxat ham) — faqat shular, boshqa hech narsa. Faqat bosqichlar kesimida haqiqatan farqlanadigan tool'lari bor ssenariylarga kerak |
| `factSchema[].name` | string | fakt kaliti, `campaign_target.context_data` bilan mos keladi |
| `factSchema[].type` | `"string"` \| `"number"` \| `"date"` | — |
| `factSchema[].required` | bool | shu fakt bo'lmasa qo'ng'iroq boshlanmaydi |
| `tools[].name` | string | LLM shu nom bilan chaqiradi |
| `tools[].params[].constraint` | string \| null | erkin matn qoida (masalan "kelajakdagi sana"), modelga aytiladi, mashina tekshirmaydi |
| `outcomeSchema[].type` | `"string"` \| `"number"` \| `"boolean"` \| `"date"` \| `"array"` | — |
| `rolePrompt` | string | "Siz ... agentisiz" — agentning roli/personasi |
| `guardrails` | string[] | qo'shimcha qoidalar — platforma darajasidagi taqiqlar (§11.1 ochiqlik, foiz/muddat haqida gapirmaslik) bularga qo'shimcha, ular kod darajasida majburiy va hech qanday ssenariy ularni yumshata olmaydi |
| `disclosureText` | string | qo'ng'iroq boshida o'qiladigan majburiy ochiqlik matni |

Fixed universal tool'lar (`transitionTo`, `endCall`, `requestHumanTransfer`,
`recordWrongPerson`, `recordDoNotCall`) `tools` ro'yxatida **e'lon qilinmaydi**
— har bir ssenariyga, har bir bosqichda avtomatik beriladi. Shu 5 ta nomdan
birortasi bilan `tools[].name` deklaratsiya qilinsa — saqlash/validatsiya
`400` bilan rad etadi (nom to'qnashuvi).

---

## `POST /api/scenarios` — yangi ssenariy yaratish

```json
{
  "scenarioKey": "my-debt-flow",
  "name": "Mening qarz oqimim",
  "description": "...",
  "definition": { /* ScenarioDefinition, yuqoriga qarang */ }
}
```

`scenarioKey` bo'sh qoldirilsa `name`dan avtomatik generatsiya qilinadi.
Saqlashdan oldin avtomatik validatsiya qilinadi (deadlock, tool/outcome/fakt
nom to'qnashuvi) — muvaffaqiyatsiz bo'lsa `400`.

**Response** — yaratilgan `Scenario` (pastga qarang).

---

## `POST /api/scenarios/list` — ro'yxat

Body — `ScenarioFilter`:

```json
{ "page": 0, "size": 20, "orders": { "NAME": "ASC" }, "builtinOnly": null }
```

| Maydon | Izoh |
|---|---|
| `builtinOnly` | `true` — faqat 5 ta tayyor shablon ("Tayyor shablonlar" tab); `false` — faqat kompaniyaning o'z ssenariylari ("Mening ssenariylarim"); `null` — ikkalasi ham |

Saralanadigan ustunlar: `ID`, `SCENARIO_KEY`, `NAME`, `VERSION`,
`CREATED_AT`. Standart: `ID ASC`. Ro'yxat har doim har bir `scenarioKey`ning
faqat **faol** versiyasini qaytaradi.

**Javob qatori** (`Scenario`, `definition`siz emas — to'liq keladi):

```json
{
  "id": 7,
  "scenarioKey": "debt-collection",
  "version": 2,
  "name": "Qarz undirish (standart)",
  "description": "...",
  "builtin": true,
  "active": true,
  "definition": { /* ScenarioDefinition */ },
  "createdAt": "2026-01-10T08:00:00Z",
  "createdBy": "system"
}
```

- `builtin: true` — 8 ta seed shablondan biri: `debt-collection`,
  `lead-qualification`, `notification`, `survey`, `appointment-reminder`
  (outbound), `reception`, `inbound-lead`, `callback-request` (kiruvchi
  qo'ng'iroqlar uchun, ROADMAP C.3 — [inbound-routes.md](inbound-routes.md)ga
  qarang). **Read-only** — to'g'ridan-to'g'ri tahrirlanmaydi, avval klonlash kerak.
- `active` — bu versiya yangi kampaniyaga bog'lanadigan versiyami; eski
  versiyaga bog'langan ishlab turgan kampaniyaga ta'sir qilmaydi.

---

## `GET /api/scenarios/{id}` — tahrirlagich uchun to'liq ma'lumot

Javob — bitta `Scenario` (yuqoridagi shakl, to'liq `definition` bilan).

---

## `PUT /api/scenarios/{id}` — yangi versiya yaratish

```json
{ "name": "Yangilangan nom", "description": "...", "definition": { /* ScenarioDefinition */ } }
```

**Muhim:** mavjud qatorni **o'zgartirmaydi** — yangi `version` yaratadi
(eski qatorga bog'langan kampaniya ta'sirlanmaydi). Built-in ssenariyni
tahrirlashga urinish **`409 Conflict`** bilan rad etiladi — avval
`POST /api/scenarios/{id}/clone` qiling.

Javob — yangi versiyaning `Scenario`i.

---

## `POST /api/scenarios/{id}/clone` — nusxalash

```json
{ "name": "Mening nusxam", "scenarioKey": null }
```

`scenarioKey` bo'sh qoldirilsa `name`dan generatsiya qilinadi. Built-in
shablonni ("Tayyor shablon") tahrirlash uchun asosiy yo'l — avval klonlang,
keyin klonni `PUT` bilan tahrirlang.

Javob — yangi (klonlangan) ssenariyning `Scenario`i.

---

## `POST /api/scenarios/validate` — saqlashdan oldin tekshirish

Body — xom `ScenarioDefinition` (o'rab olinmagan, to'g'ridan-to'g'ri):

```json
{ "stages": [ /* ... */ ], "factSchema": [], "tools": [], "outcomeSchema": [], "rolePrompt": "...", "guardrails": [], "disclosureText": "..." }
```

**Response** (`ScenarioValidationResult`):

```json
{ "valid": false, "errors": ["stage 'CLOSING' bilan bog'lanmaydigan holat: 'ORPHAN'"] }
```

Tekshiradi: bosqichlar orasida deadlock yo'qligi (BFS bilan), tool/outcome/fakt
nomlarining bir-biriga to'qnashmasligi. Bu **oldindan ko'rish** endpointi —
hech narsani saqlamaydi, faqat `valid`/`errors` qaytaradi. `POST /api/scenarios`
va `PUT /api/scenarios/{id}` ham xuddi shu tekshiruvni avtomatik qiladi, shuning
uchun tahrirlagichda "Saqlash"dan oldin foydalanuvchiga tezkor fikr-mulohaza
(instant feedback) berish uchun ishlatiladi.
