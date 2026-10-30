package uz.murodjon.robotcallv2.agent.ari;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.flow.FlowCall;
import uz.murodjon.robotcallv2.agent.flow.FlowPeople;
import uz.murodjon.robotcallv2.agent.queue.QueueOutcome;

import java.util.List;

/**
 * Where a LiveKit call's flow finds people: operators in the browser ({@link LiveKitOperatorTransfers})
 * and phone numbers rung into the room ({@link LiveKitCallService#forwardFlowCall}).
 */
@Component
public class LiveKitFlowPeople implements FlowPeople {

    private final LiveKitOperatorTransfers liveKitOperatorTransfers;
    /** Lazy: the LiveKit call service runs the flow that asks for people. */
    private final ObjectProvider<LiveKitCallService> liveKitCallServiceProvider;

    LiveKitFlowPeople(LiveKitOperatorTransfers liveKitOperatorTransfers,
                      ObjectProvider<LiveKitCallService> liveKitCallServiceProvider) {
        this.liveKitOperatorTransfers = liveKitOperatorTransfers;
        this.liveKitCallServiceProvider = liveKitCallServiceProvider;
    }

    @Override
    public QueueOutcome ringUsers(FlowCall call, List<Long> userIds, int ringSeconds) throws InterruptedException {
        return liveKitOperatorTransfers.ringUsers(call, userIds, ringSeconds);
    }

    @Override
    public QueueOutcome ringNextUser(FlowCall call, List<Long> userIds, int ringSeconds) throws InterruptedException {
        return liveKitOperatorTransfers.ringNextUser(call, userIds, ringSeconds);
    }

    @Override
    public boolean anySignedIn(long companyId, List<Long> userIds) {
        return liveKitOperatorTransfers.anySignedIn(companyId, userIds);
    }

    @Override
    public QueueOutcome forward(FlowCall call, String number, int ringSeconds) throws InterruptedException {
        return liveKitCallServiceProvider.getObject().forwardFlowCall(call, number);
    }
}
