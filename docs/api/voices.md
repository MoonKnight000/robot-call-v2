# TTS ovozlar katalogi va Dinamik Hissiyotlar

`uz.murodjon.robotcallv2.voice` · huquq: talab qilinmaydi (katalog har bir kirgan foydalanuvchiga ochiq)

Kampaniya qaysi ovoz bilan yaratilishi mumkinligining katalogi — `tts_voice`
jadvalida saqlanadi (migration bilan seed qilinadi), config fayl emas. Kampaniya
formasi ovoz tanlagichini shu yerdan to'ldiradi — shuning uchun operator
ko'radigan variantlar aynan `POST /api/campaigns`ning `ttsVoice` maydoni qabul
qilinadigan id'larning o'zi. Yaratish/o'chirish endpoint yo'q. Ovozni eshitib
ko'rish (`POST /api/tts/voices/{id}/preview`) va STT provayderni sinash
(`POST /api/stt/preview`) — [speech-preview.md](speech-preview.md).

`id` va `name` alohida ustunlar — shuning uchun bitta provayder ovozi bir nechta
katalog qatori bo'lib turishi mumkin, faqat `role` bilan farq qiladi (masalan
`id: "yulduz-whisper"` → `name: "yulduz"`, `role: "whisper"`).

Shuningdek, tizim **dinamik hissiyotlarga moslashuvchan rejimni (Emotion-Adaptive Voice)** qo'llab-quvvatlaydi. Bunda bot ssenariy bosqichi (masalan: `GREETING`da quvnoq, `DEBT_NOTICE`da qat'iy) va mijozning jonli kayfiyatiga qarab (`FRUSTRATED` bo'lganda muloyim/vazmin va sekinroq tempda) o'z ohangini avtomatik o'zgartiradi.

Umumiy javob shakli va xatolar uchun [README.md](README.md)ga qarang.

---

## `GET /api/tts/voices?language=...` — ovozlar ro'yxati

Query parametr: `language` (ixtiyoriy) — BCP-47 filtr (masalan `uz-UZ`); forma
kampaniya tilini allaqachon bilsa, faqat shu tilda gapira oladigan ovozlarni
ko'rsatish uchun.

Javob shu build'da **yoqilgan** (kredensiali sozlangan, shuning uchun Spring
kontekstiga registratsiya bo'lgan) provayderlarga tegishli ovozlar bilan
cheklanadi — kredensiali yo'q provayderning ovozi umuman qaytmaydi, chunki
`TtsRouter` baribir uni e'tiborsiz qoldirib standart provayderning o'z ovozida
gapiradi (`settings.md`).

Bundan tashqari ro'yxat **kompaniyaning `engine_config.mode` qiymati bo'yicha**
toraytiriladi, chunki ovoz nomlari ikki oila o'rtasida o'tmaydi:

| `engine_config.mode` | Qaytadigan ovozlar |
|---|---|
| `CASCADE` | TTS provayderlarining ovozlari (`yandex`, `aisha`, `google`) |
| `REALTIME` | speech-to-speech engine'ining o'z ovozlari (`gemini-live`, `openai-realtime`, `qwen-omni`) |

Oila **ichida** aralashtirish avvalgidek erkin: `TtsRouter` tanlangan ovozni
to'g'ridan-to'g'ri o'zining provayderi orqali gapiradi, shuning uchun bitta
kampaniya `yandex` va `aisha` ovozlarini yonma-yon ishlata oladi. Oiladan
tashqari ovoz esa qabul qilinmaydi — `POST /api/campaigns` uni
`TTS_VOICE_UNKNOWN` bilan rad etadi, va agar eski kampaniyada shunday ovoz qolib
ketgan bo'lsa, qo'ng'iroq paytida u e'tiborsiz qoldirilib engine o'zining
standart ovozida gapiradi (`RealtimeDialogEngine.voiceFor`).

**Response** — `List<TtsVoice>`:

```json
{
  "data": [
    { "id": "gulnoza", "provider": "aisha", "language": "uz-UZ", "name": "gulnoza", "label": "Gulnoza — o'zbek, moslashuvchan (avto-hissiyot)", "role": "neutral" },
    { "id": "gulnoza-cheerful", "provider": "aisha", "language": "uz-UZ", "name": "cheerful", "label": "Gulnoza — o'zbek, quvnoq", "role": "cheerful" },
    { "id": "gulnoza-sad", "provider": "aisha", "language": "uz-UZ", "name": "sad", "label": "Gulnoza — o'zbek, xafa/hamdard", "role": "sad" },
    { "id": "zamira", "provider": "yandex", "language": "uz-UZ", "name": "zamira", "label": "Zamira — o'zbek, ayol", "role": null },
    { "id": "yulduz", "provider": "yandex", "language": "uz-UZ", "name": "yulduz", "label": "Yulduz — o'zbek, ayol", "role": null },
    { "id": "nigora", "provider": "yandex", "language": "uz-UZ", "name": "nigora", "label": "Nigora — o'zbek, ayol", "role": null },
    { "id": "alena", "provider": "yandex", "language": "ru-RU", "name": "alena", "label": "Alena — rus, ayol", "role": "alena" },
    { "id": "filipp", "provider": "yandex", "language": "ru-RU", "name": "filipp", "label": "Filipp — rus, erkak", "role": "filipp" }
  ],
  "message": null, "messageCode": null, "accept": true, "errors": null
}
```

| Maydon | Izoh |
|---|---|
| `id` | kampaniyada saqlanadigan barqaror id (`CreateCampaignRequest.ttsVoice`ga shu qiymat yuboriladi) |
| `provider` | ovozni kim gapiradi: TTS provayderi (`yandex`, `aisha`, `google`) yoki realtime engine (`gemini-live`, `openai-realtime`, `qwen-omni`) |
| `language` | BCP-47; boshqa tildagi qo'ng'iroq bu ovozni e'tiborsiz qoldirib standart marshrutlashga qaytadi |
| `name` | provayder tomonidagi ovoz nomi (sintez so'roviga yuboriladi) |
| `label` | UI'da ko'rsatiladigan inson-o'qiy oladigan nom |
| `role` | ovozning boshlang'ich gapirish uslubi / roli |

`REALTIME` rejimidagi kompaniya uchun xuddi shu endpoint engine ovozlarini
qaytaradi (`R__seed_data.sql` bilan seed qilinadi):

```json
{
  "data": [
    { "id": "gemini-aoede-uz", "provider": "gemini-live", "language": "uz-UZ", "name": "Aoede", "label": "Aoede (ayol)", "role": null },
    { "id": "gemini-puck-uz", "provider": "gemini-live", "language": "uz-UZ", "name": "Puck", "label": "Puck (erkak)", "role": null }
  ],
  "message": null, "messageCode": null, "accept": true, "errors": null
}
```

Kampaniya ovozni ikkala rejimda ham bir xil tanlaydi: `campaign.ttsVoice` —
standart, `campaign_language_voice` — til bo'yicha ustuvor (`campaigns.md`).
Ya'ni bitta kampaniya `uz-UZ` uchun `Aoede`, `ru-RU` uchun `Puck` bilan
gapirishi mumkin.

---

## 🎭 Dinamik Hissiyotlar va Moslashuvchan Ovoz (Voice Emotion Resolution)

Agar [AI agentda](ai-agents.md) `"emotionAdaptiveVoice": true` (odatiy holatda `true`) bo'lsa yoki ssenariy bosqichida `emotion` belgilangan bo'lsa, `VoiceEmotionResolver` har bir dialog replikasida ovozning xarakteri va tezligini quyidagicha moslashtiradi:

1. **Ssenariy bosqichi bo'yicha moslashuv**:
   - `GREETING`, `CLOSING`, `OFFER` bosqichlarida: `CHEERFUL` (quvnoq, samimiy).
   - `DEBT_NOTICE`, `WARNING`, `DEMAND` bosqichlarida: `STRICT` (qat'iy, rasmiy).
   - `ESCALATE_TO_HUMAN`, `APOLOGY` bosqichlarida: `FRIENDLY` (hamdard, muloyim).
   - Agar `StageDef.emotion` da to'g'ridan-to'g'ri qiymat berilgan bo'lsa (`"cheerful"`, `"strict"`, `"friendly"`, `"whisper"`, `"sad"`), ustunlik aynan unga beriladi.

2. **Mijoz hissiyotiga ko'ra dinamik tanaffus va tezlik**:
   - `SentimentDetector` mijozning asabiylashganini yoki noroziligini (`FRUSTRATED`) aniqlasa:
     - Bot ovoz ohangi avtomatik `FRIENDLY` / `EMPATHETIC` ga o'zgaradi.
     - Gapirish tezligi `0.92x` ga tushirilib, xotirjam va muloyim intonatsiyada gapiriladi.
     - System promptga de-eskalatsiya direktivasi kiritiladi.
   - Mijoz tushunmaganida (`CONFUSED`):
     - Bot tezligi `0.96x` ga tushiriladi va `NEUTRAL` ohangda tushuntiradi.

3. **Provayderlar bo'yicha rollar moslashuvi**:
   - **Aisha (Gulnoza)**: `cheerful`, `sad`, `neutral` speaker_id lari.
   - **Yandex v3 (Zamira, Yulduz)**: `friendly`, `strict`, `whisper`, `neutral`. (Izoh: `nigora` ovozi gRPC darajasida rollarni qo'llab-quvvatlamagani uchun, unga `role: null` yuboriladi).
   - **Yandex Russian (Alena, Filipp, Jane)**: `good`, `evil`, `whisper`, `neutral`.
