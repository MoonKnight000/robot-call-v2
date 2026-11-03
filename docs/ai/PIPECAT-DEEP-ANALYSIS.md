# Pipecat Arxitekturasi, Ichki Kod Tahlili va Robot-Call-v2 Bilan Solishtirma

> **Hujjat maqsadi:** Ushbu tahlil `C:\Users\Murodjon\IdeaProjects\pipecat` lokal loyihasi manba kodlarini to'liq o'rganish, uning nima sababdan boshqa tillarda (ingliz, rus va h.k.) insondek silliq va o'ta past kechikish bilan suhbatlashish sirlarini ochib berish hamda uni bizning `robot-call-v2` loyihamiz bilan solishtirib, eng ilg'or g'oyalarni o'zlashtirish rejasini belgilaydi.

---

## 1. PIPECAT NIMA VA NEGA U JUDA TABIIY GAPLASHADI?

Pipecat — bu Daily tomonidan ishlab chiqilgan, ochiq kodli, to'liq asinxron (`asyncio`) Python karkasidir. Uning "juda yaxshi va tabiiy gaplashishi"ning asosiy sabablari quyidagi 4 ta muhandislik yechimida yotadi:

1. **Akustik EOU (Intonatsiyani eshitish):** Pipecat inson jim qolganini shunchaki millisekundlar bilan o'lchamaydi (500–1000 ms kutmaydi). U `Smart Turn v3` modeli orqali inson ovozidagi spektrogrammani tahlil qilib, **gap ohangidan (prosody)** gap tugaganini 100–150 ms ichida biladi.
2. **0 ms Barge-in (Darhol jim bo'lish):** Foydalanuvchi bitta tovush chiqargan zahoti, maxsus `InterruptionFrame` barcha navbatlarni aylanib o'tib, botning audio oqimini **20 ms ichida bo'shatadi (drain)** va davom etayotgan LLM vazifasini bekor qiladi.
3. **Token-to-Audio Streaming:** LLM butun gapni yozib bo'lishini kutmasdan, birinchi chiqqan tokenlar to'g'ridan-to'g'ri TTS ga oqiziladi (`TextAggregationMode.TOKEN`).
4. **Speculation Gate ("Predict & Hold"):** STT taxminan gap tugadi deb hisoblaganda LLM javob generatsiya qilishni boshlaydi, lekin audio simga chiqarilmay `HOLDING` holatida turadi. Turn tasdiqlanishi bilan darhol audio eshittiriladi.

---

## 2. PIPECAT ICHKI KOD ARXITEKTURASI (DEEP DIVE)

### 2.1. Frame-based Reaktiv Konveyer (`FrameProcessor`)

Pipecat'da har qanday xizmat (VAD, STT, LLM, TTS, Transport) bitta umumiy klassdan meros oladi: `FrameProcessor`.
Ular bir-biriga zanjir bo'lib ulanadi (`Pipeline`):

```python
pipeline = Pipeline([
    transport.input(),              # Mikrofon / WebRTC / Telefon audiosi
    stt_service,                    # Nutqni matnga aylantirish (Deepgram / Whisper)
    user_turn_controller,           # Navbat boshqaruvi (VAD + SmartTurn)
    llm_context_aggregator,         # Kontekst yig'uvchi
    llm_service,                    # Model (OpenAI / Groq / Anthropic)
    tts_service,                    # Ovoz sintezi (Cartesia / ElevenLabs)
    transport.output()              # Dinamik / RTP / WebRTC chiqishi
])
```

Har bir protsessor asinxron ravishda kadrlarni qabul qiladi:
* `await self.push_frame(frame, direction)` — kadrni oldinga yoki orqaga yuborish.

---

### 2.2. Kadrlar Ierarxiyasi: DataFrames vs SystemFrames (`src/pipecat/frames/frames.py`)

Kechikishni yengishdagi eng asosiy arxitektura siri — kadrlarning ikki toifaga bo'linishidir:

```
                  ┌────────────────────────┐
                  │         Frame          │
                  └───────────┬────────────┘
                              │
             ┌────────────────┴────────────────┐
             ▼                                 ▼
   ┌───────────────────┐             ┌───────────────────┐
   │     DataFrame     │             │    SystemFrame    │
   │ (Audio, Matn,...) │             │ (Interrupt,Cancel)│
   └─────────┬─────────┘             └─────────┬─────────┘
             │                                 │
             ▼                                 ▼
   Oddiy navbatda kutiladi           NAVBATNI AYLANIB O'TADI
   (FIFO: 100-300 ms)                (Darhol bajariladi: <1 ms)
```

1. **`DataFrame`:** Audio paketlar (`AudioRawFrame`), matn tokenlari (`TextFrame`), transkriptlar. Ular tartib bilan ketma-ket harakatlanadi.
2. **`SystemFrame`:** `InterruptionFrame`, `UserStartedSpeakingFrame`, `UserStoppedSpeakingFrame`, `CancelFrame`.
   * **Qoida:** Protsessorlar o'rtasidagi navbat to'la bo'lsa ham, `SystemFrame` navbatda turmaydi! U navbat boshiga sakrab o'tadi va barcha jarayonlarni shu lahzada to'xtatadi.

---

### 2.3. Turn Detection va Spekulyatsiya (`src/pipecat/turns/`)

Pipecat suhbat navbatini boshqarishni bir nechta nozik qatlamlarga ajratgan:

#### A. Gap boshlanishi: `MinWordsUserTurnStartStrategy`
* `src/pipecat/turns/user_start/` ichida joylashgan.
* Odam yo'talsa, stol qimirlasa yoki "ah" desa, bot darhol to'xtab qolmasligi uchun STT dan kamida 1–2 ta so'z kelishini kutadi.

#### B. Gap tugashi: `TurnAnalyzerUserTurnStopStrategy` va `SmartTurn`
* `src/pipecat/audio/turn/smart_turn/local_smart_turn_v3.py`:
  * Kiruvchi audio Whisper log-mel filtri orqali 80 ta mel-chastotaga ajratiladi.
  * ONNX modeli (`smart-turn-v3.2-cpu.onnx`) ovozning tugash ehtimolini hisoblaydi (`threshold = 0.5`).
  * Agar model `COMPLETE` desa, 800 ms jimlik kutmasdan **100 ms dayoq** turn yopiladi!

#### C. Spekulyatsiya eshigi: `SpeculationGate` (`src/pipecat/turns/speculation_gate.py`)
Ushbu klass 3 ta holat mashinasi (State Machine) asosida ishlaydi:
* `OPEN`: Hamma narsa to'g'ridan-to'g'ri o'tadi.
* `HOLDING`: LLM oldindan javob yozmoqda (eager turn), lekin audio chiqishi ushlab turiladi.
* `DROPPING`: Foydalanuvchi gapirishda davom etsa, tayyorlangan noto'g'ri javob tashlab yuboriladi.

```python
# Pipecat Speculation State Machine
if state == SpeculationState.HOLDING:
    # LLM javob generatsiya qilaveradi, lekin foydalanuvchiga eshittirilmaydi
    queue.append(frame)
elif frame == UserStoppedSpeakingFrame:
    # Taxmin to'g'ri chiqdi - ushlab turilgan barcha javob bir zumda eshittiriladi!
    state = SpeculationState.OPEN
    flush(queue)
elif frame == UserTurnResponseCancelFrame:
    # Foydalanuvchi gapirishda davom etdi - generatsiya o'chiriladi
    state = SpeculationState.DROPPING
    queue.clear()
```

---

### 2.4. Matnni Sintezlash: `TOKEN` vs `SENTENCE` Streaming (`src/pipecat/services/tts_service.py`)

Pipecat'da matnni ovozga aylantirishning ikkita rejimi bor (`TextAggregationMode`):

1. **`SENTENCE` rejimi:**
   * `SentenceAggregator` regex va tinish belgilari (`.`, `!`, `?`, `…`) orqali butun gap tugashini kutadi.
   * Gap to'liq bo'lgani sababli intonatsiya juda chiroyli chiqadi, ammo birinchi gap uzun bo'lsa 300–600 ms qo'shimcha kechikish paydo bo'ladi.
2. **`TOKEN` rejimi (Ultra-Fast):**
   * LLM chiqqan tokenni hech qanday tinish belgisisiz **shu zahotiyoq TTS WebSocketiga uzatadi**.
   * Cartesia Sonic va Deepgram Flux kabi yangi avlod TTS modellari uzuq-yuluq tokenlarni ham kontekst bilan sintezlay oladi.
   * Natijada, foydalanuvchi jim bo'lishi bilan 300–400 ms da birinchi ovoz eshitiladi!

---

### 2.5. Word Timestamp Tracking (Bot qayerda to'xtaganini bilish)

`src/pipecat/utils/context/word_timestamp_tracker.py`:
* TTS provayderidan kelayotgan audio bilan birga har bir so'zning vaqt belgilari (`WordTimestamp`) kuzatiladi.
* Agar foydalanuvchi botning gapini o'rtasida bo'lsa, bot aynan **qaysi so'zda to'xtaganini** biladi va LLM kontekstiga faqat eshittirilgan so'zlar yoziladi.

---

## 3. SOLISHTIRMA: PIPECAT vs ROBOT-CALL-V2

| Xususiyat / Bo'g'in | Pipecat (`pipecat-ai/pipecat`) | Bizning Loyiha (`robot-call-v2`) | Tahlil va Xulosa |
|---|---|---|---|
| **Dasturlash tili va platforma** | Python (`asyncio`) | Java 21 (`Virtual Threads` + `Netty RTP`) | **Java ancha ustun:** Virtual Thread'lar va Netty 100-500 ta parallel qo'ng'iroqlarda Python'dan ancha kam xotira va CPU yeydi. |
| **Telefoniya / Media** | WebRTC (Daily, LiveKit) va WebSocket | SIP / Asterisk G.711 RTP (20 ms) | Asterisk telefoniya uchun mos, lekin Pipecat WebRTC'da transkodlashsiz (Opus) to'g'ridan-to'g'ri ishlaydi. |
| **Kadrlar / Signallar oqimi** | `FrameProcessor` + `SystemFrame` (Priority queue bypass) | Event-listener va Reactive Flux | Pipecat'dagi `InterruptionFrame` kabi signallarni navbatsiz (bypass) uzatish bizda ham joriy etilishi kerak. |
| **Barge-in (Gapni bo'lish)** | `0 ms`: audio queue bir zumda `drain()` qilinadi | `onBargeIn`: avval `pausePlayback()`, 1200 ms so'z kutadi | Pipecat darhol o'chiradi; bizda esa yo'tal/aks-sadoni tekshirish uchun biroz kutiladi. |
| **Turn Detection (EOU)** | `SmartTurn v3` (akustik spektrogramma) + `VAD` | `Silero VAD` + `TranscriptTurnCues` + `SmartTurn` (faqat ru) | Pipecat intonatsiyani eshitadi. Bizda `smart_turn_v3.onnx` bor, lekin o'zbek tiliga moslashmagan. |
| **Spekulyatsiya (Eager turn)** | `SpeculationGate` (State machine: Holding/Dropping) | `SpeculationManager` (Interim STT bilan) | Ikkisi ham bir xil g'oya ustida qurilgan, lekin Pipecat'da kadr darajasida toza ajratilgan. |
| **TTS Aggregation** | `TextAggregationMode.TOKEN` va `SENTENCE` | `SentenceSplitter.java` (faqat jumla/nuqta kutadi) | **Bizning eng katta bo'shlig'imiz:** Biz butun boshli gap tugashini kutamiz, Pipecat esa token/clause darajasida oqizadi. |
| **O'zbek tili holati** | O'zbek tili umuman yo'q (na EOU, na STT) | O'zbek tili chuqur kiritilgan (`TranscriptTurnCues`, morfologiya, sanalar) | **Biz ustunmiz:** Bizda o'zbek tili qoidalari va fakt nazorati (`FactGuard`) bor. |

---

## 4. PIPECAT KODIDAN ROBOT-CALL-V2 GA KO'CHIRISH UCHUN 4 TA ANIQ YECHIM

Pipecat kodi tahlili natijasida `robot-call-v2` ga darhol tatbiq etish lozim bo'lgan eng muhim arxitektura yaxshilanishlari:

### 1. `SentenceSplitter.java` ni "Sub-Sentence / 3-Word" oqimiga o'tkazish
* **Pipecat kodi:** `TextAggregationMode.TOKEN` yoki qisqa iboralarni kutmasdan TTS ga uzatadi.
* **Bizda qilish kerak:** `SentenceSplitter.java` da suhbat boshidagi birinchi bo'lak (`opening`) uchun nuqta kutmasdan, dastlabki **3–4 ta so'z** (masalan, *"Assalomu alaykum, Murod aka,"*) olingandayoq TTS ga uzatish mexanizmini kiritish. Bu birinchi audio chiqishini **300–500 ms** ga tezlashtiradi.

### 2. Pipecat `SpeculationGate` mantiqini to'liq mustahkamlash
* Pipecat'ning `SpeculationGate` klassi kabi, oraliq transkript (interim) bo'yicha LLM generatsiyasi boshlanganda, uning natijasini xotirada ushlab turish (`HOLDING`), STT final kelgan zahoti uni bir zumda simga chiqarish. Agar foydalanuvchi gapirishda davom etsa, o'sha oqimni darhol yo'qotish (`DROPPING`).

### 3. Netty RTP Audio Queue ni 0 ms da "Drain" qilish
* Pipecat `InterruptionFrame` olganda `drain_audio_queue()` qiladi.
* Bizning `RtpEndpoint` da barge-in sodir bo'lganda navbatda qolgan barcha 20 ms lik G.711 paketlarini xotiradan zudlik bilan tozalash kerak, shunda bot gapni bo'lgandan keyin 100–200 ms "gapirib qolishi" butunlay yo'qoladi.

### 4. O'zbek tili uchun Agressiv Smart-EOU
* Pipecat ingliz tilida gap ohangiga qarab gap tugaganini bilgani kabi, biz o'zbek tilida gap oxiridagi fe'l shakllariga (`-dim`, `-man`, `-di`, `-mayman`, `bo'ladi`, `to'ladim`) tayangan holda `SpeechGate` sukunat taymerini 1200 ms dan **250–300 ms** ga tushirishimiz lozim.

---

## 5. XULOSA

Pipecat o'zbek tilini bilmaydi va biznes-logika (CRM, qarz undirish, fakt nazorati) jihatidan bizning loyihamizdan ancha sodda. Lekin **audioni oqimda ushlab turmasdan zudlik bilan uzatish (streaming orchestration)** bo'yicha uning arxitekturasi dunyodagi eng toza namunalardan biridir. 

Ushbu hujjatdagi 4 ta mexanizmni `robot-call-v2` loyihamizga kiritish orqali biz o'zbek tilidagi qo'ng'iroqlarni ham Pipecat kabi insondek tez va silliq gaplashadigan darajaga olib chiqa olamiz.
