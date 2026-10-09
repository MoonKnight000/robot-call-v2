# Natija kodlari (Wrap-up codes) API

`uz.murodjon.robotcallv2.wrapup` · huquq: kodlarni o'qish — **OPERATOR_READ**, sozlash —
**CALL_QUEUE_EDIT**, qo'ng'iroqni yopish — **OPERATOR_EDIT**

Operator har bir qo'ng'iroqdan keyin uning natijasini tanlaydi: "Sotildi", "Qayta
qo'ng'iroq", "Noto'g'ri raqam". Ro'yxat **kompaniya bo'yicha bitta** va barcha navbat hamda
kampaniyalarga umumiy.

Har bir kodning `outcome` maydoni bor — u operator kampaniyasidagi mijoz (target) bilan
nima bo'lishini aytadi:

| `outcome` | Ma'nosi |
|---|---|
| `DONE` | Qo'ng'iroq maqsadiga yetdi, mijoz bilan ish tugadi |
| `RETRY` | Yana qo'ng'iroq qilish kerak: operator ko'rsatgan `callbackAt` vaqtida, u bo'lmasa kampaniyaning retry qoidalari bo'yicha |
| `EXHAUSTED` | Boshqa qo'ng'iroq qilinmaydi (noto'g'ri raqam, qat'iy rad) |

> Qo'ng'iroq operator kampaniyasining kartasi uchun qilingan bo'lsa ([preview-dialing.md](preview-dialing.md)),
> natija kartani ham yopadi: `DONE` → `DONE`, `EXHAUSTED` → `EXHAUSTED`, `RETRY` → `callbackAt`
> vaqtida qayta navbatga (dial oynasiga suriladi); `callbackAt` siz `RETRY` — kampaniyaning retry
> qoidasi bo'yicha, lekin urinishlar `maxAttempts` ga yetgan bo'lsa `EXHAUSTED`. Boshqa qo'ng'iroqlarda natija
> faqat qo'ng'iroqqa yoziladi va operatorning `WRAP_UP` holatini yopadi.

Umumiy javob shakli, xatolar va pagination konventsiyasi uchun [README.md](README.md)ga qarang.

---

## `WrapUpCode` (javob)

```json
{
  "id": 5,
  "companyId": 1,
  "name": "Qayta qo'ng'iroq",
  "outcome": "RETRY",
  "requiresCallbackAt": true,
  "enabled": true,
  "createdAt": "2026-10-06T10:00:00Z"
}
```

| Maydon | Izoh |
|---|---|
| `requiresCallbackAt` | `true` bo'lsa, operator bu kodni tanlaganda `callbackAt` ni ko'rsatishi shart |
| `enabled` | O'chirilgan kodni tanlab bo'lmaydi, lekin u bilan yopilgan qo'ng'iroqlarda saqlanib qoladi |

---

## `POST /api/wrap-up-codes` — Kod yaratish · `CALL_QUEUE_EDIT`

Body (`SaveWrapUpCodeRequest`):

| Maydon | Turi | Majburiymi | Izoh |
|---|---|---|---|
| `name` | `string` (≤100) | ✅ | Operator ko'radigan nom |
| `outcome` | `WrapUpOutcome` | ✅ | `DONE`, `RETRY` yoki `EXHAUSTED` |
| `requiresCallbackAt` | `boolean` | ❌ | Standart `false` |
| `enabled` | `boolean` | ❌ | Standart `true` |

Javob — `WrapUpCode`.

## `PUT /api/wrap-up-codes/{id}` — Tahrirlash · `CALL_QUEUE_EDIT`

Body — yaratishdagi bilan bir xil, to'liq almashtiradi. Avval shu kod bilan yopilgan
qo'ng'iroqlar o'zgarmaydi: ularda kodning o'sha paytdagi nomi va `outcome`i saqlangan.
Topilmasa yoki boshqa kompaniyaniki bo'lsa — `404 WRAP_UP_CODE_NOT_FOUND`.

## `DELETE /api/wrap-up-codes/{id}` — O'chirish · `CALL_QUEUE_EDIT`

Avval yopilgan qo'ng'iroqlar nom va natijasini saqlab qoladi (`wrapUpCodeId` `null`
bo'ladi). Vaqtincha yashirish uchun o'chirish o'rniga `enabled: false` qiling.

## `GET /api/wrap-up-codes/{id}` — Bitta kod · `OPERATOR_READ`

## `POST /api/wrap-up-codes/list` — Ro'yxat · `OPERATOR_READ`

Body — `WrapUpCodeFilter`:

```json
{ "page": 0, "size": 50, "orders": { "NAME": "ASC" }, "enabled": true }
```

`enabled`: `true` — operator hozir tanlay oladiganlar, `false` — o'chirilganlar, berilmasa
— hammasi. Saralanadigan ustunlar: `ID`, `NAME`, `OUTCOME`, `CREATED_AT`; standart `NAME ASC`.
Javob — `PageableData<WrapUpCode>`.

---

## `POST /api/calls/{callId}/wrap-up` — Qo'ng'iroqni yopish · `OPERATOR_EDIT`

Qo'ng'iroqni **o'sha qo'ng'iroqni olib borgan operator** yopadi (`call_attempt.operator_user_id`
= joriy foydalanuvchi). Qayta yuborilsa, avvalgi natija almashtiriladi (xato bosilganini
tuzatish). Operator `WRAP_UP` holatida bo'lsa, u darhol `AVAILABLE` ga o'tadi — navbat
keyingi qo'ng'iroqni taklif qila oladi.

Body (`WrapUpCallRequest`):

```json
{
  "wrapUpCodeId": 5,
  "note": "Ertaga tushdan keyin qulay ekan",
  "callbackAt": "2026-10-07T10:00:00Z"
}
```

| Maydon | Turi | Majburiymi | Izoh |
|---|---|---|---|
| `wrapUpCodeId` | `number` | ✅ | Kompaniyaning yoqilgan kodi |
| `note` | `string` (≤2000) | ❌ | Operator izohi |
| `callbackAt` | `string` (ISO-8601) | kod talab qilsa ✅ | Kelajakdagi vaqt bo'lishi shart |

Javob (`CallWrapUp`):

```json
{
  "callId": 42,
  "companyId": 1,
  "wrapUpCodeId": 5,
  "codeName": "Qayta qo'ng'iroq",
  "outcome": "RETRY",
  "operatorUserId": 9,
  "note": "Ertaga tushdan keyin qulay ekan",
  "callbackAt": "2026-10-07T10:00:00Z",
  "createdAt": "2026-10-06T10:05:00Z"
}
```

**Xatolar:**

| Status | `messageCode` | Qachon |
|---|---|---|
| `400` | `WRAP_UP_CALLBACK_AT_REQUIRED` | Kod `callbackAt` talab qiladi, lekin berilmagan |
| `400` | `WRAP_UP_CALLBACK_AT_IN_PAST` | `callbackAt` hozirdan oldin |
| `403` | `WRAP_UP_NOT_OPERATORS_CALL` | Qo'ng'iroqni boshqa operator olib borgan (yoki AI qo'ng'irog'i) |
| `404` | `CALL_NOT_FOUND` | Qo'ng'iroq yo'q yoki boshqa kompaniyaniki |
| `404` | `WRAP_UP_CODE_NOT_FOUND` | Kod yo'q yoki boshqa kompaniyaniki |
| `409` | `WRAP_UP_CODE_DISABLED` | Kod o'chirilgan (`enabled=false`) |
