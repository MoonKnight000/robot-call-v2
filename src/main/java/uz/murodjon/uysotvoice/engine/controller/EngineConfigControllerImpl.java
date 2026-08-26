package uz.murodjon.uysotvoice.engine.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.uysotvoice.engine.domain.EffectiveEngineConfig;
import uz.murodjon.uysotvoice.engine.domain.EngineConfig;
import uz.murodjon.uysotvoice.engine.dto.EngineOptions;
import uz.murodjon.uysotvoice.engine.dto.UpdateEngineConfigRequest;
import uz.murodjon.uysotvoice.engine.service.EngineConfigService;
import uz.murodjon.uysotvoice.shared.api.ResponseData;

@RestController
public class EngineConfigControllerImpl implements EngineConfigController {

    private final EngineConfigService service;

    public EngineConfigControllerImpl(EngineConfigService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ResponseData<EngineConfig>> get() {
        return ResponseEntity.ok(ResponseData.ok(service.findForCurrentCompany()));
    }

    @Override
    public ResponseEntity<ResponseData<EffectiveEngineConfig>> getEffective() {
        return ResponseEntity.ok(ResponseData.ok(service.findEffectiveForCurrentCompany()));
    }

    @Override
    public ResponseEntity<ResponseData<EngineOptions>> options() {
        return ResponseEntity.ok(ResponseData.ok(service.findOptions()));
    }

    @Override
    public ResponseEntity<ResponseData<EngineConfig>> update(UpdateEngineConfigRequest request) {
        return ResponseEntity.ok(ResponseData.ok(service.updateForCurrentCompany(request)));
    }
}
