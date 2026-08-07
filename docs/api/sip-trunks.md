# SIP trunklar API

`uz.murodjon.uysotvoice.siptrunk` · rol: **ADMIN** (barcha endpoint)

Har bir kompaniya bir nechta chiquvchi PJSIP trunkga ega bo'lishi mumkin
(ROADMAP B.3), ulardan aynan bittasi — **default**. `AriService` qo'ng'iroq
boshlaganda (`PJSIP/<raqam>@<endpoint>`) o'sha kampaniyaning kompaniyasiga
tegishli default trunkni ishlatadi; agar kompaniyada yoqilgan default trunk
bo'lmasa — global `voice-agent.asterisk.trunk-endpoint`/`caller-id`ga qaytadi.

**Ikki rejim bor (report #7):**

- **Manual** — asl ROADMAP B.3 shakli. `pjsipEndpoint` — Asterisk tomonda
  allaqachon qo'lda sozlangan endpoint nomi (masalan `pjsip.conf`dagi
  `[trunk-endpoint]` bo'limi). Bu yerda faqat qaysi allaqachon-mavjud
  endpoint qaysi kompaniyaning qaysi qo'ng'irog'ida ishlatilishi boshqariladi
  — parol/login saqlanmaydi, konfiguratsiya generatsiya qilinmaydi.
- **Managed** (yangi, report #7) — haqiqiy SIP account login/paroli shu yerda
  kiritiladi (`host`/`sipUsername`/`sipPassword`). Backend o'zi
  `pjsipEndpoint`ni generatsiya qiladi (`trunk_<companyId>_<id>`),
  `siptrunk.service.PjsipConfigWriter` mos PJSIP bo'limlarini
  (`auth`/`registration`/`endpoint`/`aor`) generatsiya qilingan faylga
  yozadi (Asterisk uni `#tryinclude` bilan o'qiydi), va `agent.ami.AmiClient`
  orqali Asterisk'ga `res_pjsip.so`ni qayta yuklashni buyuradi — trunk
  darhol jonli bo'ladi, konteyner qayta ishga tushirilishi shart emas.
  **Talab:** `voice-agent.siptrunk.enabled`/`voice-agent.asterisk.ami.enabled`
  ikkalasi ham yoqilgan bo'lishi kerak (ikkalasi ham standart holatda
  o'chiq) — aks holda trunk qatori saqlanadi, lekin Asterisk'da haqiqatan
  ro'yxatdan o'tkazilmaydi.

Har bir so'rovda **ikkalasidan faqat bittasi** yuborilishi kerak:
`pjsipEndpoint` (manual) YOKI `host`+`sipUsername`+`sipPassword` (managed).
Ikkalasi ham yuborilsa yoki ikkalasi ham bo'sh bo'lsa — `400`.

Birinchi marta ishga tushirilganda (`SipTrunkBootstrap`), agar default
kompaniyada hali sip_trunk yozuvi bo'lmasa, mavjud
`voice-agent.asterisk.trunk-endpoint`/`caller-id` konfiguratsiyasidan avtomatik
bitta **manual** default trunk yaratiladi — eski, konfiguratsiyaga asoslangan
xatti-harakat o'zgarmaydi.

Umumiy javob shakli, xatolar va pagination konventsiyasi uchun
[README.md](README.md)ga qarang.

---

## `POST /api/sip-trunks` — yangi trunk

**Request body** (`CreateSipTrunkRequest`) — manual misol:

```json
{ "name": "Qo'lda sozlangan trunk", "pjsipEndpoint": "trunk-2", "callerId": "998712000001" }
```

...yoki managed misol:

```json
{
  "name": "Ikkinchi provayder",
  "host": "sip.provider.uz",
  "port": 5060,
  "sipUsername": "998712000002",
  "sipPassword": "haqiqiy-parol",
  "transport": "UDP",
  "callerId": "998712000001"
}
```

| Maydon | Rejim | Majburiymi | Izoh |
|---|---|---|---|
| `name` | ikkalasi | ✅ (`@NotBlank`) | — |
| `pjsipEndpoint` | manual | shart (agar `host` bo'lmasa) | `pjsip.conf`da mavjud endpoint nomi |
| `host` | managed | shart (agar `pjsipEndpoint` bo'lmasa) | SIP provayder hosti/domeni |
| `port` | managed | ❌ | bo'sh bo'lsa `5060` |
| `sipUsername` | managed | ✅ (agar `host` berilsa) | — |
| `sipPassword` | managed | ✅ (agar `host` berilsa) | **hech qachon javobda qaytmaydi**, faqat shifrlangan holda saqlanadi |
| `transport` | managed | ❌ | bo'sh bo'lsa `UDP`. Hozircha faqat `UDP` qo'llab-quvvatlanadi — `TCP`/`TLS` `400` bilan rad etiladi (Asterisk tomonda mos transport hali yo'q) |
| `callerId` | ikkalasi | ❌ | `null` bo'lsa global `voice-agent.asterisk.caller-id`ga qaytadi |

Kompaniyaning **birinchi** trunki avtomatik default bo'ladi (`isDefault: true`),
qolganlari boshida `isDefault: false` bilan yaratiladi.

**Response** (`SipTrunk`) — managed misolga javob:

```json
{
  "id": 2,
  "name": "Ikkinchi provayder",
  "pjsipEndpoint": "trunk_1_2",
  "callerId": "998712000001",
  "managed": true,
  "host": "sip.provider.uz",
  "port": 5060,
  "sipUsername": "998712000002",
  "transport": "UDP",
  "isDefault": false,
  "enabled": true,
  "createdAt": "2026-08-01T09:00:00Z"
}
```

Manual trunk uchun `managed: false`, `host`/`sipUsername`/`transport` —
`null`. `sipPassword` javobda **hech qachon** yo'q.

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

**Request body** (`UpdateSipTrunkRequest`) — `POST /api/sip-trunks` bilan bir
xil maydonlar (manual/managed rejim tanlovi bilan birga), plus `enabled`.
**`isDefault` bu yerda yo'q** — default holatni almashtirish uchun pastdagi
alohida endpointdan foydalaning.

```json
{ "name": "Ikkinchi provayder", "host": "sip.provider.uz", "sipUsername": "998712000002",
  "callerId": "998712000001", "enabled": true }
```

`sipPassword` — managed rejimda ham **ixtiyoriy**: bo'sh/berilmagan bo'lsa
trunkning joriy shifrlangan paroli saqlanib qoladi (nomini o'zgartirish yoki
`enabled`ni almashtirish uchun parolni qayta kiritish shart emas). Trunk
birinchi marta manual'dan managed'ga o'tkazilayotganda esa `sipPassword`
majburiy — saqlanadigan avvalgi parol yo'q.

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
{ "data": null, "message": "Cannot delete the default trunk — set another one as default first", "messageCode": "SIP_TRUNK_DEFAULT_DELETE_FORBIDDEN", "accept": false, "errors": null }
```
(`409`)

**Response** (muvaffaqiyatda, `SipTrunkDeleteResponse`):

```json
{ "id": 2, "deleted": true }
```

Managed trunk o'chirilsa, generatsiya qilingan PJSIP konfiguratsiyasidan ham
olib tashlanadi va Asterisk qayta yuklanadi (`PjsipConfigWriter`).
