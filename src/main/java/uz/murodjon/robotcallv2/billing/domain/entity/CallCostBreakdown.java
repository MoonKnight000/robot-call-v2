package uz.murodjon.robotcallv2.billing.domain.entity;

import java.util.List;

/**
 * What one call cost, in UZS, split the way an invoice line is read.
 *
 * <p>Every line is already rounded to whole UZS and {@code totalUzs} is their sum, so an
 * invoice adds up when a customer checks it with a calculator. Rounding the total
 * separately would leave it a som off its own lines often enough to generate support
 * tickets. The same holds one level down: {@code llmUzs} is the sum of its three token lines.
 *
 * @param llmUzs           tokens: the sum of the three lines below
 * @param llmPromptUzs     prompt (input) tokens billed at the full rate
 * @param llmCompletionUzs completion (output) tokens
 * @param llmCachedUzs     prompt tokens the provider served from its cache, at the cached rate
 * @param sttUzs           recognition, on the call's duration
 * @param ttsUzs           synthesis, on the characters the agent spoke
 * @param realtimeUzs      a speech-to-speech engine, on the minutes it ran — on such a call
 *                         this line stands in for the token, recognition and synthesis lines,
 *                         which are then zero
 * @param telephonyUzs     the trunk, on the call's duration
 * @param platformFeeUzs   this platform's own per-minute charge; carries no markup
 * @param totalUzs         the sum of llm, stt, tts, realtime, telephony and the platform fee
 * @param llmByModel       the LLM line per model, each at its own price; the three lines above add
 *                         up over it. Empty when nothing was priced per model (a realtime call, or a
 *                         breakdown read back from storage — the lines live in llm_usage). Never null
 */
public record CallCostBreakdown(
        long llmUzs,
        long llmPromptUzs,
        long llmCompletionUzs,
        long llmCachedUzs,
        long sttUzs,
        long ttsUzs,
        long realtimeUzs,
        long telephonyUzs,
        long platformFeeUzs,
        long totalUzs,
        List<ModelLlmCost> llmByModel
) {
    public static final CallCostBreakdown FREE = new CallCostBreakdown(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of());

    public CallCostBreakdown {
        llmByModel = llmByModel == null ? List.of() : List.copyOf(llmByModel);
    }
}
