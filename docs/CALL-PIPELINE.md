# Qo'ng'iroq jarayoni: telefoniya (Asterisk / LiveKit) va dialog (Cascade / Realtime)

Bitta qo'ng'iroq ikki **mustaqil** tanlovdan iborat:

| O'q | Variantlar | Qayerda tanlanadi |
|---|---|---|
| **Telefoniya dvigateli** — audio simdan app'gacha qanday keladi | `ASTERISK` (default) · `LIVEKIT` | Kompaniya: `PUT /api/companies/{id}/telephony-engine` (`shared/dialog/TelephonyEngine`) |
| **Dialog pipeline** — kim eshitadi, kim o'ylaydi, kim gapiradi | `CASCADE` (default) · `REALTIME` · `HALF_CASCADE` | AI Agent: `speechEngine.mode`; A/B uchun `CampaignVariant.pipelineMode` override (`DialogStarter#resolveMode`) |

Ikkalasi aralashadi: LiveKit + Cascade, Asterisk + Realtime — hammasi ishlaydi. Sababi:
LiveKit ham oxir-oqibat app'ga **xuddi Asterisk kabi G.711 RTP** beradi, shuning uchun app
ichidagi butun pipeline (VAD, STT, LLM, TTS, yozuv) dvigatelga bog'liq emas.

```
            TELEFONIYA (audio tashuvchi)                     │        DIALOG (app ichida)
                                                             │
Telefon ─SIP/RTP─▶ Asterisk ─externalMedia (G.711 RTP)───┐   │   ┌─ CASCADE:  VAD → STT → LLM → TTS
                                                         ├──▶│ RtpEndpoint (Netty, 20 ms) ─┤
Telefon ─SIP/RTP─▶ livekit-sip ─▶ LiveKit xona ─WebRTC/  │   │   └─ REALTIME: RealtimeAudioBridge ⇄ Gemini Live / OpenAI Realtime …
                        Opus─▶ livekit-bridge (Go) ──────┘   │      HALF_CASCADE: VAD → STT → (matn) → realtime engine → ovoz
```

---

## 1. Telefoniya: Asterisk

**Chiquvchi qo'ng'iroq bosqichlari** (`OutboundDialService` → `CallSetupService#setupMedia`):

1. Dialer tick (`DIALER_TICK_SECONDS`, default 5 s) target oladi, trunk CPS limitiga qaraydi
   (`TrunkCallPacer`, default 1 qo'ng'iroq/s), ARI orqali `originate` qiladi.
2. Asterisk trunk orqali SIP INVITE yuboradi, telefon jiringlaydi (`answer-timeout-sec: 30`).
3. Go'shak ko'tarilgach `StasisStart` keladi. **Media ochish tartibi** (shu tartib muhim):
   - app RTP portini band qiladi (`20000–20500`), `RtpEndpoint` + yozuv tayyor;
   - `externalMedia` kanali yaratiladi → app'ning `RTP_LOCAL_IP:port` ga G.711 yuboradi;
   - `mixing` bridge, unga avval externalMedia, **keyin** `answer`, keyin mijoz kanali
     (javob berilmagan kanal bridge'ga qo'shilsa avtomatik answer bo'ladi — shuning uchun tartib);
   - `dialogStarter.resolveMode(...)` — Cascade yoki Realtime tanlanadi va mos listener zanjiri
     yig'iladi (`AudioPipelineAssembler`);
   - bo'lsa, `preConversationAudio` (yuklangan WAV) o'ynaladi, keyin dialog boshlanadi.
4. Tugash: `StasisEnd` → `CallTeardownService` (bridge, externalMedia yopiladi, natija yoziladi).

**Media yo'li:** telefon ↔ trunk ↔ Asterisk ↔ (UDP, G.711, 20 ms paket) ↔ app.
Asterisk audioni qayta kodlamaydi (ulaw → ulaw), jitter buffer default **o'chiq**
(`ASTERISK_JITTER_BUFFER` bo'sh).

## 2. Telefoniya: LiveKit

Batafsil sozlash: [LIVEKIT.md](LIVEKIT.md). Bu yerda faqat oqim va farq.

1. `LiveKitGateway#originate` → `CreateRoom livekit-<callId>` → `CreateSIPParticipant`
   (`waitUntilAnswered`, `LIVEKIT_ANSWER_TIMEOUT_SEC: 30`) — call thread go'shak ko'tarilishini kutadi.
2. Javob berilsa: app RTP port oladi, `POST livekit-bridge:8090/calls` — Go bridge xonaga
   `robot-call-agent` bo'lib kiradi.
3. Bridge telefon tomonining **Opus** audiosini 8 kHz PCM ga dekodlaydi → 160 namunali (20 ms)
   G.711 paket qilib app'ga RTP yuboradi. Teskarisi: app G.711 → bridge Opus → xona.
4. Shundan keyin app ichida hammasi Asterisk'dagidek (`LiveKitCallService` ham
   `dialogStarter.resolveMode` ni chaqiradi).
5. Tugash: `participant_left` webhook → teardown; agent tugatsa `DeleteRoom`.

**Asterisk'dan farqi (media nuqtai nazaridan):** yo'lda qo'shimcha ikki bo'g'in bor —
SIP↔WebRTC (livekit-sip) va WebRTC↔RTP (bridge), ya'ni **ikki marta transkodlash**
(G.711 → Opus → G.711) va WebRTC jitter buffer. Funksional cheklovlari LIVEKIT.md
"hozircha yo'q" bo'limida.

## 3. Dialog: CASCADE (bizning STT + LLM + TTS)

Default rejim. Har bir gap aytilishidan oldin bizning nazoratimizdan o'tadi
(`FactGuard`, guardrail'lar) — qarz summasi/muddati kabi faktlar uchun shu sabab default.

Bitta turn (`DialogEngine` / `TurnRunner` / `SpeechOutput`):

```
mijoz gapirib bo'ldi
  │  ① EOU — VAD jimlikni kutadi (SpeechGate)            ← eng katta bo'lak
  ▼
gate yopildi → STT'ga "gap tugadi"
  │  ② stt_final — STT final matn beradi
  ▼
LLM so'rovi (streaming)
  │  ③ llm_ttft — birinchi token
  ▼
birinchi jumla tayyor (SentenceSplitter) → TTS
  │  ④ tts_ttfb — birinchi PCM
  ▼
RtpEndpoint navbatiga → simga (20 ms paketlar)          ⑤ first_audio = ①…④ yig'indisi
```

Tezlashtirgichlar (hammasi default yoqilgan):

| Mexanizm | Nima qiladi | Sozlama |
|---|---|---|
| Speculation | STT interim'idan javobni EOU pauzasi **ichida** yozib qo'yadi; final mos kelsa `llm_ttft ≈ 0` | `DIALOG_PREEMPTIVE`, `DIALOG_SPECULATIVE_TURN_CUE` |
| Preemptive TTS | taxminiy javobning sintezini ham oldindan boshlaydi; tegsa `tts_ttfb ≈ 0` | `DIALOG_PREEMPTIVE_TTS` |
| Dinamik endpointing | qisqa javob ("ha", "yo'q") tez yopiladi, uzun gap kutiladi | `STT_ENDPOINTING_*` |
| Final grace | EOU'dan keyin final kechiksa, oxirgi interim final deb olinadi | `STT_ENDPOINTING_FINAL_GRACE_MS=250` |
| Filler | javob kechiksa "hozir…" kabi to'ldiruvchi | `DIALOG_FILLER_DELAY_MS=1400` |
| Fast path | ma'lum savollarga LLM'siz tayyor javob (`TurnShortcuts`, KB item) | — |

## 4. Dialog: REALTIME va HALF_CASCADE (speech-to-speech)

**REALTIME** (`RealtimeDialogEngine`): app audioni to'g'ridan-to'g'ri engine'ga beradi
(`RealtimeAudioBridge` — faqat resampling va echo gate, VAD gate yo'q). Engine o'zi eshitadi,
o'zi "gap tugadi" deb qaror qiladi, o'ylaydi va **o'z ovozida** gapiradi. Salomlashish va
disclosure ham engine ovozida.

Provayderlar (`agent/realtime`): Gemini Live (`gemini-3.8-live`), OpenAI Realtime
(`gpt-realtime-2.1`, `semantic_vad`), Qwen Omni, Moshi, Pipecat.

```
mijoz gapirib bo'ldi
  │  engine'ning o'z endpointeri (Gemini: silence-duration-ms=500, prefix-padding 100)
  │  + engine ichida tanish + o'ylash + ovoz sintezi — bitta qora quti
  ▼
engine audiosining birinchi bo'lagi → resample → RtpEndpoint → simga
```

**HALF_CASCADE**: mijozni **bizning STT** eshitadi (§3 dagi ①②), finalni matn qilib engine'ga
beradi, engine o'ylaydi va o'z ovozida gapiradi. Sababi: engine'ning o'z tanuvchisi o'zbekcha
gapni rus/turk tili deb yozib, noto'g'ri javob bergan.

**Muhim farqlar:**

| | CASCADE | REALTIME | HALF_CASCADE |
|---|---|---|---|
| Kim eshitadi | bizning STT | engine | bizning STT |
| Kim gapiradi | bizning TTS (agent ovozi) | engine ovozi | engine ovozi |
| Gap aytilishidan **oldin** tekshiruv | ✅ (FactGuard) | ❌ faqat keyin audit | ❌ faqat keyin audit |
| Kechikish bosqichlarini alohida ko'rish | ✅ eou/stt/llm/tts | ❌ bitta raqam | qisman |
| Engine yiqilsa | — | qo'ng'iroq **Cascade'da davom etadi** (boshqa ovozda, uzr bilan) | xuddi shunday |

Engine umuman ochilmasa yoki provayder sozlanmagan bo'lsa `resolveMode` jimgina CASCADE'ga
tushadi (`voice_realtime_fallbacks_total` metrikasi, logda `no engine is available — running cascade`).
Qo'ng'iroq o'rtasida yiqilsa — `CallSetupService#continueOnCascade`.

---

## 5. Kechikish: qayerda qancha vaqt ketadi

> **Belgi:** 📏 — loyihada o'lchangan yoki koddagi sozlama qiymati; 🔶 — taxmin (tipik qiymat,
> bizda o'lchanmagan). 🔶 raqamlarni ishonishdan oldin §6 dagi metrikalar bilan tekshiring.

### 5.1. Telefoniya qatlami (bir tomonga, har bir audio paket uchun)

| Bo'g'in | Asterisk | LiveKit |
|---|---|---|
| Paketlash (RTP frame) | 📏 20 ms | 📏 20 ms (bridge `frameSamples=160`) |
| Trunk ↔ server tarmog'i | 🔶 10–50 ms (provayderga bog'liq) | 🔶 bir xil |
| Asterisk ichida (ulaw passthrough, jitter buffer o'chiq) | 🔶 ~1–5 ms | — |
| livekit-sip (SIP/RTP ↔ WebRTC) + Opus kodlash | — | 🔶 ~5–20 ms |
| WebRTC jitter buffer (bridge tomonda) | — | 🔶 ~20–60 ms (tarmoqqa qarab moslashadi) |
| Bridge: Opus dekod → G.711 → RTP | — | 🔶 ~1–5 ms |
| **Jami qo'shimcha (bir tomonga)** | **🔶 ~30–70 ms** | **🔶 ~60–150 ms** |

Xulosa: suhbatdagi mijoz kutishiga telefoniya qatlami **ikki marta** (eshitish + gapirish)
qo'shiladi, lekin bu baribir dialog qatlamidan bir tartib kichik. LiveKit bizning
arxitekturada **tezroq emas** — u Asterisk o'rnini emas, ustiga ikki bo'g'in qo'shadi
(bridge app'ga yana G.711 RTP beradi). LiveKit'ning foydasi kechikishda emas: brauzer/WebRTC
operatorlar, xona modeli, masshtablash.

**Qo'ng'iroq boshlanishi** (go'shak ko'tarilgandan birinchi ovozgacha):

| Bosqich | Asterisk | LiveKit |
|---|---|---|
| Answer → media tayyor | 🔶 ~50–150 ms (ARI: externalMedia + bridge + answer, hammasi lokal HTTP) | 🔶 ~200–600 ms (`waitUntilAnswered` qaytishi + `POST /calls` + bridge xonaga ulanishi, WebRTC ICE/DTLS) |
| Pre-conversation audio | fayl uzunligi | fayl uzunligi |
| Birinchi gap (Cascade) | 🔶 TTS ttfb ~200–500 ms (`TtsWarmup` keshi bo'lsa ~0) | xuddi shunday |
| Birinchi gap (Realtime) | 📏 `voice_realtime_first_audio_latency` — engine sessiya + salom; 🔶 ~0.8–2 s | xuddi shunday |

### 5.2. Dialog qatlami (mijoz gapirib bo'lgandan bot ovozigacha — **turnaround**)

Byudjet: 📏 **p95 < 1000 ms** (`AlertingService` har 5 daqiqada tekshiradi).

**CASCADE**, default sozlamalar (Yandex STT, VAD gate yoqilgan):

| Bosqich | Qiymat | Manba |
|---|---|---|
| ① EOU (jimlik kutish) — qisqa gap (< 1.5 s) | 📏 500 ms | `STT_ENDPOINTING_SHORT_SILENCE_MS` |
| ① EOU — uzun gap | 📏 800–1200 ms (dinamik, mijoz pauzasiga moslashadi) | `STT_VAD_POST_ROLL_MS`, `…DYNAMIC_MIN_POST_ROLL_MS` |
| ② STT final (EOU'dan keyin) | 📏 SpeechKit 500–1100 ms, lekin 250 ms grace'dan keyin interim olinadi → amalda **≤ 250 ms** | 2026-09-05 o'lchovi, `FINAL_GRACE_MS` |
| ③ LLM birinchi token | 🔶 300–1500 ms (modelga bog'liq); speculation **hit** bo'lsa 📏 ~0 | `llm_ttft` |
| ④ TTS birinchi PCM | 🔶 200–500 ms; preemptive hit bo'lsa 📏 ~0 | `tts_ttfb` |
| Telefoniya (ikki tomonga) | 🔶 Asterisk 60–140 ms / LiveKit 120–300 ms | §5.1 |

Natija:

- **Speculation hit** (javob EOU pauzasi ichida yozilgan): ≈ EOU + ~250 ms + TTS (yoki 0) →
  🔶 **~0.8–1.5 s**
- **Miss / none**: EOU + STT + LLM + TTS ketma-ket → 📏 RUN.md'dagi misol `first_audio=3250ms`,
  yomon holatda **4–6 s**.

> Muhim: `voice_turnaround_latency` soati **final transkriptdan** boshlanadi — ① EOU jimligi
> unga kirmaydi. Mijoz his qiladigan to'liq kutish — `turn latency` log qatoridagi `first_audio`.

**REALTIME**: hamma narsa engine ichida, bosqichlarga bo'lib bo'lmaydi.

| Bosqich | Qiymat |
|---|---|
| Engine endpointeri (Gemini Live) | 📏 sozlama `silence-duration-ms=500` |
| Engine ichida tanish + o'ylash + sintez | 🔶 ~300–800 ms (provayder, model, `thinking-level` ga bog'liq) |
| Resample + telefoniya | 🔶 §5.1 dagi kabi |
| **Jami** | 🔶 **~0.8–1.5 s**, 📏 `voice_realtime_turnaround_latency{engine=…}` |
| Tool chaqiruvi bo'lsa | + 📏 `voice_realtime_tool_latency` (timeout 15 s) — mijoz jimlik eshitadi |

Realtime turnaround **bizning VAD** bilan o'lchanadi (engine endpointeri emas), shuning uchun
Cascade bilan bir xil soatda solishtiriladi.

**HALF_CASCADE**: ①② bizda (Cascade'dagidek) + engine'ning matndan javob va ovoz chiqarishi
(🔶 ~300–800 ms). Odatda REALTIME'dan sekinroq, Cascade speculation-hit'dan esa sekinroq yoki teng.

### 5.3. Qisqa solishtiruv

| | Asterisk + Cascade | LiveKit + Cascade | Asterisk + Realtime | LiveKit + Realtime |
|---|---|---|---|---|
| Turnaround (hit / tipik) | 🔶 ~0.8–1.5 s | 🔶 +~0.1 s | 🔶 ~0.8–1.5 s | 🔶 +~0.1 s |
| Turnaround (miss) | 📏 3–6 s | 📏 3–6 s | — (speculation yo'q) | — |
| Javob nazorati (FactGuard) | ✅ | ✅ | ❌ | ❌ |
| O'zbek tilini tanish | bizning STT | bizning STT | engine (xato qilgan) | engine |

Asosiy xulosa: **kechikishni telefoniya emas, dialog qatlami belgilaydi** — eng katta bo'laklar
EOU jimligi va speculation miss'dagi LLM kutishi.

---

## 6. O'lchash: qaysi raqamga qarash kerak

Har turnda log qatori (`logs/app.log`):

```
[ch-1] turn latency: eou=1180ms stt_final=240ms llm_ttft=1450ms tts_ttfb=380ms first_audio=3250ms
[ch-1] speculation: hit after 7 interim(s) — the reply was already written
```

Prometheus (`/actuator/prometheus`):

| Metrika | Nima |
|---|---|
| `voice_turnaround_latency` | Cascade: final → birinchi ovoz |
| `voice_realtime_turnaround_latency{engine}` | Realtime: mijoz to'xtadi (bizning VAD) → engine ovozi |
| `voice_realtime_first_audio_latency{engine}` | Realtime: salomlashish so'raldi → birinchi ovoz |
| `voice_realtime_tool_latency{tool}` | Realtime: tool chaqiruvi ushlagan vaqt |
| `voice_rtp_jitter`, `voice_rtp_loss` | Telefoniya qatlami sifati (Asterisk va LiveKit uchun bir xil — ikkalasi ham app'ga RTP) |
| `voice_realtime_fallbacks_total` | Realtime ochilmay Cascade'ga tushgan qo'ng'iroqlar |

Audio Inspector (`TurnTiming`) — har turn bosqichlarini yozuv ustiga qo'yib ko'rsatadi.

**Asterisk va LiveKit'ni haqiqiy solishtirish:** bitta agent, bitta raqamga ikki kompaniya
(biri `ASTERISK`, biri `LIVEKIT`) orqali ~20 tadan qo'ng'iroq qilib, `first_audio` median/p95
va `voice_rtp_jitter` solishtiriladi. §5.1 dagi 🔶 raqamlar shu o'lchov bilan almashtirilishi kerak.

## 7. Kod xaritasi

| Joy | Vazifa |
|---|---|
| `agent/ari/CallSetupService` | Asterisk: StasisStart → media → answer → dialog; realtime yiqilsa `continueOnCascade` |
| `agent/ari/LiveKitCallService` | LiveKit: xona → SIP participant → bridge → dialog |
| `livekit-bridge/call.go` | WebRTC/Opus ⇄ G.711 RTP (20 ms) |
| `agent/ari/AudioPipelineAssembler` | listener zanjiri: VAD + STT (cascade) yoki `RealtimeAudioBridge` |
| `agent/dialog/DialogStarter#resolveMode` | override → agent → CASCADE; engine yo'q bo'lsa CASCADE |
| `agent/dialog/DialogEngine`, `TurnRunner`, `SpeechOutput`, `SpeculationManager` | Cascade turn |
| `agent/dialog/RealtimeDialogEngine` | Realtime / Half-cascade |
| `agent/metrics/TurnLatency`, `TurnTiming`, `RealtimeMetrics` | kechikish o'lchovlari |
| `config/speech.yml`, `config/dialog.yml`, `config/telephony.yml` | yuqoridagi barcha sozlamalar |
