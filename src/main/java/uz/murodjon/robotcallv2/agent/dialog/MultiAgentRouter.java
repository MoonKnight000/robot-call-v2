package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;

/**
 * Hands a live call to another scenario mid-call (lead qualification to order
 * confirmation, say), keeping the transcript, the facts and the caller context.
 *
 * <p>Only a cascade dialog can switch: its prompt is rebuilt from the session every turn,
 * so the next turn already speaks the new scenario. A realtime engine was configured with
 * its scenario when its session opened and keeps it for the whole call.
 */
@Service
public class MultiAgentRouter {

    private static final Logger log = LoggerFactory.getLogger(MultiAgentRouter.class);

    private final DialogEngine dialogEngine;

    public MultiAgentRouter(DialogEngine dialogEngine) {
        this.dialogEngine = dialogEngine;
    }

    /**
     * Continue {@code channelId} under {@code target}. The caller has already checked that
     * the scenario belongs to the company of the call, is active and has stages.
     *
     * @return the stage the call continues from, which is the first stage of the target
     */
    public String routeToScenario(String channelId, ScenarioDefinition target) {
        DialogSession session = dialogEngine.findSession(channelId);
        if (session == null || session.isEnded()) {
            throw new ConflictException(ErrorCode.CALL_SCENARIO_SWITCH_UNAVAILABLE, channelId);
        }
        String from = session.state();
        session.switchScenario(target);
        log.info("[{}] scenario handoff: stage {} -> {}", channelId, from, session.state());
        return session.state();
    }
}
