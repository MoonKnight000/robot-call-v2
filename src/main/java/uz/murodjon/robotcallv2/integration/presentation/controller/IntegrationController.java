package uz.murodjon.robotcallv2.integration.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.integration.application.dto.AuthorizeUrlResponse;
import uz.murodjon.robotcallv2.integration.application.dto.ConnectIntegrationRequest;
import uz.murodjon.robotcallv2.integration.application.dto.CrmCatalogEntry;
import uz.murodjon.robotcallv2.integration.application.dto.CrmIntegrationRow;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

/**
 * Uysot CRM OAuth connection (§11 settings).
 */
@RequestMapping("/api/settings/integrations")
public interface IntegrationController {

    @GetMapping("/catalog")
    ResponseEntity<ResponseData<List<CrmCatalogEntry>>> catalog();

    @GetMapping
    ResponseEntity<ResponseData<CrmIntegrationRow>> get();

    @PutMapping("/uysot")
    ResponseEntity<ResponseData<CrmIntegrationRow>> connect(@Valid @RequestBody ConnectIntegrationRequest r);

    @GetMapping("/uysot/authorize-url")
    ResponseEntity<ResponseData<AuthorizeUrlResponse>> authorizeUrl();

    @GetMapping("/uysot/callback")
    ResponseEntity<ResponseData<Void>> callback(@RequestParam String code, @RequestParam String state);

    @DeleteMapping("/uysot")
    ResponseEntity<ResponseData<Void>> disconnect();
}
