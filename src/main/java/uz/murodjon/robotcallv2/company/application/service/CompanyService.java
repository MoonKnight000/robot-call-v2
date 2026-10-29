package uz.murodjon.robotcallv2.company.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.company.application.dto.CreateCompanyRequest;
import uz.murodjon.robotcallv2.company.application.dto.UpdateCompanyByPlatformRequest;
import uz.murodjon.robotcallv2.company.application.dto.UpdateCompanyRequest;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;
import uz.murodjon.robotcallv2.company.application.port.output.CompanyRepository;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyFilter;
import uz.murodjon.robotcallv2.company.domain.enums.CompanyStatus;
import uz.murodjon.robotcallv2.company.domain.event.CompanyCreatedEvent;
import uz.murodjon.robotcallv2.company.domain.event.CompanyFeaturesChangedEvent;
import uz.murodjon.robotcallv2.company.domain.service.CompanyValidator;
import uz.murodjon.robotcallv2.company.domain.event.CompanySuspendedEvent;
import uz.murodjon.robotcallv2.company.infrastructure.config.CompanyProperties;
import uz.murodjon.robotcallv2.role.domain.enums.Feature;
import uz.murodjon.robotcallv2.security.CompanyAccessGuard;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.UploadedFile;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;
import uz.murodjon.robotcallv2.shared.integration.IntegrationProvider;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.storage.application.port.input.ImageUploadUseCase;
import uz.murodjon.robotcallv2.storage.domain.entity.StoredFile;

import java.time.Clock;
import java.util.List;

@Service
public class CompanyService implements CompanyUseCase {

    private static final Logger log = LoggerFactory.getLogger(CompanyService.class);

    private final CompanyRepository repository;
    private final CompanyConfigUseCase config;
    private final ImageUploadUseCase imageUploadUseCase;
    private final CompanyAccessGuard companyAccessGuard;
    private final AuditUseCase audit;
    private final CompanyProperties companyProperties;
    private final Clock clock;
    /**
     * Roles and sessions belong to features that depend on this one; they hear about a new or
     * suspended company from an event (handled synchronously) instead of being called.
     */
    private final ApplicationEventPublisher eventPublisher;

    public CompanyService(CompanyRepository repository, CompanyConfigUseCase config,
                          ImageUploadUseCase imageUploadUseCase, CompanyAccessGuard companyAccessGuard,
                          AuditUseCase audit, CompanyProperties companyProperties, Clock clock,
                          ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.config = config;
        this.imageUploadUseCase = imageUploadUseCase;
        this.companyAccessGuard = companyAccessGuard;
        this.audit = audit;
        this.companyProperties = companyProperties;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Company create(CreateCompanyRequest request) {
        long id = repository.create(Company.named(request.name()));
        config.createDefault(id);
        // Without its system roles a new tenant cannot be given a single user — the role
        // feature creates them before this returns.
        eventPublisher.publishEvent(new CompanyCreatedEvent(id));
        audit.record(id, "COMPANY_CREATE", "company", String.valueOf(id), request.name());
        return repository.find(id);
    }

    @Override
    public Company update(long callerCompanyId, long id, UpdateCompanyRequest request) {
        requireCompany(callerCompanyId, id);
        repository.update(id, Company.profile(request.name(), request.address()));
        audit.record(id, "COMPANY_UPDATE", "company", String.valueOf(id), request.name());
        return requireCompany(callerCompanyId, id);
    }

    private void applyStatus(long id, CompanyStatus status) {
        // The platform administrator signs in through this company: closing it locks everyone out.
        if (status != CompanyStatus.ACTIVE && id == companyProperties.defaultId()) {
            throw new ConflictException(ErrorCode.COMPANY_DEFAULT_PROTECTED, id);
        }
        repository.updateStatus(id, status);
        if (status != CompanyStatus.ACTIVE) {
            // Login and refresh refuse a closed company from now on; the auth feature
            // ends the sessions already open, instead of leaving each one its last access token.
            eventPublisher.publishEvent(new CompanySuspendedEvent(id));
        }
    }

    @Override
    @Transactional
    public Company updateByPlatform(long id, UpdateCompanyByPlatformRequest request) {
        Company company = requireCompanyUnscoped(id);
        CompanyValidator.validateDisabledFeatures(request.disabledFeatures());
        // The platform administrator signs in through the default company: it gets no end date
        // (closing it is refused in applyStatus; the transaction undoes what was written before).
        if (id == companyProperties.defaultId() && request.expiresAt() != null) {
            throw new ConflictException(ErrorCode.COMPANY_DEFAULT_PROTECTED, id);
        }
        CompanyStatus status = resolveStatus(request);
        repository.updatePlatformSettings(id, company.toBuilder()
                .name(request.name())
                .address(request.address())
                .maxConcurrentCalls(request.maxConcurrentCalls())
                .expiresAt(request.expiresAt())
                .disabledFeatures(request.disabledFeatures())
                .disabledIntegrations(request.disabledIntegrations())
                .build());
        if (status != company.status()) {
            applyStatus(id, status);
        }
        // The users' tokens still carry the old permissions; the auth feature ends those sessions.
        // A closed company's sessions are already ended by applyStatus.
        if (status == CompanyStatus.ACTIVE && !request.disabledFeatures().equals(company.disabledFeatures())) {
            eventPublisher.publishEvent(new CompanyFeaturesChangedEvent(id));
        }
        audit.record(id, "COMPANY_PLATFORM_UPDATE", "company", String.valueOf(id),
                status + ", expiresAt=" + request.expiresAt() + ", disabledFeatures=" + request.disabledFeatures()
                        + ", disabledIntegrations=" + request.disabledIntegrations());
        return requireCompanyUnscoped(id);
    }

    /**
     * What the company's status becomes. SUSPENDED and DELETED are the platform's own decision and stand;
     * ACTIVE and EXPIRED ("not closed by the platform") follow the end date, so a past date closes the
     * company now and a moved or cleared one reopens it now, without waiting for the job.
     */
    private CompanyStatus resolveStatus(UpdateCompanyByPlatformRequest request) {
        if (request.status() == CompanyStatus.SUSPENDED || request.status() == CompanyStatus.DELETED) {
            return request.status();
        }
        boolean lapsed = request.expiresAt() != null && !request.expiresAt().isAfter(clock.instant());
        return lapsed ? CompanyStatus.EXPIRED : CompanyStatus.ACTIVE;
    }

    @Override
    public void expireLapsedCompanies() {
        for (Company company : repository.findActiveExpiredBy(clock.instant())) {
            try {
                applyStatus(company.id(), CompanyStatus.EXPIRED);
                audit.record(company.id(), "COMPANY_EXPIRED", "company", String.valueOf(company.id()),
                        String.valueOf(company.expiresAt()));
            } catch (Exception e) {
                // One company failing must not keep the others open.
                log.error("Expiring company {} failed: {}", company.id(), e.getMessage());
            }
        }
    }

    @Override
    public Company uploadLogo(long callerCompanyId, long id, UploadedFile file) {
        requireCompany(callerCompanyId, id);
        StoredFile stored = imageUploadUseCase.upload(file, id);
        repository.updateLogoFileId(id, stored.id());
        audit.record(id, "COMPANY_LOGO_UPLOAD", "company", String.valueOf(id), String.valueOf(stored.id()));
        return requireCompany(callerCompanyId, id);
    }

    @Override
    public PageableData<Company> list(CompanyFilter filter) {
        return repository.findPage(filter);
    }

    @Override
    public Company findById(long id) {
        return repository.find(id);
    }

    @Override
    public List<Company> findAllForWarmup() {
        return repository.findAllCompanies();
    }

    @Override
    public Company requireCompany(long callerCompanyId, long id) {
        companyAccessGuard.requireOwnOrSuperadmin(callerCompanyId, id);
        return requireCompanyUnscoped(id);
    }

    @Override
    public void requireFeatureEnabled(long companyId, Feature feature) {
        requireUsable(repository.find(companyId), feature);
    }

    @Override
    public void requireIntegrationEnabled(long companyId, IntegrationProvider provider) {
        Company company = repository.find(companyId);
        requireUsable(company, Feature.INTEGRATIONS);
        if (company != null && !company.isEnabled(provider)) {
            throw new ForbiddenException(ErrorCode.COMPANY_INTEGRATION_DISABLED, provider);
        }
    }

    // A company id with no row is not refused, as none of these checks refused one before.
    @Override
    public boolean isFeatureAvailable(long companyId, Feature feature) {
        Company company = repository.find(companyId);
        return company == null || company.mayUse(feature);
    }

    @Override
    public boolean isIntegrationAvailable(long companyId, IntegrationProvider provider) {
        Company company = repository.find(companyId);
        return company == null || company.mayUse(provider);
    }

    private static void requireUsable(Company company, Feature feature) {
        if (company == null) {
            return;
        }
        if (!company.isOpen()) {
            throw new ForbiddenException(ErrorCode.COMPANY_SUSPENDED);
        }
        if (!company.isEnabled(feature)) {
            throw new ForbiddenException(ErrorCode.COMPANY_FEATURE_DISABLED, feature);
        }
    }

    @Override
    public void requireExists(long id) {
        if (!repository.existsById(id)) {
            throw new NotFoundException(ErrorCode.COMPANY_NOT_FOUND, id);
        }
    }

    private Company requireCompanyUnscoped(long id) {
        Company row = repository.find(id);
        if (row == null) {
            throw new NotFoundException(ErrorCode.COMPANY_NOT_FOUND, id);
        }
        return row;
    }
}
