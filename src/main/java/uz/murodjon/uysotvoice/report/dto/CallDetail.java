package uz.murodjon.uysotvoice.report.dto;

import java.util.List;

/**
 * One call in full: its report line plus the conversation it contained.
 *
 * <p>The transcript is the point. A disposition on its own is unarguable data with no
 * explanation — when a client disputes what the agent said, or the promise rate drops and
 * nobody knows why, the answer is in the turns.
 *
 * @param call       the summary line
 * @param transcript every utterance in order, agent and client
 * @param reasonCode why the debt is unpaid, as the summary classified it
 * @param sentiment  how the client came across
 * @param needsFollowUp whether the summary flagged a follow-up
 * @param followUpNote  note for that follow-up
 * @param escalated  whether the call went to a human
 * @param errorMessage technical failure recorded on the attempt, if any
 */
public record CallDetail(
        CallRow call,
        List<TranscriptLine> transcript,
        String reasonCode,
        String sentiment,
        boolean needsFollowUp,
        String followUpNote,
        boolean escalated,
        String errorMessage
) {
}
