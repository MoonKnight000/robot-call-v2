package uz.murodjon.robotcallv2.billing.application.port.input;

import uz.murodjon.robotcallv2.billing.domain.entity.CallUsage;

/**
 * What the dialer and the call finalizer ask of billing.
 *
 * <p>Separate from {@code BillingUseCase}, which serves the billing screens: these
 * operations sit on the call path, are called by other features rather than by a
 * controller, and have to work with no user logged in.
 *
 * <p>The hold is keyed on the campaign target rather than the call, because it is taken
 * before the number is dialled and a call has no row of its own until Asterisk has given
 * it a channel.
 */
public interface CallBillingUseCase {

    /**
     * Whether this company can still pay for calls.
     *
     * <p>Asked once per campaign per dialer tick, before any number is claimed — the
     * cheap check that keeps a company at zero out of the queue.
     */
    boolean hasBalanceForCall(long companyId);

    /**
     * Holds money for a target about to be dialled.
     *
     * @return false when the company cannot cover the hold, in which case the number is
     *         not dialled at all
     */
    boolean reserveForCall(long companyId, long targetId);

    /** Gives the hold back untouched, for a number that was never actually dialled. */
    void releaseReservation(long companyId, long targetId);

    /**
     * Charges a finished call for what it consumed and gives back its hold.
     *
     * <p>Safe to call twice for the same call: the second one finds the ledger entry
     * already written and changes nothing. That matters because the finalizer runs from
     * an outbox that is allowed to deliver more than once.
     *
     * @param targetId the campaign target the hold was taken for, or null for a call that
     *                 never had one — an inbound call, or a manual test call. Either way
     *                 the call is charged; only the hold to give back differs.
     */
    void settleCall(long companyId, long callAttemptId, Long targetId, CallUsage usage);
}
