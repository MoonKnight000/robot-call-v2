package uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.aiagent.application.port.output.AgentRolloutRepository;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentRollout;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.AgentRolloutEntity;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.mapper.AgentRolloutEntityMapper;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.repository.AgentRolloutJpaRepository;

@Component
public class AgentRolloutRepositoryAdapter implements AgentRolloutRepository {

    private final AgentRolloutJpaRepository jpaRepository;
    private final AgentRolloutEntityMapper mapper;

    public AgentRolloutRepositoryAdapter(AgentRolloutJpaRepository jpaRepository, AgentRolloutEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public AgentRollout findByAgentId(long companyId, long agentId) {
        return jpaRepository.findByCompanyIdAndAgentId(companyId, agentId).map(mapper::toAgentRollout).orElse(null);
    }

    @Override
    public AgentRollout create(AgentRollout rollout) {
        AgentRolloutEntity entity = new AgentRolloutEntity();
        entity.setAgentId(rollout.agentId());
        entity.setCompanyId(rollout.companyId());
        entity.setCandidateVersion(rollout.candidateVersion());
        entity.setTrafficPercent(rollout.trafficPercent());
        entity.setStagingPhones(rollout.stagingPhones());
        entity.setStartedBy(rollout.startedBy());
        return mapper.toAgentRollout(jpaRepository.saveAndFlush(entity));
    }

    @Override
    public AgentRollout update(AgentRollout rollout) {
        return jpaRepository.findByCompanyIdAndAgentId(rollout.companyId(), rollout.agentId())
                .map(entity -> {
                    entity.setTrafficPercent(rollout.trafficPercent());
                    entity.setStagingPhones(rollout.stagingPhones());
                    return mapper.toAgentRollout(jpaRepository.save(entity));
                })
                .orElse(null);
    }

    @Override
    public void deleteByAgentId(long companyId, long agentId) {
        jpaRepository.deleteByCompanyIdAndAgentId(companyId, agentId);
    }
}
