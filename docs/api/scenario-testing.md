# Ssenariylarni Testlash va Simulyatsiya API

`uz.murodjon.robotcallv2.scenario` va `uz.murodjon.robotcallv2.callrecord` · huquq: **SCENARIO_EDIT** (simulyator va persona testlari), **CALL_EDIT** (jonli sinov qo'ng'irog'i)

Ssenariy yaratish va tahrirlashda uni xatosiz ishlashiga ishonch hosil qilish uchun
tizimda **3 xil testlash mexanizmi** mavjud:

1. **Interaktiv Veb Simulyator** (`POST /api/scenarios/simulate`) — SIP/telefoniya
   xarajatisiz, brauzerda matnli chat orqali har bir dialog qadamini testlash.
2. **AI vs AI Persona Testi** (`POST /api/scenarios/{id}/test-personas`) — bir tugma
   bilan 3 xil virtual mijoz personasi orqali to'liq dialog tsiklini o'tkazish.
3. **Jonli Telefon Sinovi** (`POST /api/calls/test`, `POST /api/calls/web-test`) —
   ssenariyni haqiqiy telefonga yoki brauzer mikrofoniga ulab ovozda sinash.

Umumiy javob shakli va xatolar uchun [README.md](README.md)ga qarang.

---

## 1. Interaktiv Veb Simulyator (`POST /api/scenarios/simulate`)

Foydalanuvchi mijoz nomidan xabar yozadi, AI esa joriy bosqich (`currentState`),
faktlar (`contextFacts`) va ssenariy qoidalariga asosan javob beradi. Har bir chaqiruv
**stateless** — holatni frontend o'zi olib yuradi.

### Request Body (`ScenarioSimulationRequest`)

```json
{
  "scenarioId": 7,
  "scenarioDefinition": null,
  "userMessage": "Assalomu alaykum, kim bu?",
  "currentState": "GREETING",
  "chatHistory": [
    { "role": "assistant", "content": "Assalomu alaykum! Bu Uysot kompaniyasining avtomatik ovozli xizmati." }
  ],
  "contextFacts": {
    "clientName": "Aziz Karimov",
    "debtAmount": 1500000,
    "currency": "so'm",
    "dueDate": "2026-09-01",
    "contractNumber": "UY-2026-00123"
  }
}
```

| Maydon | Turi | Majburiymi | Izoh |
|---|---|---|---|
| `scenarioId` | long | ❌ | Bazadagi ssenariy id'si |
| `scenarioDefinition` | `ScenarioDefinition` | ❌ | Saqlanmagan qoralama. **`definition` emas** — `scenarioId` berilmaganda ishlatiladi |
| `userMessage` | string | ❌ | Test qiluvchi yozgan mijoz gapi. **`clientMessage` emas.** Birinchi turnda `null` qoldiriladi — bot o'zi salomlashadi |
| `currentState` | string | ❌ | Joriy bosqich id'si. Berilmasa ssenariyning birinchi bosqichi |
| `chatHistory` | `array<{role, content}>` | ❌ | Dialogning oldingi qadamlari. **`history` emas.** `role` — `"assistant"` yoki `"user"` |
| `contextFacts` | object | ❌ | Ssenariy `factSchema` siga mos test faktlari |

### Response (`ScenarioSimulationResponse`)

```json
{
  "accept": true,
  "data": {
    "assistantReply": "Assalomu alaykum, Aziz aka! Sizning shartnomangiz bo'yicha 1 500 000 so'm qarzdorlik mavjud. Uni qachon to'lay olasiz?",
    "nextState": "DEBT_NOTICE",
    "calledTool": null,
    "toolArguments": {},
    "extractedFacts": {},
    "disposition": null,
    "ended": false,
    "latencyMs": 842
  },
  "message": null,
  "messageCode": null,
  "errors": null
}
```

| Maydon | Turi | Izoh |
|---|---|---|
| `assistantReply` | string | Botning javob matni. **`botReply` emas** |
| `nextState` | string | Yangi bosqich; keyingi so'rovga `currentState` sifatida qaytariladi |
| `calledTool` | string \| null | Chaqirilgan tool nomi |
| `toolArguments` | object | Tool argumentlari |
| `extractedFacts` | object | Suhbatdan yig'ilgan faktlar |
| `disposition` | string \| null | Aniqlangan yakuniy natija |
| `ended` | boolean | Dialog terminal bosqichga yetdimi |
| `latencyMs` | number | Shu turn uchun LLM javob vaqti |

> ⚠️ Joriy implementatsiyada `calledTool` har doim `null`, `toolArguments` va
> `extractedFacts` har doim bo'sh obyekt — simulyator faqat matn va bosqich o'tishini
> qaytaradi, tool chaqiruvlarini modellamaydi. Frontend bu maydonlarni bo'sh bo'lishiga
> tayyor bo'lsin.

---

## 2. AI vs AI Persona Testi (`POST /api/scenarios/{id}/test-personas`)

Path parametri `id` — ssenariy id'si. **Body talab qilinmaydi.**

Har bir persona uchun bot bilan oldindan yozilgan mijoz replikalari almashtiriladi
(scriptli dialog, LLM tomonidan generatsiya qilingan mijoz emas). Faktlar qat'iy test
qiymatlari: `clientName = "Azizbek"`, `debtAmount = "1,200,000 so'm"`,
`dueDate = "2026-05-01"`, til — `uz`.

### Testlanadigan personalar (qat'iy uchtasi)

| `personaName` | Mijoz nima qiladi |
|---|---|
| `Ijobiy mijoz (To'lovga rozi)` | Xushmuomala, qarzini tan oladi, juma kunigacha to'lashga va'da beradi |
| `Qiyin vaziyatdagi mijoz (Oylik kechikdi)` | Oyligi kechikkan, keyingi oyning 5-sanasida to'lashni so'raydi |
| `Adashgan raqam (Boshqa odam)` | Raqamning yangi egasi, so'ralgan odam emas, qayta qo'ng'iroq qilmaslikni so'raydi |

### Response (`List<PersonaTestResult>`)

```json
{
  "accept": true,
  "data": [
    {
      "personaName": "Ijobiy mijoz (To'lovga rozi)",
      "personaPrompt": "Siz qarzdorsiz. Bot qo'ng'iroq qilganda xushmuomala bo'ling va juma kuni to'lashga va'da bering.",
      "passed": true,
      "disposition": "COMPLETED",
      "transcript": [
        { "role": "assistant", "content": "Assalomu alaykum! Azizbek bilan gaplashayapmanmi?" },
        { "role": "user", "content": "Alo, eshitaman" },
        { "role": "assistant", "content": "Azizbek aka, sizda 1 200 000 so'm qarzdorlik bor..." },
        { "role": "user", "content": "Ha, o'ziman" }
      ],
      "extractedFacts": { "finalState": "CLOSING" },
      "errorMessage": null
    }
  ],
  "message": null,
  "messageCode": null,
  "errors": null
}
```

| Maydon | Turi | Izoh |
|---|---|---|
| `personaName` | string | Yuqoridagi jadvaldagi nom |
| `personaPrompt` | string | Persona uchun berilgan rol matni |
| `passed` | boolean | Test xatosiz o'tdimi. Exception bo'lsa `false` |
| `disposition` | string | Dialog davomida aniqlangan natija; hech biri bo'lmasa `"COMPLETED"`, xatoda `"ERROR"` |
| `transcript` | `array<{role, content}>` | To'liq dialog; `role` — `"assistant"` / `"user"` |
| `extractedFacts` | object | Hozircha faqat `{ "finalState": "<oxirgi bosqich>" }`; xatoda bo'sh obyekt |
| `errorMessage` | string \| null | Xatolik matni |

> ⚠️ `description`, `turnCount`, `dialogLog`, `finalOutcome`, `notes` degan maydonlar
> **yo'q**.

---

## 3. Jonli Telefon Sinov Qo'ng'irog'i (`POST /api/calls/test`)

Huquq: **CALL_EDIT**. Hali bazaga saqlanmagan qoralama ssenariyni telefon raqamiga
qo'ng'iroq qilib sinash.

### So'rov

```
POST /api/calls/test?number=998901234567&sipTrunkId=1
Content-Type: application/json
```

| Query parametr | Majburiymi | Izoh |
|---|---|---|
| `number` | ✅ | Teriladigan raqam |
| `sipTrunkId` | ❌ | Chiquvchi trunk; berilmasa kompaniyaning default trunki |

**Body**: to'liq `ScenarioDefinition` JSON hujjati (o'ramsiz).

```json
{
  "stages": [
    { "id": "GREETING", "purpose": "Salomlashish", "allowedTransitions": ["OFFER"], "allowedTools": [] },
    { "id": "OFFER", "purpose": "Taklif qilish", "allowedTransitions": ["CLOSING"], "allowedTools": [] },
    { "id": "CLOSING", "purpose": "Xayrlashish", "allowedTransitions": [], "allowedTools": [] }
  ],
  "factSchema": [
    { "name": "clientName", "type": "text", "required": true }
  ],
  "tools": [],
  "outcomeSchema": [],
  "rolePrompt": "Siz ko'chmas mulk agentisiz...",
  "guardrails": [],
  "disclosureText": null,
  "factWebhook": null
}
```

**Response** (`CallOriginateResponse`) — [calls.md](calls.md#post-apicalls--qolda-qongiroq-qilish--ssenariyni-sinash) dagi
kabi, **maydon nomlari almashib ketgan**: `channelId` da raqam, `status` da haqiqiy
kanal id'si turadi.

```json
{
  "accept": true,
  "data": { "channelId": "998901234567", "status": "PJSIP/trunk-00000045" },
  "message": null,
  "messageCode": null,
  "errors": null
}
```

Asterisk darhol shu raqamga qo'ng'iroq qiladi, go'shak ko'tarilganda ulanish signali
(chime) chalinadi va AI shu qoralama ssenariy bo'yicha jonli ovozda suhbatlashadi.

> ℹ️ Telefonsiz, brauzer mikrofoni orqali sinash uchun —
> [`POST /api/calls/web-test`](calls.md#post-apicallsweb-test--brauzerdan-test-qongirogi-mikrofon--ai).
> `factWebhook` qo'lda va brauzer test qo'ng'iroqlarida chaqirilmaydi.
