package uz.murodjon.robotcallv2.promise.infrastructure.persistence.adapter;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.promise.application.port.output.PaymentPromiseRepository;
import uz.murodjon.robotcallv2.promise.domain.entity.PaymentPromise;
import uz.murodjon.robotcallv2.promise.domain.entity.PaymentPromiseFilter;
import uz.murodjon.robotcallv2.promise.domain.entity.PaymentPromiseStats;
import uz.murodjon.robotcallv2.promise.domain.entity.PaymentPromiseTally;
import uz.murodjon.robotcallv2.promise.domain.enums.PaymentPromiseStatus;
import uz.murodjon.robotcallv2.promise.infrastructure.persistence.entity.PaymentPromiseEntity;
import uz.murodjon.robotcallv2.promise.infrastructure.persistence.mapper.PaymentPromiseEntityMapper;
import uz.murodjon.robotcallv2.promise.infrastructure.persistence.repository.PaymentPromiseJpaRepository;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PaymentPromiseRepositoryAdapter implements PaymentPromiseRepository {

    private final PaymentPromiseJpaRepository jpaRepository;
    private final PaymentPromiseEntityMapper mapper;

    public PaymentPromiseRepositoryAdapter(PaymentPromiseJpaRepository jpaRepository,
                                           PaymentPromiseEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public void createIgnoringConflict(PaymentPromise promise) {
        jpaRepository.insertIgnoringConflict(promise.companyId(), promise.callAttemptId(), promise.targetId(),
                promise.phone(), promise.promisedDate(), promise.promisedAmount());
    }

    @Override
    public void cancelOpenByCompanyIdAndPhone(long companyId, String phone, long exceptCallAttemptId,
                                              Instant resolvedAt) {
        jpaRepository.cancelOpenByCompanyIdAndPhone(companyId, phone, exceptCallAttemptId, resolvedAt);
    }

    @Override
    public PaymentPromise findByCompanyIdAndId(long companyId, long id) {
        return jpaRepository.findByCompanyIdAndId(companyId, id).map(mapper::toPaymentPromise).orElse(null);
    }

    @Override
    public PageableData<PaymentPromise> findPageByCompanyId(long companyId, PaymentPromiseFilter filter) {
        Page<PaymentPromiseEntity> page = jpaRepository.findAll(specification(companyId, filter), filter.pageable());
        return PageableData.of(page.getContent().stream().map(mapper::toPaymentPromise).toList(),
                filter.pageable().getPageNumber(), filter.pageable().getPageSize(), page.getTotalElements());
    }

    @Override
    public PaymentPromiseStats findStatsByCompanyId(long companyId, LocalDate from, LocalDate to) {
        return mapper.toPaymentPromiseStats(jpaRepository.summarizeByCompanyId(companyId, from, to));
    }

    @Override
    public List<PaymentPromise> findOpenAfterId(long afterId, int limit) {
        return jpaRepository.findOpenAfterId(afterId, PageRequest.of(0, limit)).stream()
                .map(mapper::toPaymentPromise)
                .toList();
    }

    @Override
    public boolean updateStatusIfOpen(long id, PaymentPromiseStatus status, Instant resolvedAt) {
        return jpaRepository.updateStatusIfOpen(id, status.name(), resolvedAt) > 0;
    }

    @Override
    public void markReminded(long id, Instant sentAt) {
        jpaRepository.markReminded(id, sentAt);
    }

    private static Specification<PaymentPromiseEntity> specification(long companyId, PaymentPromiseFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("companyId"), companyId));
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            String phone = filter.phone() != null ? PhoneNumbers.normalize(filter.phone()) : null;
            if (phone != null && !phone.isBlank()) {
                predicates.add(cb.equal(root.get("phone"), phone));
            }
            if (filter.promisedFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("promisedDate"), filter.promisedFrom()));
            }
            if (filter.promisedTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("promisedDate"), filter.promisedTo()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    @Override
    public Map<String, PaymentPromiseTally> findTalliesByPhones(long companyId, Collection<String> phones, Instant since) {
        Map<String, PaymentPromiseTally> tallies = new HashMap<>();
        for (Object[] row : jpaRepository.countResolvedByPhone(companyId, phones, since)) {
            tallies.put((String) row[0],
                    new PaymentPromiseTally(((Number) row[1]).intValue(), ((Number) row[2]).intValue()));
        }
        return tallies;
    }
}
