package uz.murodjon.robotcallv2.billing.application.port.output;

import uz.murodjon.robotcallv2.billing.domain.entity.DailySpend;
import uz.murodjon.robotcallv2.billing.domain.entity.ExtraCharge;
import uz.murodjon.robotcallv2.billing.domain.entity.LedgerEntry;
import uz.murodjon.robotcallv2.billing.domain.entity.MonthlySpend;
import uz.murodjon.robotcallv2.billing.domain.enums.LedgerEntryType;

import java.time.Instant;
import java.util.List;

/**
 * The append-only money ledger.
 *
 * <p>There is no update and no delete on purpose: a balance that can be edited in place
 * cannot be audited, and a correction is another entry rather than a rewrite of the one
 * that was wrong.
 */
public interface BillingLedgerRepository {

    /**
     * Writes one entry, unless an entry with the same key already exists for this company.
     *
     * @return true when this call wrote it, false when it had already been written — a
     *         retried settlement or a redelivered payment callback lands here, and the
     *         caller uses the answer to decide whether to move the balance
     */
    boolean append(LedgerEntry entry);

    /** The signed sum of one company's entries of one type written at or after {@code since}; 0 when none. */
    long sumAmountByCompanyIdAndEntryTypeSince(long companyId, LedgerEntryType entryType, Instant since);

    /*
     * "Extra" charges: model tokens charged outside a call's billing row — chat, copilot,
     * questions about a call, and a call's re-analysis. They reach the balance only through
     * the ledger, so a spend total built from call rows alone would leave them out.
     */

    /** Extra charges in {@code [from, to)}, summed per source. */
    List<ExtraCharge> findExtraCharges(long companyId, Instant from, Instant to);

    /** Extra charges in {@code [from, to)} per day, oldest first; days with none are absent. */
    List<DailySpend> findDailyExtraSpend(long companyId, Instant from, Instant to);

    /** Extra charges per month, newest first, at most {@code limit} months; {@code durationSec} is 0. */
    List<MonthlySpend> findMonthlyExtraSpend(long companyId, int limit);
}
