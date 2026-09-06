# Mijoz xotirasi (`/api/memory`)

Agent bir mijoz bilan avvalgi suhbatlarda nima bo'lganini eslab qoladi va keyingi
qo'ng'iroqda shundan foydalanadi. Xotira **kompaniya + telefon raqami** bo'yicha
saqlanadi (`client_memory` jadvali), ya'ni:

- kampaniyaga bog'liq emas — mijoz keyingi kampaniyaga qo'shilsa ham xotira turadi;
- outbound va inbound qo'ng'iroqlar bitta xotirani ko'radi (kiruvchi qo'ng'iroqda
  chaqiruvchi raqami bo'yicha topiladi);
- CRM'ga bog'liq emas — CRM bo'lmagan (CSV) rejimda ham ishlaydi.

**Qanday to'ldiriladi.** Har bir summary'si chiqqan qo'ng'iroqdan keyin tizim avtomatik
yozadi: oxirgi **3 ta** suhbat (sana, ssenariy, natija, xulosa) va ssenariy
`outcomeSchema`sidagi faktlar (`promisedDate`, `reasonCode`, ...; yangi qiymat eskisini
almashtiradi). Operator esa qo'lda `preferredName`, `preferredLanguage`,
`operatorNotes` kiritadi. Promptda bu blok `MULTI-CALL MEMORY` sarlavhasi bilan
chiqadi; agentga "bazamizda yozilgan" demaslik buyurilgan.

**Til tanlash tartibi (outbound):** CRM `preferredLanguage` → xotiradagi
`preferredLanguage` → nishon/kampaniya tili.

**Telefon formati.** Yo'lda (`{phone}`) `PhoneNumbers` qabul qiladigan har qanday
ko'rinish: `998901234567`, `901234567`, `%2B998901234567`. `+` belgisini URL'da
kodlash (`%2B`) yoki tashlab yuborish kerak. Noto'g'ri raqam → `400 PHONE_INVALID`.

Huquq: **CONTACT_READ** (`GET`) / **CONTACT_EDIT** (`PUT`) — ya'ni kontaktlar sahifasiga
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
        "at": "2026-09-04T10:12:31Z",
        "scenarioKey": "debt-collection",
        "disposition": "PROMISE_TO_PAY",
        "summary": "Mijoz 10-sentabrgacha 1 500 000 so'm to'lashga va'da berdi."
      },
      {
        "at": "2026-08-28T09:40:05Z",
        "scenarioKey": "debt-collection",
        "disposition": "HUNG_UP",
        "summary": "Mijoz band ekanini aytib suhbatni to'xtatdi."
      }
    ],
    "facts": {
      "promisedDate": "2026-09-10",
      "promisedAmount": 1500000
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
| `preferredLanguage` | string \| null | Operator yozgan til (`uz-UZ`, `ru-RU`); outbound tilini CRM'dan keyin belgilaydi |
| `operatorNotes` | string \| null | Operator eslatmasi, promptga tushadi |
| `recentCalls[]` | array | Eng yangisi birinchi, ko'pi bilan 3 ta. `scenarioKey`/`disposition` eski migratsiya yozuvlarida `null` bo'lishi mumkin |
| `facts` | object | Ssenariy `outcomeSchema`sidan yig'ilgan faktlar, yangi qiymat eskisini almashtiradi |
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

### O'chirilgan endpointlar

`GET/PUT /api/campaigns/{campaignId}/targets/{targetId}/memory` olib tashlandi — xotira
endi nishonga (kampaniya ichidagi qatorga) emas, mijozga bog'langan. Eski
`context_data` ichidagi `preferredName`/`operatorNotes`/`lastCallSummary` qiymatlari
`V6__client_memory.sql` migratsiyasida `client_memory`ga ko'chirilgan.
