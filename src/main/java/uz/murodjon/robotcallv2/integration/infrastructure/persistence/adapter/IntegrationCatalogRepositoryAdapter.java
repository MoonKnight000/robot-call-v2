package uz.murodjon.robotcallv2.integration.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.integration.application.port.output.IntegrationCatalogRepository;
import uz.murodjon.robotcallv2.integration.domain.entity.IntegrationCategory;
import uz.murodjon.robotcallv2.integration.domain.entity.IntegrationDefinition;
import uz.murodjon.robotcallv2.integration.infrastructure.persistence.entity.IntegrationCategoryEntity;
import uz.murodjon.robotcallv2.integration.infrastructure.persistence.entity.IntegrationProviderEntity;
import uz.murodjon.robotcallv2.integration.infrastructure.persistence.mapper.IntegrationCatalogEntityMapper;
import uz.murodjon.robotcallv2.integration.infrastructure.persistence.repository.IntegrationCategoryJpaRepository;
import uz.murodjon.robotcallv2.integration.infrastructure.persistence.repository.IntegrationProviderJpaRepository;

import uz.murodjon.robotcallv2.storage.infrastructure.persistence.entity.StoredFileEntity;
import uz.murodjon.robotcallv2.storage.infrastructure.persistence.repository.StoredFileJpaRepository;

import java.util.List;
import java.util.Optional;

@Component
public class IntegrationCatalogRepositoryAdapter implements IntegrationCatalogRepository {

    private final IntegrationProviderJpaRepository providerJpaRepository;
    private final IntegrationCategoryJpaRepository categoryJpaRepository;
    private final IntegrationCatalogEntityMapper mapper;
    private final StoredFileJpaRepository storedFileJpaRepository;

    public IntegrationCatalogRepositoryAdapter(IntegrationProviderJpaRepository providerJpaRepository,
                                               IntegrationCategoryJpaRepository categoryJpaRepository,
                                               IntegrationCatalogEntityMapper mapper,
                                               StoredFileJpaRepository storedFileJpaRepository) {
        this.providerJpaRepository = providerJpaRepository;
        this.categoryJpaRepository = categoryJpaRepository;
        this.mapper = mapper;
        this.storedFileJpaRepository = storedFileJpaRepository;
    }

    @Override
    public List<IntegrationDefinition> findAllIntegrations() {
        return providerJpaRepository.findAllByOrderBySortOrderAscKeyAsc().stream()
                .map(mapper::toIntegrationDefinition).toList();
    }

    @Override
    public Optional<IntegrationDefinition> findIntegration(String key) {
        return providerJpaRepository.findById(key).map(mapper::toIntegrationDefinition);
    }

    @Override
    public IntegrationDefinition createIntegration(IntegrationDefinition integration) {
        IntegrationProviderEntity entity = new IntegrationProviderEntity();
        mapper.applyIntegration(entity, integration);
        entity.setIconFile(storedFileReference(integration.iconFileId()));
        return mapper.toIntegrationDefinition(providerJpaRepository.save(entity));
    }

    @Override
    public IntegrationDefinition updateIntegration(IntegrationDefinition integration) {
        return providerJpaRepository.findById(integration.key()).map(entity -> {
            mapper.applyIntegration(entity, integration);
            entity.setIconFile(storedFileReference(integration.iconFileId()));
            return mapper.toIntegrationDefinition(providerJpaRepository.save(entity));
        }).orElse(null);
    }

    @Override
    public void deleteIntegration(String key) {
        providerJpaRepository.deleteById(key);
    }


    @Override
    public long countIntegrationsInCategory(String categoryKey) {
        return providerJpaRepository.countByCategory(categoryKey);
    }

    @Override
    public List<IntegrationCategory> findAllCategories() {
        return categoryJpaRepository.findAllByOrderBySortOrderAscKeyAsc().stream()
                .map(mapper::toIntegrationCategory).toList();
    }

    @Override
    public Optional<IntegrationCategory> findCategory(String key) {
        return categoryJpaRepository.findById(key).map(mapper::toIntegrationCategory);
    }

    @Override
    public IntegrationCategory createCategory(IntegrationCategory category) {
        IntegrationCategoryEntity entity = new IntegrationCategoryEntity();
        mapper.applyCategory(entity, category);
        return mapper.toIntegrationCategory(categoryJpaRepository.save(entity));
    }

    @Override
    public IntegrationCategory updateCategory(IntegrationCategory category) {
        return categoryJpaRepository.findById(category.key()).map(entity -> {
            mapper.applyCategory(entity, category);
            return mapper.toIntegrationCategory(categoryJpaRepository.save(entity));
        }).orElse(null);
    }

    @Override
    public void deleteCategory(String key) {
        categoryJpaRepository.deleteById(key);
    }

    /** Null when the entry has no logo. */
    private StoredFileEntity storedFileReference(Long id) {
        return id != null ? storedFileJpaRepository.getReferenceById(id) : null;
    }
}
