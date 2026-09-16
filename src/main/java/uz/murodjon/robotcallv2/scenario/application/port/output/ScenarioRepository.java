package uz.murodjon.robotcallv2.scenario.application.port.output;

import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioFilter;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ScenarioRepository {

    long create(long companyId, String scenarioKey, String name, String description, boolean builtin,
                ScenarioDefinition definition, Long createdBy);

    long insertVersion(long companyId, String scenarioKey, int version, String name, String description,
                       boolean builtin, ScenarioDefinition definition, Long createdBy);

    /**
     * Retires the active row of {@code scenarioKey}, only while it is still {@code version}.
     *
     * @return {@code false} when that version is no longer the active one — another edit
     *         already replaced it — and nothing was changed
     */
    boolean deactivate(String scenarioKey, int version);

    Scenario find(long companyId, long id);

    Map<Long, String> findNamesByIds(long companyId, Collection<Long> ids);

    String findNameById(long companyId, long id);

    int findMaxVersion(String scenarioKey);

    boolean existsByKey(String scenarioKey);

    Scenario findActiveByKey(String scenarioKey);

    List<Scenario> findAll(long companyId, ScenarioFilter filter);

    long count(long companyId, ScenarioFilter filter);

    List<Scenario> findAllActive();
}
