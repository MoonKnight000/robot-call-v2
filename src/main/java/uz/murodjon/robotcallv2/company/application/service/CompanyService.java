package uz.murodjon.robotcallv2.company.application.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.company.application.dto.CompanyFilter;
import uz.murodjon.robotcallv2.company.application.dto.CreateCompanyRequest;
import uz.murodjon.robotcallv2.company.application.dto.UpdateCompanyRequest;
import uz.murodjon.robotcallv2.company.application.dto.UpdateCompanyStatusRequest;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;
import uz.murodjon.robotcallv2.company.application.port.output.CompanyRepository;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.storage.application.service.ImageUploadService;
import uz.murodjon.robotcallv2.storage.domain.entity.StoredFile;

import java.util.List;

@Service
public class CompanyService implements CompanyUseCase {

    private final CompanyRepository repo;
    private final CompanyConfigUseCase config;
    private final ImageUploadService images;
    private final CompanyAccessGuard access;
    private final AuditService audit;

    public CompanyService(CompanyRepository repo, CompanyConfigUseCase config, ImageUploadService images,
                          CompanyAccessGuard access, AuditService audit) {
        this.repo = repo;
        this.config = config;
        this.images = images;
        this.access = access;
        this.audit = audit;
    }

    @Override
    public Company create(CreateCompanyRequest r) {
        long id = repo.create(r.name());
        config.createDefault(id);
        audit.record("COMPANY_CREATE", "company", String.valueOf(id), r.name());
        return repo.find(id);
    }

    @Override
    public Company update(long id, UpdateCompanyRequest r) {
        requireCompany(id);
        repo.update(id, r.name(), r.address());
        audit.record("COMPANY_UPDATE", "company", String.valueOf(id), r.name());
        return requireCompany(id);
    }

    @Override
    public Company updateStatus(long id, UpdateCompanyStatusRequest r) {
        requireCompanyUnscoped(id);
        repo.updateStatus(id, r.status());
        audit.record("COMPANY_STATUS_UPDATE", "company", String.valueOf(id), r.status().name());
        return requireCompanyUnscoped(id);
    }

    @Override
    public Company uploadLogo(long id, MultipartFile file) {
        requireCompany(id);
        StoredFile stored = images.upload(file, id);
        repo.updateLogoFileId(id, stored.id());
        audit.record("COMPANY_LOGO_UPLOAD", "company", String.valueOf(id), String.valueOf(stored.id()));
        return requireCompany(id);
    }

    @Override
    public PageableData<Company> list(CompanyFilter filter) {
        List<Company> rows = repo.findAll(filter);
        long total = repo.count(filter);
        return PageableData.of(rows, filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    @Override
    public Company findById(long id) {
        return repo.find(id);
    }

    @Override
    public List<Company> findAllForWarmup() {
        return repo.findAll();
    }

    @Override
    public Company requireCompany(long id) {
        access.requireOwnOrSuperadmin(id);
        return requireCompanyUnscoped(id);
    }

    private Company requireCompanyUnscoped(long id) {
        Company row = repo.find(id);
        if (row == null) {
            throw new NotFoundException(ErrorCode.COMPANY_NOT_FOUND, id);
        }
        return row;
    }
}
