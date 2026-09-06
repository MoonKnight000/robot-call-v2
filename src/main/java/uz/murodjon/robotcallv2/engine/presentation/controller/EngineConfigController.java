package uz.murodjon.robotcallv2.engine.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import uz.murodjon.robotcallv2.engine.application.dto.EngineOptions;
import uz.murodjon.robotcallv2.engine.application.dto.UpdateEngineConfigRequest;
import uz.murodjon.robotcallv2.engine.domain.entity.EffectiveEngineConfig;
import uz.murodjon.robotcallv2.engine.domain.entity.EngineConfig;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * Which speech engine a company's calls run on (§11 settings).
 */
@RequestMapping("/api/settings/engine")
public interface EngineConfigController {

    @PreAuthorize("hasAuthority('ENGINE_READ')")
    @GetMapping
    ResponseEntity<ResponseData<EngineConfig>> get(@CurrentCompanyId long companyId);

    @PreAuthorize("hasAuthority('ENGINE_READ')")
    @GetMapping("/effective")
    ResponseEntity<ResponseData<EffectiveEngineConfig>> getEffective(@CurrentCompanyId long companyId);

    @PreAuthorize("hasAuthority('ENGINE_READ')")
    @GetMapping("/options")
    ResponseEntity<ResponseData<EngineOptions>> options();

    @PreAuthorize("hasAuthority('ENGINE_EDIT')")
    @PutMapping
    ResponseEntity<ResponseData<EngineConfig>> update(@CurrentCompanyId long companyId,
                                                      @Valid @RequestBody UpdateEngineConfigRequest request);
}
