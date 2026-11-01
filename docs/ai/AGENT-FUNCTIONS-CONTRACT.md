# Agent "Funksiyalar" — Retell bilan bir xil model (kontrakt)

Sana: 2026-11-01. Maqsad: agent muharriridagi Functions bo'limi Retell'dagi bilan bir xil ishlaydi —
ro'yxatda har bir funksiya alohida qator (tur ikonkasi + nom + ✎ + 🗑), "+ Qo'shish" menyusi Retell tartibida,
har bir tur o'z sozlama oynasi bilan. Backend va frontend shu kontraktga qarab yoziladi; `docs/api/ai-agents.md`
yakuniy manba bo'ladi.

## 1. API

`AiAgentRow.functions: AgentFunction[]` — har doim to'ldirilgan (saqlangan qiymat `null` bo'lsa backend
eski maydonlardan standart ro'yxatni yasab qaytaradi, pastda §3).

`UpdateAiAgentRequest.functions: AgentFunction[] | null` — `null` = o'zgartirmaslik; `[]` = funksiyasiz agent
(Retell'dagidek: `END_CALL` ham yo'q bo'lsa, agent o'zi go'shak qo'ymaydi). Ro'yxat to'liq almashtiriladi.

```jsonc
// AgentFunction
{
  "id": "fn_1a2b",            // mijoz yasagan barqaror kalit, ^[A-Za-z0-9_-]{1,40}$, ro'yxatda unikal
  "type": "END_CALL",         // AgentFunctionType, pastda
  "name": "endCall",          // modelga ko'rinadigan nom (qoidasi §2)
  "description": "…",         // ≤1000, null = standart tavsif; modelga shu beriladi
  "speakDuringExecution": false,   // "Talk while waiting"
  "executionMessage": null,        // kutish paytida aytiladigan gap (≤300), null = standart filler

  // faqat o'z turi uchun to'ldiriladi, qolganlari null:
  "transfer":      { "destination": "+998901234567", "mode": "COLD" },          // TRANSFER_CALL; destination: telefon / SIP URI / {{var}}; mode COLD|WARM
  "agentTransfer": { "agentId": 42, "keepVoice": true, "keepLanguage": true },  // AGENT_TRANSFER (kompaniyaning boshqa agenti)
  "pressDigit":    { "pauseDetectionDelayMs": 1000 },                           // PRESS_DIGIT; null = standart; 0..5000
  "sms":           { "contentMode": "STATIC", "content": "…{{customer_name}}…",
                     "sendTo": "CALLER", "number": null, "speakAfter": true },   // SEND_SMS; contentMode PROMPT|STATIC; sendTo CALLER|NUMBER
  "extract":       { "variables": [ { "name": "budget", "type": "NUMBER",
                                      "description": "…", "choices": [] } ] },  // EXTRACT_VARIABLE; type STRING|NUMBER|BOOLEAN|ENUM (ENUM → choices)
  "toolId": 17,                    // CUSTOM — kompaniyaning REST tool'i (studio "Tools")
  "integrationTool": "createLead"  // INTEGRATION — GET /api/settings/integrations/tools qatoridagi `toolName ?? tool`
}
```

`AgentFunctionType`: `END_CALL`, `TRANSFER_CALL`, `AGENT_TRANSFER`, `PRESS_DIGIT`, `SEND_SMS`,
`EXTRACT_VARIABLE`, `CUSTOM`, `INTEGRATION`. (`CODE` — Retell'da bor, bizda hozircha yo'q: frontend menyuda
"tez orada" deb o'chirilgan holda ko'rsatadi, backend qabul qilmaydi.)

## 2. Nom qoidasi
- Tizim turlari model nomi o'zgarmaydi (platforma promptlari shu nomlarga tayanadi): `END_CALL` → `endCall`,
  `TRANSFER_CALL` → `requestHumanTransfer`, `PRESS_DIGIT` → `sendDtmfTones`, `SEND_SMS` → `sendMidCallSms`,
  `AGENT_TRANSFER` → `transferToAgent`. So'rovdagi `name` e'tiborga olinmaydi, javobda shu nom qaytadi.
  Har biri ro'yxatda ko'pi bilan bir marta.
- `EXTRACT_VARIABLE` — nom foydalanuvchiniki, `^[a-zA-Z][a-zA-Z0-9_]{0,63}$`, unikal, tizim nomlari bilan to'qnashmaydi.
- `CUSTOM` — nom tool'ning o'zidan (sanitize qilingan), so'rovdagi e'tiborga olinmaydi. `INTEGRATION` — katalogdagi nom.
- `description` hamma turda modelga beriladigan tavsifni almashtiradi (`DescribedToolCallback` uslubida).

## 3. Eski maydonlar bilan moslik
- Saqlangan `functions` null bo'lgan (eski) agent uchun backend ro'yxatni yasaydi: `END_CALL`; transfer manzili
  (`transferPhoneNumber`/`transferQueueId`) yoki global operator bo'lsa `TRANSFER_CALL`; `pressDigitEnabled` →
  `PRESS_DIGIT`; `midCallSmsEnabled` → `SEND_SMS` (content = `midCallSmsTemplate`); biriktirilgan tool'lar → `CUSTOM`;
  kompaniyaning yoqilgan integratsiya tool'lari (`disabledIntegrationTools` dan tashqari) → `INTEGRATION`.
  Shu bilan eski agentlarning qo'ng'iroqdagi xatti-harakati o'zgarmaydi.
- `functions` saqlangandan keyin runtime faqat shu ro'yxatga qaraydi. `CUSTOM` ro'yxati agentning biriktirilgan
  tool'lari bilan sinxronlanadi (mavjud attach mexanizmi orqali).
- `TRANSFER_CALL` yo'q → `requestHumanTransfer` taklif qilinmaydi (5-bosqichdagi `@no-transfer` promptlari ishlaydi).
  Bor, lekin `destination` bo'sh → transfer manzili avvalgi tartibda (bosqich raqami → navbat → global operator).

## 4. Xato kodlari (400)
`AI_AGENT_FUNCTION_INVALID` (umumiy: tur/maydon noto'g'ri, args: function id + sabab kodi),
`AI_AGENT_FUNCTION_DUPLICATE` (id yoki nom takrori, tizim turi ikki marta),
`AI_AGENT_FUNCTION_TOOL_NOT_FOUND` (`toolId` yoki `agentTransfer.agentId` kompaniyada yo'q — 404 emas, 400).
