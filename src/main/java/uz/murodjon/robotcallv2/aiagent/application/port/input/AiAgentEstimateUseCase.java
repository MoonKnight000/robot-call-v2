package uz.murodjon.robotcallv2.aiagent.application.port.input;

import uz.murodjon.robotcallv2.aiagent.application.dto.AiAgentEstimateResponse;

/** What a minute of an agent's talk costs, how fast it answers and how many tokens a turn takes. */
public interface AiAgentEstimateUseCase {

    /**
     * Measured on the agent's recent calls where there are any, otherwise estimated from its
     * prompt and the current rates; a {@code NotFoundException} for another company's agent.
     */
    AiAgentEstimateResponse findEstimate(long companyId, long id);
}
