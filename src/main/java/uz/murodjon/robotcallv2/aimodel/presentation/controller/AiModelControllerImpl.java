package uz.murodjon.robotcallv2.aimodel.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.robotcallv2.aiagent.domain.enums.PipelineMode;
import uz.murodjon.robotcallv2.aimodel.application.port.input.AiModelUseCase;
import uz.murodjon.robotcallv2.aimodel.domain.entity.AiModel;
import uz.murodjon.robotcallv2.aimodel.domain.enums.AiModelKind;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

@RestController
public class AiModelControllerImpl implements AiModelController {

    private final AiModelUseCase aiModelUseCase;

    public AiModelControllerImpl(AiModelUseCase aiModelUseCase) {
        this.aiModelUseCase = aiModelUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<List<AiModel>>> models(AiModelKind kind, PipelineMode mode) {
        return ResponseEntity.ok(ResponseData.ok(aiModelUseCase.findSelectableByKindAndMode(kind, mode)));
    }
}
