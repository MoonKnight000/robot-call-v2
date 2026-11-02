package uz.murodjon.robotcallv2.integration.application.port.output;

import uz.murodjon.robotcallv2.integration.domain.entity.IntegrationCategory;
import uz.murodjon.robotcallv2.integration.domain.entity.IntegrationDefinition;

import java.util.List;
import java.util.Optional;

/** The integration catalog the platform admin keeps: its entries and their categories. */
public interface IntegrationCatalogRepository {

    /** Every entry, in the order companies see them. */
    List<IntegrationDefinition> findAllIntegrations();

    Optional<IntegrationDefinition> findIntegration(String key);

    IntegrationDefinition createIntegration(IntegrationDefinition integration);

    /** Replaces the name, category, auth methods, logo and position of the entry with the same key. */
    IntegrationDefinition updateIntegration(IntegrationDefinition integration);

    void deleteIntegration(String key);


    long countIntegrationsInCategory(String categoryKey);

    /** Every category, in the order companies see them. */
    List<IntegrationCategory> findAllCategories();

    Optional<IntegrationCategory> findCategory(String key);

    IntegrationCategory createCategory(IntegrationCategory category);

    /** Replaces the name and position of the category with the same key. */
    IntegrationCategory updateCategory(IntegrationCategory category);

    void deleteCategory(String key);
}
