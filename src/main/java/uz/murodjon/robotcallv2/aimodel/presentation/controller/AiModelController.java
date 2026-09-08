package uz.murodjon.robotcallv2.aimodel.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import uz.murodjon.robotcallv2.aiagent.domain.enums.PipelineMode;
import uz.murodjon.robotcallv2.aimodel.domain.entity.AiModel;
import uz.murodjon.robotcallv2.aimodel.domain.enums.AiModelKind;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

/**
 * The models an agent can be saved with ({@code GET /api/ai-models}) — the catalog the
 * model picker is filled from.
 *
 * <p>Narrowed by {@code kind} — an agent has three model fields and no id means anything in
 * more than one of them — and by the agent's pipeline, not by the company: since the speech
 * engine became a per-agent setting, one company's cascade agent and realtime agent must be
 * offered different families of model ids. Omitting {@code kind} answers {@code LLM}, which is
 * what the catalog held before the speech models joined it; omitting {@code mode} returns both
 * pipelines.
 */
@RequestMapping("/api/ai-models")
public interface AiModelController {

    @GetMapping
    ResponseEntity<ResponseData<List<AiModel>>> models(@RequestParam(defaultValue = "LLM") AiModelKind kind,
                                                      @RequestParam(required = false) PipelineMode mode);
}
