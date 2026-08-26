   # Sozlamalar API (§11)

Har bir bo'lim o'z paketida: `engine`, `voice`, `aimodel`, `notification` (matritsa qismi),
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

## Engine (STT/TTS/realtime tanlovi) — `uz.murodjon.uysotvoice.engine`

Kompaniya qo'ng'iroqlari **qaysi dvigatelda** ishlashini o'zi tanlaydi. Ilgari bu
butun jarayon uchun bitta edi (`voice-agent.stt.provider` / `voice-agent.tts.provider`);
endi ular faqat **standart** — kompaniya hech narsa tanlamagan holat uchun.

Ikki rejim bor va ular bir-birining sozlamasi emas, alohida pipeline:

| `mode` | O'qiladigan maydonlar | Nima ishlaydi |
|---|---|---|
| `CASCADE` (standart) | `sttProvider`, `ttsProvider` | STT → LLM → TTS zanjiri |
| `REALTIME` | `realtimeProvider` | bitta speech-to-speech dvigatel (audio kiradi, audio chiqadi) |

`REALTIME` **faqat dvigatel ulangan build'da** tanlanadi. Hech biri ro'yxatdan
o'tmagan bo'lsa, `options.realtime` bo'sh massiv qaytaradi — frontend rejim tugmasini
o'shanga qarab yashiradi — va `PUT` `400 ENGINE_REALTIME_NOT_AVAILABLE` bilan rad
etadi. Ilova baribir ko'tariladi: realtime dvigatelsiz deploy normal holat.

Birinchi dvigatel — **`gemini-live`** (Gemini Live `BidiGenerateContent` WebSocket).
U `GEMINI_API_KEY` o'rnatilgan bo'lsa o'zini ro'yxatga oladi, ya'ni LLM uchun
ishlatilayotgan bir xil Gemini kaliti. Butun rejimni o'chirish uchun —
`voice-agent.realtime.enabled=false` (u holda `options.realtime` bo'sh qaytadi).

`REALTIME` tanlagan kompaniyaning qo'ng'irog'ida nima o'zgaradi:

| | `CASCADE` | `REALTIME` |
|---|---|---|
| Tanib olish / gapirish | STT + TTS provayderlari | dvigatelning o'zi |
| Endpointing, barge-in | VAD + `SpeechGate` | dvigateldan |
| §11.1 disclosure | TTS bilan aytiladi | **xuddi shunday** — matni yuridik majburiyat, dvigatelga qayta yozdirilmaydi |
| Avtojavob detektori (AMD) | VAD | **xuddi shunday** — go'shakni odam ko'tardimi degan savolga dvigatel javob bermaydi |
| Ssenariy tool'lari, FSM, outcome | ishlaydi | **xuddi shunday** (bir xil `DialogTools`) |
| Transkript, `call_result`, CRM izohi | yoziladi | **xuddi shunday** |
| "Texnik" tab token/LLM latency | to'ldiriladi | **bo'sh (0/null)** — dvigatel token emas, sessiya vaqti bo'yicha hisoblaydi; o'ylab topilgan raqam o'lchov bo'lib ko'rinardi |
| Kompaniya ovozi (`speed`/`pitch`, `tts_voice`) | qo'llanadi | **qo'llanmaydi** — ovoz `GEMINI_LIVE_VOICE` bilan tanlanadi |
| Faktlar (summa, sana) | promptda beriladi | promptda **yo'q** — `getCallFact` tool'i orqali olinadi |
| FactGuard (§4.4) | gap **aytilishidan oldin** bloklanadi | gap **aytilgandan keyin** tekshiriladi — pastga qarang |

### §4.4 `REALTIME`da: ikki qatlam

**1-qatlam — oldini olish: faktlar promptga umuman kirmaydi.**

Cascade dvigatelga qarz summasi va muddatini ko'rsatmada beradi va bunga haqli, chunki
javobni aytilishidan oldin o'qib bloklay oladi. Realtime'da esa oldin gapiriladi.
Shuning uchun qiymatlar ko'rsatmaga **yozilmaydi** — dvigatelga faqat qaysi
ma'lumotlar *mavjudligi* aytiladi, qiymatni esa aytish paytida `getCallFact` tool'i
orqali oladi va aynan qaytgan holida aytishi shart. Summani hech qachon eshitmagan
dvigatel uni noto'g'ri eslay olmaydi.

Narxi — raqam aytilishidan oldin bitta tool round trip, liniyada qisqa pauza sifatida
eshitiladi. `voice-agent.realtime.facts-in-prompt=true` (`REALTIME_FACTS_IN_PROMPT`)
cascade shakliga qaytaradi — kafolat o'rniga latency.

Ma'lumot yo'q bo'lsa `getCallFact` `MA'LUMOT YO'Q` qaytaradi va dvigatelga uni
aytmaslik, mijozdan so'rash yoki operatorga o'tkazish buyuriladi — bo'sh qiymat ham,
taxmin ham qaytmaydi.

### 2-qatlam — aniqlash: FactGuard darvoza emas, audit

Cascade'da model javobi matn bo'lib keladi, `FactGuard` uni sintez qilinishidan oldin
tekshiradi va mos kelmasa **bloklaydi** — mijoz eshitmaydi. Realtime dvigatel esa
to'g'ridan-to'g'ri ovoz chiqaradi; o'sha ovozning transkripti — kimdir ko'radigan
birinchi matn, va u kelganda mijoz allaqachon eshitib bo'lgan. Oldini olish imkoni yo'q.

Shuning uchun javob boshqacha:

1. `ERROR` log — qaysi raqamlar, qaysi bosqichda, qanday matnda;
2. `call_attempt.error_message` ga yoziladi (qo'ng'iroqni ko'rib chiqadigan odam ko'radi);
3. `voice.dialog.fact.guard.spoken` metrikasi — `...blocks` dan **alohida**, chunki
   ma'nosi boshqa: blok — tizim ishlagani, bu esa hodisa. Shunga alert qo'ying;
4. `voice-agent.dialog.fact-violation-escalate-after` (default `1`) ga yetganda qo'ng'iroq
   **operatorga o'tkaziladi** — noto'g'ri summa eshitgan mijozga qayta urinish emas, odam
   kerak. `0` — faqat log va flag, o'tkazish yo'q.

O'tkazish o'sha zahoti emas, joriy gap eshitilib bo'lgandan keyin bo'ladi
(`finishWhenSpoken`) — dvigatelni so'z o'rtasida kesish chalkashgan mijozni battar
chalkashtiradi.

`FactGuard` **so'z bilan aytilgan summalarni ham** o'qiydi ("bir million besh yuz ming"
→ 1500000) — bu ikkala pipeline uchun ham amal qiladi, lekin realtime uchun zarur edi:
u yerda transkript nutqdan olinadi, ya'ni shakl dvigatel qanday aytgan bo'lsa shunday,
va cascade'dagi "summani raqam bilan yoz" degan prompt qoidasi bu yerda ma'nosiz (TTS
bosqichi yo'q).

Noto'g'ri ishlashning oldi olingan: yolg'iz `ming` son deb hisoblanmaydi ("ming rahmat"
— odob, da'vo emas), `nol` esa zanjirni uzadi (raqam-ma-raqam o'qilgan shartnoma raqami
undan oldingi summaga qo'shilib ketmasligi uchun), sanalar esa avvalgidek yil oynasi
bilan chiqarib tashlanadi ("ikki ming yigirma oltinchi" = 2026).

> Qolgan cheklov: faqat **o'zbekcha** son-so'zlari o'qiladi. `ru-RU` qo'ng'irog'ida ruscha
> so'z bilan aytilgan summa hamon o'tib ketadi.

Har bir qo'ng'iroq boshlanganda **bir marta** o'qiladi
(`EngineConfigService.findEffectiveByCompanyId`), keyin butun qo'ng'iroq davomida
o'zgarmaydi — sozlama qo'ng'iroq o'rtasida almashsa ham suhbat ikki dvigatelga
bo'linmaydi.

### `GET /api/settings/engine` — joriy tanlov

**Response** (`EngineConfig`) — hech qachon `404` qaytarmaydi; kompaniya hech narsa
tanlamagan bo'lsa `data` `null` bo'ladi va hamma qo'ng'iroq global standartda ishlaydi:

```json
{
  "companyId": 1, "mode": "CASCADE", "sttProvider": "yandex", "ttsProvider": "aisha",
  "realtimeProvider": null, "createdAt": "2026-08-02T10:00:00Z"
}
```

### `GET /api/settings/engine/effective` — amaldagi qiymat

`GET /api/settings/engine` kompaniya **nimani tanlaganini** aytadi; bu esa
**nima ishlashini**. Kompaniya hech narsa tanlamagan bo'lsa birinchisi `null`
qaytaradi va shu bilan tugaydi — frontend standart qaysi provayder ekanini bilmaydi.
Bu endpoint qo'ng'iroq boshlanganda bajariladigan aynan o'sha merge'ni qaytaradi
(`EngineConfigService.findEffectiveByCompanyId`), ya'ni sozlamalar ekranida
ko'rsatilgan nom qo'ng'iroqda gapiradiganning o'zi.

**Response** (`EffectiveEngineConfig`) — `data` hech qachon `null` emas:

```json
{ "mode": "CASCADE", "sttProvider": "yandex", "ttsProvider": "yandex", "realtimeProvider": null }
```

| Maydon | Izoh |
|---|---|
| `mode` | doim to'ldirilgan (`CASCADE` yoki `REALTIME`) |
| `sttProvider` / `ttsProvider` | `CASCADE`da doim to'ldirilgan — kompaniya tanlovi yoki `voice-agent.stt/tts.provider` standarti |
| `realtimeProvider` | build'da realtime dvigatel va standart bo'lmasa `null`; `REALTIME` rejimda esa doim to'ldirilgan |

Joriy rejim o'qimaydigan maydon ham to'ldirib yuboriladi (`REALTIME`da ham
`sttProvider` ko'rinadi) — bu rejim almashtirilganda nima bo'lishini oldindan
ko'rsatish uchun qulay, lekin qo'ng'iroqda o'qilmaydi.

**Frontend uchun:** `GET /api/tts/voices` javobi **shu build'da yoqilgan
provayderlarning** ovozlari bilan cheklangan — bu javobdagi `ttsProvider`ga
bog'liq emas. Kampaniya shu ro'yxatdan xohlagan provayderning ovozini tanlashi
mumkin (`TtsRouter` ovozni o'zining provayderi orqali gapiradi, `ttsProvider`
faqat ovoz tanlanmagan qo'ng'iroqlar uchun standart). `ttsProvider` shunchaki
qaysi provayder ovoz tanlanmagan holatda ishlatilishini bildiradi — ovozlar
ro'yxatini cheklamaydi.

### `GET /api/settings/engine/options` — tanlash mumkin bo'lganlar

**Response** (`EngineOptions`) — konstanta emas, ishlab turgan ilovada ro'yxatdan
o'tgan provayderlar. Kompilyatsiyaga kirmagan yoki kaliti yo'q provayder bu yerda
ko'rinmaydi:

```json
{ "stt": ["google", "yandex", "aisha"], "tts": ["google", "yandex", "aisha"], "realtime": [] }
```

### `PUT /api/settings/engine` — yangilash

**Request body** (`UpdateEngineConfigRequest`) — to'liq almashtirish; tashlab
ketilgan maydon o'sha tanlovni global standartga qaytaradi:

```json
{ "mode": "CASCADE", "sttProvider": "yandex", "ttsProvider": "aisha", "realtimeProvider": null }
```

| Maydon | Turi | Izoh |
|---|---|---|
| `mode` | enum | `CASCADE` (standart, `null` ham shu) / `REALTIME` |
| `sttProvider` | string | faqat `CASCADE`da o'qiladi; `options.stt` ichidan bo'lishi shart |
| `ttsProvider` | string | faqat `CASCADE`da o'qiladi; `options.tts` ichidan bo'lishi shart |
| `realtimeProvider` | string | faqat `REALTIME`da o'qiladi; `options.realtime` ichidan bo'lishi shart. Tashlab ketilsa `voice-agent.realtime.provider` standarti olinadi — u ham bo'sh bo'lsa nom majburiy |

Faqat tanlangan rejim o'qiydigan maydonlar tekshiriladi — qolganlari kelgan holida
saqlanadi, ya'ni `REALTIME`ga o'tib qaytgan kompaniya cascade tanlovini joyida topadi.

**Xatolar:** `400 ENGINE_STT_PROVIDER_UNKNOWN`, `400 ENGINE_TTS_PROVIDER_UNKNOWN`,
`400 ENGINE_REALTIME_NOT_AVAILABLE` (build'da dvigatel yo'q),
`400 ENGINE_REALTIME_PROVIDER_UNKNOWN`, `400 ENGINE_REALTIME_PROVIDER_REQUIRED`
(nom ham berilmadi, standart ham yo'q).

**Response** — yangilangan `EngineConfig`.

---

## Ovoz sozlamalari — `uz.murodjon.uysotvoice.voice`

Kompaniya gapirish tezligi va tembrni **standart** qilib o'zi uchun qayta
belgilashi mumkin (backend-uchun-talablar.md §6) — hech narsa belgilamasa,
`application.yml`/`voice-agent.tts.*`dagi global standart ishlatiladi. Har bir
qo'ng'iroq boshlanganda **bir marta** o'qiladi (`DialogEngine.startCall` →
`VoiceSettingsService.effective`, natija butun qo'ng'iroq davomida ishlatiladi),
`agent.tts.TtsRouter`ga uzatiladi.

> **Breaking change:** `provider` maydoni bu yerdan olib tashlandi — u
> `/api/settings/engine` ga ko'chdi (`ttsProvider`). Sabab: u bu yerda **hech qachon
> ishlamagan** (`TtsRouter` provayderni konstruktorda bir marta olib, bu ustunni
> umuman o'qimasdi), va endi bir xil tanlov TTS bosqichi umuman bo'lmaydigan realtime
> rejimni ham qamraydi. Mavjud qiymatlar migratsiyada `engine_config.tts_provider`ga
> ko'chiriladi.

### `GET /api/settings/voice` — joriy sozlama

**Response** (`VoiceSettings`) — hech qachon `404` qaytarmaydi, hech narsa
belgilanmagan bo'lsa hamma maydon `null`:

```json
{ "companyId": 1, "speed": null, "pitch": null, "createdAt": "2026-08-02T10:00:00Z" }
```

### `PUT /api/settings/voice` — yangilash

**Request body** (`UpdateVoiceSettingsRequest`) — har bir maydon ixtiyoriy,
`null` o'sha maydonni global standartga qaytaradi:

```json
{ "speed": 1.0, "pitch": 0.0 }
```

| Maydon | Turi | Izoh |
|---|---|---|
| `speed` | double, `0.1`–`3.0` | gapirish tezligi ko'paytiruvchisi (`1.0` = odatiy); har bir provayderda qo'llanadi |
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
