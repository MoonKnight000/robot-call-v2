package uz.murodjon.uysotvoice.aimodel.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.uysotvoice.aimodel.dto.AiModelConfig;
import uz.murodjon.uysotvoice.aimodel.dto.UpdateAiModelConfigRequest;
import uz.murodjon.uysotvoice.aimodel.service.AiModelConfigService;
import uz.murodjon.uysotvoice.shared.api.ResponseData;

@RestController
public class AiModelConfigControllerImpl implements AiModelConfigController {

    private final AiModelConfigService service;

    public AiModelConfigControllerImpl(AiModelConfigService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ResponseData<AiModelConfig>> get() {
        return ResponseEntity.ok(ResponseData.ok(service.find()));
    }

    @Override
    public ResponseEntity<ResponseData<AiModelConfig>> update(UpdateAiModelConfigRequest r) {
        return ResponseEntity.ok(ResponseData.ok(service.update(r)));
    }
}
