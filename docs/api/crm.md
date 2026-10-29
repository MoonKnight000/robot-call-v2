# CRM sozlamalari va Uysot ma'lumotnomasi (`/api/settings/crm`)

Kompaniyaning **o'z** CRM sozlamalari. Har bir quruvchining Uysot voronkasi o'ziga xos, shuning
uchun "uchrashuv belgilandi" yoki "to'lov va'dasi" bosqichining ID'si ham har kompaniyada boshqa.
Ilgari bu ID'lar platforma konfiguratsiyasida (`voice-agent.crm.*-status-id`) **bitta** edi —
endi har kompaniya o'zinikini saqlaydi; saqlanmagan maydon uchun platforma qiymati zaxira bo'lib qoladi.

Huquq: `INTEGRATION_READ` (`GET`) / `INTEGRATION_EDIT` (`PUT`).

---

## `GET /api/settings/crm` — Sozlamalarni olish

Kompaniya hali hech narsa saqlamagan bo'lsa ham `200`: hamma maydon `null`, `updatedAt` = `null`.

```json
{
  "accept": true,
  "data": {
    "companyId": 1,
    "aiEmployeeId": 252,
    "meetingBookedStatusId": 15,
    "promiseToPayStatusId": 22,
    "notInterestedStatusId": 19,
    "callbackStatusId": 12,
    "newLeadStatusId": 11,
    "updatedAt": "2026-10-29T10:15:00Z"
  },
  "message": null, "messageCode": null, "errors": null
}
```

| Maydon | Ma'nosi |
|---|---|
| `aiEmployeeId` | AI qo'ng'iroqlari (call-history), task'lar va yangi lidlar yoziladigan CRM xodimi. Mas'ul menejer ma'lum bo'lsa task o'shanga yoziladi |
| `meetingBookedStatusId` | Natija `meetingBooked=true` → lid shu bosqichga |
| `promiseToPayStatusId` | `PROMISE_TO_PAY` yoki `promisedDate` bor → shu bosqich |
| `notInterestedStatusId` | `REFUSED` / `DO_NOT_CALL` → shu bosqich |
| `callbackStatusId` | `NO_ANSWER` / `CALLBACK_REQUESTED` / `CARRIER_REJECTED` → shu bosqich |
| `newLeadStatusId` | Chat yoki kiruvchi qo'ng'iroqdan yaratilgan yangi lid boshlanadigan bosqich |

Ssenariy `outcomeSchema`sida `targetStatusId` qaytarsa — u hammasidan ustun.

## `PUT /api/settings/crm` — Sozlamalarni saqlash

Butun obyekt almashtiriladi: yuborilmagan (yoki `null`) maydon tozalanadi va platforma qiymatiga qaytadi.
Har bir ID musbat butun son bo'lishi shart (aks holda `400 VALIDATION_FAILED`).

```json
{
  "aiEmployeeId": 252,
  "meetingBookedStatusId": 15,
  "promiseToPayStatusId": 22,
  "notInterestedStatusId": 19,
  "callbackStatusId": 12,
  "newLeadStatusId": 11
}
```

Javob — `GET` bilan bir xil.

---

# Uysot ma'lumotnomasi (`/api/crm/uysot`)

Kompaniyaning Uysot'dagi ma'lumoti, **jonli** o'qiladi (qisqa kesh bilan). Sozlamalar ekrani
voronka bosqichlari va xodimlarni shu yerdan tanlaydi; agentning ko'chmas mulk tool'lari ham
xuddi shu ma'lumotni ko'radi.

Huquq: `INTEGRATION_READ`.

| Holat | Javob |
|---|---|
| Kompaniyada Uysot ulanishi yo'q | `409 CRM_NOT_CONNECTED` |
| Uysot javob bermadi / xato qaytardi | `502 UYSOT_REQUEST_FAILED` |
| Server konfiguratsiyasida yo'l bo'sh (`voice-agent.crm.catalog.*`) | `502 UYSOT_ENDPOINT_NOT_CONFIGURED` |

Kesh: voronka, xodim va uylar — 15 daqiqa; xonadon qidiruvi — 3 daqiqa (sotilgan xonadon taklif
qilinmasligi uchun). Shartnoma va to'lov jadvali keshlanmaydi.

## `GET /api/crm/uysot/pipes` — Voronkalar va bosqichlar

```json
{
  "accept": true,
  "data": [
    { "id": 4, "name": "Sotuv", "statuses": [
        { "id": 11, "name": "Yangi lid" },
        { "id": 15, "name": "Uchrashuv belgilandi" },
        { "id": 19, "name": "Rad etdi" } ] }
  ]
}
```

## `GET /api/crm/uysot/employees` — Xodimlar

```json
{ "accept": true, "data": [ { "id": 252, "fullName": "Dilshod Karimov", "phone": "+998901112233", "departmentName": "Sotuv" } ] }
```

## `GET /api/crm/uysot/houses` — Turar-joy majmualari

```json
{ "accept": true, "data": [ { "id": 7, "name": "Yunusobod Residence", "onSale": true, "branchName": "Toshkent", "currency": "USD" } ] }
```

## `POST /api/crm/uysot/flats/search` — Sotuvdagi xonadonlar

Faqat `SALE` holatidagilar, eng arzonidan boshlab. Hamma maydon ixtiyoriy; `limit` 1–20 (berilmasa 5).

```json
{ "rooms": 3, "priceFrom": null, "priceTo": 60000, "houseId": 7, "floorFrom": 2, "floorTo": 9, "limit": 5 }
```

```json
{
  "accept": true,
  "data": [
    { "id": 1201, "houseName": "Yunusobod Residence", "buildingName": "A blok", "number": "45",
      "rooms": 3, "area": 78.5, "floor": 5, "price": 58900, "priceRepaired": 64000,
      "pricePerArea": 750, "currency": "USD", "status": "SALE" }
  ]
}
```

---

# Agentning ko'chmas mulk tool'lari (Uysot)

Kompaniya Uysot'ga ulangan bo'lsa, uning **har bir** agentiga (ovozli cascade, realtime va chat)
quyidagi tool'lar avtomatik beriladi — ssenariyda e'lon qilish shart emas, istalgan bosqichda
ishlaydi (katalog qidiruvi kabi: faqat o'qiydi, CRM'da hech narsa o'zgartirmaydi).

| Tool | Qachon | Nima qaytaradi |
|---|---|---|
| `searchFlats(rooms, maxPrice, minPrice, complexName, floorFrom, floorTo)` | "3 xonali bormi?", "60 mingga nima bor?" | Sotuvdagi 3 ta eng arzon xonadon: majmua, blok, xona, m², qavat, narx |
| `listResidentialComplexes()` | "Qaysi loyihalaringiz bor?" | Sotuvdagi majmualar nomi |
| `getContractBalance(contractNumber?)` | "Qancha qoldi?", "Qarzim qancha?" | Shartnoma summasi, to'langan, qoldiq, kechikish kunlari, navbatdagi to'lov |
| `getPaymentSchedule(contractNumber?)` | "To'lov jadvalim qanday?" | To'lanmagan navbatdagi 3 ta to'lov (sana, summa, muddati o'tganmi) |
| `requestManagerCallback(reason, preferredTime?)` | "Menejer bilan gaplashmoqchiman", chegirma/shartlar | Outcome'ga `managerCallbackRequested` yozadi → qo'ng'iroqdan keyin CRM task |

**Shartnoma qanday topiladi:** tool'ga berilgan raqam → qo'ng'iroq faktidagi `contractNumber` →
mijoz telefoni bo'yicha faol shartnomalar (bir nechta bo'lsa — eng ko'p kechikkani).

**Guardrail:** tool javobidagi har bir raqam `FactGuard` ga "aytish mumkin" deb beriladi; agent narx,
qoldiq yoki sana to'qiy olmaydi. Uysot javob bermasa tool "ma'lumot hozir mavjud emas" deydi va agent
mijozga menejer aniqlashtirishini aytadi.

**Qo'ng'iroqdan keyin:** ssenariy `outcomeSchema`sida `managerCallbackRequested` (boolean) bo'lsa va
xulosa uni `true` desa, Uysot'da "Mijozga qayta qo'ng'iroq qiling (AI bot)" task'i yaratiladi
(`real_estate` va `real_estate_contract` paketlarida bu maydon bor). Task mas'ul xodimi — CRM
sozlamasidagi `aiEmployeeId`.

---

## `GET /api/crm/uysot/clients/{phone}` — Mijoz profili (Client 360)

Telefon bo'yicha Uysot'dagi lid va faol shartnomalar. Huquq: `CONTACT_READ`.
Telefon formati `/api/memory/{phone}` bilan bir xil; noto'g'ri raqam → `400 PHONE_INVALID`.
Uysot ulanmagan → `409 CRM_NOT_CONNECTED`. Lid topilmasa `lead` = `null`.

Xotira (`GET /api/memory/{phone}`) bilan birga ko'rsatiladi: xotira — **biz** u bilan nima
gaplashganimiz, bu — **CRM** u haqida nima biladi.

```json
{
  "accept": true,
  "data": {
    "phone": "+998901234567",
    "lead": { "name": "Ali Valiyev", "debtAmount": 50000, "currency": "USD", "dueDate": null,
              "contractNumber": "UY-123", "preferredLanguage": null, "penaltyAmount": null,
              "contractCancelDays": null, "leadId": 99436 },
    "contracts": [
      { "id": 10, "number": "UY-123", "clientName": "Ali Valiyev", "amount": 80000, "paidAmount": 30000,
        "residue": 50000, "currency": "USD", "delayDays": 12, "status": "ACTIVE", "leadId": 99436,
        "responsibleEmployeeId": 252, "responsibleEmployeeName": "Dilshod Karimov" }
    ]
  }
}
```
