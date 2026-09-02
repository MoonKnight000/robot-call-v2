# Webhook va SMS API integratsiyasi

`uz.murodjon.robotcallv2.webhook` va `uz.murodjon.robotcallv2.sms` · rol: **ADMIN / SYSTEM**

Qo'ng'iroq jarayoni (life-cycle) hodisalarini tashqi CRM, ERP yoki xabarnoma tizimlariga real vaqtda HTTP Webhook orqali yetkazish hamda suhbat davomida AI agent orqali avtomatik SMS xabarnomalar (to'lov havolasi, tasdiq kodlari) jo'natish mexanizmi.

---

## 1. Qo'ng'iroq Webhook Hodisalari (`CallWebhookEvent`)

Qo'ng'iroq har bir asosiy bosqichdan o'tganda tizim tashqi belgilangan webhook manziliga (`voice-agent.webhook.url`) virtual potoklar (`webhookExecutor`) orqali asinxron JSON POST so'rov yuboradi.

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

## 2. SMS Integratsiyasi va AI Tool (`sendSmsNotification`)

Dialog davomida AI mijoz bilan kelishuvga erishganda (masalan, to'lov havolasini yuborish, hisob rekvizitlarini jo'natish yoki SMS tasdiq) avtomatik ravishda `sendSmsNotification` tool'ini chaqiradi:

### Tool deklaratsiyasi
```json
{
  "name": "sendSmsNotification",
  "description": "Mijozga to'lov havolasi yoki ma'lumotnoma SMS jo'natish",
  "params": [
    { "name": "phone", "type": "string", "required": false, "constraint": "agar berilmasa joriy mijoz raqamiga yuboriladi" },
    { "name": "message", "type": "string", "required": true, "constraint": "yuboriladigan SMS matni" }
  ]
}
```

### SMS provayderlari
Konfiguratsiya orqali Eskiz (`ESKIZ`), PlayMobile (`PLAY_MOBILE`) yoki Mock/Log (`MOCK`) provayderlariga ulanadi (`voice-agent.sms.provider`).
