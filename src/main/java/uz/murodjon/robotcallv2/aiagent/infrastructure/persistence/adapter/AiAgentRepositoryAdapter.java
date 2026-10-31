package uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.adapter;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.aiagent.application.port.output.AiAgentRepository;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentCallStats;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentFilter;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.AiAgentEntity;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.mapper.AiAgentEntityMapper;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.repository.AiAgentJpaRepository;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.scenario.infrastructure.persistence.entity.ScenarioEntity;
import uz.murodjon.robotcallv2.scenario.infrastructure.persistence.repository.ScenarioJpaRepository;
import uz.murodjon.robotcallv2.shared.util.LikePatterns;
import uz.murodjon.robotcallv2.voice.infrastructure.persistence.entity.TtsVoiceEntity;
import uz.murodjon.robotcallv2.voice.infrastructure.persistence.repository.TtsVoiceJpaRepository;

import java.time.Instant;
import java.util.*;

@Component
public class AiAgentRepositoryAdapter implements AiAgentRepository {

    private final AiAgentJpaRepository jpaRepository;
    private final AiAgentEntityMapper mapper;
    private final CompanyJpaRepository companyJpaRepository;
    private final ScenarioJpaRepository scenarioJpaRepository;
    private final TtsVoiceJpaRepository ttsVoiceJpaRepository;

    public AiAgentRepositoryAdapter(AiAgentJpaRepository jpaRepository, AiAgentEntityMapper mapper,
                                    CompanyJpaRepository companyJpaRepository,
                                    ScenarioJpaRepository scenarioJpaRepository,
                                    TtsVoiceJpaRepository ttsVoiceJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.companyJpaRepository = companyJpaRepository;
        this.scenarioJpaRepository = scenarioJpaRepository;
        this.ttsVoiceJpaRepository = ttsVoiceJpaRepository;
    }

    @Override
    @Transactional
    public long create(AiAgent agent) {
        AiAgentEntity entity = new AiAgentEntity();
        entity.setCompany(companyJpaRepository.getReferenceById(agent.companyId()));
        entity.setCreatedBy(agent.createdBy());
        entity.setCreatedAt(Instant.now());
        mapper.applyEditableFields(entity, agent, scenarioReference(agent.script().scenarioId()),
                languageVoiceReferences(agent.voice().perLanguage()));
        return jpaRepository.save(entity).getId();
    }

    @Override
    @Transactional
    public boolean update(long companyId, long id, AiAgent agent) {
        AiAgentEntity entity = jpaRepository.findByIdAndCompanyId(id, companyId).orElse(null);
        if (entity == null) {
            return true;
        }
        if (entity.getVersion() != agent.version()) {
            return false;
        }
        // The scenario is editable here and identity is not: an agent that reads a different
        // script is the same agent with a new script, but it never changes company or id.
        // An edit landing between this read and the commit is caught by @Version at flush.
        mapper.applyEditableFields(entity, agent, scenarioReference(agent.script().scenarioId()),
                languageVoiceReferences(agent.voice().perLanguage()));
        jpaRepository.save(entity);
        return true;
    }

    @Override
    public AiAgent findByCompanyIdAndId(long companyId, long id) {
        return jpaRepository.findByIdAndCompanyId(id, companyId)
                .map(mapper::toAiAgent)
                .orElse(null);
    }

    @Override
    public List<AiAgent> findAll(long companyId, AiAgentFilter filter) {
        return jpaRepository.findAll(buildSpecification(companyId, filter), filter.pageable()).stream()
                .map(mapper::toAiAgent)
                .toList();
    }

    @Override
    public long count(long companyId, AiAgentFilter filter) {
        return jpaRepository.count(buildSpecification(companyId, filter));
    }

    @Override
    public Map<Long, String> findNamesByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new LinkedHashMap<>();
        for (AiAgentEntity entity : jpaRepository.findByIdIn(ids)) {
            names.put(entity.getId(), entity.getName());
        }
        return names;
    }

    @Override
    @Transactional
    public void delete(long companyId, long id) {
        jpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(jpaRepository::delete);
    }

    @Override
    public long countCampaignsUsing(long id) {
        return jpaRepository.countCampaignsUsing(id);
    }

    @Override
    public long countInboundRoutesUsing(long id) {
        return jpaRepository.countInboundRoutesUsing(id);
    }

    @Override
    public boolean existsCallQueueByCompanyIdAndId(long companyId, long queueId) {
        return jpaRepository.countCallQueuesByCompanyIdAndId(companyId, queueId) > 0;
    }

    @Override
    public AgentCallStats findRecentCallStats(long companyId, long agentId, int limit) {
        List<Object[]> rows = jpaRepository.findRecentCallStats(companyId, agentId, limit);
        if (rows.isEmpty() || ((Number) rows.getFirst()[0]).intValue() == 0) {
            return AgentCallStats.NONE;
        }
        Object[] row = rows.getFirst();
        return new AgentCallStats(((Number) row[0]).intValue(), ((Number) row[1]).longValue(),
                ((Number) row[2]).longValue(), rounded(row[3]), rounded(row[4]), rounded(row[5]), rounded(row[6]));
    }

    /** A percentile column: null when no call had the value measured. */
    private static Integer rounded(Object value) {
        return value == null ? null : (int) Math.round(((Number) value).doubleValue());
    }

    /** Null for an agent whose script is inline rather than a stored scenario. */
    private ScenarioEntity scenarioReference(Long scenarioId) {
        return scenarioId != null ? scenarioJpaRepository.getReferenceById(scenarioId) : null;
    }

    /** Empty when the agent speaks every language with its default voice. */
    private Map<String, TtsVoiceEntity> languageVoiceReferences(Map<String, String> perLanguage) {
        Map<String, TtsVoiceEntity> voices = new LinkedHashMap<>();
        if (perLanguage != null) {
            perLanguage.forEach((language, voiceId) ->
                    voices.put(language, ttsVoiceJpaRepository.getReferenceById(voiceId)));
        }
        return voices;
    }

    private static Specification<AiAgentEntity> buildSpecification(long companyId, AiAgentFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("company").get("id"), companyId));
            if (filter.scenarioId() != null) {
                predicates.add(cb.equal(root.get("scenario").get("id"), filter.scenarioId()));
            }
            if (filter.enabled() != null) {
                predicates.add(cb.equal(root.get("enabled"), filter.enabled()));
            }
            if (filter.search() != null && !filter.search().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")),
                        LikePatterns.buildContainsPattern(filter.search().toLowerCase()), LikePatterns.ESCAPE));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
