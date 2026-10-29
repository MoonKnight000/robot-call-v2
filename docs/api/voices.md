# TTS ovozlar katalogi va Dinamik Hissiyotlar

`uz.murodjon.robotcallv2.voice` · huquq: talab qilinmaydi (katalog har bir kirgan foydalanuvchiga ochiq)

Kampaniya qaysi ovoz bilan yaratilishi mumkinligining katalogi — `tts_voice`
jadvalida saqlanadi (migration bilan seed qilinadi), config fayl emas. Kampaniya
formasi ovoz tanlagichini shu yerdan to'ldiradi — shuning uchun operator
ko'radigan variantlar aynan `POST /api/campaigns`ning `ttsVoice` maydoni qabul
qilinadigan id'larning o'zi. Yaratish/o'chirish endpoint yo'q. Ovozni eshitib
ko'rish (`POST /api/tts/voices/{id}/preview`) va STT provayderni sinash
(`POST /api/stt/preview`) — [speech-preview.md](speech-preview.md).

**Avtomatik sinxronlash.** Ovozlar ro'yxati API'si bor provayderlarning ovozlari
seed'da emas — kuniga bir marta provayderning o'zidan so'rab olinadi
(`TtsVoiceSyncScheduler`, `voice-agent.voice-sync.*`):

| Provayder | Manba | Qaysi tillarda |
|---|---|---|
| `elevenlabs` | `GET /v2/voices` — akkauntdagi ovozlar (premade va Voice Library'dan qo'shilganlar) | har bir ovoz — har bir tilda (v4 hamma tilda gapiradi) |
| `gemini` | `GET /v1beta/voices` — prebuilt va akkauntda saqlangan ovozlar | ovozning `languageCode`i; ko'rsatilmagan bo'lsa — hamma tilda |
| `cartesia` | `GET /voices?is_owner=true` — faqat akkauntdagi ovozlar (ommaviy kutubxona emas) | ovozning o'z tili |

Vendor yangi ovoz qo'shsa, ertasi kuni ro'yxatda paydo bo'ladi;
olib tashlasa, ro'yxatdan chiqadi, lekin uni tanlab saqlangan agent shu ovozda
gapirishda davom etadi. Bunday ovozning `id`si `<provider>-tts-<vendor id>-<til>`
ko'rinishida (kichik harf, masalan `elevenlabs-tts-21m00tcm4tlvdq8ikwam-uz`), `name`
esa vendor id'ining o'zi. Faqat sozlangan tillar (`uz-UZ`, `ru-RU`, `en-US`, `kk-KZ`)
olinadi, har til alohida qator. Gemini TTS'ning qo'lda seed qilingan qatorlari
(`gemini-tts-kore-uz`) xuddi shu id'da, shuning uchun sinxronlash ularni o'zlashtiradi.
`yandex`da ovozlar ro'yxati API'si yo'q — uning to'liq ro'yxati seed'da. Ro'yxat API'si yo'q provayderlar
(`yandex`, `aisha`, `openai`, realtime engine'lar) avvalgidek seed qilinadi.

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

Bundan tashqari ro'yxat `?mode=CASCADE` / `?mode=REALTIME` / `?mode=HALF_CASCADE` query parametri bo'yicha
toraytiriladi, chunki ovoz nomlari ikki oila o'rtasida o'tmaydi:

| `mode` | Qaytadigan ovozlar |
|---|---|
| `CASCADE` | TTS provayderlarining ovozlari (`yandex`, `aisha`, `gemini`, `elevenlabs`, `cartesia`) |
| `REALTIME` | speech-to-speech engine'ining o'z ovozlari (`gemini-live`, `openai-realtime`, `qwen-omni`) |
| `HALF_CASCADE` | `REALTIME` bilan bir xil — mijozni bizning STT eshitadi, lekin engine o'z ovozida gapiradi |

> `mode` berilmasa ikkala oila ham qaytadi. Pipeline endi kompaniyaniki emas, **har bir
> agentniki** (`ai_agent.pipeline_mode`), shuning uchun agent tahrirlash ekrani o'sha
> agentning rejimini yuborishi kerak.

Oila **ichida** aralashtirish avvalgidek erkin: `TtsRouter` tanlangan ovozni
to'g'ridan-to'g'ri o'zining provayderi orqali gapiradi, shuning uchun bitta
kampaniya `yandex` va `aisha` ovozlarini yonma-yon ishlata oladi. Oiladan
tashqari ovoz esa qabul qilinmaydi — `POST /api/campaigns` uni
`TTS_VOICE_UNKNOWN` bilan rad etadi (agentning o'z `pipelineMode` iga qarab tekshiriladi), va agar eski kampaniyada shunday ovoz qolib
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
| `gender` | `FEMALE` / `MALE` / `NEUTRAL` yoki `null` (noma'lum). ElevenLabs, Cartesia va Gemini ovozlarida sinxron paytida provayder API'sidan olinadi, qo'lda kiritilganlarida seed'dan; klonlangan ovozda `null` |
| `age` | `YOUNG` / `MIDDLE_AGED` / `OLD` yoki `null`. Hozircha faqat ElevenLabs beradi, qolganlarida `null` |
| `companyId` | `null` — umumiy katalog ovozi; son — shu kompaniyaning klonlangan ovozi (faqat o'sha kompaniyaga ko'rinadi) |

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

## `POST /api/tts/voices/clone` — kompaniyaning o'z ovozini klonlash

**Rol:** `VOICE_EDIT`. `multipart/form-data`. Eng yaxshi operatorning yoki brend ovozini 1–5 ta yozuvdan
klonlaydi (ElevenLabs instant voice cloning). Yangi ovoz faqat shu kompaniyaga ko'rinadi va agentda
boshqa ovozlar kabi `id` si bilan tanlanadi (`CASCADE` / ElevenLabs).

| Maydon | Turi | Majburiy | Izoh |
|---|---|---|---|
| `name` | string (≤100) | ✅ | Agent formasida ko'rinadigan nom |
| `language` | string | ✅ | `uz-UZ`, `ru-RU` ko'rinishida — ovoz shu tildagi qo'ng'iroqlarda ishlatiladi |
| `consent` | boolean | ✅ | `true` bo'lishi shart: ovoz egasi klonlashga rozi. Audit jurnaliga yoziladi |
| `files` | fayl (1–5) | ✅ | Bitta odamning toza yozuvlari (`audio/*`: mp3, wav, m4a). Jami so'rov ≤ 5 MB; 1–2 daqiqa ovoz yetarli |

```bash
curl -X POST $BASE/api/tts/voices/clone -H "Authorization: Bearer $TOKEN" \
  -F name="Dilnoza — call-markaz" -F language=uz-UZ -F consent=true \
  -F files=@dilnoza-1.mp3 -F files=@dilnoza-2.mp3
```

**Response** — `TtsVoice`:

```json
{ "data": { "id": "clone-7-abc123xyz", "provider": "elevenlabs", "language": "uz-UZ", "name": "AbC123xyz", "label": "Dilnoza — call-markaz", "role": null, "companyId": 7 } }
```

| Status | `messageCode` | Qachon |
|---|---|---|
| 400 | `VOICE_CLONE_CONSENT_REQUIRED` | `consent` `true` emas |
| 400 | `AUDIO_UPLOAD_FILE_MISSING` | fayl yo'q yoki bo'sh |
| 400 | `VOICE_CLONE_TOO_MANY_SAMPLES` | 5 tadan ko'p fayl |
| 400 | `VOICE_CLONE_SAMPLE_NOT_AUDIO` | fayl audio emas |
| 400 | `VALIDATION_FAILED` | `name` / `language` noto'g'ri |
| 409 | `VOICE_CLONE_UNAVAILABLE` | serverda `ELEVENLABS_API_KEY` sozlanmagan |
| 409 | `VOICE_CLONE_LIMIT_REACHED` | kompaniyada allaqachon 5 ta klon bor |
| 502 | `VOICE_CLONE_FAILED` | ElevenLabs rad etdi yoki javob bermadi |

Klonlar ElevenLabs ovoz sinxronizatsiyasiga (`TtsVoiceSyncScheduler`) tushmaydi — aks holda boshqa
kompaniyalarga ham ko'rinib qolardi.

## `DELETE /api/tts/voices/{id}` — klonni o'chirish

**Rol:** `VOICE_EDIT`. Faqat o'z kompaniyasining klonini o'chiradi: ElevenLabs'dan o'chiriladi, katalogda
o'chiriladi (`active=false`; qator qoladi — agentlar va eski qo'ng'iroqlar id'ga ishora qiladi).
Umumiy katalog ovozi yoki boshqa kompaniyaning kloni — `404 TTS_VOICE_NOT_FOUND`. **Avval shu ovozdagi
agentlarni boshqa ovozga o'tkazing** — aks holda ularning qo'ng'iroqlari TTS fallback provayderiga
(sozlangan bo'lsa) o'tadi.

---

## 🎭 Dinamik Hissiyotlar va Moslashuvchan Ovoz (Voice Emotion Resolution)

> ⚠️ O'zbek Yandex ovozlarida (`nigora`, `zarina`) rol yo'q (`voice-agent.tts.yandex.voice-roles`) — ularda
> quyidagi rol tanlovi ovozni o'zgartirmaydi, faqat tezlik ishlaydi. O'zbek qo'ng'irog'ida jonlilikni agentning
> `speakingWarmth` sozlamasi (gap matni) beradi — [ai-agents.md](ai-agents.md).

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
