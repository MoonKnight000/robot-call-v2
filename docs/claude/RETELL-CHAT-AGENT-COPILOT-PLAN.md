# Reja: Chat Agent (Retell uslubida) + AI Assistant (copilot) kengaytmasi

Sana: 2026-11-04. Holat: **reja**, kod yozilmagan. Har bosqich foydalanuvchi "keyingi bosqich" degandan
keyin boshlanadi (CLAUDE.md §8.7); build/test'ni foydalanuvchi ishga tushiradi (§8.6).

---

## 0. Hozirgi holat (kod o'qib aniqlandi)

### Chat
- `AiAgentChannel { VOICE, CHAT, OMNICHANNEL }` enum'i bor, lekin amalda deyarli ishlatilmaydi: faqat
  agent klonlashda (`AiAgentService` ~552-qator) va yaratishda default sifatida. Panelda "Chat agent yaratish"
  oqimi yo'q, CHAT agentdan ovoz/STT/TTS maydonlari yashirilmaydi, validatsiya ham farq qilmaydi.
- Matnli suhbat dvigateli tayyor: `ChatConversations` + `TextTurnEngine` (eval va qo'ng'iroq bilan bir xil turn),
  kanallar `ChatChannel { WIDGET, TELEGRAM }`, operator takeover, Redis kvota, `/api/chats` ro'yxati.
- `docs/api/chat.md` "Hozircha yo'q" bo'limi: chat uchun **post-chat tahlil va billing yo'q** —
  `CallFinalizer` chat'ga ulanmagan, `outcome/disposition` faqat `chat_session` da.
- Tashqi API orqali chat (Retell'dagi `create-chat` / `create-chat-completion`), SMS-chat, chat webhook
  hodisalari, chat inaktivlik sozlamalari agent darajasida — yo'q.

### AI Assistant (copilot)
- `copilot` feature: `ask` + `askStream` (SSE), sessiyalar, `prompt/rewrite`, drafts-only, billing.
- Tool'lar — `McpServerToolCatalog` dagi **13 ta** MCP tool: `list_agents`, `get_agent`, `create_agent`,
  `update_agent_draft`, test case / eval (4), `export_agent`, `import_agent_pack`, `list_calls`, `get_call`.
- **Yo'q:** campaign (umuman), scenario, ovoz/voice katalogi, knowledge base, tool (REST/MCP), inbound route,
  telefon raqam/SIP trunk, call flow, agentni publish oldidan diff, va eng muhimi — **agregat analitika**.
- Tahlil muammosi: copilot qo'ng'iroqlarni faqat `list_calls` (sahifasi ≤100) + `get_call` bilan ko'radi.
  "Nega konversiya tushdi?" kabi savolga u 20–100 ta qo'ng'iroqdan taxmin qiladi — statistik jihatdan noto'g'ri.
  `reports` (`dashboard/summary`, `kpi`, `timeseries`, `outcomes-distribution`, `stage-funnel`, `call-topics`,
  `campaign-comparison`, `hourly-heatmap`), `agent-insights`, `scorecards`, `call-reanalyses` allaqachon bor —
  copilot ularni ko'rmaydi.
- `AskCopilotRequest` da **sahifa konteksti yo'q** (foydalanuvchi qaysi agent/kampaniya/qo'ng'iroq sahifasida
  turgani) — Retell assistenti esa ochiq turgan agentni biladi.

---

## A. Chat Agent — Retell'dagi "Chat Agent" turi

Maqsad: panelda agent yaratishda **Voice agent / Chat agent** tanlovi; chat agent — ovozsiz, o'z sozlamalari,
o'z kanallari, o'z tahlili va billing'i bilan. Domen qoidasiga mos: bu **AI Agent** savoli (kim, qaysi kanalda),
Scenario o'zgarmaydi — bitta ssenariy ovozli va chat agentda qayta ishlatiladi.

| # | Bosqich | Nima qilinadi | Tekshiruv mezoni |
|---|---|---|---|
| A1 | Kanal semantikasi | `channel=CHAT` agent: ovoz/STT/TTS/ambient/voicemail/barge-in maydonlari majburiy emas va javobda e'tiborsiz. `AiAgentValidator` da kanalga qarab qoida: CHAT agentni SIP trunk / inbound route / kampaniyaga biriktirib bo'lmaydi (`ValidationException`, yangi `ErrorCode`), VOICE agentni widget-chat / Telegram'ga biriktirib bo'lmaydi; OMNICHANNEL — ikkalasi. `POST /api/ai-agents` `channel` ni qabul qiladi (bor), filtr `channel` bo'yicha. | Validator unit testi: CHAT agent + kampaniya → 400; VOICE + Telegram bot → 400 |
| A2 | Chat-specific sozlamalar | `AiAgent` ga (builder orqali, §9) `chatSettings`: `inactivityTimeoutMinutes` (hozir global `CHAT_SESSION_TTL_MINUTES`), `maxMessages`, `endChatMessage`, `allowMarkdown`, `quickReplies` (tayyor tugmalar), `autoCloseMessage`. Global `voice-agent.chat.*` — default bo'lib qoladi. Flyway migratsiya. | Agentga `inactivityTimeoutMinutes=5` qo'yib widget chatda 5 daqiqa jim turish → suhbat yopiladi |
| A3 | Post-chat tahlil | Chat tugaganda (`ended` yoki TTL) `ChatFinalizer`: `dataNeeded`/`outcomeSchema` bo'yicha ajratish, `summary`, `sentiment`, `chatSuccessful` (Retell `chat_analysis`), disposition. Qo'ng'iroqdagi summary kodi (`agent/summary`) qayta ishlatiladi, transkript manbai — `chat_message`. Natija `chat_session` ga. | `ChatFinalizerTest` (mock model): tugagan chatdan `outcome` va `summary` yoziladi |
| A4 | Chat billing | Har javob tokenlari balansdan (`CallBillingUseCase` ga `chargeChatReply`, ledger `chat-charge:<messageId>`), copilot'dagi kabi. Balans yo'q → suhbat ochilmaydi (`409`). | Ledger'da `chat-charge:*` yozuvlari; balans 0 da widget chat ochilmaydi |
| A5 | Chat webhook'lari | `AgentWebhookEvent` ga `CHAT_STARTED`, `CHAT_ENDED`, `CHAT_ANALYZED`; post-call webhook bilan bir xil yuboruvchi port. | webhook.site ga uchala hodisa keladi |
| A6 | Tashqi Chat API (API kalit bilan) | Retell `create-chat` / `create-chat-completion` analogi: `POST /api/v1/chats` (agentId, metadata, dynamic variables) → `chatId`; `POST /api/v1/chats/{id}/messages` → javob; `POST /api/v1/chats/{id}/end`. Mobil ilova / o'z sayti uchun. Ichida o'sha `ChatConversations`, yangi kanal `ChatChannel.API`. | `chat.http`: suhbat ochish → 3 xabar → end → `CHAT_ENDED` webhook |
| A7 | SMS-chat (ixtiyoriy) | `ChatChannel.SMS`: kiruvchi SMS (`sms` feature, Eskiz) → chat agent javobi SMS bilan. Eskiz kiruvchi SMS webhook berishi tasdiqlanishi kerak — **foydalanuvchidan so'raladi**. | Raqamga SMS yozib javob olish |
| A8 | Chat test (studiya) | Mavjud `POST /api/ai-agents/{id}/test-chat` CHAT agent uchun asosiy test bo'ladi; eval `mode=TEXT` CHAT agentda default. Chat simulyatsiyasi `create_test_case` bilan ishlaydi (bor). | CHAT agentda eval yurishi |
| A9 | Chat tarixi ekrani | `/api/chats` filtri: `agentId`, `channel`, `chatSuccessful`, `sentiment`, sana; detail'da tahlil maydonlari. | Filtr bilan ro'yxat |

API o'zgaradi → har bosqichda `docs/api/ai-agents.md`, `docs/api/chat.md`, yangi `docs/api/chat-api.md`
(A6) va `README.md` jadvali; javob oxirida "Frontend'ga ta'siri".

---

## B. AI Assistant — agent, campaign va boshqa sozlamalarni to'liq qila olishi

Tamoyil o'zgarmaydi: **drafts-only**, publish va kampaniyani ishga tushirish — odam tomonidan; huquqlar —
foydalanuvchiniki. Tool'lar `McpServerToolCatalog` ga qo'shiladi (copilot va tashqi MCP mijozlari ikkalasi oladi).
Katalog 365 qatordan oshib ketadi — feature bo'yicha `*ToolSet` klasslarga bo'linadi (`AgentToolSet`,
`CampaignToolSet`, `AnalyticsToolSet`, …), har biri faqat o'z feature'ining `UseCase` ini chaqiradi (§4).

| # | Bosqich | Tool'lar | Xavfsizlik / qoida |
|---|---|---|---|
| B1 | Sahifa konteksti | `AskCopilotRequest` ga `context { page, agentId?, campaignId?, callId?, scenarioId? }`; system prompt'ga "foydalanuvchi hozir agent #12 sahifasida" qo'shiladi. | Kontekstdagi ID ham `companyId` bilan tekshiriladi (tool orqali o'qiladi, ishonilmaydi) |
| B2 | Campaign | `list_campaigns`, `get_campaign`, `create_campaign_draft`, `update_campaign_draft` (dial oynasi, kunlar, `maxAttempts`, `RetryPolicy`, concurrency, kunlik limit, budjet, `sequence`, `callerIdPoolId`, `aiAgentId`), `add_campaign_targets` (CSV matn yoki kontakt filtri), `list_campaign_variants`/`create_campaign_variant` | Kampaniya faqat `DRAFT` holatda yaratiladi/o'zgaradi; `start/resume` tool **yo'q** — "Ishga tushirish" tugmasi odamda. Yangi huquq: `CAMPAIGN_EDIT` bo'lmasa tool ko'rinmaydi |
| B3 | Scenario | `list_scenarios` (built-in shablonlar bilan), `get_scenario`, `create_scenario_draft`, `update_scenario_stage` (bitta `StageDef`), `add_global_node`, `validate_scenario` (`ScenarioValidator` natijasi), `attach_scenario_to_agent` | Yangi versiya sifatida yoziladi (`scenarioKey`+`version`), eski versiya buzilmaydi |
| B4 | Agentni to'liq sozlash | `list_voices` (+ preview URL), `list_llm_models`, `update_agent_draft` ga qolgan maydonlar: STT/TTS engine, `callBehaviour`, talaffuz qoidalari, `dataNeeded`, post-call webhook, `fastLlmModel`; `diff_agent_draft` (qoralama vs published) | Publish yo'q, faqat diff ko'rsatiladi |
| B5 | Bilim va integratsiya | `list_knowledge_bases`, `attach_knowledge_base`, `add_kb_text`; `list_tools`, `create_rest_tool_draft`, `attach_tool_to_agent`; `list_inbound_routes`, `list_phone_numbers` (faqat o'qish) | Secret/API kalit qiymatini copilot ko'rmaydi va yozmaydi — faqat `secretId` ga havola |
| B6 | Yozishdan oldin tasdiq | Har yozuvchi tool'dan oldin copilot "reja" qaytaradi (`proposedChanges` — qaysi obyekt, before → after); foydalanuvchi panelda "Qo'llash"ni bossa bajariladi. SSE'da yangi `proposal` hodisasi, `POST /api/copilot/proposals/{id}/apply|reject`. | Hozirgi "darhol qoralamaga yozish" o'rniga — xato tahrirni oldini oladi (Retell assistentidagi "Apply" tugmasi) |
| B7 | "Bir gapdan to'liq setup" | Copilot prompt'iga **SETUP WIZARD** bo'limi: "Uysot qarzdorlariga ertaga 9–18 da 3 urinish bilan eslatma qo'ng'irog'i" → scenario (shablondan) → agent → campaign draft → targetlar → eval → yakuniy checklist ("publish qiling, kampaniyani ishga tushiring"). Bitta `proposal` paketida. | Eval natijasi `passRate` < chegarada bo'lsa copilot ogohlantiradi |

---

## C. AI Assistant — qo'ng'iroqlarni to'g'ri tahlil qilish

Asosiy o'zgarish: **sanash va foiz SQL'da, LLM'da emas.** Copilot raqamlarni agregat tool'dan oladi, alohida
qo'ng'iroqlarni faqat misol/dalil uchun ochadi.

| # | Bosqich | Nima qilinadi | Tekshiruv |
|---|---|---|---|
| C1 | Agregat tool'lar | Mavjud `report` use case'lari ustidan: `get_call_stats` (davr, agent, kampaniya, disposition bo'yicha soni/foiz/o'rtacha davomiylik/narx), `compare_periods` (ikki davr, farq va o'zgarish %), `get_outcome_distribution`, `get_stage_funnel` (qaysi bosqichda tushib qolishyapti), `get_call_topics`, `get_hourly_heatmap`, `compare_campaigns`. Hammasi `REPORT_READ`. | "Oxirgi 7 kun vs oldingi 7 kun transfer %" — panel dashboardi bilan bir xil raqam |
| C2 | Namuna olish to'g'ri | `list_calls` ga filtrlar: `agentId`, `stage`, `outcomeField=value`, `sentiment`, `minDuration/maxDuration`, `hasToolError`, `qualityVerdict`, `sample=random` (tasodifiy namuna, faqat eng yangisi emas). `get_call` ga `fields` (faqat transkript / faqat extracted) — token tejash. | Tasodifiy namuna har safar boshqacha, filtr to'g'ri |
| C3 | Sifat va insight | `list_agent_insights`, `get_scorecard_summary` (eng ko'p buzilgan mezonlar), `get_call_trace` (bloklar bo'yicha iz: STT, LLM, tool, latency — "nega agent sekin javob berdi") | Bitta qo'ng'iroqda latency sababini trace'dan aytadi |
| C4 | Tahlil metodikasi (prompt) | `copilot.txt` ga **ANALYSIS** bo'limi: 1) avval agregat raqam, 2) farqni topish (davr/agent/bosqich), 3) faqat keyin 5–10 misol qo'ng'iroq, 4) javobda: raqam + manba tool + misol ID'lari + ishonch darajasi; namuna kichik bo'lsa (n<30) "xulosa taxminiy" deyish; hech qachon misollardan foiz chiqarmaslik. | Golden savollar to'plami (pastda) |
| C5 | Ko'p qo'ng'iroqni ko'rib chiqish | `analyze_calls_batch`: N ta (≤200) qo'ng'iroq transkriptini fon job'da arzon model bilan bitta savol bo'yicha belgilaydi ("mijoz narx haqida e'tiroz bildirdimi?"), natija — sanoq + misol ID'lar. Mavjud `call-reanalyses` infratuzilmasi ustiga, narx oldindan ko'rsatiladi (`preview`). | 100 qo'ng'iroqda e'tiroz ulushi; narx preview bilan mos |
| C6 | Tahlildan harakatga | Tahlil oxirida konkret taklif: "Stage `objection` ga X qoidasini qo'shish" → B6 `proposal` sifatida (scenario/agent qoralamasi) + shu muammo uchun `create_test_case`. | Taklif qo'llangach eval yuradi |
| C7 | Chat tahlili | A3 dan keyin xuddi shu tool'lar chat sessiyalari uchun (`list_chats`, `get_chat`, `get_chat_stats`). | — |

### Copilot sifatini o'lchash (C4 uchun)
`src/test/resources/copilot-golden/` — 15–20 ta savol + kutilgan tool ketma-ketligi va raqam (mock tool
javoblari bilan, Spring'siz unit test): "transfer nega ko'paydi", "qaysi bosqichda yo'qotyapmiz",
"kampaniya 7 ning kechagi natijasi", "agent 4 summani o'zidan aytdimi". Test tekshiradi: agregat tool
chaqirildimi, javobdagi raqam tool javobidagi bilan bir xilmi.

---

## Tavsiya etilgan tartib

1. **C1 + C4** — eng katta foyda, eng kam risk (faqat o'qish tool'lari, mavjud report kodi).
2. **B1 + B2** — kontekst va campaign (foydalanuvchining asosiy talabi).
3. **B6** — tasdiq oqimi (yozuvchi tool'lar ko'paygani sari zarur).
4. **A1 → A3 → A4** — chat agentning yadrosi (tur, tahlil, billing).
5. **B3, B4, B5, B7** — to'liq setup.
6. **C2, C3, C5, C6** — chuqur tahlil.
7. **A5, A6, A8, A9**, keyin **A7** (SMS — tasdiqdan keyin).

## Ochiq savollar

1. Chat agent — **alohida agent turi** (Retell kabi: ovozli agentdan butunlay ajralgan) mi, yoki hozirgi
   OMNICHANNEL (bitta agent ikkala kanalda) asosiy bo'lib, CHAT faqat "ovozsiz" variant bo'ladimi?
   Reja ikkalasini qo'llaydi, lekin panel UX'i shunga bog'liq.
2. Copilot yozishdan oldin har safar tasdiq so'rasinmi (B6), yoki qoralamaga darhol yozib keyin diff
   ko'rsatish (hozirgi uslub) yetarlimi?
3. Copilot kampaniyani **ishga tushira olmasligi** to'g'rimi (reja shunday), yoki tasdiq bilan ruxsat berilsinmi?
4. Eskiz kiruvchi SMS'ni webhook bilan beradimi (A7 uchun)?
5. `analyze_calls_batch` (C5) narxi kimga yoziladi va bir martada maksimum nechta qo'ng'iroq?

---

## Bajarilish holati (2026-11-04, branch `claude/practical-cannon-fj34hk`)

Build/test Claude tomonidan ishga tushirilmagan (§8.6) — har bosqichning tekshiruvini foydalanuvchi bajaradi.

| Bosqich | Holat | Commit mazmuni | Tekshiruv |
|---|---|---|---|
| C1 + C4 | ✅ | 9 ta sanalgan analitika tool'i (`get_call_stats`, `compare_periods`, `get_call_timeseries`, `get_stage_funnel`, `get_call_topics`, `get_hourly_heatmap`, `compare_campaigns`, `get_call_trace`, `list_agent_insights`) — `CallAnalyticsMcpToolSet`; tool'lar endi `McpToolSet` bean'larida e'lon qilinadi; `copilot.txt` ga CALL ANALYSIS | `CallPeriodComparisonTest`, `CopilotInstructionsTest`; copilot'dan "oxirgi 7 kun vs oldingi 7 kun transfer ulushi" — raqam dashboard bilan bir xil |
| B1 | ✅ | `AskCopilotRequest.context` (`page`, `agentId`, `campaignId`, `callId`, `scenarioId`) | `CopilotInstructionsTest`; agent sahifasidan "shu agentni yaxshila" |
| B2 | ✅ | `CampaignMcpToolSet`: list/get, `create_campaign_draft`, qisman `update_campaign_draft`, CSV preview/import, A/B variant; faqat DRAFT (`MCP_CAMPAIGN_NOT_DRAFT`), ishga tushirish tool'i yo'q; `copilot.txt` ga CAMPAIGN SETUP | `CampaignMcpToolSetTest`; "ertaga 9–18, 3 urinish, shu CSV" → DRAFT kampaniya |
| A1 | ✅ | `channel`: CHAT agent kampaniya/variant/sequence/inbound route'ga biriktirilmaydi (`AI_AGENT_NOT_FOR_CALLS`), VOICE agent Telegram botga (`AI_AGENT_NOT_FOR_CHAT`) | `AiAgentChannelTest`, `CampaignServiceTest` |
| A4 | ✅ | Chat turn tokenlari balansdan (`chat-charge:<id>:<n>`), balanssiz chat ochilmaydi (`CHAT_NO_BALANCE`) | `ChatConversationsTest`; ledger'da `chat-charge:*` |
| A3 | ✅ | `ChatAnalysisJob` + V33: summary, sentiment, needsFollowUp, outcome → `GET /api/chats/{id}` `analysis` | `ChatAnalysisJobTest`; widget chatni tugatib 1 daqiqadan keyin detail'da `analysis` |
| B3, B4, B5, B7 | ✅ | `ScenarioMcpToolSet` (versiyalab saqlash), `AgentResourcesMcpToolSet` (ovoz, model, publish diff, bilim, REST tool, inbound); prompt: SCENARIOS, FULL SETUP, FROM ANALYSIS TO ACTION | `AgentDraftDiffTest`; "shu agentga 'siz kimsiz' javobini qo'sh" |
| B6 | ✅ | Yozuvchi tool — `PENDING` taklif (V34), `POST /api/copilot/proposals/{id}/apply|reject`, `applyChanges`, `NOTE`; `annotations.readOnlyHint` | `CopilotServiceTest`; panelda "Qo'llash" |
| Doira | ✅ | Copilot faqat platforma bo'yicha (kod/matematika rad etiladi); `PROMPT_REWRITE_OUT_OF_SCOPE` | "Java'da saralash yoz" → rad |
| C2, C3, C5, C6, C7 | ✅ | `list_calls`: `aiAgentId`, `sentiment`, `randomSample`; `ask_calls` (≤50 qo'ng'iroq, billing); `list_chats`/`get_chat`; prompt 3a | `CallReportRepositoryAdapterTest` |
| A2, A5, A6, A8, A9 | ✅ | `chatSettings` (V35), `CHAT_*` webhook'lar, `/api/v1/chats` (API kalit `CHAT_AGENT_EDIT`), CHAT agent faqat TEXT eval, chat filtrlari (`sentiment`, `needsFollowUp`, `API` kanali) | `AgentChatSettingsTest`, `ChatAnalysisJobTest` |
| A7 (SMS) | ⏸ | Eskiz kiruvchi SMS webhook berishi tasdiqlanmagan | — |
| Frontend | ✅ | `robot-call-front`, shu nomli branch: copilot takliflari/kontekst/tugma, agent kanali tanlovi, chat sozlamalari, chat tahlili va filtrlari, agent ro'yxatlari kanal bo'yicha, xato matnlari. `tsc -b`, `vitest` (55), `vite build` o'tdi | — |

**Ochiq savollarga olingan standart javoblar** (foydalanuvchi o'zgartirishi mumkin): chat agent — Retell kabi
alohida tur, OMNICHANNEL ikkalasini ham qiladi; copilot kampaniyani ishga tushira olmaydi; tasdiq oqimi (B6)
qilindi — standart holatda taklif, panelda "O'zgarishlarni darhol qo'llash" tugmasi bilan darhol.
