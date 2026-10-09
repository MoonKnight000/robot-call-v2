# Platformaning MCP serveri

`uz.murodjon.robotcallv2.mcp` · `POST /api/mcp-server` · huquq: har bir tool o'zinikini tekshiradi

Claude Code, Cursor yoki boshqa istalgan MCP klient platformani **API kalit** bilan boshqaradi:
agent yaratadi, test holatini qo'shadi, evalni ishga tushirib natijasini o'qiydi, qo'ng'iroqlar
va transkriptlarni ko'radi, agentni paket JSON'i sifatida olib yana o'rnatadi.

**[mcp.md](mcp.md) dan farqi:** u yerda bizning agentimiz **boshqalarning** MCP serverlariga
ulanadi (mijoz). Bu yerda platformaning **o'zi** MCP server.

---

## 1. Ulanish

Transport — **Streamable HTTP**, stateless: sessiya (`Mcp-Session-Id`) yo'q, serverdan klientga
oqim yo'q (`GET` taklif qilinmaydi). Har bir xabar `X-Api-Key` bilan keladi va alohida javob oladi.
Protokol versiyalari: `2025-06-18` (standart), `2025-03-26`, `2024-11-05`.

Claude Code:

```bash
claude mcp add --transport http robot-call https://HOST/api/mcp-server \
  --header "X-Api-Key: rc_live_..."
```

Cursor (`.cursor/mcp.json`):

```json
{
  "mcpServers": {
    "robot-call": {
      "url": "https://HOST/api/mcp-server",
      "headers": { "X-Api-Key": "rc_live_..." }
    }
  }
}
```

Kalit panelda yaratiladi ([api-keys.md](api-keys.md)). Klient faqat kalit huquqlari ochgan
tool'larni ko'radi.

---

## 2. Tool'lar

| Tool | Huquq (birortasi yetarli) | Nima qiladi |
|---|---|---|
| `list_agents` | `AI_AGENT_READ` | Agentlar ro'yxati (`search`, `page`, `size`) |
| `get_agent` | `AI_AGENT_READ` | Agentning qoralamasi yoki `version` |
| `create_agent` | `AI_AGENT_EDIT`, `AGENT_DRAFT_EDIT` | Yangi agent (`POST /api/ai-agents` maydonlari) |
| `update_agent_draft` | `AI_AGENT_EDIT`, `AGENT_DRAFT_EDIT` | Qoralamani o'zgartirish; berilmagan maydon o'zgarmaydi |
| `list_test_cases` | `AI_AGENT_READ` | Eval test holatlari |
| `create_test_case` | `AI_AGENT_EDIT`, `AGENT_DRAFT_EDIT` | Test holati (`toolMocks`, `expectTools` bilan) |
| `run_evaluation` | `AI_AGENT_EDIT`, `AGENT_DRAFT_EDIT`, `AGENT_EVAL_RUN` | Evalni boshlash (`version`, `mode`, `repeat` 1–10) — darhol `runId` qaytaradi |
| `get_evaluation_run` | `AI_AGENT_READ` | Run holati, holatlar bo'yicha `passRate`, har urinish transkripti va muammolari |
| `export_agent` | `AI_AGENT_READ` | Agent paket JSON'i sifatida ([packs.md](packs.md#get-apipacksagentsagentidexport--agentni-paketga-aylantirish)) |
| `import_agent_pack` | `AI_AGENT_EDIT`, `AGENT_DRAFT_EDIT` | Paketni yangi agent sifatida o'rnatish (`install-draft` bilan bir xil) |
| `list_calls` | `REPORT_READ` | Qo'ng'iroqlar (`q`, `campaignId`, `disposition`, `dateFrom`, `dateTo`, `page`, `size`) |
| `get_call` | `REPORT_READ` | Bitta qo'ng'iroq: transkript, ajratilgan ma'lumot, sifat bahosi |

Har bir tool REST API chaqiradigan o'sha use case'ni chaqiradi, shuning uchun validatsiya, audit
va xatolar bir xil. Argumentlar **qat'iy** o'qiladi: noma'lum maydon (`systemPromt`) jimgina
tashlanmaydi, rad etiladi.

Odatdagi oqim (server `initialize` javobida modelga ham aytadi):
`create_agent` → `create_test_case` → `run_evaluation` → `get_evaluation_run` (`status` `RUNNING`
bo'lmaguncha) → muammolar bo'yicha `update_agent_draft` → yana eval. **Publish — panelda, odam qiladi.**

---

## 3. `AGENT_DRAFT_EDIT` — faqat qoralama

Bu huquq kalitga beriladi va **production'ga tegmasdan** agent qurish imkonini beradi.
Qo'ng'iroqlar e'lon qilingan versiyada ishlaydi, shuning uchun qoralamani o'zgartirish odatda hech
kimga eshitilmaydi. Qoralamaning o'zida jonli bo'lgan ikki narsa kalitga yopiq:

- **`enabled` almashtirgichi.** Kalit yaratgan agent (`create_agent`, `import_agent_pack`) **o'chiq**
  holda yaratiladi. `update_agent_draft` dagi `enabled` e'tiborga olinmaydi.
- **Hech qachon publish qilinmagan, yoqilgan agent** qo'ng'iroqlarni qoralamada qabul qiladi.
  Uni bu huquq bilan o'zgartirish mumkin emas — `MCP_AGENT_LIVE_ON_DRAFT`.

Publish, rollout, rollback, o'chirish va agentni yoqish — faqat `AI_AGENT_EDIT` (panel). REST
endpoint'lar bu huquqni qabul qilmaydi: u faqat MCP server orqali ishlaydi.

`AI_AGENT_EDIT` bilan ulangan klientga bu cheklovlar qo'llanmaydi.

---

## 4. Javoblar va xatolar

Javob — JSON-RPC konverti, **`ResponseData` emas**. Notification'ga (`id` siz xabar) `202`, tanasiz.

Tool natijasi — bitta `text` content, ichida natijaning JSON'i:

```json
{"jsonrpc":"2.0","id":5,"result":{
  "content":[{"type":"text","text":"{\"id\":31,\"status\":\"RUNNING\",\"total\":6,\"repeat\":3,…}"}],
  "isError":false}}
```

Rad etilgan chaqiruv JSON-RPC xatosi emas, balki `isError: true` natija — model uni o'qib, tuzata
oladi. Matn `ErrorCode` bilan boshlanadi, keyin xabar va (bo'lsa) muammolar ro'yxati:

```text
MCP_TOOL_ARGUMENTS_INVALID: arguments of MCP tool 'create_test_case' are invalid
- personaPrompt must not be blank
```

| Holat | Javob |
|---|---|
| Kalitda tool huquqi yo'q | `isError`, `MCP_TOOL_FORBIDDEN` |
| Argument noto'g'ri yoki noma'lum maydon | `isError`, `MCP_TOOL_ARGUMENTS_INVALID` |
| Qoralamada jonli agentni drafts-only kalit o'zgartirmoqchi | `isError`, `MCP_AGENT_LIVE_ON_DRAFT` |
| Use case xatosi (`AI_AGENT_NOT_FOUND`, `AGENT_EVAL_ALREADY_RUNNING`, …) | `isError`, o'sha `ErrorCode` |
| Buzuq JSON | JSON-RPC `-32700` |
| Massiv (batch) yoki `jsonrpc`/`method` yo'q | JSON-RPC `-32600` |
| Noma'lum metod (`resources/list`, …) | JSON-RPC `-32601` |
| Noma'lum tool | JSON-RPC `-32602` |
| Kalit yo'q yoki yaroqsiz | HTTP `401`, tanasiz |
