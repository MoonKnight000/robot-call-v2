package uz.murodjon.robotcallv2.callrecord.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import uz.murodjon.robotcallv2.callrecord.application.dto.*;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
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
    @GetMapping("/live")
    ResponseEntity<ResponseData<List<LiveCallRow>>> live();

    /**
     * @param scenarioId test-drive this scenario without a campaign (ROADMAP A.3/A.4
     *                   "sinov rejimi"); omit to use the configured default test scenario
     * @param sipTrunkId optional specific SIP trunk to originate from; omit to use default
     */
    @PostMapping
    ResponseEntity<ResponseData<CallOriginateResponse>> call(
            @RequestParam String number,
            @RequestParam(required = false) Long scenarioId,
            @RequestParam(required = false) Long sipTrunkId);

    /**
     * Test-drive an unsaved scenario draft (backend-uchun-talablar.md §4).
     * @param sipTrunkId optional specific SIP trunk to originate from; omit to use default
     */
    @PostMapping("/test")
    ResponseEntity<ResponseData<CallOriginateResponse>> testCall(
            @RequestParam String number,
            @RequestParam(required = false) Long sipTrunkId,
            @RequestBody ScenarioDefinition definition);

    @PostMapping("/{channelId}/play")
    ResponseEntity<ResponseData<PlayResponse>> play(@PathVariable String channelId, @RequestParam String file);

    /**
     * Audition TTS before creating a campaign with it (§2.5).
     */
    @PostMapping("/{channelId}/say")
    ResponseEntity<ResponseData<SayResponse>> say(
            @PathVariable String channelId,
            @RequestParam String text,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String voice
    );

    /** "Tugatish" (§10.3) — force-end a live channel. */
    @PostMapping("/{channelId}/hangup")
    ResponseEntity<ResponseData<HangupResponse>> hangup(@PathVariable String channelId);

    /** "Operatorga uzatish" (§10.3, §11.6) — bridge a live channel to a human operator. */
    @PostMapping("/{channelId}/transfer")
    ResponseEntity<ResponseData<TransferResponse>> transfer(@PathVariable String channelId);

    /**
     * "Tinglash" (§10.3) — join a live channel as a listener.
     */
    @GetMapping(value = "/{channelId}/listen", produces = "audio/wav")
    ResponseEntity<StreamingResponseBody> listen(@PathVariable String channelId);
}
