package uz.murodjon.robotcallv2.aiagent.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.aiagent.application.dto.AiAgentEstimateResponse;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentEstimateUseCase;
import uz.murodjon.robotcallv2.aiagent.application.port.output.AiAgentRepository;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentCallStats;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.service.AgentCostEstimator;
import uz.murodjon.robotcallv2.billing.application.port.input.CallBillingUseCase;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

/**
 * The agent studio's cost / latency / tokens strip. Each value is measured on the agent's
 * recent calls when they have it and estimated otherwise, independently: a fresh agent has
 * an estimated price and no latency, one whose calls are not charged yet still shows the
 * latency they had.
 */
@Service
public class AiAgentEstimateService implements AiAgentEstimateUseCase {

    /** Recent enough to follow a prompt or model change, large enough not to swing on one call. */
    static final int SAMPLE_CALLS = 50;

    private final AiAgentRepository repository;
    private final CallBillingUseCase callBillingUseCase;

    public AiAgentEstimateService(AiAgentRepository repository, CallBillingUseCase callBillingUseCase) {
        this.repository = repository;
        this.callBillingUseCase = callBillingUseCase;
    }

    @Override
    public AiAgentEstimateResponse findEstimate(long companyId, long id) {
        AiAgent agent = repository.findByCompanyIdAndId(companyId, id);
        if (agent == null) {
            throw new NotFoundException(ErrorCode.AI_AGENT_NOT_FOUND, id);
        }
        AgentCallStats stats = repository.findRecentCallStats(companyId, id, SAMPLE_CALLS);
        int promptTokens = AgentCostEstimator.findPromptTokens(agent);

        Long measuredCost = stats.costPerMinuteUzs();
        long costPerMinute = measuredCost != null
                ? measuredCost
                : callBillingUseCase.findUsageCostUzs(AgentCostEstimator.createOneMinuteUsage(agent, promptTokens));

        boolean measuredTokens = stats.hasTokens();
        int tokensFrom = measuredTokens ? stats.lowTokensPerTurn() : AgentCostEstimator.findLowTokensPerTurn(promptTokens);
        int tokensTo = measuredTokens ? stats.highTokensPerTurn() : AgentCostEstimator.findHighTokensPerTurn(promptTokens);

        return new AiAgentEstimateResponse(costPerMinute, measuredCost == null,
                stats.typicalLatencyMs(), stats.slowLatencyMs(),
                tokensFrom, tokensTo, !measuredTokens, stats.calls());
    }
}
