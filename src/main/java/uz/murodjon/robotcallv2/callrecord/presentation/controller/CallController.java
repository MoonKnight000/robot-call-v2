package uz.murodjon.robotcallv2.callrecord.presentation.controller;

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
 * Manual triggers for verification and live call control.
 */
@RequestMapping("/api/calls")
public interface CallController {

    /**
     * Every call still in conversation (§10.2/§10.3 UI-DESIGN.md "Jonli qo'ng'iroqlar").
     */
    @PreAuthorize("hasAuthority('LIVE_READ')")
    @GetMapping("/live")
    ResponseEntity<ResponseData<List<LiveCallRow>>> live();

    /**
     * @param scenarioId test-drive this scenario without a campaign (ROADMAP A.3/A.4
     *                   "sinov rejimi"); omit to use the configured default test scenario
     * @param sipTrunkId optional specific SIP trunk to originate from; omit to use default
     */
    @PreAuthorize("hasAuthority('CALL_EDIT')")
    @PostMapping
    ResponseEntity<ResponseData<CallOriginateResponse>> call(
            @CurrentCompanyId long companyId,
            @RequestParam String number,
            @RequestParam(required = false) Long scenarioId,
            @RequestParam(required = false) Long sipTrunkId);

    /**
     * Test-drive an unsaved scenario draft (backend-uchun-talablar.md §4).
     * @param sipTrunkId optional specific SIP trunk to originate from; omit to use default
     */
    @PreAuthorize("hasAuthority('CALL_EDIT')")
    @PostMapping("/test")
    ResponseEntity<ResponseData<CallOriginateResponse>> testCall(
            @CurrentCompanyId long companyId,
            @RequestParam String number,
            @RequestParam(required = false) Long sipTrunkId,
            @RequestBody ScenarioDefinition definition);

    /**
     * Test-drive a campaign (or a scenario / draft) from the browser instead of a phone:
     * returns the SIP-over-WebSocket credentials and a one-shot session id the web UI
     * dials in with (docs/api/calls.md "web-test"). No trunk minutes are used.
     */
    @PreAuthorize("hasAuthority('CALL_EDIT')")
    @PostMapping("/web-test")
    ResponseEntity<ResponseData<WebTestCallResponse>> webTest(@CurrentCompanyId long companyId, @RequestBody WebTestCallRequest request);

    @PreAuthorize("hasAuthority('LIVE_EDIT')")
    @PostMapping("/{channelId}/play")
    ResponseEntity<ResponseData<PlayResponse>> play(@PathVariable String channelId, @RequestParam String file);

    /**
     * Audition TTS before creating a campaign with it (§2.5).
     */
    @PreAuthorize("hasAuthority('LIVE_EDIT')")
    @PostMapping("/{channelId}/say")
    ResponseEntity<ResponseData<SayResponse>> say(
            @PathVariable String channelId,
            @RequestParam String text,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String voice
    );

    /** "Tugatish" (§10.3) — force-end a live channel. */
    @PreAuthorize("hasAuthority('LIVE_EDIT')")
    @PostMapping("/{channelId}/hangup")
    ResponseEntity<ResponseData<HangupResponse>> hangup(@CurrentCompanyId long companyId, @PathVariable String channelId);

    /** "Operatorga uzatish" (§10.3, §11.6) — bridge a live channel to a human operator. */
    @PreAuthorize("hasAuthority('LIVE_EDIT')")
    @PostMapping("/{channelId}/transfer")
    ResponseEntity<ResponseData<TransferResponse>> transfer(@CurrentCompanyId long companyId, @PathVariable String channelId);

    /**
     * "Tinglash" (§10.3) — join a live channel as a listener.
     */
    @PreAuthorize("hasAuthority('LIVE_READ')")
    @GetMapping(value = "/{channelId}/listen", produces = "audio/wav")
    ResponseEntity<StreamingResponseBody> listen(@CurrentCompanyId long companyId, @PathVariable String channelId);
}
