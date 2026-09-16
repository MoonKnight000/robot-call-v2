package uz.murodjon.robotcallv2.agent.flow;

import uz.murodjon.robotcallv2.agent.audio.AudioListener;
import uz.murodjon.robotcallv2.agent.rtp.RtpEndpoint;
import uz.murodjon.robotcallv2.missedcall.domain.enums.MissedCallReason;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * One call while the PBX side has it — walking a call flow, waiting in a queue, talking to
 * an operator. Owns nothing Asterisk-side: the caller's channel, bridge and media leg were
 * set up by {@code FlowCallService} and are torn down by {@code CallTeardownService}; this is what the flow thread and
 * the queue need to know about the call, and the one place the ARI event thread (a digit,
 * a hang-up) can reach them through.
 */
public final class FlowCall {

    private final String channelId;
    private final String bridgeId;
    private final RtpEndpoint endpoint;
    private final long companyId;
    private final long callAttemptId;
    private final String callerNumber;
    private final String did;
    private final Long inboundRouteId;
    private final Instant startedAt;
    private final List<AudioListener> baseListeners;
    private final List<AudioListener> extraListeners = new CopyOnWriteArrayList<>();
    private final DtmfCollector digits = new DtmfCollector();
    /** The menu choices made so far, in words — what the operator hears in the whisper. */
    private final List<String> choices = new CopyOnWriteArrayList<>();

    private volatile String language;
    private volatile boolean languageKnown;
    private volatile boolean vip;
    private volatile String clientName;
    private volatile boolean gone;
    /** An AI agent is talking; keypad digits go to it, not to a menu. */
    private volatile boolean agentActive;
    /** Someone — agent or operator — actually served the caller. */
    private volatile boolean served;
    private volatile MissedCallReason missedReason;
    private volatile Long voicemailMessageId;
    private volatile Long queueId;
    private volatile String operatorChannelId;
    private volatile Long operatorUserId;
    private volatile boolean onHold;

    public FlowCall(String channelId, String bridgeId, RtpEndpoint endpoint, long companyId, long callAttemptId,
                    String callerNumber, String did, Long inboundRouteId, String language, boolean languageKnown,
                    List<AudioListener> baseListeners) {
        this.channelId = channelId;
        this.bridgeId = bridgeId;
        this.endpoint = endpoint;
        this.companyId = companyId;
        this.callAttemptId = callAttemptId;
        this.callerNumber = callerNumber;
        this.did = did;
        this.inboundRouteId = inboundRouteId;
        this.language = language;
        this.languageKnown = languageKnown;
        this.baseListeners = List.copyOf(baseListeners);
        this.startedAt = Instant.now();
    }

    public String channelId() {
        return channelId;
    }

    public String bridgeId() {
        return bridgeId;
    }

    public RtpEndpoint endpoint() {
        return endpoint;
    }

    public long companyId() {
        return companyId;
    }

    public long callAttemptId() {
        return callAttemptId;
    }

    public String callerNumber() {
        return callerNumber;
    }

    public String did() {
        return did;
    }

    public Long inboundRouteId() {
        return inboundRouteId;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public DtmfCollector digits() {
        return digits;
    }

    public List<AudioListener> baseListeners() {
        return baseListeners;
    }

    /** Adds a listener next to the call's own (a voicemail capture, a recognizer) until it is detached. */
    public void attachListener(AudioListener listener) {
        extraListeners.add(listener);
        applyListeners();
    }

    public void detachListener(AudioListener listener) {
        extraListeners.remove(listener);
        applyListeners();
    }

    /** Back to the call's own listeners only — after an AI agent's recognizer chain is gone. */
    public void restoreListeners() {
        extraListeners.clear();
        applyListeners();
    }

    private void applyListeners() {
        List<AudioListener> all = new ArrayList<>(baseListeners);
        all.addAll(extraListeners);
        endpoint.replaceListeners(all);
    }

    public String language() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
        this.languageKnown = true;
    }

    public boolean languageKnown() {
        return languageKnown;
    }

    public boolean vip() {
        return vip;
    }

    public void setVip(boolean vip) {
        this.vip = vip;
    }

    public String clientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public boolean isGone() {
        return gone || Thread.currentThread().isInterrupted();
    }

    /** The caller hung up; every wait on this call ends at its next check. */
    public void markGone() {
        this.gone = true;
    }

    public boolean agentActive() {
        return agentActive;
    }

    public void setAgentActive(boolean agentActive) {
        this.agentActive = agentActive;
    }

    public boolean served() {
        return served;
    }

    public void markServed() {
        this.served = true;
    }

    public MissedCallReason missedReason() {
        return missedReason;
    }

    public void setMissedReason(MissedCallReason missedReason) {
        this.missedReason = missedReason;
    }

    public Long voicemailMessageId() {
        return voicemailMessageId;
    }

    public void setVoicemailMessageId(Long voicemailMessageId) {
        this.voicemailMessageId = voicemailMessageId;
    }

    public Long queueId() {
        return queueId;
    }

    public void setQueueId(Long queueId) {
        this.queueId = queueId;
    }

    public List<String> choices() {
        return choices;
    }

    public void addChoice(String choice) {
        if (choice != null && !choice.isBlank()) {
            choices.add(choice);
        }
    }

    public String operatorChannelId() {
        return operatorChannelId;
    }

    public Long operatorUserId() {
        return operatorUserId;
    }

    public void setOperator(String channelId, Long userId) {
        this.operatorChannelId = channelId;
        this.operatorUserId = userId;
    }

    public boolean onHold() {
        return onHold;
    }

    public void setOnHold(boolean onHold) {
        this.onHold = onHold;
    }
}
