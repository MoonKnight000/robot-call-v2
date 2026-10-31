# Telefon raqamlari (bozor va BYON)

`uz.murodjon.robotcallv2.phonenumber` · huquq: **SIP_TRUNK_READ** / **SIP_TRUNK_EDIT**; platforma
pulini boshqarish — **PLATFORM_ADMIN**

Platforma o'z raqamlar pulini (hovuzini) saqlaydi. Kompaniya bozordan raqam sotib oladi va unga
kanallar soni hamda kiruvchi yo'nalish (AI agent yoki Call Flow) belgilaydi. O'z SIP
provayderini ulamoqchi bo'lgan kompaniya ulanishni shu yerda sinab ko'radi; trunkning o'zi esa
[sip-trunks.md](sip-trunks.md) orqali yaratiladi.

Umumiy javob formati va xatolar uchun [README.md](README.md)ga qarang. Sxemalar:
`static/openapi/schemas/phoneNumber.yaml`.

---

## 1. Endpointlar

### Kompaniya — `/api/phone-numbers`

| Metod | Yo'l | Huquq | Vazifa |
|---|---|---|---|
| `POST` | `/api/phone-numbers/marketplace` | SIP_TRUNK_READ | Bozor: sotuvdagi (`AVAILABLE`) raqamlar sahifasi |
| `POST` | `/api/phone-numbers/purchase` | SIP_TRUNK_EDIT | Bozordan raqam sotib olish |
| `POST` | `/api/phone-numbers/byo` | SIP_TRUNK_EDIT | O'z SIP trunkidagi raqamni qo'shish (BYON) — §5.1 |
| `POST` | `/api/phone-numbers/list` | SIP_TRUNK_READ | Kompaniyaga biriktirilgan raqamlar sahifasi |
| `PUT` | `/api/phone-numbers/{id}/routing` | SIP_TRUNK_EDIT | Taxallus, kiruvchi yo'nalish va avto-uzaytirishni o'zgartirish |
| `PUT` | `/api/phone-numbers/{id}/trunk` | SIP_TRUNK_EDIT | BYON raqamni boshqa o'z trunkiga o'tkazish — §6.1 |
| `DELETE` | `/api/phone-numbers/{id}` | SIP_TRUNK_EDIT | Raqamni bo'shatish — u bozorga qaytadi |
| `POST` | `/api/phone-numbers/test-connection` | SIP_TRUNK_EDIT | SIP hostga ulanishni sinash (BYON) |

### Platforma — `/api/platform/phone-numbers`

| Metod | Yo'l | Huquq | Vazifa |
|---|---|---|---|
| `POST` | `/api/platform/phone-numbers` | PLATFORM_ADMIN | Pulga bitta raqam qo'shish |
| `POST` | `/api/platform/phone-numbers/batch` | PLATFORM_ADMIN | Pulga ko'p raqamni birdan qo'shish |
| `POST` | `/api/platform/phone-numbers/list` | PLATFORM_ADMIN | Puldagi barcha raqamlar (har qanday kompaniyaniki ham) |
| `DELETE` | `/api/platform/phone-numbers/{id}` | PLATFORM_ADMIN | Raqamni puldan butunlay o'chirish |

---

## 2. Raqamning hayot sikli

```
platforma qo'shadi ──▶ AVAILABLE ──purchase──▶ ASSIGNED ──DELETE /{id}──▶ AVAILABLE
                          │                                                  
                          └── DELETE /api/platform/phone-numbers/{id} (faqat AVAILABLE bo'lsa)

kompaniya qo'shadi (POST /byo) ──▶ ASSIGNED (BYO_SIP_TRUNK) ──DELETE /{id}──▶ o'chiriladi
```

- **BYON** (`type = BYO_SIP_TRUNK`): kompaniyaning o'z trunkidagi raqam. Bozorga hech qachon
  chiqmaydi: `DELETE /{id}` uni butunlay o'chiradi.
- **Ko'chirilgan raqamlar (V31):** migratsiya trunk `callerId` lari, caller ID pool raqamlari va
  `inbound_route` DID'larini BYON qatorlar sifatida yozdi (DID'ning agenti/Call Flow'i bilan).
  Trunki aniq bo'lmaganlarida `providerTrunkId = null` — ularni `withoutTrunk: true` filtri bilan
  topib, `PUT /{id}/trunk` bilan biriktiring. Manba jadvallar o'zgarmagan.

- **Sotib olish:** `status = ASSIGNED`, `companyId` — xaridor, `purchasedAt = hozir`,
  `expiresAt = hozir + 30 kun`. `nickname` berilmasa, raqamning o'zi yoziladi; `autoRenew`
  berilmasa — `true`.
- **Bo'shatish:** raqam pulga qaytadi va kompaniyaga tegishli hamma narsa tozalanadi:
  `companyId`, `nickname`, `inboundAgentId`, `inboundCallFlowId`, `purchasedAt`,
  `expiresAt` → `null`, `allocatedChannels = 0`, `autoRenew = false`.
- Boshqa kompaniyaning raqami, shuningdek pulga qaytgan raqam ham kompaniya endpointlarida
  `404 PHONE_NUMBER_NOT_FOUND` qaytaradi.

> **Hozircha faqat saqlanadi:**
> - Xarid uchun balansdan pul yechilmaydi.
> - `expiresAt`/`autoRenew` bo'yicha avtomatik uzaytiruvchi job yo'q.
>
> Ekranda buni "to'landi" deb ko'rsatmang.

**Kiruvchi slot (`inboundAgentId`/`inboundCallFlowId`).** Raqamga [marshrut](inbound-routes.md)
bo'lmasa, kiruvchi qo'ng'iroqqa shu slot javob beradi — soat cheklovisiz. Marshrut bo'lsa, u
ustun (soatlar, after-hours, uning agenti). Ikkalasi ham bo'sh bo'lsa qo'ng'iroq qabul qilinmaydi.

**Chiquvchi (`providerTrunkId`).** BYON raqam kampaniyaning `numbers.fromNumberId` yoki caller ID
puli orqali ishlatilganda qo'ng'iroq shu trunkdan ketadi ([campaigns.md](campaigns.md#raqamlar--qaysi-raqamdan-qo'ng'iroq-qilinadi)).
Bozor raqamining trunki platformaniki — u chiquvchi qo'ng'iroqda hali ishlatilmaydi.

---

## 3. Filtr — `PhoneNumberFilter`

Uchala ro'yxat endpointi (`marketplace`, `list`, platforma `list`) bir xil body qabul qiladi:

```json
{
  "page": 0,
  "size": 20,
  "orders": { "MONTHLY_FEE": "ASC" },
  "search": "998712",
  "category": "GOLD",
  "status": "AVAILABLE",
  "type": "MARKETPLACE_PURCHASED"
}
```

| Maydon | Izoh |
|---|---|
| `page`, `size` | `size` 1..500 |
| `orders` | Kalitlar: `ID`, `PHONE_NUMBER`, `CATEGORY`, `MONTHLY_FEE`, `STATUS`, `CREATED_AT`. Berilmasa — `CREATED_AT DESC` |
| `search` | Raqam yoki taxallus ichida qidiradi (katta-kichik harf farqi yo'q) |
| `category` | `STANDARD` · `SILVER` · `GOLD` · `PLATINUM` |
| `status` | `AVAILABLE` · `ASSIGNED`. `marketplace` doim faqat `AVAILABLE` ni qaytaradi |
| `type` | `MARKETPLACE_PURCHASED` · `BYO_SIP_TRUNK` |
| `withoutTrunk` | `true` — faqat `providerTrunkId = null` raqamlar; `null`/`false` toraytirmaydi |

Javob — `PageableData<PhoneNumberRow>`.

---

## 4. `PhoneNumberRow`

```json
{
  "id": 1,
  "companyId": 10,
  "phoneNumber": "+998712000101",
  "nickname": "Asosiy savdo liniyasi",
  "type": "MARKETPLACE_PURCHASED",
  "category": "GOLD",
  "status": "ASSIGNED",
  "monthlyFee": 50000.00,
  "pricePerChannel": 5000.00,
  "maxSupportedChannels": 20,
  "allocatedChannels": 5,
  "totalMonthlyCost": 75000.00,
  "providerTrunkId": 3,
  "inboundAgentId": 1,
  "inboundAgentName": "Dilnavoz",
  "inboundCallFlowId": null,
  "inboundCallFlowName": null,
  "autoRenew": true,
  "purchasedAt": "2026-10-29T09:00:00Z",
  "expiresAt": "2026-11-28T09:00:00Z",
  "createdAt": "2026-10-01T12:00:00Z"
}
```

| Maydon | Izoh |
|---|---|
| `companyId` | Pulda turgan raqamda `null` |
| `totalMonthlyCost` | `monthlyFee + pricePerChannel × allocatedChannels` |
| `providerTrunkId` | Raqam turgan SIP trunk. Bozor raqamida — platformaning trunki (kompaniyaning trunk ro'yxatida yo'q); BYON'da — kompaniyaning o'z trunki; `null` — hali tanlanmagan |
| `inboundAgentName`, `inboundCallFlowName` | Faqat kompaniya `list` va `purchase`/`routing` javoblarida to'ldiriladi; bozor va platforma ro'yxatlarida doim `null` |
| `maxSupportedChannels` | Shu raqamda sotib olish mumkin bo'lgan kanallar chegarasi |

---

## 5. Sotib olish — `POST /api/phone-numbers/purchase`

```json
{
  "poolNumberId": 1,
  "allocatedChannels": 5,
  "nickname": "Mijozlar qo'llab-quvvatlash",
  "inboundAgentId": 1,
  "inboundCallFlowId": null,
  "autoRenew": true
}
```

| Maydon | Majburiy | Izoh |
|---|---|---|
| `poolNumberId` | ha | Bozordagi qatorning `id` si |
| `allocatedChannels` | ha | `1..maxSupportedChannels` |
| `nickname` | — | Bo'sh bo'lsa, raqamning o'zi |
| `inboundAgentId` | — | Kompaniyaning agenti bo'lishi shart |
| `inboundCallFlowId` | — | Kompaniyaning **e'lon qilingan** Call Flow'i bo'lishi shart |
| `autoRenew` | — | Default `true` |

Javob — `PhoneNumberRow`.

| Xato | Qachon |
|---|---|
| `404 PHONE_NUMBER_NOT_FOUND` | `poolNumberId` yo'q |
| `409 PHONE_NUMBER_ALREADY_PURCHASED` | Raqam allaqachon `ASSIGNED` |
| `400 CONCURRENCY_LIMIT_OUT_OF_RANGE` | `allocatedChannels` chegaradan tashqarida (argument — `maxSupportedChannels`) |
| `404 AI_AGENT_NOT_FOUND` | Agent yo'q yoki boshqa kompaniyaniki |
| `404 CALL_FLOW_NOT_FOUND` / `409 CALL_FLOW_NOT_PUBLISHED` | Call Flow yo'q yoki e'lon qilinmagan |

---

## 5.1. O'z trunkidagi raqam (BYON) — `POST /api/phone-numbers/byo`

```json
{ "phoneNumber": "+998712000201", "trunkId": 3, "nickname": "Savdo liniyasi" }
```

| Maydon | Majburiy | Izoh |
|---|---|---|
| `phoneNumber` | ha | Normallashtiriladi; noto'g'ri bo'lsa `400 PHONE_INVALID` |
| `trunkId` | ha | Kompaniyaning o'z trunki |
| `nickname` | — | ≤ 120 belgi; bo'sh bo'lsa, raqamning o'zi |

Javob — `PhoneNumberRow`: `type = BYO_SIP_TRUNK`, `status = ASSIGNED`, narxlar `0`,
`autoRenew = false`, `purchasedAt`/`expiresAt` — `null`. Kiruvchi agent/Call Flow keyin §6 orqali
beriladi.

| Xato | Qachon |
|---|---|
| `400 PHONE_INVALID` | Raqam noto'g'ri |
| `404 SIP_TRUNK_NOT_FOUND` | Trunk yo'q yoki boshqa kompaniyaniki |
| `409 PHONE_NUMBER_ALREADY_EXISTS` | Raqam allaqachon tizimda (bozorda, boshqa kompaniyada yoki o'zingizda) |

---

## 6. Yo'naltirish — `PUT /api/phone-numbers/{id}/routing`

```json
{
  "nickname": "Asosiy qo'llab-quvvatlash",
  "inboundAgentId": 2,
  "inboundCallFlowId": null,
  "autoRenew": false
}
```

- `inboundAgentId` va `inboundCallFlowId` **har safar to'liq yoziladi**: yuborilmagani (yoki
  `null`) tozalanadi. Agentdan Call Flow'ga o'tishda `inboundAgentId: null` va
  `inboundCallFlowId: 5` yuboring.
- `nickname` va `autoRenew` yuborilmasa, eskisi qoladi.
- Kanallar soni bu yerda o'zgarmaydi — u sotib olishda belgilanadi.

Xatolar — §5 dagi agent va Call Flow xatolari, hamda `404 PHONE_NUMBER_NOT_FOUND`.

---

## 6.1. Trunkni almashtirish — `PUT /api/phone-numbers/{id}/trunk`

```json
{ "trunkId": 4 }
```

Faqat BYON raqam uchun. Javob — `PhoneNumberRow`.

| Xato | Qachon |
|---|---|
| `404 PHONE_NUMBER_NOT_FOUND` | Raqam yo'q yoki kompaniyaniki emas |
| `404 SIP_TRUNK_NOT_FOUND` | Trunk yo'q yoki boshqa kompaniyaniki |
| `409 PHONE_NUMBER_TRUNK_PLATFORM_MANAGED` | Bozordan olingan raqam — uning trunkini platforma belgilaydi |

---

## 7. Bo'shatish — `DELETE /api/phone-numbers/{id}`

Javob — `ResponseData<null>`. Bozor raqami bozorga qaytadi, BYON raqam butunlay o'chiriladi (§2).
Kompaniyaga tegishli bo'lmasa — `404 PHONE_NUMBER_NOT_FOUND`.

---

## 8. SIP ulanishini sinash — `POST /api/phone-numbers/test-connection`

O'z SIP provayderingizni ulashdan oldin shu serverdan hostga yetib bo'lishini tekshiradi.

```json
{ "host": "sip.provider.uz", "port": 5060, "transport": "TCP", "username": null, "password": null }
```

| Maydon | Izoh |
|---|---|
| `host` | Majburiy. Ichki (private, loopback, link-local) manzilga hal bo'ladigan host rad etiladi — SSRF himoyasi |
| `port` | Default `5060` |
| `transport` | `TCP` (default) yoki `UDP`. `TLS` ham TCP ulanish sifatida sinaladi |
| `username`, `password` | Hozircha sinovda ishlatilmaydi |

**Javob** (`TestSipConnectionResponse`) har doim `200` bilan qaytadi — muvaffaqiyatsizlik
xato emas, natija:

```json
{ "connected": true, "roundTripMs": 45, "status": "CONNECTED", "details": "TCP connection established with sip.provider.uz:5060" }
```

| `status` | Ma'nosi |
|---|---|
| `CONNECTED` | TCP ulanish 3 soniya ichida o'rnatildi |
| `REACHABLE` | UDP: `OPTIONS` paketi yuborildi. UDP'da javob kutilmaydi, shuning uchun bu hostning tirikligini **kafolatlamaydi** |
| `FAILED` | Ulanib bo'lmadi, host ichki manzil yoki nom topilmadi — sababi `details` da |

---

## 9. Platforma: pulni boshqarish

### `POST /api/platform/phone-numbers`

```json
{
  "phoneNumber": "+998712000101",
  "category": "GOLD",
  "monthlyFee": 50000,
  "pricePerChannel": 5000,
  "maxSupportedChannels": 20,
  "providerTrunkId": 3
}
```

| Maydon | Majburiy | Default |
|---|---|---|
| `phoneNumber` | ha | — (normallashtiriladi; noto'g'ri bo'lsa `400 PHONE_INVALID`) |
| `monthlyFee` | ha | — |
| `category` | — | `STANDARD` |
| `pricePerChannel` | — | `0` |
| `maxSupportedChannels` | — | `10`; `< 1` bo'lsa `400 VALIDATION_FAILED` |
| `providerTrunkId` | — | `null` |

Javob — `PhoneNumberRow` (`status = AVAILABLE`, `type = MARKETPLACE_PURCHASED`). Raqam allaqachon
pulda bo'lsa — `409 PHONE_NUMBER_ALREADY_EXISTS`.

### `POST /api/platform/phone-numbers/batch`

Body — `AddPoolNumberRequest[]`. Javob — **nechta raqam qo'shilgani** (`ResponseData<number>`).

Bitta yomon qator butun to'plamni yiqitmaydi. Quyidagilar jimgina o'tkazib yuboriladi:
noto'g'ri raqam, `maxSupportedChannels < 1`, to'plam ichida takrorlangan raqam va pulda
allaqachon bor raqam. Qolganlari bitta tranzaksiyada yoziladi. Nechtasi tushib qolganini
bilish uchun javobni yuborilgan qatorlar soni bilan solishtiring.

### `DELETE /api/platform/phone-numbers/{id}`

Faqat `AVAILABLE` raqam o'chiriladi. Kompaniyaga biriktirilgan bo'lsa —
`409 PHONE_NUMBER_ALREADY_PURCHASED` (avval kompaniya uni bo'shatishi kerak). Topilmasa —
`404 PHONE_NUMBER_NOT_FOUND`.
