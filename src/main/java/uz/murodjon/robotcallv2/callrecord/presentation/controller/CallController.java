package uz.murodjon.robotcallv2.callrecord.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import uz.murodjon.robotcallv2.callrecord.application.dto.*;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

/**
 * Manual triggers for verification and live call control (docs/api/calls.md, PROJECT.md §10.2).
 */
@RequestMapping("/api/calls")
public interface CallController {

    @PreAuthorize("hasAuthority('LIVE_READ')")
    @GetMapping("/live")
    ResponseEntity<ResponseData<List<LiveCallRow>>> live(@CurrentCompanyId long companyId);

    /**
     * A manual call on a saved scenario, or on an AI agent ({@code agentId}: its published
     * version with its own scenario, voice and prompt). At most one of the two.
     */
    @PreAuthorize("hasAuthority('CALL_EDIT')")
    @PostMapping
    ResponseEntity<ResponseData<CallOriginateResponse>> call(
            @CurrentCompanyId long companyId,
            @RequestParam String number,
            @RequestParam(required = false) Long scenarioId,
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) Long sipTrunkId);

    @PreAuthorize("hasAuthority('CALL_EDIT')")
    @PostMapping("/test")
    ResponseEntity<ResponseData<CallOriginateResponse>> testCall(
            @CurrentCompanyId long companyId,
            @RequestParam String number,
            @RequestParam(required = false) Long sipTrunkId,
            @Valid @RequestBody ScenarioDefinition definition);

    @PreAuthorize("hasAuthority('CALL_EDIT')")
    @PostMapping("/web-test")
    ResponseEntity<ResponseData<WebTestCallResponse>> webTest(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody WebTestCallRequest request);

    @PreAuthorize("hasAuthority('LIVE_EDIT')")
    @PostMapping("/{channelId}/play")
    ResponseEntity<ResponseData<PlayResponse>> play(
            @CurrentCompanyId long companyId,
            @PathVariable String channelId,
            @RequestParam String file);

    @PreAuthorize("hasAuthority('LIVE_EDIT')")
    @PostMapping("/{channelId}/say")
    ResponseEntity<ResponseData<SayResponse>> say(
            @CurrentCompanyId long companyId,
            @PathVariable String channelId,
            @RequestParam String text,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String voice);

    @PreAuthorize("hasAuthority('LIVE_EDIT')")
    @PostMapping("/{channelId}/hangup")
    ResponseEntity<ResponseData<HangupResponse>> hangup(
            @CurrentCompanyId long companyId,
            @PathVariable String channelId);

    @PreAuthorize("hasAuthority('LIVE_EDIT')")
    @PostMapping("/{channelId}/transfer")
    ResponseEntity<ResponseData<TransferResponse>> transfer(
            @CurrentCompanyId long companyId,
            @PathVariable String channelId,
            @Valid @RequestBody(required = false) TransferCallRequest request);

    @PreAuthorize("hasAuthority('LIVE_EDIT')")
    @PostMapping("/{channelId}/hold")
    ResponseEntity<ResponseData<TransferResponse>> hold(
            @CurrentCompanyId long companyId,
            @PathVariable String channelId);

    @PreAuthorize("hasAuthority('LIVE_EDIT')")
    @PostMapping("/{channelId}/unhold")
    ResponseEntity<ResponseData<TransferResponse>> unhold(
            @CurrentCompanyId long companyId,
            @PathVariable String channelId);

    @PreAuthorize("hasAuthority('LIVE_EDIT')")
    @PostMapping("/{channelId}/consult")
    ResponseEntity<ResponseData<TransferResponse>> consult(
            @CurrentCompanyId long companyId,
            @PathVariable String channelId,
            @Valid @RequestBody TransferCallRequest request);

    @PreAuthorize("hasAuthority('LIVE_EDIT')")
    @PostMapping("/{channelId}/consult/complete")
    ResponseEntity<ResponseData<TransferResponse>> completeConsult(
            @CurrentCompanyId long companyId,
            @PathVariable String channelId);

    @PreAuthorize("hasAuthority('LIVE_EDIT')")
    @PostMapping("/{channelId}/consult/cancel")
    ResponseEntity<ResponseData<TransferResponse>> cancelConsult(
            @CurrentCompanyId long companyId,
            @PathVariable String channelId);

    @PreAuthorize("hasAuthority('CALL_MONITOR')")
    @PostMapping("/{channelId}/monitor")
    ResponseEntity<ResponseData<MonitorCallResponse>> monitor(
            @CurrentCompanyId long companyId,
            @PathVariable String channelId,
            @Valid @RequestBody MonitorCallRequest request);

    @PreAuthorize("hasAuthority('OPERATOR_EDIT')")
    @PostMapping("/click-to-call")
    ResponseEntity<ResponseData<ClickToCallResponse>> clickToCall(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody ClickToCallRequest request);

    @PreAuthorize("hasAuthority('LIVE_READ')")
    @GetMapping(value = "/{channelId}/listen", produces = "audio/wav")
    ResponseEntity<StreamingResponseBody> listen(
            @CurrentCompanyId long companyId,
            @PathVariable String channelId);
}
