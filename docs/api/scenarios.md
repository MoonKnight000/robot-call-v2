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
`scenarioKey` va `version` shuning uchun bor. `version` optimistik qulf vazifasini ham
bajaradi — pastdagi `PUT` ga qarang.

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

`definition` tekshiruvdan o'tmasa (`POST` ham, `PUT` ham) — `400 SCENARIO_DEFINITION_INVALID`;
topilgan har bir muammo `errors[]` da alohida qator bo'ladi — `/validate` javobidagi
`errors` bilan bir xil matnlar. `message` faqat muammolar sonini aytadi.

### `GET /api/scenarios/{id}` — Bitta ssenariy

Huquq: **SCENARIO_READ**. Javob — `ScenarioRow`. Topilmasa — `404`.

### `PUT /api/scenarios/{id}` — Tahrirlash

Huquq: **SCENARIO_EDIT**. Body (`UpdateScenarioRequest`): `name`, `description`,
`definition` (`name` va `definition` majburiy), ixtiyoriy `version` (`int`). `scenarioKey`
o'zgarmaydi. Qator joyida yozilmaydi: joriy versiya `active=false` bo'ladi va `oxirgi + 1`
versiya yangi `id` bilan yaratiladi — javobdagi `ScenarioRow` shu yangi qator.

**Optimistik qulf.** Frontend o'qigan `ScenarioRow.version` ni `version` sifatida qaytaradi.
Shu kalit bo'yicha orada yangiroq versiya saqlangan bo'lsa (yuborilgan `version` eng oxirgisi
emas) — `409 ENTITY_VERSION_CONFLICT`, hech narsa yozilmaydi. `version` yuborilmasa — oxirgi
versiya almashtiriladi (avvalgidek); ikki tahrir aynan bir vaqtga to'g'ri kelsa ikkinchisi
`409` oladi (avval unique indeks tufayli `500` edi).

### `POST /api/scenarios/{id}/clone` — Nusxa olish

Huquq: **SCENARIO_EDIT**. Body (`CloneScenarioRequest`):

```json
{ "scenarioKey": "debt-collection-copy", "name": "Qarz undirish (nusxa)" }
```

`name` majburiy, `scenarioKey` ixtiyoriy. Javob — yangi `ScenarioRow`.

### Boshqa serverga ko'chirish (export / import)

Alohida endpoint yo'q — ikkita mavjud so'rov yetarli. `GET /api/scenarios/{id}` javobidagi
`scenarioKey`, `name`, `description` va `definition` — aynan `POST /api/scenarios` body'si. Shu to'rt
maydonni JSON faylga saqlab (Git'da versiyalash mumkin), boshqa serverga `POST /api/scenarios` bilan
yuboriladi. `definition` saqlashdagi kabi to'liq tekshiriladi (`/validate`, bosqich modellari katalogi).

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
| `stages` | `StageDef[]` | FSM bosqichlari: `id`, `purpose`, `allowedTransitions`, `allowedTools`, `emotion` (`cheerful`/`strict`/`friendly`/`whisper`/`sad`, [voices.md](voices.md)), `model` (ixtiyoriy — shu bosqich turn'lari agent modeli o'rniga shu LLM'da ishlaydi; `GET /api/ai-models?kind=LLM` katalogidan, noma'lum id — `400 AI_MODEL_UNKNOWN`. Bir so'zli javobdagi fast model undan ustun; realtime qo'ng'iroqda e'tiborga olinmaydi), `branches` (ixtiyoriy, LLM'siz qaror: `[{ "fact", "operator", "value", "goTo" }]` — qo'ng'iroq shu bosqichga kirishi bilan call faktlari tekshiriladi va birinchi rost shart `goTo` ga o'tkazadi; hech biri rost bo'lmasa bosqich odatdagidek gaplashiladi. `operator`: `EXISTS`, `MISSING`, `EQUALS`, `NOT_EQUALS`, `GREATER`, `GREATER_OR_EQUAL`, `LESS`, `LESS_OR_EQUAL`; ikkala tomon son bo'lsa son sifatida, aks holda matn (katta-kichik harfsiz) solishtiriladi) |
| `factSchema` | `FactField[]` | Yig'iladigan faktlar: `{ "name", "type", "required", "description" }` |
| `tools` | `ToolDef[]` | LLM chaqira oladigan tool'lar: `name`, `description`, `params` (`{ "name", "type", "required", "constraint" }[]`), `preToolSpeech` (tool ishlayotganda aytiladigan gap). Maxsus nom `sendDtmfTones` — IVR menyusida tugma bosish, pastga qarang |
| `outcomeSchema` | `OutcomeField[]` | Qo'ng'iroq natijasi maydonlari: `{ "name", "type", "description", "goal" }`. `goal: true` (ixtiyoriy, standart `false`) — ssenariy shu maydonni yozish uchun mavjud: qo'ng'iroq uni yozsa `GOAL_REACHED`, yozmasa `GOAL_NOT_REACHED` ([calls.md](calls.md)). Qiymat ssenariy tool'ining shu nomli argumentidan yoki kompaniya tool'ining `responseMapping` dagi shu nomli maydonidan keladi ([tools.md](tools.md)). Ko'pi bilan bitta maydon — aks holda `only one outcome field may be the goal` |
| `rolePrompt` | string | Agentning roli va ohangi |
| `guardrails` | `string[]` | Taqiqlar (§4.4) |
| `styleExamples` | `StyleExample[]` | Ixtiyoriy. Promptdagi "✗ noto'g'ri → ✓ to'g'ri" misollari: `{ "language": "uz" \| "ru" \| null, "wrong", "right" }`. `language` qo'ng'iroq tilining prefiksi bilan solishtiriladi, `null` — hamma tillar. Ikkala pipeline'da (cascade va realtime) promptga qo'shiladi. `wrong` yoki `right` bo'sh bo'lsa — `a style example needs both 'wrong' and 'right'` |
| `disclosureText` | string | Shu ssenariy uchun §11.1 ochiqlik matni — kompaniya matnidan ustun turadi |
| `globalNodes` | `GlobalNode[]` | Ixtiyoriy. Istalgan bosqichda beriladigan savollar: `{ "question", "answer" }` ("Siz kimsiz?", "Raqamimni qayerdan oldingiz?"). Bot javob beradi va bosqich **o'zgarmaydi** — suhbat qolgan joyidan davom etadi. Ikkala pipeline'da promptga qo'shiladi. Bo'sh maydon — `a global node needs both 'question' and 'answer'` |
| `factWebhook` | `FactWebhook` | Qo'ng'iroqdan oldin faktlarni yangilash — pastga qarang |
| — | — | Ovoz, persona va AI model sozlamalari ssenariyda **emas** — ular [AI agent](ai-agents.md)da. Bitta senariyni bir nechta agent turli ovoz va personada gapirishi mumkin |

Ssenariy yozuvida (`GET /api/scenarios/{id}`, `CreateScenarioRequest`, `UpdateScenarioRequest`) bu obyekt
`definition` maydoni ichida turadi. `POST /api/scenarios/validate`, `POST /api/calls/test` va
`/api/calls/web-test` ning `definition` maydoni esa **shu obyektning o'zini** kutadi — o'ralmagan holda.

#### Tekshiruv qoidalari (`SCENARIO_DEFINITION_INVALID`)

Saqlash, `validate`, `calls/test` — hammasi bir xil qoidalar bilan tekshiradi. Saqlash va `calls/test`
buzilgan ssenariyga `400 SCENARIO_DEFINITION_INVALID` qaytaradi (muammolar — `errors` da); `validate` esa
`200` bilan `data.valid: false` va `data.errors` qaytaradi. Matnlar (inglizcha):

| Qoida | `errors` dagi matn |
|---|---|
| `stages` kamida bitta bosqich | `stages must not be empty` |
| Har bosqichning `id` si bo'sh emas va takrorlanmaydi | `a stage has a blank id`, `duplicate stage id: X` |
| `allowedTransitions` faqat mavjud bosqichlarga | `stage X transitions to unknown stage Y` |
| Kamida bitta yakuniy bosqich (`allowedTransitions` bo'sh) | `no terminal stage (…)` |
| Har bosqichdan yakuniy bosqichga yo'l bor | `stage X cannot reach any terminal stage (deadlock)` |
| `allowedTools` dagi nom — `tools` dan yoki universal tool'lardan biri | `stage X allows unknown tool Y` |
| Birinchi bosqichda `branches` yo'q | `the first stage X cannot have branches` |
| Branch'da `fact` va `operator` bor; `EXISTS`/`MISSING` dan boshqasida `value` bor | `a branch of stage X needs 'fact' and 'operator'`, `branch on F in stage X needs a 'value' for OP` |
| Branch `goTo` — shu bosqichning `allowedTransitions` ichida | `branch of stage X goes to Y, which is not in its allowedTransitions` |
| `tools[].name` universal nomlardan biri bo'lmaydi: `transitionTo`, `endCall`, `requestHumanTransfer`, `recordWrongPerson`, `recordDoNotCall` | `tool name 'X' is reserved …` |
| Fakt / tool / tool parametri / outcome nomlari bo'sh emas va takrorlanmaydi | `a fact has a blank name`, `duplicate tool name: X`, … |
| `outcomeSchema` kamida bitta maydon | `outcomeSchema must not be empty — …` |
| `goal: true` ko'pi bilan bitta maydonda | `only one outcome field may be the goal — …` |
| `styleExamples[]` da `wrong` va `right` ikkalasi ham bor | `a style example needs both 'wrong' and 'right'` |
| `disclosureText` berilsa — avtomatik qo'ng'iroq va yozib olinishini aytadi (yoki bo'sh qoldiring) | `disclosureText must state …` |
| `factWebhook`: `url` `http(s)://` bilan, `method` `GET`/`POST`, `timeoutMs` ≤ 2000, `factSchema` bo'sh emas | `factWebhook.url must …`, `factWebhook.method must be GET or POST`, … |

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
