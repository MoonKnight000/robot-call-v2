package uz.murodjon.uysotvoice.callrecord.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import uz.murodjon.uysotvoice.agent.ari.AriService;
import uz.murodjon.uysotvoice.audit.service.AuditService;
import uz.murodjon.uysotvoice.callrecord.dto.CallOriginateResponse;
import uz.murodjon.uysotvoice.callrecord.dto.HangupResponse;
import uz.murodjon.uysotvoice.callrecord.dto.LiveCallRow;
import uz.murodjon.uysotvoice.callrecord.dto.PlayResponse;
import uz.murodjon.uysotvoice.callrecord.dto.SayResponse;
import uz.murodjon.uysotvoice.callrecord.dto.TransferResponse;
import uz.murodjon.uysotvoice.scenario.dto.ScenarioDefinition;
import uz.murodjon.uysotvoice.scenario.service.ScenarioService;
import uz.murodjon.uysotvoice.shared.exception.ErrorCode;
import uz.murodjon.uysotvoice.shared.exception.ValidationException;

import java.util.List;

/**
 * The manual-call API's own service ({@code /api/calls}) — the operator actions for
 * placing a call by hand, watching what is live, and making a channel play or say
 * something.
 *
 * <p>It exists so the controller has a service in its own package to talk to instead of
 * reaching into {@code agent/}. {@link AriService} is the voice pipeline's stateful core,
 * driven mostly by Stasis events; this is the small slice of it the REST API is allowed to
 * reach, and the seam to put per-request concerns behind if the manual API ever grows
 * authorization or rate limiting of its own.
 */
@Service
public class CallService {

    private final AriService ari;
    private final ScenarioService scenarios;
    private final AuditService audit;

    public CallService(AriService ari, ScenarioService scenarios, AuditService audit) {
        this.ari = ari;
        this.scenarios = scenarios;
        this.audit = audit;
    }

    /** Calls currently up, for the live view. */
    public List<LiveCallRow> liveCalls() {
        return ari.liveCalls();
    }

    /** Dial {@code number} by hand, outside any campaign, optionally test-driving {@code scenarioId}. */
    public CallOriginateResponse originate(String number, Long scenarioId) {
        return ari.originateManualCall(number, scenarioId);
    }

    /**
     * Test-drive an unsaved scenario draft (backend-uchun-talablar.md §4) — lets a
     * scenario editor try a call against the form's current state without saving first.
     * Validated the same way {@code POST /api/scenarios}/{@code PUT /api/scenarios/{id}}
     * validate a real one; an invalid draft is rejected here rather than surfacing as a
     * confusing failure once the call is already ringing.
     */
    public CallOriginateResponse originateTestCall(String number, ScenarioDefinition definition) {
        var result = scenarios.validate(definition);
        if (!result.valid()) {
            throw new ValidationException(ErrorCode.SCENARIO_DEFINITION_INVALID, String.join("; ", result.errors()));
        }
        return ari.originateTestCall(number, definition);
    }

    /** Play a recorded file into a live channel. */
    public PlayResponse play(String channelId, String file) {
        return ari.playRecording(channelId, file);
    }

    /** Synthesize {@code text} and speak it into a live channel. */
    public SayResponse say(String channelId, String text, String language, String voice) {
        return ari.say(channelId, text, language, voice);
    }

    /**
     * "Tugatish" (§10.3) — force-end a live channel from the monitoring UI, rather than
     * waiting for the caller or the dialog to end it. Audited: this drops a real, possibly
     * still-talking, caller.
     */
    public HangupResponse hangup(String channelId) {
        ari.hangupChannel(channelId);
        audit.record("CALL_HANGUP_MANUAL", "call", channelId, null);
        return new HangupResponse(channelId, "HUNG_UP");
    }

    /**
     * "Operatorga uzatish" (§10.3, §11.6) — bridge a live channel to a human operator by
     * hand, the same path the dialog itself uses when it decides to escalate. Audited: a
     * customer's call is being handed to a person outside the automated flow.
     */
    public TransferResponse transfer(String channelId) {
        ari.transferToOperator(channelId);
        audit.record("CALL_TRANSFER_MANUAL", "call", channelId, null);
        return new TransferResponse(channelId, "TRANSFERRING");
    }

    /**
     * "Tinglash" (§10.3) — join a live channel's mixed caller+bot audio. Audited once
     * per stream opened, not per byte: an operator listening in on a real customer
     * call is exactly the kind of access this project's audit trail exists for.
     */
    public StreamingResponseBody listen(String channelId) {
        StreamingResponseBody body = ari.listen(channelId);
        audit.record("CALL_LISTEN", "call", channelId, null);
        return body;
    }
}
