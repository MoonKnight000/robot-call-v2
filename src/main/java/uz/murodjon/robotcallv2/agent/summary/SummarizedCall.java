package uz.murodjon.robotcallv2.agent.summary;

import uz.murodjon.robotcallv2.agent.dialog.CallSummary;

/**
 * A call's summary together with what producing it took in tokens. The summary is an LLM
 * call of its own, made after the conversation, and is charged to the call on top of it.
 *
 * @param summary          the structured summary
 * @param promptTokens     prompt tokens, cached ones included — both requests when the model
 *                         had to be asked a second time
 * @param completionTokens tokens the model wrote, thinking included
 * @param cachedTokens     of {@code promptTokens}, how many the provider served cached
 * @param ownLlm           whether it ran on the company's own LLM, which the company pays its
 *                         provider for — then nothing is billed for it
 */
public record SummarizedCall(CallSummary summary, long promptTokens, long completionTokens, long cachedTokens,
                             boolean ownLlm) {

    /** Made on the platform's model. */
    public SummarizedCall(CallSummary summary, long promptTokens, long completionTokens, long cachedTokens) {
        this(summary, promptTokens, completionTokens, cachedTokens, false);
    }
}
