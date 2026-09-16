package uz.murodjon.robotcallv2.aiagent.domain.entity;

import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentVersionStatus;

import java.time.Instant;

/**
 * One published state of an agent (NEXT-PLAN A.1). The {@code ai_agent} row is the draft;
 * calls run on the {@code PUBLISHED} snapshot, so an edit reaches nobody until the editor
 * publishes it, and a rollback is just publishing an older snapshot again.
 *
 * @param version     1, 2, 3… per agent, never reused
 * @param snapshot    the whole agent as it was published; {@link AiAgent#publishedVersion()}
 *                    carries {@code version} once the snapshot is read back
 * @param note        what the editor said this version changes, if anything
 * @param publishedBy the app_user who published it, null when a system job did
 */
public record AiAgentVersion(
        Long id,
        long agentId,
        long companyId,
        int version,
        AgentVersionStatus status,
        AiAgent snapshot,
        String note,
        Instant publishedAt,
        Long publishedBy
) {

    /** A version about to be written: the adapter assigns the number and the timestamp. */
    public static AiAgentVersion publishing(AiAgent snapshot, String note, Long publishedBy) {
        return new AiAgentVersion(null, snapshot.id(), snapshot.companyId(), 0,
                AgentVersionStatus.PUBLISHED, snapshot, note, null, publishedBy);
    }
}
