# Qo'ng'iroq sifati + Retell darajasidagi agent — reja (2026-11-05)

Manba: foydalanuvchi so'rovi — agentlar Retell'dagidek bo'lsin; suhbat sifatli va tez bo'lsin;
tool ishlayotganda jim turmasin ("hozir qarayapman" desin). Pipecat / LiveKit agents / TEN kodlari
lokal (`../pipecat`, `../agents`, `../ten-framework`).

## 0. Diagnoz — oxirgi 6 ta WEB-TEST qo'ng'iroq (#16–#21, agent 3 "Qarzdorlik (cascade)", ssenariy 14)

| # | Muammo | Dalil |
|---|--------|-------|
| 1 | LLM so'z/fakt to'qiydi | #19 "Kuchukcha turibdi", #20 "Laylakxat yoki boshqa ishlar yo'qmi?", #21 "Kuchukcha/nima muammo chiqdi?" (guardrail qatori qo'shilgandan KEYIN ham), #18 "yangi ishga o'tgan ekansiz" |
| 2 | Javob berilgan savolni qayta so'raydi | #18: mijoz "keyingi oy" → agent sanani so'radi → "aytdimku o'n beshinchi"; keyin tasdiq savoli ikki marta ketma-ket |
| 3 | Har javob "Tushunaman" bilan boshlanadi | #16, #18 |
| 4 | Gaplashilgan qo'ng'iroq "Javob bermadi" bo'lib ko'rinadi | 6 tadan 4 tasida `disposition=null`, AMD=HUMAN, mijoz gapirgan |
| 5 | Salom kech | birinchi agent gapi `tsOffsetMs` 3.0–5.9 s (offset qayerdan sanalishini tekshirish kerak) |
| 6 | LLM sekin, prompt katta | LLM o'rtacha 1.4–3.2 s, max 6.1 s; ~7k prompt token/turn. Retell shu agent uchun: 1.5k token, 830–1280 ms |
| 7 | Tool paytidagi gap | kod bor (filler rejasi C/D), lekin birorta test qo'ng'iroqda tool chaqirilmagan — tekshirilmagan |

## Bosqichlar (har biri "keyingi bosqich" bilan)

1. **Simulyatsiya (Retell "Simulation" tabi)** — agent darajasidagi saqlanadigan test holatlari
   (mijoz personasi + muvaffaqiyat mezoni), haqiqiy dialog dvigateli orqali matnda yuradi
   (prompt/bosqich/tool xuddi qo'ng'iroqdagidek, tashqi tool'lar mock), har turn uchun latency +
   token, oxirida LLM-baholovchi. Mavjud `POST /api/scenarios/{id}/test-personas` ustiga quriladi.
   Tekshiruv: brauzerdan #18 ga o'xshash holat ishga tushadi va 1–4-muammolar raqam bilan ko'rinadi.
2. **Suhbat sifati** — 1–4-muammolar, 1-bosqich simulyatsiyasida qizilni yashilga o'tkazib.
   To'qish manbasini aniqlash (spekulyatsiya bo'laklari ulanishi? `/` belgisi shunga ishora),
   faktni qayta so'ramaslik (`noteClientFact` bor — nega ishlamadi), disposition yozilishi.
3. **Tezlik** — prompt dietasi (~7k → ~2–3k), salomgacha vaqt, LLM TTFT; har o'zgarish
   simulyatsiya + `TurnLatency` bilan o'lchanadi. Statik kutish qo'shilmaydi.
4. **Tool paytida jim turmaslik** — sekin tool (xonadon qidirish) bilan simulyatsiya: tool
   boshlanishida aniq e'lon, uzun kutishda ikkinchi qisqa gap (vaqt emas, tool hodisasi asosida).
5. **Retell UI paritet qoldig'i** — Agent sozlamalari akkordeonlari (Speech, Realtime
   transcription, Call, Security & fallback, Post call extraction/memory, Webhook) bo'yicha
   farqlar ro'yxati, keyin frontend.

## 1-bosqich natijasi (2026-11-05)

Eval/test holatlari backend va `AgentEvalPanel.jsx` da allaqachon bor edi, lekin ishlamasdi:
- `TextTurnEngine` bo'sh javobda qayta so'ramasdi → salomda agent jim (tuzatildi);
- mijoz modeliga rollar teskari ketardi → Gemini 400 (tuzatildi, `AgentSimulator.asPersonaSees`);
- mijoz javobi 200 token limitda uzilardi; `mustSay` "1 250 000" ≠ "1250000"; hakam bugungi sanani bilmasdi (tuzatildi).
- Qo'shildi: `successCriteria` (Retell "Success Criteria"), V39.

**TEXT rejim haqiqiy qo'ng'iroqdan farq qiladi**: FUNCTION bosqich ishlari (`RECORD_PROMISE` → `CREATE_TASK`),
xayrlashgandan keyin go'shak qo'yish, filler'lar yo'q — shuning uchun "agent said nothing" chiqadi. Ishonchli
tekshiruv uchun **VOICE** rejim (haqiqiy STT/turn/TTS quvuri). TEXT'ni haqiqiy `TurnRunner` orqali yurgizish — keyingi ish.

### VOICE eval #6 (agent 3) — 2-bosqich uchun topilgan haqiqiy muammolar
1. Agent ko'rsatmasini ovoz chiqarib o'qidi: "Lekin qoidalarga ko'ra, GREETING bosqichida faqat shuni aytish kerak."
2. Sabab aytilgandan keyin yana sababni so'radi (ru: "финансовые трудности" → "по какой причине задержался платёж?").
3. Mijoz operator talab qildi → agent hech narsa demadi (disposition TRANSFERRED, lekin "ulayman" gapi yo'q).
4. Ketma-ket ikki xayrlashuv/tasdiq gapi (ru holat).
5. Sanani tasdiqlamasdan xayrlashadi; "24-noyabn" (xato yozilgan sana nomi).
6. Model bitta javobda bir tool'ni ko'p marta chaqiradi (`recordDelayReason` ×13, `noteClientFact` ×8) — TEXT logida.

## 2-bosqich — kodlangan tuzatishlar (2026-11-05, tekshirilmagan)
1. Ko'rsatma ovoz chiqarib o'qilishi → `SpeechSanitizer.namesStage` (ssenariy bosqich nomi, masalan `GREETING`,
   qatnashgan gap aytilmaydi) + annex "HOW TO REPLY" ga "stage name, rule, plan yozma" qoidasi.
2. Aytilgan sababni qayta so'rash → **ildiz sabab spekulyatsiya**: `isSpeculationCompatible` yakuniy gap oraliq
   matnga YANGI SO'Z qo'shganda ham "hit" derdi, javob qisqa matnga yozilgan bo'lardi. Endi faqat oxirgi so'z
   tugashi yoki "ha/да" qo'shilsa hit. Spekulyatsiya hit ulushi kamayishi mumkin (latency ↔ to'g'rilik).
3. Operator so'ralganda → fast-path TRANSFER faqat uzatish manzili bo'lsa (`TransferTargets.isOffered`);
   yolg'iz "operator" so'zi endi mos kelmaydi.
4. Ikki marta tasdiq → FUNCTION bosqich qadam-2 ko'rsatmasi "oxirgi gaping allaqachon aytgan bo'lsa matnsiz o't";
   agent mijozdan keyin gapirgan bo'lsa bo'sh javob jimlik sifatida qabul qilinadi (`isCallersTurnToAnswer`).
6. Tool spam → bitta javobda bir tool ko'pi bilan 3 marta (`TurnTools.MAX_SAME_TOOL_PER_TURN`).
5 (sanani tasdiqlamasdan xayrlashish) hali qilinmadi.

### VOICE eval #7 (2-bosqich tuzatishlaridan keyin): 4/5 o'tdi (#6 da 1/5)
Qolganlar: "Summaga e'tiroz" faqat `expectStage` (fast-path bosqichni o'zgartirmaydi), agent "operatorga ulayman" dedi.

**Yangi ildiz sabab — zaxira model poygasi.** `voice-agent ... backup-after-ms: 1000` (dialog.yml:104): asosiy
`gemini-3.8-flash` 1 s ichida token bermasa `gemini-3.5-flash-lite` parallel so'raladi. Bugungi logda 46 so'rovning
46 tasida poyga boshlangan, 43 tasida flash-lite yutgan — agent amalda flash-lite'da gapiradi. Flash-lite javoblari:
bo'tqa matn ("qulaymi? рооg'iroq qilyapman…"), rus mijozga o'zbekcha javob, #16–#20 dagi "Kuchukcha"/"Laylakxat".
O'lchov (curl, stream TTFB, ~3k token prompt): 3.8-flash LOW ≈ 1.9–2.3 s (thinkingBudget 0 ham ≈ 2 s),
3.5-flash LOW ≈ 1.5–1.7 s, 3.6/3.7-flash 1.3–5.8 s (beqaror), 3.5-flash-lite ≈ 0.76–1.0 s.

**Qaror (foydalanuvchi, 2026-11-05): flash-lite poygasi qoladi + himoyalar.** Kodlandi (tekshirilmagan):
- `SpeechSanitizer.hasMixedScriptWord` (`isUnspeakable` ichida): bir so'zda lotin+kirill — gap aytilmaydi.
- `SpeechSanitizer.isOtherScript` + `SpeechOutput.refusal`: javob mijozning oxirgi gapidan boshqa yozuvda
  (ikkala tomon ≥80% bir yozuv, mijoz ≥6, javob ≥8 harf) — aytilmaydi; butun javob shunday bo'lsa
  `LlmTurnClient.answerInClientsLanguage` asosiy model bilan (poygasiz) qayta so'raydi. Faqat streaming yo'lida.
- Ma'lum chekka holat: sessiya tilidagi fast-path javobi (masalan uz DNC javobi) mijoz rus tilida gapirgan
  bo'lsa rad etiladi.

### VOICE eval #8 (himoyalardan keyin): 3/5
- Rus tili holati endi butun suhbat ruscha (#7 da o'zbekchaga o'tib ketgan edi); faqat `expectStage` (CREATE_TASK) yiqildi.
- Spekulyatsiya: mijoz gapni davom ettirganda endi "miss" — javob qayta yoziladi (tuzatish ishlayapti).
- Summaga e'tiroz: simda uzatish manzili yo'q, model operator so'rovini e'tiborsiz qoldirdi → yangi
  `@no-transfer OPERATOR REQUEST` guardrail (ulay olmasligini aytadi, qayta qo'ng'iroq, xayrlashadi).
- Til/aralash yozuv himoyalari bu yurishda ishga tushmadi (bunday gap chiqmadi).
- Test holatlaridagi `expectStage` juda qattiq (fast-path va CRM job bosqichi) — holatlarni moslash kerak.

### VOICE eval #9 (test holatlari moslangach): 4/5
Test holatlari (agent 3): `expectStage` olib tashlandi, har biriga `successCriteria` (mijoz tilida, qayta so'ramaslik,
to'qimaslik, takrorlamaslik, ko'rsatma o'qimaslik); "Summaga e'tiroz" dan `TRANSFERRED`/`mustSay operator` olindi.
- Rus holati yiqildi: "Нигина aka, yaxshimisiz?" (kirill ism + o'zbekcha, 70% lotin) 80% chegaradan o'tib ketdi →
  javob uchun chegara 2/3 ga tushirildi (mijoz uchun 80% qoldi).
- "Summaga e'tiroz": model `requestHumanTransfer` chaqirdi (uzatish ishladi), lekin transkriptda uzatish oldidagi
  gap yo'q — aytilgan-aytilmagani aniqlanmadi.

### VOICE eval #10: 3/5 — yangi topilmalar va tuzatishlar (kodlangan, tekshirilmagan)
- Til himoyasi ishladi ("Нигина aka, yaxshimisiz?" rad etildi). Lekin transkriptga AYTILMAGAN gap ham yoziladi
  (`…qulaymi?uz-UZ`) — mijoz eshitmagan gap CRM/QA transkriptida turadi. Keyin ko'rish kerak.
- "Va'da beradigan mijoz" → WRONG_NUMBER: `CREATE_TASK` ovozsiz bosqich turnida (simda CRM tool yo'q, hamma tool
  berildi) flash-lite `recordWrongPerson("24-may")` chaqirdi. Tuzatish: `DialogOutcomeSink.isInStageJob()`
  bo'lsa `recordWrongPerson`/`recordDoNotCall` rad etiladi.
- "Summaga e'tiroz": mijoz 3 marta turli shaklda operator so'radi ("operatoringizga ulang", "operatorni ulab
  bering"), flash-lite e'tiborsiz qoldirib COMPLETED bilan yopdi. Tuzatish: fast-path `TRANSFER_REQUEST_UZ/RU`
  regex (so'rash shakllari; "operator bilan gaplashgandim" mos kelmaydi).
- Simdagi mijoz gapini bo'laklab aytadi → agent javoblari barge-in bilan tez-tez kesiladi.

### VOICE eval #11 — 2/5 (2026-11-05)
- Va'da ✓, Ishdan bo'shagan ✓.
- Summaga e'tiroz ✗ — test mezoni noto'g'ri edi (simda operator bor, fast-path TRANSFER ishladi). Mezon tuzatildi: operator so'roviga e'tiborsiz qolmasin — ulasin yoki ulay olmasa ochiq aytsin.
- DNC ✗ "not judged" — hakam so'rovi bir marta yiqildi. Tuzatish: `AgentEvaluationService` hakamni bir marta qayta so'raydi.
- Ruscha mijoz ✗ — rad etilgan (aytilmagan) o'zbekcha gaplar tarix/transkriptda qolib, til spirali bo'ldi; retry esa gapning kirillcha bo'lagi aytilgani uchun ishlamadi.
  Tuzatish (`LlmTurnClient`): `TurnResult` faqat aytilgan matnni (`spokenText`) olib yuradi; javob boshqa alifboda bo'lsa `answerInClientsLanguage` aytilgan bo'lakdan davom ettirib qayta so'raydi (qo'ng'iroq tugagan/bekor bo'lsa so'ramaydi).

### VOICE eval #12 — 2/5 (2026-11-05)
- Va'da ✓, Summaga e'tiroz ✓ (to'g'rilangan mezon bilan).
- Uchala xato ham zaxira model (flash-lite) yutgan navbatlarda chiqdi:
  - DNC: `recordDoNotCall` `reply` siz chaqirildi → qo'ng'iroq jim uzildi. Tuzatish: `TurnRunner` — tool qo'ng'iroqni gapsiz yopsa, `DialogLines.doNotCallReply`/`farewell` aytiladi (transkriptga ham yoziladi).
  - Ishdan bo'shagan: hamkor mijozga birinchi navbatdayoq `endCall(FAILED)`. Tuzatish: `DialogTools.endCall` tizim natijalarini (FAILED, NO_ANSWER, CARRIER_REJECTED, TRANSFERRED) rad etadi va qo'ng'iroqni tugatmaydi.
  - Ruscha: o'zbekcha gap `transitionTo` ning `reply` argumentida keldi — rad etildi, lekin javob sifatida transkriptga tushdi, o'rniga fallback "Секунду." aytildi. Tuzatish: `LlmTurnClient` — tool olib kelgan gap boshqa alifboda bo'lsa ham `answerInClientsLanguage` qayta so'raydi.
- Kuzatuv: flash-lite deyarli har navbatda yutyapti va xatolarning asosiy manbai. Himoyalar yetmasa, `backup-after-ms` ni oshirish yoki zaxirani o'chirishni ko'rib chiqish kerak.

### VOICE eval #13 — 3/5 (2026-11-05)
- Summaga e'tiroz ✓ (TRANSFERRED), Qo'ng'iroq qilmang ✓ (endi xayrlashadi), Ishdan bo'shagan ✓.
- Va'da ✗: flash-lite tool chaqiruvini matn qilib yozdi (`<call:default_api:transitionTo{…reply:…`). Faqat o'sha bo'lak rad etildi, davomi ("Вайсалинчи? (qayta so'rash/sababni so'rash) … }") mijozga aytildi.
  Tuzatish: `LlmTurnClient.SentenceQueue` — bitta gap unspeakable/system note bo'lsa, shu javobning qolgan gaplari aytilmaydi ("derailed").
- Rus tilida ✗: flash-lite yana o'zbekcha yozdi, asosiy model qayta yozdi → to'g'ri, lekin o'sha navbatlarda first_audio 5–6 s. Hakam: dushanba aytilgandan keyin "А в понедельник сможете оплатить?" deb qayta so'radi.
- Xulosa: flash-lite zaxirasi hali ham xatolarning asosiy manbai, himoya esa qimmat (qayta so'rash +3–4 s). Taklif: `dialog.yml` `backup-after-ms` 1000 → ~2500 (asosiy model TTFB ~1.9 s; zaxira faqat haqiqiy osilishda).
- Qaror (foydalanuvchi, 2026-11-05): `backup-after-ms` 1000 → 2500. Kutilgan natija: flash-lite faqat haqiqiy osilishda yutadi; first_audio ~2–2.5 s, lekin qayta so'rashlar (5–6 s) yo'qoladi.

### VOICE eval #14 — bekor (2026-11-05)
- Gemini loyihasi oylik xarajat limitiga yetdi (429 RESOURCE_EXHAUSTED): navbatlar, persona, hakam, summary — hammasi yiqildi. backup-after-ms 2500 hali sinalmagan.

## 5-bosqich — Retell agent sozlamalari bilan farqlar (2026-11-05, Gemini kaliti kutilayotganda)

Manba: Retell dashboard, "Uysot.uz Payment Reminder" agenti, 7 ta akkordeon. Bizda: `AiAgent` va uning
ichki record'lari + frontend (`robot-call-front`, maydon nomi bo'yicha qidirildi).

**Bor (backend + FE):** fon tovushi, response wait, interruption sensitivity, reminder (sekund/marta),
talaffuz; denoising (OFF / BACKGROUND_NOISE_SUPPRESSION / VOICE_ISOLATION), transcription mode, boosted keywords;
voicemail (hang up / xabar qoldirish), keypad (timeout, termination key, digit limit), sukutda tugatish,
max davomiylik, ring duration; zero-PII, audio saqlash, retention kunlari, default dynamic variables;
post-call memory; webhook URL, timeout, events.

**Yo'q yoki qisman:**
| # | Retell | Bizda | Izoh |
|---|---|---|---|
| 1 | iOS/Android call screening handling | yo'q | ekran-skrining (Google Call Screen) javob bersa, oldindan belgilangan gap |
| 2 | IVR hangup | yo'q | AMD faqat odam/mashina (VAD); IVR klassi yo'q |
| 3 | PII redaction kategoriyalari | faqat `redactPhoneNumbers` | Retell: kategoriya tanlash (karta, pasport, manzil…) |
| 4 | Safety guardrails kategoriyalari | yo'q (platform-guardrails.txt doimiy) | agent darajasida tanlanadigan toifalar |
| 5 | Data storage "Basic attributes only" | `zeroPiiRetention` + `storeCallAudio` | 3 darajali tanlov yo'q |
| 6 | Secure (24 soatlik imzoli) URL'lar | yo'q | yozuv/transkript havolalari doimiy |
| 7 | Fallback voice tanlash | faqat avtomatik (`TtsRouter` failover) | qo'lda zaxira ovoz tanlanmaydi |
| 8 | Response wait "dynamically adjust" | `llmTurnDetection` backendda bor, FE'da yo'q | faqat FE |
| 9 | DNC handling toggle | doim yoqilgan (`recordDoNotCall`) | o'chirib bo'lmaydi — ehtimol kerak emas |

Kuzatuv (3-bosqich uchun): Retell shu agentda **1.5k token / 830–1280 ms**; uning prompti = qisqa global prompt + bitta tugun ko'rsatmasi. Bizda ~7.5k token.

## 3-bosqich — prompt dietasi (2026-11-05, boshlandi)

**O'lchov (agent 3, ssenariy 14):** system ~15.6k belgi, 15 tool (~8.1k belgi), ~7.5k token/turn, ~59% kesh.
Ssenariyning o'zi atigi ~2k belgi (rolePrompt 294, guardrails 760, globalNodes 317, styleExamples 663,
callPurpose 166). Qolgani platformaniki: platform-guardrails ~4.3k, SPEECH RULES ~5.3k, persona ~0.9k,
EXAMPLES ~1.2k, vaqt jadvali ~1k, turn annex ("HOW TO REPLY" va h.k.) ~1.5k.

**1-qadam (kodlangan, tekshirilmagan):** SPEECH RULES dagi platforma guardrails'ni takrorlovchi qismlar ixchamlandi
(~1.5k belgi, ~10%): reaksiya qoidasi, "unintelligible" (NEVER INVENT/NOT HEARD bilan ustma-ust),
rasmiy so'zlar ro'yxati + almashtirishlar → bitta qoida, ism qoidasi (CLIENT'S NAME bilan ustma-ust).
Testlar tekshiradigan iboralar saqlandi.

**Keyingi qadam — avval o'lchash (Gemini kaliti bilan):** katta qisqartirishdan oldin TTFT promptga
qanchalik bog'liqligini bilish kerak: warm-up'da `gemini-3.8-flash` 5.9k token bilan 3.7 s, `flash-lite` 0.67 s;
turnlarda 3.8-flash ~1.9 s, 59% kesh bilan ham. Tekshiruv: bir xil savolni (a) to'liq prompt, (b) ~1.5k tokenli
qisqa prompt bilan 5 martadan streaming so'rab TTFT solishtirish. Agar farq <300 ms bo'lsa — sekinlik modelda,
prompt dietasi emas, model tanlovi (masalan, tezroq model yoki thinking budget=0) asosiy dastak.

### Token tejash — eval qo'ng'iroqlari summary qilinmaydi (2026-11-06)
- Sabab: `CallOutboxService` har 5 daqiqada summary'siz qo'ng'iroqlarni summary qiladi; `VOICE-SIM` urinishlari ham shunga tushardi (har test holatiga +1 LLM chaqiruv, xato bo'lsa har sweep'da qayta).
- Tuzatish: `CallAttemptJpaRepository.claimAttemptsAwaitingSummary` — `phone = 'VOICE-SIM'` qatorlar o'tkazib yuboriladi. Eval'ning o'z hakami (criteria + QA) qoladi. WEB-TEST qo'ng'iroqlar avvalgidek summary qilinadi.

### VOICE eval #15 — 4/5 (2026-11-06) — eng yaxshi natija
- Va'da ✓, DNC ✓, Rus ✓, Ishdan bo'shagan ✓. Backup 8 marta poygaga chiqdi, 2 marta yutdi; derailed 0; summary 0.
- Summaga e'tiroz ✗: FastPathRouter PURPOSE ("tinchlikmi") ssenariy `callPurpose` ko'rsatmasini so'zma-so'z o'qidi
  ("Sizga Mijoz Uysot'dan uy olgan… Maqsad — … masalasida bog'lanyapman") va javob berilgan "qulaymi?" savolini qaytardi.
  Tuzatish: PURPOSE fast-path olib tashlandi — bu savolga javob keyingi bosqichning ishi (LLM + transitionTo).
- Latency: first_audio median ~2.0 s (fast-path/spekulyatsiya 0.1–0.5 s), llm_ttft 1.7–3.5 s.

### TTFT o'lchovi (curl, streaming, 3 tadan, ~5.5k tokenli prompt) — 2026-11-06
| Model | thinking | qisqa prompt | uzun prompt |
|---|---|---|---|
| gemini-3.8-flash (hozirgi) | LOW | 1.9–6.3 s | 1.6–3.8 s |
| gemini-3.7-flash | LOW | — | 1.8–3.5 s |
| gemini-3.6-flash | MINIMAL | — | 1.33–1.39 s |
| gemini-3.5-flash | LOW / MINIMAL | — | 1.2–1.6 s |
| gemini-3.5-flash-lite | MINIMAL | 0.75–0.89 s | 0.90–1.05 s |
| gemini-3.5-flash-lite | LOW | 0.81–0.86 s | 2.4–4.0 s |
- 3.8-flash va 3.7-flash MINIMAL'ni qo'llamaydi (400). `dialog.yml` `thinking-level` global (LOW).
- **Xulosa:** 3.8-flash'da TTFT prompt hajmiga deyarli bog'liq emas — prompt dietasi uni tezlashtirmaydi. Asosiy dastak — model:
  3.5/3.6-flash ~0.6–1 s tezroq. Keyingi qadam: agent 3 ni `gemini-3.5-flash` (yoki 3.6 + MINIMAL) bilan eval qilib, sifatni solishtirish.
- 2026-11-06: `gemini-3.5-flash` model katalogiga qo'shildi (`R__seed_data.sql`, BALANCED; `docs/api/ai-models.md`). Backend qayta ishga tushgach agent 3 `llmModel` → `gemini-3.5-flash` (oldingi qiymat: `gemini-3.8-flash`), eval #16.

### VOICE eval #16 — 3/5, agent 3 = gemini-3.5-flash (2026-11-06)
- **Tezlik:** llm_ttft ~0.4–1.4 s (median ~1.1 s; #15 da 1.7–3.5 s), first_audio median ~1.4 s (#15: ~2.0 s). Backup poygasi 0 marta.
- **Sifat yomonlashdi:** 3.5-flash "avval matn, keyin tool" qoidasiga bo'ysunmaydi — 9 ta matnsiz (faqat tool) navbat;
  Va'da holatida `recordDelayReason` ni 4 marta qayta chaqirib, mijoz ~20 s davomida 4 marta gapirdi, agent jim (fallback qatorlar).
- DNC ✗: simulyator artefakti — `VoiceCallSimulator.awaitBotTurn` `session.isEnded()` ni ko'rib darhol chiqib ketardi, yakuniy qator
  (spoken-line retry) aytilmay qolardi. Tuzatish: endi haqiqiy hangup'ni kutadi (`SpeechOutput#finishWhenSpoken` dan keyin).
- Xulosa: 3.5-flash tez, lekin tool-only navbatlar sukutga olib keladi. Variantlar: (a) 3.8-flash'ga qaytish, (b) 3.6-flash'ni sinash,
  (c) tool-only navbatda asosiy model bilan result-round (+~1 s).

### VOICE eval #17 — 3/5, agent 3 = gemini-3.6-flash (2026-11-06)
- Summaga e'tiroz ✓, DNC ✓ (simulyator tuzatishi ishladi — xayrlashuv eshitildi), Ishdan bo'shagan ✓.
- Va'da ✗: "Juma kuni to'lab bera olasizmi, shuni yozib qo'yaymi?" — sana aytilmay, qayta so'radi.
- Rus ✗: ikki marta xayrlashdi. Sabab: `Farewells` "хорошего **вам** дня" ni tanimadi → yopish yo'li ikkinchi xayrlashuvni aytdi.
  Tuzatish: `Farewells.AGENT_CLOSINGS` ga "вам" li ruscha shakllar (+ FarewellsTest). Kuzatuv: ayolga "Нигина-ака".
- Tezlik: llm_ttft 1.10–1.46 s (barqaror), lekin 7 ta matnsiz tool navbati → spoken-line retry, first_audio median ~2.2 s.

| Eval | Model | Natija | llm_ttft | first_audio (median) | Matnsiz tool navbati |
|---|---|---|---|---|---|
| #15 | gemini-3.8-flash | 4/5 | 1.7–3.5 s | ~2.0 s | (backup 8 poyga) |
| #16 | gemini-3.5-flash | 3/5 | ~1.1 s | ~1.4 s | 9 (sukut, sikl) |
| #17 | gemini-3.6-flash | 3/5 | 1.1–1.5 s | ~2.2 s | 7 |

Xulosa: har test holati bitta urinish — 4/5 va 3/5 farqi shovqin ichida. Uchala modelda ham asosiy kechikish manbai —
**matnsiz tool navbati** (model avval tool chaqiradi, gapni yozmaydi → qo'shimcha so'rov, +0.7–1 s). Keyingi dastak shu.

### Matnsiz tool navbatlari — gap `reply` da (2026-11-06, kodlangan, tekshirilmagan)
- Sabab: 3.5/3.6/3.8 ham ko'pincha avval tool chaqiradi, matn yozmaydi. Hamma tegishli tool'da (DialogTools + ssenariy tool'lari)
  `reply` parametri bor va `LlmTurnClient` uni tool'lar ishlagan zahoti aytadi — lekin tavsif "bo'sh qoldir" derdi, shuning uchun
  gap alohida so'rov bilan qayta so'ralardi (+0.7–1 s).
- Tuzatish: `DialogTools.REPLY_DESCRIPTION` — "matn yozmagan bo'lsang, mijozga aytadiganingni shu yerga yoz"; `SystemPromptFactory`
  HOW TO REPLY — "matnsiz tool chaqirsang, gapingni BITTA tool'ning reply'iga yoz" (lookup/waitForCaller'da — na matn, na reply).
  `TurnState.addToolReply` — bir xil gap ikki tool'da kelsa bir marta aytiladi (+DialogToolsTest).
- Tekshiruv: eval'da `asking again for the spoken line` soni 7–9 dan ~0 ga tushishi, first_audio median pasayishi.
