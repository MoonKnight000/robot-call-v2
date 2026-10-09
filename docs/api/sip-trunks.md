# SIP trunklar API

`uz.murodjon.robotcallv2.siptrunk` · huquq: **SIP_TRUNK_READ** / **SIP_TRUNK_EDIT**

Har bir kompaniya bir nechta chiquvchi PJSIP trunkga ega bo'lishi mumkin (ROADMAP B.3), ulardan aynan bittasi — **default** (standart).

---

## 🔀 SIP Trunklarni taqsimlash va yo'naltirish qoidalari

1. **Kampaniyalar (Ommaviy dialer)**:
   - Kampaniya yaratishda yoki tahrirlashda `sipTrunkIds: [1, 2, 3]` orqali bir nechta trunklarni biriktirish mumkin.
   - Agar `sipTrunkIds` bo'sh qoldirilsa (`null` yoki `[]`), tizim kompaniyaning **barcha yoqilgan (enabled) trunklari** bo'yicha qo'ng'iroqlarni navbatma-navbat (Round-Robin) teng taqsimlaydi.
   - Agar bir nechta aniq trunklar tanlansa, qo'ng'iroqlar faqat o'sha tanlangan trunklar o'rtasida Round-Robin taqsimlanadi.
   - Agar bitta trunk tanlansa, barcha nishonlarga faqat shu trunk orqali chiqiladi.
   - **Failover (`TrunkHealth`)**: AMI orqali registratsiyasi `Registered` bo'lmagan trunk
     (`TrunkRegistrationMonitor`, `AMI_ENABLED=true` kerak) yoki ketma-ket 5 ta qo'ng'irog'i
     `CARRIER_REJECTED`/`FAILED` bilan tugagan trunk 5 daqiqaga rotatsiyadan chiqariladi,
     qolganlari yukni oladi; bitta muvaffaqiyatli ulanish hisoblagichni nolga tushiradi.
     Hamma trunk yaroqsiz bo'lsa — baribir hammasi orqali teriladi (urinish ko'rinsin).
     Yiqilgan trunkdagi urinish `FAILED` retry (15 min) bilan boshqa trunkda qayta teriladi.
2. **Qo'lda / Sinov qo'ng'iroqlari (`/api/calls` va `/api/calls/test`)**:
   - So'rovda `sipTrunkId` parametri orqali qo'ng'iroq aynan qaysi SIP liniyadan chiqishi aniq ko'rsatilishi mumkin.
   - Agar ko'rsatilmasa — kompaniyaning standart (default) trunki ishlatiladi.
3. **Qo'ng'iroqlar tarixi va Audit**:
   - Har bir qo'ng'iroq qaysi trunk orqali amalga oshirilgani `CallTechnical` jadvalida va hisobotlarda (`GET /api/reports/calls`, `GET /api/calls/live`) `trunk` maydoni orqali aniq ko'rinadi.

---

## Ikki rejim (Manual & Managed):

- **Manual** — asl ROADMAP B.3 shakli. `pjsipEndpoint` — Asterisk tomonda allaqachon qo'lda sozlangan endpoint nomi (masalan `pjsip.conf`dagi `[trunk-endpoint]` yoki `[600]` bo'limi). Bu yerda faqat qaysi allaqachon-mavjud endpoint qaysi kompaniyaning qaysi qo'ng'irog'ida ishlatilishi boshqariladi — parol/login saqlanmaydi, konfiguratsiya generatsiya qilinmaydi.
- **Managed** — haqiqiy SIP account login/paroli shu yerda kiritiladi (`host`/`sipUsername`/`sipPassword`). Backend o'zi `pjsipEndpoint`ni generatsiya qiladi (`trunk_<companyId>_<id>`), `siptrunk.service.PjsipConfigWriter` mos PJSIP bo'limlarini (`auth`/`registration`/`endpoint`/`aor`) generatsiya qilingan faylga yozadi (Asterisk uni `#tryinclude` bilan o'qiydi), va `agent.ami.AmiClient` orqali Asterisk'ga `res_pjsip.so`ni qayta yuklashni buyuradi — trunk darhol jonli bo'ladi, konteyner qayta ishga tushirilishi shart emas.
  **HD Voice / Codec sozlash:** Har bir trunk uchun alohida audio kodeklar ro'yxati (List) belgilanishi mumkin (`codecs: ["g722", "opus", "ulaw", "alaw"]`).
  **Talab:** `voice-agent.siptrunk.enabled`/`voice-agent.asterisk.ami.enabled` ikkalasi ham yoqilgan bo'lishi kerak — aks holda trunk qatori saqlanadi, lekin Asterisk'da haqiqatan ro'yxatdan o'tkazilmaydi.

Har bir so'rovda **ikkalasidan faqat bittasi** yuborilishi kerak:
`pjsipEndpoint` (manual) YOKI `host`+`sipUsername`+`sipPassword` (managed).
Ikkalasi ham yuborilsa yoki ikkalasi ham bo'sh bo'lsa — `400`.

**Manual endpointni faqat platform admin (`PLATFORM_ADMIN`) o'rnatadi yoki o'zgartiradi.**
Manual nom to'g'ridan-to'g'ri `PJSIP/<raqam>@<nom>` ga qo'yiladi va Asterisk biladigan istalgan
endpointni — platforma trunkini yoki boshqa kompaniyaning `trunk_<companyId>_<id>` endpointini —
ko'rsatishi mumkin edi. Oddiy foydalanuvchi uchun: yangi manual trunk yaratish yoki `pjsipEndpoint`ni
o'zgartirish → `403 SIP_TRUNK_MANUAL_ENDPOINT_FORBIDDEN`. Admin sozlagan manual trunkning boshqa
maydonlarini (nom, `callerId`, `codecs`, `enabled`) `pjsipEndpoint`ni o'zgartirmasdan tahrirlash mumkin.

Barcha maydonlar tekshiriladi, chunki ular dial string'ga, hamma kompaniyalar uchun umumiy
`pjsip_trunks.conf` fayliga yoki AMI buyrug'iga tushadi (qator uzilishi boshqa bo'lim/buyruq qo'shib yuborardi).

Birinchi marta ishga tushirilganda (`SipTrunkBootstrap`), agar default kompaniyada hali sip_trunk yozuvi bo'lmasa, mavjud `voice-agent.asterisk.trunk-endpoint`/`caller-id` konfiguratsiyasidan avtomatik bitta **manual** default trunk yaratiladi.

---

## `POST /api/sip-trunks` — yangi trunk

**Request body** (`CreateSipTrunkRequest`) — manual misol:

```json
{
  "name": "Qo'lda sozlangan trunk",
  "pjsipEndpoint": "trunk-2",
  "callerId": "998712000001",
  "codecs": ["g722", "ulaw", "alaw"]
}
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
  "codecs": ["g722", "opus", "ulaw", "alaw"],
  "callerId": "998712000001"
}
```

| Maydon | Rejim | Majburiymi | Izoh |
|---|---|---|---|
| `name` | ikkalasi | ✅ (`@NotBlank`) | — |
| `pjsipEndpoint` | manual | shart (agar `host` bo'lmasa) | `pjsip.conf`da mavjud endpoint nomi. Faqat harf, raqam, `_`, `-` (≤ 64). Faqat `PLATFORM_ADMIN` |
| `host` | managed | shart (agar `pjsipEndpoint` bo'lmasa) | SIP provayder hosti/domeni yoki IPv4 (harf, raqam, `.`, `-`) |
| `port` | managed | ❌ | bo'sh bo'lsa `5060` |
| `sipUsername` | managed | ✅ (agar `host` berilsa) | Faqat harf, raqam, `.` `_` `+` `-` (≤ 64) |
| `sipPassword` | managed | ✅ (agar `host` berilsa) | **hech qachon javobda qaytmaydi**, faqat shifrlangan holda saqlanadi. Qator uzilishi va boshqa boshqaruv belgilari taqiqlangan |
| `transport` | managed | ❌ | bo'sh bo'lsa `UDP`. Hozircha faqat `UDP` qo'llab-quvvatlanadi |
| `codecs` | ikkalasi | ❌ | `List<String>`. Bo'sh bo'lsa `["alaw", "ulaw"]`. Ruxsat etilganlar: `ulaw`, `alaw`, `g722`, `g729`, `gsm`, `opus`, `ilbc` (katta-kichik harf farqi yo'q). HD voice uchun `["g722", "opus", "ulaw", "alaw"]` |
| `callerId` | ikkalasi | ❌ | Telefon raqam (`998712000001`, `+998712000001`). `null`/bo'sh bo'lsa global `voice-agent.asterisk.caller-id`ga qaytadi |
| `maxCps` | ikkalasi | ❌ | Provayder qabul qiladigan sekundiga qo'ng'iroq soni (1–100). Ilova shu trunk orqali bundan tez terimaydi — ortiqchasi keyingi sekundni kutadi (butun klaster bo'yicha). `null` bo'lsa `voice-agent.dialer.default-trunk-cps` (default `2`). Chegaradan tashqari qiymat — `400 VALIDATION_FAILED`. `PUT` da yuborilmasa `null` bo'ladi |

**Xatolar** (`POST` va `PUT` uchun):

| Status | `messageCode` | Qachon |
|---|---|---|
| `400` | `SIP_TRUNK_ENDPOINT_INVALID` | `pjsipEndpoint` formati noto'g'ri |
| `400` | `SIP_TRUNK_HOST_INVALID` | `host` formati noto'g'ri |
| `400` | `SIP_TRUNK_USERNAME_INVALID` | `sipUsername` formati noto'g'ri |
| `400` | `SIP_TRUNK_PASSWORD_INVALID` | `sipPassword` ichida qator uzilishi / boshqaruv belgisi |
| `400` | `SIP_TRUNK_CODEC_UNSUPPORTED` | ro'yxatda yo'q kodek |
| `400` | `SIP_TRUNK_CALLER_ID_INVALID` | `callerId` telefon raqam emas |
| `403` | `SIP_TRUNK_MANUAL_ENDPOINT_FORBIDDEN` | admin bo'lmagan foydalanuvchi manual endpoint o'rnatmoqchi / o'zgartirmoqchi |

Kompaniyaning **birinchi** trunki avtomatik default bo'ladi (`isDefault: true`), qolganlari boshida `isDefault: false` bilan yaratiladi.

**Response** (`SipTrunk`):

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
  "codecs": [
    "g722",
    "opus",
    "ulaw",
    "alaw"
  ],
  "maxCps": 2,
  "isDefault": false,
  "enabled": true,
  "createdAt": "2026-08-01T09:00:00Z"
}
```

---

## `POST /api/sip-trunks/list` — ro'yxat

Body — `SipTrunkFilter` (`page`/`size`/`orders`). Saralanadigan ustunlar: `ID`, `NAME`, `IS_DEFAULT`, `ENABLED`, `CREATED_AT`. Standart: `NAME ASC`.

Javob — `PageableData<SipTrunk>`.

---

## `GET /api/sip-trunks/{id}` — bitta trunk

**Response** — `SipTrunk`. Topilmasa `404`.

---

## `GET /api/sip-trunks/{id}/status` — bitta trunkning jonli holati

Asterisk AMI / PJSIP orqali ro'yxatdan o'tish (registration) va endpoint faolligini jonli tekshiradi.

**Response** (`SipTrunkStatus`):

```json
{
  "id": 2,
  "name": "Ikkinchi provayder",
  "pjsipEndpoint": "trunk_1_2",
  "managed": true,
  "enabled": true,
  "isDefault": false,
  "status": "REGISTERED",
  "isOnline": true,
  "details": "Muvaffaqiyatli ro'yxatdan o'tgan (sip.provider.uz:5060)",
  "checkedAt": "2026-09-01T11:00:00Z"
}
```

Holatlar (`status`):
- `REGISTERED` / `ONLINE` — Asteriskda faol va ro'yxatdan o'tgan (`isOnline: true`);
- `UNREGISTERED` / `TRYING` — Ro'yxatdan o'tish kutilmoqda;
- `REJECTED` — Login yoki parol noto'g'ri;
- `UNAVAILABLE` / `NOT_FOUND` — Endpoint oflayn yoki mavjud emas;
- `DISABLED` — Trunk tizimda o'chirilgan (`isOnline: false`).

---

## `GET /api/sip-trunks/status` — barcha trunklarning jonli holati

Kompaniyaning barcha SIP trunk/telefonlari uchun jonli ro'yxatdan o'tish va ulanish holatlarini qaytaradi (`List<SipTrunkStatus>`).

---

## `PUT /api/sip-trunks/{id}` — yangilash

**Request body** (`UpdateSipTrunkRequest`):
```json
{
  "name": "Ikkinchi provayder",
  "host": "sip.provider.uz",
  "sipUsername": "998712000002",
  "callerId": "998712000001",
  "codecs": [
    "g722",
    "opus",
    "ulaw",
    "alaw"
  ],
  "maxCps": 2,
  "enabled": true
}
```

---

## `POST /api/sip-trunks/{id}/default` — default qilib belgilash

Body yo'q. `id`ni kompaniyaning default trunkiga aylantiradi, avvalgi default trunkni avtomatik oddiy holatga qaytaradi.

---

## `DELETE /api/sip-trunks/{id}` — o'chirish

Default trunk o'chirilmaydi — avval boshqa trunkni default qilib belgilang, keyin o'chiring.
