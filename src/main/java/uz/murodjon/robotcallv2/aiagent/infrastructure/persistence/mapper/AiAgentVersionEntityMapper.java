package uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentVersion;
import uz.murodjon.robotcallv2.aiagent.domain.service.AiAgentSnapshot;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.AiAgentVersionEntity;

/** Maps between {@link AiAgentVersionEntity} and {@link AiAgentVersion}; the agent travels as {@link AiAgentSnapshot} JSON. */
@Component
public class AiAgentVersionEntityMapper {

    public String writeSnapshotJson(AiAgent agent) {
        return AiAgentSnapshot.toJson(agent);
    }

    public AiAgentVersion toAiAgentVersion(AiAgentVersionEntity entity) {
        if (entity == null) {
            return null;
        }
        return new AiAgentVersion(
                entity.getId(),
                entity.getAgentId(),
                entity.getCompanyId(),
                entity.getVersion(),
                entity.getStatus(),
                AiAgentSnapshot.fromJson(entity.getSnapshot()).withPublishedVersion(entity.getVersion()),
                entity.getNote(),
                entity.getPublishedAt(),
                entity.getPublishedBy());
    }
}
