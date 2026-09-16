package uz.murodjon.robotcallv2.agent.summary;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM-as-judge call-quality scoring. Bound from {@code voice-agent.quality.*}.
 *
 * <p>A second model reads each finished call's transcript and marks it against a rubric —
 * guardrail broken, wrong language, talked over the caller, wrong disposition. The verdict
 * goes to {@code voice.qa.score}/{@code voice.qa.flags} and the log, and nothing is written
 * to the customer's record: this measures the agent, not the debtor.
 *
 * @param enabled    when false no call is scored. Off by default — it is one more LLM call
 *                   per call, worth paying for around a change you want to watch rather
 *                   than as a standing cost
 * @param sampleRate share of finished calls to score, 0..1. Turn it down once a change has
 *                   settled and the scores stop moving
 * @param model      chat model that does the scoring
 * @param maxTokens  output cap for one verdict
 */
@ConfigurationProperties(prefix = "voice-agent.quality")
public record QualityProperties(
        boolean enabled,
        double sampleRate,
        String model,
        int maxTokens
) {

    public QualityProperties {
        if (sampleRate <= 0) {
            sampleRate = 1.0;
        }
        if (model == null || model.isBlank()) {
            model = "gemini-3.8-flash";
        }
        if (maxTokens <= 0) {
            maxTokens = 512;
        }
    }
}
