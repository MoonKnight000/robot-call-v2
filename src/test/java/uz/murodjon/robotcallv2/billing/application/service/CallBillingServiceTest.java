package uz.murodjon.robotcallv2.billing.application.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.murodjon.robotcallv2.billing.application.port.output.BillingLedgerRepository;
import uz.murodjon.robotcallv2.billing.application.port.output.CallBillingRepository;
import uz.murodjon.robotcallv2.billing.application.port.output.CompanyBillingRepository;
import uz.murodjon.robotcallv2.billing.domain.entity.*;
import uz.murodjon.robotcallv2.billing.domain.enums.CallBillingStatus;
import uz.murodjon.robotcallv2.billing.infrastructure.config.BillingAutoRecharge;
import uz.murodjon.robotcallv2.billing.infrastructure.config.BillingRateProperties;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * The properties worth pinning down are the ones that cost a customer money if they slip:
 * a call must not be charged twice, a company that cannot pay must not be dialled, and
 * nothing here may throw into the call path.
 */
class CallBillingServiceTest {

    private static final long COMPANY = 1L;
    private static final long CALL = 4059L;
    private static final long TARGET = 77L;

    private final CompanyBillingRepository companyBilling = mock(CompanyBillingRepository.class);
    private final CallBillingRepository callBilling = mock(CallBillingRepository.class);
    private final BillingLedgerRepository ledger = mock(BillingLedgerRepository.class);

    private static BillingRateProperties rates(boolean enforce) {
        return new BillingRateProperties("test-v1", enforce, 5000,
                new BillingRates(new BillingLlmRates(1000, 10_000, 100), 60, 50, 300, 180, 120, 0),
                new BillingAutoRecharge(0, 0, "0 0 0 * * *"));
    }

    private CallBillingService service(boolean enforce) {
        return new CallBillingService(companyBilling, callBilling, ledger, rates(enforce));
    }

    @Test
    void chargesTheCallAndTakesItOffTheBalance() {
        when(callBilling.findByCallAttemptId(CALL)).thenReturn(Optional.empty());
        when(callBilling.findOpenHold(COMPANY, TARGET)).thenReturn(Optional.empty());
        when(companyBilling.findBalanceForUpdate(COMPANY)).thenReturn(Optional.of(100_000L));
        when(ledger.append(any())).thenReturn(true);

        // 60s = 1 minute: stt 60 + telephony 180 + platform 120, and 2000 chars = 100.
        service(true).settleCall(COMPANY, CALL, TARGET, new CallUsage(60, 0, 0, 0, 2000));

        verify(companyBilling).charge(COMPANY, 0L, 460L);
        verify(callBilling).save(any(CallBilling.class));
    }

    /** Enforced, a company cannot end up owing: what the balance does not cover is written off. */
    @Test
    void neverTakesTheBalanceBelowZeroWhenEnforced() {
        when(callBilling.findByCallAttemptId(CALL)).thenReturn(Optional.empty());
        when(callBilling.findOpenHold(COMPANY, TARGET)).thenReturn(Optional.empty());
        when(companyBilling.findBalanceForUpdate(COMPANY)).thenReturn(Optional.of(300L));
        when(ledger.append(any())).thenReturn(true);

        // The call costs 460 (see above); only 300 is there.
        service(true).settleCall(COMPANY, CALL, TARGET, new CallUsage(60, 0, 0, 0, 2000));

        verify(companyBilling).charge(COMPANY, 0L, 300L);
    }

    @Test
    void chargesInFullWhenEnforcementIsOff() {
        when(callBilling.findByCallAttemptId(CALL)).thenReturn(Optional.empty());
        when(callBilling.findOpenHold(COMPANY, TARGET)).thenReturn(Optional.empty());
        when(companyBilling.findBalanceForUpdate(COMPANY)).thenReturn(Optional.of(300L));
        when(ledger.append(any())).thenReturn(true);

        service(false).settleCall(COMPANY, CALL, TARGET, new CallUsage(60, 0, 0, 0, 2000));

        verify(companyBilling).charge(COMPANY, 0L, 460L);
    }

    @Test
    void budgetsTheCallLengthOnWhatTheBalanceStillPaysFor() {
        when(companyBilling.findByCompanyId(COMPANY)).thenReturn(Optional.of(
                new CompanyBilling(1L, COMPANY, "PRO", "Pro", 10_000, 5000, false, null, null, null)));

        // 10 000 less 5000 held, plus this call's own 5000 hold = 10 000 UZS to spend.
        // Cascade minute: telephony 180 + stt 60 + platform 120 = 360 → 10 000 * 60 / 360 = 1666 s.
        assertThat(service(true).findAffordableCallSeconds(COMPANY, false)).isEqualTo(1666);
        // Realtime minute: telephony 180 + engine 300 + platform 120 = 600 → 1000 s.
        assertThat(service(true).findAffordableCallSeconds(COMPANY, true)).isEqualTo(1000);
    }

    @Test
    void leavesAtLeastAMinuteForAGoodbye() {
        when(companyBilling.findByCompanyId(COMPANY)).thenReturn(Optional.of(
                new CompanyBilling(1L, COMPANY, "PRO", "Pro", 0, 10_000, false, null, null, null)));

        assertThat(service(true).findAffordableCallSeconds(COMPANY, false)).isEqualTo(60);
    }

    @Test
    void setsNoLengthLimitWhenEnforcementIsOff() {
        assertThat(service(false).findAffordableCallSeconds(COMPANY, false)).isZero();
        verify(companyBilling, never()).findByCompanyId(anyLong());
    }

    /**
     * The finalizer runs from an outbox that is allowed to deliver twice. The ledger's
     * unique key is what makes that harmless, so a second delivery must move no money.
     */
    @Test
    void doesNotChargeTheSameCallTwice() {
        when(callBilling.findByCallAttemptId(CALL)).thenReturn(Optional.empty());
        when(callBilling.findOpenHold(COMPANY, TARGET)).thenReturn(Optional.empty());
        when(companyBilling.findBalanceForUpdate(COMPANY)).thenReturn(Optional.of(100_000L));
        when(ledger.append(any())).thenReturn(false);

        service(true).settleCall(COMPANY, CALL, TARGET, new CallUsage(60, 0, 0, 0, 2000));

        verify(companyBilling, never()).charge(anyLong(), anyLong(), anyLong());
        verify(callBilling, never()).save(any());
    }

    @Test
    void refusesToDialWhenTheHoldCannotBeTaken() {
        when(companyBilling.reserve(COMPANY, 5000L)).thenReturn(false);

        assertThat(service(true).reserveForCall(COMPANY, TARGET)).isFalse();
        verify(callBilling, never()).save(any());
    }

    @Test
    void holdsAndOpensARowWhenTheCompanyCanPay() {
        when(companyBilling.reserve(COMPANY, 5000L)).thenReturn(true);
        when(callBilling.save(any())).thenAnswer(call -> call.getArgument(0));
        when(companyBilling.findBalanceForUpdate(COMPANY)).thenReturn(Optional.of(100_000L));

        assertThat(service(true).reserveForCall(COMPANY, TARGET)).isTrue();
        verify(callBilling).save(any(CallBilling.class));
    }

    /** With enforcement off nothing is held, so no balance is touched and no row opened. */
    @Test
    void takesNoHoldWhenEnforcementIsOff() {
        assertThat(service(false).reserveForCall(COMPANY, TARGET)).isTrue();

        verify(companyBilling, never()).reserve(anyLong(), anyLong());
        verify(callBilling, never()).save(any());
    }

    @Test
    void stopsDiallingWhenTheBalanceWillNotCoverAHold() {
        when(companyBilling.findByCompanyId(COMPANY)).thenReturn(Optional.of(
                new CompanyBilling(1L, COMPANY, "PRO", "Pro", 6000, 3000, false, null, null, null)));

        // 6000 balance less 3000 already held leaves 3000, under the 5000 hold.
        assertThat(service(true).hasBalanceForCall(COMPANY)).isFalse();
    }

    @Test
    void letsEveryCompanyDialWhenEnforcementIsOff() {
        assertThat(service(false).hasBalanceForCall(COMPANY)).isTrue();
        verify(companyBilling, never()).findByCompanyId(anyLong());
    }

    /** The post-call summary is its own ledger entry, priced at the LLM rate and added to the call's row. */
    @Test
    void chargesTheSummaryOnTopOfASettledCall() {
        when(callBilling.findByCallAttemptId(CALL)).thenReturn(Optional.of(settledCall()));
        when(companyBilling.findBalanceForUpdate(COMPANY)).thenReturn(Optional.of(100_000L));
        when(ledger.append(any())).thenReturn(true);
        CallUsage summary = new CallUsage(0, 100_000, 10_000, 0, 0);

        // 100k prompt @ 1000/M = 100, 10k completion @ 10 000/M = 100. No minutes, no speech.
        service(true).chargeSummary(COMPANY, CALL, summary);

        ArgumentCaptor<LedgerEntry> entry = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledger).append(entry.capture());
        assertThat(entry.getValue().idempotencyKey()).isEqualTo("summary-charge:" + CALL);
        assertThat(entry.getValue().amountUzs()).isEqualTo(-200L);
        verify(companyBilling).charge(COMPANY, 0L, 200L);
        verify(callBilling).addSummaryCharge(CALL, summary, 200L);
    }

    /** A summary is added to the call's charge; with no charge to add it to, nothing is billed. */
    @Test
    void doesNotChargeTheSummaryOfACallThatWasNeverSettled() {
        when(callBilling.findByCallAttemptId(CALL)).thenReturn(Optional.empty());

        service(true).chargeSummary(COMPANY, CALL, new CallUsage(0, 100_000, 10_000, 0, 0));

        verify(ledger, never()).append(any());
        verify(companyBilling, never()).charge(anyLong(), anyLong(), anyLong());
    }

    /** The outbox may summarize a call again; the summary's key already written moves no money. */
    @Test
    void doesNotChargeTheSameSummaryTwice() {
        when(callBilling.findByCallAttemptId(CALL)).thenReturn(Optional.of(settledCall()));
        when(companyBilling.findBalanceForUpdate(COMPANY)).thenReturn(Optional.of(100_000L));
        when(ledger.append(any())).thenReturn(false);

        service(true).chargeSummary(COMPANY, CALL, new CallUsage(0, 100_000, 10_000, 0, 0));

        verify(companyBilling, never()).charge(anyLong(), anyLong(), anyLong());
        verify(callBilling, never()).addSummaryCharge(anyLong(), any(), anyLong());
    }

    /** An operator call's analysis: the scorecard's tokens at the LLM rate, and the extra recognition at the STT rate. */
    @Test
    void chargesTheOperatorAnalysisOnTopOfASettledCall() {
        when(callBilling.findByCallAttemptId(CALL)).thenReturn(Optional.of(settledCall()));
        when(companyBilling.findBalanceForUpdate(COMPANY)).thenReturn(Optional.of(100_000L));
        when(ledger.append(any())).thenReturn(true);
        CallUsage analysis = new CallUsage(0, 100_000, 10_000, 0, 0, 0, 120);

        // Tokens 100 + 100, and 120s of STT = 2 minutes @ 60.
        service(true).chargeOperatorAnalysis(COMPANY, CALL, analysis);

        ArgumentCaptor<LedgerEntry> entry = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledger).append(entry.capture());
        assertThat(entry.getValue().idempotencyKey()).isEqualTo("analysis-charge:" + CALL);
        assertThat(entry.getValue().amountUzs()).isEqualTo(-320L);
        verify(companyBilling).charge(COMPANY, 0L, 320L);
        verify(callBilling).addOperatorAnalysisCharge(eq(CALL), eq(analysis), any());
    }

    private static CallBilling settledCall() {
        return new CallBilling(1L, CALL, TARGET, COMPANY, CallBillingStatus.SETTLED, "test-v1", 0,
                CallUsage.NONE, CallCostBreakdown.FREE, null, null);
    }

    /**
     * A failure reaches the caller so the transaction rolls back whole — swallowed inside
     * it, a ledger row could commit without its balance change. That a billing failure
     * never ends somebody's call is CallFinalizer's job: it catches around this.
     */
    @Test
    void letsStorageFailuresReachTheCallerSoTheSettlementRollsBack() {
        when(callBilling.findByCallAttemptId(CALL)).thenThrow(new IllegalStateException("db down"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        service(true).settleCall(COMPANY, CALL, TARGET, new CallUsage(60, 0, 0, 0, 0)))
                .isInstanceOf(IllegalStateException.class);
        verify(companyBilling, never()).charge(anyLong(), anyLong(), anyLong());
    }
}
