package uz.murodjon.robotcallv2.campaign.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.shared.lock.ClusterJobLock;

/**
 * Runs the auto-enroll sweep on a timer. One sweep at a time across the cluster: two at once
 * would each enrol the same contacts, and only the unique-number check would save the list.
 */
@Component
public class AutoEnrollScheduler {

    private final AutoEnrollService autoEnrollService;
    private final ClusterJobLock clusterJobLock;
    private final boolean enabled;

    public AutoEnrollScheduler(AutoEnrollService autoEnrollService, ClusterJobLock clusterJobLock,
                               @Value("${voice-agent.campaign.auto-enroll-enabled:true}") boolean enabled) {
        this.autoEnrollService = autoEnrollService;
        this.clusterJobLock = clusterJobLock;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${voice-agent.campaign.auto-enroll-check-ms:60000}")
    public void sweepAutoEnrollCampaigns() {
        if (!enabled) {
            return;
        }
        clusterJobLock.runExclusively("campaign.auto-enroll-sweep", autoEnrollService::enrollAllActiveCampaigns);
    }
}
