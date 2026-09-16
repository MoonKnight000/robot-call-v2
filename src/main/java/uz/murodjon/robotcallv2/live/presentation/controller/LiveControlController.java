package uz.murodjon.robotcallv2.live.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import uz.murodjon.robotcallv2.live.application.dto.LiveControlResponse;
import uz.murodjon.robotcallv2.live.application.dto.SwitchScenarioRequest;
import uz.murodjon.robotcallv2.live.application.dto.TakeoverRequest;
import uz.murodjon.robotcallv2.live.application.dto.WhisperRequest;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * Live call supervision: whisper guidance the customer never hears, handing the channel
 * over to a human operator, and switching the call to another scenario. All three answer
 * {@code 409 CALL_NOT_ACTIVE} for a channel that is not a live call of the caller's
 * company, and {@code 409 CALL_ON_OTHER_INSTANCE} for one another instance holds.
 */
@RequestMapping("/api/calls/live")
public interface LiveControlController {

    @PreAuthorize("hasAuthority('OPERATOR_EDIT')")
    @PostMapping("/{channelId}/whisper")
    ResponseEntity<ResponseData<LiveControlResponse>> whisper(@CurrentCompanyId long companyId,
                                                             @PathVariable String channelId,
                                                             @Valid @RequestBody WhisperRequest request);

    @PreAuthorize("hasAuthority('OPERATOR_EDIT')")
    @PostMapping("/{channelId}/takeover")
    ResponseEntity<ResponseData<LiveControlResponse>> takeover(
            @CurrentCompanyId long companyId,
            @PathVariable String channelId,
            @Valid @RequestBody(required = false) TakeoverRequest request);

    /** The rest of the call runs under {@code scenarioId}, from its first stage; transcript and facts stay. */
    @PreAuthorize("hasAuthority('OPERATOR_EDIT')")
    @PostMapping("/{channelId}/scenario")
    ResponseEntity<ResponseData<LiveControlResponse>> switchScenario(@CurrentCompanyId long companyId,
                                                                    @PathVariable String channelId,
                                                                    @Valid @RequestBody SwitchScenarioRequest request);
}
