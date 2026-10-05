package uz.murodjon.robotcallv2.company.application.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.company.application.dto.CreateCompanyRequest;
import uz.murodjon.robotcallv2.company.application.dto.UpdateCompanyRequest;
import uz.murodjon.robotcallv2.company.application.dto.UpdateCompanyStatusRequest;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;
import uz.murodjon.robotcallv2.company.application.port.output.CompanyRepository;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyFilter;
import uz.murodjon.robotcallv2.company.domain.enums.CompanyStatus;
import uz.murodjon.robotcallv2.company.domain.event.CompanyCreatedEvent;
import uz.murodjon.robotcallv2.company.domain.event.CompanySuspendedEvent;
import uz.murodjon.robotcallv2.security.CompanyAccessGuard;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.UploadedFile;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.storage.application.port.input.ImageUploadUseCase;
import uz.murodjon.robotcallv2.storage.domain.entity.StoredFile;

import java.util.List;

@Service
public class CompanyService implements CompanyUseCase {

    private final CompanyRepository repository;
    private final CompanyConfigUseCase config;
    private final ImageUploadUseCase imageUploadUseCase;
    private final CompanyAccessGuard companyAccessGuard;
    private final AuditUseCase audit;
    /**
     * Roles and sessions belong to features that depend on this one; they hear about a new or
     * suspended company from an event (handled synchronously) instead of being called.
     */
    private final ApplicationEventPublisher eventPublisher;

    public CompanyService(CompanyRepository repository, CompanyConfigUseCase config,
                          ImageUploadUseCase imageUploadUseCase, CompanyAccessGuard companyAccessGuard,
                          AuditUseCase audit, ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.config = config;
        this.imageUploadUseCase = imageUploadUseCase;
        this.companyAccessGuard = companyAccessGuard;
        this.audit = audit;
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

    @Override
    public Company updateStatus(long id, UpdateCompanyStatusRequest request) {
        requireCompanyUnscoped(id);
        repository.updateStatus(id, request.status());
        if (request.status() == CompanyStatus.SUSPENDED) {
            // Login and refresh refuse a suspended company from now on; the auth feature
            // ends the sessions already open, instead of leaving each one its last access token.
            eventPublisher.publishEvent(new CompanySuspendedEvent(id));
        }
        audit.record(id, "COMPANY_STATUS_UPDATE", "company", String.valueOf(id), request.status().name());
        return requireCompanyUnscoped(id);
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

    private Company requireCompanyUnscoped(long id) {
        Company row = repository.find(id);
        if (row == null) {
            throw new NotFoundException(ErrorCode.COMPANY_NOT_FOUND, id);
        }
        return row;
    }
}
