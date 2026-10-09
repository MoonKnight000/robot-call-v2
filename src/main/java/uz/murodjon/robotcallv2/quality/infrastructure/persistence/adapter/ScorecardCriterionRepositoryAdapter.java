package uz.murodjon.robotcallv2.quality.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.quality.application.port.output.ScorecardCriterionRepository;
import uz.murodjon.robotcallv2.quality.domain.entity.ScorecardCriterion;
import uz.murodjon.robotcallv2.quality.domain.entity.ScorecardCriterionFilter;
import uz.murodjon.robotcallv2.quality.infrastructure.persistence.entity.ScorecardCriterionEntity;
import uz.murodjon.robotcallv2.quality.infrastructure.persistence.mapper.ScorecardCriterionEntityMapper;
import uz.murodjon.robotcallv2.quality.infrastructure.persistence.repository.ScorecardCriterionJpaRepository;

import java.time.Instant;
import java.util.List;

@Component
public class ScorecardCriterionRepositoryAdapter implements ScorecardCriterionRepository {

    private final ScorecardCriterionJpaRepository jpaRepository;
    private final ScorecardCriterionEntityMapper mapper;

    public ScorecardCriterionRepositoryAdapter(ScorecardCriterionJpaRepository jpaRepository,
                                               ScorecardCriterionEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public ScorecardCriterion create(long companyId, ScorecardCriterion criterion) {
        ScorecardCriterionEntity entity = new ScorecardCriterionEntity();
        entity.setCompanyId(companyId);
        entity.setCreatedAt(Instant.now());
        mapper.applyEditableFields(entity, criterion);
        return mapper.toScorecardCriterion(jpaRepository.save(entity));
    }

    @Override
    @Transactional
    public ScorecardCriterion update(long companyId, long id, ScorecardCriterion criterion) {
        return jpaRepository.findByIdAndCompanyId(id, companyId)
                .map(entity -> {
                    mapper.applyEditableFields(entity, criterion);
                    return mapper.toScorecardCriterion(jpaRepository.save(entity));
                })
                .orElse(null);
    }

    @Override
    @Transactional
    public void delete(long companyId, long id) {
        jpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(jpaRepository::delete);
    }

    @Override
    public ScorecardCriterion find(long companyId, long id) {
        return jpaRepository.findByIdAndCompanyId(id, companyId).map(mapper::toScorecardCriterion).orElse(null);
    }

    @Override
    public List<ScorecardCriterion> findAll(long companyId, ScorecardCriterionFilter filter) {
        List<ScorecardCriterionEntity> rows = filter.enabled() == null
                ? jpaRepository.findByCompanyId(companyId, filter.pageable())
                : jpaRepository.findByCompanyIdAndEnabled(companyId, filter.enabled(), filter.pageable());
        return rows.stream().map(mapper::toScorecardCriterion).toList();
    }

    @Override
    public long count(long companyId, ScorecardCriterionFilter filter) {
        return filter.enabled() == null
                ? jpaRepository.countByCompanyId(companyId)
                : jpaRepository.countByCompanyIdAndEnabled(companyId, filter.enabled());
    }

    @Override
    public List<ScorecardCriterion> findEnabledByCompanyId(long companyId) {
        return jpaRepository.findByCompanyIdAndEnabledTrueOrderById(companyId).stream()
                .map(mapper::toScorecardCriterion).toList();
    }
}
