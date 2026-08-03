package uz.murodjon.uysotvoice.company.repository;

import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.company.dto.CompanyFilter;
import uz.murodjon.uysotvoice.company.dto.Company;
import uz.murodjon.uysotvoice.company.entity.CompanyEntity;
import uz.murodjon.uysotvoice.company.enums.CompanyStatus;

import java.time.Instant;
import java.util.List;

/**
 * JPA-backed DAO for {@code company} (ROADMAP B.1). Unlike every other repository in
 * the project, this one is deliberately <em>not</em> scoped by {@code CurrentCompany} —
 * a company is the tenant boundary itself, not a resource scoped to one.
 */
@Repository
public class CompanyRepository {

    private final CompanyJpaRepository jpa;

    public CompanyRepository(CompanyJpaRepository jpa) {
        this.jpa = jpa;
    }

    public long create(String name) {
        CompanyEntity entity = new CompanyEntity();
        entity.setName(name);
        entity.setStatus(CompanyStatus.ACTIVE);
        entity.setCreatedAt(Instant.now());
        return jpa.save(entity).getId();
    }

    public Company find(long id) {
        return jpa.findById(id).map(CompanyRepository::toRow).orElse(null);
    }

    public void update(long id, String name, CompanyStatus status, String logoUrl, String address) {
        jpa.findById(id).ifPresent(entity -> {
            entity.setName(name);
            entity.setStatus(status);
            entity.setLogoUrl(logoUrl);
            entity.setAddress(address);
            jpa.save(entity);
        });
    }

    public List<Company> findAll(CompanyFilter filter) {
        return jpa.search(likePattern(filter.search()), filter.pageable()).stream()
                .map(CompanyRepository::toRow)
                .toList();
    }

    public long count(CompanyFilter filter) {
        return jpa.countSearch(likePattern(filter.search()));
    }

    private static String likePattern(String search) {
        return search == null || search.isBlank() ? null : "%" + search.trim().toLowerCase() + "%";
    }

    private static Company toRow(CompanyEntity e) {
        return new Company(e.getId(), e.getName(), e.getStatus(), e.getCreatedAt(), e.getLogoUrl(), e.getAddress());
    }
}
