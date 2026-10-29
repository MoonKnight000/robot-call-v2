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
