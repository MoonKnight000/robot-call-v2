package uz.murodjon.robotcallv2.agent.ari;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Asterisk ARI connection settings. Bound from {@code voice-agent.asterisk.*}
 * (PROJECT.md §12).
 *
 * @param enabled          when false, the ARI connection is not opened (useful for tests)
 * @param ariUrl           HTTP base URL of Asterisk (e.g. http://localhost:8088/) — ari4java derives the WebSocket
 * @param ariUser          ARI username (ari.conf)
 * @param ariPassword      ARI password (ari.conf)
 * @param appName          Stasis application name registered with Asterisk
 * @param trunkEndpoint    PJSIP endpoint used for outbound calls (pjsip.conf)
 * @param localEndpoint    PJSIP endpoint used for numbers matching {@code localNumberPattern} —
 *                         a registered test softphone. Lets the same originate path be
 *                         exercised for free (dial 600) without burning trunk minutes.
 *                         Blank disables local routing: everything goes to the trunk.
 * @param localNumberPattern regex; a number matching it is dialled via {@code localEndpoint}
 *                         instead of the trunk. Three or four digits is an internal
 *                         extension; a real subscriber number (998…) is not, and goes out
 *                         over the trunk as usual. The default is spelled {@code \d\d\d\d?}
 *                         rather than {@code \d{3,4}} on purpose — a closing brace inside a
 *                         {@code ${...:default}} placeholder in the YAML would terminate
 *                         the placeholder early.
 * @param callerId         caller id presented to the callee. <b>Keep this blank unless a
 *                         real DID in E.164 is available.</b> Blank leaves it unset, so
 *                         PJSIP marks the presentation restricted and sends
 *                         {@code From: "Anonymous"}; Skyline then stamps the account's own
 *                         valid number as the A-number and the call completes. Filling it
 *                         with the account login (781137492) is what broke outbound
 *                         dialling on 2026-09-01 — the login is not a subscriber number,
 *                         so it went out as the A-number and the terminating network
 *                         answered 603 Declined about seven seconds in.
 * @param answerTimeoutSec seconds to wait for the callee to answer
 * @param jitterBuffer     {@code adaptive} or {@code fixed} to put Asterisk's jitter buffer
 *                         on the caller's channel ({@code JITTERBUFFER()}), so the audio
 *                         reaching the recognizer or the realtime engine arrives evenly
 *                         spaced; blank leaves it off. Off by default: it adds 20–60 ms to
 *                         every turn, and whether a line needs it is now measurable —
 *                         {@code call_technical.rtp_jitter_ms}/{@code rtp_mos} per call.
 *                         Turn it on when jitter p95 sits above ~30 ms
 * @param ghostCallFallbackSeconds how long an AI call may run before the ghost reaper treats
 *                         it as lost, when {@code dialog.max-call-seconds} sets no limit of
 *                         its own. 0 means 300
 * @param ghostCallGraceSeconds how far past that limit a call without a StasisEnd has to be
 *                         before it is torn down — the watchdog closes calls at the limit
 *                         itself, and one still up a minute later is one Asterisk lost track
 *                         of. 0 means 60
 * @param playbackPollMs   how often a prompt played outside a dialog (a flow hand-off, a
 *                         one-leg prompt) checks whether it has finished. 0 means 100
 * @param playbackGraceMs  slack on top of a one-leg prompt's own length before it stops
 *                         waiting for playback to finish. 0 means 2000
 * @param operatorTrunk    the PJSIP endpoint this Asterisk reaches the operators' Asterisk
 *                         through, when the operators' phones sign in to another cell of
 *                         the cluster (docs/VOICE-PIPELINE-PLAN.md 4.3b). Blank: they sign
 *                         in here — the operator cell, or the only cell
 */
@ConfigurationProperties(prefix = "voice-agent.asterisk")
public record AsteriskProperties(
        boolean enabled,
        String ariUrl,
        String ariUser,
        String ariPassword,
        String appName,
        String trunkEndpoint,
        String localEndpoint,
        String localNumberPattern,
        String callerId,
        int answerTimeoutSec,
        String jitterBuffer,
        int ghostCallFallbackSeconds,
        int ghostCallGraceSeconds,
        int playbackPollMs,
        int playbackGraceMs,
        String operatorTrunk
) {

    /** Whether the operators' phones sign in to this Asterisk. */
    public boolean isOperatorCell() {
        return operatorTrunk == null || operatorTrunk.isBlank();
    }

    /** How this Asterisk dials the operator {@code endpointName}: directly, or over the operator trunk. */
    public String buildOperatorDialString(String endpointName) {
        return isOperatorCell() ? "PJSIP/" + endpointName : "PJSIP/" + endpointName + "@" + operatorTrunk.trim();
    }

    public AsteriskProperties {
        if (ghostCallFallbackSeconds <= 0) {
            ghostCallFallbackSeconds = 300;
        }
        if (ghostCallGraceSeconds <= 0) {
            ghostCallGraceSeconds = 60;
        }
        if (playbackPollMs <= 0) {
            playbackPollMs = 100;
        }
        if (playbackGraceMs <= 0) {
            playbackGraceMs = 2000;
        }
    }
}
