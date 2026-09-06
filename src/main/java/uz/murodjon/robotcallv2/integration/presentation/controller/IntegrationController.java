package uz.murodjon.robotcallv2.integration.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @PreAuthorize("hasAuthority('INTEGRATION_READ')")
    @GetMapping("/catalog")
    ResponseEntity<ResponseData<List<CrmCatalogEntry>>> catalog();

    @PreAuthorize("hasAuthority('INTEGRATION_READ')")
    @GetMapping
    ResponseEntity<ResponseData<CrmIntegrationRow>> get();

    @PreAuthorize("hasAuthority('INTEGRATION_EDIT')")
    @PutMapping("/uysot")
    ResponseEntity<ResponseData<CrmIntegrationRow>> connect(@Valid @RequestBody ConnectIntegrationRequest r);

    @PreAuthorize("hasAuthority('INTEGRATION_READ')")
    @GetMapping("/uysot/authorize-url")
    ResponseEntity<ResponseData<AuthorizeUrlResponse>> authorizeUrl();

    @GetMapping("/uysot/callback")
    ResponseEntity<ResponseData<Void>> callback(@RequestParam String code, @RequestParam String state);

    @PreAuthorize("hasAuthority('INTEGRATION_EDIT')")
    @DeleteMapping("/uysot")
    ResponseEntity<ResponseData<Void>> disconnect();
}
