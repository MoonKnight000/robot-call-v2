# Javobsiz qo'ng'iroqlar (Missed calls) API

`uz.murodjon.robotcallv2.missedcall` · huquq: **MISSED_CALL_READ** / **MISSED_CALL_EDIT**

Operatorlarning ish ro'yxati — kompaniya hali qayta qo'ng'iroq qilishi kerak bo'lgan
mijozlar: ish vaqtidan tashqari qo'ng'iroq qilganlar, navbatda kutib go'shakni
qo'yganlar, bo'sh operator topmaganlar, qayta qo'ng'iroq so'raganlar va ovozli xabar
qoldirganlar. Navbatlar — [call-queues.md](call-queues.md), operatorlar —
[operators.md](operators.md).

Umumiy javob shakli, xatolar va pagination konventsiyasi uchun
[README.md](README.md)ga qarang.

---

## 📥 Yozuv qanday paydo bo'ladi

Kiruvchi qo'ng'iroq (call flow yoki operator navbati orqali o'tgan) **hech bir operator
bilan gaplashmay** tugasa va quyidagi sabablardan biri qayd etilgan bo'lsa, ro'yxatga
yoziladi. Qo'ng'iroq keyinroq AI agentda hal bo'lgan bo'lsa (sabab tozalanadi) —
yozilmaydi.

### Sabablar (`MissedCallReason`)

| Sabab | Qachon |
|---|---|
| `AFTER_HOURS` | Ish vaqtidan tashqari qo'ng'iroq qildi va hech kim javob bermadi: call flow'dagi `SCHEDULE` tuguni yopiq, yoki [kiruvchi marshrut](inbound-routes.md) ish vaqtidan tashqarida xabar aytib qo'ng'iroqni tugatdi |
| `ABANDONED` | Navbatda kutayotganda go'shakni qo'ydi |
| `NO_OPERATOR` | Navbat overflow bo'ldi: `maxWaitSec` o'tdi, navbat to'la, hech kim tizimda emas yoki navbat o'chirilgan |
| `CALLBACK_REQUESTED` | Kutish o'rniga "1 ni bosing — qayta qo'ng'iroq qilamiz" ni tanladi va navbatda `callbackCampaignId` yo'q (bor bo'lsa, mijoz kampaniyaga tushadi, bu ro'yxatga emas) |
| `VOICEMAIL` | Ovozli xabar qoldirdi — mijozning o'z so'zlari bilan qayta qo'ng'iroq so'rovi |

### Bitta raqam — bitta ochiq yozuv

Telefon raqami faqat raqamlarga keltiriladi (`+998 90 123-45-67` va `998901234567` — bitta
mijoz; 3 ta raqamdan qisqasi yozilmaydi). Shu raqamning **ochiq** (`NEW` yoki
`IN_PROGRESS`) yozuvi bo'lsa, yangi yozuv ochilmaydi — mavjudi yangilanadi:

- `callCount` +1, `lastAt` — hozir;
- `reason` — **oxirgi** sabab;
- `lastCallAttemptId`, `voicemailMessageId`, `queueId`, `inboundRouteId` — yangi qiymat
  bo'lsa almashtiriladi.

Ish vaqtidan keyin uch marta qo'ng'iroq qilgan mijoz ro'yxatda `callCount: 3` bilan
**bitta** qator. Bazada `(company_id, phone)` bo'yicha ochiq yozuvlar uchun unique indeks bor.

### O'zi yopiladi

| Hodisa | Natija |
|---|---|
| Shu raqam yana qo'ng'iroq qildi va navbatda operator ko'tardi | `RESOLVED`, `resolvedByUserId` = o'sha operator |
| Operatorning qayta qo'ng'irog'ida (qo'lda yoki avtomatik) mijoz ko'tardi | `CALLED_BACK`, `resolvedByUserId` = operator |

### Xabar berish

- Har bir yozish (yangi ham, takroriy ham) — SSE `MISSED_CALL` hodisasi kompaniyaning
  barcha operator panellari va wallboard'lariga, payload — `MissedCall`
  ([operators.md](operators.md#get-apioperatorsmeevents--operator-paneli-sse-oqimi)).
- **Faqat yangi** yozuv ochilganda — `MISSED_CALL` in-app bildirishnomasi
  ([notifications.md](notifications.md)): `"O'tkazib yuborilgan qo'ng'iroq"`,
  `"998901234567 — ish vaqtidan tashqari"`.

---

## 🔖 Statuslar (`MissedCallStatus`)

| Status | Ochiqmi | Ma'nosi |
|---|---|---|
| `NEW` | ✅ | Hali hech kim shug'ullanmagan (yoki qayta qo'ng'iroq muvaffaqiyatsiz tugab, qaytib keldi) |
| `IN_PROGRESS` | ✅ | Hozir kimdir qayta qo'ng'iroq qilyapti |
| `CALLED_BACK` | ❌ | Qayta qo'ng'iroq qilindi va mijoz ko'tardi |
| `RESOLVED` | ❌ | Qayta qo'ng'iroqsiz yopildi (mijoz yana qo'ng'iroq qilib xizmat oldi yoki kerak bo'lmadi) |

---

## 🧾 Yozuv shakli (`MissedCall`)

```json
{
  "id": 57,
  "companyId": 1,
  "phone": "998901234567",
  "reason": "ABANDONED",
  "status": "NEW",
  "callCount": 3,
  "firstAt": "2026-09-12T18:40:00Z",
  "lastAt": "2026-09-12T19:02:15Z",
  "queueId": 4,
  "inboundRouteId": 3,
  "lastCallAttemptId": 48219,
  "voicemailMessageId": null,
  "assignedUserId": null,
  "callbackAttempts": 0,
  "lastCallbackAt": null,
  "resolvedAt": null,
  "resolvedByUserId": null,
  "note": null
}
```

| Maydon | Turi | Izoh |
|---|---|---|
| `phone` | string | Faqat raqamlar |
| `reason` | enum | Oxirgi sabab |
| `callCount` | integer | Shu ochiq yozuv davomida necha marta qo'ng'iroq qilgan |
| `firstAt` / `lastAt` | Instant | Birinchi va oxirgi javobsiz qo'ng'iroq |
| `queueId` | long | Qaysi navbatda qolgan ([call-queues.md](call-queues.md)); navbat o'chirilsa `null` |
| `inboundRouteId` | long | Qaysi DID marshrutiga kelgan ([inbound-routes.md](inbound-routes.md)) |
| `lastCallAttemptId` | long | Oxirgi javobsiz qo'ng'iroq yozuvi ([calls.md](calls.md)) |
| `voicemailMessageId` | long | Qoldirilgan ovozli xabar; audio — `GET /api/voicemails/{id}/audio` |
| `assignedUserId` | long | Kim qayta qo'ng'iroq qiladi |
| `callbackAttempts` | integer | Qayta qo'ng'iroq urinishlari — avtomatik **va** qo'lda (`call-back`) |
| `lastCallbackAt` | Instant | Oxirgi urinish vaqti |
| `resolvedAt` / `resolvedByUserId` | Instant / long | Yopilgan vaqt va kim yopgan; ochiq yozuvda `null` |
| `note` | string | Operator izohi |

---

## `POST /api/missed-calls/list` — ro'yxat

Huquq: **MISSED_CALL_READ**

Body — `MissedCallFilter`:

```json
{
  "page": 0,
  "size": 20,
  "orders": { "LAST_AT": "DESC" },
  "status": "NEW",
  "reason": null,
  "assignedUserId": null
}
```

| Maydon | Izoh |
|---|---|
| `status` | Faqat shu status; `null` — hammasi (yopilganlari ham) |
| `reason` | Faqat shu sabab; `null` — hammasi |
| `assignedUserId` | Faqat shu foydalanuvchiga biriktirilganlar ("mening ro'yxatim"); `null` — hammasi |
| `orders` | Ustunlar: `ID`, `LAST_AT`, `FIRST_AT`, `CALL_COUNT`. Standart: `LAST_AT DESC` |

> ⚠️ Raqam bo'yicha qidiruv (`search`) **yo'q**.

Javob — `PageableData<MissedCall>`.

---

## `GET /api/missed-calls/{id}` — bitta yozuv

Huquq: **MISSED_CALL_READ**

Javob — `MissedCall`. Topilmasa yoki boshqa kompaniyaniki — `404 MISSED_CALL_NOT_FOUND`.

---

## `PUT /api/missed-calls/{id}` — status va izoh

Huquq: **MISSED_CALL_EDIT**

**Request body** (`UpdateMissedCallRequest`):

```json
{ "status": "RESOLVED", "note": "Mijoz WhatsApp orqali yozdi, masala hal" }
```

| Maydon | Turi | Majburiymi | Izoh |
|---|---|---|---|
| `status` | enum | ✅ | Istalgan `MissedCallStatus` |
| `note` | string (≤2000) | ❌ | Izoh **almashtiriladi** — `null` yuborilsa mavjud izoh o'chadi |

- Yopuvchi status (`CALLED_BACK`, `RESOLVED`) — `resolvedAt` = hozir, `resolvedByUserId` =
  joriy foydalanuvchi (API kalit bilan — `null`).
- Ochiq status (`NEW`, `IN_PROGRESS`) — `resolvedAt`/`resolvedByUserId` tozalanadi.

Javob — yangilangan `MissedCall`. `404 MISSED_CALL_NOT_FOUND`; `status` yo'q — `400
VALIDATION_FAILED`. Audit: `MISSED_CALL_UPDATE`.

> ⚠️ Yopilgan yozuvni qayta ochish (`NEW`/`IN_PROGRESS`) shu raqamning boshqa ochiq yozuvi
> bor paytda `409 MISSED_CALL_ALREADY_OPEN` qaytaradi: bitta raqamga bitta ochiq yozuv.
> Xabardagi ikkinchi qiymat — o'sha ochiq yozuvning `id`si; frontend foydalanuvchini unga
> yo'naltirishi mumkin.

---

## `PUT /api/missed-calls/{id}/assign` — biriktirish

Huquq: **MISSED_CALL_EDIT**

**Request body** (`AssignMissedCallRequest`):

```json
{ "userId": 12 }
```

`userId: null` — biriktirishni olib tashlaydi. Status o'zgarmaydi.

Javob — yangilangan `MissedCall`.

| Holat | Javob |
|---|---|
| Yozuv topilmadi | `404 MISSED_CALL_NOT_FOUND` |
| Foydalanuvchi topilmadi / boshqa kompaniyaniki | `404 USER_NOT_FOUND` |

Audit: `MISSED_CALL_ASSIGN`.

---

## `POST /api/missed-calls/{id}/call-back` — qayta qo'ng'iroq qilish

Huquq: **MISSED_CALL_EDIT** · body yo'q

**Avval operator, keyin mijoz** — hech kim yo'q operatorni kutib liniyada qolib ketmaydi:

1. **Joriy foydalanuvchining** softphone'i 30 soniya jiringlaydi (caller ID:
   `"Qayta qo'ng'iroq" <998901234567>`). `forwardNumber` mobiliga bormaydi.
2. Operator ko'tarsa, kim bilan ulanayotganini eshitadi, keyin mijozga kompaniya trunk'i
   orqali qo'ng'iroq qilinadi (45 soniya). Bu yangi qo'ng'iroq yozuvi sifatida
   qayd etiladi va yoziladi.
3. Mijoz ko'tarsa — ikkalasi ulanadi, yozuv `CALLED_BACK` bo'ladi.
4. Operator ko'tarmasa yoki mijoz javob bermasa — yozuv yana `NEW` ga qaytadi (mijoz
   javob bermasa operator buni ovozli xabarda eshitadi).

So'rovning o'zida darhol: yozuv joriy foydalanuvchiga biriktiriladi, `IN_PROGRESS`
qilinadi, `callbackAttempts` +1, `lastCallbackAt` — hozir. Operator statusi
`AVAILABLE` bo'lishi shart emas (`BREAK`/`OFFLINE` da ham ishlaydi), lekin boshqa
qo'ng'iroqda bo'lmasligi kerak. Status tekshirilmaydi — yopilgan yozuvdan ham qo'ng'iroq
qilish mumkin.

**Javob** (`MissedCallCallBackResponse`):

```json
{
  "data": {
    "missedCallId": 57,
    "phone": "998901234567",
    "operatorChannelId": "1757665012.140"
  },
  "message": null,
  "messageCode": null,
  "accept": true,
  "errors": null
}
```

`operatorChannelId` — operatorni jiringlatayotgan Asterisk kanali.

| Holat | Javob |
|---|---|
| Yozuv topilmadi | `404 MISSED_CALL_NOT_FOUND` |
| API kalit bilan (odam emas) | `403 NO_USER_SESSION` |
| Joriy foydalanuvchida SIP akkaunt yo'q | `409 OPERATOR_NOT_REACHABLE` |
| Operator hozir `RINGING` yoki `ON_CALL` | `409 OPERATOR_ON_CALL` |
| Yozuvdagi raqam terib bo'lmaydigan | `400 PHONE_INVALID` |
| Asterisk qo'ng'iroqni boshlay olmadi | `502 ARI_ORIGINATE_FAILED` |

Audit: `MISSED_CALL_CALL_BACK`.

---

## 🤖 Avtomatik qayta qo'ng'iroq

Navbatda `autoCallback: true` bo'lsa ([call-queues.md](call-queues.md)), tizim o'sha
navbatning javobsiz qo'ng'iroqlariga o'zi qayta qo'ng'iroq qiladi — xuddi `call-back`
kabi, avval operator, keyin mijoz:

- Har 30 soniyada (ilova ishga tushgach 1 daqiqadan boshlab) tekshiriladi.
- Nomzodlar: `status = NEW`, `queueId` — `autoCallback` va `enabled` yoqilgan navbat,
  `callbackAttempts < 3`, oxirgi urinishdan kamida 15 daqiqa o'tgan. Eng eski `lastAt`
  birinchi, bir martada ko'pi bilan 20 ta.
- Navbatda hozir **kutayotgan** mijoz bo'lsa, o'tkazib yuboriladi — avval tirik
  qo'ng'iroq qiluvchilar.
- Operator navbat strategiyasi bo'yicha tanlanadi (`AVAILABLE`, smenada, softphone
  onlayn). U avtomatik ravishda `RINGING` qilinadi, shuning uchun navbat unga bir vaqtda
  boshqa qo'ng'iroq bermaydi. Operator bu qo'ng'iroqni ko'tarmasa — navbatdagi kabi miss
  hisoblanadi (RONA).
- Yozuv operatorga biriktiriladi va `IN_PROGRESS` bo'ladi. Ulanmasa — yana `NEW`, 15
  daqiqadan keyin qayta urinadi. 3 ta urinishdan keyin (qo'lda qilingan `call-back` lar
  ham hisobga kiradi) yozuv odamga qoldiriladi.

---

## 🔗 Bog'liq: ovozli xabarlar

`voicemailMessageId` dagi xabarlar alohida endpointlar orqali:
`POST /api/voicemails/list` (**MISSED_CALL_READ**), `PUT /api/voicemails/{id}/status`
(**MISSED_CALL_EDIT**), `GET /api/voicemails/{id}/audio` (**MISSED_CALL_READ**).
