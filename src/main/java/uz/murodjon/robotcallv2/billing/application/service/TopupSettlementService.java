package uz.murodjon.robotcallv2.billing.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.billing.application.port.output.BillingLedgerRepository;
import uz.murodjon.robotcallv2.billing.application.port.output.CompanyBillingRepository;
import uz.murodjon.robotcallv2.billing.application.port.output.PaymentTopupRepository;
import uz.murodjon.robotcallv2.billing.domain.entity.CompanyBilling;
import uz.murodjon.robotcallv2.billing.domain.entity.LedgerEntry;
import uz.murodjon.robotcallv2.billing.domain.entity.PaymentTopup;
import uz.murodjon.robotcallv2.billing.domain.enums.LedgerEntryType;
import uz.murodjon.robotcallv2.billing.infrastructure.config.PaymentProperties;

import java.time.Clock;

/**
 * Where a top-up's money meets the balance, whichever provider it came through.
 *
 * <p>{@link #complete} is the only code that credits a balance for a payment, and it does so
 * at most once per top-up: the move to COMPLETED is a conditional update only one caller
 * can win, and the ledger entry behind it is keyed {@code topup:<paymentId>}, so even a
 * second winner could not move the balance twice.
 *
 * <p>It also expires top-ups left unpaid past {@code voice-agent.billing.payment.topup-ttl}.
 */
@Service
public class TopupSettlementService {

    private static final Logger log = LoggerFactory.getLogger(TopupSettlementService.class);

    private static final String TOPUP_REFERENCE = "payment_topup";

    private final PaymentTopupRepository paymentTopupRepository;
    private final CompanyBillingRepository companyBillingRepository;
    private final BillingLedgerRepository billingLedgerRepository;
    private final AuditUseCase auditUseCase;
    private final PaymentProperties paymentProperties;
    private final Clock clock;

    public TopupSettlementService(PaymentTopupRepository paymentTopupRepository,
                                  CompanyBillingRepository companyBillingRepository,
                                  BillingLedgerRepository billingLedgerRepository,
                                  AuditUseCase auditUseCase,
                                  PaymentProperties paymentProperties,
                                  Clock clock) {
        this.paymentTopupRepository = paymentTopupRepository;
        this.companyBillingRepository = companyBillingRepository;
        this.billingLedgerRepository = billingLedgerRepository;
        this.auditUseCase = auditUseCase;
        this.paymentProperties = paymentProperties;
        this.clock = clock;
    }

    /**
     * Marks the top-up paid and credits its amount to the company's balance.
     *
     * @return true when this call completed it; false when it was already paid or cancelled
     *         and nothing was credited
     */
    @Transactional
    public boolean complete(PaymentTopup topup) {
        if (!paymentTopupRepository.completeById(topup.id(), clock.instant())) {
            return false;
        }
        long companyId = topup.companyId();
        long balanceBefore = balanceOf(companyId);
        boolean written = billingLedgerRepository.append(LedgerEntry.of(companyId, LedgerEntryType.TOPUP,
                topup.amountUzs(), balanceBefore, balanceBefore + topup.amountUzs(),
                TOPUP_REFERENCE, topup.paymentId(), "topup:" + topup.paymentId()));
        if (written) {
            companyBillingRepository.addBalance(companyId, topup.amountUzs());
        }
        auditUseCase.record(companyId, "BILLING_TOPUP_COMPLETED", "company", String.valueOf(companyId),
                "Payment: " + topup.paymentId() + ", Amount: " + topup.amountUzs() + " UZS, Method: "
                        + topup.paymentMethod());
        log.info("Top-up {} paid via {}: {} UZS for company {}",
                topup.paymentId(), topup.paymentMethod(), topup.amountUzs(), companyId);
        return true;
    }

    /**
     * Marks top-ups nobody paid within the TTL EXPIRED, so a stale payment link neither
     * stays payable forever nor keeps blocking auto-recharge.
     */
    @Scheduled(cron = "${voice-agent.billing.payment.expiry-cron:0 */10 * * * *}")
    public void expireStaleTopups() {
        int expired = paymentTopupRepository.expirePendingCreatedBefore(
                clock.instant().minus(paymentProperties.topupTtl()));
        if (expired > 0) {
            log.info("Expired {} top-up(s) left unpaid for more than {}", expired, paymentProperties.topupTtl());
        }
    }

    /** The balance, locked for the rest of this transaction so the ledger stays truthful. */
    private long balanceOf(long companyId) {
        return companyBillingRepository.findBalanceForUpdate(companyId)
                .orElseGet(() -> companyBillingRepository.save(CompanyBilling.defaultFor(companyId)).balanceUzs());
    }
}
