# O'z serverida o'rnatish (on-prem) — banklar va MFO'lar uchun

Bank yoki MFO ma'lumotni o'z serveridan chiqarmasligi kerak bo'lsa (shaxsiy ma'lumotlar qonuni —
ma'lumot O'zbekistonda saqlanadi, ko'p banklar bulutga umuman chiqmaydi), platforma butunlay
mijozning serverida ko'tariladi. Bu hujjat: nima lokal ishlaydi, nima tashqariga chiqadi, qanday
o'rnatiladi va yangilanadi.

## 1. Nima lokal, nima tashqariga chiqadi

| Komponent | Qayerda | Izoh |
|---|---|---|
| Ilova (`app`), PostgreSQL, Redis, RabbitMQ, MinIO (yozuvlar), Asterisk | **Lokal** | `docker-compose.yml` dagi hamma servis. Yozuvlar, transkriptlar, mijozlar ro'yxati, CRM ma'lumoti — hammasi shu serverda |
| SIP trunk | Bankning o'z provayderi / PBX'i | Asterisk bank PBX'iga yoki operator trunkiga ulanadi |
| **LLM** (suhbat, xulosa) | Tashqi — yoki lokal OpenAI-mos server (§3) | Har gapda transkript matni va ssenariy faktlari yuboriladi |
| **STT** (nutq → matn) | Tashqi | Qo'ng'iroq ovozi yuboriladi |
| **TTS** (matn → nutq) | Tashqi | Bot aytadigan matn yuboriladi |
| CRM, webhook, SMS, Payme/Click | Ixtiyoriy | Faqat yoqilgan bo'lsa |

**Hozirgi cheklov:** o'zbek tili uchun **lokal STT/TTS dvigateli yo'q** — barcha STT/TTS provayderlari
(Yandex, Google, Gemini, OpenAI, Deepgram, ElevenLabs, Cartesia, Aisha) bulut xizmatlari. To'liq
"hech narsa tashqariga chiqmaydi" rejimi uchun lokal o'zbek STT/TTS kerak — bu alohida ish (§6).

## 2. Tashqi manzillar (firewall ruxsat ro'yxati)

Server faqat **yoqilgan** provayderlarga chiqishi kerak. Qolgan hamma chiquvchi trafikni yoping.

| Provayder | Nima uchun | Manzil (443) |
|---|---|---|
| Google Gemini | LLM, Gemini STT/TTS, Gemini Live | `generativelanguage.googleapis.com` |
| Google Cloud STT/TTS | `STT_PROVIDER=google` / `TTS_PROVIDER=google` | `speech.googleapis.com`, `texttospeech.googleapis.com`, `oauth2.googleapis.com` |
| Yandex SpeechKit | `STT_PROVIDER=yandex` / Yandex TTS | `stt.api.cloud.yandex.net`, `tts.api.cloud.yandex.net` |
| OpenAI | LLM (`LLM_CHAT_PROVIDER=openai`), OpenAI STT/TTS, Realtime | `api.openai.com` (yoki `OPENAI_BASE_URL`) |
| Deepgram | STT | `api.deepgram.com` |
| ElevenLabs / Cartesia | TTS | `api.elevenlabs.io` / `api.cartesia.ai` |
| Uysot CRM | `CRM_ENABLED=true` | `api.service.app.uysot.uz`, `service.app.uysot.uz` |
| Eskiz SMS | SMS | `notify.eskiz.uz` |

Har provayderning manzili `.env` dan almashtiriladi (`STT_*_URL`, `TTS_*_URL`, `OPENAI_BASE_URL`,
`REALTIME_*_URL` — `src/main/resources/config/speech.yml`, `dialog.yml`), shuning uchun bank
o'z proksi-shlyuzi orqali ham yo'naltira oladi.

Kompaniya qo'shgan tool, MCP server va webhook manzillari ham chiqadi — ular `PublicHttpClientFactory`
orqali ketadi (ichki tarmoq manzillari bloklanadi). Bank sozlasa, ularni ham ruxsat ro'yxatiga qo'shing.

## 3. LLM'ni lokal serverga yo'naltirish

`openai` slot'i istalgan OpenAI-mos serverga (vLLM, Ollama, LM Studio) ulanadi:

```dotenv
LLM_CHAT_PROVIDER=openai
OPENAI_BASE_URL=http://llm.bank.local:8000
OPENAI_API_KEY=local
LLM_DIALOG_MODEL=<lokal model id>
SUMMARY_MODEL=<lokal model id>
```

Ikkita shart:
- model id `ai_model` katalogida bo'lishi kerak — aks holda agent uni tanlay olmaydi. Lokal model
  qatorlarini migratsiya yoki `/api/ai-models` orqali qo'shing;
- `TurnTools#buildOptions` OpenAI'ning o'ziga moslangan (reasoning effort va h.k.). Lokal server bu
  parametrlarni qabul qilmasa, ishga tushirishdan oldin `web-test` va agent eval'lari bilan sinang.

Tool chaqirish (function calling) qo'llab-quvvatlanadigan model kerak — ssenariy bosqichlari va
tool'lar shunga tayanadi.

## 4. O'rnatish (internetsiz server)

Internetga ega mashinada image'larni yig'ing va ko'chiring:

```bash
docker compose build app
docker save -o robot-call-images.tar $(docker compose config --images)
# serverda
docker load -i robot-call-images.tar
```

Serverda `.env` ni `.env.example` dan yarating (hamma parollar majburiy, compose ularsiz ishga
tushmaydi) va on-prem override bilan ko'taring:

```bash
docker compose -f docker-compose.yml -f docker-compose.onprem.yml up -d --no-build
```

`docker-compose.onprem.yml` faqat farqlarni qo'shadi: hamma servis `restart: unless-stopped`, log
fayllari aylanadi (bank serverida disk to'lib qolmasligi uchun), ilova build qilinmaydi — yuklangan
image ishlatiladi. `asterisk-ip-sync` tashqi IP'ni so'ramaydi (`ifconfig.me` ga chiqmaydi): on-prem
serverning manzili statik, `pjsip.conf` dagi `external_*_address` qo'lda yoziladi.

Bazani migratsiya qilish alohida qadam emas: Flyway ilova ishga tushganda o'zi bajaradi.

## 5. Ishlatish

**Zaxira nusxa** (har kecha):

```bash
docker exec robot-call-postgres pg_dump -U "$DB_USER" -Fc "$DB_NAME" > backup-$(date +%F).dump
docker run --rm -v robot-call-v2_miniodata:/data -v "$PWD":/out alpine \
  tar czf /out/minio-$(date +%F).tgz -C /data .
```

**Yangilash:** yangi `robot-call-images.tar` ni `docker load`, so'ng xuddi shu `up -d --no-build`.
Graceful shutdown jonli qo'ng'iroqlarni 120 soniyagacha kutadi (`stop_grace_period`).

**Xavfsizlik:**
- Ochiq portlar faqat SIP (5060), RTP (10000–10200/udp) va ilova (8080); bazalar, brokerlar, AMI va ARI
  (8088) `127.0.0.1` ga bog'langan.
- 8080 oldiga TLS'li reverse proxy qo'ying; `CORS_ALLOWED_ORIGINS` ga faqat bank konsoli manzili.
- `ENCRYPTION_SECRET_KEY` (saqlangan maxfiy kalitlar) va `JWT_SECRET` ni bank o'zi yaratadi va saqlaydi.
- Yozuvlar va transkriptlarni saqlash muddati — agentning data policy'si ([api/ai-agents.md](api/ai-agents.md)).

## 6. To'liq lokal rejim uchun qolgan ish

| Ish | Nima uchun |
|---|---|
| Lokal o'zbek STT (masalan, o'zbek tiliga moslangan Whisper, ONNX/GPU) — `SttProvider` sifatida | Ovoz serverdan chiqmasligi uchun |
| Lokal o'zbek TTS — `TtsProvider` sifatida | Bot matni serverdan chiqmasligi uchun |
| Lokal LLM'ni `ai_model` katalogiga qo'shish va sinash | Hozir qo'lda (§3) |

Ikkalasi ham mavjud provayder interfeysiga (`agent/stt/SttProvider`, `agent/tts/TtsProvider`) yangi
implementatsiya sifatida qo'shiladi — ilovaning qolgan qismi o'zgarmaydi.
