package uz.murodjon.robotcallv2.siptrunk.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.siptrunk.application.dto.*;

import java.util.List;

/**
 * REST API for Caller ID pools and phone number rotation.
 */
@RequestMapping("/api/caller-id-pools")
public interface CallerIdPoolController {

    @PreAuthorize("hasAuthority('SIP_TRUNK_EDIT')")
    @PostMapping
    ResponseEntity<ResponseData<CallerIdPoolDetail>> create(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody CreateCallerIdPoolRequest request);

    @PreAuthorize("hasAuthority('SIP_TRUNK_READ')")
    @GetMapping
    ResponseEntity<ResponseData<List<CallerIdPoolRow>>> list(
            @CurrentCompanyId long companyId);

    @PreAuthorize("hasAuthority('SIP_TRUNK_READ')")
    @GetMapping("/{id}")
    ResponseEntity<ResponseData<CallerIdPoolDetail>> get(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    @PreAuthorize("hasAuthority('SIP_TRUNK_EDIT')")
    @PutMapping("/{id}")
    ResponseEntity<ResponseData<CallerIdPoolDetail>> update(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @Valid @RequestBody UpdateCallerIdPoolRequest request);

    @PreAuthorize("hasAuthority('SIP_TRUNK_EDIT')")
    @DeleteMapping("/{id}")
    ResponseEntity<ResponseData<Void>> delete(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    @PreAuthorize("hasAuthority('SIP_TRUNK_EDIT')")
    @PostMapping("/{id}/numbers")
    ResponseEntity<ResponseData<CallerIdNumberRow>> addNumber(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @Valid @RequestBody AddCallerIdNumberRequest request);

    @PreAuthorize("hasAuthority('SIP_TRUNK_EDIT')")
    @DeleteMapping("/{id}/numbers/{numberId}")
    ResponseEntity<ResponseData<Void>> removeNumber(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @PathVariable long numberId);

    @PreAuthorize("hasAuthority('SIP_TRUNK_EDIT')")
    @PostMapping("/{id}/numbers/{numberId}/cooldown")
    ResponseEntity<ResponseData<Void>> setCooldown(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @PathVariable long numberId,
            @RequestParam(defaultValue = "24") int hours);

    @PreAuthorize("hasAuthority('SIP_TRUNK_EDIT')")
    @PostMapping("/{id}/numbers/{numberId}/activate")
    ResponseEntity<ResponseData<Void>> activate(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @PathVariable long numberId);

    @PreAuthorize("hasAuthority('SIP_TRUNK_EDIT')")
    @PostMapping("/{id}/numbers/{numberId}/disable")
    ResponseEntity<ResponseData<Void>> disable(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @PathVariable long numberId);
}
