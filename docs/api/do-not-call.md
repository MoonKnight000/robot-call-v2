# "Qo'ng'iroq qilinmasin" (DNC) API

`uz.murodjon.robotcallv2.donotcall` · huquq: **DO_NOT_CALL_READ** / **DO_NOT_CALL_EDIT**

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
{
  "page": 0,
  "size": 20,
  "orders": { "CREATED_AT": "DESC" }
}
```

Saralanadigan ustunlar: `PHONE`, `REASON`, `SOURCE`, `CREATED_AT`. Standart:
`CREATED_AT DESC, PHONE ASC` (yangi qo'shilgani birinchi). Qaysi saralash berilsa ham
`PHONE ASC` oxiriga qo'shiladi — sahifalar orasida tartib barqaror bo'lishi uchun.
Faqat **faol** (o'chirilmagan) yozuvlarni qaytaradi.

> ⚠️ `DoNotCallFilter` da `search` maydoni **yo'q** — raqam bo'yicha qidiruv qo'llab
> quvvatlanmaydi. `ID` ham saralanadigan ustunlar ichida emas.

**Javob qatori** (`DoNotCallRow`, `PageableData<DoNotCallRow>` ichida):

```json
{
  "data": {
    "totalPages": 1,
    "currentPage": 0,
    "totalElements": 1,
    "data": [
      {
        "id": 3,
        "phone": "998901234567",
        "contactName": "Aziz Karimov",
        "reason": "Mijoz so'rovi",
        "source": "MANUAL",
        "createdAt": "2026-07-10T08:00:00Z"
      }
    ]
  },
  "message": null,
  "messageCode": null,
  "accept": true,
  "errors": null
}
```

| Maydon | Turi | Izoh |
|---|---|---|
| `source` | `DoNotCallSource` enum | `CALL` (AI qo'ng'iroq davomida mijoz rad etganda), `CRM` (tashqi CRM sinxronizatsiyasi), `MANUAL` (panel orqali kiritilgan) |
| `contactName` | string | Shu telefon raqami bo'yicha `contact` jadvalidan topilgan ism; kontakt mavjud bo'lmasa `null` |
| `reason` | string | Qo'shilish sababi / izoh |

---

## `POST /api/do-not-call/{phone}/remove` — ro'yxatdan chiqarish

Body yo'q, `phone` — path parametr. Soft-delete qiladi (`removed_at`/
`removed_by` yoziladi, qator o'chirilmaydi) va audit jurnaliga
`DNC_REMOVE` yozuvi qo'shadi ([reports.md](reports.md#audit-log)ga qarang).

**Response** (`DoNotCallRemoveResponse`):

```json
{
  "data": {
    "phone": "998901234567",
    "removed": true
  },
  "message": null,
  "messageCode": null,
  "accept": true,
  "errors": null
}
```

Hech qachon opt-out bo'lmagan yoki allaqachon olib tashlangan raqam uchun —
`404 Not Found`.
