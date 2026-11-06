package uz.murodjon.robotcallv2.callrecord.application.port.output;

import uz.murodjon.robotcallv2.callrecord.domain.entity.CallEvent;
import uz.murodjon.robotcallv2.callrecord.domain.enums.CallEventType;

import java.time.Instant;
import java.util.List;

public interface CallEventRepository {

    void create(CallEvent event);

    /** The call's timeline, oldest first. */
    List<CallEvent> findByCallAttemptId(long companyId, long callAttemptId);

    /** A chat conversation's trace, oldest first. */
    List<CallEvent> findByChatSessionId(long companyId, long chatSessionId);

    /** The company's events of one type in {@code [from, to]}, newest first, at most {@code limit}. */
    List<CallEvent> findByCompanyIdAndType(long companyId, CallEventType type, Instant from, Instant to, int limit);

    /** Deletes events that happened before cutoff, at most batchSize rows. */
    int deleteCreatedBefore(Instant cutoff, int batchSize);
}
