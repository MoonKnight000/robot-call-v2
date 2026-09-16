package uz.murodjon.robotcallv2.billing.domain.entity;

/**
 * The price list a call is charged against, in UZS.
 *
 * <p>A domain record rather than the configuration class that carries it, so that
 * {@code CallCostCalculator} — the one piece of billing a company will argue with — stays
 * a function of two records and can be tested by handing it numbers. The binding to
 * {@code config/billing.yml} lives in {@code BillingRateProperties}, which composes this.
 *
 * <p>Speech is priced on what the company can already see on its own call record — the
 * call's duration, the characters the agent spoke — rather than on what the platform
 * actually sent to the providers. What VAD gating and the TTS cache save is this
 * platform's margin, and it is measured separately as {@code
 * voice.stt.audio.seconds.skipped} and {@code voice.tts.chars.saved}.
 *
 * @param llm                     token prices
 * @param sttPerMinuteUzs         recognition, priced on the call's duration
 * @param ttsPer1kCharsUzs        synthesis, priced on the characters the agent spoke
 * @param realtimePerMinuteUzs    a speech-to-speech engine (Gemini Live, OpenAI Realtime),
 *                                priced on the minutes it ran. One number for recognition,
 *                                reasoning and speech together, because that is how the
 *                                vendor does the work — its audio tokens priced at the
 *                                text-LLM rate would bear no relation to what it costs
 * @param telephonyPerMinuteUzs   what a connected minute costs, one number for every
 *                                trunk: a company's trunks are priced the same by its
 *                                provider, and nobody has asked for a per-trunk table
 * @param platformFeePerMinuteUzs this platform's own charge, on top of what the call cost
 *                                to run; carries no markup, being a price rather than a
 *                                resold cost
 * @param markupBasisPoints       applied to the provider costs only; 2000 = 20%
 */
public record BillingRates(
        BillingLlmRates llm,
        long sttPerMinuteUzs,
        long ttsPer1kCharsUzs,
        long realtimePerMinuteUzs,
        long telephonyPerMinuteUzs,
        long platformFeePerMinuteUzs,
        int markupBasisPoints
) {
}
