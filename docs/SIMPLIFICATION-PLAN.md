# Soddalashtirish rejasi — Agent · Campaign · Scenario

Sana: 2026-10-28 · Holat: reja (kod o'zgarmagan)

Sabab: demo paytida direktor sozlamalar (AI agent, kampaniya, ssenariy) haddan tashqari
murakkab ekanini aytdi. Maqsad — yangi foydalanuvchi hujjatsiz, 5 daqiqada agent yaratib,
bir nechta raqamga qo'ng'iroq qila oladigan holatga kelish.

---

## 1. Muammo (koddan o'lchangan)

| Narsa | Hozir |
|---|---|
| `AiAgent` | ~32 ta maydon + 7 ta ichki guruh (`script`, `speechEngine`, `voice`, `ambience`, `callBehaviour`, `limits`, `dataPolicy`) — jami taxminan 70+ sozlama |
| `Campaign` | 30 ta maydon: cron, takrorlanish, `dialMode`, `maxAiOverflowPercent`, `targetOrdering`, ikki xil budjet, `priority` |
| Ssenariy manbai | Uch xil: `script.scenarioId`, `script.definition`, `systemPrompt` (`AiAgentService.resolveScenario`) |
| Retell | Ikki joyda: `maxAttempts` + `retryIntervalMinutes` va alohida `RetryPolicy` — dublikat bo'lishi mumkin, **tekshirilmagan** |
| Hujjat | `docs/api/ai-agents.md` 1129 qator, `campaigns.md` 894 qator |

Backend'da default'lar compact konstruktorlarda bor, draft/published ham mavjud. Murakkablik
foydalanuvchiga hammasi bir xil darajada ko'rinishidan kelib chiqyapti. Shuning uchun
hech narsa o'chirilmaydi — **soddalashtirilgan qatlam** qo'shiladi.

## 2. Raqobatchilardan olingan g'oyalar

| Platforma | Nimasi yaxshi | Bizga |
|---|---|---|
| Bland | Agent uchun faqat nom va "job" kerak; "Talk in browser" / "Call me"; production'ga o'tkazish alohida qadam | Agent yaratish shunday bo'lsin |
| Retell | *Single Prompt* (tavsiya) va *Conversation Flow* (faqat tuzilma kerak bo'lganda); Batch: agent + raqam + CSV + hozir/jadval | Ssenariy "murakkab rejim" bo'lsin |
| Vapi | Shablon + *model preset* (STT + LLM + TTS bitta tanlov); Squads faqat murakkab holat uchun | "Ovoz profili" preset |
| Dasha | Kod yozuvchilar uchun, no-code emas | Mos emas |

Skit AI bo'yicha ma'lumot topilmadi; Bland batch bo'limi ochilmadi — taxmin qilinmagan.

Manbalar: [Retell batch](https://www.retellai.com/el/features/batch-call) ·
[Retell intro](https://docs.retellai.com/general/introduction) ·
[Vapi quickstart](https://docs.vapi.ai/quickstart/introduction) ·
[Bland first agent](https://docs.bland.ai/build-your-first-agent.md) ·
[Bland vs Vapi](https://dasha.ai/blog/bland-ai-vs-vapi-ai-alternatives)

## 3. Tamoyil

Foydalanuvchi 3 narsani ko'radi: **Agent**, **Kontaktlar**, **Kampaniya**. Qolgani
"Qo'shimcha" ichida. API buzilmaydi: `messageCode` va enum nomlari o'zgarmaydi.

## 4. Bosqichlar

Har bosqichni foydalanuvchi o'zi tekshiradi (CLAUDE.md §8.6).

### 1-bosqich — Demoga yetarli minimum (asosan frontend)

- **Agent yaratish — 5 maydon:** nom, vazifa (prompt), til, ovoz, birinchi gap. Qolganlari
  default'da, "Qo'shimcha sozlamalar" yig'iladigan bo'limda.
- **Kampaniya wizard — 4 qadam:** Agent → Kontaktlar (CSV) → Jadval (oyna, kunlar,
  urinishlar) → Ishga tushirish.
- **Sinov:** "Brauzerda gaplash" va "Menga qo'ng'iroq qil" (`web-test.html`, widget'lar bor).
- **Qo'shimchaga yashiriladi:** cron, takrorlanish, `dialMode`, `callQueueId`, overflow,
  ordering, priority, A/B variantlar, ambience, pronunciation, data policy, IVR, RAG.
- **Tekshiruv:** yangi odam hujjatsiz 5 daqiqada agent yaratib, 3 raqamga qo'ng'iroq qila oladi.
- **Topilma (2026-10-28):** backend 1-bosqichga tayyor — yangi endpoint kerak emas.
  `CreateAiAgentRequest` da faqat `name` majburiy; kampaniyada `name`, `type`, `aiAgentId`
  majburiy, qolganiga default bor; `POST /api/calls/web-test` `{aiAgentId}` bilan kampaniyasiz
  ishlaydi; `GET /api/ai-agents/templates` allaqachon bor (2-bosqichning bir qismi tayyor).
  Shartnoma: [api/quickstart.md](api/quickstart.md). Qolgan ish — frontend.

### 2-bosqich — Shablonlar

- 3-4 shablon: qarz eslatma, lid sifatlash, so'rovnoma, kiruvchi qabul. Har biri
  agent + ssenariy + ovoz bilan keladi (built-in ssenariy shablonlari allaqachon bor).
- Backend: `GET /api/templates`, `POST /api/ai-agents/from-template`.
- `docs/api/` ga yangi fayl + `README.md` jadvaliga qator (§7).
- **Tekshiruv:** shablon tanlab, bitta bosishda ishlaydigan agent hosil bo'ladi.
- **Bajarildi (2026-10-28):** `from-template` endpointi kerak emas bo'ldi — `POST /api/ai-agents`
  allaqachon `templateId` qabul qiladi. Mavjud shablonlar (BUSINESS/SUPPORT/MEDICAL) kiruvchi
  qo'ng'iroqqa mo'ljallangan edi; chiquvchi 3 ta qo'shildi: `DEBT_REMINDER`, `LEAD_QUALIFICATION`,
  `SURVEY` (uz/ru/en). Shablon ssenariy bog'lamaydi — faqat prompt, birinchi gap va ma'lumot
  maydonlari (PROMPT rejimi).

### 3-bosqich — "Ovoz profili" preset

- Bitta tanlov STT, TTS, `llmModel`, `fastLlmModel`, `temperature`, `maxOutputTokens` ni
  to'ldiradi (masalan: "Tez va arzon", "Sifatli", "O'zbekcha eng yaxshi").
- Eski maydonlar "Ekspert" rejimida qoladi.
- **Tekshiruv:** preset tanlanganda agentning barcha tegishli maydonlari to'ladi, qo'lda
  o'zgartirish ishlaydi.
- **Bajarildi (2026-10-28):** `GET /api/ai-agents/engine-profiles` va `POST /api/ai-agents` da
  `engineProfileId`. Profillar `config/misc.yml` (`voice-agent.engine-profiles.items`) da,
  ro'yxat faqat shu build to'liq ishga tushira oladiganlarini qaytaradi (`AiAgentEngineGuard`
  bilan tekshiriladi). **TTS profilga kirmaydi:** sintezatorni ovozning o'zi belgilaydi, shuning
  uchun profil STT, LLM, `fastLlmModel`, `temperature`, `maxOutputTokens` ni to'ldiradi.
  Hozirgi 5 ta profil (Gemini va OpenAI variantlari) taxminiy — qaysi STT/LLM o'zbekchada
  eng yaxshi ekanini siz tasdiqlaysiz. `PUT` da profil yo'q.

### 4-bosqich — Ssenariyni soddalashtirish

- UI'da ikki variant: **Oddiy** (faqat prompt, `ScenarioMode.PROMPT`) va **Bosqichli**
  (hozirgi Scenario).
- `scenarioId` / `definition` farqi UI'dan olib tashlanadi: "ssenariy tanlang yoki yangisini
  yarating".
- `CampaignVariant` (A/B) alohida "Tajriba" bo'limiga ko'chadi.
- **Tekshiruv:** Oddiy rejimda foydalanuvchi "bosqich", "transition", "outcome" so'zlarini
  ko'rmaydi.
- **Bajarildi (2026-10-28), backend qismi:** kodda to'rt tuzoq topildi va ikkitasi tuzatildi.
  (1) `scenarioMode` qo'ng'iroqda o'qilmaydigan yorliq — UI rejimni undan emas, `quickstart.md`
  dagi qoidadan aniqlaydi (kod o'zgarmadi). (2) `scenarioId` bor agentda `PUT /scenario` jimgina
  ta'sirsiz edi — endi `409 AI_AGENT_SCENARIO_SHARED`. (3) Shu holatda `GET /scenario` bo'sh
  qaytadi — o'zgarmadi, hujjatlashtirildi. (4) `PUT /scenario` `tools` va `outcomeSchema` ni
  (pack agentlarida natijalarni) o'chirib yuborardi — endi mavjud `definition` saqlanadi.
  "Oddiy / Bosqichli" tanlovi va "Tajriba" bo'limi — frontend ishi.

### 5-bosqich — Tozalash (xavfli, oxirida)

- Ikki retry mexanizmini birlashtirish — avval qaysi biri real ishlatilishini tekshirish.
- Ishlatilmayotgan maydonlarni deprecate qilish (o'chirish emas).
- `Campaign` dagi eski 19 parametrli konstruktor (faqat integration testlar uchun) ko'rib chiqiladi.
- **Tekshiruv:** mavjud testlar va `docs/api/` kod bilan mos.
- **Bajarildi (2026-10-28):** tekshiruv shuni ko'rsatdi: (1) retry dublikat **emas** — uch
  pog'onali zanjir (`RetryPolicy` qoidasi → `retryIntervalMinutes` → deployment defaultlari,
  `RetrySchedule.delayFor`), birlashtirilmaydi. (2) Campaign'ning barcha maydonlari runtime'da
  o'qiladi. (3) Eski 19 parametrli `Campaign` konstruktori faqat `CampaignTargetRepositoryTest`
  va `CompanyIsolationTest` da — integration testlar, §8.8 bo'yicha tegilmadi; ularni `builder()`
  ga o'tkazgach konstruktor o'chiriladi. (4) Agentda 6 ta sozlama qo'ng'iroqqa ta'sir qilmaydi
  (`preemptiveGeneration`, `ivrNavigationEnabled`, `voiceStability`, `voiceSimilarityBoost`,
  `endpointingDelayMs`, `turnTimeoutSeconds`) — avval faqat hujjatda belgilangan edi;
  `PROJECT-AUDIT.md` 1-bosqichi bo'yicha API, domain va bazadan to'liq olib tashlandi
  (`V23__drop_ai_agent_phantom_settings.sql`).

### 6-bosqich — Bosqichlar va kunlik sozlamalar

Sabab (2026-10-28): foydalanuvchi qo'ng'iroq bosqichlari va kampaniyaning kunlik sozlamalarini
eng murakkab joy deb ko'rsatdi. Dizayn: "Nido Design System" (hozircha bo'sh — eksport kutilmoqda).

- **6a. Bosqich o'tishlari bitta ro'yxat — bajarildi (2026-10-28).** `transitionConditions`
  kalitlari `StageDef` compact konstruktorida `allowedTransitions` ga qo'shiladi; validatordagi
  "not in its allowedTransitions" xatosi endi kerak emas (noma'lum bosqich baribir
  `transitions to unknown stage` bilan ushlanadi). API buzilmadi.
  UI bosqich kartochkasi: *Nima qiladi* (`purpose`), *Aniq gap* (`staticLine`, ixtiyoriy),
  *Keyingi qadam → bosqich, qachon* (`transitionConditions`). `allowedTools`, `emotion`, `model`,
  `branches`, `requiredOutcomes` — "Ekspert".
  **Tekshiruv:** `ScenarioValidatorTest` (`aTransitionConditionAloneMakesItsTargetAllowed`,
  `aTransitionConditionOnAnUnknownStageReturnsError`).
- **6b. Kampaniya "Jadval" qadami — keyingi.** UI 4 maydon: ish vaqti, kunlar, urinishlar
  (soni + oraliq), kunlik limit. Takrorlanish UI'da ikki tanlov: "Bir marta" / "Har kuni yangi
  ro'yxat bilan" (`DAILY` + target source). Qolgani "Qo'shimcha".
- **6c. Oyna berilmasa — kompaniya ish soatlari — bajarildi (2026-10-28).** Tekshiruv: chiquvchi
  qo'ng'iroq uchun kompaniya oynasi `company_config.dialWindowStart/End` da allaqachon bor va
  `DialWindow.isOpen` uni kampaniya oynasi bilan birga tekshiradi (bayramlar ham). `BusinessSchedule`
  (`schedule` feature) faqat kiruvchi qo'ng'iroqlar uchun (`InboundCallGate`, `CallFlowRunner`) —
  bu boshqa narsa, tegilmadi. O'zgarish: `POST /api/campaigns` da oyna berilmasa kompaniya oynasi
  nusxalanadi (09:00–20:00 emas); `PUT` da berilmasa mavjud oyna qoladi (avval `null` yozilardi,
  ustun esa `NOT NULL`). Nusxa — kompaniya keyin oynani kengaytirsa, eski kampaniya o'zgarmaydi
  (toraytirsa, `DialWindow` baribir kompaniya oynasini ham tekshiradi). Migratsiya kerak bo'lmadi.
  **Tekshiruv:** `CampaignServiceTest` (`campaignWithoutWindowTakesTheCompanysDialWindow`,
  `campaignWithoutWindowOrCompanySettingsCallsNineToEight`, `updateCampaignWithoutWindowKeepsItsOwn`).

- **6d. Agent tahririda "Ovoz profili" — bajarildi (2026-10-28).** 3-bosqichdagi profil faqat
  yaratishda edi, tahrirlash formasi esa yana 6 ta texnik maydonni ko'rsatishga majbur edi.
  Endi `PUT /api/ai-agents/{id}` `engineProfileId` qabul qiladi (profil olti maydonni almashtiradi,
  so'rovdagi aniq maydon ustun), `AiAgentRow.engineProfileId` esa agentga mos profilni qaytaradi
  (`null` = "Maxsus"; saqlanmaydi, `EngineProfile.isAppliedTo` bilan hisoblanadi).
  **Tekshiruv:** `AiAgentServiceTest.findAgentRowNamesTheEngineProfileTheAgentMatches`.
  `PUT` + profil yo'li unit testda yo'q — `AiAgentEngineGuard` mock'lar bilan modelni rad etadi;
  `ai-agents.http` dan qo'lda sinash kerak.

## 5. Ochiq savollar

1. Direktor aynan nimadan shikoyat qildi — sozlamalar sonimi, atamalar (Scenario vs Agent),
   yoki jarayon uzunligimi? 1-bosqich shunga qarab o'zgaradi.
2. Foydalanuvchi kim — kompaniya admini (texnik emas) yoki ichki jamoa? "Ekspert rejim"
   hajmi shunga bog'liq.
3. Frontend qaysi repoda? 1-bosqich asosan o'sha yerda; bu repoda 2-3-bosqich API'lari.
