package uz.murodjon.robotcallv2.callrecord.application.port.input;

import uz.murodjon.robotcallv2.callrecord.domain.entity.CallEvent;
import uz.murodjon.robotcallv2.callrecord.domain.enums.CallEventType;

import java.time.Instant;
import java.util.List;

/** A call's timeline — flow nodes, keys pressed, queueing, the operators it rang. */
public interface CallEventUseCase {

    /**
     * Records one event; {@code keyValues} alternate key, value ("queueId", 3, "position", 2).
     * Never throws: a missing timeline row must not cost a caller their call.
     */
    void record(long companyId, long callAttemptId, CallEventType type, Object... keyValues);

    /** The same, for an event that happened at {@code at} and is only written now. */
    void recordAt(long companyId, long callAttemptId, CallEventType type, Instant at, Object... keyValues);

    List<CallEvent> findByCallAttemptId(long companyId, long callAttemptId);

    /**
     * Records one step of the agent's trace in a chat conversation (the trace types only —
     * {@link CallEventType#isCallTrace()}); never throws, like {@link #record}.
     */
    void recordForChat(long companyId, long chatSessionId, CallEventType type, Object... keyValues);

    /** A chat conversation's trace, oldest first. */
    List<CallEvent> findByChatSessionId(long companyId, long chatSessionId);

    /** The company's events of one type, calls and chats alike, in {@code [from, to]}, newest first, at most {@code limit}. */
    List<CallEvent> findByCompanyIdAndType(long companyId, CallEventType type, Instant from, Instant to, int limit);
}
