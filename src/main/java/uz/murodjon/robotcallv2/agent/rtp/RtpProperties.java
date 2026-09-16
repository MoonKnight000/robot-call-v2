package uz.murodjon.robotcallv2.agent.rtp;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RTP media settings. Bound from {@code voice-agent.rtp.*} (PROJECT.md §12).
 *
 * @param localIp          address Asterisk sends RTP to. Must be reachable <em>from
 *                         Asterisk</em>: {@code 127.0.0.1} when both run on the host,
 *                         {@code host.docker.internal} (or the host's LAN IP) when
 *                         Asterisk is in Docker and this app is not
 * @param portRangeStart   first UDP port available for externalMedia listeners
 * @param portRangeEnd     last UDP port available
 * @param recordingDir     directory where per-call WAV recordings are written
 * @param testPlaybackFile optional 8 kHz mono WAV auto-played on answer (Stage 4 test); blank to disable
 * @param eventLoopThreads Netty threads shared by every call's RTP socket; {@code 0}
 *                         sizes it from the available processors. One thread carries
 *                         both the inbound packets and the 20ms playback pacer of
 *                         every concurrent call, so a single thread jitters at the
 *                         ~20 concurrent calls the MVP targets (PROJECT.md §1.3).
 * @param recordingMode    channel mixing for recordings: {@code SPATIAL_STEREO} (default,
 *                         both ears hear both speakers), {@code STEREO} (hard isolation)
 *                         or {@code DUAL_MONO}
 * @param codec            G.711 variant of the app–Asterisk leg: {@code ULAW} (default) or
 *                         {@code ALAW}. Both are 8 kHz so nothing downstream changes; pick
 *                         whichever the trunk negotiates ({@code pjsip.conf allow=}) and
 *                         Asterisk passes frames through instead of transcoding. Wideband
 *                         (G.722 / slin16) is deliberately not offered — the caller arrives
 *                         over a PSTN trunk at 8 kHz, so a 16 kHz leg here would only
 *                         upsample the same audio for STT and charge a transcode per call
 * @param receiveQueueCapacity inbound RTP packets one call may hold between the Netty thread
 *                         and its worker before new ones are dropped; 2000 is 40 s of
 *                         20 ms frames. 0 means that default
 * @param maxQueuedPlaybackSeconds the most bot audio one call may hold unsent; past it the
 *                         newest audio is dropped. A caller is never that far behind a live
 *                         reply, so reaching it means a producer ran away (an engine
 *                         streaming into a dead call). 0 means 60
 */
@ConfigurationProperties(prefix = "voice-agent.rtp")
public record RtpProperties(
        String localIp,
        int portRangeStart,
        int portRangeEnd,
        String recordingDir,
        String testPlaybackFile,
        int eventLoopThreads,
        RecordingMode recordingMode,
        RtpCodec codec,
        int receiveQueueCapacity,
        int maxQueuedPlaybackSeconds
) {

    public RtpProperties {
        if (recordingMode == null) {
            recordingMode = RecordingMode.SPATIAL_STEREO;
        }
        if (codec == null) {
            codec = RtpCodec.ULAW;
        }
        if (receiveQueueCapacity <= 0) {
            receiveQueueCapacity = 2000;
        }
        if (maxQueuedPlaybackSeconds <= 0) {
            maxQueuedPlaybackSeconds = 60;
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
