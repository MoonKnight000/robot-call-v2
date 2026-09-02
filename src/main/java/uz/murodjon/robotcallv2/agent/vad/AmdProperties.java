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
 *                                voicemail
 * @param silenceToleranceMs      silence allowed inside one speech run before it
 *                                counts as a real pause (speech dips below the
 *                                threshold on plosives)
 */
public record AmdProperties(
        boolean enabled,
        int observeMs,
        int minContinuousSpeechMs,
        int silenceToleranceMs
) {
}
