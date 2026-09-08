package uz.murodjon.robotcallv2.billing.domain.entity;

import uz.murodjon.robotcallv2.billing.domain.enums.LedgerEntryType;

import java.time.Instant;

/**
 * One movement of money, written once and never changed.
 *
 * <p>{@code balanceBeforeUzs}/{@code balanceAfterUzs} are stored rather than derived
 * because the point of the ledger is to settle a disagreement about a balance by reading
 * it: a running sum can only say what the balance should be, these two say what it was.
 *
 * <p>{@code idempotencyKey} is unique per company, so a retried settlement or a
 * redelivered payment callback is refused by storage instead of charging twice. Callers
 * build it from what caused the entry — {@code "call-charge:4059"}, {@code "topup:PAY-8839"}.
 *
 * @param id               storage's, null before it is written
 * @param companyId        whose balance moved
 * @param entryType        why
 * @param amountUzs        signed; a reservation carries 0 because it moves the hold, not the balance
 * @param balanceBeforeUzs the balance this entry found
 * @param balanceAfterUzs  the balance it left
 * @param referenceType    what caused it, e.g. {@code "call_attempt"}
 * @param referenceId      that thing's id
 * @param idempotencyKey   what makes writing this entry twice a no-op
 * @param createdAt        storage's to stamp
 */
public record LedgerEntry(
        Long id,
        long companyId,
        LedgerEntryType entryType,
        long amountUzs,
        long balanceBeforeUzs,
        long balanceAfterUzs,
        String referenceType,
        String referenceId,
        String idempotencyKey,
        Instant createdAt
) {
    /** An entry to be written; the balances are the adapter's caller to supply, the id is storage's. */
    public static LedgerEntry of(long companyId, LedgerEntryType entryType, long amountUzs,
                                 long balanceBeforeUzs, long balanceAfterUzs,
                                 String referenceType, String referenceId, String idempotencyKey) {
        return new LedgerEntry(null, companyId, entryType, amountUzs, balanceBeforeUzs, balanceAfterUzs,
                referenceType, referenceId, idempotencyKey, null);
    }
}
