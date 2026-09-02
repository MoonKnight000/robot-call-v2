package uz.murodjon.robotcallv2.scenario.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.company.application.service.CurrentCompany;
import uz.murodjon.robotcallv2.scenario.application.dto.ScenarioFilter;
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

    private final ScenarioJpaRepository jpa;
    private final CurrentCompany company;
    private final ScenarioMapper mapper;

    public ScenarioRepositoryAdapter(ScenarioJpaRepository jpa, CurrentCompany company, ScenarioMapper mapper) {
        this.jpa = jpa;
        this.company = company;
        this.mapper = mapper;
    }

    @Override
    public long create(String scenarioKey, String name, String description, boolean builtin,
                       ScenarioDefinition definition, Long createdBy) {
        return insertVersion(scenarioKey, 1, name, description, builtin, definition, createdBy);
    }

    @Override
    public long insertVersion(String scenarioKey, int version, String name, String description,
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
        entity.setCompanyId(builtin ? null : company.id());
        return jpa.save(entity).getId();
    }

    @Override
    public void deactivate(String scenarioKey) {
        jpa.deactivate(scenarioKey);
    }

    @Override
    public Scenario find(long id) {
        return jpa.findVisible(id, company.id()).map(mapper::entityToDomain).orElse(null);
    }

    @Override
    public Map<Long, String> namesByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return jpa.findNamesByIds(ids, company.id()).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (String) row[1]));
    }

    @Override
    public String nameById(long id) {
        return namesByIds(List.of(id)).get(id);
    }

    @Override
    public int maxVersion(String scenarioKey) {
        Integer max = jpa.maxVersion(scenarioKey);
        return max != null ? max : 0;
    }

    @Override
    public boolean existsByKey(String scenarioKey) {
        return jpa.existsByScenarioKey(scenarioKey);
    }

    @Override
    public Scenario findActiveByKey(String scenarioKey) {
        return jpa.findByScenarioKeyAndActiveTrue(scenarioKey).map(mapper::entityToDomain).orElse(null);
    }

    @Override
    public List<Scenario> findAll(ScenarioFilter filter) {
        return jpa.findVisible(company.id(), filter.builtinOnly(), filter.pageable()).stream()
                .map(mapper::entityToDomain)
                .toList();
    }

    @Override
    public long count(ScenarioFilter filter) {
        return jpa.countVisible(company.id(), filter.builtinOnly());
    }

    @Override
    public List<Scenario> findAllActive() {
        return jpa.findByActiveTrue().stream().map(mapper::entityToDomain).toList();
    }
}
