# Sozlamalar API (§11)

Har bir bo'lim o'z paketida: `aimodel`, `notification` (matritsa qismi),
`integration`. Barchasi rol: **ADMIN**, va barchasi so'rov qilgan foydalanuvchining
**o'z kompaniyasiga** scoped (`CurrentCompany`, JWT orqali) — path'da `{id}` yo'q,
boshqa kompaniyaning sozlamasiga path orqali kira olmaysiz.

**Kompaniyaga scoped API kalitlar panel olib tashlandi (report #11 — "API KEYLAR
UMUMAN KERAK EMAS").** `X-Api-Key` autentifikatsiyasi endi faqat ikkita global,
konstantali kalit orqali ishlaydi: `voice-agent.security.api-key` (to'liq huquq) va
`voice-agent.security.read-api-key` (faqat o'qish) — batafsil
[README.md §1.1](README.md)da.

Umumiy javob shakli, xatolar va pagination konventsiyasi uchun [README.md](README.md)ga
qarang.

---

## AI model — `uz.murodjon.uysotvoice.aimodel`

Kompaniya `DialogEngine`ning Gemini model/harorat/token limitlarini o'zi uchun
qayta belgilashi mumkin — hech narsa belgilamasa, `application.yml`/`DialogProperties`dagi
global standart ishlatiladi. Har bir qo'ng'iroq boshlanganda **bir marta** o'qiladi
(`DialogEngine.startCall`), keyingi har bir turnda emas.

### `GET /api/settings/ai-model` — joriy sozlama

**Response** (`AiModelConfig`) — hech qachon `404` qaytarmaydi, hech narsa
belgilanmagan bo'lsa hamma maydon `null`:

```json
{
  "companyId": 1, "model": null, "temperature": 0.4, "maxOutputTokens": null,
  "maxCallSeconds": null, "maxTokensPerCall": null, "createdAt": "2026-08-02T10:00:00Z"
}
```

### `PUT /api/settings/ai-model` — yangilash

**Request body** (`UpdateAiModelConfigRequest`) — har bir maydon ixtiyoriy, `null`
o'sha maydonni global standartga qaytaradi:

```json
{ "model": "gemini-3.6-pro", "temperature": 0.3, "maxOutputTokens": null,
  "maxCallSeconds": 600, "maxTokensPerCall": null }
```

**Response** — yangilangan `AiModelConfig`.

> Diqqat: kampaniyaning `maxCallSeconds`/parallel navbat limiti (dialer worker pool
> hajmi) bu yerga kirmaydi — bular hamon jarayon darajasida, `voice-agent.dialer.*`
> orqali sozlanadi.

---

## Ovoz sozlamalari — `uz.murodjon.uysotvoice.voice`

Kompaniya TTS provayder/tezlik/tembrni **standart** qilib o'zi uchun qayta
belgilashi mumkin (backend-uchun-talablar.md §6) — hech narsa belgilamasa,
`application.yml`/`voice-agent.tts.*`dagi global standart ishlatiladi. Har bir
qo'ng'iroq boshlanganda **bir marta** o'qiladi (`DialogEngine.startCall` →
`VoiceSettingsService.effective`, natija butun qo'ng'iroq davomida ishlatiladi),
`agent.tts.TtsRouter`ga uzatiladi.

### `GET /api/settings/voice` — joriy sozlama

**Response** (`VoiceSettings`) — hech qachon `404` qaytarmaydi, hech narsa
belgilanmagan bo'lsa hamma maydon `null`:

```json
{ "companyId": 1, "provider": null, "speed": null, "pitch": null, "createdAt": "2026-08-02T10:00:00Z" }
```

### `PUT /api/settings/voice` — yangilash

**Request body** (`UpdateVoiceSettingsRequest`) — har bir maydon ixtiyoriy,
`null` o'sha maydonni global standartga qaytaradi:

```json
{ "provider": "yandex", "speed": 1.0, "pitch": 0.0 }
```

| Maydon | Turi | Izoh |
|---|---|---|
| `provider` | string | `google`/`yandex`; qo'ng'iroq tilini qo'llab-quvvatlasa birinchi navbatda shu tanlanadi, bo'lmasa til bo'yicha odatdagi marshrutlashga qaytadi — campaign'ning o'z ovozi tanlangan bo'lsa (`CreateCampaignRequest.ttsVoice`), bu shundan ustun kelmaydi |
| `speed` | double, `0.1`–`3.0` | gapirish tezligi ko'paytiruvchisi (`1.0` = odatiy); ikkala provayderda ham qo'llanadi |
| `pitch` | double, `-20.0`–`20.0` | tembr siljishi (yarim ton); faqat Google Cloud TTS — Yandex SpeechKit v1'da pitch parametri yo'q, Yandex tanlanganda e'tiborga olinmaydi |

**Response** — yangilangan `VoiceSettings`.

---

## Bildirishnoma matritsasi — `uz.murodjon.uysotvoice.notification` (kanal qismi)

Mavjud topbar bell (`GET /api/notifications`, foydalanuvchi darajasida yoqish/o'chirish)
dan **mustaqil** — bu yerda kompaniya darajasida qaysi hodisa (`NotificationType`)
qaysi tashqi kanalga (`EMAIL`/`WEBHOOK`/`TELEGRAM`) boradi, shuni belgilaysiz. Ikkalasi
ham parallel ishlaydi: bir hodisa yuz berganda ichki bell **va** yoqilgan tashqi
kanallar bir vaqtda ishga tushadi.

`TELEGRAM` uchun bot tokeni butun deploy uchun bitta (`voice-agent.notification
.telegram-bot-token`), kompaniya faqat o'z `chat_id`sini kiritadi. `EMAIL`/`WEBHOOK`
mavjud SMTP sozlamasini (`spring.mail.*`, hisobot yuborish bilan bir xil) ishlatadi.

### `GET /api/settings/notifications` — joriy matritsa

**Response** (`NotificationSettings`):

```json
{
  "channels": [
    { "channel": "EMAIL", "target": "ops@company.uz", "enabled": true },
    { "channel": "WEBHOOK", "target": "https://hooks.company.uz/voice", "enabled": false },
    { "channel": "TELEGRAM", "target": "-1001234567890", "enabled": true }
  ],
  "matrix": [
    { "type": "CAMPAIGN_FINISHED", "channel": "EMAIL", "enabled": true },
    { "type": "ERROR_OCCURRED", "channel": "TELEGRAM", "enabled": true }
  ]
}
```

Ro'yxatda bo'lmagan `(type, channel)` juftligi — o'chiq deb hisoblanadi.

### `PUT /api/settings/notifications` — butun setkani yozish

**Request body** — `UpdateNotificationSettingsRequest` (`GET` javobi bilan bir xil
shakl: `channels` + `matrix`). **Butun setka almashtiriladi** — faqat bitta katakchani
o'zgartirish uchun ham to'liq `GET` javobini oling, kerakli joyini o'zgartirib, to'liq
qaytadan yuboring.

Bir kanal `enabled: false` yoki `target` bo'sh bo'lsa — matritsada o'sha kanal uchun
`enabled: true` bo'lsa ham hech narsa yuborilmaydi (ikkalasi ham rozi bo'lishi kerak).

**Response** — yangilangan `NotificationSettings`.

---

---

## Integratsiyalar — `uz.murodjon.uysotvoice.integration`

OAuth2 authorization-code oqimi, Uysot'ning haqiqiy Open API hujjatlariga qarshi
tekshirilgan (report #10). **`client_id`/`client_secret` global** — bitta platforma
darajasidagi Uysot ilovasi (`voice-agent.integration.uysot.client-id/client-secret`)
barcha kompaniyalarga xizmat qiladi; Uysot hujjatlariga ko'ra, qaysi kompaniya
ulanayotgani `client_id`dan emas, balki consent bosqichida tizimga kirgan
foydalanuvchining o'zidan aniqlanadi. Har bir kompaniya faqat o'zining `appName`
(consent ekranida ko'rsatiladi) va so'ralayotgan `grants` (ruxsatlar) ro'yxatini
kiritadi.

`authorize_url`/`token_url`/`revoke_url`/`redirect_uri` global
(`voice-agent.integration.uysot.*`) — bitta Uysot instansiyasi hammaga bir xil.
`crm_integration`dagi access/refresh tokenlar AES-256-GCM bilan shifrlanadi
(`ENCRYPTION_SECRET_KEY` — alohida, `API_KEY`/`JWT_SECRET`dan mustaqil).

### `GET /api/settings/integrations/catalog` — ulanish mumkin bo'lgan CRM'lar

Statik ro'yxat, kompaniyaga bog'liq emas (report #10):

```json
[
  { "provider": "UYSOT", "displayName": "Uysot CRM", "authMethod": "OAUTH", "available": true },
  { "provider": "BITRIX24", "displayName": "Bitrix24", "authMethod": "OAUTH", "available": false },
  { "provider": "AMOCRM", "displayName": "amoCRM", "authMethod": "OAUTH", "available": false }
]
```

`available: false` — hozircha faqat katalogda ko'rsatish uchun ("tez orada"); ulanish
oqimi ("connect") hali faqat Uysot uchun ishlaydi.

### `GET /api/settings/integrations` — joriy holat

**Response** (`CrmIntegration`) — hech qachon tokenlarni qaytarmaydi:

```json
{
  "companyId": 1, "provider": "UYSOT", "appName": "Bizning CRM integratsiyamiz",
  "grants": [ { "permission": "LEAD", "scope": "READ" }, { "permission": "CALL", "scope": "SAVE" } ],
  "status": "CONNECTED", "connectedAt": "2026-08-02T10:00:00Z"
}
```

`status`: `NOT_CONNECTED` (appName/grants hali kiritilmagan yoki OAuth hali
yakunlanmagan) → `CONNECTED` (token bor, ishlatilmoqda) → `ERROR` (refresh muvaffaqiyatsiz —
`CrmClient` avtomatik global statik tokenga qaytadi, agar u sozlangan bo'lsa).

### `PUT /api/settings/integrations/uysot` — appName/grants saqlash

**Request body** (`ConnectIntegrationRequest`):

```json
{ "appName": "Bizning CRM integratsiyamiz", "grants": [ { "permission": "LEAD", "scope": "READ" } ] }
```

`grants[].permission` — `LEAD`, `LEAD_NOTE`, `LEAD_TASK`, `CONTRACT`,
`CONTRACT_PAYMENT`, `CALL`. `grants[].scope` — `READ`, `SAVE`, `DELETE`. Bu yerda
so'ralgan ruxsatlar platformaning Uysot'da ro'yxatdan o'tgan ilovasi uchun
ruxsat etilgan doiradan chiqmasligi kerak — chiqsa, Uysot consent bosqichida
`invalid_scope` bilan rad etadi.

Saqlagandan keyin holat `NOT_CONNECTED`ga qaytadi (yangi grants — eski token endi
ishlamaydi) — keyingi qadam autentifikatsiya URL'iga o'tish.

### `GET /api/settings/integrations/uysot/authorize-url` — OAuth boshlash

**Response** (`AuthorizeUrlResponse`):

```json
{ "authorizeUrl": "https://app.uysotdev.aws.softex.uz/oauth/authorize?client_id=...&app_name=...&redirect_url=...&grants=...&state=..." }
```

`grants` — `{"permission":"PERMISSION_OPEN_API_LEAD","scope":"READ"}` shaklidagi
massivning base64'i (Uysot'ning haqiqiy wire formatiga mos). **`redirect_url`, `redirect_uri` emas** — Uysot bu ikki bosqichda parametrni turlicha nomlaydi
(authorize'da `redirect_url`, token almashinuvida `redirect_uri`); ikkalasi ham bir
xil qiymatga ishora qiladi. `appName`/`grants` hali saqlanmagan bo'lsa — `400`.

### `GET /api/settings/integrations/uysot/callback?code=&state=` — OAuth qaytishi

**Autentifikatsiya talab qilinmaydi** — Uysot brauzerni to'g'ridan-to'g'ri shu yerga
qaytaradi, na `X-Api-Key`, na `Authorization` sarlavhasi bilan. `state` (imzolangan,
ichida `companyId` bor) so'rovni tasdiqlaydi. Muvaffaqiyatda holat `CONNECTED`ga
o'tadi; token almashinuvi muvaffaqiyatsiz bo'lsa — `502`.

### `DELETE /api/settings/integrations/uysot` — uzish

Body yo'q. Avval Uysot'ning o'zida tokenni bekor qiladi (`POST
/v1/open-api/oauth/revoke`, best-effort — muvaffaqiyatsiz bo'lsa ham davom etadi),
so'ng mahalliy tokenlarni tozalaydi va holatni `NOT_CONNECTED`ga qaytaradi.
`appName`/`grants` o'chirilmaydi — qayta ulash uchun avtorizatsiya URL'iga qaytish
yetarli.
