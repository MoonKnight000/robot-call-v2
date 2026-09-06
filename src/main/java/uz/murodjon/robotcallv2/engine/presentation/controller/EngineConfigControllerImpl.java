package uz.murodjon.robotcallv2.engine.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.robotcallv2.engine.application.dto.EngineOptions;
import uz.murodjon.robotcallv2.engine.application.dto.UpdateEngineConfigRequest;
import uz.murodjon.robotcallv2.engine.application.port.input.EngineConfigUseCase;
import uz.murodjon.robotcallv2.engine.domain.entity.EffectiveEngineConfig;
import uz.murodjon.robotcallv2.engine.domain.entity.EngineConfig;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RestController
public class EngineConfigControllerImpl implements EngineConfigController {

    private final EngineConfigUseCase engineConfigUseCase;

    public EngineConfigControllerImpl(EngineConfigUseCase engineConfigUseCase) {
        this.engineConfigUseCase = engineConfigUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<EngineConfig>> get(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(engineConfigUseCase.findByCompanyId(companyId)));
    }

    @Override
    public ResponseEntity<ResponseData<EffectiveEngineConfig>> getEffective(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(engineConfigUseCase.findEffectiveByCompanyId(companyId)));
    }

    @Override
    public ResponseEntity<ResponseData<EngineOptions>> options() {
        return ResponseEntity.ok(ResponseData.ok(engineConfigUseCase.findOptions()));
    }

    @Override
    public ResponseEntity<ResponseData<EngineConfig>> update(long companyId, UpdateEngineConfigRequest request) {
        return ResponseEntity.ok(ResponseData.ok(engineConfigUseCase.updateByCompanyId(companyId, request)));
    }
}
