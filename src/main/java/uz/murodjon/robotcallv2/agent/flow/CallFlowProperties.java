package uz.murodjon.robotcallv2.agent.flow;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Timings of the operator-queue wait in {@link CallFlowRunner}. Bound from
 * {@code voice-agent.flow.*}; no YAML key is needed — 0 (absent) means the value these had
 * as constants.
 *
 * @param connectingPollMs how often a caller whose operator is being rung checks the ring's
 *                         outcome. Short, because the caller hears the connect. 0 means 100
 * @param queuePollMs      how often a caller on hold with an operator, or waiting on the
 *                         queue's companion agent, checks for a change. 0 means 200
 */
@ConfigurationProperties(prefix = "voice-agent.flow")
public record CallFlowProperties(
        int connectingPollMs,
        int queuePollMs
) {

    public CallFlowProperties {
        if (connectingPollMs <= 0) {
            connectingPollMs = 100;
        }
        if (queuePollMs <= 0) {
            queuePollMs = 200;
        }
    }
}
