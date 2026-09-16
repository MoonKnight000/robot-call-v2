package uz.murodjon.robotcallv2.callrecord.application.port.output;

import uz.murodjon.robotcallv2.callrecord.domain.entity.CallResult;

import java.time.Instant;

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
}
