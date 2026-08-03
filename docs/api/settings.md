# Sozlamalar API (§11)

Har bir bo'lim o'z paketida: `apikey`, `aimodel`, `notification` (matritsa qismi),
`integration`. Barchasi rol: **ADMIN**, va barchasi so'rov qilgan foydalanuvchining
**o'z kompaniyasiga** scoped (`CurrentCompany`, JWT orqali) — path'da `{id}` yo'q,
boshqa kompaniyaning sozlamasiga path orqali kira olmaysiz.

Umumiy javob shakli, xatolar va pagination konventsiyasi uchun [README.md](README.md)ga
qarang.

---

## API kalitlar — `uz.murodjon.uysotvoice.apikey`

Ikkita global, konstantali `X-Api-Key` (`voice-agent.security.api-key`/`read-api-key`)
o'rniga — panel orqali yaratiladigan, bekor qilinadigan, **kompaniyaga scoped** kalitlar.
Global kalitlar hamon ishlaydi (fallback/bootstrap), bu ularni almashtirmaydi, ustiga
qo'shadi.

### `POST /api/settings/api-keys` — yangi kalit

**Request body** (`CreateApiKeyRequest`):

```json
{ "name": "CRM webhook", "role": "OPERATOR" }
```

| Maydon | Majburiymi | Izoh |
|---|---|---|
| `name` | ✅ (`@NotBlank`) | Ro'yxatda ko'rsatish uchun |
| `role` | ✅ (`@NotNull`) | `ADMIN` \| `OPERATOR` \| `VIEWER` — `JwtAuthFilter`dagi bir xil rol ierarxiyasi |

**Response** (`CreateApiKeyResponse`) — **xom kalit faqat shu javobda bir marta
qaytadi**, keyin qayta ko'rsatilmaydi (faqat hash saqlanadi):

```json
{
  "key": {
    "id": 3, "companyId": 1, "name": "CRM webhook", "keyPrefix": "aB3dEf9x",
    "role": "OPERATOR", "createdAt": "2026-08-02T10:00:00Z",
    "lastUsedAt": null, "revokedAt": null
  },
  "rawKey": "aB3dEf9xQ7...<256-bit, faqat bir marta>"
}
```

---

### `POST /api/settings/api-keys/list` — ro'yxat

Body — `ApiKeyFilter` (`page`/`size`/`orders`). Saralanadigan ustunlar: `ID`, `NAME`,
`ROLE`, `CREATED_AT`, `LAST_USED_AT`. Standart: `CREATED_AT DESC`. Javobdagi qatorlar
hech qachon `rawKey`ni o'z ichiga olmaydi — faqat `keyPrefix`.

---

### `DELETE /api/settings/api-keys/{id}` — bekor qilish (revoke)

Body yo'q. Kalitni darhol ishlamaydigan qiladi (`revoked_at` belgilanadi, o'chirilmaydi —
audit tarixi saqlanadi). Boshqa kompaniyaning kalitini bekor qilib bo'lmaydi (`404`).

---

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

Hozircha faqat **Uysot CRM**, OAuth2 authorization-code oqimi (Google'nikiga o'xshash:
`client_id`/`client_secret`/`redirect_uri`/`state`). Har bir kompaniya o'zining Uysot
OAuth ilovasini ro'yxatdan o'tkazadi va `client_id`/`client_secret`ni shu yerdan
kiritadi; `authorize_url`/`token_url`/`redirect_uri` esa global (`voice-agent
.integration.uysot.*`) — bitta Uysot instansiyasi hammaga bir xil.

**Muhim:** Uysot'ning haqiqiy OAuth endpoint'lari hali noma'lum — konfiguratsiya bo'sh
bo'lsa, bu bo'lim `502 Bad Gateway` (`ExternalServiceException`) bilan javob beradi,
lekin ilova o'zi ishlayveradi. `crm_integration.client_secret`/tokenlar AES-256-GCM bilan
shifrlanadi (`ENCRYPTION_SECRET_KEY` — alohida, `API_KEY`/`JWT_SECRET`dan mustaqil).

### `GET /api/settings/integrations` — joriy holat

**Response** (`CrmIntegration`) — hech qachon `client_secret`/tokenlarni qaytarmaydi:

```json
{
  "companyId": 1, "provider": "UYSOT", "clientId": "abc123",
  "hasClientSecret": true, "status": "CONNECTED", "connectedAt": "2026-08-02T10:00:00Z"
}
```

`status`: `NOT_CONNECTED` (client_id/secret hali kiritilmagan yoki OAuth hali
yakunlanmagan) → `CONNECTED` (token bor, ishlatilmoqda) → `ERROR` (refresh muvaffaqiyatsiz —
`CrmClient` avtomatik global statik tokenga qaytadi, agar u sozlangan bo'lsa).

### `PUT /api/settings/integrations/uysot` — client_id/secret saqlash

**Request body** (`ConnectIntegrationRequest`):

```json
{ "clientId": "abc123", "clientSecret": "shh..." }
```

Saqlagandan keyin holat `NOT_CONNECTED`ga qaytadi (yangi kalit — eski token endi
ishlamaydi) — keyingi qadam autentifikatsiya URL'iga o'tish.

### `GET /api/settings/integrations/uysot/authorize-url` — OAuth boshlash

**Response** (`AuthorizeUrlResponse`):

```json
{ "authorizeUrl": "https://uysot.uz/oauth/authorize?client_id=abc123&redirect_uri=...&response_type=code&state=..." }
```

Panel shu URL'ga brauzerni yo'naltiradi. `client_id`/`client_secret` hali
saqlanmagan bo'lsa — `400`.

### `GET /api/settings/integrations/uysot/callback?code=&state=` — OAuth qaytishi

**Autentifikatsiya talab qilinmaydi** — Uysot brauzerni to'g'ridan-to'g'ri shu yerga
qaytaradi, na `X-Api-Key`, na `Authorization` sarlavhasi bilan. `state` (imzolangan,
ichida `companyId` bor) so'rovni tasdiqlaydi. Muvaffaqiyatda holat `CONNECTED`ga
o'tadi; token almashinuvi muvaffaqiyatsiz bo'lsa — `502`.

### `DELETE /api/settings/integrations/uysot` — uzish

Body yo'q. Tokenlarni tozalaydi, holatni `NOT_CONNECTED`ga qaytaradi. `client_id`/secret
o'chirilmaydi — qayta ulash uchun avtorizatsiya URL'iga qaytish yetarli.
