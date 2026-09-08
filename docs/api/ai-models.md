# Model katalogi (LLM · STT · TTS)

`uz.murodjon.robotcallv2.aimodel` · huquq: talab qilinmaydi (katalog har bir kirgan foydalanuvchiga ochiq)

Kompaniya sozlamasi (`PUT /api/settings/ai-model` → `model`) va AI agentning to'rtta model
maydoni (`POST /api/ai-agents` → `llmModel`, `fastLlmModel`, `sttModel`, `ttsModel`) qaysi id'lar bilan
saqlanishi mumkinligining katalogi — `ai_model` jadvalida saqlanadi (migration bilan seed
qilinadi), config fayl emas. Yaratish/o'chirish endpoint yo'q.

`kind` ustuni uch oilani ajratadi va ular hech qachon aralashmaydi: `nova-3` — Deepgram
tanigichi, `gemini-3.1-flash-tts-preview` — sintezator, `gemini-3.8-flash` — matn modeli.

Katalog aynan shu formalarni to'ldirish uchun bor: ilgari model erkin matn edi va xato
yozilgan id qo'ng'iroq paytida — mijoz go'shakni ko'targanidan keyin — bilinardi. Endi
forma shu endpointdan to'ladi va saqlash ham shu qatorlarga solishtiriladi, shuning uchun
noma'lum id `400 AI_MODEL_UNKNOWN` (`sttModel` uchun `STT_MODEL_UNKNOWN`, `ttsModel` uchun
`TTS_MODEL_UNKNOWN`) bo'lib qaytadi.

Umumiy javob shakli va xatolar uchun [README.md](README.md)ga qarang.

---

## `GET /api/ai-models` — mavjud modellar

Query parametrlar: `?kind=LLM|STT|TTS` (berilmasa `LLM`) va `?mode=CASCADE|REALTIME`
(ixtiyoriy). Ro'yxat uch marta toraytiriladi:

1. **`kind` bo'yicha** — agentning model maydonlari uch xil oilaga tegishli. Agent
   formasi `llmModel` va `fastLlmModel` uchun `?kind=LLM`, `sttModel` uchun `?kind=STT`,
   `ttsModel` uchun `?kind=TTS` so'raydi — ikkala LLM maydoni bitta ro'yxatdan
   to'ldiriladi. STT va TTS qatorlari doim `CASCADE`: realtime engine o'zi
   eshitadi va gapiradi, unda alohida tanlanadigan model yo'q.

2. **Berilgan `mode` bo'yicha** — `CASCADE` model id'lari (matn LLM) va `REALTIME`
   id'lari (speech-to-speech engine) o'zaro almashmaydi, provayder biri ikkinchisini
   umuman tanimaydi. `mode` berilmasa ikkalasi ham qaytadi.

   > Ilgari bu kompaniyaning `engine_config.mode` i bo'yicha toraytirilardi. Pipeline
   > endi **har bir agentning** sozlamasi (`ai_agent.pipeline_mode`), shuning uchun agent
   > tahrirlash ekrani o'sha agentning rejimini `mode` parametri bilan yuborishi kerak.
3. **Shu build haqiqatan yeta oladigan provayderlar bo'yicha** — `LLM`+`REALTIME` uchun
   registratsiyadan o'tgan engine (`RealtimeProviderRegistry`), `LLM`+`CASCADE` uchun
   `spring.ai.model.chat` bilan sozlangan yagona chat provayder, `STT` va `TTS` uchun esa
   API kaliti sozlangani uchun bean bo'lgan nutq provayderi (`SttProviderSelector` /
   `TtsProviderSelector` — `GET /api/ai-agents/engine-options` dagi ro'yxatlar bilan bir
   manba). Gemini kaliti bilan ishlayotgan deployment OpenAI model id'ini ham, Deepgram
   `nova-3` ini ham ko'rsatmaydi, chunki u qatorni qabul qilsa ham, birinchi turnda
   yiqilar edi.

| `kind` + `mode` | Qaytadigan modellar |
|---|---|
| `LLM` + `CASCADE` | `spring.ai.model.chat` provayderining matn modellari (`google-genai` → `gemini-3.8-flash`, `gemini-3.6-flash`, `gemini-3.5-flash-lite`; `openai` → `gpt-5.6-luna`, `gpt-5.6-terra`, `gpt-5.6-sol`, `gpt-6-astra`, `gpt-5.5`, `gpt-5.4-mini`) |
| `LLM` + `REALTIME` | shu build'da yoqilgan speech-to-speech engine modellari (`gemini-live` → `gemini-3.1-flash-live-preview`, `gemini-2.5-flash-native-audio-latest`; `openai-realtime` → `gpt-realtime-2.1`, `gpt-realtime-2.1-mini`; `qwen-omni`, `moshi`, `pipecat`) |
| `STT` | kaliti sozlangan STT provayderining tanish modellari (`gemini` → `gemini-3.5-transcribe-live`, `deepgram` → `nova-3`, `yandex` → `general`, `openai` → `gpt-live-transcribe`, `gpt-transcribe`) |
| `TTS` | kaliti sozlangan TTS provayderining modellari (`gemini` → `gemini-3.1-flash-tts-preview`, `cartesia` → `sonic-multilingual`, `openai` → `gpt-4o-mini-tts`) |

Aisha va Yandex TTS da tanlanadigan model yo'q — ular uchun `ttsModel` bo'sh qoldiriladi.

`STT`/`TTS` id'lari provayderi bilan juft saqlanadi: agentga `sttModel` yozilsa,
`sttProvider` o'sha qatorning `provider` i bo'lishi shart, aks holda
`400 STT_MODEL_PROVIDER_MISMATCH` (`TTS_MODEL_PROVIDER_MISMATCH`) qaytadi.

`LLM` + `REALTIME` id'lari ham xuddi shunday juft: `pipelineMode=REALTIME` bo'lgan agentga
`llmModel` yozilsa, uning `provider` i agentning `realtimeProvider` i bilan bir xil bo'lishi
shart — aks holda `400 REALTIME_MODEL_PROVIDER_MISMATCH`. Sabab: bu id qo'ng'iroq ochilganda
tanlangan engine'ga o'zgarishsiz uzatiladi (`RealtimeCallConfig.modelOr(...)`), shuning uchun
Gemini Live agentiga saqlangan `gpt-realtime-2.1` — boshlanmaydigan qo'ng'iroq.
Shu sababli formada **avval engine, keyin model** tanlanadi: model ro'yxati
`GET /api/ai-models?kind=LLM&mode=REALTIME` javobidan `provider` bo'yicha filtrlanadi.

**Response** — `List<AiModel>`:

```json
{
  "accept": true,
  "data": [
    { "id": "gemini-3.5-flash-lite", "kind": "LLM", "provider": "google-genai", "mode": "CASCADE", "label": "Gemini 3.5 Flash Lite — eng arzon, qisqa javoblar uchun" },
    { "id": "gemini-3.6-flash", "kind": "LLM", "provider": "google-genai", "mode": "CASCADE", "label": "Gemini 3.6 Flash — oldingi avlod" },
    { "id": "gemini-3.8-flash", "kind": "LLM", "provider": "google-genai", "mode": "CASCADE", "label": "Gemini 3.8 Flash — standart, tezkor" }
  ],
  "messageCode": null
}
```

| Maydon | Izoh |
|---|---|
| `id` | Provayderga o'zgarishsiz yuboriladigan model id — `llmModel`, `fastLlmModel`, `sttModel`, `ttsModel` yoki `model` maydoniga aynan shu yoziladi |
| `kind` | `LLM` · `STT` · `TTS` — qaysi maydonga yozilishi mumkinligi (`LLM` ikkalasiga: `llmModel` va `fastLlmModel`) |
| `provider` | Kim xizmat qiladi: `LLM`/`CASCADE` uchun `spring.ai.model.chat` qiymati, `LLM`/`REALTIME` uchun engine nomi, `STT`/`TTS` uchun nutq provayderi id'si |
| `mode` | `CASCADE` yoki `REALTIME`; `STT`/`TTS` qatorlari doim `CASCADE` |
| `label` | Formada ko'rsatiladigan nom |

Ro'yxat bo'sh qaytishi mumkin — bu build'da tanlangan rejim uchun bironta ham
provayder sozlanmaganini bildiradi. Bunday holda `llmModel`/`model` ni bo'sh
qoldirish kerak: qo'ng'iroq deployment'ning o'z standart modelida ketadi.

## Model qayerda qo'llanadi

| Qayerda saqlanadi | Nimaga ta'sir qiladi |
|---|---|
| `ai_model_config.model` (kompaniya) | Kompaniyaning barcha qo'ng'iroqlari |
| `ai_agent.llm_model` (agent) | Faqat shu agent qo'ng'iroqlari — kompaniya sozlamasi ustidan yoziladi |
| `ai_agent.stt_model` (agent) | Shu agentning STT provayderiga uzatiladigan tanish modeli; provayder almashsa (failover) tashlab yuboriladi |
| `ai_agent.tts_model` (agent) | Shu agentning TTS provayderi sintez modeli |

Ikkalasi ham `CASCADE`da `TurnRunner` orqali, `REALTIME`da esa
`RealtimeCallConfig.modelOr(...)` orqali qo'llanadi — ya'ni agent modeli har ikki
rejimda ham hisobga olinadi.
