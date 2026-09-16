package uz.murodjon.robotcallv2.aiagent.domain.event;

import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentEvalRunStatus;

/**
 * An evaluation run has stopped, however it stopped (NEXT-PLAN A.2, UNIVERSAL-OPERATOR-PLAN D.4).
 *
 * <p>Published so the improvement loop can hold a change behind its test cases without this
 * feature having to know that the loop exists: a run started by the insight gate is settled
 * by whoever listens for this, and a run started by a person from the editor is simply not
 * one anybody is waiting on.
 *
 * <p>Raised on the run's own thread once the results are written, so a listener reads the
 * finished run rather than the one still going.
 *
 * @param passed of {@code total} cases; a run that broke off may have passed some of them
 */
public record AgentEvalFinishedEvent(
        long companyId,
        long agentId,
        long runId,
        AgentEvalRunStatus status,
        int passed,
        int total
) {

    /** Whether every case passed — the only result the gate lets a change through on. */
    public boolean allPassed() {
        return status == AgentEvalRunStatus.DONE && total > 0 && passed == total;
    }
}
