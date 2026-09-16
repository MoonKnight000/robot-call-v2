package uz.murodjon.robotcallv2.quality.domain.entity;

import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.util.List;

/**
 * What the post-call pipeline knows about a call it hands over for scoring.
 *
 * @param agentVersion       the published version that ran it; null for a draft
 * @param transcript         the whole call, {@code AGENT:} and {@code CLIENT:} lines
 * @param bargeInCount       times the caller cut the agent off
 * @param noInputPromptCount times the silence watchdog asked whether the caller was still there
 * @param lowConfidenceCount caller utterances the recognizer was unsure of
 * @param zeroPiiRetention   the agent keeps no words of the call — the judge's note, which may
 *                           quote the caller, is then not stored
 * @param earlierCallIds     the same number's answered calls of the last day, which this one
 *                           marks as repeated
 */
public record FinishedCall(
        long callAttemptId,
        Long aiAgentId,
        Integer agentVersion,
        Disposition disposition,
        String transcript,
        int turnCount,
        int bargeInCount,
        int noInputPromptCount,
        int lowConfidenceCount,
        boolean zeroPiiRetention,
        List<Long> earlierCallIds
) {
    public FinishedCall {
        earlierCallIds = earlierCallIds == null ? List.of() : List.copyOf(earlierCallIds);
    }
}
