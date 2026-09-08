# Pipecat Cloud — Foydalanuvchi va Administrator Qo'llanmasi

Ushbu qo'llanma **Pipecat Cloud** xizmatida ro'yxatdan o'tish, to'g'ri API kalitni tanlash, AI provayderlarini sozlash va uni `robot-call-v2` tizimiga ulash bo'yicha to'liq amaliy yo'riqnomadir.

> [!NOTE]
> Bu yerda dasturlash yoki alohida server ko'tarish talab etilmaydi. Hamma ish Pipecat Cloud veb-paneli orqali boshqariladi.

---

## 1. Pipecat Cloud nima?

**Pipecat Cloud** — bu ovozli AI agentlarini boshqaruvchi tayyor bulutli platforma bo'lib, telefon qo'ng'iroqlari va WebRTC audio oqimlarini ultra-past kechikishda (ultra-low latency) qayta ishlaydi.

---

## 2. 1-qadam: API Kalit yaratish (Public vs Private va Daily)

Pipecat Cloud boshqaruv panelida **"API Keys"** bo'limiga kirganingizda quyidagi bo'limlarni ko'rasiz:

```text
API Keys
├── Public   <-- SIZGA AYNAN SHU KERAK! (+ Create)
├── Private  <-- Tashkilotni boshqarish uchun (bizga kerak emas)
└── Daily    <-- Orqa fondagi avtomatik WebRTC infratuzilmasi
```

### 1. Qaysi kalitni tanlash kerak? (Public vs Private)
* 👉 **`Public` bo'limidagi `+ Create` tugmasini bosing:**
  * **Nega aynan Public?** Pipecat Cloud arxitekturasida agentni ishga tushirish (yangi sessiya ochish: `POST /{agent}/start`) va telefon liniyasidan kelgan audio oqimini ulash uchun **Public API Key** ishlatiladi.
  * Tugmani bosib, kalitga nom bering (masalan: `robot-call-v2-prod`) va yaratilgan `pcc_...` prefiksli kalitni nusxalab oling.
* ❌ **`Private` kalit kerak emas:**
  * Private kalit butun tashkilot ma'muriyati (hisoblarni o'chirish, to'lov kartalarini boshqarish) uchun ishlatiladi va uni oddiy servislar ichiga joylash xavfsizlik jihatidan to'g'ri emas.

### 2. Pastdagi "Daily" bo'limi nima?
* **Daily** — bu audio va videoni millisekundlarda uzatib beruvchi global WebRTC tarmog'i (Pipecat loyihasi ham Daily kompaniyasi tomonidan yaratilgan).
* **Uni biror joyga kiritish kerakmi?**
  * **Yo'q!** Pipecat Cloud siz uchun avtomatik ravishda Daily hisob va domenni (`cloud-ac51...`) ochib qo'ygan. Siz Public kalit orqali sessiya ochganingizda, Pipecat o'zi bu Daily xonasini avtomatik ulab beradi. Uni qo'lda ko'chirib yurish shart emas.

---

## 3. 2-qadam: AI modellari kalitlarini kiritish (Secrets)

Pipecat agentingiz Claude, Gemini, Deepgram yoki Cartesia orqali gaplashishi uchun ularning API kalitlarini Pipecat paneliga bir marta kiritib saqlaysiz:

1. Chap menyudan **"Secrets"** (yoki **"Integrations / Environment Variables"**) bo'limiga kiring.
2. Quyidagi kalitlarni qo'shing:

| Kalit nomi              | Qaysi xizmat     | Nima vazifani bajaradi?                           |
|:------------------------|:-----------------|:--------------------------------------------------|
| **`ANTHROPIC_API_KEY`** | Anthropic Claude | Fikrlash aqli (LLM) — masalan: `claude-3-5-haiku` |
| **`DEEPGRAM_API_KEY`**  | Deepgram         | Mijoz ovozini bir zumda matnga o'girish (STT)     |
| **`CARTESIA_API_KEY`**  | Cartesia Sonic   | Javobni tabiiy va ultra-tez ovozda gapirish (TTS) |
| **`GROQ_API_KEY`**      | Groq (Llama 3.3) | Arzon va chaqmoqdek tezkor LLM                    |
| **`OPENAI_API_KEY`**    | OpenAI (GPT-4o)  | OpenAI modellari uchun                            |
| **`GEMINI_API_KEY`**    | Google Gemini    | Google Gemini va Gemini Live uchun                |

> [!TIP]
> **Tavsiya etilgan eng tezkor kombinatsiya:**  
> **Deepgram** (STT) + **Claude 3.5 Haiku** (LLM) + **Cartesia** (TTS). O'rtacha kechikish atigi **500–700 ms**.

---

## 4. 3-qadam: Ovozli Agent (Bot) yaratish

1. Chap menyudan **"Agents"** bo'limiga kiring.
2. **"Create Agent"** tugmasini bosing:
   - **Agent Name**: `phone-agent` (bu nom `.env` faylidagi nom bilan bir xil bo'lishi kerak)
   - **Default Language**: `uz-UZ` (yoki ko'p tilli)
   - **Default LLM**: `claude-3-5-haiku-20241022`
3. **Save** (Saqlash) tugmasini bosing.

---

## 5. 4-qadam: `robot-call-v2` serveriga ulash

Loyiha serveridagi `.env` faylini ochib, quyidagi 2 ta parametrni kiritish kifoya:

```bash
# Realtime dvijogini yoqish
REALTIME_ENABLED=true

# 1-qadamda Public bo'limidan olingan API kalit
PIPECAT_CLOUD_API_KEY=pcc_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

# 3-qadamda yaratilgan agent nomi
REALTIME_PIPECAT_AGENT=phone-agent
```

---

## 6. 5-qadam: Har bir agent o'z dvijoklarini tanlashi

`robot-call-v2` da dvijok **AI agent** darajasida tanlanadi — kompaniya darajasidagi
`engine_config` olib tashlangan (`V9__agent_speech_engine.sql`), shuning uchun bitta
kompaniya bir vaqtda cascade va realtime agentlarni yonma-yon ishlata oladi:

```http
PUT /api/ai-agents/{id}
Content-Type: application/json

{
  "pipelineMode": "REALTIME",
  "realtimeProvider": "pipecat",
  "pipecatStt": "deepgram",
  "pipecatLlm": "claude-3-5-haiku",
  "pipecatTts": "cartesia"
}
```

* **STT variantlari**: `deepgram`, `soniox`, `speechmatics`, `yandex`, `whisper`
* **LLM variantlari**: `claude-3-5-haiku`, `claude-3-5-sonnet`, `gemini-2.0-flash`, `gpt-4o-mini`, `groq-llama-3.3-70b`
* **TTS variantlari**: `cartesia`, `elevenlabs`, `yandex`, `google-chirp`

Qo'ng'iroq ulanganda tizim avtomatik ravishda ushbu tanlangan modellarni Pipecat sessiyasiga uzatadi va jonli ovozli muloqot boshlanadi.
