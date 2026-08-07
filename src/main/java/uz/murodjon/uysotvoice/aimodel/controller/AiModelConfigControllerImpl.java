package uz.murodjon.uysotvoice.aimodel.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.uysotvoice.aimodel.domain.AiModelConfig;
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
        return ResponseEntity.ok(ResponseData.ok(service.findForCurrentCompany()));
    }

    @Override
    public ResponseEntity<ResponseData<AiModelConfig>> update(UpdateAiModelConfigRequest request) {
        return ResponseEntity.ok(ResponseData.ok(service.updateForCurrentCompany(request)));
    }
}
