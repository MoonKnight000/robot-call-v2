package uz.murodjon.robotcallv2.integration.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.integration.application.dto.CreateIntegrationToolRequest;
import uz.murodjon.robotcallv2.integration.application.dto.IntegrationToolAdminRow;
import uz.murodjon.robotcallv2.integration.application.dto.PlatformCrmSettingsResponse;
import uz.murodjon.robotcallv2.integration.application.dto.UpdateIntegrationToolRequest;
import uz.murodjon.robotcallv2.integration.application.dto.UpdatePlatformCrmSettingsRequest;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.tool.application.dto.TestToolRequest;
import uz.murodjon.robotcallv2.tool.application.dto.ToolTestResponse;

import java.util.List;

/**
 * Platform admin: the tools an integration offers the voice agent, and their defaults.
 */
@RequestMapping("/api/platform/integration-tools")
public interface PlatformIntegrationToolController {

    /** The whole catalog of a provider (default {@code UYSOT}), platform-disabled tools included. */
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @GetMapping
    ResponseEntity<ResponseData<List<IntegrationToolAdminRow>>> findCatalog(
            @RequestParam(defaultValue = "UYSOT") CrmProvider provider);

    /**
     * Adds an HTTP tool: a request to the integration's API the voice agent may make. {@code definition.http.url}
     * is a path relative to the integration host (no scheme, no leading slash); the host and the token come
     * from each company's own connection, so {@code http.headers} and {@code http.auth} are refused.
     * When {@code defaultEnabled} is true, {@code applyTo} says whether companies that exist now get it
     * ({@code ALL}) or only companies created later ({@code NEW_ONLY}).
     */
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @PostMapping
    ResponseEntity<ResponseData<IntegrationToolAdminRow>> createTool(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody CreateIntegrationToolRequest request);

    /**
     * Edits a tool. Changing {@code defaultEnabled} needs {@code applyTo}: {@code ALL} — companies
     * that never chose follow the new default; {@code NEW_ONLY} — they keep today's value and only
     * companies created later get the new one. A company's own choice is never overwritten.
     */
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @PutMapping("/{id}")
    ResponseEntity<ResponseData<IntegrationToolAdminRow>> updateTool(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @Valid @RequestBody UpdateIntegrationToolRequest request);

    /** Removes an HTTP tool and every company's choice about it. A built-in tool answers 400. */
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @DeleteMapping("/{id}")
    ResponseEntity<ResponseData<Void>> deleteTool(@CurrentCompanyId long companyId, @PathVariable long id);

    /**
     * Runs an HTTP tool once against the calling admin's own company account (409 when it is not
     * connected to the integration), and returns what the model would see. A write really happens.
     */
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @PostMapping("/{id}/test")
    ResponseEntity<ResponseData<ToolTestResponse>> testTool(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @RequestBody(required = false) TestToolRequest request);

    /** Platform-wide CRM integration settings (app name, required OAuth grants). */
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @GetMapping("/crm-settings")
    ResponseEntity<ResponseData<PlatformCrmSettingsResponse>> getCrmSettings();

    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @PutMapping("/crm-settings")
    ResponseEntity<ResponseData<PlatformCrmSettingsResponse>> updateCrmSettings(
            @Valid @RequestBody UpdatePlatformCrmSettingsRequest request);
}
