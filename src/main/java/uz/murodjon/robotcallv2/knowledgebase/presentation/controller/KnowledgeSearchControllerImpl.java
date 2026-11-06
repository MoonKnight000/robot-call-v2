package uz.murodjon.robotcallv2.knowledgebase.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeInsightsResponse;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeSearchRequest;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeSearchResponse;
import uz.murodjon.robotcallv2.knowledgebase.application.port.input.KnowledgeSearchUseCase;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RestController
public class KnowledgeSearchControllerImpl implements KnowledgeSearchController {

    private final KnowledgeSearchUseCase knowledgeSearchUseCase;

    public KnowledgeSearchControllerImpl(KnowledgeSearchUseCase knowledgeSearchUseCase) {
        this.knowledgeSearchUseCase = knowledgeSearchUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<KnowledgeSearchResponse>> search(long companyId, KnowledgeSearchRequest request) {
        return ResponseEntity.ok(ResponseData.ok(knowledgeSearchUseCase.searchKnowledge(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<KnowledgeInsightsResponse>> insights(long companyId, int days, Long agentId) {
        return ResponseEntity.ok(ResponseData.ok(knowledgeSearchUseCase.findInsights(companyId, days, agentId)));
    }
}
