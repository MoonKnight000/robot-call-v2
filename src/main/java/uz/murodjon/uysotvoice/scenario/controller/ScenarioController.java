package uz.murodjon.uysotvoice.scenario.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import uz.murodjon.uysotvoice.scenario.dto.CloneScenarioRequest;
import uz.murodjon.uysotvoice.scenario.dto.CreateScenarioRequest;
import uz.murodjon.uysotvoice.scenario.dto.ScenarioDefinition;
import uz.murodjon.uysotvoice.scenario.dto.ScenarioFilter;
import uz.murodjon.uysotvoice.scenario.dto.ScenarioRow;
import uz.murodjon.uysotvoice.scenario.dto.ScenarioValidationResult;
import uz.murodjon.uysotvoice.scenario.dto.UpdateScenarioRequest;
import uz.murodjon.uysotvoice.shared.api.PageableData;
import uz.murodjon.uysotvoice.shared.api.ResponseData;

/**
 * Scenario CRUD API (ROADMAP A.4, §10.7): create, list, edit (as a new version),
 * clone, and validate a scenario definition. Storage + validation only in this
 * pass — see {@link ScenarioDefinition} for what deliberately stays out of scope
 * (nothing here changes how a live call runs yet).
 */
@RequestMapping("/api")
public interface ScenarioController {

    @PostMapping("/scenarios")
    ResponseEntity<ResponseData<ScenarioRow>> create(@Valid @RequestBody CreateScenarioRequest r);

    @PostMapping("/scenarios/list")
    ResponseEntity<ResponseData<PageableData<ScenarioRow>>> list(@Valid @RequestBody ScenarioFilter filter);

    @GetMapping("/scenarios/{id}")
    ResponseEntity<ResponseData<ScenarioRow>> get(@PathVariable long id);

    @PutMapping("/scenarios/{id}")
    ResponseEntity<ResponseData<ScenarioRow>> update(@PathVariable long id, @Valid @RequestBody UpdateScenarioRequest r);

    @PostMapping("/scenarios/{id}/clone")
    ResponseEntity<ResponseData<ScenarioRow>> clone(@PathVariable long id, @Valid @RequestBody CloneScenarioRequest r);

    @PostMapping("/scenarios/validate")
    ResponseEntity<ResponseData<ScenarioValidationResult>> validate(@RequestBody ScenarioDefinition definition);
}
