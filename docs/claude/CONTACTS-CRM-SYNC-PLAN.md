# Kontaktlar: maydonlar, CRM sync, backfill va redesign (Retell uslubida)

Sana: 2026-11-06. Holat: 1–6-bosqichlar kodlangan (2026-11-06, tekshirilmagan; 3 — Bitrix24, amoCRM/Kommo va Uysot (Open API, 2 s pacing, resume); 4 — pushMappings, contact_crm_link push ustunlari + backoff, CrmContactPushScheduler 30 s; 5 — CallReanalysisJob.mode CONTACTS_STORED/CONTACTS_REEXTRACT; 6 — robot-call-front ContactsScreen + ContactFieldsModal/CrmContactSyncModal/ContactBackfillModal, tekshirilmagan). API: /api/settings/crm/contact-sync (reja /api/contacts/sync edi). external_id alohida contact_crm_link jadvalida (Contact record o'zgarmadi).

1-bosqichdan chetga chiqish: `external_id`/`crm_provider` ustunlari 3-bosqichga ko'chirildi (shu yerda ishlatiladi; `Contact` 10+ maydonga yetib builder talab qiladi).

## Qarorlar (foydalanuvchi bilan kelishilgan)

1. **Mapping kompaniya darajasida** (Retell kabi): Contacts → Contact fields sahifasida har bir maydon uchun
   "extraction id + update mode". Barcha agentlarga amal qiladi. Agentdagi `DataExtractionField.contactField`
   eski konfiguratsiya uchun o'qiladi (fallback), yangi UI uni ko'rsatmaydi.
2. **CRM sync — Bitrix24, AmoCRM/Kommo, Uysot** (uchalasi). Uysot company 1 da token xatosi bor — sinash keyinroq.
3. **Backfill — ikki rejim:** (a) saqlangan extraction natijalaridan bepul ko'chirish; (b) ixtiyoriy LLM bilan
   transkriptdan extraction'ni qayta ishlatish (pulli, taxminiy narx oldindan ko'rsatiladi).
4. **Accumulate & summarize rejimi kerak** — qo'ng'iroq agentining `fastLlmModel` i bilan, billing'ga yoziladi.

## Hozirgi holat

- `contact` jadvali: `name, phone, address, tags, notes, custom_fields JSONB (string→string)`; telefon — identifikator.
- Qo'ng'iroqlar kontaktga telefon bo'yicha bog'lanadi (FK yo'q).
- Post-call: `CallMemoryWriter.saveContactFields` — agentning `contactField` li maydonlarini doim **overwrite** qiladi.
- `CrmConnectorPort`: `findLead`, `findByPhone`, note/call history/task/stage yozish. Kontaktlarni ro'yxatlab
  o'qish va CRM kontakt maydonini yozish **yo'q**.
- Re-run/backfill yo'q. Frontend: `ContactsScreen.jsx` (oddiy jadval: ism, telefon, DNC tab, CSV, scrub).

## Bosqichlar

### 1-bosqich — Kontakt maydon ta'riflari (backend)
- Yangi jadval `contact_field_def` (company_id, key, label, type TEXT/NUMBER/BOOLEAN/ENUM/DATE/DATETIME, options,
  description, `extraction_key` (nullable), `update_mode` OVERWRITE/FILL_IF_EMPTY/ACCUMULATE, created/updated).
- `contact` ga: `external_id`, `crm_provider`, `crm_synced_at`. Ro'yxat javobiga hosila: `callCount`, `lastCallAt`.
- API: `GET/POST/PUT/DELETE /api/contacts/fields`; ro'yxat filtri — custom field qiymati, tag, `lastCallFrom/To`,
  `hasExternalId`; qidiruv custom field qiymatlarida ham.
- Tekshiruv: `ContactFieldValidator` unit testlari (kalit formati, rezerv nomlar, tur bo'yicha qiymat).

### 2-bosqich — Post-call → kontakt maydoni (update mode bilan)
- `CallMemoryWriter.saveContactFields` kompaniya mapping'lari (+ agent `contactField` fallback) bo'yicha yozadi.
- OVERWRITE, FILL_IF_EMPTY — sof qoida; ACCUMULATE — `fastLlmModel` bilan birlashtirish, xato bo'lsa
  "eski\n\nyangi", ≤ ~512 token; LLM xarajati billing'ga.
- Tekshiruv: update mode'lar uchun unit test (mock LLM).

### 3-bosqich — CRM → kontakt (inbound sync)
- Port: `CrmContactSyncPort` — `listContactsModifiedSince(connection, since, cursor)`, `listContactFields(connection)`.
- Adapterlar: Bitrix24 (`crm.contact.list`, `crm.contact.fields`), AmoCRM/Kommo (`/api/v4/contacts`,
  `/api/v4/contacts/custom_fields`, `filter[updated_at][from]`), Uysot (mavjud mijoz API'si).
- Sozlama: `contact_sync_settings` (enabled, telefon maydoni, CRM maydon → contact field mapping, last_cursor,
  last_run_at, last_error). Bir kompaniyada bitta faol CRM.
- `@Scheduled` (5 daq, `ClusterJobLock`) inkremental + "Run full sync". Mos telefon → yangilash, yo'q → yaratish,
  hech qachon o'chirmaydi; noto'g'ri telefon o'tkazib yuboriladi va hisoblanadi.
- API: `GET/PUT /api/contacts/sync`, `POST /api/contacts/sync/run`, `GET /api/contacts/sync/runs`.

### 4-bosqich — kontakt → CRM (outbound sync)
- Port: `updateContactFields(connection, externalId, values)`; mapping contact field → CRM field.
- Trigger: kontakt qo'lda saqlanganda va post-call yozuvidan keyin. Faqat shu CRM'dan kelgan (external_id bor)
  kontakt; "CRM'da yangi kontakt yaratish" — ixtiyoriy toggle (default off). Telefon hech qachon yozilmaydi.
- Xato bo'lsa — mavjud outbox mexanizmi orqali qayta urinish.
- **Amalda (2026-11-06):** outbox emas (u call_attempt kalitli) — `contact_crm_link` da push_requested_at/push_next_at/
  push_attempts (V49), backoff 1→2→4… min (≤ 6 soat), 8 urinish. "CRM'da yangi kontakt yaratish" toggle qilinmadi.
  Mapping alohida: `pushMappings` (import `fieldMappings` dan mustaqil, `enabled` ga bog'liq emas). Bitrix24
  EMAIL/WEB/IM — faqat yo'q bo'lsa qo'shiladi; Uysot — faqat lid maydonlari, body shakli tasdiqlanmagan.

### 5-bosqich — Backfill eski suhbatlardan
- `contact_backfill_job` (filtr: agentlar, vaqt oralig'i; rejim: STORED / RE_EXTRACT; holat, progress, xarajat).
- STORED: saqlangan `outcome` → mapping'lar → update mode bo'yicha (eskidan yangiga tartib).
- RE_EXTRACT: transkriptdan agentning hozirgi `dataNeeded` i bilan extraction qayta, natija qo'ng'iroqqa ham yoziladi.
  Oldindan taxminiy narx (`AgentCostEstimator`), kompaniyada bir vaqtda bitta job, virtual thread.
- API: `POST /api/contacts/backfill/estimate`, `POST /api/contacts/backfill`, `GET /api/contacts/backfill/{id}`.
- **Amalda (2026-11-06):** alohida `contact_backfill_job` yo'q — mavjud `CallReanalysisJob` ga `mode` qo'shildi (V50):
  `CONTACTS_STORED` (modelsiz, bepul, balans tekshirilmaydi), `CONTACTS_REEXTRACT` (reanalysis + kontaktga). Endpointlar —
  `/api/call-reanalyses` (preview = estimate, cancel bor). Bitta ish = bitta agent. ACCUMULATE merge bir qo'ng'iroqqa bir marta yechiladi.

### 6-bosqich — Frontend redesign (`robot-call-front`)
- Jadval: telefon, ism, tags, DNC, qo'ng'iroqlar soni, oxirgi qo'ng'iroq, external ID, custom field ustunlari;
  "Manage columns" (localStorage), filtr paneli (tag, DNC, oxirgi qo'ng'iroq, custom field), qidiruv.
- Actions menyusi: Add contact · Import CSV · Manage contact fields · Backfill · CRM sync · Run full sync.
- Detail drawer: tahrir joyida, xotira (client memory), statistika (jami vaqt, o'rtacha, kiruvchi/chiquvchi),
  qo'ng'iroqlar ro'yxati, ↑/↓ bilan navigatsiya.
- Contact fields sahifasi: nom, tur, CRM maydoni, extraction maydoni, update mode badge.
- CRM sync dialogi: "Import" va "Sync to CRM" tablari; backfill dialogi narx bilan.

Har bosqich oxirida `docs/api/contacts.md` yangilanadi va "Frontend'ga ta'siri" ro'yxati beriladi.
