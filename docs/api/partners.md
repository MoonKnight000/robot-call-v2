# Partnerlar (reseller / white-label) — `/api/partners`, `/api/partner`, `/api/branding`

Partner — platformani **o'z brendi ostida** sotadigan kompaniya (integrator, call-markaz, telekom).
Platforma kompaniyani partner qiladi → partner o'z konsoli brendini (nom, logo, rang, domen) sozlaydi →
o'z mijozlarini ro'yxatdan o'tkazadi → mijozlar panelida bizning emas, partnerning brendi ko'rinadi.

- Bir daraja: partner boshqa partnerning mijozi bo'la olmaydi.
- Kompaniya partner bo'ladi = unda `partner_branding` qatori bor.
- Mijozni partner yaratadi; birinchi admin uchun aktivatsiya tokeni **partnerga qaytadi** — xatni biz
  emas, partner o'z domenidan, o'z brendi bilan yuboradi (havola: `https://<partner domeni>/activate?token=...`).
- Hozircha **yo'q**: partner komissiyasi / daromad ulushi, partnerning mijoz panelini ochishi
  (impersonation), partner balansidan mijozga to'lash. Mijozning billing'i — oddiy kompaniyaniki kabi.

## `PUT /api/partners/{companyId}/branding` — kompaniyani partner qilish

**Rol:** `PLATFORM_ADMIN`. Brend yo'q bo'lsa yaratadi (kompaniya partner bo'ladi), bor bo'lsa almashtiradi.

**Request** — `UpdatePartnerBrandingRequest`:

| Maydon | Turi | Majburiy | Izoh |
|---|---|---|---|
| `productName` | string (≤100) | ✅ | Mijozlar ko'radigan mahsulot nomi |
| `domain` | string | ❌ | Partner konsoli hosti (`app.smartcall.uz`), kichik harfga keltiriladi; har partnerda boshqa |
| `logoUrl` | string | ❌ | `https://...` |
| `primaryColor` | string | ❌ | `#RRGGBB` |
| `supportEmail` | string | ❌ | Mijozlar yordam so'raydigan email |
| `supportPhone` | string (≤32) | ❌ | Partner qo'llab-quvvatlash raqami |

```json
{ "productName": "SmartCall", "domain": "app.smartcall.uz", "logoUrl": "https://smartcall.uz/logo.png",
  "primaryColor": "#0055FF", "supportEmail": "help@smartcall.uz", "supportPhone": "+998712000000" }
```

**Response** — `PartnerBranding` (`companyId`, yuqoridagi maydonlar, `updatedAt`).

| Status | `messageCode` | Qachon |
|---|---|---|
| 400 | `VALIDATION_FAILED` | maydon formati noto'g'ri |
| 404 | `COMPANY_NOT_FOUND` | kompaniya yo'q |
| 409 | `PARTNER_CLIENT_IS_PARTNER` | kompaniya boshqa partnerning mijozi |
| 409 | `PARTNER_DOMAIN_TAKEN` | domen boshqa partnerniki |

## `GET /api/partner/branding` · `PUT /api/partner/branding` — partnerning o'z brendi

**Rol:** `COMPANY_READ` / `COMPANY_EDIT`. Request va response — yuqoridagi kabi. Partner bo'lmagan
kompaniya — `403 PARTNER_REQUIRED`; band domen — `409 PARTNER_DOMAIN_TAKEN`.

## `POST /api/partner/clients` — mijoz kompaniya yaratish

**Rol:** `COMPANY_EDIT` (faqat partner). Kompaniya + birinchi admin + partner bog'lanishi bitta
tranzaksiyada.

```json
{ "companyName": "Do'kon MChJ", "adminName": "Aziz Karimov", "username": "aziz", "email": "aziz@dokon.uz" }
```

**Response** — `CreatePartnerClientResponse`:

```json
{ "data": { "companyId": 42, "email": "aziz@dokon.uz", "inviteToken": "…", "expiresAt": "2026-10-21T09:00:00Z" } }
```

Token `POST /api/auth/activate` ga beriladi (admin parolini o'zi o'rnatadi — [auth.md](auth.md)).

| Status | `messageCode` | Qachon |
|---|---|---|
| 403 | `PARTNER_REQUIRED` | chaqiruvchi kompaniya partner emas |
| 409 | `USER_EMAIL_TAKEN` / `USER_USERNAME_TAKEN` | email yoki login band — kompaniya yaratilmaydi |

## `POST /api/partner/clients/list` — partnerning mijozlari

**Rol:** `COMPANY_READ` (faqat partner). Filtr — `PartnerClientFilter`: `page`, `size`,
`orders` (`CREATED_AT` | `CLIENT_COMPANY_ID`; standart `CREATED_AT: DESC`).

**Response** — `PageableData<PartnerClientRow>`: `companyId`, `name`, `status` (`ACTIVE` / `SUSPENDED`),
`joinedAt` (partner mijozni qo'shgan vaqt).

## `GET /api/branding` — panel qaysi brendni ko'rsatadi

**Rol:** har qanday tizimga kirgan foydalanuvchi. Kompaniya partner bo'lsa — o'z brendi, partner mijozi
bo'lsa — partnerning brendi, aks holda `data: null` (bizning brend).

**Response** — `BrandingResponse`: `productName`, `logoUrl`, `primaryColor`, `supportEmail`, `supportPhone`.

## `GET /api/public/branding?domain=app.smartcall.uz` — login sahifasi uchun

**Login talab qilinmaydi.** Frontend login sahifasi ochilgan `window.location.hostname` ni yuboradi;
shu domenli partner bo'lsa uning `BrandingResponse` i, aks holda `data: null`. Faqat login sahifasi
baribir ko'rsatadigan ma'lumot qaytadi.

**Domen ulash:** partner DNS'da `app.smartcall.uz` ni platforma frontendiga CNAME qiladi; TLS sertifikati
va `CORS_ALLOWED_ORIGINS` ga shu domen qo'shilishi — deploy tomonida qo'lda.
