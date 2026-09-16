package uz.murodjon.robotcallv2.inbound.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.inbound.infrastructure.persistence.mapper.InboundCallerRuleEntityMapper;
import uz.murodjon.robotcallv2.inbound.application.port.output.InboundCallerRuleRepository;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundCallerRule;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundCallerRuleFilter;
import uz.murodjon.robotcallv2.inbound.infrastructure.persistence.entity.InboundCallerRuleEntity;
import uz.murodjon.robotcallv2.inbound.infrastructure.persistence.repository.InboundCallerRuleJpaRepository;

import java.time.Instant;
import java.util.List;

@Component
public class InboundCallerRuleRepositoryAdapter implements InboundCallerRuleRepository {

    private final InboundCallerRuleJpaRepository jpaRepository;
    private final InboundCallerRuleEntityMapper mapper;

    public InboundCallerRuleRepositoryAdapter(InboundCallerRuleJpaRepository jpaRepository, InboundCallerRuleEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public long create(long companyId, InboundCallerRule rule) {
        InboundCallerRuleEntity entity = new InboundCallerRuleEntity();
        entity.setCompanyId(companyId);
        entity.setCreatedAt(Instant.now());
        copy(rule, entity);
        return jpaRepository.save(entity).getId();
    }

    @Override
    public void update(long companyId, long id, InboundCallerRule rule) {
        jpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(entity -> {
            copy(rule, entity);
            jpaRepository.save(entity);
        });
    }

    @Override
    public void delete(long companyId, long id) {
        jpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(jpaRepository::delete);
    }

    @Override
    public InboundCallerRule find(long companyId, long id) {
        return jpaRepository.findByIdAndCompanyId(id, companyId).map(mapper::toInboundCallerRule).orElse(null);
    }

    @Override
    public boolean existsByPrefix(long companyId, String phonePrefix, Long excludeId) {
        return excludeId == null
                ? jpaRepository.existsByCompanyIdAndPhonePrefix(companyId, phonePrefix)
                : jpaRepository.existsByCompanyIdAndPhonePrefixAndIdNot(companyId, phonePrefix, excludeId);
    }

    @Override
    public List<InboundCallerRule> findAll(long companyId, InboundCallerRuleFilter filter) {
        return jpaRepository.findByCompanyId(companyId, filter.pageable()).stream()
                .map(mapper::toInboundCallerRule)
                .toList();
    }

    @Override
    public long count(long companyId) {
        return jpaRepository.countByCompanyId(companyId);
    }

    @Override
    public List<InboundCallerRule> findByCompanyId(long companyId) {
        return jpaRepository.findByCompanyId(companyId).stream().map(mapper::toInboundCallerRule).toList();
    }

    private static void copy(InboundCallerRule rule, InboundCallerRuleEntity entity) {
        entity.setPhonePrefix(rule.phonePrefix());
        entity.setAction(rule.action());
        entity.setMessage(rule.message());
        entity.setNote(rule.note());
    }
}
