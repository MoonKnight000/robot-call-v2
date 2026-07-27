package uz.murodjon.uysotvoice.agent.vad;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Voice-activity-detection settings for barge-in. Bound from
 * {@code voice-agent.vad.*} (PROJECT.md §7.2, §12).
 *
 * @param enabled       when false no VAD runs and the bot cannot be interrupted
 * @param modelPath     path to {@code silero_vad.onnx}; blank means VAD is unavailable
 * @param sampleRate    audio rate fed to the model (8000 = native telephone, no resample)
 * @param windowSamples samples per inference window (256 @ 8 kHz ≈ 32 ms, Silero's step)
 * @param threshold     speech probability above which a window counts as speech (0..1)
 * @param minSpeechMs   continuous speech required to trigger barge-in (ignores short "aha")
 * @param silenceResetMs silence gap that re-arms the detector for the next utterance
 * @param amd           answering-machine detection, driven by the same window scores
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
        Amd amd
) {

    /**
     * Answering-machine detection settings (PROJECT.md §8.6). Needs VAD, since it reads
     * the same window scores barge-in does.
     *
     * @param enabled                 master switch; when off, a voicemail is talked to
     *                                like a person and billed like one
     * @param observeMs               how long after connect to keep watching. Past this
     *                                the conversation has started and a long utterance is
     *                                just someone talking
     * @param minContinuousSpeechMs   continuous speech that means a recording rather than
     *                                a person. Deliberately generous — cutting off a
     *                                talkative human is worse than paying for one wasted
     *                                voicemail
     * @param silenceToleranceMs      silence allowed inside one speech run before it
     *                                counts as a real pause (speech dips below the
     *                                threshold on plosives)
     */
    public record Amd(
            boolean enabled,
            int observeMs,
            int minContinuousSpeechMs,
            int silenceToleranceMs
    ) {
    }
}
