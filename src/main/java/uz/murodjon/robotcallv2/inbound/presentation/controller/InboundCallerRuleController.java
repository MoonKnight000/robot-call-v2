package uz.murodjon.robotcallv2.inbound.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.inbound.application.dto.SaveCallerRuleRequest;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundCallerRule;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundCallerRuleFilter;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/** Blacklist and VIP list for inbound callers (docs/api/inbound-routes.md, "Caller rules"). */
@RequestMapping("/api/inbound-caller-rules")
public interface InboundCallerRuleController {

    @PreAuthorize("hasAuthority('INBOUND_ROUTE_EDIT')")
    @PostMapping
    ResponseEntity<ResponseData<InboundCallerRule>> create(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody SaveCallerRuleRequest request);

    @PreAuthorize("hasAuthority('INBOUND_ROUTE_READ')")
    @PostMapping("/list")
    ResponseEntity<ResponseData<PageableData<InboundCallerRule>>> list(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody InboundCallerRuleFilter filter);

    @PreAuthorize("hasAuthority('INBOUND_ROUTE_EDIT')")
    @PutMapping("/{id}")
    ResponseEntity<ResponseData<InboundCallerRule>> update(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @Valid @RequestBody SaveCallerRuleRequest request);

    @PreAuthorize("hasAuthority('INBOUND_ROUTE_EDIT')")
    @DeleteMapping("/{id}")
    ResponseEntity<ResponseData<Void>> delete(
            @CurrentCompanyId long companyId,
            @PathVariable long id);
}
