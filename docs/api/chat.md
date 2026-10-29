# Matn kanali (widget chat) — `/api/public/widgets/{key}/chat`

Bir xil agent, endi **matnda** (UNIVERSAL-OPERATOR-PLAN E.4). Saytdagi vidjet mehmonni ovozli
qo'ng'iroqqa ulaydi; shu vidjetning chat'i esa **o'sha agent bilan** yozib gaplashtiradi: bir xil
ssenariy, bir xil bosqichlar, bir xil guardrail'lar, bir xil tool'lar (kompaniyaning REST tool'lari
va MCP tool'lari ham) va bir xil FactGuard.

Bu "ikkinchi agent" emas. Chat turni — `TextTurnEngine`, ya'ni **eval yugurishi va qo'ng'iroq
ishlatadigan turnning o'zi**, faqat audio qismi yo'q (STT, TTS, barge-in, jimlik qorovuli, RTP).
Shuning uchun magazin o'z buyurtma ssenariysini tuzatsa, u ikkala kanalda ham bir vaqtda tuzaladi.

## Endpointlar

Ikkalasi ham **tokensiz** — vidjetning boshqa ikki endpoint'i kabi. Guvohnoma — `Origin` sarlavhasi.

| Metod | URL | Tavsif |
|---|---|---|
| `POST` | `/api/public/widgets/{widgetKey}/chat?language=` | Suhbat ochadi, agentning birinchi gapini qaytaradi |
| `POST` | `/api/public/widgets/{widgetKey}/chat/{sessionKey}` | Bitta xabarga javob |

---

## Suhbat ochish

```
POST /api/public/widgets/wgt_9f2c.../chat?language=uz-UZ
Origin: https://shop.example.uz
```

```json
{
  "accept": true,
  "data": {
    "sessionKey": "cht_4d1f8a92b6c30e57a1b2c3d4",
    "greeting": "Assalomu alaykum! Buyurtma bermoqchimisiz yoki buyurtmangiz holatini bilmoqchimisiz?",
    "language": "uz-UZ",
    "maxMessages": 40
  },
  "message": null, "messageCode": null, "errors": null
}
```

- `sessionKey` — brauzer saqlaydigan yagona narsa. Baza id'si hech qachon tashqariga chiqmaydi:
  taxmin qilinadigan handle — bu begona odamning suhbati.
- `greeting` — agentning o'z `firstMessage` i. Agentda u bo'lmasa, agent **bitta turn olib**
  ssenariysi bo'yicha o'zi salomlashadi (qo'ng'iroqdagi salomlashuv turni bilan bir xil).
- `language` — berilmasa agentning tili.

## Xabar yuborish

```json
{ "text": "Ikkita lavash, Yunusobod 12-kvartal" }
```

```json
{
  "accept": true,
  "data": { "reply": "Ikkita lavash yozdim. Manzilingizni to'liq ayting: uy, xonadon, podezd?",
            "ended": false, "messagesLeft": 36 },
  "message": null, "messageCode": null, "errors": null
}
```

`ended: true` bo'lsa vidjet kiritish maydonini yopadi: agent suhbatni yakunladi (`endCall`) yoki
xabarlar chegarasi tugadi.

**Xatolar**

| Status | `messageCode` | Qachon |
|---|---|---|
| `404` | `AGENT_WIDGET_NOT_FOUND` | Bunday vidjet yo'q, o'chirilgan yoki `Origin` ro'yxatda emas |
| `404` | `CHAT_SESSION_NOT_FOUND` | Bunday suhbat yo'q — yoki u boshqa vidjetning agentiniki |
| `409` | `CHAT_SESSION_ENDED` | Suhbat yakunlangan |
| `409` | `CHAT_SESSION_FULL` | Xabarlar chegarasi (standart 40) tugadi |
| `409` | `CHAT_SESSION_EXPIRED` | Suhbat 120 daqiqadan ko'p jim turdi |
| `409` | `CHAT_DAILY_LIMIT_REACHED` | Agent bugungi chat kvotasini tugatdi |
| `409` | `CHAT_MODEL_NOT_CONFIGURED` | Serverda chat model sozlanmagan |
| `502` | `CHAT_NO_REPLY` | Model javob bermadi |

---

## Qanday ishlaydi

**Xotirada hech narsa turmaydi.** Qo'ng'iroq kanal ochiq turgan vaqtida xotirada saqlaydigan
narsani chat **bazada** saqlaydi: transkript, ssenariy bosqichi va tool'lar yozgan natija
(`chat_session`, `chat_message`). Har bir xabarda suhbat shu qatorlardan **qayta yig'iladi**.
Shuning uchun mehmon bir soatdan keyin javob bersa ham, xabari boshqa instance'ga tushsa ham
suhbat joyidan davom etadi.

**Agent har doim `AI_ASSISTANT`.** Agent sozlamasida "tirik odam" personasi yoqilgan bo'lsa ham,
chat'da u ishlatilmaydi: saytdagi yozishma oynasi o'zini tirik odam deb atashi — matn kanali hech
qachon qilmasligi kerak bo'lgan narsa.

**FactGuard.** Ovozda u gapni aytilishidan oldin to'xtatadi; chat'da to'xtatadigan narsa yo'q —
javob mehmonga o'qilyapti. Shuning uchun buzilish **log'ga `ERROR` bilan yoziladi** (sifat
alertlari qaraydigan joy), javob esa ketadi.

**Cheklovlar (`voice-agent.chat.*`, `config/misc.yml`):**

| Kalit | Standart | Ma'nosi |
|---|---|---|
| `CHAT_MAX_MESSAGES` | `40` | Bitta suhbatdagi xabarlar (mehmon + agent) |
| `CHAT_DAILY_SESSIONS` | `200` | Bitta agent kuniga nechta suhbat ochadi — Redis'da, instance'lar aro umumiy |
| `CHAT_SESSION_TTL_MINUTES` | `120` | Shuncha jimlikdan keyin suhbat tugaydi |

Kvota Redis'da atomik (`chat:daily:<agentId>:<sana>`); Redis yetib bo'lmasa suhbat **rad etiladi** —
ochiq endpoint uchun o'chib qoladigan chegara chegara emas.

**Saqlash muddati.** Chat transkripti — qo'ng'iroq transkripti bilan bir xil ma'lumot, shuning uchun
u ham `voice-agent.retention.conversation-days` bo'yicha o'chiriladi (`RetentionService`).

## Hozircha yo'q (keyingi bosqich)

- Chat uchun post-call xulosa, CRM note, mijoz xotirasi va billing — qo'ng'iroqdagi `CallFinalizer`
  yo'li chat'ga ulanmagan. Suhbat natijasi (`outcome`, `disposition`) `chat_session` da turadi.

---

# Panel: suhbatlar ekrani — `/api/chats`

Vidjet va Telegram suhbatlarini supervayzer o'qiydi. **Faqat o'qish** — panel suhbatga yozmaydi va
uni ochmaydi. Huquq — qo'ng'iroqlar ro'yxati va transkriptlari bilan bir xil (`CHAT_AGENT_READ`): chat
transkripti ham xuddi shunday ma'lumot.

| Metod | URL | Permission | Tavsif |
|---|---|---|---|
| `POST` | `/api/chats/list` | `CHAT_AGENT_READ` | Kompaniya suhbatlari, sahifalab |
| `GET` | `/api/chats/{id}` | `CHAT_AGENT_READ` | Bitta suhbat: natija va to'liq transkript |

## Ro'yxat

```json
POST /api/chats/list
{
  "page": 0,
  "size": 20,
  "orders": { "LAST_MESSAGE_AT": "DESC" },
  "aiAgentId": 42,
  "channel": "TELEGRAM",
  "ended": true,
  "from": "2026-09-01T00:00:00Z",
  "to": "2026-09-17T00:00:00Z"
}
```

Hamma filtr ixtiyoriy. `orders` berilmasa — `LAST_MESSAGE_AT DESC`.

| Maydon | Qiymatlar | Ma'nosi |
|---|---|---|
| `aiAgentId` | son | Faqat shu agent suhbatlari |
| `channel` | `WIDGET` · `TELEGRAM` | Faqat shu kanal |
| `ended` | `true` · `false` | `true` — yakunlanganlar; `false` — yakunlanmagan deb belgilanganlar (jim qolib muddati o'tganlar ham shu yerga tushadi) |
| `from` / `to` | ISO vaqt | Suhbat ochilgan vaqt bo'yicha: `from` kiradi, `to` kirmaydi |
| `orders` kalitlari | `ID` · `CREATED_AT` · `LAST_MESSAGE_AT` · `MESSAGE_COUNT` | Saralash |

```json
{
  "accept": true,
  "data": {
    "totalPages": 1, "currentPage": 0, "totalElements": 1,
    "data": [
      {
        "id": 318,
        "agentId": 42,
        "agentName": "Lavash do'koni",
        "channel": "TELEGRAM",
        "widgetId": null,
        "telegramBotId": 1,
        "language": "uz-UZ",
        "stage": "CONFIRM_ORDER",
        "disposition": "GOAL_REACHED",
        "ended": true,
        "messageCount": 9,
        "createdAt": "2026-09-16T09:12:04Z",
        "lastMessageAt": "2026-09-16T09:15:40Z"
      }
    ]
  },
  "message": null, "messageCode": null, "errors": null
}
```

- `sessionKey` bu yerda **yo'q**: u mehmonning suhbatga yozish kaliti, panel esa faqat o'qiydi.
- `channel` — `telegramBotId` bor bo'lsa `TELEGRAM`, aks holda `WIDGET`. Vidjet o'chirilgan bo'lsa
  `widgetId` `null` bo'lib qoladi, kanal esa baribir `WIDGET`.
- `disposition` — suhbat yakunlanmaguncha `null`.

## Bitta suhbat

```
GET /api/chats/318
```

```json
{
  "accept": true,
  "data": {
    "chat": { "id": 318, "agentId": 42, "agentName": "Lavash do'koni", "channel": "TELEGRAM", "...": "..." },
    "outcome": { "orderId": "A-1043", "slotBooked": true },
    "messages": [
      { "id": 5501, "chatSessionId": 318, "role": "AGENT",  "text": "Assalomu alaykum! ...", "createdAt": "2026-09-16T09:12:04Z" },
      { "id": 5502, "chatSessionId": 318, "role": "CLIENT", "text": "Ikkita lavash",          "createdAt": "2026-09-16T09:12:30Z" }
    ]
  },
  "message": null, "messageCode": null, "errors": null
}
```

- `outcome` — ssenariy tool'lari yozgan qiymatlar (qo'ng'iroqdagi natija maydonlari bilan bir xil).
- `messages` — yozilgan tartibda; `role`: `CLIENT` (mehmon) yoki `AGENT`.

**Xatolar**

| Status | `messageCode` | Qachon |
|---|---|---|
| `404` | `CHAT_SESSION_NOT_FOUND` | Bunday suhbat yo'q — yoki u boshqa kompaniyaniki |

---

# Telegram boti — `/api/telegram-bots`

O'sha agent, endi kompaniyaning **o'z Telegram botida**. Suhbat mexanikasi yuqoridagi bilan bir xil
(`ChatConversations`): bir xil turn, bir xil tool'lar, bir xil `chat_session` jadvali — farqi faqat
kirish va chiqish yo'lida.

| Metod | URL | Permission | Tavsif |
|---|---|---|---|
| `GET` | `/api/telegram-bots` | `CHAT_AGENT_READ` | Kompaniya botlari |
| `POST` | `/api/telegram-bots` | `CHAT_AGENT_EDIT` | Botni agentga bog'lash |
| `POST` | `/api/telegram-bots/{botId}/webhook` | `CHAT_AGENT_EDIT` | Telegram'ga "update'larni shu manzilga yubor" deyish |
| `DELETE` | `/api/telegram-bots/{botId}` | `CHAT_AGENT_EDIT` | Botni o'chirish (Telegram'dagi webhook ham olib tashlanadi) |
| `POST` | `/api/public/telegram/{botId}` | Ochiq (secret header) | **Telegram serverlari chaqiradi**, frontend emas |

## Ulash tartibi

1. **BotFather** dan token oling.
2. Tokenni **secret** qilib saqlang — `POST /api/secrets` ([secrets.md](secrets.md)). Token bu yerda
   ham, `telegram_bot` jadvalida ham ochiq saqlanmaydi: u faqat nomi bilan (`tokenSecretKey`)
   ishlatiladi va chaqiruv paytida shifrdan chiqariladi. Token — bu botning o'zi.
3. Botni ro'yxatga oling:

```json
POST /api/telegram-bots
{ "agentId": 42, "name": "@my_shop_bot", "tokenSecretKey": "TELEGRAM_BOT_TOKEN", "language": "uz-UZ" }
```

4. Webhook'ni ro'yxatdan o'tkazing — **manzilni siz berasiz**, chunki deployment qaysi domenda
   javob berishini ilova ichidan bilib bo'lmaydi:

```json
POST /api/telegram-bots/1/webhook
{ "publicBaseUrl": "https://voice.example.uz" }
```

Natijada Telegram'ga `https://voice.example.uz/api/public/telegram/1` manzili va **secret token**
beriladi (`setWebhook`, `allowed_updates: ["message"]`). Manzil `https` va ommaviy bo'lishi shart —
aks holda `409 TELEGRAM_WEBHOOK_URL_INVALID`.

## Xavfsizlik

Telegram'ning bu platformada akkaunti yo'q, shuning uchun update'ni haqiqiy qiladigan narsa —
`X-Telegram-Bot-Api-Secret-Token` sarlavhasi. U bot yaratilganda generatsiya qilinadi, `setWebhook`
bilan Telegram'ga beriladi va har update'da qaytib keladi; solishtirish **doimiy vaqtda**
(`MessageDigest.isEqual`) — deyarli to'g'ri secret uchun tezroq javob beradigan tekshiruv taxmin
qilinadigan tekshiruvdir. Mos kelmasa — `403`.

## Suhbat qanday davom etadi

Bitta Telegram chat'i uchun **bitta ochiq suhbat** — buni `chat_session` dagi qisman unikal indeks
kafolatlaydi (`telegram_bot_id`, `telegram_chat_id`, `ended = false`). Odam qayta yozsa, o'sha
suhbat davom etadi; suhbat yakunlangan, xabarlar chegarasi tugagan yoki juda uzoq jim turgan bo'lsa —
**yangisi ochiladi va agent salomlashadi**. Botga yozgan odamga "yangi sessiya boshlang" deb aytish
mumkin emas: u hozirgina yozdi.

## Xatolar Telegram tomonida qanday ko'rinadi

Update qabul qilinganini bildirish uchun endpoint deyarli har doim `200` qaytaradi. Sabab: Telegram
`2xx` olmagan update'ni **qayta-qayta yuboradi** — model bir daqiqa ishlamasa, bu bir necha soatlik
takror xabarga aylanardi. Model javob bermasa yoki limit tugasa, odamga qisqa uzr matni yoziladi
(bot tilida), qolgani log'ga tushadi. Faqat ikki holat `2xx` emas: bunday bot yo'q (`404`) va secret
mos kelmadi (`403`).

**Cheklovlar** chat bilan umumiy: `CHAT_MAX_MESSAGES`, `CHAT_DAILY_SESSIONS` (Redis'da, agent
bo'yicha), `CHAT_SESSION_TTL_MINUTES`. Saqlash muddati ham bir xil — `RetentionService`.

**Hozircha yo'q:** guruh chatlari maxsus ishlov (bot guruhda har xabarga javob beradi), inline tugmalar,
fayl/rasm qabul qilish, bot menyusi (`/start` maxsus emas — oddiy matn sifatida boradi).
