package uz.murodjon.robotcallv2.integration.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.integration.application.dto.CreateIntegrationToolRequest;
import uz.murodjon.robotcallv2.integration.application.dto.IntegrationToolAdminRow;
import uz.murodjon.robotcallv2.integration.application.dto.PlatformCrmSettingsResponse;
import uz.murodjon.robotcallv2.integration.application.dto.UpdateIntegrationToolRequest;
import uz.murodjon.robotcallv2.integration.application.dto.UpdatePlatformCrmSettingsRequest;
import uz.murodjon.robotcallv2.integration.application.port.input.CrmIntegrationUseCase;
import uz.murodjon.robotcallv2.integration.application.port.input.IntegrationToolUseCase;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.tool.application.dto.TestToolRequest;
import uz.murodjon.robotcallv2.tool.application.dto.ToolTestResponse;

import java.util.List;

@RestController
public class PlatformIntegrationToolControllerImpl implements PlatformIntegrationToolController {

    private final IntegrationToolUseCase integrationToolUseCase;
    private final CrmIntegrationUseCase crmIntegrationUseCase;

    public PlatformIntegrationToolControllerImpl(IntegrationToolUseCase integrationToolUseCase,
                                                 CrmIntegrationUseCase crmIntegrationUseCase) {
        this.integrationToolUseCase = integrationToolUseCase;
        this.crmIntegrationUseCase = crmIntegrationUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<List<IntegrationToolAdminRow>>> findCatalog(CrmProvider provider) {
        return ResponseEntity.ok(ResponseData.ok(integrationToolUseCase.findCatalog(provider)));
    }

    @Override
    public ResponseEntity<ResponseData<IntegrationToolAdminRow>> createTool(long companyId,
                                                                           CreateIntegrationToolRequest request) {
        return ResponseEntity.ok(ResponseData.ok(integrationToolUseCase.createCatalogTool(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<IntegrationToolAdminRow>> updateTool(long companyId, long id,
                                                                           UpdateIntegrationToolRequest request) {
        return ResponseEntity.ok(ResponseData.ok(integrationToolUseCase.updateCatalogTool(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> deleteTool(long companyId, long id) {
        integrationToolUseCase.deleteCatalogTool(companyId, id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<ToolTestResponse>> testTool(long companyId, long id, TestToolRequest request) {
        return ResponseEntity.ok(ResponseData.ok(integrationToolUseCase.testCatalogTool(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<PlatformCrmSettingsResponse>> getCrmSettings() {
        return ResponseEntity.ok(ResponseData.ok(crmIntegrationUseCase.findPlatformSettings()));
    }

    @Override
    public ResponseEntity<ResponseData<PlatformCrmSettingsResponse>> updateCrmSettings(UpdatePlatformCrmSettingsRequest request) {
        return ResponseEntity.ok(ResponseData.ok(crmIntegrationUseCase.updatePlatformSettings(request)));
    }
}
