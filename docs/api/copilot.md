# Copilot API

`uz.murodjon.robotcallv2.copilot` · huquq: **AI_AGENT_READ** yoki **REPORT_READ**

Panelning AI yordamchisi. Foydalanuvchi oddiy tilda so'raydi ("oxirgi haftada nega transferlar
ko'paydi?", "shu qo'ng'iroqda agent nega summani aytdi?", "agentga transfer qoidasini qo'sh"),
copilot esa platformaning [MCP server tool'lari](mcp-server.md) orqali qo'ng'iroqlar, transkriptlar,
agentlar va eval natijalarini o'qib javob beradi.

**Qoidalar:**
- **Faqat qoralama.** Copilot har doim drafts-only rejimda ishlaydi — foydalanuvchida
  `AI_AGENT_EDIT` bo'lsa ham. U yaratgan agent o'chiq holda boshlanadi, `enabled` ni o'zgartirmaydi,
  qo'ng'iroqlari qoralamada ishlayotgan agentni tahrirlamaydi, **publish qila olmaydi**. Publish —
  panelda odam tomonidan.
- **Huquqlar foydalanuvchiniki.** Copilot faqat so'rayotgan foydalanuvchining huquqlari ochgan
  tool'larni ko'radi: `REPORT_READ` siz — qo'ng'iroqlarni o'qiy olmaydi, `AI_AGENT_EDIT`/
  `AGENT_DRAFT_EDIT` siz — qoralamaga yoza olmaydi.
- **Faktlar faqat tool javobidan;** dalil sifatida qo'ng'iroq ID'lari keltiriladi, telefon raqamlari
  maskalanadi. Javob — foydalanuvchi yozgan tilda.
- **Suhbat foydalanuvchiniki.** Har bir suhbat uni boshlagan foydalanuvchiga tegishli; boshqasiniki
  `404`. API kalit bilan ishlamaydi (`403 NO_USER_SESSION`) — tashqi agentlar uchun [MCP server](mcp-server.md).
- **To'lov.** Har javobning tokenlari (barcha tool raundlari bilan) balansdan yechiladi — ledger'da
  `AI_CHARGE`, `copilot-charge:<messageId>`. Balans bo'lmasa — `409 COPILOT_NO_BALANCE`.
- Bitta savolda ko'pi bilan `voice-agent.copilot.max-tool-calls` (standart 15) tool chaqiruvi; undan
  keyin model bor ma'lumot bilan javob berishga majbur qilinadi. Tool javobi 12 000 belgidan uzun
  bo'lsa kesiladi.

Sozlamalar (`voice-agent.copilot.*`): `model` (standart `gemini-3.8-flash`), `max-tokens` (8192),
`max-tool-calls` (15), `max-history` (20 — yangi savol bilan birga yuboriladigan oldingi savol-javoblar).

---

## `POST /api/copilot/ask` — savol berish

Javob model tugatgandagina qaytadi (tool raundlari bilan o'nlab soniya bo'lishi mumkin — frontend
timeout'ini shunga moslang).

**Request body** (`AskCopilotRequest`):

```json
{ "sessionId": null, "message": "Oxirgi 7 kunda qaysi agentda transferlar ko'paydi va nega?" }
```

| Maydon | Turi | Majburiy | Izoh |
|---|---|---|---|
| `sessionId` | `number` \| `null` | ❌ | Davom ettiriladigan suhbat; `null` — yangi suhbat (sarlavhasi — shu savol) |
| `message` | `string` (≤4000) | ✅ | Savol |

**Response** (`CopilotReplyResponse`):

```json
{
  "accept": true,
  "data": {
    "sessionId": 12,
    "reply": {
      "id": 341, "sessionId": 12, "role": "ASSISTANT",
      "content": "Sotuv agentida (ID 4) transferlar 9% dan 21% ga oshgan. Sabab: ... (qo'ng'iroqlar 8812, 8830, 8847)",
      "toolName": null, "toolArguments": null, "toolError": false,
      "promptTokens": 18420, "completionTokens": 610, "chargedUzs": 380,
      "createdAt": "2026-10-09T08:00:12Z"
    },
    "toolCalls": [
      {
        "id": 339, "sessionId": 12, "role": "TOOL",
        "content": "{\"data\":[...]}",
        "toolName": "list_calls",
        "toolArguments": "{\"disposition\":\"TRANSFERRED\",\"dateFrom\":\"2026-10-02T00:00:00Z\"}",
        "toolError": false, "promptTokens": 0, "completionTokens": 0, "chargedUzs": 0,
        "createdAt": "2026-10-09T08:00:04Z"
      }
    ]
  },
  "message": null,
  "messageCode": null,
  "errors": null
}
```

`toolCalls` — copilot shu javob uchun chaqirgan tool'lar, tartib bilan. `toolError: true` — tool rad
etgan (huquq yo'q, noto'g'ri argument, topilmadi); `content` da xato kodi va matni, model buni o'qib
foydalanuvchiga aytadi.

**Xatolar:**

| Status | `messageCode` | Qachon |
|---|---|---|
| 400 | `VALIDATION_FAILED` | `message` bo'sh yoki 4000 belgidan uzun |
| 403 | `NO_USER_SESSION` | API kalit bilan so'raldi |
| 404 | `COPILOT_SESSION_NOT_FOUND` | `sessionId` yo'q yoki boshqa foydalanuvchiniki |
| 409 | `COPILOT_NO_BALANCE` | Balans yetarli emas |
| 409 | `COPILOT_NOT_CONFIGURED` | Serverda chat modeli sozlanmagan |
| 502 | `COPILOT_REPLY_FAILED` | Model javob bermadi (savol va bajarilgan tool'lar baribir saqlanadi) |

---

## `POST /api/copilot/sessions/filter` — mening suhbatlarim

Body — `CopilotSessionFilter` (`page`/`size`/`orders`). Saralanadigan ustunlar: `UPDATED_AT`,
`CREATED_AT`. Standart: `UPDATED_AT DESC`. Faqat so'rayotgan foydalanuvchining suhbatlari.

Javob — `PageableData<CopilotSession>`:

```json
{ "id": 12, "companyId": 1, "userId": 9, "title": "Oxirgi 7 kunda qaysi agentda transferlar ko'paydi va nega?",
  "createdAt": "2026-10-09T08:00:00Z", "updatedAt": "2026-10-09T08:03:40Z" }
```

---

## `GET /api/copilot/sessions/{sessionId}/messages` — suhbat to'liq

Javob — `List<CopilotMessage>`, eskisidan yangisiga: `USER` savollar, `TOOL` chaqiruvlar va
`ASSISTANT` javoblar (yuqoridagi shakl). `404 COPILOT_SESSION_NOT_FOUND` — boshqa foydalanuvchiniki.

---

## `DELETE /api/copilot/sessions/{sessionId}` — suhbatni o'chirish

Suhbat va uning barcha xabarlari o'chiriladi. Billing ledger'dagi yozuvlar qoladi.

---

## `POST /api/copilot/prompt/rewrite` — prompt'ni AI bilan yozish/yaxshilash

Prompt muharrirlaridagi AI tugmasi uchun. Hech narsa saqlanmaydi va suhbat tarixiga yozilmaydi —
faqat yangi matn qaytadi, uni qo'llash/bekor qilishni foydalanuvchi o'zi hal qiladi. Tool'larsiz,
bitta model chaqiruvi.

**Request body** (`RewritePromptRequest`):

```json
{ "prompt": "Mijozga qarz haqida eslat. {{client_name}} ga salom ber.", "instruction": "Qisqaroq va muloyimroq qil" }
```

| Maydon | Turi | Majburiy | Izoh |
|---|---|---|---|
| `prompt` | `string` (≤20000) | ❌ | Hozirgi prompt; bo'sh bo'lsa — `instruction` bo'yicha yangisi yoziladi |
| `instruction` | `string` (≤2000) | ❌ | Nima o'zgarsin; bo'sh bo'lsa — prompt umumiy yaxshilanadi |

Ikkalasi ham bo'sh bo'lsa — `400 PROMPT_REWRITE_EMPTY`. `{{o'zgaruvchi}}` lar o'zgarishsiz qoladi,
til — prompt tili (prompt bo'lmasa — instruction tili).

**Response** (`RewritePromptResponse`):

```json
{ "accept": true, "data": { "prompt": "..." }, "message": null, "messageCode": null, "errors": null }
```

**Xatolar:** `400 PROMPT_REWRITE_EMPTY`, `400 VALIDATION_FAILED` (uzunlik), `409 COPILOT_NO_BALANCE`,
`409 COPILOT_NOT_CONFIGURED`, `502 COPILOT_REPLY_FAILED`. Tokenlar hozircha balansdan yechilmaydi
(faqat balans borligi tekshiriladi).
