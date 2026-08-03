# ROADMAP — Kelajak rejasi

> **Maqsad:** hozirgi "qarzdorlik bo'yicha outbound qo'ng'iroq" tizimini universal AI
> qo'ng'iroq platformasiga aylantirish — Uysot bilan birga ham (OAuth orqali), mustaqil
> mahsulot sifatida ham ishlaydigan, inbound va outbound qo'ng'iroqlarni, turli suhbat
> turlarini (qarzdorlik, potensial mijozlar, so'rovnoma, eslatma...) qo'llab-quvvatlaydigan.

---

## 1. Hozirgi holat (2026-07 baholash)

PROJECT.md dagi 0–12 bosqichlarning deyarli hammasi ishlaydi:

| Qatlam     | Holat                                                                                                                                      |
|------------|--------------------------------------------------------------------------------------------------------------------------------------------|
| Telefoniya | ✅ Asterisk 20 + ARI + externalMedia, trunk va softphone, HangupCause, AMD                                                                 |
| Audio      | ✅ RTP (Netty), G.711, jitter buffer, Silero VAD, barge-in, SpeechGate                                                                     |
| STT/TTS    | ✅ Provayder-abstraksiya: Yandex (default) / Google; TTS katalog, kesh, warm-up                                                            |
| LLM        | ✅ Gemini (OpenAI-compat endpoint), streaming, tool calling, token budjeti, FactGuard                                                      |
| Dialog     | ✅ Scenario Engine orqali (ROADMAP A.1–A.4, 2026-08-01) — `DialogEngine` kampaniyaga bog'langan `ScenarioDefinition`ni ishga tushiradi, qattiq qarzdorlik FSM'i emas; guardrails, NoInputWatchdog, outcome-schema asosidagi CallSummary; 5 ta built-in shablon, custom ssenariy CRUD/validatsiya/versiyalash ham tayyor |
| Dialer     | ✅ Kampaniya CRUD, CSV import, retry, dial window/days, daily cap, do-not-call                                                             |
| Natijalar  | ✅ Transkript, yozuv (MinIO/disk), hisobotlar, audit, retention                                                                            |
| Xavfsizlik | ✅ API kalit (full/read-only) + JWT foydalanuvchi login (ADMIN/OPERATOR/VIEWER, ROADMAP E.1, 2026-08-02), audit log                        |
| CRM        | ⚠️ REST client, endi per-company OAuth token bilan; Uysot authorization-code oqimi kod darajasida tayyor, faqat Uysot'ning haqiqiy OAuth URL'lari kutilmoqda (D.2) |
| Inbound    | ✅ C.1/C.2/C.3 (ROADMAP, 2026-08-01) — DID → ssenariy/til marshrutlash, chaqiruvchi CRM orqali tanilishi, 3 ta inbound shablon. Operator navbati (C.4) hali yo'q |

### Asosiy cheklov: qarzdorlik kodga "qotirilgan" — ✅ HAL QILINDI (ROADMAP A.3, 2026-08-01)

Suhbat mantig'i to'liq qarzdorlikka bog'langan edi — boshqa suhbat turini qo'shish
uchun quyidagi sinflarni o'zgartirish kerak bo'lardi:

- ~~`DialogState` — GREETING → IDENTITY_CHECK → DEBT_NOTICE → ... (qattiq enum)~~ →
  o'chirildi, bosqich id'lari endi ssenariyning `stages[]`idan olingan `String`
- ~~`CallContext` — clientName, debtAmount, dueDate, contractNumber (qarz maydonlari)~~ →
  `Map<String,Object> facts`, `factSchema` bo'yicha to'ldiriladi
- `DialogTools` — `recordPaymentPromise`/`recordRefusalReason` real kod-guardraili
  (o'tmish sanani rad etish) tufayli qoldi, lekin faqat ssenariy shu nomlarni e'lon
  qilsagina ro'yxatga olinadi; boshqa har qanday ssenariy tool'i generik
  `ScenarioToolCallbackFactory` orqali runtime'da quriladi
- `SystemPromptFactory` — rolePrompt/faktlar/guardrails/bosqich matni endi
  `ScenarioDefinition`dan quriladi (stablePrefix/turnAnnex bo'linishi saqlangan holda)
- `CallSummary` — endi `Map<String,Object> outcome` (ssenariyning `outcomeSchema`siga
  mos); `call_result.outcome` JSONB ustuni + eski `reason_code`/`promised_date`/
  `promised_amount` ustunlari dual-write bilan saqlanadi (CRM/CSV eksport uchun)

Kampaniya endi `scenario_id` orqali ssenariyga bog'lanadi (majburiy, yaratishda
tanlanadi, keyin o'zgartirilmaydi). `POST /api/calls?scenarioId=` — ssenariyni
kampaniyasiz sinash uchun. Batafsil: `docs/api/scenarios.md`, `docs/api/campaigns.md`,
`docs/api/calls.md`; migratsiya — `V4__scenario_binding.sql`.

---

## 2. Maqsadli arxitektura

```
                        ┌────────────────────────────────┐
                        │  Scenario Engine (yadro)        │
                        │  - ssenariy = holatlar + tool'lar│
                        │  - faktlar sxemasi + guardrails │
                        │  - natija sxemasi (summary)     │
                        └───────┬────────────────┬───────┘
                                │                │
                   ┌────────────▼───┐    ┌───────▼────────────┐
                   │ OUTBOUND       │    │ INBOUND            │
                   │ kampaniya      │    │ raqam → ssenariy   │
                   │ (dialer)       │    │ routing            │
                   └────────┬───────┘    └───────┬────────────┘
                            │                    │
                        ┌───▼────────────────────▼───┐
                        │  Integration qatlami        │
                        │  (CrmConnector interfeysi)  │
                        ├────────────┬───────────────┤
                        │ Uysot      │ Standalone    │
                        │ (OAuth2)   │ (CSV/REST/API)│
                        └────────────┴───────────────┘
```

Ikkita rejim, bitta kod:

- **Uysot rejimi** — OAuth2 bilan Uysot tizimiga kiriladi, mijozlar/qarzdorlik/leadlar
  Uysot API dan olinadi, natijalar Uysot'ga qaytariladi.
- **Mustaqil rejim** — har qanday kompaniya ishlatadi: CSV import, ochiq REST API,
  keyinchalik boshqa CRM connectorlari (Bitrix24, amoCRM...).

Ikkala rejimda ham asosiy birlik — **kompaniya** (Bosqich B): har bir kompaniyaning
kampaniyalari, ssenariylari, transkriptlari va yozuvlari qat'iy ajratilgan; ssenariylar
esa built-in shablonlar bilan cheklanmaydi — kompaniya o'z custom ssenariylarini
yaratadi (A.4).

---

## 3. Bosqichlar

### Bosqich A — Scenario Engine (poydevor, hammasi shunga bog'liq)

Qarzdorlik mantig'ini koddan **ssenariy ta'rifi**ga chiqarish. `campaign.script_config`
(JSONB) ustuni allaqachon bor — endi u haqiqatan ishlatiladi.

**A.1 — ScenarioDefinition modeli — ✅ BAJARILDI**

```java
public record ScenarioDefinition(
    String id,                     // "debt-collection", "lead-qualification"
    String name,
    List<StageDef> stages,         // holatlar: id, maqsad, ruxsat etilgan o'tishlar
    List<FactField> factSchema,    // faktlar: nom, tur, majburiymi (prompt + FactGuard uchun)
    List<ToolDef> tools,           // ssenariyga xos tool'lar (natija maydonlariga yozadi)
    List<OutcomeField> outcomeSchema, // yakuniy xulosa maydonlari
    String rolePrompt,             // "Siz ... agentisiz"
    List<String> guardrails,       // qat'iy qoidalar
    String disclosureText          // ochilish matni (§11.1 — majburiy qoladi)
) {}
```

- `DialogState` enum → ssenariydagi `stages` ro'yxati (String id).
- `CallContext` → `Map<String, Object> facts` + `factSchema` bo'yicha validatsiya.
- `CallSummary` / `call_result` → umumiy `outcome` JSONB + ssenariy sxemasi.
- Umumiy tool'lar o'zgarmaydi: `transitionTo`, `endCall`, `requestHumanTransfer`,
  `recordWrongPerson`, `recordDoNotCall`. Ssenariy tool'lari deklarativ:
  "recordPaymentPromise: date (kelajakda bo'lishi shart), amount, note".
- `SystemPromptFactory` ssenariydan quriladi — **stablePrefix/turnAnnex bo'linishi
  saqlanadi** (Gemini kesh ishlashi uchun, RUN.md dagi ogohlantirish).
- FactGuard umumlashtiriladi: faktlar ro'yxati sxemadan olinadi.

**A.2 — Tayyor shablonlar (seed) — ✅ BAJARILDI**

Barcha 5 shablon `V1__baseline.sql`da `scenario` jadvaliga seed qilingan (`is_builtin=true`):

| Shablon                | Maqsad                                                      | Asosiy natija               |
|------------------------|-------------------------------------------------------------|-----------------------------|
| `debt-collection`      | hozirgi ssenariy, aynan shu xatti-harakat bilan             | promisedDate, reasonCode    |
| `lead-qualification`   | potensial mijoz: qiziqish, budjet, muddat, uchrashuv        | interest, budget, meetingAt |
| `notification`         | bir tomonlama xabar + tasdiqlash (to'lov eslatmasi, TDS...) | acknowledged                |
| `survey`               | 3–5 savollik so'rovnoma / NPS                               | answers[], score            |
| `appointment-reminder` | uchrashuvni eslatish/tasdiqlash/ko'chirish                  | confirmed, newTime          |

**A.3 — Kampaniya ssenariyga bog'lanadi — ✅ BAJARILDI (2026-08-01)**

- `campaign.scenario_id` qo'shildi (majburiy, FK `scenario.id`ga, yaratilgandan keyin
  o'zgarmaydi); `CreateCampaignRequest.scenarioId` orqali tanlanadi.
- Migratsiya (`V4__scenario_binding.sql`): mavjud kampaniyalar `debt-collection`ga
  bog'landi; shu seedning JSON ta'rifi eski hardcoded prompt/guardrails/bosqich
  matniga aynan mos keladigan qilib to'g'rilandi (rolePrompt, 5 ta qarz-xos guardrail,
  9 ta bosqichning purpose/allowedTransitions/allowedTools) — xatti-harakat
  o'zgarmasligi shu orqali ta'minlandi. Platforma-darajasidagi 5 ta guardrail
  (§11.1 disclosure, PII, noaniq javob/haqorat → eskalatsiya, noto'g'ri odam,
  opt-out) endi `SystemPromptFactory`da kod darajasida, har qanday ssenariyga
  qo'llanadi.
- `DialogEngine`/`SystemPromptFactory`/`DialogSession`/`DialogTools`/`FactGuard`/
  `CallContextMapper` to'liq generic — `DialogState` enum o'chirildi.
- **Qoldiq:** panelda CSV ustunlarini faktlar sxemasiga moslab ko'rsatish (UI ishi,
  E.4 bilan birga qilinishi mumkin).

**A.4 — Custom ssenariylar (foydalanuvchi o'zi yaratadi) — ✅ BAJARILDI (CRUD/validatsiya/versiyalash); qoldiq: vizual tahrirlagich (E.4)**

Tayyor shablonlar bilan cheklanmaslik — foydalanuvchi o'z ssenariysini yarata olishi kerak:

- Ssenariylar DB da saqlanadi (`scenario` jadvali, ta'rif JSONB). Built-in shablonlar
  **read-only** seed sifatida yuklanadi; foydalanuvchi ularni **nusxalab** (clone)
  o'zgartiradi yoki noldan yaratadi. — `ScenarioService.create`/`clone`.
- CRUD API: `GET/POST/PUT /api/scenarios`, clone endpoint. — `ScenarioController`
  (`POST /api/scenarios`, `POST /api/scenarios/list`, `GET/PUT /api/scenarios/{id}`,
  `POST /api/scenarios/{id}/clone`, `POST /api/scenarios/validate`). Panel: hozircha
  JSON tahrirlash + validatsiya; vizual tahrirlagich E.4 ga qoldirilgan.
- **Validatsiya majburiy:** har bosqichdan yakuniy holatga yo'l borligi (deadlock yo'q,
  BFS bilan tekshiriladi), tool'lar/outcome/fakt nomlari to'qnashmasligi, noma'lum
  stage/tool'ga ishora qilinmasligi, reserved tool nomlari band qilinmasligi — xato
  ssenariy saqlanmaydi. — `ScenarioValidator`.
- **Versiyalash:** kampaniya ssenariyning muayyan **versiyasiga** (aniq qatorga, FK
  orqali) bog'lanadi — `ScenarioService.update` eski qatorni o'zgartirmaydi, yangi
  versiya qo'shib eskisini deaktivlaydi (`@Transactional`); aktiv kampaniya o'rtasida
  ssenariy tahrirlansa, ketayotgan qo'ng'iroqlar eski versiyada davom etadi.
- **Guardrails tegilmaydi:** umumiy taqiqlar (§5.1) va disclosure `SystemPromptFactory`da
  kod darajasida — custom ssenariy ularni o'chira yoki yumshata olmaydi. Ssenariy faqat
  *qo'shimcha* qoida kirita oladi.
- Sinov rejimi: ssenariyni kampaniyasiz bitta test raqamga qo'ng'iroq bilan sinash —
  `POST /api/calls?scenarioId=`.

**Umumiy qoldiq (Bosqich A):** kod va birlik-darajasidagi validatsiya (deadlock,
nom to'qnashuvi) tayyor, lekin `lead-qualification`dan tashqari qolgan 4 shablon
va custom-ssenariy oqimining o'zi hali real qo'ng'iroq bilan sinalmagan (§5 xavf 6);
vizual tahrirlagich E.4 ga qoldirilgan.

---

### Bosqich B — Kompaniya (tashkilot) va ma'lumot izolyatsiyasi

Platforma bir nechta kompaniyaga xizmat qiladi — Uysot rejimida ham, mustaqil rejimda
ham. Bitta kompaniyaning kampaniyalari, nishonlari, transkriptlari, yozuvlari va
ssenariylari boshqasinikidan **qat'iy ajratilgan** bo'lishi shart. Bu bosqich ataylab
oldinga qo'yilgan: jadvallar va funksiyalar ko'payganidan keyin `company_id` qo'shish
ancha qimmatga tushadi.

**B.1 — Ma'lumot modeli — ✅ sxema + CRUD bajarildi (2026-08-01); qoldiq: E.1 (real foydalanuvchi)**

- `company` jadvali — **identifikatsiya, shu**: nom, status. Sozlamalar (default til,
  timezone, dial-window) 2026-08-01'da alohida `company_config` jadvaliga chiqarildi
  (`V8__company_config.sql`) — `Company`da vaqtincha turgan bu maydonlar hech qayerda
  o'qilmasdan "o'lik" qolgani aniqlangandan keyin ("kompaniyada faqat kompaniya haqida
  ma'lumot bo'lsin" printsipi). — `V1__baseline.sql` (`company`), `V8` (`company_config`).
- `company_config` — har kompaniyaga bitta qator: `dialWindowStart`/`dialWindowEnd`
  (**qat'iy chegara** — `DialerService` har kampaniyani o'z oynasi BILAN BIRGA shu
  oraliqqa ham tekshiradi), `timezone`, `supportedLanguages` (tartiblangan ro'yxat,
  birinchisi — default til; kampaniya yaratish/tahrirlashda tanlangan til shu
  ro'yxatda bo'lishi shart, aks holda `400` — `CompanyConfigService.resolveLanguage`,
  `CampaignService` orqali ishlatiladi). Har yangi kompaniya avtomatik default config
  bilan yaratiladi (`uz-UZ`, 09:00–20:00, `Asia/Tashkent`).
- Barcha ma'lumot jadvallariga `company_id`: `campaign`, `campaign_target`,
  `call_attempt`, `scenario` (nullable — built-in uchun), `do_not_call_list`,
  `audit_log`, `contact`, `inbound_route`, `sip_trunk`. `call_transcript`/`call_result`
  to'g'ridan-to'g'ri emas, `call_attempt`ga FK orqali izolyatsiya qilinadi (join bilan
  yetarli, ustun takrorlanmaydi). `usage_record` hali yo'q (E.3 bilan birga keladi).
- Flyway migratsiya: mavjud ma'lumotlar avtomatik "default" (id=1) kompaniyaga
  bog'langan — tarix yo'qolmagan; `company_config` migratsiyasi ham eski
  `company.default_language`/`timezone`/`dial_window_*` qiymatlarini ko'chirib
  o'tkazadi, xatti-harakat o'zgarmaydi.
- **Company CRUD** (`uz.murodjon.uysotvoice.company`): `POST /api/companies`,
  `POST /api/companies/list`, `GET/PUT /api/companies/{id}` (identifikatsiya) +
  `GET/PUT /api/companies/{id}/config` (sozlamalar) — yangi kompaniya yaratish va
  sozlamalarini tahrirlash endi API orqali mumkin (ilgari faqat SQL bilan seed
  qilingan edi). Batafsil: `docs/api/companies.md`. **Diqqat:** yangi kompaniya
  yaratish hali amaliy ma'no bermaydi — pastdagi B.2'dagi "API kalit → company_id"
  bog'lanmaguncha barcha so'rov baribir default (id=1) kompaniyaga ishlaydi.

**B.2 — Izolyatsiyani majburlash — ✅ BAJARILDI (repository-filtr + real API kalit → company_id, backend-uchun-talablar.md §8)**

- Repository darajasida **majburiy** `company_id` filtri — `CurrentCompany`
  interfeysi `CampaignRepository`, `CampaignTargetRepository`, `ContactRepository`,
  `DoNotCallRepository`, `InboundRouteRepository`, `ScenarioRepository`,
  `ReportRepository`, `AuditService`, `CallRecordService` — barchasida ishlatiladi.
  Qo'shimcha qatlam sifatida Postgres RLS hamon baholanmagan (ixtiyoriy).
- **`X-Api-Key` → `company_id`: ✅ hal qilindi.** `api_key` jadvali (`V7__api_key.sql`,
  `uz.murodjon.uysotvoice.apikey`) har kalitni bitta kompaniyaga bog'laydi; panel
  orqali yaratiladi/bekor qilinadi (`POST/DELETE /api/settings/api-keys`, [settings.md](api/settings.md)).
  `security.ApiKeyFilter` uchta manbani navbat bilan tekshiradi: global admin kalit,
  global read-only kalit, keyin DB'dagi kompaniyaga-scoped kalit — oxirgisi
  `AuthenticatedUser` (xuddi JWT login qanday principal beradigan bo'lsa, shunday)
  ni o'z `companyId`si bilan o'rnatadi, shu sababli `JwtCurrentCompanyResolver`
  o'zgarishsiz ikkalasini ham to'g'ri scoped qiladi. Ikkita global konstantali kalit
  hamon ishlaydi — fallback/bootstrap kirish sifatida, `DefaultCompanyResolver`ga
  scoped bo'lib qoladi (ataylab, ular kompaniyaga xos emas).
- **JWT bilan kirgan foydalanuvchi uchun bu ✅ hal qilindi** (ROADMAP E.1, 2026-08-02)
  — `JwtCurrentCompanyResolver` har so'rovni foydalanuvchining haqiqiy kompaniyasiga
  scoped qiladi.
- **Qoldiq:** bitta foydalanuvchi bir nechta kompaniyaga a'zo bo'lishi (E.2) — hozir
  `app_user.company_id` bitta ustun, `user_company` ko'p-ko'pga jadvali yo'q;
  `GET /api/companies` shu sababli hamon har doim bitta elementli ro'yxat qaytaradi.
- Ssenariylar: built-in shablonlar global (`company_id IS NULL`, read-only),
  custom ssenariylar (A.4) `company_id` bilan — bir kompaniya boshqasining
  ssenariyini ko'rmaydi ham, ishlata olmaydi ham.
- Do-not-call ro'yxati kompaniya kesimida (mijoz bitta kompaniyaga taqiq qo'ygani
  boshqasiga tegmaydi).
- MinIO/diskdagi yozuvlar kompaniya prefiksi bilan saqlanishi — **hali
  tekshirilmagan/qilinmagan**.
- **Izolyatsiya regressiya testlari:** `CompanyIsolationTest` (`campaign` uchun:
  boshqa kompaniya campaign'i ID bo'yicha ko'rinmaydi, ro'yxatda chiqmaydi, count'ga
  kirmaydi, update no-op). Hozircha faqat `campaign` uchun — qolgan repository'lar
  (`contact`, `scenario`, `do_not_call`, `audit`, `inbound_route`, `report`) uchun
  xuddi shunday test hali yozilmagan.

**B.3 — Kompaniyaga xos telefoniya (minimal) — ✅ SIP trunk CRUD + wiring bajarildi (2026-08-01)**

- **`sip_trunk` jadvali** (`V6__sip_trunk.sql`): har kompaniya bir nechta PJSIP trunkga
  ega bo'lishi mumkin, ulardan bittasi **default** (`idx_sip_trunk_default` — company
  bo'yicha bitta default, `scenario`ning `idx_scenario_active_key`siga o'xshash
  qisman unique indeks). Bu API `pjsip.conf`ni o'zi boshqarmaydi — faqat Asterisk
  tomonda allaqachon sozlangan endpoint nomini kompaniyaga bog'laydi.
- **CRUD** (`uz.murodjon.uysotvoice.siptrunk`): `POST /api/sip-trunks`,
  `POST /api/sip-trunks/list`, `GET/PUT /api/sip-trunks/{id}`,
  `POST /api/sip-trunks/{id}/default` (default trunkni almashtirish),
  `DELETE /api/sip-trunks/{id}` (default trunk o'chirilmaydi — avval boshqasini
  default qiling). Batafsil: `docs/api/sip-trunks.md`.
- **Asterisk bilan ulanish**: `AriService.originate(number, companyId)` endi
  qo'ng'iroq kimning kampaniyasiga tegishli ekanini biladi (`Campaign.companyId` →
  `CallTask.companyId` → `CallTaskConsumer`) va o'sha kompaniyaning yoqilgan default
  trunkidan (`SipTrunkService.findDefaultForCall`) PJSIP endpoint va caller ID'ni
  oladi. Mahalliy test-softphone marshrutlash (`localNumberPattern`) o'zgarishsiz
  global qoladi — bu kompaniya tushunchasiga aloqasiz dev/test qulayligi. Trunk
  topilmasa (masalan yangi kompaniyada hali sozlanmagan) — global
  `voice-agent.asterisk.trunk-endpoint`/`caller-id`ga qaytadi, qo'ng'iroq baribir
  ketaveradi.
- **Default kompaniya (id=1) migratsiyasi**: `SipTrunkBootstrap` ilova birinchi marta
  ishga tushganda, agar default kompaniyada hali default trunk bo'lmasa, hozirgi
  `voice-agent.asterisk.trunk-endpoint`/`caller-id` konfiguratsiyasidan avtomatik
  bitta default trunk yaratadi — foydalanuvchi Asterisk'ga allaqachon sozlab qo'ygan
  trunk API orqali qayta kiritilmasdan o'z-o'zidan default bo'lib qoladi.
- **Qat'iy dial-window** (B.1 dagi asl "kompaniya darajasida cap" niyatining bir qismi)
  `company_config.dialWindowStart/End` orqali amalga oshirilgan — B.1'ga qarang.
  **Qoldiq:** kunlik qo'ng'iroq soni/parallel limit hamon faqat campaign darajasida,
  kompaniya darajasida yo'q (E.3 billing bilan birga kelishi mumkin). Trunk holatini
  (registered/unregistered) Asterisk'dan real-time tekshirish yo'q — CRUD faqat
  qaysi endpoint ishlatilishini biladi, uning tirikligini bilmaydi.

**Tekshiruv qoldi:** ikkinchi kompaniyaga ikkinchi PJSIP trunk ulab, o'sha
kompaniyaning qo'ng'irog'i haqiqatan shu trunk orqali ketishini real Asterisk'da
tasdiqlash — kod darajasida tayyor, hali sinalmagan.

---

### Bosqich C — Inbound qo'ng'iroqlar — C.1/C.2/C.3 ✅ BAJARILDI (2026-08-01)

Texnik poydevor bor edi (Stasis + externalMedia inbound'da ham ishlagan). Yetishmayotgan
"qaysi raqamga kim qo'ng'iroq qildi va qaysi ssenariy ishlasin" mantig'i endi qo'shildi.

**C.1 — Routing ✅**

- `inbound_route` jadvali (`V5__inbound_route.sql`): `did_number → company_id,
  scenario_id, language, business_hours_start/end, fallback_message, enabled`.
  CRUD — `docs/api/inbound-routes.md`.
- `AriService.setupMedia` endi genuine inbound qo'ng'iroqni manual test
  qo'ng'iroqdan aniq ajratadi (ikkalasi ham avval bir xil "OutboundCall yo'q"
  yo'liga tushardi) va StasisStart'da DID'ni (`channel.getDialplan().getExten()`,
  ari4java orqali) marshrut jadvalidan qidiradi — eski global "auto-start" flag
  o'rniga. Marshrut topilmasa yoki ish vaqtidan tashqari bo'lsa — qo'ng'iroq
  javobsiz tugatiladi (**pastga qarang — ovozli xabar hali ulanmagan**).

**C.2 — Qo'ng'iroq qiluvchini aniqlash ✅**

- `CrmClient.findByPhone` (yangi, `CrmProperties.clientByPhonePath`) — chaqiruvchi
  raqami CRM orqali tanilsa, faktlar (`CallContextMapper.merge` qayta ishlatilgan
  holda) promptga tushadi.
- Do-not-call ro'yxati inbound yo'lida umuman tekshirilmaydi — mijoz o'zi
  qo'ng'iroq qilsa, DNC'da bo'lsa ham javob oladi.

**C.3 — Inbound ssenariy shablonlari ✅**

3 ta yangi builtin ssenariy (`V5__inbound_route.sql`, kod o'zgarishisiz — Scenario
Engine A.3'dan buyon har qanday ssenariyni bajara oladi): `reception` (qabulxona,
savolga javob, kerak bo'lsa operatorga), `inbound-lead` (reklama qo'ng'irog'i:
qiziqish + kontakt + uchrashuv), `callback-request` (band bo'lganda vaqtni
so'rab **natija sifatida yozib oladi** — avtomatik outbound navbatga
**qo'yilmaydi**, chunki "qaysi kampaniyaga qo'shilsin" degan tanlov mexanizmi
hali yo'q; bu keyingi aniq belgilangan vazifa).

**Qoldiq / bilinigan soddalashtirishlar:**
- `fallback_message` ustuni saqlanadi, lekin hali RTP orqali aytilmaydi — marshrut
  topilmasa yoki ish vaqtidan tashqari bo'lsa qo'ng'iroq shunchaki javobsiz
  tugatiladi (real Asterisk'da sinalmagan RTP-bootstrap kodini ushbu bosqichda
  ikki marta yozishdan saqlanish uchun ataylab qoldirilgan).
- DID'ni aniqlash `channel.getDialplan().getExten()`ga tayanadi — bu haqiqiy
  Asterisk dialplan'ning `exten => <DID>,1,Stasis(app)` shaklida yozilganini
  talab qiladi; foydalanuvchi o'z dialplan'iga qarab tekshirishi kerak.
- `CurrentCompany` hamon bitta hardcoded default (B.2 hali qilinmagan) —
  `inbound_route.company_id` kelajak uchun saqlanadi, amalda hozircha bitta
  kompaniyaga tegishli.

**C.4 — Operator navbati — hali qilinmagan**

PROJECT.md Bosqich 11 (hali qilinmagan) inbound bilan birga zarur bo'ladi:
Asterisk queue, transfer'da kontekstni ko'rsatish, ish vaqti tekshiruvi.

**Tekshiruv qoldi:** tashqi raqamdan trunk raqamiga real qo'ng'iroq bilan C.1-C.3
oqimini sinash — kod darajasida tayyor, hali sinalmagan.

---

### Bosqich D — Uysot integratsiyasi (OAuth)

Har bir Uysot akkaunt platformadagi bitta **kompaniyaga** (B bosqichi) bog'lanadi —
OAuth orqali kirgan foydalanuvchi faqat o'z kompaniyasining ma'lumotlarini ko'radi.

**D.1 — CrmConnector interfeysi**

Hozirgi `CrmClient` umumiy interfeysga ajratiladi:

```java
public interface CrmConnector {
    CrmClientSnapshot fetchClient(String externalId);
    CrmClientSnapshot findByPhone(String phone);          // inbound uchun
    List<CampaignTarget> fetchSegment(SegmentQuery query); // kampaniya nishonlari
    Long postOutcome(String externalId, CallOutcome outcome); // natija/note
}
```

Implementatsiyalar: `UysotConnector`, `GenericRestConnector` (hozirgi CrmClient),
`NoopConnector` (faqat CSV rejimi). Tanlov konfiguratsiyada: `crm.provider=uysot|rest|none`.

**D.2 — Uysot OAuth2 — ⚠️ kod darajasida tayyor, Uysot'ning haqiqiy URL'lari kutilmoqda**

- Authorization-code oqimi qo'shildi (`uz.murodjon.uysotvoice.integration`,
  `spring-security-oauth2-client` o'rniga qo'lda HTTP client bilan): har kompaniya
  o'z Uysot OAuth ilovasini (`client_id`/`client_secret`, shifrlangan saqlanadi)
  panel orqali ulaydi, `PUT/GET /api/settings/integrations/uysot*`,
  `GET .../authorize-url`, `GET .../callback` (imzolangan `state` orqali
  kompaniyani identifikatsiya qiladi — hech qanday auth header'siz keladigan
  yagona endpoint).
- Token yangilash (`CrmIntegrationService#refresh`, muddatdan 2 daqiqa oldin
  avtomatik) va xatoda graceful degradatsiya (`CrmClient` shifrlangan tokeni
  topolmasa/yangilay olmasa statik konfiguratsiya tokeniga qaytadi — "CRM
  yiqilsa qo'ng'iroq baribir ketadi" printsipi saqlanadi) — ishlaydi.
- **Yagona to'siq:** `voice-agent.integration.uysot.authorize-url`/`token-url`/
  `redirect-uri` hali bo'sh (`UysotOAuthProperties#configured()` — Uysot'ning
  haqiqiy qiymatlarini kutmoqda). Shu qiymatlar kelgach, D.2 boshqa kod
  o'zgarishisiz ishga tushadi.
- **Qoldiq:** D.1'dagi `CrmConnector` abstraksiyasi (Uysot/Generic/Noop) qurilmagan
  — hozirgi `CrmClient` bitta implementatsiya, faqat per-company token bilan
  ishlaydigan qilib kengaytirilgan.

**D.3 — Uysot ma'lumotlari bilan ishlash**

- Qarzdorlar segmentini Uysot'dan to'g'ridan-to'g'ri tortish (CSV o'rniga):
  kampaniya yaratishda "Uysot segmenti" tanlanadi.
- Natijalar Uysot'ga: note + strukturali maydonlar (promisedDate...). Outbox pattern
  allaqachon bor (`CallOutboxService`) — faqat connector almashadi.
- Webhook (Uysot qo'llasa): yangi lead tushdi → avtomatik qo'ng'iroq navbati.

**Ochiq savollar (Uysot tomonidan aniqlash kerak):**
1. Uysot OAuth provayder sifatida qanday flow beradi? (authorization code? scope'lar?)
2. Mijoz/qarzdorlik/lead API endpointlari va sxemasi?
3. Webhook mexanizmi bormi?
4. Bir Uysot akkauntida bir nechta filial/kompaniya bo'lsa — platformada nechta
   `company` ochiladi, mapping qanday?

---

### Bosqich E — Mustaqil mahsulot (standalone / SaaS)

Kompaniya entity va izolyatsiya B da tayyor bo'lgani uchun bu bosqich "faqat"
foydalanuvchi qatlami va biznes qismini qo'shadi.

**E.1 — Foydalanuvchi modeli — ✅ asosiy qism bajarildi (2026-08-02)**

- `app_user` + rollar (`ADMIN`, `OPERATOR`, `VIEWER`), har foydalanuvchi bitta
  kompaniyaga a'zo (ko'p-kompaniyaga a'zolik — qoldiq, pastga qarang).
- Login: lokal email/parol, JWT (`Authorization: Bearer`) — `POST /api/auth/login`,
  `POST /api/auth/activate` (invite → parol, SMTP yo'qligi uchun bir martalik token),
  `GET /api/auth/me`. `POST /api/auth/uysot/callback` — stub, D bosqichi
  kredensiallarini kutmoqda.
- Hozirgi API-kalit rejimi saqlanadi (machine-to-machine) — endi uchta rol
  ierarxiyasiga moslashtirildi (`ADMIN` → `OPERATOR` → `VIEWER`,
  `config.SecurityConfig`). JWT bilan kirgan foydalanuvchi uchun
  `CurrentCompany` haqiqiy per-request aniqlanadi (`JwtCurrentCompanyResolver`)
  — B.2'dagi "auth bilan keladi" izohi shu. `X-Api-Key → company_id` xaritalash
  o'zi hamon qilinmagan (B.2'ning qolgan yarmi).
- Foydalanuvchi boshqaruvi (`GET/invite/role/block/unblock /api/users`),
  komanda-palitra qidiruvi (`GET /api/search`) va bildirishnomalar
  (`GET/PUT /api/notifications`) ham shu bilan birga qo'shildi — batafsil
  `docs/api/auth.md`, `docs/api/users.md`, `docs/api/search.md`,
  `docs/api/notifications.md`.
- **Server-side sessiya boshqaruvi — ✅ bajarildi (2026-08-02).** `user_session`
  jadvali (profil bosqichi bilan birga) `app_user`dagi eski bitta-ustunli
  refresh-token modelini almashtirdi — bir nechta qurilmadan bir vaqtda login
  qilish, `GET/DELETE /api/profile/sessions` bilan ularni ko'rish/tugatish endi
  ishlaydi (`docs/api/profile.md`).
- **Qoldiqlar:** ko'p-kompaniyaga a'zolik (hozir `app_user.company_id` — bitta
  ustun, jadval emas), real email yuborish (SMTP — hozir aktivatsiya tokeni
  admin tomonidan qo'lda yetkaziladi), `X-Api-Key → company_id` xaritalash.

**E.2 — Kompaniyaga xos resurslar (to'liq)**

- Har kompaniya o'z: SIP trunk (yoki umumiy trunk + caller ID), TTS ovozi, LLM/STT/TTS
  kalitlari (yoki platforma kalitlari + limit).
- Trunk'lar `pjsip.conf` da statik emas — Asterisk realtime (Postgres) yoki ARI orqali
  dinamik konfiguratsiya o'rganiladi.

**E.3 — Hisob-kitob (billing)**

- O'lchov allaqachon bor: `voice_llm_tokens_*`, `voice_tts_chars_*`,
  `voice_stt_audio_seconds_*`, trunk daqiqalari — endi ular kompaniya kesimida yig'iladi.
- `usage_record` jadvali: qo'ng'iroq → sarf (daqiqa, token, belgi). Tarif keyin.

**E.4 — To'liq boshqaruv paneli**

Hozirgi test-panel o'rniga alohida frontend (React/Vue): kampaniya boshqaruvi,
ssenariy tahrirlagichi (A.4 dagi JSON'ni vizual tahrirlash — bosqichlar, promptlar,
tool'lar), jonli monitoring, hisobotlar, foydalanuvchilar boshqaruvi.

---

### Bosqich F — Miqyos va ishonchlilik (E bilan parallel)

- Sticky routing (Redis `call_id → instance`) — spec'da bor, ko'p instance kerak bo'lganda.
- voice-agent va dialer'ni alohida deploy qilish imkoni (kod allaqachon paketlarga ajratilgan).
- Asterisk HA (ikkita instance, trunk failover).
- Alerting kengaytirish: disposition anomaliyalari, provayder xatolari, latency SLO.
- Load test: 20 → 100+ parallel qo'ng'iroq (virtual threads buni ko'taradi, RTP port
  diapazoni va CPU — tekshirish kerak).

---

## 4. Tavsiya etilgan tartib va bog'liqliklar

```
A (Scenario Engine + custom ssenariylar)  ──►  hammasi shunga quriladi
   │
   └──► B (Kompaniya + izolyatsiya)     jadvallar ko'paymasidan oldin arzon
           │
           ├──► C (Inbound)             route kompaniya+ssenariyga bog'lanadi
           │
           ├──► D (Uysot OAuth)         har Uysot akkaunt = bitta kompaniya
           │
           └──► E (Standalone/SaaS)     foydalanuvchi qatlami, billing, panel
                     │
                     └──► F (Scale)     real yuk paydo bo'lganda
```

| # | Bosqich | Taxminiy hajm | Natija |
|---|---|---|---|
| 1 | **A** — Scenario Engine + custom | katta — ✅ A.1-A.4 bajarildi (2026-08-01), qoldi: real-qo'ng'iroq testlari + vizual tahrirlagich (E.4) | 5 shablon + custom ssenariy CRUD, versiyalash |
| 2 | **B** — Kompaniya va izolyatsiya | o'rta — ✅ B.1/B.3 to'liq, B.2 repo-filtr qismi bajarildi (2026-08-01), qoldi: API kalit → company_id | `company` entity, to'liq ma'lumot ajratish, kalit bog'i |
| 3 | **C** — Inbound | o'rta — ✅ C.1-C.3 bajarildi (2026-08-01), C.4 qoldi | DID routing, mijozni tanish, 3 inbound shablon |
| 4 | **D** — Uysot OAuth              | o'rta — ⚠️ D.2 oqimi kod darajasida tayyor, Uysot API/URL kutilmoqda | "Uysot bilan kirish", segment import, natija eksport    |
| 5 | **E** — Standalone/SaaS          | katta — ✅ E.1 asosiy qism bajarildi (2026-08-02), qoldi: E.2/E.3/E.4 | foydalanuvchi/rollar ✅, billing o'lchovi va yangi panel hali qolgan |
| 6 | **F** — Scale                    | doimiy                       | HA, load test, alerting                                 |

**Nega A birinchi:** "potensial mijozlar bilan ishlash" so'rovi B–E siz ham A
tugashi bilan darhol qondiriladi — `lead-qualification` ssenariyli outbound kampaniya
CSV bilan bugun ham ishlaydi. Bu eng qisqa yo'l bilan ko'rinadigan yangi qiymat.

**Nega B ikkinchi:** izolyatsiya talabi (kompaniyalar ma'lumoti aralashmasligi)
qancha kech qilinsa, shuncha ko'p jadval va kod qatlamiga `company_id` retrofit
qilishga to'g'ri keladi. C–E bosqichlarining hammasi kompaniya tushunchasiga tayanadi.

---

## 5. Xavflar va printsiplar

1. **Guardrails ssenariy bilan yumshab ketmasin.** Umumiy taqiqlar (tahdid qilmaslik,
   faktlarni o'zgartirmaslik, disclosure, do-not-call, operator huquqi — PROJECT.md §11)
   ssenariydan **tashqarida**, kod darajasida qoladi; ssenariy ularni o'chira olmaydi.
   Bu custom ssenariylar (A.4) uchun ayniqsa muhim — foydalanuvchi yozgan prompt
   platformaning huquqiy majburiyatlarini chetlab o'ta olmasligi kerak.
2. **Prompt kesh printsipi buzilmasin.** ScenarioDefinition qanday bo'lmasin,
   stablePrefix bayt-darajada barqaror qolishi shart (RUN.md dagi
   `voice_llm_tokens_cached_total` nazorati).
3. **Lead-qualification alohida huquqiy rejim.** Qarzdorga qo'ng'iroq — shartnoma
   asosida; sovuq qo'ng'iroq (cold call) — reklama, rozilik talablari boshqacha
   bo'lishi mumkin. O'zbekiston reklama/aloqa qonunchiligini tekshirish kerak.
4. **Uysot API — tashqi bog'liqlik.** D bosqichini boshlashdan oldin Uysot'dan OAuth
   va API hujjatlarini olish; bo'lmasa D ni E dagi lokal login bilan almashtirib turish.
5. **Har bosqich orqaga qaytmas migratsiyasiz.** A dagi sxema o'zgarishlari (enum →
   String, context → JSONB) va B dagi `company_id` qo'shilishi Flyway bilan, eski
   ma'lumotlar `debt-collection` ssenariysiga va "default" kompaniyaga map qilinadi —
   hisobotlar tarixi yo'qolmaydi.
6. **O'zbekcha TTS/STT sifati** — yangi ssenariylarda ham eng katta UX xavfi bo'lib
   qoladi; har yangi ssenariy shabloni real qo'ng'iroqda tinglab tasdiqlanadi.
7. **Custom ssenariy = custom xarajat va sifat xavfi.** Foydalanuvchi yozgan ssenariy
   cheksiz aylanib qolishi yoki token yeb qo'yishi mumkin — mavjud qattiq chegaralar
   (max-turns, max-duration, `DIALOG_MAX_TOKENS_PER_CALL`) ssenariydan mustaqil,
   platforma darajasida qoladi va custom ssenariy ularni oshira olmaydi (faqat
   kamaytira oladi).
8. **Izolyatsiya — bir martalik ish emas.** Har yangi jadval/endpoint `company_id`
   bilan tug'iladi; B.2 dagi regressiya testlari CI da doimiy ishlaydi, aks holda
   keyingi feature'lar izolyatsiyani sekin buzadi.

---

## 6. Keyingi konkret qadamlar

1. [x] A.1 sxemasini detallashtirish: `ScenarioDefinition` JSON formati + Flyway migratsiya loyihasi
2. [x] Hozirgi qarzdorlik xatti-harakatini `debt-collection.json` ga ko'chirish (xatti-harakat o'zgarmasligi sharti bilan) — 2026-08-01: shu bilan birga `DialogEngine`ga to'liq ulandi (A.3), shunchaki faylga ko'chirish emas
3. [ ] `lead-qualification` shablonini yozish va test kampaniyada sinash — shablon yozilgan va generic engine uni ishga tushira oladi (2026-08-01), lekin real qo'ng'iroq bilan hali sinalmagan
4. [x] `scenario` jadvali + CRUD API + validatsiya (A.4 ning birinchi qismi — vizual tahrirlagichsiz)
5. [x] `company` jadvali va `company_id` migratsiya rejasini chizish (qaysi jadvalga qaysi tartibda) — jadval ustunlari joyida; B.2 dagi to'liq izolyatsiya regressiya testlari va real per-request `CurrentCompany` (hozir bitta hardcoded default) hali qolgan
6. [ ] Uysot'dan OAuth/API hujjatlarini so'rash (D bosqichi bloklanmasligi uchun hoziroq) — OAuth kod oqimi (D.2) allaqachon yozilgan va kompaniya darajasida ishlaydi, faqat haqiqiy `authorizeUrl`/`tokenUrl` kutilmoqda
7. [x] Inbound uchun `inbound_route` jadvali va StasisStart routing (Bosqich C.1-C.3 — 2026-08-01 bajarildi)
8. [ ] Operator navbati (C.4) — Asterisk queue, transfer konteksti, ish vaqti tekshiruvi (profil ish jadvali, §15, allaqachon saqlanadi — bu navbat mantig'i uni hali o'qimaydi)
