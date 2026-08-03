# API talablari — Nido paneli uchun

> **Bu hujjat nima?** `UI-DESIGN.md` dagi har bir ekranni ishga tushirish uchun kerak
> bo'ladigan backend API'larning ro'yxati. Hozirgi kodga (2026-08-02 holati) qarab
> har biri belgilangan: ✅ **mavjud**, ⚠️ **qisman mavjud** (kengaytirish kerak), ❌ **yo'q**
> (yangidan yozish kerak). Barcha yangi/o'zgargan endpoint proyekt standarti bo'yicha
> `ResponseData<T>` / paginatsiyada `PageableData<T>` qaytarishi shart (§ ilgari
> qo'shilgan `shared.api` paketi). **To'liq ✅ bo'lgan bo'limlar bu yerda bir qatorga
> qisqartirilgan** — endpoint darajasidagi to'liq tafsilot uchun `docs/api/*.md`ga
> qarang; bu hujjat faqat qolgan (⚠️/❌) ishni ko'rsatadi.

**Qisqacha holat:** UI-DESIGN'dagi deyarli barcha ekran uchun API tayyor (0–15 bo'lim,
ROADMAP Bosqich A/B/C/E.1). Ochiq qolgan yagona katta blok — **Bosqich D (Uysot OAuth)**
va **Bosqich E.3/E.4 (billing, to'liq boshqaruv paneli)** — ular hali boshlanmagan.
Qolgani mayda ⚠️ kengaytirishlar (pastga qarang).

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

✅ Bajarilgan: **0.1** login, **0.2** logout, **0.3** joriy foydalanuvchi, **0.5** kompaniya
tanlagich, **0.6** qidiruv, **0.7** jonli SSE oqimi — [auth.md](api/auth.md),
[companies.md](api/companies.md), [search.md](api/search.md), [live.md](api/live.md).

| # | Endpoint | Holat | Izoh |
|---|----------|-------|------|
| 0.4 | `POST /api/auth/uysot/callback` | ⚠️ | "Uysot bilan kirish" (§10.1, ROADMAP D.2, OAuth) — endpoint bor, lekin stub (`502`): Uysot OAuth kredensiallari hali yo'q. |
| 0.8 | `GET /api/notifications`, `POST /api/notifications/{id}/read` | ⚠️ | Topbar qo'ng'iroq ikonkasi (§9), popover'dagi bildirishnoma toggle'lari (§8.2) — [notifications.md](api/notifications.md). Ikki turi ulangan (`OPERATOR_REQUEST`, `ERROR_OCCURRED`); `CAMPAIGN_FINISHED`/`DAILY_REPORT` uchun producer hali yo'q. |

---

## 1. Kirish (Login) — §10.1

Faqat 0.1/0.3/0.4 kerak. Boshqa hech narsa yo'q (statik dizayn sahifasi).

---

## 2. Boshqaruv paneli (Dashboard) — §10.2

✅ To'liq bajarilgan — 4 ta KPI kartochka (`changePct` + sparkline), dinamika grafik,
natijalar taqsimoti, jonli qo'ng'iroqlar paneli. Yagona ochiq mayda band: faol
kampaniyalar bloki uchun `GET /api/campaigns`da status filtri yo'q, faqat sort/paginatsiya
bor. Batafsil: [reports.md](api/reports.md), [live.md](api/live.md).

---

## 3. Jonli qo'ng'iroqlar (monitoring) — §10.3

✅ To'liq bajarilgan — jonli ro'yxat, audio-daraja va transkript SSE oqimi, tinglash/
uzatish/tugatish amallari. Batafsil: [live.md](api/live.md), [calls.md](api/calls.md).

---

## 4. Qo'ng'iroqlar (tarix) — §10.4

✅ Asosiy jadval, eksport va ommaviy amal bajarilgan — [reports.md](api/reports.md).

| Endpoint | Holat | Izoh |
|----------|-------|------|
| Ustunlar sozlamasi (foydalanuvchi profilida saqlash) | ⚠️ | "Ustunlar ⚙" — bloklovchi sabab (profil API yo'qligi) endi yo'q ([profile.md](api/profile.md), §15 ✅), lekin ustun-tanlovini saqlash uchun maxsus maydon/endpoint hali qo'shilmagan. Client-side (localStorage) bilan ham vaqtincha yopilishi mumkin. |

---

## 5. Qo'ng'iroq tafsiloti (drawer) — §10.5

✅ To'liq bajarilgan — transkript, natija, "Texnik" tab (`CallTechnicalDetail`), TXT
yuklab olish. Batafsil: [reports.md](api/reports.md).

---

## 6. Kampaniyalar — §10.6

✅ CRUD, arxivlash, start/pause, nishonlar (targets), CSV import, ssenariy bog'lash
(`campaign.scenario_id`, ROADMAP A.3) bajarilgan — [campaigns.md](api/campaigns.md).

| Endpoint | Holat | Izoh |
|----------|-------|------|
| CSV ustun moslashtirish oldindan ko'rish | ⚠️ | Hozirgi `addTargetsCsv` faylni to'g'ridan-to'g'ri import qiladi va xatolarni qaytaradi (`CsvImportResult`) — lekin dizayndagi "ustunni moslashtirib, keyin tasdiqlash" ikki bosqichli oqim emas, bitta so'rov. Kerak bo'lsa `POST /api/campaigns/{id}/targets/csv/preview` qo'shiladi. |

---

## 7. Ssenariylar — §10.7 (ROADMAP Bosqich A)

✅ To'liq bajarilgan — CRUD, validatsiya (deadlock/nom to'qnashuvi), klonlash,
versiyalash, va `DialogEngine` endi har qanday ssenariyni bajaradi (ROADMAP A.3);
`POST /api/calls?scenarioId=` orqali kampaniyasiz sinov qo'ng'irog'i ham ishlaydi.
Batafsil: [scenarios.md](api/scenarios.md), [calls.md](api/calls.md).

---

## 8. Kontaktlar — §10.8

✅ To'liq bajarilgan — CRUD, CSV import, DNC ro'yxati (ro'yxat + soft-delete o'chirish).
ROADMAP'da alohida bosqich sifatida rejalashtirilmagan edi, panel uchun yangidan
loyihalandi. Batafsil: [contacts.md](api/contacts.md), [do-not-call.md](api/do-not-call.md).

---

## 9. Kiruvchi marshrutlar — §10.9 (ROADMAP Bosqich C.1 — bajarildi, 2026-08-01)

✅ To'liq bajarilgan — DID → ssenariy/til/ish-vaqti CRUD + statistika. Batafsil:
[inbound-routes.md](api/inbound-routes.md).

---

## 10. Hisobotlar — §10.10

✅ To'liq bajarilgan — 6 ta grafik (dinamika, natijalar taqsimoti, soat×kun heatmap,
kampaniya taqqoslash, davomiylik histogram, voronka), CSV/PDF/XLSX eksport, email
jadval. Batafsil: [reports.md](api/reports.md).

---

## 11. Sozlamalar — §10.11

| Endpoint | Holat | Izoh |
|----------|-------|------|
| `GET/PUT /api/companies/{id}` va `/{id}/config` | ⚠️ | Nom/holat (`Company`) va timezone/til/dial-window (`CompanyConfig`) allaqachon bor — faqat logotip/manzil yo'q. Alohida `/api/settings/company` yo'q, chunki bu platform-admin resursi (`{id}` bilan), foydalanuvchining o'z kompaniyasiga emas. |
| `GET/POST/PUT/DELETE /api/sip-trunks` | ✅ | Trunk CRUD + "default qilish" + holat — [sip-trunks.md](api/sip-trunks.md). |
| `GET/PUT /api/settings/voice` | ⚠️ | `GET /api/tts/voices` katalogni beradi, lekin provayder/tezlik/tembr **sozlash** endpointi yo'q (hozir `application.yml` orqali). |
| `GET/PUT /api/settings/ai-model` | ✅ | Model/harorat/token/davomiylik limitlari kompaniya darajasida DB'da (`aimodel` feature). Navbat (parallel qo'ng'iroq) limiti hamon jarayon darajasida. |
| `GET/PUT /api/settings/integrations` | ⚠️ | Uysot CRM OAuth (authorization-code) kompaniya darajasida qo'shildi (`integration` feature) — client_id/secret panel orqali. Uysot'ning haqiqiy OAuth URL'lari hali noma'lum, sozlanmaguncha inert. Boshqa CRM connectorlari (ROADMAP D `CrmConnector`) hali yo'q. |
| `GET/POST/DELETE /api/settings/api-keys` | ✅ | DB'ga ko'chirilgan (`apikey` feature), kompaniyaga scoped. |
| `GET/PUT /api/settings/notifications` | ✅ | Kompaniya darajasidagi kanal × hodisa matritsasi — [settings.md](api/settings.md). Shaxsiy (per-user) varianti — §15/[profile.md](api/profile.md). |

---

## 12. Foydalanuvchilar — §10.12 (ROADMAP Bosqich E.1 — ✅ asosiy qism bajarildi)

✅ To'liq bajarilgan — invite/rol/block-unblock, ADMIN/OPERATOR/VIEWER. Batafsil:
[users.md](api/users.md).

---

## 13. Audit jurnali — §10.13

✅ To'liq bajarilgan — filtr (aktor/amal-turi/obyekt-turi), IP manzil ustuni. Batafsil:
[reports.md](api/reports.md).

---

## 14. Hisob-kitob (Billing) — §10.14 (ROADMAP Bosqich E.3 — hali boshlanmagan)

| Endpoint | Holat | Izoh |
|----------|-------|------|
| `GET /api/billing/plan` | ❌ | Joriy tarif kartochkasi. |
| `GET /api/billing/usage` | ⚠️ | O'lchov metrikalari (`voice_llm_tokens_*`, `voice_tts_chars_*`, `voice_stt_audio_seconds_*`) allaqachon yig'ilmoqda (Micrometer/Actuator), lekin **kompaniya kesimidagi `usage_record` jadvali va buni API orqali chiqarish yo'q**. |
| `GET /api/billing/invoices` | ❌ | Hisob-fakturalar jadvali + `⇩ PDF`. |

---

## 15. Profil sahifasi — §8.3

✅ Umumiy ma'lumot, parol, faol sessiyalar, shaxsiy bildirishnoma matritsasi, ish
jadvali bajarilgan (`profile` feature, V11–V14 migratsiya). Batafsil: [profile.md](api/profile.md).

| Endpoint | Holat | Izoh |
|----------|-------|------|
| Popover'dagi "Bugun" mini-statistika (24/18m/92%) | ✅ | `GET /api/profile/today-stats` — haqiqiy operator-scoped (`call_attempt.operator_user_id` orqali). Batafsil: [profile.md](api/profile.md#get-apiprofiletoday-stats--popover-mini-statistika). |

---

## Qolgan ish — ustuvorlik bo'yicha

1. **Bosqich D — Uysot OAuth** (`0.4`, `11`'dagi integratsiya URL'lari) — Uysot'dan
   kredensial/hujjat kelmaguncha bloklangan.
2. **Bosqich E.3 — Billing** (`14`) — hali boshlanmagan, `usage_record` jadvali kerak.
3. **Bosqich E.4 — To'liq boshqaruv paneli** — hozirgi test-panel o'rniga React/Vue,
   shu jumladan ssenariy vizual tahrirlagichi.
4. **ROADMAP C.4 — Operator navbati** — `user_schedule_slot` (§15) ma'lumoti hozircha
   faqat saqlanadi, kiruvchi marshrutlash uni hali o'qimaydi.
5. Mayda ⚠️ bandlar: `0.8` bildirishnoma producerlari (2 tasi qoldi), CSV ustun
   moslashtirish oldindan ko'rish (`6`), qo'ng'iroqlar jadvali ustun sozlamasi (`4`),
   `settings/voice` sozlash endpointi va kompaniya logotip/manzil (`11`).
