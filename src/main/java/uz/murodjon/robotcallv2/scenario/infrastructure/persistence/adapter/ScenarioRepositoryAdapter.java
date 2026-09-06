package uz.murodjon.robotcallv2.scenario.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioFilter;
import uz.murodjon.robotcallv2.scenario.application.mapper.ScenarioMapper;
import uz.murodjon.robotcallv2.scenario.application.port.output.ScenarioRepository;
import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.infrastructure.persistence.entity.ScenarioEntity;
import uz.murodjon.robotcallv2.scenario.infrastructure.persistence.repository.ScenarioJpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ScenarioRepositoryAdapter implements ScenarioRepository {

    private final ScenarioJpaRepository jpaRepository;
    private final ScenarioMapper mapper;

    public ScenarioRepositoryAdapter(ScenarioJpaRepository jpaRepository, ScenarioMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public long create(long companyId, String scenarioKey, String name, String description, boolean builtin,
                       ScenarioDefinition definition, Long createdBy) {
        return insertVersion(companyId, scenarioKey, 1, name, description, builtin, definition, createdBy);
    }

    @Override
    public long insertVersion(long companyId, String scenarioKey, int version, String name, String description,
                              boolean builtin, ScenarioDefinition definition, Long createdBy) {
        ScenarioEntity entity = new ScenarioEntity();
        entity.setScenarioKey(scenarioKey);
        entity.setVersion(version);
        entity.setName(name);
        entity.setDescription(description);
        entity.setBuiltin(builtin);
        entity.setActive(true);
        entity.setDefinition(mapper.writeDefinition(definition));
        entity.setCreatedBy(createdBy);
        entity.setCreatedAt(Instant.now());
        entity.setCompanyId(builtin ? null : companyId);
        return jpaRepository.save(entity).getId();
    }

    @Override
    public void deactivate(String scenarioKey) {
        jpaRepository.deactivate(scenarioKey);
    }

    @Override
    public Scenario find(long companyId, long id) {
        return jpaRepository.findVisible(id, companyId).map(mapper::entityToDomain).orElse(null);
    }

    @Override
    public Map<Long, String> namesByIds(long companyId, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return jpaRepository.findNamesByIds(ids, companyId).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (String) row[1]));
    }

    @Override
    public String nameById(long companyId, long id) {
        return namesByIds(companyId, List.of(id)).get(id);
    }

    @Override
    public int maxVersion(String scenarioKey) {
        Integer max = jpaRepository.maxVersion(scenarioKey);
        return max != null ? max : 0;
    }

    @Override
    public boolean existsByKey(String scenarioKey) {
        return jpaRepository.existsByScenarioKey(scenarioKey);
    }

    @Override
    public Scenario findActiveByKey(String scenarioKey) {
        return jpaRepository.findByScenarioKeyAndActiveTrue(scenarioKey).map(mapper::entityToDomain).orElse(null);
    }

    @Override
    public List<Scenario> findAll(long companyId, ScenarioFilter filter) {
        return jpaRepository.findVisible(companyId, filter.builtinOnly(), filter.pageable()).stream()
                .map(mapper::entityToDomain)
                .toList();
    }

    @Override
    public long count(long companyId, ScenarioFilter filter) {
        return jpaRepository.countVisible(companyId, filter.builtinOnly());
    }

    @Override
    public List<Scenario> findAllActive() {
        return jpaRepository.findByActiveTrue().stream().map(mapper::entityToDomain).toList();
    }
}
