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
{
  "name": "Aziz Karimov", "phone": "998901234567", "address": "Toshkent", "tags": "vip,doimiy", "notes": "Doimiy mijoz",
  "customFields": { "contract_no": "A-1042", "preferred_time": "kechqurun" }
}
```

| Maydon | Majburiymi | Izoh |
|---|---|---|
| `name` | ✅ (`@NotBlank`) | — |
| `phone` | ✅ (`@NotBlank`) | telefon bo'yicha dublikat rad etiladi (`409`) |
| `address` | ❌ | — |
| `tags` | ❌ | vergul bilan ajratilgan |
| `notes` | ❌ | — |
| `customFields` | ❌ | `{ "kalit": "qiymat" }` — erkin maydonlar, [cheklovlar](#custom-fields); `null` yoki yo'q — bo'sh to'plam |

**Response** (`Contact`):

```json
{
  "id": 12,
  "name": "Aziz Karimov",
  "phone": "998901234567",
  "address": "Toshkent",
  "tags": "vip,doimiy",
  "notes": "Doimiy mijoz",
  "customFields": { "contract_no": "A-1042", "preferred_time": "kechqurun" },
  "createdAt": "2026-07-20T09:00:00Z"
}
```

`customFields` javobda doim bor — maydon bo'lmasa `{}`.

### Custom fields (erkin maydonlar) {#custom-fields}

Retell uslubidagi kontakt maydonlari: `string → string` xaritasi, JSON obyekt sifatida
(`{ "contract_no": "A-1042" }`). Cheklovlar:

| Qoida | Buzilsa |
|---|---|
| ko'pi bilan 50 ta kalit | `400 CONTACT_CUSTOM_FIELDS_TOO_MANY` |
| kalit `^[A-Za-z_][A-Za-z0-9_]{0,63}$` (harf/raqam/`_`, raqam bilan boshlanmaydi, ≤ 64 belgi) | `400 CONTACT_CUSTOM_FIELD_INVALID` (argument — kalit) |
| qiymat `null` emas va ≤ 1000 belgi | `400 CONTACT_CUSTOM_FIELD_INVALID` |
| kalit uchun [kontakt maydoni](#contact-fields) ta'riflangan bo'lsa — qiymat uning turiga mos | `400 CONTACT_FIELD_VALUE_INVALID` |

Qiymatlarni AI agent ham avtomatik yozadi: agentning post-call extraction maydonida
`contactField` berilgan bo'lsa, qo'ng'iroqdan keyin ajratilgan qiymat shu kalit bilan
kontaktga qo'shiladi (mavjud kalitlar ustidan yoziladi, boshqalari saqlanadi; kontakt
bo'lmasa — telefon raqami ism sifatida yangi kontakt yaratiladi; noto'g'ri kalit yoki bo'sh
qiymat jimgina tashlanadi) — [ai-agents.md](ai-agents.md) §9.
CSV import/eksport custom field'larni o'qimaydi va yozmaydi.

---

## `POST /api/contacts/list` va `POST /api/contacts/filter` — ro'yxat

Body — `ContactFilter`:

```json
{
  "page": 0, "size": 20, "orders": { "NAME": "ASC" }, "search": "Aziz",
  "tags": ["vip"], "customFields": { "lead_status": "hot" },
  "lastCallFrom": "2026-11-01T00:00:00Z", "lastCallTo": "2026-11-06T23:59:59Z"
}
```

Filtrlarning hammasi ixtiyoriy; bo'sh/`null` — toraytirmaydi, berilganlari AND bilan birlashadi.

| Maydon | Ma'nosi |
|---|---|
| `search` | ism, telefon yoki istalgan custom field qiymati ichida qidiruv (katta-kichik harf farqsiz) |
| `tags` | shu teglardan **kamida bittasi** bor kontaktlar |
| `customFields` | `{ kalit: qiymat }` — har bir kalit qiymati aynan teng (katta-kichik harf farqsiz); `null` qiymatli kalit e'tiborsiz |
| `lastCallFrom` / `lastCallTo` | oxirgi qo'ng'irog'i shu oraliqda boshlangan kontaktlar (ISO instant); berilsa, hech qo'ng'iroq qilinmagan kontakt chiqmaydi |

Saralanadigan ustunlar: `ID`, `NAME`, `PHONE`, `CREATED_AT`. Standart: `NAME ASC`.

Javob — `PageableData<ContactRow>`: `Contact` maydonlari + ikkita hosila maydon:

```json
{
  "id": 12, "name": "Aziz Karimov", "phone": "+998901234567", "address": "Toshkent",
  "tags": "vip,doimiy", "notes": "Doimiy mijoz", "customFields": { "lead_status": "hot" },
  "createdAt": "2026-07-20T09:00:00Z",
  "callCount": 3,
  "lastCallAt": "2026-11-05T10:12:00Z",
  "crmProvider": "BITRIX24",
  "externalId": "4821"
}
```

| Maydon | Izoh |
|---|---|
| `callCount` | shu raqamga barcha qo'ng'iroqlar soni (ulanmaganlari ham), hech bo'lmasa — `0` |
| `lastCallAt` | eng yangi qo'ng'iroq boshlangan vaqt, hech bo'lmasa — `null` |
| `crmProvider` / `externalId` | kontakt qaysi CRM yozuvidan import qilingan ([crm.md#contact-sync](crm.md#contact-sync)); import qilinmagan bo'lsa — `null` |

---

## Kontakt maydonlari (contact fields) {#contact-fields}

Kompaniya darajasidagi **turlangan** maydon ta'riflari — kontakt `customFields` kalitlarining
tavsifi (Retell'dagi "Manage contact fields"). Ta'rif bo'lgan kalitning qiymati kontakt
yaratish/yangilashda turiga qarab tekshiriladi; ta'rifsiz kalitlar avvalgidek erkin matn.
Huquq: o'qish — **CONTACT_READ**, o'zgartirish — **CONTACT_EDIT**.

`ContactField` shakli:

```json
{
  "id": 3,
  "key": "lead_status",
  "label": "Lid holati",
  "type": "ENUM",
  "options": ["hot", "warm", "cold"],
  "description": "Mijozning qiziqish darajasi",
  "extractionKey": "lead_status",
  "updateMode": "OVERWRITE",
  "createdAt": "2026-11-06T09:00:00Z"
}
```

| Maydon | Izoh |
|---|---|
| `key` | `customFields` kaliti va qo'ng'iroq o'zgaruvchisi nomi. Harf bilan boshlanadi, harf/raqam/`_`, ≤ 64. `name`, `phone`, `address`, `tags`, `notes`, `id` va `contact…`/`external…` bilan boshlanadigan nomlar band. Kompaniyada katta-kichik harf farqsiz takrorlanmaydi. **Yaratilgandan keyin o'zgarmaydi.** |
| `label` | jadval ustuni sarlavhasi, ≤ 128 |
| `type` | `TEXT` · `NUMBER` · `BOOLEAN` (`true`/`false`) · `ENUM` · `DATE` (`2026-11-06`) · `DATETIME` (`2026-11-06T09:00:00Z` yoki offset bilan). **Yaratilgandan keyin o'zgarmaydi.** |
| `options` | faqat `ENUM` uchun: 1–100 ta takrorlanmaydigan, bo'sh bo'lmagan qiymat; boshqa turlarda bo'sh bo'lishi shart |
| `description` | maydon nimani saqlaydi; `ACCUMULATE` rejimida LLM uchun birlashtirish ko'rsatmasi ham shu |
| `extractionKey` | qo'ng'iroqdan keyin qaysi post-call extraction maydoni (agent `dataNeeded[].id`) bu maydonni to'ldiradi; `null` — hech qaysi. Harf/raqam/`_`, ≤ 64 |
| `updateMode` | `OVERWRITE` (standart) — doim ustidan yozadi · `FILL_IF_EMPTY` — faqat bo'sh bo'lsa · `ACCUMULATE` — eski va yangi qiymat LLM bilan birlashtiriladi (faqat `TEXT`) |

### Qo'ng'iroqdan keyin to'ldirish {#contact-fields-post-call}

Har bir yakunlangan qo'ng'iroqdan keyin post-call extraction natijasi (`outcome`) telefon
raqami bo'yicha kontaktga yoziladi (kontakt yo'q bo'lsa — raqam ism sifatida yangisi yaratiladi):

1. `extractionKey` berilgan har bir kompaniya maydoni `outcome[extractionKey]` qiymatini oladi
   (agent `dataNeeded` maydoni yoki ssenariy `outcomeSchema` kaliti) — barcha agentlarga amal qiladi.
2. Agentning `dataNeeded[].contactField` i ham ishlaydi (avvalgidek). Bir xil kalitga ikkalasi
   ham qiymat bersa — **kompaniya maydoni ustun**. Kompaniyada ta'rifi yo'q kalit — doim `OVERWRITE`.
3. Qiymat `updateMode` bo'yicha yoziladi:

| `updateMode` | Saqlangan qiymat bo'sh | Saqlangan qiymat bor |
|---|---|---|
| `OVERWRITE` | yoziladi | yangisi bilan almashtiriladi |
| `FILL_IF_EMPTY` | yoziladi | o'zgarmaydi |
| `ACCUMULATE` | yoziladi | agentning `fastLlmModel` i (yo'q bo'lsa — platformaning summary modeli; kompaniyaning o'z LLM'i bo'lsa — o'sha) `description` ko'rsatmasi bo'yicha eski va yangisini bitta matnga birlashtiradi; model javob bermasa — ikkalasi bo'sh qator bilan ketma-ket saqlanadi. Natija ≤ 1000 belgi (oshsa — eng eski qismi qisqaradi). Saqlangan matn yangi qiymatni aynan o'z ichiga olsa — qayta birlashtirilmaydi |

Bo'sh qiymat, noto'g'ri kalit, maydon turiga mos kelmagan qiymat va 50 tadan ortiq kalit —
jimgina tashlanadi (xato qaytmaydi). `ACCUMULATE` birlashtirishining tokenlari balansdan yechiladi
(ledger `contact-field-merge:<callId>:<key>`, `llm_usage.purpose = CONTACT_FIELD_MERGE`);
kompaniyaning o'z LLM'ida — yechilmaydi.

CRM'dan import qilingan kontaktda shu yozuv ham CRM'ga qaytarib yuboriladi ([crm.md#contact-push](crm.md#contact-push)).

Eski qo'ng'iroqlardan to'ldirish (Backfill) — `POST /api/call-reanalyses` `mode: CONTACTS_STORED | CONTACTS_REEXTRACT` bilan
([call-reanalyses.md#contact-backfill](call-reanalyses.md#contact-backfill)).

Kompaniyada ko'pi bilan 50 ta maydon.

### `GET /api/contacts/fields` — ro'yxat

Javob — `ContactField[]`, yaratilish tartibida.

### `POST /api/contacts/fields` — yangi maydon

Body (`CreateContactFieldRequest`): `key`, `label`, `type` — majburiy; `options`, `description`,
`extractionKey`, `updateMode` (`null` → `OVERWRITE`) — ixtiyoriy. Javob — yaratilgan `ContactField`.

### `PUT /api/contacts/fields/{id}` — tahrirlash

Body (`UpdateContactFieldRequest`): `label` (majburiy), `options`, `description`, `extractionKey`,
`updateMode` — hammasi **almashtiriladi** (`null` — tozalanadi, `updateMode: null` → `OVERWRITE`).
`key` va `type` o'zgarmaydi. Javob — yangilangan `ContactField`.

### `DELETE /api/contacts/fields/{id}` — o'chirish

Ta'rif o'chadi **va shu kalit kompaniyaning barcha kontaktlari `customFields` idan olib tashlanadi**.
Javob — `data: null`.

### Xatolar

| Holat | Kod |
|---|---|
| kalit formati noto'g'ri yoki band | `400 CONTACT_FIELD_KEY_INVALID` |
| `type` yo'q | `400 VALIDATION_FAILED` |
| `options` noto'g'ri | `400 CONTACT_FIELD_OPTIONS_INVALID` |
| `ACCUMULATE` `TEXT` bo'lmagan maydonda | `400 CONTACT_FIELD_ACCUMULATE_TEXT_ONLY` |
| `extractionKey` formati noto'g'ri | `400 CONTACT_FIELD_EXTRACTION_KEY_INVALID` |
| 50 tadan ko'p | `400 CONTACT_FIELDS_TOO_MANY` |
| kalit allaqachon bor | `409 CONTACT_FIELD_KEY_EXISTS` |
| maydon topilmadi | `404 CONTACT_FIELD_NOT_FOUND` |
| kontakt yaratish/yangilashda qiymat maydon turiga mos emas | `400 CONTACT_FIELD_VALUE_INVALID` (argumentlar — kalit, tur) |

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
{ "name": "Aziz Karimov", "address": "Toshkent", "tags": "vip", "notes": "Yangilangan izoh", "customFields": { "contract_no": "A-1042" } }
```

`customFields`: `null` yoki yo'q — saqlangan to'plam o'zgarmaydi; xarita berilsa — **butun
to'plam almashtiriladi** (berilmagan kalitlar o'chadi, `{}` hammasini tozalaydi).
Cheklovlar — [yuqorida](#custom-fields).

Kontakt CRM'dan import qilingan bo'lsa va `customFields` o'zgargan bo'lsa — `pushMappings` dagi qiymatlar
CRM yozuviga fon rejimida qaytarib yoziladi ([crm.md#contact-push](crm.md#contact-push)).

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

