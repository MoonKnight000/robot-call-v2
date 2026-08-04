# Fayllar (StoredFile katalogi) API

`uz.murodjon.uysotvoice.storage` · rol: istalgan (kirgan bo'lsa yetarli)

Bu loyihada har qanday fayl (kompaniya logotipi, foydalanuvchi avatari,
qo'ng'iroq yozuvi) MinIO'da saqlanadi, lekin **frontend hech qachon MinIO'ga
to'g'ridan-to'g'ri so'rov yubormaydi** — MinIO Docker tarmog'ida ichki
hostname (masalan `minio:9000`) bo'lib, brauzer buni resolve qila olmaydi.
Buning o'rniga har bir fayl `stored_file` jadvalida bitta qatorga ega (id,
original nom, MinIO'dagi yo'l, format, hajm, kompaniya id, kategoriya),
va shu `id` orqali quyidagi endpoint fayl baytlarini o'zi olib, streamlab
beradi.

Umumiy javob shakli, xatolar va pagination konventsiyasi uchun
[README.md](README.md)ga qarang.

---

## `GET /api/files/{id}` — faylni yuklab olish

**`ResponseData`ga o'ralmagan** — xom fayl baytlarini qaytaradi
(`ResponseEntity<Resource>`).

Autentifikatsiya boshqa hamma `/api/**` kabi (`X-Api-Key`/Bearer) — bu
degani oddiy `<img src="...">`/`<audio src="...">` ishlamaydi (brauzer
maxsus sarlavha qo'ya olmaydi). Frontend `fetch` bilan blob sifatida olib,
`URL.createObjectURL`ga o'raydi (masalan `requestBlob`, `src/api/http.ts`),
xuddi `GET /api/reports/calls/{id}/recording` uchun qilingani kabi.

| Kategoriya | `Content-Disposition` | Qayerdan kelgan |
|---|---|---|
| Rasm (kompaniya logo, user avatar) | `inline` | `POST /api/companies/{id}/logo`, `POST /api/profile/avatar` |
| Audio (qo'ng'iroq yozuvi) | `attachment` | `CallFinalizer` (Stage 9) |
| Hujjat | `attachment` | hozircha ishlatilmaydi — kelajakdagi feature uchun tayyor |

**Kompaniya izolyatsiyasi**: `id` boshqa kompaniyaniki bo'lsa (yoki umuman
mavjud bo'lmasa) — `404`, mavjudligini oshkor qilmaslik uchun bir xil xabar
bilan (`NotFoundException`). `SUPERADMIN` istalgan faylni ko'ra oladi.

MinIO o'zi ishlamasa (`voice-agent.storage.enabled=false` yoki ulanish
xato) — `502`.
