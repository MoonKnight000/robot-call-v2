package uz.murodjon.robotcallv2.agent.rtp;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RTP media settings. Bound from {@code voice-agent.rtp.*} (PROJECT.md §12).
 *
 * @param localIp          address Asterisk sends RTP to (must be reachable from Asterisk)
 * @param portRangeStart   first UDP port available for externalMedia listeners
 * @param portRangeEnd     last UDP port available
 * @param recordingDir     directory where per-call WAV recordings are written
 * @param testPlaybackFile optional 8 kHz mono WAV auto-played on answer (Stage 4 test); blank to disable
 * @param eventLoopThreads Netty threads shared by every call's RTP socket; {@code 0}
 *                         sizes it from the available processors. One thread carries
 *                         both the inbound packets and the 20ms playback pacer of
 *                         every concurrent call, so a single thread jitters at the
 *                         ~20 concurrent calls the MVP targets (PROJECT.md §1.3).
 */
@ConfigurationProperties(prefix = "voice-agent.rtp")
public record RtpProperties(
        String localIp,
        int portRangeStart,
        int portRangeEnd,
        String recordingDir,
        String testPlaybackFile,
        int eventLoopThreads
) {

    /** Effective thread count: the configured value, or one per processor (min 2). */
    public int effectiveEventLoopThreads() {
        return eventLoopThreads > 0
                ? eventLoopThreads
                : Math.max(2, Runtime.getRuntime().availableProcessors());
    }
}
