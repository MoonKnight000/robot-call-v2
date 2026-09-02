package uz.murodjon.robotcallv2.scenario.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.scenario.application.dto.*;
import uz.murodjon.robotcallv2.scenario.domain.entity.PersonaTestResult;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioValidationResult;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

/**
 * Scenario CRUD & Simulation API (ROADMAP A.4, §10.7).
 */
@RequestMapping("/api")
public interface ScenarioController {

    @PostMapping("/scenarios")
    ResponseEntity<ResponseData<ScenarioRow>> create(@Valid @RequestBody CreateScenarioRequest r);

    @PostMapping({"/scenarios/list", "/scenarios/filter"})
    ResponseEntity<ResponseData<PageableData<ScenarioRow>>> list(@Valid @RequestBody ScenarioFilter filter);

    @GetMapping("/scenarios/{id:\\d+}")
    ResponseEntity<ResponseData<ScenarioRow>> get(@PathVariable long id);

    @PutMapping("/scenarios/{id:\\d+}")
    ResponseEntity<ResponseData<ScenarioRow>> update(@PathVariable long id, @Valid @RequestBody UpdateScenarioRequest r);

    @PostMapping("/scenarios/{id:\\d+}/clone")
    ResponseEntity<ResponseData<ScenarioRow>> clone(@PathVariable long id, @Valid @RequestBody CloneScenarioRequest r);

    @PostMapping("/scenarios/validate")
    ResponseEntity<ResponseData<ScenarioValidationResult>> validate(@RequestBody ScenarioDefinition definition);

    @PostMapping("/scenarios/simulate")
    ResponseEntity<ResponseData<ScenarioSimulationResponse>> simulate(@RequestBody ScenarioSimulationRequest r);

    @PostMapping("/scenarios/{id:\\d+}/test-personas")
    ResponseEntity<ResponseData<List<PersonaTestResult>>> testPersonas(@PathVariable long id);
}
