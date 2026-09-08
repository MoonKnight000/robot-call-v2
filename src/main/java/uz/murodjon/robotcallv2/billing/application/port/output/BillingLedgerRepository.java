package uz.murodjon.robotcallv2.billing.application.port.output;

import uz.murodjon.robotcallv2.billing.domain.entity.LedgerEntry;

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
}
