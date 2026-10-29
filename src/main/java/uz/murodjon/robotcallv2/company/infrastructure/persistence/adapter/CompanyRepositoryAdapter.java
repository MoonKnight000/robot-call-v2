package uz.murodjon.robotcallv2.company.infrastructure.persistence.adapter;

import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.company.application.port.output.CompanyRepository;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyFilter;
import uz.murodjon.robotcallv2.company.domain.enums.CompanyStatus;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyFeatureEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyIntegrationAccessEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanySwitchRow;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.mapper.CompanyEntityMapper;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.role.domain.enums.Feature;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.integration.IntegrationProvider;
import uz.murodjon.robotcallv2.shared.util.LikePatterns;
import uz.murodjon.robotcallv2.storage.infrastructure.persistence.entity.StoredFileEntity;
import uz.murodjon.robotcallv2.storage.infrastructure.persistence.repository.StoredFileJpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

@Component
public class CompanyRepositoryAdapter implements CompanyRepository {

    private final CompanyJpaRepository jpaRepository;
    private final CompanyEntityMapper mapper;
    private final StoredFileJpaRepository storedFileJpaRepository;

    public CompanyRepositoryAdapter(CompanyJpaRepository jpaRepository,
                                    CompanyEntityMapper mapper,
                                    StoredFileJpaRepository storedFileJpaRepository) {
        this.jpaRepository = jpaRepository;
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

    @Override
    public void createWithId(long id, String name) {
        jpaRepository.insertWithIdIfAbsent(id, name);
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
    public void updatePlatformSettings(long id, Company company) {
        jpaRepository.findById(id).ifPresent(entity -> {
            Instant now = Instant.now();
            entity.setName(company.name());
            entity.setAddress(company.address());
            entity.setMaxConcurrentCalls(company.maxConcurrentCalls());
            entity.setExpiresAt(company.expiresAt());
            syncSwitchRows(Feature.findSwitchable(), company.disabledFeatures(), entity.getFeatures(),
                    CompanyFeatureEntity::getFeature, feature -> {
                        CompanyFeatureEntity row = new CompanyFeatureEntity();
                        row.setCompany(entity);
                        row.setFeature(feature);
                        return row;
                    }, now);
            syncSwitchRows(List.of(IntegrationProvider.values()), company.disabledIntegrations(),
                    entity.getIntegrationAccess(), CompanyIntegrationAccessEntity::getProvider, provider -> {
                        CompanyIntegrationAccessEntity row = new CompanyIntegrationAccessEntity();
                        row.setCompany(entity);
                        row.setProvider(provider);
                        return row;
                    }, now);
            jpaRepository.save(entity);
        });
    }

    @Override
    public List<Company> findActiveExpiredBy(Instant now) {
        return jpaRepository.findByStatusAndExpiresAtLessThanEqual(CompanyStatus.ACTIVE, now).stream()
                .map(mapper::toCompany).toList();
    }

    @Override
    public List<Company> findAllCompanies() {
        return jpaRepository.findAll().stream().map(mapper::toCompany).toList();
    }

    @Override
    public PageableData<Company> findPage(CompanyFilter filter) {
        // The page carries its total, so no second count query of our own.
        Page<CompanyEntity> page = jpaRepository.findAll(
                searchSpecification(filter.search()).and(statusSpecification(filter.status())), filter.pageable());
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

    /** The given status only; with none, every company that is not soft-deleted. */
    private static Specification<CompanyEntity> statusSpecification(CompanyStatus status) {
        return (root, query, cb) -> status == null
                ? cb.notEqual(root.get("status"), CompanyStatus.DELETED)
                : cb.equal(root.get("status"), status);
    }

    /**
     * One row per key, so the table reads as the company's full switchboard; a row's
     * {@code updatedAt} moves only when it is new or its value changes.
     */
    private static <K, R extends CompanySwitchRow> void syncSwitchRows(Collection<K> keys, Set<K> disabled,
                                                                      Set<R> rows, Function<R, K> keyOf,
                                                                      Function<K, R> create, Instant now) {
        for (K key : keys) {
            R row = rows.stream().filter(existing -> keyOf.apply(existing) == key).findFirst()
                    .orElseGet(() -> {
                        R created = create.apply(key);
                        rows.add(created);
                        return created;
                    });
            boolean enabled = !disabled.contains(key);
            if (row.getUpdatedAt() == null || row.isEnabled() != enabled) {
                row.setEnabled(enabled);
                row.setUpdatedAt(now);
            }
        }
    }

    /** Null when the company has no logo. */
    private StoredFileEntity storedFileReference(Long id) {
        return id != null ? storedFileJpaRepository.getReferenceById(id) : null;
    }
}
