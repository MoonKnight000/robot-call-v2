package uz.murodjon.robotcallv2.crm.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.crm.domain.entity.*;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

/**
 * The company's Uysot data, read live (docs/api/crm.md): what the settings screen maps
 * outcomes and employees against, and what the agent's real-estate tools search.
 * {@code 409 CRM_NOT_CONNECTED} without a Uysot connection, {@code 502} when Uysot fails.
 */
@RequestMapping("/api/crm/uysot")
public interface UysotCatalogController {

    @PreAuthorize("hasAuthority('INTEGRATION_READ')")
    @GetMapping("/pipes")
    ResponseEntity<ResponseData<List<CrmPipe>>> pipes(@CurrentCompanyId long companyId);

    @PreAuthorize("hasAuthority('INTEGRATION_READ')")
    @GetMapping("/employees")
    ResponseEntity<ResponseData<List<CrmEmployee>>> employees(@CurrentCompanyId long companyId);

    @PreAuthorize("hasAuthority('INTEGRATION_READ')")
    @GetMapping("/houses")
    ResponseEntity<ResponseData<List<CrmHouse>>> houses(@CurrentCompanyId long companyId);

    /** Flats on sale, cheapest first — exactly what the agent's {@code searchFlats} tool sees. */
    @PreAuthorize("hasAuthority('INTEGRATION_READ')")
    @PostMapping("/flats/search")
    ResponseEntity<ResponseData<List<CrmFlat>>> searchFlats(
            @CurrentCompanyId long companyId,
            @RequestBody FlatSearchFilter filter);
}
