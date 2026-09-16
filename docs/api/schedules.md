# Ish jadvali va bayramlar API

`uz.murodjon.robotcallv2.schedule` · huquq: **CALL_FLOW_READ** / **CALL_FLOW_EDIT**

Kompaniyaning haftalik ish vaqti (**ish jadvali**, `BusinessSchedule`) va sanaga bog'langan
istisnolar (**bayram / qisqartirilgan kun / ko'chirilgan ish kuni**, `Holiday`). Jadval
bitta joyda yoziladi va hamma joyda qayta ishlatiladi:

- [call flow](call-flows.md)dagi `SCHEDULE` tuguni (`scheduleId`);
- [kiruvchi marshrut](inbound-routes.md)ning `scheduleId` maydoni (call flow'siz marshrut uchun).

Bayram esa **kompaniyaning barcha jadvallariga** birdaniga ta'sir qiladi — har jadvalga
alohida kiritilmaydi.

Umumiy javob shakli, xatolar va pagination konventsiyasi uchun
[README.md](README.md)ga qarang.

---

## ⏱ Vaqt formati

| Tur | Format | Misol |
|---|---|---|
| Vaqt (`startTime`, `endTime`, `openTime`, `closeTime`) | `HH:mm` | `"09:00"`, `"22:30"` |
| Sana (`date`) | `yyyy-MM-dd` | `"2027-03-21"` |
| Lahza (`createdAt`) | ISO-8601 UTC | `"2026-09-01T09:00:00Z"` |
| `nextOpeningAt` | ISO-8601, jadval zonasi offset'i bilan | `"2026-09-14T09:00:00+05:00"` |

> ⚠️ Vaqt maydonlari aynan `HH:mm` (`DateTimeProperties.TIME_PATTERN`) — soniyali
> `"09:00:00"` ko'rinishi emas.

---

## 🧮 Ochiq/yopiq qanday hisoblanadi (`BusinessHoursEvaluator`)

Qoidalar tartib bo'yicha:

1. **Sana bayram bo'lsa — o'sha kunning haftalik slotlari umuman ishlatilmaydi.**
   - `CLOSED` — kun bo'yi yopiq.
   - `SHORT_DAY` / `WORKING_DAY` — faqat bayramning o'z `openTime`–`closeTime` oynasida ochiq
     (oyna `[openTime, closeTime)` — boshlanishi kiradi, tugashi kirmaydi).
2. **Bayram bo'lmasa — hafta kunining slotlari.** Bir kunda bir nechta slot bo'lishi mumkin
   (tushlik tanaffusi = ikki slot).
3. **Tungi slot:** `endTime` `startTime`dan oldin bo'lsa (`22:00`–`06:00`), slot yarim tundan
   o'tadi — dushanba `22:00`–`06:00` seshanba ertalab `06:00` gacha ham ochiq. **Lekin**
   seshanba bayram bo'lsa, seshanbani faqat bayram hal qiladi (dushanbaning tungi davomi
   bekor bo'ladi).
4. **Keyingi ochilish** (`nextOpeningAt`) — hozirdan boshlab **14 kun** oldinga qidiriladi
   (bayramlar va slotlar hisobga olinadi). Topilmasa `null`.

Jadval **qaysi zonada** o'qiladi: jadvalning `timezone`i → bo'sh bo'lsa kompaniyaning
`timezone`i ([companies.md](companies.md)) → u ham bo'lmasa `Asia/Tashkent`.

---

## 🎌 Bayram turlari (`HolidayKind`)

| Qiymat | Ma'nosi | `openTime` / `closeTime` |
|---|---|---|
| `CLOSED` | Kun bo'yi dam olish (Navro'z, Mustaqillik kuni). **Standart** — `kind` berilmasa shu | e'tiborga olinmaydi, `null` saqlanadi |
| `SHORT_DAY` | Bayram arafasidagi qisqartirilgan ish kuni | majburiy, `openTime < closeTime` |
| `WORKING_DAY` | Farmon bilan ish kuniga aylantirilgan dam olish kuni (masalan, ishlanadigan shanba) | majburiy, `openTime < closeTime` |

`SHORT_DAY` va `WORKING_DAY` bir xil hisoblanadi (faqat o'z oynasida ochiq) — farqi faqat
kalendarni o'qigan odam uchun.

---

## Ish jadvali (`/api/schedules`)

### Obyekt shakli (`BusinessSchedule`)

```json
{
  "id": 1,
  "companyId": 1,
  "name": "Ofis ish vaqti",
  "timezone": "Asia/Tashkent",
  "slots": [
    { "dayOfWeek": "MONDAY", "startTime": "09:00", "endTime": "13:00" },
    { "dayOfWeek": "MONDAY", "startTime": "14:00", "endTime": "18:00" },
    { "dayOfWeek": "FRIDAY", "startTime": "22:00", "endTime": "06:00" }
  ],
  "createdAt": "2026-09-01T09:00:00Z"
}
```

| Maydon | Turi | Izoh |
|---|---|---|
| `timezone` | string \| null | IANA zona (`Asia/Tashkent`). `null` — kompaniya zonasi |
| `slots[].dayOfWeek` | enum | `MONDAY`, `TUESDAY`, `WEDNESDAY`, `THURSDAY`, `FRIDAY`, `SATURDAY`, `SUNDAY` |
| `slots[].startTime` / `endTime` | `HH:mm` | `endTime < startTime` — tungi slot (yarim tundan o'tadi) |

Slotda bo'lmagan kun — yopiq kun.

---

### `POST /api/schedules` — yangi jadval

Huquq: **CALL_FLOW_EDIT**

**Request body** (`SaveBusinessScheduleRequest`):

```json
{
  "name": "Ofis ish vaqti",
  "timezone": "Asia/Tashkent",
  "slots": [
    { "dayOfWeek": "MONDAY", "startTime": "09:00", "endTime": "13:00" },
    { "dayOfWeek": "MONDAY", "startTime": "14:00", "endTime": "18:00" },
    { "dayOfWeek": "TUESDAY", "startTime": "09:00", "endTime": "18:00" },
    { "dayOfWeek": "WEDNESDAY", "startTime": "09:00", "endTime": "18:00" },
    { "dayOfWeek": "THURSDAY", "startTime": "09:00", "endTime": "18:00" },
    { "dayOfWeek": "FRIDAY", "startTime": "09:00", "endTime": "18:00" },
    { "dayOfWeek": "SATURDAY", "startTime": "10:00", "endTime": "15:00" }
  ]
}
```

| Maydon | Turi | Majburiymi | Izoh |
|---|---|---|---|
| `name` | string | ✅ (`@NotBlank`) | Jadval nomi |
| `timezone` | string | ❌ | IANA zona; `null`/bo'sh — kompaniya zonasi. Noma'lum zona — `400 BUSINESS_SCHEDULE_TIMEZONE_UNKNOWN` |
| `slots` | array | ✅ (`@NotEmpty`) | Kamida bitta slot (hech qachon ochilmaydigan jadval — xato) |
| `slots[].dayOfWeek` | enum | ✅ | |
| `slots[].startTime` | `HH:mm` | ✅ | |
| `slots[].endTime` | `HH:mm` | ✅ | `startTime` bilan teng bo'lishi mumkin emas |

**Response** — `ResponseData<BusinessSchedule>`:

```json
{
  "data": {
    "id": 1,
    "companyId": 1,
    "name": "Ofis ish vaqti",
    "timezone": "Asia/Tashkent",
    "slots": [ { "dayOfWeek": "MONDAY", "startTime": "09:00", "endTime": "13:00" } ],
    "createdAt": "2026-09-01T09:00:00Z"
  },
  "message": null,
  "messageCode": null,
  "accept": true,
  "errors": null
}
```

**Xatolar:** `VALIDATION_FAILED` (400, `@Valid`), `BUSINESS_SCHEDULE_NAME_REQUIRED` (400 — nom bo'sh),
`BUSINESS_SCHEDULE_TIMEZONE_UNKNOWN` (400 — noma'lum zona), `BUSINESS_SCHEDULE_SLOTS_REQUIRED` (400 — slot yo'q),
`BUSINESS_SCHEDULE_SLOT_INCOMPLETE` (400 — slotda maydon yetishmaydi), `BUSINESS_SCHEDULE_SLOT_EMPTY`
(400 — `startTime == endTime`).

---

### `POST /api/schedules/list` — ro'yxat

Huquq: **CALL_FLOW_READ**

Body — `BusinessScheduleFilter`:

```json
{ "page": 0, "size": 20, "orders": { "NAME": "ASC" } }
```

Saralanadigan ustunlar: `ID`, `NAME`, `CREATED_AT`. Standart: `NAME ASC`.

Javob — `PageableData<BusinessSchedule>`:

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
        "name": "Ofis ish vaqti",
        "timezone": "Asia/Tashkent",
        "slots": [ { "dayOfWeek": "MONDAY", "startTime": "09:00", "endTime": "18:00" } ],
        "createdAt": "2026-09-01T09:00:00Z"
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

### `GET /api/schedules/{id}` — bitta jadval

Huquq: **CALL_FLOW_READ** · Javob — `BusinessSchedule`.

**Xatolar:** `BUSINESS_SCHEDULE_NOT_FOUND` (404).

---

### `PUT /api/schedules/{id}` — tahrirlash

Huquq: **CALL_FLOW_EDIT** · Body — `POST /api/schedules` bilan bir xil
(`SaveBusinessScheduleRequest`). `slots` ro'yxati **to'liq almashtiriladi**. Javob —
yangilangan `BusinessSchedule`.

**Xatolar:** `BUSINESS_SCHEDULE_NOT_FOUND` (404), `POST` dagi `BUSINESS_SCHEDULE_*` validatsiya xatolari (400),
`VALIDATION_FAILED` (400).

---

### `DELETE /api/schedules/{id}` — o'chirish

Huquq: **CALL_FLOW_EDIT** · Javob — `data: null`.

Qator **butunlay o'chiriladi**. Oqibatlari:

- Shu jadvalga ishora qilgan kiruvchi marshrutlarning `scheduleId`si `null` bo'ladi
  (marshrut o'zining `businessHoursStart/End` oynasiga qaytadi, u ham bo'lmasa 24/7).
- Call flow'dagi `SCHEDULE` tuguni o'chirilgan jadvalni ko'rsatib qolsa, qo'ng'iroq paytida
  **doim ochiq** deb hisoblanadi (serverda ogohlantirish loglanadi). Shuning uchun
  jadvalni o'chirishdan oldin uni ishlatayotgan flow'larni yangilang.

**Xatolar:** `BUSINESS_SCHEDULE_NOT_FOUND` (404).

---

### `GET /api/schedules/{id}/status` — hozir ochiqmi?

Huquq: **CALL_FLOW_READ**

"Mijoz aynan hozir qo'ng'iroq qilsa nima bo'ladi" — bayramlar va tungi slotlar bilan
birga hisoblangan hukm.

**Response** (`ScheduleStatusResponse`):

```json
{
  "data": {
    "open": false,
    "holidayName": "Mustaqillik kuni",
    "nextOpeningAt": "2026-09-02T09:00:00+05:00",
    "timezone": "Asia/Tashkent"
  },
  "message": null,
  "messageCode": null,
  "accept": true,
  "errors": null
}
```

| Maydon | Turi | Izoh |
|---|---|---|
| `open` | boolean | Hozir ish vaqtidami |
| `holidayName` | string \| null | Bugungi kunni boshqarayotgan bayram nomi (`SHORT_DAY`/`WORKING_DAY` bo'lsa ham, `open: true` bilan birga kelishi mumkin) |
| `nextOpeningAt` | string \| null | Keyingi ochilish, jadval zonasida. `open: true` bo'lsa yoki 14 kun ichida ochilish topilmasa — `null` |
| `timezone` | string | Jadval qaysi zonada o'qilgani (jadval → kompaniya → `Asia/Tashkent`) |

**Xatolar:** `BUSINESS_SCHEDULE_NOT_FOUND` (404).

---

## Bayramlar (`/api/holidays`)

### Obyekt shakli (`Holiday`)

```json
{
  "id": 7,
  "companyId": 1,
  "date": "2027-03-20",
  "name": "Navro'z arafasi",
  "kind": "SHORT_DAY",
  "openTime": "09:00",
  "closeTime": "14:00",
  "createdAt": "2026-09-01T09:00:00Z"
}
```

Bitta kompaniyada bitta sanaga faqat **bitta** yozuv bo'lishi mumkin.

---

### `POST /api/holidays` — bayram / maxsus kun qo'shish

Huquq: **CALL_FLOW_EDIT**

**Request body** (`SaveHolidayRequest`):

```json
{
  "date": "2027-03-20",
  "name": "Navro'z arafasi",
  "kind": "SHORT_DAY",
  "openTime": "09:00",
  "closeTime": "14:00"
}
```

| Maydon | Turi | Majburiymi | Izoh |
|---|---|---|---|
| `date` | `yyyy-MM-dd` | ✅ (`@NotNull`) | |
| `name` | string | ✅ (`@NotBlank`) | |
| `kind` | enum | ❌ | `CLOSED` (standart), `SHORT_DAY`, `WORKING_DAY` |
| `openTime` | `HH:mm` | `kind != CLOSED` bo'lsa ✅ | `CLOSED` da yuborilsa ham `null` saqlanadi |
| `closeTime` | `HH:mm` | `kind != CLOSED` bo'lsa ✅ | `openTime`dan keyin bo'lishi shart (bayram oynasi yarim tundan o'tmaydi) |

Javob — yaratilgan `Holiday`.

**Xatolar:** `VALIDATION_FAILED` (400), `HOLIDAY_DATE_REQUIRED` / `HOLIDAY_NAME_REQUIRED` (400 — sana/nom yo'q),
`HOLIDAY_HOURS_INVALID` (400 — `SHORT_DAY`/`WORKING_DAY` uchun `openTime < closeTime` emas), `HOLIDAY_DATE_EXISTS` (409 —
shu sanada allaqachon yozuv bor).

---

### `POST /api/holidays/list` — ro'yxat

Huquq: **CALL_FLOW_READ**

Body — `HolidayFilter`:

```json
{ "page": 0, "size": 50, "orders": { "DATE": "ASC" }, "year": 2027 }
```

| Maydon | Turi | Izoh |
|---|---|---|
| `year` | integer \| null | Faqat shu yil bayramlari (`2000`–`2100`); `null` — hammasi |

Saralanadigan ustunlar: `ID`, `DATE`, `NAME`, `CREATED_AT`. Standart: `DATE ASC`.

Javob — `PageableData<Holiday>`.

---

### `PUT /api/holidays/{id}` — tahrirlash

Huquq: **CALL_FLOW_EDIT** · Body — `POST /api/holidays` bilan bir xil. Javob — yangilangan
`Holiday`.

**Xatolar:** `HOLIDAY_NOT_FOUND` (404), `POST` dagi `HOLIDAY_*` validatsiya xatolari (400), `HOLIDAY_DATE_EXISTS` (409 —
yangi sana boshqa yozuvda band), `VALIDATION_FAILED` (400).

---

### `DELETE /api/holidays/{id}` — o'chirish

Huquq: **CALL_FLOW_EDIT** · Javob — `data: null`. Qator butunlay o'chiriladi.

**Xatolar:** `HOLIDAY_NOT_FOUND` (404).

> `GET /api/holidays/{id}` endpointi **yo'q** — bitta bayram ro'yxatdan olinadi.

---

### `POST /api/holidays/fixed?year={year}` — O'zbekistonning belgilangan bayramlarini qo'shish

Huquq: **CALL_FLOW_EDIT** · Body yo'q, `year` — majburiy query parametr (`2000`–`2100`).

Berilgan yilga har yili bir xil sanaga tushadigan davlat bayramlarini `CLOSED` turida
qo'shadi. **Shu sanada allaqachon yozuv bo'lsa (masalan, qo'lda kiritilgan `SHORT_DAY`),
u o'zgartirilmaydi va o'tkazib yuboriladi** — shuning uchun endpointni qayta chaqirish
xavfsiz.

| Sana | Nomi |
|---|---|
| 1-yanvar | Yangi yil |
| 8-mart | Xalqaro xotin-qizlar kuni |
| 21-mart | Navro'z bayrami |
| 9-may | Xotira va qadrlash kuni |
| 1-sentabr | Mustaqillik kuni |
| 1-oktabr | O'qituvchi va murabbiylar kuni |
| 8-dekabr | Konstitutsiya kuni |

Ro'za hayit va Qurbon hayit har yili oy kalendari bo'yicha ko'chadi va farmon bilan
e'lon qilinadi — ular (hamda farmon bilan ko'chirilgan ish kunlari) `POST /api/holidays`
orqali qo'lda kiritiladi.

**Response** — `ResponseData<List<Holiday>>`, faqat **shu chaqiruvda yangi qo'shilganlar**
(hammasi mavjud bo'lsa — bo'sh ro'yxat):

```json
{
  "data": [
    {
      "id": 21,
      "companyId": 1,
      "date": "2027-01-01",
      "name": "Yangi yil",
      "kind": "CLOSED",
      "openTime": null,
      "closeTime": null,
      "createdAt": "2026-09-12T10:00:00Z"
    }
  ],
  "message": null,
  "messageCode": null,
  "accept": true,
  "errors": null
}
```

**Xatolar:** `HOLIDAY_YEAR_OUT_OF_RANGE` (400 — `year` 2000–2100 oralig'ida emas), `year` berilmasa
yoki son bo'lmasa — `400` (yetishmagan/noto'g'ri tipdagi parametr).
