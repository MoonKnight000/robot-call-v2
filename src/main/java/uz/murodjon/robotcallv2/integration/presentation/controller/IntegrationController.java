package uz.murodjon.robotcallv2.integration.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.integration.application.dto.AuthorizeUrlResponse;
import uz.murodjon.robotcallv2.integration.application.dto.ConnectCrmAccountRequest;
import uz.murodjon.robotcallv2.integration.application.dto.ConnectIntegrationRequest;
import uz.murodjon.robotcallv2.integration.application.dto.CrmCatalogEntry;
import uz.murodjon.robotcallv2.integration.application.dto.CrmIntegrationRow;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
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
    ResponseEntity<ResponseData<CrmIntegrationRow>> get(@CurrentCompanyId long companyId);

    @PreAuthorize("hasAuthority('INTEGRATION_EDIT')")
    @PutMapping("/uysot")
    ResponseEntity<ResponseData<CrmIntegrationRow>> connect(@CurrentCompanyId long companyId,
            @Valid @RequestBody ConnectIntegrationRequest request);

    /**
     * Connects amoCRM, Kommo or Bitrix24 by account address and token, replacing whatever
     * the company was connected to — these have no consent screen to go through. Calls are
     * routed to the new CRM from the next one on; {@code PUT /uysot} switches back.
     */
    @PreAuthorize("hasAuthority('INTEGRATION_EDIT')")
    @PutMapping("/account")
    ResponseEntity<ResponseData<CrmIntegrationRow>> connectAccount(@CurrentCompanyId long companyId,
            @Valid @RequestBody ConnectCrmAccountRequest request);

    @PreAuthorize("hasAuthority('INTEGRATION_READ')")
    @GetMapping("/uysot/authorize-url")
    ResponseEntity<ResponseData<AuthorizeUrlResponse>> authorizeUrl(@CurrentCompanyId long companyId);

    /**
     * Where the frontend navigates the browser with the {@code code} and {@code state} Uysot
     * returned to its integrations page after the company approved the connection.
     *
     * <p>Unauthenticated on purpose: it is a plain page navigation, so there is no bearer
     * token on it. The company it belongs to comes
     * from {@code state}, which was HMAC-signed when the authorize link was built.
     *
     * <p>Answers {@code 302} rather than a {@code ResponseData} envelope — the third
     * exception to §7, alongside file downloads and SSE — because the client is a browser
     * being sent onward to the settings page, with {@code ?crm=connected} or
     * {@code ?crm=error&reason=<ErrorCode>} for it to render. Nothing calls this from code.
     */
    @GetMapping("/uysot/callback")
    ResponseEntity<Void> callback(@RequestParam String code, @RequestParam String state);

    @PreAuthorize("hasAuthority('INTEGRATION_EDIT')")
    @DeleteMapping("/uysot")
    ResponseEntity<ResponseData<Void>> disconnect(@CurrentCompanyId long companyId);
}
