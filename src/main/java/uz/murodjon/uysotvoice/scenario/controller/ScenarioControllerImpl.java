package uz.murodjon.uysotvoice.scenario.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.uysotvoice.scenario.dto.CloneScenarioRequest;
import uz.murodjon.uysotvoice.scenario.dto.CreateScenarioRequest;
import uz.murodjon.uysotvoice.scenario.dto.PersonaTestResult;
import uz.murodjon.uysotvoice.scenario.dto.ScenarioDefinition;
import uz.murodjon.uysotvoice.scenario.dto.ScenarioFilter;
import uz.murodjon.uysotvoice.scenario.dto.ScenarioRow;
import uz.murodjon.uysotvoice.scenario.dto.ScenarioSimulationRequest;
import uz.murodjon.uysotvoice.scenario.dto.ScenarioSimulationResponse;
import uz.murodjon.uysotvoice.scenario.dto.ScenarioValidationResult;
import uz.murodjon.uysotvoice.scenario.dto.UpdateScenarioRequest;
import uz.murodjon.uysotvoice.scenario.service.ScenarioService;
import uz.murodjon.uysotvoice.scenario.service.ScenarioSimulationService;
import uz.murodjon.uysotvoice.shared.api.PageableData;
import uz.murodjon.uysotvoice.shared.api.ResponseData;

import java.util.List;

@RestController
public class ScenarioControllerImpl implements ScenarioController {

    private final ScenarioService service;
    private final ScenarioSimulationService simulationService;

    public ScenarioControllerImpl(ScenarioService service,
                                  ScenarioSimulationService simulationService) {
        this.service = service;
        this.simulationService = simulationService;
    }

    @Override
    public ResponseEntity<ResponseData<ScenarioRow>> create(CreateScenarioRequest r) {
        return ResponseEntity.ok(ResponseData.ok(service.create(r)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<ScenarioRow>>> list(ScenarioFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(service.list(filter)));
    }

    @Override
    public ResponseEntity<ResponseData<ScenarioRow>> get(long id) {
        return ResponseEntity.ok(ResponseData.ok(service.scenarioRow(id)));
    }

    @Override
    public ResponseEntity<ResponseData<ScenarioRow>> update(long id, UpdateScenarioRequest r) {
        return ResponseEntity.ok(ResponseData.ok(service.update(id, r)));
    }

    @Override
    public ResponseEntity<ResponseData<ScenarioRow>> clone(long id, CloneScenarioRequest r) {
        return ResponseEntity.ok(ResponseData.ok(service.clone(id, r)));
    }

    @Override
    public ResponseEntity<ResponseData<ScenarioValidationResult>> validate(ScenarioDefinition definition) {
        return ResponseEntity.ok(ResponseData.ok(service.validate(definition)));
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
