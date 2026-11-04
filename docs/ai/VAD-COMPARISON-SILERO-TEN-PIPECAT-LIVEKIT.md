# VAD (Voice Activity Detection) Tahlili: Robot-Call-v2, TEN Framework, Pipecat va LiveKit Taqqoslashi

> **Hujjat maqsadi:** Ushbu hujjat `robot-call-v2` loyihasidagi **Silero VAD** (va uning muqobili **TEN VAD**) arxitekturasining qanday ishlashini to'liq ochib beradi hamda uni sanoatdagi uchta yirik ovozli freymvork — **Pipecat**, **TEN Framework** va **LiveKit Agents** dagi VAD mexanizmlari bilan chuqur taqqoslaydi.

---

## 1. VAD NIMA VA OVOZLI AGENTDA UNING O'RNI

VAD (**Voice Activity Detection** — Ovoz faolligini aniqlash) audio oqimida inson nutqi bormi yoki oddiy sukunat/shovqinmi degan savolga har 10–32 millisekundda javob beruvchi neyrotarmoq modelidir.

Zamonaviy ovozli agentda VAD quyidagi 4 ta hal qiluvchi funksiyani bajaradi:
1. **Barge-in (Gapni bo'lish):** Bot gapirayotgan paytda mijoz so'z boshlasa, bot o'z nutqini darhol to'xtatishi kerak.
2. **STT Gating (Xarajat va resursni tejash):** Mijoz jim turganda bo'sh audioni Google/Deepgram STT ga yuborib pul sarflamaslik uchun audio oqimi VAD orqali ochiladi va yopiladi.
3. **Turn-taking (Navbat almashinuvi):** Mijoz gapini qachon tugatganini aniqlash (VAD sukunatni qayd etgach, `SmartTurn` yoki `NAMO` ga navbat beriladi).
4. **AMD (Answering Machine Detection):** Qo'ng'iroq ko'tarilganda odam javob berdimi yoki mobil operatorning pochtasi/avtojavobgichimi — VAD nutq uzluksizligiga qarab ajratadi.

---

## 2. `robot-call-v2` DA VAD QANDAY ISHLAYDI?

Loyihamizda VAD implementatsiyasi `src/main/java/uz/murodjon/robotcallv2/agent/vad/` paketida joylashgan bo'lib, telefoniya (SIP/RTP 8 kHz) uchun maxsus optimallashtirilgan.

```
Incoming RTP (G.711 / PCM 8kHz)
            │
            ▼
    [ VadStream.java ] ──(Har 32ms: 256 samples)──▶ [ SileroVad.java (ONNX Runtime) ]
            │                                             │ (State: [2, 1, 128])
            │ ◀── Nutq ehtimolligi (0.0 .. 1.0) ──────────┘
            │
            ├─▶ 1. Dinamik Fon Shovqini Moslashuvi (Noise Floor Tracking)
            ├─▶ 2. Akustik Aks-Sado To'sig'i (Acoustic Echo Gate)
            ├─▶ 3. Dual-Threshold Qarori (Barge-in vs Gating)
            │
            ├──▶ Barge-in bo'lsa ──────────▶ Bot audiosini to'xtatish (Netty/Asterisk)
            ├──▶ Nutq ochilsa ─────────────▶ SpeechGate (STT ga oqimni uzatish)
            ├──▶ Sukunat bo'lsa ───────────▶ onSpeechEnd (SmartTurn / DialogEngine)
            └──▶ Uzluksiz 3.5s nutq bo'lsa ──▶ AMD (Avtojavobgich deb belgilash)
```

### 2.1. Neyrotarmoq Dvigateli: `SileroVad.java`
* **Model:** Silero VAD **v6.2.2** (`models/silero_vad.onnx`).
* **Telefon formati:** `8000 Hz` chastota, bitta oyna — **256 ta namuna (samples)** = **32 ms**.
* **Zero-GC va Xotira unumdorligi (`VadScratch`):**
  Har bir qo'ng'iroq uchun alohida Java heap obyektlari yaratib, Garbage Collector'ni qiynashning oldini olish uchun `ThreadLocal<VadScratch>` ishlatiladi:
  ```java
  ByteBuffer.allocateDirect(count * Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
  ```
  RTP worker threadlari bevosita off-heap C++ xotirasidagi buferlardan foydalanadi. Natijada yuzlab parallel qo'ng'iroqlarda ham xotira tozalash to'xtalishlari (GC pause) bo'lmaydi.
* **Kontekst ulanishi (Context Prepending):**
  Silero v5 va v6 arxitekturasi har bir yangi oynaning boshiga oldingi oynaning oxirgi **32 ta namunasini (16kHz da 64)** ulab berishni talab qiladi:
  ```java
  input.put(context).put(window).flip();
  ```
  Agar bu kontekst berilmasa, modelning birinchi konvolyutsiyasi noldan sakrab tushadi va bitta gapda ehtimollik `0.94 -> 0.10 -> 0.71` bo'lib tebranadi (thrashing). Kontekst bilan esa ehtimollik barqaror `0.97–0.99` bo'lib turadi.
* **Stateless Session + Stateful Stream:**
  Bitta `OrtSession` (1 intra/inter thread) barcha qo'ng'iroqlarga umumiy xizmat qiladi. LSTM holati (`state [2, 1, 128]`) esa har bir qo'ng'iroqning o'zida saqlanadi va modelga argument sifatida kirib-chiqadi.

### 2.2. Oqim Mantiqi va Filtrlash: `VadStream.java`
Shunchaki model ehtimolligiga ishonib bo'lmaydi — telefonda ko'cha shovqini, nafas va aks-sado bo'ladi. `VadStream` 3 ta murakkab DSP filtrni qo'llaydi:

1. **Dinamik Fon Shovqini Moslashuvi (Noise Floor Tracking):**
   Model sukunat deb baholagan oynalardan kelib chiqib, qo'ng'iroqning fon shovqini o'lchanadi:
   ```java
   if (prob < listeningThreshold) {
       double weight = rms < noiseFloorRms ? FLOOR_FALL : FLOOR_RISE;
       noiseFloorRms = noiseFloorRms + weight * (rms - noiseFloorRms);
   }
   double bargeInFloor = Math.min(Math.max(minEnergyRms, noiseFloorRms * noiseFloorMargin), minEnergyRms * MAX_FLOOR_MULTIPLE);
   ```
   Shovqinli ko'chada yurgan mijozda fon chegarasi avtomatik ko'tariladi, aks holda mashinalar tovushi botni to'xtataveradi.
2. **Akustik Aks-Sado To'sig'i (Acoustic Echo Gate):**
   Bot baland ovozda gapirayotganda, telefon dinamigidan chiqqan ovoz mikrofonga qaytib kiradi (hybrid echo). Agar mikrofondagi audio quvvati botning ovoz quvvatining ma'lum nisbatidan (`echoGateRatio = 0.2`) past bo'lsa, bu mijoz emas — **botning o'z aks-sadosi** deb hisoblanadi va barge-in bloklanadi.
3. **Dual-Threshold Tizimi (Ikki pog'onali chegara):**
   * **Barge-in chegarasi (`threshold = 0.4`):** Bot gapirayotgan paytda qo'llaniladi. Qat'iy quvvat va `minSpeechMs = 400 ms` (kamida 12 ta ketma-ket nutq oynasi) talab qilinadi. Yo'tal yoki qisqa nafas botni to'xtatmaydi.
   * **Listening / Gating chegarasi (`listeningThreshold = 0.3`):** Bot jim bo'lib, mijozni eshitayotgan paytda qo'llaniladi. Pastroq quvvat va pastroq ehtimollik qabul qilinadi, natijada pichirlab yoki sekin gapirgan mijozning gapi ham STT ga tushadi.

---

## 3. TEN FRAMEWORK VAD TAHLILI (`IdeaProjects/ten-vad` va `ten_vad_python`)

Agora jamoasi tomonidan yaratilgan **TEN VAD** — Silero VAD ga to'g'ridan-to'g'ri raqobatchi va muqobil sifatida yaratilgan maxsus neyrotarmoq modelidir.

### 3.1. TEN VAD ning Silero VAD dan Ustunliklari:
1. **Tezkor Speech-to-Silence O'tishi (Zero-Tail Latency):**
   Silero VAD ning eng mashhur muammosi — inson gapirishdan to'xtaganidan keyin ham LSTM xotirasi hisobiga **150–300 ms davomida "nutq bor" deb hisoblab turadi** (kechikkan dum). TEN VAD esa nutq to'xtagan lahzani deyarli darhol (bir necha millisekundda) aniqlaydi. Bu dialogda botning javob berish tezligini oshiradi.
2. **Kamroq Xotira va Hisoblash Resursi:**
   TEN VAD modeli Silero'ga qaraganda yengilroq bo'lib, mobil qurilmalar va yuqori zichlikdagi serverlarda CPU yuklamasini kamaytiradi.
3. **Turli Oyna O'lchamlari (Hop Size):**
   16 kHz da 10 ms, 16 ms, 20 ms, 32 ms oynalarni qo'llab-quvvatlaydi.

### 3.2. `robot-call-v2` dagi TEN VAD Integratsiyasi:
Loyiha arxitekturamizda `TenVad.java` va `TenVadLibrary.java` allaqachon mavjud!
`speech.yml` da `voice-agent.ten-vad.enabled: true` qilinganda, tizim Silero o'rniga C++ JNI orqali TEN VAD kutubxonasini ishga tushiradi (`AudioPipelineAssembler.java:391`).

---

## 4. PIPECAT SILERO VAD TAHLILI (`IdeaProjects/pipecat`)

Pipecat `src/pipecat/audio/vad/silero.py` faylida Python va ONNX Runtime asosida Silero VAD ni amalga oshirgan (`SileroVADAnalyzer`).

### 4.1. Pipecat'dagi Eng Muhim Yechim: Davriy Model Reset (`_maybe_reset_model`)
Silero VAD ning LSTM holati uzoq sukunatda (ayniqsa 8 kHz telefonda yoki orqa fondagi musiqada) "charchaydi" va shovqinni o'ziga yig'ib olib, keyingi haqiqiy nutqni sezmay qolishi mumkin.

Pipecat buni hal qilish uchun ajoyib algoritm yozgan:
```python
# Pipecat har 2 soniya toza sukunatda modelning LSTM holatini tozalaydi:
if (self._windows_since_reset * self.num_frames_required() >= _MODEL_RESET_INTERVAL_SECS * self.sample_rate
    and self._vad_state == VADState.QUIET
    and self._low_confidence_windows >= _MODEL_RESET_LOW_CONFIDENCE_WINDOWS):
    self._model.reset_states()
    self._windows_since_reset = 0
```
* **Qoida:** Agar sukunat davom etayotgan bo'lsa va ishonchlilik past bo'lsa — har 2 soniyada `reset_states()` qilinadi.
* **Xavfsizlik:** Agar inson gapirayotgan bo'lsa, hech qachon reset qilinmaydi (aks holda nutq uzilib ketadi).

### 4.2. Pipecat Reaktiv Holat Mashinasi:
Pipecat VAD holatini 4 bosqichli holat mashinasi (`VADState`) orqali boshqaradi:
`QUIET` ──▶ `STARTING` (ovoz boshlandi) ──▶ `SPEAKING` ──▶ `STOPPING` ──▶ `QUIET`.
Bu holatlar asosida butun pipeline'ga `UserStartedSpeakingFrame` va `UserStoppedSpeakingFrame` uzatiladi.

---

## 5. LIVEKIT SILERO VAD TAHLILI (`livekit-plugins-silero`)

LiveKit Agents platformasi Silero VAD ni Python va Rust orqali yuqori darajadagi asinxron iterator sifatida taqdim etadi.

### 5.1. LiveKit'ning Kuchli Tomoni: Audio Pre-Padding Buferi
Ko'p VAD tizimlarida inson gap boshlaganda, VAD uni tasdiqlaguncha (masalan, 50–100 ms) boshlang'ich tovushlar (unli yoki portlovchi "P", "T", "B" harflari) kesilib qoladi va STT ga bormaydi.
LiveKit buni **aylanma bufer (ring buffer)** bilan yechgan:
* `prefix_padding_duration`: Nutq boshlanishidan oldingi **200–300 ms audio doimiy buferda saqlanadi**.
* VAD nutqni tasdiqlashi bilanoq, STT ga nafaqat joriy oyna, balki shu 300 ms lik o'tmish audiosi ham qo'shib uzatiladi. Natijada gapning birinchi harfi ham yo'qolmaydi.

### 5.2. LiveKit Asosiy Sozlamalari:
* `min_speech_duration`: Nutq deb hisoblash uchun minimal vaqt (standart 50 ms).
* `min_silence_duration`: Gap tugadi deb hisoblash uchun sukunat vaqti (standart 500 ms).
* `activation_threshold`: Nutq ehtimolligi chegarasi (0.5).

---

## 6. KATTA TAQQOSLASH MATRITSASI

| Parametr / Imkoniyat | `robot-call-v2` (Bizda) | TEN Framework (`ten-vad`) | Pipecat (`SileroVADAnalyzer`) | LiveKit (`livekit-silero`) |
|---|---|---|---|---|
| **Asosiy Dvigatel** | Silero v6.2.2 ONNX (va TEN VAD muqobili) | TEN VAD (Agora Custom NN) | Silero ONNX (Python) | Silero ONNX (Rust/Python) |
| **Dasturlash tili** | Java 21 + ONNX Runtime (JNI) | C++ Native Core + Python | Python (`asyncio` + `numpy`) | Python / Rust Core |
| **Xotira boshqaruvi** | **Direct Off-Heap Buffers** (Zero-GC, `ThreadLocal`) | C++ Zero-Copy Ring Buffer | Python / Numpy heap obyektlari | Rust memory management |
| **Telefoniya (8 kHz) optimalligi** | **A'lo:** 8 kHz uchun maxsus 256 ta namuna (32 ms) | Yaxshi (16 kHz ga ko'proq yo'naltirilgan) | Yaxshi (8 kHz va 16 kHz) | O'rtacha (asosan WebRTC 16-48 kHz) |
| **Aks-sado (Echo) Filtrlash** | **Mavjud:** `echoGateRatio` (Bot ovozidan qaytgan signalni bloklaydi) | WebRTC AEC / Agora Engine | Tashqi AEC ga tayanadi | LiveKit WebRTC AEC |
| **Dinamik Shovqin Moslashuvi** | **Mavjud:** `noiseFloorRms` faqat sukunatda o'lchanadi | Model ichki shovqin filtri | O'rnatilmagan (statik chegara) | O'rnatilmagan |
| **Dual-Threshold Tizimi** | **Mavjud:** Barge-in (0.4) vs Gating (0.3) | Yagona chegara (0.5) | Yagona VADParams | Yagona activation_threshold |
| **LSTM Xotirasini Tozalash** | Doimiy (stream yopilguncha saqlanadi) | Model arxitekturasi tez tiklanadi | **Mavjud:** Har 2s toza sukunatda model reset bo'ladi | Oqim bo'yicha reset |
| **Pre-padding (Bosh tovushlar)** | `SpeechGate` da pre-buffer orqali | Hop-size orqali | Frame queue orqali | **Mukammal:** `prefix_padding` 300 ms |

---

## 7. XULOSA VA BIZ UCHUN ILG'OR TAVSIYALAR

`robot-call-v2` dagi VAD arxitekturasi real telefoniya (Asterisk/SIP) va yuqori yuklamali Java muhiti uchun **nihoyatda puxta va professional** yozilgan. Ayniqsa `ThreadLocal` direct buferlar, dinamik fon shovqini o'lchagich va akustik aks-sado filtri — boshqa ko'plab ochiq kodli loyihalarda umuman uchramaydigan darajada yuqori sifatli.

Shu bilan birga, **Pipecat** va **TEN** tajribasidan quyidagi 2 ta yaxshilanishni o'zlashtirishimiz mumkin:

1. **Pipecat tajribasidan — Sukunatdagi LSTM Reset:**
   Uzoq davom etgan qo'ng'iroqlarda (masalan, mijoz 20 soniya kutilganda yoki musiqa chalinganda) Silero VAD holati shovqinga to'yinib qolmasligi uchun, `VadStream` ichida sukunat davom etganda har 2–3 soniyada model holatini xavfsiz tozalash (`reset_states`) tavsiya etiladi.
2. **TEN VAD ni faolroq sinash:**
   Loyiha ichida `TenVad.java` borligi katta boylik. TEN VAD ning nutqdan sukunatga o'tish kechikishi (zero-tail) Silero'dan ancha tez bo'lgani sababli, turn-taking va botning javob berish tezligini oshirish uchun uni asosiy rejimga chiqarishni sinab ko'rish mumkin.
