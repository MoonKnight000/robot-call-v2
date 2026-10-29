# MODELS — tashqi model fayllari

Ovoz pipeline'i bir nechta ONNX modelga tayanadi. Ular **repoga commit qilinmaydi**
(`.gitignore` → `models/`), qo'lda yuklab olinadi. Barchasi ixtiyoriy: fayl bo'lmasa
tegishli funksiya o'chadi, qo'ng'iroq esa ishlashda davom etadi va logda sababi yoziladi.

Hammasi loyiha ildizidagi `models/` papkasiga tushadi — IDE'dan ishga tushirilganda shu yo'llar
o'qiladi. **Docker'da** faqat Silero VAD image ichiga qo'shiladi (`Dockerfile`, `/app/silero_vad.onnx`);
qolganlari `docker-compose.yml` dagi `./models:/app/models:ro` volume orqali o'qiladi.
GTCRN uchun yo'l compose'da tayyor (`/app/models/gtcrn_simple.onnx`), `.env` da faqat
`DENOISER_ENABLED=true` kifoya. Smart Turn / NAMO uchun env'da `/app/models/...` yo'lini bering.

| Model | Nima uchun | Fayl | Hajmi | Yoqish |
|---|---|---|---|---|
| Silero VAD v5 | Nutq/sukunat — barge-in, STT gating, endpointing, AMD | `models/silero_vad.onnx` | ~2 MB | Standart yoqiq (`VAD_MODEL_PATH`) |
| GTCRN | Mijoz liniyasidagi neyron shovqin filtri (VOICE-PIPELINE-PLAN Bosqich 6) | `models/gtcrn_simple.onnx` | ~0.5 MB | `DENOISER_ENABLED=true` |
| Smart Turn v3 | Audio bo'yicha "gap tugadimi" (turn detection) | `models/smart_turn_v3.onnx` | ~8.8 MB | `TURN_DETECTOR=true` |
| NAMO Turn Detector v1 | Matn bo'yicha "gap tugadimi" (Bosqich 1) | `models/namo/model_quant.onnx` + `models/namo/tokenizer.json` | ~310 MB | Standart o'chiq; `TEXT_TURN_DETECTOR=true` |

---

## Yuklab olish (bash / Git Bash)

```bash
mkdir -p models/namo

# Silero VAD — majburiy darajada muhim
curl -fsSL -o models/silero_vad.onnx \
  https://raw.githubusercontent.com/snakers4/silero-vad/v5.1.2/src/silero_vad/data/silero_vad.onnx

# GTCRN — streaming export (kirish: bitta STFT kadr + 3 ta cache)
curl -fsSL -o models/gtcrn_simple.onnx \
  https://github.com/Xiaobin-Rong/gtcrn/raw/main/stream/onnx_models/gtcrn_simple.onnx

# Smart Turn v3
curl -fsSL -o models/smart_turn_v3.onnx \
  https://huggingface.co/pipecat-ai/smart-turn-v3/resolve/main/smart-turn-v3.0.onnx

# NAMO (ko'p tilli; uz-UZ ga hali o'rgatilmagan — pastga qarang)
curl -fsSL -o models/namo/model_quant.onnx \
  https://huggingface.co/videosdk-live/Namo-Turn-Detector-v1-Multilingual/resolve/main/model_quant.onnx
curl -fsSL -o models/namo/tokenizer.json \
  https://huggingface.co/videosdk-live/Namo-Turn-Detector-v1-Multilingual/resolve/main/tokenizer.json
```

---

## Har bir model haqida

### Silero VAD
- Manba: [snakers4/silero-vad](https://github.com/snakers4/silero-vad), `v5.1.2` tegi.
  Versiya ataylab mahkamlangan: boshqa versiyada kirish shakli farq qilishi mumkin.
- Sozlamalar: `voice-agent.vad.*` (`config/speech.yml`).

### GTCRN (neyron shovqin filtri)
- Manba: [Xiaobin-Rong/gtcrn](https://github.com/Xiaobin-Rong/gtcrn) →
  `stream/onnx_models/gtcrn_simple.onnx`. Litsenziya — MIT.
- Kod (`agent/audio/NeuralDenoiser`) **streaming** export'ni kutadi. Fayl 2026-10-08 da
  tekshirilgan va shu shaklda:

  | Kirish | Shakl | Chiqish |
  |---|---|---|
  | `mix` | `[1, 257, 1, 2]` — 512 nuqtali STFT kadri, 16 kHz, real/imag | `enh` |
  | `conv_cache` | `[2, 1, 16, 16, 33]` | `conv_cache_out` |
  | `tra_cache` | `[2, 3, 1, 1, 16]` | `tra_cache_out` |
  | `inter_cache` | `[2, 1, 33, 16]` | `inter_cache_out` |

  Shu repodagi boshqa fayllar mos kelmaydi: `gtcrn.onnx` — soddalashtirilmagan graf,
  `*.tar` — PyTorch og'irliklari. Kontraktga mos kelmagan graf yuklanmaydi va logda sababi
  yoziladi.
- Yoqish:
  ```
  DENOISER_ENABLED=true
  DENOISER_MODEL_PATH=models/gtcrn_simple.onnx   # standart qiymat
  ```
  Filtr faqat noise cancellation yoqilgan agentlarda ishlaydi va eski broadband filtr
  o'rnini egallaydi. Faqat recognizer'ga boradigan nusxa tozalanadi, yozuv o'zgarmaydi.
- Oflayn sinash (shovqinli 8 kHz WAV → tozalangan WAV + CPU sarfi):
  ```
  java -cp <test classpath> uz.murodjon.robotcallv2.agent.audio.DenoiseTool in.wav out.wav --model=models/gtcrn_simple.onnx
  ```
  Ishlab turgan tizimda: Grafana'dagi `voice_denoiser_hop_latency_seconds` (har 16 ms'lik hop).

### Smart Turn v3
- Manba: [pipecat-ai/smart-turn-v3](https://huggingface.co/pipecat-ai/smart-turn-v3). Repoda
  `v3.0`, `v3.1-cpu`, `v3.2-cpu` bor.
- 2026-10-08 da ikki muammo tuzatildi:
  1. `onnxruntime` 1.17.3 → **1.20.0** (eskisi uchala faylni ham ochmasdi: `v3.0` — `ai.onnx.ml`
     opset 5, `v3.1`/`v3.2` — IR version 10). Silero, GTCRN, NAMO ham 1.20.0 da ochilishi tekshirildi.
  2. Rasmiy model `input_features [*, 80, 800]` kutadi — Whisper'ning standart preprocessor'i
     (80 mel, hop 160, **n_fft 400**). `WhisperFeatures` endi 400 ni Bluestein algoritmi bilan
     aniq hisoblaydi (to'g'ridan-to'g'ri DFT'dan farqi ~1e-13), standart sozlamalar shunga
     o'zgartirildi: `TURN_N_FFT=400`, `TURN_HOP_SAMPLES=160`, `TURN_MELS=80`, `TURN_FRAMES=800`.

  Java'da hisoblangan xususiyatlar bilan model ishga tushirildi va [0, 1] oralig'ida ehtimollik
  qaytardi. Bu Python'dagi `WhisperFeatureExtractor` bilan bitma-bit mosligini **isbotlamaydi** —
  yoqishdan oldin rus tilidagi yozuvlarda `TurnReplayTool` bilan tekshiring.
- Yoqish (model o'zbekchaga o'rgatilmagan, faqat ru-RU):
  ```
  TURN_DETECTOR=true
  TURN_MODEL_PATH=models/smart_turn_v3.onnx     # Docker'da /app/models/smart_turn_v3.onnx
  ```
- Sozlamalar: `voice-agent.turn.*` (`TURN_MODEL_PATH`). U faqat `STT_VAD_GATING=true` bo'lganda
  ta'sir qiladi.

### NAMO Turn Detector v1 (matn)
- Manba: [videosdk-live/Namo-Turn-Detector-v1-Multilingual](https://huggingface.co/videosdk-live/Namo-Turn-Detector-v1-Multilingual).
- **uz-UZ ga o'rgatilmagan** — shuning uchun `TEXT_TURN_LANGUAGES` standart holatda `ru-RU`.
  O'zbekcha uchun yo'l (VOICE-PIPELINE-PLAN Bosqich 1):
  1. `TURN_DATASET_ENABLED=true` bilan jonli qo'ng'iroqlardan `{text, label}` yig'iladi
     (`data/turn-dataset/*.jsonl`).
  2. Fine-tune — repo tashqarisida (loyihada Python yo'q, CLAUDE.md §8.1).
  3. `TextTurnReplayTool` bilan yangi model baholanadi; o'tsa, `models/` ga qo'yilib
     `TEXT_TURN_LANGUAGES=uz-UZ,ru-RU` qilinadi.
- **Standart o'chiq** (2026-10-28, `PROJECT-AUDIT.md` 2-bosqich): model startup'da ~310 MB xotira
  oladi, lekin faqat `ru-RU` qo'ng'iroqlarga xizmat qiladi. Rus tilida qo'ng'iroq qiladigan
  o'rnatmada yoqing:
  ```
  TEXT_TURN_DETECTOR=true
  TEXT_TURN_MODEL_PATH=models/namo/model_quant.onnx
  TEXT_TURN_TOKENIZER_PATH=models/namo/tokenizer.json
  ```

---

## Tekshirish

Ilova startida har bir model bitta qator log yozadi:

```
Neural denoiser ready (model=models/gtcrn_simple.onnx, caches=[conv_cache, tra_cache, inter_cache])
Text turn detector ready (model=..., inputs=..., languages=[ru-RU], threshold=0.5)
```

`... not found` yoki `... disabled` qatori chiqsa, fayl yo'li yoki fayl versiyasi noto'g'ri.
