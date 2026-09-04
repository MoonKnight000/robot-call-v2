package uz.murodjon.robotcallv2.agent.vad;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Voice-activity-detection settings for barge-in. Bound from
 * {@code voice-agent.vad.*} (PROJECT.md §7.2, §12).
 *
 * @param enabled            when false no VAD runs and the bot cannot be interrupted
 * @param modelPath          path to {@code silero_vad.onnx}; blank means VAD is unavailable
 * @param sampleRate         audio rate fed to the model (8000 = native telephone, no resample)
 * @param windowSamples      samples per inference window (256 @ 8 kHz ≈ 32 ms, Silero's step)
 * @param threshold          speech probability above which a window counts as speech (0..1)
 * @param minSpeechMs        continuous speech required to trigger barge-in (ignores short "aha")
 * @param silenceResetMs     silence gap that re-arms the detector for the next utterance
 * @param amd                answering-machine detection, driven by the same window scores
 * @param listeningThreshold more sensitive speech probability threshold when the bot is silent and listening (0..1)
 * @param minEnergyRms       minimum RMS energy threshold to prevent mic noise, coughs, and breathing from triggering barge-in
 */
@ConfigurationProperties(prefix = "voice-agent.vad")
public record VadProperties(
        boolean enabled,
        String modelPath,
        int sampleRate,
        int windowSamples,
        float threshold,
        int minSpeechMs,
        int silenceResetMs,
        AmdProperties amd,
        float listeningThreshold,
        double minEnergyRms
) {
}
