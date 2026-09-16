package uz.murodjon.robotcallv2.callqueue.application.dto;

import uz.murodjon.robotcallv2.agent.queue.WaitingCallView;

import java.util.List;

/**
 * One queue on the wallboard (GET /api/call-queues/live, SSE {@code QUEUE_STATS}): who is
 * waiting and for how long, and where the queue's operators are.
 *
 * @param waiting            callers in line (not yet with an operator)
 * @param longestWaitSeconds the longest of those waits — the number a supervisor acts on
 * @param connected          calls with an operator right now
 */
public record CallQueueLiveRow(
        long queueId,
        String name,
        int waiting,
        long longestWaitSeconds,
        int connected,
        int available,
        int ringing,
        int onCall,
        int wrapUp,
        int onBreak,
        int offline,
        List<WaitingCallView> calls
) {
}
