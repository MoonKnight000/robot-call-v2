# Retell vs Nido — Functions va Knowledge Base farqi, tuzatish rejasi

Sana: 2026-11-01. Solishtirilgan agentlar (ikkalasi ham single-prompt):
- Retell: "Eman Materials Lead Qualification" — functions: `end_call`; KB: "First";
  ulangan integratsiya: Salesforce (OAuth), agentga hali biriktirilmagan.
- Nido: agent #13 "Erkin suhbat (PROMPT rejimi)" — functions: Qo'ng'iroqni yakunlash,
  Operatorga ulash (+ kompaniya HTTP tool'lari); KB: kompaniyaning umumiy manbalari.

## 1. Retell'da nima bor (dashboard'dan ko'rilgan)

**Function turlari (Add menyusi):** End Call · Call Transfer · Agent Transfer ·
Press Digit (IVR) · In-Call SMS · Extract Dynamic Variable · Code · Custom Function ·
Connected Integrations (Salesforce: Search/Get/Create/Update Contact, Lead, Account,
Opportunity, Case… — har bir CRM amali alohida function).

**Har bir function oynasi:**
- Name, Description (`{{user_number}}` kabi o'zgaruvchi bilan, "Edit with AI").
- Input — har bir parametr alohida: **Description** rejimi ("let AI decide", model to'ldiradi)
  yoki **Value** rejimi (qat'iy qiymat / `{{var}}`), "Run a test".
- Output — "Fields for agent context" (javobning qaysi maydonlari modelga boradi) +
  har bir maydon uchun **"Save as variable"** (keyingi function/promptda `{{var}}`).
- Execution mode: **Wait for completion** (model natijani kutadi va SHU natija bilan javob beradi)
  + "Talk while waiting" (to'ldiruvchi gap) + "Play typing sound"; yoki **Run in background (async)**.

**KB:** chunks to retrieve (1–10, default 3), **Similarity threshold** (0.60),
**Knowledge Base Instruction** (qidiruv uchun fokus, `{{var}}` bilan), contact memory.
Retrieval har turnda avtomatik (tool emas).

## 2. Bizda nima bor (kod bo'yicha) va asosiy farqlar

| # | Farq | Bizda hozir | Ta'siri |
|---|------|-------------|---------|
| F1 | **"Wait for completion" yo'q (cascade)** | LLM bir turnda bir marta chaqiriladi; tool javobni gapirib bo'lgandan KEYIN ishlaydi (`LlmTurnClient.streamTurn`). Muvaffaqiyatli natija modelga faqat keyingi turnda annex'dagi 120 belgilik "ALREADY ESTABLISHED" qatori orqali yetadi. | Agent CRM/HTTP natijasini aytolmaydi yoki to'qiydi. **Eng katta farq.** |
| F2 | Tool'ning o'z `preToolSpeech` i ishlamaydi | Cascade filler faqat scenario `ToolDef.preToolSpeech` ni o'qiydi; HTTP tool'ning `speech.preToolSpeech` i faqat model matn yozmasa va tool tugagandan keyin aytiladi. `disableInterruptions` hech qayerda o'qilmaydi. | "Talk while waiting" amalda yo'q. |
| F3 | KB qidiruv so'rovi — faqat oxirgi gap | `TurnRunner.retrieveKnowledge` mijozning bitta oxirgi gapini embed qiladi; kontekst yo'q; threshold (0.62) hardcoded; "KB instruction" yo'q. Realtime'da esa KB — tool, boshqa format (900 vs 1200 belgi), stage manbalarini e'tiborsiz qoldiradi. | "Shuning narxi qancha?" kabi savollarda noto'g'ri parcha. |
| F4 | Hardcoded bank javoblari | `KnowledgeBaseLookup` 4 ta qarz/bank javobini (Click/Payme, MIB, restrukturizatsiya, filial vaqti) HAR kompaniyada, `useRag` siz ham, LLM'ni chetlab aytadi. | Eman/Uysot mijozi "filial qachon ochiq" desa bank javobi. |
| F5 | URL manbalar yangilanmaydi | `KnowledgeSource.isDueForRefresh` faqat STRUCTURED_FEED/SITEMAP uchun true. | Sayt o'zgarsa KB eskiradi. |
| F6 | Single-prompt'ga FSM/qarz qoldiqlari | Bitta bosqichda ham `transitionTo` taklif qilinadi (istalgan nomni qabul qiladi), salomlashuvda "call transitionTo", annex'da "(final state)" va qarz misoli; realtime promptda `getFact` (aslida `getCallFact`), "100% IN UZBEK" qat'iy. | Model chalg'iydi, ruscha agent buziladi. |
| F7 | Realtime'da HTTP va MCP tool'lar yo'q | `RealtimeToolRunner.buildTools` `findAgentToolDefinitions` va MCP'ni chaqirmaydi. | Realtime agent funksiyalarini jimgina yo'qotadi. |
| F8 | Built-in function'lar sozlanmaydi | `requestHumanTransfer` raqam/navbat bo'lmasa ham taklif qilinadi (keyin shunchaki go'shak qo'yadi); DTMF single-prompt'da mavjud emas; CRM/integratsiya tool'lari kompaniya bo'yicha hammaga, agent o'chira olmaydi. | Retell'dagi "Add → type" modeli yo'q. |
| F9 | Function output → o'zgaruvchi | `responseMapping` (≈ "Fields for agent context") va `recordFindings` (outcome var) bor, lekin keyingi tool/promptda `{{var}}` sifatida aniq ishlatilishi va UI'da "Save as variable" yo'q; FE parametrlarni xom JSON sifatida beradi (backend'da `valueType` LLM_PROMPT/qat'iy allaqachon bor). | Retell'dagi Input/Output UX. |

## 3. Bosqichlar

Har bosqich alohida; keyingisiga foydalanuvchi "keyingi bosqich" degandan keyin o'tiladi.

### 1-bosqich — Cascade'da "Wait for completion" (F1, F2)
- `LlmTurnClient.streamTurn` va `blockingTurn`: model **ma'lumot qaytaradigan tool** chaqirsa
  (`DialogTools.HARDCODED_TOOL_NAMES` va `transitionTo` dan tashqari har qanday tool — HTTP, MCP,
  CRM, integratsiya, catalog, scenario tool'lari), tool'lar ishlagach **follow-up LLM round**:
  shu messages + model allaqachon aytgan matn (AssistantMessage) +
  `[SYSTEM: Tool result: …] Answer the caller using these results; do not repeat what you already said.`
  Natija matn sifatida beriladi (native tool-call replay emas — Gemini `thought_signature` muammosi saqlanadi).
- Follow-up'da tool'lar yana taklif qilinadi (zanjir: search_contact → create_lead), lekin
  hop soni cheklangan (`dialog.yml`: `tool-result-rounds: 2`); oxirgi round tool'siz.
- Follow-up matni stream qilib gapiriladi; `TurnResult.text` = birinchi + follow-up matni (history uchun).
- "Talk while waiting": `maybeSpeakPreToolSpeech` HTTP tool'ning `speech.preToolSpeech` ini ham o'qiydi
  (model o'zi matn yozgan bo'lsa filler aytilmaydi — ikki marta gapirmaslik uchun).
- Prompt annex: data tool chaqirganda faqat qisqa kutish gapi aytilsin yoki hech narsa — javob natija kelgach.
- **Tekshiruv:** test-chat/simulyatsiyada HTTP tool'li agent — "buyurtmam qayerda?" → bot natijadagi
  statusni o'sha turnda aytadi; logda `tool result round` qatori; tool'siz turnlar latency o'zgarmaydi.

### 2-bosqich — Knowledge Base sifati (F3, F4, F5)
- Qidiruv so'rovi: oxirgi 2–3 turn (mijoz + bot) birlashtiriladi, uzunligi cheklanadi.
- `AiAgent`: `ragMinScore` (similarity threshold, default 0.60) va `ragInstruction` (KB instruction —
  so'rovga qo'shiladigan fokus matni, `{{var}}` bilan). Flyway migratsiya + DTO + `docs/api/ai-agents.md`.
- Realtime `searchKnowledgeBase` cascade bilan bir xil top-k / threshold / passage uzunligi / stage manbalari.
- `KnowledgeBaseLookup` dagi hardcoded bank javoblari faqat qarz ssenariysida (yoki butunlay o'chiriladi —
  foydalanuvchi qaror qiladi); `useRag=false` bo'lsa KnowledgeItem shortcut ham ishlamaydi.
- URL manbalar `refreshMinutes` bo'yicha yangilanadi.
- **Tekshiruv:** bir xil savol to'plami Retell va Nido'da; "filial ish vaqti" bank javobini qaytarmaydi.

### 3-bosqich — Single-prompt tozalash (F6)
- Bitta bosqichli (PROMPT) ssenariyda `transitionTo` taklif qilinmaydi, salomlashuv/annex/realtime prompt
  matnlaridagi transitionTo, "(final state)", qarz misoli olib tashlanadi.
- Realtime: `getFact` → `getCallFact`; "100% IN UZBEK" agent tiliga qarab.
- Transfer: guardrail matnidagi "haqoratda darhol transfer" ↔ tool tavsifi ziddiyati bartaraf etiladi.

### 4-bosqich — Realtime paritet (F7)
- `RealtimeToolRunner.buildTools` ga agent HTTP tool'lari va MCP tool'lari; xato matni umumiy
  (exception xabari modelga chiqmaydi).

### 5-bosqich — Retell uslubidagi function modeli (F8, F9) — API o'zgaradi
- Built-in'lar agentda aniq yoqiladi: Transfer (o'z raqami/navbati bilan; konfiguratsiyasiz taklif qilinmaydi),
  Press Digit (`dtmf` flag), In-Call SMS (bor), End Call (doim).
- CRM/integratsiya tool'lari agent bo'yicha tanlanadi (default — kompaniya sozlamasi).
- Tool: execution mode (`waitForResult` bor) + "typing sound" (ambience'dan), output'da "save as variable" —
  keyingi tool shablonlari va prompt `{{var}}` ida ishlatiladi.
- Bu bosqich frontend'ga ta'sir qiladi (Funksiyalar menyusi, tool modal Input/Output) — alohida so'rov.
