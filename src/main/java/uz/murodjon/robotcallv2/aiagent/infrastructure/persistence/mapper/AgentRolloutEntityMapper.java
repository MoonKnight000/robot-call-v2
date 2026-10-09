package uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentRollout;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.AgentRolloutEntity;

@Component
public class AgentRolloutEntityMapper {

    public AgentRollout toAgentRollout(AgentRolloutEntity entity) {
        if (entity == null) {
            return null;
        }
        return new AgentRollout(entity.getAgentId(), entity.getCompanyId(), entity.getCandidateVersion(),
                entity.getTrafficPercent(), entity.getStagingPhones(), entity.getStartedAt(), entity.getStartedBy());
    }
}
