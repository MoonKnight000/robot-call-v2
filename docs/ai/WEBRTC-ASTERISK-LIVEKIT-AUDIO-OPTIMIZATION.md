# WebRTC, Asterisk va LiveKit: audio sifati va transport kechikishi

> **Holat (2026-11-05):** birinchi versiyadagi har bir band kod va ishlab turgan konteynerlar bilan
> solishtirildi. Ko'p bandlar allaqachon bajarilgan, ikkitasi esa yozilganidek qilinsa **audioni
> buzadi** (§1). Haqiqiy foyda beradigan yagona katta ish — brauzer qo'ng'iroqlari uchun wideband
> (16 kHz) yo'l (§3, 2–3-bosqichlar). 1–3-bosqichlar kodlangan (tekshirilmagan); 4 — `livekit-sip` pin qilindi, qolgani server runbook'i.

---

## 0. Asosiy fakt: qaysi qo'ng'iroqda wideband umuman bor?

| Qo'ng'iroq turi | Mijozdan keladigan audio | Wideband'dan foyda |
|---|---|---|
| PSTN trunk (Asterisk yoki livekit-sip) | G.711, 8 kHz — operator tarmog'i shuni beradi | **Yo'q.** 3.4 kHz dan yuqori ma'lumot simdayoq yo'q; uni 16 kHz ga ko'tarish yangi tovush qo'shmaydi |
| Brauzer test / vidjet, Asterisk (`[webtest]`, WebRTC) | Mikrofon 48 kHz, lekin endpoint `ulaw/alaw` ga majbur | **Ha** — hozir brauzerda 8 kHz ga tushiriladi |
| Brauzer test / vidjet, LiveKit xonasi | Opus 48 kHz | **Ha** — `livekit-bridge` Opus'ni to'g'ridan 8 kHz ga dekodlaydi |

Shuning uchun "STT aniqligi 20–30% oshadi" va "150–250 ms tejash" kabi umumiy raqamlar telefon
qo'ng'iroqlariga tegishli emas. Ular o'lchanmagan; o'lchov 1-bosqichda.

App ichida `8000` ga bog'langan kod ~50 faylda (`agent/rtp`, `agent/audio`, `agent/flow`,
`agent/realtime`, `agent/stt`, VAD). Butun pipeline'ni 16 kHz ga o'tkazish emas, **faqat RTP chetida**
(§3, 2-bosqich) wideband'ni ushlash rejalashtiriladi.

---

## 1. Eski plan bandlari — tekshiruv natijasi

| # | Eski band | Haqiqat (kod / konteyner) | Qaror |
|---|---|---|---|
| 2.1 | `getUserMedia`: AEC, NS, AGC, mono, 48 kHz | Bajarilgan: `robot-call-front/src/lib/useWebTestCall.ts:56` `OPTIMAL_AUDIO_CONSTRAINTS` | ✅ Tayyor. `goog*` kalitlari zamonaviy Chrome'da e'tiborsiz qoladi — zararsiz, kerak emas |
| 2.2 | Opus `fmtp` (`useinbandfec`, `cbr`, `usedtx`) | `[webtest]` faqat `ulaw/alaw` — Opus umuman kelishilmaydi, `fmtp` ta'sir qilmaydi. Bundan tashqari `cbr=1` va `usedtx=1` bir-biriga zid, DTX esa jimlikda paket yubormaydi — VAD/turn uchun bo'sh joy "jimlik" emas, "paket yo'q" bo'lib qoladi | ❌ Qilinmaydi |
| 2.3 | `audio.playoutDelayHint = 0` | Bunday xususiyat `HTMLAudioElement` da **yo'q**; u `RTCRtpReceiver` da (`jitterBufferTarget`, Chrome 124+; eski `playoutDelayHint`) | 🔧 To'g'ri API bilan, 1-bosqich (frontend) |
| 3.1 | Asterisk adaptive jitter buffer o'chiq | Bajarilgan: `voice-agent.asterisk.jitter-buffer` bo'sh (`config/telephony.yml`), `AriClient` faqat qiymat berilsa `JITTERBUFFER()` qo'yadi | ✅ Tayyor. Yoqish mezoni — `voice.rtp.jitter` p95 > ~30 ms (`AsteriskProperties` javadoc) |
| 3.2 | `[webtest]` ga `allow = opus` | `andrius/asterisk:20` da **`codec_opus.so` yo'q** (faqat `res_format_attr_opus.so` — SDP atributi). `core show codecs` Opus'ni format sifatida ko'rsatadi, lekin tarjimon yo'q: Opus kelishilsa, externalMedia'ning `ulaw` iga yo'l bo'lmaydi → audiosiz qo'ng'iroq | ❌ **Xavfli.** O'rniga G.722 (§3, 2-bosqich) — `codec_g722.so` bor, Chrome/Firefox WebRTC G.722 ni qo'llaydi |
| 3.3 | `sysctl` `rmem_max=16MB` | Har qo'ng'iroq o'z UDP soketida, ~50 pkt/s × ~172 bayt; standart `rmem_default` (~208 KB) ~1200 paketni — ~24 s audioni sig'diradi. `rmem_max` faqat soket o'zi katta bufer so'rasa ta'sir qiladi, `RtpEndpoint` so'ramaydi | ⏸ Faqat o'lchov ko'rsatsa (4-bosqich) |
| 4.1 | `livekit-bridge` `sampleRate = 16000` | Diagramma noto'g'ri: libopus 8 kHz ga **to'g'ridan** dekodlaydi (resample yo'q), G.711 esa kompanding. Konstantani o'zgartirish yolg'iz app'ni buzadi — app 8 kHz PT 0/8 kutadi. PSTN xonalari uchun foyda nol | 🔧 Faqat brauzer xonalari uchun, 3-bosqich |
| 4.2 | `network_mode: host` | `docker-compose.yml` da izoh sifatida bor (Asterisk, livekit-sip). Docker Desktop'da ishlamaydi | ✅ Deploy hujjati, kod emas |
| 4.3 | "Doim `livekit/sip:latest`" | Teskarisi to'g'ri: `latest` deploy'ni takrorlanmas qiladi. `livekit-server` allaqachon pin qilingan (`v1.13.11`) | 🔧 4-bosqich: `LIVEKIT_SIP_IMAGE` ni aniq versiyaga pin qilish |

Allaqachon mavjud, plan'da tilga olinmagan himoyalar:
- STT'ga 8→16/24 kHz `StreamingUpsampler` (Blackman-sinc, ~50 dB image bostirish) — chiziqli emas.
- Har provayder o'z tezligini aytadi (`SttProvider.sampleRate()`): Deepgram/Google 8 kHz'da nativ, ElevenLabs/Gemini 16 kHz.
- RTP sifati har qo'ng'iroqda o'lchanadi: `RtpStats` → loss, jitter, MOS (`CallTeardownService`, `voice.rtp.*` metrikalari, `call_technical`).

---

## 2. Maqsad va o'lchov mezoni

Har bosqich raqam bilan yopiladi, "his qilish" bilan emas:

| Ko'rsatkich | Qayerdan olinadi |
|---|---|
| Kiruvchi jitter / loss / MOS | `voice.rtp.jitter`, `voice.rtp.loss`, `call_technical.rtp_jitter_ms` / `rtp_mos` |
| STT sifati | Bir xil 20 ta o'zbek/rus iborasini softphone (8 kHz) va brauzer (1-bosqich bilan / 2-bosqich bilan) orqali o'qib, transkriptni solishtirish (WER) |
| Bot ovozining kechikishi (oxirgi so'z → birinchi audio) | Mavjud turn latency metrikalari (`TurnLatency`, TTFA) |
| Brauzer playout kechikishi | `getStats()` → `inbound-rtp.jitterBufferDelay / jitterBufferEmittedCount` |

---

## 3. Bosqichlar

### 1-bosqich — Frontend: brauzer jitter buffer — ✅ kodlandi 2026-11-05 (tekshirilmagan)
- `useWebTestCall.ts` `minimizePlayoutDelay`: `pc.getReceivers()` siklida har audio receiver'ga
  `jitterBufferTarget = 0` (bo'lmasa `playoutDelayHint = 0`).
- `NetworkStats.playoutDelayMs`: stats intervalidagi `jitterBufferDelay / jitterBufferEmittedCount`
  deltasi — oxirgi 2.5 s dagi o'rtacha playout kechikishi.
- **Tekshiruv:** o'zgarishdan oldingi va keyingi commit'da web-test qilib `playoutDelayMs` ni
  (React DevTools yoki `chrome://webrtc-internals` → `inbound-rtp` → `[jitterBufferDelay/jitterBufferEmittedCount_in_ms]`)
  solishtirish; bot ovozi uzilib-uzilib chiqmasligi, `packetsLost`/`concealedSamples` ko'paymasligi.
- Eslatma: `opusOptimizationModifier` (`usedtx=1`) hozir ta'sirsiz — Asterisk Opus'ni kelishmaydi.
  Agar keyinroq Opus yoqilsa, `usedtx=1` ni olib tashlash kerak (§4).

### 2-bosqich — Asterisk brauzer qo'ng'irog'i uchun wideband chet — ✅ kodlandi 2026-11-05 (tekshirilmagan)
Faqat `[webtest]` (brauzer test / vidjet) kanallari; trunk, softphone va LiveKit 8 kHz'da qoladi.
Yoqish: `.env` da `RTP_WIDEBAND_WEB_TEST=true` (`voice-agent.rtp.wideband-web-test`, standart `false`).

Nima qilindi:
1. `pjsip.conf [webtest]`: `allow = g722` birinchi, keyin `ulaw`, `alaw`. Flag o'chiq bo'lsa ham ishlaydi —
   Asterisk G.722 ni eski `ulaw` oyog'iga o'zi o'giradi (`g722@16k → slin@8k → ulaw`).
2. `CallSetupService.isWidebandBrowserCall`: flag yoniq, kanal `PJSIP/webtest-…` va
   `CHANNEL(audionativeformat)` da `g722` bo'lsa — externalMedia `slin16`. Brauzer G.711 ga tushgan bo'lsa
   yoki o'qib bo'lmasa — eski tor oyoq (Asterisk upsample qilgan 8 kHz'dan foyda yo'q).
3. `RtpEndpoint` (`wideband = true`): kiruvchi L16 big-endian (640 bayt / 20 ms) dekod qilinadi;
   `WidebandAudioListener` (= `SttStreamBridge`) 16 kHz kadrni oladi, qolgan listener'lar va yozuv —
   `StreamingDownsampler` dan 8 kHz nusxani. PLC 16 kHz'da. RTP soati / `RtpStats` 16 kHz.
   Chiquvchi: bot 8 kHz audiosi `StreamingUpsampler` bilan 16 kHz, PT 118 (Asterisk'ning slin16 statik PT'si).
4. `SttStreamBridge.onWidebandAudio`: birinchi kadrda bridge 16 kHz manbaga o'tadi — `SpeechGate.widenPreRoll`,
   replay buffer ×2, utterance cap ×2. 16 kHz provayder (ElevenLabs, Gemini, Aisha) audioni o'zgarishsiz oladi;
   8 kHz (Deepgram/Google standart) — 16→8; 24 kHz (OpenAI) — 16→8→24 (telefon oyog'idan yomon emas).

Bilib qo'yish kerak:
- STT'ga boradigan 16 kHz nusxa **shovqindan tozalanmaydi** (`TelephonyNoiseCanceller` va neural denoiser 8 kHz modellar);
  brauzer `noiseSuppression` ni o'zi qiladi. VAD/AMD/yozuv nusxasi avvalgidek tozalanadi.
- Qo'ng'iroq yozuvi 8 kHz'da qoladi.
- Bot ovozi hali ham 8 kHz sifatda (faqat konteyner 16 kHz) — TTS'ning nativ 16 kHz chiqishini to'g'ridan berish keyingi ish.

**Tekshiruv (siz):**
1. `docker compose restart asterisk`, `.env` ga `RTP_WIDEBAND_WEB_TEST=true`, app restart.
2. Web-test qo'ng'irog'i: app logida `browser leg negotiated (g722) — wideband (slin16) media leg` va
   `STT fed 16 kHz from a wideband leg`. `Wideband leg … receives payload type …` ogohlantirishi **chiqmasligi** kerak
   (chiqsa — Asterisk boshqa PT ishlatadi va bot eshitilmaydi, `PT_SLIN16` ni moslash kerak).
3. Asterisk CLI: `core show channel PJSIP/webtest-…` → `NativeFormats: (g722)`; `UnicastRTP/…` → `slin16`.
4. Ikki tomon eshitiladi, barge-in ishlaydi, yozuv to'g'ri; §2 dagi WER sinovi: flag `false` va `true` bilan.
5. Trunk va softphone qo'ng'iroqlari o'zgarmagan (log'da wideband qatori yo'q).

### 3-bosqich — LiveKit brauzer xonasi uchun wideband — ✅ kodlandi 2026-11-05 (tekshirilmagan)
Xuddi shu flag: `RTP_WIDEBAND_WEB_TEST=true`.
- `livekit-bridge` (`g711.go` → `mediaCodec`): yangi `SLIN16` codec — PT 118, 16 kHz, big-endian.
  libopus Opus'ni to'g'ridan 16 kHz ga dekod qiladi va 16 kHz dan kodlaydi; kadr hajmi codec'dan
  (`frameSamples()`: 160 yoki 320). `ULAW`/`ALAW` o'zgarishsiz.
- `LiveKitCallService.startSession`: xona `web-` (`BROWSER_ROOM_PREFIX`) bilan boshlansa va flag yoniq bo'lsa —
  `RtpEndpoint(wideband = true)` va `LiveKitBridgeClient.startMedia(..., wideband)` → bridge'ga `codec: "SLIN16"`.
  Telefon xonalari (`in-`/`out-`) G.711 da qoladi. Endpoint va STT tomoni 2-bosqichdagi bilan bir xil.
- **Deploy:** bridge image'ni qayta yig'ish shart (`docker compose --profile livekit build livekit-bridge`).
  Eski bridge `SLIN16` ni 400 bilan rad etadi → brauzer qo'ng'irog'i boshlanmaydi.

**Tekshiruv (siz):**
1. Bridge'ni qayta yig'ib, flag bilan LiveKit dvigatelida brauzer test qo'ng'irog'i.
2. Bridge logida `bridged … room=web-…`; app logida `STT fed 16 kHz from a wideband leg`; payload type ogohlantirishi yo'q.
3. Ikki tomon eshitiladi, bot ovozi buzilmagan; LiveKit telefon (SIP) qo'ng'irog'i o'zgarmagan.
4. §2 dagi WER sinovi LiveKit'da flag `false` / `true` bilan.

### 4-bosqich — Ops (deploy) — ✅ 2026-11-05
**Qilindi:** `docker-compose.yml` da `livekit-sip` `livekit/sip:latest` → `livekit/sip:v1.22.0`
(ishlab turgan konteynerning `org.opencontainers.image.version` i). `LIVEKIT_SIP_IMAGE` bilan baribir
almashtirsa bo'ladi; versiyani faqat release notes'ni o'qib, ataylab ko'tarish.

**Server'da qilinadi (kodda emas) — runbook:**

1. *Host networking (faqat Linux production).* Docker bridge NAT har RTP paketni iptables orqali o'tkazadi va
   SDP'da konteyner manzilini e'lon qiladi. `asterisk`, `livekit`, `livekit-sip` uchun
   `docker-compose.yml` izohlaridagidek `network_mode: host`, `ports:` olib tashlanadi. Shu bilan birga:
   - `asterisk`: `pjsip.conf` dagi `external_*_address` / `local_net` va `rtp.conf [ice_host_candidates]` host manzillariga moslanadi
     (konteyner IP `172.29.0.10` endi yo'q); app'da `ARI_URL`, `RTP_LOCAL_IP` host manzilini ko'rsatadi.
   - `livekit` / `livekit-sip`: `use_external_ip: true`, Redis manzili `localhost`; port oralig'lari (`50000-50100`, `10300-10400`)
     Asterisk'ning `10000-10200` i bilan to'qnashmasligini tekshirish.
   - Docker Desktop (Windows/Mac) da **qilinmaydi** — u yerda host rejimi ishlamaydi.
   - **Tekshiruv:** `voice.rtp.jitter` p95 va `call_technical.rtp_jitter_ms` oldin/keyin; SDP `c=` qatorida public/LAN IP.

2. *UDP buferlari — faqat o'lchov ko'rsatsa.* Avval o'lchash (yuklama paytida, bir necha marta):
   ```bash
   netstat -su | grep -i -E "receive buffer errors|RcvbufErrors"
   ```
   Hisoblagich yuklama bilan **o'sib borsa** yoki `voice.rtp.loss` concurrency bilan birga ko'tarilsa —
   `/etc/sysctl.d/90-rtp.conf`:
   ```ini
   net.core.rmem_default = 262144
   net.core.netdev_max_backlog = 10000
   ```
   `sudo sysctl --system`, keyin o'lchov takrorlanadi. `net.core.*` konteyner ichidan o'rnatilmaydi (host sozlamasi).
   `rmem_max` ni oshirish `RtpEndpoint` soketga `SO_RCVBUF` so'ramaguncha ta'sir qilmaydi — hisoblagich o'smasa, hech narsa qilinmaydi.

---

## 4. Qilinmaydiganlar
- `[webtest]` ga `allow = opus` — `codec_opus` yo'q (yuqorida). Kerak bo'lsa, avval Opus tarjimonli Asterisk image'i, keyin qayta ko'rib chiqiladi.
- Butun pipeline'ni 16 kHz ga o'tkazish — PSTN uchun foydasiz, ~50 faylga tegadi.
- Opus DTX — VAD/turn jimlikni paket yo'qligidan ajrata olmaydi.
