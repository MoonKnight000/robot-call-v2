# LLM modellar katalogi

`uz.murodjon.robotcallv2.aimodel` · huquq: talab qilinmaydi (katalog har bir kirgan foydalanuvchiga ochiq)

Kompaniya sozlamasi (`PUT /api/settings/ai-model` → `model`) va AI agent
(`POST /api/ai-agents` → `llmModel`) qaysi model id'lari bilan saqlanishi
mumkinligining katalogi — `ai_model` jadvalida saqlanadi (migration bilan seed
qilinadi), config fayl emas. Yaratish/o'chirish endpoint yo'q.

Katalog aynan shu ikki formani to'ldirish uchun bor: ilgari model erkin matn edi
va xato yozilgan id qo'ng'iroq paytida — mijoz go'shakni ko'targanidan keyin —
bilinardi. Endi forma shu endpointdan to'ladi va saqlash ham shu qatorlarga
solishtiriladi, shuning uchun noma'lum id `400 AI_MODEL_UNKNOWN` bo'lib qaytadi.

Umumiy javob shakli va xatolar uchun [README.md](README.md)ga qarang.

---

## `GET /api/ai-models` — mavjud modellar

Query parametr yo'q. Ro'yxat ikki marta toraytiriladi:

1. **Kompaniyaning `engine_config.mode` qiymati bo'yicha** — `CASCADE` model
   id'lari (matn LLM) va `REALTIME` id'lari (speech-to-speech engine) o'zaro
   almashmaydi, provayder biri ikkinchisini umuman tanimaydi.
2. **Shu build haqiqatan yeta oladigan provayderlar bo'yicha** — `REALTIME` uchun
   registratsiyadan o'tgan engine (`RealtimeProviderRegistry`), `CASCADE` uchun esa
   `spring.ai.model.chat` bilan sozlangan yagona chat provayder. Gemini kaliti
   bilan ishlayotgan deployment Groq model id'ini ko'rsatmaydi, chunki u qatorni
   qabul qilsa ham, birinchi turnda yiqilar edi.

| `engine_config.mode` | Qaytadigan modellar |
|---|---|
| `CASCADE` | `spring.ai.model.chat` provayderining matn modellari (`google-genai` → `gemini-*`, `openai` → Groq `llama-*`) |
| `REALTIME` | shu build'da yoqilgan speech-to-speech engine modellari (`gemini-live`, `openai-realtime`, `qwen-omni`, `moshi`, `pipecat`) |

**Response** — `List<AiModel>`:

```json
{
  "accept": true,
  "data": [
    { "id": "gemini-3.5-flash-lite", "provider": "google-genai", "mode": "CASCADE", "label": "Gemini 3.5 Flash Lite — eng arzon, qisqa javoblar uchun" },
    { "id": "gemini-3.8-flash", "provider": "google-genai", "mode": "CASCADE", "label": "Gemini 3.8 Flash — standart, tezkor" }
  ],
  "messageCode": null
}
```

| Maydon | Izoh |
|---|---|
| `id` | Provayderga o'zgarishsiz yuboriladigan model id — `llmModel` va `model` maydonlariga aynan shu yoziladi |
| `provider` | Kim xizmat qiladi: `CASCADE` uchun `spring.ai.model.chat` qiymati, `REALTIME` uchun engine nomi |
| `mode` | `CASCADE` yoki `REALTIME` |
| `label` | Formada ko'rsatiladigan nom |

Ro'yxat bo'sh qaytishi mumkin — bu build'da tanlangan rejim uchun bironta ham
provayder sozlanmaganini bildiradi. Bunday holda `llmModel`/`model` ni bo'sh
qoldirish kerak: qo'ng'iroq deployment'ning o'z standart modelida ketadi.

## Model qayerda qo'llanadi

| Qayerda saqlanadi | Nimaga ta'sir qiladi |
|---|---|
| `ai_model_config.model` (kompaniya) | Kompaniyaning barcha qo'ng'iroqlari |
| `ai_agent.llm_model` (agent) | Faqat shu agent qo'ng'iroqlari — kompaniya sozlamasi ustidan yoziladi |

Ikkalasi ham `CASCADE`da `TurnRunner` orqali, `REALTIME`da esa
`RealtimeCallConfig.modelOr(...)` orqali qo'llanadi — ya'ni agent modeli har ikki
rejimda ham hisobga olinadi.
