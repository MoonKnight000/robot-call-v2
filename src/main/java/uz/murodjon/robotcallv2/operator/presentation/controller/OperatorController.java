package uz.murodjon.robotcallv2.operator.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.operator.application.dto.OperatorContextResponse;
import uz.murodjon.robotcallv2.operator.application.dto.OperatorJoinResponse;
import uz.murodjon.robotcallv2.operator.application.dto.OperatorTakeoverResponse;
import uz.murodjon.robotcallv2.operator.application.dto.OperatorWhisperRequest;
import uz.murodjon.robotcallv2.operator.application.dto.OperatorWhisperResponse;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * REST API for the operator screen (docs/api/operators.md, PROJECT.md §11.6, Stage 11).
 */
@RequestMapping("/api/operator")
public interface OperatorController {

    @PreAuthorize("hasAuthority('OPERATOR_READ')")
    @GetMapping("/calls/{channelId}")
    ResponseEntity<ResponseData<OperatorContextResponse>> context(
            @CurrentCompanyId long companyId,
            @PathVariable String channelId);

    @PreAuthorize("hasAuthority('OPERATOR_EDIT')")
    @PostMapping("/calls/{channelId}/takeover")
    ResponseEntity<ResponseData<OperatorTakeoverResponse>> takeover(
            @CurrentCompanyId long companyId,
            @PathVariable String channelId,
            @RequestParam(required = false, defaultValue = "100") String extension);

    @PreAuthorize("hasAuthority('OPERATOR_EDIT')")
    @PostMapping("/calls/{channelId}/whisper")
    ResponseEntity<ResponseData<OperatorWhisperResponse>> whisper(
            @CurrentCompanyId long companyId,
            @PathVariable String channelId,
            @Valid @RequestBody OperatorWhisperRequest request);

    /**
     * The signed-in operator accepts a call the LiveKit agent offered them ({@code CALL_OFFER});
     * joining the returned room with the token completes the transfer.
     */
    @PreAuthorize("hasAuthority('OPERATOR_EDIT')")
    @PostMapping("/calls/{channelId}/accept")
    ResponseEntity<ResponseData<OperatorJoinResponse>> accept(
            @CurrentCompanyId long companyId,
            @PathVariable String channelId);

    /** The signed-in operator turns a LiveKit transfer offered to them down. */
    @PreAuthorize("hasAuthority('OPERATOR_EDIT')")
    @PostMapping("/calls/{channelId}/decline")
    ResponseEntity<ResponseData<Void>> decline(
            @CurrentCompanyId long companyId,
            @PathVariable String channelId);
}
