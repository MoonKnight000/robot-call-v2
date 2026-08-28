# Ssenariylar API

`uz.murodjon.uysotvoice.scenario` · rol: **ADMIN** (barcha endpoint)

Ssenariy — bosqichlar (FSM), kerakli faktlar, tool'lar, natija (outcome) shakli,
rol-prompt va hissiyot (emotion) sozlamalarini bitta JSON hujjatda saqlaydigan CRUD. **`DialogEngine` har bir
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
    { "id": "GREETING", "purpose": "Salomlashish va shaxsni tasdiqlash", "allowedTransitions": ["DEBT_NOTICE"], "allowedTools": [], "emotion": "cheerful" },
    { "id": "DEBT_NOTICE", "purpose": "Qarz haqida xabar berish", "allowedTransitions": ["CLOSING"], "allowedTools": ["recordPaymentPromise"], "emotion": "strict" },
    { "id": "CLOSING", "purpose": "Yakunlash", "allowedTransitions": [], "allowedTools": [], "emotion": "cheerful" }
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
  "disclosureText": "Assalomu alaykum! Bu {company} kompaniyasining avtomatik ovozli xizmati. Suhbat yozib olinmoqda."
}
```

| Maydon | Turi | Izoh |
|---|---|---|
| `stages[].id` | string | barqaror holat id (masalan `GREETING`) |
| `stages[].purpose` | string | promptga qo'shiladigan maqsad tavsifi |
| `stages[].allowedTransitions` | string[] | `transitionTo`ga ruxsat berilgan keyingi holat id'lari; bo'sh/`null` — bu holat terminal (qo'ng'iroq shu yerda tugashi mumkin) |
| `stages[].allowedTools` | string[] \| `null` | shu bosqichda, umumiy tool'lardan tashqari, qaysi `tools[]` chaqirilishi mumkin. `null` (odatiy holat) — ssenariyning **barcha** tool'lari shu bosqichda mavjud; aniq ro'yxat (bo'sh ro'yxat ham) — faqat shular, boshqa hech narsa |
| `stages[].emotion` | string \| `null` | shu bosqichdagi ovozning hissiy ohangi (`cheerful`, `strict`, `friendly`, `whisper`, `neutral`, `sad`) |
| `factSchema[].name` | string | fakt kaliti, `campaign_target.context_data` bilan mos keladi |
| `factSchema[].type` | `"string"` \| `"number"` \| `"date"` | — |
| `factSchema[].required` | bool | shu fakt bo'lmasa qo'ng'iroq boshlanmaydi |
| `tools[].name` | string | LLM shu nom bilan chaqiradi |
| `tools[].params[].constraint` | string \| null | erkin matn qoida (masalan "kelajakdagi sana"), modelga aytiladi, mashina tekshirmaydi |
| `outcomeSchema[].type` | `"string"` \| `"number"` \| `"boolean"` \| `"date"` \| `"array"` | — |
| `rolePrompt` | string | "Siz ... agentisiz" — agentning roli/personasi |
| `guardrails` | string[] | qo'shimcha qoidalar — platforma darajasidagi taqiqlar (§11.1 ochiqlik, foiz/muddat haqida gapirmaslik) bularga qo'shimcha, ular kod darajasida majburiy va hech qanday ssenariy ularni yumshata olmaydi |
| `disclosureText` | string \| null | qo'ng'iroq boshida o'qiladigan §11.1 ochiqlik matni. Bo'sh qoldirilsa platformaning o'z matni aytiladi (pastga qarang) |

Fixed universal tool'lar (`transitionTo`, `endCall`, `requestHumanTransfer`,
`recordWrongPerson`, `recordDoNotCall`) `tools` ro'yxatida **e'lon qilinmaydi**
— har bir ssenariyga, har bir bosqichda avtomatik beriladi. Shu 5 ta nomdan
birortasi bilan `tools[].name` deklaratsiya qilinsa — saqlash/validatsiya
`400` bilan rad etadi (nom to'qnashuvi).

Har bir tool — universal ham, ssenariy e'lon qilgani ham — avtomatik ravishda
majburiy `reply` parametrini oladi: model shu maydonga mijozga ovoz bilan
aytiladigan gapni yozadi, agent aynan shuni o'qib eshittiradi. `reply` nomi band,
uni `tools[].params` da qayta e'lon qilmang.

### `disclosureText` — ochiqlik matni (§11.1)

Ochiqlik matni **asosan kompaniya sozlamasida** turadi
(`PUT /api/companies/{id}/config` → `disclosureText`, `docs/api/companies.md`) —
u kim qo'ng'iroq qilayotganini aytadi, ya'ni tenantga tegishli fakt. Ssenariydagi
bu maydon esa **shu ssenariy uchun ustun turuvchi variant**: berilsa, shu ssenariy
bo'yicha qo'ng'iroqlarda kompaniya matni o'rniga aytiladi. Tartib:

```
ssenariy disclosureText  →  kompaniya config disclosureText  →  platforma matni
```

Builtin (tayyor) ssenariylarda bu maydon **bo'sh** — ular hamma tenantga umumiy,
shuning uchun ularda qotirilgan matn kompaniya nomini ayta olmaydi.

Ssenariy ochiqlik matnini **o'z so'zi bilan yozishi mumkin, lekin undan qutula
olmaydi**. Matn faqat uchala shart bajarilganda aytiladi:

1. **Ikkala majburiy faktni aytadi** — qo'ng'iroq avtomatik ekani *va* yozib
   olinayotgani. Tekshiruv kalit so'zlar bo'yicha: `avtomatik`/`robot`/
   `автоматич`/`робот` va `yozib ol`/`yozuv`/`запис`. Bittasi yetishmasa —
   saqlashda `400`, va (agar baza chetidan kirib qolgan bo'lsa) qo'ng'iroqda ham
   ishlatilmaydi.
2. **Qo'ng'iroq tili bilan mos** — matn kirill yozuvida bo'lsa `ru-*` qo'ng'iroqqa,
   lotin yozuvida bo'lsa qolganlariga tegishli deb hisoblanadi. Bir ssenariy ikkala
   tilda dial qilsa, `disclosureText` ni bo'sh qoldiring: platforma matni har bir
   qo'ng'iroqda to'g'ri tilda aytiladi.
3. **Bo'sh emas** — bo'sh/`null` bo'lsa platformaning o'z matni ishlatiladi.

Bu shartlardan biri bajarilmasa, xato qaytmaydi — shunchaki platforma matni
aytiladi (ochiqlik hech qachon tushib qolmaydi).

`{company}` — qo'ng'iroq qilayotgan kompaniya nomiga almashadi, shuning uchun
bitta umumiy (builtin) ssenariy har bir tenantni o'z nomi bilan tanishtira oladi.

---

## `POST /api/scenarios` — yangi ssenariy yaratish

```json
{
  "name": "Qarz undirish — mayin uslub",
  "description": "Yumshoqroq ohangdagi qarz eslatmasi",
  "definition": { /* ScenarioDefinition */ }
}
```

Majburiy: `name` (`@NotBlank`), `definition` (`@NotNull`).
`description` ixtiyoriy.

Validatsiya xatosi (masalan ruxsat etilmagan tool nomi, `reply` nomi bilan
parametr, bo'sh bosqichlar) — `400 Bad Request`, xato tavsiflari ro'yxati
bilan.

**Response** (`ScenarioRow`):

```json
{
  "data": {
    "id": 42,
    "scenarioKey": "qarz-undirish-mayin-uslub",
    "name": "Qarz undirish — mayin uslub",
    "description": "...",
    "version": 1,
    "active": true,
    "builtin": false,
    "createdAt": "2026-07-01T10:00:00Z",
    "updatedAt": "2026-07-01T10:00:00Z",
    "createdBy": 7,
    "createdByName": "Aziz Rahimov",
    "definition": { /* ScenarioDefinition */ }
  },
  "message": null, "messageCode": null, "accept": true, "errors": null
}
```

---

## `POST /api/scenarios/list` — ro'yxat

Body — `ScenarioFilter` (`page`/`size`/`orders`, [README §3](README.md#3-royxatfiltr-endpointlari-pagination)ga qarang).
Saralanadigan ustunlar: `ID`, `NAME`, `UPDATED_AT`. Standart: `ID ASC`.

Filter parametri `activeOnly: true` berilsa — faqat joriy (oxirgi) versiyalar
qaytariladi (`active = true`).

**Response** — `PageableData<ScenarioRow>`: har bir element to'liq `definition`
bilan qaytadi.

Har bir qatordagi muhim bayroqlar:

- `builtin: true` — 8 ta seed shablondan biri: `debt-collection`,
  `lead-qualification`, `notification`, `survey`, `appointment-reminder`
  (outbound), `reception`, `inbound-lead`, `callback-request` (kiruvchi
  qo'ng'iroqlar uchun, ROADMAP C.3 — [inbound-routes.md](inbound-routes.md)ga
  qarang). **Read-only** — to'g'ridan-to'g'ri tahrirlanmaydi, avval klonlash kerak.
- `active` — bu versiya yangi kampaniyaga bog'lanadigan versiyami; eski
  versiyaga bog'langan ishlab turgan kampaniyaga ta'sir qilmaydi.
- `createdBy`/`createdByName` — ssenariyni yaratgan `app_user` (ROADMAP E.1);
  ikkalasi ham `null` bo'lishi mumkin: built-in shablon, `X-Api-Key` orqali
  (shaxssiz) yaratilgan yoki bu ustun ishga tushirilishidan oldin yaratilgan
  ssenariylar uchun.

---

## `GET /api/scenarios/{id}` — tahrirlagich uchun to'liq ma'lumot

Javob — bitta `ScenarioRow` (yuqoridagi shakl, to'liq `definition` bilan).

---

## `PUT /api/scenarios/{id}` — yangi versiya yaratish

```json
{ "name": "Yangilangan nom", "description": "...", "definition": { /* ScenarioDefinition */ } }
```

**Muhim:** mavjud qatorni **o'zgartirmaydi** — yangi `version` yaratadi
(eski qatorga bog'langan kampaniya ta'sirlanmaydi). Built-in ssenariyni
tahrirlashga urinish **`409 Conflict`** bilan rad etiladi — avval
`POST /api/scenarios/{id}/clone` qiling.

Javob — yangi versiyaning `ScenarioRow`i. `createdBy` yangi versiyani
yaratgan joriy foydalanuvchiga yangilanadi — avvalgi versiyaning yaratuvchisi
emas.

---

## `POST /api/scenarios/{id}/clone` — nusxalash

```json
{ "name": "Mening nusxam", "scenarioKey": null }
```

`scenarioKey` bo'sh qoldirilsa `name`dan generatsiya qilinadi. Built-in
shablonni ("Tayyor shablon") tahrirlash uchun asosiy yo'l — avval klonlang,
keyin klonni `PUT` bilan tahrirlang.

Javob — yangi (klonlangan) ssenariyning `ScenarioRow`i. `createdBy` manba
yaratuvchisi emas — nusxalashni bajargan joriy foydalanuvchi.

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

---

## `POST /api/scenarios/simulate` — qadamma-qadam dialog simulyatori

Asterisk yoki SIP trunk sarflamasdan, ssenariy bo'yicha AI botning qanday javob berishi va qaysi holatga o'tishini veb/Swagger orqali interaktiv testlash.
Batafsil ma'lumot va AI benchmark uchun [scenario-testing.md](scenario-testing.md)ga qarang.

**Request body** (`ScenarioSimulationRequest`):

```json
{
  "scenarioId": 7,
  "definition": null,
  "language": "uz-UZ",
  "currentState": "GREETING",
  "clientMessage": "Assalomu alaykum, ha eshitaman",
  "contextFacts": {
    "clientName": "Aziz Karimov",
    "debtAmount": 1500000,
    "dueDate": "2026-09-01"
  },
  "history": [
    { "role": "assistant", "content": "Assalomu alaykum, Aziz aka sizmisiz?" }
  ]
}
```

| Maydon | Turi | Majburiymi | Izoh |
|---|---|---|---|
| `scenarioId` | long | ❌ | Baza ssenariy id'si (berilmasa `definition` ishlatiladi) |
| `definition` | `ScenarioDefinition` | ❌ | Saqlanmagan qorama ssenariy |
| `language` | string | ❌ | Suhbat tili (`uz-UZ`, `ru-RU`) |
| `currentState` | string | ❌ | Joriy bosqich (`GREETING`, `DEBT_NOTICE` va h.k.) |
| `clientMessage` | string | ✅ (`@NotBlank`) | Mijoz yozgan yoki aytgan sinov gapi |
| `contextFacts` | object | ❌ | Qo'ng'iroq parametrlari va faktlari |
| `history` | array | ❌ | Oldingi replikalar tarixi |

**Response** (`ScenarioSimulationResponse`):

```json
{
  "data": {
    "botReply": "Aziz aka, sizning 1 500 000 so'm qarzdorligingiz mavjud, qachon to'lay olasiz?",
    "nextState": "DEBT_NOTICE",
    "extractedOutcome": { "identityConfirmed": true },
    "calledTools": ["transitionTo"],
    "completed": false
  },
  "message": null,
  "messageCode": null,
  "accept": true,
  "errors": null
}
```

---

## `POST /api/scenarios/{id}/test-personas` — AI vs AI ko'p personali avtomatik test

Batafsil foydalanish va natijalar strukturasi uchun [scenario-testing.md](scenario-testing.md)ga qarang.
