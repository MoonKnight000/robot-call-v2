package uz.murodjon.robotcallv2.integration.presentation.controller;

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
    public ResponseEntity<ResponseData<CrmIntegrationRow>> get() {
        return ResponseEntity.ok(ResponseData.ok(crmIntegrationUseCase.find()));
    }

    @Override
    public ResponseEntity<ResponseData<CrmIntegrationRow>> connect(ConnectIntegrationRequest r) {
        return ResponseEntity.ok(ResponseData.ok(crmIntegrationUseCase.connect(r)));
    }

    @Override
    public ResponseEntity<ResponseData<AuthorizeUrlResponse>> authorizeUrl() {
        return ResponseEntity.ok(ResponseData.ok(crmIntegrationUseCase.buildAuthorizeUrl()));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> callback(String code, String state) {
        crmIntegrationUseCase.handleCallback(code, state);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> disconnect() {
        crmIntegrationUseCase.disconnect();
        return ResponseEntity.ok(ResponseData.ok(null));
    }
}
