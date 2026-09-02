package uz.murodjon.robotcallv2.scenario.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.scenario.application.dto.*;
import uz.murodjon.robotcallv2.scenario.application.port.input.ScenarioUseCase;
import uz.murodjon.robotcallv2.scenario.application.service.ScenarioSimulationService;
import uz.murodjon.robotcallv2.scenario.domain.entity.PersonaTestResult;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioValidationResult;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

@RestController
public class ScenarioControllerImpl implements ScenarioController {

    private final ScenarioUseCase scenarioUseCase;
    private final ScenarioSimulationService simulationService;

    public ScenarioControllerImpl(ScenarioUseCase scenarioUseCase,
                                  ScenarioSimulationService simulationService) {
        this.scenarioUseCase = scenarioUseCase;
        this.simulationService = simulationService;
    }

    @Override
    public ResponseEntity<ResponseData<ScenarioRow>> create(CreateScenarioRequest r) {
        return ResponseEntity.ok(ResponseData.ok(scenarioUseCase.create(r)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<ScenarioRow>>> list(ScenarioFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(scenarioUseCase.list(filter)));
    }

    @Override
    public ResponseEntity<ResponseData<ScenarioRow>> get(long id) {
        return ResponseEntity.ok(ResponseData.ok(scenarioUseCase.scenarioRow(id)));
    }

    @Override
    public ResponseEntity<ResponseData<ScenarioRow>> update(long id, UpdateScenarioRequest r) {
        return ResponseEntity.ok(ResponseData.ok(scenarioUseCase.update(id, r)));
    }

    @Override
    public ResponseEntity<ResponseData<ScenarioRow>> clone(long id, CloneScenarioRequest r) {
        return ResponseEntity.ok(ResponseData.ok(scenarioUseCase.clone(id, r)));
    }

    @Override
    public ResponseEntity<ResponseData<ScenarioValidationResult>> validate(ScenarioDefinition definition) {
        return ResponseEntity.ok(ResponseData.ok(scenarioUseCase.validate(definition)));
    }

    @Override
    public ResponseEntity<ResponseData<ScenarioSimulationResponse>> simulate(ScenarioSimulationRequest r) {
        return ResponseEntity.ok(ResponseData.ok(simulationService.simulateTurn(r)));
    }

    @Override
    public ResponseEntity<ResponseData<List<PersonaTestResult>>> testPersonas(long id) {
        return ResponseEntity.ok(ResponseData.ok(simulationService.runPersonaTests(id)));
    }
}
