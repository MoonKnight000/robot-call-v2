package uz.murodjon.robotcallv2.agent.telephony;

import uz.murodjon.robotcallv2.agent.session.CallSession;
import uz.murodjon.robotcallv2.callrecord.domain.enums.MonitorMode;
import uz.murodjon.robotcallv2.shared.dialog.TelephonyEngine;

/**
 * What the voice pipeline asks of a telephony engine (Asterisk, LiveKit, later FreeSWITCH) to
 * control one call. The rest of the application sees this and never an engine's own API
 * (ARI channels, LiveKit rooms), so a new engine is one more implementation of it and
 * nothing that calls it changes.
 *
 * <p>{@code callId} is the call's channel id — an Asterisk channel, a LiveKit room name.
 * Implementations are best-effort where a failure cannot be acted on (a hang-up of a call
 * that is already gone).</p>
 */
public interface TelephonyGateway {

    /** The engine this gateway drives. */
    TelephonyEngine findEngine();

    /**
     * Whether {@code callId} names a call of this engine. Engines whose ids carry no marker
     * (Asterisk channel ids) keep the default and are the fallback of {@link TelephonyGatewayResolver}.
     */
    default boolean owns(String callId) {
        return false;
    }

    /**
     * Places an outbound call and returns its id. A call the engine never takes leaves a closed
     * attempt row saying why and throws, so the attempt the dialer already counted is not lost.
     */
    String originate(OutboundDial dial);

    /**
     * Lets a supervisor watch a live call of the company in {@code mode}: their phone rings, or they
     * are given what to join the call's room with from the browser. Throws when the engine cannot
     * offer that mode on this call.
     */
    SupervisorAccess monitor(long companyId, long supervisorUserId, String callId, MonitorMode mode);

    /**
     * Opens the call a browser is about to start with nobody to dial — a test call or a website widget —
     * and returns what the browser joins it with, or null when the browser reaches the engine by itself
     * (Asterisk: it dials in over SIP with the session id). {@code sessionId} names the waiting test.
     */
    WebRtcJoin openBrowserCall(String sessionId);

    /**
     * Hands the live call to a person: the agent's transfer queue, number or operator, whichever the call
     * is configured with and the engine can reach. Used by anything that moves a call without the AI
     * agent asking — the manual transfer button.
     */
    void transferToOperator(String callId);

    void answer(String callId);

    void hangup(String callId);

    /** Refuses the call with a busy signal instead of a plain hang-up. */
    void hangupBusy(String callId);

    /** Ringback to a call that is waiting for someone to pick up. */
    void ring(String callId);

    /** Sends keypad tones down the call — the bot pressing buttons, not the client. */
    void sendDtmf(String callId, String digits);

    /**
     * Takes down what carries the call's audio to the pipeline (Asterisk's externalMedia
     * channel and bridge, a LiveKit room with its media bridge). Runs ahead of the RTP port's
     * release, so the port is not reused while the old leg still sends to it.
     */
    void releaseMedia(CallSession session);
}
