package uz.murodjon.robotcallv2.billing.domain.entity;

/**
 * What one call cost, in UZS, split the way an invoice line is read.
 *
 * <p>Every line is already rounded to whole UZS and {@code totalUzs} is their sum, so an
 * invoice adds up when a customer checks it with a calculator. Rounding the total
 * separately would leave it a som off its own lines often enough to generate support
 * tickets.
 *
 * @param llmUzs          tokens, prompt and completion, cached ones at the cached rate
 * @param sttUzs          recognition, on the call's duration
 * @param ttsUzs          synthesis, on the characters the agent spoke
 * @param telephonyUzs    the trunk, on the call's duration
 * @param platformFeeUzs  this platform's own per-minute charge; carries no markup
 * @param totalUzs        the sum of the lines above
 */
public record CallCostBreakdown(
        long llmUzs,
        long sttUzs,
        long ttsUzs,
        long telephonyUzs,
        long platformFeeUzs,
        long totalUzs
) {
    public static final CallCostBreakdown FREE = new CallCostBreakdown(0, 0, 0, 0, 0, 0);
}
