# Kiruvchi qo'ng'iroq va Virtual PBX (OnlinePBX) marshrutlash API

`uz.murodjon.robotcallv2.inbound` · huquq: **INBOUND_ROUTE_READ** / **INBOUND_ROUTE_EDIT**

Kiruvchi marshrut DID raqamni (kompaniyaga tushadigan telefon raqamini) qo'ng'iroqni kim
qabul qilishiga bog'laydi:

- **[Call flow](call-flows.md)** (`callFlowId`) — to'liq IVR/PBX grafi: ish vaqti, til tanlash,
  tugmali/ovozli menyu, AI agent, operatorlar navbati, guruh/ichki raqam, tashqi raqamga
  yo'naltirish, ovozli pochta, qayta qo'ng'iroq taklifi. **Navbat, IVR va yo'naltirishlar
  endi faqat shu yo'l bilan quriladi.**
- **AI agent** (`aiAgentId`) — flow'siz oddiy marshrut: ish vaqti ichida qo'ng'iroqqa agent
  javob beradi.

Bundan tashqari kompaniya darajasidagi
[qo'ng'iroq qiluvchi qoidalari](#qongiroq-qiluvchi-qoidalari-apiinbound-caller-rules)
(qora ro'yxat / VIP) har qanday marshrutdan oldin ishlaydi.

Do-not-call ro'yxati kiruvchi qo'ng'iroqlarga **qo'llanilmaydi** — bir marta
"qo'ng'iroq qilmang" degan mijoz, o'zi qo'ng'iroq qilsa, baribir javob oladi
(ROADMAP C.2).

Umumiy javob shakli, xatolar va pagination konventsiyasi uchun
[README.md](README.md)ga qarang.

---

## 🚦 Kiruvchi qo'ng'iroq qanday hal qilinadi

Qo'ng'iroqqa javob berishdan **oldin** (mijoz hali gudok eshitayotganda), tartib bo'yicha
(`InboundCallGate`):

1. **Marshrut** — terilgan DID bo'yicha yoqilgan (`enabled: true`) marshrut. Topilmasa —
   DID kompaniyaga biriktirilgan [telefon raqami](phone-numbers.md) bo'lsa, uning **o'z sloti**
   (`inboundAgentId` yoki `inboundCallFlowId`, `PUT /api/phone-numbers/{id}/routing`) javob beradi:
   ish vaqti, after-hours va callback yo'q, agent har doim oladi; qo'ng'iroq tarixida
   `inboundRouteId` bo'sh. Slot ham bo'sh bo'lsa — go'shak qo'yiladi. **Marshrut doim slotdan
   ustun:** soatlar yoki after-hours kerak bo'lsa marshrut yarating.
2. **Qo'ng'iroq qiluvchi qoidasi** — kompaniyaning qoidalaridan eng uzun mos prefiks.
   `BLOCK_HANGUP` / `BLOCK_BUSY` / `BLOCK_MESSAGE` bo'lsa — qo'ng'iroq rad etiladi
   (qo'ng'iroqlar tarixida `REFUSED` natijasi bilan yoziladi) va boshqa hech narsa
   bajarilmaydi. `VIP` bo'lsa — qo'ng'iroq davom etadi, lekin VIP belgisi bilan.
3. **Call flow** — marshrutda `callFlowId` bo'lsa, flow'ning **nashr qilingan** versiyasi
   bajariladi. Ish vaqti, agent va boshqa hamma narsani flow'ning o'zi hal qiladi
   (marshrutning `scheduleId`, soatlari, `aiAgentId`, `fallbackMessage`i **ishlatilmaydi**).
4. **Ish vaqti** (flow bo'lmasa) — `scheduleId` bo'lsa shu [jadval](schedules.md), aks holda
   marshrutning `businessHoursStart`/`businessHoursEnd` oynasi (har kuni). Ikkalasi ham
   kompaniya zonasida va kompaniya [bayramlari](schedules.md#bayramlar-apiholidays) bilan
   hisoblanadi; soatlar ham, jadval ham bo'lmasa — 24/7 ochiq.
   - **Yopiq** bo'lsa: `afterHoursAction` sozlamasi tekshiriladi:
     - `AI_AGENT`: Tungi AI agentga (`afterHoursDestination` dagi agent ID, u berilmagan bo'lsa marshrutning o'z `aiAgentId`si) uzatiladi va 24/7 rejimda qabul qilinadi.
     - `VOICEMAIL`: Ish vaqtidan tashqari ekanligi xabari hamda ovozli xabar qoldirish taklifi aytiladi va go'shak qo'yiladi.
     - `PLAY_MESSAGE_AND_HANGUP` (standart): `fallbackMessage` (yoki jadvaldan yasalgan "Ertaga soat to'qqizda ishni boshlaymiz" iborasi) o'qiladi va go'shak qo'yiladi.
     Raqami ma'lum qo'ng'iroq qiluvchi o'tkazib yuborilgan qo'ng'iroqlar ro'yxatiga "ish vaqtidan tashqari" (`AFTER_HOURS`) sababi bilan tushadi.
   - **Ochiq, lekin `aiAgentId` yo'q** bo'lsa: `fallbackMessage` bo'lsa aytiladi, bo'lmasa
     jimgina go'shak qo'yiladi.
5. **AI agent** — ochiq va `aiAgentId` bor: qo'ng'iroqqa agent javob beradi.

> Tanlangan call flow biror sababga ko'ra nashr qilingan grafga ega bo'lmasa, qo'ng'iroq
> 4–5-qadamlar bo'yicha (flow'siz) davom etadi.

---

## Olib tashlangan maydonlar (2026-10-28, V22)

`routeType`, `targetDestination`, `queueStrategy`, `ringTimeoutSec`, `failoverAction`,
`failoverDestination`, `ivrMenuConfig` marshrutdan **olib tashlandi** (API, domain va DB
ustunlari). Ularni qo'ng'iroq yo'li allaqachon o'qimas edi, shuning uchun hech bir marshrutning
xatti-harakati o'zgarmaydi. Eski klient ularni yuborsa — noma'lum maydon sifatida jimgina
e'tiborsiz qoladi; javobda endi chiqmaydi.

Operatorlar navbati, ichki raqam, tashqi raqam, IVR menyu va ovozli pochta
[call flow](call-flows.md) tugunlari bilan quriladi (`QUEUE`, `EXTENSION`, `RING_GROUP`,
`FORWARD_EXTERNAL`, `MENU`, `VOICEMAIL`, `SCHEDULE`, `CALLBACK_OFFER`).

`afterHoursAction` / `afterHoursDestination` **qoladi** — flow'siz marshrutning ish vaqtidan
tashqari xatti-harakati (yuqoridagi 4-qadam).

### Ish vaqtidan tashqari amallar (`InboundAfterHoursAction`)

| Amal | Izoh |
|---|---|
| `PLAY_MESSAGE_AND_HANGUP` | `fallbackMessage` matnini o'qib berib, qo'ng'iroqni tugatadi (standart) |
| `AI_AGENT` | Tungi AI Ovozli yordamchi ssenariysiga ulaydi |
| `VOICEMAIL` | Mijozga audio xabar qoldirish imkonini beradi |
| `FORWARD_EXTERNAL` | ⚠️ Flow'siz marshrutda **amalga oshirilmagan** — `PLAY_MESSAGE_AND_HANGUP` kabi ishlaydi. Navbatchi raqamga yo'naltirish uchun call flow'ning `SCHEDULE` → `FORWARD_EXTERNAL` tugunlarini ishlating |

---

## `POST /api/inbound-routes` — yangi marshrut

Huquq: **INBOUND_ROUTE_EDIT**

**Request body** (`CreateInboundRouteRequest`) — flow bilan ishlaydigan marshrut:

```json
{
  "didNumber": "998712345678",
  "callFlowId": 1
}
```

flow'siz, AI agent javob beradigan marshrut:

```json
{
  "didNumber": "998712345678",
  "aiAgentId": 9,
  "scheduleId": 1,
  "businessHoursStart": null,
  "businessHoursEnd": null,
  "fallbackMessage": null,
  "callbackCampaignId": 14
}
```

| Maydon | Turi | Majburiymi | Izoh |
|---|---|---|---|
| `didNumber` | string | ✅ (`@NotBlank`, `^\+?[1-9]\d{8,14}$`) | Terilgan DID raqam; bitta raqamga bitta **yoqilgan** marshrut |
| `callFlowId` | long | ❌ | [Call flow](call-flows.md) ID. Berilsa qo'ng'iroq shu flow bo'yicha boradi va quyidagi `aiAgentId`/`scheduleId`/soatlar/`fallbackMessage` ishlatilmaydi. Flow shu kompaniyaniki va **kamida bir marta nashr qilingan** bo'lishi shart |
| `aiAgentId` | long | ❌ | Flow'siz marshrutda qo'ng'iroqqa javob beradigan [AI agent](ai-agents.md) — o'z senariysi, tili, ovozi va personasi bilan. Agentsiz va flow'siz marshrutga kelgan qo'ng'iroqni hech kim ko'tarmaydi. Agent senariysida [`factWebhook`](scenarios.md) sozlangan bo'lsa, qo'ng'iroqqa **javob berilishidan oldin** (mijoz hali gudok eshitayotganda) kompaniya tizimidan qo'ng'iroq qiluvchining faktlari so'raladi |
| `scheduleId` | long | ❌ | Flow'siz marshrutning ish vaqti — [ish jadvali](schedules.md) (hafta kunlari, tushlik, tungi slotlar, bayramlar). Berilsa `businessHoursStart/End` o'rniga **shu** ishlatiladi. Jadval shu kompaniyaniki bo'lishi shart |
| `businessHoursStart` / `businessHoursEnd` | `HH:mm` | ❌ | Eski, oddiy ish vaqti: har kuni shu oyna (kompaniya zonasida, bayramlar hisobga olinadi). `end < start` — tungi oyna (`22:00`–`06:00`). Ikkalasi teng bo'lishi mumkin emas. Berilmasa (va `scheduleId` ham bo'lmasa) — 24/7 ochiq |
| `fallbackMessage` | string | ❌ | Ish vaqtidan tashqari (yoki agentsiz marshrutda) aytiladigan matn. Bo'sh bo'lsa ish vaqtidan tashqari ibora jadvaldan avtomatik yasaladi ("… Ertaga soat to'qqizda ishni boshlaymiz.") |
| `callbackCampaignId` | long | ❌ | Qo'ng'iroq qiluvchi "keyinroq qo'ng'iroq qiling, soat 5 da" desa (yakuniy xulosada `callbackAt`), u shu kampaniyaga **yangi nishon** bo'lib qo'shiladi va aynan o'sha vaqtga rejalashtiriladi (kampaniyaning dial-window'i hisobga olinadi). Kampaniya shu kompaniyaniki bo'lishi shart, aks holda `404`. Kampaniya `ACTIVE` bo'lmasa nishon navbatda kutadi. `null` — so'rov faqat qo'ng'iroq natijasiga yoziladi, hech kim qayta qo'ng'iroq qilmaydi |
| `afterHoursAction` | `InboundAfterHoursAction` | ❌ | Flow'siz marshrut ish vaqtidan tashqarida nima qiladi ([jadval](#ish-vaqtidan-tashqari-amallar-inboundafterhoursaction)). Standart `PLAY_MESSAGE_AND_HANGUP`; `PUT` da `null` — avvalgi qiymat qoladi |
| `afterHoursDestination` | string | ❌ | `AI_AGENT` uchun tungi agent ID'si; bo'sh bo'lsa — marshrutning `aiAgentId`si |

Yangi marshrut har doim `enabled: true` bilan yaratiladi.

**Response** — `ResponseData<InboundRouteRow>`:

```json
{
  "data": {
    "id": 3,
    "didNumber": "998712345678",
    "aiAgentId": 9,
    "aiAgentName": "Kirish so'rovlari agenti",
    "afterHoursAction": "PLAY_MESSAGE_AND_HANGUP",
    "afterHoursDestination": null,
    "businessHoursStart": null,
    "businessHoursEnd": null,
    "fallbackMessage": null,
    "callbackCampaignId": 14,
    "scheduleId": 1,
    "callFlowId": null,
    "enabled": true,
    "createdAt": "2026-09-01T09:00:00Z"
  },
  "message": null,
  "messageCode": null,
  "accept": true,
  "errors": null
}
```

**Xatolar:**

| Kod | Status | Qachon |
|---|---|---|
| `VALIDATION_FAILED` | 400 | `didNumber` bo'sh/format noto'g'ri |
| `INBOUND_ROUTE_HOURS_IDENTICAL` | 400 | `businessHoursStart == businessHoursEnd` |
| `PHONE_INVALID` | 400 | `didNumber` normallashtirilmadi |
| `AI_AGENT_NOT_FOUND` | 404 | `aiAgentId` topilmadi (yoki boshqa kompaniyaniki) |
| `CAMPAIGN_NOT_FOUND` | 404 | `callbackCampaignId` topilmadi |
| `BUSINESS_SCHEDULE_NOT_FOUND` | 404 | `scheduleId` topilmadi |
| `CALL_FLOW_NOT_FOUND` | 404 | `callFlowId` topilmadi |
| `CALL_FLOW_NOT_PUBLISHED` | 409 | Flow hali hech qachon nashr qilinmagan — avval `POST /api/call-flows/{id}/publish` |
| `INBOUND_ROUTE_DID_EXISTS` | 409 | Shu DID uchun yoqilgan marshrut allaqachon bor |
| `INBOUND_ROUTE_DID_PLATFORM_ONLY` | 403 | DID raqamni faqat platform admin (`PLATFORM_ADMIN`) biriktiradi |

**DID raqam — platformaniki.** Marshrut DID'i shu raqamga keladigan har bir qo'ng'iroqni qaysi
kompaniya olishini hal qiladi, backend esa raqam haqiqatan shu kompaniyaniki ekanini bilmaydi —
ilgari bir kompaniya boshqasining raqamiga marshrut ochib, uning kiruvchi qo'ng'iroqlarini (va
yozuvlarini) olib qo'yishi mumkin edi. Shuning uchun yangi marshrut yaratish va `didNumber`ni
o'zgartirish faqat platform admin uchun; kompaniya admini qolgan maydonlarni tahrirlashda davom
etadi. Qo'shimcha himoya: managed trunk (`trunk_<companyId>_<id>`) orqali kelgan qo'ng'iroq
faqat o'sha trunk egasining marshrutiga tushadi, aks holda qo'ng'iroq uziladi.

---

## `POST /api/inbound-routes/list` — ro'yxat

Huquq: **INBOUND_ROUTE_READ**

Body — `InboundRouteFilter` (`page`/`size`/`orders`).
Saralash: `ID`, `DID_NUMBER`, `AI_AGENT_ID`, `ENABLED`, `CREATED_AT`.
Standart: `CREATED_AT DESC`.

Javob — `PageableData<InboundRouteRow>`.

---

## `GET /api/inbound-routes/{id}` — bitta marshrut

Huquq: **INBOUND_ROUTE_READ** · Javob — `InboundRouteRow`.

**Xatolar:** `INBOUND_ROUTE_NOT_FOUND` (404).

---

## `PUT /api/inbound-routes/{id}` — tahrirlash

Huquq: **INBOUND_ROUTE_EDIT**

**Request body** (`UpdateInboundRouteRequest`) — `POST /api/inbound-routes` bilan bir xil
maydonlar, plus `enabled`:

```json
{
  "didNumber": "998712345678",
  "callFlowId": 1,
  "enabled": true
}
```

> ⚠️ `PUT` — **to'liq almashtirish**. Yuborilmagan `callFlowId`, `scheduleId`, `aiAgentId`,
> soatlar, `fallbackMessage`, `callbackCampaignId` `null` bo'ladi (flow yoki jadvalni
> olib tashlash uchun ham shunchaki `null` yuboring). **`enabled` yuborilmasa — `false`**
> (marshrut o'chadi), shuning uchun uni har doim yuboring. `afterHoursAction` `null` bo'lsa
> avvalgi qiymati qoladi; `afterHoursDestination` esa boshqa maydonlar kabi `null` bo'ladi.

**Xatolar:** `INBOUND_ROUTE_NOT_FOUND` (404) va `POST` dagi barcha xatolar
(`INBOUND_ROUTE_DID_EXISTS` faqat `enabled: true` bo'lsa tekshiriladi;
`INBOUND_ROUTE_DID_PLATFORM_ONLY` faqat `didNumber` o'zgarsa).

---

## `DELETE /api/inbound-routes/{id}` — o'chirish (arxivlash)

Huquq: **INBOUND_ROUTE_EDIT**

Qatorni o'chirmaydi, `enabled=false` qiladi. Javob — yangilangan `InboundRouteRow`.

**Xatolar:** `INBOUND_ROUTE_NOT_FOUND` (404).

---

## `GET /api/inbound-routes/{id}/stats` — statistika

Huquq: **INBOUND_ROUTE_READ**

Qo'ng'iroqlar soni, qabul qilish foizi va dispositionlar taqsimoti.

**Xatolar:** `INBOUND_ROUTE_NOT_FOUND` (404).

---

## Qo'ng'iroq qiluvchi qoidalari (`/api/inbound-caller-rules`)

Huquq: **INBOUND_ROUTE_READ** / **INBOUND_ROUTE_EDIT**

Kompaniya darajasidagi **qora ro'yxat va VIP ro'yxat**: har qanday marshrut va call
flow'dan **oldin**, kompaniyaning barcha DID raqamlari uchun ishlaydi. Do-not-call'dan
alohida: DNC — mijozning bizning qo'ng'iroqlarimizdan voz kechishi, bu — kompaniyaning
qo'ng'iroq qiluvchini (spam, bezori) rad etishi.

### Amallar (`CallerRuleAction`)

| Amal | Nima bo'ladi |
|---|---|
| `BLOCK_HANGUP` | Darhol go'shak qo'yiladi |
| `BLOCK_BUSY` | "Band" signali — qo'ng'iroq qiluvchi oddiy band gudokini eshitadi |
| `BLOCK_MESSAGE` | Javob beriladi, qoidaning `message` matni aytiladi, go'shak qo'yiladi (`message` majburiy) |
| `VIP` | Qo'ng'iroq odatdagidek davom etadi, lekin: har qanday operator navbatida **birinchi** o'ringa qo'yiladi, call flow'dagi `CALLER_ROUTER` tugunining `vip` chiqishini oladi, operator ulanishdan oldin "VIP mijoz" ni eshitadi |

Rad etilgan qo'ng'iroqlar ham qo'ng'iroqlar tarixiga (`REFUSED` natijasi va qoida prefiksi
bilan) yoziladi.

### Moslash qoidalari

- `phonePrefix` — qo'ng'iroq qiluvchi raqami boshlanadigan raqamlar: butun raqam
  (`998901112233`) yoki operator kodi (`99890`). 1–15 raqam; boshidagi `+` saqlanmaydi
  (`+99890` → `99890`). Solishtirish faqat raqamlar bo'yicha — `+` bor-yo'qligi farq qilmaydi.
- **Eng uzun mos prefiks yutadi**: `99890` → `BLOCK_BUSY` va `998901234567` → `VIP` bo'lsa,
  `998901234567` VIP bo'ladi, `99890` bilan boshlanadigan qolgan raqamlar esa bloklanadi.
- **`ANONYMOUS`** — maxsus prefiks (`InboundCallerRule.ANONYMOUS`): raqamini **yashirgan**
  (caller ID'siz) qo'ng'iroqlarga mos keladi. Katta-kichik harf farqi yo'q — `"anonymous"`
  ham `ANONYMOUS` bo'lib saqlanadi.
- Bitta kompaniyada bitta prefiksga faqat bitta qoida.

### Obyekt shakli (`InboundCallerRule`)

```json
{
  "id": 5,
  "companyId": 1,
  "phonePrefix": "998901112233",
  "action": "BLOCK_BUSY",
  "message": null,
  "note": "spam",
  "createdAt": "2026-09-01T09:00:00Z"
}
```

---

### `POST /api/inbound-caller-rules` — yangi qoida

Huquq: **INBOUND_ROUTE_EDIT**

**Request body** (`SaveCallerRuleRequest`):

```json
{
  "phonePrefix": "ANONYMOUS",
  "action": "BLOCK_MESSAGE",
  "message": "Iltimos, raqamingizni yashirmasdan qayta qo'ng'iroq qiling.",
  "note": "yashirin raqamlar"
}
```

| Maydon | Turi | Majburiymi | Izoh |
|---|---|---|---|
| `phonePrefix` | string | ✅ (`@NotBlank`) | 1–15 raqam (boshida `+` mumkin) yoki `ANONYMOUS` |
| `action` | enum | ✅ (`@NotNull`) | `BLOCK_HANGUP`, `BLOCK_BUSY`, `BLOCK_MESSAGE`, `VIP` |
| `message` | string | `BLOCK_MESSAGE` da ✅ | ≤ 1000 belgi; kompaniyaning standart tilida TTS bilan aytiladi. Boshqa amallarda saqlanadi, lekin ishlatilmaydi |
| `note` | string | ❌ | ≤ 255 belgi, ichki izoh ("spam", "Katta investor") |

Javob — yaratilgan `InboundCallerRule`.

**Xatolar:** `VALIDATION_FAILED` (400), `CALLER_RULE_PREFIX_INVALID` (400 — prefiks formati
noto'g'ri), `CALLER_RULE_MESSAGE_REQUIRED` (400 — `BLOCK_MESSAGE` uchun `message` yo'q),
`CALLER_RULE_PREFIX_EXISTS` (409 —
shu prefiksga qoida allaqachon bor).

---

### `POST /api/inbound-caller-rules/list` — ro'yxat

Huquq: **INBOUND_ROUTE_READ**

Body — `InboundCallerRuleFilter`:

```json
{ "page": 0, "size": 20, "orders": { "PHONE_PREFIX": "ASC" } }
```

Saralanadigan ustunlar: `ID`, `PHONE_PREFIX`, `CREATED_AT`. Standart: `PHONE_PREFIX ASC`.

Javob — `PageableData<InboundCallerRule>`:

```json
{
  "data": {
    "totalPages": 1,
    "currentPage": 0,
    "totalElements": 2,
    "data": [
      { "id": 5, "companyId": 1, "phonePrefix": "998901112233", "action": "BLOCK_BUSY", "message": null, "note": "spam", "createdAt": "2026-09-01T09:00:00Z" },
      { "id": 6, "companyId": 1, "phonePrefix": "998935550000", "action": "VIP", "message": null, "note": "Katta investor", "createdAt": "2026-09-02T10:00:00Z" }
    ]
  },
  "message": null,
  "messageCode": null,
  "accept": true,
  "errors": null
}
```

> `GET /api/inbound-caller-rules/{id}` endpointi **yo'q** — qoida ro'yxatdan olinadi.

---

### `PUT /api/inbound-caller-rules/{id}` — tahrirlash

Huquq: **INBOUND_ROUTE_EDIT** · Body — `POST` bilan bir xil (`SaveCallerRuleRequest`), to'liq
almashtiradi. Javob — yangilangan `InboundCallerRule`.

**Xatolar:** `CALLER_RULE_NOT_FOUND` (404), `CALLER_RULE_PREFIX_INVALID` / `CALLER_RULE_MESSAGE_REQUIRED` (400),
`CALLER_RULE_PREFIX_EXISTS` (409 — yangi prefiks boshqa qoidada band), `VALIDATION_FAILED` (400).

---

### `DELETE /api/inbound-caller-rules/{id}` — o'chirish

Huquq: **INBOUND_ROUTE_EDIT** · Javob — `data: null`. Qoida butunlay o'chiriladi.

**Xatolar:** `CALLER_RULE_NOT_FOUND` (404).

---

## 🌱 Seed'dagi tayyor marshrut

Yangi baza ko'tarilganda (`R__seed_data.sql`) bitta marshrut allaqachon mavjud:

| `didNumber` | `aiAgentId` | Ish vaqti |
|---|---|---|
| `600` | `Kiruvchi qabulxona` agenti | belgilanmagan (24/7) |

`600` — dialplan'dagi softphone test raqami (`asterisk/etc/asterisk/extensions.conf`,
`[from-internal]`): trunk'siz, faqat registratsiya qilingan softphone bilan kiruvchi
qo'ng'iroqni to'liq pipeline orqali sinash uchun. Haqiqiy DID `POST /api/inbound-routes`
orqali qo'shiladi. Call flow'ni sinash uchun shu marshrutga nashr qilingan flow'ni
biriktiring (`PUT` bilan `callFlowId`).

`businessHoursStart`/`businessHoursEnd` va `scheduleId` ataylab bo'sh: ish vaqti belgilangan
zahoti undan tashqarida `fallbackMessage` (yoki avtomatik ibora) aytiladi va qo'ng'iroq
tugatiladi.
