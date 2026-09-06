package uz.murodjon.robotcallv2.scenario.application.port.input;

import uz.murodjon.robotcallv2.scenario.application.dto.*;
import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioFilter;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioValidationResult;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ScenarioUseCase {

    ScenarioRow create(long companyId, CreateScenarioRequest request);

    ScenarioRow update(long companyId, long id, UpdateScenarioRequest request);

    ScenarioRow clone(long companyId, long id, CloneScenarioRequest request);

    ScenarioValidationResult validate(ScenarioDefinition definition);

    PageableData<ScenarioRow> list(long companyId, ScenarioFilter filter);

    ScenarioRow scenarioRow(long companyId, long id);

    Scenario requireScenario(long companyId, long id);

    Scenario findById(long companyId, long id);

    Scenario requireScenarioByKey(String scenarioKey);

    List<Scenario> findAllActiveForWarmup();

    Map<Long, String> scenarioNamesByIds(long companyId, Collection<Long> ids);
}
