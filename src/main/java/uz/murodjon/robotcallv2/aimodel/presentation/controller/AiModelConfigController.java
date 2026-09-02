package uz.murodjon.robotcallv2.aimodel.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import uz.murodjon.robotcallv2.aimodel.application.dto.UpdateAiModelConfigRequest;
import uz.murodjon.robotcallv2.aimodel.domain.entity.AiModelConfig;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * Per-company AI model overrides (§11 settings).
 */
@RequestMapping("/api/settings/ai-model")
public interface AiModelConfigController {

    @GetMapping
    ResponseEntity<ResponseData<AiModelConfig>> get();

    @PutMapping
    ResponseEntity<ResponseData<AiModelConfig>> update(@Valid @RequestBody UpdateAiModelConfigRequest request);
}
