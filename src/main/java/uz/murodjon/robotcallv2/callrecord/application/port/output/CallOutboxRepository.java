package uz.murodjon.robotcallv2.callrecord.application.port.output;

import uz.murodjon.robotcallv2.callrecord.domain.entity.PendingSummary;

import java.time.Instant;
import java.util.List;

public interface CallOutboxRepository {

    /**
     * Takes up to {@code limit} finished calls that still have no summary and have attempts
     * left — only calls that ended at or after {@code endedAfter`}; older ones are summarized or
     * given up on. Each claimed call has its attempt counted and is hidden from every
     * instance's sweep until {@code leaseUntil}; calls another instance is claiming at the same
     * moment are skipped, so no call is summarized twice at once.
     */
    List<PendingSummary> claimAttemptsAwaitingSummary(int maxAttempts, Instant endedAfter, Instant now,
                                                      Instant leaseUntil, int limit);
}
