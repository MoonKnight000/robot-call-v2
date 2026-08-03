package uz.murodjon.uysotvoice.agent.session;

import uz.murodjon.uysotvoice.agent.audio.LiveAudioMonitor;
import uz.murodjon.uysotvoice.agent.rtp.RtpEndpoint;

import java.time.Instant;

/**
 * In-memory state for one active call (PROJECT.md §5.1). Tracks the media resources
 * plus the persisted call_attempt id and timing needed to finalize the call (Stage 9).
 *
 * @param channelId         the caller's Asterisk channel id
 * @param extMediaChannelId the externalMedia channel id bridged to the caller
 * @param bridgeId          the mixing bridge id
 * @param rtpPort           the UDP port allocated for this call's RTP
 * @param endpoint          the RTP listener + recorder for this call
 * @param callAttemptId     persisted call_attempt id (0 if not recorded)
 * @param startedAt         when media became ready (for duration)
 * @param wavPath           local path of the recording WAV
 * @param channelName       the caller's Asterisk channel name (e.g. {@code PJSIP/trunk-00000012}),
 *                          for the "Texnik" tab (§10.5) — distinct from {@link #channelId}, which
 *                          is the opaque ARI id
 * @param trunk             the PJSIP endpoint dialled, parsed from {@code channelName}; null if it
 *                          does not match the expected {@code PJSIP/<endpoint>-<seq>} shape
 * @param scenarioId        the scenario row this call ran (ROADMAP A.3) — resolved once
 *                          at setup and carried here so teardown can build the post-call
 *                          summary against the same scenario, regardless of whether the
 *                          call was a campaign call or a manual/test one
 * @param audioMonitor      mixes this call's caller+bot audio for operator "listen in"
 *                          (§10.3); closed alongside {@code endpoint} at teardown, which
 *                          disconnects any operator still listening
 */
public record CallSession(
        String channelId,
        String extMediaChannelId,
        String bridgeId,
        int rtpPort,
        RtpEndpoint endpoint,
        long callAttemptId,
        Instant startedAt,
        String wavPath,
        String channelName,
        String trunk,
        long scenarioId,
        LiveAudioMonitor audioMonitor
) {
}
