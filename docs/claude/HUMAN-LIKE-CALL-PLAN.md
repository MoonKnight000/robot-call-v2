# Ishonchli va insondek qo'ng'iroq — reja

Manba: 2026-11-06 da internetdan yig'ilgan ikki qidiruv:
(1) chiquvchi ovozli agentlarning production muammolari, (2) agentni insondek gapirtirish.
Asosiy manbalar dev.to amaliy maqolalari (Asterisk'li outbound pilot, "sekinlik LLM'da emas"),
shaxsiy bloglar va vendor bloglari (Deepgram, Vapi, Meduzzen, Seed&Society, Famulor). Vendor raqamlari
ehtiyot bilan olinadi. Hammasi **kodga qarshi tekshirildi**. Hamma o'lchov, test va build foydalanuvchi
qo'lida (§8.6). "Keyingi bosqich" demaguncha keyingisiga o'tilmaydi.

## 1. Tekshiruv natijasi

### Allaqachon bor (qayta qilinmaydi)
| Tavsiya | Bizda |
|---|---|
| AMD + voicemail drop | `AnsweringMachineDetector`, `AnsweringMachineWatcher`, `VoicemailDrop`, `speech.yml` `vad.amd` |
| "mhm"ni barge-in deb olmaslik | `Backchannels`, `BargeInRecovery`, `FloorRules`, `ClientInputGate` |
| Raqam/sana og'zaki shaklda | `UzbekNumberWords`, `RussianNumberWords`, `SpeechTextNormalizer` |
| Jimlikda qayta so'rash, osilgan qo'ng'iroqlar | `SilenceWatchdogRunner`, `RealtimeWatchdogRunner`, `GhostCallReaper`, `CallLimits` |
| Bosqichma-bosqich kechikish | `voice.turn.stage.latency` (eou, stt_final, llm_ttft, tts_ttfb, first_audio) |
| Og'zaki register, kitobiy so'zlar taqiqi, markdown taqiqi | `SystemPromptFactory` (prompt darajasida) |
| Kamdan-kam filler, faqat HUMAN_LIKE | "NATURAL FILLERS AND BRIDGES" qoidasi |
| Avval tasdiqlash, keyin javob | `acknowledgementSize` (RESERVED/BALANCED/WARM) |
| "Yomon → yaxshi" misollari | Scenario `styleExamples` (`appendStyleExamples`) |
| Kayfiyatga qarab tezlik | `VoiceEmotionResolver` |
| Oldingi gap TTS kontekstida | ElevenLabs `previous_text` (`TtsStreamProperties.elevenLabsPreviousText`) |
| Qo'ng'iroq boshida AI ekanini aytish | `disclosureText` |
| Qarz bo'yicha guardrail jonli yo'lda | `FactGuard` |
| Eval | `AgentEvaluationService` (test case qo'lda yaratiladi) |

### Bo'shliqlar (rejaga kiradi)
| # | Topilma | Manba | Bizda hozir |
|---|---|---|---|
| G1 | LLM JSON, tool sintaksisi yoki markdown chiqarsa, TTS uni o'qib beradi | SwiftCase, outbound pilot | Faqat promptda taqiq; `SpeechTextNormalizer` da deterministik tozalash topilmadi |
| G2 | Prompt jimgina o'sadi: 6.5k→14k belgi, javob 1.7 s→5.3 s, provayder kesib tashlaydi | outbound pilot | `AlertingService` faqat umumiy token sur'atini kuzatadi; bitta turn promptining budjeti yo'q |
| G3 | Barge-in'dan keyin kontekst qayta yig'ilganda system prompt tushib qolgan | "sekinlik LLM'da emas" | `BargeInRecoveryTest` bor, lekin bu invariantni tekshirishi aniqlanmadi |
| G4 | Ssenariy bo'yicha umumiy misollar 3–4 turndan keyin "unutiladi"; misol bosqichga bog'langan bo'lishi kerak | bvsbharat, LiveKit | `styleExamples` faqat ssenariy darajasida |
| G5 | Mijozning gapirish tezligiga moslashish (shoshgan bo'lsa tez va qisqa) | Seed&Society, Meduzzen | Tezlik faqat kayfiyatga qarab o'zgaradi, mijozning haqiqiy tezligiga emas |
| G6 | Raqam yoki summa o'qiladigan gapda ifodalilik default bo'lsin; taklifdan oldin qisqa pauza | Deepgram, Meduzzen | Tekshirilmagan |
| G7 | Yomon real qo'ng'iroq eval case'ga aylansin | AssemblyAI meetup, Reddit | Test case faqat qo'lda yoziladi |
| G8 | AMD xatosi qimmat: bir pilotda 47% qo'ng'iroq "mashina" deb uzilgan; shubha bo'lsa "odam" | outbound pilot, Famulor | AMD qarorlari statistikasi ko'rinmaydi |
| G9 | Parallellik limiti o'lchanmasdan qo'yilgan: 1 qo'ng'iroqda 1.7 s, 6 tasida p50 6.6 s | outbound pilot | `max-concurrent-calls: 2`, o'lchov yo'q |
| G10 | Gapma-gap TTS ohangni tekislaydi; bo'laklar kattalashtirilganda turn ohangi barqarorlashgan | Deepgram/LiveKit | `SpeechOutput` gapma-gap ishlaydi (bu birinchi audioning tez chiqishi uchun ataylab qilingan) |

### Qilinmaydi
- **Klassik pipeline'da mijoz gapirayotganda backchannel ("mhm") aytish.** Noto'g'ri vaqtda aytilsa mijozning gapini bo'ladi; tadqiqotlarda ham bu ochiq muammo. Faqat `openai-live` (full-duplex) da keyinroq kuzatiladi.
- **Har turnda 2–4 filler qo'shish** (Vapi tavsiyasi). Bizdagi "kamdan-kam" qoidasi Meduzzen bilan mos, o'zgartirilmaydi.
- **Turn uchun yangi qattiq ms timer.** `no-static-waits-in-dialog` qoidasi amal qiladi.
- **Alohida "judge LLM" jonli yo'lda.** Kechikishni oshiradi; `FactGuard` + G1 yetarli.

## 2. Bosqichlar

### Bosqich 0 — o'lchov (xatti-harakat o'zgarmaydi)
Maqsad: G2, G8, G9 bo'yicha raqam olish. Keyingi bosqichlar shu raqamlarga qarab sozlanadi.
- Har bir LLM so'rovida system prompt hajmi (belgi soni + provayder qaytargan `promptTokens`): metrika
  `voice.llm.prompt.size{stage}`, `CallTraceLog` ga ham yoziladi.
- AMD qarori metrikasi `voice.amd.decision{result=human|machine|unknown}` va AMD'da o'tgan vaqt.
- Mijozning gapirish tezligi (so'z/soniya = STT final so'zlari / VAD nutq davomiyligi), faqat log/metrika.

**Tekshiruv (foydalanuvchi):** web-test va 10–20 real qo'ng'iroqdan keyin Grafana/logda uchala metrika ko'rinadi.

**Kodlandi 2026-11-06 (tekshirilmagan).** Rejadan farqi: `stage` tegi qo'yilmadi, chunki bosqich nomlarini
tenant o'zi beradi va teglar soni cheksiz o'sib ketadi. Bosqich o'rniga `llm request` log qatoriga `stage=` qo'shildi.
- `LlmMetrics.recordTurnPrompt` → `voice.llm.prompt.system.chars`, `voice.llm.prompt.tokens` (turnning birinchi
  so'rovi, tool-result round'larisiz). Chaqiriladigan joylar: `LlmTurnClient.streamTurn` / `blockingTurn`.
- `AmdVerdictListener` (yangi) → `AnsweringMachineDetector` qarorni bir marta xabar qiladi →
  `CallMetrics.amdVerdict` → `voice.amd.verdicts{result,path=ai|operator}`, `voice.amd.verdict.ms`.
  Mashina deb o'tkazib yuborilganlar allaqachon bor metrikada ko'rinadi: `voice.calls.voicemail.detected{by=transcript}`.
- `SpeechGate.lastUtteranceSpeechMs()` (pauzalarsiz VAD nutq vaqti) → `SttStreamBridge.recordSpeechRate` →
  `voice.stt.caller.words.per.second{language}` + `caller pace:` log. Kamida 600 ms nutq va 2 so'z bo'lgandagina yoziladi.
- Testlar: `AnsweringMachineDetectorTest` (3 ta yangi), `SpeechGateTest` (1 ta yangi).
- Grafana dashboard (`docs/grafana/voice-agent-dashboard.json`) yangilanmadi.

### Bosqich 1 — TTS oldidan himoya (G1 + G3)
- `SpeechTextNormalizer` ga deterministik tozalash: `{...}`/`[...]` JSON bo'laklari, kod bloki,
  `functions.x(` / `tool_call` izlari, markdown belgilar (`*`, `_`, `#`, `` ` ``) va URL. Gap butunlay
  JSON bo'lsa — tashlab yuboriladi va WARN log yoziladi (`refused` loglari uslubida).
- `BargeInRecoveryTest` ga invariant qo'shiladi: gap bo'lingandan keyin keyingi so'rovda system prompt va
  bosqich ko'rsatmasi saqlanib qoladi.

**Tekshiruv:** `SpeechTextNormalizerTest` ga 6–8 ta holat (JSON, tool izi, markdown, oddiy matn o'zgarmaydi,
raqamlar buzilmaydi); `BargeInRecoveryTest` o'tadi.

**Kodlandi 2026-11-06 (tekshirilmagan).** Tekshiruvda ma'lum bo'ldiki, ko'p narsa `SpeechSanitizer` da allaqachon bor:
butun JSON, tool va bosqich nomlari, `<thought>`, `{{...}}`, markdown belgilari (`*`, `#`, `` ` `` — `stripUnspeakableCharacters`
ularni olib tashlaydi). Qo'shilgani:
- `SpeechSanitizer.isUnspeakable`: gap ichidagi JSON (`{"key":`) va `_` li har qanday identifikator
  (`get_balance`, `debt_amount`) → gap tashlanadi, javob "derailed" bo'ladi (mavjud mexanizm).
- `SpeechTextNormalizer.normalize`: URL → faqat domen (`https://www.uysot.uz/ru?x=1.` → `uysot.uz.`), `www.` olib
  tashlanadi. Faqat TTS matni o'zgaradi, transkriptda link qoladi.
- G3: system prompt umuman history'da yo'q — har so'rovda `SystemPromptFactory` qaytadan yasaydi va `.system()`
  bilan yuboradi. Shuning uchun xavf faqat history'ni noto'g'ri tahrirlash. `BargeInRecoveryTest` ga 2 ta test:
  stitch faqat kesilgan mijoz gapini olib tashlaydi, resume esa faqat oxirgi javobga qo'shiladi.
- Testlar: `SpeechSanitizerTest` (+3), `SpeechTextNormalizerTest` (+1), `BargeInRecoveryTest` (+2).

### Bosqich 2 — prompt budjeti (G2)
- `voice-agent.dialog.prompt-budget-tokens` (default Bosqich 0 dagi p95 dan biroz yuqori).
- Budjetdan oshsa WARN + metrika, xatti-harakat o'zgarmaydi.
- Ikkinchi qadam: KB/xotira bo'laklari budjet bo'yicha qisqartiriladi. Bu foydalanuvchi bilan kelishilgandan
  keyin qilinadi; system va bosqich ko'rsatmasiga tegilmaydi.

**Tekshiruv:** `SystemPromptFactoryTest` da katta KB bilan budjet ogohlantirishi chiqadi; real qo'ng'iroqda
`voice.llm.prompt.size` budjet ostida.

**Birinchi qadam kodlandi 2026-11-06 (tekshirilmagan).** Default qiymat `logs/` dagi real qo'ng'iroqlardan olindi
(2026-11-05/06, n=927): prompt tokenlari p50 7464, p95 9325, max 10100; system prompt p50 15.6k, p95 20.5k belgi.
- `DialogProperties.promptBudgetTokens` = `voice-agent.dialog.prompt-budget-tokens` (`DIALOG_PROMPT_BUDGET_TOKENS`,
  default 12000; 0 = o'chiq).
- `LlmMetrics.recordTurnPrompt(..., budget)` budjetdan oshganda `voice.llm.prompt.over.budget` ni oshiradi.
  `LlmTurnClient` esa bosqich, token, system belgilari va history hajmi bilan WARN yozadi. Hech narsa kesilmaydi.
- Test: `LlmMetricsTest` (yangi, 3 ta).
- Ikkinchi qadam (KB/xotirani budjet bo'yicha qisqartirish) **qilinmadi**: avval `over.budget` real qo'ng'iroqlarda
  chiqishi kerak, keyin foydalanuvchi bilan kelishiladi.

### Bosqich 3 — bosqich darajasidagi uslub misollari (G4)
- `StageDef.styleExamples` (ixtiyoriy). Shu bosqichda bosqich misollari ssenariy misollaridan oldin
  qo'shiladi; bosqichda misol bo'lmasa — hozirgidek ishlaydi.
- `ScenarioValidator` dagi misol cheklovlari bosqich misollariga ham qo'llanadi.
- `docs/api/scenarios.md` yangilanadi, javobda "Frontend'ga ta'siri" ro'yxati beriladi.

**Tekshiruv:** `SystemPromptFactoryTest` — OBJECTION bosqichida o'z juftligi bor, GREETING'da yo'q.

### Bosqich 4 — mijoz tezligiga moslashish (G5)
- `VoiceEmotionResolver` ga mijoz tezligi omili qo'shiladi: oxirgi 2–3 turnning o'rtacha so'z/soniyasi
  agentning bazaviy tezligiga nisbatan ±6% doirada (chegara YAML'da). Sekin gapiruvchi mijoz → sekinroq.
  Kayfiyat omili bilan ko'paytiriladi va umumiy chegara bilan cheklanadi.
- Shoshgan mijoz uchun promptga bitta ko'rsatma qo'shiladi: "javobni bir gapga qisqartir". Bu
  `DialogSession` dagi tezlik belgisiga qarab ishlaydi.

**Tekshiruv:** `VoiceEmotionResolverTest` — tez, sekin va noma'lum mijoz uchun kutilgan tezlik.
Real qo'ng'iroqda tezlik o'zgarishi logda ko'rinadi.

### Bosqich 5 — raqamli gapda ehtiyotkor ohang (G6)
- Raqam yoki summa bor gapda (`SpeechOutput` dagi `DIGIT` mezoni) emotion/role default'ga qaytariladi.
  Ifodali rejim faqat raqamsiz gaplarda ishlaydi.
- Taklif oldidagi pauza ms bilan emas, tinish belgisi bilan beriladi: raqam oldidan vergul yoki tire.
  Pauzani TTS'ning o'zi chiqaradi (`SpeechTextNormalizer`). Bu turn vaqtiga ta'sir qilmaydi.

**Tekshiruv:** unit test — summali gapda role/emotion default; quloq bilan 3–4 qo'ng'iroq solishtiriladi.

### Bosqich 6 — real qo'ng'iroqdan eval case (G7)
- `POST /api/ai-agents/{id}/test-cases/from-call/{callAttemptId}`: transkriptning mijoz tomonidagi gaplari
  test case'ga yoziladi, nom va kutilgan natija foydalanuvchi tomonidan tahrirlanadi. `AgentEvaluationService`
  orqali, `callrecord` ga UseCase orqali murojaat qilinadi.
- `docs/api/ai-agents.md` yangilanadi, "Frontend'ga ta'siri" beriladi (qo'ng'iroq sahifasida tugma).

**Tekshiruv:** `.http` dan yaratilgan case eval'da yuradi.

### Bosqich 7 — AMD va parallellik sozlash (G8, G9) — asosan foydalanuvchi o'lchovi
- Bosqich 0 dagi AMD statistikasiga qarab `vad.amd.*` qiymatlari ko'rib chiqiladi. Qoida: shubha bo'lsa
  "odam" — `unknown` qaror hozir qanday ishlashi kodda tekshiriladi va kerak bo'lsa shunga moslanadi.
- Parallellik: `VoiceCallSimulator` bilan 1/2/4/6 parallel qo'ng'iroqda `first_audio` p95 o'lchanadi
  (foydalanuvchi ishga tushiradi). `max-concurrent-calls` shu p95 bo'yicha qo'yiladi.

**Tekshiruv:** jadval: parallellik → p50/p95; yangi limit shu jadvalga asoslangan.

### Bosqich 8 — TTS bo'lak hajmi tajribasi (G10) — flag ortida
- Birinchi gap hozirgidek darhol yuboriladi. Undan keyingi qisqa gaplar (< N belgi) navbatdagi gap bilan
  birlashtiriladi. `voice-agent.tts.stream.merge-short-sentences=false` default.
- Faqat Bosqich 0–7 dan keyin, A/B quloq bilan solishtiriladi.

**Tekshiruv:** flag yoqilganda `first_audio` o'zgarmaydi (metrika), ohang subyektiv baholanadi.

## 2a. Bosqich 3–8 natijasi (2026-11-06, bitta so'rovda)

- **Bosqich 3 — kod kerak bo'lmadi.** `StageDef.examples` allaqachon bor: `SystemPromptFactory.appendStageExamples`
  uni turn annex'ga "HOW TO PHRASE IT IN THIS STAGE" deb qo'shadi, `ScenarioValidator` tekshiradi,
  `docs/api/scenarios.md` da hujjatlangan. G4 jadvaldagi "faqat ssenariy darajasida" degan da'vo noto'g'ri edi.
- **Bosqich 4 — kodlandi, default'da o'chiq.** `CallerPaceProperties` (`voice-agent.dialog.caller-pace.*`: `enabled=false`,
  `reference-words-per-second=2.5`, `max-adjust-percent=6`, `hurried-ratio=1.3`). `SpeechRate` (agent/stt) so'z/soniyani
  hisoblaydi, `CallerPaceTracker` (agent/dialog) oxirgi 3 gapning o'rtachasini oladi (kamida 2 gap kerak) va
  `DialogSession.setCallerPace(factor, hurried)` ga yozadi. Uni `AudioPipelineAssembler` yakuniy transkriptda,
  `onClientFinal` dan oldin chaqiradi. `VoiceEmotionResolver` tezlikni shu omilga ko'paytiradi. Mijoz shoshayotgan
  bo'lsa, `SystemPromptFactory.turnAnnex` ga "bitta qisqa gap" ko'rsatmasi qo'shiladi. Faqat cascade, realtime yo'q.
  **Yoqishdan oldin** `reference-words-per-second` ni `voice.stt.caller.words.per.second` p50 qiymatiga qo'ying.
- **Bosqich 5 — kodlandi.** `VoiceEmotionResolver.resolveForSentence`: raqam bor gapda role `neutral` bo'ladi, tezlik
  saqlanadi. `SpeechOutput.speak` va `prepare` (javob gaplari) shu metodni ishlatadi. Filler, bridge va boshqa
  qatorlar o'zgarmadi. Raqam oldidan tinish belgisi bilan pauza qilish **qilinmadi**: normalizer raqamni so'zga
  aylantiradi va qo'shimcha vergul sun'iy eshitilishi mumkin. Avval quloq bilan baholash kerak.
- **Bosqich 6 — faqat frontend.** Avval backend'ga `POST /api/calls/{callId}/eval-case` yozilgan edi, keyin u olib
  tashlandi: shu ish allaqachon bor ekan — `POST /api/call-reviews/{callId}/test-case` (`QUALITY_REVIEW_EDIT`).
  U qo'ng'iroq agentini o'zi aniqlaydi va holatni sifat qatoriga bog'laydi. Unga frontend modali ham bor
  (`CallScorecardTab`), lekin u yerda personani qo'lda yozish kerak edi. Endi modalda "Transkriptdan to'ldirish"
  tugmasi bor (`robot-call-front/src/screens/calls/CallScorecardTab.jsx`, `personaFromTranscript`): mijoz gaplari
  raqamlangan persona bo'ladi (≤4000 belgi). Transkriptni `CallDetailDrawer` uzatadi. API o'zgarmadi.
- **Bosqich 7 — kod kerak bo'lmadi.** AMD'da "unknown" holati yo'q: faqat uzluksiz uzun nutq "mashina" deb olinadi,
  oyna yopilsa — "odam". Demak "shubha bo'lsa — odam" qoidasi allaqachon dizaynda bor. O'lchov (foydalanuvchi):
  1) bir necha kun `voice_amd_verdicts_total{result}` ulushi va `voice_calls_voicemail_detected_total{by="transcript"}`
     (AMD o'tkazib yuborgan mashinalar) ko'riladi; machine ulushi baland bo'lsa, `logs` dagi
     `answering machine: N ms of continuous speech` qatorlarining yozuvlari tinglanadi va `VAD_AMD_MIN_SPEECH_MS` ko'tariladi;
  2) parallellik: `POST /api/ai-agents/{id}/evaluate?mode=VOICE&repeat=N` ni 1/2/4/6 parallel qo'ng'iroqda yurgizib,
     `voice_turn_stage_latency{stage="first_audio"}` p95 yoziladi; `DIALER_MAX_CONCURRENT` shu jadval bo'yicha qo'yiladi.
- **Bosqich 8 — kod kerak bo'lmadi.** `ChunkerProperties.minChunkChars` (`DIALOG_CHUNKER_MIN_CHUNK_CHARS`, default 20)
  birinchi gapdan keyingi qisqa bo'laklarni keyingisiga qo'shib yuboradi, birinchi gap esa erta kesiladi. Tajriba:
  qiymatni 60–80 ga ko'tarib, `first_audio` o'zgarmaganini va ohangni quloq bilan solishtirish.

Testlar (yangi yoki kengaytirilgan): `CallerPaceTrackerTest` (5), `VoiceEmotionResolverTest` (+2).

## 3. Tartib va ustuvorlik
0 → 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8. Eng arzon va eng katta xavfni yopadigani — 1. Eng ko'p
"insondek" foyda — 3 va 4. Bosqich 7 asosan foydalanuvchining o'lchovi.

## Holat
- 2026-11-06: reja yozildi.
- 2026-11-06: Bosqich 0 kodlandi, tekshirilmagan. Keyingisi — Bosqich 1, faqat "keyingi bosqich" deyilgandan keyin.
- 2026-11-06: Bosqich 1 kodlandi, tekshirilmagan.
- 2026-11-06: Bosqich 2 ning birinchi qadami (faqat ogohlantirish, budjet 12000) kodlandi, tekshirilmagan.
- 2026-11-06: Bosqich 3–8 bajarildi (3, 7, 8 — kod kerak emas edi, 4–6 kodlandi), tekshirilmagan. Reja yopildi.
  Ochiq qolganlar: caller-pace reference'ni real p50 bilan yoqish, KB/xotirani budjet bo'yicha qisqartirish (2-qadam),
  raqam oldidan pauza.
- 2026-11-06: Bosqich 6 qayta qilindi: backend `/eval-case` olib tashlandi (`call-reviews/{id}/test-case` bilan takror edi),
  o'rniga mavjud modalga "Transkriptdan to'ldirish" tugmasi qo'shildi (frontend).
