package uz.murodjon.robotcallv2.scenario.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @PreAuthorize("hasAuthority('SCENARIO_EDIT')")
    @PostMapping("/scenarios")
    ResponseEntity<ResponseData<ScenarioRow>> create(@Valid @RequestBody CreateScenarioRequest r);

    @PreAuthorize("hasAuthority('SCENARIO_READ')")
    @PostMapping({"/scenarios/list", "/scenarios/filter"})
    ResponseEntity<ResponseData<PageableData<ScenarioRow>>> list(@Valid @RequestBody ScenarioFilter filter);

    @PreAuthorize("hasAuthority('SCENARIO_READ')")
    @GetMapping("/scenarios/{id:\\d+}")
    ResponseEntity<ResponseData<ScenarioRow>> get(@PathVariable long id);

    @PreAuthorize("hasAuthority('SCENARIO_EDIT')")
    @PutMapping("/scenarios/{id:\\d+}")
    ResponseEntity<ResponseData<ScenarioRow>> update(@PathVariable long id, @Valid @RequestBody UpdateScenarioRequest r);

    @PreAuthorize("hasAuthority('SCENARIO_EDIT')")
    @PostMapping("/scenarios/{id:\\d+}/clone")
    ResponseEntity<ResponseData<ScenarioRow>> clone(@PathVariable long id, @Valid @RequestBody CloneScenarioRequest r);

    @PreAuthorize("hasAuthority('SCENARIO_EDIT')")
    @PostMapping("/scenarios/validate")
    ResponseEntity<ResponseData<ScenarioValidationResult>> validate(@RequestBody ScenarioDefinition definition);

    @PreAuthorize("hasAuthority('SCENARIO_EDIT')")
    @PostMapping("/scenarios/simulate")
    ResponseEntity<ResponseData<ScenarioSimulationResponse>> simulate(@RequestBody ScenarioSimulationRequest r);

    @PreAuthorize("hasAuthority('SCENARIO_EDIT')")
    @PostMapping("/scenarios/{id:\\d+}/test-personas")
    ResponseEntity<ResponseData<List<PersonaTestResult>>> testPersonas(@PathVariable long id);
}
