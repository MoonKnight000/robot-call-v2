package uz.murodjon.robotcallv2.wrapup.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.wrapup.application.port.output.WrapUpCodeRepository;
import uz.murodjon.robotcallv2.wrapup.domain.entity.WrapUpCode;
import uz.murodjon.robotcallv2.wrapup.domain.entity.WrapUpCodeFilter;
import uz.murodjon.robotcallv2.wrapup.infrastructure.persistence.entity.WrapUpCodeEntity;
import uz.murodjon.robotcallv2.wrapup.infrastructure.persistence.mapper.WrapUpCodeEntityMapper;
import uz.murodjon.robotcallv2.wrapup.infrastructure.persistence.repository.WrapUpCodeJpaRepository;

import java.time.Instant;
import java.util.List;

@Component
public class WrapUpCodeRepositoryAdapter implements WrapUpCodeRepository {

    private final WrapUpCodeJpaRepository jpaRepository;
    private final WrapUpCodeEntityMapper mapper;

    public WrapUpCodeRepositoryAdapter(WrapUpCodeJpaRepository jpaRepository, WrapUpCodeEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public WrapUpCode create(long companyId, WrapUpCode code) {
        WrapUpCodeEntity entity = new WrapUpCodeEntity();
        entity.setCompanyId(companyId);
        entity.setCreatedAt(Instant.now());
        mapper.applyEditableFields(entity, code);
        return mapper.toWrapUpCode(jpaRepository.save(entity));
    }

    @Override
    @Transactional
    public WrapUpCode update(long companyId, long id, WrapUpCode code) {
        return jpaRepository.findByIdAndCompanyId(id, companyId)
                .map(entity -> {
                    mapper.applyEditableFields(entity, code);
                    return mapper.toWrapUpCode(jpaRepository.save(entity));
                })
                .orElse(null);
    }

    @Override
    @Transactional
    public void delete(long companyId, long id) {
        jpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(jpaRepository::delete);
    }

    @Override
    public WrapUpCode find(long companyId, long id) {
        return jpaRepository.findByIdAndCompanyId(id, companyId).map(mapper::toWrapUpCode).orElse(null);
    }

    @Override
    public List<WrapUpCode> findAll(long companyId, WrapUpCodeFilter filter) {
        List<WrapUpCodeEntity> rows = filter.enabled() == null
                ? jpaRepository.findByCompanyId(companyId, filter.pageable())
                : jpaRepository.findByCompanyIdAndEnabled(companyId, filter.enabled(), filter.pageable());
        return rows.stream().map(mapper::toWrapUpCode).toList();
    }

    @Override
    public long count(long companyId, WrapUpCodeFilter filter) {
        return filter.enabled() == null
                ? jpaRepository.countByCompanyId(companyId)
                : jpaRepository.countByCompanyIdAndEnabled(companyId, filter.enabled());
    }
}
