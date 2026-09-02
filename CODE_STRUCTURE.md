# Clean / Hexagonal Architecture (Ports & Adapters) — Code Strukturasi

Ushbu hujjat `uysot-v2/mortgage` moduli arxitekturasi asosida ishlab chiqilgan bo'lib, `robot-call-v2` loyihasidagi barcha modullarni yagona, toza va standart arxitekturaga keltirish uchun qo'llanma hisoblanadi.

---

## 1. Umumiy Arxitektura Ko'rinishi (4 Qatlam)

Har bir modul quyidagi 4 ta mustaqil qatlamga bo'linadi:

```
uz.murodjon.robotcallv2.<module_name>/
├── domain/                  # 1. Sof biznes mantig'i va entitiylar (Frameworklardan holi)
│   ├── entity/              # Domain Entity/Modellar (biznes metodlari, invariantlar)
│   ├── enums/               # Domain Enumlar
│   └── service/             # Sof Domain Validatorlar / Domain Servislar
│
├── application/             # 2. UseCase va Biznes Orchestration (Biznes qoidalarini boshqarish)
│   ├── port/
│   │   ├── input/           # Inbound Ports (UseCase interfeyslari)
│   │   └── output/          # Outbound Ports (Repository / External SPI interfeyslari)
│   ├── service/             # Input Port (UseCase) implementatsiyalari
│   ├── dto/                 # Request, Response, Filter, Webhook va Event DTOlar
│   ├── enums/               # Application darajasidagi enumlar
│   ├── exception/           # Modulga xos maxsus typed exceptionlar
│   └── mapper/              # Domain <-> Entity <-> DTO mapperlari (@Component)
│
├── infrastructure/          # 3. Texnologik implementatsiyalar (DB, External API, gRPC, RabbitMQ)
│   ├── persistence/         # Ma'lumotlar bazasi (JPA)
│   │   ├── entity/          # JPA @Entity sinflari (@Table, @Id, @Column)
│   │   ├── repository/      # Spring Data JpaRepository interfeyslari
│   │   └── adapter/         # Output Port Repository Adapterlari (@Component)
│   ├── request/             # Tashqi REST / HTTP integratsiyalar (External SPI adapterlari)
│   │   ├── dto/             # Tashqi API DTOlari
│   │   └── adapter/         # External SPI adapterlari
│   ├── data/                # Boshqa modullar bilan ichki adapterlar (DataServiceAdapter)
│   ├── grpc/                # gRPC servislar va adapterlar
│   ├── security/            # Xavfsizlik filtrlari, auth tekshiruvlari
│   └── util/                # Modul konstantalari va texnik yordamchilar
│
└── presentation/            # 4. Tashqi interfeyslar va Delivery mexanizmi
    ├── controller/          # REST Controller Interfeys (@RequestMapping) va Impl (@RestController)
    ├── event_listener/      # Spring Event / RabbitMQ listenerlar
    ├── exception/           # Modul Exception Handler (@RestControllerAdvice)
    └── http/                # IDE orqali testlash uchun .http fayllar
```

---

## 2. Har bir Qatlamning Mas'uliyati va Qoidalari

### 1. `domain` Qatlami (Core Domain)
- **Bog'liqliklar**: Frameworklardan to'liq mustaqil (Spring, JPA, Jackson importlari bo'lmaydi).
- **`domain/entity/`**: Biznes obyekti. O'z holatini o'zi o'zgartiruvchi biznes metodlariga ega bo'ladi (masalan: `cancel()`, `changeStatus()`, `recordAttempt()`).
- **`domain/enums/`**: Biznes statuslari va turlari (masalan: `CampaignStatus`, `Disposition`, `TargetStatus`).
- **`domain/service/`**: Bir nechta entity o'rtasidagi sof biznes validatsiyalar va invariantlar (masalan: `DomainValidator.validateCampaignCanBeStarted(...)`).

### 2. `application` Qatlami (Use Cases & Ports)
- **`application/port/input/`**: Tashqi dunyo modulga qanday murojaat qilishi mumkinligini belgilovchi **UseCase interfeyslari** (masalan: `CampaignUseCase`, `ContactUseCase`).
- **`application/port/output/`**: Modul tashqi tizimlar (DB, External API, Dialer) bilan qanday bog'lanishini belgilovchi **SPI interfeyslari** (masalan: `CampaignRepository`, `ExternalSmsService`).
- **`application/service/`**: UseCaselarni implement qiluvchi servislar. Ular:
  1. `CurrentCompany` / `CurrentUser` orqali kontekstni oladi.
  2. Tashqi portlardan (Repository / External) ma'lumotlarni o'qiydi.
  3. Domain entitiyni chaqirib biznes operatsiyani bajaradi.
  4. Natijani output port orqali saqlaydi.
  5. Audit yozadi va `ApplicationEventPublisher` orqali hodisa tarqatadi (`publishEvent`).
- **`application/dto/`**: Controller va tashqi dunyo uchun Request / Response / Filter DTOlar (Java `record`).
- **`application/mapper/`**: Domain <-> JPA Entity <-> Response DTO o'rtasidagi konvertatsiyani bajaruvchi `@Component` mapperlar.
- **`application/exception/`**: Modulga tegishli typed exceptionlar.

### 3. `infrastructure` Qatlami (Adapters & Technical Details)
- **`infrastructure/persistence/`**:
  - `entity/`: `@Entity`, `@Table` belgilangan JPA ob'ektlari.
  - `repository/`: `JpaRepository<Entity, ID>` interfeyslari.
  - `adapter/`: `application.port.output.*Repository` interfeysini implement qiluvchi **Adapter** sinflar. Mapper orqali `Domain <-> JPA Entity` o'giradi.
- **`infrastructure/request/`**: Tashqi REST API (masalan, Yandex TTS, SMS provayder, CRM) mijozlari va ularning adapterlari.
- **`infrastructure/data/`**: Boshqa modullardan ma'lumot olish uchun data adapterlari.
- **`infrastructure/security/`**: Webhook imzo tekshirish yoki modul darajasidagi filtrlash.

### 4. `presentation` Qatlami (Delivery)
- **`presentation/controller/`**:
  - **Interface**: `@RequestMapping`, `@PreAuthorize`, `@Valid`, Swagger annotatsiyalari bo'ladi.
  - **Impl**: `@RestController`. Hech qanday biznes mantig'i, `if` yoki `try-catch` bo'lmaydi — faqat mos UseCase'ga 1 qatorda delegatsiya qiladi va `ResponseEntity.ok(ResponseData.ok(...))` qaytaradi.
- **`presentation/event_listener/`**: Boshqa modullar yoki asinxron jarayonlardan kelgan hodisalarni qabul qilib, tegishli UseCase'ga yo'naltiruvchi listenerlar.
- **`presentation/exception/`**: `@RestControllerAdvice` orqali modul xatolarini `ResponseData` shaklida tutib berish.
- **`presentation/http/`**: IntelliJ HTTP Client `.http` so'rovlar to'plami.

---

## 3. Kod Misollari (Pattern Shablonlari)

### 3.1. Domain Entity (`domain/entity/Campaign.java`)
```java
package uz.murodjon.robotcallv2.campaign.domain.entity;

import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignType;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

public class Campaign {
    private Long id;
    private String name;
    private CampaignType type;
    private CampaignStatus status;
    private String goal;
    private String language;
    private LocalTime dialStartTime;
    private LocalTime dialEndTime;
    private Set<DayOfWeek> dialDays;
    private int maxAttempts;
    private int retryIntervalMinutes;
    private int concurrentCalls;
    private Integer dailyCallCap;
    private long scenarioId;
    private long voiceId;
    private boolean disclosureEnabled;
    private Long companyId;

    // Konstruktorlar, getter/setterlar

    public void start() {
        if (this.status != CampaignStatus.DRAFT && this.status != CampaignStatus.PAUSED) {
            throw new IllegalStateException("Campaign cannot be started from status: " + this.status);
        }
        this.status = CampaignStatus.ACTIVE;
    }

    public void pause() {
        this.status = CampaignStatus.PAUSED;
    }
}
```

### 3.2. Input Port (`application/port/input/CampaignUseCase.java`)
```java
package uz.murodjon.robotcallv2.campaign.application.port.input;

import uz.murodjon.robotcallv2.campaign.application.dto.CampaignDetail;
import uz.murodjon.robotcallv2.campaign.application.dto.CampaignFilter;
import uz.murodjon.robotcallv2.campaign.application.dto.CampaignRow;
import uz.murodjon.robotcallv2.campaign.application.dto.CreateCampaignRequest;
import uz.murodjon.robotcallv2.shared.api.PageableData;

public interface CampaignUseCase {
    CampaignDetail create(CreateCampaignRequest request);
    CampaignDetail getById(long id);
    PageableData<CampaignRow> filter(CampaignFilter filter);
    void start(long id);
    void pause(long id);
}
```

### 3.3. Output Port (`application/port/output/CampaignRepository.java`)
```java
package uz.murodjon.robotcallv2.campaign.application.port.output;

import uz.murodjon.robotcallv2.campaign.application.dto.CampaignFilter;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.util.Optional;

public interface CampaignRepository {
    Campaign save(long companyId, Campaign campaign);
    Optional<Campaign> findById(long companyId, long id);
    PageableData<Campaign> filter(long companyId, CampaignFilter filter);
    void delete(long companyId, long id);
}
```

### 3.4. Output Port Adapter (`infrastructure/persistence/adapter/CampaignRepositoryAdapter.java`)
```java
package uz.murodjon.robotcallv2.campaign.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.campaign.application.dto.CampaignFilter;
import uz.murodjon.robotcallv2.campaign.application.mapper.CampaignMapper;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignRepository;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity.CampaignEntity;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.repository.CampaignJpaRepository;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.util.Optional;

@Component
public class CampaignRepositoryAdapter implements CampaignRepository {

    private final CampaignJpaRepository campaignJpaRepository;
    private final CampaignMapper campaignMapper;

    public CampaignRepositoryAdapter(CampaignJpaRepository campaignJpaRepository, CampaignMapper campaignMapper) {
        this.campaignJpaRepository = campaignJpaRepository;
        this.campaignMapper = campaignMapper;
    }

    @Override
    public Campaign save(long companyId, Campaign campaign) {
        CampaignEntity entity = campaignMapper.domainToEntity(campaign);
        entity.setCompanyId(companyId);
        CampaignEntity saved = campaignJpaRepository.save(entity);
        return campaignMapper.entityToDomain(saved);
    }

    @Override
    public Optional<Campaign> findById(long companyId, long id) {
        return campaignJpaRepository.findByIdAndCompanyId(id, companyId)
                .map(campaignMapper::entityToDomain);
    }

    @Override
    public PageableData<Campaign> filter(long companyId, CampaignFilter filter) {
        // Paging va qidiruv mantiqlari
        return null;
    }

    @Override
    public void delete(long companyId, long id) {
        campaignJpaRepository.findByIdAndCompanyId(id, companyId)
                .ifPresent(campaignJpaRepository::delete);
    }
}
```

### 3.5. Application Service (`application/service/CampaignService.java`)
```java
package uz.murodjon.robotcallv2.campaign.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.audit.service.AuditService;
import uz.murodjon.robotcallv2.campaign.application.dto.*;
import uz.murodjon.robotcallv2.campaign.application.mapper.CampaignMapper;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignRepository;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.company.service.CurrentCompany;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

@Service
public class CampaignService implements CampaignUseCase {

    private final CampaignRepository campaignRepository;
    private final CampaignMapper campaignMapper;
    private final CurrentCompany currentCompany;
    private final AuditService auditService;

    public CampaignService(CampaignRepository campaignRepository,
                           CampaignMapper campaignMapper,
                           CurrentCompany currentCompany,
                           AuditService auditService) {
        this.campaignRepository = campaignRepository;
        this.campaignMapper = campaignMapper;
        this.currentCompany = currentCompany;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public CampaignDetail create(CreateCampaignRequest request) {
        long companyId = currentCompany.id();
        Campaign campaign = campaignMapper.createRequestToDomain(request);
        Campaign saved = campaignRepository.save(companyId, campaign);
        auditService.record("CAMPAIGN_CREATE", "campaign", String.valueOf(saved.getId()), saved.getName());
        return campaignMapper.domainToDetail(saved);
    }

    @Override
    public CampaignDetail getById(long id) {
        long companyId = currentCompany.id();
        Campaign campaign = campaignRepository.findById(companyId, id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CAMPAIGN_NOT_FOUND, id));
        return campaignMapper.domainToDetail(campaign);
    }

    @Override
    public PageableData<CampaignRow> filter(CampaignFilter filter) {
        // filter logic
        return null;
    }

    @Override
    @Transactional
    public void start(long id) {
        long companyId = currentCompany.id();
        Campaign campaign = campaignRepository.findById(companyId, id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CAMPAIGN_NOT_FOUND, id));
        campaign.start();
        campaignRepository.save(companyId, campaign);
        auditService.record("CAMPAIGN_START", "campaign", String.valueOf(id), campaign.getName());
    }

    @Override
    @Transactional
    public void pause(long id) {
        long companyId = currentCompany.id();
        Campaign campaign = campaignRepository.findById(companyId, id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CAMPAIGN_NOT_FOUND, id));
        campaign.pause();
        campaignRepository.save(companyId, campaign);
        auditService.record("CAMPAIGN_PAUSE", "campaign", String.valueOf(id), campaign.getName());
    }
}
```

### 3.6. Controller (`presentation/controller/CampaignController.java` va `Impl`)
```java
package uz.murodjon.robotcallv2.campaign.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.campaign.application.dto.*;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RequestMapping("/api/campaigns")
@PreAuthorize("hasAuthority('ADMIN')")
public interface CampaignController {

    @PostMapping
    ResponseEntity<ResponseData<CampaignDetail>> create(@Valid @RequestBody CreateCampaignRequest request);

    @GetMapping("/{id}")
    ResponseEntity<ResponseData<CampaignDetail>> getById(@PathVariable long id);

    @PostMapping("/filter")
    ResponseEntity<ResponseData<PageableData<CampaignRow>>> filter(@Valid @RequestBody CampaignFilter filter);

    @PostMapping("/{id}/start")
    ResponseEntity<ResponseData<Void>> start(@PathVariable long id);

    @PostMapping("/{id}/pause")
    ResponseEntity<ResponseData<Void>> pause(@PathVariable long id);
}
```

```java
package uz.murodjon.robotcallv2.campaign.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.campaign.application.dto.*;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignUseCase;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RestController
public class CampaignControllerImpl implements CampaignController {

    private final CampaignUseCase campaignUseCase;

    public CampaignControllerImpl(CampaignUseCase campaignUseCase) {
        this.campaignUseCase = campaignUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<CampaignDetail>> create(CreateCampaignRequest request) {
        return ResponseEntity.ok(ResponseData.ok(campaignUseCase.create(request)));
    }

    @Override
    public ResponseEntity<ResponseData<CampaignDetail>> getById(long id) {
        return ResponseEntity.ok(ResponseData.ok(campaignUseCase.getById(id)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<CampaignRow>>> filter(CampaignFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(campaignUseCase.filter(filter)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> start(long id) {
        campaignUseCase.start(id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> pause(long id) {
        campaignUseCase.pause(id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }
}
```

---

## 4. `robot-call-v2` Modullarini Shu Strukturaga O'tkazish Ketma-Ketligi

1. **Guruh 1: Foydalanuvchi & Kompaniya/Profil (`profile`, `company`, `user`, `auth`, `audit`)**
2. **Guruh 2: Aloqa va Ovoz vositalari (`voice`, `storage`, `sms`, `donotcall`, `notification`, `contact`)**
3. **Guruh 3: Core Biznes Modullar (`scenario`, `campaign`, `crm`, `dialer`, `report`, `dashboard`, `aimodel`)**
