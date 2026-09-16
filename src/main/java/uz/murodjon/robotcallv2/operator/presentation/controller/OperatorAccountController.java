package uz.murodjon.robotcallv2.operator.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import uz.murodjon.robotcallv2.operator.application.dto.OperatorRow;
import uz.murodjon.robotcallv2.operator.application.dto.OperatorSipAccountRow;
import uz.murodjon.robotcallv2.operator.application.dto.SaveOperatorSipAccountRequest;
import uz.murodjon.robotcallv2.operator.application.dto.SipCredentialsResponse;
import uz.murodjon.robotcallv2.operator.application.dto.UpdateOperatorStatusRequest;
import uz.murodjon.robotcallv2.operator.domain.entity.OperatorFilter;
import uz.murodjon.robotcallv2.operator.domain.entity.OperatorPresence;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * Operators as people on the phones (docs/api/operators.md): an admin gives a user a SIP
 * account; the operator signs their softphone in with it, sets their own status and
 * receives call offers on the event stream.
 */
@RequestMapping("/api/operators")
public interface OperatorAccountController {

    @PreAuthorize("hasAuthority('USER_EDIT')")
    @PutMapping("/{userId}/sip-account")
    ResponseEntity<ResponseData<OperatorSipAccountRow>> saveSipAccount(@CurrentCompanyId long companyId,
                                                                       @PathVariable long userId,
                                                                       @Valid @RequestBody SaveOperatorSipAccountRequest request);

    /** New password, returned once — for provisioning a desk phone. */
    @PreAuthorize("hasAuthority('USER_EDIT')")
    @PostMapping("/{userId}/sip-account/reset-password")
    ResponseEntity<ResponseData<SipCredentialsResponse>> resetSipPassword(@CurrentCompanyId long companyId,
                                                                          @PathVariable long userId);

    @PreAuthorize("hasAuthority('USER_EDIT')")
    @DeleteMapping("/{userId}/sip-account")
    ResponseEntity<ResponseData<Void>> deleteSipAccount(@CurrentCompanyId long companyId, @PathVariable long userId);

    @PreAuthorize("hasAuthority('OPERATOR_READ')")
    @PostMapping("/list")
    ResponseEntity<ResponseData<PageableData<OperatorRow>>> list(@CurrentCompanyId long companyId,
                                                                 @Valid @RequestBody OperatorFilter filter);

    @PreAuthorize("hasAuthority('OPERATOR_READ')")
    @GetMapping("/me/sip-credentials")
    ResponseEntity<ResponseData<SipCredentialsResponse>> myCredentials(@CurrentCompanyId long companyId);

    @PreAuthorize("hasAuthority('OPERATOR_READ')")
    @GetMapping("/me/status")
    ResponseEntity<ResponseData<OperatorPresence>> myStatus(@CurrentCompanyId long companyId);

    @PreAuthorize("hasAuthority('OPERATOR_EDIT')")
    @PutMapping("/me/status")
    ResponseEntity<ResponseData<OperatorPresence>> updateMyStatus(@CurrentCompanyId long companyId,
                                                                  @Valid @RequestBody UpdateOperatorStatusRequest request);

    /** CALL_OFFER / CALL_CONNECTED / CALL_ENDED / STATUS_CHANGED / MISSED_CALL / VOICEMAIL for this operator. */
    @PreAuthorize("hasAuthority('OPERATOR_READ')")
    @GetMapping(value = "/me/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter myEvents(@CurrentCompanyId long companyId);
}
