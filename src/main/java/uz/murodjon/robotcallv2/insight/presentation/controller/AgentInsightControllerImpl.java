package uz.murodjon.robotcallv2.insight.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.insight.application.dto.RejectInsightRequest;
import uz.murodjon.robotcallv2.insight.application.port.input.AgentInsightUseCase;
import uz.murodjon.robotcallv2.insight.domain.entity.AgentInsight;
import uz.murodjon.robotcallv2.insight.domain.entity.AgentInsightFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RestController
public class AgentInsightControllerImpl implements AgentInsightController {

    private final AgentInsightUseCase agentInsightUseCase;

    public AgentInsightControllerImpl(AgentInsightUseCase agentInsightUseCase) {
        this.agentInsightUseCase = agentInsightUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<AgentInsight>>> filter(long companyId,
                                                                          AgentInsightFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(agentInsightUseCase.filterInsights(companyId, filter)));
    }

    @Override
    public ResponseEntity<ResponseData<AgentInsight>> get(long companyId, long insightId) {
        return ResponseEntity.ok(ResponseData.ok(agentInsightUseCase.findInsight(companyId, insightId)));
    }

    @Override
    public ResponseEntity<ResponseData<AgentInsight>> accept(long companyId, long insightId) {
        return ResponseEntity.ok(ResponseData.ok(agentInsightUseCase.acceptInsight(companyId, insightId)));
    }

    @Override
    public ResponseEntity<ResponseData<AgentInsight>> reject(long companyId, long insightId,
                                                             RejectInsightRequest request) {
        return ResponseEntity.ok(ResponseData.ok(agentInsightUseCase.rejectInsight(companyId, insightId, request)));
    }
}
