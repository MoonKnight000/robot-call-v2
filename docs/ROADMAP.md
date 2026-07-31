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
| Dialog     | ✅ FSM (qarzdorlik skripti), guardrails, NoInputWatchdog, CallSummary                                                                      |
| Dialer     | ✅ Kampaniya CRUD, CSV import, retry, dial window/days, daily cap, do-not-call                                                             |
| Natijalar  | ✅ Transkript, yozuv (MinIO/disk), hisobotlar, audit, retention                                                                            |
| Xavfsizlik | ✅ API kalit (full/read-only), audit log                                                                                                   |
| CRM        | ⚠️ Oddiy REST client (Bearer token) — Uysot OAuth **yo'q**                                                                                 |
| Inbound    | ⚠️ Texnik ishlaydi (softphone 600 → AI javob beradi), lekin **inbound ssenariy tushunchasi yo'q** — o'sha qarzdorlik dialogi ishga tushadi |

### Asosiy cheklov: qarzdorlik kodga "qotirilgan"

Suhbat mantig'i to'liq qarzdorlikka bog'langan — boshqa suhbat turini qo'shish uchun
shu sinflarni o'zgartirish kerak bo'ladi:

- `DialogState` — GREETING → IDENTITY_CHECK → DEBT_NOTICE → ... (qattiq enum)
- `CallContext` — clientName, debtAmount, dueDate, contractNumber (qarz maydonlari)
- `DialogTools` — recordPaymentPromise, recordRefusalReason (qarz tool'lari)
- `SystemPromptFactory` — "Siz Uysot kompaniyasining qarz undirish agentisiz..."
- `CallSummary` / `ReasonCode` / `call_result` jadvali — qarz natijalari

Bu **texnik qarz emas** — MVP uchun to'g'ri qaror edi. Endi keyingi bosqich: shu
skeletni konfiguratsiyaga chiqarish.

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

**A.1 — ScenarioDefinition modeli**

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

**A.2 — Tayyor shablonlar (seed)**

| Shablon                | Maqsad                                                      | Asosiy natija               |
|------------------------|-------------------------------------------------------------|-----------------------------|
| `debt-collection`      | hozirgi ssenariy, aynan shu xatti-harakat bilan             | promisedDate, reasonCode    |
| `lead-qualification`   | potensial mijoz: qiziqish, budjet, muddat, uchrashuv        | interest, budget, meetingAt |
| `notification`         | bir tomonlama xabar + tasdiqlash (to'lov eslatmasi, TDS...) | acknowledged                |
| `survey`               | 3–5 savollik so'rovnoma / NPS                               | answers[], score            |
| `appointment-reminder` | uchrashuvni eslatish/tasdiqlash/ko'chirish                  | confirmed, newTime          |

**A.3 — Kampaniya ssenariyga bog'lanadi**

- `campaign.type` → `scenario_id`; kampaniya yaratishda ssenariy tanlanadi.
- Panel: ssenariy tanlash + faktlar sxemasiga mos CSV ustunlari ko'rsatiladi.
- Migratsiya: mavjud kampaniyalar `debt-collection` ga bog'lanadi. Xatti-harakat
  o'zgarmasligi regressiya testi bilan tekshiriladi (bir xil prompt chiqishi).

**Tekshiruv:** `lead-qualification` ssenariyli kampaniya ochiladi, bot qarz haqida
emas, mahsulotga qiziqish haqida suhbat quradi, natija outcome JSONB da.

**A.4 — Custom ssenariylar (foydalanuvchi o'zi yaratadi)**

Tayyor shablonlar bilan cheklanmaslik — foydalanuvchi o'z ssenariysini yarata olishi kerak:

- Ssenariylar DB da saqlanadi (`scenario` jadvali, ta'rif JSONB). Built-in shablonlar
  **read-only** seed sifatida yuklanadi; foydalanuvchi ularni **nusxalab** (clone)
  o'zgartiradi yoki noldan yaratadi.
- CRUD API: `GET/POST/PUT /api/scenarios`, clone endpoint. Panel: avval JSON tahrirlash
  + validatsiya, keyin (E.4 da) vizual tahrirlagich.
- **Validatsiya majburiy:** har bosqichdan yakuniy holatga yo'l borligi (deadlock yo'q),
  tool'lar outcome sxemasiga mosligi, fakt nomlari to'qnashmasligi — xato ssenariy
  saqlanmaydi, aktivlashtirilmaydi.
- **Versiyalash:** kampaniya ssenariyning muayyan **versiyasiga** bog'lanadi — aktiv
  kampaniya o'rtasida ssenariy tahrirlansa, ketayotgan qo'ng'iroqlar eski versiyada
  davom etadi; hisobotda qaysi versiya ishlagani ko'rinadi.
- **Guardrails tegilmaydi:** umumiy taqiqlar (§5.1) va disclosure kod darajasida —
  custom ssenariy ularni o'chira yoki yumshata olmaydi. Ssenariy faqat *qo'shimcha*
  qoida kirita oladi.
- Sinov rejimi: ssenariyni kampaniyasiz bitta test raqamga qo'ng'iroq bilan sinash
  (hozirgi `POST /api/calls` ga `scenarioId` parametri).

**Tekshiruv:** panel orqali yangi ssenariy yaratiladi (masalan, "yetkazib berishni
tasdiqlash"), test qo'ng'iroqda ishlaydi, noto'g'ri ta'rif 400 bilan rad etiladi.

---

### Bosqich B — Kompaniya (tashkilot) va ma'lumot izolyatsiyasi

Platforma bir nechta kompaniyaga xizmat qiladi — Uysot rejimida ham, mustaqil rejimda
ham. Bitta kompaniyaning kampaniyalari, nishonlari, transkriptlari, yozuvlari va
ssenariylari boshqasinikidan **qat'iy ajratilgan** bo'lishi shart. Bu bosqich ataylab
oldinga qo'yilgan: jadvallar va funksiyalar ko'payganidan keyin `company_id` qo'shish
ancha qimmatga tushadi.

**B.1 — Ma'lumot modeli**

- `company` jadvali: nom, status, sozlamalar (default til, timezone, caller ID,
  dial-window defaultlari).
- Barcha ma'lumot jadvallariga `company_id`: `campaign`, `campaign_target`,
  `call_attempt`, `call_transcript`, `call_result`, `scenario`, `do_not_call`,
  `audit_log`, keyinchalik `inbound_route` va `usage_record`.
- Flyway migratsiya: mavjud ma'lumotlar avtomatik "default" kompaniyaga bog'lanadi —
  tarix yo'qolmaydi.

**B.2 — Izolyatsiyani majburlash**

- Repository darajasida **majburiy** `company_id` filtri — filtrsiz so'rov arxitektura
  jihatdan mumkin bo'lmasin (umumiy company-scoped repository bazasi). Qo'shimcha
  qatlam sifatida Postgres RLS baholanadi.
- API kalitlari kompaniyaga bog'lanadi: `kalit → company_id`; hisobotlar, kampaniyalar,
  yozuvlar, audit — faqat o'z kompaniyasiniki. MinIO/diskdagi yozuvlar ham kompaniya
  prefiksi bilan saqlanadi.
- Ssenariylar: built-in shablonlar global (read-only), custom ssenariylar (A.4)
  `company_id` bilan — bir kompaniya boshqasining ssenariyini ko'rmaydi ham,
  ishlata olmaydi ham.
- Do-not-call ro'yxati kompaniya kesimida (mijoz bitta kompaniyaga taqiq qo'ygani
  boshqasiga tegmaydi).
- **Izolyatsiya regressiya testlari:** A kompaniya kaliti bilan B kompaniya resursiga
  har turdagi so'rov → 403/404. Bu test to'plami doimiy CI da qoladi.

**B.3 — Kompaniyaga xos telefoniya (minimal)**

- Har kompaniyaga caller ID / DID raqam biriktiriladi; kunlik cap va parallel limitlar
  kompaniya darajasida ham. To'liq "har kompaniyaga o'z trunki" — E bosqichida.

**Tekshiruv:** ikkita kompaniya yaratiladi, har birida kampaniya ishga tushadi; hech
bir API chaqiruv (hisobot, transkript, yozuv, ssenariy) boshqa kompaniya ma'lumotini
qaytarmaydi.

---

### Bosqich C — Inbound qo'ng'iroqlar

Texnik poydevor bor (Stasis + externalMedia inbound'da ham ishlaydi). Yetishmayotgani —
"qaysi raqamga kim qo'ng'iroq qildi va qaysi ssenariy ishlasin" mantig'i.

**C.1 — Routing**

- `inbound_route` jadvali: `did_number → company_id, scenario_id, language,
  business_hours, fallback` (ish vaqtidan tashqari nima bo'ladi: xabar / voicemail /
  operator).
- `AriService` StasisStart'da yo'nalishni aniqlaydi (hozirgi "auto-start" flag o'rniga).

**C.2 — Qo'ng'iroq qiluvchini aniqlash**

- Caller ID → CRM lookup (`CrmConnector.findByPhone`): tanish mijoz bo'lsa faktlar
  promptga tushadi ("Assalomu alaykum, Aziz aka!"), notanish bo'lsa lead-capture oqimi.
- Do-not-call ro'yxati inbound'da teskari ishlaydi: mijoz o'zi qo'ng'iroq qilsa gaplashish mumkin.

**C.3 — Inbound ssenariy shablonlari**

- `reception` — qabulxona: savolga javob (FAQ faktlar bazasidan), kerak bo'lsa operatorga.
- `inbound-lead` — reklamadan kelgan qo'ng'iriq: ma'lumot berish + kontakt olish + uchrashuvga yozish.
- `callback-request` — band bo'lsa: raqam olib, outbound navbatga qo'yish (dialer bilan bog'lanadi).

**C.4 — Operator navbati**

PROJECT.md Bosqich 11 (hali qilinmagan) inbound bilan birga zarur bo'ladi:
Asterisk queue, transfer'da kontekstni ko'rsatish, ish vaqti tekshiruvi.

**Tekshiruv:** tashqi raqamdan trunk raqamiga qo'ng'iroq → route bo'yicha ssenariy
ishlaydi, tanish mijozni ismi bilan kutib oladi, natija hisobotda ko'rinadi.

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
    List<TargetRow> fetchSegment(SegmentQuery query);     // kampaniya nishonlari
    Long postOutcome(String externalId, CallOutcome outcome); // natija/note
}
```

Implementatsiyalar: `UysotConnector`, `GenericRestConnector` (hozirgi CrmClient),
`NoopConnector` (faqat CSV rejimi). Tanlov konfiguratsiyada: `crm.provider=uysot|rest|none`.

**D.2 — Uysot OAuth2**

- `spring-security-oauth2-client`: authorization code flow — foydalanuvchi panelda
  "Uysot bilan kirish" tugmasini bosadi, token server tomonda saqlanadi/yangilanadi.
- Server-to-server chaqiruvlar (dialer, outbox) uchun refresh token yoki client
  credentials — Uysot API qaysi birini berishiga qarab.
- Token yangilash, muddati o'tganda qayta login talab qilish, xatolarda graceful
  degradatsiya (hozirgi "CRM yiqilsa qo'ng'iroq baribir ketadi" printsipi saqlanadi).

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

**E.1 — Foydalanuvchi modeli**

- `app_user` + rollar (ADMIN, OPERATOR, VIEWER), har foydalanuvchi bitta (yoki bir
  nechta) kompaniyaga a'zo.
- Login: Uysot OAuth **yoki** lokal email/parol (standalone mijozlar uchun).
- Hozirgi API-kalit rejimi saqlanadi (machine-to-machine, B.2 dagi kompaniya bog'i bilan).

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

| # | Bosqich                          | Taxminiy hajm                | Natija                                                  |
|---|----------------------------------|------------------------------|---------------------------------------------------------|
| 1 | **A** — Scenario Engine + custom | katta (yadro refaktoring)    | 5 shablon + custom ssenariy CRUD, versiyalash           |
| 2 | **B** — Kompaniya va izolyatsiya | o'rta (migratsiya og'ir)     | `company` entity, to'liq ma'lumot ajratish, kalit bog'i |
| 3 | **C** — Inbound                  | o'rta                        | DID routing, mijozni tanish, 3 inbound shablon          |
| 4 | **D** — Uysot OAuth              | o'rta (Uysot API ga bog'liq) | "Uysot bilan kirish", segment import, natija eksport    |
| 5 | **E** — Standalone/SaaS          | katta                        | foydalanuvchi/rollar, billing o'lchovi, yangi panel     |
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

1. [ ] A.1 sxemasini detallashtirish: `ScenarioDefinition` JSON formati + Flyway migratsiya loyihasi
2. [ ] Hozirgi qarzdorlik xatti-harakatini `debt-collection.json` ga ko'chirish (xatti-harakat o'zgarmasligi sharti bilan)
3. [ ] `lead-qualification` shablonini yozish va test kampaniyada sinash
4. [ ] `scenario` jadvali + CRUD API + validatsiya (A.4 ning birinchi qismi — vizual tahrirlagichsiz)
5. [ ] `company` jadvali va `company_id` migratsiya rejasini chizish (qaysi jadvalga qaysi tartibda)
6. [ ] Uysot'dan OAuth/API hujjatlarini so'rash (D bosqichi bloklanmasligi uchun hoziroq)
7. [ ] Inbound uchun `inbound_route` jadvali va StasisStart routing eskizi
