package uz.murodjon.robotcallv2.inbound.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.inbound.application.dto.CreateInboundRouteRequest;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundRouteFilter;
import uz.murodjon.robotcallv2.inbound.application.dto.InboundRouteRow;
import uz.murodjon.robotcallv2.inbound.application.dto.UpdateInboundRouteRequest;
import uz.murodjon.robotcallv2.report.domain.entity.InboundRouteStats;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * Inbound route CRUD API (ROADMAP C.1, §10.9).
 */
@RequestMapping("/api")
public interface InboundRouteController {

    @PreAuthorize("hasAuthority('INBOUND_ROUTE_EDIT')")
    @PostMapping("/inbound-routes")
    ResponseEntity<ResponseData<InboundRouteRow>> create(@CurrentCompanyId long companyId,
            @Valid @RequestBody CreateInboundRouteRequest request);

    @PreAuthorize("hasAuthority('INBOUND_ROUTE_READ')")
    @PostMapping("/inbound-routes/list")
    ResponseEntity<ResponseData<PageableData<InboundRouteRow>>> list(@CurrentCompanyId long companyId,
            @Valid @RequestBody InboundRouteFilter filter);

    @PreAuthorize("hasAuthority('INBOUND_ROUTE_READ')")
    @GetMapping("/inbound-routes/{id}")
    ResponseEntity<ResponseData<InboundRouteRow>> get(@CurrentCompanyId long companyId, @PathVariable long id);

    @PreAuthorize("hasAuthority('INBOUND_ROUTE_EDIT')")
    @PutMapping("/inbound-routes/{id}")
    ResponseEntity<ResponseData<InboundRouteRow>> update(@CurrentCompanyId long companyId, @PathVariable long id,
                                                         @Valid @RequestBody UpdateInboundRouteRequest r);

    @PreAuthorize("hasAuthority('INBOUND_ROUTE_EDIT')")
    @DeleteMapping("/inbound-routes/{id}")
    ResponseEntity<ResponseData<InboundRouteRow>> disable(@CurrentCompanyId long companyId, @PathVariable long id);

    @PreAuthorize("hasAuthority('INBOUND_ROUTE_READ')")
    @GetMapping("/inbound-routes/{id}/stats")
    ResponseEntity<ResponseData<InboundRouteStats>> stats(@CurrentCompanyId long companyId, @PathVariable long id);
}

