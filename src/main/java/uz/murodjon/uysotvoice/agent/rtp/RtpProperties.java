package uz.murodjon.uysotvoice.agent.rtp;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RTP media settings. Bound from {@code voice-agent.rtp.*} (PROJECT.md §12).
 *
 * @param localIp          address Asterisk sends RTP to (must be reachable from Asterisk)
 * @param portRangeStart   first UDP port available for externalMedia listeners
 * @param portRangeEnd     last UDP port available
 * @param recordingDir     directory where per-call WAV recordings are written
 * @param testPlaybackFile optional 8 kHz mono WAV auto-played on answer (Stage 4 test); blank to disable
 */
@ConfigurationProperties(prefix = "voice-agent.rtp")
public record RtpProperties(
        String localIp,
        int portRangeStart,
        int portRangeEnd,
        String recordingDir,
        String testPlaybackFile
) {
}
