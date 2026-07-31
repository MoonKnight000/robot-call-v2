# API talablari — Nido paneli uchun

> **Bu hujjat nima?** `UI-DESIGN.md` dagi har bir ekranni ishga tushirish uchun kerak
> bo'ladigan backend API'larning ro'yxati. Hozirgi kodga (2026-07-31 holati) qarab
> har biri belgilangan: ✅ **mavjud**, ⚠️ **qisman mavjud** (kengaytirish kerak), ❌ **yo'q**
> (yangidan yozish kerak). Barcha yangi/o'zgargan endpoint proyekt standarti bo'yicha
> `ResponseData<T>` / paginatsiyada `PageableData<T>` qaytarishi shart (§ ilgari
> qo'shilgan `shared.api` paketi).

**Qisqacha holat:** hozirgi API kampaniya/qo'ng'iroq/hisobot MVP'sini yopadi (0–12
bosqich, ROADMAP.md). Panel dizayni esa foydalanuvchi/kompaniya/ssenariy/kontakt/
kiruvchi-marshrut/billing kabi ROADMAP'dagi **hali qo'lga olinmagan bosqichlar**ni
ham chizadi — shuning uchun ro'yxatning katta qismi ❌.

---

## 0.0 Umumiy javob strukturasi (barcha endpoint uchun majburiy)

Har bir controller `ResponseEntity<ResponseData<T>>` qaytaradi (`shared.api.ResponseData`,
`shared.api.PageableData`) — bitta controller ham xom `Map`/list qaytarmaydi, hammasi nomlangan
DTO record. Muvaffaqiyatli va xatolik javoblari bitta shaklda keladi, shuning uchun frontend
ikkalasini ham bitta joyda parse qiladi.

**Muvaffaqiyatli javob** (`HTTP 200`):

```json
{ "data": { /* T */ }, "message": null, "accept": true, "errors": null }
```

**Xatolik javobi** — status kod xatoning turiga qarab tanlanadi, tana har doim shu shaklda:

```json
{ "data": null, "message": "Validation failed", "accept": false, "errors": ["name: must not be blank"] }
```

`errors` faqat maydon darajasidagi xatolar bo'lganda to'ldiriladi (masalan Bean Validation);
qolgan hollarda `null` bo'lib, `message`ning o'zi yetarli bo'ladi.

Bu javobni **hech bir controller o'zi qurmaydi** — hammasi `config.ApiExceptionHandler`
(`@RestControllerAdvice`) orqali markazlashtirilgan, shu jumladan controllerlar bilvosita
tashlaydigan (servis qatlamidan chiqqan) istisnolar ham. Xarita:

| Istisno | Status | Qachon |
|---|---|---|
| `IllegalArgumentException` | 400 | Noto'g'ri kirish (yaroqsiz raqam, bo'sh matn, ruxsatsiz fayl yo'li) |
| `MethodArgumentNotValidException` | 400 | `@Valid @RequestBody` Bean Validation'dan o'tmadi — `errors`da har bir maydon uchun xabar |
| `HttpMessageNotReadableException` | 400 | So'rov tanasi buzilgan JSON yoki noma'lum enum qiymati (masalan filter `orders`dagi noto'g'ri ustun nomi) |
| `MissingServletRequestParameterException` | 400 | Majburiy `@RequestParam` umuman yuborilmagan |
| `MethodArgumentTypeMismatchException` | 400 | Path/query qiymati kerakli turga o'girilmadi (masalan sonli bo'lmagan `id`) |
| `IllegalStateException` | 409 | So'rov to'g'ri, lekin tizim uni bajarish holatida emas (ARI ulanmagan, TTS o'chirilgan) |
| `ResponseStatusException` | controller belgilagan status | Masalan mavjud bo'lmagan `id` uchun 404 |
| Boshqa har qanday istisno | 500 | To'liq stack-trace faqat server logida; klientga umumiy "Internal server error" xabari — ichki tafsilot hech qachon tashqariga chiqmaydi |

**Filter/ro'yxat endpointlari** — 3 tadan ko'p parametr qabul qiladigan har qanday ro'yxat/filtr
endpointi `GET` emas, `POST` bo'lib, parametrlarni bodyda kutadi. Body — `shared.api.FilterInterface`dan
voris olgan record (`CampaignFilter`, `TargetFilter`, `CallFilter`, `AuditFilter`): `page`, `size`,
`orders` (ustun → yo'nalish xaritasi). Masalan `POST /api/campaigns/list`, `POST /api/reports/calls/list`,
`POST /api/reports/audit/list`. 3 yoki undan kam parametrli (masalan dashboard `from`/`to`/`campaignId`)
endpointlar oddiy `GET` bo'lib qoladi.

**Validatsiya** — har bir `@RequestBody` DTO Jakarta Bean Validation annotatsiyalari bilan
belgilangan va controller interfeysida `@Valid` orqali ulangan (`spring-boot-starter-validation`):

- `CreateCampaignRequest.name` — `@NotBlank`
- `AddTargetRequest.clientId` — `@Positive`, `.phone` — `@NotBlank`
- `CampaignFilter`/`TargetFilter`/`CallFilter`/`AuditFilter` — `page` (`@Min(0)`), `size`
  (`@Min(1)` `@Max(500)`); qiymat berilmasa `FilterInterface` standart qiymat bilan to'ldiradi,
  lekin berilgan-u chegaradan tashqari qiymat rad etiladi (jim tarzda kesilmaydi)
- Oddiy `@RequestParam` qabul qiladigan endpointlar (masalan `POST /api/calls?number=`,
  `POST /api/calls/{channelId}/say`) servis qatlamida tekshiriladi (`PhoneNumbers.require`,
  `AriService.say`/`playRecording` — bo'sh/uzun matn, ruxsatsiz fayl yo'li) va xuddi shu
  `IllegalArgumentException → 400` yo'li orqali qaytadi

---

## 0. Kesib o'tuvchi (cross-cutting) — hamma ekranga kerak

| # | Endpoint | Holat | Izoh |
|---|----------|-------|------|
| 0.1 | `POST /api/auth/login` | ❌ | Login sahifasi (§10.1). Hozir faqat `X-Api-Key` bor, sessiya/JWT yo'q — ROADMAP E.1. |
| 0.2 | `POST /api/auth/logout` | ❌ | Xodim kartochkasi "Chiqish" (§8.2). |
| 0.3 | `GET /api/auth/me` | ❌ | Joriy foydalanuvchi: ism, rol, avatar, kompaniya(lar) — sidebar, xodim kartochkasi, topbar uchun asos. |
| 0.4 | `POST /api/auth/uysot/callback` | ❌ | "Uysot bilan kirish" (§10.1, ROADMAP D.2, OAuth). |
| 0.5 | `GET /api/companies` | ❌ | Kompaniya tanlagich (§7.3) — ROADMAP Bosqich B (`company` jadvali) qilinmaguncha bitta "default" kompaniya bilan stub qilinadi. |
| 0.6 | `GET /api/search?q=` | ❌ | Command palette (§11.9) — sahifa/kampaniya/qo'ng'iroq bo'yicha qidiruv. |
| 0.7 | `GET /api/live/stream` (SSE) | ✅ | Jonli KPI (`KPI`), jonli qo'ng'iroqlar ro'yxati (`LIVE_CALLS`), transkript qatorlari (`TRANSCRIPT`), audio-daraja (`AUDIO_LEVEL`, ⚙ o'chirilgan holatda), bildirishnoma (`NOTIFICATION`) — barchasi `live.LiveEventType` bo'yicha bitta oqimda. `ResponseData` bilan o'ralmaydi (event-stream formati talabi). "Ulanish uzildi" holati serverdan push qilinmaydi — standart `EventSource` `onerror`/qayta-ulanish + 15s heartbeat orqali klient aniqlaydi. |
| 0.8 | `GET /api/notifications`, `POST /api/notifications/{id}/read` | ❌ | Topbar qo'ng'iroq ikonkasi (§9), popover'dagi bildirishnoma toggle'lari (§8.2). |

---

## 1. Kirish (Login) — §10.1

Faqat 0.1/0.3/0.4 kerak. Boshqa hech narsa yo'q (statik dizayn sahifasi).

---

## 2. Boshqaruv paneli (Dashboard) — §10.2

| Endpoint | Holat | Izoh |
|----------|-------|------|
| `GET /api/campaigns` (status=ACTIVE filtri) | ✅ | Faol kampaniyalar bloki uchun ishlatsa bo'ladi — filtr parametri qo'shish kifoya (⚠️: hozir status bo'yicha filtr yo'q, faqat sort/paginatsiya bor). |
| `GET /api/reports/calls` | ✅ | "Oxirgi qo'ng'iroqlar" jadvali. |
| `GET /api/reports/dashboard/kpi?from=&to=&campaignId=` | ✅ | 4 ta KPI kartochkasi (jami qo'ng'iroq, javob %, o'rtacha davomiylik, va'da soni), har biri oldingi teng uzunlikdagi davrga nisbatan `changePct` va bucket'langan `sparkline` bilan (`DashboardKpi`/`DashboardMetric`, `ReportRepository.dashboardTotals`+`dashboardBuckets`). `from`/`to` — ISO-8601 instant, ikkalasi ham ixtiyoriy (standart: oxirgi 7 kun). |
| `GET /api/reports/dashboard/timeseries?from=&to=&campaignId=` | ✅ | "Qo'ng'iroqlar dinamikasi" stacked-area grafik — `DashboardBucket` ro'yxati (`answered`/`noAnswer`/`error`), granularity avtomatik: ≤2 kun → soat, ≤62 kun → kun, undan katta → hafta (`DashboardRange.granularity()`). |
| `GET /api/reports/dashboard/outcomes?from=&to=&campaignId=` | ✅ | "Natijalar taqsimoti" — davr bo'yicha (bucket'siz) `disposition → son` ro'yxati, ko'p sonidan kamiga saralangan. |
| `GET /api/calls/live` | ✅ | "Jonli qo'ng'iroqlar" paneli (o'ng ustun) — §3 bilan bir xil ma'lumot, qisqartirilgan shakli. |

---

## 3. Jonli qo'ng'iroqlar (monitoring) — §10.3

| Endpoint | Holat | Izoh |
|----------|-------|------|
| `GET /api/calls/live` | ✅ | Har bir jonli qo'ng'iroq: channelId, telefon, mijoz ismi, kampaniya (id+nom), til, boshlanish vaqti, bosqich (dialogState). `DialogEngine.liveDialogs()` (in-memory sessiyalar) `OutboundCallRegistry`/`CampaignService` bilan `AriService.liveCalls()`da birlashtiriladi. Oxirgi transkript qatori hali yo'q (⚠️ — buning uchun jonli transkript oqimi kerak, pastga qarang). |
| Live audio-daraja / to'lqin oqimi | ✅ | `GET /api/live/stream` dagi `AUDIO_LEVEL` event'i (§0.7) — channelId bo'yicha RMS daraja, ~5/sek throttling. `voice-agent.live.audio-level-enabled` bilan yoqiladi (standart holatda o'chiq). |
| Live transkript oqimi | ✅ | `GET /api/live/stream` dagi `TRANSCRIPT` event'i (§0.7) — har bir CLIENT/AGENT qatori gapirilgan/tanilgan zahoti push qilinadi. |
| `POST /api/calls` | ✅ | Qo'lda qo'ng'iroq boshlash (test/tekshiruv uchun ishlatilgan, kampaniyaga bog'liq emas). |
| `POST /api/calls/{channelId}/say` | ✅ | — |
| `POST /api/calls/{channelId}/transfer` | ❌ | "Operatorga uzatish" tugmasi. `AriService`da hozircha transfer yo'q. |
| `POST /api/calls/{channelId}/hangup` | ❌ | "Tugatish" (danger, tasdiq bilan). Hozir dastur qo'ng'iroqni faqat o'zi tugatadi, tashqaridan majburiy tugatish endpointi yo'q. |
| `GET /api/tts/voices` yoki audio proxy | ✅/❌ | "Tinglash" tugmasi (jonli qo'ng'iroqqa join qilib eshitish) — bu **live audio monitor** funksiyasi, hozir yo'q (faqat post-call recording bor, §10.5). |

---

## 4. Qo'ng'iroqlar (tarix) — §10.4

| Endpoint | Holat | Izoh |
|----------|-------|------|
| `GET /api/reports/calls` | ✅ | Asosiy jadval — sahifalash/saralash bor. **Kengaytirish kerak:** matn bo'yicha qidiruv (`q=`), kampaniya/natija/sana/davomiylik filtri hozir yo'q (⚠️ faqat `CallFilter`da sort bor, `where` filtri yo'q). |
| `GET /api/reports/calls/export?format=csv` | ❌ | "⇩ Eksport" tugmasi. |
| `POST /api/reports/calls/bulk` (`retry` \| `dnc` \| `export`, ids[]) | ❌ | Ommaviy amal paneli: "Qayta qo'ng'iroq", "DNC ro'yxatiga", tanlangan qatorlar uchun eksport. |
| Ustunlar sozlamasi (foydalanuvchi profilida saqlash) | ❌ | "Ustunlar ⚙" — client-side'da ham bo'lishi mumkin, lekin foydalanuvchilar orasida saqlanishi uchun profil API'siga bog'liq (§0.3). |

---

## 5. Qo'ng'iroq tafsiloti (drawer) — §10.5

| Endpoint | Holat | Izoh |
|----------|-------|------|
| `GET /api/reports/calls/{callId}` | ✅ | Transkript + natija maydonlari (`CallDetail`) — "Transkript" va "Natija" tab'lari uchun yetarli. |
| `GET /api/reports/calls/{callId}/recording` | ✅ | To'lqin pleyer audio manbai. |
| **"Texnik" tab** uchun qo'shimcha maydonlar | ⚠️ | Kerakli: kanal, trunk, AMD natijasi, STT/TTS provayder, LLM modeli, token sarfi, latency, xatolar jurnali. Hozirgi `CallDetail` faqat `errorMessage` beradi — qolganlarini `call_attempt`/metrikalardan qo'shish kerak (yangi `CallTechnicalDetail` record + repository so'rovi). |
| `GET /api/reports/calls/{callId}/transcript.txt` | ❌ | "TXT yuklab olish" tugmasi. |

---

## 6. Kampaniyalar — §10.6

| Endpoint | Holat | Izoh |
|----------|-------|------|
| `POST /api/campaigns` | ✅ | — |
| `GET /api/campaigns` | ✅ | Ro'yxat — kartochka grid uchun yetarli, lekin status bo'yicha filtr yo'q (⚠️). |
| `GET /api/campaigns/{id}` | ✅ | — |
| `PUT /api/campaigns/{id}` | ❌ | "Tahrirlash" tugmasi — hozir faqat status o'zgartirish (`start`/`pause`) bor, boshqa maydonlarni yangilash yo'q. |
| `DELETE /api/campaigns/{id}` (yoki arxivlash) | ❌ | Kartochkadagi `⋯` menyusi uchun ehtimoliy amal. |
| `POST /api/campaigns/{id}/start` \| `/pause` | ✅ | — |
| `POST /api/campaigns/{id}/targets` \| `/targets/csv` | ✅ | Sehrgar 3-qadami ("Nishonlar"). |
| `GET /api/campaigns/{id}/targets` | ✅ | "Nishonlar" tab'i, tafsilot sahifasida. |
| CSV ustun moslashtirish oldindan ko'rish | ⚠️ | Hozirgi `addTargetsCsv` faylni to'g'ridan-to'g'ri import qiladi va xatolarni qaytaradi (`CsvImportResult`) — lekin dizayndagi "ustunni moslashtirib, keyin tasdiqlash" ikki bosqichli oqim emas, bitta so'rov. Kerak bo'lsa `POST /api/campaigns/{id}/targets/csv/preview` qo'shiladi. |
| `POST /api/scenarios/list` | ✅ | Sehrgar 2-qadami ("Ssenariy tanlash") — §7 bilan bir xil, ROADMAP Bosqich A. Kampaniya hali `scenario_id`ga bog'lanmagan (⚠️ — A.3 keyingi bosqich). |
| `GET /api/campaigns/{id}/calls` | ✅ | Tafsilot sahifasi "Qo'ng'iroqlar" tab'i. |

---

## 7. Ssenariylar — §10.7 (ROADMAP Bosqich A)

**Saqlash + CRUD + validatsiya ✅ qo'shildi** (`scenario` paketi, V10/V11 migratsiya, 5 ta
tayyor shablon seed qilingan: `debt-collection`, `lead-qualification`, `notification`,
`survey`, `appointment-reminder`). **Qat'iy qamrov chegarasi:** `DialogEngine`/`DialogState`/
`CallContext` hali tegilmagan — jonli qo'ng'iroqlar bugungidek qattiq qarzdorlik FSM'i bilan
ishlaydi; ssenariy ta'rifini haqiqiy qo'ng'iroqni boshqarishga ulash keyingi, alohida bosqich.

| Endpoint | Holat | Izoh |
|----------|-------|------|
| `POST /api/scenarios/list` | ✅ | Ro'yxat (built-in shablonlar + kompaniya custom'lari), `builtinOnly` filtri bilan ikkala tab uchun. Faqat har bir `scenario_key`ning faol versiyasini qaytaradi. |
| `GET /api/scenarios/{id}` | ✅ | Tahrirlagich uchun to'liq `ScenarioDefinition` (bosqichlar, faktlar, tool'lar, outcome, rolePrompt, guardrails). |
| `POST /api/scenarios` | ✅ | Yangi custom ssenariy yaratish — saqlashdan oldin avtomatik validatsiya qilinadi. |
| `PUT /api/scenarios/{id}` | ✅ | Yangi versiya yaratadi (versiyalash — ROADMAP A.4); builtin ssenariyni tahrirlash 409 bilan rad etiladi (avval clone kerak). |
| `POST /api/scenarios/{id}/clone` | ✅ | "Tayyor shablon"ni (yoki istalgan ssenariyni) nusxalash. |
| `POST /api/scenarios/validate` | ✅ | Saqlashdan oldin: deadlock yo'qligi (BFS), tool/outcome/fakt nomi to'qnashuvi tekshiruvi (`ScenarioValidator`). |
| `POST /api/calls` ga `scenarioId` parametri | ⚠️ | "Sinov qo'ng'irog'i" — hali qo'shilmagan, chunki `DialogEngine` ssenariy ta'rifini bajarmaydi (Scenario Engine ishga tushirilgach qo'shiladi). |

---

## 8. Kontaktlar — §10.8

ROADMAP'da alohida bosqich sifatida rejalashtirilmagan — panel uchun yangidan
loyihalandi: `contact` jadvali `campaign_target`dan mustaqil (kampaniyaga bog'liq
emas), `company_id` bilan (Bosqich B). Qo'ng'iroqlar tarixi `contact_id` FK orqali
emas, telefon raqami bo'yicha moslashtiriladi (`do_not_call_list`ning mavjud
pretsedentiga mos).

| Endpoint | Holat | Izoh |
|----------|-------|------|
| `POST /api/contacts/list` | ✅ | Ro'yxat — `search` (ism/telefon) filtri bilan. |
| `GET /api/contacts/{id}` | ✅ | Drawer: profil + qo'ng'iroqlar tarixi taymlayni (`ReportRepository.callsForPhone`, oxirgi 50 ta). |
| `POST /api/contacts` | ✅ | "+ Kontakt" — telefon bo'yicha dublikat rad etiladi. |
| `PUT /api/contacts/{id}` | ✅ | Telefon o'zgartirilmaydi (barqaror identifikator sifatida). |
| `POST /api/contacts/csv` | ✅ | "⇧ CSV import" — `name,phone,address,tags,notes` ustunlari, xato qatorlar hisobot qilinadi. |
| `POST /api/contacts/{id}/dnc` | ✅ | "DNC ga qo'shish" — kontaktning telefonini opt-out ro'yxatiga qo'shadi. |
| `POST /api/do-not-call/list` | ✅ | "Qo'ng'iroq qilinmasin (DNC)" tab'i — faqat faol (o'chirilmagan) yozuvlar. |
| `POST /api/do-not-call/{phone}/remove` | ✅ | "Ro'yxatdan chiqarish" — soft-delete (`removed_at`/`removed_by`), `AuditService.record("DNC_REMOVE", ...)` bilan birga. Hech qachon opt-out bo'lmagan yoki allaqachon olib tashlangan raqam — 404. |

---

## 9. Kiruvchi marshrutlar — §10.9 (ROADMAP Bosqich C.1 — hali boshlanmagan)

| Endpoint | Holat | Izoh |
|----------|-------|------|
| `GET /api/inbound-routes` | ❌ | `inbound_route` jadvali ROADMAP'da rejalashtirilgan, hali yo'q. |
| `POST /api/inbound-routes` \| `PUT` \| status toggle | ❌ | DID → ssenariy/til/ish-vaqti bog'lash. |
| `GET /api/inbound-routes/{id}/stats` | ❌ | Drawer'dagi "shu raqamga tushgan qo'ng'iroqlar statistikasi". |

---

## 10. Hisobotlar — §10.10

| Endpoint | Holat | Izoh |
|----------|-------|------|
| `GET /api/reports/dynamics?range=&campaignId=&scenarioId=&operator=` | ❌ | Grafik 1 — umumiy filtr paneliga mos parametrlar bilan (§2 dagi dashboard versiyasidan farqi — bu yerda scenario/operator filtri ham bor). |
| `GET /api/reports/outcomes-distribution` | ❌ | Grafik 2. |
| `GET /api/reports/hourly-heatmap` | ❌ | Grafik 3 (kun × soat javob foizi) — operator uchun eng qimmatli, alohida SQL agregatsiya kerak. |
| `GET /api/reports/campaign-comparison` | ❌ | Grafik 4. |
| `GET /api/reports/duration-histogram` | ❌ | Grafik 5. |
| `GET /api/reports/funnel` | ❌ | Grafik 6 (Qo'ng'iroq → Javob → Shaxs tasdiqlandi → Suhbat → Natija). |
| `GET /api/reports/export?format=pdf|csv|xlsx` | ❌ | "⇩ Hisobotni yuklab olish". |
| `POST /api/reports/schedule` (email, davriylik) | ❌ | "📅 Jadval bo'yicha yuborish". |

---

## 11. Sozlamalar — §10.11

| Endpoint | Holat | Izoh |
|----------|-------|------|
| `GET/PUT /api/settings/company` | ❌ | Nom, logotip, timezone, til, manzil — ROADMAP Bosqich B (`company` jadvali) bilan bog'liq. |
| `GET/PUT /api/settings/telephony` | ❌ | SIP trunk ro'yxati + holat + "Sinash", caller ID/DID, parallel limit. Hozir trunk sozlamalari `application.yml`/`pjsip.conf` da statik. |
| `GET/PUT /api/settings/voice` | ⚠️ | `GET /api/tts/voices` katalogni beradi, lekin provayder/tezlik/tembr **sozlash** endpointi yo'q (hozir `application.yml` orqali). |
| `GET/PUT /api/settings/ai-model` | ❌ | Model, harorat, token, davomiylik/navbat limitlari — hozir config-fayl orqali. |
| `GET/PUT /api/settings/integrations` | ⚠️ | ROADMAP D (`CrmConnector`) rejalashtirilgan; hozir bitta `CrmClient` bor, panel orqali tanlash/sinash yo'q. |
| `GET/POST/DELETE /api/settings/api-keys` | ❌ | Hozir API kalitlar `SecurityProperties`/config orqali statik — panel orqali yaratish/bekor qilish uchun DB'ga ko'chirish va CRUD kerak. |
| `GET/PUT /api/settings/notifications` | ❌ | Kanal × hodisa matritsasi (kompaniya darajasida). |

---

## 12. Foydalanuvchilar — §10.12 (ROADMAP Bosqich E.1 — hali boshlanmagan)

| Endpoint | Holat | Izoh |
|----------|-------|------|
| `GET /api/users` | ❌ | `app_user` jadvali yo'q. |
| `POST /api/users/invite` | ❌ | Email + rol bilan taklif. |
| `PUT /api/users/{id}/role` | ❌ | Inline rol o'zgartirish. |
| `POST /api/users/{id}/block` \| `unblock` | ❌ | Holat ustuni. |

---

## 13. Audit jurnali — §10.13

| Endpoint | Holat | Izoh |
|----------|-------|------|
| `GET /api/reports/audit` | ✅ | Paginatsiya + sort bor. **Kengaytirish kerak:** aktor/amal-turi/obyekt-turi bo'yicha filtr (⚠️ hozir `AuditFilter`da faqat sort, `where` yo'q) — chapdagi filtr paneli uchun zarur. |
| IP manzil ustuni | ❌ | Dizaynda "IP (mono, Small)" bor, `AuditRow`da IP maydoni yo'q — `audit_log` jadvaliga ustun qo'shish kerak. |

---

## 14. Hisob-kitob (Billing) — §10.14 (ROADMAP Bosqich E.3 — hali boshlanmagan)

| Endpoint | Holat | Izoh |
|----------|-------|------|
| `GET /api/billing/plan` | ❌ | Joriy tarif kartochkasi. |
| `GET /api/billing/usage` | ⚠️ | O'lchov metrikalari (`voice_llm_tokens_*`, `voice_tts_chars_*`, `voice_stt_audio_seconds_*`) allaqachon yig'ilmoqda (Micrometer/Actuator), lekin **kompaniya kesimidagi `usage_record` jadvali va buni API orqali chiqarish yo'q**. |
| `GET /api/billing/invoices` | ❌ | Hisob-fakturalar jadvali + `⇩ PDF`. |

---

## 15. Profil sahifasi — §8.3

| Endpoint | Holat | Izoh |
|----------|-------|------|
| `GET/PUT /api/profile` | ❌ | Umumiy: avatar, ism, telefon, email, lavozim. |
| `PUT /api/profile/password` | ❌ | Xavfsizlik tab'i. |
| `GET /api/profile/sessions`, `DELETE /api/profile/sessions/{id}` | ❌ | Faol sessiyalar ro'yxati. |
| `GET/PUT /api/profile/notifications` | ❌ | Kanal × hodisa matritsasi (shaxsiy daraja, §11 dagi kompaniya darajasidan farqli). |
| `GET/PUT /api/profile/schedule` | ❌ | Operator ish jadvali (inbound transfer uchun, ROADMAP C.4 bilan bog'liq). |
| Popover'dagi "Bugun" mini-statistika (24/18m/92%) | ❌ | `GET /api/profile/today-stats` yoki `GET /api/reports/dashboard/kpi`ning operator-scoped varianti. |

---

## Xulosa — ustuvorlik bo'yicha guruhlash

Dizaynning "1-to'plam" (§18.1, birinchi chiziladigan ekranlar) bilan taqqoslasak:

1. **App Shell + xodim kartochkasi (§7–9)** — asosan `0.3 GET /api/auth/me` ga bog'liq;
   auth tizimi bo'lmasa ham panel statik/mock foydalanuvchi bilan chizilishi mumkin.
2. **Boshqaruv paneli (§10.2)** — barcha rejalashtirilgan endpoint ✅ **qo'shildi**: 3 ta
   agregatsiya (KPI, timeseries, outcomes — `ReportRepository.dashboardTotals`/
   `dashboardBuckets`/`dashboardOutcomes`, yangi jadval kerak bo'lmadi) va
   `GET /api/calls/live` (`DialogEngine.liveDialogs()` + `OutboundCallRegistry` +
   `CampaignService`, `AriService.liveCalls()`da birlashtiriladi).
3. **Qo'ng'iroqlar jadvali + tafsilot (§10.4–10.5)** — 80% tayyor; asosiy tirnov:
   filtrlash (matn/kampaniya/natija/sana) va "Texnik" tab uchun qo'shimcha maydonlar.
4. **Jonli qo'ng'iroqlar (§10.3)** — real-vaqt push kanali ✅ qo'shildi
   (`GET /api/live/stream`, SSE — §0.7).
5. **Ssenariy (§10.7)** — CRUD + validatsiya ✅ qo'shildi (ROADMAP A.4); `DialogEngine`ni
   ssenariy ta'rifi bilan ishga tushirish (A.3) hali qolgan.
6. **Kompaniya izolyatsiyasi (ROADMAP B)** — DB darajasida ✅ qo'shildi: `company` jadvali,
   `company_id` — `campaign`, `campaign_target`, `call_attempt`, `scenario` (nullable —
   builtin shablonlar global), `do_not_call_list`, `audit_log`da; barcha tegishli
   repository so'rovlari shu ustun bilan filtrlanadi. **Company-resolution hozircha
   vaqtincha bitta konstantali** (`voice-agent.company.default-id`, `CurrentCompany`
   interfeysi orqali) — haqiqiy per-request aniqlash (auth/api_key → company_id)
   Bosqich E.1 bilan keladi. Kompaniya sozlash API'si (§11 `GET/PUT /api/settings/company`)
   hali ❌ — bu safar faqat izolyatsiya infratuzilmasi qo'shildi, panel emas.
7. **Kontaktlar (§10.8)** — CRUD + CSV import + DNC tab (ro'yxat va soft-delete
   o'chirish) ✅ qo'shildi — ROADMAP'da rejalashtirilmagan, panel uchun yangidan
   loyihalandi.
8. **Kiruvchi marshrut, Foydalanuvchi, Billing (§10.9, 10.12, 10.14)** — bularning har
   biri ROADMAP'da alohida bosqich (C, E) sifatida rejalashtirilgan va hali
   boshlanmagan; dizayn ularni chizishi mumkin, lekin orqasidagi API'lar shu bosqichlar
   amalga oshgach paydo bo'ladi.
