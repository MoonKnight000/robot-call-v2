package uz.murodjon.robotcallv2.integration.application.port.input;

import uz.murodjon.robotcallv2.integration.application.dto.CreateIntegrationCategoryRequest;
import uz.murodjon.robotcallv2.integration.application.dto.CreateIntegrationRequest;
import uz.murodjon.robotcallv2.integration.application.dto.IntegrationDefinitionRow;
import uz.murodjon.robotcallv2.integration.application.dto.UpdateIntegrationCategoryRequest;
import uz.murodjon.robotcallv2.integration.application.dto.UpdateIntegrationRequest;
import uz.murodjon.robotcallv2.integration.domain.entity.IntegrationCategory;

import java.util.List;

/** The integration catalog as the platform admin keeps it: entries and the categories they are listed under. */
public interface IntegrationCatalogUseCase {

    List<IntegrationDefinitionRow> findIntegrations();

    IntegrationDefinitionRow createIntegration(long companyId, CreateIntegrationRequest request);

    IntegrationDefinitionRow updateIntegration(long companyId, String key, UpdateIntegrationRequest request);

    /** Removes an entry the admin added, with every company's access switch for it; a built-in one is refused. */
    void deleteIntegration(long companyId, String key);

    List<IntegrationCategory> findCategories();

    IntegrationCategory createCategory(long companyId, CreateIntegrationCategoryRequest request);

    IntegrationCategory updateCategory(long companyId, String key, UpdateIntegrationCategoryRequest request);

    /** Removes a category no entry is listed under. */
    void deleteCategory(long companyId, String key);
}
