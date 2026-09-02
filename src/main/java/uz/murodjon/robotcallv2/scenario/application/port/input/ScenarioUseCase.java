package uz.murodjon.robotcallv2.scenario.application.port.input;

import uz.murodjon.robotcallv2.scenario.application.dto.*;
import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioValidationResult;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ScenarioUseCase {

    ScenarioRow create(CreateScenarioRequest r);

    ScenarioRow update(long id, UpdateScenarioRequest r);

    ScenarioRow clone(long id, CloneScenarioRequest r);

    ScenarioValidationResult validate(ScenarioDefinition definition);

    PageableData<ScenarioRow> list(ScenarioFilter filter);

    ScenarioRow scenarioRow(long id);

    Scenario requireScenario(long id);

    Scenario findById(long id);

    Scenario requireScenarioByKey(String scenarioKey);

    List<Scenario> findAllActiveForWarmup();

    Map<Long, String> scenarioNamesByIds(Collection<Long> ids);
}
