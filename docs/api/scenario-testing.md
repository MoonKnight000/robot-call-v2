# Ssenariylarni Testlash va Simulyatsiya API

`uz.murodjon.uysotvoice.scenario` va `uz.murodjon.uysotvoice.call` · rol: **OPERATOR / ADMIN**

Ssenariy yaratish va tahrirlashda uni xatosiz ishlashiga ishonch hosil qilish uchun tizimda **3 xil testlash mexanizmi** mavjud:

1. **Interaktiv Veb Simulyator (Turn-by-turn Web Simulator)** — SIP/telefoniya xarajatisiz, brauzerda matnli chat orqali har bir dialog qadamini testlash.
2. **AI vs AI Ko'p Personali Avtomat Test (Persona Benchmark)** — Bir tugma bilan 3 xil virtual mijoz (Ijobiy, Qiyin/Etirozli, Adashgan) personasi orqali to'liq dialog tsiklini stress-test qilish.
3. **Jonli Telefon Sinovi (Real Test-Drive Call)** — Saqlangan yoki saqlanmagan qorama ssenariyni bevosita Asterisk orqali o'z telefon raqamingizga qo'ng'iroq qilib ovozda sinash.

---

## 1. Interaktiv Veb Simulyator (`POST /api/scenarios/simulate`)

Ushbu endpoint orqali Frontend ssenariy muharririda o'ng tomonda **"Interactive Chat Simulator"** panelini qurishi mumkin. Foydalanuvchi mijoz nomidan xabar yozadi, AI esa joriy bosqich (`currentState`), parametrlar (`contextFacts`) va ssenariy qoidalariga asosan javob beradi.

### Request Body (`ScenarioSimulationRequest`)

```json
{
  "scenarioId": 7,
  "definition": null,
  "language": "uz-UZ",
  "currentState": "GREETING",
  "clientMessage": "Assalomu alaykum, kim bu?",
  "contextFacts": {
    "clientName": "Aziz Karimov",
    "debtAmount": 1500000,
    "currency": "so'm",
    "dueDate": "2026-09-01",
    "contractNumber": "UY-2026-00123"
  },
  "history": [
    { "role": "assistant", "content": "Assalomu alaykum! Bu UySot kompaniyasining avtomatlashtirilgan ovozli xizmati. Suhbat yozib olinmoqda." }
  ]
}
```

| Maydon | Turi | Majburiymi | Izoh |
|---|---|---|---|
| `scenarioId` | long | ❌ | Baza ssenariy id'si (agar mavjud ssenariy testlanayotgan bo'lsa) |
| `definition` | `ScenarioDefinition` | ❌ | Hali saqlanmagan qorama ssenariy obyektining o'zi (`scenarioId` bo'lmasa ishlatiladi) |
| `language` | string | ❌ | Suhbat tili: `uz-UZ`, `ru-RU` (standart: `uz-UZ`) |
| `currentState` | string | ❌ | Joriy holat (masalan: `GREETING`, `DEBT_NOTICE`, `CLOSING`). Birinchi turn uchun ssenariyning birinchi stage'i olinadi |
| `clientMessage` | string | ✅ (`@NotBlank`) | Test qiluvchi yozgan mijoz gapi |
| `contextFacts` | object | ❌ | Ssenariydagi `factSchema`ga mos test faktlari |
| `history` | array | ❌ | Dialogning oldingi qadamlari: `[ { "role": "user"|"assistant", "content": "..." } ]` |

### Response (`ScenarioSimulationResponse`)

```json
{
  "data": {
    "botReply": "Assalomu alaykum, Aziz aka! Sizning shartnomangiz bo'yicha 1 500 000 so'm qarzdorlik mavjud. Uni qachon to'lay olasiz?",
    "nextState": "DEBT_NOTICE",
    "extractedOutcome": {
      "identityConfirmed": true
    },
    "calledTools": ["transitionTo"],
    "completed": false
  },
  "message": null,
  "messageCode": null,
  "accept": true,
  "errors": null
}
```

* `botReply`: AI mijozga aytadigan javob matni.
* `nextState`: Yangi o'tilgan ssenariy bosqichi (state machine bo'yicha).
* `extractedOutcome`: Suhbat davomida yig'ilgan natija ma'lumotlari (`promisedDate`, `promisedAmount` va h.k.).
* `calledTools`: AI chaqirgan tool'lar ro'yxati (masalan: `recordPaymentPromise`, `sendSmsNotification`, `transitionTo`).
* `completed`: Agar dialog `endCall` yoki terminal bosqichga yetgan bo'lsa `true` bo'ladi.

---

## 2. AI vs AI Avtomatik Persona Testi (`POST /api/scenarios/{id}/test-personas`)

Bir klik bilan ssenariyni barcha mumkin bo'lgan mijoz xatti-harakatlariga nisbatan avtomatlashtirilgan 5-turnli stress-testdan o'tkazish. 

### Ishlash printsipi:
1. **Virtual Mijoz (Persona LLM)**: Berilgan personaj roli (masalan: "Pulim yo'q, oyligim kechikdi, haftaga cho'zib bering") asosida gapiradi.
2. **AI Agent (Scenario LLM)**: Siz yaratgan ssenariy va qoidalar bo'yicha mijozga javob beradi va tool'larni ishlatadi.
3. Jarayon 3 ta alohida persona uchun to'liq bajariladi va yakuniy hisobot shakllantiriladi.

### Testlanadigan Personalar:
1. **Ijobiy va hamkorlikka tayyor mijoz**: O'zini tanishtiradi, qarzini tan oladi va to'lov sanasini aytadi.
2. **Moliyaviy qiyinchilikdagi / e'tirozli mijoz**: Sharoitini tushuntirib, kechiktirishni yoki boshqa shartlarni so'raydi.
3. **Adashib tushilgan raqam**: Bu raqam egasi boshqa shaxs ekanligini ta'kidlaydi (`WRONG_NUMBER` / `wrongPerson`).

### URL va So'rov
`POST /api/scenarios/{id}/test-personas`  
(Path parametri `id` — ssenariy id'si, Body talab qilinmaydi)

### Response (`List<PersonaTestResult>`)

```json
{
  "data": [
    {
      "personaName": "Ijobiy va hamkorlikka tayyor mijoz",
      "description": "Mijoz o'zini tanidi, qarzini tan oldi va yaqin kunlarda to'lashga va'da berdi",
      "passed": true,
      "turnCount": 3,
      "dialogLog": [
        "[AI]: Assalomu alaykum! Aziz Karimov bilan gaplashayapmanmi?",
        "[Client]: Ha, menman, eshitaman.",
        "[AI]: Aziz aka, sizda 1 500 000 so'm qarzdorlik bor. Uni bugun to'lay olasizmi?",
        "[Client]: Ha xabarim bor, ertaga tushlikkacha to'lab beraman.",
        "[AI]: Rahmat, to'lov va'dasini ertangi sanaga belgiladim. Kuningiz xayrli o'tsin!"
      ],
      "finalOutcome": {
        "promisedDate": "2026-08-29",
        "promisedAmount": 1500000
      },
      "notes": "Muvaffaqiyatli yakunlandi: to'lov va'dasi olindi"
    },
    {
      "personaName": "Moliyaviy qiyinchilikdagi mijoz",
      "description": "Hozir puli yo'qligini, oyligi kechikayotganini aytadi",
      "passed": true,
      "turnCount": 4,
      "dialogLog": [
        "[AI]: Assalomu alaykum! Aziz Karimov bilan gaplashayapmanmi?",
        "[Client]: Assalomu alaykum, ha eshitaman.",
        "[AI]: Sizda muddati o'tgan qarzdorlik mavjud...",
        "[Client]: Hozir pulim yo'q edi, oyligim 5-sentyabrda tushadi, o'shanda to'lasam bo'ladimi?",
        "[AI]: Tushundim. Unda to'lov muddatini 5-sentyabrga belgilab qo'yaman."
      ],
      "finalOutcome": {
        "promisedDate": "2026-09-05"
      },
      "notes": "Muvaffaqiyatli: kechiktirilgan sana kelishildi"
    },
    {
      "personaName": "Adashib tushilgan raqam",
      "description": "Bu boshqa odam ekanligini aytadi",
      "passed": true,
      "turnCount": 2,
      "dialogLog": [
        "[AI]: Assalomu alaykum! Aziz Karimov bilan gaplashayapmanmi?",
        "[Client]: Yo'q, adashdingiz, bu boshqa raqam, men Aziz emasman.",
        "[AI]: Uzr so'raymiz, bezovta qildik. Raqamingizni ro'yxatdan chiqaramiz."
      ],
      "finalOutcome": {
        "disposition": "WRONG_NUMBER"
      },
      "notes": "Muvaffaqiyatli: noto'g'ri shaxs deb belgilandi va yakunlandi"
    }
  ],
  "message": null,
  "messageCode": null,
  "accept": true,
  "errors": null
}
```

---

## 3. Jonli Telefon Sinov Qo'ng'irog'i (`POST /api/calls/test`)

Ssenariy muharririda turgan holatda hali bazaga saqlanmagan qorama ssenariyni telefon raqamingizga qo'ng'iroq qilib sinash.

### So'rov
`POST /api/calls/test?number=998901234567`  
`Content-Type: application/json`

**Body**: to'liq `ScenarioDefinition` JSON hujjati.

```json
{
  "stages": [
    { "id": "GREETING", "purpose": "Salomlashish", "allowedTransitions": ["OFFER"] },
    { "id": "OFFER", "purpose": "Taklif qilish", "allowedTransitions": ["CLOSING"] },
    { "id": "CLOSING", "purpose": "Xayrlashish", "allowedTransitions": [] }
  ],
  "factSchema": [
    { "name": "clientName", "type": "string", "required": true }
  ],
  "tools": [],
  "outcomeSchema": [],
  "rolePrompt": "Siz ko'chmas mulk agentisiz...",
  "guardrails": []
}
```

**Response**:
```json
{
  "data": {
    "number": "998901234567",
    "channelId": "PJSIP/trunk-00000045"
  },
  "accept": true
}
```

Asterisk darhol ushbu raqamga qo'ng'iroq qiladi, go'shak ko'tarilganda ulanish signali (chime) chalinadi va AI shu qorama ssenariy bo'yicha jonli ovozda suhbatlashadi.
