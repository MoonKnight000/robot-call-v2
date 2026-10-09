package uz.murodjon.robotcallv2.aiagent.application.port.output;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentRollout;

public interface AgentRolloutRepository {

    /** Null when the agent has no rollout running. */
    AgentRollout findByAgentId(long companyId, long agentId);

    AgentRollout create(AgentRollout rollout);

    /** Replaces the split; null when the agent has no rollout running. */
    AgentRollout update(AgentRollout rollout);

    void deleteByAgentId(long companyId, long agentId);
}
