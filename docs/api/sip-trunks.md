# SIP trunklar API

`uz.murodjon.uysotvoice.siptrunk` · rol: **ADMIN** (barcha endpoint)

Har bir kompaniya bir nechta chiquvchi PJSIP trunkga ega bo'lishi mumkin
(ROADMAP B.3), ulardan aynan bittasi — **default**. `AriService` qo'ng'iroq
boshlaganda (`PJSIP/<raqam>@<endpoint>`) o'sha kampaniyaning kompaniyasiga
tegishli default trunkni ishlatadi; agar kompaniyada yoqilgan default trunk
bo'lmasa — global `voice-agent.asterisk.trunk-endpoint`/`caller-id`ga qaytadi.

**Muhim:** bu API `pjsip.conf`ni o'zi boshqarmaydi — `pjsipEndpoint` Asterisk
tomonda allaqachon sozlangan endpoint nomi bo'lishi shart (masalan
`pjsip.conf`dagi `[trunk-endpoint]` bo'limi). Bu yerda faqat qaysi
allaqachon-mavjud endpoint qaysi kompaniyaning qaysi qo'ng'irog'ida
ishlatilishini boshqarasiz.

Birinchi marta ishga tushirilganda (`SipTrunkBootstrap`), agar default
kompaniyada hali sip_trunk yozuvi bo'lmasa, mavjud
`voice-agent.asterisk.trunk-endpoint`/`caller-id` konfiguratsiyasidan avtomatik
bitta default trunk yaratiladi — eski, konfiguratsiyaga asoslangan xatti-harakat
o'zgarmaydi.

Umumiy javob shakli, xatolar va pagination konventsiyasi uchun
[README.md](README.md)ga qarang.

---

## `POST /api/sip-trunks` — yangi trunk

**Request body** (`CreateSipTrunkRequest`):

```json
{ "name": "Ikkinchi trunk", "pjsipEndpoint": "trunk-2", "callerId": "998712000001" }
```

| Maydon | Majburiymi | Izoh |
|---|---|---|
| `name` | ✅ (`@NotBlank`) | — |
| `pjsipEndpoint` | ✅ (`@NotBlank`) | `pjsip.conf`da mavjud endpoint nomi |
| `callerId` | ❌ | `null` bo'lsa global `voice-agent.asterisk.caller-id`ga qaytadi |

Kompaniyaning **birinchi** trunki avtomatik default bo'ladi (`isDefault: true`),
qolganlari boshida `isDefault: false` bilan yaratiladi.

**Response** (`SipTrunk`):

```json
{
  "id": 2,
  "name": "Ikkinchi trunk",
  "pjsipEndpoint": "trunk-2",
  "callerId": "998712000001",
  "isDefault": false,
  "enabled": true,
  "createdAt": "2026-08-01T09:00:00Z"
}
```

---

## `POST /api/sip-trunks/list` — ro'yxat

Body — `SipTrunkFilter` (`page`/`size`/`orders` — README §3ga qarang, qo'shimcha
filtr maydoni yo'q). Saralanadigan ustunlar: `ID`, `NAME`, `IS_DEFAULT`,
`ENABLED`, `CREATED_AT`. Standart: `NAME ASC`.

Javob — `PageableData<SipTrunk>` (`SipTrunk` shakli yuqorida).

---

## `GET /api/sip-trunks/{id}` — bitta trunk

**Response** — `SipTrunk`. Topilmasa `404`.

---

## `PUT /api/sip-trunks/{id}` — yangilash

**Request body** (`UpdateSipTrunkRequest`) — **`isDefault` bu yerda yo'q**,
default holatni almashtirish uchun pastdagi alohida endpointdan foydalaning:

```json
{ "name": "Ikkinchi trunk", "pjsipEndpoint": "trunk-2", "callerId": "998712000001", "enabled": true }
```

Javob — yangilangan `SipTrunk`.

---

## `POST /api/sip-trunks/{id}/default` — default qilib belgilash

Body yo'q. `id`ni kompaniyaning default trunkiga aylantiradi, avvalgi default
trunkni avtomatik oddiy holatga qaytaradi (bir vaqtda faqat bitta default
bo'lishi bazada `UNIQUE INDEX` bilan ta'minlangan).

**Response** — yangilangan `SipTrunk` (`isDefault: true`).

---

## `DELETE /api/sip-trunks/{id}` — o'chirish

Default trunk **o'chirilmaydi** — avval boshqa trunkni default qilib
belgilang, keyin o'chiring:

```json
{ "data": null, "message": "Cannot delete the default trunk — set another one as default first", "accept": false, "errors": null }
```
(`409`)

**Response** (muvaffaqiyatda, `SipTrunkDeleteResponse`):

```json
{ "id": 2, "deleted": true }
```
