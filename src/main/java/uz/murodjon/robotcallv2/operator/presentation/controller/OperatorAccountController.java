package uz.murodjon.robotcallv2.operator.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import uz.murodjon.robotcallv2.operator.application.dto.*;
import uz.murodjon.robotcallv2.operator.domain.entity.OperatorFilter;
import uz.murodjon.robotcallv2.operator.domain.entity.OperatorPresence;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * Operators as people on the phones (docs/api/operators.md).
 */
@RequestMapping("/api/operators")
public interface OperatorAccountController {

    @PreAuthorize("hasAuthority('USER_EDIT')")
    @PutMapping("/{userId}/sip-account")
    ResponseEntity<ResponseData<OperatorSipAccountRow>> saveSipAccount(
            @CurrentCompanyId long companyId,
            @PathVariable long userId,
            @Valid @RequestBody SaveOperatorSipAccountRequest request);

    @PreAuthorize("hasAuthority('USER_EDIT')")
    @PostMapping("/{userId}/sip-account/reset-password")
    ResponseEntity<ResponseData<SipCredentialsResponse>> resetSipPassword(
            @CurrentCompanyId long companyId,
            @PathVariable long userId);

    @PreAuthorize("hasAuthority('USER_EDIT')")
    @DeleteMapping("/{userId}/sip-account")
    ResponseEntity<ResponseData<Void>> deleteSipAccount(
            @CurrentCompanyId long companyId,
            @PathVariable long userId);

    @PreAuthorize("hasAuthority('OPERATOR_READ')")
    @PostMapping("/list")
    ResponseEntity<ResponseData<PageableData<OperatorRow>>> list(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody OperatorFilter filter);

    @PreAuthorize("hasAuthority('OPERATOR_READ')")
    @GetMapping("/me/sip-credentials")
    ResponseEntity<ResponseData<SipCredentialsResponse>> myCredentials(@CurrentCompanyId long companyId);

    @PreAuthorize("hasAuthority('OPERATOR_READ')")
    @GetMapping("/me/status")
    ResponseEntity<ResponseData<OperatorPresence>> myStatus(@CurrentCompanyId long companyId);

    @PreAuthorize("hasAuthority('OPERATOR_EDIT')")
    @PutMapping("/me/status")
    ResponseEntity<ResponseData<OperatorPresence>> updateMyStatus(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody UpdateOperatorStatusRequest request);

    @PreAuthorize("hasAuthority('OPERATOR_READ')")
    @GetMapping(value = "/me/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter myEvents(@CurrentCompanyId long companyId);
}
