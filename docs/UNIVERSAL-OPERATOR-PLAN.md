# UNIVERSAL-OPERATOR-PLAN — qarz undirishdan "har sohadagi operator"ga

Sana: 2026-09-14. Maqsad: platforma bitta ssenariy (qarz undirish) uchun emas, **istalgan
sohadagi operator ishini**
bajaradigan bo'lsin — buyurtma qabul qilish, navbatga yozish,
lead kvalifikatsiyasi, 1-liniya support — va **har kundan kunga o'zi yaxshilanadigan**
halqaga ega bo'lsin.

Bu hujjat `docs/AUDIT-2026-09-13.md` ustiga quriladi: audit "nima buzilgan"ni aytadi, bu
reja "qayerga borish kerak"ni. Kod misollari ko'rsatilgan joyda ular mavjud fayllarga
ishora qiladi, taklif emas.

---

## 0. Qisqa xulosa

Yaxshi xabar: platformaning **umumiy qismi allaqachon bor** va u Vapi/Retell darajasidagi
raqobatchilardan kam emas. Yomon xabar: yadroning bir nechta joyi hali "qarz" deb
o'ylaydi, tool qatlami esa "buyurtma" darajasidagi murakkab yozuvni ko'tara olmaydi.

| Soha-mustaqil bo'lishi uchun kerak                        | Hozir                                                                                                           | Holat                       |
|-----------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------|-----------------------------|
| Model tashqi API'ni o'zi to'ldirib chaqirishi             | `tool/` — `LLM Prompt` parametrlar, path/query/body, secrets, responseMapping, retry (`docs/api/tools.md`)      | ✅ bor, lekin yassi         |
| MCP orqali tashqi tool'lar                                | `mcp/` — discovery, `McpToolGuard` (destructive → hech qachon, write → `allowWrites`)                           | ✅ bor                      |
| Bilimlar bazasi (RAG)                                     | `knowledgebase/` — hujjat/URL/matn, embedding, xotiradagi cosine                                                | ✅ bor, ~10k chunk cheklovi |
| Mijoz xotirasi (qo'ng'iroqlar aro)                        | `memory/ClientMemory` — `(companyId, phone)`, facts, recentCalls                                                | ✅ bor                      |
| Agent shablonlari                                         | `AgentTemplate` — `BUSINESS/SUPPORT/MEDICAL/BLANK`, `AiAgentTemplates` (kodda)                                  | ⚠️ kodda hardcode           |
| Ssenariy: bosqichlar, faktlar, natija sxemasi, guardrails | `scenario/ScenarioDefinition` — `stages`, `factSchema`, `outcomeSchema`, `guardrails`, `factWebhook`            | ✅ bor                      |
| Eval: test holatlari + yugurish                           | `aiagent/AgentTestCase`, `AgentEvalRun` — persona, `mustSay/mustNotSay`, `expectDisposition`                    | ✅ bor, 3 hardcode persona  |
| Sifat sudyasi                                             | `agent/summary/CallQualityJudge` — sampled, default o'chiq, natija faqat metrikaga                              | ⚠️ halqaga ulanmagan        |
| A/B variantlar, versiya, rollback                         | `AiAgentVersion`, `ScenarioDefinition.withRolePrompt`, `AgentVersionStats`                                      | ✅ bor                      |
| Konversiya maqsadi                                        | `conversion/ConversionGoal` — `goalKey` (`payment`, `appointment`), attribution                                 | ✅ bor                      |
| Natija turlari                                            | `shared/dialog/Disposition.PROMISE_TO_PAY`, `ReasonCode.NO_MONEY/JOB_LOSS/...`                                  | ❌ qarzga bog'langan        |
| Raqamlar nazorati                                         | `agent/dialog/FactGuard` — faqat **faktlardagi** pul-shkala raqamlar; tool javobidagi narx/ID hisobga olinmaydi | ❌ qarzga bog'langan        |
| System prompt                                             | `SystemPromptFactory` — "qarzingiz bor", "to'lov muddati" jumlalari kodda                                       | ❌ qarzga bog'langan        |
| Kompleks yozuv (ichma-ich JSON, ro'yxat)                  | `Tool.apiBody` — yassi `List<ToolParamConfig>`; `items: [{sku, qty}]` yasab bo'lmaydi                           | ❌ yo'q                     |
| OpenAPI'dan tool import                                   | —                                                                                                               | ❌ yo'q                     |
| Yozuvchi tool oldidan tasdiq (kod darajasida)             | Faqat prompt; `McpToolGuard` faqat MCP uchun                                                                    | ❌ yo'q                     |
| Katalog qidiruvi (mahsulot → ID)                          | —                                                                                                               | ❌ yo'q                     |
| O'z-o'zini yaxshilash halqasi                             | Bo'laklari bor (judge, eval, A/B, memory), **bir-biriga ulanmagan**                                             | ❌ yo'q                     |

**Asosiy tamoyil.** "Har bitta soha" — bu 40 ta yangi feature emas. Soha = **konfiguratsiya
paketi**: agent + ssenariy + tool'lar + bilimlar bazasi + natija sxemasi + test holatlari.
Yadro bitta va soha bilmaydi; sohalar — ma'lumot. Aks holda AUDIT §7.1 dagi scope creep
davom etadi.

---

## 1. ✅ Bosqich A — Yadroni qarzdan ajratish

Hozir "qarz" uchta joyda yadroga tikilgan. Bularni olib tashlamasdan boshqa soha
paketlari doim ikkinchi darajali bo'lib qoladi.

### ✅ A.1. `Disposition` va `ReasonCode` — transport vs biznes natija

- `Disposition` da ikki xil narsa aralashgan: **telefoniya natijasi** (`NO_ANSWER`,
  `HUNG_UP`, `VOICEMAIL`, `CARRIER_REJECTED`, `FAILED`, `TRANSFERRED`, `DO_NOT_CALL`,
  `CALLBACK_REQUESTED`, `WRONG_NUMBER`) va **biznes natijasi** (`PROMISE_TO_PAY`,
  `REFUSED`, `COMPLETED`).
- Biznes natijasi ssenariyning `outcomeSchema` sida allaqachon tasvirlanadi. Shuning uchun:
  `Disposition` faqat telefoniya + ikkita umumiy qiymat qoladi: `GOAL_REACHED`,
  `GOAL_NOT_REACHED`. Ssenariy o'z `outcomeSchema` sida `goalField` ni aytadi (qarz uchun
  `promiseToPay == true`, magazin uchun `orderId != null`, klinika uchun `slotBooked`).
- `ReasonCode` enum'i o'chadi → `OutcomeField` ning `allowedValues` bo'ladi. Qarz ssenariysi
  seed'ida `NO_MONEY, JOB_LOSS, ...` ro'yxati saqlanadi — hech narsa yo'qolmaydi, faqat
  koddan ma'lumotga ko'chadi.
- `Disposition.isConversion()` va `AgentVersionStats.conversions` → `ConversionGoal`
  orqali hisoblanadi (u allaqachon `goalKey` bilan kompaniya darajasida).

Ta'sir zonasi: `call_result`, hisobotlar (`report/`), CRM note, `MidCallSmsSender`,
`AgentVersionStats`. Migratsiya: `PROMISE_TO_PAY → GOAL_REACHED + outcome.promiseToPay=true`,
`REFUSED → GOAL_NOT_REACHED + outcome.reason`.

**Tekshiruv:** qarz ssenariysi bilan eski regressiya qo'ng'irog'i xuddi shu CRM note va
hisobot qatorini beradi; `outcomeSchema` da `promiseToPay` bo'lmagan ssenariy
`GOAL_REACHED` ni tool natijasi (`orderId`) bilan qaytaradi.

**Qaror (2026-09-15) — buzmasdan, qo'shimcha.** `GOAL_REACHED`/`GOAL_NOT_REACHED` qo'shildi
(V9), lekin `PROMISE_TO_PAY`/`REFUSED`/`ReasonCode` joyida qoldi: frontend va hisobotlar shu
nomlarga bog'langan. Maqsad alohida `goalField` bilan emas, `OutcomeField.goal` bayrog'i bilan
belgilanadi. Disposition'ni model emas, `agent/dialog/GoalOutcome` hal qiladi; maqsad qiymati
ssenariy tool argumentidan yoki HTTP tool'ning `responseMapping` maydonidan (faqat 2xx) keladi.
Qarz ssenariysida maqsad maydoni yo'q — u avvalgidek ishlaydi.

### ✅ A.2. `SystemPromptFactory` — qarz jumlalari ssenariyga

`SystemPromptFactory.java:60-61` dagi o'z izohi buni allaqachon aytadi: "domain-specific
rules belong in the scenario's own guardrails". Amalda 257, 362-375, 434-438, 610-qatorlar
qarz haqida gapiradi.

- Umumiy qism (til, qisqalik, bo'lmaslik, raqamlarni raqam bilan yozish, disclosure) —
  kodda qoladi.
- "Qarz bor-yo'qligi muhokama mavzusi emas", "Nima uchun to'lanmayapti?" uslub misollari —
  qarz ssenariysining `rolePrompt` va `guardrails` ga ko'chadi (seed migratsiya).
- `SystemPromptFactory` ssenariydan **`styleExamples`** (✗ → ✓ juftliklar) ro'yxatini oladi;
  bu maydon `ScenarioDefinition` ga qo'shiladi. Har soha o'z misollarini beradi.

**Tekshiruv:** qarz agentining eval yugurishi (`POST /api/ai-agents/{id}/eval-runs`)
ko'chirishdan oldin va keyin bir xil `mustSay/mustNotSay` natijasini beradi.

### ✅ A.3. `FactGuard` — manba: faktlar **va tool natijalari**

Hozir pul-shkala raqam faqat `factSchema` dan kelishi mumkin. Magazin uchun narx katalog
tool'idan, buyurtma raqami `create_order` javobidan keladi — hozirgi guard ularni "uydirma"
deb bloklaydi.

- `CallContext` ga `toolFacts` to'plami qo'shiladi: har tool javobidagi (`responseMapping`
  dan keyingi) raqam va sana qiymatlari guard uchun ruxsat etilgan manbaga aylanadi.
- Guard qoidasi o'zgarmaydi: "aytilgan raqam qayerdandir kelgan bo'lishi shart". Faqat
  "qayerdan" kengayadi.
- Ruscha son so'zlarini ham o'qish (`UzbekNumberParser` yonida `RussianNumberParser`) —
  guard'dagi ma'lum teshik, `ru-RU` qo'ng'iroqlar ko'payganda yopilishi shart.

**Tekshiruv:** unit test — tool `{"price": 250000}` qaytargan, agent "250 ming so'm" dedi →
o'tadi; agent "300 ming" dedi → bloklanadi.

### ✅ A.4. `CallQualityJudge` rubrikasi — maqsadga bog'lanadi

`guardrailViolation`, `languageMismatch`, `talkedOverClient`, `outcomeCorrect` umumiy va
qoladi. Qo'shiladi: `goalReached` (ssenariy `goalField` ga qarab), `unansweredQuestion`
(mijoz so'radi, agent javob bera olmadi / "bilmayman" dedi — KB bo'shlig'i signali),
`toolMisuse` (tool kerak edi, chaqirilmadi yoki noto'g'ri argument). Bu uchta flag D
bosqichdagi halqaning kirishi.

---

## 2. Bosqich B — Tool qatlami: "mijoz aytadi, AI body'ni to'ldirib yuboradi"

Foydalanuvchi misoli: magazin API'siga ulanadi, mijoz buyurtmani og'zaki aytadi, agent
`POST /orders` body'sini to'ldirib yuboradi. Bugun bu **yassi body** uchun ishlaydi
(`{customer_phone, product_name, quantity}`); real buyurtma `{items:[{sku,qty}], address:{...}}`
— ishlamaydi. Bosqich shu bo'shliqni yopadi.

### ✅ B.1. Ichma-ich body — JSON Schema

- `Tool` ga `bodySchema` (JSON Schema, `object`/`array`/nested) qo'shiladi; `apiBody`
  yassi ro'yxati saqlanib qoladi (oddiy holatlar uchun), ikkalasi bir vaqtda berilsa —
  `400 TOOL_BODY_AMBIGUOUS`.
- Sxemadagi `x-source: static|variable|secret` kengaytmasi orqali maydon qaysi manbadan
  to'lishi aytiladi; qolgani modelniki (hozirgi `valueType` ning sxema ichidagi ko'rinishi).
- `ScenarioToolCallbackFactory` / `DialogTools` sxemani to'g'ridan-to'g'ri LLM tool
  ta'rifiga uzatadi — model butun obyektni bir chaqiruvda to'ldiradi.

**Tekshiruv:** `POST /api/tools/{id}/test` ga `args: {items:[{sku:"A1",qty:2}]}` berilsa,
endpoint'ga aynan shu ichma-ich JSON ketadi.

### ✅ B.2. OpenAPI import

`POST /api/tools/import` — `{ "openApiUrl" | "openApiSpec", "operationIds": [...] }` →
har operation'dan `Tool` yasaladi: nom `operationId`, tavsif `summary + description`,
path/query/body sxemadan, auth `securitySchemes` dan `{{secrets.KEY}}` bilan. Import qilingan
tool oddiy tool — keyin qo'lda tahrirlanadi. Bu "har bitta soha"ga kirishning eng arzon
yo'li: deyarli har platformada OpenAPI bor (Shopify, WooCommerce, Bitrix24, iiko, Poster,
1C).

Adapter: `tool/infrastructure/adapter/OpenApiToolImporterAdapter` (parser — tashqi
kutubxona, servis faqat `ToolImporterPort` ni ko'radi).

**Holat (2026-09-15).** Qilindi (`docs/api/tools.md` §7): `POST /api/tools/import/operations` (ro'yxat, hech
narsa yaratmaydi) va `POST /api/tools/import` (bitta tranzaksiya). Rejadan farq: tashqi kutubxona emas, o'z
o'quvchi — `OpenApiToolImporter` (sof Java, `domain.service`), matnni esa `OpenApiDocumentReaderAdapter`
(Jackson / SnakeYAML) o'qiydi. Sababi: swagger-parser masofaviy `$ref` larni o'zi yuklaydi, bu `PublicHttpClient`
dagi SSRF himoyasini chetlab o'tadi. Bizga faqat OpenAPI 3.x, lokal `$ref`, `allOf`, parametrlar, JSON body
va `securitySchemes` kerak edi. Swagger 2.0 rad etiladi (`OPENAPI_VERSION_UNSUPPORTED`) — avval 3.x ga
o'giriladi. `DELETE` amallar `DESTRUCTIVE` bo'lib import qilinadi (modelga berilmaydi). Birinchi real
magazin platformasi (ochiq savol 2) hali noma'lum — §6 mezonini o'sha hujjatda sinash kerak.

### ✅ B.3. Yozuvchi tool siyosati — kod darajasida tasdiq

`McpToolGuard` prinsipini HTTP tool'larga ham keltirish. `Tool` ga:

| Maydon                 | Ma'nosi                                                                                                                                                                                                                                                                               |
|------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `effect`               | `READ` / `WRITE` / `DESTRUCTIVE`. `DESTRUCTIVE` modelga hech qachon berilmaydi (MCP bilan bir xil)                                                                                                                                                                                    |
| `requiresConfirmation` | `WRITE` uchun default `true`: `DialogTools` tool'ni **ikki fazali** qiladi — 1-faza: model argumentlarni yig'adi, tizim ularni mijozga o'qib beradi ("2 ta A1, Chilonzor 5, 250 ming — tasdiqlaysizmi?"), 2-faza: faqat aniq "ha" dan keyin HTTP ketadi. Tasdiq prompt'da emas, kodda |
| `idempotencyKey`       | `POST` retry xavfsiz bo'lsin: `Idempotency-Key: {{call_id}}:{{tool}}:{{n}}` header'i avtomatik. Hozir `retryAttempts` default 0 aynan shu sababdan                                                                                                                                    |
| `readbackTemplate`     | Tasdiq jumlasi shabloni (`{{items}} … {{total}} so'm`), bo'sh bo'lsa model o'zi tuzadi                                                                                                                                                                                                |

Bu bo'lmasa "mijoz buyurtma beradi" — bu "notanish odam ovoz bilan sizning tizimingizga
yozadi" degani; audit §2 aynan shu xavfdan ogohlantiradi.

**Tekshiruv:** eval test holati — persona "3 ta emas, 2 ta" deb o'zgartirsa, HTTP faqat
bir marta, oxirgi tasdiqlangan argument bilan ketadi (`tool_execution` jadvalida 1 qator).

**Holat (2026-09-15).** B.1, B.3, B.6 qilindi (V10, `docs/api/tools.md` §3.1–3.3). "Ha" kodda
`agent/dialog/Affirmations` bilan aniqlanadi, tasdiq kutilayotgan chaqiruv `DialogSession` da turadi.
Mavjud tool'lar uchun `requires_confirmation=false` qoldirildi, shunda ishlab turgan qo'ng'iroqlar
o'zgarmaydi; yangi `WRITE` tool'da standart `true`. `idempotencyKey` alohida maydon emas — `WRITE` da
avtomatik. Cheklov: kompaniya HTTP tool'lari faqat cascade pipeline'da ishlaydi, realtime ularni
hozir ham taklif qilmaydi.

### ✅ B.4. Katalog qidiruvi — "ro'yxat qaytaradigan endpoint tool bo'lmaydi"

`docs/api/tools.md` §6.2 to'g'ri aytadi: 4000 belgi chegarasiga katalog sig'maydi. Mijoz
"qora rangli 42 razmer krossovka" deydi — SKU kerak.

- `KnowledgeSourceType.STRUCTURED_FEED`: CSV/JSON/OpenAPI list endpoint'i davriy (cron)
  qayta indekslanadi; har qator = bitta chunk, `metadata` da `sku`, `price`, `stock`.
- Agentga avtomatik `search_catalog(query)` tool'i beriladi (`RealtimeKnowledgeTools`
  singari) — natija top-5 qator, model SKU ni tanlab `create_order` ga uzatadi.
- Xotiradagi cosine qidiruv (audit §7.2) 10k dan katta katalogda yiqiladi → **pgvector**
  ga o'tish shu bosqichda majburiy (`KnowledgeChunkRepositoryAdapter` almashadi, servis
  o'zgarmaydi).

**Holat (2026-09-15).** Qilindi (V11, `docs/api/knowledge-base.md` §Katalog). Katalog alohida
`catalog_item` jadvalida turadi: `vector(768)` + HNSW va `tsvector` + GIN. Hujjat parchalari
(`knowledge_chunk`) esa xotirada qoldi — ular kichik, va ularni ko'chirish endi faqat adapterni
almashtirishga teng. Qidiruv gibrid: DB so'zlar va vektor bo'yicha nomzodlarni beradi, `CatalogRanker`
ularni mijoz so'zlari qamrovi bo'yicha tartiblaydi (raqam aniq, qo'shimcha prefiks bilan), embedding
esa teng holatda hal qiladi. `searchCatalog` tool'i ikkala pipeline'da. Cascade'da natija keyingi
qidiruvgacha annex'da turadi. Docker va CI `pgvector/pgvector` image'iga o'tdi; integration testlar
image'ini foydalanuvchi almashtiradi (docs/RUN.md).

### ✅ B.5. Auth turlari

Hozir: statik header + `{{secrets.KEY}}`. Qo'shiladi: `OAUTH2_CLIENT_CREDENTIALS`
(token cache + refresh, `integration/` dagi `HttpClientUysotOAuthAdapter` andozasi),
`API_KEY_QUERY`. `CrmProvider` uchun yozilgan o'lik `AmoCrmClient`/`Bitrix24Client` (audit
§7.2) — **o'chiriladi**; ular B.2 import + B.5 auth bilan tool sifatida qaytadi, Java
kodi sifatida emas.

**Holat (2026-09-15).** `OAUTH2_CLIENT_CREDENTIALS` qilindi (V14, `docs/api/tools.md` §3.4): `Tool.auth` token
endpoint'ini va client id/secret saqlangan secret **kalitlarini** ushlaydi. Token `agent/dialog/ToolOAuthTokens`
da keshlanadi, muddati tugashidan 60 s oldin yangilanadi, API `401` qaytarsa tashlanadi. `API_KEY_QUERY`
alohida tur qilinmadi: `apiUrl` dagi `?key={{secrets.X}}` bilan allaqachon ishlaydi, import ham shunday
yozadi. CRM konnektorlari (`crm/infrastructure/adapter/AmoCrm…`, `Bitrix24…`) o'lik ekani tekshirilmadi va ular
o'chirilmadi (CLAUDE.md: o'lik kod aytiladi, o'chirilmaydi) — alohida qaror kerak.

### ✅ B.6. Tool kuzatuvi

`McpToolExecution` faqat MCP uchun. Umumiy `tool_execution` (tool, call, args hash,
status, latency, error) HTTP tool'lar uchun ham yoziladi. Bu D bosqich uchun manba
("`create_order` 12% 422 qaytaryapti — model `phone` ni +998siz yuboryapti").

### ✅ B.7. Ovoz → strukturaviy qiymat

Buyurtma va bron'da eng ko'p xato: manzil, sana/vaqt, raqam, ism imlosi. Kodda bor:
`UzbekNumberParser`, `SpokenDates`, `SpelledNumber`. Qo'shiladi: `SpokenAddress`
(Toshkent tumanlari/ko'chalar lug'ati — KB'dan), telefon raqam readback ("to'qson bir,
ikki yuz o'ttiz to'rt..."), harf-harflab ism tasdiqlash. Bu soha emas, umumiy voice UX.

**Holat (2026-09-16).** Qilindi:

- **Vaqt promptda.** `SystemPromptFactory` va `RealtimeSystemPromptFactory` endi `HOZIRGI VAQT` ni ham
  yozadi va `TIME_ANCHORS` bilan kun qismlarini soatga bog'laydi (ertalab 09:00–12:00, tushlik 12:00–13:00,
  tushdan keyin 13:00–17:00, kechqurun 17:00–20:00) — "ertaga tushdan keyin" endi taxmin emas, lug'atdan
  o'qish. Sana ham, vaqt ham `Asia/Tashkent` da hisoblanadi: server UTC'da yurganda kechki qo'ng'iroqqa
  ertangi sana aytilardi.
- **Telefon raqam readback.** `SpeechTextNormalizer` da `+998901234567` 12 xonali bo'lgani uchun *summa*
  qoidasiga tushib, "to'qqiz yuz to'qson sakkiz milliard…" bo'lib o'qilardi. Endi guruh-guruh: "to'qson,
  bir yuz yigirma uch, qirq besh, oltmish yetti" (uz/ru/en). Vergul — atayin: TTS shu yerda pauza qiladi.
- **Soat o'qilishi.** `14:30` → "o'n to'rt o'ttiz" (`SpokenNumbers.clockTime` qayta ishlatildi). Ilgari
  ikki yonma-yon son bo'lib ketardi.
- **Manzil/vaqt readback.** Write tool'ning `readbackTemplate` i paketlarda to'ldirildi: klinikada kun,
  soat, ism; magazinda yetkazib berish manzili va qabul qiluvchi. Ilgari o'qish matni modelning ixtiyorida
  edi.

Qilinmadi (ataylab, foydalanuvchi qarori 2026-09-16): `SpokenAddress` — Toshkent tumanlari/ko'chalar
lug'ati bilan manzilni **to'g'rilash**, va harf-harflab ism tasdiqlash. Lug'at manbasi hali yo'q; bu
alohida ish. Shuningdek `2026-09-17T14:00:00` ko'rinishidagi to'liq ISO vaqt ovozga chiqsa hali ham
xunuk o'qiladi — tool javobidagi `startsAt` ni ovozga chiqarmaslik paket ta'rifi va guardrail bilan
hal qilingan, kodda emas.

---

## 3. Bosqich C — Soha paketlari (Vertical Pack)

`AgentTemplate` enum'i (`BUSINESS/SUPPORT/MEDICAL`) va `AiAgentTemplates` dagi kodda
yozilgan prompt'lar → **ma'lumotga** aylanadi.

### ✅ C.1. Pack — bu bitta JSON

```
VerticalPack {
  key, name, description, industry,
  agent:        AiAgent qismi (prompt, firstMessage, behaviour, voice tavsiyasi)
  scenario:     ScenarioDefinition (stages, factSchema, outcomeSchema[goalField], guardrails, styleExamples)
  tools:        Tool[] (URL'lar {{secrets.*}} bilan, effect/requiresConfirmation to'ldirilgan)
  knowledge:    KB seed (FAQ, siyosatlar) + STRUCTURED_FEED ta'rifi (bo'lsa)
  analysis:     DataExtractionField[], DataEvaluationCriterion[]
  postCall:     PostCallAction[] (SMS matni, webhook)
  conversion:   ConversionGoal (goalKey, oyna)
  testCases:    AgentTestCase[] — kamida 5 persona, shu jumladan "yomon niyatli"
                (chegirma so'raydi, boshqa odam nomidan buyurtma qiladi)
}
```

Endpoint: `GET /api/packs` (katalog), `POST /api/packs/{key}/install` → kompaniyada
agent+ssenariy+tool+KB+test holatlari yaratiladi, secrets bo'sh — foydalanuvchi to'ldiradi.
`POST /api/ai-agents/{id}/export-pack` — teskarisi (o'z agentini pack qilib saqlash;
keyinchalik marketplace).

Feature: `pack/` (hexagonal, `PackUseCase`, `PackRepository` — seed JSON'lar
`resources/packs/*.json`, install — boshqa feature'larning UseCase'lari orqali).

**Holat (2026-09-15).** C.1 va Pack 2 qilindi (`docs/api/packs.md`): `GET /api/packs`,
`POST /api/packs/{key}/install`. Paketlar DB'da emas, build ichidagi JSON'da — shuning uchun port
`PackRepository` emas, `PackCatalogPort` (`ClasspathJsonPackCatalogAdapter`, buzuq fayl ilovani
startda to'xtatadi). Install bitta tranzaksiya: agent (ssenariy agentning o'zida saqlanadi — paketni qayta
o'rnatish mumkin), tool'lar + agentga biriktirish, `catalogUrl` berilsa katalog feed, test holatlari,
konversiya maqsadi (faqat kompaniyada yo'q bo'lsa). Secrets to'ldirilmaydi — javob `missingSecrets` ni
qaytaradi. Qilinmadi: `export-pack` (marketplace bilan birga keladi), `knowledge` va `postCall` bo'limlari —
yetkazib berish narxi, qaytarish shartlari, SMS matni har kompaniyaning o'z faktlari, umumiy matn agentni
yolg'on gapirtiradi. Mijoz raqami `CallContext.phone` dan tool'larga `phone` o'zgaruvchisi bo'lib keladi
(kiruvchida qo'ng'iroq qiluvchi, chiquvchida terilgan raqam) — `create_order` uni `x-source: variable` bilan oladi.

### C.2. Birinchi paketlar (tartib bilan)

| # | Pack                                                | Yo'nalish | Tool'lar                                                      | goalField            | Nega shu tartibda                                    |
|---|-----------------------------------------------------|-----------|---------------------------------------------------------------|----------------------|------------------------------------------------------|
| 1 | Qarz undirish (mavjud)                              | out       | `fetch_debt` (factWebhook), `send_payment_link`               | `promiseToPay`       | A bosqich migratsiyasining o'zi                      |
| 2 ✅ | Internet-magazin buyurtma                        | in+out    | `search_catalog`, `create_order`(WRITE), `check_order_status` | `orderId`            | Foydalanuvchining asosiy misoli; B ni to'liq sinaydi |
| 3 ✅ | Klinika / salon bron                                | in+out    | `list_slots`, `book_slot`(WRITE), `cancel_slot`(WRITE)        | `slotBooked`         | `MEDICAL` shabloni bor; sana/vaqt UX (B.7) sinaladi  |
| 4 ✅ | Restoran yetkazib berish                            | in        | Pack 2 + `menu` feed + manzil UX                              | `orderId`            | Pack 2 ning nusxasi, kam ish                         |
| 5 ✅ | Lead kvalifikatsiya (ta'lim, ko'chmas mulk / Uysot) | out       | `create_lead`(WRITE), `schedule_visit`(WRITE)                 | `leadQualified`      | Uysot OAuth kodi tayyor; CRM integratsiyasi          |
| 6 ✅ | 1-liniya support                                    | in        | KB + `create_ticket`(WRITE) + `transfer`                      | `resolvedOrTicketed` | `SUPPORT` shabloni bor; `unansweredQuestion` signali |
| 7 ✅ | Eslatma / NPS so'rovnoma                            | out       | tool'siz, faqat `outcomeSchema`                               | `answered`           | Eng oddiy; hajm beradi, halqa uchun ma'lumot         |

Har pack "tayyor" hisoblanadi faqat: eval yugurishi 5/5 o'tsa **va** kamida 20 ta real
qo'ng'iroq `CallQualityJudge` dan `guardrailViolation=false` bilan o'tsa.

**Holat (2026-09-16).** 5, 6, 7-paketlar qo'shildi (`docs/api/packs.md`), hammasi faqat JSON:

- **`lead`** — B.5 ni birinchi bo'lib ishlatadi: tool'lar `OAUTH2_CLIENT_CREDENTIALS` bilan, `Authorization`
  sarlavhasi qo'lda yozilmaydi (4 ta secret). `schedule_visit` `leadId` ni `create_lead` javobidan oladi.
- **`support`** — maqsadni HTTP tool emas, **ssenariy tool'i** (`recordResolution`) yozadi: "hal qilindi"
  ikki xil yo'l bilan bo'ladi (javob berildi yoki tiket ochildi) va javob berilgan qo'ng'iroqda hech qanday
  yozuv yaratilmaydi. Ssenariy tool argumenti `GoalOutcome` uchun yaroqli manba (A.1).
- **`nps`** — tool'siz, secrets'siz: `recordSurvey` qo'ng'iroqning o'z natijasiga yozadi, hech qayerga
  so'rov ketmaydi. O'rnatilgach darhol ishlaydi.

Yo'l-yo'lakay `PackAgent.useRag` qo'shildi (`PackMapper` orqali): bilimlar bazasidan qidirish soha
qarori — support va lead uchun shart, `nps` uchun ortiqcha xarajat. Mavjud `ecommerce` va `clinic`
paketlarida ham yoqildi, chunki ularning hujjatlari KB'ga ishora qiladi, ilgari esa agent uni hech qachon
o'qimasdi.

**4-pack (restoran)** ham qo'shildi (2026-09-16, tartib jadvalidan tashqari — foydalanuvchi so'radi).
`ecommerce` bilan bir xil tool shartnomasi, farqi uchta: menyu feed'i 15 daqiqada yangilanadi
(stop-list kun davomida o'zgaradi), manzil podezd va mo'ljal bilan olinadi va `wantedAt` bilan birga
o'qib tasdiqlatiladi, **tarkib/allergiya savoliga taxmin qilib javob berish alohida guardrail bilan
taqiqlangan** — bu sog'liq masalasi, shuning uchun uning o'z test holati bor. Konversiya oynasi 24 soat
(magazinda 168): ovqat o'sha kuni to'lanadi.

### C.3. Inbound + outbound bir xil agent

`callflow` (`AI_AGENT` node) va `campaign` bitta `AiAgent` ga ishora qiladi — bu allaqachon
shunday. Pack ikkalasiga ham o'rnatiladi; farq faqat `firstMessage` (kim qo'ng'iroq qildi).

---

## 4. Bosqich D — O'z-o'zini yaxshilash halqasi

Hozir bo'laklar bor, ulanmagan: judge → metrika (hech kim o'qimaydi); eval → qo'lda;
A/B → qo'lda; memory → faqat keyingi qo'ng'iroqda. Halqa:

```
 real qo'ng'iroq
   │  transkript, outcome, tool_execution, judge verdict, sentiment, transfer, qayta qo'ng'iroq
   ▼
 [D.1] Signal yig'ish  ──►  [D.2] Review inbox (odam belgilaydi)
   │                                      │
   ▼                                      ▼
 [D.3] Kunlik Insight (LLM klasterlaydi) ─┴─►  taklif: KB item / prompt diff / yangi test holati
   │
   ▼
 [D.4] Draft versiya  ──►  eval o'tdi?  ──►  A/B canary 10%  ──►  yaxshi? ──► publish
   │                          │ yo'q                                 │ yo'q
   └──────────────────────────┴─────────── tashlanadi ◄──────────────┘
```

Hech bir qadam odamsiz publish qilmaydi. "O'zi mukammal qiladi" = o'zi **taklif** qiladi,
o'zi **tekshiradi**, o'zi **canary** yuritadi; odam faqat "ha" deydi.

### D.1. Signallar (har qo'ng'iroq)

`CallQualityJudge` default **yoqiladi** (sample 100% dastlab, keyin 20%), natija
`call_quality` jadvaliga yoziladi (hozir faqat metrika). Qo'shimcha, LLM'siz signallar:
`agent/dialog` dan repair soni (mijoz "nima?", "qaytaring"), barge-in soni, `NoInputWatchdog`
ishlaganlar, tool xatolari (B.6), `TRANSFERRED` sababi, 24 soat ichida qayta qo'ng'iroq
(mijoz muammosi hal bo'lmagan), STT ishonch pastligi (`TranscriptListener` confidence).

### D.2. Review inbox

`QualityVerdict.needsReview()` → `call_review` qatori. Operator/supervisor ekrani:
yozuv + transkript + verdict, tugmalar: "to'g'ri", "noto'g'ri: <sabab>", "KB'ga qo'sh",
"test holatiga aylantir". `operator/` wrap-up'ida ham shu tugmalar. Bu odam belgisi —
halqadagi yagona haqiqat manbai; LLM sudyasi unga qarab kalibrlanadi.

**Holat (2026-09-15).** D.1 va D.2 qisman qilindi (V12, `docs/api/call-reviews.md`, yangi `quality/`
feature). Judge standart holatda yoqilgan (`QUALITY_ENABLED=true`, sample 100%). Har AI qo'ng'iroq uchun
`call_quality` qatori yoziladi — judge baholamagan bo'lsa ham, LLM'siz signallar bilan: turn soni, repair
soni (`RepairCounter`, transkriptdan), barge-in soni, `NoInputWatchdog` so'rovlari, STT ishonchi past
gaplar (V13, `DialogTechnicalSnapshot` orqali), tool xatolari (`tool_execution`), yakun (`TRANSFERRED`
shu yerda), 24 soat ichida qayta qo'ng'iroq (`repeat_call` — keyingi qo'ng'iroq belgilaydi). `call_review` alohida jadval
emas, review ustunlari shu qatorda. Inbox — `/api/call-reviews`, `QUALITY_REVIEW_READ/EDIT` (ADMIN,
SUPERVISOR): `AGENT_CORRECT` / `AGENT_WRONG: sabab`, qo'ng'iroq agentiga KB javobi va test holati.
Hali yo'q: `operator/` wrap-up'idagi tugmalar.

### ✅ D.3. Kunlik Insight job

Agent (versiya) bo'yicha oxirgi 24 soat: judge flag'lari + review belgilari + tool xatolari
+ `unansweredQuestion` transkript parchalari → LLM klasterlaydi → `agent_insight` qatori:

| Turi              | Misol                                                        | Taklif                                                      |
|-------------------|--------------------------------------------------------------|-------------------------------------------------------------|
| `KB_GAP`          | "14 qo'ng'iroqda yetkazib berish narxi so'raldi, javob yo'q" | KB item qoralamasi (matn bo'sh, odam to'ldiradi)            |
| `PROMPT_FIX`      | "Agent 9 marta chegirma va'da qildi (guardrail)"             | `rolePrompt`/`guardrails` diff → draft versiya              |
| `TOOL_FIX`        | "`create_order` 422: `phone` formatsiz"                      | tool param `description` diff                               |
| `NEW_TEST_CASE`   | "Bu qo'ng'iroq yiqildi — persona sifatida saqlash"           | transkript → `AgentTestCase` (persona prompt'ni LLM tuzadi) |
| `PRONUNCIATION`   | "STT 6 marta 'Chilonzor'ni 'Chilon zor' deb oldi"            | `PronunciationRule` taklifi                                 |
| `HANDOFF_PATTERN` | "Mijoz 'operator' deganda 3 turn kech uzatildi"              | behaviour sozlamasi taklifi                                 |

Taklif UI'da "qabul / rad" — qabul = draft versiya yoki KB item yaratiladi.

**Holat (2026-09-16).** Yangi `insight/` feature (V15, `docs/api/agent-insights.md`): `agent_insight`
jadvali, `DailyInsightJob` (har kuni 06:20 Asia/Tashkent, `insight.daily` cluster lock),
`/api/agent-insights` inbox'i (`AI_AGENT_READ/EDIT`). Job LLM'ga **transkript bermaydi** —
`AgentCallSignals` (sof domain, testi bor) bir sutkalik `call_quality` qatorlarini sonlarga
yig'adi va faqat sifat sudyasining bir qatorli izohlari bilan supervayzer sabablarini beradi;
klasterlash `InsightClustererPort` orqali (`SpringAiInsightClustererAdapter`), model sozlanmagan
bo'lsa job jim tugaydi. Model javobi ishonchsiz deb qaraladi: noma'lum `type`, bo'sh `title` yoki
oynadagidan katta `callCount` — taklif tashlanadi.

Qabul qilish nima qilishi turiga qarab: `PROMPT_FIX` → qoralamaga bitta guardrail gapi + gate (D.4),
`NEW_TEST_CASE` → test holati yaratiladi, qolganlari (`KB_GAP`, `TOOL_FIX`, `PRONUNCIATION`,
`HANDOFF_PATTERN`) → faqat belgilanadi. Sabab: bu o'zgarishlarning **matnini faqat kompaniya biladi**
(yetkazib berish narxi, tool parametri, talaffuz), LLM yozsa agent yolg'on gapiradi; D.2 dagi
"KB'ga qo'sh" / "test holatiga aylantir" tugmalari o'z joyida qoladi.

### ✅ D.4. Gate: draft → eval → canary → publish

- Draft (D.3 yoki qo'lda) `POST /api/ai-agents/{id}/eval-runs` avtomatik yugadi. Yiqilsa —
  taklif "rad" holatiga, sabab bilan.
- O'tsa — mavjud A/B mexanizmi (`promptOverride` variant) orqali **canary 10%** ga chiqadi,
  minimal N qo'ng'iroq (config, default 50).
- Solishtirish: `goalRate` (ConversionGoal), `guardrailViolation` ulushi, `transfer` ulushi,
  o'rtacha `score`. Ikki proporsiya testi (z-test, sof Java `domain.service`), p<0.05.
- Yaxshi → "publish?" bildirishnomasi (`notification/`), odam bosadi. Yomon yoki
  `guardrailViolation` **oshsa** → canary avtomatik to'xtaydi (rollback mavjud).

**Holat (2026-09-16) — eval gate qilindi, canary qilinmadi.** `InsightEvalGate`: qabul qilingan
`PROMPT_FIX` qoralamaga tushadi va darhol eval run boshlanadi; `AgentEvalFinishedEvent`
(`aiagent/domain/event`) kelganda **hamma holat o'tgan bo'lsa** taklif `ACCEPTED` bo'lib
`AGENT_INSIGHT_READY` bildirishnomasi ketadi, aks holda qo'shilgan gap qoralamadan olib tashlanadi va
taklif `REJECTED` bo'ladi. Shuning uchun taklif "bitta guardrail gapi": rollback shu gapning o'zini
o'chirish bilan bo'ladi va operator shu orada kiritgan boshqa o'zgarishlar saqlanib qoladi. Eval
`AiAgentSettingsUseCase.updateScenario` orqali emas, yangi `AiAgentUseCase.changeScenarioGuardrail`
orqali yoziladi — chunki ssenariy bo'limini butunlay qayta yozish `outcomeSchema`, `tools` va
`styleExamples` ni yo'qotadi (mavjud kamchilik, alohida tuzatiladi).

Canary (10% trafik, z-test, avtomatik to'xtash) **qilinmadi** — foydalanuvchi qarori (2026-09-16):
agent darajasida canary mexanizmi yo'q (A/B faqat kampaniya ichida, `CampaignVariant.promptOverride`,
kiruvchi qo'ng'iroqqa tegmaydi), uni qo'shish qo'ng'iroq yo'lining issiq qismiga tegadi. Hozircha
gate = eval; publish har doim odamning tugmasi.

### D.5. Xotira — sohalar aro

`ClientMemory.facts` allaqachon `outcomeSchema` qiymatlarini saqlaydi. Qo'shiladi:
`DataExtractionField` natijalari ham (`preferredDeliveryAddress`, `allergies`,
`lastOrderItems`) → keyingi qo'ng'iroqda "o'tgan safargi manzilgami?" Bu magazin va klinika
uchun konversiyani eng ko'p oshiradigan arzon narsa. `AiAgentDataPolicy` orqali kompaniya
qaysi maydon eslab qolinishini cheklaydi (retention, audit §7.5).

### D.6. Ko'rsatkichlar

Grafana (`docs/grafana/`) ga agent-versiya kesimida: `goalRate`, `qualityScore` trend,
`guardrailViolationRate`, `toolErrorRate`, `handoffRate`, o'rtacha turn, **muvaffaqiyatli
natija narxi** (`call_billing` / goal). Oxirgisi mijozga ko'rsatiladigan raqam.

---

## 5. Bosqich E — Platforma darajasi

### ✅ E.1. Agent generator (tabiiy tildan pack)

"Men gul do'koniman, API mana: <openapi url>, ish vaqti 9-21, yetkazib berish 30 ming" →
LLM: pack qoralamasi (prompt, stages, outcomeSchema, B.2 import, KB seed, 5 test holati).
Foydalanuvchi ko'rib, install qiladi. Bu C.1 + B.2 ustidagi yupqa qatlam — alohida
feature emas, `pack/` ning `POST /api/packs/generate` endpoint'i.

**Holat (2026-09-16).** `POST /api/packs/generate` va `POST /api/packs/install-draft`
(`docs/api/packs.md`). Mehnat taqsimoti qat'iy: **model faqat suhbatni yozadi** (firstMessage,
bosqichlar, guardrails, `outcomeSchema`, 5 test holati), **tool'lar API hujjatidan** B.2
importeri orqali quriladi (`ToolImportUseCase.findTools` — yangi, saqlamaydigan metod), `secrets`
esa tool'larning o'zidan yig'iladi (`PackSecrets`, sof domain). Model o'ylab topgan endpoint —
hech kim tekshirmagan manzilga ketadigan so'rov, shuning uchun unga URL yozish umuman berilmagan.

Qoralama qaytarilishidan oldin **ssenariy saqlangan ssenariy kabi tekshiriladi**
(`ScenarioValidator`) — ilgari paket ssenariysi hech qayerda tekshirilmasdi (paket ssenariysi
agent ichida saqlanadi, `AiAgentValidator` esa uni ko'rmaydi). Shu tekshiruv `install-draft` da ham
takrorlanadi: qoralama serverda saqlanmaydi, body'dan keladi — o'rnatiladigan narsa odam ko'rgan
narsa bo'lishi uchun. Model sozlanmagan bo'lsa 409, model javobi o'qilmasa 502.

### ✅ E.2. Masshtab — sotishdan oldingi to'siq

Audit §7.4: holat xotirada, jadvalli job'lar 2 instance'da dublikat. Ko'p soha = ko'p
kompaniya = ko'p qo'ng'iroq. D bosqich tugaguncha kamida: ShedLock (barcha `@Scheduled`),
concurrency cap'lar Redis'da atomik (`INCR` + TTL), `CallOutboxService` idempotent.
Media plane'ni ajratish (§7.3) — faqat real yuk ko'rsatsa.

**Holat (2026-09-16) — tekshirildi, kod o'zgarmadi.** Audit yozilgandan beri bular allaqachon
qilingan ekan; 24 ta `@Scheduled`li klass bitta-bitta ko'rildi:

| Qaysi | Nima bilan himoyalangan |
|---|---|
| Global job'lar: dialer, outbox, takroriy kampaniyalar, retention, hisobot jadvali, auto-recharge, auto-callback, katalog yangilash, alerting, kunlik insight | `ClusterJobLock` (`PostgresClusterJobLock`) — ShedLock o'rnini bosadi |
| Qator da'vo qilish: kampaniya nishonlari, outbox, qo'ng'iroq urinishlari | `FOR UPDATE SKIP LOCKED` |
| Webhook qayta urinishlari | `repository.claim(...)` lease bilan |
| To'lov TTL'i | bitta `UPDATE ... WHERE status = PENDING` — idempotent |
| Concurrency cap'lar (platforma / kompaniya / kampaniya) | `DialerState` — Redis `INCR` + TTL, ya'ni atomik va instance'lar aro umumiy |
| Qopqoqsiz qolganlari: ARI event lane'lari, ghost call reaper, navbat dispatcher'i, SSE/live publisher'lar, AMI trunk monitori | Har biri **o'z instance'ining** xotirasidagi sessiyalari ustida ishlaydi — dublikat qiladigan umumiy holat yo'q |

Ataylab xotirada qolgani: `OperatorPresenceService` (o'z javadoc'ida yozilgan qaror — bitta
Asterisk'ga bitta instance). Uni Redis'ga ko'chirish kerak bo'ladigan kun — bitta Asterisk'ga ikkita
instance qo'yilganda; bu deployment qarori, shuning uchun bu yerda qilinmadi.

### ✅ E.3. Compliance — soha bo'yicha

Qarz uchun bor qoidalar (vaqt oynasi, DNC, disclosure) hamma pack'ga tegishli. Qo'shimcha:
klinika — tibbiy ma'lumot `AiAgentDataPolicy` da default eslab qolinmaydi; magazin —
to'lov ma'lumoti ovoz orqali **hech qachon** olinmaydi (guardrail + `PiiRedactor` karta
raqami patterni); hamma — erasure/export API (audit S.7).

**Holat (2026-09-16).** Uchtadan ikkitasi allaqachon bor edi; qilingan ish — klinika siyosati va
bitta qarzga bog'langan qoldiq.

Tekshirildi, hamma pack'ga tegishli va qarzga bog'lanmagan:

| Qoida | Qayerda |
|---|---|
| Terish vaqti oynasi | `DialerService.isWithinDialWindow` — kompaniya oynasi + kampaniya oynasi + bayramlar, **kompaniyaning o'z zonasida**; `CallTaskConsumer` terishdan oldin qayta tekshiradi |
| DNC | `CallTaskConsumer` terishdan oldin `doNotCallUseCase.contains(...)`; `recordDoNotCall` — `PLATFORM_GUARDRAILS` da, ya'ni har bir ssenariyda |
| Disclosure | `DialogPolicy` (ssenariy → kompaniya matni), realtime'da `DisclosureCheck.covered`; `ScenarioValidator` bo'sh bo'lmagan `disclosureText` ni tekshiradi |
| To'lov ma'lumoti ovozda | `PiiRedactor` — karta, CVV, passport, PINFL patternlari transkript yozilishidan **oldin**; magazin va restoran paketlarida alohida guardrail + test holati |
| Erasure / export | `ContactUseCase.exportSubjectData` / `eraseSubjectData` (S.7) — telefon bo'yicha kontakt, xotira va transkriptli qo'ng'iroqlar |

Qo'shildi:

- **`PackAgent.dataPolicy`** — ma'lumot siyosatini paket belgilaydi (`useRag` kabi, `PackMapper` orqali).
  Hammasi yoki hech nima: paket to'rttala savolga javob beradi yoki umuman aralashmaydi.
- **`clinic` paketi:** `zeroPiiRetention: true`, `storeCallAudio: false`, transkript 7 kun. Klinika
  qo'ng'irog'ida mijoz nima uchun shifokorga yozilayotganini aytadi — bu sog'liq ma'lumoti va standart
  holatda saqlanmaydi (mijoz xotirasiga ham tushmaydi). Narxi bor: review inbox'da sudya izohi
  bo'lmaydi — hujjatda ochiq yozilgan.
- **`CallSummary.stripPersonalData(goalField)`** — ilgari u faqat `reasonCode`, `promisedDate`,
  `promisedAmount` ni saqlardi, ya'ni **qarzga bog'langan uchta kalit**. Klinika zero-PII ni yoqsa,
  `slotBooked` ham o'chib ketardi: "shikoyatni saqlamaslik" "qabulni yo'qotish"ga aylanardi. Endi
  ssenariyning maqsad maydoni ham saqlanadi (`CallFinalizer` uni `Scenario` dan oladi) — u ta'rifiga
  ko'ra kod, matn emas.

### E.4. Keyin (bu rejada emas)

Matn kanallari (Telegram, widget chat) bilan bir xil agent; tool marketplace; ko'p
tilli (qoraqalpoq, tojik). Ataylab kiritilmadi — avval ovoz halqasi yopilsin.

**Holat (2026-09-16) — halqa yopilgach, birinchi yo'nalish boshlandi: widget chat
(`docs/api/chat.md`).** Uchtasidan biri; qolgan ikkitasi hali ochiq.

Asosiy ish — **turn mantig'ini transportdan ajratish**: `TextTurnEngine` (`agent/dialog`) endi bitta
agent turnini matndan matnga bajaradi — o'sha prompt prefiksi, o'sha turn annex'i, o'sha bosqichga
bog'langan tool to'plami, o'sha kompaniya REST/MCP tool'lari, o'sha FactGuard. Audio qismi (STT, TTS,
barge-in, jimlik qorovuli, RTP) — yo'q. `AgentSimulator` ham shu dvigatelga o'tkazildi, ya'ni
**eval, chat va qo'ng'iroq bitta turn kodini ishlatadi**: ssenariy sinovda bir xil, matnda boshqacha
o'zini tuta olmaydi.

Ustiga `chat/` feature'i (V16): `POST /api/public/widgets/{key}/chat` va `.../chat/{sessionKey}` —
vidjetning o'z `Origin` ro'yxati va Redis'dagi kunlik kvota bilan himoyalangan, tokensiz. Suhbat
**xotirada turmaydi**: transkript, bosqich va tool natijasi `chat_session`/`chat_message` da, har
xabarda qayta yig'iladi — mehmon bir soatdan keyin yozsa ham, xabari boshqa instance'ga tushsa ham
davom etadi (E.2 ruhida). Ikkita ataylab qilingan farq: chat agenti **har doim `AI_ASSISTANT`**
(yozishma oynasi o'zini tirik odam deb atamasligi kerak) va FactGuard buzilishi **to'xtatilmaydi,
log'ga yoziladi** — ovozdan farqli, javob allaqachon o'qilyapti. Chat transkripti qo'ng'iroq
transkripti bilan bir xil muddat bo'yicha o'chiriladi (`RetentionService`).

**Telegram (2026-09-16, ikkinchi kanal).** V17: `telegram_bot` jadvali (token — kompaniya secret'i,
faqat nomi bilan; webhook secret'i — bizniki) va `chat_session` ga ikki ustun. `POST /api/public/telegram/{botId}`
— Telegram serverlari chaqiradi, `X-Telegram-Bot-Api-Secret-Token` doimiy vaqtda solishtiriladi.
Suhbat mantig'i `ChatConversations` ga ajratildi: vidjet chat'i va bot bitta koddan foydalanadi,
kanal esa faqat "kim yozishi mumkin" va "javob qayerga ketadi" ni hal qiladi. Bitta Telegram chat'iga
bitta ochiq suhbat — qisman unikal indeks bilan; suhbat tugagan bo'lsa yangisi ochiladi (botga yozgan
odamga "yangi sessiya boshlang" deb bo'lmaydi). Endpoint deyarli har doim 200 qaytaradi: Telegram 2xx
olmagan update'ni qayta yuboradi, ya'ni bir daqiqalik nosozlik soatlab takror xabarga aylanardi.

**Panel: suhbatlar ekrani (2026-09-16).** `POST /api/chats/list` va `GET /api/chats/{id}`
(`ChatHistoryUseCase`) — vidjet va Telegram suhbatlari bitta ro'yxatda, agent / kanal / yakunlangan /
sana bo'yicha filtr bilan; tafsilotda ssenariy natijasi (`outcome`) va to'liq transkript. Faqat
o'qish, huquq — qo'ng'iroq transkriptlari bilan bir xil `REPORT_READ`. Migratsiya kerak bo'lmadi:
`idx_chat_session_company` (V16) ro'yxatni qoplaydi. `sessionKey` panelga chiqmaydi — u mehmonning
yozish kaliti.

Qilinmadi: chat uchun post-call xulosa/CRM/xotira/billing, **tool marketplace** va **ko'p tillilik**
(u provayder qo'llab-quvvatlashiga bog'liq — kod masalasi emas).

---

## 6. Tartib va tekshiruv mezonlari

| Tartib | Bosqich                                         | "Tayyor" mezoni (foydalanuvchi tekshiradi)                                                                   |
|--------|-------------------------------------------------|--------------------------------------------------------------------------------------------------------------|
| 1 ✅   | A.1–A.4 yadroni ajratish                        | Qarz regressiya qo'ng'irog'i va eval yugurishi o'zgarmaydi; `goal: true` maydonli ssenariy `GOAL_REACHED` bilan tugaydi (`PROMISE_TO_PAY` qoladi — A.1 qarori) |
| 2 ✅   | B.1, B.3, B.6 (ichma-ich body, tasdiq, kuzatuv) | Sinov magazin API'siga (mock) ovoz orqali 2 pozitsiyali buyurtma, `tool_execution` da 1 ta `create_order`    |
| 3 ✅   | B.4 + pgvector (katalog)                        | 5000 qatorli CSV katalogdan "qora 42 krossovka" → to'g'ri SKU top-1 da, javob < 300 ms                       |
| 4 ✅   | C.1 + Pack 2 (magazin)                          | `POST /api/packs/ecommerce/install` → secrets to'ldirib, 20 real sinov qo'ng'irog'i, judge violation 0       |
| 5 ✅   | D.1–D.2 (signal + review inbox)                 | Har qo'ng'iroqda `call_quality` qatori; review inbox'da needsReview qo'ng'iroqlar                            |
| 6 ✅   | B.2 OpenAPI import, B.5 auth                    | Bitrix24 yoki WooCommerce spec'idan 3 tool import, biri ishlaydi                                             |
| 7 ✅   | Pack 3 (klinika) + B.7 (sana/manzil UX)         | "ertaga tushdan keyin" → to'g'ri slot; manzil readback                                                       |
| 8 ✅   | D.3–D.4 (insight + gate)                        | Ataylab buzilgan prompt bilan 30 qo'ng'iroq → ertasi `PROMPT_FIX` taklifi, draft eval'da yiqiladi            |
| 9 ✅   | Pack 5, 6, 7                                    | Har biri C.2 mezoni bo'yicha                                                                                 |
| 10 ✅  | E.1 generator, E.2 masshtab                     | 2 instance'da dublikat SMS/qo'ng'iroq yo'q                                                                   |

### Nima qilinMAYdi

- Yangi STT/TTS/realtime engine (audit: 6/6/5 bor, muzlatilgan).
- Har soha uchun Java kod (`RestaurantService`, `ClinicBookingService`) — soha faqat pack.
- Avto-publish. Halqa taklif qiladi, odam tasdiqlaydi.
- `agent/` ni hexagonal qilish (CLAUDE.md §2 istisno).
- Matn kanallari — E.4.

### Ochiq savollar (taxmin qilinmaydi, so'raladi)

1. ~~A.1 da `PROMISE_TO_PAY` ni hisobot/CRM'da saqlab qolish kerakmi?~~ Hal qilindi
   (2026-09-15): saqlanadi, yangi qiymatlar qo'shimcha — A.1 "Qaror".
2. Birinchi real magazin mijozi qaysi platformada (WooCommerce, o'z API, 1C)? B.2 ni shunga
   qarab sinash kerak.
3. ~~Review inbox'ni kim yuritadi?~~ Hal qilindi (2026-09-15): kompaniya supervayzeri —
   `QUALITY_REVIEW_READ` / `QUALITY_REVIEW_EDIT`, ADMIN va SUPERVISOR rollarida.
