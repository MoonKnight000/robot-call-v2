# Mijoz xotirasi (`/api/memory`)

Agent bir mijoz bilan avvalgi suhbatlarda nima bo'lganini eslab qoladi va keyingi
qo'ng'iroqda shundan foydalanadi. Xotira **kompaniya + telefon raqami** bo'yicha
saqlanadi (`client_memory` jadvali), ya'ni:

- kampaniyaga bog'liq emas — mijoz keyingi kampaniyaga qo'shilsa ham xotira turadi;
- outbound va inbound qo'ng'iroqlar bitta xotirani ko'radi (kiruvchi qo'ng'iroqda
  chaqiruvchi raqami bo'yicha topiladi);
- CRM'ga bog'liq emas — CRM bo'lmagan (CSV) rejimda ham ishlaydi.

**Qanday to'ldiriladi.** Har bir summary'si chiqqan qo'ng'iroq (va tugagan chat) dan keyin tizim avtomatik
yozadi: oxirgi **10 ta** suhbat (`MEMORY_KEPT_CALLS`; sana, ssenariy, natija, xulosa — promptga faqat eng yangi **3 tasi**
tushadi) va ssenariy
`outcomeSchema`sidagi faktlar (`promisedDate`, `reasonCode`, ...; yangi qiymat eskisini
almashtiradi). Operator esa qo'lda `preferredName`, `preferredLanguage`,
`operatorNotes` kiritadi. Promptda bu blok `MULTI-CALL MEMORY` sarlavhasi bilan
chiqadi; agentga "bazamizda yozilgan" demaslik buyurilgan.

**Til tanlash tartibi.** Ikkala yo'nalishda ham bir xil: CRM `preferredLanguage` →
xotiradagi `preferredLanguage` → oxirgi zaxira. Zaxira outbound'da nishon/kampaniya
tili, inbound'da esa javob beruvchi agentning tili (`ai_agent.language`).

**Telefon formati.** Yo'lda (`{phone}`) `PhoneNumbers` qabul qiladigan har qanday
ko'rinish: `998901234567`, `901234567`, `%2B998901234567`. `+` belgisini URL'da
kodlash (`%2B`) yoki tashlab yuborish kerak. Noto'g'ri raqam → `400 PHONE_INVALID`.

**Xonadon va mahsulotlar.** Suhbat davomida agent tool'lari ham xotiraga yozadi (yangi qo'ng'iroq
qo'shmasdan, faqat `facts`):

| Fakt | Kim yozadi | Ma'nosi |
|---|---|---|
| `flatSearch` | `searchFlats` | Mijoz nima qidirgani: "3 xonali, 60000 gacha, Yunusobod Residence" |
| `offeredFlats` | `searchFlats` | Taklif qilingan xonadonlar (majmua, blok, №, xona, narx) |
| `interestedFlat` | agentning `rememberFlatInterest` funksiyasi (`EXTRACT_VARIABLE`, `remember: true`) | Mijoz yoqtirgan / tanlagan xonadon va izohi |
| `productInterest`, `productSkus` | `searchCatalog` | Mahsulot katalogida nima so'ragani va ko'rsatilgan SKU'lar |

Keyingi qo'ng'iroq yoki chatda agent buni ko'radi ("o'tgan safar 3 xonali A blok №45 yoqqan edi");
narx eskirgan bo'lishi mumkin, shuning uchun aytishdan oldin qayta qidiradi.

Huquq: **CONTACT_READ** (`GET`) / **CONTACT_EDIT** (`PUT`, `PATCH`, `DELETE`) — ya'ni kontaktlar sahifasiga
kirish huquqi bo'lgan har kim ([roles.md](roles.md)).

---

### `GET /api/memory/{phone}` — Mijoz xotirasini olish

Xotira yo'q bo'lsa (kompaniya bu raqam bilan hali gaplashmagan, operator ham hech
narsa yozmagan) → `404 CLIENT_MEMORY_NOT_FOUND`. Frontend buni "birinchi suhbat"
deb ko'rsatadi.

**Javob (`ResponseData<ClientMemory>`):**

```json
{
  "accept": true,
  "data": {
    "id": 12,
    "companyId": 1,
    "phone": "+998901234567",
    "preferredName": "Anvar aka",
    "preferredLanguage": null,
    "operatorNotes": "Kechqurun 18:00 dan keyin qo'ng'iroq qilish qulay",
    "recentCalls": [
      {
        "id": "5f0c2a9e-8d1b-4c7e-9a43-1f2e3d4c5b6a",
        "at": "2026-09-04T10:12:31Z",
        "scenarioKey": "debt-collection",
        "disposition": "PROMISE_TO_PAY",
        "summary": "Mijoz 10-sentabrgacha 1 500 000 so'm to'lashga va'da berdi.",
        "callAttemptId": 9031,
        "chatSessionId": null
      },
      {
        "id": "c3a91f0d2",
        "at": "2026-08-28T09:40:05Z",
        "scenarioKey": "debt-collection",
        "disposition": "HUNG_UP",
        "summary": "Mijoz band ekanini aytib suhbatni to'xtatdi.",
        "callAttemptId": null,
        "chatSessionId": null
      }
    ],
    "facts": {
      "promisedDate": "2026-09-10",
      "promisedAmount": 1500000
    },
    "factSources": {
      "promisedDate": { "kind": "CALL", "callAttemptId": 9031, "chatSessionId": null, "tool": null, "at": "2026-09-04T10:12:31Z" }
    },
    "updatedAt": "2026-09-04T10:12:31Z"
  },
  "message": null,
  "messageCode": null,
  "errors": null
}
```

| Maydon | Tur | Izoh |
|---|---|---|
| `phone` | string | E.164 ko'rinishda normalizatsiya qilingan |
| `preferredName` | string \| null | Operator yozgan murojaat shakli ("Anvar aka") |
| `preferredLanguage` | string \| null | Operator yozgan til (`uz-UZ`, `ru-RU`); qo'ng'iroq tilini CRM'dan keyin belgilaydi (outbound ham, inbound ham) |
| `operatorNotes` | string \| null | Operator eslatmasi, promptga tushadi |
| `recentCalls[]` | array | Eng yangisi birinchi, ko'pi bilan 10 ta (`MEMORY_KEPT_CALLS`); promptga eng yangi 3 tasi tushadi. Har biri: `id` (tahrirlash/o'chirish uchun), `at`, `scenarioKey`, `disposition`, `summary`, `callAttemptId` (qo'ng'iroq bo'lsa — call detail'ga havola), `chatSessionId` (chat bo'lsa). `scenarioKey`/`disposition` eski migratsiya yozuvlarida, `callAttemptId`/`chatSessionId` Phase 3 dan oldingi yozuvlarda `null` |
| `facts` | object | Ssenariy `outcomeSchema`sidan, tool'lardan va operatordan yig'ilgan faktlar, yangi qiymat eskisini almashtiradi. Ko'pi bilan 50 ta (`MEMORY_MAX_FACTS`) — oshsa eng eski yozilgani o'chadi, operator yozgani hech qachon |
| `factSources` | object | Fakt nomi → kim yozgan: `{ kind, callAttemptId, chatSessionId, tool, at }`, `kind` — `CALL`, `CHAT`, `TOOL` yoki `OPERATOR`. `tool` — `searchCatalog`, `searchFlats` yoki funksiya nomi. Phase 3 dan oldin yozilgan faktda yozuv yo'q |
| `updatedAt` | string | Oxirgi yozilish vaqti |

---

### `PUT /api/memory/{phone}` — Operator qismini yangilash

Faqat operator maydonlari o'zgaradi; `recentCalls` va `facts` tizimniki, PUT ularga
tegmaydi. Xotira yo'q bo'lsa yaratiladi.

Maydon semantikasi: **`null` (yoki yuborilmagan) — o'zgarmaydi; `""` — tozalanadi;
matn — almashtiriladi.**

**So'rov:**

```json
{
  "preferredName": "Anvar aka",
  "preferredLanguage": "uz-UZ",
  "operatorNotes": "Kechqurun 18:00 dan keyin qo'ng'iroq qilish qulay"
}
```

| Maydon | Cheklov |
|---|---|
| `preferredName` | ≤ 100 belgi |
| `preferredLanguage` | ≤ 10 belgi (`uz-UZ`, `ru-RU`) |
| `operatorNotes` | ≤ 2000 belgi |

**Javob:** `200`, `ResponseData<ClientMemory>` — yuqoridagi shakl. Audit: `CLIENT_MEMORY_UPDATE`.

---

### `PUT /api/memory/{phone}/facts/{key}` — Faktni qo'lda yozish

Operator bitta faktni qo'shadi yoki almashtiradi; agent keyingi qo'ng'iroqda uni boshqa faktlar kabi eshitadi.
`factSources[key].kind` — `OPERATOR`. Operator yozgan fakt limit (`MEMORY_MAX_FACTS`) oshganda o'chirilmaydi.
Xotira yo'q bo'lsa yaratiladi.

`key` — harf bilan boshlanadi, faqat harf, raqam va `_`, ≤64 belgi (`visitDay`, `promisedDate`), aks holda
`400 CLIENT_MEMORY_FACT_KEY_INVALID`. Tana: `{ "value": "shanba kuni" }` — bo'sh emas, ≤500 belgi.

**Javob:** yangilangan `ClientMemory`. Audit: `CLIENT_MEMORY_FACT_UPSERT`.

### `DELETE /api/memory/{phone}/facts/{key}` — Faktni o'chirish

Fakt va uning manbasi o'chiriladi. Yo'q bo'lsa → `404 CLIENT_MEMORY_FACT_NOT_FOUND`.
**Javob:** yangilangan `ClientMemory`. Audit: `CLIENT_MEMORY_FACT_DELETE`.

> Keyingi qo'ng'iroq shu faktni qayta yozishi mumkin (masalan, ssenariy `outcomeSchema`si yana `promisedDate` chiqarsa).

### `PATCH /api/memory/{phone}/calls/{callId}` — Suhbat xulosasini tuzatish

`callId` — `recentCalls[].id`. Tana: `{ "summary": "..." }` — bo'sh emas, ≤1000 belgi. Agent keyingi qo'ng'iroqda
tuzatilgan xulosani eshitadi; sana, ssenariy va natija o'zgarmaydi. Yo'q bo'lsa → `404 CLIENT_MEMORY_CALL_NOT_FOUND`.
**Javob:** yangilangan `ClientMemory`. Audit: `CLIENT_MEMORY_CALL_UPDATE`.

### `DELETE /api/memory/{phone}/calls/{callId}` — Suhbatni xotiradan o'chirish

Faqat xotiradagi yozuv o'chadi — qo'ng'iroqning o'zi, transkripti va yozuvi qoladi. Yo'q bo'lsa →
`404 CLIENT_MEMORY_CALL_NOT_FOUND`. **Javob:** yangilangan `ClientMemory`. Audit: `CLIENT_MEMORY_CALL_DELETE`.

### `DELETE /api/memory/{phone}` — Butun xotirani tozalash

Operator eslatmalari, suhbatlar va faktlar — hammasi o'chadi; keyingi qo'ng'iroq "birinchi suhbat" bo'ladi.
Xotira bo'lmasa → `404 CLIENT_MEMORY_NOT_FOUND`. **Javob:** `ResponseData<Void>`. Audit: `CLIENT_MEMORY_DELETE`.

---

### Qo'ng'iroqda nima eslangani

AI agent suhbati boshlanganda promptga tushgan xotira qo'ng'iroq timeline'iga `MEMORY_RECALLED` hodisasi bo'lib
yoziladi ([reports.md](reports.md)) — operator keyin xotirani tahrirlasa ham, agent aynan nimani ko'rgani qoladi.

---

### O'chirilgan endpointlar

`GET/PUT /api/campaigns/{campaignId}/targets/{targetId}/memory` olib tashlandi — xotira
endi nishonga (kampaniya ichidagi qatorga) emas, mijozga bog'langan. Eski
`context_data` ichidagi `preferredName`/`operatorNotes`/`lastCallSummary` qiymatlari
`V6__client_memory.sql` migratsiyasida `client_memory`ga ko'chirilgan.
