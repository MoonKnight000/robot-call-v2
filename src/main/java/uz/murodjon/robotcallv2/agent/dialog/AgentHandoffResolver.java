package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.exception.AppException;

/** Where an AGENT_TRANSFER stage sends a call: the published script of another of the company's agents. */
@Component
public class AgentHandoffResolver {

    private static final Logger log = LoggerFactory.getLogger(AgentHandoffResolver.class);

    private final AiAgentUseCase aiAgentUseCase;

    public AgentHandoffResolver(AiAgentUseCase aiAgentUseCase) {
        this.aiAgentUseCase = aiAgentUseCase;
    }

    /**
     * The published scenario of {@code agentId}, an agent of {@code companyId}, or null when it
     * is not one — the stage then falls back to its own transition.
     */
    public ScenarioDefinition findPublishedScenario(long companyId, long agentId) {
        try {
            AiAgent target = aiAgentUseCase.requirePublishedAgent(companyId, agentId);
            return aiAgentUseCase.resolveScenario(companyId, target).definition();
        } catch (AppException e) {
            log.warn("Agent handoff to {} of company {} refused: {}", agentId, companyId, e.getMessage());
            return null;
        }
    }
}
