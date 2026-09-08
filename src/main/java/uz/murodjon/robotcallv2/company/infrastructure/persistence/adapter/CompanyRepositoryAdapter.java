package uz.murodjon.robotcallv2.company.infrastructure.persistence.adapter;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.company.application.mapper.CompanyMapper;
import uz.murodjon.robotcallv2.company.application.port.output.CompanyRepository;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyFilter;
import uz.murodjon.robotcallv2.company.domain.enums.CompanyStatus;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.storage.infrastructure.persistence.entity.StoredFileEntity;
import uz.murodjon.robotcallv2.storage.infrastructure.persistence.repository.StoredFileJpaRepository;

import java.time.Instant;
import java.util.List;

@Component
public class CompanyRepositoryAdapter implements CompanyRepository {

    private final CompanyJpaRepository jpaRepository;
    private final JdbcTemplate jdbcTemplate;
    private final CompanyMapper mapper;
    private final StoredFileJpaRepository storedFileJpaRepository;

    public CompanyRepositoryAdapter(CompanyJpaRepository jpaRepository, JdbcTemplate jdbcTemplate,
                                    CompanyMapper mapper,
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
        return jpaRepository.findById(id).map(mapper::entityToDomain).orElse(null);
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
    public List<Company> findAll() {
        return jpaRepository.findAll().stream().map(mapper::entityToDomain).toList();
    }

    @Override
    public List<Company> findAll(CompanyFilter filter) {
        return jpaRepository.search(likePattern(filter.search()), filter.pageable()).stream()
                .map(mapper::entityToDomain)
                .toList();
    }

    @Override
    public long count(CompanyFilter filter) {
        return jpaRepository.countSearch(likePattern(filter.search()));
    }

    private static String likePattern(String search) {
        return search == null || search.isBlank() ? null : "%" + search.trim().toLowerCase() + "%";
    }

    /** Null when the company has no logo. */
    private StoredFileEntity storedFileReference(Long id) {
        return id != null ? storedFileJpaRepository.getReferenceById(id) : null;
    }
}
