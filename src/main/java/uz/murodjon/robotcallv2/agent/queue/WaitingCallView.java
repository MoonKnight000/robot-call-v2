package uz.murodjon.robotcallv2.agent.queue;

/** A waiting call as the wallboard shows it. */
public record WaitingCallView(
        long queueId,
        String channelId,
        String callerNumber,
        int position,
        long waitSeconds,
        WaitingCallState state,
        boolean vip,
        Long operatorUserId
) {
}
