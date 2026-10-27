package uz.murodjon.robotcallv2.callrecord.application.port.output;

import uz.murodjon.robotcallv2.callrecord.domain.entity.CallEvent;

import java.time.Instant;
import java.util.List;

public interface CallEventRepository {

    void create(CallEvent event);

    /** The call's timeline, oldest first. */
    List<CallEvent> findByCallAttemptId(long companyId, long callAttemptId);

    /** Deletes events that happened before cutoff, at most batchSize rows. */
    int deleteCreatedBefore(Instant cutoff, int batchSize);
}
