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
 * @param recordingMode    channel mixing mode for recordings (STEREO, SPATIAL_STEREO, DUAL_MONO)
 * @param codec            G.711 variant of the app–Asterisk leg (ULAW default, or ALAW to
 *                         match an A-law trunk)
 */
@ConfigurationProperties(prefix = "voice-agent.rtp")
public record RtpProperties(
        String localIp,
        int portRangeStart,
        int portRangeEnd,
        String recordingDir,
        String testPlaybackFile,
        int eventLoopThreads,
        WavRecorder.RecordingMode recordingMode,
        RtpCodec codec
) {

    public RtpProperties {
        if (recordingMode == null) {
            recordingMode = WavRecorder.RecordingMode.SPATIAL_STEREO;
        }
        if (codec == null) {
            codec = RtpCodec.ULAW;
        }
    }

    /** Effective thread count: the configured value, or one per processor (min 2). */
    public int effectiveEventLoopThreads() {
        return eventLoopThreads > 0
                ? eventLoopThreads
                : Math.max(2, Runtime.getRuntime().availableProcessors());
    }

    /**
     * How many calls can hold media at once — one even port each ({@code RtpPortAllocator}).
     *
     * <p>This is the platform's real ceiling, not a preference: past it
     * {@code setupMedia} answers a call it has no port for and drops it. The dialer reads
     * it so that per-company limits, added up, cannot promise more than the machine has.
     */
    public int mediaCapacity() {
        return Math.max(0, (portRangeEnd - portRangeStart) / 2 + 1);
    }
}
