# Bilimlar bazasi + Mijoz xotirasi — yaxshilash rejasi (2026-11-06)

Maqsad: Retell'ning Knowledge Base'idan kuchliroq KB (sifat, ko'rinuvchanlik, boshqaruv) va
hamma joyda bir xil ishlaydigan, tahrirlanadigan mijoz xotirasi. Bosqichma-bosqich, har bosqich
alohida tasdiqlanadi.

## Hozirgi holat (audit, kodga qarab tekshirilgan)

**KB backend**
- Ikki tizim: qo'lda Q&A (`KnowledgeItem`) va manbalar (`KnowledgeSource` → `knowledge_chunk`).
- `knowledge_chunk.embedding` — **BYTEA** (pgvector emas, `V1__baseline.sql:2020`), qidiruv JVM ichida
  brute-force kosinus, 64 kompaniyalik LRU (`KnowledgeRetrievalService:60`). Katalog esa allaqachon
  pgvector + HNSW + tsvector.
- Chunk kalit so'z qidiruvi — oddiy `contains` (BM25/tsvector yo'q). Reranker yo'q.
- Q&A yozuvlar — substring kalit so'z, birinchi moslik g'olib, embedding yo'q → qisqa so'zlarda xato moslik.
- Sitemap: ko'pi bilan 50 sahifa, hammasi bitta matnga qo'shiladi → sahifa URL'i (manzil) yo'qoladi.
- URL/sitemap yangilanganda hamma chunk qayta embed qilinadi (hash diff yo'q); `FAILED` hech qachon
  avtomatik qayta urinilmaydi.
- Navbatdagi query embedding — sinxron, timeout'siz; retrieval metrikasi yo'q, qaysi parcha
  ishlatilgani qo'ng'iroq trace'iga yozilmaydi.
- Endpoint yo'q: test qidiruv, chunk ko'rish/tahrirlash, manbani o'chirib-yoqish.
- Copilot `query_knowledge_base` — faqat label, implementatsiya yo'q.

**Xotira backend**
- `client_memory` (company + phone): `recentCalls` **3 ta**, `facts` cheksiz o'sadi, manbasi (qaysi
  qo'ng'iroq/tool yozgan) saqlanmaydi.
- Yozish — qulfsiz read-modify-write (`ClientMemoryService:134-182`) → tool + post-call bir vaqtda
  yozsa yangilanish yo'qolishi mumkin.
- `facts`/`recentCalls` ni tahrirlab ham, o'chirib ham bo'lmaydi; `preferredName` faqat bo'sh bo'lsa yoziladi.

**Frontend** (`robot-call-front`)
- `/knowledge` — bitta sahifa, uchta qo'lda yozilgan segment; manba/kolleksiya detail sahifasi yo'q.
- Xato holati ko'rsatilmaydi (xato = "manba yo'q"), indekslash progressi yo'q, chunk preview yo'q,
  test qidiruv yo'q, ko'p faylni birga yuklash yo'q, STRUCTURED_FEED (katalog) qo'shish UI yo'q.
- Hardcoded hex, emoji, aralash tilli sarlavhalar; KB/xotira fayllarida i18n yo'q.
- Agent editorida manba/kolleksiya biriktirib bo'lmaydi (faqat `/knowledge` ga havola), kolleksiyalar ko'rinmaydi.
- Xotira: `ClientMemoryBox` va `TargetMemoryModal` — bir xil ish, ikki xil kod; qo'ng'iroq detailida xotira yo'q.

## Retell'dan ustun bo'ladigan joylar (maqsad)

| | Retell | Biz (reja) |
|---|---|---|
| Manbalar | URL, sitemap, fayl, matn, auto-sync | + katalog (feed), Q&A tayyor javob, kolleksiyalar |
| Qidiruv | vektor, top-k + threshold | hybrid (pgvector + tsvector) + ixtiyoriy rerank, Q&A ham semantik |
| Ko'rinuvchanlik | yo'q | **Playground** (savol → parchalar + ball), chunk ko'rish/tahrirlash/o'chirish |
| Qo'ng'iroqda | ko'rinmaydi | har navbatda qaysi parcha ishlatildi — trace'da manzil bilan |
| Tahlil | yo'q | **Bilim bo'shliqlari**: javob topilmagan savollar → bir bosishda Q&A |
| Xotira | yo'q (webhook orqali) | o'rnatilgan, tahrirlanadigan, manbali faktlar, qo'ng'iroqda nima eslangani ko'rinadi |

## Bosqichlar

### 0 — Tuzatishlar (API o'zgarmaydi)
- Query embedding'ga timeout (oshsa — faqat kalit so'z qidiruvi bilan davom).
- Xotira yozishni atomik qilish (`SELECT … FOR UPDATE` yoki JSONB upsert) — yo'qolgan yangilanish yo'q.
- `FAILED` manbalarni backoff bilan avtomatik qayta urinish (ko'pi bilan N marta).
- Retrieval metrikalari (latency, topilgan/topilmagan) + ishlatilgan parchalar qo'ng'iroq trace'iga.
- Tekshiruv: unit test (timeout fallback, parallel yozish), web-test qo'ng'iroq trace'ida parchalar.

### 1 — Qidiruv dvigateli
- `knowledge_chunk` → `vector(768)` + HNSW + tsvector (katalog bilan bir xil), migratsiya mavjud
  BYTEA'ni ko'chiradi; qidiruv DB'da, JVM LRU olib tashlanadi.
- Q&A yozuvlar ham embed qilinadi: semantik + kalit so'z, ball bo'yicha eng yaxshisi (ostona bilan).
- Sitemap: har sahifa o'z URL'i bilan chunk metadata'sida (manzil), limit konfiguratsiyadan.
- Yangilashda `contentHash` diff — o'zgarmagan chunk qayta embed qilinmaydi.
- Ixtiyoriy rerank (`fastLlmModel`, agentda flag), chunker sarlavha/jadvalni hurmat qiladi.
- Tekshiruv: retrieval unit testlari, mavjud manba qayta indekslanganda chunkCount o'zgarmasligi.

### 2 — Yangi KB API
- `POST /api/knowledge-base/search` — playground (savol + agentId → Q&A/parchalar, ball, manba).
- `GET /sources/{id}/chunks` (sahifali), chunk tahrirlash/o'chirish/disable (qayta embed),
  manbani yoqish/o'chirish, sitemap sahifalari ro'yxati.
- Manbalar ro'yxati server tomonida filtr/sahifalash; ko'p manbani birga o'chirish; FAQ mavzulari endpoint'i.
- `GET /api/knowledge-base/insights` — javob topilmagan savollar, eng ko'p ishlatilgan manbalar;
  "Q&A ga aylantirish".
- Copilot `query_knowledge_base` tool'ini haqiqatda ulash.
- `docs/api/knowledge-base.md` yangilanadi + "Frontend'ga ta'siri".

### 3 — Xotira backend
- `recentCalls` saqlash limiti konfiguratsiyadan (masalan 10 saqlanadi, promptga 3 tushadi).
- Har faktga manba (`callId`/tool/operator) va vaqt; faktlar soni chegarasi.
- Fakt / qo'ng'iroq yozuvini tahrirlash va o'chirish, butun xotirani tozalash.
- Qo'ng'iroq boshida promptga tushgan xotira snapshot'i call detail'da ko'rinadi.
- `docs/api/memory.md` yangilanadi.

### 4 — Frontend: KB qayta dizayn
- DS `Tabs`: Manbalar · Q&A · Kolleksiyalar · Playground · Bo'shliqlar.
- Manba detail sahifasi (chunklar, sitemap sahifalari, progress, xato sababi), kolleksiya detail.
- Ko'p faylni drag-drop, katalog (feed) qo'shish UI, to'g'ri loading/empty/error holatlari.
- Agent editorida kolleksiya/manba biriktirish + "Qidiruvni sinash".
- i18n (uz/ru/en), tokenlar, emoji/hex tozalanadi.

### 5 — Frontend: xotira
- Bitta `ClientMemoryPanel` (kontakt drawer + kampaniya nishoni ikkalasi ishlatadi).
- Qo'ng'iroqlar timeline'i, faktlarni tahrirlash/o'chirish, manba belgisi.
- Call detail'da "Agent nimani eslagan" bloki.

## Holat
- **0 — kod yozildi (2026-11-06), tekshirilmagan:**
  - `KnowledgeBaseProperties` (`voice-agent.knowledge-base.*`, `misc.yml`) — embed timeout 800 ms,
    auto-retry 3 marta / 5 daqiqadan ikki baravar.
  - `SpringAiTextEmbedderAdapter.embedQuery` timeout bilan (oshsa → null → kalit so'z qidiruvi).
  - Xotira: `ClientMemoryRepository.lockByCompanyIdAndPhone` (INSERT … ON CONFLICT DO NOTHING + `FOR UPDATE`),
    uchala yozuvchi metod shu orqali.
  - `V51__knowledge_source_auto_retry.sql` (`failed_attempts`, `failed_at`), `KnowledgeSource.isDueForAutoRetry`,
    `CatalogFeedRefreshScheduler.enqueueDueRetries`. Faqat FETCH_FAILED / FETCH_INTERRUPTED / INTERNAL_SERVER_ERROR.
  - `CallEventType.KNOWLEDGE_RETRIEVED` (trace, CASCADE), `DialogMetrics.knowledgeSearch` (hit/miss + latency).
  - Testlar: `KnowledgeSourceTest` (yangi), `ClientMemoryServiceTest` (lock'ga moslandi).
- **1 — kod yozildi (2026-11-06), tekshirilmagan:**
  - `V52__knowledge_chunk_pgvector.sql`: `knowledge_chunk` → `vector(768)` + HNSW + tsvector, `company_id`,
    `content_hash`, `page_url`; eski BYTEA vektorlar o'chiriladi. `knowledge_base_item.embedding vector(768)`.
  - `KnowledgeChunkRepositoryAdapter` — plain JDBC (katalog kabi); `KnowledgeChunkEntity`/`JpaRepository`/
    `EntityMapper` va `IndexedChunk` o'chirildi. Qidiruv DB'da, JVM LRU va `evictCompany` olib tashlandi.
    Qayta indekslanayotgan manba eski parchalari bilan javob beradi (`status <> 'FAILED'`).
  - `KnowledgeEmbeddingBackfillScheduler` — vektorsiz parcha/yozuvlarni har daqiqada to'ldiradi.
  - Q&A: `KnowledgeItemMatcher` (so'z boshidan kalit so'z + ma'no ≥ `itemMinScore` 0.80),
    `KnowledgeRetrievalUseCase.findCuratedAnswer`; `KnowledgeBaseLookup` va `findRelevantAnswer` o'chirildi.
  - Sitemap: har sahifa alohida, `pageUrl` bilan, limit `KB_SITEMAP_MAX_PAGES` (100); `content_hash` bo'yicha
    o'zgarmagan parcha qayta embed qilinmaydi.
  - Trace: `curatedItemId/MatchedBy/Score`, passage `pageUrl`.
  - Testlar: `KnowledgeRetrievalServiceTest` (qayta yozildi, mock'lar bilan), `KnowledgeItemMatcherTest` (yangi).
  - **Qoldirildi (1d, ixtiyoriy):** LLM rerank (`fastLlmModel`) va sarlavha/jadvalni hurmat qiladigan chunker.
- **2a — kod yozildi (2026-11-06), tekshirilmagan:**
  - `V53__knowledge_chunk_curation.sql`: `knowledge_chunk.disabled`, `original_hash`; `knowledge_source.enabled`.
    Tahrir/o'chirish refresh va FAILED dan omon qoladi (`KnowledgeChunk.carrying`, `deleteUncuratedBySourceId`).
  - API: `GET/PATCH /sources/{id}/chunks`, `GET /sources/{id}/pages`, `POST /search` (playground),
    `GET /insights` (KNOWLEDGE_RETRIEVED dan: hit rate, gap'lar, top manba/yozuv), `GET /topics`;
    `PUT /sources/{id}` da `enabled`. Trace payload: `agentId`, passage `chunkId`.
  - Testlar: `KnowledgeChunkTest`, `KnowledgeSearchServiceTest` (yangi), `KnowledgeSourceTest` moslandi.
  - **2b ga qoldirildi:** manbalar ro'yxatida server tomonda paging/filtr, ommaviy o'chirish, copilot
    `query_knowledge_base` tool'i.
- **3 — kod yozildi (2026-11-06), tekshirilmagan:**
  - `V54__client_memory_fact_sources.sql` (`fact_sources` JSONB). `ClientMemory` + `ClientMemoryBuilder`,
    `MemorySource` (CALL/CHAT/TOOL/OPERATOR + callAttemptId/chatSessionId/tool/at).
  - `RememberedCall`: `id` (yangi — UUID, eskisi mazmundan barqaror hash), `callAttemptId`, `chatSessionId`;
    outbox qayta urinishida bir qo'ng'iroq takrorlanmaydi.
  - `ClientMemoryProperties` (`voice-agent.memory`): `kept-calls` 10, `max-facts` 50 (operator fakti o'chmaydi);
    promptga 3 ta suhbat (`CallContext.MAX_PROMPT_CALLS`).
  - API: `PUT/DELETE /api/memory/{phone}/facts/{key}`, `PATCH/DELETE /{phone}/calls/{callId}`, `DELETE /{phone}`.
  - `DialogOutcomeSink.callAttemptId()/chatSessionId()` — tool'lar fakt manbasini yozadi.
  - `CallEventType.MEMORY_RECALLED` (plain timeline): suhbat boshida promptga tushgan xotira.
  - Testlar: `ClientMemoryServiceTest` (kengaytirildi), `CallContextMemoryTest` (yangi);
    `SystemPromptFactoryTest`, `ChatConversationsTest`, `AgentFunctionToolsTest` moslandi.
  - Qilinmadi: `static/openapi/schemas/memory.yaml` (KB ham) yangilanmagan.
- **4a — FE kod yozildi (2026-11-06), tekshirilmagan** (`../robot-call-front`):
  - `KnowledgeBaseView` — DS `Tabs` (Manbalar · Savol-javoblar · Kolleksiyalar · Sinov · Bo'shliqlar), `?tab=` URL'da;
    tab nomlari i18n (`knowledgeBase.tabs.*`, uz/ru/en).
  - `KbSourceDetailDrawer` (yangi): holat, xato sababi + nima qilish, yoqish/o'chirish, sitemap sahifa filtri,
    parcha qidiruvi, parchani tuzatish/o'chirib qo'yish, indekslash paytida kuzatish.
  - `KbPlaygroundSection` (yangi), `KbInsightsSection` (yangi: KPI, gap'lar → `KnowledgeModal` oldindan to'ldirilgan,
    top manba/yozuvlar).
  - `RagSourcesSection`: DS `Badge`, hex/emoji olib tashlandi, `enabled`, karta → drawer. `kbShared` ranglari tokenlarga.
  - API: `types.ts` (Kb* tiplar, `KbSourceRow.enabled`), `knowledgeBase.ts` (chunks, pages, search, insights, topics),
    `errors.ts` (KNOWLEDGE_CHUNK_*).
  - **4b ga qoldi:** agent editorida kolleksiya/manba biriktirish + "Qidiruvni sinash", ko'p faylni drag-drop,
    katalog (feed) qo'shish UI, kolleksiya detail, `/topics` ni FAQ mavzu tanlagichiga ulash, ekran matnlarining
    to'liq i18n'i, qolgan KB modal'laridagi hex/emoji.
- **4b — FE kod yozildi (2026-11-06), tekshirilmagan:**
  - `KbSourceModal` qayta yozildi: bir nechta faylni tanlash/sudrab tashlash (20 tagacha, har biri alohida manba),
    "Katalog" (`STRUCTURED_FEED`: URL yoki fayl, JSONPath, kalit ustun, yangilanish), `defaultScope`.
  - Agent editori (`KnowledgeSection`): kolleksiyalarni Toggle bilan biriktirish (darhol saqlanadi) va
    "Qidiruvni sinash" (formadagi topK/minScore bilan).
  - `KbCollectionDetailDrawer` (yangi): agentlar, manbalar, kolleksiyaga manba qo'shish, manba → parchalar.
  - `kbTopics.useKbTopics` — `/topics` + taklif qilinganlar; FAQ filtri va modal'da "Yangi mavzu…".
  - Qoldi: ekran matnlarining to'liq i18n'i (faqat tab nomlari i18n); `#fff` faqat primary fon ustidagi matnda (DS Button kabi).
- **5 — FE kod yozildi (2026-11-06), tekshirilmagan:**
  - `contacts/ClientMemoryPanel` (yangi, umumiy): operator qismi (til `uz-UZ`/`ru-RU`/`en-US`), manba belgili faktlar
    (tuzatish, o'chirish, qo'lda qo'shish; qo'ng'iroq manbasi → `/calls?callId=`), suhbatlar timeline'i (promptga
    tushadigan 3 tasi belgilangan; tuzatish/o'chirish), "Xotirani tozalash".
  - `ClientMemoryBox` va `TargetMemoryModal` endi shu panelni o'raydi (ikki xil kod yo'q).
  - Qo'ng'iroq detail'da "Xotira" tabi (`calls/CallMemoryRecalled`, `MEMORY_RECALLED` hodisasi).
  - API: memory tiplari/funksiyalari, `CLIENT_MEMORY_*` matnlari, `CLIENT_MEMORY_NOT_FOUND` toast'siz.
  - Eslatma: `campaigns.ts` dagi `getTargetMemory`/`updateTargetMemory` oldin ham ishlatilmagan — tegilmadi.
- **Reja (0–5) kod sifatida tugadi; hammasi tekshirilmagan.** Qolganlar: 1d rerank, heading-aware chunker,
  2b (server paging, bulk delete, copilot tool), KB ekran matnlari i18n, openapi yaml.

