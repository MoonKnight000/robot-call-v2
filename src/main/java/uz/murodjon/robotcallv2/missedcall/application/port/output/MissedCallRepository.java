package uz.murodjon.robotcallv2.missedcall.application.port.output;

import uz.murodjon.robotcallv2.missedcall.domain.entity.MissedCall;
import uz.murodjon.robotcallv2.missedcall.domain.entity.MissedCallFilter;
import uz.murodjon.robotcallv2.missedcall.domain.entity.MissedCallOccurrence;
import uz.murodjon.robotcallv2.missedcall.domain.enums.MissedCallStatus;

import java.time.Instant;
import java.util.List;

public interface MissedCallRepository {

    long create(long companyId, MissedCallOccurrence occurrence, Instant at);

    /** One more unserved call on an open row: count, last time, latest reason and context. */
    void touch(long id, MissedCallOccurrence occurrence, Instant at);

    MissedCall find(long companyId, long id);

    MissedCall findOpenByPhone(long companyId, String phone);

    void update(long companyId, long id, MissedCallStatus status, String note, Long resolvedByUserId, Instant resolvedAt);

    void assign(long companyId, long id, Long userId);

    int resolveOpenByPhone(long companyId, String phone, MissedCallStatus status, Long userId, Instant at);

    void markCallbackAttempt(long id, Instant at);

    List<MissedCall> findAll(long companyId, MissedCallFilter filter);

    long count(long companyId, MissedCallFilter filter);

    List<MissedCall> findAutoCallbackCandidates(Instant retryBefore, int maxAttempts, int limit);

    /**
     * Stamps the row as tried at {@code now}, only while it is NEW and was last tried before
     * {@code retryBefore}; false when another instance took it first.
     */
    boolean claimAutoCallback(long companyId, long id, Instant now, Instant retryBefore);
}
