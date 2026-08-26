package uz.murodjon.uysotvoice.engine.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import uz.murodjon.uysotvoice.engine.domain.EffectiveEngineConfig;
import uz.murodjon.uysotvoice.engine.domain.EngineConfig;
import uz.murodjon.uysotvoice.engine.dto.EngineOptions;
import uz.murodjon.uysotvoice.engine.dto.UpdateEngineConfigRequest;
import uz.murodjon.uysotvoice.shared.api.ResponseData;

/**
 * Which speech engine a company's calls run on (§11 settings) — scoped to the caller's
 * own company via {@code CurrentCompany} (JWT), not a path id. ADMIN-only
 * ({@code SecurityConfig}).
 */
@RequestMapping("/api/settings/engine")
public interface EngineConfigController {

    /**
     * The company's own choice. {@code null} data means it has never chosen and every
     * call runs on the process defaults — never {@code 404}.
     */
    @GetMapping
    ResponseEntity<ResponseData<EngineConfig>> get();

    /**
     * The same choice merged over the process defaults — which engines a call placed now
     * would actually run on. {@link #get} answers what the company chose, which is not the
     * same question: it says {@code null} for "chose nothing" without saying what speaks
     * instead, so a settings screen cannot name the provider in effect and the campaign
     * voice picker cannot tell which catalog voices this company can actually use.
     */
    @GetMapping("/effective")
    ResponseEntity<ResponseData<EffectiveEngineConfig>> getEffective();

    /** The engine ids this build accepts — populate the settings screen's selects from here. */
    @GetMapping("/options")
    ResponseEntity<ResponseData<EngineOptions>> options();

    /** Full replace; a field left out clears that choice back to the process default. */
    @PutMapping
    ResponseEntity<ResponseData<EngineConfig>> update(@Valid @RequestBody UpdateEngineConfigRequest request);
}
