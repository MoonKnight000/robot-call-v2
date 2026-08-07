# CLAUDE.md — Loyiha qoidalari

AI voice agent: SIP orqali mijozlarga qo'ng'iroq qilib, o'zbek/rus tilida suhbatlashadi
va natijani CRM ga yozadi.

**Stack:** Java 21 · Spring Boot 3.4.5 · Asterisk 20 · PostgreSQL · RabbitMQ · Redis · MinIO
**Base paket:** `uz.murodjon.uysotvoice` · bitta Gradle moduli
Batafsil topshiriq: `docs/PROJECT.md` · bosqichlar tarixi: `docs/claude/CLAUDE.md`

---

# ISH USLUBI

Bu bo'lim — *qanday ishlash* haqida, keyingisi — *kod qanday ko'rinishi* haqida.
Arzimas vazifada mulohaza bilan yondash: bu qoidalar tezlikdan ko'ra ehtiyotkorlikni
tanlaydi.

1. **Avval o'yla.** Taxminingni ochiq ayt; noaniq bo'lsa to'xta va so'ra. Bir nechta
   talqin bo'lsa — jimgina birini tanlama, ikkalasini ko'rsat. Soddaroq yo'l bo'lsa ayt,
   kerak bo'lsa e'tiroz bildir.
2. **Soddalik.** Masalani yechadigan eng kam kod. So'ralmagan feature, bir marta
   ishlatiladigan abstraksiya, so'ralmagan "moslashuvchanlik", bo'lmaydigan holat uchun
   error handling — yozilmaydi. 200 qator yozding-u, 50 qatorda bo'ladimi — qayta yoz.
3. **Jarrohlik o'zgarish.** Faqat kerakli joyga teg: yonidagi kodni, kommentni,
   formatlashni "yaxshilama", buzilmagan narsani refactor qilma, mavjud uslubga moslash.
   Aloqasi yo'q o'lik kodni ko'rsang — ayt, o'chirma. O'z o'zgarishing natijasida
   keraksiz qolgan import/o'zgaruvchi/metodni esa o'zing tozala. **Test:** har bir
   o'zgargan qator foydalanuvchi so'roviga bevosita bog'lana olsin.
4. **Maqsad + tekshiruv mezoni.** Vazifani tekshirib bo'ladigan maqsadga aylantir
   ("validation qo'sh" → "noto'g'ri kirish uchun test, keyin uni o'tkaz"). Ko'p
   bosqichli ishda qisqa reja ber: har bosqich va uni qanday tekshirish. **Lekin
   tekshirishni foydalanuvchi bajaradi** (§8.6) — Claude build/test ishga tushirmaydi,
   mezonni aytib beradi.

---

# QAT'IY QOIDALAR

Quyidagilar **majburiy**. Kod yozishdan oldin ham, review qilishda ham shu ro'yxatga
qarab tekshiriladi. Qoidani buzadigan kod qabul qilinmaydi.

## 1. Bitta faylda bitta public type

Har bir `class`/`interface`/`enum`/`record` — **o'z faylida**, fayl nomi type nomi bilan
bir xil. Nested type faqat **`private`** bo'lsa ruxsat (`DialogEngine.TurnResult`,
`GoogleSttProvider.GoogleSttSession`) — bu tashqariga ko'rinmaydigan implementatsiya
detali. **`public` nested type taqiqlanadi:** u API'ning bir qismi, alohida faylga
chiqariladi — `TtsProperties` ichida `public record Voice` emas, balki `TtsProperties.java`
va `TtsVoice.java`.

## 2. Paket strukturasi — feature-first + qatlam

```
uz.murodjon.uysotvoice.<feature>.controller   — REST interfeys + bitta Impl
uz.murodjon.uysotvoice.<feature>.service      — BUTUN biznes logika
uz.murodjon.uysotvoice.<feature>.repository   — DB kirish (DAO + Spring Data)
uz.murodjon.uysotvoice.<feature>.domain       — ASOSIY model (service ↔ repository)
uz.murodjon.uysotvoice.<feature>.entity       — JPA entity (faqat saqlash uchun)
uz.murodjon.uysotvoice.<feature>.dto          — faqat HTTP uchun: request/response/row/filter
uz.murodjon.uysotvoice.<feature>.enums        — shu feature'ga tegishli barcha enum'lar
uz.murodjon.uysotvoice.<feature>.config       — @ConfigurationProperties, @Configuration
```

**Enum'lar — har doim `<feature>.enums` da.** `dto` yoki `entity` ichiga enum
qo'shilmaydi (`CampaignStatus`, `CampaignTableField` ham shu qoidaga bo'ysunadi).

### 2.1. `domain` vs `entity` vs `dto`

| Paket    | Nima uchun                                                                                                                                                       | Kim ko'radi                   |
|----------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------|
| `domain` | **Asosiy model.** Service ↔ repository o'rtasida ikki tomonga yuradigan type; undan olingan hosila qiymatlar ham (`EffectiveAiModelConfig`, `CallTask`).         | service, repository, `agent/` |
| `entity` | **Faqat saqlash.** JPA detali — `domain`ga aylanmasdan repository'dan chiqmaydi.                                                                                 | faqat repository              |
| `dto`    | **Faqat HTTP borligi uchun mavjud.** `*Request`, `*Response`, `*Filter`, bitta endpoint javobiga moslangan proyeksiya (`*Row`, `*Detail`, dashboard bo'laklari). | controller, service cheti     |

Ajratish testi: **service uni o'qiydi ham, yozadi ham → `domain`; faqat bitta endpoint
javobi uchun yasalgan → `dto`.** Entity hech qachon controller'ga chiqmaydi, DTO hech
qachon repository'ga kirmaydi.

Mavjud feature'lar:
`aimodel` · `audit` · `auth` · `callrecord` · `campaign` · `company` · `contact` · `crm` ·
`dialer` · `donotcall` · `inbound` · `integration` · `live` · `notification` · `operator` ·
`profile` · `report` · `scenario` · `search` · `siptrunk` · `storage` · `user` · `voice`

**Istisno — `agent/`.** Voice pipeline infratuzilmasi, CRUD emas, shuning uchun texnik
tamoyil bo'yicha bo'linadi: `agent/ari`, `agent/rtp`, `agent/codec`, `agent/vad`,
`agent/stt`, `agent/tts`, `agent/dialog`, `agent/audio`, `agent/session`, `agent/routing`,
`agent/metrics`, `agent/lifecycle`, `agent/alerting`, `agent/summary`.

**`shared/`** — feature'lardan mustaqil umumiy kod: `shared/api` (ResponseData,
PageableData, FilterInterface, TableField), `shared/dialog`, `shared/exception`,
`shared/csv`, `shared/util`. **`config/`** — ilova darajasidagi konfiguratsiya: security,
executor, netty, clock, global exception handler.

Yangi feature qo'shganda skeletni to'liq takrorlang. Qatlam bo'sh bo'lsa — subpaketni
umuman yaratmang (masalan `live` da `entity` yo'q).

## 3. Controller — interfeys + bitta Impl, logikasiz

- Har bir controller **interfeys**. Barcha Spring web annotatsiyalari (`@RequestMapping`,
  `@GetMapping`, `@PathVariable`, `@RequestBody`, `@RequestParam`, `@Valid`) va butun
  Javadoc — **interfeysda**.
- Implementatsiya bitta: `<Name>ControllerImpl`, faqat `@RestController` bilan
  belgilanadi, `implements <Name>Controller`.
- **Impl'da logika bo'lmaydi.** Har bir metod bitta qator: service'ni chaqirish va
  javobni o'rash. `if`, `for`, `try`, `instanceof`, hisob-kitob, tekshiruv,
  transformatsiya — hech biri controller'da bo'lmaydi.
- Controller **faqat o'z paketining service'iga** murojaat qiladi; boshqa feature'ning
  service'iga yoki `agent/` ga to'g'ridan-to'g'ri emas.

## 4. Logika — service'da

- **Butun** biznes logikasi `<feature>.service` da. Service repository'ga, boshqa
  service'larga va `agent/` ga murojaat qilishi mumkin.
- Service HTTP haqida bilmaydi: `ResponseEntity`, `HttpStatus`, `ResponseStatusException`,
  `HttpServletRequest` — service'da **taqiqlangan**.
- Repository'da biznes logikasi bo'lmaydi — faqat DB kirish va entity↔domain mapping.
- **Repository domain qabul qiladi, domain qaytaradi.** Yozish metodlariga yoyilgan
  parametrlar berilmaydi. Identifikator, `company_id`, `created_at` kabi repository'niki
  bo'lgan maydonlar kirishda e'tiborga olinmaydi (repository o'zi shtamplaydi), chiqishda
  esa doim to'ldirilgan bo'ladi; faqat yozish uchun mo'ljallangan qiymatni domain
  record'dagi nomlangan factory yasaydi (`AiModelConfig.overrides(...)`).
- **`CurrentCompany` faqat service'da.** Repository yashirin kontekst bilmaydi —
  `companyId` unga har doim argument bo'lib beriladi.

```java
// ✗ yoyilgan parametrlar + repository ichida yashirin kompaniya
public AiModelConfig save(String model, Double temperature, Integer maxOutputTokens) {
    long companyId = company.id();
    ...
}
// ✓
public AiModelConfig upsert(long companyId, AiModelConfig config) { ... }
```

## 5. Exception — umumiy ierarxiya

Kutilgan har qanday xato `shared/exception` dagi `AppException` avlodi bo'ladi:

| Exception                  | Status | Qachon                                                                                                             |
|----------------------------|--------|--------------------------------------------------------------------------------------------------------------------|
| `ValidationException`      | 400    | Kiruvchi ma'lumot noto'g'ri                                                                                        |
| `ForbiddenException`       | 403    | Ruxsat yo'q                                                                                                        |
| `NotFoundException`        | 404    | Entity topilmadi (yoki boshqa company'niki)                                                                        |
| `ConflictException`        | 409    | So'rov to'g'ri, lekin tizim holati mos emas                                                                        |
| `ExternalServiceException` | 502    | Asterisk / CRM / STT / TTS / storage yiqildi — qo'shimcha `service` argumenti oladi (`"asterisk"`, `"google-stt"`) |

- **Xabar hech qachon literal matn emas.** Har bir exception `ErrorCode` qabul qiladi,
  xabar `code.format(args)` bilan yasaladi. Yangi xato qo'shish = `ErrorCode` enum'iga
  yangi qator qo'shish (`shared/exception/ErrorCode.java`, kategoriyalar bo'yicha
  guruhlangan), kodda matn yozish emas.
- Frontend `ResponseData.messageCode` (= `ErrorCode.name()`) bo'yicha tarjima qiladi:
  **matnni o'zgartirish erkin, enum nomini o'zgartirish breaking change.**
- **Taqiqlangan:** biznes xatosi uchun `IllegalArgumentException`, `IllegalStateException`,
  `RuntimeException`, `ResponseStatusException` tashlash.
- **Exception faqat service va `agent/` da tashlanadi.** Repository topilmasa `null`/
  `Optional.empty()` qaytaradi — bu 404 mi yoki normal holat mi, service hal qiladi.
  Controller'da `throw` ham, `try/catch` ham bo'lmaydi.
- Hamma xatoni `config/ApiExceptionHandler` (`@RestControllerAdvice`) ushlaydi va yagona
  `ResponseData` konvertiga o'raydi. U `AppException`dan tashqari `@Valid`
  (`VALIDATION_FAILED`), buzuq JSON, yetishmagan/noto'g'ri tipdagi parametr va noma'lum
  endpoint holatlarini ham qoplaydi — qo'lda tekshirish shart emas.
- `AppException` bo'lmagan exception — bu bug: 500 (`INTERNAL_SERVER_ERROR`), stack trace
  faqat serverda loglanadi.

```java
// ✗
throw new ResponseStatusException(NOT_FOUND, "campaign " + id + " not found");
throw new IllegalArgumentException("phone is empty");
throw new NotFoundException("Campaign " + id + " not found");   // matn kodda
// ✓
throw new NotFoundException(ErrorCode.CAMPAIGN_NOT_FOUND, id);
throw new ValidationException(ErrorCode.CSV_PHONE_EMPTY);
throw new ExternalServiceException(ErrorCode.ARI_ORIGINATE_FAILED, "asterisk", cause, number, trunk);
```

## 6. Nomlash

| Type                           | Qoida                                           | Misol                                 |
|--------------------------------|-------------------------------------------------|---------------------------------------|
| JPA entity                     | `<Domain>Entity` — doim `Entity` suffiksi bilan | `CampaignEntity`, `CallAttemptEntity` |
| Spring Data interfeys          | `<Entity>JpaRepository`                         | `CampaignJpaRepository`               |
| DAO klass                      | `<Entity>Repository`                            | `CampaignRepository`                  |
| Service                        | `<Feature>Service`                              | `CampaignService`                     |
| Controller                     | `<Feature>Controller` + `...Impl`               | `CampaignController`                  |
| Domain model                   | Suffikssiz sof domain nomi                      | `Campaign`, `AiModelConfig`           |
| Request DTO                    | `<Verb><Noun>Request`                           | `CreateCampaignRequest`               |
| Response DTO                   | `<Noun>Response`                                | `CreateCampaignResponse`              |
| Endpoint javobidagi proyeksiya | `<Noun>Row` / `<Noun>Detail`                    | `CallRow`, `CallDetail`               |
| Filtr                          | `<Noun>Filter`                                  | `CampaignFilter`                      |
| Sort ustunlari                 | `<Noun>TableField`                              | `CampaignTableField`                  |
| Properties                     | `<Noun>Properties`                              | `DialerProperties`                    |

- Suffikssiz nom — **`domain` paketiniki**; `Row`/`Detail` esa faqat bitta endpoint
  javobi uchun yasalgan `dto` proyeksiyasi (`CallRow` = `call_attempt` + target +
  disposition). Shuning uchun `domain.Campaign` va `dto.CampaignRow` yonma-yon turishi
  normal.
- Bitta simple name butun loyihada **faqat bir marta** uchraydi — aks holda import
  chalkashadi. Domain/entity juftligi mustasno: `Campaign` va `CampaignEntity` ikkalasi
  ham bo'lishi joiz, ular `Entity` suffiksi bilan ajraladi.

### 6.1. Maydon nomlari

**Qisqartma yo'q:** `jpa`, `repo`, `props`, `cfg`, `defaults` — taqiqlangan. Bitta roldan
bitta bog'liqlik bo'lsa **rol nomi** olinadi (`jpaRepository`, `repository`, `service`);
bir nechta bo'lsa **nima bilan ishlashi prefiks bo'ladi** (`aiModelConfigService`,
`voiceSettingsService`, `callAttemptJpaRepository`). Request parametri — `request`, `r` emas.

```java
// ✗ jpa · repo · defaults
// ✓ jpaRepository · repository · dialogProperties
```

### 6.2. Metod nomlari

- Metod **fe'l bilan boshlanadi**: `find…`, `create…`, `update…`, `upsert…`, `delete…`,
  `count…`. `effective(...)`, `active(...)` kabi sifat-nomlar taqiqlanadi.
- Nom o'z qamrovini aytadi: argument bo'lsa nomda ko'rinadi (`findByCompanyId(long)`),
  argumentsiz va joriy kompaniya/foydalanuvchi kontekstida ishlasa — `…ForCurrentCompany`.
- **Yashirin overload taqiqlanadi:** `find()` va `find(long companyId)` yonma-yon
  turmaydi — birinchisi qaysi kompaniya ekanini yashiradi.
- Entity→domain mapping metodi `to<Domain>`: `toAiModelConfig(entity)`. `toRow`/`toDto` emas.

**Etalon:** `aimodel` feature'i — yangi kod yozishda yoki eski feature'ni ko'chirishda
`AiModelConfigRepository` va `AiModelConfigService` ga qarab tekshiriladi.

## 7. API konvert

- Har bir REST javob `ResponseData<T>` ichida qaytadi. Fayl yuklab olish (`Resource`) va
  SSE (`SseEmitter`) — istisno.
- Ro'yxat qaytaruvchi endpoint `PageableData<T>` ishlatadi va `FilterInterface`
  merosxo'ri bo'lgan filtr qabul qiladi.
- **`docs/api/` koddagi API bilan doim mos bo'lishi shart.** Har qanday controller
  o'zgarishi (yangi endpoint, o'zgargan maydon, yangi status kod, rol o'zgarishi,
  endpoint o'chirilishi) shu o'zgarishni qilgan commit ichidayoq `docs/api/`ga ham
  yoziladi — bu keyingi alohida vazifa emas. Yangi controller qo'shilsa,
  `docs/api/README.md` jadvaliga ham qator qo'shiladi. Frontend shu papkaga qarab
  ishlaydi.

## 8. Umumiy texnik qoidalar

1. **Python ISHLATILMAYDI.** Butun stack Java; skript kerak bo'lsa — bash yoki Java.
2. **Audio transport — RTP over UDP.** WebSocket faqat ARI control va STT/TTS
   provayderlar uchun, audio uchun emas.
3. **Netty event loop threadida blocking kod yozilmasin.** Paketni qabul qil, queue ga
   tashla, ishlov virtual threadda.
4. **Guardrails majburiy.** LLM qarz summasi, muddat, chegirma haqida o'zidan gapirmasin
   (`docs/PROJECT.md` §4.4).
5. **Kommentariya va log — ingliz tilida.** Bot gapiradigan, foydalanuvchiga ko'rinadigan
   matnlar — o'zbek/rus.
6. **Build'ni Claude ishga tushirmaydi.** `./gradlew`, testlar, `bootRun` — foydalanuvchi
   o'zi tekshiradi.
7. **Bosqichma-bosqich.** `docs/PROJECT.md` §10 tartibini buzma; foydalanuvchi "keyingi
   bosqich" demaguncha oldinga yugurma.
8. **Integration testlarga tegilmaydi.** `@SpringBootTest` + `@Testcontainers` ishlatadigan
   testlarni ko'tarish uchun butun loyiha (Docker) kerak — buni faqat foydalanuvchi
   qiladi. Production kod o'zgarganda ular compile bo'lmay qolsa ham **tuzatishga urinma**;
   foydalanuvchi o'zi moslashtiradi. Faqat mock asosidagi sof unit testlar (Spring
   context'siz) yangi signaturaga moslanadi.

---

## Yordam kerak bo'lganda

Quyidagilar noaniq bo'lsa — **taxmin qilma, so'ra**: SIP provayder credentials, CRM API
endpointlari, Google Cloud / Yandex kalitlari, test uchun telefon raqami, qarzdorlik
ma'lumotining aniq strukturasi.
