package uz.murodjon.robotcallv2.crm.application.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.shared.lock.ClusterJobLock;

/** Every 30 seconds writes the due contact changes back to their CRMs — on one instance only. */
@Component
class CrmContactPushScheduler {

    private static final int PUSHES_PER_TICK = 50;

    private final CrmContactPushService crmContactPushService;
    private final ClusterJobLock clusterJobLock;

    CrmContactPushScheduler(CrmContactPushService crmContactPushService, ClusterJobLock clusterJobLock) {
        this.crmContactPushService = crmContactPushService;
        this.clusterJobLock = clusterJobLock;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 120_000)
    void runDuePushes() {
        clusterJobLock.runExclusively("crm.contact-push", () -> crmContactPushService.runDuePushes(PUSHES_PER_TICK));
    }
}
