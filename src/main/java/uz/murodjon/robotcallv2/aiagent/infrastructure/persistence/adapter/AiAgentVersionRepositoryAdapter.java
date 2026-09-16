package uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.adapter;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.mapper.AiAgentVersionEntityMapper;
import uz.murodjon.robotcallv2.aiagent.application.port.output.AiAgentVersionRepository;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentVersionStats;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentVersion;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentVersionStatus;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.AiAgentVersionEntity;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.repository.AiAgentVersionJpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public class AiAgentVersionRepositoryAdapter implements AiAgentVersionRepository {

    private final AiAgentVersionJpaRepository jpaRepository;
    private final AiAgentVersionEntityMapper mapper;

    public AiAgentVersionRepositoryAdapter(AiAgentVersionJpaRepository jpaRepository, AiAgentVersionEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public AiAgentVersion publish(AiAgentVersion version) {
        jpaRepository.findByCompanyIdAndAgentIdAndStatus(version.companyId(), version.agentId(),
                        AgentVersionStatus.PUBLISHED)
                .ifPresent(previous -> {
                    previous.setStatus(AgentVersionStatus.ARCHIVED);
                    jpaRepository.saveAndFlush(previous);
                });
        // Archived and flushed before the insert, so the partial unique index on the live
        // version never sees two of them.
        AiAgentVersionEntity entity = new AiAgentVersionEntity();
        entity.setAgentId(version.agentId());
        entity.setCompanyId(version.companyId());
        entity.setVersion(jpaRepository.findMaxVersion(version.agentId()) + 1);
        entity.setStatus(AgentVersionStatus.PUBLISHED);
        entity.setSnapshot(mapper.writeSnapshotJson(version.snapshot()));
        entity.setNote(version.note());
        entity.setPublishedAt(Instant.now());
        entity.setPublishedBy(version.publishedBy());
        return mapper.toAiAgentVersion(jpaRepository.save(entity));
    }

    @Override
    public AiAgentVersion findPublished(long companyId, long agentId) {
        return jpaRepository.findByCompanyIdAndAgentIdAndStatus(companyId, agentId, AgentVersionStatus.PUBLISHED)
                .map(mapper::toAiAgentVersion)
                .orElse(null);
    }

    @Override
    public List<AiAgentVersion> findPublishedByAgentIds(long companyId, Collection<Long> agentIds) {
        if (agentIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findByCompanyIdAndAgentIdInAndStatus(companyId, agentIds, AgentVersionStatus.PUBLISHED)
                .stream()
                .map(mapper::toAiAgentVersion)
                .toList();
    }

    @Override
    public AiAgentVersion findByAgentIdAndVersion(long companyId, long agentId, int version) {
        return jpaRepository.findByCompanyIdAndAgentIdAndVersion(companyId, agentId, version)
                .map(mapper::toAiAgentVersion)
                .orElse(null);
    }

    @Override
    public List<AiAgentVersion> findByAgentId(long companyId, long agentId) {
        return jpaRepository.findByCompanyIdAndAgentIdOrderByVersionDesc(companyId, agentId)
                .stream()
                .map(mapper::toAiAgentVersion)
                .toList();
    }

    @Override
    public List<AgentVersionStats> findCallStats(long companyId, long agentId) {
        return jpaRepository.findCallStats(companyId, agentId).stream()
                .map(row -> new AgentVersionStats(((Number) row[0]).intValue(),
                        ((Number) row[1]).longValue(), ((Number) row[2]).longValue()))
                .toList();
    }
}
