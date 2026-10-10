# Kontaktlar API

`uz.murodjon.robotcallv2.contact` · huquq: **CONTACT_READ** / **CONTACT_EDIT**

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

## `POST /api/contacts/list` va `POST /api/contacts/filter` — ro'yxat

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

## `GET /api/contacts/data?phone=...` — shaxs ma'lumotlari eksporti {#subject-export}

Kompaniyada shu telefon raqami bo'yicha saqlangan hamma narsa bitta JSON'da: kontakt,
xotira (`client_memory`) va har bir qo'ng'iroq (sana, davomiylik, natija, xulosa, to'liq
transkript). Ma'lumot egasining so'rovi uchun. Huquq: `CONTACT_READ`. Audit: `SUBJECT_EXPORTED`.

`phone` istalgan yozuvda bo'lishi mumkin: `+998901234567` va `998901234567` ikkalasi ham
qidiriladi. Noto'g'ri raqam — `400 PHONE_INVALID`.

```json
{
  "accept": true,
  "data": {
    "phone": "+998901234567",
    "contact": { "id": 5, "name": "Aziz Karimov", "phone": "+998901234567" },
    "memory": { "preferredName": "Aziz aka", "preferredLanguage": "uz-UZ", "recentCalls": [] },
    "calls": [
      {
        "callId": 1042,
        "startedAt": "2026-09-10T06:12:00Z",
        "durationSec": 94,
        "disposition": "PROMISE_TO_PAY",
        "summary": "Mijoz 15-sentabrgacha to'lashga va'da berdi.",
        "transcript": "AGENT: Assalomu alaykum...\nCLIENT: Va alaykum assalom..."
      }
    ]
  }
}
```

`contact` yoki `memory` bo'lmasa — `null`.

---

## `DELETE /api/contacts/data?phone=...&confirm=true` — shaxs ma'lumotlarini o'chirish {#subject-erasure}

**Qaytarib bo'lmaydi.** `confirm=true` bo'lmasa — `400 SUBJECT_ERASURE_NOT_CONFIRMED`.
Huquq: `CONTACT_EDIT`. Audit: `SUBJECT_ERASED` (raqam niqoblangan holda).

| Ma'lumot | Nima bo'ladi |
|---|---|
| Kontakt, xotira (`client_memory`) | O'chiriladi |
| Transkriptlar | O'chiriladi |
| Qo'ng'iroq yozuvlari va ovozli xabarlar (MinIO) | Fayl va uning qatori o'chiriladi |
| Qo'ng'iroq xulosasi | `summary` → `[o'chirildi]`, `outcome` → `{}`, izohlar tozalanadi; `reasonCode`, va'da sanasi va summasi hisobot uchun qoladi |
| Qo'ng'iroq, nishon, o'tkazib yuborilgan qo'ng'iroq, konversiya qatorlari | Hisobot va billing uchun qoladi, lekin raqam qaytarib bo'lmaydigan tamg'a (`x` + 16 belgi) bilan almashtiriladi; nishonning `contextData`si tozalanadi, navbatdagi nishon `DONE` bo'ladi |
| Qo'ng'iroq voqealari (`call_event`) | `payload` tozalanadi |
| CRM'ga hali yetkazilmagan yozuvlar (outbox: eslatma, qo'ng'iroqlar tarixi) | `PENDING` va `DEAD` xabarlarning `payload`i tozalanadi; `PENDING` xabar `DEAD` bo'ladi va CRM'ga yuborilmaydi |
| DNC ro'yxati | **Qoladi**, aks holda raqamga yana qo'ng'iroq qilinardi |

CRM'ga allaqachon yuborilgan eslatmalar bu yerdan o'chirilmaydi, ularni CRM'ning o'zida
o'chirish kerak.

```json
{
  "accept": true,
  "data": { "phone": "+99890*****67", "callsAnonymized": 7, "filesDeleted": 5 }
}
```

Bu raqam bo'yicha hech narsa bo'lmasa ham `200`, nollar bilan.

---

## `POST /api/contacts/csv` — CSV import {#csv-import}

`Content-Type: text/csv` (yoki `text/plain`), body — CSV faylning o'zi (ko'pi bilan 5 000 000
belgi; uzunrog'i → `400 VALIDATION_FAILED`):

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

---

## `POST /api/contacts/scrub` — Telefon raqamlarini ommaviy tekshirish va tozalash (Batch Scrubbing) {#scrub}

Kompaniya qo'ng'iroq kampaniyasini boshlashdan oldin raqamlar ro'yxatini (E.164 formati, O'zbekiston/xalqaro prefikslar, operator ajratilishi hamda kompaniyaning Do-Not-Call ro'yxatida mavjudligi) bitta so'rovda tekshirib oladi.

Huquq: **CONTACT_READ**

**Request body** (`ContactScrubRequest`):
```json
{
  "phones": [
    "+998901234567",
    "+998011234567",
    "998931112233",
    "+12125551234",
    "invalid-phone"
  ]
}
```
*Maksimal hajm: 5 000 ta raqam.*

**Response** (`ContactScrubResponse`):
```json
{
  "success": true,
  "data": {
    "total": 5,
    "validCount": 3,
    "invalidCount": 2,
    "dncCount": 0,
    "items": [
      {
        "rawPhone": "+998901234567",
        "normalizedPhone": "+998901234567",
        "valid": true,
        "status": "VALID",
        "carrierOrRegion": "Beeline",
        "reason": null,
        "inDoNotCall": false
      },
      {
        "rawPhone": "+998011234567",
        "normalizedPhone": "+998011234567",
        "valid": false,
        "status": "UNALLOCATED_PREFIX",
        "carrierOrRegion": null,
        "reason": "unallocated or invalid Uzbekistan NDC prefix: 01",
        "inDoNotCall": false
      },
      {
        "rawPhone": "998931112233",
        "normalizedPhone": "+998931112233",
        "valid": true,
        "status": "VALID",
        "carrierOrRegion": "Ucell",
        "reason": null,
        "inDoNotCall": false
      },
      {
        "rawPhone": "+12125551234",
        "normalizedPhone": "+12125551234",
        "valid": true,
        "status": "VALID",
        "carrierOrRegion": "International",
        "reason": null,
        "inDoNotCall": false
      },
      {
        "rawPhone": "invalid-phone",
        "normalizedPhone": null,
        "valid": false,
        "status": "INVALID_FORMAT",
        "carrierOrRegion": null,
        "reason": "phone must not contain letters or special characters",
        "inDoNotCall": false
      }
    ]
  }
}
```
*Agar raqam kompaniyaning Do-Not-Call ro'yxatida bo'lsa: `valid: false`, `status: "BLOCKED"`, `inDoNotCall: true`, `reason: "number is on Do-Not-Call list"`.*

