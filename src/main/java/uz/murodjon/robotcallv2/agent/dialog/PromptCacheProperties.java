package uz.murodjon.robotcallv2.agent.dialog;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * What a cascade call does to keep its LLM requests in the provider's prompt cache. Gemini
 * matches a request by its leading bytes — system prompt, then tools — so the cache holds only
 * while those stay the same and only after a request has put them there.
 * Bound from {@code voice-agent.dialog.prompt-cache.*}.
 *
 * @param warmup          send the call's prompt once while the greeting plays
 *                        ({@link LlmTurnClient#warmUp}), so the first turn is not its first request
 * @param warmupFastModel also warm the call's fast model when it differs from the main one: the
 *                        cache is per model, and a short first answer ("alo", "ha") is exactly the
 *                        turn the fast model takes
 * @param stableToolset   declare every tool of the call on every turn, so a stage change does not
 *                        move the cached prefix; what a stage may use is then said in the turn's
 *                        annex and enforced when a call comes back ({@link TurnTools#run}). Off,
 *                        each stage declares only its own tools ({@code dialog.state-scoped-tools})
 */
@ConfigurationProperties(prefix = "voice-agent.dialog.prompt-cache")
public record PromptCacheProperties(boolean warmup, boolean warmupFastModel, boolean stableToolset) {
}
