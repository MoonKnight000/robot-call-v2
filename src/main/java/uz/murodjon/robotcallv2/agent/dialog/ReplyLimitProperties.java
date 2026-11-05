package uz.murodjon.robotcallv2.agent.dialog;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The output cap of a cascade turn when neither the company's AI-model config nor the agent
 * sets one. A phone reply is one or two short sentences; the cap stops a monologue the prompt
 * rules did not. Bound from {@code voice-agent.dialog.reply-limit.*}.
 *
 * <p>A thinking model counts its reasoning against the same cap, and at 256 a turn that
 * thought for a moment came back empty ({@link TurnTools#buildOptions}). Such a model is
 * given {@code thinkingReserveTokens} on top, so only the spoken part is held to
 * {@code maxTokens}.
 *
 * @param maxTokens             tokens the spoken reply may take; 0 leaves the vendor default
 * @param thinkingReserveTokens added to {@code maxTokens} for a model that thinks before it answers
 */
@ConfigurationProperties(prefix = "voice-agent.dialog.reply-limit")
public record ReplyLimitProperties(int maxTokens, int thinkingReserveTokens) {

    /** The cap for one request, or null to leave the vendor default. */
    Integer findCap(boolean thinkingModel) {
        if (maxTokens <= 0) {
            return null;
        }
        return thinkingModel ? maxTokens + Math.max(thinkingReserveTokens, 0) : maxTokens;
    }
}
