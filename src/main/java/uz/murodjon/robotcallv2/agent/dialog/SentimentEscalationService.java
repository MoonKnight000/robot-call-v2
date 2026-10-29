package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;

/**
 * Watches the caller's sentiment and auto-escalates to an operator when frustration
 * persists (RETELL_AI_GAP_ANALYSIS.md #1).
 *
 * <p>When the agent has {@code autoEscalateOnFrustration=true}, this service counts
 * consecutive FRUSTRATED turns. Once the count reaches the configured threshold
 * (default 2), it triggers a transfer to the operator.
 */
@Component
public class SentimentEscalationService {

    private static final Logger log = LoggerFactory.getLogger(SentimentEscalationService.class);

    /**
     * Called after each turn when the caller's mood changes. Returns true if the call
     * should be transferred to an operator.
     *
     * @param channelId the call channel identifier
     * @param agent the agent configuration; null disables escalation
     * @param sentiment the current caller sentiment
     * @param frustrationCount the number of consecutive FRUSTRATED turns so far
     * @return true if the transfer should be triggered now
     */
    public boolean onSentimentChanged(String channelId, AiAgent agent, CustomerSentiment sentiment,
                                      int frustrationCount) {
        if (agent == null || agent.callBehaviour() == null) {
            return false;
        }
        Boolean enabled = agent.callBehaviour().autoEscalateOnFrustration();
        if (enabled == null || !enabled) {
            return false;
        }
        if (!agent.callBehaviour().transferEnabled()) {
            log.debug("[{}] auto-escalation enabled but no transfer target configured", channelId);
            return false;
        }
        if (sentiment != CustomerSentiment.FRUSTRATED) {
            // Sentiment improved — reset the counter
            if (frustrationCount > 0) {
                log.info("[{}] frustration cleared after {} turn(s)", channelId, frustrationCount);
            }
            return false;
        }
        int threshold = agent.callBehaviour().frustrationThreshold() != null
                ? agent.callBehaviour().frustrationThreshold() : 2;
        if (frustrationCount >= threshold) {
            log.info("[{}] auto-escalating after {} consecutive frustrated turns (threshold={})",
                    channelId, frustrationCount, threshold);
            return true;
        }
        log.info("[{}] frustration count {}/{}", channelId, frustrationCount, threshold);
        return false;
    }
}
