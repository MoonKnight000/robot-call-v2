# STT/TTS kechikishini kamaytirish — reja

Manba: `Downloads/STT TTS kechikishini kamaytirish.md` (tashqi tahlil). Quyida u **kodga qarshi tekshirilgan**
(2026-11-05): nima tasdiqlandi, nima allaqachon bor, nima qilinadi. Hamma o'lchov/test/build — foydalanuvchi qo'lida (§8.6).

## 1. Tekshiruv natijasi

### Tasdiqlangan muammolar (hisobot to'g'ri)
| # | Topilma | Joy |
|---|---|---|
| A | Cartesia javobni `BodyHandlers.ofByteArray()` bilan to'liq kutadi — TTFA = to'liq sintez vaqti | `CartesiaTtsProvider.java:114` |
| B | Model `sonic-multilingual`, `Cartesia-Version: 2024-06-10` (eskirgan). Shu klassning `listVoices` i allaqachon `2026-08-14` ishlatadi — ya'ni yangi versiya kalitimizda ishlaydi | `CartesiaTtsProperties.java:26`, `speech.yml:247`, `CartesiaTtsProvider.java:108/155` |
| C | `NamedProviderSelector.findFallback` tilni **tekshirmaydi** — uz-UZ qo'ng'iroq Deepgram'ga (uz yo'q) yoki Cartesia'ga o'tib ketishi mumkin | `NamedProviderSelector.java:100` |
| D | Gemini TTS'da birinchi chunk uchun timeout yo'q: `HttpRequest.timeout` faqat javob sarlavhasigacha; tana oqimi cheksiz kutadi | `GeminiTtsProvider.java:139-143` |
| E | Deepgram: `smart_format=true&endpointing=300` qattiq yozilgan, tashqi endpointingda ham | `DeepgramSttProvider.java:95` |
| F | OpenAI TTS `gpt-4o-mini-tts` snapshotga mahkamlanmagan; `/v1/audio/speech` 2027-01-06 da o'chadi | `speech.yml:254` |
| G | `CartesiaTtsProvider.supports()` har doim `true`, holbuki Cartesia o'zbekchani qo'llamaydi | `CartesiaTtsProvider.java:69` |

### Hisobot "tekshirish kerak / yangi" degan, lekin allaqachon BOR
- Gemini STT: `audioStreamEnd` emas, undan kuchlisi — server VAD o'chirilgan, `activityStart/activityEnd` bilan gate yopadi (`GeminiSttProvider.java:216-220, 399`).
- OpenAI STT: `turn_detection=null` + `input_audio_buffer.commit`, `languages[]` (uz+ru), `prompt` hintlar — hammasi bor (`OpenAiSttProvider.java:158-207, 338`).
- ElevenLabs STT: `language_code`, `commit_strategy=manual`, keyterms bor; ElevenLabs TTS: `pcm_8000` + `ofInputStream` bor.
- Yandex STT: 5 daqiqalik oqim rollover — `maxStreamSeconds()=300` + `SttStreamBridge` (`YandexSttProvider.java:101`).
- Bosqich metrikasi: `voice.turn.stage.latency{stage,stt,tts}` — `eou`, `stt_final`, `llm_ttft`, `tts_ttfb`, `first_audio` (`TurnLatency.java`, `DialogMetrics.java:249`). Hisobot "qisman" degan #3 aslida katta qismi bor.
- Yandex EOU (external), Deepgram `Finalize`, speculation, TTS keshi/prewarm, TTS failover — bor.

### Hisobotdagi da'volar kodda tekshirilmadi / ehtiyot bilan
Vendor ms raqamlari, Gemini TTS preview regressiyalari, ElevenLabs v4 Turbo'ning WS'ga sig'maslik cheklovi, OpenAI `delay` parametrining **aniq JSON joyi** — kodlashdan oldin rasmiy hujjatdan qayta tekshiriladi.

### Qilinmaydi (hisobot ham, ichki tajriba ham)
Yandex `HIGH` sensitivity (uz-UZ finallari bo'laklanadi) · ElevenLabs `optimize_streaming_latency=4` (raqam/sana buziladi) · 8 kHz'ni 16/24 kHz'ga ko'tarib sifat kutish.

## 2. Bosqichlar

Har bosqich alohida; "keyingi bosqich" demaguncha keyingisiga o'tilmaydi.

### Bosqich 0 — o'lchov (xatti-harakat o'zgarmaydi) — KODLANDI 2026-11-05, tekshirilmagan
Maqsad: keyingi bosqichlar nimani yaxshilaganini ko'rish.

Qilingan:
- `voice.tts.ttfa{provider,language}` — `TtsRouter.synthesizeStreaming` da provayderga so'rovdan birinchi eshitiladigan chunkgacha (|sample| > 100). Faqat streaming yo'li (`SpeechOutput:307`, javob gaplari); blocking `synthesize` yo'li (filler, statik qatorlar, greeting) o'lchanmaydi. Kesh hit va xato/fallback yozilmaydi.
- `voice.turn.stage.latency` ga `language` tegi (`TurnLatency.language()`, `DialogEngine.startCall`).
- `voice.stt.final.latency{provider}` endi Deepgram, ElevenLabs, OpenAI, Gemini'da ham (`EouLatencyTracker`: `endUtterance()` → keyingi final). Faqat bizning EOU yuborilgan holatda; 10 s dan eskirgan stamp tashlanadi. Yandex o'z kodida qoldi.
- `ElevenLabsSttProvider` konstruktoriga `SttMetrics` qo'shildi (Spring inject qiladi; testda `new` yo'q).

Qolgan Bosqich 0 ishi: dashboard/alert (Grafana) — alohida, `docs/grafana` ga qarab.
- `TtsRouter.synthesizeStreaming` ichida birinchi **jim bo'lmagan** PCM chunkgacha vaqtni `voice.tts.ttfa{provider,language}` sifatida yozish (hozirgi `voice.tts.synth.latency` — to'liq sintez; `tts_ttfb` — navbat va RTP'ni ham o'z ichiga oladi).
- `voice.turn.stage.latency` ga `language` tegi (kardinallik kichik: uz/ru).
- `voice.stt.final.latency` hozir faqat Yandex'da to'ldiriladi (hisobot shuni aytadi) — qolgan providerlarda ham EOU→final yozilishini tekshirish (Deepgram/ElevenLabs/OpenAI/Gemini'da `endUtterance()` dan `final` gacha).
- **Tekshiruv mezoni:** `/actuator/prometheus` da provider × til bo'yicha p50/p95.

### Bosqich 1 — Cartesia tuzatish (A, B, G) — KODLANDI 2026-11-05, tekshirilmagan
Qilingan (Cartesia docs bilan solishtirildi, 2026-11-05):
- `synthesizeStreaming` override: `ofInputStream` + 640 baytlik o'qish (ElevenLabs bilan bir xil carry mantiqi); `synthesize` shu oqimni yig'adi.
- Model default `sonic-3.6` (`CartesiaTtsProperties`, `speech.yml`). **Docs: `sonic-multilingual` 2026-06-01 da o'chirilgan** — ya'ni hozirgi default prodda ishlamayotgan bo'lishi mumkin edi.
- `Cartesia-Version: 2026-08-14` konstanta (synth + `listVoices`), kalit `Authorization: Bearer` (docs shuni aytadi; `listVoices` allaqachon shunday).
- So'rov tanasi hozirgi sxemaga: `voice: {"id": ...}` (`mode` olib tashlandi).
- `supports()` — uz uchun `false`. Hozircha hech kim `TtsProvider.supports` ni chaqirmaydi; Bosqich 2 (til-aware fallback) ishlatadi.
- Seed: `sonic-3.6` qatori; `ai_agent.tts_model = 'sonic-multilingual'` → `sonic-3.6`; eski qator o'chiriladi. `docs/api/ai-models.md` yangilandi.
- Eslatma: agentning `ttsModel` maydoni Cartesia provider'ida o'qilmaydi (faqat `cartesia.model()`), bu avvalgidek qoldi.
- Qolgan xavf: docs 2024-06-10 → 2026-08-14 sxema farqini aniq aytmaydi; haqiqiy tekshiruv — bitta ru qo'ng'iroq.

Reja matni (asl):
- `ofByteArray` → `ofInputStream`, 8 kHz `pcm_s16le` ni bo'laklab `onChunk` ga uzatish; `synthesizeStreaming` ni override qilish (ElevenLabs `streamPcm` ga o'xshash; toq bayt carry).
- `model` default → `sonic-3.6` (YAML env bilan o'zgaradi), `Cartesia-Version` → `listVoices` dagi bilan bir xil, konstantaga chiqarish.
- `supports(language)` — uz uchun `false` (ochiq savol 1 ga qarang).
- Bu bosqichda WebSocket/`context_id` **qilinmaydi** (Bosqich 4).
- **Tekshiruv:** ru qo'ng'iroqda Cartesia ovozi to'g'ri; `voice.tts.ttfa{cartesia}` oldin/keyin.

### Bosqich 2 — failover xavfsizligi (C, D, F) — KODLANDI 2026-11-05, tekshirilmagan
Qilingan:
- `NamedProviderSelector.findFallback(failed, Predicate)` overload (bir argumentli versiya o'z joyida). `SttProvider.supports(language)` (default `true`), `DeepgramSttProvider` uz uchun `false`. `SttStreamBridge.failOver` va `TtsRouter` (ikkala yo'l) endi tilni qo'llamaydigan providerni fallback qilmaydi; mos provider bo'lmasa — fallback yo'q (STT: qo'ng'iroq o'sha providerda qoladi, TTS: xato).
- `TtsStreamProperties` (`voice-agent.tts.stream.first-chunk-timeout-ms`, env `TTS_FIRST_CHUNK_TIMEOUT_MS`, **default 0 = o'chiq**) + `FirstChunkWatchdog`: birinchi eshitiladigan chunk kelmasa chaqiruvchi thread interrupt qilinadi → mavjud fallback yo'li; `voice.tts.first.chunk.timeouts{provider}` hisoblagichi. Qiymatni `voice.tts.ttfa` p95 dan (zaxira bilan) o'zingiz qo'yasiz — o'lchovsiz taxmin qilinmadi.
- OpenAI TTS: YAML default `gpt-4o-mini-tts-2025-12-15` (docs: shu default snapshot; ikkala snapshot ham deprecated).
- Testlar moslandi: `TtsRouterTest`, `SttStreamBridgeTest` (yangi imzo); yangi `FirstChunkWatchdogTest`.

OpenAI hujjati (2026-11-05): `tts-1`, `tts-1-hd`, `gpt-4o-mini-tts` **2027-01-06** da o'chadi; tavsiya — Realtime `gpt-realtime-2.1-mini`, lekin u `/v1/audio/speech` da ishlamaydi va hujjatda "faqat matn→ovoz" Realtime oqimi tasvirlanmagan. Shuning uchun kodlanmadi. **Qaror kerak 2026-12-15 gacha:** OpenAI TTS'ni katalogdan olib tashlash (retired ids bloki, `R__seed_data.sql`) yoki Realtime orqali alohida provider yozish (avval `gpt-realtime-2.1-mini` sahifasi va PCM chiqishini o'qish). OpenAI STT: eski `gpt-4o-transcribe` va b. 2027-02-26 da o'chadi — bizdagi `gpt-live-transcribe`/`gpt-transcribe` allaqachon almashtirish modellari.

Reja matni (asl):
- `NamedProviderSelector.findFallback(failed, language)` — `SttProvider`/`TtsProvider` ga `supports(language)` default metodi (STT'da default `true`, Deepgram uz uchun `false`; TTS'da allaqachon bor). Tilga mos provider topilmasa — mavjud xatti-harakat (har qanday provider) saqlanadi, lekin faqat uz uchun Deepgram/Cartesia'ga emas.
- TTS birinchi-chunk timeout: `voice-agent.tts.first-chunk-timeout-ms` (YAML, `TtsProperties`), `TtsRouter.synthesizeStreaming` da. Muddat o'tsa — stream bekor qilinadi, mavjud fallback yo'li ishlaydi, `voice.tts.failovers` ortadi. Bu **xatoga qarshi qo'riqchi**, burilish vaqtini boshqaradigan statik kutish emas (`no-static-waits-in-dialog` qoidasiga zid emas) — baribir chegarani Bosqich 0 p95'idan belgilaymiz.
- OpenAI TTS: modelni sanali snapshotga mahkamlash (env) + 2027-01-06 dan oldin chiqarib tashlash yoki `gpt-realtime-*` ga ko'chish qarori — alohida vazifa, sana: **2026-12-15 gacha**.
- **Tekshiruv:** uz qo'ng'iroqda asosiy STT o'chirilsa Deepgram'ga tushmaydi; Gemini TTS'ni sun'iy sekinlashtirib timeout→fallback ishlashi.

### Bosqich 3 — kichik STT sozlamalar (E va boshqalar), har biri A/B — KODLANDI 2026-11-05, tekshirilmagan
Hamma yangi sozlama default'da **avvalgi xatti-harakatni saqlaydi**; A/B env orqali.
- Deepgram: `STT_DEEPGRAM_EXTERNAL_ENDPOINTING_MS` (default 300; `0` → `endpointing=false`; faqat tashqi endpointingda, oddiy rejimda 300 qoladi) va `STT_DEEPGRAM_SMART_FORMAT` (default true). Hujjat: `endpointing` butun ms yoki `false`; **`Finalize` + `from_finalize` past qiymat/`false` bilan ishlashi hujjatda aytilmagan** — `false` ni sinashdan oldin `voice.stt.final.latency{provider=deepgram}` va bo'laklanish (`voice.stt.gate.close outcome=continued`) ni kuzating.
- Gemini STT: `maxStreamSeconds()=600` (hujjat: 10 daqiqa); `SttStreamBridge` pauzada ~570 s da almashtiradi. OpenAI STT uchun **qo'shilmadi** — hujjatda sessiya chegarasi yozilmagan.
- OpenAI STT: `STT_OPENAI_DELAY` (`minimal|low|medium|high|xhigh`, bo'sh = OpenAI default) → `session.audio.input.transcription.delay`, faqat `gpt-live-transcribe` da yuboriladi. Har daraja necha ms ekani e'lon qilinmagan.
- ElevenLabs STT: `STT_ELEVENLABS_SECONDARY_LANGUAGES` (default **false**) — `detect-languages` dagi boshqa tillar `&secondary_languages=ru` ko'rinishida (takrorlanuvchi parametr; API hujjati ro'yxat kodlanishini aytmaydi, shuning uchun opt-in). `commit_throttled` xatosi `voice.stt.provider.warnings{provider=elevenlabs}` ga yoziladi.
- Yandex: `text-normalization`/`literature-text` allaqachon env (`STT_YANDEX_TEXT_NORMALIZATION`, `STT_YANDEX_LITERATURE_TEXT`) — kod kerak emas, faqat A/B.

Reja matni (asl):
- Deepgram: tashqi endpointingda `endpointing` ni 10–100 ms (URL'ga `externalEndpointing` uzatiladi), `smart_format` ni YAML bayrog'iga chiqarib A/B; rus qo'ng'iroqlarida EOU→final bilan solishtirish.
- Gemini/OpenAI STT: `maxStreamSeconds()` override (Gemini ~570, OpenAI ~590; `SttStreamBridge` allaqachon rollover qiladi — `SttProvider.java:86`).
- OpenAI STT: `delay` (`low` vs `medium`) — **avval hujjatdan maydon joyini tasdiqlash**.
- ElevenLabs STT: `secondary_languages=ru` (uz asosiy bo'lsa); `commit_throttled` xatosini logda/metrikada ko'rinadigan qilish.
- Yandex STT: `text-normalization`/`literature-text` ni faqat transkript sifati kerak bo'lmasa o'chirib A/B (dialog `final` ni o'qiydi, kechikishga ta'siri kutilmaydi — foyda kichik, oxirgi navbatda).
- **Tekshiruv:** har o'zgarish uchun `voice.stt.final.latency{provider}` p50/p95 + uz yozuvlarida sifatni quloq bilan.

### Bosqich 4 — kattaroq tajribalar — KODLANDI 2026-11-05, tekshirilmagan
Foydalanuvchi qarori: hammasi qilinsin. Bosqich 0 raqamlari hali yo'q, shuning uchun **har bir tajriba opt-in, default o'chiq** — yoqib `voice.tts.ttfa{provider}` / `voice.stt.final.latency{provider}` bilan oldin/keyin solishtiriladi.

TTS (`voice-agent.tts.stream.*`, `TtsStreamProperties`):
- **Cartesia WebSocket** — `TTS_CARTESIA_WEBSOCKET=true`. Yangi `CartesiaWebSocketClient`: bitta umumiy `/tts/websocket` ulanishi, har gap alohida `context_id`; `chunk` (base64 PCM) → `onChunk`, `done`/`error`; to'xtagan gap `cancel` bilan yopiladi; uzilgan socket bir marta qayta ochiladi. Xavf: 2026-08-14 versiyasining WS sxemasi hujjatdan to'liq tasdiqlanmagan (auth header ham, `cartesia_version` query ham yuboriladi).
- **Yandex StreamSynthesis** — `TTS_YANDEX_STREAM_SYNTHESIS=true`. Matn 5000 belgidan bo'linadi, options + matn + `force_synthesis` yuboriladi, so'ng half-close; `UtteranceSynthesis` yo'li o'zgarmagan. Xavf: half-close flush semantikasi va uz tilida streaming hujjatda tasdiqlanmagan.
- **ElevenLabs** — `x-region` javob sarlavhasi doim `voice.tts.region{provider,region}` ga yoziladi (opt-in emas, faqat kuzatuv). `previous_text` — `TTS_ELEVENLABS_PREVIOUS_TEXT=true`: oldingi gap (oxirgi 300 belgi) `SentenceQueue` → `SpeechOutput.prepare` → `TtsRouter.synthesize(..., previousText)` orqali. Qaysi modellar `previous_text` ni hisobga olishi hujjatda yo'q. `ulaw_8000` **ataylab qilinmadi** — biz PCM ishlatamiz (RTP kodek savoli, §3.2).
- **Gemini TTS 8 kHz mulaw** — `TTS_GEMINI_MULAW=true`. Google hujjati (2026-11-05 o'qildi): `generationConfig.responseFormat.audio.{mimeType: AUDIO_MULAW, sampleRate: 8000}` (`generateContent`/`streamGenerateContent`, `speechConfig` ichida emas). So'rovga shu qo'shiladi; javobdagi har chunkning `inlineData.mimeType` si "mulaw" desa — `G711Codec.ulawToPcm` bilan ochiladi va resample'siz uzatiladi, aks holda eski yo'l (24 kHz PCM → 8 kHz) — model formatni e'tiborsiz qoldirsa ham zarar yo'q. Birinchi chunkning mime turi logga yoziladi (`Gemini TTS answered mu-law request with mimeType=...`). Hujjatda **yo'q**: streaming + mulaw misoli va mulaw baytlari sarlavhasizmi (RIFF'siz deb faraz qilindi), `sampleRate` ruxsat etilgan qiymatlari. Hujjat yangi kodlarni Interactions API'ga yo'naltiradi (`generateContent` — legacy), lekin migratsiya qilinmadi.
- ElevenLabs `ulaw_8000` va Cartesia `pcm_mulaw` **ataylab qilinmadi**: yo'l ikki baravar kam bayt beradi, kechikishni kamaytirmaydi; biz baribir PCM ga ochamiz.

STT:
- **Socketni jiringlash paytida ochish ("prewarm")** — `STT_PREWARM_ENABLED=true` (`STT_PREWARM_MAX_PENDING`=50, `STT_PREWARM_TTL_SLACK_MS`=10000). `AsteriskGateway.originate` kampaniya qo'ng'irog'ini register qilgach `AudioPipelineAssembler.prewarmStt` fonda stream ochadi (`SttPrewarmRegistry`). Javob berilganda `openSttBridge` bir xil parametrlar (`SttOpenParams`: provider, til, detect ro'yxati, hints, model, external endpointing) bilan `take` qiladi va `SttStreamBridge` o'sha sessiyani qabul qiladi; parametr farq qilsa yoki stream o'lgan bo'lsa — yopiladi va qo'ng'iroq avvalgidek o'zinikini ochadi. Javobdan oldin tayyor bo'lmagan stream kelgach yopiladi (javob hech qachon kutmaydi). Javobsiz qo'ng'iroqlarning streamlari TTL (jiringlash vaqti + slack) dan keyin 5 s lik tozalash bilan yopiladi. Faqat `supportsPrewarm()=true` providerlar: **Deepgram, ElevenLabs** (ikkalasi o'z keepalive'iga ega). Gemini/OpenAI idle xatti-harakati tasdiqlanmagani uchun qo'shilmadi; Yandex idle stream tez yopiladi. Narxi: har jiringlagan qo'ng'iroq uchun vendor sessiyasi (javobsiz bo'lsa ham) — `max-pending` shuni cheklaydi. Foyda: faqat stream ochilish vaqti (~100–300 ms, o'lchanmagan), birinchi `voice.stt.final.latency` da ko'riladi.
- Realtime/HALF_CASCADE qo'ng'iroqlar parametri boshqacha bo'lgani uchun streamni qabul qilmaydi (yopiladi) — behuda ochilishi mumkin; agent rejimiga qarab oldindan filtrlash qo'shilmadi.

Aisha (kod emas; hozir balans yo'qligi uchun o'chiq) — vendor'dan yozma so'raladigan savollar: (1) oqimni majburan yakunlovchi (flush/commit) xabar bormi; (2) segment sukunat chegarasi qanday va sozlanadimi; (3) til qanday tanlanadi/aniqlanadi (uz+ru aralash qo'ng'iroq); (4) qaysi region/serverdan xizmat qiladi va kechikish kafolati.

## 3. Ochiq savollar (kodlashdan oldin javob kerak)
1. Cartesia prodda ishlatilyaptimi va qaysi tillarda? Agar faqat ru bo'lsa — `supports('uz')=false` xavfsiz; agar uz'da ham ishlatilsa — bu alohida muammo.
2. RTP kodek PCMU (mu-law) mi yoki PCM? (Bosqich 4 dagi `ulaw_8000`/`pcm_mulaw` shunga bog'liq.)
3. Prodda hozir qaysi STT/TTS provayderlar yoqilgan (hisobot barchasini qamraydi, ustuvorlik shunga qarab o'zgaradi)? Aisha o'chiq ekani ma'lum.

## 4. API / frontend ta'siri
Bosqich 0–4 da REST API o'zgarmaydi (faqat YAML sozlamalar va metrikalar). `supports()` o'zgarishi voice catalog/`listVoices` javobiga ta'sir qilmaydi.
