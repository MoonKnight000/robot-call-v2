package uz.murodjon.robotcallv2.scenario.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.scenario.application.dto.*;
import uz.murodjon.robotcallv2.scenario.application.port.input.ScenarioSimulationUseCase;
import uz.murodjon.robotcallv2.scenario.application.port.input.ScenarioUseCase;
import uz.murodjon.robotcallv2.scenario.domain.entity.PersonaTestResult;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioFilter;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioValidationResult;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

@RestController
public class ScenarioControllerImpl implements ScenarioController {

    private final ScenarioUseCase scenarioUseCase;
    private final ScenarioSimulationUseCase scenarioSimulationUseCase;

    public ScenarioControllerImpl(ScenarioUseCase scenarioUseCase,
                                  ScenarioSimulationUseCase scenarioSimulationUseCase) {
        this.scenarioUseCase = scenarioUseCase;
        this.scenarioSimulationUseCase = scenarioSimulationUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<ScenarioRow>> create(long companyId, CreateScenarioRequest request) {
        return ResponseEntity.ok(ResponseData.ok(scenarioUseCase.create(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<ScenarioRow>>> list(long companyId, ScenarioFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(scenarioUseCase.list(companyId, filter)));
    }

    @Override
    public ResponseEntity<ResponseData<ScenarioRow>> get(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(scenarioUseCase.scenarioRow(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<ScenarioRow>> update(long companyId, long id, UpdateScenarioRequest request) {
        return ResponseEntity.ok(ResponseData.ok(scenarioUseCase.update(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<ScenarioRow>> clone(long companyId, long id, CloneScenarioRequest request) {
        return ResponseEntity.ok(ResponseData.ok(scenarioUseCase.clone(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<ScenarioValidationResult>> validate(ScenarioDefinition definition) {
        return ResponseEntity.ok(ResponseData.ok(scenarioUseCase.validate(definition)));
    }

    @Override
    public ResponseEntity<ResponseData<ScenarioSimulationResponse>> simulate(long companyId, ScenarioSimulationRequest request) {
        return ResponseEntity.ok(ResponseData.ok(scenarioSimulationUseCase.simulateTurn(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<List<PersonaTestResult>>> testPersonas(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(scenarioSimulationUseCase.runPersonaTests(companyId, id)));
    }
}
