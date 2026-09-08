package uz.murodjon.robotcallv2.integration.presentation.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.robotcallv2.integration.application.dto.AuthorizeUrlResponse;
import uz.murodjon.robotcallv2.integration.application.dto.ConnectIntegrationRequest;
import uz.murodjon.robotcallv2.integration.application.dto.CrmCatalogEntry;
import uz.murodjon.robotcallv2.integration.application.dto.CrmIntegrationRow;
import uz.murodjon.robotcallv2.integration.application.port.input.CrmIntegrationUseCase;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

@RestController
public class IntegrationControllerImpl implements IntegrationController {

    private final CrmIntegrationUseCase crmIntegrationUseCase;

    public IntegrationControllerImpl(CrmIntegrationUseCase crmIntegrationUseCase) {
        this.crmIntegrationUseCase = crmIntegrationUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<List<CrmCatalogEntry>>> catalog() {
        return ResponseEntity.ok(ResponseData.ok(crmIntegrationUseCase.catalog()));
    }

    @Override
    public ResponseEntity<ResponseData<CrmIntegrationRow>> get(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(crmIntegrationUseCase.findByCompanyId(companyId)));
    }

    @Override
    public ResponseEntity<ResponseData<CrmIntegrationRow>> connect(long companyId, ConnectIntegrationRequest request) {
        return ResponseEntity.ok(ResponseData.ok(crmIntegrationUseCase.connect(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<AuthorizeUrlResponse>> authorizeUrl(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(crmIntegrationUseCase.buildAuthorizeUrl(companyId)));
    }

    @Override
    public ResponseEntity<Void> callback(String code, String state) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(crmIntegrationUseCase.handleCallback(code, state)).build();
    }

    @Override
    public ResponseEntity<ResponseData<Void>> disconnect(long companyId) {
        crmIntegrationUseCase.disconnect(companyId);
        return ResponseEntity.ok(ResponseData.ok(null));
    }
}
