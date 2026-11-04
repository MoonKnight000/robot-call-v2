# AI Voice Agent Arxitekturasi: Robot-Call-v2, Pipecat, TEN Framework va LiveKit Agents Chuqur Tahlili

> **Hujjat maqsadi:** Ushbu hujjat `robot-call-v2` loyihasining to'liq ovozli agent pipeline arxitekturasini sanoatdagi uchta yetakchi ochiq kodli ovozli freymvork — **`Pipecat`**, **`TEN Framework`** va **`LiveKit Agents`** bilan taqqoslaydi. Hujjatda ularning har birida nimalar yaxshiroq qilingani, bizning loyihamizning qaysi jihatlari ulardan ancha ustun ekanligi va `robot-call-v2` ni yangi bosqichga olib chiqish bo'yicha amaliy yo'l xaritasi keltirilgan.

---

## 1. TO'RT FREYMVORKNING ASOSIY FALSAFASI VA MAQSADI

Har bir platforma ma'lum bir ehtiyojdan kelib chiqib arxitekturalangan:

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                              ARXITEKTURA FALSAFASI                           │
├──────────────────────┬───────────────────────────────────────────────────────┤
│ robot-call-v2        │ Enterprise Telephony & Outbound Campaigns             │
│ (Bizning loyiha)     │ Asterisk 20 (ARI/AMI) + G.711 RTP + CRM + O'zbek tili │
├──────────────────────┼───────────────────────────────────────────────────────┤
│ Pipecat              │ Developer-First Modular Audio/Video Pipeline          │
│ (Daily.co)           │ Python asinxron generatorlar + WebRTC + Multi-modal   │
├──────────────────────┼───────────────────────────────────────────────────────┤
│ TEN Framework        │ Ultra-Low Latency Real-Time Multi-Modal Engine        │
│ (Agora)              │ C++ Native Core + Lock-Free Ring Buffers + Avatarlar  │
├──────────────────────┼───────────────────────────────────────────────────────┤
│ LiveKit Agents       │ Scalable WebRTC Cloud Voice & S2S Platform            │
│ (LiveKit)            │ Rust/Python + Realtime Speech-to-Speech + State Engine│
└──────────────────────┴───────────────────────────────────────────────────────┘
```

---

## 2. ULARDA NIMALAR YAXSHIROQ EKAN? (Biz ulardan nimani o'rganishimiz kerak?)

Pipecat, TEN Framework va LiveKit Agents manba kodlarini sinchiklab o'rganish natijasida ularning quyidagi 5 ta sohada bizdan oldinda ekanligi ma'lum bo'ldi:

---

### 2.1. Token-to-Audio Oqimi (Streaming) va Past Kechikish (TTFB)
* **Ularda (Pipecat va LiveKit):**
  Pipecat'da `TextAggregationMode.TOKEN` rejimi mavjud. LLM dan chiqqan har bir token (yoki 2–3 ta so'zdan iborat kichik qismlar) **butun gap tugashini kutmasdan**, darhol Cartesia, ElevenLabs yoki Deepgram TTS ga oqim sifatida uzatiladi.
  Natijada **TTFB (Time-To-First-Byte)** bor-yo'g'i **250–350 ms** ni tashkil qiladi!
* **Bizda (`robot-call-v2`):**
  Bizda `SentenceSplitter` klassi mavjud. U LLM dan kelayotgan matn ichida nuqta (`.`), so'roq (`?`) yoki undov (`!`) belgisi paydo bo'lmaguncha TTS ga yubormaydi.
  * **Natija:** Agar LLM 15 ta so'zdan iborat uzun gap tuzayotgan bo'lsa, foydalanuvchi butun gap generatsiya bo'lguncha **600–1200 ms kutib qoladi**.
  * **Yechim:** `SentenceSplitter` o'rniga so'zlar soniga qarab (masalan, dastlabki 3–4 ta so'z kelishi bilanoq) birinchi bo'lakni TTS ga berishni joriy qilish kerak.

---

### 2.2. Out-of-Band Barge-in va Zudlik Bilan Tozalash (Pipeline Flush)
* **Ularda (Pipecat):**
  Pipecat'da `InterruptionFrame` tushunchasi mavjud. Bu oddiy audio yoki matn kabi navbatda turmaydi, balki barcha navbatlarni aylanib o'tuvchi **"Out-of-band Control Frame"** hisoblanadi.
  Barge-in sodir bo'lishi bilan bu signal:
  1. Ishlayotgan asinxron LLM generatsiyasini darhol bekor qiladi (`task.cancel()`).
  2. TTS serveridagi generatsiyani WebSocket orqali uzadi (`{"type": "clear"}`).
  3. Mijoz qulog'iga borayotgan barcha chiquvchi audio buferlarni **10–15 ms ichida bo'shatadi (flush)**.
* **Bizda (`robot-call-v2`):**
  Bizda Netty RTP serveri chiquvchi paketlarni tashlab yuboradi va Asterisk ga buyruq beradi. Ammo ba'zan LLM orqa fonda keraksiz tokenlarni ishlab chiqarishni davom ettiradi va pul sarflaydi.

---

### 2.3. Reasoning / Thinking Taglarini Filtrlash (`<think> ... </think>`)
* **Ularda (TEN Framework):**
  Yangi avlod mulohaza yurituvchi modellari (DeepSeek-R1, Qwen 2.5 Max Thinking) o'z javobidan oldin `<think>Mijoz qarzini so'ramoqda, demak avval xushmuomala bo'lishim kerak...</think>` deb o'ylaydi.
  TEN Framework'da maxsus **`ThinkParser`** mavjud:
  * U LLM dan oqib kelayotgan streaming tokenlar orasidagi `<think>` tegi ichidagi hamma narsani yutib yuboradi (TTS ga uzatmaydi).
  * Faqat `</think>` yopilgandan keyingi haqiqiy javob matnini ovozga aylantiradi.
* **Bizda (`robot-call-v2`):**
  Agar tizimimizga DeepSeek-R1 yoki shunga o'xshash model ulansa, bot o'zining ichki o'y-fikrlarini ham mijozga ovoz chiqarib o'qib berishi xavfi bor.

---

### 2.4. Dinamik Filler Scheduler (Vosita chaqirilganda aqlli to'ldiruvchi)
* **Ularda (LiveKit Agents):**
  LiveKit'dagi `_FillerScheduler` (`filler_scheduler.py`) asinxron fon vazifasi sifatida ishlaydi:
  * Agar bot CRM dan ma'lumot qidirayotgan bo'lsa (Tool Execution), va oraliqda 800 ms dan ortiq jimjitlik paydo bo'lsa, tizim avtomatik ravishda dinamik to'ldiruvchi frazalarni aytadi ("Bir daqiqa, tekshirib ko'raman...").
  * Agar CRM tez javob qaytarsa, `reset_dwell()` chaqirilib, to'ldiruvchi fraza bekor qilinadi.
* **Bizda (`robot-call-v2`):**
  Bizda `PreToolPhrases.java` da faqat 6 ta vosita uchun statik O'zbekcha frazalar yozilgan. Agar yangi vosita (masalan, `checkPaymentStatus`) qo'shilsa, kodga kirmasdan turib unga fraza biriktirib bo'lmaydi.

---

### 2.5. Avtomatik Kontekst Qisqartirish (Context Compaction & Summarization)
* **Ularda (Pipecat):**
  Pipecat'da `llm_context_summarization.py` moduli mavjud. Uzoq davom etgan suhbatlarda (10–20 turn) LLM konteksti to'lib ketmasligi va narxi oshib ketmasligi uchun, eski xabarlar fon rejimida 3–4 ta ixcham faktlarga (bullet-points) aylantiriladi.
* **Bizda (`robot-call-v2`):**
  Bizda `ScenarioDefinition.facts` bor, ammo agar suhbat uzoq cho'zilsa, butun chat tarixi LLM ga ketaveradi va tokenlar soni o'sib boradi.

---

## 3. BIZDA NIMALAR USTUN? (Ularda umuman bo'lmagan narsalar)

Pipecat, TEN va LiveKit — asosan dasturchilar uchun mo'ljallangan past darajadagi (low-level) freymvorklardir. Ularda biznesga tayyor to'liq mahsulot yo'q.

`robot-call-v2` da esa quyidagi sohalar ulardan **bir necha barobar kuchliroq va tayyor**:

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                    ROBOT-CALL-V2 NING ABSOLYUT USTUNLIKLARI                  │
├──────────────────────────────────────────────────────────────────────────────┤
│ 1. To'liq Korporativ Telefoniya (Asterisk 20, ARI/AMI, SIP Trunklar, PBX)    │
│ 2. Ommaviy Qo'ng'iroqlar Kampaniyasi (Campaign Engine, Dialer, Pacing, DNC) │
│ 3. O'zbek Tili Lingvistikasi (TranscriptTurnCues, FloorRules, Affirmations) │
│ 4. Operatorlar Tizimi (Call Queue, Live Supervision, Warm/Blind Transfer)   │
│ 5. High-Concurrency Zero-GC Java Core (Netty RTP Server, Off-Heap Buffers)   │
└──────────────────────────────────────────────────────────────────────────────┘
```

### 3.1. Haqiqiy PBX va Telefoniya Infratuzilmasi
* **Pipecat va TEN:** Faqat WebRTC freymvorki. Ularni oddiy telefon raqamiga (GSM / shahar raqamlari) ulash uchun alohida tashqi SIP shlyuz (masalan, Daily SIP yoki LiveKit SIP) sotib olishingiz yoki Asterisk'ni o'zingiz noldan sozlashingiz kerak.
* **`robot-call-v2`:** Asterisk 20 bilan ARI (Asterisk REST Interface) va AMI orqali to'g'ridan-to'g'ri integratsiya qilingan. SIP Trunklar, Inbound marshrutlash, audio yozib olish (Stereo MinIO), IVR daraxti (`callflow`), DTMF tugmalarini qabul qilish — hammasi qutidan chiqqan holda tayyor.

### 3.2. Outbound Dialer va Kampaniyalar Boshqaruvi
* **Ularda:** Kampaniya, mijozlar bazasi, CSV import, qayta qo'ng'iroq qilish rejasi (`RetryPolicy`) degan tushunchalarning o'zi yo'q.
* **`robot-call-v2`:**
  * Katta masshtabdagi kampaniyalar (`campaign`), dial oynalari (masalan, faqat 09:00 dan 18:00 gacha), budjet nazorati.
  * **DNC (Do Not Call):** Bezovta qilinmaydigan raqamlar ro'yxati.
  * **AMD (Answering Machine Detection):** Ko'targan odammi yoki pochtami — darhol ajratadi.
  * **A/B Testing:** Ikki xil ovoz yoki ssenariyni solishtiruvchi `CampaignVariant`.

### 3.3. O'zbek Tili Fonetikasi va Mahalliy Madaniyat
* **Ularda:** Faqat ingliz yoki xitoy tillariga moslangan intonatsiya va matn modellari bor.
* **`robot-call-v2`:**
  * `TranscriptTurnCues.java`: O'zbek tili grammatikasi (`-yapman`, `-ganman`, `chunki`, `agar`, `lekin`) asosida gap tugaganini 99% aniqlik bilan hisoblaydi.
  * `FloorRules.java`: O'zbeklar suhbat davomida aytib turadigan "ha", "aha", "xo'p", "mayli" kabi tasdiqlarni (backchannel) ajratib olib, bot gapini bo'lmasligini ta'minlaydi.
  * `FastPathRouter.java`: "Ha", "Yo'q", "Rahmat" kabi qisqa so'zlarga LLM ga bormasdan **20 ms ichida** keshdan javob qaytaradi!

### 3.4. Yuqori Yuklama (High Concurrency) va Zero-GC Java Core
* **Python asosidagi tizimlar (Pipecat, LiveKit Python):**
  Python'ning **GIL (Global Interpreter Lock)** cheklovi tufayli bitta serverda 30–50 tadan ortiq parallel real vaqt audio oqimini ko'tara olmaydi (ko'p protsesslar ochish talab etiladi).
* **`robot-call-v2` (Java 21 + Netty):**
  Non-blocking I/O va `ThreadLocal` direct buferlar hisobiga bitta o'rtacha serverda **200–300 ta parallel qo'ng'iroqni** xotirada to'xtalishlarsiz (Zero-GC pauses) barqaror ushlab tura oladi.

---

## 4. BATAFSIL ARXITEKTURA SOLISHTIRUVI (Side-by-Side Matrix)

| Komponent / Mezon | `robot-call-v2` | `Pipecat` | `TEN Framework` | `LiveKit Agents` |
|---|---|---|---|---|
| **Asosiy Stack** | Java 21, Spring Boot, Netty, ONNX | Python, asyncio, numpy | C++ Native Core, Python/Go | Rust Core, Python SDK, WebRTC |
| **Pipeline Turi** | Reaktiv Voqea Asosidagi Sessiya | Chiziqli `FrameProcessor` Grafi | Kengaytmalar Grafi (`manifest.json`) | Asinxron `AgentSession` Mashinasi |
| **Birlamchi Transport** | **SIP / RTP (Asterisk 20)** | WebRTC (Daily.co) / WebSocket | WebRTC (Agora RTC) | WebRTC (LiveKit Cloud/OSS) |
| **Audio Chastotasi** | **8 kHz (Telefoniya)** | 16 kHz / 8 kHz | 16 kHz / 48 kHz | 16 kHz / 48 kHz |
| **O'rtacha TTFB (Latentlik)** | 600–900 ms (gap kutish sababli) | **250–350 ms (Token streaming)** | **200–300 ms (Native C++)** | **250–400 ms (Realtime S2S)** |
| **LLM Reasoning (Think Filter)** | Mavjud emas | Regex orqali | **Mavjud (`ThinkParser`)** | Mavjud emas |
| **Interruption Mexanikasi** | RTP Drop + Asterisk Stop | **`InterruptionFrame` (Out-of-band)** | Graph Flush buyrug'i | Audio Track Abort + Resume |
| **Tool Calling & Fillers** | Statik 6 ta fraza (`PreToolPhrases`) | Dinamik matnlar | Event-driven hooklar | **Dinamik `_FillerScheduler`** |
| **Kontekstni Qisqartirish** | Ssenariy faktlari | **Avtomatik Summarization** | Kengaytma orqali | Slayding oyna |
| **O'zbek Tili Integratsiyasi** | **Chuqur integratsiya (Morfologiya)** | Yo'q | Yo'q | Yo'q |
| **PBX, Navbatlar va Transfer** | **To'liq tayyor (Queues, Transfer)** | Yo'q | Yo'q | Alohida SIP shlyuz kerak |
| **Outbound Kampaniyalar** | **Tayyor (Dialer, DNC, Budjet)** | Yo'q | Yo'q | Yo'q |
| **A/B Testing** | **Mavjud (`CampaignVariant`)** | Qo'lda kodlash kerak | Qo'lda kodlash kerak | Qo'lda kodlash kerak |
| **Grafik Dashboard & CRM** | **Tayyor (React UI, Uysot CRM)** | Faqat demo UI | Faqat demo UI | Faqat Cloud Console |

---

## 5. ROBOT-CALL-V2 UCHUN 5 BOSQICHLI MODERNIZATSIYA REJASI

Ushbu tahlil asosida loyihamizni dunyo darajasidagi eng tezkor va aqlli telefoniya agentiga aylantirish uchun quyidagi 5 ta amaliy o'zgarish tavsiya etiladi:

```
                  MODERNIZATSIYA YO'L XARITASI
                  
  [ 1. Token-Level TTS ]  ──▶  TTFB ni 800ms dan 300ms ga tushirish
            │
  [ 2. DeepSeek Think ]   ──▶  <think> teglari ovozga chiqmasligini ta'minlash
            │
  [ 3. Dynamic Endpoint]  ──▶  LiveKit ExpFilter orqali mijoz tempiga moslashish
            │
  [ 4. Dynamic Fillers ]  ──▶  Statik 6 ta fraza o'rniga aqlli scheduler qo'yish
            │
  [ 5. VAD Silent Reset]  ──▶  Uzoq qo'ng'iroqlarda Silero VAD charchashini yo'qotish
```

### 1-Bosqich: Token-Level TTS Streaming (Kechikishni 300 ms ga tushirish)
* `SentenceSplitter.java` ga minimal so'z chegarasini (`minFirstChunkWords = 3`) qo'shish.
* Birinchi jumla uchun nuqtani kutmasdan, birinchi 3–4 ta so'z kelishi bilanoq uni TTS ga yuborish. Qolgan so'zlar esa keyingi bo'lakda uzatiladi. Natijada mijoz kutib qolmaydi.

### 2-Bosqich: DeepSeek-R1 / Qwen `<think>` Teglarini Filtrlash
* TEN Framework tajribasidan foydalanib, LLM stream parseriga `ThinkTagFilter` qo'shish.
* Model javobidagi `<think>...</think>` bloklari loglarga yoziladi, lekin TTS va mijoz audio oqimiga umuman o'tkazilmaydi.

### 3-Bosqich: Dinamik Endpointing (`ExpFilter`)
* LiveKit Agents'ning `DynamicEndpointing` algoritmini `SpeechGate.java` ga integratsiya qilish.
* Tez gapiruvchi mijozlarda kutish vaqtini 300 ms ga qisqartirish, o'ylab sekin gapiruvchi mijozlarda esa 700 ms gacha kengaytirish.

### 4-Bosqich: Dinamik Tool Fillers
* Statik `PreToolPhrases.java` o'rniga, ssenariy JSON ichidan har bir vosita uchun to'ldiruvchi frazalarni olish yoki LiveKit'dagi kabi fon taymeri asosida ishlovchi `FillerScheduler` joriy qilish.

### 5-Bosqich: VAD Sukunat Reseti
* Pipecat tajribasiga binoan, `VadStream.java` da 2 soniyadan ortiq davom etgan toza sukunatda Silero VAD ning LSTM holat matritsasini nolga tenglash (`reset_states`).

---

## 6. XULOSA

`robot-call-v2` o'zining korporativ telefoniya (Asterisk/SIP), kampaniyalar boshqaruvi, O'zbek tili lingvistikasi va yuqori unumdorlikka ega Java yadrosi bilan **Pipecat**, **TEN** va **LiveKit** dan biznes va amaliy foydalanish nuqtai nazaridan bir necha qadam oldinda turadi.

Pipecat va LiveKit'ning eng sara texnologiyalari — **Token-level TTS streaming**, **Dynamic endpointing** va **VAD silent reset** mexanizmlarini loyihamizga tatbiq etish orqali, biz o'zbek tilidagi eng tezkor, tabiiy va uzilishlarsiz gapiruvchi №1 ovozli agent platformasini yaratamiz.
