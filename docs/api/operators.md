# Operatorlar (SIP akkaunt, status, panel oqimi) API

> Uzatilgan qo'ng'iroq konteksti, takeover va whisper (`/api/operator`, birlik) —
> alohida hujjat: [operator.md](operator.md).

`uz.murodjon.robotcallv2.operator` · huquq: **USER_EDIT** (SIP akkauntni boshqarish) ·
**OPERATOR_READ** / **OPERATOR_EDIT** (operatorning o'zi)

Operator — telefonda ishlaydigan odam. Admin foydalanuvchiga SIP akkaunt beradi; operator
shu akkaunt bilan softphone'ini ro'yxatdan o'tkazadi, o'z statusini o'zi belgilaydi va
navbatdan keladigan qo'ng'iroq takliflarini SSE oqimida oladi. Navbatlarning o'zi —
[call-queues.md](call-queues.md), javobsiz qo'ng'iroqlar — [missed-calls.md](missed-calls.md).

Umumiy javob shakli, xatolar va pagination konventsiyasi uchun
[README.md](README.md)ga qarang.

---

## 📞 SIP akkaunt qanday ishlaydi

- Har bir operatorga **bitta** PJSIP endpoint: nomi va SIP username'i — `op-<userId>`
  (masalan `op-12`). Nom foydalanuvchi kiritgan hech narsadan yasalmaydi va kompaniyalar
  bo'ylab unique.
- **Parolni admin kiritmaydi** — server 20 belgili tasodifiy parol yaratadi (o'xshash
  belgilarsiz: `0/O`, `1/l/I` yo'q). U shifrlangan saqlanadi va faqat operatorning o'ziga
  (`GET /me/sip-credentials`) yoki parolni tiklagan adminga bir marta qaytariladi.
- Akkaunt yaratilsa, o'zgarsa, paroli tiklansa yoki o'chirilsa — server Asterisk
  konfiguratsiyasini **qaytadan yozadi** (`pjsip_operators.conf`, sozlama
  `voice-agent.operator.sip-config-file-name`, `voice-agent.siptrunk.config-dir`
  papkasida) va `res_pjsip` ni AMI orqali qayta yuklaydi. Ilova ishga tushganda ham fayl
  qayta yoziladi. Bloklangan foydalanuvchining endpointi faylga kirmaydi — bloklash uning
  telefonini ham Asteriskdan chiqaradi.
- **`webrtc: true`** (standart) — brauzerdagi softphone: operator paneli JsSIP / SIP.js
  bilan `wsUrl` WebSocket'iga ulanadi va `username`/`password` bilan REGISTER qiladi
  (SIP URI odatda `sip:<username>@<wsUrl dagi host>`). Kodeklar: `ulaw`, `alaw`.
- **`webrtc: false`** — stol telefoni yoki mobil SIP ilova, LAN transporti (UDP) orqali.
  Parolni telefonga kiritish uchun `reset-password` yangi parolni qaytaradi.
- Bitta akkauntga bir vaqtda ko'pi bilan **3 ta** qurilma ro'yxatdan o'tishi mumkin.
- Operator telefoni faqat **hamkasbining ichki raqamini** (`extension`) tera oladi.
  Tashqi raqam (7 va undan ortiq raqam) ilova orqali, operator kompaniyasining trunk'idan
  chiqadi va yoziladi — sizib chiqqan SIP parol bilan tashqariga qo'ng'iroq qilib
  bo'lmaydi.
- **`forwardNumber`** — operatorning mobili. Softphone oflayn bo'lsa navbat qo'ng'irog'i
  shu raqamga trunk orqali boradi; operator 10 soniya ichida `1` ni bosib qabul qiladi
  (aks holda — miss, pastdagi RONA'ga qarang). Missed-call qayta qo'ng'irog'i
  (`call-back`) mobilga **bormaydi** — faqat softphone'ga.

---

## 🟢 Operator statuslari (`OperatorStatus`)

| Status | Kim qo'yadi | Ma'nosi |
|---|---|---|
| `OFFLINE` | ✋ operator | Tizimda emas. SIP akkauntsiz foydalanuvchi ham shu statusda ko'rinadi |
| `AVAILABLE` | ✋ operator / tizim | Qo'ng'iroq olishga tayyor — navbat **faqat** shu statusdagilarga taklif qiladi |
| `RINGING` | tizim | Unga navbat qo'ng'irog'i jiringlayapti |
| `ON_CALL` | tizim | Suhbatda |
| `WRAP_UP` | tizim | Suhbatdan keyingi dam (`wrapUpSec`), tugagach avtomatik `AVAILABLE` |
| `BREAK` | ✋ operator / tizim | Tanaffus (`reason`: `"tushlik"` va h.k.). Tizim ham qo'yadi — **RONA**: operator ketma-ket 2 ta taklifni ko'tarmasa, `reason: "javob bermadi"` bilan |

Operator o'zi faqat `AVAILABLE`, `BREAK`, `OFFLINE` ni tanlay oladi
(`selectableByOperator`); qolganlarini qo'ng'iroqning o'zi qo'yadi.

Presence ilova xotirasida saqlanadi; har bir o'zgarish `operator_status_log` ga ham
yoziladi ("statusda o'tgan vaqt" hisobotlari shundan). Ilova qayta ishga tushsa `AVAILABLE`
va `BREAK` tiklanadi, suhbat o'rtasida qolganlar `AVAILABLE` bo'ladi.

---

## `PUT /api/operators/{userId}/sip-account` — SIP akkaunt berish / o'zgartirish

Huquq: **USER_EDIT**

Akkaunt bo'lmasa yaratadi (parol generatsiya qilinadi), bo'lsa `webrtc` va
`forwardNumber` ni yangilaydi. Parol qaytarilmaydi.

**Request body** (`SaveOperatorSipAccountRequest`):

```json
{
  "webrtc": true,
  "forwardNumber": "+998901234567"
}
```

| Maydon | Turi | Majburiymi | Izoh |
|---|---|---|---|
| `webrtc` | boolean | ❌ | `true` — brauzer softphone, `false` — SIP telefon. **`null` → `true`**, tahrirlashda ham |
| `forwardNumber` | string | ❌ | `^\+?[1-9]\d{8,14}$`. `null`/bo'sh — mobilga yo'naltirish yo'q (tahrirlashda mavjud raqamni **o'chiradi**) |

Ikkala maydon har safar to'liq yuboriladi — yuborilmagan maydon eski qiymatini saqlamaydi.

**Javob** (`OperatorSipAccountRow`):

```json
{
  "data": {
    "userId": 12,
    "name": "Dilnoza Karimova",
    "extension": "101",
    "endpointName": "op-12",
    "webrtc": true,
    "forwardNumber": "998901234567",
    "createdAt": "2026-09-12T08:00:00Z"
  },
  "message": null,
  "messageCode": null,
  "accept": true,
  "errors": null
}
```

`extension` — foydalanuvchining `sipExtension` i ([users.md](users.md)): hamkasblar
teradigan raqam va telefonda ko'rinadigan caller ID.

| Holat | Javob |
|---|---|
| Foydalanuvchi topilmadi / boshqa kompaniyaniki | `404 USER_NOT_FOUND` |
| `forwardNumber` formati noto'g'ri | `400 VALIDATION_FAILED` yoki `400 PHONE_INVALID` |

Audit: `OPERATOR_SIP_ACCOUNT_CREATE` / `OPERATOR_SIP_ACCOUNT_UPDATE`.

---

## `POST /api/operators/{userId}/sip-account/reset-password` — yangi parol

Huquq: **USER_EDIT** · body yo'q

Yangi parol yaratadi va **bir marta** qaytaradi (stol telefonini sozlash uchun). Eski
parol Asterisk konfiguratsiyasi qayta yuklangach ishlamay qoladi — ro'yxatdan o'tgan
softphone'lar qayta kirishi kerak.

**Javob** — `SipCredentialsResponse` (pastda, `GET /me/sip-credentials` bilan bir xil).

| Holat | Javob |
|---|---|
| Foydalanuvchi topilmadi | `404 USER_NOT_FOUND` |
| Unda SIP akkaunt yo'q | `404 OPERATOR_SIP_ACCOUNT_NOT_FOUND` |

Audit: `OPERATOR_SIP_PASSWORD_RESET`.

---

## `DELETE /api/operators/{userId}/sip-account` — SIP akkauntni o'chirish

Huquq: **USER_EDIT**

Akkaunt o'chiriladi, operator `OFFLINE` qilinadi, endpoint Asteriskdan olib tashlanadi.
Javob: `data: null`, `accept: true`. Akkaunt yo'q bo'lsa — `404
OPERATOR_SIP_ACCOUNT_NOT_FOUND`. Audit: `OPERATOR_SIP_ACCOUNT_DELETE`.

---

## `POST /api/operators/list` — operatorlar ro'yxati (supervisor ekrani)

Huquq: **OPERATOR_READ**

Faqat SIP akkaunti bor foydalanuvchilar, hozirgi statusi bilan.

Body — `OperatorFilter`:

```json
{ "page": 0, "size": 20, "orders": { "NAME": "ASC" }, "status": "AVAILABLE" }
```

| Maydon | Izoh |
|---|---|
| `status` | Faqat shu statusdagilar; `null` — hammasi |
| `orders` | Ustunlar: `USER_ID`, `NAME`, `STATUS`. Standart: `NAME ASC`. Faqat **birinchi** kalit hisobga olinadi |

**Javob qatori** (`OperatorRow`, `PageableData<OperatorRow>` ichida):

```json
{
  "userId": 12,
  "name": "Dilnoza Karimova",
  "extension": "101",
  "status": "ON_CALL",
  "reason": null,
  "since": "2026-09-12T09:14:03Z",
  "channelId": "1757664031.121",
  "callsToday": 17,
  "webrtc": true,
  "forwardNumber": null
}
```

| Maydon | Izoh |
|---|---|
| `reason` | Faqat `BREAK` da (`"tushlik"`, `"javob bermadi"`) |
| `since` | Hozirgi status qachondan |
| `channelId` | Jiringlayotgan yoki ulangan qo'ng'iroq kanali (monitor/transfer shu bilan ishlaydi); bo'lmasa `null` |
| `callsToday` | Bugun ko'tarilgan qo'ng'iroqlar (yarim tunda nolga tushadi) |

---

## `GET /api/operators/me/sip-credentials` — softphone uchun login

Huquq: **OPERATOR_READ**

Joriy foydalanuvchining SIP ma'lumotlari — operator paneli softphone'ni shu bilan
ro'yxatdan o'tkazadi.

**Javob** (`SipCredentialsResponse`):

```json
{
  "data": {
    "username": "op-12",
    "password": "k7XmPq2RtWz9hN4bVc3s",
    "wsUrl": "wss://voice.app.uysot.uz:8089/ws",
    "extension": "101",
    "webrtc": true
  },
  "message": null,
  "messageCode": null,
  "accept": true,
  "errors": null
}
```

| Maydon | Izoh |
|---|---|
| `username` | SIP username va PJSIP endpoint, `op-<userId>` |
| `password` | SIP parol (ochiq matn) — faqat shu operatorga |
| `wsUrl` | Brauzer softphone ulanadigan WebSocket (`voice-agent.asterisk.web-test.ws-url`); sozlanmagan bo'lsa `null` |
| `extension` | Hamkasblar teradigan ichki raqam |
| `webrtc` | `false` bo'lsa bu akkaunt stol telefoni uchun — brauzer softphone'ni ishga tushirmang |

| Holat | Javob |
|---|---|
| Foydalanuvchida SIP akkaunt yo'q | `404 OPERATOR_SIP_ACCOUNT_NOT_FOUND` |
| API kalit bilan chaqirilgan (odam emas) | `403 NO_USER_SESSION` |

---

## `GET /api/operators/me/status` — mening statusim

Huquq: **OPERATOR_READ**

**Javob** (`OperatorPresence`):

```json
{
  "data": {
    "userId": 12,
    "companyId": 1,
    "status": "BREAK",
    "reason": "tushlik",
    "since": "2026-09-12T12:00:00Z",
    "channelId": null,
    "lastCallEndedAt": "2026-09-12T11:58:40Z",
    "callsToday": 17,
    "consecutiveMisses": 0,
    "wrapUpUntil": null
  },
  "message": null,
  "messageCode": null,
  "accept": true,
  "errors": null
}
```

| Maydon | Izoh |
|---|---|
| `lastCallEndedAt` | Oxirgi suhbat qachon tugagan (`LEAST_RECENT` strategiyasi kaliti) |
| `consecutiveMisses` | Ketma-ket ko'tarilmagan takliflar; 2 ga yetsa — `BREAK` |
| `wrapUpUntil` | `WRAP_UP` qachon o'zi tugaydi; boshqa statuslarda `null` |

API kalit bilan — `403 NO_USER_SESSION`.

---

## `PUT /api/operators/me/status` — statusni o'zgartirish

Huquq: **OPERATOR_EDIT**

**Request body** (`UpdateOperatorStatusRequest`):

```json
{ "status": "BREAK", "reason": "tushlik" }
```

| Maydon | Turi | Majburiymi | Izoh |
|---|---|---|---|
| `status` | enum | ✅ | Faqat `AVAILABLE`, `BREAK`, `OFFLINE` |
| `reason` | string (≤255) | ❌ | Faqat `BREAK` da saqlanadi, boshqa statuslarda e'tiborsiz |

`WRAP_UP` paytida `AVAILABLE` ni tanlash — wrap-up'ni muddatidan oldin tugatadi. RONA
tufayli `BREAK` ga tushgan operator qo'ng'iroq olish uchun o'zi `AVAILABLE` ni tanlaydi.

**Javob** — yangilangan `OperatorPresence`.

| Holat | Javob |
|---|---|
| `status` — `RINGING`/`ON_CALL`/`WRAP_UP` | `400 OPERATOR_STATUS_NOT_SELECTABLE` |
| `AVAILABLE`, lekin SIP akkaunt yo'q | `404 OPERATOR_SIP_ACCOUNT_NOT_FOUND` |
| Operator hozir `RINGING` yoki `ON_CALL` | `409 OPERATOR_ON_CALL` |
| API kalit bilan | `403 NO_USER_SESSION` |

---

## `GET /api/operators/me/events` — operator paneli SSE oqimi

Huquq: **OPERATOR_READ**

`Content-Type: text/event-stream`, **`ResponseData` ga o'ralmagan**. Autentifikatsiya
[live.md](live.md#muhim-browserning-tayyor-eventsourcei-ishlamaydi)dagidek: `Authorization:
Bearer` sarlavhasi yoki bir martalik `?ticket=`
([`POST /api/auth/stream-ticket`](auth.md#post-apiauthstream-ticket--oqim-uchun-bir-martalik-ticket),
60 soniya, har ulanishga yangisi); eskicha `?token=` / `?access_token=` — deprecated. Obuna joriy
foydalanuvchiga bog'langan — bir operatorga mo'ljallangan taklif boshqasiga bormaydi;
operatorning har bir ochiq tabiga keladi. Vaqt chegarasi yo'q; har ~15 soniyada `: ping`
izohi keladi. API kalit bilan — `403 NO_USER_SESSION`.

| `event:` | Kimga | Payload | Qachon |
|---|---|---|---|
| `connected` | shu ulanish | raqam — obuna ID si | Ulanish ochilganda |
| `CALL_OFFER` | shu operator | `CallOffer` | Navbat qo'ng'irog'i shu operatorga jiringlay boshladi (softphone ham, `forwardNumber` mobili ham) |
| `CALL_CONNECTED` | shu operator | `CallOffer` | Operator ko'tardi va mijoz bilan ulandi |
| `CALL_ENDED` | shu operator | `CallOffer` | Taklif javobsiz tugadi, qaytarib olindi (boshqasi ko'tardi, mijoz go'shakni qo'ydi) yoki ulangan suhbat tugadi |
| `STATUS_CHANGED` | shu operator | `OperatorPresence` | Har qanday status o'zgarishi — operator o'zi qo'ygan ham, tizim qo'ygani ham (`RINGING`, `ON_CALL`, `WRAP_UP`, RONA `BREAK`) |
| `MISSED_CALL` | butun kompaniya | `MissedCall` | Javobsiz qo'ng'iroq yozildi — yangi yozuv ham, takroriy qo'ng'iroq ham ([missed-calls.md](missed-calls.md)) |
| `VOICEMAIL` | butun kompaniya | `VoicemailMessage` | Mijoz ovozli xabar qoldirdi |

`QUEUE_STATS` bu oqimga kelmaydi — u faqat wallboard'ga
([call-queues.md](call-queues.md#get-apicall-queueslivestream--wallboard-sse-oqimi)).
Missed-call'dan qayta qo'ng'iroq (`call-back`) paytida `CALL_OFFER` **kelmaydi** —
softphone oddiy kiruvchi qo'ng'iroq sifatida `"Qayta qo'ng'iroq" <raqam>` caller ID bilan
jiringlaydi.

**`CallOffer`** — screen pop: mijozdan qaytadan so'ramaslik uchun hamma narsa:

```json
{
  "channelId": "1757664000.118",
  "callAttemptId": 48213,
  "callerNumber": "998901234567",
  "clientName": "Aziz Karimov",
  "queueName": "Sotuv bo'limi",
  "waitSeconds": 42,
  "language": "uz-UZ",
  "vip": true,
  "summary": "Kiruvchi qo'ng'iroq, navbat: Sotuv bo'limi. VIP mijoz. Mijoz: Aziz Karimov. Tanlovi: sotuv.",
  "transcript": "AGENT: Assalomu alaykum...\nCLIENT: Operator bilan gaplashmoqchiman\n..."
}
```

| Maydon | Izoh |
|---|---|
| `channelId` | Mijoz kanali — hold, transfer va monitor shu bilan ishlaydi |
| `callAttemptId` | Qo'ng'iroq yozuvi; transkript va CRM kartasi shu ID bilan olinadi |
| `clientName` | CRM bilgan ism; bilmasa `null` |
| `queueName` | Navbat nomi; navbatsiz (ring group, extension) qo'ng'iroqda `null` |
| `waitSeconds` | Hozirgacha qancha kutgan |
| `summary` | Operator ko'targanda eshitadigan whisper matnining o'zi |
| `transcript` | Avval AI agent gaplashgan bo'lsa — o'sha suhbat; bo'lmasa `null` |

**`OperatorPresence`** — `GET /me/status` bilan bir xil shakl.

**`MissedCall`** — [missed-calls.md](missed-calls.md#-yozuv-shakli-missedcall)dagi shakl.

**`VoicemailMessage`**:

```json
{
  "id": 31,
  "companyId": 1,
  "boxId": 7,
  "callAttemptId": 48220,
  "callerPhone": "998935556677",
  "storedFileId": 905,
  "durationSec": 18,
  "transcript": "Assalomu alaykum, buyurtmam haqida qayta qo'ng'iroq qiling.",
  "status": "NEW",
  "createdAt": "2026-09-12T19:05:11Z"
}
```

`storedFileId` — audio (saqlash ishlamagan bo'lsa `null`); `transcript` — quti
transkripsiya qilmasa `null`. Audio: `GET /api/voicemails/{id}/audio` (**MISSED_CALL_READ**).

---

## 🧭 Operator paneli oqimi (frontend uchun)

1. Admin: `PUT /api/operators/{userId}/sip-account` (**USER_EDIT**) va operatorni
   navbatga a'zo qiladi (`members`, [call-queues.md](call-queues.md)).
2. Operator panelni ochadi: `GET /me/sip-credentials` → JsSIP/SIP.js bilan `wsUrl` ga
   REGISTER; `GET /me/events` ga obuna bo'ladi.
3. `PUT /me/status` → `AVAILABLE`. Endi navbat unga qo'ng'iroq taklif qila oladi.
4. `CALL_OFFER` keladi, bir vaqtda softphone'ga INVITE keladi. Operator softphone'da
   javob beradi → whisper → `CALL_CONNECTED`.
5. Suhbat tugaydi → `CALL_ENDED`, `STATUS_CHANGED` (`WRAP_UP`), keyin yana
   `STATUS_CHANGED` (`AVAILABLE`).
