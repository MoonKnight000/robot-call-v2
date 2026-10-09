package uz.murodjon.robotcallv2.agent.vad;

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
 *                                voicemail. 3500, up from 2800: "Alo, assalomu alaykum,
 *                                eshitaman, kim bu?" runs about three seconds, and at 2800
 *                                that person was hung up on as a voicemail
 * @param silenceToleranceMs      silence allowed inside one speech run before it
 *                                counts as a real pause (speech dips below the
 *                                threshold on plosives)
 * @param beepSilenceMs           before a voicemail message: continuous quiet after which
 *                                a box that never beeps is taken to be recording
 *                                ({@code VoicemailBeepDetector}). Longer than a pause inside
 *                                a greeting, or the message starts over its second half
 * @param beepMaxWaitMs           before a voicemail message: how long to wait for the beep
 *                                or that quiet before speaking anyway
 */
public record AmdProperties(
        boolean enabled,
        int observeMs,
        int minContinuousSpeechMs,
        int silenceToleranceMs,
        int beepSilenceMs,
        int beepMaxWaitMs
) {
    /** Used when {@code voice-agent.vad.amd} sets no beep waits of its own, or is not set at all. */
    public static final int DEFAULT_BEEP_SILENCE_MS = 1500;
    public static final int DEFAULT_BEEP_MAX_WAIT_MS = 10_000;

    public AmdProperties {
        beepSilenceMs = beepSilenceMs > 0 ? beepSilenceMs : DEFAULT_BEEP_SILENCE_MS;
        beepMaxWaitMs = beepMaxWaitMs > 0 ? beepMaxWaitMs : DEFAULT_BEEP_MAX_WAIT_MS;
    }
}
