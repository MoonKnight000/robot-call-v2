package uz.murodjon.robotcallv2.agent.queue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.callqueue.application.port.input.CallQueueUseCase;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueue;
import uz.murodjon.robotcallv2.missedcall.application.port.input.MissedCallUseCase;
import uz.murodjon.robotcallv2.missedcall.domain.entity.MissedCall;
import uz.murodjon.robotcallv2.missedcall.domain.enums.MissedCallReason;
import uz.murodjon.robotcallv2.missedcall.domain.enums.MissedCallStatus;
import uz.murodjon.robotcallv2.shared.lock.ClusterJobLock;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AutoCallbackSchedulerTest {

    private static final long COMPANY_ID = 7L;
    private static final long QUEUE_ID = 3L;
    private static final long MISSED_ID = 11L;
    private static final long OPERATOR_ID = 21L;
    private static final String PHONE = "998901234567";
    private static final Instant NOW = Instant.parse("2026-09-14T10:00:00Z");

    private MissedCallUseCase missedCalls;
    private CallQueueDispatcher dispatcher;
    private OperatorFirstDialer dialer;
    private AutoCallbackScheduler scheduler;

    @BeforeEach
    void setUp() {
        missedCalls = mock(MissedCallUseCase.class);
        CallQueueUseCase queues = mock(CallQueueUseCase.class);
        dispatcher = mock(CallQueueDispatcher.class);
        dialer = mock(OperatorFirstDialer.class);
        ClusterJobLock runsHere = (jobName, job) -> {
            job.run();
            return true;
        };
        scheduler = new AutoCallbackScheduler(missedCalls, queues, dispatcher, dialer, runsHere);

        CallQueue queue = queue();
        when(missedCalls.findAutoCallbackCandidates(anyInt())).thenReturn(List.of(missedCall()));
        when(queues.findQueue(COMPANY_ID, QUEUE_ID)).thenReturn(queue);
        when(dispatcher.lineLength(QUEUE_ID)).thenReturn(0);
        when(dispatcher.findFreeOperator(queue)).thenReturn(OPERATOR_ID);
    }

    @Test
    void missedCallAnotherInstanceTookIsNotRung() {
        when(missedCalls.claimAutoCallback(COMPANY_ID, MISSED_ID)).thenReturn(false);

        scheduler.callBack();

        verifyNoInteractions(dialer);
        verify(missedCalls, never()).startAutoCallback(anyLong(), anyLong(), anyLong());
    }

    @Test
    void claimedMissedCallIsRung() {
        when(missedCalls.claimAutoCallback(COMPANY_ID, MISSED_ID)).thenReturn(true);

        scheduler.callBack();

        verify(dialer).dial(COMPANY_ID, OPERATOR_ID, PHONE, MISSED_ID, true);
        verify(missedCalls).startAutoCallback(COMPANY_ID, MISSED_ID, OPERATOR_ID);
    }

    private static MissedCall missedCall() {
        return new MissedCall(MISSED_ID, COMPANY_ID, PHONE, MissedCallReason.ABANDONED, MissedCallStatus.NEW, 1,
                NOW, NOW, QUEUE_ID, null, null, null, null, 0, null, null, null, null);
    }

    private static CallQueue queue() {
        return new CallQueue(QUEUE_ID, COMPANY_ID, "Sotuv", null, 20, 0, 0, 0, 0, false, null, 0, null, 0, null,
                null, null, 20, 0, false, null, true, true, List.of(), NOW);
    }
}
