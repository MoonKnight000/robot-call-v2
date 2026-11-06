package uz.murodjon.robotcallv2.billing.domain.entity;

/**
 * Model tokens charged outside a call's own billing row, summed per source over a period:
 * chat, copilot, questions about a call (AI_CHARGE ledger entries) and call re-analysis.
 * Only the money is known for these — the ledger keeps no token counts.
 *
 * @param source what the ledger names as the cause: the entry's {@code reference_type},
 *               or {@code reanalysis} for a re-analysis charge
 * @param uzs    what was taken off the balance
 * @param count  how many charges
 */
public record ExtraCharge(String source, long uzs, long count) {
}
