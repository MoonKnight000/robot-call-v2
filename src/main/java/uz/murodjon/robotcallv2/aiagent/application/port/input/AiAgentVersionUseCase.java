package uz.murodjon.robotcallv2.aiagent.application.port.input;

import uz.murodjon.robotcallv2.aiagent.application.dto.AgentRolloutRow;
import uz.murodjon.robotcallv2.aiagent.application.dto.AiAgentVersionRow;
import uz.murodjon.robotcallv2.aiagent.application.dto.PublishAiAgentRequest;
import uz.murodjon.robotcallv2.aiagent.application.dto.StartAgentRolloutRequest;
import uz.murodjon.robotcallv2.aiagent.application.dto.UpdateAgentRolloutRequest;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentVersionChange;

import java.util.List;

/** An agent's published versions: publishing the draft, rolling back, and the history (NEXT-PLAN A.1). */
public interface AiAgentVersionUseCase {

    /** Copies the draft into a new published version; 409 when nothing changed since the last one. */
    AiAgentVersionRow publishAgent(long companyId, long id, PublishAiAgentRequest request);

    /** Makes {@code version} the draft again and publishes it as a new version. */
    AiAgentVersionRow rollbackAgent(long companyId, long id, int version);

    List<AiAgentVersionRow> findVersions(long companyId, long id);

    /**
     * Publishes the draft as a candidate beside the live version and sends part of the traffic,
     * plus the staging numbers, to it. Gated like a publish (an evaluation pass when required).
     */
    AgentRolloutRow startRollout(long companyId, long id, StartAgentRolloutRequest request);

    AgentRolloutRow updateRollout(long companyId, long id, UpdateAgentRolloutRequest request);

    /** The running rollout with each version's calls and conversions since it started. */
    AgentRolloutRow findRollout(long companyId, long id);

    /** The candidate goes live for every caller; the rollout ends. */
    AiAgentVersionRow promoteRollout(long companyId, long id);

    /** The candidate is archived and every caller is back on the live version. */
    void cancelRollout(long companyId, long id);

    /**
     * Settings that differ from version {@code from} to version {@code to}; {@code to} null
     * compares against the draft — what a publish would change.
     */
    List<AgentVersionChange> diffVersions(long companyId, long id, int from, Integer to);
}
