# Chat API — `/api/v1/chats`

`uz.murodjon.robotcallv2.chat` · huquq: **CHAT_AGENT_EDIT** (API kalitga ham beriladi)

Kompaniyaning **o'z ilovasi yoki sayti** ichidagi chat agent (Retell'dagi `create-chat` /
`create-chat-completion`). Kompaniya serveri API kalit bilan suhbat ochadi, mijozning har bir
xabarini yuboradi va agent javobini oladi. Suhbat sayt vidjeti va Telegram'dagi bilan **bir xil**:
o'sha ssenariy, tool'lar, FactGuard, kvota, billing, tahlil — va panelda `/api/chats` ro'yxatida
`channel: "API"` bilan ko'rinadi ([chat.md](chat.md)).

Agent `channel` i `CHAT` yoki `OMNICHANNEL` bo'lishi shart ([ai-agents.md](ai-agents.md)).
Kalit sozlamasi: panelda API kalit yaratib, unga `CHAT_AGENT_EDIT` scope'ini bering ([api-keys.md](api-keys.md)).

| Metod | URL | Tavsif |
|---|---|---|
| `POST` | `/api/v1/chats` | Suhbat ochish, agentning birinchi gapi |
| `POST` | `/api/v1/chats/{sessionKey}/messages` | Mijozning bitta xabari → agent javobi |
| `POST` | `/api/v1/chats/{sessionKey}/end` | Suhbatni tugatish |

---

## Suhbat ochish

```json
POST /api/v1/chats
X-API-Key: <kalit>

{ "agentId": 42, "language": "uz-UZ",
  "facts": { "customer_name": "Aziz", "phone": "+998901234567", "order_id": "A-1043" } }
```

| Maydon | Turi | Majburiy | Izoh |
|---|---|---|---|
| `agentId` | `number` | ✅ | Kompaniyaning `CHAT`/`OMNICHANNEL` agenti |
| `language` | `string` | ❌ | `uz`, `ru`, `uz-UZ` ...; berilmasa — agent tili (mijoz xotirasida til bo'lsa — o'sha) |
| `facts` | `object` (≤50 kalit) | ❌ | Ilova mijoz haqida biladigan narsalar; agent ularni qo'ng'iroq faktlari kabi o'qiydi (`{{customer_name}}`). `phone` berilsa — mijoz xotirasi va CRM bog'lanadi |

Javob — `StartChatResponse` (vidjet bilan bir xil):

```json
{ "accept": true,
  "data": { "sessionKey": "cht_4d1f8a92b6c30e57a1b2c3d4", "greeting": "Assalomu alaykum, Aziz aka! ...",
            "language": "uz-UZ", "maxMessages": 40 },
  "message": null, "messageCode": null, "errors": null }
```

`sessionKey` — keyingi so'rovlar shu bilan. Uni mijoz brauzeriga emas, o'z serveringizda saqlang.

## Xabar yuborish

```json
POST /api/v1/chats/cht_4d1f.../messages
{ "text": "Buyurtmam qachon keladi?" }
```

Javob — `ChatReplyResponse`: `{ "reply": "...", "ended": false, "messagesLeft": 38, "operatorActive": false }`.
`operatorActive: true` — panelda xodim suhbatni o'z qo'liga olgan, `reply` `null`; xodim javoblari panel
orqali yoziladi (API'da ularni olish — keyingi bosqich).

## Tugatish

`POST /api/v1/chats/{sessionKey}/end` — `{ "reply": null, "ended": true, "messagesLeft": 0, ... }`.
Tugagan suhbat bir daqiqa ichida tahlil qilinadi (`GET /api/chats/{id}` → `analysis`) va
`CHAT_ENDED` / `CHAT_ANALYZED` webhook'lari yuboriladi ([ai-agents.md](ai-agents.md), webhook hodisalari).
`end` chaqirilmasa ham suhbat `CHAT_SESSION_TTL_MINUTES` jimlikdan keyin yopiladi.

## Xatolar

| Status | `messageCode` | Qachon |
|---|---|---|
| 400 | `VALIDATION_FAILED` | `agentId` yo'q, `text` bo'sh yoki 2000 belgidan uzun, `facts` 50 tadan ko'p |
| 400 | `AI_AGENT_NOT_FOR_CHAT` | Agent `channel: VOICE` |
| 403 | — | Kalitda `CHAT_AGENT_EDIT` yo'q, yoki kompaniyada chat agent funksiyasi o'chiq |
| 404 | `AI_AGENT_NOT_FOUND` | Agent yo'q yoki boshqa kompaniyaniki |
| 404 | `CHAT_SESSION_NOT_FOUND` | `sessionKey` yo'q, boshqa kompaniyaniki yoki API suhbati emas |
| 409 | `CHAT_NO_BALANCE` | Balans yetarli emas |
| 409 | `CHAT_DAILY_LIMIT_REACHED` | Agentning kunlik chat kvotasi tugadi |
| 409 | `CHAT_SESSION_FULL` / `CHAT_SESSION_EXPIRED` | Xabarlar chegarasi tugadi / suhbat jim turib yopildi |
| 409 | `CHAT_MODEL_NOT_CONFIGURED` | Serverda chat modeli sozlanmagan |
| 502 | `CHAT_NO_REPLY` | Model javob bermadi |
