# Webhook va SMS API integratsiyasi

`uz.murodjon.robotcallv2.webhook` va `uz.murodjon.robotcallv2.sms`

> ⚠️ **Bu yerda REST endpoint yo'q.** Ikkala mexanizm ham **chiquvchi**: platforma o'zi
> tashqi manzilga so'rov yuboradi. `/api/webhooks` degan kontroller mavjud emas —
> sozlash `application.yml` orqali qilinadi:
>
> | Kalit | Izoh |
> |---|---|
> | `voice-agent.webhook.enabled` | Standart `false`. `false` bo'lsa hech narsa yuborilmaydi |
> | `voice-agent.webhook.url` | Hodisalar POST qilinadigan yagona manzil. Bo'sh bo'lsa ham yuborilmaydi |
> | `voice-agent.sms.provider` | `ESKIZ`, `PLAY_MOBILE` yoki `MOCK` |
>
> Manzil **kompaniya bo'yicha emas, platforma bo'yicha bitta** — hozircha har bir tenant
> uchun alohida webhook URL sozlab bo'lmaydi.

Qo'ng'iroq jarayoni (life-cycle) hodisalarini tashqi CRM, ERP yoki xabarnoma tizimlariga real vaqtda HTTP Webhook orqali yetkazish hamda suhbat davomida AI agent orqali avtomatik SMS xabarnomalar (to'lov havolasi, tasdiq kodlari) jo'natish mexanizmi.

---

## 1. Qo'ng'iroq Webhook Hodisalari (`CallWebhookEvent`)

> ⚠️ **Hozircha hech qanday hodisa yuborilmaydi.** `WebhookEventService.dispatchEvent(...)`
> yozilgan, lekin uni loyihada hech kim chaqirmaydi — ya'ni `voice-agent.webhook.enabled`
> yoqilsa ham webhook kelmaydi. Quyidagi payload va hodisa turlari — **rejalashtirilgan
> shartnoma**, ishlayotgan integratsiya emas.

Qo'ng'iroq har bir asosiy bosqichdan o'tganda tizim tashqi belgilangan webhook manziliga (`voice-agent.webhook.url`) virtual potoklar (`webhookExecutor`) orqali asinxron JSON POST so'rov yuboradi.

So'rov sarlavhalari: `Content-Type: application/json` va `X-VoiceAgent-Event:
<eventType>`. Ulanish va o'qish taymauti — **5 soniya**. Javob 2xx bo'lmasa yoki xato
chiqsa **qayta urinilmaydi** — faqat warn logga yoziladi (qo'ng'iroq to'xtamasligi
uchun). Ya'ni yetkazish kafolatlanmagan: kritik integratsiya buni yagona manba sifatida
ishlatmasin.

### Webhook payload shakli

```json
{
  "eventType": "CALL_COMPLETED",
  "companyId": 1,
  "callAttemptId": 4059,
  "channelId": "PJSIP/trunk-0000001a",
  "phone": "998901234567",
  "disposition": "PROMISE_TO_PAY",
  "durationSeconds": 45,
  "recordingUrl": "/api/files/recordings/call_4059.wav",
  "summary": "Mijoz qarzini tan oldi va 2026-09-01 sanasida to'lashga va'da berdi.",
  "outcome": {
    "promisedDate": "2026-09-01",
    "promisedAmount": 1500000
  },
  "timestamp": "2026-08-28T06:10:00Z"
}
```

### Hodisa turlari (`eventType`)

| Turi | Qachon yuboriladi | Muhim maydonlar |
|---|---|---|
| `CALL_STARTED` | Qo'ng'iroq kanali ochilib, Asterisk raqam terishni boshlaganda | `callAttemptId`, `channelId`, `phone` |
| `CALL_ANSWERED` | Mijoz go'shakni ko'targanda va RTP audio ulanishi o'rnatilganda | `callAttemptId`, `channelId`, `phone`, `timestamp` |
| `CALL_COMPLETED` | Suhbat yakunlanib, audio yozib olingan va tahlil qilinganda | `durationSeconds`, `recordingUrl`, `summary`, `outcome` |
| `DISPOSITION_RECORDED` | Qo'ng'iroq natijasi (disposition) aniqlanganda | `disposition` (`PROMISE_TO_PAY`, `REFUSED`, `WRONG_NUMBER`, `DO_NOT_CALL`, `NO_ANSWER` va h.k.) |

---

## 2. SMS va AI Tool (`sendSmsPaymentLink`)

Dialog davomida mijoz to'lov havolasini yoki rekvizitlarni SMS orqali so'rasa, LLM
`sendSmsPaymentLink` tool'ini chaqiradi (`DialogTools`).

### Tool deklaratsiyasi

| Parametr | Turi | Majburiymi | Izoh |
|---|---|---|---|
| `reply` | string | ❌ | Mijozga aytiladigan javob matni |
| `note` | string | ❌ | SMS xabar turi yoki qo'shimcha matn |

> ⚠️ **Tool SMS yubormaydi.** U faqat qo'ng'iroq natijasiga ikkita belgi yozadi —
> `sendSmsRequested: true` va `smsNote: "<note>"` — va mijozga "To'lov havolasi SMS
> orqali yuboriladigan bo'ldi" deb aytadi. Haqiqiy jo'natish `SmsService` orqali
> bo'lishi kerak, lekin `sms` moduli dialog pipeline'iga ulanmagan
> (`SmsUseCase` ni `agent/` da hech kim chaqirmaydi).
>
> `sendSmsNotification` degan tool **yo'q** — nomi `sendSmsPaymentLink`.
> Kampaniyadagi `midCallSmsEnabled` / `midCallSmsTemplate` maydonlari ham hozircha
> faqat saqlanadi ([campaigns.md](campaigns.md)).

### SMS provayderlari

`voice-agent.sms.provider` orqali tanlanadi. Kodda hozircha faqat **Eskiz**
(`EskizSmsClientAdapter`) adapteri bor; PlayMobile va Mock adapterlari yozilmagan.


---

## 3. Imzo va manzil cheklovlari (agent hook'lari va post-call action'lar)

Bu bo'lim **ishlayotgan** yo'lga tegishli: AI agentning `initiationWebhook` /
`postCallWebhook` sozlamalari ([ai-agents.md](ai-agents.md)) va qo'ng'iroqdan keyingi
`WEBHOOK` turidagi action'lar. Yuqoridagi `voice-agent.webhook.*` dispatcher'i bunga
kirmaydi.

### 3.1. Manzil ommaviy bo'lishi shart

Webhook URL faqat ommaviy `http(s)` manzil bo'la oladi. Loopback (`127.0.0.1`,
`localhost`), link-local (`169.254.0.0/16`), xususiy tarmoq (`10/8`, `172.16/12`,
`192.168/16`), IPv6 unique-local (`fc00::/7`) va multicast manzillar rad etiladi —
aks holda platforma o'z perimetri ichidagi xizmatlarga so'rov yuborishga majburlanishi
mumkin edi (SSRF).

- **Agent saqlanayotganda:** manzil xususiy IP'ga qaralsa `400 WEBHOOK_URL_INVALID`
  qaytadi. `{{secrets.KEY}}` placeholder'i bo'lgan manzil bu bosqichda tekshirilmaydi —
  u hali URL emas.
- **Yuborish paytida:** placeholder'lar ochilgandan keyin manzil qayta tekshiriladi.
  O'tmasa webhook **yuborilmaydi**, warn log yoziladi, qo'ng'iroq esa davom etadi.
- **Redirect kuzatilmaydi.** 302 — bu endpoint tekshiruvdan keyin boshqa manzilni
  tanlashi, ya'ni filtrni aylanib o'tishning odatiy yo'li.

### 3.2. HMAC imzo

Agar kompaniyaning secret'lari orasida **`WEBHOOK_SIGNING_SECRET`** nomli secret bo'lsa
([secrets.md](secrets.md)), o'sha kompaniyaning barcha chiquvchi webhook'lariga imzo
sarlavhasi qo'shiladi:

```
X-RobotCall-Signature: t=1757203200,v1=9f2b...c41d
```

- `t` — imzo qo'yilgan vaqt, Unix soniyalarda.
- `v1` — `HMAC-SHA256(secret, "<t>.<body>")` ning hex ko'rinishi, bu yerda `<body>` —
  so'rovning **aynan** JSON tanasi (probel qo'shmasdan, qayta serializatsiya qilmasdan).

Secret yo'q bo'lsa webhook imzosiz yuboriladi — ilgari qanday bo'lsa shunday.

### 3.3. Qabul qiluvchi tomonda tekshirish

1. `X-RobotCall-Signature` ni `t` va `v1` ga ajrating.
2. `t` hozirgi vaqtdan 5 daqiqadan ko'p farq qilsa — rad eting (replay himoyasi).
3. So'rov tanasini **xom holida** o'qing, `"<t>." + body` uchun HMAC-SHA256 hisoblang.
4. Natijani `v1` bilan **doimiy vaqtli** taqqoslash orqali solishtiring.

Node.js misoli:

```js
const crypto = require("crypto");

function verify(rawBody, header, secret) {
  const parts = Object.fromEntries(header.split(",").map(p => p.split("=")));
  const age = Math.abs(Math.floor(Date.now() / 1000) - Number(parts.t));
  if (!Number.isFinite(age) || age > 300) return false;

  const expected = crypto.createHmac("sha256", secret)
      .update(`${parts.t}.${rawBody}`)
      .digest("hex");
  return crypto.timingSafeEqual(Buffer.from(expected), Buffer.from(parts.v1));
}
```
