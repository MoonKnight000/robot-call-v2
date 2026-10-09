package uz.murodjon.robotcallv2.aimodel.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.robotcallv2.aimodel.application.dto.UpdateAiModelConfigRequest;
import uz.murodjon.robotcallv2.aimodel.application.dto.UpdateOwnLlmRequest;
import uz.murodjon.robotcallv2.aimodel.application.port.input.AiModelConfigUseCase;
import uz.murodjon.robotcallv2.aimodel.domain.entity.AiModelConfig;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RestController
public class AiModelConfigControllerImpl implements AiModelConfigController {

    private final AiModelConfigUseCase aiModelConfigUseCase;

    public AiModelConfigControllerImpl(AiModelConfigUseCase aiModelConfigUseCase) {
        this.aiModelConfigUseCase = aiModelConfigUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<AiModelConfig>> get(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(aiModelConfigUseCase.findByCompanyId(companyId)));
    }

    @Override
    public ResponseEntity<ResponseData<AiModelConfig>> update(long companyId, UpdateAiModelConfigRequest request) {
        return ResponseEntity.ok(ResponseData.ok(aiModelConfigUseCase.updateByCompanyId(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<AiModelConfig>> updateOwnLlm(long companyId, UpdateOwnLlmRequest request) {
        return ResponseEntity.ok(ResponseData.ok(aiModelConfigUseCase.updateOwnLlm(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<AiModelConfig>> deleteOwnLlm(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(aiModelConfigUseCase.deleteOwnLlm(companyId)));
    }
}
