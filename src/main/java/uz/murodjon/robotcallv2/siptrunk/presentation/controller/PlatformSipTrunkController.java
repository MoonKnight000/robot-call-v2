package uz.murodjon.robotcallv2.siptrunk.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.siptrunk.application.dto.CreateSipTrunkRequest;
import uz.murodjon.robotcallv2.siptrunk.application.dto.SipTrunkDeleteResponse;
import uz.murodjon.robotcallv2.siptrunk.application.dto.SipTrunkRow;
import uz.murodjon.robotcallv2.siptrunk.application.dto.SipTrunkStatus;
import uz.murodjon.robotcallv2.siptrunk.application.dto.UpdateSipTrunkRequest;
import uz.murodjon.robotcallv2.siptrunk.domain.entity.SipTrunkFilter;

import java.util.List;

/**
 * Platform-only SIP trunk setup for any company (docs/api/sip-trunks.md). Same operations and
 * bodies as {@link SipTrunkController}, with the company taken from the URL.
 */
@RequestMapping("/api/companies/{companyId}/sip-trunks")
public interface PlatformSipTrunkController {

    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @PostMapping
    ResponseEntity<ResponseData<SipTrunkRow>> create(
            @PathVariable long companyId,
            @Valid @RequestBody CreateSipTrunkRequest request);

    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @PostMapping("/list")
    ResponseEntity<ResponseData<PageableData<SipTrunkRow>>> list(
            @PathVariable long companyId,
            @Valid @RequestBody SipTrunkFilter filter);

    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @GetMapping("/status")
    ResponseEntity<ResponseData<List<SipTrunkStatus>>> getAllStatuses(@PathVariable long companyId);

    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @GetMapping("/{id}")
    ResponseEntity<ResponseData<SipTrunkRow>> get(@PathVariable long companyId, @PathVariable long id);

    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @PutMapping("/{id}")
    ResponseEntity<ResponseData<SipTrunkRow>> update(
            @PathVariable long companyId,
            @PathVariable long id,
            @Valid @RequestBody UpdateSipTrunkRequest request);

    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @PostMapping("/{id}/default")
    ResponseEntity<ResponseData<SipTrunkRow>> makeDefault(@PathVariable long companyId, @PathVariable long id);

    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @DeleteMapping("/{id}")
    ResponseEntity<ResponseData<SipTrunkDeleteResponse>> delete(@PathVariable long companyId, @PathVariable long id);
}
