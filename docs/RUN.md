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
katalogdan — `voice-agent.tts.catalog` (`application.yml`). Ovoz o'zi bilan birga
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

| Metrika | Nimani ko'rsatadi |
|---|---|
| `voice_llm_tokens_prompt_total` / `_completion_total` | LLM'ga ketgan/qaytgan tokenlar |
| `voice_llm_tokens_cached_total` | prompt'ning provayder keshidan (arzon narxda) o'qilgan qismi |
| `voice_tts_chars_synthesized_total` | TTS'ga haqiqatan yuborilgan (to'langan) belgilar |
| `voice_tts_chars_saved_total` | kesh tufayli sotib olinmagan belgilar |
| `voice_stt_audio_seconds_sent_total` | STT'ga uzatilgan (to'langan) audio-sekundlar |
| `voice_stt_audio_seconds_skipped_total` | VAD gating ushlab qolgan audio-sekundlar |

Nimaga qarash kerak:

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
  noto'g'ri.

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
