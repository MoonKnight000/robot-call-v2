package uz.murodjon.robotcallv2.company.infrastructure.persistence.adapter;

import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.mapper.CompanyEntityMapper;
import uz.murodjon.robotcallv2.company.application.port.output.CompanyRepository;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyFilter;
import uz.murodjon.robotcallv2.company.domain.enums.CompanyStatus;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.util.LikePatterns;
import uz.murodjon.robotcallv2.storage.infrastructure.persistence.entity.StoredFileEntity;
import uz.murodjon.robotcallv2.storage.infrastructure.persistence.repository.StoredFileJpaRepository;

import java.time.Instant;
import java.util.List;

@Component
public class CompanyRepositoryAdapter implements CompanyRepository {

    private final CompanyJpaRepository jpaRepository;
    private final JdbcTemplate jdbcTemplate;
    private final CompanyEntityMapper mapper;
    private final StoredFileJpaRepository storedFileJpaRepository;

    public CompanyRepositoryAdapter(CompanyJpaRepository jpaRepository, JdbcTemplate jdbcTemplate,
                                    CompanyEntityMapper mapper,
                                    StoredFileJpaRepository storedFileJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.mapper = mapper;
        this.storedFileJpaRepository = storedFileJpaRepository;
    }

    @Override
    public long create(Company company) {
        CompanyEntity entity = new CompanyEntity();
        entity.setName(company.name());
        entity.setStatus(CompanyStatus.ACTIVE);
        entity.setCreatedAt(Instant.now());
        return jpaRepository.save(entity).getId();
    }

    /**
     * Native SQL rather than JPA: {@code company.id} is {@code GenerationType.IDENTITY},
     * so a persist would discard the caller's id and let the sequence pick another one.
     */
    @Override
    public void createWithId(long id, String name) {
        jdbcTemplate.update(
                "INSERT INTO company(id, name, status, created_at) VALUES (?, ?, ?, now()) "
                        + "ON CONFLICT (id) DO NOTHING",
                id, name, CompanyStatus.ACTIVE.name());
    }

    @Override
    public boolean existsById(long id) {
        return jpaRepository.existsById(id);
    }

    @Override
    public Company find(long id) {
        return jpaRepository.findById(id).map(mapper::toCompany).orElse(null);
    }

    @Override
    public void update(long id, Company company) {
        jpaRepository.findById(id).ifPresent(entity -> {
            entity.setName(company.name());
            entity.setAddress(company.address());
            jpaRepository.save(entity);
        });
    }

    @Override
    public void updateLogoFileId(long id, Long logoFileId) {
        jpaRepository.findById(id).ifPresent(entity -> {
            entity.setLogoFile(storedFileReference(logoFileId));
            jpaRepository.save(entity);
        });
    }

    @Override
    public void updateStatus(long id, CompanyStatus status) {
        jpaRepository.findById(id).ifPresent(entity -> {
            entity.setStatus(status);
            jpaRepository.save(entity);
        });
    }

    @Override
    public List<Company> findAllCompanies() {
        return jpaRepository.findAll().stream().map(mapper::toCompany).toList();
    }

    @Override
    public PageableData<Company> findPage(CompanyFilter filter) {
        // The page carries its total, so no second count query of our own.
        Page<CompanyEntity> page = jpaRepository.findAll(searchSpecification(filter.search()), filter.pageable());
        return PageableData.of(page.getContent().stream().map(mapper::toCompany).toList(),
                filter.pageOrDefault(), filter.sizeOrDefault(), page.getTotalElements());
    }

    /** Name or address contains the search text; a blank search does not narrow. */
    private static Specification<CompanyEntity> searchSpecification(String search) {
        String pattern = search == null ? null : LikePatterns.buildContainsPattern(search.toLowerCase());
        return (root, query, cb) -> pattern == null
                ? cb.conjunction()
                : cb.or(cb.like(cb.lower(root.get("name")), pattern, LikePatterns.ESCAPE),
                        cb.like(cb.lower(root.get("address")), pattern, LikePatterns.ESCAPE));
    }

    /** Null when the company has no logo. */
    private StoredFileEntity storedFileReference(Long id) {
        return id != null ? storedFileJpaRepository.getReferenceById(id) : null;
    }
}
