package uz.murodjon.robotcallv2.billing.domain.entity;

import java.util.ArrayList;
import java.util.List;

/**
 * What one finished call actually consumed, before anything is priced.
 *
 * <p>Kept apart from the money so a charge can be recomputed later from the same inputs:
 * a company disputing an invoice line is asking "how did you get this number", and the
 * answer is this record plus the rate version stamped on the charge.
 *
 * @param durationSec       connected seconds; what recognition and the trunk are priced on
 * @param promptTokens      prompt tokens across every turn, cached ones included
 * @param completionTokens  tokens the model wrote
 * @param cachedTokens      of {@code promptTokens}, how many the provider served cached
 * @param ttsChars          characters the agent asked a synthesizer for
 * @param realtimeSeconds   seconds the call ran on a speech-to-speech engine, which is
 *                          priced per minute in place of tokens, recognition and synthesis;
 *                          0 for a cascade call. The tokens are still recorded for such a
 *                          call, but only as information
 * @param extraSttSeconds   recognition beyond the call's own stream — the second one that
 *                          transcribes an operator's side (docs/OPERATOR-DIALER-ANALYTICS-PLAN.md
 *                          §2.3b). Priced at the recognition rate even on a realtime call,
 *                          since it never ran inside the engine
 * @param llmByModel        the tokens split by the model that spent them, each billed at its own
 *                          price; empty when not known — the totals above are then billed at the
 *                          common rate. Never null
 * @param realtimeModel     the model the speech-to-speech engine ran; its own per-minute price
 *                          applies when the price list has one. Null on a cascade call, or when not known
 */
public record CallUsage(
        int durationSec,
        long promptTokens,
        long completionTokens,
        long cachedTokens,
        long ttsChars,
        int realtimeSeconds,
        int extraSttSeconds,
        List<ModelTokens> llmByModel,
        String realtimeModel
) {
    public static final CallUsage NONE = new CallUsage(0, 0, 0, 0, 0, 0);

    public CallUsage {
        llmByModel = llmByModel == null ? List.of() : List.copyOf(llmByModel);
    }

    /** Without a realtime model — a cascade call, or one whose engine model is not known. */
    public CallUsage(int durationSec, long promptTokens, long completionTokens, long cachedTokens, long ttsChars,
                     int realtimeSeconds, int extraSttSeconds, List<ModelTokens> llmByModel) {
        this(durationSec, promptTokens, completionTokens, cachedTokens, ttsChars, realtimeSeconds, extraSttSeconds,
                llmByModel, null);
    }

    /** Without a per-model split — the tokens are billed at the common rate. */
    public CallUsage(int durationSec, long promptTokens, long completionTokens, long cachedTokens, long ttsChars,
                     int realtimeSeconds, int extraSttSeconds) {
        this(durationSec, promptTokens, completionTokens, cachedTokens, ttsChars, realtimeSeconds, extraSttSeconds,
                List.of());
    }

    /**
     * One model's tokens and nothing else — a summary, a chat turn, a copilot reply. The
     * totals are the model's own. With no model id there is nothing to split by: the tokens
     * go to the common rate.
     */
    public static CallUsage ofModel(ModelTokens tokens) {
        return new CallUsage(0, tokens.promptTokens(), tokens.completionTokens(), tokens.cachedTokens(), 0, 0, 0,
                tokens.model() == null || tokens.model().isBlank() ? List.of() : List.of(tokens));
    }

    /** A call's usage with no recognition beyond its own stream. */
    public CallUsage(int durationSec, long promptTokens, long completionTokens, long cachedTokens, long ttsChars,
                     int realtimeSeconds) {
        this(durationSec, promptTokens, completionTokens, cachedTokens, ttsChars, realtimeSeconds, 0);
    }

    /** A cascade call's usage — nothing ran on a realtime engine. */
    public CallUsage(int durationSec, long promptTokens, long completionTokens, long cachedTokens, long ttsChars) {
        this(durationSec, promptTokens, completionTokens, cachedTokens, ttsChars, 0);
    }

    /**
     * The same usage with the model's tokens left out: they ran on the company's own LLM,
     * which it pays its provider for (gap analysis 3.14). Recognition, synthesis and the line
     * are still the platform's.
     */
    public CallUsage withoutLlm() {
        return new CallUsage(durationSec, 0, 0, 0, ttsChars, realtimeSeconds, extraSttSeconds, List.of(),
                realtimeModel);
    }

    /** Prompt tokens billed at the full rate — the cached ones are priced separately. */
    public long billablePromptTokens() {
        return Math.max(0, promptTokens - cachedTokens);
    }

    /**
     * What to price, model by model: the per-model split when one was recorded. Tokens the
     * split does not account for — all of them when there is no split — go to one unknown
     * model (the common rate), so nothing is left unbilled.
     */
    public List<ModelTokens> tokensToPrice() {
        long prompt = 0;
        long completion = 0;
        long cached = 0;
        for (ModelTokens model : llmByModel) {
            prompt += model.promptTokens();
            completion += model.completionTokens();
            cached += model.cachedTokens();
        }
        long restPrompt = Math.max(0, promptTokens - prompt);
        long restCompletion = Math.max(0, completionTokens - completion);
        long restCached = Math.max(0, cachedTokens - cached);
        if (restPrompt == 0 && restCompletion == 0 && restCached == 0) {
            return llmByModel;
        }
        List<ModelTokens> all = new ArrayList<>(llmByModel);
        all.add(new ModelTokens(null, restPrompt, restCompletion, restCached));
        return all;
    }

    /** Whether the engine's per-minute price replaces the token, recognition and synthesis lines. */
    public boolean realtime() {
        return realtimeSeconds > 0;
    }
}
