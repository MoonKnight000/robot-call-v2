# API hujjatlari — frontend uchun

Bu papka frontend (Nido paneli) ishlatishi mumkin bo'lgan **hozirda mavjud, real
ishlaydigan** endpointlarni hujjatlaydi. Rejalashtirilgan-u hali yozilmagan
endpointlar uchun `docs/API-REQUIREMENTS.md`ga qarang (holat: ✅/⚠️/❌ jadvali).

## Fayllar

| Fayl | Nima haqida |
|---|---|
| [campaigns.md](campaigns.md) | Kampaniyalar: yaratish, tahrirlash, arxivlash, nishonlar (targets), CSV import, start/pause |
| [scenarios.md](scenarios.md) | Ssenariy CRUD, validatsiya, klonlash |
| [contacts.md](contacts.md) | Kontaktlar: CRUD, CSV import, qo'ng'iroqlar tarixi |
| [do-not-call.md](do-not-call.md) | "Qo'ng'iroq qilinmasin" (DNC) ro'yxati |
| [calls.md](calls.md) | Qo'lda qo'ng'iroq boshlash / play / say (test-tekshiruv uchun) |
| [live.md](live.md) | Jonli qo'ng'iroqlar ro'yxati va SSE push kanali |
| [operator.md](operator.md) | Operatorga uzatilgan qo'ng'iroq konteksti |
| [reports.md](reports.md) | Dashboard KPI/grafiklar, qo'ng'iroqlar tarixi + filtr/eksport/ommaviy amal, texnik tafsilot, transkript TXT, audit jurnali, recording |
| [voices.md](voices.md) | TTS ovozlar katalogi |

---

## 1. Bazaviy URL va autentifikatsiya

Barcha endpoint `/api` prefiksi ostida. Autentifikatsiya — `X-Api-Key` sarlavhasi
(header), sessiya yoki cookie yo'q:

```
X-Api-Key: <kalit>
```

Ikki xil kalit bor, ikkita rol beradi:

| Rol | Kalit | Nimaga ruxsat beradi |
|---|---|---|
| `ADMIN` | `voice-agent.security.api-key` (`API_KEY` env) | Hammasi — qo'ng'iroq boshlash, kampaniya yaratish/boshqarish, DNC'ga qo'shish va h.k. |
| `VIEWER` | `voice-agent.security.read-api-key` (`READ_API_KEY` env) | Faqat o'qish: `GET /api/reports/**` va `GET /api/live/**` |

`ADMIN` kaliti `VIEWER` huquqini ham beradi — ya'ni admin kalit bilan hisobotlarni
ham o'qib bo'ladi. Kalit noto'g'ri yoki berilmagan bo'lsa — `401 Unauthorized`
(body yo'q, sof status kod).

Frontend uchun amaliy xulosa: **hisobot/jonli-monitoring sahifalari** uchun
`read-api-key` yetarli; kampaniya yaratish, qo'ng'iroq boshlash, kontakt/DNC
o'zgartirish kabi harakatlar uchun **admin kalit** kerak. Ikkalasini bitta joyda
config qilib, sahifaga qarab tanlang — yoki (oddiyroq) frontend har doim admin
kalit bilan ishlaydi, agar backendda alohida "faqat o'qish" foydalanuvchi
bo'lmasa.

> `GET /api/live/stream` — brauzerning tayyor `EventSource` klassi maxsus
> sarlavha (`X-Api-Key`) qo'ya olmaydi, shuning uchun bu endpoint uchun
> `fetch`-asosidagi SSE klient kerak (masalan `@microsoft/fetch-event-source`).
> Batafsil: [live.md](live.md).

---

## 2. Har bir javobning umumiy shakli

Har bir endpoint (SSE oqimidan tashqari) `ResponseData<T>` shaklida javob
qaytaradi:

```json
{
  "data": { /* T — muvaffaqiyatda */ },
  "message": null,
  "accept": true,
  "errors": null
}
```

**Xatolik javobi** — HTTP status kod xatoning turiga qarab tanlanadi, tana esa
har doim shu shaklda:

```json
{
  "data": null,
  "message": "campaign 42 not found",
  "accept": false,
  "errors": null
}
```

`errors` faqat maydon darajasidagi validatsiya xatosida to'ldiriladi (pastga
qarang), qolgan hollarda `null` — `message`ning o'zi yetarli.

Frontend uchun amaliy qoida: **har doim `accept`ga qarab tekshiring**, HTTP
status kodga emas — ikkalasi ham mos keladi, lekin `accept: false` bo'lganda
`data` doim `null` bo'ladi, shuning uchun `data`ni undefined/null tekshiruvisiz
ishlatmang.

### Status kodlar va nima uchun

| Status | Qachon | Misol |
|---|---|---|
| `200` | Muvaffaqiyat | — |
| `400` | Validatsiya xatosi — kiruvchi ma'lumot noto'g'ri (bo'sh maydon, noto'g'ri sana, JSON buzilgan, majburiy parametr yo'q) | `"name: must not be blank"` |
| `401` | `X-Api-Key` yo'q yoki noto'g'ri | body yo'q |
| `403` | Kalit to'g'ri, lekin bu amalga ruxsat yo'q (masalan built-in ssenariyni tahrirlash) | `"builtin scenario cannot be edited — clone it first"` |
| `404` | Berilgan `id` topilmadi (yoki boshqa kompaniyaniki — ataylab bir xil xabar bilan) | `"campaign 42 not found"` |
| `409` | So'rov to'g'ri, lekin tizim holati mos emas (ARI ulanmagan, allaqachon DNC'da) | `"phone already on the do-not-call list"` |
| `502` | Tashqi xizmat (Asterisk/CRM/STT/TTS/storage) ishlamadi | `"asterisk: channel already hung up"` |
| `500` | Kutilmagan ichki xato (bug) — tafsilot faqat server logida | `"Internal server error"` |

**Bean Validation xatosi** (`@Valid @RequestBody` o'tmagan maydon) — `errors`
massivi har bir maydon uchun bitta xabar bilan to'ladi:

```json
{
  "data": null,
  "message": "Validation failed",
  "accept": false,
  "errors": ["name: must not be blank", "phone: must not be blank"]
}
```

---

## 3. Ro'yxat/filtr endpointlari (pagination)

3 tadan ko'p parametr qabul qiladigan har qanday ro'yxat endpointi **`GET` emas,
`POST`** bo'lib, parametrlarni JSON bodyda kutadi (masalan
`POST /api/campaigns/list`, `POST /api/reports/calls/list`). So'rov tanasi har
doim shu uchta maydonni qabul qiladi:

```json
{
  "page": 0,
  "size": 20,
  "orders": { "NAME": "ASC" }
}
```

- `page` — 0-based sahifa raqami; berilmasa `0`.
- `size` — sahifa hajmi; berilmasa `20`, maksimal `500`. **Chegaradan tashqari
  qiymat (masalan `size: 1000`) jim tarzda kesilmaydi — `400` bilan rad
  etiladi.**
- `orders` — ustun nomi → yo'nalish (`"ASC"`/`"DESC"`) xaritasi, tartib
  muhim (bir nechta ustun bo'yicha saralash uchun). Ustun nomlari har bir
  ro'yxat uchun qat'iy enum (masalan `CampaignTableField`) — quyidagi
  jadvalga qarang. Noma'lum ustun nomi yuborilsa — `400 Malformed or invalid
  request body`.

Javob har doim shu shaklda keladi (`ResponseData<T>` ichida):

```json
{
  "data": {
    "totalPages": 3,
    "currentPage": 0,
    "totalElements": 47,
    "data": [ /* T qatorlar ro'yxati */ ]
  },
  "message": null,
  "accept": true,
  "errors": null
}
```

Har bir ro'yxat endpointi uchun qabul qilinadigan `orders` kalitlari (enum
qiymatlari, JSON'da string sifatida yuboriladi):

| Endpoint | Filtr turi | Saralanadigan ustunlar |
|---|---|---|
| `POST /api/campaigns/list` | `CampaignFilter` | `ID`, `NAME`, `TYPE`, `STATUS` |
| `POST /api/campaigns/{id}/targets/list` | `TargetFilter` | `ID`, `PHONE`, `STATUS`, `ATTEMPTS` |
| `POST /api/scenarios/list` | `ScenarioFilter` | `ID`, `SCENARIO_KEY`, `NAME`, `VERSION`, `CREATED_AT` |
| `POST /api/contacts/list` | `ContactFilter` | `ID`, `NAME`, `PHONE`, `CREATED_AT` |
| `POST /api/do-not-call/list` | `DoNotCallFilter` | `ID`, `PHONE`, `CREATED_AT` |
| `POST /api/reports/calls/list`, `POST /api/reports/campaigns/{id}/calls/list` | `CallFilter` | `CALL_ID`, `STARTED_AT`, `ENDED_AT`, `DURATION_SEC`, `DISPOSITION` |
| `POST /api/reports/audit/list` | `AuditFilter` | `ID`, `CREATED_AT`, `ACTION`, `ENTITY` |

Har bir filtrning standart saralashi (`orders` berilmasa yoki bo'sh bo'lsa)
o'sha faylning batafsil hujjatida ko'rsatilgan.

---

## 4. CSV import konventsiyasi

Ikkita endpoint (`POST /api/campaigns/{id}/targets/csv`,
`POST /api/contacts/csv`) faylni **JSON emas, xom matn** sifatida kutadi:

```
Content-Type: text/csv
```

(yoki `text/plain`) — body esa CSV faylning o'zi, masalan:

```csv
name,phone,address,tags,notes
Aziz Karimov,998901234567,Toshkent,vip,Doimiy mijoz
```

Ustunlar sarlavha (header) nomi bo'yicha moslashtiriladi — tartib muhim emas,
ortiqcha ustunlar `unknownColumns` sifatida qaytariladi. Noto'g'ri qatorlar
qator raqami bilan rad etiladi, qolgani yuklanadi (hech qachon "hammasi yoki
hech narsa" emas). Batafsil: [campaigns.md](campaigns.md#csv-import),
[contacts.md](contacts.md#csv-import).

Frontendda `fetch`/`axios` bilan yuborishda `JSON.stringify` qilinmaydi —
CSV matnini to'g'ridan-to'g'ri body sifatida, `Content-Type: text/csv` bilan
yuborish kerak.

---

## 5. Muhim eslatmalar

- **Sana/vaqt** — barcha `Instant` maydonlar ISO-8601 (`2026-07-31T12:00:00Z`).
  Dashboard endpointlaridagi `from`/`to` query parametrlari ham shu formatda,
  ikkalasi ham ixtiyoriy.
- **Telefon raqam** — validatsiyadan o'tgan formatda yuboring; noto'g'ri format
  `400` bilan rad etiladi (aniq xabar bilan).
- **`campaignId`/`contactId`/`id` kabi path parametrlar** — `long`. Raqam
  bo'lmagan qiymat `400 Invalid value for parameter: id` beradi.
- Hozircha **bitta kompaniya** bilan ishlaydi (`voice-agent.company.default-id`)
  — frontendda kompaniya tanlash/almashtirish UI'si hali kerak emas.
