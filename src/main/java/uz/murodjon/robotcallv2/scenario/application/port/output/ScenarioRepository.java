package uz.murodjon.robotcallv2.scenario.application.port.output;

import uz.murodjon.robotcallv2.scenario.application.dto.ScenarioFilter;
import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ScenarioRepository {

    long create(String scenarioKey, String name, String description, boolean builtin,
                ScenarioDefinition definition, Long createdBy);

    long insertVersion(String scenarioKey, int version, String name, String description,
                       boolean builtin, ScenarioDefinition definition, Long createdBy);

    void deactivate(String scenarioKey);

    Scenario find(long id);

    Map<Long, String> namesByIds(Collection<Long> ids);

    String nameById(long id);

    int maxVersion(String scenarioKey);

    boolean existsByKey(String scenarioKey);

    Scenario findActiveByKey(String scenarioKey);

    List<Scenario> findAll(ScenarioFilter filter);

    long count(ScenarioFilter filter);

    List<Scenario> findAllActive();
}
