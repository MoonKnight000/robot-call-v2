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

