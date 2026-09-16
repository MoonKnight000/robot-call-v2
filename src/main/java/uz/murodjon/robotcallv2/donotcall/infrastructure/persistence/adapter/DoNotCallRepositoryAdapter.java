package uz.murodjon.robotcallv2.donotcall.infrastructure.persistence.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.donotcall.application.port.output.DoNotCallRepository;
import uz.murodjon.robotcallv2.donotcall.domain.entity.DoNotCall;
import uz.murodjon.robotcallv2.donotcall.domain.entity.DoNotCallFilter;
import uz.murodjon.robotcallv2.donotcall.domain.enums.DoNotCallSource;
import uz.murodjon.robotcallv2.donotcall.infrastructure.persistence.mapper.DoNotCallEntityMapper;
import uz.murodjon.robotcallv2.donotcall.infrastructure.persistence.repository.DoNotCallJpaRepository;
import uz.murodjon.robotcallv2.shared.util.PhoneMasker;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class DoNotCallRepositoryAdapter implements DoNotCallRepository {

    private static final Logger log = LoggerFactory.getLogger(DoNotCallRepositoryAdapter.class);

    /** Phones per IN list: an import can bring tens of thousands, and PostgreSQL binds at most 32 767 parameters. */
    private static final int LOOKUP_BATCH_SIZE = 1000;

    private final DoNotCallJpaRepository jpaRepository;
    private final DoNotCallEntityMapper mapper;

    public DoNotCallRepositoryAdapter(DoNotCallJpaRepository jpaRepository, DoNotCallEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public void add(long companyId, String phone, String reason, DoNotCallSource source) {
        if (phone == null || phone.isBlank()) {
            return;
        }
        try {
            jpaRepository.upsert(phone, reason, (source != null ? source : DoNotCallSource.CALL).name(), Instant.now(), companyId);
            log.info("Do-not-call recorded for company {} phone {} ({}): {}", companyId, PhoneMasker.mask(phone),
                    source, reason);
        } catch (Exception e) {
            log.warn("Do-not-call write failed for company {} phone {}: {}", companyId, PhoneMasker.mask(phone),
                    e.getMessage());
        }
    }

    @Override
    public boolean existsByPhone(long companyId, String phone) {
        if (phone == null || phone.isBlank()) {
            return false;
        }
        try {
            return jpaRepository.existsByCompanyIdAndPhoneAndRemovedAtIsNull(companyId, phone);
        } catch (Exception e) {
            log.warn("Do-not-call lookup failed for company {} phone {}: {}", companyId, PhoneMasker.mask(phone),
                    e.getMessage());
            return false;
        }
    }

    @Override
    public Set<String> findListedPhones(long companyId, Collection<String> phones) {
        List<String> candidates = phones.stream()
                .filter(phone -> phone != null && !phone.isBlank())
                .distinct()
                .toList();
        Set<String> listed = new HashSet<>();
        try {
            for (int from = 0; from < candidates.size(); from += LOOKUP_BATCH_SIZE) {
                listed.addAll(jpaRepository.findActivePhones(companyId,
                        candidates.subList(from, Math.min(from + LOOKUP_BATCH_SIZE, candidates.size()))));
            }
        } catch (Exception e) {
            // Fails open like existsByPhone(): the dialer's claim checks the list again before dialling.
            log.warn("Do-not-call lookup failed for company {} ({} numbers): {}", companyId, candidates.size(),
                    e.getMessage());
        }
        return listed;
    }

    @Override
    public List<DoNotCall> findAll(long companyId, DoNotCallFilter filter) {
        return jpaRepository.findByCompanyIdAndRemovedAtIsNull(companyId, filter.pageable()).stream()
                .map(mapper::toDoNotCall)
                .toList();
    }

    @Override
    public long count(long companyId, DoNotCallFilter filter) {
        return jpaRepository.countByCompanyIdAndRemovedAtIsNull(companyId);
    }

    @Override
    public boolean remove(long companyId, String phone, String removedBy) {
        int updated = jpaRepository.remove(companyId, phone, Instant.now(), removedBy);
        return updated > 0;
    }
}
