package uz.murodjon.robotcallv2.billing.domain.service;

/**
 * How much of a trial credit is still unspent when the trial ends.
 *
 * <p>Trial money counts as spent first: a company that was given 20 000, paid in 100 000 and
 * spent 30 000 has used up its trial and keeps all of its own 90 000. So the unspent trial is
 * whatever the balance holds beyond what the company paid in itself, never more than the
 * credit and never less than zero.
 *
 * <p>It is also capped by what is not held for running calls — taking back money a running
 * call has reserved would let that call's charge push the balance below zero.
 */
public final class TrialCreditRules {

    private TrialCreditRules() {
    }

    /**
     * @param creditUzs     the trial credit that was given
     * @param balanceUzs    the balance now
     * @param reservedUzs   held for calls still running
     * @param paidInSinceUzs what the company topped up itself since the trial was granted
     * @return what to take back, 0 or more
     */
    public static long findUnspentUzs(long creditUzs, long balanceUzs, long reservedUzs, long paidInSinceUzs) {
        long unspent = Math.min(creditUzs, balanceUzs - paidInSinceUzs);
        long spendable = balanceUzs - reservedUzs;
        return Math.max(0, Math.min(unspent, spendable));
    }
}
