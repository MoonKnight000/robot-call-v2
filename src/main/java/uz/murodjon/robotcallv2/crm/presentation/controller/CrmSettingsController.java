package uz.murodjon.robotcallv2.crm.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.crm.application.dto.UpdateCrmSettingsRequest;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmSettings;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * Per-company CRM settings (docs/api/crm-settings.md): which pipeline stage each call outcome
 * moves a lead to, and the CRM employee the AI works under.
 */
@RequestMapping("/api/settings/crm")
public interface CrmSettingsController {

    @PreAuthorize("hasAuthority('INTEGRATION_READ')")
    @GetMapping
    ResponseEntity<ResponseData<CrmSettings>> get(@CurrentCompanyId long companyId);

    @PreAuthorize("hasAuthority('INTEGRATION_EDIT')")
    @PutMapping
    ResponseEntity<ResponseData<CrmSettings>> update(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody UpdateCrmSettingsRequest request);
}
