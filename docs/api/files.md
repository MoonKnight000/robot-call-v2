# Fayllar (StoredFile katalogi) API

`uz.murodjon.robotcallv2.storage` · rol: istalgan (kirgan bo'lsa yetarli)

Bu loyihada har qanday fayl (kompaniya logotipi, foydalanuvchi avatari,
qo'ng'iroq yozuvi, hujjatlar) MinIO/S3'da saqlanadi, lekin **frontend hech qachon MinIO'ga
to'g'ridan-to'g'ri so'rov yubormaydi** — MinIO Docker tarmog'ida ichki
hostname (masalan `minio:9000`) bo'lib, brauzer buni to'g'ridan-to'g'ri resolve qila olmaydi.
Buning o'rniga har bir fayl `stored_file` jadvalida bitta qatorga ega (`id`,
original nom, MinIO'dagi yo'l, format, hajm, kompaniya id, kategoriya),
va shu `id` orqali fayllarni yuklash va stream qilib olish backend orqali amalga oshiriladi.

Umumiy javob shakli, xatolar va pagination konventsiyasi uchun
[README.md](README.md)ga qarang.

---

## `POST /api/files/upload` (yoki `/api/v1/files/upload`) — Yagona fayl yuklash

Interfeysdan har qanday fayllarni (rasmlar, audio namunalar, CSV/hujjatlar) yuklash uchun yagona umumiy endpoint.

**Headers**:
- `Content-Type: multipart/form-data`
- `Authorization: Bearer <token>`

**Form Data (Multipart params)**:
- `file` (majburiy, `MultipartFile`): Yuklanayotgan fayl baytlari.
- `category` (ixtiyoriy, string enum): `IMAGE`, `AUDIO`, `DOCUMENT`. Agar ko'rsatilsa shu kategoriya o'rnatiladi, agar ko'rsatilmasa faylning MIME-type'idan avtomatik aniqlanadi (`image/*` -> `IMAGE`, `audio/*` -> `AUDIO`, boshqa -> `DOCUMENT`).

**Response** (`FileUploadResponse`):

```json
{
  "accept": true,
  "data": {
    "id": 12,
    "originalName": "avatar.png",
    "url": "/api/files/12",
    "contentType": "image/png",
    "sizeBytes": 1048576,
    "category": "IMAGE",
    "createdAt": "2026-09-02T12:00:00Z"
  },
  "message": "Fayl muvaffaqiyatli yuklandi",
  "messageCode": "SUCCESS",
  "errors": null
}
```

| Maydon | Turi | Izoh |
|---|---|---|
| `id` | long | `stored_file` jadvalidagi unikal ID |
| `originalName` | string | Yuklangan faylning asl nomi |
| `url` | string | Faylni olish uchun nisbiy havola (`/api/files/{id}`) |
| `contentType` | string | MIME tipi (`image/png`, `audio/wav`, ...) |
| `sizeBytes` | long | Fayl hajmi baytlarda |
| `category` | enum | `IMAGE`, `AUDIO`, `DOCUMENT` |
| `createdAt` | ISO timestamp | Yuklangan vaqti |

---

## `GET /api/files/{id}` — Faylni yuklab olish

**`ResponseData`ga o'ralmagan** — xom fayl baytlarini qaytaradi
(`ResponseEntity<Resource>`).

Autentifikatsiya boshqa hamma `/api/**` kabi (`X-Api-Key`/Bearer) — bu
degani oddiy `<img src="...">`/`<audio src="...">` ishlamaydi (brauzer
maxsus sarlavha qo'ya olmaydi). Frontend `fetch` bilan blob sifatida olib,
`URL.createObjectURL`ga o'raydi (masalan `requestBlob`, `src/api/http.ts`),
xuddi `GET /api/reports/calls/{id}/recording` uchun qilingani kabi.

| Kategoriya | `Content-Disposition` | Qayerdan kelgan |
|---|---|---|
| Rasm (`IMAGE`) | `inline` | `POST /api/files/upload`, `POST /api/companies/{id}/logo`, `POST /api/profile/avatar` |
| Audio (`AUDIO`) | `attachment` | `CallFinalizer`, `POST /api/files/upload` |
| Hujjat (`DOCUMENT`) | `attachment` | `POST /api/files/upload` |

**Kompaniya izolyatsiyasi**: `id` boshqa kompaniyaniki bo'lsa (yoki umuman
mavjud bo'lmasa) — `404`, mavjudligini oshkor qilmaslik uchun bir xil xabar
bilan (`NotFoundException`). `SUPERADMIN` istalgan faylni ko'ra oladi.

MinIO o'zi ishlamasa (`voice-agent.storage.enabled=false` yoki ulanish
xato) — `502`.
