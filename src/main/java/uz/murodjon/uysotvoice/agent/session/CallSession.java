package uz.murodjon.uysotvoice.agent.session;

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
 */
public record CallSession(
        String channelId,
        String extMediaChannelId,
        String bridgeId,
        int rtpPort,
        RtpEndpoint endpoint,
        long callAttemptId,
        Instant startedAt,
        String wavPath
) {
}
