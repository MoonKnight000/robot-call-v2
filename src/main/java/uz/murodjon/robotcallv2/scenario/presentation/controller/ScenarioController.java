package uz.murodjon.robotcallv2.scenario.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.scenario.application.dto.*;
import uz.murodjon.robotcallv2.scenario.domain.entity.PersonaTestResult;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioFilter;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioValidationResult;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
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
    ResponseEntity<ResponseData<ScenarioRow>> create(@CurrentCompanyId long companyId,
            @Valid @RequestBody CreateScenarioRequest request);

    @PreAuthorize("hasAuthority('SCENARIO_READ')")
    @PostMapping({"/scenarios/list", "/scenarios/filter"})
    ResponseEntity<ResponseData<PageableData<ScenarioRow>>> list(@CurrentCompanyId long companyId,
            @Valid @RequestBody ScenarioFilter filter);

    @PreAuthorize("hasAuthority('SCENARIO_READ')")
    @GetMapping("/scenarios/{id:\\d+}")
    ResponseEntity<ResponseData<ScenarioRow>> get(@CurrentCompanyId long companyId, @PathVariable long id);

    @PreAuthorize("hasAuthority('SCENARIO_EDIT')")
    @PutMapping("/scenarios/{id:\\d+}")
    ResponseEntity<ResponseData<ScenarioRow>> update(@CurrentCompanyId long companyId, @PathVariable long id,
            @Valid @RequestBody UpdateScenarioRequest request);

    @PreAuthorize("hasAuthority('SCENARIO_EDIT')")
    @PostMapping("/scenarios/{id:\\d+}/clone")
    ResponseEntity<ResponseData<ScenarioRow>> clone(@CurrentCompanyId long companyId, @PathVariable long id,
            @Valid @RequestBody CloneScenarioRequest request);

    @PreAuthorize("hasAuthority('SCENARIO_EDIT')")
    @PostMapping("/scenarios/validate")
    ResponseEntity<ResponseData<ScenarioValidationResult>> validate(@RequestBody ScenarioDefinition definition);

    @PreAuthorize("hasAuthority('SCENARIO_EDIT')")
    @PostMapping("/scenarios/simulate")
    ResponseEntity<ResponseData<ScenarioSimulationResponse>> simulate(@CurrentCompanyId long companyId,
            @RequestBody ScenarioSimulationRequest request);

    @PreAuthorize("hasAuthority('SCENARIO_EDIT')")
    @PostMapping("/scenarios/{id:\\d+}/test-personas")
    ResponseEntity<ResponseData<List<PersonaTestResult>>> testPersonas(@CurrentCompanyId long companyId, @PathVariable long id);
}
