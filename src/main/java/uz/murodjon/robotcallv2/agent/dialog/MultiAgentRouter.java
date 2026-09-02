package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.scenario.application.port.output.ScenarioRepository;
import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

/**
 * Routes an ongoing live call session to a specialized sub-agent or another scenario mid-call
 * while preserving call context and transcript history (Multi-Agent Swarm pattern).
 */
@Service
public class MultiAgentRouter {

    private static final Logger log = LoggerFactory.getLogger(MultiAgentRouter.class);

    private final ScenarioRepository scenarioRepo;
    private final DialogEngine dialogEngine;

    public MultiAgentRouter(ScenarioRepository scenarioRepo, DialogEngine dialogEngine) {
        this.scenarioRepo = scenarioRepo;
        this.dialogEngine = dialogEngine;
    }

    /**
     * Seamlessly hands off an active call to a new scenario (e.g. from lead-qualification to order-confirmation).
     */
    public boolean routeToScenario(String channelId, String targetScenarioKey) {
        DialogSession session = dialogEngine.findSession(channelId);
        if (session == null || session.isEnded()) {
            throw new NotFoundException(ErrorCode.CALL_NOT_FOUND, channelId);
        }

        Scenario targetScenario = scenarioRepo.findActiveByKey(targetScenarioKey);
        if (targetScenario == null) {
            throw new NotFoundException(ErrorCode.SCENARIO_NOT_FOUND, targetScenarioKey);
        }

        log.info("[{}] multi-agent handoff from scenario '{}' to '{}'",
                channelId, session.scenario().rolePrompt(), targetScenario.name());

        // Context, transcript and facts remain intact while bound scenario stage is updated
        if (targetScenario.definition() != null && targetScenario.definition().stages() != null && !targetScenario.definition().stages().isEmpty()) {
            session.setState(targetScenario.definition().stages().get(0).id());
        }
        return true;
    }
}
