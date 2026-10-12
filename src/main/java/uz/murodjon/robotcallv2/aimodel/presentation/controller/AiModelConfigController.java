package uz.murodjon.robotcallv2.aimodel.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.aimodel.application.dto.UpdateAiModelConfigRequest;
import uz.murodjon.robotcallv2.aimodel.application.dto.UpdateOwnLlmRequest;
import uz.murodjon.robotcallv2.aimodel.domain.entity.AiModelConfig;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * Per-company AI model overrides (§11 settings).
 */
@RequestMapping("/api/settings/ai-model")
public interface AiModelConfigController {

    @PreAuthorize("hasAuthority('AI_MODEL_READ')")
    @GetMapping
    ResponseEntity<ResponseData<AiModelConfig>> get(@CurrentCompanyId long companyId);

    @PreAuthorize("hasAuthority('AI_MODEL_EDIT')")
    @PutMapping
    ResponseEntity<ResponseData<AiModelConfig>> update(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody UpdateAiModelConfigRequest request);

    /**
     * Moves the company's cascade calls and their summaries onto its own OpenAI-compatible LLM
     * (docs/api/settings.md). The API key is named, not sent: it lives in the company's secrets.
     * The platform's model is never used as a fallback while this is set.
     */
    @PreAuthorize("hasAuthority('AI_MODEL_EDIT')")
    @PutMapping("/own-llm")
    ResponseEntity<ResponseData<AiModelConfig>> updateOwnLlm(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody UpdateOwnLlmRequest request);

    /** Back to the platform's model. */
    @PreAuthorize("hasAuthority('AI_MODEL_EDIT')")
    @DeleteMapping("/own-llm")
    ResponseEntity<ResponseData<AiModelConfig>> deleteOwnLlm(@CurrentCompanyId long companyId);
}
