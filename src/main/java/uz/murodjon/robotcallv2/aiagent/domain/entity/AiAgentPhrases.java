package uz.murodjon.robotcallv2.aiagent.domain.entity;

import java.util.List;
import java.util.Objects;

/**
 * The agent's own wording for the lines the engine speaks from code rather than through the
 * model — what this agent says on a quiet line, after an empty reply, or when it closes. Every
 * field left null keeps the standard line ({@code DialogPhrases}).
 *
 * <p>Written in the agent's own {@code language}: a call that runs in another of its speech
 * languages hears the standard lines of that language instead, never this text in the wrong one.
 * Spoken word for word and pre-synthesized at campaign start, so they cost no model time.
 *
 * @param stillThere         asked when the line has gone quiet (the unanswered question follows it)
 * @param stillThereLastCall the last check on a quiet line, which warns the call is about to end
 * @param didNotCatch        said when the model produced nothing speakable and the stage has no line of its own
 * @param farewell           the goodbye when a limit (turns, duration, silence) ends the call
 * @param gratitudeReply     the reply to a caller's closing "rahmat, xayr"
 * @param doNotCallReply     the reply to "boshqa qo'ng'iroq qilmang" before the call ends
 * @param thinking           holding lines over a long wait ("Bir lahza.") — about the waiting only,
 *                           never an acknowledgement or a promise; empty = the standard ones
 * @param backchannels       one- or two-word listening noises over a long answer ("Aha."); empty = the standard ones
 */
public record AiAgentPhrases(
        String stillThere,
        String stillThereLastCall,
        String didNotCatch,
        String farewell,
        String gratitudeReply,
        String doNotCallReply,
        List<String> thinking,
        List<String> backchannels
) {

    /** No line of its own anywhere: the agent speaks the standard ones. */
    public static final AiAgentPhrases NONE = new AiAgentPhrases(null, null, null, null, null, null, null, null);

    public AiAgentPhrases {
        stillThere = clean(stillThere);
        stillThereLastCall = clean(stillThereLastCall);
        didNotCatch = clean(didNotCatch);
        farewell = clean(farewell);
        gratitudeReply = clean(gratitudeReply);
        doNotCallReply = clean(doNotCallReply);
        thinking = cleanAll(thinking);
        backchannels = cleanAll(backchannels);
    }

    private static String clean(String line) {
        return line == null || line.isBlank() ? null : line.strip();
    }

    private static List<String> cleanAll(List<String> lines) {
        return lines == null ? List.of() : lines.stream().map(AiAgentPhrases::clean).filter(Objects::nonNull).toList();
    }
}
