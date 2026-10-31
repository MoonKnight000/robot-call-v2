# Kompaniyalar API

`uz.murodjon.robotcallv2.company` · huquq: **COMPANY_READ** / **COMPANY_EDIT**; kompaniya yaratish, ro'yxati va statusi — **PLATFORM_ADMIN** (pastga qarang)

Kompaniya — platformadagi tenant chegarasi (ROADMAP Bosqich B): kampaniya,
ssenariy, kontakt, DNC, audit va h.k. hammasi `company_id` bilan shu qatorga
bog'langan. Kompaniya har bir so'rovda tokendan olinadi: JWT ichidagi
`app_user.company_id` controller kirishida `@CurrentCompanyId` bilan argumentga
aylanadi. Tokensiz so'rov uchun default kompaniya **yo'q** — bunday so'rov rad
etiladi (`NO_USER_SESSION`). `voice-agent.company.default-id` faqat startupdagi
so'rovsiz ishlar uchun (bootstrap, TTS warmup, simulyatsiya).

**Rol bo'linishi (report #3):** `SUPERADMIN` — platforma xodimi, hech qaysi
kompaniyaga tegishli emas, faqat tenantlarni boshqaradi (yaratish, ro'yxat,
holat). `ADMIN` — kompaniyaning o'z admini, faqat **o'z** kompaniyasini
ko'radi/tahrirlaydi va **hech qachon o'z holatini o'zi o'zgartira olmaydi**
— avval bu yerda `PUT /api/companies/{id}` orqali `status` ham
o'zgartirilar edi, endi bu maydon butunlay olib tashlangan, faqat
`SUPERADMIN` (`PUT /api/companies/{id}/platform`) o'zgartira oladi. `SUPERADMIN`
roli `POST /api/users/invite` / `PUT /api/users/{id}/role` orqali
berilmaydi (`UserService` rad etadi) — birinchi superadmin hisobi qo'lda,
to'g'ridan-to'g'ri bazaga yoziladi.

**Ikki resurs bor:**
- **Company** — identifikatsiya: nom, holat (`ACTIVE`/`SUSPENDED`/`DELETED`/`EXPIRED`), logotip URL'i
  va manzil (backend-uchun-talablar.md §5 — sozlama emas, identifikatsiya
  ma'lumoti sifatida shu yerda).
- **CompanyConfig** — sozlamalar: qat'iy qo'ng'iroq oralig'i, timezone, kontakt
  siyosati, tatil kunlari. Har bir kompaniya yaratilganda avtomatik default config bilan
  ta'minlanadi (09:00–20:00, `Asia/Tashkent`). **Til va §11.1 ochiqlik matni bu yerda yo'q:**
  qo'ng'iroq tili — AI agentniki (`language` + `perLanguage` ovozlari, [ai-agents.md](ai-agents.md)),
  ochiqlik matni — ssenariyniki yoki platformaniki. **Telefoniya dvigateli ham yo'q** — uni faqat
  platforma ko'radi va o'zgartiradi ([pastda](#get--put-apicompaniesidtelephony-engine--telefoniya-dvigateli)).

Caller ID bu yerda emas — trunk darajasida (`sip_trunk.callerId`), chunki bitta
kompaniyaning bir nechta trunki har xil caller ID bilan bo'lishi mumkin.
Batafsil: [sip-trunks.md](sip-trunks.md).

Umumiy javob shakli, xatolar va pagination konventsiyasi uchun
[README.md](README.md)ga qarang.

---

## `POST /api/companies` — yangi kompaniya

**SUPERADMIN-only.**

**Request body** (`CreateCompanyRequest`):

```json
{ "name": "Ikkinchi kompaniya" }
```

Yaratilgan kompaniya `status: "ACTIVE"` bilan va yuqorida aytilgan default
`CompanyConfig` bilan boshlanadi — sozlamalarni keyin
`PUT /api/companies/{id}/config` orqali tahrirlang.

**Response** (`Company`):

```json
{ "id": 2, "name": "Ikkinchi kompaniya", "status": "ACTIVE", "createdAt": "2026-08-01T09:00:00Z",
  "logoFileId": null, "address": null, "maxConcurrentCalls": null, "disabledFeatures": [], "disabledIntegrations": [], "expiresAt": null }
```

`maxConcurrentCalls` — kompaniyaning parallel dialer qo'ng'iroqlari limiti (tarif); `null` —
platforma standarti. Faqat `SUPERADMIN` o'zgartiradi
([`PUT /api/companies/{id}/platform`](#put-apicompaniesidplatform--kompaniyani-platforma-tomonidan-tahrirlash)).

---

## `POST /api/companies/list` — ro'yxat

**SUPERADMIN-only** — barcha tenantlar ro'yxati; kompaniyaning o'z ADMINi
o'z kompaniyasidan boshqasini ko'ra olmaydi.

Body — `CompanyFilter`:

```json
{ "page": 0, "size": 20, "orders": { "NAME": "ASC" }, "search": "Ikkinchi", "status": null }
```

`search` — nom bo'yicha erkin qidiruv, bo'sh/`null` — filtr yo'q. `status` — `ACTIVE`/`SUSPENDED`/`DELETED`/`EXPIRED`; `null` bo'lsa
`DELETED` kompaniyalar ro'yxatda ko'rinmaydi (o'chirilganlarni ko'rish va tiklash uchun `"status": "DELETED"`). Saralanadigan
ustunlar: `ID`, `NAME`, `STATUS`, `CREATED_AT`. Standart: `NAME ASC`.

Javob — `PageableData<Company>` (`Company` shakli yuqorida).

---

## `GET /api/companies/{id}` — bitta kompaniya

`ADMIN` yoki `SUPERADMIN`. `ADMIN` uchun `id` faqat o'z kompaniyasiniki
bo'lishi mumkin — boshqa kompaniyaniki so'ralsa `404` (mavjudligini
oshkor qilmaslik uchun — `NotFoundException`, `403` emas). `SUPERADMIN`
istalgan `id`ni ko'ra oladi.

**Response** — `Company` (yuqoridagi shakl). Topilmasa (yoki boshqa
kompaniyaniki, `ADMIN` uchun) — `404`.

---

## `PUT /api/companies/{id}` — yangilash

`ADMIN`, faqat o'z kompaniyasi (yuqoridagi `GET` bilan bir xil scoping).

**Request body** (`UpdateCompanyRequest`):

```json
{ "name": "Ikkinchi kompaniya", "address": "Toshkent, Chilonzor" }
```

**`status` bu yerda yo'q** (report #3) — kompaniya o'zini o'zi
faollashtira/to'xtata olmaydi, pastga qarang. `address` ixtiyoriy —
`null`/bo'sh qoldirilsa tozalanadi. **`logoFileId` bu yerda ham yo'q** — logo
faqat pastdagi `POST /api/companies/{id}/logo` orqali o'zgaradi, qo'lda
arbitrar id/URL sifatida yuborib bo'lmaydi.

Javob — yangilangan `Company`.

---

## `PUT /api/companies/{id}/platform` — kompaniyani platforma tomonidan tahrirlash

**SUPERADMIN-only.** Kompaniya haqidagi hamma platforma sozlamalari bitta formada: nom, manzil, holat
(bloklash / blokdan chiqarish / o'chirish), amal qilish muddati, parallel qo'ng'iroqlar limiti va feature'lar.
**Barcha maydonlar almashtiriladi** — frontend yuklangan `Company` ni o'zgartirib qaytaradi.

**Request body** (`UpdateCompanyByPlatformRequest`):

```json
{
  "name": "Ikkinchi kompaniya",
  "address": "Toshkent, Chilonzor",
  "status": "ACTIVE",
  "expiresAt": "2026-12-31T23:59:59Z",
  "maxConcurrentCalls": 40,
  "disabledFeatures": ["ALERTING"],
  "disabledIntegrations": ["BITRIX24"]
}
```

| Maydon | Turi | Majburiy | Izoh |
|---|---|---|---|
| `name` | `string` (≤120) | ✅ | |
| `address` | `string` (≤255) \| `null` | ❌ | `null` — tozalaydi |
| `status` | `ACTIVE` \| `SUSPENDED` \| `DELETED` \| `EXPIRED` | ✅ | `SUSPENDED` — bloklash, `ACTIVE` — blokdan chiqarish, `DELETED` — soft delete. `ACTIVE` va `EXPIRED` ikkalasi "platforma yopmagan" degani: ochiq yoki muddati tugaganligi `expiresAt` dan kelib chiqadi |
| `expiresAt` | ISO vaqt \| `null` | ❌ | obuna tugaydigan vaqt; `null` — muddat yo'q |
| `maxConcurrentCalls` | `number` (1–10000) | `null` | ❌ | kompaniya tarifiga ko'ra bir vaqtda nechta dialer qo'ng'irog'i; `null` — platforma standarti (`voice-agent.dialer.max-concurrent-calls-per-company`). Keyingi dialer tick'idan (≈5 s) kuchga kiradi; platformaning umumiy chegarasi (RTP portlar) bundan ustun. Hozirgi holat — [`GET /api/concurrency`](concurrency.md) |
| `disabledFeatures` | `Feature[]` | ✅ | o'chirilgan feature'lar (`VOICE_AGENT`, `CHAT_AGENT`, `ALERTING`, `INTEGRATIONS`, `QUALITY_ASSURANCE`); `[]` — hammasi yoqiq |
| `disabledIntegrations` | `IntegrationProvider[]` | ✅ | o'chirilgan integratsiya provayderlari (`UYSOT`, `AMOCRM`, `KOMMO`, `BITRIX24`, `GOOGLE_DRIVE`, `NOTION`, `ASTERISK`); `[]` — hammasi yoqiq |

Javob — yangilangan `Company`. Audit: `COMPANY_PLATFORM_UPDATE`.

**Holat va muddat:**
- `SUSPENDED` / `DELETED` — platformaning qarori, sana qanday bo'lishidan qat'iy nazar turadi. `DELETED` — yumshoq o'chirish: ma'lumot bazada qoladi, kompaniya `POST /api/companies/list` da (standart filtrda) ko'rinmaydi; tiklash — `status: "ACTIVE"` yuborish.
- `ACTIVE`/`EXPIRED` yuborilganda: `expiresAt` o'tib ketgan bo'lsa kompaniya shu zahoti `EXPIRED` bo'ladi,
  kelajakda yoki `null` bo'lsa shu zahoti `ACTIVE`. Muddati tugagan kompaniyani ochish uchun sanani uzaytiring.
- Muddat o'tganda `CompanyExpiryJob` (har daqiqa) `ACTIVE` kompaniyani o'zi `EXPIRED` qiladi (audit `COMPANY_EXPIRED`).
- `SUSPENDED`, `DELETED` va `EXPIRED` bir xil oqibat beradi: sessiyalar bekor, login/refresh `403 COMPANY_SUSPENDED`,
  API kalitlar va dialer to'xtaydi, kiruvchi qo'ng'iroqlar rad etiladi. Holat o'zgargandagina sessiyalar bekor qilinadi.

**Feature'lar:** `disabledFeatures` — platforma kompaniya uchun o'chirgan mahsulotlar; `[]` — hammasi yoqiq.
Holat `company_feature` jadvalida (kompaniya × feature × `enabled`); qatori yo'q feature yoqiq hisoblanadi, shuning
uchun keyinroq qo'shilgan yangi feature barcha kompaniyada o'zi yoqiq bo'ladi. O'chirish mumkin bo'lganlar:

| Feature | Nimani yopadi (huquq guruhlari) |
|---|---|
| `VOICE_AGENT` | kampaniyalar, qo'ng'iroqlar, jonli qo'ng'iroqlar, operatorlar, kontaktlar, DNC, kiruvchi yo'nalishlar, call flow, navbatlar, javobsiz qo'ng'iroqlar, SIP trunklar, ovozlar (`CAMPAIGN`, `CALL`, `LIVE`, `OPERATOR`, `CONTACT`, `DO_NOT_CALL`, `INBOUND_ROUTE`, `CALL_FLOW`, `CALL_QUEUE`, `MISSED_CALL`, `SIP_TRUNK`, `VOICE`) |
| `CHAT_AGENT` | Telegram botlar va chat tarixi (`CHAT_AGENT`) |
| `ALERTING` | alert qoidalari (`ALERT`) |
| `INTEGRATIONS` | CRM va boshqa integratsiyalar (`INTEGRATION`) |
| `QUALITY_ASSURANCE` | review inbox va scorecard (`QUALITY_REVIEW`) |

`CORE` — platformaning o'zi (dashboard, hisobotlar, audit, foydalanuvchilar, rollar, kompaniya, billing, API kalitlar,
AI modellar, bildirishnoma kanallari) va voice hamda chat agent **umumiy ishlatadigan agent/ssenariy/bilim bazasi
muharriri** (`AI_AGENT`, `SCENARIO`). U o'chirilmaydi: `disabledFeatures` ichida `CORE` bo'lsa
`400 COMPANY_FEATURE_NOT_SWITCHABLE`.

**Foydalanuvchilardan huquq olib tashlash:** har bir huquq aynan bitta feature'ga tegishli
(`GET /api/roles/permissions` har guruh bilan `feature` ni qaytaradi). Feature o'chirilganda uning barcha huquqlari
(`*_READ`/`*_EDIT`) kompaniyaning **hamma foydalanuvchisi va API kalitidan** olib tashlanadi — rolida bo'lsa ham:
endpointlari `403` beradi, `GET /api/auth/me` dagi `permissions` da ko'rinmaydi, kompaniya admini ularni boshqa
rolga bera olmaydi (o'zida yo'q huquqni bera olmaydi). Rollarning o'zi o'zgarmaydi, shuning uchun feature qayta
yoqilganda hamma narsa avvalgidek qaytadi. Ro'yxat o'zgarganda kompaniya foydalanuvchilari sessiyalari bekor
qilinadi (keyingi login yangi huquqlar bilan).

**Ish vaqtida ham to'xtaydi** (faqat panel/API emas — ma'lumot saqlanadi, feature qayta yoqilsa davom etadi):

| Feature | O'chiq bo'lsa |
|---|---|
| `VOICE_AGENT` | dialer kompaniya kampaniyalarini tanlamaydi; navbatda turgan target terilmaydi; qo'lda va brauzer (widget/test) qo'ng'irog'i `403 COMPANY_FEATURE_DISABLED`; kiruvchi qo'ng'iroq "kompaniya mavjud emas" xabari bilan yopiladi |
| `CHAT_AGENT` | `POST /api/public/widgets/{key}/chat` va `.../chat/{session}` — `403 COMPANY_FEATURE_DISABLED`; `POST /api/public/telegram/{botId}` `200` qaytaradi (Telegram qayta yubormasligi uchun), lekin javob berilmaydi |
| `ALERTING` | alert scheduler kompaniya qoidalarini baholamaydi, bildirishnoma yuborilmaydi |
| `INTEGRATIONS` | qo'ng'iroqdan keyingi CRM yozuvlari (eslatma, bosqich, tarix) va CRM'dan o'qish bajarilmaydi — qo'ng'iroq CRM'siz davom etadi; Uysot webhook `skipped` javobi bilan qaytadi |
| `QUALITY_ASSURANCE` | qo'ng'iroqdan keyingi sifat hakami (LLM) va operator scorecard ishga tushmaydi |

**Integratsiyalar:** `disabledIntegrations` — `INTEGRATIONS` feature'i yoqiq bo'lganda ham alohida provayderni yopadi. Holat `company_integration_access` jadvalida (kompaniya × provayder × `enabled`; qatori yo'q — yoqiq). O'chirilgan provayderni ulab bo'lmaydi, ulangani ishlamaydi, kompaniya katalogida `DISABLED_BY_PLATFORM` ko'rinadi ([settings.md](settings.md#5-crm-integratsiyalari-apisettingsintegrations)).

Faqat shu kompaniyaga tegadi: boshqa kompaniyalar o'z feature'lari bilan ishlayveradi.

Xatolar: `400 VALIDATION_FAILED`, `400 COMPANY_FEATURE_NOT_SWITCHABLE` (`CORE`), `404`, `409 COMPANY_DEFAULT_PROTECTED` —
platforma administratori kiradigan default kompaniya (`voice-agent.company.default-id`) bloklanmaydi,
o'chirilmaydi va unga muddat qo'yilmaydi.


---

## `POST /api/companies/{id}/logo` — logotip yuklash

`ADMIN`, faqat o'z kompaniyasi. `multipart/form-data`, maydon nomi `file`.
Faqat rasm (`image/png`, `image/jpeg`, `image/webp`), maksimum **5 MB** —
mos kelmasa `400`, MinIO ishlamasa `502`. Muvaffaqiyatli yuklangan fayl
`company.logoFileId`ni almashtiradi, boshqa hech qaysi maydonga tegmaydi.
Rasmning o'zini olish uchun [files.md](files.md)dagi
`GET /api/files/{logoFileId}` ishlatiladi — javobdagi `logoFileId` xom MinIO
URL emas, shu endpointga beriladigan id.

Javob — yangilangan `Company` (yuqoridagi shakl, yangi `logoFileId` bilan).

---

## `GET /api/companies/{id}/config` — sozlamalar

`ADMIN`, faqat o'z kompaniyasi (yuqoridagi `GET /api/companies/{id}` bilan
bir xil scoping — boshqa kompaniyaniki so'ralsa `404`).

**Response** (`CompanyConfig`):

```json
{
  "id": 5,
  "companyId": 2,
  "dialWindowStart": "09:00",
  "dialWindowEnd": "20:00",
  "timezone": "Asia/Tashkent",
  "maxAttemptsPerPhonePerWeek": 3,
  "quietDaysAfterConversation": 1,
  "respectPromisedDate": true,
  "humanLikePersonaAllowed": false,
  "scorecardReviewThreshold": 60,
  "createdAt": "2026-08-01T09:00:00Z"
}
```

**Kontakt siyosati** — bitta telefon raqamiga kompaniyaning **barcha** kampaniyalari
bo'ylab qo'ng'iroq chastotasi (kampaniyaning `dailyCallCap` i faqat o'z kampaniyasini
ko'radi). Yangi kompaniyada `respectPromisedDate: true` (to'lashga va'da berib qo'ygan odamga
qo'ng'iroq qilish — raqamlardan qat'i nazar shikoyat sababi), `maxAttemptsPerPhonePerWeek: 3` va
`quietDaysAfterConversation: 1`; kompaniya ularni O'zbekiston qonunchiligiga qarab o'zgartiradi
(taqqoslash uchun: AQSh Regulation F — 7 kunda 7 urinish, suhbatdan keyin 7 kun tinch). `V2`
migratsiyasigacha yaratilgan kompaniyalarning bu ikki qiymati o'zgartirilmagan.

Yangi kompaniya `dialWindowStart`/`dialWindowEnd` = `09:00`–`20:00` bilan yaratiladi (ilgari
`07:00`–`23:00`). `V39` migratsiyasigacha mavjud bo'lgan kompaniyalarning qiymatlari
o'zgartirilmagan.

| Maydon | Ma'nosi |
|---|---|
| `maxAttemptsPerPhonePerWeek` | Istalgan 7 kunda bitta raqamga ko'pi bilan shuncha **terilgan** outbound urinish. `0` — cheklov yo'q. Kiruvchi qo'ng'iroqlar va terilmay qolgan urinishlar sanalmaydi |
| `quietDaysAfterConversation` | Haqiqiy suhbatdan (javob berilgan; avtojavob, noto'g'ri raqam emas) keyin shuncha kun hech bir kampaniya qo'ng'iroq qilmaydi. `0` — yo'q. Disposition'i `CALLBACK_REQUESTED` bo'lgan suhbat (mijozning o'zi qayta qo'ng'iroq so'ragan) tinch davrni boshlamaydi |
| `respectPromisedDate` | `true` — raqam to'lashga va'da bergan sana o'tmaguncha unga qo'ng'iroq qilinmaydi (keyingi kun ertalabdan) |

Siyosat nishonni **o'tkazib yubormaydi**: raqam terilmaydi, nishon ruxsat etilgan
vaqtga qayta rejalashtiriladi va urinish sarflanmaydi (`attempts` qaytariladi, chunki
qo'ng'iroq bo'lmagan). Log: `held until ... by the contact policy`.

**`humanLikePersonaAllowed`** — kompaniya agentlari o'zini inson sifatida tanishtirishi
(`persona: HUMAN_LIKE`) va §11.1 "avtomatik qo'ng'iroq" ogohlantirishini aytmasligiga
ruxsat. Bu **huquqiy qaror**: `false` bo'lsa `HUMAN_LIKE` agentni saqlab bo'lmaydi
(`409 AI_AGENT_HUMAN_LIKE_NOT_ALLOWED`), avval saqlanganlari esa qo'ng'iroqda
`AI_ASSISTANT` bo'lib, ogohlantirish bilan gapiradi. Har o'zgarishi audit log'ga
(`COMPANY_CONFIG_UPDATE`) yoziladi. Yangi kompaniyada `false`; V15 migratsiyasigacha
mavjud bo'lgan kompaniyalarda `true` (xatti-harakat o'zgarmasligi uchun).

- **Faqat platform admin o'zgartiradi.** Kompaniya admini qiymatni o'zgartirmoqchi bo'lsa —
  `403 HUMAN_LIKE_PERSONA_PLATFORM_ONLY` (o'zgarmagan qiymatni qayta yuborish mumkin): aks
  holda kompaniya §11.1 ogohlantirishini o'zi o'chira olardi.
- Ruxsat bo'lsa ham `HUMAN_LIKE` agent **yozib olish ogohlantirishini** aytadi ("Suhbat sifat
  nazorati uchun yozib olinmoqda.") — faqat "avtomatik qo'ng'iroq" qismi tushib qoladi.

**Ochiqlik (§11.1)** — qo'ng'iroq boshida aytiladigan "avtomatik qo'ng'iroq, yozib olinmoqda"
matni kompaniya sozlamasi emas: ssenariy o'z `disclosureText` ini bersa o'sha
(`docs/api/scenarios.md`), bo'lmasa platformaning o'z matni (qo'ng'iroq tiliga qarab o'zbekcha
yoki ruscha) aytiladi.

Topilmasa `404` — amalda bo'lmasligi kerak, chunki har bir kompaniya
yaratilganda avtomatik config oladi.

---

## `PUT /api/companies/{id}/config` — sozlamalarni yangilash

`ADMIN`, faqat o'z kompaniyasi (yuqoridagi kabi scoping).

**Request body** (`UpdateCompanyConfigRequest`):

```json
{ "dialWindowStart": "08:00", "dialWindowEnd": "21:00", "timezone": "Asia/Tashkent" }
```

| Maydon | Majburiymi | Izoh |
|---|---|---|
| `dialWindowStart`/`dialWindowEnd` | ✅ | **Qat'iy chegara** — `DialerService` har bir kampaniyani o'z oynasi BILAN BIRGA shu oraliqqa ham tekshiradi; kampaniya bu kompaniyaning oralig'idan tashqariga chiqa olmaydi, hatto kampaniyaning o'z oynasi kengroq bo'lsa ham. |
| `timezone` | ✅ (`@NotBlank`) | — |
| `maxAttemptsPerPhonePerWeek` | ❌ | `0..100`; `null` — o'zgarmaydi. Yuqoridagi "Kontakt siyosati" |
| `quietDaysAfterConversation` | ❌ | `0..90`; `null` — o'zgarmaydi |
| `respectPromisedDate` | ❌ | `null` — o'zgarmaydi |
| `humanLikePersonaAllowed` | ❌ | `null` — o'zgarmaydi. Qiymatni faqat platform admin o'zgartira oladi, aks holda `403 HUMAN_LIKE_PERSONA_PLATFORM_ONLY`. O'zgarishi audit log'ga yoziladi |
| `scorecardReviewThreshold` | ❌ | `0..100`, standart `60`; `null` — o'zgarmaydi. Operator qo'ng'irog'i bahosi shundan past bo'lsa, tekshiruv inbox'iga tushadi ([scorecards.md](scorecards.md)). `0` — faqat norozi mijozli qo'ng'iroqlar tushadi |

**Response** — yangilangan `CompanyConfig`.

---

## `GET` / `PUT /api/companies/{id}/telephony-engine` — telefoniya dvigateli

**SUPERADMIN-only** (`PLATFORM_ADMIN`). Kompaniya qo'ng'iroqlari qaysi media stekda ishlashi —
platformaning qarori: kompaniya o'z SIP trunklarini qo'shadi, lekin ular ostidagi dvigatelni na ko'radi,
na o'zgartiradi. Shuning uchun u `CompanyConfig` javobida ham yo'q. Kompaniya foydalanuvchisi so'rasa — `403`.

`PUT` body (`UpdateTelephonyEngineRequest`):

```json
{ "telephonyEngine": "LIVEKIT" }
```

| Maydon | Majburiymi | Izoh |
|---|---|---|
| `telephonyEngine` | ✅ | `ASTERISK` (standart) yoki `LIVEKIT` |

Ikkala endpoint javobi — `TelephonyEngineResponse`: `{ "telephonyEngine": "ASTERISK" }`. `PUT` audit'ga
`COMPANY_TELEPHONY_ENGINE_UPDATE` (`ASTERISK -> LIVEKIT`) yozadi. Kompaniya topilmasa — `404`.

---

## Platforma uchun to'liq ro'yxat — kompaniyaning hamma sozlamalari

Hammasi `SUPERADMIN` token bilan, istalgan kompaniya `{id}` si uchun ishlaydi:

| Nima | Endpoint |
|---|---|
| Logotip (rasm) | `POST /api/companies/{id}/logo` |
| Nom, manzil, holat (blok/unblok/o'chirish), muddat, limit, feature'lar — bitta forma | `PUT /api/companies/{id}/platform` |
| Ish vaqti, tatil kunlari, limitlar | `GET` / `PUT /api/companies/{id}/config` |
| Telefoniya dvigateli (ASTERISK / LIVEKIT) | `GET` / `PUT /api/companies/{id}/telephony-engine` |
| Foydalanuvchilar, rollar | `/api/companies/{id}/users`, `/roles` — [users.md](users.md) |
| SIP trunklar | `/api/companies/{id}/sip-trunks` — [sip-trunks.md](sip-trunks.md) |
