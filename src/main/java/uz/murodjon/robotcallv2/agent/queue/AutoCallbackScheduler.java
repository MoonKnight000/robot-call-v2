package uz.murodjon.robotcallv2.agent.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.callqueue.application.port.input.CallQueueUseCase;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueue;
import uz.murodjon.robotcallv2.missedcall.application.port.input.MissedCallUseCase;
import uz.murodjon.robotcallv2.missedcall.domain.entity.MissedCall;
import uz.murodjon.robotcallv2.shared.lock.ClusterJobLock;
import uz.murodjon.robotcallv2.shared.util.PhoneMasker;

/**
 * Calls missed callers back by itself (OnlinePBX's "avtoperezvon", docs/INBOUND-PBX-PLAN.md
 * §8.2): for a queue with {@code autoCallback}, whenever one of its operators is free, the
 * oldest open missed call is rung — operator first, client once the operator answers. A
 * call that does not connect goes back on the list and is tried again later, a few times.
 */
@Component
public class AutoCallbackScheduler {

    private static final Logger log = LoggerFactory.getLogger(AutoCallbackScheduler.class);
    private static final int BATCH = 20;

    private final MissedCallUseCase missedCallUseCase;
    private final CallQueueUseCase callQueueUseCase;
    private final CallQueueDispatcher dispatcher;
    private final OperatorFirstDialer dialer;
    private final ClusterJobLock clusterJobLock;

    public AutoCallbackScheduler(MissedCallUseCase missedCallUseCase, CallQueueUseCase callQueueUseCase,
                                 CallQueueDispatcher dispatcher, OperatorFirstDialer dialer,
                                 ClusterJobLock clusterJobLock) {
        this.missedCallUseCase = missedCallUseCase;
        this.callQueueUseCase = callQueueUseCase;
        this.dispatcher = dispatcher;
        this.dialer = dialer;
        this.clusterJobLock = clusterJobLock;
    }

    /** One instance at a time: two sweeps reading the same open missed call rang the client twice. */
    @Scheduled(fixedDelay = 30_000, initialDelay = 60_000)
    public void callBack() {
        clusterJobLock.runExclusively("queue.auto-callback", this::callBackMissedCalls);
    }

    private void callBackMissedCalls() {
        for (MissedCall missed : missedCallUseCase.findAutoCallbackCandidates(BATCH)) {
            try {
                CallQueue queue = missed.queueId() != null ? callQueueUseCase.findQueue(missed.companyId(), missed.queueId()) : null;
                if (queue == null || dispatcher.lineLength(queue.id()) > 0) {
                    // Callers already waiting come first; a callback would take their operator.
                    continue;
                }
                Long operator = dispatcher.findFreeOperator(queue);
                if (operator == null) {
                    continue;
                }
                if (!missedCallUseCase.claimAutoCallback(missed.companyId(), missed.id())) {
                    // Another instance took this caller between the scan and now. A claimed
                    // call whose dial throws below is tried again after the retry interval.
                    continue;
                }
                dialer.dial(missed.companyId(), operator, missed.phone(), missed.id(), true);
                missedCallUseCase.startAutoCallback(missed.companyId(), missed.id(), operator);
                log.info("Auto callback of missed call {} ({}) via operator {}", missed.id(),
                        PhoneMasker.mask(missed.phone()), operator);
            } catch (Exception e) {
                log.warn("Auto callback of missed call {} failed: {}", missed.id(), e.getMessage());
            }
        }
    }
}
