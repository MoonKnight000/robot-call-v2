package uz.murodjon.robotcallv2.integration.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.integration.application.dto.CreateIntegrationCategoryRequest;
import uz.murodjon.robotcallv2.integration.application.dto.CreateIntegrationRequest;
import uz.murodjon.robotcallv2.integration.application.dto.IntegrationDefinitionRow;
import uz.murodjon.robotcallv2.integration.application.dto.UpdateIntegrationCategoryRequest;
import uz.murodjon.robotcallv2.integration.application.dto.UpdateIntegrationRequest;
import uz.murodjon.robotcallv2.integration.application.port.input.IntegrationCatalogUseCase;
import uz.murodjon.robotcallv2.integration.domain.entity.IntegrationCategory;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

@RestController
public class PlatformIntegrationCatalogControllerImpl implements PlatformIntegrationCatalogController {

    private final IntegrationCatalogUseCase integrationCatalogUseCase;

    public PlatformIntegrationCatalogControllerImpl(IntegrationCatalogUseCase integrationCatalogUseCase) {
        this.integrationCatalogUseCase = integrationCatalogUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<List<IntegrationDefinitionRow>>> findIntegrations() {
        return ResponseEntity.ok(ResponseData.ok(integrationCatalogUseCase.findIntegrations()));
    }

    @Override
    public ResponseEntity<ResponseData<IntegrationDefinitionRow>> createIntegration(long companyId,
                                                                                   CreateIntegrationRequest request) {
        return ResponseEntity.ok(ResponseData.ok(integrationCatalogUseCase.createIntegration(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<IntegrationDefinitionRow>> updateIntegration(long companyId, String key,
                                                                                   UpdateIntegrationRequest request) {
        return ResponseEntity.ok(ResponseData.ok(integrationCatalogUseCase.updateIntegration(companyId, key, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> deleteIntegration(long companyId, String key) {
        integrationCatalogUseCase.deleteIntegration(companyId, key);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<List<IntegrationCategory>>> findCategories() {
        return ResponseEntity.ok(ResponseData.ok(integrationCatalogUseCase.findCategories()));
    }

    @Override
    public ResponseEntity<ResponseData<IntegrationCategory>> createCategory(long companyId,
                                                                           CreateIntegrationCategoryRequest request) {
        return ResponseEntity.ok(ResponseData.ok(integrationCatalogUseCase.createCategory(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<IntegrationCategory>> updateCategory(long companyId, String key,
                                                                           UpdateIntegrationCategoryRequest request) {
        return ResponseEntity.ok(ResponseData.ok(integrationCatalogUseCase.updateCategory(companyId, key, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> deleteCategory(long companyId, String key) {
        integrationCatalogUseCase.deleteCategory(companyId, key);
        return ResponseEntity.ok(ResponseData.ok(null));
    }
}
