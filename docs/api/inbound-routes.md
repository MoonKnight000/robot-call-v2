# Kiruvchi qo'ng'iroq marshrutlash API

`uz.murodjon.uysotvoice.inbound` · rol: **OPERATOR** (barcha endpoint — ADMIN ham kiradi, rol ierarxiyasi bo'yicha)

Qaysi DID (dialangan) raqamga qo'ng'iroq qilinsa, qaysi ssenariy va tilda javob
berilishini belgilaydi (ROADMAP C.1). `DialogEngine` marshrut topilgan har bir
kiruvchi qo'ng'iroqda shu ssenariyni ishga tushiradi — batafsil:
[scenarios.md](scenarios.md).

Do-not-call ro'yxati kiruvchi qo'ng'iroqlarga **qo'llanilmaydi** — bir marta
"qo'ng'iroq qilmang" degan mijoz, o'zi qo'ng'iroq qilsa, baribir javob oladi
(ROADMAP C.2).

Umumiy javob shakli, xatolar va pagination konventsiyasi uchun
[README.md](README.md)ga qarang.

---

## `POST /api/inbound-routes` — yangi marshrut

**Request body** (`CreateInboundRouteRequest`):

```json
{
  "didNumber": "998712345678",
  "scenarioId": 6,
  "language": "uz-UZ",
  "businessHoursStart": "09:00:00",
  "businessHoursEnd": "20:00:00",
  "fallbackMessage": null
}
```

| Maydon | Turi | Majburiymi | Izoh |
|---|---|---|---|
| `didNumber` | string | ✅ (`@NotBlank`) | dialangan raqam (3-15 raqam, `+` bilan/bepul); bitta faol raqam uchun faqat bitta yoqilgan marshrut bo'lishi mumkin — dublikat `409` |
| `scenarioId` | long | ✅ (`@NotNull`) | `GET/POST /api/scenarios/list`dagi `id`; noma'lum yoki boshqa kompaniyaniki bo'lsa `404` |
| `language` | string | ❌ | BCP-47; bo'sh bo'lsa `uz-UZ` |
| `businessHoursStart` / `businessHoursEnd` | `LocalTime` (`HH:mm:ss`) | ❌ | ikkalasidan biri bo'lmasa — cheklovsiz (doim ochiq) |
| `fallbackMessage` | string | ❌ | ish vaqtidan tashqari kelgan qo'ng'iroqda TTS orqali aytiladi, so'ng qo'ng'iroq tugatiladi (`AriService.playFallbackAndHangup`) — dialog/ssenariy ishga tushmaydi, `call_attempt` yozuvi ham ochilmaydi. Bo'sh bo'lsa — jim tarzda tugatiladi. Marshrut umuman topilmagan (noma'lum DID) holatda hech qachon aytilmaydi — aytadigan marshrut yo'q |

**Response** (`InboundRouteRow`):

```json
{
  "id": 3,
  "didNumber": "998712345678",
  "scenarioId": 6,
  "scenarioName": "Kirish so'rovlari",
  "language": "uz-UZ",
  "businessHoursStart": "09:00:00",
  "businessHoursEnd": "20:00:00",
  "fallbackMessage": null,
  "enabled": true,
  "createdAt": "2026-08-01T09:00:00Z"
}
```

`scenarioName` — `scenarioId`dan hal qilingan (backend-uchun-talablar.md §3), ssenariy
o'chirilgan bo'lsa `null`.

---

## `POST /api/inbound-routes/list` — ro'yxat

Body — `InboundRouteFilter` (`page`/`size`/`orders`, [README §3](README.md#3-royxatfiltr-endpointlari-pagination)ga qarang).
Saralanadigan ustunlar: `ID`, `DID_NUMBER`, `LANGUAGE`, `ENABLED`, `CREATED_AT`.
Standart: `ID ASC`.

Javob — `PageableData<InboundRouteRow>` (`InboundRouteRow` shakli yuqorida).

---

## `GET /api/inbound-routes/{id}` — bitta marshrut

Javob — bitta `InboundRouteRow`. Topilmasa (yoki boshqa kompaniyaniki bo'lsa) —
`404`.

---

## `PUT /api/inbound-routes/{id}` — tahrirlash

**Request body** (`UpdateInboundRouteRequest`) — `POST /api/inbound-routes`
bilan bir xil maydonlar, plus `enabled`:

```json
{
  "didNumber": "998712345678",
  "scenarioId": 6,
  "language": "uz-UZ",
  "businessHoursStart": "09:00:00",
  "businessHoursEnd": "20:00:00",
  "fallbackMessage": null,
  "enabled": true
}
```

`didNumber`/`scenarioId` ham shu yerda o'zgartirilishi mumkin (kampaniyaning
`scenarioId`sidan farqli o'laroq — marshrutga bog'liq davom etayotgan holat
yo'q, shuning uchun erkin tahrirlanadi). Javob — yangilangan `InboundRouteRow`.

---

## `DELETE /api/inbound-routes/{id}` — o'chirish (yoqilmagan holatga o'tkazish)

Qatorni **o'chirmaydi** — `campaigns`ning arxivlash konventsiyasiga mos
ravishda `enabled=false` qiladi, shu raqamga marshrut endi topilmaydi. Body
yo'q. Javob — yangilangan `InboundRouteRow` (`enabled: false`).

---

## `GET /api/inbound-routes/{id}/stats` — qo'ng'iroqlar statistikasi {#get-apiinbound-routesidstats}

Drawer'dagi "shu raqamga tushgan qo'ng'iroqlar statistikasi" (§10.9). Har bir
kiruvchi qo'ng'iroq javob berilgan zahoti qaysi marshrutga tegishli ekanligi
yozib boriladi (`call_attempt.inbound_route_id`), shu ustunga qarab hisoblanadi
— kampaniya qo'ng'iroqlariga aralashmaydi.

**Response** (`InboundRouteStats`):

```json
{
  "inboundRouteId": 3,
  "didNumber": "998712345678",
  "totalCalls": 142,
  "answeredCalls": 118,
  "answerRate": 0.831,
  "avgDurationSec": 96.4,
  "lastCallAt": "2026-08-02T14:05:00Z",
  "dispositions": { "COMPLETED": 80, "TRANSFERRED": 20, "HUNG_UP": 18 },
  "statsAvailableFrom": "2026-06-15T00:00:00Z"
}
```

`answerRate` — `answeredCalls / totalCalls` (0..1), qo'ng'iroq bo'lmasa `0`.
`dispositions` — tugagan suhbatlar bo'yicha natija taqsimoti (`CampaignStats`
bilan bir xil shakl, [reports.md](reports.md)ga qarang). Marshrutga bog'lanish
(`inbound_route_id`) yozib borilishidan oldin tushgan qo'ng'iroqlar hisobga
kirmaydi (backend-uchun-talablar.md §8) — `statsAvailableFrom` shu kompaniyada
`inbound_route_id` birinchi marta yozilgan `started_at` vaqti; hech qanday
kiruvchi qo'ng'iroq hali bog'lanmagan bo'lsa `null`. Frontend `totalCalls`ni
"barcha vaqt" emas, **"`statsAvailableFrom`dan beri"** deb ko'rsatishi kerak —
undan oldingi tarixiy ma'lumot butunlay yo'q, nolga teng emas.
Marshrut topilmasa (yoki boshqa kompaniyaniki bo'lsa) — `404`.
