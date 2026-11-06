# CRM sozlamalari va Uysot ma'lumotnomasi (`/api/settings/crm`)

Kompaniyaning **o'z** CRM sozlamalari. Har bir quruvchining Uysot voronkasi o'ziga xos, shuning
uchun "uchrashuv belgilandi" yoki "to'lov va'dasi" bosqichining ID'si ham har kompaniyada boshqa.
Ilgarigi qat'iy ustunlar o'rniga relyatsion `crm_status_mapping` jadvali ishlatiladi — har qanday
qo'ng'iroq natijasi (`Disposition`) yoki maxsus outcome kaliti CRM bosqichiga bog'lanadi.
Saqlanmagan maydon uchun platforma qiymati zaxira bo'lib qoladi.

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
    "taskTypeId": 7,
    "statusMappings": {
      "MEETING_BOOKED": 15,
      "PROMISE_TO_PAY": 22,
      "NOT_INTERESTED": 19,
      "CALLBACK_REQUESTED": 12,
      "NEW_LEAD": 11
    },
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
| `taskTypeId` | AI ochadigan task turi (`GET /v1/open-api/lead-task/types`). Bo'sh bo'lsa — CRM'dagi birinchi tur |
| `statusMappings` | Qo'ng'iroq natijasi (disposition yoki maxsus outcome kaliti) ➔ CRM voronkasi bosqich ID'si xaritasi |
| `meetingBookedStatusId` | Moslik uchun: `statusMappings["MEETING_BOOKED"]` qiymati |
| `promiseToPayStatusId` | Moslik uchun: `statusMappings["PROMISE_TO_PAY"]` qiymati |
| `notInterestedStatusId` | Moslik uchun: `statusMappings["NOT_INTERESTED"]` qiymati |
| `callbackStatusId` | Moslik uchun: `statusMappings["CALLBACK_REQUESTED"]` qiymati |
| `newLeadStatusId` | Chat yoki kiruvchi qo'ng'iroqdan yaratilgan yangi lid boshlanadigan bosqich (`statusMappings["NEW_LEAD"]`) |

Ssenariy `outcomeSchema`sida `targetStatusId` qaytarsa — u hammasidan ustun.

## `PUT /api/settings/crm` — Sozlamalarni saqlash

Butun obyekt almashtiriladi: yuborilmagan (yoki `null`) maydon tozalanadi va platforma qiymatiga qaytadi.
Har bir ID musbat butun son bo'lishi shart (aks holda `400 VALIDATION_FAILED`).
Dinamik `statusMappings` xaritasi yoki klassik maydonlar (`meetingBookedStatusId`, `promiseToPayStatusId` va h.k.) qabul qilinadi.

```json
{
  "aiEmployeeId": 252,
  "taskTypeId": 7,
  "statusMappings": {
    "MEETING_BOOKED": 15,
    "PROMISE_TO_PAY": 22,
    "NOT_INTERESTED": 19,
    "CALLBACK_REQUESTED": 12,
    "NEW_LEAD": 11
  }
}
```

Yoki klassik formatda (moslik uchun):
```json
{
  "aiEmployeeId": 252,
  "meetingBookedStatusId": 15,
  "promiseToPayStatusId": 22,
  "notInterestedStatusId": 19,
  "callbackStatusId": 12,
  "newLeadStatusId": 11,
  "taskTypeId": 7
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

Uysot yo'llari ([hujjat](https://apidoc.app.uysot.uz/docs/open-api/uysot-open-api)): `GET house/all`,
`POST flat/filter` (grant `CONTRACT:READ`), `POST employee/filter`, `GET pipe/all` (`LEAD:READ`),
`GET contract/{id}/monthly-payment` (`CONTRACT:READ`), `POST contract/filter` (`search` — raqam, mijoz ismi yoki telefon).

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
{ "accept": true, "data": [ { "id": 252, "fullName": "Dilshod Karimov", "phone": null, "departmentName": null } ] }
```

`phone` va `departmentName` Uysot `employee/filter` javobida yo'q — hozircha doim `null`.

```text
```

## `GET /api/crm/uysot/task-types` — Task turlari

```json
{ "accept": true, "data": [ { "id": 7, "name": "Qo'ng'iroq" }, { "id": 8, "name": "Uchrashuv" } ] }
```

## `GET /api/crm/uysot/houses` — Turar-joy majmualari

```json
{ "accept": true, "data": [ { "id": 7, "name": "Yunusobod Residence", "onSale": true, "address": "Yunusobod 12-kvartal",
                              "floors": 12, "handOverDate": "01.12.2027", "buildings": ["A blok", "B blok"] } ] }
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

`searchFlats`, `listResidentialComplexes`, `getContractBalance`, `getPaymentSchedule`, `getDebtSummary`,
`getClientProfile`, `getBookingStatus` — kod emas, integratsiya katalogidagi HTTP vositalar
([platform-integration-tools.md](platform-integration-tools.md), migratsiya `V23`–`V26`).
`getContractBalance` da shartnoma: model aytgan raqam → qo'ng'iroq faktidagi `contractNumber` → mijoz
telefoni (`x-fallback`); bir nechta chiqsa, model eng ko'p kechikkani haqida gapiradi.

`rememberFlatInterest` va `requestManagerCallback` — kod emas, agentning o'z `EXTRACT_VARIABLE` funksiyalari (migratsiya
`V25`, [ai-agents.md](ai-agents.md)): birinchisi `interestedFlat` ni qo'ng'iroq natijasiga va mijoz xotirasiga
(`remember: true`), ikkinchisi `managerCallbackRequested` / `managerCallbackReason` / `managerCallbackTime` ni
qo'ng'iroq natijasiga yozadi. Studio'da boshqa funksiyalar kabi tahrirlanadi.

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

---

## Kontakt sinxronizatsiyasi (CRM → kontaktlar) {#contact-sync}

Retell'dagi "Set up contact sync → Import contacts". Kompaniyaning ulangan CRM'idagi kontaktlar
har **5 daqiqada** platforma kontaktlariga (`contacts.md`) import qilinadi: faqat oxirgi
importdan keyin o'zgarganlari; birinchi marta yoki "full" so'ralganda — hammasi. Huquq:
o'qish — **INTEGRATION_READ**, o'zgartirish — **INTEGRATION_EDIT**.

Qo'llab-quvvatlanadi:
- **Bitrix24** — `crm.contact.list`, `DATE_MODIFY` bo'yicha, sahifada 50 ta.
- **amoCRM / Kommo** — `/api/v4/contacts`, `updated_at` bo'yicha, sahifada 250 ta.
- **Uysot** — Open API: `POST /v1/open-api/lead/filter` (`updatedTimestampFrom`, `UPDATED_TIMESTAMP ASC`), sahifada 20 ta,
  so'ng har bir lid uchun `GET /v1/open-api/lead/{id}` (ro'yxat qatorida telefon yo'q). **Bitta lid = bitta kontakt**:
  ism — lidning birinchi kontaktining ismi (bo'lmasa lid nomi), telefonlar — barcha kontaktlarniki, maydonlar —
  lid va kontakt custom field'lari (`crmField` = Uysot maydon id'si, `GET /v1/open-api/crm-field`). Open API
  cheklovi 60 so'rov/daqiqa va token qo'ng'iroqlar bilan umumiy, shuning uchun so'rovlar orasida 2 soniya kutiladi
  (≈ 28 lid/daqiqa) — katta bazaning birinchi to'liq importi soatlab davom etishi mumkin.

Qoidalar:
- Moslashtirish: avval shu CRM yozuvidan import qilingan kontakt (`contact_crm_link`), bo'lmasa —
  shu telefon raqamli kontakt, bo'lmasa — yangi kontakt.
- Telefon — yozuvning normallashtirib bo'ladigan birinchi raqami; yaroqli raqam bo'lmasa yozuv o'tkazib
  yuboriladi (`lastSkipped`). Mavjud kontaktning telefoni o'zgartirilmaydi.
- Ism — doim CRM'dan (Bitrix24: `NAME LAST_NAME`; amoCRM: `name`); bo'sh bo'lsa eskisi qoladi.
- `fieldMappings` — CRM maydoni → kompaniya kontakt maydoni (`contacts.md#contact-fields`). CRM'dagi bo'sh
  qiymat saqlangan qiymatni **o'chirmaydi**; kontakt maydoni turiga mos kelmagan qiymat tashlanadi.
  Ko'p qiymatli maydon (EMAIL, ro'yxat) vergul bilan birlashtiriladi.
- Import **hech qachon o'chirmaydi**. CRM'ni boshqasiga almashtirsangiz — keyingi run to'liq o'qiydi.
- Bitta run ko'pi bilan 3 daqiqa (va 100 000 yozuv) ishlaydi; tugamagan o'qish (`state.resume`) keyingi run'da
  (≤ 1 daqiqa) aynan to'xtagan sahifadan davom etadi. CRM xatosida ham sahifa saqlanadi va 5 daqiqadan keyin qayta o'qiladi.
- O'qish davomida CRM'da o'zgargan yozuv sahifalarni siljitishi mumkin (offset paginatsiya); juda kam hollarda
  bitta yozuv o'tkazib yuborilsa — keyingi o'zgarishida yoki "full" run'da import bo'ladi.

### `GET /api/settings/crm/contact-sync`

```json
{
  "enabled": true,
  "provider": "BITRIX24",
  "supported": true,
  "fieldMappings": [ { "crmField": "UF_CRM_1700000000", "contactField": "lead_status" },
                     { "crmField": "EMAIL", "contactField": "email" } ],
  "pushMappings": [ { "crmField": "UF_CRM_1700000001", "contactField": "last_call_summary" } ],
  "state": {
    "provider": "BITRIX24",
    "cursor": "2026-11-06T08:41:00Z",
    "resume": null,
    "nextRunAt": "2026-11-06T08:50:00Z",
    "lastRunAt": "2026-11-06T08:45:00Z",
    "lastStatus": "SUCCESS",
    "lastError": null,
    "lastCounts": { "created": 12, "updated": 40, "skipped": 3 }
  }
}
```

| Maydon | Izoh |
|---|---|
| `provider` | ulangan CRM (`BITRIX24` · `AMOCRM` · `KOMMO` · `UYSOT`), ulanmagan bo'lsa `null` |
| `supported` | shu CRM'dan kontakt import qilib bo'ladimi |
| `state.cursor` | importlangan eng yangi o'zgarish vaqti; `null` — keyingi run to'liq |
| `state.nextRunAt` | keyingi run vaqti; `null` — rejalashtirilmagan |
| `state.lastStatus` | `SUCCESS` · `FAILED` (`lastError` — sabab) · `NOT_CONNECTED` · `UNSUPPORTED`; `null` — hali ishlamagan |
| `state.resume` | tugamagan o'qish: `{ "since": ..., "pageToken": "3" }` — keyingi run shu sahifadan davom etadi; `null` — ochiq o'qish yo'q |
| `state.lastCounts` | oxirgi runda `created` / `updated` / `skipped` (yaratilgan / o'zgargan / o'tkazib yuborilgan) |
| `pushMappings` | kontakt maydoni → CRM maydoni, CRM'ga qaytarib yoziladi ([pastda](#contact-push)); bo'sh — hech narsa yozilmaydi |

Sozlama hech saqlanmagan bo'lsa: `enabled: false`, `fieldMappings: []`, `pushMappings: []`, `state` maydonlari `null`, `lastCounts` — nollar.

### `PUT /api/settings/crm/contact-sync`

Body: `{ "enabled": true, "fieldMappings": [ { "crmField": "...", "contactField": "..." } ], "pushMappings": [ ... ] }`
— hammasi almashtiriladi (`fieldMappings` / `pushMappings` `null` — bo'sh). O'chiq holatdan yoqilsa — run darhol rejalashtiriladi.
Javob — `GET` bilan bir xil.

| Holat | Kod |
|---|---|
| `enabled` yo'q | `400 VALIDATION_FAILED` |
| 50 tadan ko'p mapping | `400 CRM_CONTACT_SYNC_TOO_MANY_MAPPINGS` |
| `crmField` bo'sh yoki > 128 | `400 CRM_CONTACT_SYNC_CRM_FIELD_INVALID` |
| `contactField` kompaniya kontakt maydonlarida yo'q | `400 CRM_CONTACT_SYNC_CONTACT_FIELD_UNKNOWN` |
| bitta kontakt maydoni ikki marta | `400 CRM_CONTACT_SYNC_CONTACT_FIELD_TWICE` |
| `pushMappings` da bitta CRM maydoni ikki marta | `400 CRM_CONTACT_SYNC_CRM_FIELD_TWICE` |
| `pushMappings` da `crmField` = `PHONE` | `400 CRM_CONTACT_SYNC_PHONE_NOT_WRITABLE` |

### `GET /api/settings/crm/contact-sync/crm-fields`

Ulangan CRM'ning kontakt maydonlari (jonli o'qiladi), mapping ekrani uchun:

```json
[ { "id": "UF_CRM_1700000000", "label": "Lid holati", "type": "enumeration" },
  { "id": "EMAIL", "label": "E-mail", "type": "crm_multifield" } ]
```

`id` — `crmField` ga yoziladigan qiymat. Bitrix24: `crm.contact.fields` ning hammasi (standart va `UF_CRM_*`).
amoCRM/Kommo: custom field'lar, `id` — raqamli id (`fieldMappings` da maydon kodi, masalan `EMAIL`, ham ishlaydi).
Uysot: `GET /v1/open-api/crm-field` — lid va kontakt maydonlari (kontaktnikiga `label` oxirida "(kontakt)").

| Holat | Kod |
|---|---|
| CRM ulanmagan | `409 CRM_CONTACT_SYNC_NOT_CONNECTED` |
| CRM kontakt importini qo'llamaydi | `409 CRM_CONTACT_SYNC_UNSUPPORTED` |
| CRM rad etdi / javob bermadi | `502 CRM_CONTACT_SYNC_FAILED` |

### `POST /api/settings/crm/contact-sync/run?full=false`

Run'ni darhol (≤ 1 daqiqa ichida) rejalashtiradi; `full=true` — cursor tozalanadi, CRM'dagi barcha
kontaktlar qayta o'qiladi (Retell'dagi "Run full sync"). Run fon rejimida ishlaydi — natija keyin
`GET` ning `state` ida ko'rinadi. Javob — `GET` bilan bir xil.

| Holat | Kod |
|---|---|
| sinxronizatsiya o'chiq | `409 CRM_CONTACT_SYNC_DISABLED` |
| CRM ulanmagan | `409 CRM_CONTACT_SYNC_NOT_CONNECTED` |
| CRM qo'llamaydi | `409 CRM_CONTACT_SYNC_UNSUPPORTED` |

### CRM'ga qaytarib yozish (kontaktlar → CRM) {#contact-push}

Retell'dagi "Sync to CRM". CRM'dan import qilingan kontaktning maydonlari **qo'lda** (`PUT /api/contacts/{id}`)
yoki **qo'ng'iroqdan keyin** (extraction → kontakt maydonlari) o'zgarsa, `pushMappings` dagi qiymatlar o'sha CRM
yozuviga yoziladi (≈ 30 soniya ichida, fon rejimida). Importning o'zi o'zgartirgan qiymat qaytarib yozilmaydi.

- `enabled` (import) ga bog'liq emas: import o'chiq bo'lsa ham, avval import qilingan kontaktlar yoziladi.
- Faqat CRM'dan kelgan kontakt (`contact_crm_link`) yoziladi; CSV/qo'lda qo'shilgani — yo'q.
- Bo'sh kontakt qiymati CRM'dagi qiymatni **o'chirmaydi** (yuborilmaydi). Telefon hech qachon yozilmaydi.
- Kompaniya boshqa CRM'ga o'tgan bo'lsa, eski CRM yozuviga yozish bekor qilinadi.
- CRM xatosida qayta urinish: 1, 2, 4 … daqiqadan keyin (ko'pi 6 soat), 8 urinishdan keyin to'xtatiladi.
  Yozish davomida yana o'zgarish bo'lsa — keyin yana yoziladi.

Provayder bo'yicha:
- **Bitrix24** — `crm.contact.update`. `EMAIL` / `WEB` / `IM` (ko'p qiymatli) maydonga qiymat **yangi yozuv
  sifatida qo'shiladi**, faqat u yerda hali bo'lmasa (`crm.contact.get` bilan tekshiriladi).
- **amoCRM / Kommo** — `PATCH /api/v4/contacts/{id}`, `custom_fields_values`: raqamli `crmField` — `field_id`,
  boshqasi — `field_code`. Qiymat almashtiriladi.
- **Uysot** — `POST /v1/open-api/lead/{id}/field/list` (`[{ "id": <maydon id>, "value": "..." }]`), asinxron.
  Faqat **lid** maydonlari yoziladi; kontakt maydoni ("(kontakt)") Uysot tomonidan rad etiladi va xato sifatida qayta uriniladi.
  So'rovlar import bilan bir xil 2 soniyalik oraliqda.

Holat hozircha API'da ko'rsatilmaydi (xato serverda loglanadi).
