package uz.murodjon.robotcallv2.siptrunk.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.siptrunk.application.dto.*;
import uz.murodjon.robotcallv2.siptrunk.domain.entity.SipTrunkFilter;

import java.util.List;

/**
 * Per-company SIP trunk CRUD and live registration/connectivity status (ROADMAP B.3).
 */
@RequestMapping("/api")
public interface SipTrunkController {

    @PreAuthorize("hasAuthority('SIP_TRUNK_EDIT')")
    @PostMapping("/sip-trunks")
    ResponseEntity<ResponseData<SipTrunkRow>> create(@CurrentCompanyId long companyId,
            @Valid @RequestBody CreateSipTrunkRequest request);

    @PreAuthorize("hasAuthority('SIP_TRUNK_READ')")
    @PostMapping("/sip-trunks/list")
    ResponseEntity<ResponseData<PageableData<SipTrunkRow>>> list(@CurrentCompanyId long companyId,
            @Valid @RequestBody SipTrunkFilter filter);

    @PreAuthorize("hasAuthority('SIP_TRUNK_READ')")
    @GetMapping("/sip-trunks/{id}")
    ResponseEntity<ResponseData<SipTrunkRow>> get(@CurrentCompanyId long companyId, @PathVariable long id);

    @PreAuthorize("hasAuthority('SIP_TRUNK_READ')")
    @GetMapping("/sip-trunks/{id}/status")
    ResponseEntity<ResponseData<SipTrunkStatus>> getStatus(@CurrentCompanyId long companyId, @PathVariable long id);

    @PreAuthorize("hasAuthority('SIP_TRUNK_READ')")
    @GetMapping("/sip-trunks/status")
    ResponseEntity<ResponseData<List<SipTrunkStatus>>> getAllStatuses(@CurrentCompanyId long companyId);

    @PreAuthorize("hasAuthority('SIP_TRUNK_EDIT')")
    @PutMapping("/sip-trunks/{id}")
    ResponseEntity<ResponseData<SipTrunkRow>> update(@CurrentCompanyId long companyId, @PathVariable long id,
            @Valid @RequestBody UpdateSipTrunkRequest request);

    @PreAuthorize("hasAuthority('SIP_TRUNK_EDIT')")
    @PostMapping("/sip-trunks/{id}/default")
    ResponseEntity<ResponseData<SipTrunkRow>> makeDefault(@CurrentCompanyId long companyId, @PathVariable long id);

    @PreAuthorize("hasAuthority('SIP_TRUNK_EDIT')")
    @DeleteMapping("/sip-trunks/{id}")
    ResponseEntity<ResponseData<SipTrunkDeleteResponse>> delete(@CurrentCompanyId long companyId, @PathVariable long id);
}
