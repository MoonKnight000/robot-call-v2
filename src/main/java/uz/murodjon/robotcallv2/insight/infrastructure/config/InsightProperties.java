package uz.murodjon.robotcallv2.insight.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The daily insight job (UNIVERSAL-OPERATOR-PLAN D.3). Bound from {@code voice-agent.insight.*}.
 *
 * @param enabled     when false nothing is proposed. The job reads what the quality judge wrote,
 *                    so it is worth nothing on a deployment with the judge turned off
 * @param windowHours how far back one run looks; a day, overlapping the last run on purpose,
 *                    because a finding that is still true tomorrow is still worth proposing
 * @param minCalls    an agent below this many calls in the window is skipped — three bad calls
 *                    out of four is not a pattern, it is a Tuesday
 * @param maxPerAgent proposals kept from one run of one agent. An inbox nobody can get to the
 *                    bottom of is an inbox nobody opens
 * @param model       the chat model that does the clustering
 * @param maxTokens   output cap for one agent's proposals
 */
@ConfigurationProperties(prefix = "voice-agent.insight")
public record InsightProperties(
        boolean enabled,
        int windowHours,
        int minCalls,
        int maxPerAgent,
        String model,
        int maxTokens
) {

    public InsightProperties {
        if (windowHours <= 0) {
            windowHours = 24;
        }
        if (minCalls <= 0) {
            minCalls = 10;
        }
        if (maxPerAgent <= 0) {
            maxPerAgent = 5;
        }
        if (model == null || model.isBlank()) {
            model = "gemini-3.8-flash";
        }
        if (maxTokens <= 0) {
            maxTokens = 2048;
        }
    }
}
