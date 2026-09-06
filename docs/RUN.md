# Ishga tushirish (Docker, Windows)

Barcha buyruqlar loyiha ildizida (`robot-call-v2`), PowerShell'da bajariladi.
Windows/Docker Desktop'da RTP ishlashi uchun app Asterisk bilan bir Docker
tarmog'ida turishi kerak — shuning uchun `gradlew bootRun` emas, `docker compose`.

Birinchi marta: `.env.example` dan `.env` yarating va kalitlarni to'ldiring.

## Asosiy

```powershell
# app'ni qayta build qilib ko'tarish (infra allaqachon turgan bo'lsa ham yetadi)
docker compose up -d --build app

# loglarni kuzatish
docker compose logs -f app
```

Butun stack'ni noldan ko'tarish:

```powershell
docker compose up -d --build
docker compose logs -f app
```

## Tekshirish

```powershell
# xato bo'lmasligi kerak — hech narsa qaytarmasa, yaxshi
docker compose logs app | Select-String "initialDelay"

# @Scheduled task'lar o'z scheduler'ida ishlayaptimi (sched-1 / sched-2)
docker compose logs app | Select-String "sched-"

# app javob beryaptimi (health kalitsiz ham ochiq, lekin tafsilotsiz)
curl.exe http://localhost:8080/actuator/health
```

## Kirish tokeni

`/api/**` va `/actuator/**` `Authorization: Bearer <token>` talab qiladi —
`POST /api/calls` real trunk orqali qo'ng'iroq qiladi, shuning uchun hech qachon
ochiq qolmaydi. Token `POST /api/auth/login` dan olinadi; birinchi admin hisobi
`.env` dagi `BOOTSTRAP_ADMIN_*` bilan seed qilinadi. `JWT_SECRET` qo'yilmasa app
ishga tushadi, lekin hech kim login qila olmaydi va hamma so'rov `401` bo'ladi.

```powershell
$token = (curl.exe -s -X POST -H "Content-Type: application/json" `
  -d '{"username":"admin","password":"..."}' `
  http://localhost:8080/api/auth/login | ConvertFrom-Json).data.accessToken

# tokensiz -> 401
curl.exe -i -X POST "http://localhost:8080/api/calls?number=600"

# token bilan -> 200
curl.exe -X POST -H "Authorization: Bearer $token" "http://localhost:8080/api/calls?number=600"
```

Test paneli (`http://localhost:8080/`) tokensiz ochiladi, lekin yuqoridagi **Token**
maydoniga tokenni kiritish kerak — u `localStorage` da saqlanadi va har bir so'rovga
qo'shiladi.

### Faqat o'qish huquqi

Tashqi xizmatga yoki hisobot ko'radigan xodimga alohida foydalanuvchi ochiladi va
unga `VIEWER` roli beriladi: u `*_READ` permissionlarni oladi, ya'ni natijalar,
transkriptlar va yozuvlarni ko'ra oladi, lekin qo'ng'iroq qila olmaydi.

```powershell
$read = "..."   # VIEWER foydalanuvchining tokeni

# ruxsat: hisobotlar
curl.exe -s -H "Authorization: Bearer $read" http://localhost:8080/api/reports/calls?limit=5

# rad etiladi (403): qo'ng'iroq qilish yoki kampaniya boshqarish
curl.exe -i -X POST -H "Authorization: Bearer $read" "http://localhost:8080/api/calls?number=600"
```

Kim nima qilgani `audit_log` ga yoziladi (`GET /api/reports/audit`) — kampaniya
yaratish/boshlash, qo'ng'iroq originate qilish, do-not-call qo'yish, yozuv yuklab olish.

## Boshqarish

```powershell
docker compose restart app   # qayta build'siz restart
docker compose ps            # konteynerlar holati
docker compose stop app      # faqat app'ni to'xtatish
docker compose down          # hammasini to'xtatish (volume'lar saqlanadi)
docker compose down -v       # volume'lar bilan o'chirish (DB ma'lumotlari yo'qoladi)
```

## Portlar

|   | Servis   | Port                                                       |
|:--|----------|------------------------------------------------------------|
|   | app      | 8080                                                       |
|   | Asterisk | 5060/udp (SIP trunk), 5062/udp (SIP softphone), 8088 (ARI + brauzer SIP/WS) |
|   | RTP      | 10000-10200/udp                                            |
|   | Postgres | 5432                                                       |
|   | Redis    | 6379                                                       |
|   | RabbitMQ | 5672, 15672 (UI)                                           |
|   | MinIO    | 9000 (S3), 9001 (konsol)                                   |

## Softphone

MicroSIP / Zoiper sozlamalari:

|   | Maydon | Qiymat               |
|:--|--------|----------------------|
|   | server | `192.168.0.100:5062` |
|   | user   | `softphone`          |
|   | parol  | `softphone123`       |

Port **5062**, 5060 emas — 5060 trunk uchun band (`pjsip.conf` dagi ikkita transport).

Ro'yxatdan o'tgach ikki yo'nalishni ham sinash mumkin:

```powershell
# 1. Kiruvchi: softphone'dan 600 ni tering -> AI javob beradi

# 2. Chiquvchi: app o'zi softphone'ni chaqiradi (trunk daqiqasi sarflanmaydi)
curl.exe -X POST -H "Authorization: Bearer $token" "http://localhost:8080/api/calls?number=600"

# 3. Chiquvchi, real raqam (trunk orqali) — `+` belgisisiz
curl.exe -X POST -H "Authorization: Bearer $token" "http://localhost:8080/api/calls?number=998953692029"
```

3-4 xonali raqamlar softphone'ga, uzunroqlari trunk'ga yo'naltiriladi
(`voice-agent.asterisk.local-number-pattern`).

## Brauzerdan test (mikrofon ↔ AI, softphone'siz)

Kampaniya yoki ssenariyni brauzerning o'zida sinash: `POST /api/calls/web-test`
sessiya ochadi, brauzer WebRTC (SIP.js) bilan Asterisk'dagi `[webtest]` akkaunt
orqali `700` ni teradi — audio yo'li real qo'ng'iroq bilan bir xil, trunk daqiqasi
sarflanmaydi (`docs/api/calls.md` "web-test").

```powershell
# 1. Asterisk WebRTC'ga tayyormi — ikkalasi ham "Running" bo'lishi kerak
docker exec robot-call-asterisk asterisk -rx "module show like websocket"
docker exec robot-call-asterisk asterisk -rx "module show like srtp"
docker exec robot-call-asterisk asterisk -rx "pjsip show endpoint webtest"

# 2. Sessiya ochish (frontend'siz)
curl.exe -X POST -H "Authorization: Bearer $token" -H "Content-Type: application/json" `
  -d '{"campaignId": 1}' "http://localhost:8080/api/calls/web-test"
```

Frontend'siz to'liq sinov: `docs/web-test.html` ni Chrome'da oching (`file://`
ham bo'ladi), API manzili + token va `campaignId` ni kiriting, **Boshlash**.
AI salomlashadi, siz gapirasiz. Qo'ng'iroq `GET /api/calls/live` da `phone =
WEB-TEST` bilan ko'rinadi, `call_attempt` va WAV yozuvi odatdagidek paydo bo'ladi.

Ishlamasa, tartib bilan:

- **Mikrofon so'ralmadi** — `getUserMedia` faqat `https://`, `http://localhost`
  yoki `file://` sahifada ishlaydi. `https` sahifa `ws://` ni ham ochmaydi:
  `http.conf` da TLS yoqib `WEB_TEST_WS_URL=wss://<host>:8089/ws` bering.
- **INVITE 401/403** — `pjsip.conf` `[webtest-auth]` paroli bilan
  `WEB_TEST_SIP_PASSWORD` mos emas.
- **Ulanish bor, ovoz yo'q (RxPkt = 0)** — ICE kandidati konteyner IP'si bo'lib
  qolgan. `rtp.conf` `[ice_host_candidates]` dagi `172.29.0.10` docker-compose'dagi
  `ipv4_address` bilan, `192.168.0.100` esa hostning LAN IP'si bilan bir xil
  bo'lsin (`ipconfig`). `pjsip set logger on` bilan SDP'dagi `a=candidate`
  qatorlarini ko'ring.
- **Darhol uziladi, logda "unknown or expired session"** — `X-Web-Test` header
  yetib kelmagan yoki sessiya 5 daqiqadan eskirgan; yangi sessiya oching.

## TTS ovozini tanlash

Kampaniya yaratilayotganda bot qaysi ovozda gapirishi tanlanadi. Tanlov paneldagi
"TTS ovozi" ro'yxatidan (yoki `ttsVoice` maydonidan) keladi, ro'yxat esa serverdagi
`tts_voice` jadvalidan (migration V3 bilan seed qilingan). Ovoz o'zi bilan birga
provayderni ham belgilaydi; hech narsa tanlanmasa, eski sozlama bo'yicha
(`tts.provider` + `tts.<provider>.voices`) ishlaydi.

```powershell
# mavjud ovozlar (til bo'yicha filtr — ixtiyoriy)
curl.exe -H "Authorization: Bearer $token" "http://localhost:8080/api/tts/voices?language=uz-UZ"

# kampaniyani tanlangan ovoz bilan yaratish (noto'g'ri id -> 400)
curl.exe -X POST -H "Authorization: Bearer $token" -H "Content-Type: application/json" `
  -d '{\"name\":\"Iyul\",\"defaultLanguage\":\"uz-UZ\",\"ttsVoice\":\"nigora\"}' `
  http://localhost:8080/api/campaigns

# kampaniyagacha ovozni tinglab ko'rish (faol qo'ng'iroqda)
curl.exe -X POST -H "Authorization: Bearer $token" `
  "http://localhost:8080/api/calls/$chid/say?text=Assalomu%20alaykum&language=uz-UZ&voice=nigora"
```

Qo'ng'iroqsiz sinash: `POST /api/tts/voices/{id}/preview` (matn → WAV) va
`POST /api/stt/preview` (brauzer yozuvi → transkript) — `docs/api/voices.md`. STT preview
brauzerning `webm/opus` yozuvini serverda `ffmpeg` bilan dekodlaydi: Docker image'da u
o'rnatilgan, lokal (Docker'siz) run uchun `ffmpeg` PATH'da bo'lishi yoki `FFMPEG_PATH`
berilishi kerak, aks holda `AUDIO_TRANSCODER_UNAVAILABLE` (502) qaytadi.

Ovoz faqat o'z tilidagi qo'ng'iroqlarga qo'llanadi: uz-UZ ovozi tanlangan kampaniyada
`ru-RU` deb belgilangan nishon odatdagi yo'naltirish bo'yicha rus ovozida gapiradi.
Nishon tili — `campaign_target.language`.

## Trunk / SIP debug

SIP paketlari logi `pjsip.conf` dagi `[global] debug = yes` orqali doimiy yoqilgan —
CLI'da `pjsip set logger on` terish shart emas (u har restartda unutiladi).

```powershell
docker compose logs -f asterisk                                    # SIP trace
docker exec -it uysot-asterisk asterisk -rx "pjsip show registrations"
docker exec -it uysot-asterisk asterisk -rx "pjsip show contacts"
docker exec -it uysot-asterisk asterisk -rx "pjsip show channelstats"  # RxPkt/TxPkt
```

Ishlagach `debug = no` qilib qo'ying — aks holda softphone'ning har REGISTER'i logni to'ldiradi.

Tashqi IP, NAT, router'da port forward va "ovoz kelmayapti" muammolari uchun:
[NETWORK.md](NETWORK.md).

## Xarajat (token / belgi / audio-sekund) nazorati

Uchala provayder uchta xil narsani hisoblaydi: Gemini — token, Yandex TTS — belgi,
Yandex STT — oqimga uzatilgan audio-sekund. Har biri uchun tejash yoqilgan va har biri
metrika bilan o'lchanadi. Prometheus'dan ko'rish:

```powershell
$token = "..."   # POST /api/auth/login dan
curl.exe -s -H "Authorization: Bearer $token" http://localhost:8080/actuator/prometheus |
  Select-String "voice_llm_tokens|voice_tts_chars|voice_stt_audio|voice_tts_cache"
```

| Metrika                                               | Nimani ko'rsatadi                                                |
|-------------------------------------------------------|------------------------------------------------------------------|
| `voice_llm_tokens_prompt_total` / `_completion_total` | LLM'ga ketgan/qaytgan tokenlar                                   |
| `voice_llm_tokens_cached_total`                       | prompt'ning provayder keshidan (arzon narxda) o'qilgan qismi     |
| `voice_tts_chars_synthesized_total`                   | TTS'ga haqiqatan yuborilgan (to'langan) belgilar                 |
| `voice_tts_chars_saved_total`                         | kesh tufayli sotib olinmagan belgilar                            |
| `voice_stt_audio_seconds_sent_total`                  | STT'ga uzatilgan (to'langan) audio-sekundlar                     |
| `voice_stt_audio_seconds_skipped_total`               | VAD gating ushlab qolgan audio-sekundlar                         |
| `voice_dialog_barge_in_total`                         | mijoz botni bo'lgan javoblar soni                                |
| `voice_dialog_barge_in_false_total`                   | bo'lish sodir bo'ldi, lekin so'z kelmadi — javob davom ettirildi |
| `voice_dialog_echo_suppressed_total`                  | botning o'z gapi mijoz transkripti sifatida qaytgani             |

Nimaga qarash kerak:

- **`voice_dialog_barge_in_total` nolga yaqin** — bu "hech kim bo'lmaydi" degani emas,
  odatda bo'lish umuman ishlamayotganini bildiradi. Avval startup logini tekshiring:
  `Silero VAD ready (model=...)` bo'lishi shart, `barge-in disabled` emas
  (`VAD_MODEL_PATH`; Docker'da model image ichida, `/app/silero_vad.onnx`; IDE'dan
  ishga tushirilganda `models/silero_vad.onnx` o'qiladi — yuklab olish buyrug'i
  `.gitignore` ichida).
- **`voice_dialog_barge_in_false_total` `_barge_in_total` ning yarmidan ko'pi** — VAD
  shovqinga ishlayapti yoki liniyada aks-sado bor. `VAD_MIN_SPEECH_MS` ni oshiring
  (350 → 450) yoki `VAD_THRESHOLD` ni. Har bir noto'g'ri bo'lish mijozga
  `DIALOG_FALSE_INTERRUPTION_TIMEOUT_MS` chamasi pauza sifatida eshitiladi.
- **`voice_dialog_echo_suppressed_total` noldan katta** — mijozning telefoni karnayda va
  operator tomonida akustik aks-sado bekor qilish (AEC) yo'q: STT botning o'z gapini
  mijoznikidek yozib beryapti. Bu yerdagi filtr faqat halqani to'xtatadi (bot o'ziga
  javob bermaydi) — haqiqiy yechim Asterisk tomonida. Transkriptda AGENT gaplari CLIENT
  qatorlarida takrorlanayotgan bo'lsa, shu holat.

- **`voice_llm_tokens_cached_total` nol bo'lib qolsa** — so'rov prefiksi buzilyapti.
  Prefiks = system prompt + tarix; bosqich/holat matni tarixdan *keyin* yuboriladi
  (`SystemPromptFactory`). System prompt'ga har turnda o'zgaradigan narsa qo'shilsa,
  kesh butunlay o'chadi.
- **`voice_stt_audio_seconds_skipped_total` nol** — VAD modeli yo'q (`VAD_MODEL_PATH`)
  yoki liniyada doimiy shovqin bor; gating ishlamayapti, lekin zarar ham yo'q.
- **Turn osilib qolsa (final transcript kelmasa)** — `STT_VAD_POST_ROLL_MS` ni oshiring
  (provayderning end-of-utterance detektori shu jimlikni eshitishi kerak) yoki
  `STT_VAD_GATING=false` bilan o'chiring.
- **Birinchi qo'ng'iroqda ovoz kechiksa** — startupdagi TTS warm-up logini ko'ring:
  `TTS warm-up: N line(s) cached in ... ms`. `failed` bo'lsa kalit/voice sozlamasi
  noto'g'ri. Warm-up har kompaniyaning **o'z ovoz sozlamalari** (tezlik/ton/provayder)
  bilan sintez qiladi — bular kesh kalitining bir qismi, shuning uchun sozlamani
  o'zgartirgan kompaniya uchun bir marta qayta sintez bo'ladi.
- **`voice_tts_failovers_total` o'sib turibdi** — asosiy TTS provayder yiqilgan va
  gaplarni ikkinchisi aytyapti (logda `TTS provider ... failed ... goes to ... instead`).
  Qo'ng'iroq buzilmaydi, lekin mijoz kampaniya tanlagan ovozni emas, o'rinbosarning
  standart ovozini eshitadi va belgi narxi ham boshqa provayderniki bo'ladi. Yiqilgan
  provayder 60 soniya chetlab o'tiladi, keyin o'zi qayta sinaladi.

Sozlamalar `.env.example` dagi "Cost controls" bo'limida.

Bundan tashqari ikkita qattiq chegara bor:

- **`DIALOG_MAX_TOKENS_PER_CALL`** — bitta qo'ng'iroq sarflaydigan token budjeti. Turn
  va davomiylik chegaralari o'zini tutgan qo'ng'iroqni cheklaydi; bu esa tutmaganini —
  sikldagi model har turnda butun kontekstni qayta yuboradi va turn soni normal
  ko'ringani holda hisob o'sadi. Oshsa bot xayrlashib qo'ng'iroqni yopadi.
- **Kampaniyaning `dailyCallCap`** — kuniga nechta qo'ng'iroq. Hisob Redis'da
  (`dialer:campaign:{id}:{sana}`) va *dispatch* bo'yicha yuritiladi, `call_attempt`
  bo'yicha emas: javobsiz qo'ng'iroq attempt yozuvi yaratmaydi, lekin trunk daqiqasini
  sarflaydi.

## Audio yo'lining sifati (RTP)

"Bot meni eshitmadi" degan shikoyatning uch xil sababi bor va ular tashqaridan bir xil
ko'rinadi: tarmoq audioni yo'qotgan, mijoz jim turgan, yoki STT final qaytarmagan. Har
qo'ng'iroq oxirida kiruvchi RTP oqimi (RFC 3550) hisoblanadi va logda bitta qator
bo'lib chiqadi:

```
[PJSIP/trunk-0000001] inbound RTP: 1487 packets, 0% loss, jitter 3ms
```

```powershell
curl.exe -s -H "Authorization: Bearer $token" http://localhost:8080/actuator/prometheus |
  Select-String "voice_rtp"
```

| Metrika | Nimani ko'rsatadi |
|---|---|
| `voice_rtp_packets_received_total` / `_lost_total` | kelgan / ketma-ketlik bo'yicha umuman kelmagan paketlar |
| `voice_rtp_packets_reordered_total` | kech yoki ikki marta kelgan paketlar |
| `voice_rtp_calls_silent_total` | bitta ham kiruvchi paket kelmagan qo'ng'iroqlar |
| `voice_rtp_jitter` / `voice_rtp_loss` | har qo'ng'iroq uchun jitter (ms) va yo'qotish (%) taqsimoti |

Nimaga qarash kerak:

- **`voice_rtp_calls_silent_total` nolda emas** — media yo'li umuman qurilmagan. Logda
  `ALERT: no inbound RTP at all on call ...` chiqadi: `RTP_LOCAL_IP` Asterisk tomondan
  erishilmayapti yoki externalMedia porti yopiq ([NETWORK.md](NETWORK.md)).
- **5% dan ortiq yo'qotish** — logda `poor inbound RTP` ogohlantirishi. Bunday
  qo'ng'iroqning transkripti ishonchsiz: STT eshitilmagan so'zlarni "to'ldiradi", va
  disposition ham shunga qarab noto'g'ri chiqadi. Trunk/tarmoqni tekshiring, LLM
  promptini emas.
- **Jitter 30-40 ms dan oshsa** — `RTP_EVENT_LOOP_THREADS` ni oshiring: bitta Netty
  threadi barcha qo'ng'iroqlarning ham kiruvchi paketini, ham 20 ms pacer'ini
  tortayotgan bo'lishi mumkin.

## Javob tezligi (turnaround)

`voice_turnaround_latency` — mijoz gapirib bo'lgandan botning birinchi tovushi simga
chiqqunicha o'tgan vaqt. §1.3 byudjeti: **p95 < 1000 ms**. Endi buni kimdir kutib
o'tirmaydi — `AlertingService` har 5 daqiqada tekshiradi va oshsa logga yozadi:

```
ALERT: turnaround p95 1840ms over the last 64 turns exceeds the 1000ms budget (§1.3)
```

Oyna aylanma (Micrometer), ya'ni "hozir qanday" degan savolga javob beradi; kam turnli
oynaga baho berilmaydi (`ALERTING_TURNAROUND_MIN_TURNS`, default 30).

### Kutish qayerga ketgani — har turnda bitta qator

p95 raqami mijoz qancha kutganini aytadi, **nimani** kutganini aytmaydi. Buni har turnda
INFO darajasida chiqadigan qator aytadi (`agent/metrics/TurnLatency`). Qatorlar konsoldan
tashqari `logs/app.log` ga ham yoziladi (14 kun saqlanadi), shuning uchun qo'ng'iroqdan
keyin ham olish mumkin:

```powershell
Select-String "turn latency" logs\app.log
```

Qator ko'rinishi:

```
[ch-1] turn latency: eou=1180ms stt_final=240ms llm_ttft=1450ms tts_ttfb=380ms first_audio=3250ms
[ch-1] speculation: hit after 7 interim(s) — the reply was already written
```

- `eou` — mijoz gapirib bo'lgan, lekin STT hali "tugadi" demagan vaqt. Bu **default
  yo'lda eng katta bo'lak**; `STT_YANDEX_EOU_MAX_PAUSE_MS` shuni boshqaradi.
- `llm_ttft` — so'rov ketdi → birinchi token. `speculation: hit` bo'lsa **0** deb
  yoziladi (javob EOU pauzasi ichida yozilgan).
- `tts_ttfb` — birinchi jumla sintezga ketdi → birinchi PCM. `DIALOG_PREEMPTIVE_TTS=true`
  bo'lsa va guess tegsa, bu ham keshdan ~0 bo'ladi.
- `first_audio` — mijozning butun jimligi. Sekin qo'ng'iroqni tekshirganda **faqat
  shuni** boshqa raqamlar bilan solishtiring.

`speculation: miss` yoki `speculation: none` ko'p chiqsa — `eou` va `llm_ttft` **qo'shilib**
to'lanadi, ya'ni 4-6 soniya. Miss sababini o'sha qator o'zi yozadi (nimaga taxmin qilingan
va final nima degan).

Byudjetdagi eng katta bo'lak — **mijoz jim bo'lgandan keyin STT "gap tugadi" deb
hisoblagunicha** o'tgan vaqt. Uni tezlashtirish uchun uch yo'l bor va uchalasi ham
transkript sifati bilan savdolashadi:

1. `STT_YANDEX_EOU_MAX_PAUSE_MS` — SpeechKit qancha pauzani "gap tugadi" deb hisoblashi.
   Default **900 ms**. Bu default yo'ldagi butun endpointing byudjeti. Pastga tushirish
   mumkin, lekin 500 sinalgan va qaytarilgan: mijozni 0.60 s va 0.78 s pauzalarda gap
   o'rtasida kesib qo'ygan (`config/speech.yml` dagi izohga qarang). SpeechKit
   [500, 5000] dan tashqarisini qabul qilmaydi — 500 dan past qiymat har qo'ng'iroqda
   sessiyani `INVALID_ARGUMENT` bilan o'ldiradi.
2. `STT_YANDEX_EOU_SENSITIVITY=HIGH` — SpeechKit'ning o'z detektorini tezlashtiradi.
   **Bu allaqachon sinalgan va qaytarilgan:** uz-UZ finallari bo'lak-bo'lak kela
   boshlagan (application.yml dagi izohga qarang).
3. **O'z gate'imiz — hozir default shu** (2026-09-04). Qarorni o'zimiz qabul qilamiz:
   barge-in uchun ishlayotgan VAD "gap tugadi" deydi va SpeechKit'ga aytiladi (external
   EOU klassifikatori), shunda `STT_YANDEX_EOU_MAX_PAUSE_MS` **umuman o'qilmaydi**.
   Default qiymatlar:

   ```
   STT_VAD_GATING=true                             # gate — busiz qolganlari ta'sirsiz
   STT_ENDPOINTING=true                            # external EOU klassifikatori
   STT_ENDPOINTING_DYNAMIC=true                    # mijozning pauzasiga moslashish
   STT_VAD_POST_ROLL_MS=1200                       # byudjetning tepasi (700 dan oshirildi)
   STT_ENDPOINTING_DYNAMIC_MIN_POST_ROLL_MS=600    # pasti — tez gapiradigan mijozda
   ```

   Tez kesish **faqat qisqa javoblarga** qo'llanadi:
   `STT_ENDPOINTING_SHORT_UTTERANCE_MS` (1500 ms) dan qisqa gap
   `STT_ENDPOINTING_SHORT_SILENCE_MS` (500 ms) jimlikdan keyin yopiladi, undan uzunlari
   esa to'liq `STT_VAD_POST_ROLL_MS` ni kutadi. Ya'ni "ha"/"yo'q" tez ketadi,
   shartnoma raqamini sekin aytayotgan odam esa bo'linmaydi.

   Gate yopilib EOU yuborilgach SpeechKit'ning final'i yana 500–1100 ms kechikadi
   (2026-09-05 yozuvlarida o'lchandi). `STT_ENDPOINTING_FINAL_GRACE_MS` (250) dan keyin
   final kelmasa oxirgi interim final deb olinadi va turn shunda boshlanadi; keyin kelgan
   final tashlab yuboriladi (`voice_stt_finals_promoted_total`, logda
   `using the last interim` / `late final dropped`). 0 — avvalgidek final kutiladi.

   **Shart:** `VAD_MODEL_PATH` o'rnatilgan bo'lishi kerak. Bo'lmasa VAD ko'tarilmaydi,
   gate qurilmaydi va bu blok jimgina ta'sirsiz qoladi — startda
   `Silero VAD model path not set ...; barge-in disabled` degan WARN chiqadi. Bu holda
   qo'ng'iroq 1-yo'lga (`STT_YANDEX_EOU_MAX_PAUSE_MS=900`) qaytadi.

   **Orqaga qaytarish:** `STT_VAD_GATING=false` — bitta env, deploy shart emas.

3-yo'l yangi default, shuning uchun real uz-UZ qo'ng'iroqlarni tinglab tasdiqlash kerak.
Kuzatiladigan narsalar:

- `voice_stt_utterances_endpointed_total` mijoz navbatlari soniga yaqin bo'lsin. Ancha
  ko'p bo'lsa — gaplar bo'linyapti (`SHORT_SILENCE_MS` ni oshiring), ancha kam bo'lsa —
  gaplar qo'shilib ketyapti.
- Logda `utterance ran past ... ms — forcing end of utterance` chiqsa, VAD ishlamayapti
  (model yo'q yoki liniyada doimiy shovqin): bu xavfsizlik to'ri, normal holat emas.
- Google STT bilan bu sozlama ta'sir qilmaydi — uning API'sida bunday imkoniyat yo'q.

## Natijalar va hisobotlar

Panelning **6 · Natijalar** bo'limi, yoki to'g'ridan-to'g'ri:

```powershell
$token = "..."   # POST /api/auth/login dan

# kampaniya natijasi: disposition taqsimoti, va'da foizi, o'rtacha davomiylik
curl.exe -s -H "Authorization: Bearer $token" http://localhost:8080/api/reports/campaigns/1

# oxirgi qo'ng'iroqlar
curl.exe -s -H "Authorization: Bearer $token" "http://localhost:8080/api/reports/calls?limit=20"

# bitta suhbat + to'liq transkript
curl.exe -s -H "Authorization: Bearer $token" http://localhost:8080/api/reports/calls/12

# yozuvni yuklab olish (§11.3 — nizoda dalil)
curl.exe -s -H "Authorization: Bearer $token" -o call-12.wav `
  http://localhost:8080/api/reports/calls/12/recording
```

Yozuv MinIO yoqilgan bo'lsa u yerga redirect qilinadi, aks holda diskdagi fayl
beriladi (`recording_url` `file:` bilan boshlanadi). `STORAGE_RETENTION_DAYS` o'tgach
yozuv ham, transkript ham o'chadi — `call_attempt`/`call_result` esa qoladi.

Fayl **stereo**: chap kanal — mijoz, o'ng kanal — botning o'z ovozi (8 kHz, 16-bit).
Ikkalasi aralashtirilmagan, shuning uchun ikkovi bir vaqtda gapirganda ham kim nima
deganini ajratib bo'ladi. Bitta kanalni tinglash uchun pleyerda balansni buring yoki
`ffmpeg -i call-12.wav -map_channel 0.0.0 mijoz.wav -map_channel 0.0.1 bot.wav`.

## Nishonlarni CSV bilan yuklash

Bittalab `POST .../targets` o'rniga CRM eksportini to'g'ridan-to'g'ri yuboring.
Ustunlar **nom bo'yicha** topiladi (tartib muhim emas), noto'g'ri satrlar alohida
qaytariladi va qolganlari yuklanadi:

```powershell
curl.exe -X POST -H "Authorization: Bearer $token" -H "Content-Type: text/csv" `
  --data-binary "@targets.csv" http://localhost:8080/api/campaigns/1/targets/csv
```

```csv
clientId,phone,language,clientName,debtAmount,currency,dueDate,contractNumber
1001,998901234567,uz-UZ,Aziz Karimov,1500000,so'm,2026-07-01,UY-2026-00123
```

Javobdagi `unknownColumns` ga qarang: sarlavhadagi xato (masalan `debtamout`) o'sha
ustundagi faktlar promptga umuman tushmasligini bildiradi.
