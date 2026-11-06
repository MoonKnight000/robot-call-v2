# Agent model sozlamalari → har bir provayderga to'g'ri uzatish — reja (2026-11-06)

Manba: foydalanuvchi — "AI agentda model sozlamalari (thinking, token limit…) umumiy sozlansin, keyin har bir
AI provayderga to'g'ri parse qilinib ishlasin; ChatGPT modellarini ham sinab ko'raylik."

## Diagnoz

**WEB-TEST qo'ng'iroq 2026-11-06 09:28 (attempt 77, `gpt-5.6-luna`):** ChatGPT bitta ham javob bermagan.
Har bir so'rov `404: Unknown`; eshitilgan gaplarni Gemini flash-lite (spekulyatsiya + spoken-line retry) aytgan.
"ChatGPT promptlari yomon" taassuroti — aslida OpenAI umuman ishlamagani.

| # | Muammo | Dalil |
|---|---|---|
| 1 | `spring.ai.openai.base-url` = `https://api.openai.com` (`/v1` siz) | curl: `/chat/completions` → 404, `/v1/chat/completions` → 401 (kalit) |
| 2 | `.env` dagi `OPENAI_API_KEY` yaroqsiz | `GET /v1/models` → 401 "Incorrect API key" |
| 3 | Fikrlash darajasi faqat global va faqat Gemini uchun | `dialog.yml` `thinking-level: LOW` → `TurnTools` → `GoogleGenAiChatOptions.thinkingLevel`. Agentda yo'q. 3.8/3.7-flash `MINIMAL`ni rad etadi (400) |
| 4 | OpenAI'ga reasoning effort berilmaydi | `TurnTools.buildOptions` OpenAI shoxi: faqat `maxTokens`, `temperature`, `frequency/presence penalty`. gpt-5.x standart "medium" fikrlaydi → sekin; reasoning modellari temperature/penalty'ni qabul qilmasligi mumkin |
| 5 | Claude modellari Gemini opsiyalarini oladi | `TurnTools.buildOptions` da faqat OpenAI va Google shoxi bor; `claude-*` → `GoogleGenAiChatOptions` |
| 6 | Tezkor yo'llar Gemini'ga bog'langan | `fastLlmModel` (flash-lite), backup race, spoken-line retry, LLM turn detector — boshqa provayderli agentda ham Gemini'ga boradi |

## Bosqichlar (har biri "keyingi bosqich" bilan)

0. **OpenAI ishlasin.** `base-url` standarti `https://api.openai.com/v1` (`dialog.yml`, `open-ai-base-url` ham).
   Yaroqli kalit — foydalanuvchi qo'yadi. Tekshiruv: curl `/v1/chat/completions` 200; WEB-TEST'da `llm request: model=gpt-…`
   dan keyin 404 yo'q.
1. **Agentda umumiy model sozlamalari.** `AiAgent` ga `reasoningEffort` (NONE / MINIMAL / LOW / MEDIUM / HIGH; null =
   platforma standarti) — `temperature`, `maxOutputTokens` yonida. API + `docs/api/ai-agents.md` + "Frontend'ga ta'siri".
2. **Provayder mapperlari (bitta joyda).** `agent/provider` da har vendor uchun generic sozlama → o'z opsiyasi:
   Gemini (`thinkingLevel`; qo'llamaydigan daraja eng yaqiniga, masalan 3.8 da MINIMAL→LOW), OpenAI (`reasoning_effort`;
   reasoning modelida temperature/penalty yuborilmaydi), Anthropic (`AnthropicChatOptions` + thinking budget). Model
   imkoniyatlari (`supportsTemperature`, ruxsat etilgan fikrlash darajalari) `ai_model` katalogida. Tekshiruv: har vendor
   uchun mock unit test — kutilgan opsiyalar.
3. **Tezkor yo'llar provayderdan mustaqil.** fast model / backup / retry agentning o'z vendoridan yoki aniq tanlangan
   modeldan; Gemini kaliti yo'q deploy'da ham ishlasin.
4. **Model matritsasi eval'i.** Bir xil 5 test holati: gemini-3.8-flash, gemini-3.6-flash, gpt-5.6-luna (LOW),
   claude-haiku/sonnet — pass rate + TTFT + token narxi jadvali, standart modelni tanlash.

## Eval natijalari (kontekst)
- #15 gemini-3.8-flash: 4/5, TTFT 1.7–3.5 s.
- #16 gemini-3.5-flash: 3/5, TTFT ~1.1 s, lekin 9 ta matnsiz tool navbati (sukut, sikl).

## 0-bosqich — kodlangan (2026-11-06, tekshirilmagan)
- `dialog.yml`: `spring.ai.openai.base-url` va `voice-agent.llm.open-ai-base-url` standarti → `https://api.openai.com/v1`
  (SDK manzilga faqat `chat/completions` qo'shadi). `.env.example` izohi ham.
- Qolgani foydalanuvchida: `.env` `OPENAI_API_KEY` ga yaroqli kalit (hozirgisi 401). `.env` da `OPENAI_BASE_URL` berilsa — `/v1` bilan.
- Tekshiruv: curl `/v1/models` 200 → backend restart → agent 3 ni vaqtincha `gpt-5.6-luna` ga qo'yib bitta VOICE simulyatsiya:
  logda `llm request: model=gpt-5.6-luna` dan keyin 404/401 yo'q va `turn latency` chiqadi.

## 2-bosqichdan bir qism — OpenAI so'rovi to'g'rilandi (2026-11-06, kodlangan, tekshirilmagan)
Kalit to'ldirilgach curl o'lchovi (5.5k tokenli prompt, streaming TTFT, 3 tadan):
| Model | reasoning_effort | TTFT |
|---|---|---|
| gpt-6-luna | (yuborilmasa = medium) | 4.5–6.4 s |
| gpt-6-luna | low | 1.0–1.4 s |
| gpt-6-luna | none | 1.4–1.8 s |
| gpt-5.6-luna | (yuborilmasa) | 2.6–3.5 s |
| gpt-5.6-luna | low / none | 1.05–1.2 s |
`minimal` — 400. gpt-6-luna `temperature≠1`, `frequency/presence_penalty`, `max_tokens` ga 400 qaytaradi → bizning kod bilan har navbat yiqilardi.

Tuzatish: `TurnTools.buildOptions` — reasoning modeli (gpt-5*, gpt-6*, o*) uchun faqat `max_completion_tokens` + `reasoning_effort`
(`spring.ai.openai.chat.reasoning-effort` = `${OPENAI_REASONING_EFFORT:low}`); temperature/penalty yuborilmaydi. `dialog.yml` dan
`spring.ai.openai.chat.max-tokens` olib tashlandi (ChatClient standartlarni har so'rovga qo'shadi), `ChatModelRegistry` → `maxCompletionTokens`.
`gpt-6-luna` katalogga (BALANCED). Qoldi: summary/judge/copilot/insight/pack/scorecard adapterlari OpenAI modelida hali `maxTokens`/temperature yuboradi.

## 1–2-bosqichlar — kodlangan (2026-11-06, tekshirilmagan)

**Yangi topilma (asosiy bug):** `RoutingChatModel.getOptions()` Gemini options turini qaytarardi, ChatClient esa
so'rovni shunga `combineWith` qiladi — vendor maydonlari faqat bir xil turda o'tadi. Natijada TurnTools qanday
qursa ham OpenAI'ning `reasoning_effort` va `max_completion_tokens` i yo'qolib, gpt-6 ga yana `max_tokens` borardi.
Shuning uchun vendor shakli endi vendor aniq bo'lgan joyda — routing ichida yasaladi.

Arxitektura (Strategy + Template Method):
- `shared/dialog/ReasoningEffort` — `NONE|MINIMAL|LOW|MEDIUM|HIGH`, agentning umumiy sozlamasi
  (`AiAgent.reasoningEffort`, `ai_agent.reasoning_effort` — V45, `clearFields: REASONING_EFFORT`).
- `EffectiveAiModelConfig.reasoningEffort` + `withReasoningEffort(...)` — DialogEngine, AgentSimulator,
  StudioTestChat, ChatConversations.
- `agent/provider/PortableChatOptions` (+Builder) — vendor-neytral options; ChatClient birlashtirishidan butun o'tadi.
  Vendor builder'lari (summary/judge'lar hali Gemini/OpenAI options quradi) `combineWith` da thinking darajasi
  sifatida saqlanadi.
- `VendorChatOptionsStrategy` → `AbstractVendorChatOptionsStrategy` (umumiy qadamlar) →
  `GeminiChatOptionsStrategy`, `OpenAiChatOptionsStrategy`, `AnthropicChatOptionsStrategy`;
  `VendorChatOptionsStrategies` — `ChatVendor` bo'yicha tanlaydi. `RoutingChatModel` portable so'rovni shu orqali o'giradi.
- `TurnTools.buildOptions` endi `PortableChatOptions` qaytaradi (own-LLM yo'li o'zgarmagan, OpenAI options).
  Gemini `toolChoice` → umumiy `allowedToolNames` (Gemini strategiyasi VALIDATED/NONE ga o'giradi).

Moslash qoidalari:
| | Gemini 3 | Gemini 2.5 | OpenAI gpt-5/6/o | OpenAI boshqa | Claude |
|---|---|---|---|---|---|
| Token cheki | maxOutputTokens | maxOutputTokens | max_completion_tokens | max_tokens | max_tokens (+budget) |
| Temperature | yo'q | bor | yo'q | bor | ≤1, thinking'da yo'q |
| Penalty | yo'q | yo'q | yo'q | bor | yo'q |
| Fikrlash | thinkingLevel, eng yaqin qo'llanadigan | yuborilmaydi | reasoning_effort (NONE/MINIMAL → gpt-5.x `none`, boshqasi `low`) | yuborilmaydi | MEDIUM 1024 / HIGH 4096 budget |

Gemini: 3.7/3.8 Flash `MINIMAL` → `LOW`; 3.5/3.6 Flash va Lite `MINIMAL` oladi; Pro faqat `LOW`/`HIGH`.
Agent darajasi `null` → YAML defaulti (`LLM_REASONING_EFFORT`, `OPENAI_REASONING_EFFORT`), u ham shu qoidadan o'tadi.

Testlar (mock, Spring'siz): `GeminiChatOptionsStrategyTest`, `OpenAiChatOptionsStrategyTest`,
`AnthropicChatOptionsStrategyTest`, `RoutingChatModelTest` (haqiqiy ChatClient orqali), `TurnToolsTest`,
`TurnToolsToolChoiceTest` moslandi. Gemini qismini eval bilan tekshirish: agent 3 ga `reasoningEffort` qo'yib eval.

### Eval #18 (2026-11-06, gemini-3.6-flash, agent `reasoningEffort=null` → YAML `minimal`) — 3/5
- Provayder yo'li ishladi: 400/INVALID_ARGUMENT yo'q; 3.6 Flash MINIMAL ni oldi — 13 navbatdan 12 tasida `thinking 0`,
  bittasida 308 thinking token (backup race 2500 ms da boshlandi).
- `llm_ttft` median ≈ 1.27 s (0.96–1.58), `first_audio` median 1.33 s — #17 da ~2.2 s edi.
- "asking again for the spoken line" — 1 marta (#17 da 7): tool-only tuzatish ishlayapti.
- Yiqilganlar provayderga bog'liq emas:
  - "Va'da beradigan mijoz": simulyatsiyadagi mijoz 3 gapni ketma-ket aytdi, har biri agent javobini barge-in bilan
    kesdi; `recordPaymentPromise` (13-noyabr) yozildi, lekin tasdiq gapi transkriptga tushmadi.
  - "Qo'ng'iroq qilmang": agent "uzoqroq bezovta qilmaymiz" dedi (g'alati so'z, uzr yo'q) — judge to'g'ri, prompt/model sifati.
- Prompt cache: `cached 0%` barcha navbatlarda — alohida masala.

### Eval #19 (2026-11-06, gpt-6-luna) — 2/5, YAROQSIZ
- Deyarli har navbat 400: "Function tools with reasoning_effort are not supported for gpt-6-luna in
  /v1/chat/completions. To use function tools, use /v1/responses or set reasoning_effort to 'none'".
  Agent fallback gaplarni aytdi, judge'lar shunga baho berdi — model sifatini o'lchamaydi.
- curl: tools + `none` → 200 (≈3.1 s to'liq javob, stream emas), tools + `low` → 400.
- Tuzatish: `OpenAiChatOptionsStrategy` — gpt-6 + tools → `reasoning_effort=none` (agent darajasidan qat'i nazar;
  tools'siz so'rovlarda daraja o'z holicha). Unit test qo'shildi. gpt-6 ni fikrlash bilan tools'da ishlatish uchun
  Responses API kerak — Spring AI chat modeli uni ishlatmaydi (keyingi ish, agar kerak bo'lsa).
- Agent 3 qayta `gemini-3.6-flash` ga qaytarildi. #19 ni backend qayta ishga tushgach takrorlash kerak.
- Solishtirish uchun alohida agent: **13 "Qarzdorlik (cascade, GPT-6)"** — agent 3 ning nusxasi (ssenariy 14, ovoz,
  sozlamalar bir xil), faqat `llmModel=gpt-6-luna`; 5 ta eval test holati ko'chirildi (id 6–10). Endi agent 3 (Gemini)
  va 13 (GPT-6) model almashtirmasdan parallel eval qilinadi: `POST /api/ai-agents/13/evaluate?mode=VOICE&repeat=1`.

### Eval #20 (agent 13, gpt-6-luna) va #21 (agent 3, gemini-3.6-flash) — 2026-11-06, restartdan keyin, ketma-ket
| | #20 GPT-6 | #21 Gemini 3.6 |
|---|---|---|
| O'tdi | 3/5 | 3/5 |
| 400 / turn failed | 0 / 0 | 0 / 0 |
| Model TTFT (median, spekulyatsiyasiz) | ≈1.04 s (0.69–1.42) | ≈1.41 s (1.04–1.71) |
| first_audio (median, oldindan yozilmagan navbatlar) | ≈1.77 s (0.92–2.57) | ≈1.89 s (1.52–2.42) |
| Javob mijoz gapi tugashidan oldin tayyor ("ahead of the final") | 10 | 2 |
| Spoken-line qayta so'rov / fallback gap | 5 / 2 | 5 / 1 |
| Thinking tokenlar | 0 (`none`) | 0 (`minimal`) |
- Ikkalasida umumiy yiqilish — "Va'da beradigan mijoz": "juma kuni" deydi, aniq sanani (13-noyabr) aytmaydi → ssenariy/prompt.
- GPT-6 xatosi: rus tilidagi suhbat oxirida "Хайр, саломат бўлинг!" (o'zbekcha kirillda) — xayrlashuv yo'lini tekshirish kerak.
- Gemini xatosi: DNC'da uzr yo'q, "uzoqroq bezovta qilmaymiz" (#18 da ham) — g'alati so'z, prompt/model.
- Xulosa: sifat teng (3/5), GPT-6 birinchi tokenda ~0.35 s tezroq; fikrlash ikkalasida o'chiq. Bitta takrorda
  farq shovqin ichida — aniqroq xulosa uchun `repeat=3`.

### #20/#21 dagi umumiy xatolar tuzatildi (2026-11-06, kodlangan, tekshirilmagan)
- **Rus qo'ng'irog'ida "Хайр, саломат бўлинг!"**: GPT-6 bo'sh javob berdi → spoken-line retry (tez model, gemini-lite)
  o'zbekcha kirillda javob qaytardi; `SpeechSanitizer.isOtherScript` faqat yozuvni solishtirgani uchun o'tib ketdi.
  Endi: javobda ў/қ/ғ/ҳ bo'lsa va mijoz gapida bo'lmasa — boshqa til (SpeechOutput rad etadi, LlmTurnClient qayta so'raydi).
  Retry ko'rsatmasi ham "mijoz ishlatgan til va alifboda" deb so'raydi. Test: `uzbekInCyrillicIsAnotherLanguageToARussianCaller`.
- **Va'da sanasiz tasdiqlanadi ("juma kuniga yozib qo'ydim")**: seed'dagi uchala qarz ssenariysida `recordPaymentPromise`
  tavsifi — gap kunni sanasi bilan aytadi ("juma, 13-noyabr"); `debt-collection-crm` CONFIRMATION bosqichi ham shuni talab qiladi.
  (R__seed_data — repeatable, restartda built-in ssenariylar yangilanadi.)
- **DNC'da uzr yo'q, "uzoqroq bezovta qilmaymiz"**: agent gapi `recordDoNotCall` bilan birga ketadi va qo'ng'iroq shu
  bilan yopiladi, tool natijasidagi "uzr so'ra" kech qolardi. Ko'rsatma va namuna tool tavsifiga ko'chirildi.

### Eval #22 (agent 13, GPT-6) va #23 (agent 3, Gemini 3.6) — tuzatishlardan keyin, 2026-11-06
| | #22 GPT-6 | #23 Gemini 3.6 |
|---|---|---|
| O'tdi | **4/5** (#20 da 3/5) | **4/5** (#21 da 3/5) |
| Model TTFT median | ≈0.80 s | ≈1.08 s |
| first_audio median (model navbatlari) | ≈0.94 s | ≈1.56 s |
| Spoken-line qayta so'rov / boshqa til rad etildi | 1 / 1 | 5 / 0 |
- DNC: ikkalasi ham uzr bilan ("Bezovta qilganimiz uchun uzr…") — tuzatish ishladi.
- Rus qo'ng'irog'ida o'zbekcha kirill gap yo'q (1 marta rad etilib qayta so'raldi).
- GPT-6 va'dani sana bilan aytdi: "Bo'ldi, juma, 13-noyabrga yozib qo'ydim". Gemini esa "jumaga yozib qo'ydim" (sanasiz)
  va `promisedDate=2026-11-06` — bugun (juma) deb oldi; gap barge-in bilan kesildi → yiqildi. "Juma" juma kuni aytilsa noaniq.
- GPT-6 yiqilishi: rus holatida tasdiqni ketma-ket ikki marta aytdi.
- Ochiq: Gemini tool tavsifidagi "sana bilan ayt" qoidasiga amal qilmadi; "bugungi hafta kuni" noaniqligi uchun
  guardrail kerak (mijoz bugungi hafta kunini aytsa — qaysi biri ekanini so'rash yoki kelasi haftani olish).
- **Bugungi hafta kuni qoidasi qo'shildi (kodlangan, tekshirilmagan):** `SystemPromptFactory.weekdayAnchors` — promptdagi
  DATES ro'yxatiga yaqin 7 hafta kuni sanasi bilan ("dushanba" = 2026-11-09 …) va qoida: mijoz BUGUNGI hafta kunini aytsa —
  bitta qisqa savol ("Bugunmi yoki kelasi juma, 13-sanami?"), o'zingdan tanlama; kunni tasdiqlaganda sanasini ham ayt.
  Barcha ssenariylarga ta'sir qiladi (umumiy prompt). Test: `todaysWeekdayIsAskedAboutAndTheOthersHaveTheirDates`.

## Insondek gapirish + qarzdorga qarab muomala + tezlik (2026-11-06)
**So'rov:** HUMAN_LIKE agent real odamdek gapirsin; qarzdorga qarab muomala: to'lamasa peniya, oldin va'da berib
to'lamagan bo'lsa — shartnoma bekor qilinishi mumkinligini aytish; javob ~1 s.

Kodlangan:
- `debt-collection-crm` factSchema: `penaltyAmount`, `contractCancelDays`, `brokenPromiseDate` (CSV/CRM/test-case
  allaqachon beradi — `CallContextFactory`, `TargetCsvImporter`). FactGuard peniya summasini shu faktdan tekshiradi.
- Buzilgan va'da avtomatik: `SystemPromptFactory.findBrokenPromise` — mijoz xotirasidagi `promisedDate` bugundan oldin
  bo'lsa MULTI-CALL MEMORY'ga "BROKEN PROMISE" qatori. Test: `anEarlierPromiseThatFellDueIsBroken`.
- Ssenariy: birinchi marta kechikkan — yumshoq; va'dasini buzgan — DEBT_NOTICE da va'da + peniya, PAYMENT_DATE da
  birinchi so'rashdayoq "shartnoma bekor qilinishi mumkin", CONFIRMATION da "bu safar kechiktirmang". Eski guardrail
  ("jarima/huquqiy oqibat haqida gapirma") o'rniga OQIBATLAR qoidasi: faqat faktdan, har biri bir marta, sud/ijro yo'q.
  `debt-promise-follow-up` NEW_DATE ga ham shartnoma ogohlantirishi. "bekor qilinadi" emas, "bekor qilinishi mumkin".
- HUMAN_LIKE persona: `SystemPromptFactory.HUMAN_SPEECH` (cascade + realtime) — namuna gaplarni so'zma-so'z
  takrorlamaslik, aytilganga qisqa jonli reaksiya, mijoz holatiga moslashish, yon savolga javob berib qaytish.
- CONFIRMATION: sana allaqachon aytilgan bo'lsa — faqat xayrlashish (ikki marta tasdiq bug'i).
- FastPathRouter: STT `ʻ` (U+02BB) ni `'` ga aylantiradi; "qo'ng'iroq qilmang(lar)" DNC iborasi. #24 da DNC tez yo'lda
  tanilmagan, agentning uzr gapi mijozning davomi bilan kesilib, mijoz hech narsa eshitmagan.
- Agent 3 (Gemini): `reasoningEffort=MINIMAL`. Frontend: LLM bo'limida "Fikrlash darajasi" tanlovi.

Eval #24 (agent 13, GPT-6) 1/6 va #25 (agent 3, Gemini, MINIMAL) 5/6 — yuqoridagi so'nggi uch tuzatishdan OLDIN:
- Ikkalasida ham "Va'dasini buzgan mijoz" (yangi test-case 11/12) yiqildi: va'da va shartnoma aytildi, peniya aytilmadi.
- GPT-6: FUNCTION bosqichidan keyin bo'sh javob (5 marta fallback qator), ikki-uch marta tasdiq, DNC (U+02BB).
- Kechikish: first_audio median #24 1.41 s, #25 1.60 s; LLM TTFT median 1.18 / 1.27 s. MINIMAL Gemini'ni tezlatmadi.
  Spekulyatsiya: #24 13 hit / 7 miss, #25 0 hit / 16 miss — guess har doim 1–3 so'z orqada (Yandex final interim'dan
  ko'p so'z bilan keladi). ~1 s uchun asosiy richag — spekulyatsiyani shu farqqa chidamli qilish (alohida reja kerak).
- Oxirgi tuzatishlardan keyin restart qilindi (12:29), eval qilinmadi — brauzer sessiyasi tugagan.

### Eval #26–#29 (2026-11-06, after the debtor-treatment fixes)

| Run | Agent | Passed | Notes |
|---|---|---|---|
| #26 | 13 (GPT-6) | 2/6 | DNC now passes (U+02BB fix). Date confirmed twice (ack line + CONFIRMATION). |
| #27 | 3 (Gemini) | 4/6 | Broken-promise case: penalty and cancellation said naturally, but a vague "dekabr boshida" was recorded as a refusal, and the call was then auto-closed after the client gave 20-noyabr. |
| #28 | 13 (GPT-6) | 3/6 | Same double confirmation. Broken-promise case errored: Gemini spend cap (the persona simulator runs on Gemini). |
| #29 | 3 (Gemini) | 3/6 | Invalid: Gemini monthly spend cap hit mid-run. |

Fixes after #27:
- `TurnRunner`: the "outcome recorded and the model did not close" auto-close no longer applies to REFUSED.
- `DialogSession.setDisposition`: a changed outcome restarts the turn count.
- Scenario: the PAYMENT_DATE refusal condition now treats a vague answer as not a refusal.
- Test cases 11/12: the penalty may be mentioned in the first notice.

Fixes after #28:
- Scenario: when the date is given, the agent says only "Xo'p, aka"; CONFIRMATION states the date once, together with the goodbye.
- Not evaluated yet: the Gemini cap blocks the persona simulator.

Latency #28/#29: median first_audio 1.44 s (n=27).

### GPT-only mode + eval #30 (2026-11-06)
Gemini's cap was reached again, so every model default in `.env` now points to OpenAI:
- dialog, summary, copilot, quality judge, insight, pack generator and the eval persona use gpt-6-luna;
- the dialog fast model is gpt-5.6-luna;
- agent 13's fastLlmModel is now gpt-5.6-luna.

Eval #30 on agent 13 (all GPT): 0/6, first_audio p50 1.84 s. Causes:
1. gpt-5.6-luna returns 400 for tools combined with reasoning_effort, so every fast-model turn failed.
   Fixed: `OpenAiChatOptionsStrategy.refusesEffortWithTools` now also covers gpt-5.6.
2. The LLM turn detector sent gpt-5.6-luna to Gemini's endpoint. Against OpenAI it would also need
   `max_completion_tokens` and takes ~1.1 s. Disabled in GPT-only mode (`LLM_TURN_DETECTOR=false`);
   Smart Turn decides alone.
3. The "Xo'p, aka" acknowledgement lost the date whenever the client hung up after "xayr" or
   CREATE_TASK went wrong. Reverted: the date is said once in the acknowledgement line, and
   CONFIRMATION is only a goodbye.

Still open:
- GPT-6 sometimes skips DEBT_NOTICE when the client asks "siz yaxshimisiz?".
- In the Russian case the greeting was repeated.

### LLM turn detector usage is now billed (2026-11-06)
Until now `LlmTurnDetector` called the model over raw HTTP and never counted its tokens. On 2026-11-06 it made ~1030 requests at ~650 tokens each, about 15% of LLM tokens, and none of it showed in call cost or `/billing`.
- The OpenAI-shaped `usage` of each answered question now goes through `LlmUsageSink`, then `DialogEngine.recordSideLlmUsage`, then `DialogSession.addTokenBreakdown`. It is recorded under the model that was asked, so it lands in `llmUsageByModel` and billing.
- Questions cancelled by a newer interim have no body and are not counted.
- These tokens do not count against `maxTokensPerCall`: that budget bounds a dialog loop.
- Test: `LlmTurnDetectorUsageTest`.

### Turn detector vs speculation, cache, speculation matching (2026-11-06)
- **GPT-only mode:** the separate LLM turn detector stays off. The turn is judged by Smart Turn plus the speculative reply's own cue (`speculative-turn-cue`: text means finished, `waitForCaller` means unfinished).
- **OpenAI cached tokens were never read.**
  - `TokenUsage.cachedTokens` read only Gemini's usage type, so every GPT turn logged "cached 0" and billed its cached prefix at the full rate.
  - It now falls back to the generic `Usage.getCacheReadInputTokens()`, which Spring AI fills from OpenAI's `prompt_tokens_details.cached_tokens`. The value is capped at the prompt.
  - Probe: a repeated 7.2k-token prompt on gpt-6-luna gets `cached_tokens=7211` from the 2nd request. OpenAI's automatic cache works; only the accounting was missing.
  - Gemini's implicit cache could not be checked (spend cap).
- **Speculation matching:**
  - `TranscriptText.normalize` drops o‘/g‘ apostrophes (U+02BB is a letter to Java).
  - `isSpeculationCompatible` compares without spaces, because Yandex writes "koʻramiz" in the interim and "ko ramiz" in the final.
  - Finals that add content words ("…oylik berishadi" → "+ o'sha kuni to'liq to'lab yuboraman") still miss, on purpose: the guessed reply never saw those words.
- Tests: `TokenUsageTest`, `TurnRunnerSentenceTest.speculationSurvivesTheFinalSpellingTheSameWordsApart`.

### Post-call LLM jobs switchable from YAML (2026-11-06)
While call quality is being tuned, the jobs below only spend tokens:

| Job | Flag (env) | Now in `.env` | What turns off with it |
|---|---|---|---|
| Post-call summary (`SummaryService`) | `voice-agent.summary.enabled` (`SUMMARY_ENABLED`) | false | call summary/sentiment, the "summary found a promise" disposition fallback, client memory/CRM note written by `applySummary`, LLM outcome classifier, contact-field merge, chat analysis. The outbox retry finds `summarize()==null` and spends nothing. |
| QA score per call (`CallQualityJudge.judge/evaluate`, scorecards) | **new** `voice-agent.quality.call-scoring` (`QUALITY_CALL_SCORING`) | false | `voice.qa.*` metrics, eval `avgQaScore`, operator scorecards. |
| Eval success criteria (`CallQualityJudge.checkCriteria`) | `voice-agent.quality.enabled` (`QUALITY_ENABLED`) | true | Kept on: an eval still says pass/fail. |
| Nightly insights | `voice-agent.insight.enabled` (`INSIGHT_ENABLED`) | false | insight clustering job |

- Intent detection (`INTENT_DETECTION_ENABLED`) runs only for agents with an `INTENT_DETECTED` webhook, so it was left as is.
- Before production, turn summary back on: the next call's memory (broken promise) and the CRM note depend on it.

### Eval #31 (2026-11-06, all GPT, detector off, summary/QA off)
- **Result:** 2/6.
- **Speed (big improvement over #30):**
  - first_audio p50 **0.95 s**, p90 2.06 s (#30: 1.84 s);
  - speculation 20 hits / 2 misses;
  - prompt cache **64%** of prompt tokens (each turn's cached share 82–88%; it was never visible before the OpenAI fix).
- **Main bug:** an inline job leaves the call stuck in a silent FUNCTION stage.
  - REASON_INQUIRY did recordDelayReason inline and spoke the date question.
  - The flow then moved to RECORD_REASON. Its sole way on (PAYMENT_DATE) is not a job stage, so a step-2 model turn ran inside RECORD_REASON. The model re-called the tool and said nothing.
  - The call stayed in RECORD_REASON: the caller gave the date 5 times to silence.
  - Fix: `TurnRunner.scheduleStageAction`. When the inline reply already asked a question (last assistant line ends in "?") and the sole way on is a conversation stage, move there and wait for the caller. A reply that asked nothing (refusal → CLOSING) still gets the step-2 goodbye turn.
  - Helper: `StageEntry.findSoleWayOn`.
- **Do-not-call reply:** now also says "boshqa qo'ng'iroq qilmaymiz". The judge failed it for not saying so explicitly.
- **Still open:**
  - Russian call: mixed Uzbek greeting words; asked the reason after the date was given.
  - CONFIRMATION re-asks when the persona answers with noise ("ха ха").
  - The broken-promise case failed only on the judge's "repeated" reading of promise + contract lines.

### Eval #32 (2026-11-06, after the stuck-stage fix)
- **Result:** 2/6.
- **Speed:** first_audio p50 0.95 s, p90 2.7 s; speculation 15 hits / 3 misses; cache 64%.
- **Stuck-stage fix confirmed:** "its line was already spoken" fired 4 times; "no speakable text" 0 times (6 in #31).
- **Do-not-call** passes again.
- **Every other failure is the double confirmation.** The acknowledgement line confirms the date, then CONFIRMATION, written by the step-2 turn after CREATE_TASK, confirms it again.
  - Fix: step-2 notes now quote the line the caller just heard and say not to repeat any date, sum or confirmation in it (`TurnRunner.describeLineJustSpoken`).
- **Still open:**
  - Russian: re-asks the date the caller already gave.
  - "Juma" on a Friday is genuinely ambiguous, so asking "bugunmi yoki kelasi juma?" is reasonable.

### Web research, then phases 1–3 (2026-11-06)
Sources: Retell conversation-flow best practices and debug guide; Pipecat Flows (`tts_say`, `respond_immediately`, `context_strategy`); the OpenAI realtime prompting guide (variety rule, pin the language); Retell, Vapi and LiveKit forum threads on silence after tool calls.
1. **The engine speaks `staticLine` after a job** (`TurnRunner.speakNextStaticLine`, `StageEntry.findSoleWayOn` / `onlyEndsCall`, `DialogLines.speaksAgentLanguage`).
   - When a FUNCTION stage's one way on is a conversation stage with a `staticLine`, the engine says it with facts filled in; no model turn.
   - If that stage only ends the call, the call is closed.
   - If the call switched language, a closing stage gets the agent's farewell in the call's language.
   - Documented in `docs/api/scenarios.md`.
2. **debt-collection-crm rewritten Retell-style.**
   - Stage purposes say only what to say and which tool to call; "X ga o't" is gone (transitions live in `transitionConditions`).
   - New guardrail "SANA TASDIG'I": the date is said once, in the line that records it, with no goodbye in it ("Bu safar kechiktirmang" is added for promise-breakers).
   - CONFIRMATION and CLOSING have `staticLine`s and lead only to END_CALL.
   - ESCALATE_TO_HUMAN got a transition condition.
3. **Platform prompt (`SystemPromptFactory.speechRules`).**
   - "the agreed outcome is read back once at the end" became "confirmed ONCE, in the reply that agrees it".
   - New VARIETY rule.
   - The MULTILINGUAL block now says every word must be in the switched language, with no Uzbek words carried into Russian.
4. **Per-stage context reset: not done.** It would change the prompt prefix every stage and lose the 64% prompt cache. Retell/Pipecat RESET trades cache for focus; our numbers say keep APPEND.

### Eval #33 (2026-11-06, after the research-driven phases 1–3): **6/6**
- **Speed:** first_audio p50 1.00 s, p90 1.75 s (#32: 2.7 s); speculation 11/15; cache 64%; no "no speakable text".
- **Every case passed:**
  - the date is confirmed once;
  - the broken-promise flow says penalty → contract warning → date → "Bu safar kechiktirmang" → goodbye;
  - the Russian call stayed in Russian;
  - the do-not-call reply is explicit.
- **Engine `staticLine` path never fired.** The model wrote CONFIRMATION's line itself, word for word: the stage's `staticLine` reaches it through the annex "gapingiz AYNAN shu". This happened during CREATE_TASK's step-1 turn, so the engine path is only a backstop.
- **Left open:** in one call the model called the unavailable `endCall` in CREATE_TASK and no goodbye was said; the client had already hung up.
