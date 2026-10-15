package uz.murodjon.robotcallv2.billing.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.billing.application.port.input.TrialCreditUseCase;
import uz.murodjon.robotcallv2.billing.application.port.output.TrialCreditRepository;
import uz.murodjon.robotcallv2.billing.domain.entity.TrialCredit;
import uz.murodjon.robotcallv2.shared.lock.ClusterJobLock;

import java.time.Clock;

/** Ends the trials whose time is up — one company per transaction, so one failure does not stop the rest. */
@Service
public class TrialCreditExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(TrialCreditExpiryJob.class);

    private final TrialCreditRepository trialCreditRepository;
    private final TrialCreditUseCase trialCreditUseCase;
    private final ClusterJobLock clusterJobLock;
    private final Clock clock;

    public TrialCreditExpiryJob(TrialCreditRepository trialCreditRepository,
                                TrialCreditUseCase trialCreditUseCase,
                                ClusterJobLock clusterJobLock,
                                Clock clock) {
        this.trialCreditRepository = trialCreditRepository;
        this.trialCreditUseCase = trialCreditUseCase;
        this.clusterJobLock = clusterJobLock;
        this.clock = clock;
    }

    @Scheduled(cron = "${voice-agent.billing.trial.expiry-cron:0 0 * * * *}")
    public void expireTrials() {
        clusterJobLock.runExclusively("billing.trial-expiry", this::expireEndedTrials);
    }

    private void expireEndedTrials() {
        for (TrialCredit trial : trialCreditRepository.findUnsettledExpiredBefore(clock.instant())) {
            try {
                trialCreditUseCase.expireTrialCredit(trial);
            } catch (Exception e) {
                log.error("Ending the trial of company {} failed: {}", trial.companyId(), e.getMessage());
            }
        }
    }
}
