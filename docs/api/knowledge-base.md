# Bilim bazasi (Knowledge Base) API

`uz.murodjon.robotcallv2.knowledgebase` · huquq: **KNOWLEDGE_BASE_READ** / **KNOWLEDGE_BASE_EDIT**

Kompaniyaning tez-tez so'raladigan savollariga tayyor javoblar katalogi: har bir yozuv —
bitta mavzu, bitta savol turkumi va uch tildagi javob (o'zbek majburiy, rus/ingliz
ixtiyoriy). Yozuv kalit so'zlar (`keywords`) orqali mijozning gapi bilan solishtiriladi.

> ⚠️ `KnowledgeBaseUseCase.findRelevantAnswer(...)` metodini `agent/` da hech kim
> chaqirmaydi — qo'ng'iroq paytida `KnowledgeBaseLookup.findRelevantKnowledge(...)` ishlaydi,
> u ham shu jadvaldan o'qiydi. Ya'ni javoblar qo'ng'iroqqa tushadi, lekin ikkita bir xil
> ishni qiladigan yo'l bor (§Javob qanday tanlanadi).

> ℹ️ Ikkalasi bir xil emas: **yozuv** topilsa uning matni AYNAN o'qib beriladi (tayyor,
> tekshirilgan javob), **manba** parchasi esa modelga kontekst sifatida beriladi va model
> uni o'z so'zi bilan qayta aytadi. Shuning uchun siyosat matnini yozuvga, uzun hujjatni
> manbaga qo'ying.

> ℹ️ Bilim bazasi ikki qismdan iborat: **yozuvlar** (qo'lda yoziladigan savol-javob,
> `/api/knowledge-base`) va **manbalar** (hujjat va havolalar, `/api/knowledge-base/sources`).
> Ikkalasi ham AI agentga bog'lanadi: `agentId` berilsa faqat o'sha agent ishlatadi,
> `null` bo'lsa — kompaniyaning barcha agentlari.

Umumiy javob shakli, xatolar va pagination konventsiyasi uchun
[README.md](README.md)ga qarang.

---

## Mavzu (`topic`)

`topic` — **erkin matn** (`VARCHAR(50)`), enum emas: kompaniya o'z mavzularini o'zi
nomlaydi, qat'iy ro'yxat esa o'ylab topilgan har bir mavzuni rad etardi. Saqlashda chetdagi
bo'shliqlar olib tashlanadi va **kichik harfga** keltiriladi (`" Payment "` → `"payment"`) —
bitta mavzu registr farqi bilan filtrda ikkiga bo'linmaydi. Seed yozuvlari `payment`,
`legal`, `terms`, `locations` ishlatadi: frontend tanlagichda shularni taklif qilib, yangi
mavzu kiritishga ham ruxsat berishi mumkin. (Hech qayerda ishlatilmagan `KnowledgeTopic`
enum'i olib tashlandi.)

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
| `agentId` | number | ❌ | Qaysi agentga tegishli. Berilmasa (`null`) — yozuv kompaniyaning barcha agentlariga ochiq |
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
    "agentId": 5,
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
{ "page": 0, "size": 20, "orders": { "CREATED_AT": "DESC" }, "agentId": 5, "search": "to'lov" }
```

| Maydon | Izoh |
|---|---|
| `page` / `size` | Standart `0` / `20`, maksimum `size` — `500` |
| `orders` | Saralanadigan ustunlar: `ID`, `KEY`, `TOPIC`, `TITLE`, `CREATED_AT`. Standart: `CREATED_AT DESC` |
| `agentId` | Berilsa — faqat shu agentning yozuvlari. Berilmasa — kompaniyaning hammasi |
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

---

# Bilim manbalari (Knowledge Sources) API

`/api/knowledge-base/sources` · huquq: **KNOWLEDGE_BASE_READ** / **KNOWLEDGE_BASE_EDIT**

Yozuvlar qo'lda yoziladi; manbalar esa tayyor hujjat (PDF, TXT, DOCX, CSV, XLSX, XLS)
yoki tashqi havola (URL) sifatida ro'yxatga olinadi va agentga biriktiriladi. Mahsulot
katalogi — alohida tur, `STRUCTURED_FEED` (§Katalog): u parchalanmaydi, har qatori alohida
mahsulot bo'ladi.

## Manba qanday ishlaydi

1. Fayl avval `POST /api/files?category=DOCUMENT` orqali yuklanadi, javobdagi `data.id`
   olinadi. URL manbaga fayl kerak emas.
2. `POST /api/knowledge-base/sources` manbani ro'yxatga oladi va uni **RabbitMQ navbatiga**
   qo'yadi (`knowledge.indexing`). Javob darhol qaytadi, `status: PENDING`.
3. Indekslovchi hujjatni o'qiydi (PDF — OpenPDF, DOCX/XLSX/XLS — Apache POI, TXT/CSV —
   UTF-8, URL — sahifa yuklab olinib teglardan tozalanadi), matnni ~900 belgilik
   parchalarga bo'ladi (150 belgi ustma-ust), har birini **Gemini embedding** modeli bilan
   vektorga aylantiradi va `knowledge_chunk` jadvaliga yozadi. Status `PROCESSING` →
   `INDEXED`, `chunkCount` to'ladi.
4. Qo'ng'iroq paytida mijozning savoli ham vektorga aylantiriladi va eng yaqin parchalar
   (kosinus o'xshashligi, ostona 0.62) modelning promptiga qo'yiladi. Model javobni o'z
   so'zi bilan aytadi — matnni o'qib bermaydi.
   **Hybrid** (NEXT-PLAN A.3): shu bilan birga savol so'zlari bo'yicha kalit so'z reytingi ham
   tuziladi va ikkala reyting RRF (reciprocal rank fusion) bilan birlashtiriladi — embedding past
   baholagan, lekin mijoz aytgan shartnoma raqami, mahsulot kodi yoki ismni o'z ichiga olgan parcha
   ham yuqoriga chiqadi. Matni bir xil parcha bir marta sanaladi.

> ℹ️ **Qidiruv faqat `useRag: true` agentlar uchun ishlaydi**
> ([ai-agents.md](ai-agents.md) → Advanced). O'chirilgan bo'lsa manbalar saqlanadi, lekin
> qo'ng'iroqqa tushmaydi.

> ℹ️ Embedding modeli mavjud bo'lmasa (`GEMINI_API_KEY` yo'q) tizim yiqilmaydi: parchalar
> vektorsiz saqlanadi va qidiruv kalit so'z bo'yicha ishlaydi. Sifati pastroq, lekin
> ishlaydi. `retry` keyinchalik vektorlarni to'ldiradi.

> ⚠️ Skanerlangan PDF ichida matn qatlami bo'lmaydi — bunday manba
> `FAILED` + `KNOWLEDGE_SOURCE_EMPTY` bo'lib qoladi.

---

## `POST /api/knowledge-base/sources` — manba(lar) qo'shish

Huquq: **KNOWLEDGE_BASE_EDIT**. Bitta so'rovda bir nechta hujjat yuboriladi.

```json
{
  "agentId": 5,
  "documents": [
    {
      "name": "Narxlar ro'yxati 2026",
      "sourceType": "PDF",
      "storedFileId": 42,
      "originalFileName": "pricing-2026.pdf"
    },
    { "name": "Savol-javob sahifasi", "sourceType": "URL", "url": "https://docs.company.uz/faq" }
  ]
}
```

| Maydon | Turi | Majburiymi | Izoh |
|---|---|---|---|
| `agentId` | number | ❌ | Berilsa — agent shu kompaniyaniki ekani tekshiriladi (`404 AI_AGENT_NOT_FOUND`). `null` — kompaniyaning barcha agentlari uchun |
| `documents` | array | ✅ (`@NotEmpty`) | Kamida bitta hujjat |
| `documents[].name` | string | ✅ (≤200) | UI da ko'rinadigan nom |
| `documents[].sourceType` | enum | ✅ | `PDF`, `TXT`, `DOCX`, `CSV`, `XLSX`, `XLS`, `URL`, `SITEMAP` (§Sitemap), `STRUCTURED_FEED` (§Katalog) |
| `documents[].url` | string | `sourceType=URL` yoki `SITEMAP` bo'lsa ✅ | Aks holda `400 KNOWLEDGE_SOURCE_URL_REQUIRED` |
| `documents[].storedFileId` | number | `URL` yoki `SITEMAP` bo'lmasa ✅ | `POST /api/files?category=DOCUMENT` qaytargan `data.id`. Berilmasa `400 KNOWLEDGE_SOURCE_FILE_REQUIRED` |
| `documents[].originalFileName` | string | ❌ (≤255) | Foydalanuvchi yuklagan fayl nomi |
| `documents[].feedItemsPath` | string | ❌ (≤200) | Faqat `STRUCTURED_FEED`: JSON javobdagi mahsulotlar massivining JSONPath'i (`$.data.items`). Bo'sh — butun javob massiv |
| `documents[].feedKeyField` | string | ❌ (≤100) | Faqat `STRUCTURED_FEED`: kalit ustun (SKU). Standart `sku`, katta-kichik harf farqi yo'q |
| `documents[].refreshMinutes` | int | ❌ (≥15) | Faqat URL'li `STRUCTURED_FEED` yoki `SITEMAP`: manba necha daqiqada avtomatik qayta yuklanadi. Bo'sh — faqat qo'lda (`/retry`) |

`SITEMAP` turi berilgan sitemap XML yoki sitemap index manzilidan sahifalar havolalarini avtomatik ajratib oladi va har bir sahifa matnini indekslaydi.
`STRUCTURED_FEED` uchun `url` yoki `storedFileId` dan biri kerak — ikkalasi ham bo'lmasa `400 KNOWLEDGE_FEED_LOCATION_REQUIRED`.
`KnowledgeSourceRow` javobida ham `feedItemsPath`, `feedKeyField`, `refreshMinutes` qaytadi; katalog uchun `chunkCount` — mahsulotlar soni.

Javob — yaratilgan manbalar ro'yxati (`KnowledgeSourceRow[]`):

```json
{
  "accept": true,
  "data": [
    {
      "id": 12,
      "companyId": 1,
      "agentId": 5,
      "agentName": "Savdo maslahatchisi",
      "name": "Narxlar ro'yxati 2026",
      "sourceType": "PDF",
      "originalFileName": "pricing-2026.pdf",
      "storedFileId": 42,
      "url": null,
      "status": "PENDING",
      "errorCode": null,
      "errorMessage": null,
      "lastIndexedAt": null,
      "chunkCount": 0,
      "createdAt": "2026-09-07T10:00:00Z"
    }
  ]
}
```

Yaratilganda status doim `PENDING` — indekslash fonda ketadi. Frontend ro'yxatni bir necha
soniyada qayta so'rab statusni kuzatadi.

| `status` | Ma'nosi |
|---|---|
| `PENDING` | Navbatda, hali o'qilmagan |
| `PROCESSING` | Hujjat o'qilib, parchalanib, vektorlanmoqda |
| `INDEXED` | Tayyor — qo'ng'iroqda ishlatiladi. `chunkCount` nechta parcha chiqqanini aytadi |
| `FAILED` | `errorCode` va `errorMessage` sababni aytadi. Tuzatib `/retry` chaqiring |

Eng ko'p uchraydigan `errorCode` lar: `KNOWLEDGE_SOURCE_EMPTY` (matn qatlami yo'q,
odatda skanerlangan PDF), `KNOWLEDGE_SOURCE_UNREADABLE` (buzuq yoki qo'llab-quvvatlanmagan
fayl), `KNOWLEDGE_SOURCE_FETCH_FAILED` (URL ochilmadi),
`KNOWLEDGE_SOURCE_FETCH_INTERRUPTED` (URL yuklanishi to'xtatildi — odatda server qayta ishga tushganda; `/retry`), `FILE_NOT_FOUND` (yuklangan fayl
o'chirilgan).

---

## Katalog (`STRUCTURED_FEED`) — mahsulot qidiruvi

Magazin, restoran menyusi yoki xizmatlar ro'yxati — minglab qatorli jadval. U hujjat kabi ~900
belgilik parchalarga bo'linmaydi: **har qator — bitta mahsulot**, alohida vektor bilan. Qo'ng'iroqda
agent mijoz aytgan mahsulotni ("qora 42 razmer krossovka") shu katalogdan qidiradi va buyurtma
tool'iga ([tools.md](tools.md)) katalogdagi haqiqiy SKU ni beradi.

```json
{
  "agentId": 5,
  "documents": [
    { "name": "Mahsulotlar", "sourceType": "STRUCTURED_FEED",
      "url": "https://shop.uz/api/products", "feedItemsPath": "$.data", "feedKeyField": "sku",
      "refreshMinutes": 60 }
  ]
}
```

**Format.** Fayl sifatida: CSV (birinchi qator — ustun nomlari; `,` / `;` / tab avtomatik aniqlanadi),
XLSX/XLS (birinchi varaq, birinchi bo'sh bo'lmagan qator — sarlavha), yoki `.json` (obyektlar massivi).
URL sifatida: javob `[` yoki `{` bilan boshlansa JSON (`feedItemsPath` dagi massiv), aks holda CSV.
JSON obyektning har bir maydoni — ustun; ichma-ich qiymat JSON matn sifatida saqlanadi.

**Kalit.** Har qatorda `feedKeyField` (standart `sku`) ustuni bo'lishi kerak. Kalitsiz qator tashlab
yuboriladi, takroriy kalitdan birinchisi qoladi. Birorta qatorda ham kalit bo'lmasa, manba
`FAILED` + `CATALOG_KEY_FIELD_MISSING` bo'ladi — feed noto'g'ri ustunga qaratilgan.

**Chegaralar.** 50 000 qator, URL javobi 32 MB va 30 soniya. Qator matni (barcha ustunlar
`ustun: qiymat; …`) 1000 belgigacha. Feed faqat ochiq URL'dan yuklanadi — header/secret bilan himoyalangan
feed hozircha qo'llab-quvvatlanmaydi (§6.1 dagi manzil tekshiruvi xuddi URL manbadagidek).

**Yangilanish.** `refreshMinutes` berilgan URL feed shu oraliqda qayta yuklanadi (bir daqiqalik
tekshiruv, ikki instance'da takrorlanmaydi). Faqat `INDEXED` feed yangilanadi. `FAILED` feed
`/retry` ni kutadi, chunki xato qatorda yozilgan va har daqiqa urinish uni tuzatmaydi. Qayta
yuklashda faqat matni o'zgargan qatorlar qayta vektorlanadi. Yuklash yiqilsa, eski mahsulotlar
**o'chiriladi**: qo'ng'iroqda eskirgan narx aytilgandan ko'ra "topilmadi" yaxshiroq.

**Qidiruv.** Katalog Postgres'da (`catalog_item`, pgvector HNSW indeksi + matn indeksi) saqlanadi.
Mijozning so'zlari bo'yicha ham, embedding bo'yicha ham 50 tadan nomzod olinadi. Keyin ular mijoz
so'zlarining qancha qismini o'z ichiga olgani bo'yicha tartiblanadi, embedding esa teng holatda
hal qiladi. Raqam faqat o'ziga mos keladi ("42" ≠ "420"); 4+ harfli so'z qo'shimchasiz ham topiladi
("krossovkalar" → "krossovka"). "menga", "kerak", "bormi" kabi so'zlar hisobga olinmaydi.

**Agentda.** Agentga ko'rinadigan `INDEXED` katalog bo'lsa, `searchCatalog(query)` tool'i avtomatik
beriladi — ikkala pipeline'da, `useRag` dan qat'i nazar. Tool eng mos 5 qatorni `SKU …: ustun: qiymat; …`
ko'rinishida qaytaradi. Undagi raqamlar (narx, qoldiq) agent aytishi mumkin bo'lgan raqamlarga
qo'shiladi (fact guard). Cascade qo'ng'iroqda oxirgi natija keyingi qidiruvgacha har turn promptida
turadi — buyurtma tool'i SKU'ni o'sha yerdan oladi.

---

## `GET /api/knowledge-base/sources` — ro'yxat

Huquq: **KNOWLEDGE_BASE_READ**. Ixtiyoriy `?agentId=5` — faqat shu agentning manbalari;
berilmasa kompaniyaning hammasi (`createdAt DESC`). Javob — `KnowledgeSourceRow[]`.

---

## `GET /api/knowledge-base/sources/{id}` — bitta manba

Huquq: **KNOWLEDGE_BASE_READ**. Topilmasa yoki boshqa kompaniyaniki bo'lsa —
`404 KNOWLEDGE_SOURCE_NOT_FOUND`.

---

## `PUT /api/knowledge-base/sources/{id}` — nomini yoki agentini o'zgartirish

Huquq: **KNOWLEDGE_BASE_EDIT**.

```json
{ "name": "Narxlar ro'yxati (2026-yil sentyabr)", "agentId": 6, "url": null }
```

Berilmagan (`null`) maydon o'zgarmaydi. `sourceType`, `storedFileId` va
`originalFileName` tahrirlanmaydi — hujjatni almashtirish uchun yangisini qo'shing.
Katalog feed uchun `refreshMinutes` (≥15) ham yuborish mumkin — qayta indekslamaydi, faqat
keyingi yangilanish vaqtini o'zgartiradi. `feedItemsPath` va `feedKeyField` tahrirlanmaydi.

`url` o'zgarsa manba **qayta indekslanadi** (status `PENDING` ga tushadi). Faqat nom yoki
agent o'zgarsa parchalar o'sha-o'sha qoladi — bir xil vektorlarni qayta hisoblash
embedding kvotasini behuda sarflaydi.

---

## `POST /api/knowledge-base/sources/{id}/retry` — qayta indekslash

Huquq: **KNOWLEDGE_BASE_EDIT**. Manbani navbatga qaytaradi: xatosi tozalanadi, status
`PENDING` bo'ladi, eski parchalar yangisiga almashtiriladi. `FAILED` manbani tuzatgandan
keyin ham, hujjat mazmuni o'zgargan URL uchun ham shu chaqiriladi.

Javob — `status: PENDING` bilan yangilangan `KnowledgeSourceRow`.

---

## `DELETE /api/knowledge-base/sources/{id}` — o'chirish

Huquq: **KNOWLEDGE_BASE_EDIT**. Haqiqiy o'chirish; javob `data: null`. Manbaning barcha
parchalari ham o'chadi va qidiruv keshi darhol yangilanadi — o'chirilgan hujjat keyingi
qo'ng'iroqda gapirmaydi.

> Yuklangan faylning o'zi `stored_file` da qoladi — uni `/api/files` orqali alohida
> boshqarasiz.
