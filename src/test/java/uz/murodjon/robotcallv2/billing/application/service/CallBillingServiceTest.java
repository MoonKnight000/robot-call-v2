package uz.murodjon.robotcallv2.billing.application.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.billing.application.port.output.BillingLedgerRepository;
import uz.murodjon.robotcallv2.billing.application.port.output.CallBillingRepository;
import uz.murodjon.robotcallv2.billing.application.port.output.CompanyBillingRepository;
import uz.murodjon.robotcallv2.billing.domain.entity.*;
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
                new BillingRates(new BillingLlmRates(1000, 10_000, 100), 60, 50, 180, 120, 0),
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

    /** A billing failure must never be what ends somebody's call. */
    @Test
    void swallowsStorageFailuresDuringSettlement() {
        when(callBilling.findByCallAttemptId(CALL)).thenThrow(new IllegalStateException("db down"));

        service(true).settleCall(COMPANY, CALL, TARGET, new CallUsage(60, 0, 0, 0, 0));
    }
}
