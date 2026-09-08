# Ovoz va STT sinovi (Speech Preview)

`uz.murodjon.robotcallv2.voice` · huquq: **VOICE_READ** · auth: `Authorization: Bearer <token>`

Sozlamalar/kampaniya ekranida qo'ng'iroq qilmasdan sinab ko'rish uchun ikkita endpoint:

| Endpoint | Nima qiladi |
|---|---|
| `POST /api/tts/voices/{id}/preview` | matn → tanlangan ovozda gapirgan audio (`audio/wav`) |
| `POST /api/stt/preview` | brauzerda yozilgan audio → tanlangan STT provayder transkripti |

Umumiy javob shakli va xatolar uchun [README.md](README.md)ga qarang.

---

## `POST /api/tts/voices/{id}/preview` — ovozni eshitib ko'rish

**Path:** `id` — `GET /api/tts/voices` dan kelgan ovoz `id`si (`zamira`, `gulnoza-cheerful`, ...).

**Request** — `Content-Type: application/json`, `PreviewVoiceRequest`:

```json
{ "text": "Assalomu alaykum, men Uysot kompaniyasining virtual yordamchisiman." }
```

| Maydon | Tip | Qoida |
|---|---|---|
| `text` | string | majburiy, bo'sh bo'lmasin, **500 belgigacha**. Raqamlar serverda so'zga aylantiriladi (`1500` → "bir ming besh yuz") |

**Response `200`** — body **`audio/wav`** (8 kHz, mono, 16-bit PCM). `ResponseData` konverti **yo'q**, xom fayl.
Muvaffaqiyatsiz holatda esa odatdagi JSON `ResponseData` qaytadi — shuning uchun `response.ok` ni tekshirib, keyin `blob()` yoki `json()` o'qing.

Ovoz **o'z provayderi** orqali gapiradi va kompaniyaning `PUT /api/settings/voice` dagi `speed`/`pitch` qiymatlari qo'llanadi — qo'ng'iroqda qanday eshitilsa, shunday. Bir xil matn har safar qaytadan sintez qilinadi (kesh yo'q), odatda 0.5–3 s.

**Frontend misoli:**

```js
async function playVoicePreview(voiceId, text) {
  const res = await fetch(`/api/tts/voices/${voiceId}/preview`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ text }),
  });
  if (!res.ok) {
    const { messageCode, message } = await res.json();   // ResponseData
    throw new Error(`${messageCode}: ${message}`);
  }
  const url = URL.createObjectURL(await res.blob());
  const audio = new Audio(url);
  audio.onended = () => URL.revokeObjectURL(url);
  await audio.play();
}
```

**Xatolar:**

| Status | `messageCode` | Qachon |
|---|---|---|
| 400 | `VALIDATION_FAILED` | `text` bo'sh yoki 500 dan uzun (`errors[]` da maydon) |
| 404 | `TTS_VOICE_NOT_FOUND` | katalogda bunday `id` yo'q |
| 409 | `TTS_VOICE_PROVIDER_UNAVAILABLE` | ovoz realtime engine'niki (`gemini-live`, `openai-realtime`) yoki provayder kredensiali serverda yo'q — UI'da bu ovoz uchun "eshitish" tugmasini o'chirib qo'yish mumkin (`provider` maydoniga qarab) |
| 502 | `TTS_YANDEX_*`, `TTS_AISHA_*`, `TTS_GEMINI_*` | provayder sintez qila olmadi — "Provayder javob bermadi, qayta urinib ko'ring" |

---

## `POST /api/stt/preview` — STT provayderni sinash

Foydalanuvchi mikrofonga gapiradi, yozuv serverga yuboriladi, tanlangan STT provayder
uni qo'ng'iroqdagidek tinglaydi va nima "eshitganini" qaytaradi.

**Request** — `Content-Type: multipart/form-data`:

| Maydon | Majburiy | Izoh |
|---|---|---|
| `file` | ha | Audio fayl. Brauzer `MediaRecorder` beradigan `audio/webm;codecs=opus` (Chrome, Firefox, Edge) va `audio/mp4` (Safari) **to'g'ridan-to'g'ri yuboriladi**, frontendda konvert qilish shart emas. WAV, MP3, OGG ham bo'ladi. Sample rate istalgan, stereo bo'lsa mono'ga yig'iladi. **30 soniyagacha, 5 MB gacha** |
| `provider` | yo'q | STT provayder id: `GET /api/ai-agents/engine-options` javobidagi `stt` ro'yxatidan (`yandex`, `aisha`, `gemini`, `deepgram`). Berilmasa — `voice-agent.stt.provider` (YAML) qiymati; kompaniya darajasidagi STT sozlamasi endi yo'q, u agentniki |
| `language` | yo'q | BCP-47: `uz-UZ`, `ru-RU`. Berilmasa — serverdagi standart (`uz-UZ`) |

**Javob vaqti:** audio provayderga **real vaqt tezligida** yuboriladi (qo'ng'iroqdagidek), shuning uchun
javob ≈ *audio uzunligi + 1–4 s*. 10 soniyalik yozuv → ~12–14 s. UI'da loading indikatori va
"Tinglanmoqda..." holati kerak; tugma qayta bosilmasin.

**Response `200`** — `ResponseData<SttPreviewResponse>`:

```json
{
  "data": {
    "provider": "yandex",
    "language": "uz-UZ",
    "transcript": "ha men eshitaman gapiring",
    "audioMs": 4120
  },
  "message": null, "messageCode": null, "accept": true, "errors": null
}
```

| Maydon | Izoh |
|---|---|
| `provider` | aslida ishlagan provayder (default ishlatilganda qaysi bo'lganini ko'rsatadi) |
| `language` | recognizer'ga berilgan til |
| `transcript` | tanilgan matn; provayder hech narsa tanimasa `""` — UI'da "Hech narsa tanilmadi" deb ko'rsatiladi |
| `audioMs` | yuborilgan audio uzunligi, ms |

**Frontend misoli** (yozib olish + yuborish):

```js
let recorder, chunks = [];

async function startRecording() {
  const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
  recorder = new MediaRecorder(stream);          // Chrome: audio/webm;codecs=opus, Safari: audio/mp4
  chunks = [];
  recorder.ondataavailable = e => chunks.push(e.data);
  recorder.start();
}

async function stopAndTranscribe(provider, language) {
  await new Promise(r => { recorder.onstop = r; recorder.stop(); });
  recorder.stream.getTracks().forEach(t => t.stop());

  const blob = new Blob(chunks, { type: recorder.mimeType });
  const form = new FormData();
  form.append('file', blob, 'recording.webm');   // fayl nomi/kengaytmasi muhim emas, server o'zi aniqlaydi
  if (provider) form.append('provider', provider);
  if (language) form.append('language', language);

  const res = await fetch('/api/stt/preview', {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${token}` },   // Content-Type qo'ymang — brauzer boundary bilan o'zi qo'yadi
    body: form,
  });
  const body = await res.json();
  if (!res.ok) throw new Error(`${body.messageCode}: ${body.message}`);
  return body.data;   // { provider, language, transcript, audioMs }
}
```

**Xatolar:**

| Status | `messageCode` | Qachon | UI |
|---|---|---|---|
| 400 | `AUDIO_UPLOAD_FILE_MISSING` | `file` yo'q yoki 0 bayt | "Yozuv bo'sh" |
| 400 | `AUDIO_UPLOAD_INVALID` | fayl audio deb tanilmadi (xabarda dekoder xatosi) | "Fayl formati noto'g'ri" |
| 400 | `AUDIO_UPLOAD_TOO_LONG` | 30 soniyadan uzun | yozishni 30 s da avtomatik to'xtatish tavsiya etiladi |
| 400 | `ENGINE_STT_PROVIDER_UNKNOWN` | `provider` bu serverda yo'q (`message` da mavjudlar ro'yxati) | ro'yxatni `engine/options` dan yangilash |
| 409 | `STT_PROVIDER_UNAVAILABLE` | serverda birorta STT provayder yoqilmagan | "STT sozlanmagan" |
| 413 | — | 5 MB dan katta fayl (Spring multipart limiti) | |
| 502 | `AUDIO_TRANSCODER_UNAVAILABLE`, `AUDIO_TRANSCODER_FAILED` | server tomonidagi dekoder muammosi | "Server xatosi, administratorga murojaat qiling" |
| 502 | `STT_YANDEX_*`, `STT_GEMINI_*`, `STT_AISHA_*`, `STT_DEEPGRAM_*`, `STT_PREVIEW_INTERRUPTED` | provayder ulanmadi yoki stream uzildi | "Provayder javob bermadi, qayta urinib ko'ring" |

---

## Bog'liq endpointlar

| Endpoint | Nima uchun kerak |
|---|---|
| `GET /api/tts/voices?language=` | ovoz tanlagich ro'yxati ([voices.md](voices.md)) |
| `GET /api/ai-agents/engine-options` | `stt` ro'yxati — STT provayder tanlagich ([settings.md](settings.md)) |
| `GET /api/ai-agents/{id}` | agentning `sttProvider` i — tanlagichda default sifatida ko'rsatish |
| `PUT /api/settings/voice` | `speed`/`pitch` — TTS preview ularni hisobga oladi, o'zgartirgach qayta eshitish mumkin |
