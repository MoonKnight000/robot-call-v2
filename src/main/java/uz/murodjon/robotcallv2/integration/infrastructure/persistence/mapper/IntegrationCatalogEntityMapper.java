package uz.murodjon.robotcallv2.integration.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.integration.domain.entity.IntegrationCategory;
import uz.murodjon.robotcallv2.integration.domain.entity.IntegrationDefinition;
import uz.murodjon.robotcallv2.integration.domain.enums.IntegrationAuthMethod;
import uz.murodjon.robotcallv2.integration.infrastructure.persistence.entity.IntegrationCategoryEntity;
import uz.murodjon.robotcallv2.integration.infrastructure.persistence.entity.IntegrationProviderEntity;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class IntegrationCatalogEntityMapper {

    public IntegrationDefinition toIntegrationDefinition(IntegrationProviderEntity entity) {
        return new IntegrationDefinition(entity.getKey(), entity.getName(), entity.getCategory(),
                readAuthMethods(entity.getAuthMethods()), entity.isAvailable(), entity.getSortOrder(), entity.isBuiltIn(),
                entity.getIconFileId());
    }

    public IntegrationCategory toIntegrationCategory(IntegrationCategoryEntity entity) {
        return new IntegrationCategory(entity.getKey(), entity.getName(), entity.getSortOrder());
    }

    /** Writes {@code definition} into {@code entity}: a new row, or the entry's existing one. */
    public void applyIntegration(IntegrationProviderEntity entity, IntegrationDefinition definition) {
        entity.setKey(definition.key());
        entity.setName(definition.name());
        entity.setCategory(definition.category());
        entity.setAuthMethods(definition.authMethods().stream().map(Enum::name).collect(Collectors.joining(",")));
        entity.setAvailable(definition.available());
        entity.setSortOrder(definition.sortOrder());
        entity.setBuiltIn(definition.builtIn());
    }

    public void applyCategory(IntegrationCategoryEntity entity, IntegrationCategory category) {
        entity.setKey(category.key());
        entity.setName(category.name());
        entity.setSortOrder(category.sortOrder());
    }

    private static List<IntegrationAuthMethod> readAuthMethods(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        return Arrays.stream(stored.split(",")).map(String::strip).map(IntegrationAuthMethod::valueOf).toList();
    }
}
