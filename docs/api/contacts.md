# Kontaktlar API

`uz.murodjon.robotcallv2.contact` · rol: **ADMIN** (barcha endpoint)

Kontaktlar jadvali `campaign_target`dan mustaqil — kampaniyaga bog'liq emas.
Qo'ng'iroqlar tarixi `contact_id` FK orqali emas, telefon raqami bo'yicha
moslashtiriladi.

Umumiy javob shakli, xatolar va pagination konventsiyasi uchun
[README.md](README.md)ga qarang.

---

## `POST /api/contacts` — yangi kontakt

**Request body** (`CreateContactRequest`):

```json
{ "name": "Aziz Karimov", "phone": "998901234567", "address": "Toshkent", "tags": "vip,doimiy", "notes": "Doimiy mijoz" }
```

| Maydon | Majburiymi | Izoh |
|---|---|---|
| `name` | ✅ (`@NotBlank`) | — |
| `phone` | ✅ (`@NotBlank`) | telefon bo'yicha dublikat rad etiladi (`409`) |
| `address` | ❌ | — |
| `tags` | ❌ | vergul bilan ajratilgan |
| `notes` | ❌ | — |

**Response** (`Contact`):

```json
{
  "id": 12,
  "name": "Aziz Karimov",
  "phone": "998901234567",
  "address": "Toshkent",
  "tags": "vip,doimiy",
  "notes": "Doimiy mijoz",
  "createdAt": "2026-07-20T09:00:00Z"
}
```

---

## `POST /api/contacts/list` — ro'yxat

Body — `ContactFilter`:

```json
{ "page": 0, "size": 20, "orders": { "NAME": "ASC" }, "search": "Aziz" }
```

`search` — ism yoki telefon bo'yicha erkin qidiruv, bo'sh/`null` — filtr yo'q.
Saralanadigan ustunlar: `ID`, `NAME`, `PHONE`, `CREATED_AT`. Standart:
`NAME ASC`.

Javob — `PageableData<Contact>` (`Contact` shakli yuqorida).

---

## `GET /api/contacts/{id}` — profil + qo'ng'iroqlar tarixi

**Response** (`ContactDetail`):

```json
{
  "contact": { /* Contact */ },
  "callHistory": [
    { "callId": 501, "campaignName": "Iyul qarzdorlik", "startedAt": "2026-07-15T10:00:00Z", "durationSec": 87, "disposition": "PROMISE_TO_PAY" }
  ]
}
```

`callHistory` — shu telefon raqamiga tegishli oxirgi 50 ta qo'ng'iroq, yangisi
birinchi. Kampaniya qo'ng'iroqlari bilan bir qatorda shu raqamga qilingan qo'lda
test qo'ng'iroqlari va ulanmagan urinishlar (`NO_ANSWER`, `CARRIER_REJECTED`) ham
kiradi; ulanmaganida `durationSec` — `null`.

---

## `PUT /api/contacts/{id}` — yangilash

**Request body** (`UpdateContactRequest`) — **telefon o'zgartirilmaydi**
(barqaror identifikator sifatida saqlanadi, shuning uchun bodyda yo'q):

```json
{ "name": "Aziz Karimov", "address": "Toshkent", "tags": "vip", "notes": "Yangilangan izoh" }
```

Javob — yangilangan `Contact`.

---

## `DELETE /api/contacts/{id}` — kontaktni o'chirish

Body yo'q. Kontakt o'chiriladi.

**Response**:

```json
{
  "data": null,
  "accept": true
}
```

Mavjud bo'lmagan kontakt uchun — `404 Not Found`.

---

## `POST /api/contacts/csv` — CSV import {#csv-import}

`Content-Type: text/csv` (yoki `text/plain`), body — CSV faylning o'zi:

```csv
name,phone,address,tags,notes
Aziz Karimov,998901234567,Toshkent,vip,Doimiy mijoz
```

Ustunlar sarlavha nomi bo'yicha moslashtiriladi.

**Response** (`ContactImportResult`):

```json
{
  "added": 45,
  "contactIds": [12, 13, "..."],
  "errors": [ { "line": 8, "message": "phone: must not be blank" } ],
  "unknownColumns": ["extraColumn"]
}
```

---

## `POST /api/contacts/{id}/dnc` — "DNC ga qo'shish" {#post-apicontactsiddnc}

Kontaktning telefon raqamini opt-out (qo'ng'iroq qilinmasin) ro'yxatiga
qo'shadi. Body yo'q.

**Response** (`ContactDncResponse`):

```json
{ "contactId": 12, "doNotCall": true }
```

Butun opt-out ro'yxatini ko'rish/undan olib tashlash uchun
[do-not-call.md](do-not-call.md)ga qarang.
