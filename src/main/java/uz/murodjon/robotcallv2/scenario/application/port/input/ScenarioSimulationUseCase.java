package uz.murodjon.robotcallv2.scenario.application.port.input;

import uz.murodjon.robotcallv2.scenario.application.dto.ScenarioSimulationRequest;
import uz.murodjon.robotcallv2.scenario.application.dto.ScenarioSimulationResponse;
import uz.murodjon.robotcallv2.scenario.domain.entity.PersonaTestResult;

import java.util.List;

public interface ScenarioSimulationUseCase {

    ScenarioSimulationResponse simulateTurn(long companyId, ScenarioSimulationRequest request);

    List<PersonaTestResult> runPersonaTests(long companyId, Long scenarioId);
}
