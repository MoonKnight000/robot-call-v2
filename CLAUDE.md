# CLAUDE.md — Loyiha qoidalari

AI voice agent: SIP orqali mijozlarga qo'ng'iroq qilib, o'zbek/rus tilida suhbatlashadi
va natijani CRM ga yozadi.

**Stack:** Java 21 · Spring Boot 3.4.5 · Asterisk 20 · PostgreSQL · RabbitMQ · Redis · MinIO
**Base paket:** `uz.murodjon.uysotvoice` · bitta Gradle moduli

Batafsil texnik topshiriq: `docs/PROJECT.md`. Bosqichlar tarixi: `docs/claude/CLAUDE.md`.

---

# QAT'IY QOIDALAR

Quyidagilar **majburiy**. Kod yozishdan oldin ham, review qilishda ham shu ro'yxatga
qarab tekshiriladi. Qoidani buzadigan kod qabul qilinmaydi.

## 1. Bitta faylda bitta public type

- Har bir `class` / `interface` / `enum` / `record` — **o'z faylida**, fayl nomi type
  nomi bilan bir xil.
- Nested (ichki) type faqat **`private`** bo'lsa ruxsat: bu tashqariga ko'rinmaydigan,
  faqat shu klass ichida ishlatiladigan implementatsiya detali.
  Masalan `DialogEngine.TurnResult`, `GoogleSttProvider.GoogleSttSession` — bu joiz.
- `public` nested type **taqiqlanadi**. U API'ning bir qismi — alohida faylga chiqariladi.

```java
// ✗ NOTO'G'RI
public record TtsProperties(...) {
    public record Voice(...) {}      // public nested — taqiqlangan
}

// ✓ TO'G'RI — TtsProperties.java va TtsVoice.java
public record TtsProperties(List<TtsVoice> catalog, ...) {}
public record TtsVoice(String id, String provider, ...) {}
```

## 2. Paket strukturasi — feature-first + qatlam

Har bir biznes-feature o'z paketi, ichida qatlam subpaketlari:

```
uz.murodjon.uysotvoice.<feature>.controller   — REST interfeys + bitta Impl
uz.murodjon.uysotvoice.<feature>.service      — BUTUN biznes logika
uz.murodjon.uysotvoice.<feature>.repository   — DB kirish (DAO + Spring Data)
uz.murodjon.uysotvoice.<feature>.entity       — JPA entity
uz.murodjon.uysotvoice.<feature>.dto          — request/response/row/filter record'lar
uz.murodjon.uysotvoice.<feature>.enums        — shu feature'ga tegishli barcha enum'lar
uz.murodjon.uysotvoice.<feature>.config       — @ConfigurationProperties, @Configuration
```

**Enum'lar — har doim `<feature>.enums` da.** `dto` yoki `entity` ichiga enum
qo'shilmaydi (masalan `CampaignStatus`, `CampaignTableField`) — o'z `enums`
subpaketiga chiqariladi, xuddi boshqa qatlamlar kabi.

Mavjud feature'lar:
`campaign` · `contact` · `donotcall` · `scenario` · `report` · `audit` · `operator` ·
`call` · `live` · `crm` · `company` · `dialer` · `callrecord` · `storage` · `voice`

**Istisno — `agent/`.** Bu voice pipeline infratuzilmasi (ARI, RTP, codec, VAD, STT,
TTS, dialog). U CRUD emas, shuning uchun texnik tamoyil bo'yicha bo'linadi:
`agent/ari`, `agent/rtp`, `agent/codec`, `agent/vad`, `agent/stt`, `agent/tts`,
`agent/dialog`, `agent/audio`, `agent/session`, `agent/routing`, `agent/metrics`,
`agent/lifecycle`, `agent/alerting`, `agent/summary`.

**`shared/`** — feature'lardan mustaqil umumiy kod: `shared/api` (ResponseData,
PageableData, FilterInterface, TableField), `shared/dialog` (enum'lar), `shared/exception`,
`shared/csv`, `shared/util`.

**`config/`** — butun ilova darajasidagi konfiguratsiya: security, executor, netty, clock,
global exception handler.

Yangi feature qo'shganda shu skeletni to'liq takrorlang. Qatlam bo'sh bo'lsa — subpaketni
umuman yaratmang (masalan `live` da `entity` yo'q).

## 3. Controller — interfeys + bitta Impl, logikasiz

- Har bir controller **interfeys** bo'ladi. Barcha Spring web annotatsiyalari
  (`@RequestMapping`, `@GetMapping`, `@PostMapping`, `@PathVariable`, `@RequestBody`,
  `@RequestParam`, `@Valid`) va butun Javadoc — **interfeysda**.
- Implementatsiya bitta: `<Name>ControllerImpl`, faqat `@RestController` bilan
  belgilanadi, `implements <Name>Controller`.
- **Impl'da logika bo'lmaydi.** Har bir metod bitta qatordan iborat: service'ni chaqirish
  va javobni o'rash. `if`, `for`, `try`, `instanceof`, hisob-kitob, tekshiruv,
  transformatsiya — hech biri controller'da bo'lmaydi.
- Controller **faqat o'z paketining service'iga** murojaat qiladi. Boshqa feature'ning
  service'iga yoki `agent/` ga to'g'ridan-to'g'ri murojaat qilmaydi.

```java
// ✓ TO'G'RI
@RestController
public class CampaignControllerImpl implements CampaignController {
    private final CampaignService service;

    public CampaignControllerImpl(CampaignService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ResponseData<Campaign>> get(long id) {
        return ResponseEntity.ok(ResponseData.ok(service.requireCampaign(id)));
    }
}
```

## 4. Logika — service'da

- **Butun** biznes logikasi `<feature>.service` paketida.
- Service repository'ga, boshqa service'larga va `agent/` ga murojaat qilishi mumkin.
- Service HTTP haqida bilmaydi: `ResponseEntity`, `HttpStatus`,
  `ResponseStatusException`, `HttpServletRequest` — service'da **taqiqlangan**.
- Repository'da biznes logikasi bo'lmaydi — faqat DB kirish va entity↔DTO mapping.

## 5. Exception — umumiy ierarxiya

- Kutilgan har qanday xato `shared/exception` dagi `AppException` avlodi bo'ladi:

  | Exception | Status | Qachon |
  |---|---|---|
  | `ValidationException` | 400 | Kiruvchi ma'lumot noto'g'ri |
  | `ForbiddenException` | 403 | Ruxsat yo'q |
  | `NotFoundException` | 404 | Entity topilmadi (yoki boshqa company'niki) |
  | `ConflictException` | 409 | So'rov to'g'ri, lekin tizim holati mos emas |
  | `ExternalServiceException` | 502 | Asterisk / CRM / STT / TTS / storage yiqildi |

- **Taqiqlangan:** biznes xatosi uchun `IllegalArgumentException`,
  `IllegalStateException`, `RuntimeException`, `ResponseStatusException` tashlash.
- Controller'da `try/catch` yozilmaydi. Barcha xatolarni
  `config/ApiExceptionHandler` (`@RestControllerAdvice`) ushlaydi va yagona
  `ResponseData` konvertiga o'raydi.
- `AppException` bo'lmagan har qanday exception — bu bug: 500 qaytariladi, stack trace
  faqat serverda loglanadi, mijozga umumiy xabar boradi.

```java
// ✗ NOTO'G'RI
throw new ResponseStatusException(NOT_FOUND, "campaign " + id + " not found");
throw new IllegalArgumentException("phone is empty");

// ✓ TO'G'RI
throw new NotFoundException("campaign", id);
throw new ValidationException("phone is empty");
```

## 6. Nomlash

| Type | Qoida | Misol |
|---|---|---|
| JPA entity | `<Domain>Entity` — doim `Entity` suffiksi bilan | `CampaignEntity`, `CallAttemptEntity` |
| Spring Data interfeys | `<Entity>JpaRepository` | `CampaignJpaRepository` |
| DAO klass | `<Entity>Repository` | `CampaignRepository` |
| Service | `<Feature>Service` | `CampaignService` |
| Controller | `<Feature>Controller` + `...Impl` | `CampaignController` |
| Request DTO | `<Verb><Noun>Request` | `CreateCampaignRequest` |
| Response DTO | `<Noun>Response` | `CreateCampaignResponse` |
| Jadval qatori — bitta entity'ni 1:1 ifodalasa | Suffikssiz, entity bilan bir xil domain nomi | `Campaign`, `Company` |
| Jadval qatori — bir nechta entity'ni birlashtirgan proyeksiya | `<Noun>Row` | `CallRow`, `LiveCallRow` |
| Filtr | `<Noun>Filter` | `CampaignFilter` |
| Sort ustunlari | `<Noun>TableField` | `CampaignTableField` |
| Properties | `<Noun>Properties` | `DialerProperties`, `RetryProperties` |

- JPA entity **doim** `Entity` suffiksi bilan tugaydi. Bu uni xuddi shu domain
  nomidagi DTO'dan ajratib turadi: `entity.CampaignEntity` vs `dto.Campaign`,
  `entity.CompanyEntity` vs `dto.Company`.
- Row DTO faqat **bitta** entity'ni to'g'ridan-to'g'ri ifodalasa (`GET /api/companies`
  javobidagi bir qator — bu `Company`), suffikssiz, entity bilan bir xil domain nomi
  olinadi. Agar DTO bir nechta entity'ni birlashtirgan proyeksiya bo'lsa (masalan
  `CallRow` — `call_attempt` + target + disposition'ni birlashtiradi), eski `<Noun>Row`
  qoidasi qoladi — bitta backing entity yo'qligini aniq ko'rsatish uchun.
- Bitta simple name butun loyihada **faqat bir marta** uchraydi. Ikki paketda bir xil
  nomli klass bo'lmasin — import chalkashadi va IDE'da qidirish qiyinlashadi. (Entity/DTO
  juftligi bundan mustasno: ular turli paketda va nomlari `Entity` suffiksi bilan
  ajraladi, shuning uchun `Campaign` va `CampaignEntity` ikkalasi ham loyihada bo'lishi joiz.)

## 7. API konvert

- Har bir REST javob `ResponseData<T>` ichida qaytadi (`ResponseData.ok(...)` /
  `ResponseData.error(...)`). Fayl yuklab olish (`Resource`) va SSE (`SseEmitter`) —
  istisno.
- Ro'yxat qaytaruvchi endpoint `PageableData<T>` ishlatadi va `FilterInterface`
  merosxo'ri bo'lgan filtr qabul qiladi.
- **`docs/api/` — har doim koddagi API bilan mos bo'lishi shart.** Har qanday
  controller o'zgarishi (yangi endpoint, o'zgargan request/response maydoni,
  yangi status kod, rol/ruxsat o'zgarishi, endpoint o'chirilishi) shu o'zgarishni
  qilgan commit/PR ichidayoq `docs/api/`dagi tegishli faylga ham yozilishi
  kerak — hujjat alohida keyingi vazifa emas. Yangi feature/controller
  qo'shilsa, `docs/api/README.md`dagi fayllar jadvaliga ham qator qo'shiladi.
  Frontend shu papkaga qarab ishlaydi — eskirgan hujjat noto'g'ri integratsiyaga
  olib keladi.

## 8. Umumiy texnik qoidalar

1. **Python ISHLATILMAYDI.** Butun stack Java. Skript kerak bo'lsa — bash yoki Java.
2. **Audio transport — RTP over UDP.** WebSocket faqat ARI control va STT/TTS provayderlar
   uchun; audio uchun emas.
3. **Netty event loop threadida blocking kod yozilmasin.** Paketni qabul qil, queue ga
   tashla, ishlov virtual threadda.
4. **Guardrails majburiy.** LLM qarz summasi, muddat, chegirma haqida o'zidan gapirmasin
   (`docs/PROJECT.md` §4.4).
5. **Kommentariyalar va log xabarlari — ingliz tilida.** Bot gapiradigan, foydalanuvchiga
   ko'rinadigan matnlar — o'zbek/rus.
6. **Build'ni Claude ishga tushirmaydi.** `./gradlew`, testlar, `bootRun` — foydalanuvchi
   o'zi tekshiradi.
7. **Bosqichma-bosqich.** `docs/PROJECT.md` §10 dagi tartibni buzma; foydalanuvchi
   "keyingi bosqich" demaguncha oldinga yugurma.
8. **Integration testlarga tegilmaydi.** `@SpringBootTest` + `@Testcontainers` (real
   Postgres) ishlatadigan testlar — ularni ishga tushirish uchun butun loyihani (Docker,
   Testcontainers) ko'tarish kerak, buni faqat foydalanuvchi o'zi qiladi. Production
   kodga o'zgartirish kiritilganda (masalan bir DTO'ga yangi maydon qo'shilsa) bu
   testlarni **tuzatishga urinma va ularga umuman tegma** — ular hozircha
   compile bo'lmasa ham, foydalanuvchi loyihani to'liq ko'tarib, o'zi qo'lda
   moslashtiradi. Faqat mock asosidagi sof unit testlar (Spring context'siz)
   tegishli — ularni yangi signaturaga moslab yangilash mumkin.

---

## Yordam kerak bo'lganda

Quyidagilar noaniq bo'lsa — **taxmin qilma, so'ra**: SIP provayder credentials, CRM API
endpointlari, Google Cloud / Yandex kalitlari, test uchun telefon raqami, qarzdorlik
ma'lumotining aniq strukturasi.
