package uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.callrecord.application.port.output.CallOutboxRepository;
import uz.murodjon.robotcallv2.callrecord.domain.entity.PendingSummary;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository.CallAttemptJpaRepository;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Instant;
import java.util.List;

@Component
public class CallOutboxRepositoryAdapter implements CallOutboxRepository {

    private final CallAttemptJpaRepository callAttemptJpaRepository;

    public CallOutboxRepositoryAdapter(CallAttemptJpaRepository callAttemptJpaRepository) {
        this.callAttemptJpaRepository = callAttemptJpaRepository;
    }

    @Override
    // Its own short transaction, like every claim: the row locks last for this statement only,
    // and the summaries that follow run outside it.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<PendingSummary> claimAttemptsAwaitingSummary(int maxAttempts, Instant endedAfter, Instant now,
                                                             Instant leaseUntil, int limit) {
        return callAttemptJpaRepository.claimAttemptsAwaitingSummary(maxAttempts, endedAfter, now, leaseUntil, limit)
                .stream()
                .map(row -> new PendingSummary(
                        ((Number) row[0]).longValue(),
                        // No target (inbound, manual, widget) means no client to file under: 0.
                        row[1] != null ? ((Number) row[1]).longValue() : 0L,
                        ((Number) row[2]).longValue(),
                        row[3] != null ? Disposition.valueOf(row[3].toString()) : null,
                        ((Number) row[4]).longValue()))
                .toList();
    }
}
