# Billing & Balans API

`uz.murodjon.robotcallv2.billing` · huquq: **BILLING_READ** / **BILLING_EDIT** (topup)

Kompaniyaning joriy tarif rejasi, balansi, qo'ng'iroq daqiqalari va AI token sarf-xarajatlari metrikalari, oylik hisobotlar (invoices), hisob-fakturani PDF formatida yuklab olish hamda hisobni to'ldirish (Payme, Click, Bank Transfer) endpointlari.

Umumiy javob formati va xatolar uchun [README.md](README.md)ga qarang.

---

## 1. `GET /api/billing/overview` — Hisob holati va limitlar

Kompaniyaning umumiy balans holati, faol tarif rejasi, keyingi hisob-kitob sanasi va asosiy resurslar (daqiqalar, AI tokenlar, TTS belgilar, parallel chiquvchi kanallar) bo'yicha joriy sarf va limitlarni qaytaradi.

> ℹ️ **`used` qiymatlari haqiqiy.** Ular `call_billing` jadvalidagi hisoblangan
> qo'ng'iroqlardan (joriy oy, Toshkent vaqti bo'yicha) yig'iladi. `limit` esa tarif
> rejasi qatoridan (`billing_usage`) olinadi. Ilgari ikkalasi ham bitta qatordan kelardi
> va har bir kompaniyaga bir xil o'ylab topilgan raqamlar ko'rsatilardi.
>
> Kompaniyada hali `company_billing` qatori bo'lmasa, birinchi chaqiruvda **bo'sh qator
> yaratiladi**: balans 0, auto-recharge o'chiq. `billing_usage` qatori esa tarif
> limitlarini beradi (5000 daqiqa, 2 000 000 token, 1 000 000 TTS belgi, 30 kanal),
> sarf ustunlari 0.
>
> `concurrentChannels.used` hozircha to'ldirilmaydi (0 qaytadi) — parallel kanallar
> soni faqat ish vaqtida ma'lum, u `live` bo'limida ko'rinadi.

### Request
```http
GET /api/billing/overview
Authorization: Bearer <accessToken>
```

### Response (`BillingOverviewResponse`)
```json
{
  "accept": true,
  "data": {
    "planName": "Enterprise AI Pro",
    "planCode": "ENTERPRISE_PRO",
    "balanceUzs": 4500000,
    "nextBillingDate": "2026-10-01T00:00:00Z",
    "autoRecharge": true,
    "metrics": {
      "minutes": {
        "used": 12450,
        "limit": 50000,
        "unit": "min",
        "overagePricePerUnit": 120.0
      },
      "aiTokens": {
        "used": 4500000,
        "limit": 20000000,
        "unit": "tokens",
        "overagePricePerUnit": 0.05
      },
      "ttsCharacters": {
        "used": 850000,
        "limit": 5000000,
        "unit": "chars",
        "overagePricePerUnit": 0.02
      },
      "concurrentChannels": {
        "used": 18,
        "limit": 50,
        "unit": "channels",
        "overagePricePerUnit": null
      }
    }
  },
  "message": null,
  "messageCode": null,
  "errors": null
}
```

| Maydon | Turi | Izoh |
|---|---|---|
| `planName` | `string` | Tarif rejasi nomi |
| `planCode` | `string` | Tarif rejasi kodi (`STARTER`, `BUSINESS`, `ENTERPRISE_PRO`) |
| `balanceUzs` | `number` | Joriy hisob balansi (so'mda) |
| `nextBillingDate` | `string` (ISO-8601) | Navbatdagi obuna yechilish sanasi |
| `autoRecharge` | `boolean` | Balans ma'lum chegaraga tushganda avtomatik to'ldirish yoqilganmi |
| `metrics.minutes` | `BillingMetricItemDto` | Qo'ng'iroq daqiqalari sarfi va limiti |
| `metrics.aiTokens` | `BillingMetricItemDto` | LLM tokenlari sarfi va limiti |
| `metrics.ttsCharacters` | `BillingMetricItemDto` | TTS matn belgilari sarfi va limiti |
| `metrics.concurrentChannels` | `BillingMetricItemDto` | Bir vaqtda faol parallel liniyalar soni va ruxsat etilgan limit |

---

## 2. `GET /api/billing/spend-chart` — Sarf-xarajatlar dinamikasi

Oylik moliyaviy sarf-xarajatlar va gaplashilgan daqiqalar bo'yicha grafik chizish uchun ma'lumotlar ro'yxatini qaytaradi.

### Request
```http
GET /api/billing/spend-chart?months=6
Authorization: Bearer <accessToken>
```

| Parametr | Turi | Standart | Izoh |
|---|---|---|---|
| `months` | `number` | `6` | Necha oylik tarix qaytarilishi (masalan, 3, 6, 12). Maksimal 24 |

> ℹ️ Faqat **hisoblangan qo'ng'iroqlar** bo'lgan oylar qaytadi, eskisidan yangisiga.
> Hali qo'ng'iroq qilinmagan bo'lsa — **bo'sh massiv**. Ilgari bu holatda o'ylab
> topilgan oylar qaytarilardi.

### Response (`List<SpendMonthDto>`)
```json
{
  "accept": true,
  "data": [
    {
      "month": "2026-04",
      "totalSpendUzs": 3200000,
      "callMinutes": 8900
    },
    {
      "month": "2026-05",
      "totalSpendUzs": 4100000,
      "callMinutes": 11500
    },
    {
      "month": "2026-06",
      "totalSpendUzs": 4800000,
      "callMinutes": 13200
    }
  ],
  "message": null,
  "messageCode": null,
  "errors": null
}
```

---

## 3. `GET /api/billing/invoices` — Hisob-fakturalar ro'yxati

Oylik hisoblangan fakturalar va to'lovlar tarixini sahifalangan holda qaytaradi.

### Request
```http
GET /api/billing/invoices?page=0&size=10
Authorization: Bearer <accessToken>
```

### Response (`PageableData<InvoiceDto>`)
```json
{
  "accept": true,
  "data": {
    "totalPages": 2,
    "currentPage": 0,
    "totalElements": 14,
    "data": [
      {
        "id": "INV-2026-0091",
        "period": "Avgust 2026",
        "amountUzs": 4850000,
        "status": "PAID",
        "paidAt": "2026-09-01T08:14:00Z",
        "pdfUrl": "/api/billing/invoices/INV-2026-0091/pdf"
      },
      {
        "id": "INV-2026-0082",
        "period": "Iyul 2026",
        "amountUzs": 4120000,
        "status": "PAID",
        "paidAt": "2026-08-01T09:00:00Z",
        "pdfUrl": "/api/billing/invoices/INV-2026-0082/pdf"
      }
    ]
  },
  "message": null,
  "messageCode": null,
  "errors": null
}
```

`page`/`size` — bu endpointda **query parametrlar** (`?page=0&size=10`), boshqa
ro'yxatlardagi kabi body'dagi filtr emas.

| Faktura Statusi (`InvoiceStatus`) | Izoh |
|---|---|
| `PENDING` | To'lov kutilmoqda |
| `PAID` | To'langan |
| `CANCELLED` | Bekor qilingan |

> ⚠️ `OVERDUE` degan status **yo'q** — enum'da faqat yuqoridagi uchtasi.

---

## 4. `GET /api/billing/invoices/{id}/pdf` — Hisob-faktura PDF faylini yuklab olish

Belgilangan fakturaning rasmiy muhrli elektron PDF hujjatini to'g'ridan-to'g'ri qaytaradi.

### Request
```http
GET /api/billing/invoices/INV-2026-0091/pdf
Authorization: Bearer <accessToken>
```

### Response
- **Status**: `200 OK`
- **Content-Type**: `application/pdf`
- **Content-Disposition**: `attachment; filename="invoice-INV-2026-0091.pdf"`
- **Body**: Baytlar oqimi (`byte[]`).

---

## 5. `POST /api/billing/topup` — Balansni to'ldirish

Hisobni to'ldirish uchun to'lov tizimiga tranzaksiya yaratadi va foydalanuvchini checkout sahifasiga yo'naltirish havolasini taqdim etadi.

### Request Body (`TopupRequest`)
```json
{
  "amountUzs": 1500000,
  "paymentMethod": "CLICK"
}
```

| Maydon | Turi | Majburiymi | Izoh |
|---|---|---|---|
| `amountUzs` | `number` | ✅ | To'ldirish summasi (minimal 1 000 so'm) |
| `paymentMethod` | `string` | ✅ | To'lov tizimi: `PAYME`, `CLICK`, `BANK_TRANSFER` |

### Response (`TopupResponse`)
```json
{
  "accept": true,
  "data": {
    "paymentId": "PAY-88231920",
    "checkoutUrl": "https://my.click.uz/services/pay?service_id=...&trans_id=PAY-88231920"
  },
  "message": null,
  "messageCode": null,
  "errors": null
}
```

---

## 6. Qo'ng'iroq qanday hisoblanadi

REST endpoint yo'q — bu qism qo'ng'iroq oqimining ichida ishlaydi. Frontend uchun muhimi:
`overview` va `spend-chart` dagi raqamlar shu yerdan keladi.

### 6.1. Ketma-ketlik

| Bosqich | Qachon | Nima bo'ladi |
|---|---|---|
| Tekshiruv | Dialer har tikida, kampaniya bo'yicha | Balans yetmasa kampaniya shu tikda o'tkazib yuboriladi (log'da sabab) |
| Rezerv | Raqam terilishidan oldin, har target uchun | `reservation-uzs` band qilinadi, `call_billing` qatori `RESERVED` holatda ochiladi |
| Qaytarish | Raqam umuman terilmasa (DNC, originate xatosi) | Rezerv qaytariladi, qator `RELEASED` |
| Hisoblash | Qo'ng'iroq tugab, yozuv saqlangach | Sarf narxlanadi, balansdan yechiladi, qator `SETTLED` |

Rezerv qo'ng'iroq bilan **kampaniya target'i orqali** bog'lanadi — pul raqam terilishidan
oldin band qilinadi, o'shanda `call_attempt` qatori hali yo'q.

### 6.2. Nima uchun pul olinadi

| Resurs | Nimaga qarab | Izoh |
|---|---|---|
| LLM | prompt / completion / cached tokenlar | Cached tokenlar alohida, arzonroq narxda |
| STT | qo'ng'iroq davomiyligi | VAD gating tejagan qism platformaning marjasi (`voice.stt.audio.seconds.skipped`) |
| TTS | sintezga so'ralgan belgilar | Cache'dan kelgani ham sanaladi — cache platformaning marjasi (`voice.tts.chars.saved`) |
| Telefoniya | qo'ng'iroq davomiyligi | Trunk narxi |
| Platforma | qo'ng'iroq davomiyligi | Ustama qo'llanilmaydi |

Ustama (`markup-basis-points`) faqat provayder xarajatlariga qo'llanadi, platforma
to'loviga emas. Har bir qator butun so'mgacha yaxlitlanadi va **jami — qatorlar yig'indisi**,
shuning uchun hisob-faktura qo'lda qo'shilganda ham to'g'ri chiqadi.

### 6.3. Ikki marta hisoblanmasligi

Har bir balans harakati `billing_ledger` ga **faqat qo'shiladigan** qator sifatida
yoziladi va `(company_id, idempotency_key)` bo'yicha unique. Qo'ng'iroq hisobining kaliti
— `call-charge:<callAttemptId>`. Finalizer outbox orqali ishlaydi va qayta yetkazishi
mumkin; ikkinchi urinish kalitni band ko'radi va balansga tegmaydi.

Har bir qatorda `balance_before_uzs` va `balance_after_uzs` saqlanadi — balans haqidagi
har qanday bahs shu jadvalni o'qib hal qilinadi.

### 6.4. Sozlamalar (`config/billing.yml`)

| Kalit | Standart | Izoh |
|---|---|---|
| `voice-agent.billing.version` | `2026-09-v1` | Har bir hisoblangan qo'ng'iroqqa yoziladi. Narx o'zgarsa **albatta oshiring** — aks holda eski qo'ng'iroqlar jimgina qayta narxlanadi |
| `voice-agent.billing.enforce-balance` | `false` | `false` bo'lsa balans tekshirilmaydi va rezerv olinmaydi, lekin qo'ng'iroqlar baribir hisoblanadi |
| `voice-agent.billing.reservation-uzs` | `5000` | Bitta qo'ng'iroq uchun band qilinadigan summa |
| `voice-agent.billing.rates.*` | — | Narxlar, hammasi UZS'da (§6.2 jadvali) |
| `voice-agent.billing.auto-recharge.*` | 50 000 / 500 000 / 15 daq | Chegara, summa va tekshiruv jadvali |

> ⚠️ **Balans manfiy bo'lishi mumkin.** Rezerv qo'ng'iroqning haqiqiy narxidan kichik
> bo'lsa (uzoq suhbat), farq balansdan yechiladi va u manfiyga tushishi mumkin. Bu
> ataylab: qo'ng'iroq o'rtasida uzish mijozga ham, kompaniyaga ham yomonroq. Keyingi
> tikda `enforce-balance` shunday kompaniyani terishdan to'xtatadi.

### 6.5. Auto-recharge

`company_billing.auto_recharge = true` bo'lgan kompaniya balansi chegaradan pastga
tushsa, tizim **to'lov havolasi yaratadi** (`payment_topup`, PAYME) — pul o'zi
qo'shilmaydi. To'lanmagan havola turgan bo'lsa yangisi yaratilmaydi.
