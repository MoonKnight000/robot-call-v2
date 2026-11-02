package uz.murodjon.robotcallv2.integration.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.integration.application.dto.CreateIntegrationCategoryRequest;
import uz.murodjon.robotcallv2.integration.application.dto.CreateIntegrationRequest;
import uz.murodjon.robotcallv2.integration.application.dto.IntegrationDefinitionRow;
import uz.murodjon.robotcallv2.integration.application.dto.UpdateIntegrationCategoryRequest;
import uz.murodjon.robotcallv2.integration.application.dto.UpdateIntegrationRequest;
import uz.murodjon.robotcallv2.integration.application.port.input.IntegrationCatalogUseCase;
import uz.murodjon.robotcallv2.integration.application.port.output.IntegrationCatalogRepository;
import uz.murodjon.robotcallv2.integration.domain.entity.IntegrationCategory;
import uz.murodjon.robotcallv2.integration.domain.entity.IntegrationDefinition;
import uz.murodjon.robotcallv2.integration.domain.service.IntegrationCatalogValidator;
import uz.murodjon.robotcallv2.integration.domain.service.IntegrationIconValidator;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import uz.murodjon.robotcallv2.storage.application.port.input.FileStorageUseCase;
import uz.murodjon.robotcallv2.storage.domain.entity.StoredFile;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class IntegrationCatalogService implements IntegrationCatalogUseCase {

    /** The gap left between positions, so an entry can be slotted in between two others later. */
    private static final int SORT_STEP = 10;

    private final IntegrationCatalogRepository repository;
    private final FileStorageUseCase fileStorageUseCase;
    private final AuditUseCase audit;

    public IntegrationCatalogService(IntegrationCatalogRepository repository, FileStorageUseCase fileStorageUseCase,
                                     AuditUseCase audit) {
        this.repository = repository;
        this.fileStorageUseCase = fileStorageUseCase;
        this.audit = audit;
    }

    @Override
    public List<IntegrationDefinitionRow> findIntegrations() {
        Map<String, String> categoryNames = repository.findAllCategories().stream()
                .collect(Collectors.toMap(IntegrationCategory::key, IntegrationCategory::name));
        return repository.findAllIntegrations().stream()
                .map(definition -> IntegrationDefinitionRow.of(definition, categoryNames.get(definition.category())))
                .toList();
    }

    @Override
    public IntegrationDefinitionRow createIntegration(long companyId, CreateIntegrationRequest request) {
        String key = IntegrationCatalogValidator.normalizeKey(request.key());
        String name = IntegrationCatalogValidator.normalizeName(request.name());
        String category = requireCategory(request.category()).key();
        if (repository.findIntegration(key).isPresent()) {
            throw new ConflictException(ErrorCode.INTEGRATION_KEY_TAKEN, key);
        }
        int sortOrder = request.sortOrder() != null ? request.sortOrder() : nextIntegrationSortOrder();
        IntegrationDefinition created = repository.createIntegration(IntegrationDefinition.planned(key, name, category,
                IntegrationCatalogValidator.normalizeAuthMethods(request.authMethods()), sortOrder,
                requireIconFile(companyId, request.iconFileId())));
        audit.record(companyId, "INTEGRATION_CREATE", "integration_provider", key, name);
        return toRow(created);
    }

    @Override
    public IntegrationDefinitionRow updateIntegration(long companyId, String key, UpdateIntegrationRequest request) {
        IntegrationDefinition existing = requireIntegration(key);
        String name = IntegrationCatalogValidator.normalizeName(request.name());
        String category = requireCategory(request.category()).key();
        int sortOrder = request.sortOrder() != null ? request.sortOrder() : existing.sortOrder();
        Long iconFileId = requireIconFile(companyId, request.iconFileId());
        IntegrationDefinition updated = repository.updateIntegration(existing.edited(name, category,
                IntegrationCatalogValidator.normalizeAuthMethods(request.authMethods()), iconFileId, sortOrder));
        if (!Objects.equals(existing.iconFileId(), iconFileId)) {
            removeStoredFile(companyId, existing.iconFileId());
        }
        audit.record(companyId, "INTEGRATION_UPDATE", "integration_provider", existing.key(), name);
        return toRow(updated);
    }

    /**
     * A logo must be an image of the admin's own, uploaded through the file API beforehand and small
     * enough; null means no logo.
     */
    private Long requireIconFile(long companyId, Long fileId) {
        if (fileId == null) {
            return null;
        }
        StoredFile file = fileStorageUseCase.find(fileId);
        if (file == null || file.companyId() != companyId) {
            throw new NotFoundException(ErrorCode.FILE_NOT_FOUND, fileId);
        }
        IntegrationIconValidator.validate(file.format(), file.sizeBytes());
        return fileId;
    }

    /** The replaced or removed logo's file; a bucket that refuses leaves the row, so it can be removed again. */
    private void removeStoredFile(long companyId, Long fileId) {
        if (fileId != null) {
            fileStorageUseCase.delete(companyId, fileId);
        }
    }

    @Override
    public void deleteIntegration(long companyId, String key) {
        IntegrationDefinition existing = requireIntegration(key);
        if (existing.builtIn()) {
            throw new ValidationException(ErrorCode.INTEGRATION_BUILT_IN_LOCKED, existing.key());
        }
        repository.deleteIntegration(existing.key());
        removeStoredFile(companyId, existing.iconFileId());
        audit.record(companyId, "INTEGRATION_DELETE", "integration_provider", existing.key(), null);
    }

    @Override
    public List<IntegrationCategory> findCategories() {
        return repository.findAllCategories();
    }

    @Override
    public IntegrationCategory createCategory(long companyId, CreateIntegrationCategoryRequest request) {
        String key = IntegrationCatalogValidator.normalizeKey(request.key());
        String name = IntegrationCatalogValidator.normalizeName(request.name());
        if (repository.findCategory(key).isPresent()) {
            throw new ConflictException(ErrorCode.INTEGRATION_CATEGORY_KEY_TAKEN, key);
        }
        int sortOrder = request.sortOrder() != null ? request.sortOrder() : nextCategorySortOrder();
        IntegrationCategory created = repository.createCategory(new IntegrationCategory(key, name, sortOrder));
        audit.record(companyId, "INTEGRATION_CATEGORY_CREATE", "integration_category", key, name);
        return created;
    }

    @Override
    public IntegrationCategory updateCategory(long companyId, String key, UpdateIntegrationCategoryRequest request) {
        IntegrationCategory existing = requireCategory(key);
        String name = IntegrationCatalogValidator.normalizeName(request.name());
        int sortOrder = request.sortOrder() != null ? request.sortOrder() : existing.sortOrder();
        IntegrationCategory updated = repository.updateCategory(new IntegrationCategory(existing.key(), name, sortOrder));
        audit.record(companyId, "INTEGRATION_CATEGORY_UPDATE", "integration_category", existing.key(), name);
        return updated;
    }

    @Override
    public void deleteCategory(long companyId, String key) {
        IntegrationCategory existing = requireCategory(key);
        long used = repository.countIntegrationsInCategory(existing.key());
        if (used > 0) {
            throw new ConflictException(ErrorCode.INTEGRATION_CATEGORY_IN_USE, existing.key(), used);
        }
        repository.deleteCategory(existing.key());
        audit.record(companyId, "INTEGRATION_CATEGORY_DELETE", "integration_category", existing.key(), null);
    }

    private IntegrationDefinition requireIntegration(String key) {
        String normalized = IntegrationCatalogValidator.normalizeKey(key);
        return repository.findIntegration(normalized)
                .orElseThrow(() -> new NotFoundException(ErrorCode.INTEGRATION_NOT_FOUND, normalized));
    }

    private IntegrationCategory requireCategory(String key) {
        String normalized = IntegrationCatalogValidator.normalizeKey(key);
        return repository.findCategory(normalized)
                .orElseThrow(() -> new NotFoundException(ErrorCode.INTEGRATION_CATEGORY_NOT_FOUND, normalized));
    }

    private int nextIntegrationSortOrder() {
        return repository.findAllIntegrations().stream().mapToInt(IntegrationDefinition::sortOrder).max().orElse(0)
                + SORT_STEP;
    }

    private int nextCategorySortOrder() {
        return repository.findAllCategories().stream().mapToInt(IntegrationCategory::sortOrder).max().orElse(0)
                + SORT_STEP;
    }

    private IntegrationDefinitionRow toRow(IntegrationDefinition definition) {
        Map<String, String> categoryNames = repository.findAllCategories().stream()
                .collect(Collectors.toMap(IntegrationCategory::key, IntegrationCategory::name));
        return IntegrationDefinitionRow.of(definition, categoryNames.get(definition.category()));
    }
}
