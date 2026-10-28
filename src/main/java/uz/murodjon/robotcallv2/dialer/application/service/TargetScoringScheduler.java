package uz.murodjon.robotcallv2.dialer.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.callrecord.domain.entity.PhoneCallHistory;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignTargetUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignUseCase;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.campaign.domain.entity.TargetScore;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetOrdering;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.dialer.domain.service.PaymentPropensityScorer;
import uz.murodjon.robotcallv2.promise.application.port.input.PaymentPromiseUseCase;
import uz.murodjon.robotcallv2.promise.domain.entity.PaymentPromiseTally;
import uz.murodjon.robotcallv2.shared.lock.ClusterJobLock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Scores the waiting targets of every ACTIVE {@link TargetOrdering#SCORE} campaign
 * ({@link PaymentPropensityScorer}), so the dialer can claim the likeliest payers first.
 * Never-scored targets go first, then ones scored more than {@link #RESCORE_AFTER} ago — a
 * number's history moves with every call. Until a target is scored the dialer counts it as
 * neutral. One campaign failing does not stop the rest.
 */
@Service
public class TargetScoringScheduler {

    private static final Logger log = LoggerFactory.getLogger(TargetScoringScheduler.class);

    /** How far back a number's calls and promises are read. */
    static final Duration HISTORY = Duration.ofDays(180);
    static final Duration RESCORE_AFTER = Duration.ofHours(12);
    static final int PAGE_SIZE = 500;
    /** A run's ceiling per campaign, so one huge list does not hold the lock for the others. */
    static final int MAX_PAGES_PER_RUN = 40;

    private final CampaignUseCase campaignUseCase;
    private final CampaignTargetUseCase campaignTargetUseCase;
    private final CallAttemptUseCase callAttemptUseCase;
    private final PaymentPromiseUseCase paymentPromiseUseCase;
    private final CompanyConfigUseCase companyConfigUseCase;
    private final ClusterJobLock clusterJobLock;
    private final Clock clock;

    public TargetScoringScheduler(CampaignUseCase campaignUseCase, CampaignTargetUseCase campaignTargetUseCase,
                                  CallAttemptUseCase callAttemptUseCase, PaymentPromiseUseCase paymentPromiseUseCase,
                                  CompanyConfigUseCase companyConfigUseCase, ClusterJobLock clusterJobLock,
                                  Clock clock) {
        this.campaignUseCase = campaignUseCase;
        this.campaignTargetUseCase = campaignTargetUseCase;
        this.callAttemptUseCase = callAttemptUseCase;
        this.paymentPromiseUseCase = paymentPromiseUseCase;
        this.companyConfigUseCase = companyConfigUseCase;
        this.clusterJobLock = clusterJobLock;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 600_000, initialDelay = 60_000)
    public void scoreTargets() {
        clusterJobLock.runExclusively("dialer.target-scoring", this::scoreActiveCampaigns);
    }

    void scoreActiveCampaigns() {
        Instant now = clock.instant();
        for (Campaign campaign : campaignUseCase.findActiveCampaigns()) {
            if (campaign.dialing().targetOrdering() != TargetOrdering.SCORE) {
                continue;
            }
            try {
                int scored = scoreCampaign(campaign, now);
                if (scored > 0) {
                    log.info("Scored {} target(s) of campaign {}", scored, campaign.id());
                }
            } catch (RuntimeException e) {
                log.warn("Targets of campaign {} not scored this run: {}", campaign.id(), e.getMessage());
            }
        }
    }

    private int scoreCampaign(Campaign campaign, Instant now) {
        long companyId = campaign.companyId();
        CompanyConfig config = companyConfigUseCase.find(companyId);
        ZoneId zone = config != null ? config.zoneId() : clock.getZone();
        Instant since = now.minus(HISTORY);
        int scored = 0;
        for (int page = 0; page < MAX_PAGES_PER_RUN; page++) {
            List<CampaignTarget> targets =
                    campaignTargetUseCase.findTargetsToScore(campaign.id(), now.minus(RESCORE_AFTER), PAGE_SIZE);
            if (targets.isEmpty()) {
                break;
            }
            Set<String> phones = targets.stream().map(CampaignTarget::phone).collect(Collectors.toSet());
            Map<String, PhoneCallHistory> calls =
                    callAttemptUseCase.findPhoneCallHistories(companyId, phones, since, zone);
            Map<String, PaymentPromiseTally> promises =
                    paymentPromiseUseCase.findTalliesByPhones(companyId, phones, since);
            Map<Long, TargetScore> scores = new HashMap<>();
            for (CampaignTarget target : targets) {
                scores.put(target.id(),
                        PaymentPropensityScorer.score(calls.get(target.phone()), promises.get(target.phone())));
            }
            campaignTargetUseCase.updateTargetScores(companyId, scores);
            scored += scores.size();
            if (targets.size() < PAGE_SIZE) {
                break;
            }
        }
        return scored;
    }
}
