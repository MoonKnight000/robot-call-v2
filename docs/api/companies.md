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
`SUPERADMIN` (`PUT /api/companies/{id}/status`) o'zgartira oladi. `SUPERADMIN`
roli `POST /api/users/invite` / `PUT /api/users/{id}/role` orqali
berilmaydi (`UserService` rad etadi) — birinchi superadmin hisobi qo'lda,
to'g'ridan-to'g'ri bazaga yoziladi.

**Ikki resurs bor:**
- **Company** — identifikatsiya: nom, holat (`ACTIVE`/`SUSPENDED`), logotip URL'i
  va manzil (backend-uchun-talablar.md §5 — sozlama emas, identifikatsiya
  ma'lumoti sifatida shu yerda).
- **CompanyConfig** — sozlamalar: qo'llab-quvvatlanadigan tillar ro'yxati,
  aniq `defaultLanguage` maydoni (backend-uchun-talablar.md §13), qat'iy
  qo'ng'iroq oralig'i, timezone. Har bir kompaniya yaratilganda avtomatik
  default config bilan ta'minlanadi (`uz-UZ`, 08:00–20:00, `Asia/Tashkent`).

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
  "logoFileId": null, "address": null }
```

---

## `POST /api/companies/list` — ro'yxat

**SUPERADMIN-only** — barcha tenantlar ro'yxati; kompaniyaning o'z ADMINi
o'z kompaniyasidan boshqasini ko'ra olmaydi.

Body — `CompanyFilter`:

```json
{ "page": 0, "size": 20, "orders": { "NAME": "ASC" }, "search": "Ikkinchi" }
```

`search` — nom bo'yicha erkin qidiruv, bo'sh/`null` — filtr yo'q. Saralanadigan
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

## `PUT /api/companies/{id}/status` — holatni o'zgartirish

**SUPERADMIN-only** (report #3) — kompaniyaning `ACTIVE`/`SUSPENDED`
holatini o'zgartirishning yagona yo'li. Kompaniyaning o'z ADMINi bu
endpointga umuman kira olmaydi (`403`, `SecurityConfig`).

**Request body** (`UpdateCompanyStatusRequest`):

```json
{ "status": "SUSPENDED" }
```

Javob — yangilangan `Company`.

**`SUSPENDED` nima qiladi:**
- kompaniya foydalanuvchilarining barcha ochiq sessiyalari va access tokenlari darhol bekor qilinadi;
- `POST /api/auth/login` (to'g'ri parol bilan ham) va `POST /api/auth/refresh` → `403 COMPANY_SUSPENDED`;
- kompaniyaning `X-Api-Key` kalitlari ishlamaydi (`401`);
- dialer uning `ACTIVE` kampaniyalarini terishni to'xtatadi (kampaniyalar statusi o'zgarmaydi —
  `ACTIVE`ga qaytarilganda davom etadi).

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
  "defaultLanguage": "uz-UZ",
  "supportedLanguages": ["uz-UZ", "ru-RU"],
  "disclosureText": "Assalomu alaykum! Bu {company} kompaniyasining avtomatik ovozli xizmati. Suhbat yozib olinmoqda.",
  "maxAttemptsPerPhonePerWeek": 3,
  "quietDaysAfterConversation": 1,
  "respectPromisedDate": true,
  "humanLikePersonaAllowed": false,
  "holidays": ["2026-03-21", "2026-09-01"],
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

`defaultLanguage`/`supportedLanguages` — yopiq to'plam (report #6, `company.enums.Language`):
faqat `"uz-UZ"`, `"ru-RU"`, `"en-US"`. Boshqa qiymat JSON darajasida
rad etiladi — `400`, validatsiyagacha yetib bormaydi.

`disclosureText` — §11.1 ochiqlik matni: qo'ng'iroq boshida, bot gapirishni
boshlashidan oldin aytiladi. Har bir kompaniya yaratilganda yuqoridagi default
bilan to'ldiriladi.

- `{company}` — kompaniya nomiga almashadi (shuning uchun bitta matn hamma
  tenantga yaraydi).
- **Matn ikkala majburiy faktni aytishi shart** — qo'ng'iroq avtomatik ekani *va*
  yozib olinayotgani. Aks holda `PUT` `400` qaytaradi
  (`COMPANY_CONFIG_DISCLOSURE_INCOMPLETE`). Ochiqlik — platformaning huquqiy
  majburiyati, tenant uni o'chira olmaydi (§11.1).
- `null`/bo'sh qoldirilsa — platformaning o'z matni aytiladi (qo'ng'iroq tiliga
  qarab o'zbekcha yoki ruscha). Ya'ni maydonni tozalash ochiqlikni o'chirmaydi.
- Matn **bitta tilda** saqlanadi: yozuvi qo'ng'iroq tiliga mos kelmasa (kirill
  matn `uz-UZ` qo'ng'irog'ida yoki aksincha) shu qo'ng'iroqda platforma matni
  ishlatiladi. Ikkala tilda dial qiladigan kompaniya matnni bo'sh qoldirsin.
- Ssenariy o'z `disclosureText` ini bersa, u shu ssenariy bo'yicha qo'ng'iroqlarda
  kompaniya matnidan ustun turadi (`docs/api/scenarios.md`).

Topilmasa `404` — amalda bo'lmasligi kerak, chunki har bir kompaniya
yaratilganda avtomatik config oladi.

---

## `PUT /api/companies/{id}/config` — sozlamalarni yangilash

`ADMIN`, faqat o'z kompaniyasi (yuqoridagi kabi scoping).

**Request body** (`UpdateCompanyConfigRequest`):

```json
{ "dialWindowStart": "08:00", "dialWindowEnd": "21:00", "timezone": "Asia/Tashkent", "defaultLanguage": "uz-UZ", "supportedLanguages": ["uz-UZ", "ru-RU"], "disclosureText": "Assalomu alaykum! Bu {company} kompaniyasining avtomatik ovozli xizmati. Suhbat yozib olinmoqda." }
```

| Maydon | Majburiymi | Izoh |
|---|---|---|
| `dialWindowStart`/`dialWindowEnd` | ✅ | **Qat'iy chegara** — `DialerService` har bir kampaniyani o'z oynasi BILAN BIRGA shu oraliqqa ham tekshiradi; kampaniya bu kompaniyaning oralig'idan tashqariga chiqa olmaydi, hatto kampaniyaning o'z oynasi kengroq bo'lsa ham. |
| `timezone` | ✅ (`@NotBlank`) | — |
| `defaultLanguage` | ✅ | Aniq maydon (backend-uchun-talablar.md §13) — `supportedLanguages` ro'yxatining a'zosi bo'lishi shart, aks holda `400`. Kampaniya yaratish/tahrirlashda (`CreateCampaignRequest.defaultLanguage`/`UpdateCampaignRequest.defaultLanguage`) til berilmasa shu qiymatga tushadi. Yopiq to'plam — `"uz-UZ"`/`"ru-RU"`/`"en-US"` (report #6), boshqa qiymat `400`. |
| `supportedLanguages` | ✅ (bo'sh bo'lmasin) | Kampaniya/marshrut e'lon qilishi mumkin bo'lgan barcha tillar, `defaultLanguage`ni ham o'z ichiga olgan holda; berilgan til shu ro'yxatda bo'lishi shart, aks holda `400`. Yopiq to'plam, xuddi `defaultLanguage` kabi. |
| `disclosureText` | ❌ | §11.1 ochiqlik matni (yuqoridagi `GET` izohiga qarang). Matn qo'ng'iroq avtomatik ekanini va yozib olinayotganini aytmasa — `400` (`COMPANY_CONFIG_DISCLOSURE_INCOMPLETE`). `null`/bo'sh — platforma matniga qaytadi, ochiqlik o'chmaydi. |
| `maxAttemptsPerPhonePerWeek` | ❌ | `0..100`; `null` — o'zgarmaydi. Yuqoridagi "Kontakt siyosati" |
| `quietDaysAfterConversation` | ❌ | `0..90`; `null` — o'zgarmaydi |
| `respectPromisedDate` | ❌ | `null` — o'zgarmaydi |
| `humanLikePersonaAllowed` | ❌ | `null` — o'zgarmaydi. Qiymatni faqat platform admin o'zgartira oladi, aks holda `403 HUMAN_LIKE_PERSONA_PLATFORM_ONLY`. O'zgarishi audit log'ga yoziladi |
| `holidays` | ❌ | Sanalar (`YYYY-MM-DD`, ko'pi bilan 366 ta). Shu kunlari kampaniyalar umuman termaydi, retry va callback esa keyingi ruxsat etilgan kunga suriladi. Kun kompaniyaning `timezone`ida hisoblanadi. `null` — o'zgarmaydi, `[]` — tozalaydi |

**Response** — yangilangan `CompanyConfig`.
