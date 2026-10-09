package uz.murodjon.robotcallv2.webhook.infrastructure.persistence.adapter;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.webhook.application.port.output.WebhookDeliveryRepository;
import uz.murodjon.robotcallv2.webhook.domain.entity.WebhookDelivery;
import uz.murodjon.robotcallv2.webhook.domain.entity.WebhookDeliveryFilter;
import uz.murodjon.robotcallv2.webhook.domain.enums.WebhookDeliveryStatus;
import uz.murodjon.robotcallv2.webhook.infrastructure.persistence.entity.WebhookDeliveryEntity;
import uz.murodjon.robotcallv2.webhook.infrastructure.persistence.mapper.WebhookDeliveryEntityMapper;
import uz.murodjon.robotcallv2.webhook.infrastructure.persistence.repository.WebhookDeliveryJpaRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Repository
public class WebhookDeliveryRepositoryAdapter implements WebhookDeliveryRepository {

    private final WebhookDeliveryJpaRepository jpaRepository;
    private final WebhookDeliveryEntityMapper mapper;

    public WebhookDeliveryRepositoryAdapter(WebhookDeliveryJpaRepository jpaRepository, WebhookDeliveryEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public long create(WebhookDelivery delivery) {
        WebhookDeliveryEntity entity = new WebhookDeliveryEntity();
        entity.setDeliveryId(delivery.deliveryId());
        entity.setCompanyId(delivery.companyId());
        entity.setSource(delivery.source());
        entity.setHost(delivery.host());
        entity.setSealedTarget(delivery.sealedTarget());
        entity.setPayload(mapper.writePayload(delivery.payload()));
        entity.setStatus(delivery.status());
        entity.setAttempts(delivery.attempts());
        entity.setNextAttemptAt(delivery.nextAttemptAt());
        entity.setLastError(delivery.lastError());
        entity.setCreatedAt(Instant.now());
        return jpaRepository.save(entity).getId();
    }

    @Override
    public List<WebhookDelivery> findDue(Instant now, int limit) {
        return jpaRepository.findByStatusAndNextAttemptAtLessThanEqualOrderById(
                        WebhookDeliveryStatus.PENDING, now, PageRequest.of(0, limit)).stream()
                .map(mapper::toWebhookDelivery)
                .toList();
    }

    @Override
    public boolean claim(long id, Instant now, Instant lease) {
        return jpaRepository.claim(id, WebhookDeliveryStatus.PENDING, now, lease) == 1;
    }

    @Override
    public void markDelivered(long id, int attempts, Instant deliveredAt) {
        jpaRepository.updateState(id, WebhookDeliveryStatus.DELIVERED, attempts, null, null, deliveredAt);
    }

    @Override
    public void markRetry(long id, int attempts, Instant nextAttemptAt, String error) {
        jpaRepository.updateState(id, WebhookDeliveryStatus.PENDING, attempts, nextAttemptAt, error, null);
    }

    @Override
    public void markFailed(long id, int attempts, String error) {
        jpaRepository.updateState(id, WebhookDeliveryStatus.FAILED, attempts, null, error, null);
    }

    @Override
    public int deleteDeliveredBefore(Instant cutoff) {
        return jpaRepository.deleteByStatusBefore(WebhookDeliveryStatus.DELIVERED, cutoff);
    }

    @Override
    public PageableData<WebhookDelivery> findPageByCompanyId(long companyId, WebhookDeliveryFilter filter) {
        Page<WebhookDeliveryEntity> page = jpaRepository.findAll(specification(companyId, filter), filter.pageable());
        return PageableData.of(page.getContent().stream().map(mapper::toWebhookDelivery).toList(),
                filter.pageable().getPageNumber(), filter.pageable().getPageSize(), page.getTotalElements());
    }

    @Override
    public WebhookDelivery findByCompanyIdAndId(long companyId, long id) {
        return jpaRepository.findByCompanyIdAndId(companyId, id).map(mapper::toWebhookDelivery).orElse(null);
    }

    @Override
    public boolean claimFailed(long id, Instant lease) {
        return jpaRepository.claimInStatus(id, WebhookDeliveryStatus.FAILED, WebhookDeliveryStatus.PENDING, lease) == 1;
    }

    private static Specification<WebhookDeliveryEntity> specification(long companyId, WebhookDeliveryFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("companyId"), companyId));
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.source() != null && !filter.source().isBlank()) {
                predicates.add(cb.equal(root.get("source"), filter.source()));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), filter.to()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
