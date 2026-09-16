package uz.murodjon.robotcallv2.operator.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.operator.application.dto.OperatorContextResponse;
import uz.murodjon.robotcallv2.operator.application.dto.OperatorTakeoverResponse;
import uz.murodjon.robotcallv2.operator.application.dto.OperatorWhisperRequest;
import uz.murodjon.robotcallv2.operator.application.dto.OperatorWhisperResponse;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * REST API for the operator screen (PROJECT.md §11.6, Stage 11). Every endpoint answers
 * {@code 409 CALL_NOT_ACTIVE} for a channel that is not a live call of the caller's company.
 */
@RequestMapping("/api/operator")
public interface OperatorController {

    @PreAuthorize("hasAuthority('OPERATOR_READ')")
    @GetMapping("/calls/{channelId}")
    ResponseEntity<ResponseData<OperatorContextResponse>> context(@CurrentCompanyId long companyId,
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
}
