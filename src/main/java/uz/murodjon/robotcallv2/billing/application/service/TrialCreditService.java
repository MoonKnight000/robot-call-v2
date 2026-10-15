package uz.murodjon.robotcallv2.billing.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.billing.application.dto.TrialCreditResponse;
import uz.murodjon.robotcallv2.billing.application.port.input.TrialCreditUseCase;
import uz.murodjon.robotcallv2.billing.application.port.output.BillingLedgerRepository;
import uz.murodjon.robotcallv2.billing.application.port.output.CompanyBillingRepository;
import uz.murodjon.robotcallv2.billing.application.port.output.TrialCreditRepository;
import uz.murodjon.robotcallv2.billing.domain.entity.CompanyBilling;
import uz.murodjon.robotcallv2.billing.domain.entity.LedgerEntry;
import uz.murodjon.robotcallv2.billing.domain.entity.TrialCredit;
import uz.murodjon.robotcallv2.billing.domain.enums.LedgerEntryType;
import uz.murodjon.robotcallv2.billing.domain.service.TrialCreditRules;
import uz.murodjon.robotcallv2.billing.infrastructure.config.TrialCreditProperties;

import java.time.Clock;
import java.time.Instant;

/**
 * The free trial money of a self-registered company: given once at signup, its unspent part
 * taken back when {@code voice-agent.billing.trial.duration} has passed.
 *
 * <p>Both moves go through the ledger like any other money ({@code TRIAL_CREDIT},
 * {@code TRIAL_EXPIRE}), keyed by company, so neither can happen twice.
 */
@Service
public class TrialCreditService implements TrialCreditUseCase {

    private static final Logger log = LoggerFactory.getLogger(TrialCreditService.class);

    private static final String TRIAL_REFERENCE = "billing_trial";

    private final TrialCreditRepository trialCreditRepository;
    private final CompanyBillingRepository companyBillingRepository;
    private final BillingLedgerRepository billingLedgerRepository;
    private final AuditUseCase auditUseCase;
    private final TrialCreditProperties trialCreditProperties;
    private final Clock clock;

    public TrialCreditService(TrialCreditRepository trialCreditRepository,
                              CompanyBillingRepository companyBillingRepository,
                              BillingLedgerRepository billingLedgerRepository,
                              AuditUseCase auditUseCase,
                              TrialCreditProperties trialCreditProperties,
                              Clock clock) {
        this.trialCreditRepository = trialCreditRepository;
        this.companyBillingRepository = companyBillingRepository;
        this.billingLedgerRepository = billingLedgerRepository;
        this.auditUseCase = auditUseCase;
        this.trialCreditProperties = trialCreditProperties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void grantTrialCredit(long companyId) {
        long credit = trialCreditProperties.creditUzs();
        if (credit <= 0) {
            return;
        }
        Instant now = clock.instant();
        if (!trialCreditRepository.create(
                TrialCredit.granted(companyId, credit, now, now.plus(trialCreditProperties.duration())))) {
            return;
        }
        long balanceBefore = lockBalance(companyId).balanceUzs();
        boolean written = billingLedgerRepository.append(LedgerEntry.of(companyId, LedgerEntryType.TRIAL_CREDIT,
                credit, balanceBefore, balanceBefore + credit,
                TRIAL_REFERENCE, String.valueOf(companyId), "trial:" + companyId));
        if (written) {
            companyBillingRepository.addBalance(companyId, credit);
        }
        auditUseCase.record(companyId, "BILLING_TRIAL_GRANTED", "company", String.valueOf(companyId),
                "Amount: " + credit + " UZS");
    }

    @Override
    public TrialCreditResponse findTrialCredit(long companyId) {
        return trialCreditRepository.findByCompanyId(companyId)
                .map(trial -> new TrialCreditResponse(trial.creditUzs(), trial.grantedAt(), trial.expiresAt(),
                        !trial.isSettled(), trial.clawbackUzs()))
                .orElse(null);
    }

    @Override
    @Transactional
    public void expireTrialCredit(TrialCredit trial) {
        long companyId = trial.companyId();
        // Locked first, so no call settles between reading the balance and taking money from it.
        CompanyBilling billing = lockBalance(companyId);
        long paidIn = billingLedgerRepository.sumAmountByCompanyIdAndEntryTypeSince(
                companyId, LedgerEntryType.TOPUP, trial.grantedAt());
        long clawback = TrialCreditRules.findUnspentUzs(
                trial.creditUzs(), billing.balanceUzs(), billing.reservedUzs(), paidIn);

        if (!trialCreditRepository.settle(companyId, clawback, clock.instant())) {
            return;
        }
        if (clawback > 0 && billingLedgerRepository.append(LedgerEntry.of(companyId, LedgerEntryType.TRIAL_EXPIRE,
                -clawback, billing.balanceUzs(), billing.balanceUzs() - clawback,
                TRIAL_REFERENCE, String.valueOf(companyId), "trial-expire:" + companyId))) {
            companyBillingRepository.addBalance(companyId, -clawback);
        }
        auditUseCase.record(companyId, "BILLING_TRIAL_EXPIRED", "company", String.valueOf(companyId),
                "Taken back: " + clawback + " UZS of " + trial.creditUzs());
        log.info("Trial of company {} ended: took back {} of {} UZS", companyId, clawback, trial.creditUzs());
    }

    /** The billing row, locked for the rest of this transaction; created empty if missing. */
    private CompanyBilling lockBalance(long companyId) {
        if (companyBillingRepository.findBalanceForUpdate(companyId).isEmpty()) {
            companyBillingRepository.save(CompanyBilling.defaultFor(companyId));
            companyBillingRepository.findBalanceForUpdate(companyId);
        }
        return companyBillingRepository.findByCompanyId(companyId).orElseThrow();
    }
}
