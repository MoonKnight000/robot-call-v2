# Webhook va SMS API integratsiyasi

`uz.murodjon.robotcallv2.webhook` va `uz.murodjon.robotcallv2.sms`

> ⚠️ Ikkala mexanizm ham **chiquvchi**: platforma o'zi tashqi manzilga so'rov yuboradi.
> `/api/webhooks` degan kontroller mavjud emas. Yagona REST qismi — qayta urinish navbatini
> ko'rish va `FAILED` yozuvni qo'lda qayta yuborish: `/api/webhook-deliveries` (§1.3).
> Webhook manzillari **kompaniya/agent sozlamalarida** beriladi (§1). `application.yml`
> dagi kalitlar:
>
> | Kalit | Default | Izoh |
> |---|---|---|
> | `voice-agent.webhook.retry-check-ms` | `60000` | Qayta urinish navbatini tekshirish oralig'i (ms) — §1.2 |
> | `voice-agent.sms.*` | — | Eskiz SMS shlyuzi — to'liq ro'yxat §2 "Eskiz sozlamalari" da |
>
> Platforma darajasidagi `voice-agent.webhook.url` / `CallWebhookEvent` mexanizmi hech qachon
> chaqirilmagan o'lik kod edi va 2026-09-12 da olib tashlandi — bunday kalit yo'q.

---

## 1. Chiquvchi webhook'lar — qayerda sozlanadi

| Webhook | Qachon | Qayerda sozlanadi | Imzo | `X-RobotCall-Delivery` | Qayta urinish |
|---|---|---|---|---|---|
| `initiationWebhook` | qo'ng'iroq boshida; javobidagi `variables` / `firstMessage` suhbatga qo'shiladi | agent — [ai-agents.md](ai-agents.md) §Webhooks | ✅ | har so'rovga yangi UUID | yo'q (javob faqat o'sha paytda kerak) |
| `postCallWebhook` | qo'ng'iroq tugab tahlil tayyor bo'lganda (transkript, audio URL, natija) | agent — [ai-agents.md](ai-agents.md) §Webhooks | ✅ | barqaror — retry'larda o'zgarmaydi | ✅ §1.2 |
| post-call `WEBHOOK` action | disposition bo'yicha shartli (`triggerDisposition`) | agent — [ai-agents.md](ai-agents.md) §Post-call actions | ✅ | barqaror — retry'larda o'zgarmaydi | ✅ §1.2 |
| bildirishnoma `WEBHOOK` kanali | kompaniya hodisasi (`NotificationType`) | [settings.md](settings.md) §4 | ✅ | har so'rovga yangi UUID | yo'q (best-effort) |
| `WEBHOOK_ROUTER` tuguni | kiruvchi qo'ng'iroq oqimi shu tugunga yetganda | [call-flows.md](call-flows.md) `WEBHOOK_ROUTER` | ✅ | har so'rovga yangi UUID | yo'q (javob bo'lmasa `error` chiqishi) |

"✅ imzo" — kompaniyada `WEBHOOK_SIGNING_SECRET` secret'i bo'lsa ([secrets.md](secrets.md));
bo'lmasa so'rov imzosiz ketadi. Hammasi bitta secret va bitta format bilan imzolanadi (§3.2),
shuning uchun qabul qiluvchi bitta tekshiruvchi yozadi.

**Payload'lar:** agent hook'lari va post-call action'lari tanasi — [ai-agents.md](ai-agents.md);
bildirishnoma kanali — `{"type", "title", "message", "link"}` ([settings.md](settings.md) §4);
`WEBHOOK_ROUTER` — `{"callerNumber", "did", "language", "callId", "vip", "choices"}`, javob
`{"exit": "<nom>"}` ([call-flows.md](call-flows.md)). Hammasi `POST`,
`Content-Type: application/json`.

**Taymautlar:** agent hook'lari va post-call action'lari — ulanish 3 s / javob 5 s
(`WebhookSenderPort` → `RestClientWebhookSenderAdapter`); bildirishnoma kanali — 5 s;
`WEBHOOK_ROUTER` — 3 s (mijoz liniyada kutib turadi).

### 1.1. Sarlavhalar

| Sarlavha | Qachon | Qiymat |
|---|---|---|
| `Content-Type` | doim | `application/json` |
| `X-RobotCall-Delivery` | doim | UUID — yetkazish identifikatori (§3.3) |
| `X-RobotCall-Signature` | `WEBHOOK_SIGNING_SECRET` bo'lsa | `t=<unix soniya>,v1=<hex HMAC>` (§3.2) |
| agentda sozlangan header'lar | agent hook'lari / post-call action'lari | `{{secrets.KEY}}` ochilgan holda |

### 1.2. Qayta urinish siyosati (`webhook_delivery`)

Faqat `postCallWebhook` va post-call `WEBHOOK` action uchun:

- Birinchi urinish darhol, qo'ng'iroq thread'ida. 2xx kelmasa (tarmoq xatosi, 4xx/5xx,
  taymaut, manzil ichki tarmoqqa qaragan) so'rov `webhook_delivery` jadvaliga saqlanadi.
- Keyingi urinishlar **1, 5 va 30 daqiqadan** keyin (jami 4 urinish). Navbatni har
  `voice-agent.webhook.retry-check-ms` da (default 1 daqiqa) bitta instansiya oladi — ikki
  instansiya bir yozuvni ikki marta yubormaydi.
- 4-urinish ham muvaffaqiyatsiz bo'lsa — `FAILED` va kompaniyaga `WEBHOOK_DELIVERY_FAILED`
  bildirishnomasi ([notifications.md](notifications.md)).
- Har urinishda manzil **qayta tekshiriladi** (§3.1): o'tmasa urinish muvaffaqiyatsiz
  hisoblanadi (`webhook url must be a public http(s) address`), so'rov yuborilmaydi.
- Har urinishda imzo yangidan qo'yiladi (`t=` — o'sha urinish vaqti), lekin
  `X-RobotCall-Delivery` **o'zgarmaydi** — takrorni shu bo'yicha aniqlang.
- Saqlangan URL va header'lar shifrlangan (secret vault kaliti bilan); kalit
  (`voice-agent.encryption.secret-key`) o'rnatilmagan bo'lsa qayta urinish bo'lmaydi.
- Yetkazilgan yozuvlar 7 kundan keyin o'chiriladi.
- `FAILED` yozuvni panel qayta yuborishi mumkin — §1.3.

### 1.3. Yetkazishlar jurnali va qo'lda qayta yuborish — `/api/webhook-deliveries`

Jurnalda faqat **birinchi urinishi muvaffaqiyatsiz bo'lgan** yetkazishlar bor (§1.2): birinchi
urinishda yetkazilgan webhook saqlanmaydi. Javobda faqat `host` ko'rinadi — URL, header va payload
qaytmaydi (ularda kalitlar va mijoz ma'lumoti bor).

| Metod | URL | Permission | Tavsif |
|---|---|---|---|
| `POST` | `/api/webhook-deliveries/filter` | `AI_AGENT_READ` | Sahifalangan ro'yxat |
| `POST` | `/api/webhook-deliveries/{id}/resend` | `AI_AGENT_EDIT` | `FAILED` yozuvni hozir yana bir marta yuborish |
| `GET` | `/api/webhook-deliveries/listen` | `AI_AGENT_READ` | Jonli SSE stream: webhook yetkazish urinishlarini real-vaqt rejimida kuzatish |

**`POST /api/webhook-deliveries/filter`** — barcha maydonlar ixtiyoriy:

```json
{
  "page": 0,
  "size": 20,
  "orders": { "CREATED_AT": "DESC" },
  "status": "FAILED",
  "source": "POST_CALL",
  "from": "2026-10-01T00:00:00Z",
  "to": "2026-10-08T00:00:00Z"
}
```

`orders` ustunlari: `CREATED_AT`, `ATTEMPTS` (default `CREATED_AT: DESC`). `status`: `PENDING` /
`DELIVERED` / `FAILED`. `source`: `POST_CALL` / `POST_CALL_ACTION`. `from` kiradi, `to` kirmaydi.

Javob — `PageableData<WebhookDeliveryRow>`:

```json
{
  "id": 41,
  "deliveryId": "6b1f0c7e-4c55-4a57-9a43-0e3f5b0c2d11",
  "source": "POST_CALL",
  "host": "crm.example.uz",
  "status": "FAILED",
  "attempts": 4,
  "nextAttemptAt": null,
  "lastError": "503 Service Unavailable",
  "createdAt": "2026-10-07T09:12:44Z",
  "deliveredAt": null
}
```

**`POST /api/webhook-deliveries/{id}/resend`** — so'rov darhol, o'sha `X-RobotCall-Delivery` bilan
yuboriladi va urinishlar soniga qo'shiladi. Natija qanday bo'lmasin `200`: qaytgan qatorning
`status` i `DELIVERED` yoki yana `FAILED` (yangi `lastError` bilan). Muvaffaqiyatsiz qayta yuborish
navbatga qo'yilmaydi va bildirishnoma yubormaydi — natija javobda ko'rinadi.

| Xato | Qachon |
|---|---|
| `404 WEBHOOK_DELIVERY_NOT_FOUND` | Bunday yozuv yo'q yoki boshqa kompaniyaniki |
| `409 WEBHOOK_DELIVERY_NOT_FAILED` | Yozuv `FAILED` emas (navbatda yoki yetkazilgan) yoki uni hozir boshqa so'rov qayta yubormoqda |

**`GET /api/webhook-deliveries/listen`** — dasturchilar uchun jonli SSE (Server-Sent Events) oqimi (Vapi CLI / listen analogi).
Lokal muhitda webhook'larni ngrok/tunnel ochmasdan to'g'ridan-to'g'ri kuzatish yoki frontend monitoring uchun mo'ljallangan.

- **Content-Type:** `text/event-stream`
- **Hodisalar:**
  - `connected`: ulanish o'rnatilganda (payload: subscription id)
  - `webhook`: chiquvchi webhook yetkazish urinishi amalga oshirilganda (har bir urinish, ham muvaffaqiyatli, ham xatoli):
    ```json
    {
      "deliveryId": "6b1f0c7e-4c55-4a57-9a43-0e3f5b0c2d11",
      "companyId": 1,
      "source": "POST_CALL",
      "url": "https://api.example.uz/webhook",
      "headers": { "Content-Type": "application/json" },
      "payload": { "callId": 123, "disposition": "GOAL_REACHED" },
      "timestamp": "2026-10-09T22:20:00Z",
      "success": true,
      "error": null
    }
    ```
  - Har 20 soniyada `:ping` heartbeat yuboriladi. Aloqa uzilganda abonent avtomatik o'chiriladi.

---

## 2. SMS: AI tool'lari va Eskiz

SMS bitta yo'ldan ketadi: `SmsUseCase` → `SmsService` → `SmsSenderPort` →
`EskizSmsClientAdapter`. Uni qo'ng'iroq davomida AI tool'lari (quyida) va qo'ng'iroqdan
keyingi `SEND_SMS` action ([ai-agents.md](ai-agents.md) §Post-call actions) chaqiradi.

### Tool'lar

| Tool | Qachon taklif qilinadi | Nima yuboradi |
|---|---|---|
| `sendMidCallSms(reply, text)` | agentda `midCallSmsEnabled: true` bo'lsa | `midCallSmsTemplate` bo'lmasa — model yozgan `text`; bo'lsa — to'ldirilgan shablon (quyida) |
| `sendSmsPaymentLink(reply, note)` | ssenariy uni e'lon qilgan bo'lsa | qat'iy matn: `To'lov havolasi: <paymentLink>` |

`sendSmsPaymentLink` havolani qo'ng'iroq faktlaridagi `paymentLink` dan oladi (kontakt
ma'lumoti yoki CRM). Fakt yo'q, butun `http(s)://` manzil emas yoki 200 belgidan uzun
(kesilgan) bo'lsa SMS yuborilmaydi — tool avvalgidek faqat `sendSmsRequested: true` va
`smsNote` ni natijaga yozadi. Havola bo'lsa ham SMS faqat agentda `midCallSmsEnabled: true`
bo'lganda ketadi.

**Cheklovlar** (`DialogTools` — Java'da, promptda emas):

- **Faqat shu mijozga.** Tool'larda raqam parametri yo'q: SMS qo'ng'iroqning o'z raqamiga
  ketadi (`call_attempt` raqami, u bo'lmasa `phone` fakti).
- **Bir qo'ng'iroqqa bitta SMS**, ikkala tool uchun umumiy. Ikkinchisi `XATO:` bilan rad
  etiladi. Yuborilmay qolgan urinish ham hisoblanadi: taymaut bo'lgan SMS yetib borgan
  bo'lishi mumkin, qayta urinish mijozga ikkinchi nusxa bo'ladi.
- **Ko'pi bilan 480 belgi.** Uzunroq matn rad etiladi; bu urinish hisoblanmaydi.
- **Yolg'on "yuborildi" yo'q.** Shlyuz qabul qilmasa, SMS o'rnatmada o'chiq bo'lsa
  (`SMS_ENABLED=false`) yoki agentda `midCallSmsEnabled` o'chiq bo'lsa tool
  `XATO: SMS yuborilmadi` qaytaradi va `reply` aytilmaydi. `CASCADE` va `REALTIME`
  qo'ng'iroqlarda bir xil ishlaydi.
- SMS matni va to'liq raqam logga yozilmaydi — faqat maskalangan raqam va uzunlik.

Natijaga yoziladigan kalitlar: `midCallSmsSent`, `midCallSmsText` (`sendMidCallSms`);
`sendSmsRequested`, `smsNote`, `paymentLinkSmsSent` (`sendSmsPaymentLink`).

**`midCallSmsTemplate`** — agentning SMS shabloni. Eskiz production'da faqat oldindan
tasdiqlangan shablonga mos matnni yetkazadi, shuning uchun shablon berilsa xabarni model
yozmaydi:

- `{{text}}` — model `text` parametrida beradigan qism (havola, kod). Shablonda bo'lsa-yu
  model uni bo'sh qoldirsa, tool `XATO:` qaytaradi va qo'ng'iroqning SMS'i sarflanmaydi.
- `{{<fakt>}}` — qo'ng'iroq faktlari (`{{clientName}}`, `{{debtAmount}}`); ssenariy
  shablonlaridagi `{{fakt | zaxira}}` sintaksisi ham ishlaydi. Fakt bo'lmasa zaxira qo'yiladi,
  u ham bo'lmasa — bo'sh joy.
- `{{text}}` siz shablon model nima yozganidan qat'i nazar aynan o'zi yuboriladi.
- Shablon bo'sh (`null`) bo'lsa — model yozgan `text` ketadi. Bu faqat erkin matnni
  yetkazadigan shlyuzda ishlaydi.

480 belgi chegarasi to'ldirilgan matnga qo'llanadi; natijadagi `midCallSmsText` — haqiqatda
yuborilgan matn. Misol: `"midCallSmsTemplate": "Hurmatli {{clientName | mijoz}}, to'lov
havolasi: {{text}}"` — Eskiz kabinetida xuddi shu matn (o'zgaruvchan qismlari bilan)
shablon sifatida tasdiqlangan bo'lishi kerak. `sendSmsPaymentLink` ning qat'iy matni
(`To'lov havolasi: <havola>`) ham shablon sifatida tasdiqlanishi kerak.

### Eskiz sozlamalari (`config/integrations.yml`)

| Kalit | Env | Default | Izoh |
|---|---|---|---|
| `voice-agent.sms.enabled` | `SMS_ENABLED` | `false` | `false` bo'lsa SMS faqat logga yoziladi (maskalangan) va **yuborilmadi** deb hisoblanadi — bot mijozga "SMS yubordim" demaydi |
| `voice-agent.sms.default-sender` | `SMS_DEFAULT_SENDER` | `4546` | Eskiz akkauntida tasdiqlangan jo'natuvchi |
| `voice-agent.sms.eskiz.email` | `SMS_ESKIZ_EMAIL` | — | bo'sh bo'lsa har bir SMS "yuborilmadi" |
| `voice-agent.sms.eskiz.password` | `SMS_ESKIZ_PASSWORD` | — | |
| `voice-agent.sms.eskiz.base-url` | `SMS_ESKIZ_BASE_URL` | `https://notify.eskiz.uz/api` | |
| `voice-agent.sms.eskiz.callback-url` | `SMS_ESKIZ_CALLBACK_URL` | — | Eskiz yetkazish hisobotini yuboradigan manzil |

Adapter `POST /auth/login` bilan token oladi va saqlaydi; `401` kelsa bir marta qayta login
qilib yana bir marta urinadi. Yuborish — `POST /message/sms/send` (`mobile_phone` =
`998XXXXXXXXX`, `message`, `from`, ixtiyoriy `callback_url`). HTTP 200 va `status` =
`waiting`/`success` — yuborildi; qolgan hammasi — yuborilmadi. Faqat O'zbekiston raqamlari.
Taymaut: ulanish 3 s, so'rov 5 s.

---

## 3. Xavfsizlik: manzil, imzo, yetkazish identifikatori

§1 jadvalidagi **barcha** webhook'larga tegishli.

### 3.1. Manzil ommaviy bo'lishi shart (SSRF)

Webhook URL faqat ommaviy `http(s)` manzil bo'la oladi. Loopback (`127.0.0.1`,
`localhost`), link-local (`169.254.0.0/16`, bulut metadata), xususiy tarmoq (`10/8`,
`172.16/12`, `192.168/16`), `0.0.0.0/8`, `100.64/10`, `198.18/15`, IPv6 unique-local
(`fc00::/7`), IPv4-mapped / NAT64 / 6to4 va multicast manzillar rad etiladi — aks holda
platforma o'z perimetri ichidagi xizmatlarga so'rov yuborishga majburlanishi mumkin edi.

- **Saqlashda:** manzil ichki tarmoqqa qarasa `400` (`WEBHOOK_URL_INVALID`,
  `NOTIFICATION_CHANNEL_TARGET_INVALID`, `CALL_FLOW_INVALID`). `{{secrets.KEY}}`
  placeholder'i bo'lgan manzil bu bosqichda tekshirilmaydi — u hali URL emas.
- **Yuborishda:** placeholder'lar ochilgandan keyin manzil qayta tekshiriladi; o'tmasa
  webhook **yuborilmaydi** (warn log), qo'ng'iroq esa davom etadi.
- **DNS pinning.** Tekshiruv va ulanish bitta DNS javobiga tayanadi: HTTP klient hostni
  ulanish paytida o'zi resolve qiladi va faqat ommaviy deb topilgan manzilga socket ochadi.
  Qisqa TTL'li DNS yozuvi bilan "tekshiruvda ommaviy, ulanishda `127.0.0.1`" hiylasi
  (DNS rebinding) ishlamaydi — so'rov "host topilmadi" xatosi bilan tugaydi.
- **Redirect kuzatilmaydi.** 3xx javob — bu endpoint tekshiruvdan keyin boshqa manzilni
  tanlashi; unga ergashilmaydi, 3xx shu so'rovning o'z javobi bo'lib qoladi.
- Cookie saqlanmaydi, so'rov klient tomonidan avtomatik qayta yuborilmaydi (qayta urinish
  faqat §1.2 dagi).

### 3.2. HMAC imzo

Kompaniyaning secret'lari orasida **`WEBHOOK_SIGNING_SECRET`** nomli secret bo'lsa
([secrets.md](secrets.md)), §1 dagi barcha webhook'larga imzo sarlavhasi qo'shiladi:

```
X-RobotCall-Signature: t=1757203200,v1=9f2b...c41d
```

- `t` — imzo qo'yilgan vaqt, Unix soniyalarda (qayta urinishda — o'sha urinish vaqti).
- `v1` — `HMAC-SHA256(secret, "<t>.<body>")` ning hex (kichik harf) ko'rinishi, bu yerda
  `<body>` — so'rovning **aynan** JSON tanasi (probel qo'shmasdan, qayta serializatsiya
  qilmasdan), UTF-8.

Secret yo'q bo'lsa webhook imzosiz yuboriladi. Secret almashtirilsa keyingi urinish yangi
secret bilan imzolanadi.

### 3.3. `X-RobotCall-Delivery`

Har bir so'rovda `X-RobotCall-Delivery: <UUID>` bor. U **yetkazishni** anglatadi, urinishni
emas: `postCallWebhook` va post-call `WEBHOOK` action qayta urinilganda xuddi shu qiymat
bilan keladi. Qabul qiluvchi uni saqlab qo'yib, allaqachon ishlangan qiymat qayta kelsa
`2xx` qaytarib hech narsa qilmasligi kerak (idempotentlik). Boshqa webhook'larda (§1
jadval) har so'rovga yangi UUID.

Sarlavha imzoga kirmaydi (`v1` formati o'zgarmagan) — replay'dan himoya `t` orqali (§3.4).

### 3.4. Qabul qiluvchi tomonda tekshirish

1. `X-RobotCall-Signature` ni `t` va `v1` ga ajrating.
2. `t` hozirgi vaqtdan 5 daqiqadan ko'p farq qilsa — rad eting (replay himoyasi).
3. So'rov tanasini **xom holida** o'qing, `"<t>." + body` uchun HMAC-SHA256 hisoblang.
4. Natijani `v1` bilan **doimiy vaqtli** taqqoslash orqali solishtiring.
5. `X-RobotCall-Delivery` ilgari ishlangan bo'lsa — `2xx` qaytaring va qayta ishlamang.

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
  return expected.length === parts.v1.length
      && crypto.timingSafeEqual(Buffer.from(expected), Buffer.from(parts.v1));
}
```

Java misoli:

```java
static boolean verify(String rawBody, String header, String secret) throws Exception {
    Map<String, String> parts = Arrays.stream(header.split(","))
            .map(p -> p.split("=", 2)).collect(Collectors.toMap(p -> p[0], p -> p[1]));
    long t = Long.parseLong(parts.get("t"));
    if (Math.abs(Instant.now().getEpochSecond() - t) > 300) return false;

    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    String expected = HexFormat.of().formatHex(mac.doFinal((t + "." + rawBody).getBytes(StandardCharsets.UTF_8)));
    return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
            parts.get("v1").getBytes(StandardCharsets.UTF_8));
}
```
