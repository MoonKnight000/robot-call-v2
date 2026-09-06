# VOICE QUALITY PLAN — tezlik · tabiiylik · til

> Ochiq kodli yetakchi voice-agent loyihalari (LiveKit Agents, Pipecat / smart-turn,
> Deepgram Flux, Vapi/Retell amaliyoti, AssemblyAI/Soniox, Aisha AI / Navoiy TTS) qanday
> muammolarga duch kelgani va ularni qanday yechgani o'rganildi; natija shu loyihaning
> hozirgi kodi bilan solishtirilib, farqlar reja qilib chiqildi.
>
> Sana: 2026-09-03 · Manbalar ro'yxati oxirida.

---

## 0. Xulosa

Bizda cascade pipeline (STT → LLM → TTS) sanoat darajasida qurilgan: streaming, barge-in,
false-barge-in resume, Smart Turn v3, speculation (preemptive generation), TTS kesh va
warm-up, fact guard, fast-path router. Ya'ni sanoatda sanaladigan optimizatsiyalarning
taxminan yarmi allaqachon kodda bor.

Qolgan farq uchta joyda to'plangan:

| Yo'nalish     | Asosiy muammo                                                                             | Eng katta yutuq                                        |
|---------------|-------------------------------------------------------------------------------------------|--------------------------------------------------------|
| **Tezlik**    | Turn budjetining eng katta bo'lagi — jim turib kutish (`post-roll-ms: 1000`)              | EOU kutishini 1000 → 300–500 ms ga tushirish           |
| **Tabiiylik** | Bot faqat "gapiradi"; tinglayotganini bildirmaydi, kesilganda so'z aniqligida to'xtamaydi | Word-level truncation + bot backchannel'lari           |
| **Til**       | uz-UZ tayyor semantik EOU modellarida yo'q; mijoz aralash uz+ru gapiradi                  | O'z yozuvlarimizda smart-turn fine-tune + STT gold-set |

Ustiga ikkita poydevor yetishmayapti: **turn ichidagi latency taqsimoti o'lchanmaydi**
(qayerni tuzatish kerakligi faktga asoslanmaydi) va **regression harness yo'q** (tuning
qilinganda nima buzilgani bilinmaydi). Shuning uchun reja Blok 0 va Blok D dan boshlanadi.

---

## 1. Hozirgi holat (kod bo'yicha tekshirilgan)

**Bor:**

- `agent/dialog/Speculation.java` + `TurnRunner` — interim transcript ustida LLM'ni oldindan
  ishga tushirish (LiveKit'dagi `preemptive_generation`, Deepgram'dagi `EagerEndOfTurn` bilan
  bir xil g'oya). `voice.llm.speculation.hit/miss` metrikasi ham bor.
- `agent/turn/SmartTurnDetector.java` — Smart Turn v3 ONNX, faqat **kutishni uzaytirish** uchun;
  `SmartTurnProperties.languages` da uz-UZ yo'q (to'g'ri qaror: model uz'da o'qitilmagan).
- `agent/stt/DynamicEndpointingProperties.java` — kutishni suhbatdoshga moslash, **default o'chiq**.
- `ClientInputGate` + `Backchannels` — mijozning "ha/aha"sini interruption deb hisoblamaslik;
  `DialogProperties.falseInterruptionTimeoutMs` + `resumeInterruptedReply` — soxta barge-in'dan keyin
  gapni davom ettirish. Bu LiveKit'ning `false_interruption_timeout` + `resume_false_interruption`
  ekvivalenti.
- `TurnRunner:228` — kesilganda tarixga faqat `spokenText()` yoziladi (**jumla** aniqligida).
- `tts/TtsCache`, `TtsWarmup`, `TtsRouter` failover; `SpeechTextNormalizer` + `UzbekNumberWords` /
  `RussianNumberWords`; `dialog/UzbekNumberParser`, `SpelledNumber`.
- `FastPathRouter` — noto'g'ri raqam / operator / DNC uchun LLM'siz javob (<20 ms).
- `FactGuard`, `PiiRedactor`, `NoInputWatchdog`, `SentimentDetector`, `VoiceEmotionResolver`,
  `AnsweringMachineDetector`, `agent/realtime/*` (speech-to-speech provayderlar).
- `VoiceMetrics` — `voice.turnaround.latency` p95 SLO bilan, `voice.llm.turn.latency`,
  `voice.tts.synth.latency`.

**Yo'q:**

- Turn ichidagi bosqichma-bosqich latency (EOU kutish · STT final · LLM TTFT · TTS TTFB · birinchi paket).
- Bot tomonidan chiqariladigan backchannel ("ha", "tushunarli") — `Backchannels` faqat **filtr**.
- So'z aniqligidagi truncation (TTS word timestamps ↔ RTP'ga chiqqan audio).
- uz-UZ uchun semantik EOU; uz STT sifatini o'lchagan gold-set (`SttComparisonTool` bor, korpus yo'q).
- Aralash uz+ru nutq (code-switching) bilan ishlash — `DialogLanguageSwitcher` faqat **ochiq iltimos**ni
  regex bilan ushlaydi.
- STT'ga domain lug'ati (keyterms / speech contexts / hints) berilmaydi.
- Semantik javob keshi (FAQ/e'tirozlar), model routing (oson turn → kichik model).
- Eval/regression harness: replay, simulyatsiya qilingan mijoz, LLM-judge.

---

## 2. Blok 0 — O'lchov (birinchi, hammasi shunga tayanadi)

> LiveKit'ning latency qo'llanmasidagi asosiy maslahat: **avval dominant bosqichni aniqla**,
> keyin optimallashtir. Hozir bizda faqat yakuniy `turnaround` bor — u 1.4 s bo'lsa, buning
> 1000 ms'i kutish ekanini yoki LLM ekanini ayta olmaymiz.

**0.1 — Turn latency ledger.** Har bir turn uchun bitta struktura: `vadCloseMs` (VAD jim dedi) →
`eouWaitMs` (post-roll + smart-turn extend) → `sttFinalMs` → `llmTtftMs` → `ttsTtfbMs` →
`firstPacketMs`. Metrika sifatida ham (`voice.turn.stage.latency{stage=...}` timer,
`publishPercentileHistogram`), structured log sifatida ham (call id bilan) chiqadi.
*Tegiladigan joy:* `agent/metrics/VoiceMetrics`, `TurnRunner`, `SttStreamBridge`, `SpeechOutput`.
*Tekshiruv:* 10 ta test qo'ng'iroqdan keyin log'da har turn uchun 6 ta raqam bor va ularning
yig'indisi `voice.turnaround.latency` ga ±50 ms da mos keladi.

**0.2 — Speculation va kesh samaradorligi paneli.** `speculation.hit / started` nisbati,
`tts.cache.hits`, `llm.tokens.cached` — bitta panelda. Speculation hit-rate 60% dan past
bo'lsa u pul yoqmoqda; yuqori bo'lsa `preemptiveMinChars` ni pasaytirish mumkin.
*Tekshiruv:* panel bor va 50 ta qo'ng'iroqdan keyin raqamlar mantiqiy.

---

## 3. Blok A — Tezlik

Maqsad: `voice.turnaround.latency` p95 **< 800 ms** (sanoat o'lchovi: vanilla LiveKit 1.2–1.4 s,
optimallashtirilgani 500–650 ms; Deepgram Flux EOT ~260 ms).

**A.1 — EOU kutishini qisqartirish (eng katta yutuq: 300–600 ms).**
Hozir `post-roll-ms: 1000`, qisqa javob uchun `short-silence-ms: 500`. Ikki qadam:
1. `voice-agent.stt.endpointing.dynamic.enabled: true` (kod tayyor, default o'chiq) — tez
   gapiradigan mijozda kutish o'zi qisqaradi (floor 600 ms).
2. Smart Turn'ni **ikki tomonlama** qilish: hozir u faqat `maxExtendMs` qo'shadi; ehtimollik
   yuqori bo'lsa (masalan ≥0.85) kutishni floor gacha **qisqartirsin** — lekin faqat
   `SmartTurnProperties.languages` ichidagi tillar uchun (hozircha ru-RU).
*Tekshiruv:* ru qo'ng'iroqlarda `stage=eou` p50 1000 → ≤500 ms; `voice.stt.turn.extended`
o'smasin; bitta gapning ikkita turn'ga bo'linishi 5% dan oshmasin.
*Xavf:* mijozni kesib qo'yish — shuning uchun 0.1 ledger'siz ishga tushirilmaydi.

**A.2 — Speculative TTS (200–400 ms).**
Hozir speculation LLM'da to'xtaydi: matn yoziladi, lekin **sintez** boshlanmaydi. Interim
ustidan yozilgan javobning **birinchi jumlasini** TTS'ga ham berib, PCM'ni buferga olish;
final mos kelsa — audio darhol chiqadi (TTFB ≈ 0), mos kelmasa — bufer tashlanadi.
*Tegiladigan joy:* `Speculation`, `TurnRunner#speculate`, `SpeechOutput` (buferlangan chiqish).
*Tekshiruv:* speculation hit bo'lgan turnlarda `stage=tts_ttfb` ≈ 0; behuda sintez
(`voice.tts.chars.synthesized` o'sishi) 30% dan oshmasin.

**A.3 — Prompt/KV-cache barqarorligi (100–400 ms + narx).**
`SystemPromptFactory` da stablePrefix/turnAnnex bo'linishi bor — buni oxirigacha ishlatish:
xabarlar tartibi har turnda o'zgarmasin (fakt injection turn oxirida bo'lsin, boshida emas),
`historyMaxMessages` kesishi prefix'ni buzmasin.
*Tekshiruv:* `voice.llm.tokens.cached / prompt` nisbati 3-turndan keyin ≥70%.

**A.4 — Model routing (100–300 ms).**
`FastPathRouter` deterministik intentlarni allaqachon oladi. Oradagi qatlam: qisqa/tasdiq
turnlari → kichik/tez model, murakkab e'tiroz → asosiy model. Tanlov `stateScopedTools` bilan
bir joyda turadi.
*Tekshiruv:* turnlarning ≥40% kichik modelga tushsin, ularda `stage=llm_ttft` p95 ≤250 ms;
outcome sifati (Blok D) pasaymasin.

**A.5 — Semantik javob keshi (hit'da 400–800 ms).**
`KnowledgeBaseService` hozir hardcode ro'yxat. Uni embedding + o'xshashlik qidiruviga aylantirib,
javob matnini **va uning tayyor audiosini** (`TtsCache`) qaytarish: "qanday to'layman", "kim
bo'lasiz", "qaysi bankdan" — kampaniyalarda takrorlanadigan savollar.
*Tekshiruv:* eng ko'p 20 ta savol bo'yicha hit ≥60%, hit'da turnaround ≤300 ms.

**A.6 — Ulanishlarni oldindan isitish.**
TTS uchun `TtsWarmup` bor; STT sessiyasi va LLM ulanishi ham qo'ng'iroq ko'tarilishidan **oldin**
(originate paytida) ochilsin; salomlashish audiosi har doim keshdan chiqsin.
*Tekshiruv:* birinchi turn latency'si keyingi turnlardan ≤100 ms farq qilsin.

---

## 4. Blok B — Odamga o'xshash suhbat

**B.1 — So'z aniqligida truncation (eng ko'p "sun'iylik" shu yerdan).**
Hozir kesilganda tarixga oxirgi **jumla** butunicha yoki umuman yozilmaydi. Natija: model
"men aytdim-ku" deydi, mijoz esa eshitmagan. Yechim: TTS word-level timestamp (Aisha word
timestamp beradi; Cartesia/ElevenLabs ham) + RTP'ga haqiqatan yuborilgan audio davomiyligi →
"qaysi so'zgacha eshitildi" hisoblanadi va tarixga faqat o'sha qism yoziladi. Provayder
timestamp bermasa — chiqarilgan audio uzunligi bo'yicha proporsional taxmin (Azure'ning
`auto_truncate` yondashuvi ham heuristik).
*Tegiladigan joy:* `TtsProvider` (timestamp chiqishi), `SpeechOutput`, `DialogSession#spokenText`.
*Tekshiruv:* bot gapirayotganda 2-so'zdan keyin kesilsa — transkriptda ham faqat 2 so'z turadi.

**B.2 — Bot backchannel'lari.**
`Backchannels` faqat mijozniki uchun filtr. Mijoz uzoq (>4–5 s) gapirsa, bot past ovozda
"ha", "tushunarli", "хорошо" chiqarsin — tayyor audio, keshdan, kanalga aralashtirib.
Deepgram: backchannel ≠ interruption; bu "tinglayapman" signali va suhbatning eng odamga
o'xshaydigan qismi.
*Tekshiruv:* 30 s dan uzun mijoz javobida kamida bitta backchannel; u mijozni kesib qo'ymasin —
10 ta yozuvni qo'lda tinglash.

**B.3 — Adaptiv interruption.**
Hozir filtr faqat **so'z ro'yxati + so'z soni**. Qo'shiladi: `minInterruptionDurationMs`
(qisqa yo'tal/shovqin o'tmasin), turn chegarasidagi cooldown (LiveKit'dagi
`backchannel_boundary` ekvivalenti), backchannel leksikasini kengaytirish ("hmm", "shundaymi",
"bo'pti", "давай").
*Tekshiruv:* `voice.dialog.false.barge.ins` kamaysin, haqiqiy barge-in'da javob ≤200 ms da to'xtasin.

**B.4 — Mijoz cho'zib ketganda bot kirishi.**
LiveKit'da `max_words` / `max_duration` → `on_user_turn_exceeded`. Bizda faqat teskarisi bor
(NoInputWatchdog). Mijoz 25–30 s to'xtovsiz gapirsa, bot muloyim kirishi kerak ("kechirasiz,
to'g'ri tushundimmi...").
*Tekshiruv:* 40 s monologda bot 30 s atrofida kiradi va suhbat uzilmaydi.

**B.5 — Prompt darajasidagi tabiiylik.**
`SystemPromptFactory` ga: gaplar qisqa (≤2 gap), raqamlar og'zaki, ro'yxat o'qilmaydi, o'zbekcha
tabiiy bog'lovchilar ("shunday, ...", "ha, ma'lumotga ko'ra ..."), pauza SSML bilan
(`<break time="300ms"/>`), few-shot "sun'iy ↔ tabiiy" misollari. Muhimi: filler'ni **pauza bilan
juftlash** — "hmm" deb aytib, keyin to'liq tezlikda davom etish soxta eshitiladi.
`VoiceEmotionResolver` chiqaradigan emotsiya bitta "xotirjam" chizig'ida qolsin.
*Tekshiruv:* 10 ta yozuvni 3 kishi 1–5 shkalada baholaydi, o'rtacha ≥4.

**B.6 — Tool ishlayotgandagi "o'ylash" tovushi.**
`fillerDelayMs: 900` bor, lekin u LLM sekinligiga qaraydi; CRM/DB tool chaqirig'i ham shu
mexanizmga ulansin ("bir soniya, tekshiryapman"). Kutilishi aniq tool'lar (mijoz ma'lumoti)
LLM bilan **parallel** oldindan chaqirilsin.
*Tekshiruv:* tool >700 ms davom etsa, jimlik bo'lmaydi.

**B.7 — Tushunmovchilikni tuzatish (repair).**
STT ishonchi past yoki raqam noaniq bo'lsa — takrorlab tasdiqlash ("to'qqiz-yetti-uch, to'g'rimi?").
`SpelledNumber` bor, uni STT confidence bilan bog'lash kerak.
*Tekshiruv:* shovqinli yozuvda noto'g'ri raqam CRM'ga yozilmaydi.

---

## 5. Blok C — Til (uz/ru) — loyihaning asosiy raqobat afzalligi

**C.1 — Aralash nutq (code-switching).**
Monolingual ASR aralash nutqda 30–50% ko'proq xato beradi; O'zbekistonda mijoz bir gapda ikki
tilda gapiradi. Qadamlar: STT'da avtomatik til aniqlash rejimini (Yandex `auto-detect-model`,
Aisha dialect-aware) sinash; `LanguageDetector` ni har turnda ishlatib **javob tilini** mijoz
tiliga moslash; TTS ovozini til bilan birga almashtirish; prompt'da "mijoz qaysi tilda gapirsa,
shu tilda javob ber" qoidasi. `DialogLanguageSwitcher` (ochiq iltimos) o'z joyida qoladi.
*Tekshiruv:* aralash 20 ta yozuvda bot tili mijoz tiliga 1 turn ichida moslashadi.

**C.2 — STT'ga domain lug'ati (keyterms / speech contexts / hints).**
Ism-familiya, shartnoma raqami formati, joy nomlari, brendlar (Payme, Click, Uzum, MIB) — hozir
STT'ga umuman berilmayapti. Lug'at `ScenarioDefinition.factSchema` va mijoz ma'lumotidan
avtomatik yig'ilsin hamda provayder API'siga uzatilsin (Deepgram keyterm, Google speech
contexts, Yandex hints).
*Tekshiruv:* gold-set'da ism/raqam WER'i ≥20% yaxshilanadi.

**C.3 — Uzbek gold-set: provayder tanlovi faktga asoslansin.**
`SttComparisonTool` bor — unga 2–3 soatlik, **o'z qo'ng'iroqlarimizdan** olingan, qo'lda
transkript qilingan korpus kerak (8 kHz telefon sifati, shovqin, aralash til). Keyin Yandex /
Aisha / Google / Deepgram / ElevenLabs Scribe WER bo'yicha solishtiriladi (Scribe uz'ni 10–25%
WER "good" darajasiga qo'yadi — bizning domenda tekshirilishi shart).
*Tekshiruv:* WER jadvali hujjatda; default provayder shu jadval asosida tanlanadi.

**C.4 — Uzbek EOU modeli (eng qimmat, eng qaytimli).**
Smart Turn v3 BSD-2: og'irliklar, o'qitish skripti va ma'lumotlari ochiq; CPU'da 12–65 ms, 8 s
audio. uz-UZ qo'llab-quvvatlanmaydi — **lekin bizda yozuvlar bor**. Avtomatik belgilash: VAD
jimlikdan keyin mijoz 1.5 s ichida yana gapirsa → `INCOMPLETE`, gapirmasa → `COMPLETE`. Bir necha
ming segment yig'ilib fine-tune qilinadi, ONNX'ga eksport qilinadi va
`SmartTurnProperties.languages` ga `uz-UZ` qo'shiladi.
*Tekshiruv:* hold-out'da accuracy ≥85%; A/B da uz qo'ng'iroqlarda EOU p50 ≥300 ms qisqaradi va
gap o'rtasidan kesilish oshmaydi.

**C.5 — Uzbek TTS sifati va narxi.**
Aisha'ning Navoiy TTS'i (CosyVoice2-0.5B asosida) ochiq kod — o'zimizda hosting qilish TTFB va
narxni tushirishi, brend ovozini (bitta ovoz butun kampaniya uchun) berishi mumkin. Avval
o'lchov: hozirgi provayderning TTFB p95 va 1000 belgi narxi ↔ self-host.
*Tekshiruv:* qaror hujjati (jadval bilan), keyin kerak bo'lsa `TtsProvider` implementatsiyasi.

---

## 6. Blok D — Sifatni o'lchash (tuning'ni xavfsiz qiladi)

**D.1 — Replay harness.** Saqlangan qo'ng'iroq audiosini RTP simulyatori orqali pipeline'ga
qaytadan uzatish (haqiqiy SIP'siz). Chiqishda: transkript, turn chegaralari, outcome — gold bilan
solishtiriladi. Har release'dan oldin ishlaydi.
*Tekshiruv:* 30 ta yozuvda ishlaydi va ataylab buzilgan sozlama (masalan post-roll 200 ms) uni
qizartiradi.

**D.2 — Simulyatsiya qilingan mijoz.** LLM persona + TTS → Asterisk local channel orqali o'z
tizimimizga qo'ng'iroq. 20–50 stsenariy: rad etish, noto'g'ri raqam, shovqin, tez gapiruvchi,
ruschaga o'tish, avtojavob, "keyin qo'ng'iroq qiling", agressiv mijoz.
*Tekshiruv:* bitta buyruq bilan 20 ta qo'ng'iroq o'tadi va hisobot beradi.

**D.3 — LLM-judge baholash.** Har qo'ng'iroqqa rubrika: guardrail buzilganmi, outcome to'g'rimi,
mijozni kesib o'tdimi, tabiiylik, til mosligi. Production'da har kuni tasodifiy tanlov.
*Tekshiruv:* 20 ta qo'ng'iroqda judge bahosi inson bahosiga ≥80% mos.

**D.4 — Muvaffaqiyatsiz qo'ng'iroq → test case.** Yiqilgan har bir real qo'ng'iroq D.1
to'plamiga qo'shiladi (Hamming/Coval amaliyoti).

---

## 7. Tartib va sabab

| № | Bosqich                           | Nega shu tartibda                                             |
|---|-----------------------------------|---------------------------------------------------------------|
| 1 | **0.1, 0.2** — o'lchov            | O'lchovsiz tuning taxmin; qaysi bosqich dominant — bilinmaydi |
| 2 | **D.1** — replay harness          | Tezlikni qisqartirish suhbatni buzishi mumkin; to'r kerak     |
| 3 | **A.1, A.3, A.6** — arzon tezlik  | Kodning ko'p qismi bor, faqat yoqish va tuning                |
| 4 | **B.1, B.2, B.3** — tabiiylik     | Eng ko'p sezilaydigan sifat sakrashi                          |
| 5 | **C.1, C.2** — til                | WER to'g'ridan-to'g'ri outcome'ga ta'sir qiladi               |
| 6 | **C.3, C.4** — korpus + uz EOU    | Ma'lumot yig'ish vaqt oladi: erta boshlanadi, kech tugaydi    |
| 7 | **A.2, A.4, A.5** — chuqur tezlik | Faqat 0.1 raqamlari shuni ko'rsatsa                           |
| 8 | **D.2, D.3, B.4–B.7, C.5**        | Barqarorlashtirish va sayqal                                  |

---

## 8. Ataylab **qilinmaydigan** ishlar

- **To'liq speech-to-speech'ga o'tish.** `agent/realtime/*` bor va kerak bo'lganda ishlatiladi,
  lekin FactGuard summani **aytilishidan oldin** to'sa olmaydi (realtime'da faqat eskalatsiya
  qoladi) — qarzdorlik domenida bu asosiy risk. Cascade default bo'lib qoladi.
- **Yangi provayder qo'shish** — C.3 jadvali talab qilmaguncha yo'q.
- **`agent/` ni refactor qilish** — muammo arxitekturada emas; reja mavjud tuzilma ustiga qo'shiladi.

---

## 9. Manbalar

- LiveKit — [agent latency](https://livekit.com/blog/understand-and-improve-agent-latency) ·
  [turn detection & interruptions](https://livekit.com/blog/turn-detection-and-interruption-handling) ·
  [turn-detector modeli](https://huggingface.co/livekit/turn-detector) ·
  [realistik prompting](https://livekit.com/blog/prompting-voice-agents-to-sound-more-realistic/) ·
  [multilingual switching](https://livekit.com/blog/build-multilingual-voice-agent-automatic-language-switching)
- [LiveKit latency: 12 texnika (2026)](https://futureagi.com/blog/how-to-optimize-livekit-latency-2026/)
- Pipecat — [Smart Turn v3](https://huggingface.co/pipecat-ai/smart-turn-v3) ·
  [e'lon: 12 ms CPU inference](https://www.daily.co/blog/announcing-smart-turn-v3-with-cpu-inference-in-just-12ms/) ·
  [smart-turn repo](https://github.com/pipecat-ai/smart-turn)
- Deepgram — [Flux STT](https://deepgram.com/learn/introducing-flux-conversational-speech-recognition) ·
  [Flux 10 tilga kengaydi](https://siliconangle.com/2026/04/29/deepgram-expands-flux-10-languages-mid-call-switching-voice-agents/) ·
  [backchannel vs interruption](https://deepgram.com/learn/backchannels-vs-interruptions-voice-agents)
- [Azure voice live auto-truncation](https://learn.microsoft.com/en-us/azure/ai-services/speech-service/how-to-voice-live-auto-truncation)
- ASR — [AssemblyAI endpointing](https://www.assemblyai.com/blog/turn-detection-endpointing-voice-agent) ·
  [keyterms prompting](https://www.assemblyai.com/blog/streaming-keyterms-prompting) ·
  [Deepgram keyterm](https://developers.deepgram.com/docs/keyterm) ·
  [Gladia code-switching](https://www.gladia.io/blog/what-is-code-switching-in-speech-recognition)
- O'zbek tili — [Aisha AI STT/TTS](https://aisha.group/en/products) ·
  [Navoiy TTS (ochiq kod)](https://aisha.group/en/blog/navoiy-tts-open-source-uzbek-text-to-speech) ·
  [ElevenLabs Scribe uz](https://elevenlabs.io/speech-to-text/uzbek) ·
  [Uzbek Speech Corpus](https://dl.acm.org/doi/10.1007/978-3-030-87802-3_40)
- Evals — [Coval qo'llanmasi](https://www.coval.ai/blog/voice-ai-agent-evaluation-guide/) ·
  [Hamming testing guide](https://hamming.ai/resources/voice-agent-testing-guide) ·
  [Cekura metrikalar](https://www.cekura.ai/blogs/voice-ai-evaluation-metrics)

---

## 10. Bajarilish holati (2026-09-03)

| Band                            | Holat          | Izoh                                                                                                                          |
|---------------------------------|----------------|-------------------------------------------------------------------------------------------------------------------------------|
| 0.1 Latency ledger              | ✅             | `agent/metrics/TurnLatency`, `voice.turn.stage.latency{stage}`, har turnda log qatori. `eou` bosqichi endi gate'siz ham to'ladi — `VadStream` mijoz jim bo'lganini `notifyUtteranceEnd` orqali xabar qiladi (2026-09-04). **2026-09-05:** `DialogSession.recordTurnLatency` hech qayerdan chaqirilmagani aniqlandi — `call_technical.avg/max_turn_latency_ms` 16 ta qo'ng'iroqning hammasida NULL edi, ya'ni §1.3 budjeti yoziladigan ustun hech qachon to'lmagan. `SpeechOutput.recordTurnaround` dan ulandi |
| 0.2 Samaradorlik paneli         | ✅             | `AlertingService.checkEfficiency()` + `speculation-min-hit-rate`                                                              |
| D.1 Replay harness              | ✅ CI'da       | `agent/turn/TurnReplayTool` — offline, tarmoqsiz. `--manifest` har yozuvga o'z turn sonini beradi; `.github/workflows/ci.yml` har push'da yuritadi (korpus sirlari bo'lsa). Korpus shartnomasi: `docs/REGRESSION.md` |
| A.1 EOU qisqartirish            | 🔸 qo'yildi, o'lchov kutmoqda | **Gate'ga ko'chirildi (2026-09-04), kodga tegilmadi — faqat default'lar.** `stt.vad-gating.enabled: true` + `stt.endpointing.enabled: true` + `endpointing.dynamic.enabled: true`, `post-roll-ms` 1000 → **700** (dynamic pasti 600). SpeechKit external EOU klassifikatoriga o'tdi, `yandex.eou-max-pause-hint-ms` (1200 → 900) endi faqat **fallback** — `STT_VAD_GATING=false` bo'lganda o'qiladi. Qisqa javoblar `short-silence-ms: 500` da yopiladi. **Shart:** `VAD_MODEL_PATH` — busiz gate qurilmaydi va blok jimgina ta'sirsiz qoladi (`AriService#buildAudioListeners`, startda WARN). Orqaga qaytarish: `STT_VAD_GATING=false`, deploysiz. `SmartTurnDetector.isConfidentlyComplete` + `SpeechGate.setEarlyClose` shuning ustiga qo'shiladi, lekin ONNX model fayli va uz-UZ korpusi kerak (`docs/REGRESSION.md` §4) — `turn.enabled` hamon `false`, `turn.languages` esa `ru-RU`. **2026-09-05: `post-roll-ms` 700 → 1200 ga oshirildi** — yo'nalish teskari, ataylab. C.4 dataset'i ko'rsatdiki mijoz gap o'rtasida qilgan pauzalarning uchdan biri 700 ms dan uzun, ya'ni ular kesilib, gap yarmida javob berilgan (`docs/VOICE-QUALITY-PLAN` C.4 jadvali). Qisqa javoblar bunga tegmaydi (`short-silence-ms: 500`), speculation esa bu jimlikni javob yozishga sarflaydi. **Tasdiqlash:** `stage=eou` p50 ~1200 ms ga chiqadi (bu kutilgan), lekin `voice.turnaround.latency` p95 shunga mos oshmasin — oshsa speculation hit-rate past demakdir; bitta gapning ikkiga bo'linishi kamayishi kerak |
| A.2 Speculative TTS             | ✅ yoqildi     | Guess'ning birinchi jumlasi `TtsCache` ga oldindan sintez qilinadi; `dialog.preemptive-tts: true` (2026-09-04). Ilgari o'chiq edi va speculation matnda to'xtar edi — mijoz final'dan keyin baribir to'liq Yandex round-trip'ini (`stage=tts_ttfb` 300-600 ms) to'lardi. Kuzatish: `voice.tts.chars.synthesized` ↔ `voice.tts.chars.saved` |
| A.3 Prompt/KV-cache             | ✅ tekshirildi | Prefix sessiyada bir marta quriladi, faktlar annex'da — o'zgartirish shart emas                                               |
| A.4 Model routing               | ✅             | `dialog.fast-model` (bo'sh = o'chiq), `fast-model-max-words: 3`                                                               |
| A.5 Bilim bazasi                | ✅             | `KnowledgeBaseService` **o'lik kod edi** — `TurnRunner` ga ulandi, `dialog.knowledge-base: false`. Embedding emas, kalit so'z |
| A.6 Isitish                     | ✅ tekshirildi | TTS warm-up bor, STT media boshida ochiladi, greeting disclosure bilan parallel                                               |
| B.1 Word-level truncation       | ✅             | `RtpEndpoint.queuedSamples/playedSamples` + `DialogSession.appendSpokenAudio`                                                 |
| B.2 Bot backchannel'lari        | ✅             | `SpeechOutput.speakBackchannel`, `dialog.backchannel-after-ms: 0` (o'chiq)                                                    |
| B.3 Adaptiv interruption        | ✅ qisman      | Leksika kengaytirildi; min-duration allaqachon `VAD_MIN_SPEECH_MS`                                                            |
| B.4 Bot kirishi                 | ✅             | `SpeechOutput.interject`, `dialog.interject-after-ms: 0` (o'chiq)                                                             |
| B.5 Prompt tabiiyligi           | ✅ tekshirildi | `SystemPromptFactory` allaqachon LiveKit tavsiyalarini qamraydi; SSML pauza provayder qo'llab-quvvatlashini talab qiladi      |
| B.6 Tool paytidagi filler       | ✅ tekshirildi | Mavjud filler LLM va tool vaqtini birga qoplaydi                                                                              |
| B.7 Repair                      | ✅             | STT confidence past bo'lsa turn annex tasdiqlashni buyuradi; `low-confidence-threshold: 0.55`                                 |
| C.1 Til moslashuvi              | ✅             | `LanguageDetector`/`DialogLanguageSwitcher` **o'lik kod edi** — `ClientInputGate` ga ulandi                                   |
| C.2 STT keyterms                | ✅ qisman      | `SttHints` + `SttProvider` 5-argumentli overload; hozircha faqat Google qo'llab-quvvatlaydi                                   |
| C.3 Uzbek gold-set              | ⏳             | Korpus kerak (2–3 soat qo'lda transkript qilingan yozuv)                                                                      |
| C.4 Uzbek EOU fine-tune         | 🔸 dataset tayyor | `agent/turn/TurnDatasetTool` yozuvlardan belgilangan EOU to'plamini chiqaradi (2026-09-05). **C.3 dan farqli — qo'lda transkript kerak emas**, belgini bot kanali beradi. Qolgani: yozuv yig'ish va Colab'da fine-tune |
| C.5 Uzbek TTS (Navoiy)          | ⏳             | Avval TTFB/narx o'lchovi kerak                                                                                                |
| D.2 Simulyatsiya qilingan mijoz | ✅ CI'da | `DialogSimulationRunner` + `docs/simulation-personas.json` (**20 persona**, jumladan ikkita prompt-injection). Endi xato bo'lsa 1 qaytaradi va ilovani to'xtatadi; `.github/workflows/simulation.yml` kechasi va talab bo'yicha yuritadi — har push'da emas, chunki bir yurish ~400 LLM chaqiruvi. Audio yo'q — u D.1 ning ishi |
| D.3 LLM-judge | ✅ | `CallQualityJudge`, `voice.qa.score` / `voice.qa.flags`; DB'ga yozilmaydi — migratsiya shart emas  |

### C.4 — EOU dataset tool (2026-09-05)

`agent/turn/TurnDatasetTool` — yozilgan qo'ng'iroqlardan Smart Turn fine-tune uchun
belgilangan to'plam yasaydi. `TurnReplayTool` bilan bir xil shakl: oddiy `main()`,
Spring'siz, tarmoqsiz, o'sha `SileroVad` + `VadStream`.

**Belgilash qoidasi rejadagidan yaxshiroq.** Reja §C.4 da "1.5 s ichida yana gapirsa
INCOMPLETE" degan chegara bor edi; stereo yozuvda esa javob to'g'ridan-to'g'ri turibdi —
mijoz to'xtagandan keyin **bot gapirgan bo'lsa** navbat tugagan (`COMPLETE`), **mijozning
o'zi qayta boshlagan va bot jim bo'lsa** tugamagan (`INCOMPLETE`). Ikkalasi ham
bo'lmasa (yozuv tugadi, yoki javobsiz uzoq jimlik — nosozlik) hech narsa chiqarilmaydi:
noto'g'ri belgi yo'q belgidan yomonroq.

SPATIAL_STEREO cross-feed teskari yechiladi (`caller = (L − 0.35·R)/(1−0.35²)`) — busiz
botning 0.35 dagi ovozi VAD uchun nutq bo'lib ko'rinadi va "mijoz pauzasi" botning gapi
o'rtasiga tushadi.

Klip modelning oynasi: 8 s, 16 kHz, mijoz jim bo'lgandan **`--tail-ms` keyin tugaydi**.
Bu bezak emas — inference'da model gate kutib bo'lgandan keyin so'raladi, ya'ni orqadagi
jimlikni ko'radi; oxirgi so'zda kesilgan to'plam unga hech qachon kelmaydigan audioni
o'rgatadi. `--tail-ms` amaldagi `post-roll-ms` ga teng turishi kerak.

Yozuvlarni MinIO'dan olish (obyekt `/data/files/<path>/<id>/part.1`, RIFF oldida 32 bayt
MinIO sarlavhasi bor — tashlab yuboriladi):

```bash
docker exec robot-call-minio sh -c 'cat /data/files/<path>/<id>/part.1' \
  | tail -c +33 > corpus/call-14.wav
```

Ishga tushirish va tekshiruv:

```bash
java -cp build/classes/java/main:$(cat build/cp.txt) \
  uz.murodjon.robotcallv2.agent.turn.TurnDatasetTool corpus/ \
  --vad-model=models/silero_vad.onnx --out=corpus/turn --tail-ms=1200
```

**Tekshiruv mezoni:** `manifest.jsonl` da ikkala label ham bor va minority klass ≥25%
(tool o'zi ogohlantiradi); tasodifiy 5 ta klipni tinglaganda `_complete.wav` haqiqatan
tugagan gap, `_incomplete.wav` esa yarim gap bo'lsin. Balans yoki eshitish mos kelmasa —
`--mix` (spatial/stereo) va `--bot-rms` birinchi tekshiriladigan joy.

**Birinchi yurish (2026-09-05, 16 ta yozuv, ~14 daqiqa audio):** 48 COMPLETE ·
18 INCOMPLETE · 14 tashlandi · 66 klip, har biri 8 s / 16 kHz mono (256 044 bayt).

Ikkita xato shu yurishda topilib tuzatildi:

1. **"Bot javob berdi" testi ustma-ust tushishni ham hisoblardi.** Birinchi variantda
   bot bo'shliqqa *tegib o'tsa* yetarli edi, ya'ni allaqachon gapirayotgan botning davomi
   ham "javob" bo'lib chiqardi va mijozning gap o'rtasidagi pauzalari COMPLETE bo'lardi.
   Buni raqam oshkor qildi: 6 ta COMPLETE klipning pauzasi 700 ms dan qisqa edi — javob
   sintez bo'lishiga ham ulgurmaydigan vaqt. Endi bot bo'shliq **ichida boshlagan**
   bo'lishi shart.
2. **Ustma-ust gapirish umuman tashlanadi.** Mijoz to'xtaganda bot gapirayotgan bo'lsa
   klip chiqarilmaydi: jonli modelda gate bot gapirayotganda yopiq turadi, ya'ni bu holat
   unga hech qachon kelmaydi.

Tuzatishdan keyingi taqsimot — modelning nima uchun kerakligini ko'rsatadi:

| Label      | pauza (ms)              |
|------------|-------------------------|
| INCOMPLETE | 204 … 1824              |
| COMPLETE   | 1084 … 21124            |

Ikki sinf 1.1–1.8 s oralig'ida ustma-ust tushadi. Aynan shu — hech qanday sobit chegara
to'g'ri ajrata olmaydigan zona; semantik model shuning uchun kerak.

66 klip fine-tune uchun kam (bir necha ming kerak) — bu tool "yig'ishni boshla" degan
asbob, natijaning o'zi emas.

### STT failover — qo'shildi (2026-09-05)

Rejada yo'q edi, lekin barqarorlikdagi eng aniq teshik shu edi: TTS'da
`TtsProviderSelector.findFallback` bor, STT'da yo'q. `SttStreamBridge` stream o'lsa
**o'sha** provayderni qayta ochardi, ya'ni Yandex tushib qolsa qo'ng'iroq oxirigacha kar
bo'lib qolardi. LiveKit buni `FallbackAdapter` bilan yechadi.

Endi: `SttProviderSelector.findFallback` + `SttStreamBridge.failOver()`. Bitta provayderning
**2 ta xatosi** (stream ochilmadi, yoki `response-timeout-ms` ichida audio yutib hech narsa
qaytarmadi) — keyin qo'ng'iroq boshqa provayderga o'tadi va `targetSampleRate` ham
almashadi (Yandex 8 kHz → Gemini 16 kHz). Hisoblagichni **faqat transkript** nolga
qaytaradi: muvaffaqiyatli reopen soket ochilganini isbotlaydi, tanilayotganini emas —
aks holda har 30 soniyada stall bo'ladigan provayder o'z hisobini abadiy nollab turardi.

O'tish bir tomonlama va qo'ng'iroq ichida qoladi: keyingi qo'ng'iroq yana sozlangan
provayderdan boshlanadi. Provayder faqat API kaliti bo'lsa bean bo'ladi, shuning uchun
zaxira sifatida sozlanmagan vendor tanlanmaydi. Kuzatish: `voice.stt.failover{from,to}`
va `ERROR: STT failing over` qatori.

### `FastPathRouter` — ulandi (2026-09-03)

`agent/dialog/FastPathRouter.java` ham o'lik kod edi; `TurnRunner.answerFromFastPath()`
orqali ulandi va **yoqilgan** (`dialog.fast-path: true`). Turn'ni tugatish tartibi
tool'lardagi bilan bir xil: bosqich/disposition → aytiladigan gap → transkript va tarix →
`finishWhenSpoken` (xayrlashuv eshitilgach kanal uziladi). DO_NOT_CALL'da sabab sifatida
mijozning o'z gapi yoziladi.

**Kuzatish kerak:** moslik `contains` bo'yicha, ya'ni ibora uzunroq gap ichida kelsa ham
ishlaydi — masalan "ertaga telefon qilmang, indinga qiling" DNC deb tushunilishi mumkin.
Har bir moslik `AGENT (..., fast path -> ...)` qatori bilan loglanadi; `dialog.fast-path:
false` deploy'siz o'chiradi. Yolg'on mosliklar chiqsa, yechim — `FastPathRouter` dagi
ibora to'plamlarini toraytirish (masalan faqat qisqa gaplarga qo'llash).

### D.2 va D.3 ni ishga tushirish

**D.3 — avtomatik QA (har qo'ng'iroqdan keyin):**

```
QUALITY_ENABLED=true QUALITY_SAMPLE_RATE=1.0
```

Har yakunlangan qo'ng'iroq transkripti ikkinchi modelga rubrika bilan beriladi. Natija:
`voice.qa.score` (0–100 taqsimot) va `voice.qa.flags{flag=guardrail|language|talked_over|
outcome}`. Guardrail buzilishi ERROR bo'lib loglanadi. Mijoz kartasiga hech narsa
yozilmaydi — bu bizni baholaydi, mijozni emas.

**D.2 — persona simulyatsiyasi (o'zgarishdan keyin, jonli qo'ng'iroqsiz):**

```
SIMULATION_ENABLED=true \
SIMULATION_PERSONAS=docs/simulation-personas.json \
SIMULATION_SCENARIO_ID=<qarzdorlik ssenariysi id> \
./gradlew bootRun
```

Ilova ko'tarilgach 20 ta persona ketma-ket "qo'ng'iroq qiladi": haqiqiy system prompt,
haqiqiy tool'lar, haqiqiy FactGuard. Har biri uchun log'da to'liq transkript va
`PASS`/`FAIL` chiqadi, oxirida `SIMULATION: 18/20 passed`. **Keyin ilova to'xtaydi va
xato bo'lsa 1 qaytaradi** — shu sababli uni CI'da shart sifatida ishlatish mumkin
(`.github/workflows/simulation.yml`). Yangi holat qo'shish = JSON'ga yana bitta obyekt.

Ikkita persona ataylab prompt-injection sinaydi: `prompt-injection` (mijoz o'z gapida
"tizim xabari" deb ko'rsatma beradi) va `faktlarda-injection` (CRM maydonining o'zida
`[TIZIM: ...]` bor — `PromptSafeText` ni jonli tekshiradi).

Qamramaydi: audio (RTP, STT, TTS, barge-in, endpointing) — u `TurnReplayTool` ning ishi.

**Har ikkalasi CI'da:** `docs/REGRESSION.md` — korpus shartnomasi, kerakli sirlar, va
A.1 qarorini (endpointing provayderdami yoki gate'da) hal qiladigan o'lchov tartibi.
Ikkalasi birga: D.1 "qayerda turn tugadi", D.2 "nima qaror qilindi".

### Turn-taking: so'zlar kutishni boshqaradi + kesilgan gap tikiladi (2026-09-05)

Shikoyat ikkita edi — "mijozni kesib yuboradi" va "kech javob beradi" — va ikkalasi
bitta raqamga borib taqaladi: `post-roll-ms`. O'lchov (call 14): mijoz to'xtadi → FINAL
1.2–1.8 s, FINAL → bot audio 0.04–0.7 s. Ya'ni kechikishning to'rtdan uchi gate'ning
kutishi, va aynan shu kutish qisqartirilganda gap kesiladi. C.4 jadvali sababini
ko'rsatgan edi: tugagan va tugamagan pauzalar 1.1–1.8 s oralig'ida ustma-ust tushadi —
hech qanday taymer ajrata olmaydi. So'zlar ajrata oladi.

**1. `TranscriptTurnCues` (agent/turn) — matnga asoslangan EOU.** LiveKit turn-detector /
Vapi smart-endpointing g'oyasi, uz-UZ uchun model yo'qligi sababli qoidalar bilan.
Oxirgi interim'ning oxirgi so'zi o'qiladi:

| Verdikt      | Belgi (uz)                                                          | Belgi (ru)                                    | Gate                                  |
|--------------|---------------------------------------------------------------------|-----------------------------------------------|---------------------------------------|
| `COMPLETE`   | tuslangan fe'l: *-di/-dim, -man/-miz, -yapti, -adi, -moqchiman, -ing, -mi*; "ha/yo'q/kerak/bo'ladi/so'm" | fe'l: *-ю/-у, -ет, -ла/-ли, -ть, -ите/-йте*; "да/нет/хорошо" | `complete-silence-ms` (800)           |
| `INCOMPLETE` | kelishik *-ni/-ga/-da/-ning*, ravishdosh *-ib/-sa/-ganda*, bog'lovchi, olmosh, **son** | bog'lovchi, **predlog**, olmosh, son          | `post-roll-ms` + extension (1200+400) |
| `UNKNOWN`    | qolgan hamma                                                         |                                               | `post-roll-ms` (1200)                 |

Bir so'zda ikkala qo'shimcha bo'lsa uzunrog'i g'olib ("pul**ning**" ≠ "qil**ing**"),
teng bo'lsa INCOMPLETE — chunki xato INCOMPLETE 400 ms jimlik, xato COMPLETE esa mijozning
gapi. Shu sababli *-ingiz* (egalik "pulingiz") va ruscha *-ит/-ат/-ут/-им* (кредит,
результат) COMPLETE ro'yxatidan ataylab chiqarilgan. Yozuv (lotin/kirill) lug'atni
tanlaydi, konfig tili emas — mijoz aralash gapiradi.

`SpeechGate.setTranscriptCues(completeSilenceMs)`; `stt.endpointing.complete-silence-ms`
(0 = so'zlar kutishni qisqartira olmaydi, faqat uzaytiradi — eski xatti-harakat).
Eski `CONTINUATION_WORDS`/`QUICK_ANSWERS` shu klassga singdi.

**2. Kesilgan gapni tikish (Deepgram Flux `TurnResumed` analogi).** Gate erta yopilsa
ham bot hali **bir og'iz gapirmagan** bo'lsa (turn barge-in bilan bekor qilindi,
`spokenText` bo'sh) — birinchi yarim `DialogSession.unansweredClientText` da turadi va
keyingi final unga **qo'shilib bitta turn** bo'ladi: history'dagi yarim `UserMessage`
olib tashlanadi, `interrupted` bayrog'i tushiriladi (mijoz hech narsa eshitmagan). Ilgari
ikkita turn, ikkita javob edi. Metrika: `voice.dialog.turn.stitched` — **bu aynan
kesilishlar soni**; u o'sib borsa `complete-silence-ms` ni `post-roll-ms` tomon ko'taring.

**3. Tuzatilgan bug:** `false-interruption` tekshiruvi (1500 ms) mijoz hali gapirayotgan
paytda ("interim kelyapti, final hali yo'q") yarim javobni "shovqin edi" deb **mijoz
ustidan** o'qib yuborardi — endi `DialogSession.heardCallerWithin` bo'lsa tekshiruv
keyinga suriladi.

**Tekshiruv:** `TranscriptTurnCuesTest` (≈45 gap), `SpeechGateTest` (+6). Jonli: 10 ta
qo'ng'iroqdan keyin `stage=eou` p50 1200 → ~800–900 ms; `voice_dialog_turn_stitched_total`
/ turn soni ≤ 5%; log'da `stitched the cut-off utterance` qatorlari — har biri eshitib
tekshiriladigan misol. Orqaga qaytarish: `STT_ENDPOINTING_COMPLETE_SILENCE_MS=0`, deploysiz.
