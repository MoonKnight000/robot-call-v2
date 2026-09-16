# Call flow (IVR / marshrutlash grafi) API

`uz.murodjon.robotcallv2.callflow` · huquq: **CALL_FLOW_READ** / **CALL_FLOW_EDIT**

Call flow — kiruvchi qo'ng'iroq bosib o'tadigan **graf** (OnlinePBX'dagi kabi): salomlashish →
ish vaqtini tekshirish → "o'zbek tili uchun 1 ni, rus tili uchun 2 ni bosing" → asosiy menyu →
AI agent / operatorlar navbati / ovozli pochta. Flow [kiruvchi marshrut](inbound-routes.md)ga
`callFlowId` orqali biriktiriladi.

Flow ishlatadigan boshqa sozlamalar:
[ish jadvallari va bayramlar](schedules.md) (`SCHEDULE` tuguni),
[ovozli pochta qutilari](voicemail.md) (`VOICEMAIL` tuguni),
[AI agentlar](ai-agents.md) (`AI_AGENT` tuguni),
operator navbatlari — `/api/call-queues` (`QUEUE` tuguni),
[foydalanuvchilar](users.md) (`RING_GROUP` / `EXTENSION` tugunlari),
[fayllar](files.md) (`audioFileId` — oldindan yozilgan audio).

Umumiy javob shakli, xatolar va pagination konventsiyasi uchun
[README.md](README.md)ga qarang.

---

## 📝 Qoralama (draft) va nashr (published)

Har bir flow'da **ikkita graf** bor:

| Maydon | Ma'nosi |
|---|---|
| `draftGraph` | Tahrirlanayotgan nusxa. `POST`/`PUT` faqat shuni yozadi — **tekshiruvsiz** (yarim tayyor graf ham saqlanadi) |
| `publishedGraph` | Qo'ng'iroqlar **aynan shuni** bajaradi. Faqat `POST /{id}/publish` almashtiradi. Hech qachon nashr qilinmagan bo'lsa — `null` |

- `version` — flow necha marta nashr qilingani (`0` — hali nashr qilinmagan).
- `publishedAt` — oxirgi nashr vaqti.
- Draft'ni saqlash jonli qo'ng'iroqlarga **ta'sir qilmaydi** — ular nashr qilingan versiyada
  davom etadi.
- **Nashr** (`publish`) draft'ni to'liq tekshiradi: grafning shakli ([qoidalar](#-tekshiruv-qoidalari))
  + tugunlar ishora qilgan hamma narsa (jadval, navbat, AI agent, pochta qutisi,
  foydalanuvchilar, audio fayl) **shu kompaniyaniki ekani va mavjudligi**. Xato bo'lsa hech
  narsa o'zgarmaydi.
- Marshrutga faqat **kamida bir marta nashr qilingan** flow biriktiriladi
  (`CALL_FLOW_NOT_PUBLISHED`, [inbound-routes.md](inbound-routes.md)).

> Javobda alohida `published` / `hasUnpublishedChanges` bayrog'i **yo'q**. Frontend:
> nashr qilinganmi — `publishedGraph != null`; nashr qilinmagan o'zgarish bormi —
> `draftGraph` va `publishedGraph`ni solishtirish (yoki `publishedGraph == null`).

---

## 📦 Obyekt shakli (`CallFlow`)

```json
{
  "id": 1,
  "companyId": 1,
  "name": "Asosiy kiruvchi oqim",
  "description": "Uysot ofis raqami",
  "draftGraph": { "entry": "greet", "nodes": { "...": {} } },
  "publishedGraph": { "entry": "greet", "nodes": { "...": {} } },
  "version": 3,
  "publishedAt": "2026-09-10T08:15:00Z",
  "createdAt": "2026-09-01T09:00:00Z",
  "updatedAt": "2026-09-11T12:40:00Z"
}
```

---

## 🕸 Graf (`CallFlowGraph`)

```json
{
  "entry": "greet",
  "nodes": {
    "greet": { "type": "PLAY", "prompt": { "uz-UZ": "Assalomu alaykum!" }, "exits": { "next": "main" } },
    "main":  { "type": "MENU", "...": "..." }
  }
}
```

| Maydon | Turi | Izoh |
|---|---|---|
| `entry` | string | Qo'ng'iroq boshlanadigan tugun id'si |
| `nodes` | object | Tugun id → tugun (`CallFlowNode`). Id: `[A-Za-z0-9_-]`, 1–40 belgi. Maksimum **200** tugun |

### Tugun (`CallFlowNode`) maydonlari

Qaysi maydon kerakligi `type`ga bog'liq (quyidagi [tugun turlari](#-tugun-turlari-callflownodetype)
jadvaliga qarang); keraksizlari yuborilmaydi yoki `null`.

| Maydon | Turi | Qaysi turlarda | Izoh |
|---|---|---|---|
| `type` | enum | hammasi | [Tugun turi](#-tugun-turlari-callflownodetype) — majburiy |
| `prompt` | object `{til: matn}` | PLAY, MENU, LANGUAGE_SELECT, CALLBACK_OFFER | TTS bilan aytiladigan matn, til tegi bo'yicha: `{ "uz-UZ": "...", "ru-RU": "..." }` |
| `audioFileId` | long | PLAY, MENU, LANGUAGE_SELECT, CALLBACK_OFFER | Oldindan yozilgan audio ([files.md](files.md)); berilsa `prompt` o'rniga **shu** ijro etiladi |
| `options` | object `{tugma: qiymat}` | MENU, LANGUAGE_SELECT | MENU: tugma → keyingi tugun id. LANGUAGE_SELECT: tugma → til tegi. Tugma — `0`-`9`, `*` yoki `#` |
| `exits` | object `{chiqish: tugun id}` | hammasi (HANGUP'dan tashqari) | Chiqish nomi → keyingi tugun. Ruxsat etilgan chiqishlar turga bog'liq |
| `timeoutSec` | integer | MENU, LANGUAGE_SELECT, RING_GROUP, EXTENSION, FORWARD_EXTERNAL | MENU/LANGUAGE_SELECT: tugma kutish (1–30); qolganlari: jiringlash vaqti (5–120) |
| `maxRetries` | integer | MENU | Tugma bosilmasa/noto'g'ri bo'lsa menyu necha marta qaytariladi (0–5, standart `2`) |
| `language` | string | LANGUAGE_SELECT | Hech narsa bosilmasa tanlanadigan til (`uz-UZ`) |
| `skipWhenKnown` | boolean | LANGUAGE_SELECT | `true` — mijozning tili allaqachon ma'lum bo'lsa savol berilmaydi |
| `scheduleId` | long | SCHEDULE | [Ish jadvali](schedules.md) |
| `queueId` | long | QUEUE | Operatorlar navbati (`/api/call-queues`) |
| `aiAgentId` | long | AI_AGENT | [AI agent](ai-agents.md) |
| `voicemailBoxId` | long | VOICEMAIL | [Ovozli pochta qutisi](voicemail.md) |
| `userIds` | long[] | RING_GROUP, EXTENSION | Jiringlatiladigan operator(lar) — [foydalanuvchi](users.md) id'lari |
| `phoneNumber` | string | FORWARD_EXTERNAL | Tashqi raqam (`^\+?[1-9]\d{8,14}$`, masalan `+998901234567`) |
| `confirmAnswer` | boolean | FORWARD_EXTERNAL | `true` — javob bergan odam ulanishdan oldin **1 ni bosishi** kerak (mobil ovozli pochtasi qo'ng'iroqni "yeb qo'ymasligi" uchun) |
| `url` | string | WEBHOOK_ROUTER | Kompaniya endpointi (`http(s)://`) |
| `extensionDigits` | integer | MENU | Menyuda ichki raqamni to'g'ridan-to'g'ri terishga ruxsat: shuncha xonali raqam (2–6) |
| `keywords` | object `{tugma: [so'zlar]}` | MENU | Tugma o'rniga **aytilganda** shu variantni tanlaydigan so'zlar ([qarang](#menu)) |

> Javobda (GET/list) ishlatilmagan skalyar maydonlar `null` bo'lib, `prompt`/`options`/
> `exits`/`keywords` esa `{}`, `userIds` esa `[]` bo'lib kelishi mumkin — frontend ularni
> "bo'sh" deb qabul qilsin.

---

## 🔀 Umumiy ishlash qoidalari

- Qo'ng'iroq `entry` tugunidan boshlanadi, har tugun **qaysi chiqishdan** chiqishini hal
  qiladi va `exits`dagi tugunga o'tadi.
- **Tugunda berilmagan chiqish = qo'ng'iroq tugaydi** (go'shak qo'yiladi). Masalan, `PLAY`da
  `next` bo'lmasa — matn aytiladi va qo'ng'iroq tugaydi.
- Mijoz go'shakni qo'ysa — flow darhol to'xtaydi.
- Flow **300 qadamdan** oshsa (o'zini-o'zi aylanib yurgan menyu) — qo'ng'iroq tugatiladi.
- **Prompt tili:** qo'ng'iroq tili bo'yicha aniq teg (`ru-RU`) → shu asosiy til (`ru` bilan
  boshlanadigan istalgan teg) → bo'lmasa `prompt`dagi istalgan boshqa matn. Shuning uchun
  qo'llab-quvvatlanadigan **har bir tilga** matn yozish tavsiya etiladi.
- **Qo'ng'iroq tili** boshida: CRM yoki mijoz xotirasidagi `preferredLanguage` → bo'lmasa
  kompaniyaning standart tili. `LANGUAGE_SELECT` uni o'zgartiradi.
- MENU, LANGUAGE_SELECT va CALLBACK_OFFER prompt'larini mijoz tugma bosib **bo'lishi**
  mumkin (barge-in); PLAY — oxirigacha aytiladi.

### `{afterHours}` tokeni

Istalgan tugunning `prompt` matniga `{afterHours}` qo'yilsa, u avtomatik yasalgan ish vaqti
iborasi bilan almashtiriladi — shu qo'ng'iroqda **oxirgi bajarilgan `SCHEDULE` tuguni**
natijasidan:

> "Bugun Mustaqillik kuni munosabati bilan dam olish kuni. Hozir ish vaqtimizdan tashqari.
> Ertaga soat to'qqizda ishni boshlaymiz."

Rus tilidagi qo'ng'iroqda — ruscha ("Сейчас нерабочее время. Мы начнём работу завтра в …").
Oldin hech qanday `SCHEDULE` tuguni bajarilmagan bo'lsa, token bo'sh satrga almashadi.
Token faqat matnli `prompt`da ishlaydi (`audioFileId`da emas).

```json
"closed": { "type": "PLAY", "prompt": { "uz-UZ": "Afsuski, {afterHours}", "ru-RU": "К сожалению, {afterHours}" }, "exits": { "next": "vm" } }
```

---

## 🧩 Tugun turlari (`CallFlowNodeType`)

Chiqish nomlari (`CallFlowExit`) `exits` obyektida **kichik harf** bilan yoziladi: `next`,
`open`, `closed`, `holiday`, `timeout`, `invalid`, `completed`, `transfer`, `answered`,
`overflow`, `callback`, `no_answer`, `accepted`, `declined`, `vip`, `known`, `repeat`,
`default`, `error`.

| Tur | Nima qiladi | Majburiy maydonlar | Ixtiyoriy maydonlar | Ruxsat etilgan chiqishlar |
|---|---|---|---|---|
| `PLAY` | Matn aytadi / audio ijro etadi | `prompt` yoki `audioFileId` | — | `next` |
| `SCHEDULE` | Ish jadvali bo'yicha tarmoqlanadi | `scheduleId`, chiqishlar `open` **va** `closed` | `holiday` chiqishi | `open`, `closed`, `holiday` |
| `MENU` | Tugmali (va ixtiyoriy ovozli) menyu | `prompt` yoki `audioFileId`, kamida bitta `options` | `timeoutSec`, `maxRetries`, `keywords`, `extensionDigits` | `timeout`, `invalid` (+ `options`dagi tugunlar) |
| `LANGUAGE_SELECT` | Til tanlash menyusi | `prompt` yoki `audioFileId`, kamida bitta `options` | `language`, `skipWhenKnown`, `timeoutSec` | `next` |
| `AI_AGENT` | Qo'ng'iroqni AI agentga beradi | `aiAgentId` | — | `completed`, `transfer` |
| `QUEUE` | Operatorlar navbatida kutadi | `queueId` | — | `answered`, `overflow`, `callback` |
| `RING_GROUP` | Bir nechta operatorni birdaniga jiringlatadi | `userIds` (≥1) | `timeoutSec` | `answered`, `no_answer` |
| `EXTENSION` | Bitta operatorni jiringlatadi | `userIds` (aynan 1 ta) | `timeoutSec` | `answered`, `no_answer` |
| `FORWARD_EXTERNAL` | Tashqi raqamga trunk orqali yo'naltiradi | `phoneNumber` | `timeoutSec`, `confirmAnswer` | `answered`, `no_answer` |
| `VOICEMAIL` | Ovozli xabar yozib oladi | `voicemailBoxId` | — | `next` |
| `CALLBACK_OFFER` | "Qayta qo'ng'iroq qilaylikmi? 1 ni bosing" | — | `prompt` / `audioFileId` | `accepted`, `declined` |
| `CALLER_ROUTER` | Kim qo'ng'iroq qilayotgani bo'yicha tarmoqlanadi | — | — | `vip`, `known`, `repeat`, `default` |
| `WEBHOOK_ROUTER` | Kompaniya endpointidan qaysi chiqishni olishni so'raydi | `url` | — | `default`, `error` + **istalgan nom** (`[a-z0-9_]`, 1–40) |
| `HANGUP` | Qo'ng'iroqni tugatadi | chiqishlari **bo'lmasligi** kerak | — | — |

### `PLAY`

`prompt` (yoki `audioFileId`) aytiladi, keyin `next`.

### `SCHEDULE`

`scheduleId` jadvali hozirgi vaqtga baholanadi ([qoidalar](schedules.md#-ochiqyopiq-qanday-hisoblanadi-businesshoursevaluator)):

- ochiq → `open`;
- yopiq **va** bugun bayram **va** `holiday` chiqishi berilgan → `holiday`;
- aks holda → `closed`.

`SHORT_DAY`/`WORKING_DAY` bayram o'z oynasi ichida — `open`. Yopiq natija qo'ng'iroqni
"ish vaqtidan tashqari" sababi bilan o'tkazib yuborilganlar ro'yxatiga belgilaydi
(keyinroq AI agent yoki operator javob bersa, belgi olib tashlanadi). Bu tugun
[`{afterHours}`](#afterhours-tokeni) tokeni uchun ma'lumot beradi.

### `MENU`

1. Prompt aytiladi; mijoz tugma bosishi (yoki `keywords` bo'lsa — gapirishi) kutiladi,
   `timeoutSec` (standart **6**) soniya.
2. Bosilgan tugma `options`da bo'lsa → o'sha tugunga o'tadi.
3. Hech narsa bosilmasa: "Siz hech narsa tanlamadingiz" deb menyu qaytariladi; `maxRetries`
   (standart **2**) tugagach → `timeout` chiqishi.
4. Noto'g'ri tugma: "Noto'g'ri tanlov" deb qaytariladi; urinishlar tugagach → `invalid`
   (u bo'lmasa `timeout`).
5. **`extensionDigits`** berilgan bo'lsa (masalan `3`), mijoz shuncha xonali ichki raqamni
   terishi mumkin (`101`): shu SIP extension'li foydalanuvchi 25 soniya jiringlatiladi. Javob
   berilsa (yoki mijoz go'shakni qo'ysa) qo'ng'iroq shu yerda tugaydi; javob bo'lmasa —
   noto'g'ri tanlov kabi. Bunda bir xonali menyu tugmasi va raqamning birinchi xonasi
   ~1,5 soniyalik pauza bilan ajratiladi.
6. **`keywords`** — tugma o'rniga aytilganda variantni tanlaydigan so'zlar
   (`{ "1": ["uy", "kvartir", "квартир"] }`). Kalitlar `options`da bo'lishi shart.
   Ishlashi uchun serverda STT yoqilgan bo'lishi kerak (aks holda menyu faqat tugma bilan
   ishlaydi).

> ⚠️ **Kalit so'zlar kichik harfga o'tkazilgan transkript ichida _qism satr_ sifatida
> qidiriladi** (o'zbekcha apostroflarning barcha ko'rinishlari — `‘ ’ ` ʻ ʼ` — bitta `'`ga
> keltiriladi). Shuning uchun so'zning **o'zagini** yozing, ayniqsa rus tilida kelishiklar
> tufayli: `"оплат"` ("оплата", "оплату", "оплатой"ning hammasini topadi), `"оплата"` esa
> "оплату"ni topmaydi. Juda qisqa o'zak boshqa so'z ichida ham topilib qolishi mumkin —
> variantlar orasida umumiy qism bo'lmasin.

Operator ulanganda eshitadigan qisqa ma'lumotda ("Tanlovi: …") tanlangan variantning
**birinchi** kalit so'zi (bo'lmasa tugmaning o'zi) aytiladi.

### `LANGUAGE_SELECT`

- `skipWhenKnown: true` va mijozning tili ma'lum (CRM yoki mijoz xotirasidagi
  `preferredLanguage`, yoki oldingi LANGUAGE_SELECT) bo'lsa — savol berilmaydi, darhol `next`.
- Aks holda prompt aytiladi va **bitta** tugma `timeoutSec` (standart **5**) soniya kutiladi.
  `options[tugma]` — yangi qo'ng'iroq tili; bosilmasa yoki noto'g'ri bo'lsa `language`
  (u ham bo'lmasa til o'zgarmaydi). Keyin `next`.
- Til tegi: `uz-UZ`, `ru-RU`, `en-US` ko'rinishida (`[a-z]{2}(-[A-Z]{2})?`).

### `AI_AGENT`

Qo'ng'iroq `aiAgentId` agentiga beriladi (uning senariysi, ovozi, personasi bilan).

- Agent suhbatni tugatsa → `completed`.
- Agent odamga uzatishni so'rasa → `transfer`. **`transfer` chiqishi berilmagan** bo'lsa va
  agentda o'tkazish navbati (`callBehaviour.transferQueueId`,
  [ai-agents.md](ai-agents.md)) sozlangan bo'lsa — qo'ng'iroq o'sha navbatga tushadi va
  undan keyin tugaydi.
- Agent ishga tusha olmasa — `transfer` deb hisoblanadi (mijoz baribir kimgadir yetib borsin).

### `QUEUE`

Qo'ng'iroq `queueId` navbatiga qo'yiladi: kutish musiqasi, "Sizning navbatingiz —
to'rtinchi", navbat sozlamasiga ko'ra qayta qo'ng'iroq taklifi, operatordan keyin
so'rovnoma — hammasi navbat sozlamalaridan (`/api/call-queues`). [VIP](inbound-routes.md#qongiroq-qiluvchi-qoidalari-apiinbound-caller-rules)
qo'ng'iroq navbat boshiga qo'yiladi.

| Natija | Chiqish |
|---|---|
| Operator gaplashib bo'ldi (yoki kutish paytidagi AI yordamchi masalani hal qildi) | `answered` |
| Mijoz navbatda "1 — qayta qo'ng'iroq" ni tanladi | `callback` (navbatda callback kampaniyasi bo'lsa, mijoz unga qo'shiladi) |
| Hech kim tizimga kirmagan / navbat to'la / kutish vaqti tugadi / navbat o'chirilgan | `overflow`. **`overflow` chiqishi berilmagan** bo'lsa — navbatning o'z overflow sozlamasi bajariladi va qo'ng'iroq tugaydi |
| Mijoz kutib turib go'shakni qo'ydi | qo'ng'iroq tugaydi |

### `RING_GROUP` / `EXTENSION`

`userIds` operatorlarini jiringlatadi (navbat e'lonlarisiz, gudok bilan): `RING_GROUP` —
hammasini birdaniga, `EXTENSION` — bitta operatorni. `timeoutSec` (standart **25**) ichida
javob berilsa, suhbatdan keyin → `answered`; aks holda → `no_answer`.

### `FORWARD_EXTERNAL`

`phoneNumber` raqamiga trunk orqali qo'ng'iroq qiladi (`timeoutSec`, standart **25**).
`confirmAnswer: true` bo'lsa javob bergan odam "Kompaniyaga kiruvchi qo'ng'iroq. Qabul
qilish uchun 1 ni bosing" ni eshitadi va faqat 1 ni bossa ulanadi. Chiqishlar: `answered`
/ `no_answer`.

### `VOICEMAIL`

`voicemailBoxId` qutisining salomlashuvi → signal → mijoz xabari (# bosguncha, 5 soniya
jim qolguncha, qutining `maxSeconds` chegarasigacha yoki go'shak qo'yilguncha). Xabar
[voicemail.md](voicemail.md) ro'yxatiga tushadi. Keyin `next` (quti o'chirilgan/yo'q
bo'lsa ham `next`, xabar olinmaydi).

### `CALLBACK_OFFER`

`prompt`/`audioFileId` bo'lmasa standart ibora: "Sizga qayta qo'ng'iroq qilishimizni
xohlasangiz, 1 ni bosing." 7 soniya kutiladi:

- `1` → "Rahmat. Operatorimiz sizga imkon qadar tezroq qayta qo'ng'iroq qiladi." →
  `accepted`. Mijoz o'tkazib yuborilgan qo'ng'iroqlar ro'yxatiga "qayta qo'ng'iroq so'radi"
  sababi bilan tushadi (operatorlar uchun; bu tugun avtomatik kampaniya yaratmaydi).
- boshqa tugma / jimlik → `declined`.

### `CALLER_ROUTER`

Birinchi mos kelgan chiqish (faqat `exits`da berilganlari tekshiriladi):

1. `vip` — qo'ng'iroq qiluvchiga [`VIP` qoidasi](inbound-routes.md#qongiroq-qiluvchi-qoidalari-apiinbound-caller-rules) mos kelgan;
2. `known` — mijoz tanish: CRM'da ismi bor yoki mijoz xotirasida `preferredName` bor;
3. `repeat` — shu raqam oxirgi **24 soatda** qo'ng'iroq qilib, javob olgan (qayta
   qo'ng'iroq odatda masala hal bo'lmaganini bildiradi);
4. `default` — qolgan hamma.

### `WEBHOOK_ROUTER`

Server `url`ga **POST** qiladi (3 soniya timeout, redirect'larga ergashmaydi):

```json
{
  "callerNumber": "998901234567",
  "did": "998712345678",
  "language": "uz-UZ",
  "callId": 5123,
  "vip": false,
  "choices": ["uy"]
}
```

`choices` — mijoz shu paytgacha menyularda tanlagan variantlar. So'rovda
`X-RobotCall-Delivery` (har so'rovga yangi UUID) bor; kompaniyada `WEBHOOK_SIGNING_SECRET`
secret'i bo'lsa `X-RobotCall-Signature` bilan imzolanadi — tekshirish usuli
[webhooks.md](webhooks.md) §3 da. Endpoint `2xx` bilan qaysi chiqishga borishni qaytaradi
(javob 64 KB dan oshmasin):

```json
{ "exit": "debtor" }
```

- Qaytgan nom `exits`da bo'lsa → o'sha tugun; bo'lmasa → `default`.
- Xato (timeout, `2xx` emas, JSON'da `exit` yo'q, manzil ichki tarmoqqa qaraydi) → `error`,
  u bo'lmasa `default`.
- `exits` kalitlari `default`, `error` va istalgan o'z nomingiz (`[a-z0-9_]`, 1–40 belgi).
- `url` **ommaviy** `http(s)` manzil bo'lishi kerak: `localhost`, `127.0.0.1`, `10.x`,
  `192.168.x`, Docker tarmog'i va shu kabi ichki manzillarga server so'rov yubormaydi.
  `/publish` bunday oqimni `400 CALL_FLOW_INVALID` bilan rad etadi (`/validate` va qoralamani
  saqlash tekshirmaydi). Publish'dan keyin DNS ichki manzilga o'zgarib qolsa, qo'ng'iroq
  paytida `error` chiqishiga ketadi.

### `HANGUP`

Qo'ng'iroqni tugatadi. `exits` bo'sh bo'lishi shart.

---

## ✅ Tekshiruv qoidalari

`POST /{id}/validate` va `POST /{id}/publish` quyidagilarni tekshiradi (`CallFlowValidator`).
Hamma muammo birdaniga yig'iladi, shuning uchun muharrir ularni bir vaqtda ko'rsata oladi.

**Grafning shakli:**

- kamida bitta tugun, ko'pi bilan 200;
- `entry` mavjud tugun;
- tugun id — `[A-Za-z0-9_-]{1,40}`; har tugunda `type` bor;
- har bir chiqish kaliti shu tur uchun ruxsat etilgan ([jadval](#-tugun-turlari-callflownodetype))
  va mavjud tugunga olib boradi;
- turga xos qoidalar:

| Tur | Qoida |
|---|---|
| PLAY | `prompt`da kamida bitta bo'sh bo'lmagan matn yoki `audioFileId` |
| SCHEDULE | `scheduleId` bor; `open` va `closed` chiqishlari **ikkalasi ham** bor |
| MENU | prompt/audio; kamida bitta `options`; tugmalar `0-9`, `*`, `#`; har bir variant mavjud tugunga olib boradi; `keywords` kalitlari `options`da bor; `timeoutSec` 1–30, `maxRetries` 0–5, `extensionDigits` 2–6 |
| LANGUAGE_SELECT | prompt/audio; kamida bitta `options`; tugmalar `0-9`, `*`, `#`; qiymatlar til tegi (`uz-UZ`); `language` (berilsa) til tegi; `timeoutSec` 1–30 |
| AI_AGENT | `aiAgentId` bor |
| QUEUE | `queueId` bor |
| RING_GROUP | `userIds` — kamida bitta; `timeoutSec` 5–120 |
| EXTENSION | `userIds` — aynan bitta; `timeoutSec` 5–120 |
| FORWARD_EXTERNAL | `phoneNumber` — telefon raqami (`^\+?[1-9]\d{8,14}$`); `timeoutSec` 5–120 |
| VOICEMAIL | `voicemailBoxId` bor |
| WEBHOOK_ROUTER | `url` `http://` yoki `https://` bilan boshlanadi; chiqish nomlari `[a-z0-9_]{1,40}` |
| HANGUP | chiqishlari yo'q |
| CALLBACK_OFFER, CALLER_ROUTER | qo'shimcha talab yo'q |

**Havolalar** (faqat shakl xatosiz bo'lsa tekshiriladi): `scheduleId`, `queueId`,
`aiAgentId`, `voicemailBoxId`, `userIds` va `audioFileId` mavjud va **shu kompaniyaniki**.

Muammo matnlari inson o'qiydigan ingliz tilidagi satrlar, tugun id'si bilan boshlanadi:
`"main: option 3 leads to missing node 'sales'"`, `"hours: SCHEDULE needs both an 'open' and a 'closed' exit"`,
`"ai: AI agent 9 not found"`.

---

## `POST /api/call-flows` — yangi flow (draft)

Huquq: **CALL_FLOW_EDIT**

**Request body** (`SaveCallFlowRequest`):

```json
{
  "name": "Asosiy kiruvchi oqim",
  "description": "Uysot ofis raqami",
  "graph": {
    "entry": "greet",
    "nodes": {
      "greet": { "type": "PLAY", "prompt": { "uz-UZ": "Assalomu alaykum!" }, "exits": { "next": "bye" } },
      "bye": { "type": "HANGUP" }
    }
  }
}
```

| Maydon | Turi | Majburiymi | Izoh |
|---|---|---|---|
| `name` | string | ✅ (`@NotBlank`, ≤255) | |
| `description` | string | ❌ | Bo'sh satr `null` saqlanadi |
| `graph` | `CallFlowGraph` | ✅ (`@NotNull`) | `draftGraph`ga yoziladi, **tekshirilmaydi** |

Javob — `CallFlow` (`draftGraph` to'ldirilgan, `publishedGraph: null`, `version: 0`).

**Xatolar:** `VALIDATION_FAILED` (400), buzuq JSON — 400.

---

## `POST /api/call-flows/list` — ro'yxat

Huquq: **CALL_FLOW_READ**

Body — `CallFlowFilter`:

```json
{ "page": 0, "size": 20, "orders": { "UPDATED_AT": "DESC" } }
```

Saralanadigan ustunlar: `ID`, `NAME`, `CREATED_AT`, `UPDATED_AT`. Standart: `UPDATED_AT DESC`.

Javob — `PageableData<CallFlow>` (har bir element to'liq graflar bilan):

```json
{
  "data": {
    "totalPages": 1,
    "currentPage": 0,
    "totalElements": 1,
    "data": [
      {
        "id": 1,
        "companyId": 1,
        "name": "Asosiy kiruvchi oqim",
        "description": null,
        "draftGraph": { "entry": "greet", "nodes": { } },
        "publishedGraph": null,
        "version": 0,
        "publishedAt": null,
        "createdAt": "2026-09-01T09:00:00Z",
        "updatedAt": "2026-09-01T09:00:00Z"
      }
    ]
  },
  "message": null,
  "messageCode": null,
  "accept": true,
  "errors": null
}
```

---

## `GET /api/call-flows/{id}` — bitta flow

Huquq: **CALL_FLOW_READ** · Javob — `CallFlow`.

**Xatolar:** `CALL_FLOW_NOT_FOUND` (404).

---

## `PUT /api/call-flows/{id}` — draft'ni saqlash

Huquq: **CALL_FLOW_EDIT** · Body — `SaveCallFlowRequest` (yuqoridagidek).

`name`, `description` va `draftGraph`ni almashtiradi. **Jonli qo'ng'iroqlar nashr qilingan
versiyada qoladi** — o'zgarish faqat `publish`dan keyin kuchga kiradi. `name`/`description`
esa darhol o'zgaradi.

**Xatolar:** `CALL_FLOW_NOT_FOUND` (404), `VALIDATION_FAILED` (400).

---

## `DELETE /api/call-flows/{id}` — o'chirish

Huquq: **CALL_FLOW_EDIT** · Javob — `data: null`.

Flow butunlay o'chiriladi. Unga biriktirilgan kiruvchi marshrutlarning `callFlowId`si
`null` bo'ladi — ular endi flow'siz ishlaydi (ish vaqti + AI agent,
[inbound-routes.md](inbound-routes.md#-kiruvchi-qongiroq-qanday-hal-qilinadi)).

**Xatolar:** `CALL_FLOW_NOT_FOUND` (404).

---

## `POST /api/call-flows/{id}/validate` — nashr qilsa bo'ladimi?

Huquq: **CALL_FLOW_READ** · Body yo'q.

**Draft**ni [tekshiruv qoidalari](#-tekshiruv-qoidalari) bo'yicha tekshiradi, hech narsani
o'zgartirmaydi. Muammolar bo'lsa ham javob `200` bo'ladi.

**Response** (`CallFlowValidationResponse`):

```json
{
  "data": {
    "valid": false,
    "problems": [
      "main: option 3 leads to missing node 'sales'",
      "hours: SCHEDULE needs both an 'open' and a 'closed' exit"
    ]
  },
  "message": null,
  "messageCode": null,
  "accept": true,
  "errors": null
}
```

`valid: true` bo'lsa `problems: []`. Havolalar (jadval, navbat, agent, …) faqat shakl
muammosiz bo'lganda tekshiriladi.

**Xatolar:** `CALL_FLOW_NOT_FOUND` (404).

---

## `POST /api/call-flows/{id}/publish` — nashr qilish

Huquq: **CALL_FLOW_EDIT** · Body yo'q.

Draft tekshiriladi (validate bilan bir xil); muammo bo'lmasa `publishedGraph := draftGraph`,
`version` bittaga oshadi, `publishedAt` yangilanadi. Shu lahzadan boshlab **yangi**
qo'ng'iroqlar yangi versiyani bajaradi. Javob — yangilangan `CallFlow`.

Muammo bo'lsa hech narsa o'zgarmaydi va `400 CALL_FLOW_INVALID` qaytadi — har bir muammo
`errors` ro'yxatida alohida qator (`"<tugun>: <muammo>"`, `/validate` dagi `problems` bilan bir
xil shakl), `message`da esa faqat ularning soni:

```json
{
  "data": null,
  "message": "Invalid call flow: 2 problem(s), listed in errors",
  "messageCode": "CALL_FLOW_INVALID",
  "accept": false,
  "errors": [
    "main: option 3 leads to missing node 'sales'",
    "ai: AI agent 9 not found"
  ]
}
```

> **O'zgarish:** ilgari muammolar `message` ichida `; ` bilan birlashtirilib kelardi va
> `errors` `null` edi. `messageCode` o'zgarmadi. `WEBHOOK_ROUTER` ning ommaviy bo'lmagan `url`i
> endi `"<tugun>: url must be a public http(s) address"` qatori bo'lib keladi (ilgari tugun
> nomi va `Invalid call flow:` prefiksi ikki marta takrorlanardi).

**Xatolar:** `CALL_FLOW_NOT_FOUND` (404), `CALL_FLOW_INVALID` (400).

> Muharrirda avval `validate` chaqirib muammolarni ro'yxat ko'rinishida ko'rsatish, keyin
> `publish` qilish qulayroq.

---

## 🏠 To'liq misol — ko'chmas mulk kompaniyasining kiruvchi oqimi

Salomlashish → ish vaqti → til tanlash → asosiy menyu (tugma yoki ovoz bilan) → AI agent
yoki operatorlar → ish vaqtidan tashqari: qayta qo'ng'iroq taklifi yoki ovozli pochta.
`scheduleId` / `aiAgentId` / `queueId` / `voicemailBoxId` ni o'zingiznikiga almashtiring.

```json
{
  "name": "Asosiy kiruvchi oqim",
  "graph": {
    "entry": "greet",
    "nodes": {
      "greet": {
        "type": "PLAY",
        "prompt": {
          "uz-UZ": "Assalomu alaykum! Uysot kompaniyasiga xush kelibsiz.",
          "ru-RU": "Здравствуйте! Добро пожаловать в компанию Uysot."
        },
        "exits": { "next": "hours" }
      },
      "hours": {
        "type": "SCHEDULE",
        "scheduleId": 1,
        "exits": { "open": "lang", "closed": "closed", "holiday": "closed" }
      },
      "lang": {
        "type": "LANGUAGE_SELECT",
        "prompt": { "uz-UZ": "O'zbek tili uchun 1 ni, rus tili uchun 2 ni bosing." },
        "options": { "1": "uz-UZ", "2": "ru-RU" },
        "timeoutSec": 5,
        "language": "uz-UZ",
        "skipWhenKnown": true,
        "exits": { "next": "main" }
      },
      "main": {
        "type": "MENU",
        "prompt": {
          "uz-UZ": "Uylar haqida ma'lumot uchun 1 ni, to'lovlar bo'yicha 2 ni, operator bilan bog'lanish uchun 0 ni bosing. Qayta eshitish uchun yulduzchani bosing.",
          "ru-RU": "Информация о квартирах — нажмите 1, оплата — 2, оператор — 0. Повторить — звёздочка."
        },
        "options": { "1": "ai", "2": "operators", "0": "operators", "*": "main" },
        "keywords": {
          "1": ["uy", "kvartir", "квартир"],
          "2": ["to'lov", "оплат"],
          "0": ["operator", "оператор"]
        },
        "timeoutSec": 6,
        "maxRetries": 2,
        "exits": { "timeout": "operators", "invalid": "main" }
      },
      "ai": {
        "type": "AI_AGENT",
        "aiAgentId": 1,
        "exits": { "transfer": "operators", "completed": "bye" }
      },
      "operators": {
        "type": "QUEUE",
        "queueId": 1,
        "exits": { "answered": "bye", "overflow": "vm", "callback": "bye" }
      },
      "closed": {
        "type": "PLAY",
        "prompt": {
          "uz-UZ": "Afsuski, {afterHours}",
          "ru-RU": "К сожалению, {afterHours}"
        },
        "exits": { "next": "cb" }
      },
      "cb": {
        "type": "CALLBACK_OFFER",
        "exits": { "accepted": "bye", "declined": "vm" }
      },
      "vm": {
        "type": "VOICEMAIL",
        "voicemailBoxId": 1,
        "exits": { "next": "bye" }
      },
      "bye": { "type": "HANGUP" }
    }
  }
}
```

Keyin: `POST /api/call-flows/{id}/validate` → `POST /api/call-flows/{id}/publish` → marshrutga
biriktirish:

```http
PUT /api/inbound-routes/1
{ "didNumber": "600", "callFlowId": 1, "enabled": true }
```

(`PUT` marshrutning hamma maydonini almashtiradi — [inbound-routes.md](inbound-routes.md#put-apiinbound-routesid--tahrirlash).)
