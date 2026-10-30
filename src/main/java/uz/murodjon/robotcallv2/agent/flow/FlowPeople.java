package uz.murodjon.robotcallv2.agent.flow;

import uz.murodjon.robotcallv2.agent.queue.QueueOutcome;

import java.util.List;

/**
 * How a call flow reaches a person on an engine whose operators are not Asterisk's queue
 * ({@code CallQueueDispatcher}): browser operators and phone numbers rung into the call's room.
 * Each call blocks the flow's thread until the person has talked to the caller and gone, the
 * ringing lapsed, or the caller hung up — the outcomes {@link FlowQueueRunner} gives on Asterisk.
 */
public interface FlowPeople {

    /**
     * Offers the call, one after another, to those of {@code userIds} who are available, each for
     * {@code ringSeconds}. {@link QueueOutcome#NO_ANSWER} when nobody took it.
     */
    QueueOutcome ringUsers(FlowCall call, List<Long> userIds, int ringSeconds) throws InterruptedException;

    /**
     * One step of a waiting line: offers the call to the next available one of {@code userIds} who has not
     * yet let this call go, for {@code ringSeconds}. {@link QueueOutcome#NO_ANSWER} when nobody was left to ask
     * (everyone has had a turn, so the next step starts a new round) or the one asked did not take it.
     */
    QueueOutcome ringNextUser(FlowCall call, List<Long> userIds, int ringSeconds) throws InterruptedException;

    /** Whether any of {@code userIds} is signed in at all, available or not. */
    boolean anySignedIn(long companyId, List<Long> userIds);

    /** Rings {@code number} into the call's room; {@link QueueOutcome#NO_ANSWER} when it is not answered. */
    QueueOutcome forward(FlowCall call, String number, int ringSeconds) throws InterruptedException;
}
