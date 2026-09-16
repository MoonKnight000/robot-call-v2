package uz.murodjon.robotcallv2.billing.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.billing.application.dto.TopupRequest;
import uz.murodjon.robotcallv2.billing.application.port.input.BillingUseCase;
import uz.murodjon.robotcallv2.billing.application.port.output.CompanyBillingRepository;
import uz.murodjon.robotcallv2.billing.application.port.output.PaymentTopupRepository;
import uz.murodjon.robotcallv2.billing.domain.entity.CompanyBilling;
import uz.murodjon.robotcallv2.billing.domain.enums.PaymentMethod;
import uz.murodjon.robotcallv2.billing.infrastructure.config.BillingRateProperties;
import uz.murodjon.robotcallv2.billing.infrastructure.config.PaymentProperties;
import uz.murodjon.robotcallv2.shared.lock.ClusterJobLock;

import java.time.Instant;

/**
 * Raises a payment link for companies that asked not to run out mid-campaign.
 *
 * <p>It creates the top-up and tells the company where to pay it; it does not add money.
 * Nothing here can credit a balance, because the only thing that may is a payment
 * actually landing — otherwise "automatic recharge" would be a way to dial on credit
 * nobody ever settles.
 */
@Service
public class AutoRechargeService {

    private static final Logger log = LoggerFactory.getLogger(AutoRechargeService.class);

    private final CompanyBillingRepository companyBillingRepository;
    private final PaymentTopupRepository paymentTopupRepository;
    private final BillingUseCase billingUseCase;
    private final BillingRateProperties billingRateProperties;
    private final PaymentProperties paymentProperties;
    private final ClusterJobLock clusterJobLock;

    public AutoRechargeService(CompanyBillingRepository companyBillingRepository,
                               PaymentTopupRepository paymentTopupRepository,
                               BillingUseCase billingUseCase,
                               BillingRateProperties billingRateProperties,
                               PaymentProperties paymentProperties,
                               ClusterJobLock clusterJobLock) {
        this.companyBillingRepository = companyBillingRepository;
        this.paymentTopupRepository = paymentTopupRepository;
        this.billingUseCase = billingUseCase;
        this.billingRateProperties = billingRateProperties;
        this.paymentProperties = paymentProperties;
        this.clusterJobLock = clusterJobLock;
    }

    /** One instance at a time: two sweeps racing past the pending-link check each raised a top-up. */
    @Scheduled(cron = "${voice-agent.billing.auto-recharge.cron:0 */15 * * * *}")
    public void raiseTopups() {
        clusterJobLock.runExclusively("billing.auto-recharge", this::raiseTopupsForLowBalances);
    }

    private void raiseTopupsForLowBalances() {
        long threshold = billingRateProperties.autoRecharge().thresholdUzs();
        long amount = billingRateProperties.autoRecharge().amountUzs();
        if (amount <= 0) {
            return;
        }
        // A link older than the TTL is expired or about to be: it no longer counts as the
        // one the company is "about to pay", whether or not the expiry sweep has run yet.
        Instant stillPayableSince = Instant.now().minus(paymentProperties.topupTtl());

        for (CompanyBilling billing : companyBillingRepository.findByAutoRechargeEnabled()) {
            if (billing.spendableUzs() > threshold) {
                continue;
            }
            // The balance stays low until somebody pays, so without this the sweep would
            // raise a fresh payment link every fifteen minutes for the same shortfall.
            if (paymentTopupRepository.existsPendingByCompanyIdCreatedAfter(billing.companyId(), stillPayableSince)) {
                continue;
            }
            try {
                billingUseCase.createTopup(billing.companyId(), new TopupRequest(amount, PaymentMethod.PAYME));
                log.info("Auto-recharge raised {} UZS for company {} (spendable {} UZS)",
                        amount, billing.companyId(), billing.spendableUzs());
            } catch (Exception e) {
                log.error("Auto-recharge failed for company {}: {}", billing.companyId(), e.getMessage());
            }
        }
    }
}
