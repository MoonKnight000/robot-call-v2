package uz.murodjon.robotcallv2.insight.application.port.input;

import uz.murodjon.robotcallv2.insight.application.dto.RejectInsightRequest;
import uz.murodjon.robotcallv2.insight.domain.entity.AgentInsight;
import uz.murodjon.robotcallv2.insight.domain.entity.AgentInsightFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;

/** The improvement inbox (UNIVERSAL-OPERATOR-PLAN D.3): what the agents' own calls suggest changing. */
public interface AgentInsightUseCase {

    PageableData<AgentInsight> filterInsights(long companyId, AgentInsightFilter filter);

    AgentInsight findInsight(long companyId, long insightId);

    /**
     * Carries out what the proposal suggests, as far as it can be carried out on its own: a
     * prompt change goes into the draft and straight into an evaluation run that decides
     * whether it stays ({@code EVALUATING}), a test case or a pronunciation rule is created
     * outright, and a finding with nothing mechanical to do is simply acknowledged.
     */
    AgentInsight acceptInsight(long companyId, long insightId);

    AgentInsight rejectInsight(long companyId, long insightId, RejectInsightRequest request);
}
