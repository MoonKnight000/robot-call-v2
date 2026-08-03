# "Qo'ng'iroq qilinmasin" (DNC) API

`uz.murodjon.uysotvoice.donotcall` · rol: **OPERATOR** (barcha endpoint — ADMIN ham kiradi, rol ierarxiyasi bo'yicha)

Kompaniya darajasidagi opt-out ro'yxati — kampaniyaga bog'liq emas, shuning
uchun alohida API. Kontakt yoki nishondan qo'shish uchun
[contacts.md](contacts.md#post-apicontactsiddnc) va
[campaigns.md](campaigns.md#post-apitargetsiddo-not-call)ga qarang — bu ikkisi
shu yerdagi ro'yxatga yozadi.

Umumiy javob shakli, xatolar va pagination konventsiyasi uchun
[README.md](README.md)ga qarang.

---

## `POST /api/do-not-call/list` — ro'yxat

Body — `DoNotCallFilter`:

```json
{ "page": 0, "size": 20, "orders": { "CREATED_AT": "DESC" } }
```

Saralanadigan ustunlar: `ID`, `PHONE`, `CREATED_AT`. Standart:
`CREATED_AT DESC` (yangi qo'shilgani birinchi). Faqat **faol** (o'chirilmagan)
yozuvlarni qaytaradi.

**Javob qatori** (`DoNotCallRow`, `PageableData<DoNotCallRow>` ichida):

```json
{
  "id": 3,
  "phone": "998901234567",
  "contactName": "Aziz Karimov",
  "reason": "Mijoz so'rovi",
  "source": "contact",
  "createdAt": "2026-07-10T08:00:00Z"
}
```

`source` — qaysi joydan qo'shilgani (masalan `contact`, `target`, `manual`).
`contactName` — shu telefon raqami bo'yicha `contact` jadvalidan hal qilingan
(backend-uchun-talablar.md §15); shu raqamli kontakt umuman mavjud bo'lmasa `null`
(opt-out hech qachon kontakt sifatida qo'shilmagan raqam uchun ham yozilishi mumkin).

---

## `POST /api/do-not-call/{phone}/remove` — ro'yxatdan chiqarish

Body yo'q, `phone` — path parametr. Soft-delete qiladi (`removed_at`/
`removed_by` yoziladi, qator o'chirilmaydi) va audit jurnaliga
`DNC_REMOVE` yozuvi qo'shadi ([reports.md](reports.md#audit-log)ga qarang).

**Response** (`DoNotCallRemoveResponse`):

```json
{ "phone": "998901234567", "removed": true }
```

Hech qachon opt-out bo'lmagan yoki allaqachon olib tashlangan raqam uchun —
`404 Not Found`.
