# To'lov va'dalari (`/api/payment-promises`)

Qo'ng'iroq natijasida sanasi bilan to'lov va'dasi (`outcome.promisedDate`) yozilsa, tizim
avtomatik ravishda **va'da** ochadi va uni bajarilguncha yoki buzilguncha kuzatadi:

1. **Eslatma SMS.** Sanadan `reminderDaysBefore` kun oldin, kompaniya vaqti bilan
   09:00–20:00 oralig'ida, bir marta yuboriladi.
2. **Bajarildi (`KEPT`).** Kompaniyaning tizimi [`/api/conversions`](conversions.md) ga shu
   raqam bo'yicha hodisa yuborsa, va'da avtomatik `KEPT` bo'ladi:
   - qaysi goal hisobga olinishini `paymentGoalKey` belgilaydi, `null` bo'lsa — istalgan goal;
   - hodisa qo'ng'iroqdan keyin (yoki qo'ng'iroq paytida) bo'lishi kerak;
   - hodisa biror qo'ng'iroqqa atribut qilinmagan (`rejected`) bo'lsa ham, to'lov bo'lgan deb hisoblanadi.
   
   Integratsiyasi yo'q kompaniya va'dani qo'lda belgilaydi (§4).
3. **Buzildi (`BROKEN`).** Sana va undan keyingi `graceDays` kun o'tib to'lov kelmasa, va'da
   buziladi. `recallOnBroken: true` bo'lsa, kampaniya nishoni darhol qayta qo'ng'iroq navbatiga
   qo'yiladi. Bunda kampaniyaning terish oynasi va kompaniyaning chastota limiti
   (`maxAttemptsPerPhonePerWeek`, [companies.md](companies.md)) amal qilishda davom etadi.
   Kampaniya allaqachon tugagan bo'lsa, nishon navbatga tushadi, lekin kampaniya qayta
   ishga tushmaguncha terilmaydi. Kiruvchi yoki qo'lda qilingan qo'ng'iroqning nishoni yo'q —
   bunday va'da faqat `BROKEN` deb belgilanadi.

Scheduler har 15 daqiqada ishlaydi (`voice-agent.payment-promises.check-ms`, default `900000`).

Bitta raqamdan **yangi** va'da kelsa, shu raqamning oldingi ochiq va'dalari `CANCELLED` bo'ladi —
mijozga bitta sana eslatiladi.

Eslatma SMS ham, qayta qo'ng'iroq ham **sukut bo'yicha o'chiq** — kompaniya ularni §5 dagi
sozlamalarda yoqadi. SMS yuborish platformada o'chiq bo'lsa (`sms.enabled: false`), eslatma
yuborilmaydi.

| Metod | Yo'l | Huquq | Vazifasi |
|---|---|---|---|
| `POST` | `/api/payment-promises/filter` | `CALL_READ` | Ro'yxat (sahifalangan) |
| `GET` | `/api/payment-promises/{id}` | `CALL_READ` | Bitta va'da |
| `GET` | `/api/payment-promises/stats?from=&to=` | `CALL_READ` | Natijalar bo'yicha sonlar va kept-PTP foizi |
| `POST` | `/api/payment-promises/{id}/status` | `CALL_EDIT` | Ochiq va'dani qo'lda belgilash |
| `GET` | `/api/payment-promises/settings` | `CALL_READ` | Kompaniya sozlamalari |
| `PUT` | `/api/payment-promises/settings` | `CAMPAIGN_EDIT` | Sozlamalarni to'liq almashtirish |

---

## 1. `PaymentPromise`

```json
{
  "id": 1,
  "companyId": 1,
  "callAttemptId": 4521,
  "targetId": 3310,
  "phone": "+998901234567",
  "promisedDate": "2026-10-15",
  "promisedAmount": 500000.00,
  "status": "OPEN",
  "reminderSentAt": "2026-10-14T05:00:12Z",
  "resolvedAt": null,
  "createdAt": "2026-10-09T10:04:51Z"
}
```

| Maydon | Izoh |
|---|---|
| `targetId` | Qayta qo'ng'iroq qilinadigan kampaniya nishoni; kiruvchi yoki qo'lda qilingan qo'ng'iroqda `null` |
| `promisedAmount` | Mijoz summa aytmagan bo'lsa `null` |
| `status` | `OPEN` · `KEPT` · `BROKEN` · `CANCELLED`. Faqat `OPEN` holat o'zgaradi, qolgan uchtasi yakuniy |
| `reminderSentAt` | Eslatma SMS yuborilgan vaqt; `null` — yuborilmagan |
| `resolvedAt` | `OPEN` dan chiqqan vaqt |

---

## 2. Ro'yxat

```http
POST /api/payment-promises/filter
```

```json
{ "page": 0, "size": 20, "status": "OPEN", "phone": "90 123 45 67", "promisedFrom": "2026-10-01", "promisedTo": "2026-10-31" }
```

Barcha filtrlar ixtiyoriy. `phone` istalgan formatda yuboriladi va normallashtiriladi.
`promisedFrom`/`promisedTo` ikkala chegarasi ham kiradi. Saralash ustunlari
(`PaymentPromiseTableField`): `PROMISED_DATE` (default, `ASC`) va `CREATED_AT`.

---

## 3. Statistika (kept-PTP)

```http
GET /api/payment-promises/stats?from=2026-10-01&to=2026-10-31
```

Sanasi (`promisedDate`) shu oraliqqa tushgan va'dalar hisoblanadi. Oraliq ko'pi bilan 366 kun.

```json
{
  "success": true,
  "data": {
    "total": 120,
    "open": 30,
    "kept": 54,
    "broken": 26,
    "cancelled": 10,
    "keptRate": 67.5,
    "promisedAmount": 58000000.00,
    "keptAmount": 27500000.00
  }
}
```

- `keptRate` = `kept / (kept + broken) × 100`. Ochiq va bekor qilingan va'dalar bu foizga
  kirmaydi. Hech bir va'da yakunlanmagan bo'lsa — `null`.
- `promisedAmount` va `keptAmount` — aytilgan summalar yig'indisi. Summasiz va'dalar yig'indiga
  hech narsa qo'shmaydi. Bu summalar kelgan to'lov emas, mijoz va'da qilgan summa.

---

## 4. Qo'lda belgilash

```http
POST /api/payment-promises/{id}/status
```

```json
{ "status": "KEPT" }
```

Mumkin bo'lgan qiymatlar: `KEPT`, `BROKEN`, `CANCELLED`. Faqat `OPEN` va'dani belgilash mumkin.
Qo'lda `BROKEN` qilish muddat o'tgandagi bilan bir xil ishlaydi: `recallOnBroken` yoqilgan
bo'lsa, nishon qayta navbatga qo'yiladi. Javob — yangilangan `PaymentPromise`.

---

## 5. Sozlamalar

```http
GET /api/payment-promises/settings
PUT /api/payment-promises/settings
```

```json
{
  "reminderEnabled": true,
  "reminderDaysBefore": 1,
  "reminderTemplate": "Hurmatli mijoz, {date} gacha {amount} so'm to'lashga va'da bergansiz. Rahmat!",
  "graceDays": 2,
  "recallOnBroken": true,
  "paymentGoalKey": "payment"
}
```

| Maydon | Turi | Majburiy | Cheklov / default |
|---|---|---|---|
| `reminderEnabled` | boolean | ha | default `false` |
| `reminderDaysBefore` | int | ha | 0–7; `0` — sananing o'zida. Default `1` |
| `reminderTemplate` | string | yo'q | 1–300 belgi. `{date}` → `15.10.2026`, `{amount}` → `500000` (summa yo'q bo'lsa — bo'sh joy). `null` — standart matn |
| `graceDays` | int | ha | 0–14, default `1` |
| `recallOnBroken` | boolean | ha | default `false` |
| `paymentGoalKey` | string | yo'q | Conversion goal kaliti; `null` yoki bo'sh — istalgan goal |

Sozlamani hech qachon saqlamagan kompaniya uchun `GET` default qiymatlarni qaytaradi.
Standart eslatma matni: `"Hurmatli mijoz, {date} gacha to'lov qilishga va'da berganingizni eslatib o'tamiz. Rahmat!"`

---

## 6. Xatolar

| Status | `messageCode` | Qachon |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Majburiy maydon yo'q, `from`/`to` yo'q yoki noto'g'ri formatda |
| 400 | `PAYMENT_PROMISE_SETTINGS_INVALID` | Sozlama oraliqdan tashqarida — har bir muammo `errors` ro'yxatida |
| 400 | `PAYMENT_PROMISE_STATUS_INVALID` | Qo'lda `OPEN` deb belgilashga urinish |
| 400 | `PAYMENT_PROMISE_RANGE_INVALID` | `from` > `to` yoki oraliq 366 kundan uzun |
| 404 | `PAYMENT_PROMISE_NOT_FOUND` | Va'da yo'q yoki boshqa kompaniyaniki |
| 409 | `PAYMENT_PROMISE_NOT_OPEN` | Va'da allaqachon `KEPT` / `BROKEN` / `CANCELLED` |
