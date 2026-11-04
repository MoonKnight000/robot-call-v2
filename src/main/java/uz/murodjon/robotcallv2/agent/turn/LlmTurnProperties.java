package uz.murodjon.robotcallv2.agent.turn;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * End-of-turn detection by a language model, after TEN Turn Detection ({@link LlmTurnDetector}).
 * Bound from {@code voice-agent.llm-turn.*}. Works through the speech gate, so it needs
 * {@code stt.vad-gating.enabled}.
 *
 * @param enabled          master switch; off, nothing is asked
 * @param mode             {@link LlmTurnMode#TEN} — TEN's own model, or {@link LlmTurnMode#PROMPT} —
 *                         the agent's fast model with an instruction
 * @param baseUrl          OpenAI-compatible base URL, without {@code /chat/completions}: the vLLM
 *                         server for TEN, e.g. Gemini's {@code .../v1beta/openai} for PROMPT
 * @param apiKey           bearer key for that endpoint
 * @param model            model id; blank in PROMPT mode means the agent's {@code fastLlmModel},
 *                         else {@code dialog.fast-model}
 * @param maxTokens        answer length: 1 for TEN's single special token; a few for a general
 *                         model, whose words may take more than one token
 * @param reasoningEffort  sent as {@code reasoning_effort} when set — a thinking model may spend
 *                         {@code maxTokens} on thought and answer nothing
 * @param timeoutMs        how long one verdict may take (TEN: 5000)
 * @param unfinishedWaitMs silence an "unfinished" utterance is given on top of the hangover before
 *                         it closes anyway — TEN's {@code force_threshold_ms}, but much shorter: TEN's
 *                         5000 sits on a VAD with no hangover, and on top of our 1200 ms post-roll it
 *                         left a caller who had finished ("… aha rahmat") 6 s in silence until they
 *                         said "allo"
 * @param waitHoldMs       silence a "wait" ("bir daqiqa", "подождите") is given on top of the
 *                         hangover before the agent speaks again; TEN never replies to one, here
 *                         the hold has an end
 */
@ConfigurationProperties(prefix = "voice-agent.llm-turn")
public record LlmTurnProperties(
        boolean enabled,
        LlmTurnMode mode,
        String baseUrl,
        String apiKey,
        String model,
        int maxTokens,
        String reasoningEffort,
        int timeoutMs,
        int unfinishedWaitMs,
        int waitHoldMs
) {
}
