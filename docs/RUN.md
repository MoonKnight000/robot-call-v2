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

## API kaliti

`/api/**` va `/actuator/**` `X-Api-Key` sarlavhasini talab qiladi — `POST /api/calls`
real trunk orqali qo'ng'iroq qiladi, shuning uchun hech qachon ochiq qolmaydi.
Kalit `.env` dagi `API_KEY` dan olinadi (`openssl rand -hex 32` bilan yangilanadi).
Kalit qo'yilmasa app ishga tushadi, lekin hamma so'rovga `401` qaytaradi.

```powershell
$key = (Select-String -Path .env -Pattern '^API_KEY=(.*)$').Matches.Groups[1].Value

# kalitsiz -> 401
curl.exe -i -X POST "http://localhost:8080/api/calls?number=600"

# kalit bilan -> 200
curl.exe -X POST -H "X-Api-Key: $key" "http://localhost:8080/api/calls?number=600"
```

Test paneli (`http://localhost:8080/`) kalitsiz ochiladi, lekin yuqoridagi **Key**
maydoniga kalitni kiritish kerak — u `localStorage` da saqlanadi va har bir so'rovga
qo'shiladi.

### Faqat o'qish uchun kalit

`.env` dagi ixtiyoriy `READ_API_KEY` ikkinchi kalit beradi: u faqat `/api/reports/**`
va `/actuator/**` ni ochadi. Kundalik ishda kerak bo'ladigan narsa — natijalar,
transkriptlar, yozuvlar — shu kalit bilan olinadi, ya'ni ularni ko'rish uchun 50 000
abonentga qo'ng'iroq qila oladigan kalitni tarqatish shart emas.

```powershell
$read = "..."   # READ_API_KEY

# ruxsat: hisobotlar
curl.exe -s -H "X-Api-Key: $read" http://localhost:8080/api/reports/calls?limit=5

# rad etiladi (403): qo'ng'iroq qilish yoki kampaniya boshqarish
curl.exe -i -X POST -H "X-Api-Key: $read" "http://localhost:8080/api/calls?number=600"
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
|   | Asterisk | 5060/udp (SIP trunk), 5062/udp (SIP softphone), 8088 (ARI) |
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
curl.exe -X POST -H "X-Api-Key: $key" "http://localhost:8080/api/calls?number=600"

# 3. Chiquvchi, real raqam (trunk orqali) — `+` belgisisiz
curl.exe -X POST -H "X-Api-Key: $key" "http://localhost:8080/api/calls?number=998953692029"
```

3-4 xonali raqamlar softphone'ga, uzunroqlari trunk'ga yo'naltiriladi
(`voice-agent.asterisk.local-number-pattern`).

## TTS ovozini tanlash

Kampaniya yaratilayotganda bot qaysi ovozda gapirishi tanlanadi. Tanlov paneldagi
"TTS ovozi" ro'yxatidan (yoki `ttsVoice` maydonidan) keladi, ro'yxat esa serverdagi
`tts_voice` jadvalidan (migration V3 bilan seed qilingan). Ovoz o'zi bilan birga
provayderni ham belgilaydi; hech narsa tanlanmasa, eski sozlama bo'yicha
(`tts.provider` + `tts.<provider>.voices`) ishlaydi.

```powershell
# mavjud ovozlar (til bo'yicha filtr — ixtiyoriy)
curl.exe -H "X-Api-Key: $key" "http://localhost:8080/api/tts/voices?language=uz-UZ"

# kampaniyani tanlangan ovoz bilan yaratish (noto'g'ri id -> 400)
curl.exe -X POST -H "X-Api-Key: $key" -H "Content-Type: application/json" `
  -d '{\"name\":\"Iyul\",\"defaultLanguage\":\"uz-UZ\",\"ttsVoice\":\"nigora\"}' `
  http://localhost:8080/api/campaigns

# kampaniyagacha ovozni tinglab ko'rish (faol qo'ng'iroqda)
curl.exe -X POST -H "X-Api-Key: $key" `
  "http://localhost:8080/api/calls/$chid/say?text=Assalomu%20alaykum&language=uz-UZ&voice=nigora"
```

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
$key = "..."   # API_KEY
curl.exe -s -H "X-Api-Key: $key" http://localhost:8080/actuator/prometheus |
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
  (`VAD_MODEL_PATH`; Docker'da model image ichida, `/app/silero_vad.onnx`).
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
curl.exe -s -H "X-Api-Key: $key" http://localhost:8080/actuator/prometheus |
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

Byudjetdagi eng katta bo'lak — **mijoz jim bo'lgandan keyin STT "gap tugadi" deb
hisoblagunicha** o'tgan vaqt. Uni tezlashtirish uchun ikki yo'l bor va ikkalasi ham
transkript sifati bilan savdolashadi:

1. `STT_YANDEX_EOU_SENSITIVITY=HIGH` — SpeechKit'ning o'z detektorini tezlashtiradi.
   **Bu allaqachon sinalgan va qaytarilgan:** uz-UZ finallari bo'lak-bo'lak kela
   boshlagan (application.yml dagi izohga qarang).
2. `STT_ENDPOINTING=true` — qarorni o'zimiz qabul qilamiz: barge-in uchun ishlayotgan
   VAD "gap tugadi" deydi va SpeechKit'ga aytiladi (external EOU klassifikatori).
   Farqi shundaki, tez kesish **faqat qisqa javoblarga** qo'llanadi:
   `STT_ENDPOINTING_SHORT_UTTERANCE_MS` (1200 ms) dan qisqa gap
   `STT_ENDPOINTING_SHORT_SILENCE_MS` (400 ms) jimlikdan keyin yopiladi, undan uzunlari
   esa oldingidek to'liq `STT_VAD_POST_ROLL_MS` ni kutadi. Ya'ni "ha"/"yo'q" tez ketadi,
   shartnoma raqamini sekin aytayotgan odam esa bo'linmaydi.

`STT_ENDPOINTING` **default o'chiq** va uni faqat real uz-UZ qo'ng'iroqlarni tinglab
yoqish kerak. Yoqqandan keyin:

- `voice_stt_utterances_endpointed_total` mijoz navbatlari soniga yaqin bo'lsin. Ancha
  ko'p bo'lsa — gaplar bo'linyapti (`SHORT_SILENCE_MS` ni oshiring), ancha kam bo'lsa —
  gaplar qo'shilib ketyapti.
- Logda `utterance ran past ... ms — forcing end of utterance` chiqsa, VAD ishlamayapti
  (model yo'q yoki liniyada doimiy shovqin): bu xavfsizlik to'ri, normal holat emas.
- Google STT bilan bu sozlama ta'sir qilmaydi — uning API'sida bunday imkoniyat yo'q.

## Natijalar va hisobotlar

Panelning **6 · Natijalar** bo'limi, yoki to'g'ridan-to'g'ri:

```powershell
$key = "..."

# kampaniya natijasi: disposition taqsimoti, va'da foizi, o'rtacha davomiylik
curl.exe -s -H "X-Api-Key: $key" http://localhost:8080/api/reports/campaigns/1

# oxirgi qo'ng'iroqlar
curl.exe -s -H "X-Api-Key: $key" "http://localhost:8080/api/reports/calls?limit=20"

# bitta suhbat + to'liq transkript
curl.exe -s -H "X-Api-Key: $key" http://localhost:8080/api/reports/calls/12

# yozuvni yuklab olish (§11.3 — nizoda dalil)
curl.exe -s -H "X-Api-Key: $key" -o call-12.wav `
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
curl.exe -X POST -H "X-Api-Key: $key" -H "Content-Type: text/csv" `
  --data-binary "@targets.csv" http://localhost:8080/api/campaigns/1/targets/csv
```

```csv
clientId,phone,language,clientName,debtAmount,currency,dueDate,contractNumber
1001,998901234567,uz-UZ,Aziz Karimov,1500000,so'm,2026-07-01,UY-2026-00123
```

Javobdagi `unknownColumns` ga qarang: sarlavhadagi xato (masalan `debtamout`) o'sha
ustundagi faktlar promptga umuman tushmasligini bildiradi.
