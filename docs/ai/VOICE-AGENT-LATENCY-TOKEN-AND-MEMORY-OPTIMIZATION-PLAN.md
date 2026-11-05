# Voice AI Agent: Kechikishni Pasaytirish (Latency), Tokenlarni Tejash va To'liq Xotirani Saqlash Rejasi

> **Hujjat maqsadi:** Ushbu hujjat ovozli telefon agentlarida (AI Voice Agents) eng muhim 3 ta ziddiyatli talabni — **ultra-past kechikish (sub-500ms voice latency)**, **token tejamkorligi (70-80% tejash)** va **qo'ng'iroqning to'liq kontekstini 100% yodda saqlash (zero amnesia)** — bir vaqtda qanoatlantirish bo'yicha to'liq amaliy va arxitektura rejasini taqdim etadi.
> 
> Hujjat rasmiy hujjatlar (Google Gemini Context Caching, OpenAI Prompt Caching, LiveKit, Cartesia, Deepgram) hamda dasturchilar hamjamiyati (Reddit `r/LocalLLaMA`, `r/VoiceAutomationAI`, Hacker News) tajribalarini o'rganish asosida tuzilgan va `robot-call-v2` loyihasi arxitekturasiga moslashtirilgan.

---

## 1. Asosiy Muammo va Arxitektura Paradoksi

Ovozli telefon muloqotida insonlararo tabiiy pauza **200–400ms** atrofida bo'ladi. Agar ovozli bot 800–1000ms dan ortiq o'ylanib tursa, mijoz *"Alo? Eshitmayapsizmi?"* deb yuboradi va suhbat tabiiyligi yo'qoladi.

Biroq an'anaviy LLM chat arxitekturasi 3 ta to'siqqa duch keladi:

```
                  ┌──────────────────────────────────────────────┐
                  │             ARXITEKTURA PARADOKSI            │
                  └──────────────────────┬───────────────────────┘
                                         │
         ┌───────────────────────────────┼──────────────────────────────┐
         ▼                               ▼                              ▼
  1. TO'LIQ XOTIRA                2. TEZKORLIK                   3. TEJAMKORLIK
Har bir turn'da butun chat      Prompt qisqa bo'lishi va      Faqat kerakli tokenlar
tarixi yuboriladi.              model tez javob berishi       jo'natilishi va olinishi
Natija: 15-turnda 8,000+        kerak (TTFT < 150ms).         kerak (kirish/chiqish
token, ulkan prefill vaqti      Natija: Xom tarix kesilsa,    arzon bo'lishi shart).
va sekin javob.                 bot muhim faktlarni unutadi!
```

### Biz erishishimiz kerak bo'lgan maqsadli metrikalar (KPI):
* **TTFT (Time-to-First-Token):** $\le 120\text{ – }180\text{ ms}$.
* **TTFA (Time-to-First-Audio / Mijoz ovoz eshitish vaqti):** $\le 400\text{ – }500\text{ ms}$.
* **Token tejami:** Oddiy yondashuvga nisbatan **$70\% \text{ – } 80\%$**.
* **Faktik xotira saqlanishi (Recall / Retention):** 25-turnda ham **$100\%$**.

---

## 2. Rasmiy Hujjatlar va Hamjamiyat (Reddit / Hacker News) Tahlili

### 2.1. Google Gemini & OpenAI: Prompt Caching (KV-Cache) Injiqliklari
Ko'pchilik dasturchilar token tejash maqsadida keshdan foydalanishga urinadi, lekin amalda kesh 0% bo'lib qoladi. Buning sabablari:
1. **Prefix Mutlaqo O'zgarmasligi Shart:**
   * Gemini (implicit caching $\ge 2048$ yoki $4096$ token) va OpenAI (prompt caching $\ge 1024$ token) bir xil qoidaga tayanadi: promptning eng boshidan boshlab har bir bayt o'tgan safargi so'rov bilan 100% mos tushishi kerak.
   * Agar siz System Prompt ichiga dinamik o'zgaruvchi (masalan: `Hozirgi vaqt: 09:25:31` yoki joriy FSM bosqich nomi `Stage: IDENTITY_CHECK`) qo'ysangiz — **kesh butunlay buziladi (Cache Miss)**.
2. **Xom Tarixni O'rtadan Kesish — Kesh Qotili:**
   * Ko'p botlar `history.subList(0, 2).clear()` qilib dastlabki 2 ta xabarni o'chiradi. Bu prefixni tubdan o'zgartiradi va butun KV-kesh nollanadi. Model har safar prefillni noldan hisoblaydi, kechikish va narx 2–3 baravar oshadi.
3. **Kesh urilgandagi (Cache Hit) samara:**
   * Kiruvchi tokenlar narxi **50% dan 80% gacha arzonlashadi**.
   * LLM prefill vaqti 70% ga qisqaradi (TTFT 400ms dan 100ms ga tushadi).

### 2.2. Reddit & Hacker News Hamjamiyatining "Dardlari" va Yechimlari
1. **"Chiqish tokenlari — yashirin kechikish manbai":**
   * Ko'pchilik faqat kiruvchi tokenlarni optimallashtiradi. Ammo LLM generatsiyasi ketma-ket (har bir yangi so'z oldingi so'zga qarab yoziladi). 100 ta so'z yozish uchun model 1.5–2 soniya sarflaydi.
   * **Yechim:** `max_tokens = 60–80` qat'iy cheklovi va promptda 1–2 gapdan ortiq gapirishni qat'iy taqiqlash.
2. **"To'liq matn kutish — o'lim":**
   * Agar bot LLM to'liq nuqta qo'yishini kutsa, keyin uni TTS ga bersa, foydalanuvchi 2.5–3.5 soniya jimlikka chidashi kerak bo'ladi.
   * **Yechim:** Stateful Regex Sentence Chunker — birinchi 4–6 ta so'z yoki tinish belgisi (`.`, `?`, `!`, `,`) kelgan zahoti uni TTS oqimiga (streaming) uzatish.
3. **"Faktlarni matnda emas, slotlarda saqlang":**
   * Mijoz aytgan telefon raqami, to'lov sanasi, xotinining ismi yoki rad etish sababi — bularni 10 varaq dialog matni ichida qayta-qayta yuborish shart emas. Bularni bitta ixcham JSON obyektida (Dialog State Ledger) saqlash lozim.

---

## 3. 5 Pog'onali Strategik Arxitektura

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                    USTUN 1: IERARXIK XOTIRA ARXITEKTURASI                     │
│   [Slotlar (Facts)]  +  [Asinxron Rolling Summary]  +  [Oxirgi 3-4 Turn Raw] │
└──────────────────────────────────────┬───────────────────────────────────────┘
                                       │
┌──────────────────────────────────────▼───────────────────────────────────────┐
│                 USTUN 2: STRICT PROMPT CACHING (APPEND-ONLY)                 │
│   [Stable System Prefix (100% Cached)] ──► [History] ──► [Turn Annex (Oxirida)]│
└──────────────────────────────────────┬───────────────────────────────────────┘
                                       │
┌──────────────────────────────────────▼───────────────────────────────────────┐
│               USTUN 3: CHIQISH TOKENLARINI QAT'IY CHEKLASH                   │
│   max_tokens: 60 - 80  |  1-2 lo'nda gap  |  Hech qanday markdown/ro'yxatsiz │
└──────────────────────────────────────┬───────────────────────────────────────┘
                                       │
┌──────────────────────────────────────▼───────────────────────────────────────┐
│            USTUN 4: STREAMING SENTENCE CHUNKER & TTS PIPELINING              │
│   LLM birinchi gapni chiqardi (80ms) ──► TTS o'qimoqda ──► LLM 2-gapni yozmoqda│
└──────────────────────────────────────┬───────────────────────────────────────┘
                                       │
┌──────────────────────────────────────▼───────────────────────────────────────┐
│             USTUN 5: FAST-PATH SHORTCUTS & SPECULATIVE TURN                  │
│   "Alo?", "Rahmat" -> 0ms LLM  |  STT Interim orqali oldindan bashorat       │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

### USTUN 1: 3 Qatlamli Ierarxik Xotira (Hierarchical Context)

Suhbat tarixini shunchaki matn sifatida to'plamasdan, uni 3 ta darajaga ajratamiz:

#### 1. Qatlam A: Strukturaviy Slotlar / Fact Ledger (30–50 token)
Har bir bosqichda yoki harakatda mijoz bildirgan faktlar ajratib olinadi va xotira jadvalida saqlanadi:
```json
{
  "client_name": "Ali Valiyev",
  "client_state": "kasalxonada",
  "objection": "oylik_kechikishi",
  "agreed_payment_date": "2026-11-10",
  "agreed_amount": 500000
}
```
Bu ma'lumot promptda atigi 3–4 qator joy oladi, lekin suhbatning butun mohiyatini ushlab turadi. Bot hech qachon *"Kechirasiz, ismingiz nima edi?"* yoki *"Qachon to'laysiz?"* deb qayta so'ramaydi.

#### 2. Qatlam B: Asinxron Rolling Summary (Fon rejimida qisqartirish)
Suhbat 6 turndan oshganda, eski replikalar shunchaki o'chirilmaydi. Asosiy ovoz oqimini **to'xtatmagan holda**, orqa fonda (virtual thread orqali arzon model yordamida) xulosa qilinadi:
* **Summarizer Prompti:**
  > *"Quyidagi eski replikalarni 1-2 ta faktik gap bilan xulosa qil. Odob-axloq so'zlarini tashlab yubor, faqat maqsad va kelishuvni qoldir. Hajmi: 30 ta so'zdan oshmasin."*
* **Natija:**
  > `[SUMMARY: Mijoz dastlab qarzdorlikni to'lashdan bosh tortdi, sababi oyligi kechikkan. Agent bo'lib to'lashni taklif qildi, mijoz 10-noyabrga 500 ming to'lashga rozi bo'ldi.]`

#### 3. Qatlam C: Immediate Sliding Window (Oxirgi 3–4 turn raw text)
Faqat eng oxirgi 3–4 ta user va assistant replikalari to'liq matn sifatida beriladi. Bu modelga so'nggi ohangni, intonatsiyani va qisqa havolalarni (*"u qancha bo'ladi?", "ha, o'sha"*) aniq tushunishga xizmat qiladi.

---

### USTUN 2: Strict Prompt Caching (KV-Cache Optimization)

Gemini va OpenAI keshining afzalliklaridan to'liq foydalanish uchun so'rov tuzilishi quyidagicha qat'iy standartga keltiriladi:

```
┌─────────────────────────────────────────────────────────────┐
│ 1. STABLE SYSTEM PREFIX (Doimiy o'zgarmas, 100% CACHED):    │
│    - Platform qoidalari va xavfsizlik chegaralari           │
│    - Agent Personasi va gapirish uslubi                     │
│    - Ssenariyning barcha bosqichlari qoidalari              │
│    - BARCHA mavjud Tool ta'riflari (JSON Schema)            │
│    - Knowledge Base umumiy qoidalari                        │
│    (Hajmi: 1,500 - 3,000 token. Bu qism bir marta yuklanadi │
│     va suhbat oxirigacha 1 bayt ham o'zgarmaydi!)           │
├─────────────────────────────────────────────────────────────┤
│ 2. GROWING CHAT HISTORY (Append-Only):                      │
│    - Rolling Summary (agar mavjud bo'lsa)                   │
│    - Turn 1 .. Turn N (oxirgi 3-4 turn)                     │
├─────────────────────────────────────────────────────────────┤
│ 3. PER-TURN ANNEX (Dinamik o'zgaruvchilar - FAQAT OXIRIDA): │
│    - Joriy sana va vaqt                                     │
│    - Joriy FSM bosqichi va ruxsat etilgan o'tishlar         │
│    - Fact Ledger (yig'ilgan faktlar)                        │
│    - Barge-in (gap bo'lingani) haqida eslatma               │
└─────────────────────────────────────────────────────────────┘
```

> [!IMPORTANT]
> **Nima uchun Dinamik Annex faqat oxirida turadi?**
> LLM kesh mexanizmi boshidan boshlab moslikni qidiradi. Dinamik qism oxirida tursa, oldingi 90% qism keshdan olinadi. Agar dinamik qism o'rtaga qo'yilsa, undan keyingi hamma narsa keshdan tushib qoladi. Qolaversa, LLM'lar prompt oxiridagi ko'rsatmalarga eng yaxshi bo'ysunadi (Recency Bias).

---

### USTUN 3: Chiqish Tokenlarini Qat'iy Cheklash (Output Token Hard-Cap)

Telefon orqali suhbat — bu Telegram chat emas. Odam uzun monologlarni eshitishga sabri yetmaydi.

1. **`max_tokens = 70` (yoki 80):**
   * Model qancha ko'p token generatsiya qilsa, TTFT emas, balki butun javobning tugash vaqti shuncha cho'ziladi.
   * 70 ta token o'zbek/rus tilida taxminan 25–35 ta so'z degani (2 ta to'liq gap). Bu ovozli suhbat uchun ideal hajm.
2. **System Prompt Direktivalari:**
   ```markdown
   [VOICE CONVERSATION CONSTRAINTS]
   - Javobingizni har doim 1 yoki maksimal 2 ta qisqa gap bilan cheklang.
   - Hech qachon markdown (*, _, #), tire, nuqtali ro'yxatlar ishlatmang.
   - Bitta turn'da faqat bitta aniq savol bering yoki bitta fikr bildiring.
   - Raqamlarni matn shaklida ifodalang (masalan: "500 ming" yoki "o'ninchi noyabr").
   ```

---

### USTUN 4: Streaming Sentence Chunker va TTS Pipelining

Ushbu usul inson sezadigan kechikishni (Perceived Latency) **1.5 soniyadan 400ms gacha** qisqartiradi:

```
Vaqt o'qi (ms):
0ms      100ms         200ms         300ms         400ms         500ms         600ms
├─────────┼─────────────┼─────────────┼─────────────┼─────────────┼─────────────┤
[User jim bo'ldi]
          [LLM TTFT]
          [LLM: "Assalomu alaykum, eshityapman."]
                        [TTS Chunk 1 generatsiya]
                                      [Audio 1-gap jaranglamoqda...]
                                                    [LLM 2-gapni generatsiya qilmoqda...]
```

* **Sentence Chunker qoidasi:**
  * LLM oqimidan (tokens) har bir so'z qabul qilinadi.
  * Matn buferiga yig'iladi. Agar buferda `.`, `?`, `!` yoki so'zlar soni 6 tadan oshib vergul `,` uchrasa:
  * Bu bo'lak darhol alohida chunk sifatida kesiladi va **darhol Streaming TTS ga (WebSockets/HTTP2 oqimi orqali)** yuboriladi.
  * Foydalanuvchi birinchi gapni tinglayotgan vaqtda, LLM ikkinchi gapni yozayotgan bo'ladi (Pipelined Overlapping).

---

### USTUN 5: Fast-Path Shortcuts va Speculative Execution

1. **Fast-Path Shortcuts (0ms LLM kechikishi):**
   * Ko'p hollarda mijoz faqat *"Alo?", "Eshityapsizmi?", "Rahmat, sog' bo'ling"* kabi standart replikalarni aytadi.
   * Bu holatlarda LLM ga borish shart emas. Mahalliy Regex yoki Intent Matcher buni ushlaydi va oldindan sintez qilingan (pre-cached audio) javobni **30–50ms** ichida ijro etadi.
2. **Speculative Turn Execution (Gap tugashidan oldin boshlash):**
   * STT vositasi (Deepgram / Google / Vosk) gap davomida Interim (oraliq) natijalarni yuborib turadi.
   * Agar VAD mijozning gapi tugash arafasida ekanini sezsa, oxirgi nafas olish pauzasida oraliq matn LLM ga yuboriladi.
   * Mijoz gapirishdan to'xtagan lahzada LLM allaqachon birinchi tokenlarni chiqarib bo'lgan bo'ladi.

---

## 4. `robot-call-v2` Loyihasiga Integratsiya Qilish Rejasi

Bizning loyihamizda mavjud klasslar va ularga kiritilishi kerak bo'lgan aniq o'zgarishlar:

### 4.1. `DialogSession.java` & Xotira Qatlami
* **Mavjud holat:** `trimHistory(maxMessages, trimBlock)` shunchaki ro'yxat boshidan xabarlarni o'chiradi. Bu keshni buzadi va faktlarni yo'qotadi.
* **Yangi yechim:**
  1. `DialogSession` ga `String runningSummary` va `Map<String, Object> extractedSlots` maydonlarini qo'shish.
  2. Tarix `historyMaxMessages` (masalan, 8 ta xabar) dan oshganda, o'chirilayotgan xabarlar fon rejimida `RunningSummaryService` ga uzatiladi.
  3. Yangilangan `runningSummary` sessiyada saqlanadi.

### 4.2. `SystemPromptFactory.java` & Kesh Standarti
* **Mavjud holat:** `stablePrefix` va `turnAnnex` ga bo'lingan (bu ajoyib poydevor).
* **Kuchaytirish:**
  1. `stablePrefix` ichiga barcha Tool schemalari va doimiy qoidalar to'liq joylashtirilishi, uning hajmi kamida 1,500–2,000 tokenni tashkil etishi (Gemini va OpenAI kesh chegarasidan o'tishi uchun).
  2. `turnAnnex` ichiga `runningSummary` va `extractedSlots` kiritilishi (chat tarixidan keyin, eng oxirida).

### 4.3. `LlmTurnClient.java` & Chiqish Cheklovlari
* ChatClient chaqiruvida parametrlar:
  ```java
  ChatOptions options = ChatOptions.builder()
          .maxTokens(70)
          .temperature(0.3)
          .build();
  ```
* Bu modelning ortiqcha safsata sotishini va kutish vaqtini to'xtatadi.

### 4.4. `SpeechOutput.java` & Sentence Chunker Sozlamalari
* LLM dan kelayotgan oqimni (Flux<ChatResponse>) qabul qilishda birinchi chunk chegarasini kichraytirish:
  * Birinchi gap uchun: 3–5 ta so'z yetarli.
  * Keyingi gaplar uchun: 6–10 ta so'z.

---

## 5. Qiyosiy Ko'rsatkichlar (Before vs After)

| Ko'rsatkich | Eski / Standart Yondashuv | Ushbu Reja Joriy Qilingach | Yutuq |
|---|---|---|---|
| **1-turn kechikishi (TTFT)** | 350 – 500 ms | **120 – 180 ms** (Warm Cache) | **2.5x tezroq** |
| **15-turn kechikishi (TTFT)** | 1,200 – 1,800 ms (Katta kontekst) | **150 – 220 ms** (Cached + Compact) | **6x tezroq** |
| **Mijoz ovoz eshitish vaqti (TTFA)** | 1,800 – 2,500 ms | **380 – 500 ms** (Streaming Chunker) | **Tabiiy jonli suhbat** |
| **15-turnda umumiy kiruvchi token** | ~12,000 token | ~1,400 token (yangi) + Cached | **85% tejamkorlik** |
| **Chiqish tokenlari (Turn boshiga)** | 80 – 150 token (monolog) | **25 – 45 token** (lo'nda, 1-2 gap) | **3x arzon va tez** |
| **Uzoq suhbatda xotira (Recall)** | 40% (boshidagi faktlar o'chib ketadi) | **100%** (Slotlar va Summary hisobiga) | **Amneziya 0%** |
| **Oylik LLM hisob-kitobi (10,000 qo'ng'iroq)** | $100 \%$ (Baza) | **$20 - $28 \%$** | **3-4 baravar arzon** |

---

## 6. Bosqichma-bosqich Amalga Oshirish Yo'l Xaritasi (Roadmap)

> **Qaror (2026-11-05):** Rolling summary (Ustun 1B, §4.1 summary qismi, 3-bosqichdagi `AsyncSummaryService`) rejadan chiqarildi.

### 1-Bosqich: Chiqishni Cheklash va Keshni Qat'iylashtirish (Eng tez natija — 1-kun) — kodlandi 2026-11-05, tekshirilmagan
* [x] Chiqish limiti: `ReplyLimitProperties` (`voice-agent.dialog.reply-limit.max-tokens: 70`), `TurnTools.buildOptions` da qo'llanadi. Kompaniya/agent o'z `maxOutputTokens` ini qo'ysa — o'shanisi ustun. Thinking modellarga (Gemini, gpt-5/o*) `thinking-reserve-tokens` (768) qo'shiladi, aks holda thinking limitni yeb bo'sh javob beradi.
* [x] Promptga "markdown/ro'yxat/emoji yo'q" qoidasi qo'shildi; "1–2 gap, bitta savol" qoidasi avvaldan bor edi. Raqamlar ataylab raqamda qoladi (TTS o'zi o'qiydi).
* [x] `stablePrefix` tekshirildi: sana/soat va qo'ng'iroq faktlari prefix oxirida, qo'ng'iroq davomida o'zgarmaydi. `stable-toolset` default `true` qilindi; bosqichga bog'liq bo'lib qolgan ikki tool (`requestHumanTransfer`, EXTRACT_VARIABLE tool'i) endi butun qo'ng'iroq davomida bir xil e'lon qilinadi, bosqich cheklovi `TurnTools.run` da.

### 2-Bosqich: Sentence Chunker Streaming optimizatsiyasi (2-kun) — kodlandi 2026-11-05, tekshirilmagan
* [x] `SentenceSplitter` chegaralari `ChunkerProperties` (`voice-agent.dialog.chunker.*`) ga chiqarildi. Birinchi bo'lak (`opening`) endi 4 so'zdan keyin vergulda kesiladi (avval 50 belgi); `.?!` da 14 belgidan keyin — o'zgarmagan. Keyingi bo'laklar avvalgidek butun gap (20 belgi / vergul 90 belgi) — ular birinchi bo'lak ijro etilayotganda sintez qilinadi, kechikishga qo'shilmaydi. `SpeculationManager` ham shu chegaralarni ishlatadi (oldindan sintez qilingan gap bilan mos bo'lishi uchun).
* [x] TTS TTFB allaqachon o'lchanadi (`voice.turn.stage.latency{stage=tts_ttfb}`). Yangi `chunk_wait` bosqichi qo'shildi: LLM birinchi tokeni → birinchi bo'lak TTS'ga berilgan payt — aynan shu bosqich o'zgarishining samarasini ko'rsatadi.

### 3-Bosqich: Ierarxik Xotira — faqat slotlar (summary chiqarildi) — kodlandi 2026-11-05, tekshirilmagan
* [x] Tool yozgan slotlar avvaldan bor edi: `CallOutcomeLedger` → annex'dagi `ALREADY ESTABLISHED`.
* [x] Bo'shliq yopildi — hech qaysi tool yozmaydigan erkin faktlar ("kasalxonada", "oylik 15-da"): `DialogTools.noteClientFact(name, value)` (cascade-only, har doim mavjud, follow-up round'siz) → `DialogSession.clientFacts()` (ko'pi bilan 12 ta, nom bo'yicha yangilanadi) → annex'da `THE CLIENT TOLD YOU` qatori. Tarix kesilsa ham saqlanadi. O'chirish: `DIALOG_CLIENT_FACTS=false`.
* [—] `runningSummary` / `AsyncSummaryService` — rejadan chiqarilgan.

### 4-Bosqich: Fast-Path va Dynamic Model Routing (5-kun) — kodlandi 2026-11-05, tekshirilmagan
* [x] "Rahmat", "Xayr", takrorlash, "kimsiz?", "nima maqsadda?" avvaldan `FastPathRouter` da bor edi. Qo'shildi: **LINE_CHECK** — butun gap "Alo?", "Eshityapsizmi?", "Алло, вы меня слышите?" bo'lsa, LLM'siz "Ha, eshitaman." + agentning oxirgi savoli. Gap ichida ("alo, ha men Murodjonman") yoki agent hali gapirmagan bo'lsa — LLM'ga qoldiriladi.
* [x] Model routing avvaldan bor: `LlmTurnClient.selectModel` — `fast-model-max-words` (10) so'zgacha javob `dialog.fast-model` (gemini-3.5-flash-lite) ga, raqamli javob esa doim asosiy modelga. O'zgartirilmadi.
