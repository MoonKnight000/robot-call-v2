package uz.murodjon.uysotvoice.aimodel.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import uz.murodjon.uysotvoice.aimodel.domain.AiModelConfig;
import uz.murodjon.uysotvoice.aimodel.dto.UpdateAiModelConfigRequest;
import uz.murodjon.uysotvoice.shared.api.ResponseData;

/**
 * Per-company AI model overrides (§11 settings) — scoped to the caller's own company
 * via {@code CurrentCompany} (JWT), not a path id. ADMIN-only ({@code SecurityConfig}).
 */
@RequestMapping("/api/settings/ai-model")
public interface AiModelConfigController {

    /** {@code null} fields mean "using the process default" — never {@code 404}. */
    @GetMapping
    ResponseEntity<ResponseData<AiModelConfig>> get();

    @PutMapping
    ResponseEntity<ResponseData<AiModelConfig>> update(@Valid @RequestBody UpdateAiModelConfigRequest request);
}
