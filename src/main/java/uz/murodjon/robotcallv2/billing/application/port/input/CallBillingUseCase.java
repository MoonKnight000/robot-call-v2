package uz.murodjon.robotcallv2.billing.application.port.input;

import uz.murodjon.robotcallv2.billing.domain.entity.CallUsage;

import java.time.Instant;

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
     * How many seconds of talk the company's balance still pays for, at the per-minute floor
     * (line, recognition or realtime engine, platform fee) — never under a minute, so a call
     * already up is closed with a goodbye rather than cut.
     *
     * @return {@code 0} when the balance is not enforced, meaning no limit from billing
     */
    int findAffordableCallSeconds(long companyId, boolean realtime);

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

    /**
     * Charges the post-call summary's tokens to a call that has already been settled.
     *
     * <p>Its own ledger entry ({@code summary-charge:<callAttemptId>}) rather than part of
     * {@link #settleCall}: the outbox can summarize a call long after it was settled, and a
     * realtime call prices its own tokens by the minute, so a summary folded into the
     * call's usage would go unpriced on exactly those calls. The tokens and their price are
     * added to the call's charge row, so the per-call breakdown and the monthly totals
     * include them.
     *
     * <p>Safe to call twice: the second finds the entry already written. Does nothing for a
     * call that has no settled charge.
     *
     * @param summaryUsage the summary's tokens; duration, speech and realtime seconds are 0
     */
    void chargeSummary(long companyId, long callAttemptId, CallUsage summaryUsage);

    /**
     * Charges an operator call's analysis to a call that has already been settled, as
     * {@link #chargeSummary} does the summary: the scorecard's tokens and the second
     * recognition stream on the operator's side ({@code extraSttSeconds}). Its own ledger
     * entry ({@code analysis-charge:<callAttemptId>}), so it is safe to call twice; does
     * nothing for a call that has no settled charge.
     */
    void chargeOperatorAnalysis(long companyId, long callAttemptId, CallUsage analysisUsage);

    /**
     * Charges one call's reanalysis (a {@code CallReanalysisJob}) on top of its settled charge,
     * under its own ledger entry ({@code reanalysis-charge:<jobId>:<callAttemptId>}): safe to
     * call twice, and a second job over the same call is charged again. The call's own charge
     * row is left as it was — the reanalysis is a separate service, not part of the call.
     *
     * @return what was taken off the balance; 0 when nothing was — no tokens, no settled charge,
     *         or already charged
     */
    long chargeReanalysis(long companyId, long callAttemptId, long jobId, CallUsage usage);

    /**
     * Charges the tokens one copilot reply used, under its own ledger entry
     * ({@code copilot-charge:<messageId>}), so a retry is charged once.
     *
     * @return what was taken off the balance; 0 when nothing was — no tokens, or already charged
     */
    long chargeCopilotReply(long companyId, long messageId, CallUsage usage);

    /** What {@code usage} costs at the current rates, in UZS. Charges nothing — for an estimate. */
    long findUsageCostUzs(CallUsage usage);

    /**
     * What the campaign's settled calls were charged since {@code since}, all of it when
     * {@code since} is null — read by the dialer's budget cap and the campaign report.
     */
    long sumSpentUzsByCampaign(long companyId, long campaignId, Instant since);
}
