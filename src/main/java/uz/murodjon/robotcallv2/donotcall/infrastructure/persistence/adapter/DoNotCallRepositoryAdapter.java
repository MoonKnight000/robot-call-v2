package uz.murodjon.robotcallv2.donotcall.infrastructure.persistence.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.donotcall.application.dto.DoNotCallFilter;
import uz.murodjon.robotcallv2.donotcall.application.mapper.DoNotCallMapper;
import uz.murodjon.robotcallv2.donotcall.application.port.output.DoNotCallRepository;
import uz.murodjon.robotcallv2.donotcall.domain.entity.DoNotCall;
import uz.murodjon.robotcallv2.donotcall.domain.enums.DoNotCallSource;
import uz.murodjon.robotcallv2.donotcall.infrastructure.persistence.repository.DoNotCallJpaRepository;

import java.time.Instant;
import java.util.List;

@Component
public class DoNotCallRepositoryAdapter implements DoNotCallRepository {

    private static final Logger log = LoggerFactory.getLogger(DoNotCallRepositoryAdapter.class);

    private final DoNotCallJpaRepository jpaRepository;
    private final DoNotCallMapper mapper;

    public DoNotCallRepositoryAdapter(DoNotCallJpaRepository jpaRepository, DoNotCallMapper mapper) {
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
            log.info("Do-not-call recorded for company {} phone {} ({}): {}", companyId, phone, source, reason);
        } catch (Exception e) {
            log.warn("Do-not-call write failed for company {} phone {}: {}", companyId, phone, e.getMessage());
        }
    }

    @Override
    public boolean contains(long companyId, String phone) {
        if (phone == null || phone.isBlank()) {
            return false;
        }
        try {
            return jpaRepository.existsByCompanyIdAndPhoneAndRemovedAtIsNull(companyId, phone);
        } catch (Exception e) {
            log.warn("Do-not-call lookup failed for company {} phone {}: {}", companyId, phone, e.getMessage());
            return false;
        }
    }

    @Override
    public List<DoNotCall> findAll(long companyId, DoNotCallFilter filter) {
        return jpaRepository.findByCompanyIdAndRemovedAtIsNull(companyId, filter.pageable()).stream()
                .map(mapper::entityToDomain)
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
