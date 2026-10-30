package uz.murodjon.robotcallv2.integration.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.integration.application.dto.*;
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
    ResponseEntity<ResponseData<List<IntegrationCatalogEntry>>> catalog(@CurrentCompanyId long companyId);

    @PreAuthorize("hasAuthority('INTEGRATION_READ')")
    @GetMapping
    ResponseEntity<ResponseData<CrmIntegrationRow>> get(@CurrentCompanyId long companyId);

    @PreAuthorize("hasAuthority('INTEGRATION_EDIT')")
    @PutMapping("/uysot")
    ResponseEntity<ResponseData<CrmIntegrationRow>> connect(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody ConnectIntegrationRequest request);

    /**
     * Connects amoCRM, Kommo or Bitrix24 by account address and token, replacing whatever
     * the company was connected to — these have no consent screen to go through. Calls are
     * routed to the new CRM from the next one on; {@code PUT /uysot} switches back.
     */
    @PreAuthorize("hasAuthority('INTEGRATION_EDIT')")
    @PutMapping("/account")
    ResponseEntity<ResponseData<CrmIntegrationRow>> connectAccount(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody ConnectCrmAccountRequest request);

    @PreAuthorize("hasAuthority('INTEGRATION_READ')")
    @GetMapping("/uysot/authorize-url")
    ResponseEntity<ResponseData<AuthorizeUrlResponse>> authorizeUrl(@CurrentCompanyId long companyId);

    /**
     * Where the frontend navigates the browser with the {@code code} and {@code state} Uysot
     * returned to its integrations page after the company approved the connection.
     */
    @GetMapping("/uysot/callback")
    ResponseEntity<Void> callback(
            @RequestParam String code,
            @RequestParam String state);

    /**
     * The CRM tools the voice agent can be offered, with the company's choice. All are on
     * until switched off; {@code granted=false} marks a tool the company's CRM grants do not cover.
     */
    @PreAuthorize("hasAuthority('INTEGRATION_READ')")
    @GetMapping("/tools")
    ResponseEntity<ResponseData<List<CrmToolRow>>> tools(@CurrentCompanyId long companyId);

    /** Saves the tools the agent is offered; every tool not in {@code enabledTools} is switched off. */
    @PreAuthorize("hasAuthority('INTEGRATION_EDIT')")
    @PutMapping("/tools")
    ResponseEntity<ResponseData<List<CrmToolRow>>> updateTools(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody UpdateCrmToolsRequest request);

    /**
     * Asks the connected CRM account whether the saved address and credential still work. The status
     * becomes {@code CONNECTED} or {@code ERROR} accordingly; {@code 409 CRM_INTEGRATION_NOT_CONNECTED}
     * when there is no connection to check.
     */
    @PreAuthorize("hasAuthority('INTEGRATION_EDIT')")
    @PostMapping("/check")
    ResponseEntity<ResponseData<CrmConnectionCheckResponse>> checkConnection(@CurrentCompanyId long companyId);

    @PreAuthorize("hasAuthority('INTEGRATION_EDIT')")
    @DeleteMapping("/uysot")
    ResponseEntity<ResponseData<Void>> disconnect(@CurrentCompanyId long companyId);
}
