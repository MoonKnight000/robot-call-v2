# Secrets Management API (Maxfiy Kalitlar Ombori)

`uz.murodjon.robotcallv2.secret` · huquq: **INTEGRATION_READ** (ro'yxat, ko'rish) / **INTEGRATION_EDIT** (yaratish, tahrirlash, o'chirish)

AI Agent Tool'lari, webhooklar va integratsiyalarda ishlatiladigan API token, parol va
kalitlarni bir joyda saqlash. Tool yoki webhook konfiguratsiyasida `{{secrets.KEY}}`
deb yoziladi, server chaqiruvni yuborishdan oldin uni haqiqiy qiymatga almashtiradi.

Qiymatlar bazada **AES-256-GCM** bilan shifrlanadi (`SecretCipher`). API hech qachon
to'liq qiymatni qaytarmaydi — faqat maskalangan ko'rinish: `******` va qiymatning oxirgi
ko'pi bilan 2 belgisi, lekin hech qachon uzunligining choragidan ko'p emas (`******45`; 4 belgidan
qisqa qiymat butunlay `******`).

> ⚠️ `voice-agent.encryption.secret-key` sozlanmagan bo'lsa kalit **saqlanmaydi**:
> `409 ENCRYPTION_KEY_NOT_SET` qaytadi. Ochiq matnda saqlab qo'yish ataylab qilinmaydi.
> Kalit almashtirilgan bo'lsa eski yozuvlar o'qilmaydi — ular ro'yxatda `******` bo'lib
> ko'rinadi va `{{secrets.KEY}}` almashtirilmay qoladi.

---

## 1. `GET /api/secrets` — Maxfiy kalitlar ro'yxati

Kompaniyaning barcha saqlangan kalitlarini qaytaradi.

### Response
```json
{
  "accept": true,
  "data": [
    {
      "id": 1,
      "companyId": 1,
      "key": "CRM_API_KEY",
      "maskedValue": "******99",
      "description": "Asosiy CRM tizimi API tokeni",
      "createdAt": "2026-09-06T12:00:00Z",
      "updatedAt": "2026-09-06T12:00:00Z"
    },
    {
      "id": 2,
      "companyId": 1,
      "key": "PAYME_SECRET",
      "maskedValue": "******88",
      "description": "Payme to'lov tizimi maxfiy kaliti",
      "createdAt": "2026-09-06T12:10:00Z",
      "updatedAt": "2026-09-06T12:10:00Z"
    }
  ],
  "message": null,
  "messageCode": null,
  "errors": null
}
```

---

## 2. `POST /api/secrets` — Yangi maxfiy kalit yaratish

### Request
```json
{
  "key": "TELEGRAM_BOT_TOKEN",
  "value": "123456789:ABCdefGHIjklMNOpqrSTUvwxYZ",
  "description": "Operatorlarni xabardor qiluvchi Telegram bot tokeni"
}
```

### Constraints:
- `key`: faqat harflar, raqamlar, pastki chiziq `_`, chiziqcha `-` yoki nuqta `.` bo'lishi mumkin. Kompaniya doirasida unikal bo'lishi shart.
- `value`: bo'sh bo'lmasligi kerak.

---

## 3. `GET /api/secrets/{id}` — Kalit tafsilotlarini olish

```json
{
  "accept": true,
  "data": {
    "id": 1,
    "companyId": 1,
    "key": "CRM_API_KEY",
    "maskedValue": "******99",
    "description": "Asosiy CRM tizimi API tokeni",
    "createdAt": "2026-09-06T12:00:00Z",
    "updatedAt": "2026-09-06T12:00:00Z"
  }
}
```

---

## 4. `PUT /api/secrets/{id}` — Kalitni tahrirlash

Qiymat yoki tavsifni yangilash. Agar `value` uzatilmasa, mavjud shifrlangan qiymat o'zgarishsiz qoladi.

### Request
```json
{
  "value": "new-crm-live-token-2026",
  "description": "Yangilangan CRM API kaliti"
}
```

---

## 5. `DELETE /api/secrets/{id}` — Kalitni o'chirish

---

## 6. Ishlatish: Tool va Webhooklarda Dinamik O'rniga Qo'yish

AI Agent Toollari va Post-Call Webhooklarida maxfiy kalitlarni ochiq yozish o'rniga shablon sintaksisidan foydalaniladi:

- `{{secrets.KEY_NAME}}` yoki `{{secret.KEY_NAME}}`

### Misollar:

1. **Tool HTTP Headers:**
   ```json
   {
     "Authorization": "Bearer {{secrets.CRM_API_KEY}}",
     "X-Secret-Token": "{{secrets.PAYME_SECRET}}"
   }
   ```

2. **Tool API URL:**
   ```
   https://api.mycrm.uz/v1/orders?api_token={{secrets.CRM_API_KEY}}
   ```

3. **Post-Call Webhook Headers:**
   ```json
   {
     "Authorization": "Bearer {{secrets.WEBHOOK_SECRET}}"
   }
   ```

Agent muloqot paytida Tool yoki Webhookni chaqirganda, tizim maxfiy kalitlarni bazadan xavfsiz dekodlab, kerakli joyga avtomatik qo'yadi. Foydalanuvchi, brauzer yoki LLM ga maxfiy kalit oshkor bo'lmaydi.

---

## Maxsus nom: `WEBHOOK_SIGNING_SECRET`

Bitta secret nomi tizim uchun ma'noga ega. Kompaniyada **`WEBHOOK_SIGNING_SECRET`**
nomli secret yaratilsa, o'sha kompaniyaning barcha chiquvchi webhook'lariga
(`initiationWebhook`, `postCallWebhook`, post-call `WEBHOOK` action'lari, bildirishnomalarning
`WEBHOOK` kanali va qo'ng'iroq oqimidagi `WEBHOOK_ROUTER` tuguni)
`X-RobotCall-Signature` sarlavhasi qo'shiladi — qabul qiluvchi so'rov haqiqatan
platformadan kelganini tekshira oladi.

Boshqa secret'lardan farqi: uni `{{secrets.WEBHOOK_SIGNING_SECRET}}` ko'rinishida
biror joyga yozish shart emas — tizim uni nomi bo'yicha o'zi topadi. Secret bo'lmasa
webhook imzosiz yuboriladi.

Imzo formati va tekshirish kodi: [webhooks.md](webhooks.md) §3.2–3.3.

---

## Audit va kalit rotatsiyasi

Har bir `POST`/`PUT`/`DELETE` audit jurnaliga yoziladi: `SECRET_CREATE`, `SECRET_UPDATE`
(qiymat almashtirilgan bo'lsa `(value replaced)` belgisi bilan), `SECRET_DELETE` — qiymatning
o'zi hech qachon yozilmaydi, faqat `key`.

Qiymatlar AES-256-GCM bilan `ENCRYPTION_SECRET_KEY` ostida saqlanadi. Kalitni almashtirish:
eski kalitni `ENCRYPTION_PREVIOUS_SECRET_KEYS` ga (vergul bilan, eng yangisi birinchi)
ko'chiring, `ENCRYPTION_SECRET_KEY` ga yangisini qo'ying, ilovani qayta ishga tushiring.
Eski secret'lar o'qilaveradi (dekodlash avval joriy, keyin eski kalitlarni sinaydi); har
bir secret `PUT` bilan qayta saqlanganda yangi kalitga o'tadi. Hech qaysi kalit mos kelmasa
secret `null` sifatida o'qiladi (tool/webhook'da placeholder bo'sh qoladi) va logda `ERROR`
chiqadi.
