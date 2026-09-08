package uz.murodjon.robotcallv2.billing.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.billing.application.port.input.CallBillingUseCase;
import uz.murodjon.robotcallv2.billing.application.port.output.BillingLedgerRepository;
import uz.murodjon.robotcallv2.billing.application.port.output.CallBillingRepository;
import uz.murodjon.robotcallv2.billing.application.port.output.CompanyBillingRepository;
import uz.murodjon.robotcallv2.billing.domain.entity.*;
import uz.murodjon.robotcallv2.billing.domain.enums.CallBillingStatus;
import uz.murodjon.robotcallv2.billing.domain.enums.LedgerEntryType;
import uz.murodjon.robotcallv2.billing.domain.service.CallCostCalculator;
import uz.murodjon.robotcallv2.billing.infrastructure.config.BillingRateProperties;

import java.util.Optional;

/**
 * Money on the call path: hold before dialling, charge after hanging up.
 *
 * <p>Two rules shape everything here. Nothing may abort a call that is already up — a
 * billing failure is logged and the conversation goes on, because a caller hearing
 * silence over a failed ledger write is worse than a call reconciled by hand later. And
 * every balance movement goes through an idempotency key, because the finalizer runs from
 * an outbox that is allowed to deliver twice.
 *
 * <p>A hold and the call that spends it are paired through the campaign target: the money
 * is put aside before Asterisk gives the call a channel, so there is no call id to key it
 * on yet, and carrying the amount along the queue, the originate and the media session
 * would touch four types to no other purpose.
 */
@Service
public class CallBillingService implements CallBillingUseCase {

    private static final Logger log = LoggerFactory.getLogger(CallBillingService.class);

    private static final String CALL_REFERENCE = "call_attempt";
    private static final String TARGET_REFERENCE = "campaign_target";

    private final CompanyBillingRepository companyBillingRepository;
    private final CallBillingRepository callBillingRepository;
    private final BillingLedgerRepository billingLedgerRepository;
    private final BillingRateProperties billingRateProperties;

    public CallBillingService(CompanyBillingRepository companyBillingRepository,
                              CallBillingRepository callBillingRepository,
                              BillingLedgerRepository billingLedgerRepository,
                              BillingRateProperties billingRateProperties) {
        this.companyBillingRepository = companyBillingRepository;
        this.callBillingRepository = callBillingRepository;
        this.billingLedgerRepository = billingLedgerRepository;
        this.billingRateProperties = billingRateProperties;
    }

    @Override
    public boolean hasBalanceForCall(long companyId) {
        if (!billingRateProperties.enforceBalance()) {
            return true;
        }
        return companyBillingRepository.findByCompanyId(companyId)
                .map(billing -> billing.spendableUzs() >= billingRateProperties.reservationUzs())
                .orElse(false);
    }

    @Override
    @Transactional
    public boolean reserveForCall(long companyId, long targetId) {
        if (!billingRateProperties.enforceBalance() || billingRateProperties.reservationUzs() <= 0) {
            return true;
        }
        long hold = billingRateProperties.reservationUzs();

        // Storage decides, not this method: two targets claimed in the same tick against a
        // balance big enough for one of them must not both be allowed through.
        if (!companyBillingRepository.reserve(companyId, hold)) {
            log.info("Target {} not dialled: company {} cannot cover the {} UZS hold", targetId, companyId, hold);
            return false;
        }

        try {
            CallBilling saved = callBillingRepository.save(
                    CallBilling.heldFor(targetId, companyId, billingRateProperties.version(), hold));
            long balance = balanceOf(companyId);
            billingLedgerRepository.append(LedgerEntry.of(companyId, LedgerEntryType.RESERVE, 0,
                    balance, balance, TARGET_REFERENCE, String.valueOf(targetId),
                    "call-reserve:" + saved.id()));
            return true;
        } catch (Exception e) {
            // The money is already held. Give it back rather than strand it: a hold nobody
            // releases silently shrinks what the company can spend, for good.
            companyBillingRepository.release(companyId, hold);
            log.error("Could not open the hold row for target {} of company {}: {}",
                    targetId, companyId, e.getMessage());
            return false;
        }
    }

    @Override
    @Transactional
    public void releaseReservation(long companyId, long targetId) {
        try {
            Optional<CallBilling> open = callBillingRepository.findOpenHold(companyId, targetId);
            if (open.isEmpty()) {
                return;
            }
            CallBilling hold = open.get();
            callBillingRepository.save(hold.release());
            if (hold.reservedUzs() > 0) {
                companyBillingRepository.release(companyId, hold.reservedUzs());
                long balance = balanceOf(companyId);
                billingLedgerRepository.append(LedgerEntry.of(companyId, LedgerEntryType.RELEASE, 0,
                        balance, balance, TARGET_REFERENCE, String.valueOf(targetId),
                        "call-release:" + hold.id()));
            }
        } catch (Exception e) {
            log.error("Could not release the hold on target {} for company {}: {}",
                    targetId, companyId, e.getMessage());
        }
    }

    @Override
    @Transactional
    public void settleCall(long companyId, long callAttemptId, Long targetId, CallUsage usage) {
        try {
            if (callBillingRepository.findByCallAttemptId(callAttemptId)
                    .filter(row -> row.status() == CallBillingStatus.SETTLED)
                    .isPresent()) {
                return;
            }

            // The hold this call was dialled under, or a fresh unheld row for a call that
            // never had one: an inbound call, or one placed before billing was switched on.
            CallBilling charge = (targetId == null ? Optional.<CallBilling>empty()
                    : callBillingRepository.findOpenHold(companyId, targetId))
                    .orElseGet(() -> CallBilling.unheld(callAttemptId, companyId, billingRateProperties.version()));

            CallCostBreakdown cost = CallCostCalculator.calculate(usage, billingRateProperties.rates());

            // The ledger entry is the gate: if it was already written, this settlement is a
            // redelivery and must not move the balance a second time.
            long balanceBefore = balanceOf(companyId);
            long balanceAfter = balanceBefore - cost.totalUzs();
            boolean written = billingLedgerRepository.append(LedgerEntry.of(companyId,
                    LedgerEntryType.CALL_CHARGE, -cost.totalUzs(), balanceBefore, balanceAfter,
                    CALL_REFERENCE, String.valueOf(callAttemptId), "call-charge:" + callAttemptId));
            if (!written) {
                return;
            }

            companyBillingRepository.charge(companyId, charge.reservedUzs(), cost.totalUzs());
            callBillingRepository.save(charge.settle(callAttemptId, usage, cost, billingRateProperties.version()));

            log.info("[{}] charged {} UZS to company {} ({}s, {} tokens, {} tts chars)",
                    callAttemptId, cost.totalUzs(), companyId, usage.durationSec(),
                    usage.promptTokens() + usage.completionTokens(), usage.ttsChars());
        } catch (Exception e) {
            log.error("[{}] settlement failed for company {}: {}", callAttemptId, companyId, e.getMessage());
        }
    }

    /** The balance, locked for the rest of this transaction so the ledger stays truthful. */
    private long balanceOf(long companyId) {
        return companyBillingRepository.findBalanceForUpdate(companyId)
                .orElseGet(() -> companyBillingRepository.save(CompanyBilling.defaultFor(companyId)).balanceUzs());
    }
}
