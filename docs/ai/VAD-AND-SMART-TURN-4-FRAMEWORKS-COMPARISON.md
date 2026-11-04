# VAD, Smart Turn va Turn-Taking Tizimlari: 4 Ta Freymvork Chuqur Tahlili va Qiyosiy Matritsasi

> **Hujjat maqsadi:** Ushbu hujjat real vaqt rejimida ishlovchi 4 ta yirik ovozli AI platformasi — **`robot-call-v2`** (bizning loyiha), **`Pipecat`**, **`TEN Framework`** va **`LiveKit Agents`** arxitekturasida **VAD (Voice Activity Detection)**, **Smart Turn (Turn-taking)**, **Barge-in (gapni bo'lish)** va **Endpointing (gap tugashini aniqlash)** mexanizmlarining qanday ishlashini manba kodlari asosida tahlil qiladi hamda yakunda to'liq qiyosiy matritsani taqdim etadi.

---

## 1. KIRISH VA VOZLI AI AGENTLARNING ENG NOZIK MUAMMOSI

Insonlar bilan tabiiy muloqot qiluvchi ovozli agent (Voice Agent) yaratishda eng qiyin vazifa — bu LLM javob berishi emas, balki **"Inson qachon gap boshladi va qachon gapirishdan to'xtadi?"** degan savolga millisekundlar ichida to'g'ri javob topishdir.

Agar tizim bu savolga noto'g'ri javob bersa:
* **Erta to'xtatish (Premature Cut-off):** Inson gap orasida 400 ms nafas olgani yoki o'ylab qolgani uchun bot uning gapini bo'lib, chala javob berib yuboradi (mijozning g'ashiga tegadi).
* **Kechikish (High Latency):** Inson gapirib bo'lganidan keyin bot 1–1.5 soniya "balki yana gapirib qolar" deb kutib turadi (suhbat sun'iy va noqulay bo'lib qoladi).
* **Soxta Barge-in (False Interruption):** Mijoz yo'talsa, nafas olsa, ko'cha shovqini eshitilsa yoki bot gapirayotganda mijoz shunchaki "ha", "tushundim" deb eshitayotganini bildirsa (backchannel), bot o'z nutqini darhol to'xtatib jim bo'lib qoladi.

Quyida to'rtta tizim ushbu muammolarni qanday hal qilgani tahlil qilinadi.

---

## 2. 1-MANBA: `robot-call-v2` QANDAY ISHLAYDI?

Loyiha kodi: `src/main/java/uz/murodjon/robotcallv2/agent/`

`robot-call-v2` real telefoniya (SIP/Asterisk, G.711 RTP 8 kHz) muhitida yuqori yuklama (Concurrency) va past kechikish uchun maxsus ishlab chiqilgan.

```
Incoming G.711 RTP (8 kHz, 20ms paketlar)
                 │
                 ▼
     [ VadStream.java ] ──(Har 32ms: 256 samples)──▶ [ SileroVad.java (ONNX Runtime) ]
                 │                                         │ LSTM Holati: [2, 1, 128]
                 │ ◀── Nutq ehtimolligi (0.0 .. 1.0) ──────┘
                 │
         [ DSP Filtrlari ]
         ├─ 1. noiseFloorRms (Dinamik fon shovqini o'lchagich)
         ├─ 2. echoGateRatio = 0.2 (Bot o'z ovozi aks-sadosini bloklaydi)
         └─ 3. Dual-Threshold (Barge-in: 0.4, Gating: 0.3)
                 │
                 ├──▶ Nutq boshlandi:
                 │       ├─ Bot gapirayotgan bo'lsa ──▶ Netty RTP Playback to'xtatiladi (Barge-in)
                 │       └─ Bot jim bo'lsa ───────────▶ SpeechGate ochiladi (Google/Deepgram STT ga uzatish)
                 │
                 └──▶ Sukunat boshlandi (VAD jim bo'ldi):
                         │
                         ▼
             [ Turn-Taking Dvigateli ]
             ├─ 1. SmartTurnDetector.java (16kHz Akustik Smart Turn v3 ONNX)
             ├─ 2. TranscriptTurnCues.java (O'zbek/Rus tili morfologik qoidalari)
             └─ 3. FloorRules.java (O'zbekcha "aha", "ha", "xo'p" backchannel filtri)
```

### 2.1. VAD Dvigateli: `SileroVad.java` va `VadStream.java`
1. **Silero VAD v6.2.2 ONNX:**
   * Model telefoniya uchun maxsus 8000 Hz rejimida ishlaydi: 1 ta tahlil oynasi = **256 ta namuna (32 ms)**.
   * **Zero-GC va Direct Buffers (`VadScratch`):** Har 32 ms da Java obyektlari yaratilib Garbage Collector'ni qiynamasligi uchun `ThreadLocal<VadScratch>` direct off-heap `FloatBuffer` lari ishlatiladi (`allocateDirect`).
   * **Context Prepending (32 samples):** Silero v6 arxitekturasi oynaning boshiga oldingi oynaning oxirgi 32 ta namunasini ulab berishni talab qiladi. Busiz modelning birinchi konvolyutsiyasi ehtimollikni sakratib yuboradi (thrashing: `0.95 -> 0.12 -> 0.88`).
2. **`VadStream.java` DSP Filtrlari:**
   * **Dinamik Shovqin Moslashuvi (`noiseFloorRms`):** Sukunat vaqtida fon shovqinining RMS quvvati doimiy hisoblab boriladi. Ko'chada ketayotgan mijozda fon chegarasi avtomatik oshiriladi.
   * **Akustik Aks-Sado To'sig'i (`echoGateRatio = 0.2`):** Bot baland ovozda gapirganda dinamikdan mikrofonga qaytib kirgan aks-sado botning o'zini to'xtatib qo'ymasligi (hybrid echo) uchun quvvat nisbati tekshiriladi.
   * **Dual-Threshold:** Bot gapirayotganda barge-in uchun yuqori chegara (`0.4` va 400 ms uzluksiz nutq), bot jim turganda esa pastroq chegara (`0.3`) ishlatiladi.
3. **TEN VAD Muqobili (`TenVad.java`):**
   * Loyihada Agora kompaniyasining C++ native `TenVadLibrary.java` kutubxonasi mavjud. `speech.yml` orqali Silero o'rniga TEN VAD ga bir zumda o'tish mumkin.

### 2.2. Turn-Taking va Til Qoidalari
* **Akustik Model (`SmartTurnDetector.java`):** 16 kHz audio bo'yicha Whisper log-mel spektrogrammasi (`WhisperFeatures`) orqali inson ovozining intonatsiyasiga qarab gap tugagan yoki tugamaganligini aniqlaydi (`threshold = 0.5`, `earlyThreshold = 0.8`).
* **Lingvistik Qoidalar (`TranscriptTurnCues.java`):** O'zbek tili fe'li gap oxirida keluvchi (verb-final) til hisoblanadi. Shuning uchun:
  * Gap bog'lovchilar (`chunki`, `lekin`, `ammo`, `agar`, `va`, `yoki`), kelishik qo'shimchalari (`-ning`, `-ni`, `-ga`, `-da`, `-dan`), ravishdoshlar (`-ib`, `-gach`, `-sa`) bilan tugasa — **INCOMPLETE** deb hisoblanadi va kutish vaqti avtomatik uzaytiriladi.
  * Gap tuslangan fe'llar (`-yapman`, `-di`, `-aman`, `-moqchi`), tasdiq so'zlar (`ha`, `yo'q`, `bo'ldi`, `rahmat`, `kelishdik`) bilan tugasa — **COMPLETE** deb belgilanadi va kutmasdan darhol javob beriladi.
* **O'zbekcha Backchannel To'sig'i (`FloorRules.java`):** Bot gapirayotgan paytda mijoz faqat "ha", "aha", "xo'p", "mayli" desa, bot to'xtamaydi — nutqini davom ettiradi.

---

## 3. 2-MANBA: `Pipecat` QANDAY ISHLAYDI?

Loyiha kodi: `IdeaProjects/pipecat/src/pipecat/`

Pipecat — bu Python asosidagi ochiq kodli, asinxron audio/video oqimlar pipeline freymvorki.

```
Audio Frame ──▶ [ SileroVADAnalyzer ] ──▶ [ UserTurnProcessor ] ──▶ [ LLM / TTS ]
                       │                         │
            _maybe_reset_model()        ClassifierUserTurnStopStrategy
            (Har 2s sukunatda           (Tezkor LLM: ✓, ○, ◐)
             LSTM tozalash)
```

### 3.1. VAD Dvigateli: `SileroVADAnalyzer` (`src/pipecat/audio/vad/silero.py`)
Pipecat Silero VAD ni Python/ONNX muhitida ishlatadi. Uning eng e'tiborga molik ixtirosi — **davriy holat tozalash (`_maybe_reset_model`)**:
```python
# Pipecat: har 2 soniya toza sukunatda LSTM holatini tozalaydi
if (self._windows_since_reset * self.num_frames_required() >= _MODEL_RESET_INTERVAL_SECS * self.sample_rate
    and self._vad_state == VADState.QUIET
    and self._low_confidence_windows >= _MODEL_RESET_LOW_CONFIDENCE_WINDOWS):
    self._model.reset_states()
    self._windows_since_reset = 0
```
* **Nima uchun kerak?** Silero VAD ning LSTM rekurrent holati uzoq sukunatda (yoki telefondagi uzluksiz chug'ur-chug'ur shovqinda) xatoliklarni yig'ib boradi (drift). Natijada keyingi haqiqiy nutq boshlanganda model uning birinchi soniyasini sezmay qolishi mumkin. Pipecat har 2 soniya sukunatda modelni tozalab, doim "yangi" holatda ushlab turadi.
* **4-bosqichli holat mashinasi:** `QUIET` ──▶ `STARTING` ──▶ `SPEAKING` ──▶ `STOPPING` ──▶ `QUIET`.

### 3.2. Turn-Taking Arxitekturasi (`src/pipecat/turns/`)
Pipecat inson nutqining boshlanishi va tugashini ikki xil strategiyalar guruhiga ajratgan:

1. **User Start Strategies (`user_start/`):**
   * `VADUserTurnStartStrategy`: VAD ovoz bor deb aytganda turn boshlanadi.
   * `TranscriptionUserTurnStartStrategy`: STT dan birinchi matn kelmaguncha turn boshlanmaydi (yo'tal va shovqinni bartaraf qiladi).
   * `MinWordsUserTurnStartStrategy`: Kamida $N$ ta so'z aytilmaguncha turn hisoblanmaydi.
   * `WakePhraseUserTurnStartStrategy`: "Hey bot" kabi uyg'otuvchi so'z aytilgandagina boshlanadi.
2. **User Stop Strategies (`user_stop/`):**
   * `ClassifierUserTurnStopStrategy`: STT dan chiqqan matnni orqa fonda juda tezkor kichik LLM ga yuboradi. LLM quyidagi 3 ta belgidan birini qaytaradi:
     * `✓` (Complete): Fikr to'liq tugadi, bot darhol gapirishi mumkin.
     * `○` (Incomplete): Gap tugamadi (masalan, "Men kecha do'konga borib..."), bot kutishi kerak.
     * `◐` (Wait/Ambiguous): Noaniq, biroz kutish lozim.
   * `SpeechTimeoutUserTurnStopStrategy`: Agar belgilangan vaqt ichida yangi so'z kelmasa, majburiy turn yakunlanadi.
   * `EagerUserTurnStopStrategy`: Kutmasdan, minimal sukunatda darhol to'xtatish.
3. **`UserIdleController` (3 Pog'onali Eslatish):**
   Mijoz jim bo'lib qolsa, 3 bosqichli eslatma yuboriladi:
   1. Kontekstli yumshoq eslatma ("Meni eshityapsizmi?").
   2. Qat'iy eslatma ("Savolimga javob bermasangiz, qo'ng'iroqni yakunlayman").
   3. Avtomatik xayrlashuv va aloqani uzish.

### 3.3. Barge-in Mexanikasi: `InterruptionFrame`
Pipecat'da barge-in sodir bo'lganda `InterruptionFrame` deb nomlangan maxsus yuqori ustuvorlikdagi boshqaruv freymi (Out-of-band Control Frame) yuboriladi. U barcha navbatlarni aylanib o'tib, ishlayotgan LLM generator vazifasini (`asyncio.Task.cancel()`) darhol o'chiradi va TTS buferlarini bo'shatadi (flush).

---

## 4. 3-MANBA: `TEN Framework` QANDAY ISHLAYDI?

Loyiha kodi: `IdeaProjects/ten-framework/ai_agents/`

Agora jamoasi tomonidan yaratilgan TEN Framework C++ o'zagi va Python/Go bog'lamalari bilan ishlovchi ultra past kechikishli (ultra-low latency) real vaqt tizimidir.

```
Agora RTC Stream ──▶ [ ten_vad (C++ Native) ] ──▶ [ ten_turn_detection ] ──▶ [ LLM Extension ]
                            │                               │
                     Zero-Tail Latency               Micro-Model:
                     (<10ms silence drop)            unfinished | wait | finished
```

### 4.1. VAD Dvigateli: `ten_vad` (Agora Custom Neural VAD)
* **Zero-Tail Latency (Kechikmagan dum):** Silero VAD ning eng mashhur kamchiligi — inson gapdan to'xtaganidan keyin ham LSTM rekurrent qatlami sababli **150–300 ms davomida nutq bor deb hisoblab turadi**. TEN VAD da bu kechikish bartaraf etilgan: inson to'xtashi bilan model ehtimolligi bir necha millisekundda nolgacha tushadi.
* **Native C++ Core:** Qayta ishlash to'liq C++ xotirasida, lock-free ring-buffer yordamida kechadi. Python faqat yuqori qatlam boshqaruvi uchun ishlatiladi.
* **Moslashuvchan Hop-Size:** 16 kHz da 10 ms, 16 ms, 20 ms va 32 ms oynalar bilan ishlay oladi.

### 4.2. Turn-Taking Dvigateli: `ten_turn_detection` (`turn_detector.py`)
TEN Framework turn-taking uchun maxsus mikromodel kengaytmasidan (`ten_turn_detection`) foydalanadi:
1. **Tinish belgilarini tozalash (`remove_punctuation`):** STT ko'pincha sukunatga qarab o'zi nuqta qo'yib beradi. Model unga chalg'imasligi uchun barcha tinish belgilari olib tashlanadi.
2. **Kichik LLM ga 1-Tokenlik So'rov (`max_tokens = 1`):**
   ```python
   chat_completion = await self.client_session.chat.completions.create(
       messages=[{"role": "user", "content": no_punc_text}],
       model=self.config.model,
       max_tokens=1,
       temperature=self.config.temperature,
   )
   ```
3. **Qarorlar Tizimi:**
   * `unfinished`: Inson gapni tugatmadi, tinglashda davom etish.
   * `wait`: Qisqa tanaffus, kutish lozim.
   * `finished`: Gap tugadi, darhol asosiy LLM ga yuborish.
4. **TTFB (Time-To-First-Byte) Monitoring:** Har bir baholashning vaqti `KEYPOINT [ttfb:{}ms]` sifatida loglanadi (odatda Groq/Cerebrium bilan 80–120 ms). Agar foydalanuvchi yangi so'z aytib yuborsa, ishlayotgan task darhol bekor qilinadi (`cancel_eval()`).

---

## 5. 4-MANBA: `LiveKit Agents` QANDAY ISHLAYDI?

Loyiha kodi: `IdeaProjects/agents/livekit-agents/livekit/agents/voice/`

LiveKit Agents — WebRTC asosida dunyodagi eng ilg'or ovozli agent platformalaridan biri bo'lib, unda VAD va Turn-taking sohasida inqilobiy yechimlar mavjud.

```
WebRTC Audio ──▶ [ Silero VAD (Rust/Python) ] ──▶ [ DynamicEndpointing ] ──▶ [ TurnDetector ]
                         │                               │                         │
                  prefix_padding                  ExpFilter (0.9)          end_of_turn_prob
                  (300ms ring buffer)             Inson tempini o'rganish  backchannel_prob
```

### 5.1. VAD va Audio Pre-Padding (`livekit-plugins-silero`)
* **Audio Pre-Padding Buferi (`prefix_padding_duration`):**
  Ko'pchilik tizimlarda inson gap boshlagan paytda, VAD uni tahlil qilib "ha, bu nutq" deb tasdiqlaguncha (50–100 ms) boshlang'ich tovushlar (masalan, portlovchi "P", "T", "B", "K" tovushlari) kesilib ketadi.
  LiveKit doimiy ravishda **200–300 ms audioni aylanma buferda (ring buffer)** ushlab turadi. VAD nutqni qayd etishi bilan STT ga nafaqat yangi audio, balki shu 300 ms lik o'tmish audiosi ham qo'shib beriladi. Natijada birorta so'z boshidagi tovush yo'qolmaydi.

### 5.2. Dinamik Endpointing: `DynamicEndpointing` (`endpointing.py`)
Bu LiveKit Agents'ning eng noyob innovatsiyalaridan biridir.
Odatdagi tizimlarda sukunat kutish vaqti statik qilib qo'yiladi (masalan, `silence_timeout = 500ms`). Lekin kimdir juda tez gapiradi (unga 250 ms kifoya), kimdir esa o'ylab-o'ylab sekin gapiradi (unga 800 ms kerak).

LiveKit **insonning shaxsiy gapirish tempiga real vaqtda moslashuvchi matematik filtr (`ExpFilter`)** qo'llaydi:
```python
class DynamicEndpointing(BaseEndpointing):
    def __init__(self, min_delay: float, max_delay: float, alpha: float = 0.9):
        self._utterance_pause = ExpFilter(
            alpha=alpha, initial=min_delay, min_val=min_delay, max_val=max_delay
        )
```
* **Qanday o'rganadi?**
  1. **Nutqlar orasidagi tanaffus (Pauses between utterances):** Inson bir jumla ichida nechchi millisekund tanaffus qilayotgani o'lchanadi va eksponentsial siljuvchi o'rtacha (`alpha = 0.9`) bilan `min_delay` moslashtirib boriladi.
  2. **Soxta Turn Tugashi (False EOT) va Zudlik bilan Barge-in:** Agar bot gapirishni boshlashi bilanoq mijoz darhol botni bo'lib gapini davom ettirsa (`immediate interruption`), tizim: *"Demak men erta gapirib yuboribman, bu odam gap orasida uzoqroq to'xtar ekan"* deb xulosa chiqaradi va `min_delay` chegarasini avtomatik oshiradi!

### 5.3. Ko'p Modalli Turn Detektori: `TurnDetector` (`turn.py`)
LiveKit shunchaki matnga qaramaydi. U audio va suhbat kontekstini birgalikda baholovchi EOT (End-of-Turn) modelini ishlatadi:
* `end_of_turn_probability`: Turn tugash ehtimolligi.
* `backchannel_probability`: Mijoz shunchaki "aha", "hm" deb eshitayotganini bildirish ehtimolligi.
* `backchannel_boundary = (1.0, 1.0)`: Agent gapirayotganda, agar mijozning ovozi backchannel deb topilsa, bot o'z nutqini to'xtatmaydi!

### 5.4. Qayta Tiklanuvchi Barge-in: `resume_false_interruption`
Agar mijoz yo'talsa yoki qisqa tovush chiqarsa va bot to'xtab qolsa:
* Agar `false_interruption_timeout` (2.0 soniya) davomida mijoz boshqa hech narsa gapirmasa, LiveKit buni **"soxta barge-in"** deb baholaydi va agent avtomatik ravishda **uzilgan joyidan o'z nutqini davom ettiradi** (`resume_false_interruption: True`)!

### 5.5. Filler Scheduler: `_FillerScheduler` (`filler_scheduler.py`)
Agar agent tashqi vositani (Tool calling, CRM API) chaqirayotgan bo'lsa va bu bir necha soniya vaqt olsa, fon jimjit bo'lib qolmasligi uchun `_FillerScheduler` avtomatik ravishda to'ldiruvchi frazalarni aytadi ("Bir daqiqa, tekshiryapman..."). Agar foydalanuvchi gap boshlasa yoki tool tezroq qaytsa, `reset_dwell()` chaqirilib, fraza to'xtatiladi.

---

## 6. KATTA QIYOSIY TAHLIL MATRITSASI (Side-by-Side Comparison)

Quyidagi jadvalda 4 ta tizimning VAD, Turn-taking va nutqni boshqarish imkoniyatlari to'liq solishtirilgan:

| Funksiya / Xususiyat | `robot-call-v2` (Bizda) | `Pipecat` | `TEN Framework` | `LiveKit Agents` |
|---|---|---|---|---|
| **Asosiy VAD Modeli** | Silero v6.2.2 ONNX (va TEN VAD JNI) | Silero ONNX (Python) | **TEN VAD (Agora Custom NN)** | Silero ONNX (Rust/Python) |
| **Birlamchi Audio Chastotasi** | **8 000 Hz (Telefoniya G.711)** | 16 000 Hz / 8 000 Hz | 16 000 Hz / 48 000 Hz | 16 000 Hz / 48 000 Hz (WebRTC) |
| **Xotira va GC Arxitekturasi** | **Zero-GC Off-Heap** (`ThreadLocal` Direct FloatBuffers) | Python Heap (`numpy` array) | C++ Native Zero-Copy Ring Buffers | Rust Native Memory Management |
| **VAD Tail Latency (Sukunatga o'tish)** | 150–250 ms (Silero) / <10 ms (TenVad) | 150–250 ms | **< 10 ms (Zero-Tail)** | 150–200 ms |
| **VAD LSTM Holatini Reset Qilish** | Mavjud emas (oqim davomida saqlanadi) | **Mavjud (`_maybe_reset_model`)** Har 2s toza sukunatda | Model arxitekturasi doimiy yangilanadi | Mavjud emas |
| **Audio Pre-padding (Bosh tovushlar)** | Pre-buffer (`SpeechGate` da 100ms) | O'rtacha | Hop-size orqali | **Mukammal (`prefix_padding` 300ms ring buffer)** |
| **Akustik Aks-Sado (Echo) Filtrlash** | **Mavjud (`echoGateRatio = 0.2`)** | Tashqi AEC ga tayanadi | WebRTC AEC / Agora Engine | WebRTC AEC |
| **Dinamik Shovqin Moslashuvi** | **Mavjud (`noiseFloorRms` tracking)** | Mavjud emas | Model ichki shovqin filtri | Mavjud emas |
| **Dinamik Endpointing (Tempga moslashuv)** | Qisman (`TranscriptTurnCues` orqali) | Mavjud emas | Mavjud emas | **Mukammal (`DynamicEndpointing` ExpFilter)** |
| **Semantik / Matnli Turn Detektori** | **O'zbek/Rus tili morfologiyasi** (`TranscriptTurnCues.java`) | Fast LLM (`ClassifierUserTurnStopStrategy`) | Fast Micro-LLM (`ten_turn_detection`) | Ko'p modalli `TurnDetector` (Audio+Text) |
| **Akustik Intonatsiya Modeli** | **Smart Turn v3 ONNX** (`WhisperFeatures`) | Smart Turn v3 (CoreML/ONNX) | Mavjud emas | LiveKit Audio EOT model |
| **Backchannel Filtrlash (Mijoz "ha" desa)** | **Mavjud (`FloorRules.java`)** | Mavjud emas | Mavjud emas | **Mavjud (`backchannel_probability`)** |
| **Soxta Barge-in'dan Keyin Tiklanish** | Rejalashtirilgan | Mavjud emas | Mavjud emas | **Mavjud (`resume_false_interruption`)** |
| **Uzoq Amallarda Filler Aytish** | Statik `PreToolPhrases` | Kontekstli xabarlar | Mavjud emas | **Dinamik `_FillerScheduler`** |
| **Foydalanuvchi Jim Qolganda (User Idle)** | Scenario Timeout orqali | **3-darajali `UserIdleController`** | Timeout orqali | Agent Session State orqali |

---

## 7. HAR BIR PLATFORMANING ENG KUCHLI "SUPER-KUCHI" (Superpower)

1. **`robot-call-v2` Super-kuchi:**
   * **Real Telefoniya va O'zbek tili lingvistikasi:** 8 kHz G.711 oqimini to'g'ridan-to'g'ri Netty/Asterisk orqali qabul qilib, Direct Memory'da GC-pause'larsiz ishlashi hamda O'zbek tili morfologiyasiga (`TranscriptTurnCues`) va madaniyatiga (`FloorRules`) moslashtirilgan yagona platforma.
2. **`Pipecat` Super-kuchi:**
   * **Silero VAD Reseti va LLM Classifier:** Har 2 soniyada LSTM xotirasini tozalab turishi modelni xatolikdan asraydi, `ClassifierUserTurnStopStrategy` esa matn tugallanganligini arzon 1-tokenlik model bilan aniq tekshiradi.
3. **`TEN Framework` Super-kuchi:**
   * **TEN VAD ning Zero-Tail Tezligi:** Inson gapdan to'xtashi bilan kechikishsiz sukunatni qayd etishi sababli dialogda insoniy chaqqonlik (snappiness) beradi.
4. **`LiveKit Agents` Super-kuchi:**
   * **`DynamicEndpointing` va `resume_false_interruption`:** Insonning shaxsiy gapirish tezligini o'rganib moslashishi va tasodifiy yo'taldan keyin gapini davom ettirib ketishi botni sun'iy intellektdan ko'ra tirik insonga yaqinlashtiradi.

---

## 8. BIZNING LOYIHA (`robot-call-v2`) UCHUN AMALIY TAVSIYALAR

Tahlil natijasida `robot-call-v2` ga joriy qilishimiz kerak bo'lgan eng muhim 3 ta vazifa:

1. **Pipecat tajribasidan: Sukunatda VAD LSTM Resetini qo'shish:**
   `VadStream.java` ichida agar `prob < listeningThreshold` ketma-ket 60 ta oyna (taxminan 2 soniya) davom etsa, `state` matritsasini nolga tenglash (`Arrays.fill(state, 0.0f)`). Bu uzoq kutilgan qo'ng'iroqlarda Silero VAD charchashining oldini oladi.
2. **LiveKit tajribasidan: Dinamik Endpointing (`ExpFilter`):**
   `SpeechGate.java` ga oddiy siljuvchi o'rtacha filtrni kiritish: agar mijoz tez gapirsa `silenceTimeoutMs` ni 350 ms ga tushirish, agar mijoz botni erta bo'lib gapini davom ettirsa, `silenceTimeoutMs` ni 700 ms gacha oshirish.
3. **LiveKit tajribasidan: `resume_false_interruption`:**
   Agar mijoz bot gapirayotganda kutilmaganda 300 ms dan qisqa tovush chiqarsa va keyin 1.5 soniya jim tursa, bot to'xtab qolgan audio oqimini o'sha jumladan boshlab qayta ijro etishi kerak.
