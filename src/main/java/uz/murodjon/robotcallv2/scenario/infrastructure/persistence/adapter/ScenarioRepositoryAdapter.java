package uz.murodjon.robotcallv2.scenario.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.scenario.application.port.output.ScenarioRepository;
import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioFilter;
import uz.murodjon.robotcallv2.scenario.infrastructure.persistence.entity.ScenarioEntity;
import uz.murodjon.robotcallv2.scenario.infrastructure.persistence.mapper.ScenarioEntityMapper;
import uz.murodjon.robotcallv2.scenario.infrastructure.persistence.repository.ScenarioJpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ScenarioRepositoryAdapter implements ScenarioRepository {

    private final ScenarioJpaRepository jpaRepository;
    private final ScenarioEntityMapper mapper;
    private final CompanyJpaRepository companyJpaRepository;

    public ScenarioRepositoryAdapter(ScenarioJpaRepository jpaRepository, ScenarioEntityMapper mapper,
                                     CompanyJpaRepository companyJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.companyJpaRepository = companyJpaRepository;
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
        entity.setCompany(builtin ? null : companyJpaRepository.getReferenceById(companyId));
        return jpaRepository.save(entity).getId();
    }

    @Override
    public boolean deactivate(String scenarioKey, int version) {
        return jpaRepository.deactivate(scenarioKey, version) > 0;
    }

    @Override
    public Scenario find(long companyId, long id) {
        return jpaRepository.findVisible(id, companyId).map(mapper::toScenario).orElse(null);
    }

    @Override
    public Map<Long, String> findNamesByIds(long companyId, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return jpaRepository.findNamesByIds(ids, companyId).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (String) row[1]));
    }

    @Override
    public String findNameById(long companyId, long id) {
        return findNamesByIds(companyId, List.of(id)).get(id);
    }

    @Override
    public int findMaxVersion(String scenarioKey) {
        Integer max = jpaRepository.findMaxVersion(scenarioKey);
        return max != null ? max : 0;
    }

    @Override
    public boolean existsByKey(String scenarioKey) {
        return jpaRepository.existsByScenarioKey(scenarioKey);
    }

    @Override
    public Scenario findActiveByKey(String scenarioKey) {
        return jpaRepository.findByScenarioKeyAndActiveTrue(scenarioKey).map(mapper::toScenario).orElse(null);
    }

    @Override
    public List<Scenario> findAll(long companyId, ScenarioFilter filter) {
        return jpaRepository.findVisible(companyId, filter.builtinOnly(), filter.pageable()).stream()
                .map(mapper::toScenario)
                .toList();
    }

    @Override
    public long count(long companyId, ScenarioFilter filter) {
        return jpaRepository.countVisible(companyId, filter.builtinOnly());
    }

    @Override
    public List<Scenario> findAllActive() {
        return jpaRepository.findByActiveTrue().stream().map(mapper::toScenario).toList();
    }
}
