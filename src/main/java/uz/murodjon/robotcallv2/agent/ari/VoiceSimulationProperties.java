package uz.murodjon.robotcallv2.agent.ari;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Voice-level agent evaluation ({@link VoiceCallSimulator}). Bound from
 * {@code voice-agent.simulation.voice.*}.
 *
 * @param personaVoice  catalog id of the voice the simulated caller speaks with; blank uses
 *                      the default TTS provider's default voice for the call's language
 * @param snrDb         background noise under the caller, as signal-to-noise in dB — lower
 *                      is noisier; 0 or less sends the caller's speech clean
 * @param botQuietMs    how long the bot must have been silent before its turn counts as
 *                      over and the caller answers. Longer than the gap between two of
 *                      its sentences, or the caller talks over its second one
 * @param turnTimeoutMs how long the caller waits for the bot to answer before the call is
 *                      given up as stuck
 */
@ConfigurationProperties(prefix = "voice-agent.simulation.voice")
public record VoiceSimulationProperties(
        String personaVoice,
        double snrDb,
        int botQuietMs,
        int turnTimeoutMs
) {

    public VoiceSimulationProperties {
        if (botQuietMs <= 0) {
            botQuietMs = 1500;
        }
        if (turnTimeoutMs <= 0) {
            turnTimeoutMs = 30_000;
        }
    }
}
