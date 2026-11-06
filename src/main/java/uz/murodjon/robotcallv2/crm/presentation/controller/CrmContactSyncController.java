package uz.murodjon.robotcallv2.crm.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.crm.application.dto.CrmContactSyncResponse;
import uz.murodjon.robotcallv2.crm.application.dto.UpdateCrmContactSyncRequest;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmFieldOption;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

/**
 * The CRM → contacts import (docs/api/crm.md#contact-sync): settings, the CRM's fields for
 * mapping, and an on-demand run.
 */
@RequestMapping("/api/settings/crm/contact-sync")
public interface CrmContactSyncController {

    @PreAuthorize("hasAuthority('INTEGRATION_READ')")
    @GetMapping
    ResponseEntity<ResponseData<CrmContactSyncResponse>> get(@CurrentCompanyId long companyId);

    @PreAuthorize("hasAuthority('INTEGRATION_EDIT')")
    @PutMapping
    ResponseEntity<ResponseData<CrmContactSyncResponse>> update(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody UpdateCrmContactSyncRequest request);

    @PreAuthorize("hasAuthority('INTEGRATION_READ')")
    @GetMapping("/crm-fields")
    ResponseEntity<ResponseData<List<CrmFieldOption>>> crmFields(@CurrentCompanyId long companyId);

    /** {@code full=true} reads every CRM contact again rather than only the ones changed since the last run. */
    @PreAuthorize("hasAuthority('INTEGRATION_EDIT')")
    @PostMapping("/run")
    ResponseEntity<ResponseData<CrmContactSyncResponse>> run(
            @CurrentCompanyId long companyId,
            @RequestParam(defaultValue = "false") boolean full);
}
