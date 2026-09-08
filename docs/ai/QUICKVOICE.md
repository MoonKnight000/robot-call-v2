# QuickVoice bilan solishtirma — nima olamiz, nima olmaymiz

[QuickVoice](https://github.com/allgpt-co/QuickVoice) — ochiq kodli AI telefon agenti
platformasi (Retell alternativasi sifatida pozitsiyalangan). Tarkibi: marketing sayt,
mijoz konsoli, Express + Prisma/Postgres API, LiveKit asosidagi Python AI worker,
Twilio/Telnyx telefoniyasi.

Bu hujjat — bir martalik tahlil natijasi. Maqsadi: qaysi g'oyani o'zimizga olishimiz
kerakligini va **qaysi birini ataylab olmaganimizni** yozib qo'yish, keyin bu savol
qayta ochilmasligi uchun.

Tahlil sanasi: 2026-09-07. QuickVoice commit: `4243b0b`.

---

## 1. Voice pipeline — bu yerda olinadigan narsa deyarli yo'q

Ularning `apps/ai` worker'i — LiveKit Agents SDK ustidagi yupqa qatlam. Turn detection,
barge-in, TTS bekor qilish — hammasi SDK ichida, ularning kodida emas:

```python
# apps/ai/main.py
session = AgentSession(
    vad=silero.VAD.load(),
    turn_handling=TurnHandlingOptions(turn_detection=inference.TurnDetector()),
)
```

Ya'ni portlanadigan mantiq yo'q — SDK'ning o'zi ko'chirilmaydi.

Bizda bor, ularda **yo'q**: filler ("bir soniya..."), bot backchannel, spekulyativ LLM +
spekulyativ TTS, TTS cache, STT/TTS provayder failover, avtojavob berish moslamasini
aniqlash (AMD), maksimal qo'ng'iroq davomiyligi, `FactGuard` (LLM summa/muddatni o'zidan
aytmasligi), `TurnLatency` bosqichma-bosqich kechikish ledgeri, `TranscriptTurnCues`
(o'zbek/rus morfologiyasi bo'yicha EOU).

Ularning `AgentConfiguration` da `preemptive_generation`, `fallback_model`,
`silence_end_call_timeout_seconds`, `turn_timeout_seconds`, `max_conversation_duration_seconds`
maydonlari **bor, lekin hech qayerda o'qilmaydi** — sxemadagi maydon ishlayotgan feature
degani emas. `tests/test_livekit_turn_handling.py` esa `preemptive_generation` ni
ataylab tashqarida qoldirishni tasdiqlaydi.

### Voice tomonidan olinadigan uchta kichik g'oya (hozir qurilmaydi)

1. **Transport bo'yicha turli noise-cancellation modeli** — SIP ishtirokchi uchun bir
   model, WebRTC uchun boshqasi (`main.py: build_room_options`). Bizda
   `agent/audio/TelephonyNoiseCanceller` bitta yo'l uchun sozlangan; widget WebRTC
   oqimi kuchayganda shu ajratish kerak bo'ladi.
2. **Boot vaqtidagi readiness self-check** — `utils/runtime_readiness.py` ishga tushishda
   konfiguratsiyani tekshiradi va noto'g'ri bo'lsa baland ovozda yiqiladi, qo'ng'iroq
   o'rtasida emas.
3. **Matnli kirishni xuddi shu dialog engine'ga ulash** — preview/widget mijozi matn
   yuboradi, worker uni sun'iy user turn'ga aylantiradi. Bizning `widget/` uchun
   `DialogEngine` ni qayta yozmasdan matnli rejim beradi.

---

## 2. Backend/product — bu yerda ular bizdan oldinda

| Mavzu | QuickVoice | Bizda |
|---|---|---|
| Billing | To'liq wallet: append-only ledger (idempotency key bilan), qo'ng'iroqdan oldin balans rezervi, per-call `CallBillingSession` holat mashinasi, provider CDR bilan solishtirish, auto-recharge, versiyalangan narx katalogi + ustama | `BillingUsage.defaultFor` **qattiq yozilgan demo raqamlar** qaytaradi; `CallCostCalculator` — hech kim chaqirmaydigan o'lik kod |
| Konversiya | `CampaignGoal → ConversionEvent → AttributionResult` — qo'ng'iroq haqiqiy natijaga (to'lov, kelishuv) bog'lanadi, versiyalangan atributsiya siyosati bilan | `CampaignVariant` + `AbTestSignificance` bor, lekin faqat disposition bo'yicha o'lchaydi |
| MCP | Katalog + ulanish holat mashinasi + har chaqiruv logi; tasdiq talab qiladigan tool'lar promptdan butunlay yashiriladi | Yo'q |
| Tashqi kirish | Org-scoped API kalitlari; kalit ruxsatlari hardcoded allowlist bilan **kesiladi** (billing'ni o'zgartirish har qanday holatda yopiq) | Faqat konsol JWT |
| Kampaniya personalizatsiyasi | Tipli maydon sxemasi (`source`, `missingBehavior`: fallback/omit/skip), dial oldidan quruq render + PII-xavfsiz ko'rinish | Prompt o'zgaruvchilari bor, lekin sxema va preflight yo'q |
| SSRF himoyasi | KB ingest va webhook'da bor | `FactWebhookClient`, `TargetApiImporter` da bor; `HttpToolExecutor` va `RestClientWebhookSenderAdapter` da **yo'q** |

### Ikkalamizda ham yo'q, lekin kerak

**Webhook HMAC imzosi.** QuickVoice chiquvchi webhook'ni imzolamaydi — faqat custom
header beradi. Bu ularning kamchiligi, takrorlamaymiz: biz Stripe uslubidagi
`t=<epoch>,v1=<hex>` imzo qo'shamiz (ular buni faqat *kiruvchi* Stripe webhook'i uchun
to'g'ri qilishgan).

---

## 3. Olamiz — bajarildi

Beshtasi ham yozildi (2026-09-07). Har biri o'z hujjatiga ega, bu yerda faqat nima
qilingani va QuickVoice'dan nimasi olingani:

1. **Xavfsizlik** — `shared/util/PublicUrlGuard` (bitta joyda SSRF qoidasi),
   `HttpToolExecutor` da redirect o'chirilishi va javob hajmi chegarasi, chiquvchi
   webhook HMAC imzosi.
2. **Billing'ni haqiqiy qilish** — narx katalogi, `call_billing` + `billing_ledger`,
   dial oldidan balans gate'i, `CallFinalizer` da hisob-kitob.
3. **API kalitlari** — tashqi tizimlar uchun kirish, allowlist bilan kesilgan scope.
4. **Konversiya atributsiyasi** — maqsad → konversiya hodisasi → atributsiya, va shu
   asosda ROI hisoboti.
5. **MCP klient** — kompaniya ulagan MCP serverlari tool sifatida ([mcp.md](../api/mcp.md)).
   Buzuvchi tool modelga hech qachon berilmaydi; yozuvchi tool faqat kompaniya
   `allowWrites` ni yoqsa. Spring AI starteri emas, sof JSON-RPC: ulanishlar kompaniya
   bo'yicha dinamik, starterning statik autoconfig'i bunga mos kelmaydi.

Hujjatlar: [webhooks.md](../api/webhooks.md) §3, [billing.md](../api/billing.md) §6,
[api-keys.md](../api/api-keys.md), [conversions.md](../api/conversions.md),
[mcp.md](../api/mcp.md).

---

## 4. Ataylab OLMAYMIZ

Bu ro'yxat qayta muhokama qilinmasligi uchun yozilgan.

- **Pinecone / tashqi vektor DB.** Bizning `KnowledgeRetrievalService` ataylab
  in-process cosine — korpus kichik, tarmoq sakrashi kechikish qo'shadi. Ularning
  500-belgili `CHUNK_SIZE` char-chunking'i bizning chunker'dan yomonroq (jumla
  o'rtasidan kesadi).
- **Regex asosidagi post-call ma'lumot chiqarish.** `post_call_metadata.py` ismni
  `"my name is ..."` shablonidan qidiradi. Bizning `SummaryService` + scenario
  `outcomeSchema` LLM bilan strukturali chiqaradi — bu orqaga qadam bo'lardi.
- **Cursor pagination.** Bizda `PageableData` offset asosida; almashtirish uchun sabab
  yo'q, frontend ham shunga qurilgan.
- **Micros (BigInt, 1e6 = $1).** Bizda `BIGINT` UZS — tiyin yo'q, aniqlik yetarli.
- **Provider CDR bilan solishtirish (`TelephonyCostReport`).** Telnyx narxni asinxron
  CSV hisobot bilan beradi; bizning trunk narxi oldindan ma'lum, davomiylikdan
  hisoblanadi.
- **O'z API'mizni MCP server sifatida ochish** (ularning `apps/mcp-server` i). Qiziq,
  lekin mijoz talabi yo'q. `apps/mcp-server/src/api-registry.ts` baribir foydali —
  butun REST yuzasini bitta faylda, auth talablari bilan tavsiflash namunasi.
- **LiveKit'ga o'tish.** Savol emas: bizning transport — Asterisk ARI + RTP.
