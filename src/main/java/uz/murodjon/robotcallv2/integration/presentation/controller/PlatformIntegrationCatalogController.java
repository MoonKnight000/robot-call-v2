package uz.murodjon.robotcallv2.integration.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.integration.application.dto.CreateIntegrationCategoryRequest;
import uz.murodjon.robotcallv2.integration.application.dto.CreateIntegrationRequest;
import uz.murodjon.robotcallv2.integration.application.dto.IntegrationDefinitionRow;
import uz.murodjon.robotcallv2.integration.application.dto.UpdateIntegrationCategoryRequest;
import uz.murodjon.robotcallv2.integration.application.dto.UpdateIntegrationRequest;
import uz.murodjon.robotcallv2.integration.domain.entity.IntegrationCategory;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

/**
 * Platform admin: the integration catalog every company sees — its entries and the categories
 * (CRM, file manager, SMS...) they are listed under.
 */
@RequestMapping("/api/platform")
public interface PlatformIntegrationCatalogController {

    /** Every catalog entry with its category, connector and logo. */
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @GetMapping("/integrations")
    ResponseEntity<ResponseData<List<IntegrationDefinitionRow>>> findIntegrations();

    /** Adds a planned entry (no connector yet: companies see it as "coming soon"). */
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @PostMapping("/integrations")
    ResponseEntity<ResponseData<IntegrationDefinitionRow>> createIntegration(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody CreateIntegrationRequest request);

    /** Renames an entry, moves it to another category or changes its position. */
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @PutMapping("/integrations/{key}")
    ResponseEntity<ResponseData<IntegrationDefinitionRow>> updateIntegration(
            @CurrentCompanyId long companyId,
            @PathVariable String key,
            @Valid @RequestBody UpdateIntegrationRequest request);

    /** Removes an entry the admin added; a built-in one answers 400. */
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @DeleteMapping("/integrations/{key}")
    ResponseEntity<ResponseData<Void>> deleteIntegration(@CurrentCompanyId long companyId, @PathVariable String key);

    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @GetMapping("/integration-categories")
    ResponseEntity<ResponseData<List<IntegrationCategory>>> findCategories();

    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @PostMapping("/integration-categories")
    ResponseEntity<ResponseData<IntegrationCategory>> createCategory(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody CreateIntegrationCategoryRequest request);

    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @PutMapping("/integration-categories/{key}")
    ResponseEntity<ResponseData<IntegrationCategory>> updateCategory(
            @CurrentCompanyId long companyId,
            @PathVariable String key,
            @Valid @RequestBody UpdateIntegrationCategoryRequest request);

    /** Removes a category; 409 while an entry is still listed under it. */
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @DeleteMapping("/integration-categories/{key}")
    ResponseEntity<ResponseData<Void>> deleteCategory(@CurrentCompanyId long companyId, @PathVariable String key);
}
