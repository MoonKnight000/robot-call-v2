# Konversiya va atributsiya API

`uz.murodjon.robotcallv2.conversion` · huquq: **CAMPAIGN_READ** / **CAMPAIGN_EDIT**

**Nima uchun kerak:** disposition — natija emas. Bot `PROMISE_TO_PAY` yozganda mijoz
to'lashga *rozi bo'lgan*, to'lagani emas. Shu sababli va'da olishda kuchli variant
A/B hisobotda doim g'olib bo'lib ko'rinardi. Bu bo'lim kompaniyaning o'z tizimiga
"mijoz haqiqatan to'ladi" deb aytish imkonini beradi va platforma buni qaysi
qo'ng'iroq, qaysi kampaniya va qaysi A/B variant keltirganini o'zi aniqlaydi.

Umumiy javob formati va xatolar uchun [README.md](README.md)ga qarang.

---

## 1. Model

```
conversion_goal        →  kompaniya nimani natija deb sanaydi va necha soat ichida
        ↓
conversion_event       →  tashqi tizim xabar bergan bitta hodisa (to'lov, uchrashuv)
        ↓
conversion_attribution →  uni qaysi qo'ng'iroq keltirdi
```

**Maqsad kompaniya darajasida**, kampaniya darajasida emas. Sabab amaliy: hodisani
yuboradigan tizim mijoz to'laganini biladi, uni qaysi kampaniya chaqirganini emas —
buni aniqlash atributsiyaning ishi. Shu sababli bitta CRM webhook kompaniyaning barcha
kampaniyalariga xizmat qiladi.

**Moslashtirish telefon raqami bo'yicha.** Ikkala tomon ham raqamni biladi; ichki ID
bo'yicha moslashtirish birovning CRM'i bilan doim sinxron turishi kerak bo'lgan mapping
jadvalini talab qilardi. Raqam yuborishdan oldin normallashtiriladi, ya'ni
`90 123 45 67` ham `+998901234567` ga terilgan qo'ng'iroqni topadi.

**Faqat javob berilgan qo'ng'iroqlar** va **faqat hodisadan oldingilari** hisobga
olinadi: jiringlab qo'yilgan qo'ng'iroq hech kimning fikrini o'zgartirmagan, mijoz
to'laganidan keyin qilingan qo'ng'iroq ham.

---

## 2. `PUT /api/conversions/goals` — Maqsad yaratish/yangilash

`goalKey` bo'yicha upsert.

```json
{
  "goalKey": "payment",
  "name": "Qarz to'landi",
  "attributionWindowHours": 72,
  "attributionModel": "LAST_CALL",
  "enabled": true
}
```

| Maydon | Turi | Majburiymi | Izoh |
|---|---|---|---|
| `goalKey` | `string` | ✅ | Tashqi tizim shu nom bilan yuboradi. Maks. 64 belgi |
| `name` | `string` | ✅ | Panelda ko'rinadigan nom |
| `attributionWindowHours` | `number` | ✅ | 1..8760. Qo'ng'iroqdan keyin necha soat ichida sanaladi |
| `attributionModel` | `string` | ❌ | `LAST_CALL` (standart) yoki `FIRST_CALL` |
| `enabled` | `boolean` | ❌ | Standart `true` |

`LAST_CALL` — oynadagi eng oxirgi javob berilgan qo'ng'iroq. Qarz undirishda odamni
qo'zg'atgan qo'ng'iroq deyarli har doim u ko'targan oxirgisi bo'ladi. `FIRST_CALL` —
birinchi kontakt asosiy ishni qilib, keyingilari faqat qarorni kutgan holatlar uchun.

## 3. `GET /api/conversions/goals` — Maqsadlar ro'yxati

## 4. `DELETE /api/conversions/goals/{id}` — O'chirish

Qator **o'chirilmaydi**, `enabled = false` qilinadi. Ostidagi atributsiyalar o'qilishi
kerak; o'z maqsadining nomini ayta olmaydigan hisobot — hech kim yubormaydigan maqsad
nomini ko'rsatadigan hisobotdan yomonroq.

---

## 5. `POST /api/conversions` — Hodisa haqida xabar berish

Odatda buni CRM `X-Api-Key` bilan chaqiradi ([api-keys.md](api-keys.md)); kalitga
`CAMPAIGN_EDIT` scope'i kerak.

### Request Body (`ReportConversionRequest`)
```json
{
  "goalKey": "payment",
  "phone": "998901234567",
  "occurredAt": "2026-09-07T12:00:00Z",
  "valueUzs": 1500000,
  "source": "crm",
  "dedupeKey": "payment-88231",
  "evidence": { "orderId": "88231", "method": "PAYME" }
}
```

| Maydon | Turi | Majburiymi | Izoh |
|---|---|---|---|
| `goalKey` | `string` | ✅ | Sozlanmagan bo'lsa `400 CONVERSION_GOAL_UNKNOWN` |
| `phone` | `string` | ✅ | Har qanday formatda; normallashtiriladi |
| `occurredAt` | `string` (ISO-8601) | ✅ | **Hodisa vaqti**, yuborilgan vaqt emas |
| `valueUzs` | `number` | ❌ | Puli yo'q maqsad uchun `null` |
| `source` | `string` | ❌ | `crm`, `payme`, integratsiya nomi |
| `dedupeKey` | `string` | ✅ | Yuboruvchining o'z ID'si. Takroriy urinish hech narsani o'zgartirmaydi |
| `evidence` | `object` | ❌ | Keyinchalik bahs uchun saqlanadigan har narsa |

### Response (`ConversionResultResponse`)
```json
{
  "accept": true,
  "data": {
    "conversionEventId": 4211,
    "attributed": true,
    "reason": null,
    "duplicate": false,
    "campaignId": 12,
    "variantId": 3,
    "callAttemptId": 40591
  },
  "message": null, "messageCode": null, "errors": null
}
```

Javob **sinxron va rostgo'y**: raqam hech nimaga mos kelmasa `attributed: false` va
`reason` qaytadi. Har doim 200 qaytarib, hodisalarning yarmini jimgina yo'qotadigan
webhook — kampaniya raqamlari hech kim sezmasdan buziladigan yo'l.

| Holat | `attributed` | `reason` |
|---|---|---|
| Qo'ng'iroq topildi | `true` | `null` |
| Oyna ichida javob berilgan qo'ng'iroq yo'q | `false` | `no answered call to this number in the last 72h` |
| Maqsad o'chirilgan | `false` | `goal is switched off` |
| Raqamni o'qib bo'lmadi | `false` | `phone could not be read as a number` |

**Takroriylik:** o'sha `dedupeKey` bilan qayta yuborilsa, birinchi marta qabul qilingan
qaror qaytadi va `duplicate: true` bo'ladi. Balansga ham, hisobotga ham hech narsa
qo'shilmaydi.

Mos kelmagan hodisa ham **saqlanadi** (`rejected = true` va sabab bilan). O'chirib
yuborilsa, ertaga o'sha webhook qayta urinib yangi hodisa sifatida kelardi.

---

## 6. A/B hisobotidagi yangi ustunlar

`GET /api/campaigns/{id}/variants/report` javobidagi har bir variantda:

| Maydon | Izoh |
|---|---|
| `convertedCount`, `conversionRate` | **Eskisi:** bot yozgan disposition (va'da) |
| `attributedConversions` | **Yangisi:** shu variant qo'ng'iroqlariga bog'langan haqiqiy konversiyalar |
| `attributedValueUzs` | Ularning umumiy qiymati |
| `spentUzs` | Shu variant qo'ng'iroqlariga hisoblangan xarajat ([billing.md](billing.md) §6) |
| `costPerConversionUzs` | `spentUzs / attributedConversions`, konversiya bo'lmasa `null` |

> ℹ️ **Ikkala to'plam ham qoladi.** Maqsad sozlamagan kompaniyada yangi ustunlar 0
> bo'ladi, eskilari esa ishlashda davom etadi.
>
> ⚠️ **G'olibni aniqlash (`winningVariantName`) hozircha eski `convertedCount` ustuni
> bo'yicha hisoblanadi.** Uni haqiqiy konversiyaga o'tkazish — ma'lumot to'plangandan
> keyin ataylab qabul qilinadigan qaror; hozir o'tkazilsa, maqsad sozlamagan har bir
> kompaniyada g'olib "yo'q" bo'lib qolardi.

---

## 7. Cheklovlar

- Atributsiya **hodisa kelganda darhol** hisoblanadi, keyin qayta hisoblanmaydi. Maqsad
  oynasi o'zgartirilsa, eski atributsiyalar o'z oynasi bilan qolaveradi (har qatorda
  `window_hours` va `attribution_model` saqlanadi).
- Bitta hodisa — bitta qo'ng'iroq. Bir nechta qo'ng'iroqqa ulush bo'lib taqsimlanmaydi.
- CRM'dan hodisalarni o'zimiz tortib olish (poll) yo'q — faqat push.
