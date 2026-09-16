package uz.murodjon.robotcallv2.agent.queue;

import uz.murodjon.robotcallv2.agent.flow.FlowCall;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueue;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallTransferTarget;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * One call in a queue — or ringing a group, an extension or a number, which is the same
 * machinery with a queue made up on the spot ({@code queue.id() == 0}). Shared by the
 * dispatcher, which rings operators for it, and the call's own thread, which plays the
 * hold music and watches {@link #state()}.
 */
public final class WaitingCall {

    private final long id;
    private final FlowCall call;
    private final CallQueue queue;
    private final Instant enqueuedAt;
    /** The language the operator hears the handover in — the company's, not necessarily the caller's. */
    private final String handoverLanguage;
    private final String transcript;
    private final Long stickyUserId;
    /** For a forwarded call: the number rung instead of operators. */
    private final String externalNumber;
    private final boolean confirmExternal;

    private volatile WaitingCallState state = WaitingCallState.WAITING;
    private final Set<String> ringingChannels = ConcurrentHashMap.newKeySet();
    private volatile String connectedChannelId;
    private volatile Long connectedUserId;
    private volatile Instant connectedAt;
    private volatile CallTransferTarget transfer;
    private volatile boolean externalTried;
    /**
     * Guards the state transitions below. A lock rather than {@code synchronized}: the
     * dispatcher holds it across an ARI originate, and virtual threads waiting on a monitor
     * would pin their carriers for that long.
     */
    private final ReentrantLock lock = new ReentrantLock();

    WaitingCall(long id, FlowCall call, CallQueue queue, Instant enqueuedAt, String handoverLanguage, String transcript,
                Long stickyUserId, String externalNumber, boolean confirmExternal) {
        this.id = id;
        this.call = call;
        this.queue = queue;
        this.enqueuedAt = enqueuedAt;
        this.handoverLanguage = handoverLanguage;
        this.transcript = transcript;
        this.stickyUserId = stickyUserId;
        this.externalNumber = externalNumber;
        this.confirmExternal = confirmExternal;
    }

    public long id() {
        return id;
    }

    public FlowCall call() {
        return call;
    }

    public CallQueue queue() {
        return queue;
    }

    public boolean adHoc() {
        return queue.id() <= 0;
    }

    public Instant enqueuedAt() {
        return enqueuedAt;
    }

    public long waitedSeconds(Instant now) {
        return Duration.between(enqueuedAt, now).toSeconds();
    }

    public String handoverLanguage() {
        return handoverLanguage;
    }

    public String transcript() {
        return transcript;
    }

    public Long stickyUserId() {
        return stickyUserId;
    }

    public String externalNumber() {
        return externalNumber;
    }

    public boolean confirmExternal() {
        return confirmExternal;
    }

    public WaitingCallState state() {
        return state;
    }

    void setState(WaitingCallState state) {
        this.state = state;
    }

    Set<String> ringingChannels() {
        return ringingChannels;
    }

    public String connectedChannelId() {
        return connectedChannelId;
    }

    public Long connectedUserId() {
        return connectedUserId;
    }

    public Instant connectedAt() {
        return connectedAt;
    }

    void connected(String channelId, Long userId, Instant at) {
        this.connectedChannelId = channelId;
        this.connectedUserId = userId;
        this.connectedAt = at;
    }

    /** Where the call goes once its operator has hung up — a blind transfer; null for nowhere. */
    public CallTransferTarget transfer() {
        return transfer;
    }

    void setTransfer(CallTransferTarget transfer) {
        this.transfer = transfer;
    }

    ReentrantLock lock() {
        return lock;
    }

    public boolean externalTried() {
        return externalTried;
    }

    void setExternalTried(boolean externalTried) {
        this.externalTried = externalTried;
    }
}
