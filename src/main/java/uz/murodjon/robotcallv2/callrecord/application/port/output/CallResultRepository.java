package uz.murodjon.robotcallv2.callrecord.application.port.output;

import uz.murodjon.robotcallv2.callrecord.domain.entity.CallResult;

public interface CallResultRepository {

    /**
     * Writes the one {@code call_result} row for a call, keeping whichever row got there
     * first — the outbox re-runs the summary for calls that still have none, and two
     * instances sweeping at once are summarizing the same transcript.
     */
    void insertIgnoringConflict(CallResult result);
}
