package uz.murodjon.robotcallv2.agent.vad;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * Voice-activity-detection settings for barge-in. Bound from
 * {@code voice-agent.vad.*} (PROJECT.md §7.2, §12).
 *
 * @param enabled            when false no VAD runs and the bot cannot be interrupted
 * @param modelPath          path to {@code silero_vad.onnx}. A local run (IDE/bootRun)
 *                           reads {@code models/silero_vad.onnx} from the project root —
 *                           the Silero v5.1.2 file the Dockerfile bakes into the image
 *                           (the download line is in .gitignore); Docker sets
 *                           {@code VAD_MODEL_PATH} itself. Without a model there is no
 *                           barge-in, no STT gating and no endpointing on this side: the
 *                           call silently falls back to the recognizer's own EOU, which is
 *                           why an IDE run sounded nothing like Docker
 * @param sampleRate         audio rate fed to the model (8000 = native telephone, no resample)
 * @param windowSamples      samples per inference window (256 @ 8 kHz ≈ 32 ms, Silero's step)
 * @param threshold          speech probability above which a window counts as speech (0..1)
 * @param minSpeechMs        continuous speech required to trigger barge-in (ignores short
 *                           "aha"). 400, up from 250 — LiveKit ships 500. At 250 a cough,
 *                           a door, or the bot's own voice off a speakerphone silenced the
 *                           line for the whole {@code dialog.false-interruption-timeout-ms}
 *                           before the reply resumed
 * @param silenceResetMs     silence gap that re-arms the detector for the next utterance
 * @param amd                answering-machine detection, driven by the same window scores
 * @param listeningThreshold more sensitive speech probability threshold when the bot is silent and listening (0..1)
 * @param minEnergyRms       minimum RMS energy threshold to prevent mic noise, coughs, and breathing from triggering barge-in
 * @param noiseFloorMargin   how far above the measured background noise speech has to sit
 *                           before it may interrupt the bot, as a multiple of that noise.
 *                           {@code minEnergyRms} alone is a fixed line and a caller on a
 *                           street sits above it permanently, so every passing car cut the
 *                           bot off; this raises the line with the call. 2.5 is about 8 dB
 *                           over the measured floor, and the floor can only push the
 *                           threshold to 6× {@code minEnergyRms} so a loud line never
 *                           becomes one the caller cannot interrupt at all. 0 or less
 * @param echoGateRatio      caller audio quieter than this share of the bot's own recent output
 *                           level is treated as the bot's voice coming back down the line and
 *                           ignored for barge-in (e.g. 0.15 is -16 dB). 0 disables echo gate.
 * @param echoMinBotLevel    bot output level (RMS) below which the echo gate stays open (e.g. 300.0).
 * @param sustainedSpeechMs  how long the caller has to keep talking after a barge-in, gaps
 *                           shorter than {@code silenceResetMs} included, before the paused
 *                           bot yields without waiting for their words. A listener's "aha" or
 *                           "xo'p, xo'p" is over well inside it; a caller still talking is
 *                           taking the floor in whatever dialect they speak. Counted on top of
 *                           {@code minSpeechMs}. 0 turns it off
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
        double minEnergyRms,
        double noiseFloorMargin,
        double echoGateRatio,
        double echoMinBotLevel,
        int sustainedSpeechMs
) {
    @ConstructorBinding
    public VadProperties {
    }

    public VadProperties(boolean enabled, String modelPath, int sampleRate, int windowSamples,
                         float threshold, int minSpeechMs, int silenceResetMs, AmdProperties amd,
                         float listeningThreshold, double minEnergyRms, double noiseFloorMargin,
                         double echoGateRatio, double echoMinBotLevel) {
        this(enabled, modelPath, sampleRate, windowSamples, threshold, minSpeechMs, silenceResetMs, amd,
                listeningThreshold, minEnergyRms, noiseFloorMargin, echoGateRatio, echoMinBotLevel, 0);
    }

    public VadProperties(boolean enabled, String modelPath, int sampleRate, int windowSamples,
                         float threshold, int minSpeechMs, int silenceResetMs, AmdProperties amd,
                         float listeningThreshold, double minEnergyRms, double noiseFloorMargin) {
        this(enabled, modelPath, sampleRate, windowSamples, threshold, minSpeechMs, silenceResetMs, amd,
                listeningThreshold, minEnergyRms, noiseFloorMargin, 0.15, 300.0, 0);
    }
}
