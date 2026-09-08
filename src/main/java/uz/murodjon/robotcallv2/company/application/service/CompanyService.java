package uz.murodjon.robotcallv2.company.application.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.company.application.dto.CreateCompanyRequest;
import uz.murodjon.robotcallv2.company.application.dto.UpdateCompanyRequest;
import uz.murodjon.robotcallv2.company.application.dto.UpdateCompanyStatusRequest;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;
import uz.murodjon.robotcallv2.company.application.port.output.CompanyRepository;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyFilter;
import uz.murodjon.robotcallv2.role.application.port.input.RoleUseCase;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.storage.application.service.ImageUploadService;
import uz.murodjon.robotcallv2.storage.domain.entity.StoredFile;

import java.util.List;

@Service
public class CompanyService implements CompanyUseCase {

    private final CompanyRepository repository;
    private final CompanyConfigUseCase config;
    private final ImageUploadService images;
    private final CompanyAccessGuard access;
    private final AuditService audit;
    private final RoleUseCase roleUseCase;

    public CompanyService(CompanyRepository repository, CompanyConfigUseCase config, ImageUploadService images,
                          CompanyAccessGuard access, AuditService audit, RoleUseCase roleUseCase) {
        this.repository = repository;
        this.config = config;
        this.images = images;
        this.access = access;
        this.audit = audit;
        this.roleUseCase = roleUseCase;
    }

    @Override
    public Company create(CreateCompanyRequest r) {
        long id = repository.create(Company.named(r.name()));
        config.createDefault(id);
        // Without its system roles a new tenant cannot be given a single user.
        roleUseCase.createSystemRoles(id);
        audit.record(id, "COMPANY_CREATE", "company", String.valueOf(id), r.name());
        return repository.find(id);
    }

    @Override
    public Company update(long callerCompanyId, long id, UpdateCompanyRequest r) {
        requireCompany(callerCompanyId, id);
        repository.update(id, Company.profile(r.name(), r.address()));
        audit.record(id, "COMPANY_UPDATE", "company", String.valueOf(id), r.name());
        return requireCompany(callerCompanyId, id);
    }

    @Override
    public Company updateStatus(long id, UpdateCompanyStatusRequest r) {
        requireCompanyUnscoped(id);
        repository.updateStatus(id, r.status());
        audit.record(id, "COMPANY_STATUS_UPDATE", "company", String.valueOf(id), r.status().name());
        return requireCompanyUnscoped(id);
    }

    @Override
    public Company uploadLogo(long callerCompanyId, long id, MultipartFile file) {
        requireCompany(callerCompanyId, id);
        StoredFile stored = images.upload(file, id);
        repository.updateLogoFileId(id, stored.id());
        audit.record(id, "COMPANY_LOGO_UPLOAD", "company", String.valueOf(id), String.valueOf(stored.id()));
        return requireCompany(callerCompanyId, id);
    }

    @Override
    public PageableData<Company> list(CompanyFilter filter) {
        List<Company> rows = repository.findAll(filter);
        long total = repository.count(filter);
        return PageableData.of(rows, filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    @Override
    public Company findById(long id) {
        return repository.find(id);
    }

    @Override
    public List<Company> findAllForWarmup() {
        return repository.findAll();
    }

    @Override
    public Company requireCompany(long callerCompanyId, long id) {
        access.requireOwnOrSuperadmin(callerCompanyId, id);
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
