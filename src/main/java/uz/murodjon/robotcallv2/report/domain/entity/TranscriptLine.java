package uz.murodjon.robotcallv2.report.domain.entity;

/**
 * One utterance of a call transcript.
 *
 * @param seq         order within the call
 * @param role        AGENT or CLIENT
 * @param text        what was said
 * @param dialogState FSM state the agent was in (null for client lines)
 * @param tsOffsetMs  milliseconds from the start of the call
 * @param confidence  recognizer confidence for client lines; a low value next to a
 *                    strange transcript is usually the explanation for a strange turn
 */
public record TranscriptLine(
        int seq,
        String role,
        String text,
        String dialogState,
        int tsOffsetMs,
        Float confidence
) {
}

