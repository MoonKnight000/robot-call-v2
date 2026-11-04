package uz.murodjon.robotcallv2.agent.vad;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * TEN VAD (TEN-framework/ten-vad) as the call's voice-activity model in place of Silero.
 * Bound from {@code voice-agent.ten-vad.*}.
 *
 * <p>The windows, energy and echo checks of {@link VadProperties} still decide what counts as
 * speech; the probability thresholds are TEN's own, since its scores are not Silero's. With
 * Silero's 0.4 TEN took breathing for a caller: false barge-ins cut three replies off on one
 * recorded call, and the gate stayed open on a silent line for 25 s. TEN VAD runs at 16 kHz;
 * the 8 kHz call audio is upsampled to it, so it scores narrowband speech it was not trained on.
 *
 * @param enabled            use TEN VAD for new calls; off, or with the library missing, calls keep Silero
 * @param libraryPath        path to the native library from the ten-vad repository
 *                           ({@code lib/Linux/x64/libten_vad.so}, {@code lib/Windows/x64/ten_vad.dll})
 * @param hopSize            samples per TEN VAD frame at 16 kHz — 256 (16 ms) as TEN recommends; a
 *                           VAD window must upsample to a whole number of hops
 * @param threshold          probability a window needs to count toward a barge-in on a TEN call, in
 *                           place of {@code vad.threshold}; also the library's own speech flag
 * @param listeningThreshold probability a window needs to hold the speech gate open while the bot
 *                           listens, in place of {@code vad.listening-threshold}
 */
@ConfigurationProperties(prefix = "voice-agent.ten-vad")
public record TenVadProperties(
        boolean enabled,
        String libraryPath,
        int hopSize,
        float threshold,
        float listeningThreshold
) {
}
