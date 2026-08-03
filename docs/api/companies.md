# Kompaniyalar API

`uz.murodjon.uysotvoice.company` · rol: **ADMIN** (barcha endpoint)

Kompaniya — platformadagi tenant chegarasi (ROADMAP Bosqich B): kampaniya,
ssenariy, kontakt, DNC, audit va h.k. hammasi `company_id` bilan shu qatorga
bog'langan. Real per-request kompaniya aniqlash ishlaydi (ROADMAP E.1) —
JWT bilan kirgan foydalanuvchi uchun `CurrentCompany` uning `app_user.company_id`
qiymatiga (`JwtCurrentCompanyResolver`) ishora qiladi; faqat ikkita global
bootstrap `X-Api-Key` uchun `voice-agent.company.default-id`dagi statik
qiymatga tushadi (`DefaultCompanyResolver`, faqat fallback).

**Ikki resurs bor:**
- **Company** — identifikatsiya: nom, holat (`ACTIVE`/`SUSPENDED`), logotip URL'i
  va manzil (backend-uchun-talablar.md §5 — sozlama emas, identifikatsiya
  ma'lumoti sifatida shu yerda).
- **CompanyConfig** — sozlamalar: qo'llab-quvvatlanadigan tillar ro'yxati,
  aniq `defaultLanguage` maydoni (backend-uchun-talablar.md §13), qat'iy
  qo'ng'iroq oralig'i, timezone. Har bir kompaniya yaratilganda avtomatik
  default config bilan ta'minlanadi (`uz-UZ`, 09:00–20:00, `Asia/Tashkent`).

Caller ID bu yerda emas — trunk darajasida (`sip_trunk.callerId`), chunki bitta
kompaniyaning bir nechta trunki har xil caller ID bilan bo'lishi mumkin.
Batafsil: [sip-trunks.md](sip-trunks.md).

Umumiy javob shakli, xatolar va pagination konventsiyasi uchun
[README.md](README.md)ga qarang.

---

## `POST /api/companies` — yangi kompaniya

**Request body** (`CreateCompanyRequest`):

```json
{ "name": "Ikkinchi kompaniya" }
```

Yaratilgan kompaniya `status: "ACTIVE"` bilan va yuqorida aytilgan default
`CompanyConfig` bilan boshlanadi — sozlamalarni keyin
`PUT /api/companies/{id}/config` orqali tahrirlang.

**Response** (`Company`):

```json
{ "id": 2, "name": "Ikkinchi kompaniya", "status": "ACTIVE", "createdAt": "2026-08-01T09:00:00Z",
  "logoUrl": null, "address": null }
```

---

## `POST /api/companies/list` — ro'yxat

Body — `CompanyFilter`:

```json
{ "page": 0, "size": 20, "orders": { "NAME": "ASC" }, "search": "Ikkinchi" }
```

`search` — nom bo'yicha erkin qidiruv, bo'sh/`null` — filtr yo'q. Saralanadigan
ustunlar: `ID`, `NAME`, `STATUS`, `CREATED_AT`. Standart: `NAME ASC`.

Javob — `PageableData<Company>` (`Company` shakli yuqorida).

---

## `GET /api/companies/{id}` — bitta kompaniya

**Response** — `Company` (yuqoridagi shakl). Topilmasa `404`.

---

## `PUT /api/companies/{id}` — yangilash

**Request body** (`UpdateCompanyRequest`):

```json
{ "name": "Ikkinchi kompaniya", "status": "ACTIVE",
  "logoUrl": "https://cdn.example.uz/logo.png", "address": "Toshkent, Chilonzor" }
```

`status` — `ACTIVE` yoki `SUSPENDED`. `logoUrl`/`address` ixtiyoriy —
`null`/bo'sh qoldirilsa tozalanadi. Fayl yuklash endpointi yo'q — panel
tayyor URL beradi (profil `avatarUrl`si bilan bir xil konventsiya).

Javob — yangilangan `Company`.

---

## `GET /api/companies/{id}/config` — sozlamalar

**Response** (`CompanyConfig`):

```json
{
  "id": 5,
  "companyId": 2,
  "dialWindowStart": "09:00:00",
  "dialWindowEnd": "20:00:00",
  "timezone": "Asia/Tashkent",
  "defaultLanguage": "uz-UZ",
  "supportedLanguages": ["uz-UZ", "ru-RU"],
  "createdAt": "2026-08-01T09:00:00Z"
}
```

Topilmasa `404` — amalda bo'lmasligi kerak, chunki har bir kompaniya
yaratilganda avtomatik config oladi.

---

## `PUT /api/companies/{id}/config` — sozlamalarni yangilash

**Request body** (`UpdateCompanyConfigRequest`):

```json
{ "dialWindowStart": "08:00", "dialWindowEnd": "21:00", "timezone": "Asia/Tashkent", "defaultLanguage": "uz-UZ", "supportedLanguages": ["uz-UZ", "ru-RU"] }
```

| Maydon | Majburiymi | Izoh |
|---|---|---|
| `dialWindowStart`/`dialWindowEnd` | ✅ | **Qat'iy chegara** — `DialerService` har bir kampaniyani o'z oynasi BILAN BIRGA shu oraliqqa ham tekshiradi; kampaniya bu kompaniyaning oralig'idan tashqariga chiqa olmaydi, hatto kampaniyaning o'z oynasi kengroq bo'lsa ham. |
| `timezone` | ✅ (`@NotBlank`) | — |
| `defaultLanguage` | ✅ (`@NotBlank`) | Aniq maydon (backend-uchun-talablar.md §13) — `supportedLanguages` ro'yxatining a'zosi bo'lishi shart, aks holda `400`. Kampaniya yaratish/tahrirlashda (`CreateCampaignRequest.defaultLanguage`/`UpdateCampaignRequest.defaultLanguage`) til berilmasa shu qiymatga tushadi. |
| `supportedLanguages` | ✅ (bo'sh bo'lmasin) | Kampaniya/marshrut e'lon qilishi mumkin bo'lgan barcha tillar, `defaultLanguage`ni ham o'z ichiga olgan holda; berilgan til shu ro'yxatda bo'lishi shart, aks holda `400`. |

**Response** — yangilangan `CompanyConfig`.
