# AI Voice Agent — Avtomatik Qo'ng'iroq Tizimi

> **Bu hujjat Claude Code uchun texnik topshiriq (spec).**
> Loyiha: mijozlarga SIP orqali avtomatik qo'ng'iroq qilib, AI bilan suhbatlashadigan va natijani CRM ga yozadigan tizim.
> Asosiy use-case: qarzdorlik bo'yicha suhbat — sababi, to'lov muddati, izoh.

---

## 1. Umumiy tavsif

### 1.1 Maqsad

Operatorlar qo'lda qiladigan qarzdorlik qo'ng'iroqlarini avtomatlashtirish. Tizim:

1. Kampaniya (campaign) yaratiladi — maqsad, skript, mijozlar segmenti
2. Dialer navbat bo'yicha qo'ng'iroq qiladi
3. AI agent mijoz bilan o'zbek yoki rus tilida suhbatlashadi
4. Suhbat natijasi structured ma'lumot sifatida CRM ga note bo'lib yoziladi

### 1.2 Nima QILMAYDI (scope dan tashqari)

- Inbound (kiruvchi) qo'ng'iroqlar — faqat outbound
- To'lov qabul qilish / IVR orqali karta ma'lumoti olish
- Sotuv qo'ng'iroqlari (keyingi bosqichda campaign type sifatida qo'shiladi)
- Kod-switching (bir jumlada o'zbek+rus aralash) — qo'llab-quvvatlanmaydi

### 1.3 Asosiy talablar

| Talab | Qiymat |
|---|---|
| Tillar | O'zbek (`uz-UZ`), Rus (`ru-RU`) |
| Parallel qo'ng'iroqlar (MVP) | ~20 (konfiguratsiya orqali o'zgaruvchan) |
| End-to-end latency maqsadi | < 1000ms (mijoz gapirib bo'lgandan bot javob boshlagunicha) |
| Til | Java 21, Spring Boot 3.x |
| Python | **ISHLATILMAYDI** — butun stack Java |

---

## 2. Texnologiya stack

### 2.1 Umumiy arxitektura

```
        ┌─────────────────────────────────────────────┐
        │  Mijozning telefoni                          │
        └──────────────────┬──────────────────────────┘
                           │ SIP / RTP
        ┌──────────────────▼──────────────────────────┐
        │  SIP Provayder (trunk)                       │
        └──────────────────┬──────────────────────────┘
                           │ SIP / RTP
        ┌──────────────────▼──────────────────────────┐
        │  Asterisk 20 LTS                             │
        │   - PJSIP trunk                              │
        │   - Stasis app                               │
        │   - externalMedia channel                    │
        └────┬─────────────────────────┬──────────────┘
             │ ARI (WebSocket)         │ RTP (UDP)
             │ boshqaruv                │ audio
        ┌────▼─────────────────────────▼──────────────┐
        │  voice-agent-service (Spring Boot, Java 21)  │
        │                                              │
        │   ari4java  ──  RtpEndpoint (Netty UDP)      │
        │                      │                       │
        │                 AudioPipeline                │
        │                      │                       │
        │      ┌───────────────┼───────────────┐       │
        │      ▼               ▼               ▼       │
        │   VAD(ONNX)     SttClient      TtsClient     │
        │                      │                       │
        │                 DialogEngine                 │
        │                (Spring AI + FSM)             │
        └──────────────┬───────────────────────────────┘
                       │ RabbitMQ / gRPC
        ┌──────────────▼───────────────────────────────┐
        │  dialer-service (Spring Boot)                 │
        │   campaign, target, scheduling, retry         │
        └──────────────┬───────────────────────────────┘
                       │
        ┌──────────────▼───────────────────────────────┐
        │  Uysot CRM (mavjud tizim)                     │
        │   mijoz, qarzdorlik, note                     │
        └───────────────────────────────────────────────┘

   Infra: PostgreSQL · Redis · RabbitMQ · MinIO · Docker
```

### 2.2 Nima uchun RTP, WebSocket/WebRTC emas

Mijoz brauzerda emas — oddiy telefonda. Media allaqachon RTP formatida keladi.

| Transport | Latency | Qaror |
|---|---|---|
| **RTP over UDP** | ~5–15ms | ✅ TANLANDI — Asterisk `externalMedia` shuni beradi |
| WebSocket (TCP) | ~20–50ms | ❌ TCP head-of-line blocking, paket yo'qolsa butun oqim kutadi |
| WebRTC | ~15–30ms | ❌ ICE/DTLS/SRTP handshake — brauzer uchun, bu yerda ortiqcha |

WebSocket baribir ishlatiladi, lekin **audio uchun emas**:
- ARI control channel (Asterisk bilan buyruq almashish)
- STT / TTS provayderlarga ulanish

### 2.3 Kutubxonalar

```gradle
dependencies {
    // Spring
    implementation "org.springframework.boot:spring-boot-starter-web"
    implementation "org.springframework.boot:spring-boot-starter-data-jpa"
    implementation "org.springframework.boot:spring-boot-starter-amqp"
    implementation "org.springframework.boot:spring-boot-starter-data-redis"
    implementation "org.springframework.boot:spring-boot-starter-actuator"

    // Asterisk ARI
    implementation "ch.loway.oss.ari4java:ari4java:0.9.0"

    // Audio transport — RTP UDP + WebSocket clients
    implementation "io.netty:netty-all:4.1.109.Final"

    // VAD — Silero model
    implementation "com.microsoft.onnxruntime:onnxruntime:1.17.3"

    // LLM
    implementation "org.springframework.ai:spring-ai-anthropic-spring-boot-starter:1.0.0"

    // STT / TTS
    implementation "com.google.cloud:google-cloud-speech:4.36.0"
    implementation "com.google.cloud:google-cloud-texttospeech:2.44.0"
    // Yandex SpeechKit — gRPC stub generatsiya qilinadi (2.6 ga qarang)

    // Storage
    implementation "io.minio:minio:8.5.10"

    // Utils
    implementation "org.postgresql:postgresql"
    implementation "org.flywaydb:flyway-core"
    compileOnly "org.projectlombok:lombok"
}
```

**G.711 kodek** — kutubxona kerak emas. µ-law/A-law ↔ PCM16 lookup table bilan ~30 qator Java kodi. `G711Codec.java` da yoziladi.

### 2.4 STT tanlovi

| Provayder            | Ruscha      | O'zbekcha  | Streaming          | Java SDK         |
|----------------------|-------------|------------|--------------------|------------------|
| **Google Cloud STT** | Yaxshi      | ✅ `uz-UZ` | gRPC bidirectional | ✅ rasmiy        |
| Yandex SpeechKit     | Juda yaxshi | ✅ `uz-UZ` | gRPC               | stub generatsiya |
| Deepgram             | Yaxshi      | ❌         | WebSocket          | ✅ rasmiy        |
| Aisha (Toshkent)     | Yaxshi      | ✅ uz + uz/ru aralash | WebSocket (16 kHz PCM) | JDK WebSocket    |

**MVP qarori: Google Cloud STT** — ikkala tilni bitta provayderda beradi, integratsiya bitta.

Keyingi bosqichda ruscha oqimni Yandexga ko'chirish mumkin (aniqroq), shuning uchun `SttProvider` interfeysi orqali abstraktlashtiriladi.

**Model:** `latest_long` yoki telefon uchun `phone_call` model. Sample rate 8000 Hz (telefon). `enableAutomaticPunctuation=true`, `singleUtterance=false`.

### 2.5 TTS tanlovi

| Provayder        | Ruscha            | O'zbekcha                   |
|------------------|-------------------|-----------------------------|
| Yandex SpeechKit | ✅ Ajoyib, tabiiy | ❌                          |
| **Google TTS**   | Yaxshi            | ✅ `uz-UZ` Standard/WaveNet |
| Aisha (Toshkent) | ✅                | ✅ Gulnoza (neutral/cheerful/happy/sad) |

**Qaror:** interfeys orqali router.
- `uz-UZ` → Google TTS
- `ru-RU` → Yandex SpeechKit (tabiiyroq) yoki Google (MVP da soddaroq)

TTS da provayder almashtirish oson — sessiya holati yo'q, matn yuborasan, audio olasan.

> ⚠️ **MVP dan oldin o'zbekcha TTS ovozini tinglab tekshirish shart.** Bu mijoz taassurotiga eng ko'p ta'sir qiladigan komponent.

### 2.6 LLM

**Anthropic Claude** — Spring AI orqali.

- **Dialog uchun:** tez model (Haiku darajasi) — 300-400ms first token. Suhbat oddiy, katta model shart emas.
- **Yakuniy xulosa uchun:** kuchliroq model (Sonnet) — sifat muhim, latency muhim emas.
- Streaming **majburiy** — TTS birinchi jumla kelishi bilan boshlanadi.

Spring AI ishlatilgani uchun keyinchalik boshqa provayderga o'tish oson.

---

## 3. Til boshqaruvi

### 3.1 Strategiya

Ustuvorlik tartibi:

1. **CRM dagi `preferred_language`** — eng ishonchli. `client` jadvalida saqlanadi.
2. **Kampaniya default tili** — mijozda ko'rsatilmagan bo'lsa.
3. **Suhbat boshida so'rash** — ikkalasi ham noma'lum bo'lsa:
   > "Assalomu alaykum! Sizga o'zbek tilida gaplashsam bo'ladimi? / Здравствуйте! Вам удобно говорить на русском?"

   Javobga qarab STT sessiyasi shu til bilan qayta ochiladi va CRM ga yoziladi.

### 3.2 Muhim cheklovlar

- **Real-time avtomatik til aniqlash MVP ga KIRMAYDI** — telefon audiosida (8kHz) ishonchsiz.
- **Kod-switching qo'llab-quvvatlanmaydi.** Bot sof tilda gapirsa, mijoz odatda moslashadi.
- Til qo'ng'iroq boshida bir marta tanlanadi va suhbat oxirigacha o'zgarmaydi (2-bosqichda: mijoz boshqa tilda javob bersa, bir marta almashtirish).

---

## 4. Suhbat arxitekturasi

### 4.1 Gibrid model: FSM skeleti + LLM moslashuvchanligi

Sof free-form LLM qarz masalasida **xavfli** — noto'g'ri summa aytishi, chegirma va'da qilishi, muddat uzaytirishi mumkin.

```
   ┌──────────┐
   │ GREETING │  Salomlashish + tizim ekanini aytish + yozib olish haqida
   └────┬─────┘
        ▼
   ┌────────────────┐
   │ IDENTITY_CHECK │  "Siz Falonchimisiz?" — noto'g'ri odam bo'lsa → END
   └────┬───────────┘
        ▼
   ┌─────────────┐
   │ DEBT_NOTICE │  Qarz miqdori va muddatini aytish (FAKT — promptdan)
   └────┬────────┘
        ▼
   ┌────────────────┐
   │ REASON_INQUIRY │  "Nima uchun to'lanmayapti?" — sabab aniqlash
   └────┬───────────┘
        ▼
   ┌──────────────┐
   │ PAYMENT_DATE │  "Qachon to'lay olasiz?" — aniq sana olish
   └────┬─────────┘
        ▼
   ┌──────────────┐
   │ CONFIRMATION │  Kelishuvni takrorlash va tasdiqlash
   └────┬─────────┘
        ▼
   ┌─────────┐
   │ CLOSING │  Xayrlashuv
   └─────────┘

   Istalgan holatdan:
   → ESCALATE_TO_HUMAN  (mijoz operator so'rasa yoki janjal qilsa)
   → END_CALL           (mijoz go'shakni qo'ysa yoki rad etsa)
```

Har holat uchun LLM ga beriladi:
- Mijoz konteksti (ism, qarz summasi, muddat, shartnoma raqami)
- Shu bosqichning maqsadi
- Ruxsat etilgan keyingi holatlar

LLM javob **matnini** generatsiya qiladi va **tool call** orqali keyingi holatni tanlaydi.

### 4.2 Tool'lar (LLM function calling)

Spring AI `@Tool` annotatsiyasi bilan ro'yxatdan o'tkaziladi.

```java
@Tool(description = "Mijoz to'lov sanasini va'da qilganda chaqiriladi")
void recordPaymentPromise(LocalDate promisedDate, BigDecimal amount, String note);

@Tool(description = "Mijoz to'lay olmasligini aytganda, sababni yozib qo'yish")
void recordRefusalReason(ReasonCode code, String detail);

@Tool(description = "Mijoz operator bilan gaplashishni so'raganda yoki janjal qilganda")
void requestHumanTransfer(String reason);

@Tool(description = "Telefonni ko'targan odam qarzdor emasligi aniqlanganda")
void recordWrongPerson(String detail);

@Tool(description = "Suhbat tugadi, qo'ng'iroqni yakunlash")
void endCall(Disposition disposition);

@Tool(description = "Suhbat bosqichini keyingisiga o'tkazish")
void transitionTo(DialogState nextState);

@Tool(description = "Narigi tomondagi avtomat menyuda (IVR) kerakli tugmani bot o'zi bosadi")
String sendDtmfTones(String reply, String digits, String menuOption);
```

**`sendDtmfTones` — IVR navigatsiyasi.** Korxona raqamiga qo'ng'iroq qilganda odamgacha
avtomat menyu javob beradi ("buxgalteriya uchun 1 ni bosing"). Bot menyuni oddiy nutq
kabi eshitadi va mos raqamni o'zi bosadi (ARI `channels/{id}/dtmf`).

Ikki cheklov bilan:
- **Faqat chiquvchi qo'ng'iroqda.** Kiruvchi qo'ng'iroqda narigi uchda odam turadi;
  `AriService` u yerda DTMF yuboruvchini umuman ulamaydi va tool "bo'lmaydi" deb javob
  qaytaradi.
- **Faqat ssenariy so'raganda.** Tool `HARDCODED_TOOL_NAMES` ichida, ya'ni LLM ga faqat
  ssenariy `tools` ro'yxatida `sendDtmfTones` nomli `ToolDef` e'lon qilingan bo'lsa
  ko'rinadi. Odamga qo'ng'iroq qiladigan ssenariyda umuman mavjud emas.

```java
enum ReasonCode {
    NO_MONEY,           // pul yo'q
    JOB_LOSS,           // ishdan chiqqan
    ILLNESS,            // kasallik
    ALREADY_PAID,       // "men to'laganman" — tekshirish kerak
    DISPUTES_DEBT,      // qarzni tan olmaydi
    FORGOT,             // esdan chiqqan
    TECHNICAL_ISSUE,    // to'lov o'tmagan
    OTHER
}

enum Disposition {
    PROMISE_TO_PAY,     // to'lash va'dasi olindi
    REFUSED,            // rad etdi
    NO_ANSWER,          // javob bermadi
    WRONG_NUMBER,       // noto'g'ri raqam
    HUNG_UP,            // go'shakni qo'ydi
    TRANSFERRED,        // operatorga o'tkazildi
    VOICEMAIL,          // avtojavob
    FAILED              // texnik xato
}
```

### 4.3 Yakuniy xulosa

Suhbat tugagach — **alohida bitta LLM chaqiruv**, to'liq transkriptdan structured output:

```java
public record CallSummary(
    String summary,              // 2-3 jumla, CRM note uchun
    ReasonCode reasonCode,
    LocalDate promisedDate,      // null bo'lishi mumkin
    BigDecimal promisedAmount,   // null bo'lishi mumkin
    Sentiment sentiment,         // POSITIVE / NEUTRAL / NEGATIVE / HOSTILE
    boolean needsFollowUp,
    String followUpNote
) {}
```

```java
CallSummary result = chatClient.prompt()
        .system(SUMMARY_SYSTEM_PROMPT)
        .user(transcript)
        .call()
        .entity(CallSummary.class);
```

Bu to'g'ridan-to'g'ri CRM `note` ga yoziladi.

### 4.4 Guardrails (MAJBURIY)

Faktlar (summa, muddat, shartnoma raqami) **LLM dan generatsiya qilinmaydi** — promptga oldindan qo'yiladi.

System prompt da qat'iy taqiqlar:

```
QAT'IY QOIDALAR:
- Qarz summasini HECH QACHON o'zgartirma. Faqat berilgan raqamni ayt.
- Chegirma, imtiyoz yoki qarz kechirishni HECH QACHON taklif qilma.
- To'lov muddatini o'zing uzaytirma — faqat mijoz aytgan sanani yozib ol.
- Peniya va shartnoma bekor bo'lish muddatini FAQAT faktlarda berilgan bo'lsa va aynan
  berilgan raqam bilan ayt — o'zingdan raqam to'qima, foiz hisoblama, tahdid ohangida aytma.
- Sud, ijro, qora ro'yxat, musodara, jinoiy javobgarlik haqida o'zingdan gapirma va
  qo'rqitma — mijoz so'rasa operatorga o'tkaz.
- Mijozning shaxsiy ma'lumotlarini boshqa odamga (telefonni ko'targan begonaga) aytma.
- Agar savolga javobni bilmasang — operatorga o'tkaz, o'ylab topma.
- Mijoz asabiylashsa yoki haqorat qilsa — darhol operatorga o'tkaz.
```

Qo'shimcha kod darajasidagi himoya:
- LLM javobida raqam bo'lsa, u kontekstdagi faktlar bilan solishtiriladi (regex validation)
- `promisedDate` o'tgan sana bo'lsa — rad etiladi
- Maksimal suhbat davomiyligi (masalan 5 daqiqa) — oshsa avtomatik yakunlash
- Maksimal turn soni (masalan 25) — oshsa yakunlash

---

## 5. Servis bo'linishi

```
uysot-voice/
├── voice-agent-service/     # Asterisk ARI + RTP + STT/LLM/TTS  (STATEFUL)
├── dialer-service/          # campaign, target, scheduling, retry
└── shared/                  # DTO, enum, contract
```

### 5.1 voice-agent-service

**Stateful** — har aktiv qo'ng'iroq xotirada sessiya ushlaydi.

Vazifalari:
- ARI orqali Asterisk bilan aloqa (qo'ng'iroq boshqarish)
- RTP audio qabul qilish / yuborish
- STT / TTS / LLM pipeline
- Suhbat holatini boshqarish (FSM)
- Transkript va natijani saqlash

**Scaling:** sticky routing kerak. Redis da `call_id → instance_id` registry. Load balancer shunga qarab yo'naltiradi.

### 5.2 dialer-service

**Stateless** — oddiy scale qilinadi.

Vazifalari:
- Kampaniya CRUD
- Mijozlar segmentatsiyasi (CRM dan target ro'yxati)
- Scheduling — qo'ng'iroq vaqti oynasi, rate limiting
- Retry logikasi
- RabbitMQ ga qo'ng'iroq topshirig'i yuborish
- Natijalarni CRM ga yozish

---

## 6. Ma'lumotlar modeli

Uchta obyekt uch xil savolga javob beradi va bir-birini takrorlamaydi:
`scenario` — **nima** gapiriladi, `ai_agent` — **kim** gapiradi, `campaign` — **kimga va
qachon**. Har bir qo'ng'iroq — chiquvchi ham, kiruvchi ham — aynan bitta `ai_agent` orqali
o'tadi (`campaign.ai_agent_id`, `inbound_route.ai_agent_id`), shuning uchun ovoz, persona
va model bitta joydan o'qiladi.

```sql
-- AI agent: senariyni qanday ovoz, persona va model bilan gapirish (V12)
CREATE TABLE ai_agent (
    id              BIGSERIAL PRIMARY KEY,
    company_id      BIGINT       NOT NULL REFERENCES company(id),
    name            VARCHAR(255) NOT NULL,
    scenario_id     BIGINT       NOT NULL REFERENCES scenario(id),
    language        VARCHAR(10)  NOT NULL DEFAULT 'uz-UZ',
    tts_voice       VARCHAR(64),             -- voice-agent.tts.catalog id; null -> kompaniya sozlamasi
    persona         VARCHAR(30)  NOT NULL DEFAULT 'AI_ASSISTANT',  -- AI_ASSISTANT, HUMAN_LIKE
    llm_model       VARCHAR(120),            -- null -> kompaniyaning ai_model_config
    temperature     DOUBLE PRECISION,
    max_output_tokens INT,
    ambient_sound   VARCHAR(30)  NOT NULL DEFAULT 'OFF',
    emotion_adaptive_voice BOOLEAN NOT NULL DEFAULT true,
    dtmf_input_enabled BOOLEAN   NOT NULL DEFAULT false,
    voicemail_action VARCHAR(30) NOT NULL DEFAULT 'HANGUP',
    enabled         BOOLEAN      NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- + ai_agent_language_voice (til -> ovoz) va ai_agent_sip_trunk (chiquvchi trunklar)

-- Kampaniya: kimga va qachon qo'ng'iroq qilinadi
CREATE TABLE campaign (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    type            VARCHAR(50)  NOT NULL,   -- DEBT_COLLECTION, SURVEY, ...
    status          VARCHAR(50)  NOT NULL,   -- DRAFT, ACTIVE, PAUSED, COMPLETED
    script_config   JSONB        NOT NULL,
    ai_agent_id     BIGINT       NOT NULL REFERENCES ai_agent(id),
    dial_window_start TIME       NOT NULL DEFAULT '07:00',
    dial_window_end   TIME       NOT NULL DEFAULT '23:00',
    max_attempts    INT          NOT NULL DEFAULT 3,
    retry_interval_minutes INT   NOT NULL DEFAULT 0,   -- 0 -> natijaga qarab (dialer.retry.*)
    max_concurrent_calls INT     NOT NULL DEFAULT 20,
    daily_call_cap  INT          NOT NULL DEFAULT 0,
    recurrence_type VARCHAR(20)  NOT NULL DEFAULT 'ONCE',  -- ONCE, DAILY, WEEKLY, MONTHLY, CRON
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      BIGINT
);

-- Kampaniya nishonlari qayerdan olinadi (CSV o'rniga kompaniyaning o'z API'si; V13)
CREATE TABLE campaign_target_source (
    campaign_id     BIGINT PRIMARY KEY REFERENCES campaign(id) ON DELETE CASCADE,
    url             VARCHAR(1000) NOT NULL,
    http_method     VARCHAR(10)   NOT NULL DEFAULT 'GET',
    auth_header_name  VARCHAR(100),
    auth_header_value TEXT,                  -- AES-GCM
    items_path      VARCHAR(200),            -- javob ichidagi massivgacha nuqtali yo'l
    phone_field     VARCHAR(100)  NOT NULL DEFAULT 'phone',
    replace_targets BOOLEAN       NOT NULL DEFAULT false,
    sync_on_recurrence BOOLEAN    NOT NULL DEFAULT true,
    last_sync_at    TIMESTAMPTZ,
    last_sync_error VARCHAR(1000)
);

-- Kampaniya nishoni (mijoz)
CREATE TABLE campaign_target (
    id              BIGSERIAL PRIMARY KEY,
    campaign_id     BIGINT       NOT NULL REFERENCES campaign(id),
    client_id       BIGINT       NOT NULL,   -- CRM dagi mijoz
    phone           VARCHAR(20)  NOT NULL,
    language        VARCHAR(10),             -- null bo'lsa campaign default
    context_data    JSONB        NOT NULL,   -- qarz summasi, muddat, shartnoma №
    status          VARCHAR(50)  NOT NULL,   -- PENDING, IN_PROGRESS, DONE, FAILED, EXHAUSTED
    attempts        INT          NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_target_dial ON campaign_target(campaign_id, status, next_attempt_at);

-- Qo'ng'iroq urinishi
CREATE TABLE call_attempt (
    id              BIGSERIAL PRIMARY KEY,
    target_id       BIGINT       NOT NULL REFERENCES campaign_target(id),
    sip_call_id     VARCHAR(255),
    asterisk_channel VARCHAR(255),
    language        VARCHAR(10)  NOT NULL,
    started_at      TIMESTAMPTZ,
    answered_at     TIMESTAMPTZ,
    ended_at        TIMESTAMPTZ,
    duration_sec    INT,
    disposition     VARCHAR(50),             -- Disposition enum
    hangup_cause    VARCHAR(50),             -- Asterisk cause code
    recording_url   VARCHAR(500),            -- MinIO
    error_message   TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_attempt_target ON call_attempt(target_id);

-- Transkript (har bir gap)
CREATE TABLE call_transcript (
    id              BIGSERIAL PRIMARY KEY,
    call_id         BIGINT       NOT NULL REFERENCES call_attempt(id),
    seq             INT          NOT NULL,
    role            VARCHAR(20)  NOT NULL,   -- AGENT, CLIENT
    text            TEXT         NOT NULL,
    dialog_state    VARCHAR(50),
    ts_offset_ms    INT          NOT NULL,   -- qo'ng'iroq boshidan
    stt_confidence  REAL
);
CREATE INDEX idx_transcript_call ON call_transcript(call_id, seq);

-- Natija
CREATE TABLE call_result (
    id              BIGSERIAL PRIMARY KEY,
    call_id         BIGINT       NOT NULL UNIQUE REFERENCES call_attempt(id),
    summary         TEXT         NOT NULL,
    reason_code     VARCHAR(50),
    promised_date   DATE,
    promised_amount NUMERIC(18,2),
    sentiment       VARCHAR(20),
    needs_follow_up BOOLEAN      NOT NULL DEFAULT false,
    follow_up_note  TEXT,
    escalated       BOOLEAN      NOT NULL DEFAULT false,
    crm_note_id     BIGINT,                  -- CRM ga yozilgandan keyin
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
```

---

## 7. Audio pipeline (texnik detallar)

### 7.1 Oqim

```
Asterisk externalMedia
    │  RTP/UDP, G.711 µ-law yoki A-law, 8kHz, 20ms frame (160 bayt)
    ▼
Netty UDP handler
    │
    ▼
Jitter buffer (40–60ms)          ← sequence number bo'yicha tartiblash
    │                                katta qilsang latency oshadi
    ▼
G.711 decode → PCM16 8kHz
    │
    ├──────────────► VAD (Silero ONNX)  ← barge-in aniqlash
    │                     │
    ▼                     │
Resample 8k → 16k         │
    │                     │
    ▼                     │
STT streaming (gRPC)      │
    │                     │
    ▼                     │
Transkript (interim + final)
    │                     │
    ▼                     │
DialogEngine (FSM + LLM streaming)
    │                     │
    ▼                     │
TTS streaming ◄───────────┘  (VAD signal kelsa — DARHOL to'xtatish)
    │
    ▼
PCM16 → resample 16k→8k → G.711 encode
    │
    ▼
RTP out (20ms frame, to'g'ri timestamp/seq)
    │
    ▼
Asterisk
```

### 7.2 Barge-in (KRITIK)

Mijoz gapira boshlaganda bot **darhol** jim bo'lishi kerak. Bo'lmasa suhbat butunlay buziladi.

Ketma-ketlik:
1. VAD mijoz ovozini aniqlaydi (≥ 200ms davomiy nutq — qisqa "aha", "ha" larga reaksiya qilmaslik uchun)
2. TTS oqimi **bekor qilinadi** (WebSocket/gRPC stream cancel)
3. RTP output buferi **tozalanadi** (bufferdagi eski audio ketmasin)
4. STT ga o'tiladi
5. LLM ga "mijoz meni bo'ldi, shu yergacha aytgan edim: ..." konteksti beriladi

### 7.3 Threading — Java 21 virtual threads

```java
@Bean
ExecutorService callExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
}
```

Har qo'ng'iroq — bitta virtual thread. Blocking kod yoziladi, lekin 500+ parallel qo'ng'iroq muammosiz.

> ⚠️ Netty event loop threadlarida **hech qachon blocking kod yozilmasin**. RTP paketni qabul qilib, darhol queue ga tashla, ishlov virtual threadda.

### 7.4 Latency byudjeti

```
RTP jitter buffer          ~50ms
STT endpointing + final    ~200-300ms
LLM first token            ~300-500ms
TTS first chunk            ~150-300ms
Encode + RTP out           ~20ms
──────────────────────────────────
JAMI                       ~720-1170ms
```

Optimallashtirish:
- STT **interim** natijalarni LLM ga bermang (noto'g'ri javob generatsiya qiladi)
- Lekin **endpointing** ni agressiv sozlang (jim turish 500-700ms → gap tugadi)
- TTS ni birinchi **jumla** kelishi bilan boshlang, butun javobni kutmang
- LLM streaming da jumla chegarasini aniqlash: `.`, `!`, `?` + probel

Byudjetning ikki eng katta hadiga qarshi qo'shimcha mexanizmlar (hammasi **default
o'chiq**, har biri alohida bayroq ostida — batafsil izoh `.env.example` da):

| Mexanizm | Qaysi hadga tegadi | Sozlama |
|---|---|---|
| **Dinamik endpointing** — uzun utterance kutish vaqti sessiya davomida EMA bilan o'rganiladi: mijoz gapini davom ettirsa uzayadi, toza tugasa qisqaradi. Modelga bog'liq emas, uz-UZ da ham ishlaydi | STT endpointing | `STT_ENDPOINTING_DYNAMIC` |
| **Preemptive generation** — LLM interim transkript bilan oldindan ishga tushiriladi, final mos kelmasa javob tashlanadi. Interim'ga javob **aytilmaydi** (yuqoridagi qoida buzilmaydi) | LLM first token | `DIALOG_PREEMPTIVE` |
| **Semantik turn detection** (Smart Turn v3, ONNX) — mijoz *fikrini* tugatdimi degan savolga javob beradi va tugatmagan bo'lsa kutishni **uzaytiradi** (hech qachon qisqartirmaydi). O'zbek tilini qo'llamaydi — faqat ru-RU | STT endpointing | `TURN_DETECTOR` |
| **`min_words` barge-in filtri** — bot gapini kesib o'tgan "aha" yangi turn boshlamaydi, kesilgan javob davom ettiriladi | (latency emas — suhbat sifati) | `DIALOG_MIN_INTERRUPTION_WORDS` |

Bayroqlar yoqilgach jadvaldagi raqamlar `/actuator/prometheus` dagi `voice_turnaround`
p50/p95 bo'yicha qayta o'lchanadi.

---

## 8. Asterisk konfiguratsiyasi

### 8.1 Versiya va modullar

- Asterisk **20 LTS**
- Kerakli modullar: `res_ari`, `res_ari_channels`, `res_ari_applications`, `chan_pjsip`, `res_rtp_asterisk`
- Kodek: G.711 (`ulaw` / `alaw`) — transkodlash kerak emas, eng kam latency

### 8.2 pjsip.conf (trunk skeleti)

```ini
[trunk-provider]
type=registration
transport=transport-udp
outbound_auth=trunk-auth
server_uri=sip:PROVIDER_HOST
client_uri=sip:USERNAME@PROVIDER_HOST
retry_interval=60

[trunk-auth]
type=auth
auth_type=userpass
username=USERNAME
password=PASSWORD

[trunk-endpoint]
type=endpoint
transport=transport-udp
context=from-trunk
disallow=all
allow=ulaw
allow=alaw
direct_media=no
from_user=USERNAME
aors=trunk-aor

[trunk-aor]
type=aor
contact=sip:PROVIDER_HOST
```

### 8.3 extensions.conf

```ini
[outbound-ai]
exten => _X.,1,NoOp(AI outbound call to ${EXTEN})
 same => n,Stasis(uysot-voice-agent,${CALL_ID})
 same => n,Hangup()
```

### 8.4 externalMedia oqimi

```java
// 1. Qo'ng'iroq yaratish
Channel channel = ari.channels().originate("PJSIP/" + phone + "@trunk-endpoint")
        .setApp("uysot-voice-agent")
        .setAppArgs(callId)
        .execute();

// 2. StasisStart eventini kutish

// 3. Javob berish
ari.channels().answer(channel.getId()).execute();

// 4. Bridge yaratish
Bridge bridge = ari.bridges().create().setType("mixing").execute();

// 5. externalMedia kanal — audio bizning servisga keladi
Channel extMedia = ari.channels().externalMedia(
        "uysot-voice-agent",
        localIp + ":" + rtpPort,   // bizning Netty UDP listener
        "ulaw"
).setEncapsulation("rtp").setTransport("udp").execute();

// 6. Ikkalasini bridge ga qo'shish
ari.bridges().addChannel(bridge.getId(), channel.getId() + "," + extMedia.getId()).execute();
```

### 8.5 Muhim eventlar

| Event | Ma'nosi | Harakat |
|---|---|---|
| `StasisStart` | Kanal Stasis app ga kirdi | Sessiya yaratish, externalMedia ochish |
| `ChannelStateChange` → `Up` | Mijoz javob berdi | Suhbatni boshlash |
| `StasisEnd` | Kanal chiqdi | Sessiyani yopish |
| `ChannelHangupRequest` | Go'shak qo'yildi | Yakuniy xulosa, DB ga yozish |

### 8.6 Qo'ng'iroq holatini aniqlash

`Disposition` ni to'g'ri belgilash uchun Asterisk hangup cause code ishlatiladi:

| Cause | Ma'nosi | Disposition |
|---|---|---|
| 16 | Normal clearing | suhbat mazmuniga qarab |
| 17 | User busy | `NO_ANSWER` |
| 18/19 | No answer | `NO_ANSWER` |
| 21 | Call rejected | `REFUSED` |
| 1/22 | Unallocated number | `WRONG_NUMBER` |

**AMD (Answering Machine Detection)** — avtojavobni aniqlash. Asterisk `AMD()` funksiyasi bor, lekin ishonchsiz. MVP: birinchi 3 soniyada uzun uzluksiz nutq bo'lsa → `VOICEMAIL` deb belgilash.

---

## 9. Qo'ng'iroq oqimi (to'liq ketma-ketlik)

```
1.  dialer-service: campaign_target dan navbatdagi mijozni oladi
       (status=PENDING, next_attempt_at <= now, dial_window ichida)
2.  RabbitMQ → call.request queue ga xabar
3.  voice-agent-service xabarni oladi
4.  CRM dan mijoz konteksti olinadi (qarz summasi, muddat, ism, til)
5.  RTP port ajratiladi, Netty listener ochiladi
6.  ARI originate → qo'ng'iroq boshlanadi
7.  call_attempt yozuvi yaratiladi (status=IN_PROGRESS)
8.  Javob kutish (timeout 30s)
       ├─ javob yo'q → disposition=NO_ANSWER → 15-qadam
       └─ javob bor → davom
9.  externalMedia + bridge → audio oqim boshlanadi
10. STT sessiya ochiladi (til bo'yicha)
11. GREETING holati: bot salomlashadi
       "Assalomu alaykum. Bu Uysot kompaniyasining avtomatik
        xabar berish tizimi. Suhbat sifat nazorati uchun yozib olinadi."
12. FSM bo'yicha suhbat davom etadi
       - Har turn: STT final → LLM (streaming) → TTS → RTP
       - Tool call bo'lsa → DB ga yoziladi
       - VAD → barge-in
       - Timeout / max turns nazorati
13. Suhbat tugaydi (endCall tool yoki hangup)
14. Yakuniy LLM chaqiruv → CallSummary
15. call_attempt yangilanadi (ended_at, duration, disposition)
16. call_result yoziladi
17. Audio MinIO ga yuklanadi, recording_url yoziladi
18. CRM ga note yoziladi (summary + promised_date + reason)
19. campaign_target yangilanadi:
       - PROMISE_TO_PAY → status=DONE
       - NO_ANSWER + attempts < max → next_attempt_at = now + interval
       - attempts >= max → status=EXHAUSTED
20. Sessiya tozalanadi, RTP port bo'shatiladi
```

---

## 10. Amalga oshirish bosqichlari

> **Har bosqich alohida ishlashi va tekshirilishi SHART. Birdaniga hammasini yig'ish — xato.**

### Bosqich 0 — Infratuzilma
- [ ] Docker Compose: PostgreSQL, Redis, RabbitMQ, MinIO
- [ ] Gradle multi-module loyiha skeleti
- [ ] Flyway migratsiyalar (6-bo'limdagi schema)

### Bosqich 1 — Asterisk
- [ ] Asterisk 20 o'rnatish (Docker yoki alohida server)
- [ ] SIP trunk sozlash, registratsiya ishlashini tekshirish
- [ ] Qo'lda test qo'ng'iroq (softphone orqali) — ovoz ikki tomonlama
- [ ] ARI yoqish (`ari.conf`, user/password)

**Tekshiruv:** telefonga qo'lda qo'ng'iroq ketadi va ovoz eshitiladi.

### Bosqich 2 — ARI boshqaruv
- [ ] `ari4java` ulash, WebSocket connection
- [ ] StasisStart / StasisEnd eventlarni qabul qilish
- [ ] Spring Boot dan `originate` → qo'ng'iroq qilish
- [ ] `answer` / `hangup`

**Tekshiruv:** REST endpoint chaqirilsa, telefon jiringlaydi, javob bergach 5 soniyadan keyin uziladi.

### Bosqich 3 — RTP audio olish
- [ ] Netty UDP listener (RTP)
- [ ] RTP header parsing (seq, timestamp, payload type)
- [ ] `externalMedia` kanal + bridge
- [ ] G.711 decode → PCM16
- [ ] Kelgan audioni WAV faylga yozish

**Tekshiruv:** qo'ng'iroq qilib gapirasan, WAV fayl yaratiladi va unda ovozing tushunarli eshitiladi. **Bu eng muhim tekshiruv** — audio kelmasa keyingi hammasi behuda.

### Bosqich 4 — RTP audio yuborish
- [ ] PCM16 → G.711 encode
- [ ] RTP paket yasash (to'g'ri seq, timestamp, SSRC)
- [ ] 20ms interval bilan yuborish (pacing!)
- [ ] Oldindan tayyorlangan WAV faylni qo'ng'iroqda ijro etish

**Tekshiruv:** qo'ng'iroq qilganda telefonda tayyor audio eshitiladi, tez yoki sekin emas, uzilmaydi.

### Bosqich 5 — STT
- [ ] `SttProvider` interfeysi
- [ ] Google Cloud STT streaming implementatsiya
- [ ] 8k→16k resample
- [ ] Interim va final natijalarni ajratish
- [ ] Transkriptni log va DB ga yozish

**Tekshiruv:** qo'ng'iroqda o'zbekcha va ruscha gapirasan, log da matn to'g'ri chiqadi.

### Bosqich 6 — TTS
- [ ] `TtsProvider` interfeysi
- [ ] Google TTS (uz-UZ) implementatsiya
- [ ] Yandex SpeechKit (ru-RU) implementatsiya
- [ ] Language router
- [ ] Streaming → RTP

**Tekshiruv:** matn beriladi, telefonda tabiiy ovoz eshitiladi. **O'zbekcha sifatini alohida baholang.**

### Bosqich 7 — LLM dialog
- [ ] Spring AI Anthropic ulash
- [ ] FSM implementatsiyasi (DialogState enum + transition logic)
- [ ] System prompt (guardrails bilan)
- [ ] Tool calling (4.2 dagi tool'lar)
- [ ] Streaming + jumla chegarasini aniqlash

**Tekshiruv:** to'liq suhbat ishlaydi, bot savol beradi, javobni tushunadi, keyingi bosqichga o'tadi.

### Bosqich 8 — VAD va barge-in
- [ ] Silero VAD ONNX model yuklash
- [ ] Real-time VAD (20ms frame)
- [ ] TTS cancel + RTP buffer flush
- [ ] LLM ga "bo'lindim" konteksti

**Tekshiruv:** bot gapirayotganda gapirsang, darhol jim bo'ladi.

### Bosqich 9 — Yakuniy xulosa va CRM
- [ ] `CallSummary` structured output
- [ ] `call_result` ga yozish
- [ ] MinIO ga audio yuklash
- [ ] CRM API ga note yozish

**Tekshiruv:** suhbatdan keyin CRM da to'g'ri note paydo bo'ladi.

### Bosqich 10 — Dialer va kampaniya
- [ ] Campaign CRUD (REST API)
- [ ] Target ro'yxatini CRM dan yuklash
- [ ] RabbitMQ producer/consumer
- [ ] Scheduling (dial window, rate limiting)
- [ ] Retry logikasi
- [ ] Concurrency limiti (`max_concurrent_calls`)

**Tekshiruv:** kampaniya ishga tushiriladi, 5 ta test raqamga ketma-ket qo'ng'iroq ketadi.

### Bosqich 11 — Operatorga o'tkazish
- [ ] Asterisk queue sozlash
- [ ] `requestHumanTransfer` → bridge ni operatorga o'tkazish
- [ ] Operator ekranida kontekst ko'rsatish

### Bosqich 12 — Production tayyorgarlik
- [ ] Metrics (Micrometer): latency, STT/TTS xatolar, disposition taqsimoti
- [ ] Structured logging (call_id bo'yicha trace)
- [ ] Sticky routing (Redis session registry)
- [ ] Graceful shutdown (aktiv qo'ng'iroqlarni tugatib)
- [ ] Alerting (qo'ng'iroq muvaffaqiyat foizi tushsa)

---

## 11. Huquqiy va etik talablar

**Bular MVP ga kiritilishi shart, keyinga qoldirilmaydi:**

1. **Ogohlantirish** — suhbat boshida aytiladi:
   - Bu avtomatik tizim ekani
   - Suhbat yozib olinayotgani
2. **Qo'ng'iroq vaqti** — faqat `dial_window` ichida (default 07:00–23:00). Dam olish kunlari alohida sozlanadi.
3. **Yozuvlar** — MinIO da saqlanadi, saqlash muddati konfiguratsiyada. Nizoda dalil bo'ladi.
4. **Rad etish huquqi** — mijoz "boshqa qo'ng'iroq qilmang" desa, `do_not_call` flag qo'yiladi va keyingi kampaniyalarga tushmaydi.
5. **Tahdid taqiqi** — guardrails da (4.4).
6. **Operatorga o'tish** — mijoz so'rasa, majburiy imkoniyat bo'lishi kerak.

---

## 12. Konfiguratsiya namunasi

```yaml
voice-agent:
  asterisk:
    ari-url: ws://asterisk:8088/ari/events
    ari-user: ${ARI_USER}
    ari-password: ${ARI_PASSWORD}
    app-name: uysot-voice-agent
    trunk-endpoint: trunk-endpoint
  rtp:
    local-ip: ${RTP_LOCAL_IP}
    port-range-start: 20000
    port-range-end: 20500
    jitter-buffer-ms: 50
  stt:
    provider: google
    google:
      model: phone_call
      sample-rate: 8000
      enable-punctuation: true
    endpointing-silence-ms: 600
  tts:
    uz-UZ:
      provider: google
      voice: uz-UZ-Standard-A
    ru-RU:
      provider: yandex
      voice: alena
  llm:
    dialog-model: claude-haiku-4-5
    summary-model: claude-sonnet-4-5
    max-turns: 25
    temperature: 0.3
  call:
    answer-timeout-sec: 30
    max-duration-sec: 300
    max-concurrent: 20
  vad:
    model-path: /models/silero_vad.onnx
    speech-threshold: 0.5
    min-speech-ms: 200
```

---

## 13. Hal qilinmagan savollar

Boshlashdan oldin aniqlanishi kerak:

1. **SIP provayder** — kim? Trunk bormi? Avtomatik obzvon uchun ruxsat/cheklov bormi?
   O'zbekistonda ba'zi provayderlar buni cheklaydi — **loyiha boshlashdan oldin tekshiring**.
2. **Hajm** — kuniga nechta qo'ng'iroq, pik vaqtda nechta parallel?
   (bu specda ~20 parallel deb olingan)
3. **Qarzdorlik ma'lumoti** — Uysot CRM dami yoki tashqi tizimdan? Real-time so'raladimi
   (mijoz "men to'ladim" desa tekshirish uchun) yoki snapshot?
4. **Operator jamoasi bormi** — escalation kimga boradi?
5. **O'zbekcha TTS sifati** — Google `uz-UZ` ovozini tinglab tasdiqlash kerak.
   Sifat past bo'lsa, alternativa qidirish kerak (bu jiddiy risk).
6. **Yozuvlarni saqlash muddati** — huquqiy talab qanday?
