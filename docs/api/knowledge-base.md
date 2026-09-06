# Bilim bazasi (Knowledge Base) API

`uz.murodjon.robotcallv2.knowledgebase` · huquq: **KNOWLEDGE_BASE_READ** / **KNOWLEDGE_BASE_EDIT**

Kompaniyaning tez-tez so'raladigan savollariga tayyor javoblar katalogi: har bir yozuv —
bitta mavzu, bitta savol turkumi va uch tildagi javob (o'zbek majburiy, rus/ingliz
ixtiyoriy). Yozuv kalit so'zlar (`keywords`) orqali mijozning gapi bilan solishtiriladi.

> ⚠️ **Hozircha faqat CRUD.** `KnowledgeBaseUseCase.findRelevantAnswer(...)` metodi
> yozilgan, lekin uni `DialogEngine`/`agent/` da hech kim chaqirmaydi — ya'ni bazadagi
> javoblar hali qo'ng'iroqqa tushmaydi. Frontend sahifasini qurish uchun endpointlar
> to'liq ishlaydi.

Umumiy javob shakli, xatolar va pagination konventsiyasi uchun
[README.md](README.md)ga qarang.

---

## Mavzu (`topic`)

`topic` — **erkin matn** (`VARCHAR(50)`), enum emas: DTO va `KnowledgeItem` da u `String`.
Kodda `KnowledgeTopic` enum'i bor (`PAYMENT`, `LEGAL`, `TERMS`, `LOCATIONS`, `GENERAL`),
lekin API uni tekshirmaydi va `V7__knowledge_base.sql` seed'i **kichik harfda**
(`payment`, `legal`, `terms`, `locations`) yozadi. Frontend tanlagichni shu beshtalik
bilan cheklashi tavsiya etiladi; qiymatni bitta registrga keltirib yuboring.

---

## `POST /api/knowledge-base` — yangi yozuv

Huquq: **KNOWLEDGE_BASE_EDIT**.

**Request body** (`KnowledgeItemCreateRequest`):

```json
{
  "key": "payment_methods",
  "topic": "payment",
  "title": "To'lov usullari",
  "answerUz": "To'lovni Click, Payme yoki bank kassasida shartnoma raqamingizni ko'rsatib amalga oshirishingiz mumkin.",
  "answerRu": "Оплату можно произвести через Click, Payme или в кассах банков, указав номер договора.",
  "answerEn": "You can pay via Click, Payme or at a bank branch using your contract number.",
  "keywords": "click,payme,to'lash,qanday to'layman,оплатить,how to pay"
}
```

| Maydon | Turi | Majburiymi | Izoh |
|---|---|---|---|
| `key` | string | ✅ (`@NotBlank`, ≤100) | Yozuvning barqaror kaliti (`item_key` ustuni). Mijoz gapida shu matn uchrasa ham moslik hisoblanadi |
| `topic` | string | ✅ (`@NotBlank`, ≤50) | Mavzu (yuqoriga qarang) |
| `title` | string | ✅ (`@NotBlank`, ≤255) | UI da ko'rinadigan sarlavha |
| `answerUz` | string | ✅ (`@NotBlank`) | O'zbekcha javob — majburiy, tayanch til |
| `answerRu` | string | ❌ | Ruscha javob |
| `answerEn` | string | ❌ | Inglizcha javob |
| `keywords` | string | ❌ | Vergul/nuqta-vergul/probel bilan ajratilgan kalit so'zlar. Serverda **kichik harfga** keltiriladi; berilmasa `""` |

Yangi yozuv har doim `active: true` bilan yaratiladi.

**Response** (`KnowledgeItemResponse`):

```json
{
  "accept": true,
  "data": {
    "id": 7,
    "companyId": 1,
    "key": "payment_methods",
    "topic": "payment",
    "title": "To'lov usullari",
    "answerUz": "To'lovni Click, Payme yoki bank kassasida...",
    "answerRu": "Оплату можно произвести через Click, Payme...",
    "answerEn": "You can pay via Click, Payme...",
    "keywords": "click,payme,to'lash,qanday to'layman,оплатить,how to pay",
    "active": true,
    "createdAt": "2026-09-05T10:00:00Z",
    "updatedAt": "2026-09-05T10:00:00Z"
  },
  "message": null,
  "messageCode": null,
  "errors": null
}
```

> ℹ️ `key` bo'yicha unikallik **majburlanmaydi** — bazada `idx_knowledge_key` oddiy
> (unique bo'lmagan) indeks. Bir xil kalitli ikkita yozuv yaratilsa, moslik izlashda
> ro'yxatdagi birinchisi qaytadi.

---

## `GET /api/knowledge-base/{id}` — bitta yozuv

Huquq: **KNOWLEDGE_BASE_READ**. Javob — `KnowledgeItemResponse`. Topilmasa yoki boshqa
kompaniyaniki bo'lsa — `404 KNOWLEDGE_ITEM_NOT_FOUND`.

---

## `PUT /api/knowledge-base/{id}` — tahrirlash

Huquq: **KNOWLEDGE_BASE_EDIT**. Body (`KnowledgeItemUpdateRequest`) — `POST` bilan bir
xil, ustiga `active` (`boolean`) maydoni qo'shiladi. `active: false` — yozuv saqlanadi,
lekin javob izlashda e'tiborga olinmaydi.

Javob — yangilangan `KnowledgeItemResponse`.

---

## `DELETE /api/knowledge-base/{id}` — o'chirish

Huquq: **KNOWLEDGE_BASE_EDIT**. Body yo'q, javob `data: null`. Bu **haqiqiy o'chirish**
(soft-delete emas) — vaqtincha o'chirish uchun `PUT` bilan `active: false` yuboring.

---

## `POST /api/knowledge-base/list` va `POST /api/knowledge-base/filter` — ro'yxat

Huquq: **KNOWLEDGE_BASE_READ**. Ikkala path bir xil metodga boradi.

Body — `KnowledgeItemFilter`:

```json
{ "page": 0, "size": 20, "orders": { "CREATED_AT": "DESC" }, "search": "to'lov" }
```

| Maydon | Izoh |
|---|---|
| `page` / `size` | Standart `0` / `20`, maksimum `size` — `500` |
| `orders` | Saralanadigan ustunlar: `ID`, `KEY`, `TOPIC`, `TITLE`, `CREATED_AT`. Standart: `CREATED_AT DESC` |
| `search` | `key`, `title`, `keywords` yoki `topic` bo'yicha erkin qidiruv (registrga bog'liq emas) |

Javob — `PageableData<KnowledgeItemResponse>`. Ro'yxat `active` bo'yicha filtrlanmaydi —
o'chirilgan yozuvlar ham qaytadi (`active: false` bilan).

---

## Javob qanday tanlanadi (`findRelevantAnswer`)

Ichki API (REST endpoint emas): kompaniyaning **faol** yozuvlari ketma-ket ko'rib
chiqiladi va birinchi mos kelgani qaytadi.

1. Mijoz gapi kichik harfga keltiriladi.
2. Gapda yozuvning `key` i uchrasa — moslik.
3. Yoki `keywords` dagi biror so'z gapda uchrasa — moslik.
4. Til bo'yicha javob tanlanadi: `ru-*` → `answerRu`, `en-*` → `answerEn`, qolgan
   hamma holatda `answerUz` (mos til bo'sh bo'lsa ham `answerUz` ga qaytadi).

Bu **kalit so'z qidiruvi**, semantik (embedding) qidiruv emas — sinonim va noto'g'ri
yozilgan so'z topilmaydi, shuning uchun `keywords` ga ikkala tildagi variantlarni ham
yozing.
