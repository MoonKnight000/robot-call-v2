package uz.murodjon.robotcallv2.operator.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.operator.application.port.output.OperatorStatusLogRepository;
import uz.murodjon.robotcallv2.operator.domain.entity.OperatorStatusChange;
import uz.murodjon.robotcallv2.operator.infrastructure.persistence.entity.OperatorStatusLogEntity;
import uz.murodjon.robotcallv2.operator.infrastructure.persistence.repository.OperatorStatusLogJpaRepository;

import java.time.Instant;
import java.util.List;

@Component
public class OperatorStatusLogRepositoryAdapter implements OperatorStatusLogRepository {

    private final OperatorStatusLogJpaRepository jpaRepository;

    public OperatorStatusLogRepositoryAdapter(OperatorStatusLogJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public void record(OperatorStatusChange change) {
        jpaRepository.closeOpen(change.userId(), change.startedAt());
        OperatorStatusLogEntity entity = new OperatorStatusLogEntity();
        entity.setCompanyId(change.companyId());
        entity.setUserId(change.userId());
        entity.setStatus(change.status());
        entity.setReason(change.reason());
        entity.setStartedAt(change.startedAt());
        jpaRepository.save(entity);
    }

    @Override
    public void closeOpen(long userId, Instant at) {
        jpaRepository.closeOpen(userId, at);
    }

    @Override
    public List<OperatorStatusChange> findOpen() {
        return jpaRepository.findByEndedAtIsNull().stream()
                .map(e -> new OperatorStatusChange(e.getUserId(), e.getCompanyId(), e.getStatus(), e.getReason(),
                        e.getStartedAt(), e.getEndedAt()))
                .toList();
    }
}
