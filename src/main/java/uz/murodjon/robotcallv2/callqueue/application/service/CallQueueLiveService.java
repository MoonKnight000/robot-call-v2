package uz.murodjon.robotcallv2.callqueue.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.queue.CallQueueDispatcher;
import uz.murodjon.robotcallv2.agent.queue.WaitingCallState;
import uz.murodjon.robotcallv2.agent.queue.WaitingCallView;
import uz.murodjon.robotcallv2.callqueue.application.dto.CallQueueLiveRow;
import uz.murodjon.robotcallv2.callqueue.application.port.input.CallQueueLiveUseCase;
import uz.murodjon.robotcallv2.callqueue.application.port.input.CallQueueUseCase;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueue;
import uz.murodjon.robotcallv2.operator.application.port.input.ContactCenterEventUseCase;
import uz.murodjon.robotcallv2.operator.domain.entity.OperatorPresence;
import uz.murodjon.robotcallv2.operator.domain.enums.ContactCenterEventType;
import uz.murodjon.robotcallv2.operator.domain.enums.OperatorStatus;
import uz.murodjon.robotcallv2.shared.stream.EventSink;
import uz.murodjon.robotcallv2.shared.stream.EventSubscription;

import java.util.ArrayList;
import java.util.List;

/**
 * The queue wallboard (docs/INBOUND-PBX-PLAN.md §10): live state read straight from the
 * dispatcher and operator presence — nothing here touches the database but the queue list.
 */
@Service
public class CallQueueLiveService implements CallQueueLiveUseCase {

    private static final Logger log = LoggerFactory.getLogger(CallQueueLiveService.class);

    private final CallQueueUseCase callQueueUseCase;
    private final CallQueueDispatcher dispatcher;
    private final ContactCenterEventUseCase contactCenterEventUseCase;

    public CallQueueLiveService(CallQueueUseCase callQueueUseCase, CallQueueDispatcher dispatcher,
                                ContactCenterEventUseCase contactCenterEventUseCase) {
        this.callQueueUseCase = callQueueUseCase;
        this.dispatcher = dispatcher;
        this.contactCenterEventUseCase = contactCenterEventUseCase;
    }

    @Override
    public List<CallQueueLiveRow> findLive(long companyId) {
        List<CallQueueLiveRow> rows = new ArrayList<>();
        for (CallQueue queue : callQueueUseCase.findEnabledByCompanyId(companyId)) {
            List<WaitingCallView> calls = dispatcher.findLive(queue.id());
            int waiting = 0;
            int connected = 0;
            long longest = 0;
            for (WaitingCallView call : calls) {
                if (call.state() == WaitingCallState.WAITING || call.state() == WaitingCallState.RINGING) {
                    waiting++;
                    longest = Math.max(longest, call.waitSeconds());
                } else if (call.state() == WaitingCallState.CONNECTED || call.state() == WaitingCallState.CONNECTING) {
                    connected++;
                }
            }
            int[] byStatus = new int[OperatorStatus.values().length];
            for (OperatorPresence presence : dispatcher.findMemberPresence(queue)) {
                byStatus[presence.status().ordinal()]++;
            }
            rows.add(new CallQueueLiveRow(queue.id(), queue.name(), waiting, longest, connected,
                    byStatus[OperatorStatus.AVAILABLE.ordinal()], byStatus[OperatorStatus.RINGING.ordinal()],
                    byStatus[OperatorStatus.ON_CALL.ordinal()], byStatus[OperatorStatus.WRAP_UP.ordinal()],
                    byStatus[OperatorStatus.BREAK.ordinal()], byStatus[OperatorStatus.OFFLINE.ordinal()], calls));
        }
        return rows;
    }

    @Override
    public EventSubscription subscribe(long companyId, EventSink sink) {
        return contactCenterEventUseCase.subscribeWallboard(companyId, sink);
    }

    /** Pushes every open wallboard its company's queues. */
    @Scheduled(fixedRateString = "${voice-agent.live.kpi-poll-ms:2000}")
    void publish() {
        for (Long companyId : contactCenterEventUseCase.findWallboardCompanyIds()) {
            try {
                contactCenterEventUseCase.publishToWallboard(companyId, ContactCenterEventType.QUEUE_STATS,
                        findLive(companyId));
            } catch (Exception e) {
                log.warn("Wallboard of company {} not published: {}", companyId, e.getMessage());
            }
        }
    }
}
