package uz.murodjon.uysotvoice.company.service;

import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.audit.service.AuditService;
import uz.murodjon.uysotvoice.company.dto.*;
import uz.murodjon.uysotvoice.company.repository.CompanyRepository;
import uz.murodjon.uysotvoice.shared.api.PageableData;
import uz.murodjon.uysotvoice.shared.exception.NotFoundException;

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
    private final AuditService audit;

    public CompanyService(CompanyRepository repo, CompanyConfigService config, AuditService audit) {
        this.repo = repo;
        this.config = config;
        this.audit = audit;
    }

    /** Also provisions a default {@link CompanyConfig} for the new company. */
    public Company create(CreateCompanyRequest r) {
        long id = repo.create(r.name());
        config.createDefault(id);
        audit.record("COMPANY_CREATE", "company", String.valueOf(id), r.name());
        return repo.find(id);
    }

    public Company update(long id, UpdateCompanyRequest r) {
        requireCompany(id);
        repo.update(id, r.name(), r.status(), r.logoUrl(), r.address());
        audit.record("COMPANY_UPDATE", "company", String.valueOf(id), r.name());
        return requireCompany(id);
    }

    public PageableData<Company> list(CompanyFilter filter) {
        List<Company> rows = repo.findAll(filter);
        long total = repo.count(filter);
        return PageableData.of(rows, filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    /** As {@link CompanyRepository#find}, for the REST API — a missing company is a 404, not a null. */
    public Company requireCompany(long id) {
        Company row = repo.find(id);
        if (row == null) {
            throw new NotFoundException("company", id);
        }
        return row;
    }
}
