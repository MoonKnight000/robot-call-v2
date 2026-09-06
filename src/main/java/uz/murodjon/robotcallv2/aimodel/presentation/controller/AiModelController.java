package uz.murodjon.robotcallv2.aimodel.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import uz.murodjon.robotcallv2.aimodel.domain.entity.AiModel;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

/**
 * The models an agent or the company settings can be saved with
 * ({@code GET /api/ai-models}) — the catalog the model picker is filled from, narrowed to
 * the company's engine.
 */
@RequestMapping("/api/ai-models")
public interface AiModelController {

    @GetMapping
    ResponseEntity<ResponseData<List<AiModel>>> models(@CurrentCompanyId long companyId);
}
