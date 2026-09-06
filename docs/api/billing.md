# Billing & Balans API

`uz.murodjon.robotcallv2.billing` · huquq: **BILLING_READ** / **BILLING_EDIT** (topup)

Kompaniyaning joriy tarif rejasi, balansi, qo'ng'iroq daqiqalari va AI token sarf-xarajatlari metrikalari, oylik hisobotlar (invoices), hisob-fakturani PDF formatida yuklab olish hamda hisobni to'ldirish (Payme, Click, Bank Transfer) endpointlari.

Umumiy javob formati va xatolar uchun [README.md](README.md)ga qarang.

---

## 1. `GET /api/billing/overview` — Hisob holati va limitlar

Kompaniyaning umumiy balans holati, faol tarif rejasi, keyingi hisob-kitob sanasi va asosiy resurslar (daqiqalar, AI tokenlar, TTS belgilar, parallel chiquvchi kanallar) bo'yicha joriy sarf va limitlarni qaytaradi.

> ℹ️ Kompaniyada hali `company_billing` / `billing_usage` qatori bo'lmasa, birinchi
> chaqiruvda **standart qator yaratiladi** (`PRO_MONTHLY` / "Professional (Pro)",
> balans 1 450 000 so'm, 5000 daqiqa, 2 000 000 token, 1 000 000 TTS belgi, 30 kanal).
> Ya'ni bu qiymatlar haqiqiy sarf emas, boshlang'ich sozlama — real hisoblash ulanmaguncha
> shunday qoladi.

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
| `months` | `number` | `6` | Necha oylik tarix qaytarilishi (masalan, 3, 6, 12) |

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
