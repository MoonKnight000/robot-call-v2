package uz.murodjon.robotcallv2.callrecord.application.port.output;

import uz.murodjon.robotcallv2.callrecord.domain.entity.CallReanalysisScope;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallReanalysisTarget;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface CallResultRepository {

    /**
     * Writes the one {@code call_result} row for a call, keeping whichever row got there
     * first — the outbox re-runs the summary for calls that still have none, and two
     * instances sweeping at once are summarizing the same transcript.
     */
    void insertIgnoringConflict(CallResult result);

    /** The CRM took the call's note under {@code crmNoteId} — the calls page and reports read it here. */
    void markCrmPosted(long callId, String crmNoteId);

    /** Deletes up to {@code batchSize} rows, across every company, written before {@code cutoff}. */
    int deleteCreatedBefore(Instant cutoff, int batchSize);

    /**
     * The calls a reanalysis of {@code aiAgentId} goes through: started in [{@code from}, {@code to}),
     * with a stored result and a transcript to read again. In id order after {@code afterCallId}.
     */
    List<CallReanalysisTarget> findReanalysisTargets(long companyId, long aiAgentId, Instant from, Instant to,
                                                     long afterCallId, int limit);

    /** How many calls {@link #findReanalysisTargets} would give over the whole range, and their transcripts' length. */
    CallReanalysisScope findReanalysisScope(long companyId, long aiAgentId, Instant from, Instant to);

    /** The call's stored outcome; null when it has no result. */
    Map<String, Object> findOutcomeByCallId(long callId);

    /** Replaces the call's stored outcome and nothing else in its result. */
    void updateOutcome(long callId, Map<String, Object> outcome);
}
