# Funksiyalar qachon ishlaydi — kechikish rejasi

Holat (2026-11-05): **1–4-bosqich kodi yozildi (4 — faqat pre-call), tekshirilmagan.** `AFTER_CALL` qilinmadi.

## Muammo (o'lchangan)

Natijasi mijozga kerak bo'lmagan ishlar ham hozir suhbat ichida, mijoz kutib turgan paytda
bajariladi.

- **Call 4** (`1793812714.59`, 2026-11-04): mijoz sanani aytgandan keyin ketma-ket uchta LLM
  turni bo'ldi — `recordPaymentPromise` → `createFollowUpTask` (Uysot HTTP) → tasdiq. Mijoz
  shu payt jim kutdi va oxiri chidamay o'zi gapirdi. Jim turnlarda tool to'plami bosqichga qarab
  o'zgaradi, shuning uchun prompt keshi 0%.
- Model turn ichida yozuvchi tool chaqirsa (`addLeadNote` va h.k.), hozir HTTP tugashi kutiladi,
  keyin natija bilan **yana bitta LLM aylanmasi** qilinadi (`wantsResultRound`). Natija mijozga
  kerak emas.
- **Call 8** (`1793860995.8`): bot sanani eshitib, `RECORD_PROMISE` ga o'tib, xayrlashdi.
  `Farewells.saidByAgent` qo'ng'iroqni yopdi va va'da umuman yozilmadi.

## Qoida — funksiyaning `timing` i

Funksiya natijasini mijoz eshitsa yoki keyingi qadam uni o'qisa — **suhbat ichida**.
Aks holda — **fonda**. Bu endi kod ichidagi taxmin emas, agent funksiyasining sozlamasi:

`AgentFunction.timing` (`AgentFunctionTiming`), faqat `CUSTOM` va `INTEGRATION` turlari uchun:

| Qiymat | Ma'nosi | Misollar |
|---|---|---|
| `null` | Tool'ning o'z `http.waitForResult` i (avvalgidek) | — |
| `IN_TURN` | Turn kutadi; natija modelga qaytadi va shu turnda aytiladi | `SEARCH_FLATS`, `GET_BOOKING_STATUS`, `CREATE_LEAD` |
| `BACKGROUND` | Fonda ishga tushadi, turn kutmaydi; natija bilan LLM aylanmasi ham, kutish gapi ham yo'q | `CREATE_FOLLOW_UP_TASK`, `ADD_LEAD_NOTE`, `MOVE_LEAD_STAGE` |

Fon mexanizmi yangi emas: tool'ning `waitForResult=false` sozlamasi (`HttpToolExecutor`, virtual thread,
`formatStarted`) allaqachon bor edi. `timing` shu sozlamani **agent darajasida** almashtiradi.

- Qo'ng'iroqdan **oldin** ishlaydigan funksiyalar uchun alohida joy allaqachon bor —
  `pre_call_functions`. Unda argumentlar model emas, shablonlar orqali to'ldiriladi
  (`GET_DEBT_SUMMARY`, `GET_CLIENT_PROFILE`). Shu sababli `timing` da `PRE_CALL` yo'q:
  bir ishni bajaradigan ikki joy bo'lmasligi kerak.
- `AFTER_CALL` ham hozircha yo'q: integratsiya tool'ini auth bilan qo'ng'iroqdan keyin ishga
  tushirish uchun `PostCallActionExecutor` ga yangi yo'l kerak (4-bosqich).
- Tasdiq talab qiladigan tool (`requiresConfirmation`) `BACKGROUND` da ham avval tasdiqni turn ichida
  o'qib beradi: `HttpToolExecutor` da tasdiq tekshiruvi fon tekshiruvidan oldin turadi.
- Fondagi so'rovning xatosi modelga aytilmaydi; u `tool_execution` da qoladi (oldingi `waitForResult=false` bilan bir xil).
- Kaskad va realtime pipeline ikkalasida ishlaydi (realtime'da natija aylanmasi yo'q, faqat kutish yoki kutmaslik).

---

## 1-bosqich — `timing` maydoni va turn ichidagi `BACKGROUND` (kod yozildi)

- `aiagent`:
  - `AgentFunctionTiming` enum;
  - `AgentFunction.timing` + builder + `runsInBackground()` (`is…` emas, aks holda Jackson uni JSON maydoniga aylantiradi);
  - `AgentFunctionValidator.normalize` boshqa turlarda bu maydonni tashlab yuboradi.
- `tool`: `ToolOverrides.withWaitForResult(tool, boolean)`.
- `agent/dialog`:
  - `AgentFunctionPlan.applyTiming` / `findCustomFunction`. Uchta joyda qo'llanadi:
    `IntegrationHttpToolFactory` (integratsiya tool'lari), `TurnTools` va `RealtimeToolRunner` (CUSTOM tool'lar).
  - `TurnTools.run`: javob `formatStarted` bo'lsa, tool turn holatiga belgilanadi (`DialogSession.addBackgroundTool`).
  - `LlmTurnClient.wantsResultRound`: belgilangan tool natija aylanmasini chaqirmaydi.
    Ilgari `waitForResult=false` tool ham modelga "started" ni o'qitish uchun bitta aylanma sarflardi.
  - `maybeSpeakPreToolSpeech`: `BACKGROUND` funksiya uchun kutish gapi aytilmaydi.
- API: `docs/api/ai-agents.md`, `openapi/schemas/aiagent.yaml`, `docs/ai/AGENT-FUNCTIONS-CONTRACT.md`.

**Tekshirish (foydalanuvchi):**
- unit:
  - `AgentFunctionValidatorTest.keepsTheTimingOfAnHttpToolAndDropsItElsewhere`;
  - `ToolOverridesTest.waitForResultCanBeTurnedOffForOneUserAndTheRestStays`;
  - `AgentFunctionPlanTest` (yangi).
- sozlama: agent 3 da `createFollowUpTask` va `addLeadNote` funksiyalariga `"timing": "BACKGROUND"`.
- test qo'ng'iroq, model `addLeadNote` yoki `createFollowUpTask` ni chaqirganda logda:
  - shu turnda `tool result round` qatori **yo'q**;
  - `pre-tool speech before executing tool …` yo'q.
- `tool_execution` jadvalida so'rov yozuvi bor (fonda tugagan).

Bu bosqich FUNCTION bosqichining **jim LLM turnini** olib tashlamaydi (2-bosqich). Faqat HTTP kutishini
va natija aylanmasini olib tashlaydi.

## 2-bosqich — FUNCTION bosqichi `BACKGROUND` funksiyani kutmasin (kod yozildi, tekshirilmagan)

FUNCTION bosqichining tool'i `BACKGROUND` funksiya bo'lsa, bosqichning jim LLM turni (argumentlarni
to'ldirish) ham qo'ng'iroqdan tashqarida ishlaydi. Flow esa darhol yagona keyingi bosqichga o'tadi
va uning gapi aytiladi. Call 4 dagi `CREATE_TASK` jimligini (~2,3 s LLM + HTTP) yo'qotadi.

- `StageEntry.findDetachedNextStage` — qachon mumkin:
  - bosqich FUNCTION, yagona yo'l, branch / `requiredOutcomes` yo'q, flow FLEX emas;
  - keyingi bosqich branch'siz CONVERSATION yoki yana model ishi.

  Qoida sof, Spring'siz; agent tomonini (`timing`) chaqiruvchi tekshiradi. `ScenarioValidator` ga
  qo'shilmadi: ssenariy agentning funksiyalarini bilmaydi, shart bajarilmasa eski yo'l ishlaydi.
- `StageEntry.describeDetachedJob` — keyingi gapiruvchi turnga beriladigan eslatma ("u tool'ni chaqirma").
- `TurnRunner.detachStageJob` (`scheduleStageAction` step 1 da):
  - so'rov hali bosqich ichida turganda yasaladi: history nusxasi + vazifa + bosqich annex'i, tool — faqat shu bitta;
  - `executors` ga beriladi;
  - `setState(next)`;
  - keyingi bosqich model ishi bo'lsa → `scheduleStageAction`, aks holda gapiruvchi turn.
- `LlmTurnClient.runStageJob` — bitta bloklovchi so'rov (asosiy model). Tool chaqiruvlari bajariladi, matn tashlanadi, xato logga yoziladi.
- Faqat kaskad. Realtime'da FUNCTION bosqichi avvalgidek.

**Tekshirish:**
- unit: `StageEntryTest.aJobWhoseAnswerChangesNothingCanBeLeftRunning`, `aJobWhoseAnswerMayDecideTheWayOnIsWaitedFor`.
- sozlama: agent 3 da `createFollowUpTask` funksiyasiga `"timing": "BACKGROUND"`.
- test qo'ng'iroq (va'da beriladigan yo'l), logda:
  - `dialog state -> CONFIRMATION (stage CREATE_TASK left running createFollowUpTask in the background)`;
  - keyin `background stage job createFollowUpTask(...)` qatori;
  - `RECORD_PROMISE` turnidan keyin CONFIRMATION gapi ~1 turn kechikishi bilan aytiladi (Call 4 da ikki turn edi).

## 3-bosqich — Gapirayotgan turnga qo'shish va xayrlashuv himoyasi (kod yozildi, tekshirilmagan)

- **Inline taklif.** `StageEntry.findInlineJobTools` / `describeInlineJobs`: CONVERSATION bosqichi
  to'g'ridan-to'g'ri o'tadigan FUNCTION bosqichlarining tool'i shu bosqichda ham beriladi
  (`TurnTools.allowedTools`). Annex'ga "X ga o'tayotganda shu javobda T ni chaqir (avval T, keyin transitionTo X)"
  qatori qo'shiladi (`TurnRunner.advance` va `SpeculationManager` da).
  - Lokal ssenariy tool'i (`recordPaymentPromise`): natija aylanmasi yo'q.
  - READ HTTP tool (`SEARCH_FLATS`): kutish gapi va natija o'sha turnning follow-up round'ida keladi.
- **Bajarildi belgisi.** `TurnTools.run` tool natijasini turn holatiga yozadi:
  - `addDoneTool` — muvaffaqiyatli (`ToolResponseFormatter.isFailure`: `ERROR…` yoki status 4xx/5xx emas);
  - `addHeldTool` — tasdiq kutilmoqda.

  `scheduleStageAction`:
  - step 1 da tool bajarilgan bo'lsa → step 1 o'tkazib yuboriladi (`INLINE_JOB_NOTE` bilan step 2 ga);
  - tool ushlab turilgan bo'lsa → turn so'ralmaydi, mijoz "ha" deyishi kutiladi.
- **Xayrlashuv.** Javobdan keyin holat jim ish bosqichida bo'lsa va model xayrlashgan bo'lsa,
  qo'ng'iroq yopilmaydi, faqat `markFarewellSaid()`. Shundan keyin `scheduleStageAction` gapiradigan joyga
  yetganda (CONVERSATION/ENDING bosqichi, step 2 model turni yoki detach'dan keyingi gapiruvchi turn)
  `closeAfterFarewell` qo'ng'iroqni ikkinchi xayrlashuvsiz yopadi.

**Ma'lum kamchiliklar:**
- Inline'dan keyin keyingi bosqich CONVERSATION bo'lsa (`RECORD_REASON → PAYMENT_DATE`), step 2 model turni
  baribir bor. Avvalgi ikki turn o'rniga bitta qoladi.
- Model faqat `transitionTo(N)` ni chaqirib ishni o'tkazib yuborsa (yo'l yurish), ish bajarilmaydi. Bu eski
  xatti-harakat (`DialogTools.transitionTo` walk), o'zgartirilmadi.
- Faqat kaskad.

**Tekshirish:**
- unit:
  - `StageEntryTest.aConversationStageOffersTheJobsItLeadsToDirectly`;
  - `ToolResponseFormatterResultTest.aFailedOrRefusedAnswerIsToldFromOneThatDidTheJob`.
- test qo'ng'iroq (va'da yo'li), logda:
  - PAYMENT_DATE turnida `scenario tool recordPaymentPromise(...) recorded` va `dialog state -> RECORD_PROMISE`;
  - keyin `stage RECORD_PROMISE job recordPaymentPromise was done in the reply that moved here`;
  - `RECORD_PROMISE` uchun alohida `llm request` yo'q.
- Xayrlashuv holati (Call 8 kabi): `agent said goodbye on its way into stage RECORD_PROMISE`, keyin
  `the jobs after the goodbye are done — closing the call`. `call_result` da va'da yozilgan bo'ladi.

## 4-bosqich — Qo'ng'iroqdan oldin / keyin (pre-call kodi yozildi, tekshirilmagan)

- `pre_call_functions` kampaniya qo'ng'irog'ida telefon **jiringlayotganda** ishlaydi (originate'dan oldin
  emas: `channel_id` / `call_attempt_id` faqat originate'dan keyin bor).
  - `OutboundDialService` originate'dan keyin `RingingPreCallRunner.start` ni chaqiradi (`outboundCall != null` bo'lsa);
    faktlar javobdagidek yasaladi: `context.withPhone` + `withDefaultFacts(agent.defaultVariables())`.
  - Natija `PendingCallRegistry.registerPreCallRun` da; `CallSetupService` / `LiveKitCallService` setup boshida
    `takePreCallRun` qiladi (erta chiqishda ham qolib ketmaydi), pre-call joyida `RingingPreCallRunner.await`
    (byudjet + 1 s). Run yo'q bo'lsa — avvalgidek inline.
  - `forgetUnanswered` run'ni olib tashlaydi va bekor qiladi.
  - Narxi: javob berilmagan qo'ng'iroqda ham READ qidiruvlar ishlaydi.
  - Qo'lda/test, brauzer testi, kiruvchi — o'zgarmagan.
- `AFTER_CALL` timing'i (`PostCallActionExecutor` orqali integratsiya tool'i) — qilinmadi, kerak bo'lsa alohida.

**Tekshiruv (4):** `preCallFunctions` li agent bilan kampaniya qo'ng'irog'i.
- Logda `Pre-call functions of agent … done while ringing` javobdan **oldin** chiqadi.
- Javobdan keyin salomlashuvgacha pre-call kutish yo'q; `{{o'zgaruvchi}}` lar promptda to'lgan.
- Javob berilmagan qo'ng'iroq: xato yo'q, run qolib ketmaydi.

## Bu rejaga kirmaydi

- Salomlashuv `AI_CUSTOM` (ochilishda 5,4 s jimlik).
- gemini-3.8-flash TTFT, Gemini TTS TTFB.

Tartib: 1 → test qo'ng'iroq → 2 → 3 → 4. Har bosqich "keyingi bosqich" deyilgandan keyin.
