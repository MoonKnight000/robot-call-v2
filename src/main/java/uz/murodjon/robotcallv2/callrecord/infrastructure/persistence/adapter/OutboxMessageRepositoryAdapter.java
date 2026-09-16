package uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.robotcallv2.callrecord.application.port.output.OutboxMessageRepository;
import uz.murodjon.robotcallv2.callrecord.domain.entity.OutboxMessage;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.mapper.OutboxMessageEntityMapper;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository.OutboxMessageJpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Component
public class OutboxMessageRepositoryAdapter implements OutboxMessageRepository {

    private final OutboxMessageJpaRepository jpaRepository;
    private final OutboxMessageEntityMapper mapper;

    public OutboxMessageRepositoryAdapter(OutboxMessageJpaRepository jpaRepository, OutboxMessageEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Long enqueue(long companyId, OutboxMessage message) {
        List<Long> ids = jpaRepository.insertIgnoringConflict(companyId, message.kind().name(),
                message.callAttemptId(), mapper.toPayloadJson(message.payload()), message.idempotencyKey());
        return ids.isEmpty() ? null : ids.get(0);
    }

    @Override
    // Its own short transaction: the claim's row locks last for this statement only, and the
    // delivery that follows runs outside any transaction.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<OutboxMessage> claimDue(Instant now, Instant leaseUntil, int limit) {
        return jpaRepository.claimDue(now, leaseUntil, limit).stream().map(mapper::toOutboxMessage).toList();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OutboxMessage claimFresh(long id, Instant leaseUntil) {
        return jpaRepository.claimFresh(id, leaseUntil).stream().findFirst().map(mapper::toOutboxMessage).orElse(null);
    }

    @Override
    public boolean update(OutboxMessage message) {
        return jpaRepository.updateClaimed(message.id(), message.attempts(), message.status(),
                mapper.toPayloadJson(message.payload()), message.nextAttemptAt(), message.lastError(),
                message.sentAt()) == 1;
    }

    @Override
    public int deleteFinishedCreatedBefore(Instant cutoff, int batchSize) {
        return jpaRepository.deleteFinishedCreatedBefore(cutoff, batchSize);
    }

    @Override
    public int erasePayloadsByPhones(long companyId, Collection<String> phones, String reason) {
        return jpaRepository.erasePayloadsByPhones(companyId, phones, reason);
    }
}
