package uz.murodjon.robotcallv2.agent.ari;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.dialog.DialogOutcome;
import uz.murodjon.robotcallv2.agent.dialog.DialogTechnicalSnapshot;
import uz.murodjon.robotcallv2.agent.dialog.OperatorSnapshot;
import uz.murodjon.robotcallv2.agent.flow.FlowCall;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Calls the PBX side has (docs/INBOUND-PBX-PLAN.md §3.2) — in a call flow, a queue, or with
 * an operator — keyed by the caller's channel, and what an AI agent left behind on them.
 */
@Component
class FlowCallRegistry {

    /**
     * Calls the PBX side has, keyed by the caller's channel. A keypad digit on such a call
     * goes to its menu, not to the dialog, unless an AI agent is the one talking.
     */
    private final Map<String, FlowCall> flowCalls = new ConcurrentHashMap<>();
    /**
     * What an AI agent left behind when a flow moved the call on (to a queue, a menu): its
     * outcome and technical detail for teardown, and its view of the call for the operator.
     */
    private final Map<String, DialogOutcome> flowOutcomes = new ConcurrentHashMap<>();
    private final Map<String, DialogTechnicalSnapshot> flowTechnical = new ConcurrentHashMap<>();
    private final Map<String, OperatorSnapshot> flowSnapshots = new ConcurrentHashMap<>();
    /** A flow's own reaction to its agent asking for a person, by channel. */
    private final Map<String, Runnable> flowTransferCallbacks = new ConcurrentHashMap<>();
    /** Callers a VIP rule matched, so a later transfer to a queue still puts them first. */
    private final Set<String> vipChannels = ConcurrentHashMap.newKeySet();

    FlowCall find(String channelId) {
        return flowCalls.get(channelId);
    }

    void register(FlowCall call) {
        flowCalls.put(call.channelId(), call);
    }

    boolean contains(String channelId) {
        return flowCalls.containsKey(channelId);
    }

    FlowCall remove(String channelId) {
        return flowCalls.remove(channelId);
    }

    void keepOutcome(String channelId, DialogOutcome outcome) {
        flowOutcomes.put(channelId, outcome);
    }

    DialogOutcome takeOutcome(String channelId) {
        return flowOutcomes.remove(channelId);
    }

    void keepTechnical(String channelId, DialogTechnicalSnapshot technical) {
        flowTechnical.put(channelId, technical);
    }

    DialogTechnicalSnapshot takeTechnical(String channelId) {
        return flowTechnical.remove(channelId);
    }

    void keepSnapshot(String channelId, OperatorSnapshot snapshot) {
        flowSnapshots.put(channelId, snapshot);
    }

    OperatorSnapshot findSnapshot(String channelId) {
        return flowSnapshots.get(channelId);
    }

    void forgetSnapshot(String channelId) {
        flowSnapshots.remove(channelId);
    }

    void registerTransferCallback(String channelId, Runnable callback) {
        flowTransferCallbacks.put(channelId, callback);
    }

    Runnable takeTransferCallback(String channelId) {
        return flowTransferCallbacks.remove(channelId);
    }

    void markVip(String channelId) {
        vipChannels.add(channelId);
    }

    boolean isVip(String channelId) {
        return vipChannels.contains(channelId);
    }

    void forgetVip(String channelId) {
        vipChannels.remove(channelId);
    }
}
