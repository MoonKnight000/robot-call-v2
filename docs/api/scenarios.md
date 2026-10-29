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

---

## 🎨 Vizual Ssenariy Konstruktori (Visual Studio & Canvas Engine)

FSM stsenariylarini React Flow, Vue Flow yoki Rete.js kabi drag-and-drop vizual muharrirlarda ko'rish, tahrirlash va interaktiv simulyatsiya qilish uchun to'liq Canvas API:

### `GET /api/scenarios/{id}/canvas` — Vizual Canvas Grafini olish

Huquq: **SCENARIO_READ**.
Joriy ssenariyni node'lar (bosqichlar, global FAQlar, boshlang'ich nuqta) va bog'lovchi yo'naltirilgan edge'lar (shartli o'tishlar va deterministik branch'lar) bilan React Flow formatida qaytaradi. Koordinatalar avtomatik topologik qatlamlar (DAG auto-layout) asosida hisoblangan bo'ladi.

Javob (`CanvasGraphDto`):
```json
{
  "scenarioId": 7,
  "scenarioKey": "debt-collection",
  "name": "Qarz undirish v2",
  "version": 3,
  "valid": true,
  "validationErrors": [],
  "viewport": { "x": 0.0, "y": 0.0, "zoom": 1.0 },
  "nodes": [
    {
      "id": "start",
      "type": "start",
      "position": { "x": 40.0, "y": 40.0 },
      "data": {
        "title": "Start",
        "callPurpose": "Mijozdan qarz to'lovini so'rash",
        "disclosureText": "Qo'ng'iroq yozib olinadi..."
      }
    },
    {
      "id": "GREETING",
      "type": "stage",
      "position": { "x": 400.0, "y": 40.0 },
      "data": {
        "stageId": "GREETING",
        "title": "Greeting",
        "purpose": "Salomlashish",
        "staticLine": "Assalomu alaykum {{clientName}}!",
        "emotion": "FRIENDLY",
        "allowedTools": [],
        "requiredOutcomes": ["contacted"]
      }
    },
    {
      "id": "gn_0",
      "type": "global_node",
      "position": { "x": -340.0, "y": 40.0 },
      "data": {
        "question": "Siz kimsiz?",
        "answer": "Men Robot Call virtual assistentiman."
      }
    }
  ],
  "edges": [
    {
      "id": "e_start_to_GREETING",
      "source": "start",
      "target": "GREETING",
      "label": "Begins call",
      "type": "default",
      "data": {}
    },
    {
      "id": "e_GREETING_to_OFFER",
      "source": "GREETING",
      "target": "OFFER",
      "label": "Mijoz ha dedi",
      "type": "transition",
      "data": { "condition": "Mijoz ha dedi" }
    },
    {
      "id": "e_branch_GREETING_CLOSE_0",
      "source": "GREETING",
      "target": "CLOSE",
      "label": "debtAmount LESS_OR_EQUAL 0",
      "type": "branch",
      "data": { "fact": "debtAmount", "operator": "LESS_OR_EQUAL", "value": "0", "branch": true }
    }
  ]
}
```

### `PUT /api/scenarios/{id}/canvas` — Vizual Canvasni Saqlash

Huquq: **SCENARIO_EDIT**.
Frontend vizual muharririda o'zgartirilgan node'lar va edge'lar grafigini yuboradi. Server ularni tekshiradi, to'liq `ScenarioDefinition` ga aylantiradi, optimistik qulf bilan yangi versiya sifatida saqlaydi va yangilangan `ScenarioRow` ni qaytaradi.

Body — `CanvasGraphDto`.

### `POST /api/scenarios/canvas/auto-layout` — Avtomatik Chiroyli Joylashtirish

Huquq: **SCENARIO_EDIT**.
Graflar chigallashib ketganda yoki yangi node'lar qo'shilganda ularni topologik qatlamlar (topological rank BFS) bo'yicha tartiblab, yangilangan koordinatalar bilan qaytaradi.

### `POST /api/scenarios/canvas/dry-run` — Node Darajasidagi Tezkor Simulyator

Huquq: **SCENARIO_EDIT**.
Foydalanuvchi Studio'da xohlagan node'ni tanlab, mijozning gapi va faktlarini kiritib, suhbat keyingi qaysi node'ga o'tishini, qaysi edge faollashishini, bot qanday javob qaytarishini (`{{fact}}` shablonlari to'ldirilgan holda) va terminal holatni real vaqtda tekshirishi mumkin.

Request (`CanvasDryRunRequest`):
```json
{
  "scenarioId": 7,
  "currentNodeId": "GREETING",
  "userInput": "Ha menman, eshitaman",
  "facts": { "clientName": "Azizbek", "debtAmount": 150000 },
  "currentOutcomes": {}
}
```

Response (`CanvasDryRunResponse`):
```json
{
  "previousNodeId": "GREETING",
  "nextNodeId": "OFFER",
  "activeEdgeId": "e_GREETING_to_OFFER",
  "botResponse": "Hurmatli Azizbek, siz uchun yangi aksiya...",
  "transitionReason": "Transition matched -> OFFER",
  "updatedFacts": { "clientName": "Azizbek", "debtAmount": 150000 },
  "updatedOutcomes": { "contacted": "Ha menman, eshitaman" },
  "terminal": false,
  "visitedPath": ["GREETING", "OFFER"],
  "availableTransitions": ["CONFIRM", "REJECT"]
}
```

---

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
| `stages` | `StageDef[]` | FSM bosqichlari: `id`, `purpose`, `allowedTransitions`, `allowedTools`, `emotion` (`cheerful`/`strict`/`friendly`/`whisper`/`sad`, [voices.md](voices.md)), `model` (ixtiyoriy — shu bosqich turn'lari agent modeli o'rniga shu LLM'da ishlaydi; `GET /api/ai-models?kind=LLM` katalogidan, noma'lum id — `400 AI_MODEL_UNKNOWN`. Bir so'zli javobdagi fast model undan ustun; realtime qo'ng'iroqda e'tiborga olinmaydi), `branches` (ixtiyoriy, LLM'siz qaror: `[{ "fact", "operator", "value", "goTo" }]` — qo'ng'iroq shu bosqichga kirishi bilan call faktlari tekshiriladi va birinchi rost shart `goTo` ga o'tkazadi; hech biri rost bo'lmasa bosqich odatdagidek gaplashiladi. `operator`: `EXISTS`, `MISSING`, `EQUALS`, `NOT_EQUALS`, `GREATER`, `GREATER_OR_EQUAL`, `LESS`, `LESS_OR_EQUAL`, `ALWAYS` (fakt va qiymat o'qilmaydi, doim rost — `LOGIC_SPLIT` ning "else" i, faqat oxirgi branch bo'la oladi); ikkala tomon son bo'lsa son sifatida, aks holda matn (katta-kichik harfsiz) solishtiriladi), `transitionConditions` (ixtiyoriy, LLM qaror qiladigan o'tish sharti: `{ "<keyingi bosqich id>": "<tabiiy tildagi shart>" }` — pastga qarang), `staticLine` (ixtiyoriy, bosqichga kirgandagi tayyor gap, `{{fact}}` bilan), `requiredOutcomes` (ixtiyoriy, bosqichdan chiqish uchun kamida bittasi yozilishi kerak bo'lgan natija nomlari) — ikkalasi "Tayyor gap va chiqish sharti" bo'limida. Flow muharriri uchun: `type`, `position`, `globalCondition`, `action` — pastdagi "Flow tugun turlari" bo'limida |
| `factSchema` | `FactField[]` | Yig'iladigan faktlar: `{ "name", "type", "required", "description" }` |
| `tools` | `ToolDef[]` | LLM chaqira oladigan tool'lar: `name`, `description`, `params` (`{ "name", "type", "required", "constraint" }[]`), `preToolSpeech` (tool ishlayotganda aytiladigan gap). Maxsus nom `sendDtmfTones` — IVR menyusida tugma bosish, pastga qarang |
| `outcomeSchema` | `OutcomeField[]` | Qo'ng'iroq natijasi maydonlari: `{ "name", "type", "description", "goal" }`. `goal: true` (ixtiyoriy, standart `false`) — ssenariy shu maydonni yozish uchun mavjud: qo'ng'iroq uni yozsa `GOAL_REACHED`, yozmasa `GOAL_NOT_REACHED` ([calls.md](calls.md)). Qiymat ssenariy tool'ining shu nomli argumentidan yoki kompaniya tool'ining `responseMapping` dagi shu nomli maydonidan keladi ([tools.md](tools.md)). Ko'pi bilan bitta maydon — aks holda `only one outcome field may be the goal` |
| `rolePrompt` | string | Agentning roli va ohangi |
| `callPurpose` | string | Ixtiyoriy. Qo'ng'iroq **nima haqida** — bir gap: "Mijoz saytda kredit bo'yicha ariza qoldirgan; arizani aniqlashtirish". Promptning eng boshida, rol'dan keyin `QO'NG'IROQ SABABI:` bo'lib turadi; `{{fact}}` shablonlari ishlaydi. Pastdagi "Qo'ng'iroq turi va sababi" ga qarang |
| `guardrails` | `string[]` | Taqiqlar (§4.4) |
| `styleExamples` | `StyleExample[]` | Ixtiyoriy. Promptdagi "✗ noto'g'ri → ✓ to'g'ri" misollari: `{ "language": "uz" \| "ru" \| null, "wrong", "right" }`. `language` qo'ng'iroq tilining prefiksi bilan solishtiriladi, `null` — hamma tillar. Ikkala pipeline'da (cascade va realtime) promptga qo'shiladi. `wrong` yoki `right` bo'sh bo'lsa — `a style example needs both 'wrong' and 'right'` |
| `disclosureText` | string | Shu ssenariy uchun §11.1 ochiqlik matni — kompaniya matnidan ustun turadi |
| `globalNodes` | `GlobalNode[]` | Ixtiyoriy. Istalgan bosqichda beriladigan savollar: `{ "question", "answer" }` ("Siz kimsiz?", "Raqamimni qayerdan oldingiz?"). Bot javob beradi va bosqich **o'zgarmaydi** — suhbat qolgan joyidan davom etadi. Ikkala pipeline'da promptga qo'shiladi. Bo'sh maydon — `a global node needs both 'question' and 'answer'` |
| `factWebhook` | `FactWebhook` | Qo'ng'iroqdan oldin faktlarni yangilash — pastga qarang |
| `startStageId` | string | Ixtiyoriy. Qo'ng'iroq qaysi bosqichdan boshlanadi; `null` — `stages` ning birinchisi. Faqat `CONVERSATION` turidagi va global bo'lmagan bosqich bo'la oladi |
| `transitionMode` | `RIGID` / `FLEX` | Ixtiyoriy, standart `RIGID`. `RIGID` — faqat bosqichning o'z o'tishlari (chizilgan edge'lar); `FLEX` — model istalgan CONVERSATION bosqichiga o'ta oladi, chizilganlari birinchi. |
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
| Start bosqichida (`startStageId` yoki birinchi) `branches` yo'q | `the start stage X cannot have branches` |
| `ALWAYS` branch faqat oxirgisi; boshqa operatorlarda `fact` majburiy | `the ALWAYS branch of stage X must be its last` |
| Branch'da `fact` va `operator` bor; `EXISTS`/`MISSING` dan boshqasida `value` bor | `a branch of stage X needs 'fact' and 'operator'`, `branch on F in stage X needs a 'value' for OP` |
| Branch `goTo` — shu bosqichning `allowedTransitions` ichida | `branch of stage X goes to Y, which is not in its allowedTransitions` |
| Start bosqichida `staticLine` yo'q (qo'ng'iroqni agentning `firstMessage` i ochadi) | `the start stage X cannot have a staticLine — …` |
| `startStageId` mavjud bosqich; start bosqich `CONVERSATION` va global emas | `startStageId X is not a stage`, `the start stage X must be a CONVERSATION stage, not T`, `the start stage X cannot be global` |
| Tugun turi qoidalari — "Flow tugun turlari" jadvali | `<TYPE> stage X needs …`, `ENDING stage X cannot have transitions`, `stage X transitions to NOTE Y`, … |
| `requiredOutcomes` yakuniy bosqichda yo'q, nomlari bo'sh emas | `terminal stage X cannot have requiredOutcomes — …`, `stage X has a blank required outcome` |
| `transitionConditions` kaliti — mavjud bosqich (kalit avtomatik `allowedTransitions` ga qo'shiladi), qiymati bo'sh emas | `stage X transitions to unknown stage Y`, `transition condition of stage X for Y must not be empty` |
| `tools[].name` universal nomlardan biri bo'lmaydi: `transitionTo`, `endCall`, `requestHumanTransfer`, `recordWrongPerson`, `recordDoNotCall`, `extractVariables` | `tool name 'X' is reserved …` |
| Fakt / tool / tool parametri / outcome nomlari bo'sh emas va takrorlanmaydi | `a fact has a blank name`, `duplicate tool name: X`, … |
| `outcomeSchema` kamida bitta maydon | `outcomeSchema must not be empty — …` |
| `goal: true` ko'pi bilan bitta maydonda | `only one outcome field may be the goal — …` |
| `styleExamples[]` da `wrong` va `right` ikkalasi ham bor | `a style example needs both 'wrong' and 'right'` |
| `disclosureText` berilsa — avtomatik qo'ng'iroq va yozib olinishini aytadi (yoki bo'sh qoldiring) | `disclosureText must state …` |
| `factWebhook`: `url` `http(s)://` bilan, `method` `GET`/`POST`, `timeoutMs` ≤ 2000, `factSchema` bo'sh emas | `factWebhook.url must …`, `factWebhook.method must be GET or POST`, … |

---

## 🧩 Flow tugun turlari (`StageDef.type`, `action`, `position`, `globalCondition`)

Retell uslubidagi flow muharriri (canvas) uchun. Har bir bosqich — canvas'dagi bitta tugun. Maydonlarning
hammasi ixtiyoriy, shuning uchun eski ssenariylar o'zgarishsiz ishlaydi (`type` yo'q bo'lsa `CONVERSATION`).

| Maydon | Turi | Izoh |
|---|---|---|
| `type` | enum | Tugun turi, pastdagi jadval. `null` — `CONVERSATION` |
| `position` | `{ "x": number, "y": number }` | Canvas'dagi joyi. Qo'ng'iroq uni o'qimaydi; `null` — frontend o'zi joylashtiradi |
| `globalCondition` | string | Berilsa, bosqich **global** bo'ladi: shart bajarilganda ("mijoz operator so'raydi") model qo'ng'iroqni istalgan bosqichdan shu yerga o'tkaza oladi, edge chizish shart emas. Bo'sh satr bo'lishi mumkin emas, NOTE global bo'lmaydi |
| `action` | `StageAction` | CONVERSATION'dan boshqa turlarning sozlamalari. Har tur faqat o'z maydonini o'qiydi |

`StageAction`: `{ "toolName", "transferTo", "transferMode", "digits", "targetAgentId", "smsText", "extractFields", "code", "mcpConnectionId" }`.

| `type` | Nima qiladi | Majburiy | O'tishlar (`allowedTransitions`) |
|---|---|---|---|
| `CONVERSATION` | Model mijoz bilan `purpose` bo'yicha gaplashadi (hozirgi oddiy bosqich) | — | Ixtiyoriy; yo'q bo'lsa yakuniy |
| `FUNCTION` | Bitta tool'ni chaqiradi va natija bilan davom etadi | `action.toolName` — ssenariy `tools` idagi nom yoki agentga ulangan kompaniya tool'i (model ko'radigan nom); saqlashda tekshirilmaydi | Kamida bitta |
| `MCP` | Kompaniya MCP ulanishidagi bitta tool'ni chaqiradi | `action.mcpConnectionId`, `action.toolName` | Kamida bitta |
| `CALL_TRANSFER` | Qo'ng'iroqni telefon raqamiga o'tkazadi | `action.transferTo` — `+?[0-9]{3,15}`; `action.transferMode` `COLD` (standart) / `WARM` | Ixtiyoriy — o'tkazish muvaffaqiyatsiz bo'lsa |
| `PRESS_DIGIT` | DTMF tugmalarini bosadi (IVR menyu) | `action.digits` — `0-9`, `*`, `#`, `w` (yarim soniya pauza), ko'pi bilan 32 | Kamida bitta |
| `LOGIC_SPLIT` | Faktlarga qarab LLM'siz tanlaydi | `branches`, oxirgisi `ALWAYS` ("else"); `transitionConditions` bo'lmaydi | Kamida bitta (branch'larning `goTo` lari) |
| `AGENT_TRANSFER` | Suhbatni boshqa AI agentga beradi | `action.targetAgentId` | Ixtiyoriy — o'tkazish muvaffaqiyatsiz bo'lsa |
| `SMS` | Qo'ng'iroq davomida SMS yuboradi | `action.smsText` (`{{fact}}` bilan, ≤ 1600 belgi) | Kamida bitta |
| `EXTRACT_VARIABLE` | Suhbatdan natija qiymatlarini ajratib oladi | `action.extractFields` — `outcomeSchema` dagi nomlar | Kamida bitta |
| `CODE` | Qisqa skriptni faktlar ustida ishga tushiradi | `action.code` (≤ 20 000 belgi) | Kamida bitta |
| `ENDING` | `staticLine` ni aytadi va go'shakni qo'yadi | — | **Bo'lmaydi** |
| `NOTE` | Canvas'dagi izoh; qo'ng'iroq unga hech qachon kirmaydi | — | Bo'lmaydi; hech bir bosqich NOTE'ga o'tolmaydi |

#### Qo'ng'iroqda qanday ishlaydi

Model `transitionTo` bilan bosqichga o'tganda, quyidagi turlar **kodda, modelsiz** darhol bajariladi va
qo'ng'iroq keyingi bosqichga o'tadi. Keyingi bosqich `branches` dan (birinchi rost shart), u bo'lmasa birinchi
`allowedTransitions` dan olinadi. Zanjir gaplashadigan yoki modelni kutadigan bosqichda to'xtaydi:

| Tur | Kodda nima bo'ladi |
|---|---|
| `LOGIC_SPLIT` | `branches` tekshiriladi |
| `PRESS_DIGIT` | DTMF yuboriladi, `w` — 500 ms pauza. Faqat chiquvchi qo'ng'iroqda ishlaydi |
| `SMS` | `smsText` faktlar bilan to'ldirilib yuboriladi. Agentda mid-call SMS o'chiq bo'lsa ham ishlaydi; modelning `sendMidCallSms` tool'i esa avvalgidek agent sozlamasiga bog'liq |
| `CODE` | JavaScript GraalJS sandbox'da ishga tushadi (Java, fayl, tarmoq yo'q; 1 soniya va 1 mln statement limiti). Skript funksiya tanasi: `vars` (faktlar + yozilgan natijalar) ni o'qiydi va obyekt qaytaradi, masalan `return { segment: vars.debtAmount > 1000000 ? 'katta' : 'kichik' };`. Qaytgan maydonlar natijaga (`outcome`) yoziladi va keyingi `branches` ularni ko'radi. Xato yoki timeout bo'lsa hech narsa yozilmaydi va qo'ng'iroq davom etadi |
| `AGENT_TRANSFER` | Qo'ng'iroq `targetAgentId` agentining nashr qilingan ssenariysi bilan, uning start bosqichidan davom etadi. Ovoz o'zgarmaydi. O'tkazib bo'lmasa (agent boshqa kompaniyaniki, nashr qilinmagan yoki realtime qo'ng'iroq) birinchi `allowedTransitions` ga o'tadi |
| `ENDING`, `CALL_TRANSFER` | Model to'g'ridan-to'g'ri o'tgan bo'lsa (uning `staticLine` i oldingi bosqich promptida berilgan va aytilgan), qo'ng'iroq darhol yopiladi yoki o'tkaziladi. Boshqa tugun orqali kelingan bo'lsa, model `staticLine` ni aytib `endCall` / `requestHumanTransfer` ni chaqiradi. `CALL_TRANSFER` raqami agentning transfer raqami va navbatidan ustun turadi |

`FUNCTION`, `MCP` va `EXTRACT_VARIABLE` ning ishi modelniki, chunki argumentlarni suhbatdan oladi. Cascade'da bu ikki
avtomatik turn'da bajariladi, mijoz gapirishini kutmasdan: (1) tool chaqiriladi (`EXTRACT_VARIABLE` uchun —
`extractVariables`, `extractFields` dagi har bir maydon uchun bitta parametr), (2) natijaga qarab keyingi
bosqichga o'tiladi. Realtime'da bu vazifa `transitionTo` natijasida aytiladi; `extractVariables` tool'i va bosqich
almashganda instructions'ni yangilash realtime uchun keyingi bosqichda (3) ulanadi.

`globalCondition` li bosqichlar har bir bosqichning "keyingi bosqichlar" ro'yxatida turadi va ularga
`requiredOutcomes` bajarilmagan bo'lsa ham o'tish mumkin. `transitionMode: FLEX` da ro'yxatga barcha CONVERSATION
bosqichlari (maqsadi bilan) qo'shiladi. Faktlar va natijalar `branches` da bitta lug'at sifatida o'qiladi, nomi
bir xil bo'lsa natija ustun turadi.

Xatolar (`SCENARIO_DEFINITION_INVALID`, `errors` ichida): `<TYPE> stage X needs a transition to pass the call on`,
`ENDING stage X cannot have transitions`, `NOTE stage X cannot have transitions`, `stage X transitions to NOTE Y`,
`global stage X needs a non-empty globalCondition and cannot be a NOTE`,
`LOGIC_SPLIT stage X needs branches ending with an ALWAYS branch`,
`LOGIC_SPLIT stage X decides by branches only, not transitionConditions`, `FUNCTION stage X needs action.toolName`,
`MCP stage X needs action.mcpConnectionId and action.toolName`,
`CALL_TRANSFER stage X needs action.transferTo as a phone number`,
`PRESS_DIGIT stage X needs action.digits of 0-9, *, # and w`, `AGENT_TRANSFER stage X needs action.targetAgentId`,
`SMS stage X needs action.smsText`, `SMS stage X text must not exceed 1600 characters`,
`EXTRACT_VARIABLE stage X needs action.extractFields`,
`EXTRACT_VARIABLE stage X extracts Y, which is not in outcomeSchema`, `CODE stage X needs action.code`,
`CODE stage X code must not exceed 20000 characters`.

```json
{
  "startStageId": "GREETING",
  "transitionMode": "RIGID",
  "stages": [
    { "id": "GREETING", "purpose": "Salomlashing va mijozni tasdiqlang",
      "position": { "x": 120, "y": 80 }, "transitionConditions": { "SPLIT": "Mijoz o'zi ekanini tasdiqladi" } },
    { "id": "SPLIT", "type": "LOGIC_SPLIT", "position": { "x": 480, "y": 80 },
      "allowedTransitions": ["NOTICE", "BYE"],
      "branches": [
        { "fact": "debtAmount", "operator": "GREATER", "value": "0", "goTo": "NOTICE" },
        { "operator": "ALWAYS", "goTo": "BYE" } ] },
    { "id": "NOTICE", "purpose": "Qarz haqida xabar bering va to'lov sanasini so'rang", "allowedTransitions": ["BYE"] },
    { "id": "HUMAN", "type": "CALL_TRANSFER", "globalCondition": "Mijoz operator bilan gaplashmoqchi",
      "action": { "transferTo": "+998712000000", "transferMode": "WARM" } },
    { "id": "BYE", "type": "ENDING", "staticLine": "Rahmat, xayr!" }
  ]
}
```

---

## 🔀 `transitionConditions` — Mijoz javobiga qarab tarmoqlanish

"Vaqtingiz bormi?" deb so'ralgan bosqichdan "ha desa — taklifga, yo'q desa — qulay vaqtni
so'rashga" o'tish. Ikki xil tarmoqlanish bor, ularni adashtirmang:

| | `branches` | `transitionConditions` |
|---|---|---|
| Kim qaror qiladi | Kod, LLM'siz | LLM |
| Nimaga qarab | Qo'ng'iroqdan oldin ma'lum bo'lgan fakt (`birthYear` yo'q) | Mijozning shu suhbatdagi javobi |
| Qachon | Bosqichga kirishi bilan | Mijoz javob bergach, `transitionTo` tool'i bilan |

```json
{
  "stages": [
    { "id": "GREETING", "purpose": "Salomlashing, hol-ahvol so'rang", "allowedTransitions": ["ASK_TIME"], "allowedTools": [] },
    { "id": "ASK_TIME", "purpose": "Hozir 2 daqiqa gaplashishga vaqti bormi, so'rang",
      "allowedTransitions": ["OFFER", "RESCHEDULE"], "allowedTools": [],
      "transitionConditions": {
        "OFFER": "mijoz vaqtim bor, gapiring desa",
        "RESCHEDULE": "mijoz hozir bandman, keyinroq desa"
      } },
    { "id": "OFFER", "purpose": "Taklifni qisqa tushuntiring", "allowedTransitions": ["CLOSING"], "allowedTools": [] },
    { "id": "RESCHEDULE", "purpose": "Qachon qo'ng'iroq qilish qulayligini so'rang", "allowedTransitions": ["CLOSING"], "allowedTools": [] },
    { "id": "CLOSING", "purpose": "Xayrlashing", "allowedTransitions": [], "allowedTools": ["endCall"] }
  ]
}
```

- Shart promptga `Ruxsat etilgan keyingi bosqichlar: OFFER (agar: …), RESCHEDULE (agar: …)` bo'lib
  tushadi — ikkala pipeline'da (cascade annex va realtime bosqichlar ro'yxati) va `transitionTo`
  javobida.
- Shartsiz qoldirilgan o'tish ham ruxsat — LLM uni keyingi bosqichning `purpose` iga qarab tanlaydi.
- `transitionConditions` dagi har bir kalit avtomatik ravishda `allowedTransitions` ga ham
  qo'shiladi (saqlashda ham, javobda ham). Shuning uchun shartli o'tishni ikki marta yozish shart
  emas: `allowedTransitions` da faqat shartsiz o'tishlar qolsa yetadi. UI "Keyingi qadam →
  bosqich, qachon: …" ko'rinishida bitta ro'yxat bilan ishlashi mumkin:

```json
{ "id": "ASK_TIME", "purpose": "Vaqti bormi, so'rang", "allowedTools": [],
  "transitionConditions": { "OFFER": "vaqtim bor desa", "RESCHEDULE": "bandman desa" } }
```
  → javobda `"allowedTransitions": ["OFFER", "RESCHEDULE"]`.

---

## 🎯 Tayyor gap (`staticLine`) va chiqish sharti (`requiredOutcomes`)

Qo'ng'iroq davomida xato gapirmaslik va bosqichni tashlab ketmaslik uchun (Bland "Static Text" va
"loop condition" g'oyasi):

```json
{ "id": "DEBT_NOTICE", "purpose": "Qarzni xabar qiling",
  "staticLine": "{{clientName}} aka, shartnoma bo'yicha {{debtAmount}} so'm to'lov {{dueDate}} da kechikdi.",
  "allowedTransitions": ["PAYMENT_DATE"], "allowedTools": [] },
{ "id": "PAYMENT_DATE", "purpose": "Aniq to'lov sanasini oling",
  "requiredOutcomes": ["promisedDate", "reasonCode"],
  "allowedTransitions": ["CONFIRMATION"], "allowedTools": ["recordPaymentPromise", "recordRefusalReason"] }
```

**`staticLine`** — muhim gapning matni kompaniyaniki, modelniki emas. Faktlarni kod qo'yadi,
model gapni so'zma-so'z aytadi:
- cascade'da keyingi bosqichlarning tayyor gapi annex'da: `DEBT_NOTICE ga o'tsangiz, gapingiz AYNAN shu bo'lsin: «…»`;
- realtime'da bosqichlar ro'yxatida: `Birinchi gap (AYNAN shu): «…»`.

> ⚠️ Gapni kod emas, model aytadi: pipeline'da bosqich gapi o'tish bilan bir turnda tuziladi.
> Pul summalarini baribir `FactGuard` tekshiradi. Start bosqichida ruxsat yo'q — uni agentning `firstMessage` i ochadi.

**`requiredOutcomes`** — bosqichdan chiqish uchun ro'yxatdagi natijalardan **kamida bittasi**
(`recordPaymentPromise` → `promisedDate`, `recordRefusalReason` → `reasonCode`, …) shu qo'ng'iroqda yozilgan
bo'lishi kerak. Bittasi yetarli: rad etgan mijoz sana o'rniga sabab beradi va baribir davom etadi.
- Tekshiruv kodda (`transitionTo`, ikkala pipeline'da): yozilmagan bo'lsa o'tish rad etiladi va model qaysi
  natijani yozishi kerakligini eshitadi. `endCall` va `requestHumanTransfer` doim ishlaydi.
- Model bir necha bosqichni sakrab o'tmoqchi bo'lsa, natijasi yozilmagan oraliq bosqichda to'xtatiladi.
- Natija nomlari validatorda tekshirilmaydi (tool'lar ularni kodda yozadi) — tool yozadigan nomni aynan yozing.

**"ANIQLANGAN" bloki** (cascade) — annex'da har turn shu qo'ng'iroqda yozilgan natijalar:
`ANIQLANGAN (qayta so'ramang; …): promisedDate = 2026-10-15; reasonCode = NO_MONEY`. Model yozilgan sanani qayta so'ramaydi.

---

## 🧭 Qo'ng'iroq turi va sababi — AI mavzuni adashtirmasligi uchun

Har bir qo'ng'iroq promptining boshida (rol'dan keyin, ikkala pipeline'da) shu blok turadi:

```
QO'NG'IROQ TURI: CHIQUVCHI — mijozga siz O'ZINGIZ qo'ng'iroq qildingiz…
   yoki
QO'NG'IROQ TURI: KIRUVCHI — mijoz O'ZI kompaniyaga qo'ng'iroq qildi, siz qabul qilyapsiz…
QO'NG'IROQ SABABI: <ssenariyning callPurpose'i>
KAMPANIYA MAQSADI: <kampaniya goal'i, bo'lsa>
```

- **Yo'nalish avtomatik**: qo'ng'iroq kiruvchi marshrut ([inbound-routes.md](inbound-routes.md))
  orqali kelgan bo'lsa — KIRUVCHI. Kiruvchida bot "…nomidan qo'ng'iroq qilyapman" demaydi,
  "Men [Ism] aka bilan gaplashayapmanmi?" deb so'ramaydi, "Sizga qanday yordam bera olaman?"
  bilan boshlaydi va mijoz aytmagan mavzuni (qarz, eslatma) o'zi boshlamaydi.
- **Mavzu = ssenariy.** Bitta ssenariy — bitta mavzu (qarz undirish, eslatma, lid). `callPurpose`
  ni to'ldiring — usiz model mavzuni faqat `rolePrompt` dan taxmin qiladi.
- **Xotira** (`MULTI-CALL MEMORY`): har bir avvalgi suhbat yonida `mavzu: <scenarioKey>` yoziladi va
  model boshqa mavzudagi gapni mijoz so'ramasa boshlamaydi — qarz qo'ng'irog'i bo'lgan raqamga
  lid bo'yicha qo'ng'iroq qilinsa, qarz mavzusi o'z-o'zidan chiqmaydi.

### Bitta kiruvchi raqamga turli mavzuda qo'ng'iroq qilinsa

Kiruvchi marshrut bitta agentga (demak bitta ssenariyga) ulanadi. Mijoz qarz, ariza yoki
eslatma bo'yicha qo'ng'iroq qilishi mumkin bo'lsa — avval mavzuni aniqlovchi `TRIAGE` bosqichi,
keyin `transitionConditions` bilan tarmoqlanish:

```json
{
  "callPurpose": "Mijoz kompaniyaga o'zi qo'ng'iroq qildi — nima uchun ekanini aniqlab, yordam berish",
  "stages": [
    { "id": "TRIAGE", "purpose": "Mijoz nima uchun qo'ng'iroq qilganini tinglang, kerak bo'lsa so'rang",
      "allowedTransitions": ["DEBT_HELP", "LEAD", "REMINDER", "ESCALATE"], "allowedTools": [],
      "transitionConditions": {
        "DEBT_HELP": "mijoz to'lov, qarz yoki shartnoma haqida so'rasa",
        "LEAD": "mijoz xizmat, narx yoki qoldirgan arizasi haqida so'rasa",
        "REMINDER": "mijoz uchrashuv, qabul yoki eslatma haqida so'rasa",
        "ESCALATE": "mavzu yuqoridagilarning hech biriga to'g'ri kelmasa"
      } },
    { "id": "DEBT_HELP", "purpose": "…", "allowedTransitions": ["CLOSING"], "allowedTools": [] },
    { "id": "LEAD", "purpose": "…", "allowedTransitions": ["CLOSING"], "allowedTools": [] },
    { "id": "REMINDER", "purpose": "…", "allowedTransitions": ["CLOSING"], "allowedTools": [] },
    { "id": "ESCALATE", "purpose": "Operatorga ulashni ayting", "allowedTransitions": [], "allowedTools": ["requestHumanTransfer"] },
    { "id": "CLOSING", "purpose": "Xayrlashing", "allowedTransitions": [], "allowedTools": ["endCall"] }
  ]
}
```

Chiquvchi qo'ng'iroqda bunday triage kerak emas: kampaniya bitta mavzuli agent/ssenariyni
ishlatadi — qarz kampaniyasi qarz ssenariysini, eslatma kampaniyasi eslatma ssenariysini.

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
