package uz.murodjon.robotcallv2.crm.application.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.shared.lock.ClusterJobLock;

/**
 * Wakes every minute and runs the CRM contact syncs that are due — on one instance only. A
 * company's own interval ({@link CrmContactSyncService#INTERVAL}) lives in its {@code next_run_at}.
 */
@Component
class CrmContactSyncScheduler {

    private static final int COMPANIES_PER_TICK = 20;

    private final CrmContactSyncService crmContactSyncService;
    private final ClusterJobLock clusterJobLock;

    CrmContactSyncScheduler(CrmContactSyncService crmContactSyncService, ClusterJobLock clusterJobLock) {
        this.crmContactSyncService = crmContactSyncService;
        this.clusterJobLock = clusterJobLock;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 90_000)
    void runDueContactSyncs() {
        clusterJobLock.runExclusively("crm.contact-sync",
                () -> crmContactSyncService.runDueContactSyncs(COMPANIES_PER_TICK));
    }
}
