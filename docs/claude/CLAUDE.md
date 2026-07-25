# CLAUDE.md — Ishchi yo'riqnoma

Bu fayl Claude Code uchun. To'liq texnik topshiriq: `../PROJECT.md`.

---

## Loyiha haqida qisqacha

AI voice agent: SIP orqali mijozlarga qo'ng'iroq qilib, o'zbek/rus tilida qarzdorlik
haqida suhbatlashadi va natijani CRM ga yozadi.

**Stack:** Java 21 · Spring Boot 3.x · Asterisk 20 · PostgreSQL · RabbitMQ · Redis · MinIO

---

## Qat'iy qoidalar

1. **Python ISHLATILMAYDI.** Butun stack Java. Skript kerak bo'lsa ham — bash yoki Java.
2. **Audio transport — RTP over UDP.** WebSocket yoki WebRTC audio uchun ishlatilmaydi.
   WebSocket faqat ARI control va STT/TTS provayderlar uchun.
3. **Netty event loop threadida blocking kod yozilmasin.** Paketni qabul qil, queue ga tashla,
   ishlov virtual threadda.
4. **Bosqichma-bosqich.** `../PROJECT.md` §10 dagi tartibni buzma. Har bosqich alohida
   tekshirilmaguncha keyingisiga o'tma.
5. **Guardrails majburiy.** LLM qarz summasi, muddat, chegirma haqida o'zidan gapirmasin
   (`../PROJECT.md` §4.4).
6. **Kommentariyalar va log xabarlari — ingliz tilida.** Foydalanuvchiga ko'rinadigan
   matnlar (bot gapiradigan) — o'zbek/rus.

---

## Modul strukturasi

> **QAROR (foydalanuvchi):** Bitta Gradle moduli, bitta source. Hamma kod
> `uz.murodjon.uysotvoice` paketi ostida. Multi-module (shared/voice-agent/dialer
> alohida modul) ISHLATILMAYDI. Base paket `uz.softex.voice` EMAS.
> Build/`gradlew`/RUN ni Claude qilmaydi — foydalanuvchi o'zi ishga tushiradi.

```
uysot-voice/  (dir: robot-call-v2)
├── build.gradle.kts                 # bitta Spring Boot ilova, Boot 3.4.5, Gradle 8.14.3
├── settings.gradle.kts
├── docker-compose.yml               # postgres, redis, rabbitmq, minio (+ asterisk Bosqich 1)
├── .env.example
└── src/main/java/uz/murodjon/uysotvoice/
    ├── UysotVoiceApplication.java
    ├── shared/                       # DTO, enum, contract
    │   └── dialog/                   # DialogState, ReasonCode, Disposition, Sentiment
    ├── agent/                        # STATEFUL — ARI + RTP + AI pipeline (keyingi bosqichlar)
    │   ├── ari/                      # ari4java, Stasis event handling
    │   ├── rtp/                      # Netty UDP, RTP parsing, jitter buffer
    │   ├── codec/                    # G711Codec, Resampler
    │   ├── vad/                      # Silero ONNX
    │   ├── stt/                      # SttProvider + implementatsiyalar
    │   ├── tts/                      # TtsProvider + implementatsiyalar
    │   ├── dialog/                   # FSM, DialogEngine, tools, prompts
    │   ├── session/                  # CallSession, registry
    │   └── persistence/              # entity, repository
    └── dialer/                       # campaign, scheduling (keyingi bosqichlar)
        ├── campaign/
        ├── scheduling/
        ├── crm/                      # Uysot CRM integratsiya
        └── messaging/                # RabbitMQ
```

---

## Har bosqichda nima qilish

`../PROJECT.md` §10 da 13 ta bosqich bor. Har biri uchun:

1. Faqat shu bosqich doirasidagi kodni yoz
2. Bosqich oxiridagi **Tekshiruv** shartini bajaradigan test/skript qo'sh
3. Ishlaganini tasdiqlagandan keyingina keyingisiga o't

Foydalanuvchi "keyingi bosqich" demaguncha oldinga yugurma.

---

## Hozir qayerdamiz

**Bosqich: 5 — STT kodi yozildi, foydalanuvchi tekshiruvi kutilmoqda**

Yaratilgan (Bosqich 5):
- ✅ `agent/stt/SttProvider` + `SttSession` + `TranscriptListener` interfeyslari (§2.4 abstraksiya)
- ✅ `agent/stt/GoogleSttProvider.java` — Google Cloud Speech streaming (bidi gRPC,
      LINEAR16, interim+final). Kredensiallar yo'q bo'lsa app crash bo'lmaydi (log).
      `@ConditionalOnProperty voice-agent.stt.enabled`.
- ✅ `agent/stt/SttStreamBridge.java` — AudioListener: PCM → (kerak bo'lsa 16k resample) → bytes → STT, transkript log
- ✅ `agent/audio/AudioListener.java` + `Resampler.java` (8k→16k linear, sof funksiya)
- ✅ `agent/stt/SttProperties.java` — `voice-agent.stt.*` (provider, model, sample-rate, til)
- ✅ `RtpEndpoint` — `List<AudioListener>` qabul qiladi (recorder + STT fan-out); close'da listener'larni yopadi
- ✅ `AriService` — STT provider ixtiyoriy inject (ObjectProvider), media'da bridge qo'shadi
- ✅ Test: `ResamplerTest`

> Model default BO'SH (uz-UZ uchun; phone_call ingliz/rus-centric). Google 8kHz native
> qabul qiladi (config sample-rate=8000), shuning uchun resample faqat 16k sozlansa ishlaydi.
> Kredensiallar: `GOOGLE_APPLICATION_CREDENTIALS` (service-account JSON) — foydalanuvchi beradi.
> Transkript DB'ga YOZILMAYDI hali: `call_transcript.call_id`→`call_attempt`→`campaign_target`
> (dialer) kerak. Bosqich 9 da qo'shiladi. Hozircha faqat log.

Tekshiruv (§10 Bosqich 5): qo'ng'iroqda o'zbekcha/ruscha gapirasan → logда matn (FINAL) chiqadi.
Keyingi: **Bosqich 6 — TTS (Google uz-UZ, Yandex ru-RU, router, streaming→RTP).**

---

### Oldingi bosqichlar

**Bosqich: 4 — RTP audio yuborish kodi yozildi**

Yaratilgan (Bosqich 4):
- ✅ `G711Codec` — PCM16 → µ-law/A-law encode qo'shildi (Sun g711.c reference)
- ✅ `agent/rtp/RtpPacket.toBytes(...)` — chiquvchi RTP paket (12-baytli header)
- ✅ `agent/rtp/WavReader.java` — WAV → PCM16 (mono, 16-bit; 8kHz kutiladi)
- ✅ `RtpEndpoint.playPcm(...)` — 20ms/frame (160 sample) pacing, symmetric RTP
      (kelgan paket manz]iga qaytaradi), eventLoop.scheduleAtFixedRate, seq/ts/ssrc
- ✅ `AriService.play(channelId, file)` + answer'dan keyin avto-ijro (test-playback-file)
- ✅ `CallController` — `POST /api/calls/{channelId}/play?file=...`
- ✅ `RtpProperties.testPlaybackFile` + `voice-agent.rtp.test-playback-file`
- ✅ Testlar: `G711CodecTest` (encode/round-trip) kengaytirildi

> ari4java endi `io.github.ari4java:ari4java:0.18.0` (foydalanuvchi yangiladi) — Java
> paket nomlari o'zgarmagan (`ch.loway.oss.ari4java.generated.models`, `...generated.AriWSHelper`),
> shu bois Bosqich 2/3 kodi mos. Netty out event loop'da faqat encode+send (bloklovchi I/O yo'q).

Tekshiruv (§10 Bosqich 4): `test-playback-file` ga 8kHz mono WAV bering (yoki
`POST .../play?file=...`) → qo'ng'iroqda audio tekis (tez/sekin emas, uzilmaydi) eshitiladi.
Keyingi: **Bosqich 5 — STT (Google streaming, 8k→16k resample, jitter buffer).**

---

### Oldingi bosqichlar

**Bosqich: 3 — RTP audio olish kodi yozildi**

Yaratilgan (Bosqich 3):
- ✅ `../../build.gradle.kts` — `io.netty:netty-transport` (BOM boshqaradi; netty-all EMAS, CVE'li HTTP kodeklaridan qochish uchun)
- ✅ `agent/codec/G711Codec.java` — µ-law/A-law → PCM16 dekod (encode Bosqich 4)
- ✅ `agent/rtp/RtpPacket.java` — RTP header parsing (seq, ts, PT, ssrc, padding, extension)
- ✅ `agent/rtp/WavRecorder.java` — PCM16 → WAV (8kHz mono, RandomAccessFile stream)
- ✅ `agent/rtp/RtpEndpoint.java` — Netty UDP listener + virtual thread consumer (dekod+WAV)
- ✅ `agent/rtp/RtpPortAllocator.java` + `RtpProperties.java` (voice-agent.rtp.*)
- ✅ `agent/session/CallSession.java`
- ✅ `config/NettyConfig.java` — shared NioEventLoopGroup
- ✅ `AriService` qayta yozildi — StasisStart'da: answer → RtpEndpoint → externalMedia (§8.4)
      → mixing bridge → yozib olish; StasisEnd'da teardown. (externalMedia "UnicastRTP"
      kanalini StasisStart'da e'tiborsiz qoldiradi)
- ✅ Unit testlar: `G711CodecTest`, `RtpPacketTest` (sof funksiyalar, Docker'siz o'tadi)

> Netty event loop'da bloklovchi kod yo'q: handler paketni queue'ga tashlaydi, dekod+WAV
> virtual threadда (§7.1, §7.3). Jitter buffer hozircha yo'q — STT (Bosqich 5) da qo'shiladi.

Tekshiruv (§10 Bosqich 3, **ENG MUHIM**): qo'ng'iroq qilib gapirasan → `recordings/<channel>.wav`
yaratiladi va ovoz tushunarli eshitiladi. `RTP_LOCAL_IP` Asterisk'dan erishiladigan bo'lsin
(Docker'da `host.docker.internal`). Keyingi: **Bosqich 4 — RTP audio yuborish (G711 encode, WAV ijro).**

---

### Oldingi bosqichlar

**Bosqich: 2 — ARI boshqaruv kodi yozildi**

Yaratilgan (Bosqich 2):
- ✅ `../../build.gradle.kts` — `ch.loway.oss.ari4java:ari4java:0.9.0` qo'shildi
- ✅ `agent/ari/AsteriskProperties.java` — `voice-agent.asterisk.*` (§12)
- ✅ `agent/ari/AriService.java` — ARI ulanish (AriVersion.IM_FEELING_LUCKY),
      StasisStart/StasisEnd handler, originate/answer/hangup. StasisStart'da
      javob berib ~5s ushlab, uzadi (Bosqich 2 test xatti-harakati).
- ✅ `agent/ari/CallController.java` — `POST /api/calls?number=...`
- ✅ `config/ExecutorConfig.java` — virtual thread executor (§7.3)
- ✅ `@ConfigurationPropertiesScan` asosiy klassда

> ari4java 0.9.0 paketlari (tekshirildi): model = `ch.loway.oss.ari4java.generated.models`,
> `AriWSHelper` = `ch.loway.oss.ari4java.generated`. Ulanish uzilsa app crash bo'lmaydi (log).

Tekshiruv (§10 Bosqich 2): `POST /api/calls?number=<raqam>` chaqirilsa telefon
jiringlaydi, javob bergach ~5s dan keyin uziladi; logда StasisStart/StasisEnd.
Keyingi: **Bosqich 3 — RTP audio olish (Netty UDP, externalMedia, WAV).**

---

### Oldingi bosqichlar

**Bosqich: 1 — Asterisk konfig yozildi**

Yaratilgan (Bosqich 1):
- ✅ `../../asterisk/etc/asterisk` — asterisk.conf, modules.conf, http.conf, ari.conf,
      pjsip.conf (trunk skeleti §8.2), extensions.conf (§8.3), rtp.conf, logger.conf
- ✅ `../../docker-compose.yml` da asterisk servisi yoqildi (andrius/asterisk:20-current)
- ⏳ FOYDALANUVCHI TO'LDIRADI: `pjsip.conf` — PROVIDER_HOST, USERNAME, PASSWORD;
      `ari.conf` — parol. SIP provayder avtomatik obzvonga ruxsat berishini tasdiqlash (§13.1).

Tekshiruv (§10 Bosqich 1): trunk registratsiyasi (`pjsip show registrations`),
softphone orqali qo'lda test qo'ng'iroq — ovoz ikki tomonlama, ARI ulanadi.
Keyingi: **Bosqich 2 — ARI boshqaruv (ari4java, Java kod).**

---

### Oldingi bosqichlar

**Bosqich: 0 — kod yozildi (infratuzilma)**

Yaratilgan:
- ✅ Bitta modulli Gradle skelet (`../../build.gradle.kts`, `../../settings.gradle.kts`, Kotlin DSL)
      Boot 3.4.5, Gradle wrapper 8.14.3, Java 21
- ✅ `../../docker-compose.yml` — PostgreSQL, Redis, RabbitMQ, MinIO (Asterisk kommentda, Bosqich 1)
- ✅ `.env.example`
- ✅ Flyway migratsiya `V1__initial_schema.sql` — §6 dagi 5 jadval
- ✅ `UysotVoiceApplication.java` + shared enumlar (DialogState, ReasonCode, Disposition, Sentiment)
- ✅ `UysotVoiceApplicationTests` — Testcontainers Postgres, Flyway migratsiya tekshiruvi

**Foydalanuvchi tekshiruvi (Claude RUN qilmaydi):**
- `docker compose up -d`
- `./gradlew build` — kompilyatsiya + Testcontainers testi o'tishi kerak
- Ilova ishga tushishi (`./gradlew bootRun`)

Tasdiqlangach → **Bosqich 1 (Asterisk)**. Foydalanuvchi "keyingi bosqich" demaguncha oldinga yugurma.

---

## Testlash

- **Unit:** G711Codec, Resampler, RTP parsing, FSM transitions — bular sof funksiyalar, oson test
- **Integration:** Testcontainers (PostgreSQL, RabbitMQ, Redis)
- **Audio pipeline:** WAV fayl → pipeline → WAV fayl. Real qo'ng'iroqsiz tekshirish mumkin.
- **LLM:** dialog testlari uchun mock STT (matn beriladi) — LLM javobini tekshirish

Real qo'ng'iroq testi uchun alohida test raqam kerak — foydalanuvchidan so'ra.

---

## Yordam kerak bo'lganda

Quyidagilar noaniq bo'lsa — **taxmin qilma, so'ra**:
- SIP provayder credentials va trunk sozlamalari
- CRM API endpointlari va autentifikatsiya
- Google Cloud / Yandex API kalitlari
- Test uchun telefon raqami
- Qarzdorlik ma'lumotining aniq strukturasi
