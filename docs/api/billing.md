# Billing & Balans API

`uz.murodjon.robotcallv2.billing` · huquq: **BILLING_READ** / **BILLING_EDIT** (topup) · to'lov
callback'lari (§7, §8) — ochiq, provayderning o'z imzosi bilan

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

Faktura topilmasa **yoki boshqa kompaniyaniki bo'lsa** — `404 INVOICE_NOT_FOUND` (ilgari `403`
qaytarardi va shu bilan faktura mavjudligini oshkor qilardi).

---

## 5. `POST /api/billing/topup` — Balansni to'ldirish

To'lov yozuvini (`payment_topup`, status `PENDING`) yaratadi va to'lovchini yuborish uchun
checkout havolasini qaytaradi. **Pul balansga havola yaratilganda tushmaydi** — faqat
provayder to'lov o'tganini callback orqali xabar qilganda (Payme §7, Click §8).

### Request Body (`TopupRequest`)
```json
{
  "amountUzs": 1500000,
  "paymentMethod": "PAYME"
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
    "paymentId": "PAY-8823A19F",
    "checkoutUrl": "https://checkout.paycom.uz/<base64(m=<merchant-id>;ac.payment_id=PAY-8823A19F;a=150000000)>"
  },
  "message": null,
  "messageCode": null,
  "errors": null
}
```

| `paymentMethod` | `checkoutUrl` |
|---|---|
| `PAYME` | `<checkout-url>/` + base64(`m=<merchant-id>;ac.payment_id=<paymentId>;a=<summa tiyinda>` + `;c=<return-url>` sozlangan bo'lsa). 1 so'm = 100 tiyin |
| `CLICK` | `https://my.click.uz/services/pay?service_id=..&merchant_id=..&amount=<so'm>&transaction_param=<paymentId>` + `&return_url=<URL-encoded>` sozlangan bo'lsa |
| `BANK_TRANSFER` | `/api/billing/topup/<paymentId>/bank-details` (o'zgarmagan) |

Top-up faqat **o'zi yaratilgan usul bilan** to'lanadi: `PAYME` top-up'ni Click (va aksincha)
"topilmadi" deb qaytaradi.

### Xatolar

| Status | `messageCode` | Qachon |
|---|---|---|
| `400` | `VALIDATION_FAILED` | `amountUzs` 1 000 dan kam yoki `paymentMethod` yo'q |
| `409` | `PAYMENT_METHOD_NOT_CONFIGURED` | Tanlangan provayder (`PAYME` / `CLICK`) platformada sozlanmagan — `enabled=false` yoki merchant kalitlari bo'sh (§9). Top-up yaratilmaydi. Ilgari bu holda o'ylab topilgan merchant'ga havola qaytarilardi va to'lovchi xatoni faqat provayder sahifasida ko'rardi |

### Top-up statuslari (`TopupStatus`)

Status hozircha hech bir REST javobda qaytmaydi (faqat `payment_topup` jadvalida), lekin
auto-recharge va callback'lar shunga qarab ishlaydi.

| Status | Izoh |
|---|---|
| `PENDING` | To'lov kutilmoqda — yangi to'lov boshlanishi mumkin |
| `COMPLETED` | To'landi, summa balansga qo'shildi (`billing_ledger`, `TOPUP`) |
| `CANCELLED` | Provayder boshlangan to'lovni bekor qildi (Payme `CancelTransaction`, Click Complete `error < 0`). Qayta to'lash uchun yangi top-up kerak |
| `EXPIRED` | `topup-ttl` (standart 24 soat) ichida to'lanmadi; har `expiry-cron`da (standart 10 daqiqa) belgilanadi, usulidan qat'i nazar. Yangi to'lov boshlanmaydi, lekin muddat tugashidan **oldin** boshlangan to'lov baribir hisobga olinadi — tushgan pul rad etilmaydi |
| `FAILED` | Hozircha ishlatilmaydi |

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
| Realtime engine | engine ishlagan daqiqalar (`realtime-per-minute-uzs`) | Faqat `REALTIME` qo'ng'iroqlarda. Bunday qo'ng'iroqda LLM/STT/TTS qatorlari `0` — tanish, o'ylash va gapirish bitta vendor ichida bo'lgan. Engine xabar qilgan tokenlar `call_billing` ga yoziladi, lekin narxlanmaydi (audio tokenlar matn-LLM narxiga to'g'ri kelmaydi) |
| Telefoniya | qo'ng'iroq davomiyligi | Trunk narxi |
| Platforma | qo'ng'iroq davomiyligi | Ustama qo'llanilmaydi |
| Post-call summary | summary so'rovining prompt / completion / cached tokenlari | Suhbatdan keyingi alohida LLM so'rovi, har qanday qo'ng'iroqda (realtime'da ham) matn-LLM narxida. Qo'ng'iroq hisoblangandan keyin alohida ledger yozuvi (`summary-charge:<callAttemptId>`) bilan yechiladi va `call_billing` qatorining LLM qatori va jamisiga qo'shiladi; tokenlar `summary_*_tokens` ustunlarida. `metrics.aiTokens` ularni ham sanaydi |

Ustama (`markup-basis-points`) faqat provayder xarajatlariga qo'llanadi, platforma
to'loviga emas. Har bir qator butun so'mgacha yaxlitlanadi va **jami — qatorlar yig'indisi**,
shuning uchun hisob-faktura qo'lda qo'shilganda ham to'g'ri chiqadi.

### 6.3. Ikki marta hisoblanmasligi

Har bir balans harakati `billing_ledger` ga **faqat qo'shiladigan** qator sifatida
yoziladi va `(company_id, idempotency_key)` bo'yicha unique. Qo'ng'iroq hisobining kaliti
— `call-charge:<callAttemptId>`, uning summary'siniki — `summary-charge:<callAttemptId>`,
top-up'niki — `topup:<paymentId>`. Finalizer outbox orqali
ishlaydi va qayta yetkazishi mumkin, to'lov provayderi ham callback'ni takrorlaydi; ikkinchi
urinish kalitni band ko'radi va balansga tegmaydi.

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
| `voice-agent.billing.payment.*` | — | Payme / Click va top-up muddati — §9 |

> **`enforce-balance: true` bo'lsa balans manfiyga tushmaydi.** Qo'ng'iroq boshlanganda uning
> eng uzun davomiyligi kompaniyaning bo'sh balansi (+ shu qo'ng'iroq rezervi) bilan cheklanadi:
> daqiqa narxining pastki chegarasi — telefoniya + STT (realtime'da realtime narxi) markup bilan
> + platforma to'lovi — bo'yicha, lekin kamida 60 soniya (liniyadagi odam xayrlashuvni eshitadi).
> Token va TTS narxi oldindan ma'lum emas, shuning uchun hisob-kitobda balansdan oshgan qism
> yechilmaydi — hisobdan chiqariladi (server logida `written off`). `call_billing` qatorida
> qo'ng'iroqning to'liq narxi qoladi, ledger'da esa haqiqatda yechilgan summa.
>
> `enforce-balance: false` bo'lsa avvalgidek: qo'ng'iroq to'liq narxda hisoblanadi va balans
> manfiyga tushishi mumkin.
>
> Dialerdan tashqari qo'ng'iroqlar (qo'lda/test, operator, callback, navbatdan tashqi raqamga
> yo'naltirish, konsultatsiya, brauzer test va widget) ham boshlanishdan oldin balans bilan
> tekshiriladi (`409 CALL_BALANCE_INSUFFICIENT`).

### 6.5. Auto-recharge

`company_billing.auto_recharge = true` bo'lgan kompaniya balansi chegaradan pastga
tushsa, tizim **to'lov havolasi yaratadi** (`payment_topup`, PAYME) — pul o'zi
qo'shilmaydi, u faqat Payme to'lovni o'tkazganda tushadi (§7). So'nggi `topup-ttl` ichida
yaratilgan to'lanmagan (`PENDING`) havola turgan bo'lsa yangisi yaratilmaydi; muddati
o'tgan (`EXPIRED`) yoki bekor qilingan (`CANCELLED`) havola endi to'sib qo'ymaydi — ilgari
`PENDING` hech qachon yopilmagani uchun auto-recharge umrida bir marta ishlardi.

Payme sozlanmagan bo'lsa havola yaratilmaydi (§5, `409`), har tekshiruvda log'da xato yoziladi.

---

## 7. `POST /api/billing/payme/callback` — Payme Merchant API

> ⚠️ **Frontend uchun emas.** Buni Payme serverlari chaqiradi. Payme kabinetida endpoint
> sifatida `https://<host>/api/billing/payme/callback`, hisob (account) maydoni sifatida
> `payment_id` ro'yxatdan o'tkaziladi.

- **Autentifikatsiya:** Bearer token yo'q (yo'l `SecurityConfig`da ochiq). Har bir so'rov
  `Authorization: Basic base64("Paycom:<PAYME_KEY>")` bilan keladi, kalit doimiy vaqtda
  solishtiriladi. Noto'g'ri / yo'q bo'lsa yoki Payme sozlanmagan bo'lsa — `-32504`.
- **Javob `ResponseData` emas** — JSON-RPC 2.0, Payme formatida. **HTTP status har doim
  `200`**, natija body'da: `{"result": {...}, "id": ..}` yoki
  `{"error": {"code": .., "message": {"ru": .., "uz": .., "en": ..}, "data": ..}, "id": ..}`.
  Ichki xato (masalan DB yiqilsa) ham `200` + `-32400`; Payme so'rovni takrorlaydi.
- **Summalar tiyinda** (1 so'm = 100 tiyin), `account.payment_id` — `POST /api/billing/topup`
  qaytargan `paymentId`. Vaqtlar — epoch millisekund.

### Request
```http
POST /api/billing/payme/callback
Authorization: Basic UGF5Y29tOjxrZXk+
Content-Type: application/json

{"method": "CheckPerformTransaction", "params": {"amount": 150000000, "account": {"payment_id": "PAY-8823A19F"}}, "id": 1}
```

### Metodlar

| Metod | Parametrlar | Muvaffaqiyatli `result` | Xatolar |
|---|---|---|---|
| `CheckPerformTransaction` | `amount`, `account` | `{"allow": true}` + IKPU sozlangan bo'lsa `detail` (fiskal chek, bitta qator) | `-31050` top-up topilmadi · `-31001` summa noto'g'ri · `-31051` to'lab bo'lmaydi (`PENDING` emas: to'langan / bekor / `EXPIRED`) · `-31052` top-up'da boshqa faol Payme tranzaksiyasi bor |
| `CreateTransaction` | `id`, `time`, `amount`, `account` | `{"create_time", "transaction", "state": 1}` | Yangi `id` — `CheckPerformTransaction` bilan bir xil tekshiruvlar. Mavjud `id`: holati 1 emas — `-31008`; 12 soatdan (43 200 000 ms) eski — reason `4` bilan bekor qilinadi va `-31008`; aks holda o'sha javob qaytadi |
| `PerformTransaction` | `id` | `{"transaction", "perform_time", "state": 2}` | `-31003` topilmadi · holati 1 va muddati o'tgan — reason `4` bilan bekor, `-31008` · bekor qilingan — `-31008` |
| `CancelTransaction` | `id`, `reason` | `{"transaction", "cancel_time", "state": -1}` | `-31003` topilmadi · `-31007` allaqachon bajarilgan (balansga tushgan pul sarflangan bo'lishi mumkin — qaytarish qo'lda hal qilinadi) |
| `CheckTransaction` | `id` | `{"create_time", "perform_time", "cancel_time", "transaction", "state", "reason"}` — bo'lmagan vaqt `0`, `reason` bekor qilinmaguncha `null` | `-31003` topilmadi |
| `GetStatement` | `from`, `to` | `{"transactions": [{"id", "time", "amount", "account", "create_time", "perform_time", "cancel_time", "transaction", "state", "reason", "receivers": null}]}` — Payme `time`i bo'yicha oraliqda, eskisidan yangisiga | — |

Umumiy xatolar: `-32504` autentifikatsiya · `-32700` body JSON emas · `-32600` majburiy
parametr yo'q · `-32601` noma'lum metod (`data` = metod nomi) · `-32400` tizim xatosi.
`-31050..-31052` da `data: "payment_id"`.

### Qanday ishlaydi

- `PerformTransaction` — balans **aynan shu yerda** to'ldiriladi: top-up `COMPLETED`,
  `billing_ledger`ga `TOPUP` qatori (`topup:<paymentId>` kaliti bilan), `company_billing.balance_uzs`
  oshadi — hammasi bitta tranzaksiyada. Takroriy `PerformTransaction` o'sha javobni qaytaradi
  va **ikkinchi marta hisobga olmaydi** (qator lock + shartli status o'zgarishi + ledger kaliti).
- `CancelTransaction` (holat 1) top-up'ni ham `CANCELLED` qiladi.
- Top-up `EXPIRED` bo'lib qolgan bo'lsa ham, **undan oldin** yaratilgan tranzaksiya bajariladi.
- Tranzaksiyalar `payment_provider_transaction` jadvalida (`provider = PAYME`,
  `(provider, provider_transaction_id)` unique).

---

## 8. `POST /api/billing/click/prepare` + `/complete` — Click SHOP API

> ⚠️ **Frontend uchun emas.** Buni Click serverlari chaqiradi. Click kabinetida Prepare URL
> `https://<host>/api/billing/click/prepare`, Complete URL `https://<host>/api/billing/click/complete`.

- **So'rov:** `application/x-www-form-urlencoded`, Click maydonlari: `click_trans_id`,
  `service_id`, `click_paydoc_id`, `merchant_trans_id` (= top-up `paymentId`), `merchant_prepare_id`
  (faqat Complete), `amount` (so'mda, kasr bilan bo'lishi mumkin: `1500000.00`), `action`
  (Prepare `0`, Complete `1`), `error`, `error_note`, `sign_time`, `sign_string`.
- **Autentifikatsiya:** Bearer token yo'q (yo'llar ochiq). `sign_string` =
  `md5(click_trans_id + service_id + CLICK_SECRET_KEY + merchant_trans_id + [merchant_prepare_id — faqat Complete] + amount + action + sign_time)`,
  qiymatlar Click yuborganidek (masalan `amount` `"1500000.00"` bo'lsa shundayligicha), doimiy vaqtda solishtiriladi.
- **Javob `ResponseData` emas** — Click formatidagi JSON, HTTP `200`:

```json
{
  "click_trans_id": 2003,
  "merchant_trans_id": "PAY-8823A19F",
  "merchant_prepare_id": 41,
  "error": 0,
  "error_note": "Success"
}
```

Complete'da `merchant_prepare_id` o'rniga `merchant_confirm_id`. Rad etilganda id maydoni
bo'lmaydi, `error` manfiy.

| `error` | Qachon |
|---|---|
| `0` | Muvaffaqiyat |
| `-1` | `sign_string` mos emas yoki Click sozlanmagan |
| `-2` | `amount` top-up summasiga teng emas |
| `-3` | `action` noto'g'ri endpointga (Prepare'ga `1`, Complete'ga `0`) |
| `-4` | Top-up allaqachon to'langan / tranzaksiya allaqachon bajarilgan |
| `-5` | Top-up topilmadi (yoki CLICK usulida yaratilmagan) |
| `-6` | `merchant_prepare_id` bo'yicha tranzaksiya topilmadi (yoki boshqa `click_trans_id` / top-up'niki) |
| `-8` | Majburiy maydon yo'q yoki raqam emas |
| `-9` | Tranzaksiya / top-up bekor qilingan yoki muddati o'tgan (Prepare'da `EXPIRED` ham) |

- **Prepare** top-up `PENDING` bo'lsa tranzaksiya ochadi; bir xil `click_trans_id` bilan
  takroriy Prepare o'sha `merchant_prepare_id`ni qaytaradi.
- **Complete** balansni **faqat bir marta** to'ldiradi (Payme bilan bir xil yo'l: `COMPLETED` +
  ledger `topup:<paymentId>`). Takroriy Complete — `-4`. Click `error < 0` yuborsa (karta rad
  etildi va h.k.) tranzaksiya va top-up bekor qilinadi, javob `-9`.
- Prepare'dan keyin top-up `EXPIRED` bo'lsa ham Complete baribir hisobga oladi.

---

## 9. To'lov sozlamalari (`voice-agent.billing.payment.*`)

Kalitlar kodda yo'q — env'dan olinadi. Provayder o'chiq yoki kalitlari bo'sh bo'lsa: uning
top-up'lari `409 PAYMENT_METHOD_NOT_CONFIGURED`, callback'lari rad etiladi (Payme `-32504`, Click `-1`).

| Kalit | Env | Standart | Izoh |
|---|---|---|---|
| `topup-ttl` | `PAYMENT_TOPUP_TTL` | `24h` | Shu vaqtda to'lanmagan top-up `EXPIRED` bo'ladi |
| `expiry-cron` | `PAYMENT_TOPUP_EXPIRY_CRON` | `0 */10 * * * *` | Muddat tekshiruvi jadvali |
| `payme.enabled` | `PAYME_ENABLED` | `false` | |
| `payme.merchant-id` | `PAYME_MERCHANT_ID` | — | Kassa id (checkout `m=`) |
| `payme.key` | `PAYME_KEY` | — | Kassa kaliti; callback `Paycom:<key>` bilan keladi. Sandbox'da test kalit |
| `payme.checkout-url` | `PAYME_CHECKOUT_URL` | `https://checkout.paycom.uz` | Sandbox: `https://test.paycom.uz` |
| `payme.return-url` | `PAYME_RETURN_URL` | — | To'lovdan keyin qaytish manzili (`c=`) |
| `payme.ikpu-code` | `PAYME_IKPU_CODE` | — | Fiskal chek uchun IKPU (MXIK); bo'sh bo'lsa `detail` yuborilmaydi |
| `payme.package-code` | `PAYME_PACKAGE_CODE` | — | IKPU'ning qadoq kodi |
| `payme.vat-percent` | `PAYME_VAT_PERCENT` | `12` | Chekdagi QQS foizi |
| `click.enabled` | `CLICK_ENABLED` | `false` | |
| `click.service-id` | `CLICK_SERVICE_ID` | — | |
| `click.merchant-id` | `CLICK_MERCHANT_ID` | — | |
| `click.secret-key` | `CLICK_SECRET_KEY` | — | `sign_string` shu kalit bilan hisoblanadi |
| `click.return-url` | `CLICK_RETURN_URL` | — | To'lovdan keyin qaytish manzili |

Qo'lda sinash: `src/main/java/uz/murodjon/robotcallv2/billing/presentation/http/billing.http`.
