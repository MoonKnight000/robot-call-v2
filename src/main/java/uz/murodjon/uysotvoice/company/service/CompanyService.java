package uz.murodjon.uysotvoice.company.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import uz.murodjon.uysotvoice.audit.service.AuditService;
import uz.murodjon.uysotvoice.company.dto.*;
import uz.murodjon.uysotvoice.company.repository.CompanyRepository;
import uz.murodjon.uysotvoice.shared.api.PageableData;
import uz.murodjon.uysotvoice.shared.exception.NotFoundException;
import uz.murodjon.uysotvoice.storage.dto.StoredFile;
import uz.murodjon.uysotvoice.storage.service.ImageUploadService;

import java.util.List;

/**
 * Company (tenant) CRUD (ROADMAP B.1) — onboarding a new tenant and editing its
 * settings. Not scoped by {@link CurrentCompany}: a company is the tenant boundary
 * every other feature is filtered by, not a resource that itself belongs to one.
 */
@Service
public class CompanyService {

    private final CompanyRepository repo;
    private final CompanyConfigService config;
    private final ImageUploadService images;
    private final CompanyAccessGuard access;
    private final AuditService audit;

    public CompanyService(CompanyRepository repo, CompanyConfigService config, ImageUploadService images,
                          CompanyAccessGuard access, AuditService audit) {
        this.repo = repo;
        this.config = config;
        this.images = images;
        this.access = access;
        this.audit = audit;
    }

    /** SUPERADMIN-only (report #3) — also provisions a default {@link CompanyConfig} for the new company. */
    public Company create(CreateCompanyRequest r) {
        long id = repo.create(r.name());
        config.createDefault(id);
        audit.record("COMPANY_CREATE", "company", String.valueOf(id), r.name());
        return repo.find(id);
    }

    /** A tenant's own {@code ADMIN}, self-scoped (report #3) — identity only, never {@code status}. */
    public Company update(long id, UpdateCompanyRequest r) {
        requireCompany(id);
        repo.update(id, r.name(), r.address());
        audit.record("COMPANY_UPDATE", "company", String.valueOf(id), r.name());
        return requireCompany(id);
    }

    /** SUPERADMIN-only (report #3) — the only way a company's status changes now, unscoped by design. */
    public Company updateStatus(long id, UpdateCompanyStatusRequest r) {
        requireCompanyUnscoped(id);
        repo.updateStatus(id, r.status());
        audit.record("COMPANY_STATUS_UPDATE", "company", String.valueOf(id), r.status().name());
        return requireCompanyUnscoped(id);
    }

    /** {@code POST /api/companies/{id}/logo} (report #4) — replaces {@code logoFileId}, nothing else. */
    public Company uploadLogo(long id, MultipartFile file) {
        requireCompany(id);
        StoredFile stored = images.upload(file, id);
        repo.updateLogoFileId(id, stored.id());
        audit.record("COMPANY_LOGO_UPLOAD", "company", String.valueOf(id), String.valueOf(stored.id()));
        return requireCompany(id);
    }

    /** SUPERADMIN-only (report #3). */
    public PageableData<Company> list(CompanyFilter filter) {
        List<Company> rows = repo.findAll(filter);
        long total = repo.count(filter);
        return PageableData.of(rows, filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    /**
     * As {@link CompanyRepository#find}, for the REST API — a missing company is a 404,
     * not a null. Also enforces {@link CompanyAccessGuard} (report #3): a tenant's own
     * {@code ADMIN} only ever sees its own company through this method; {@code
     * SUPERADMIN} sees any of them. Used by {@code get}/{@link #update}/{@link
     * #uploadLogo} — every controller-facing read/write of a company by id.
     */
    public Company requireCompany(long id) {
        access.requireOwnOrSuperadmin(id);
        return requireCompanyUnscoped(id);
    }

    /** As {@link #requireCompany}, without the ownership check — {@link #create}/{@link #updateStatus} (SUPERADMIN-only paths). */
    private Company requireCompanyUnscoped(long id) {
        Company row = repo.find(id);
        if (row == null) {
            throw new NotFoundException("company", id);
        }
        return row;
    }
}
