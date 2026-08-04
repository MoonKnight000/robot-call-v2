package uz.murodjon.uysotvoice.integration.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.uysotvoice.integration.dto.AuthorizeUrlResponse;
import uz.murodjon.uysotvoice.integration.dto.ConnectIntegrationRequest;
import uz.murodjon.uysotvoice.integration.dto.CrmCatalogEntry;
import uz.murodjon.uysotvoice.integration.dto.CrmIntegration;
import uz.murodjon.uysotvoice.integration.service.CrmIntegrationService;
import uz.murodjon.uysotvoice.shared.api.ResponseData;

import java.util.List;

@RestController
public class IntegrationControllerImpl implements IntegrationController {

    private final CrmIntegrationService service;

    public IntegrationControllerImpl(CrmIntegrationService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ResponseData<List<CrmCatalogEntry>>> catalog() {
        return ResponseEntity.ok(ResponseData.ok(service.catalog()));
    }

    @Override
    public ResponseEntity<ResponseData<CrmIntegration>> get() {
        return ResponseEntity.ok(ResponseData.ok(service.find()));
    }

    @Override
    public ResponseEntity<ResponseData<CrmIntegration>> connect(ConnectIntegrationRequest r) {
        return ResponseEntity.ok(ResponseData.ok(service.connect(r)));
    }

    @Override
    public ResponseEntity<ResponseData<AuthorizeUrlResponse>> authorizeUrl() {
        return ResponseEntity.ok(ResponseData.ok(service.buildAuthorizeUrl()));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> callback(String code, String state) {
        service.handleCallback(code, state);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> disconnect() {
        service.disconnect();
        return ResponseEntity.ok(ResponseData.ok(null));
    }
}
