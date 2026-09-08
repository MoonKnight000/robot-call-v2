package uz.murodjon.robotcallv2.conversion.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.conversion.application.mapper.ConversionGoalMapper;
import uz.murodjon.robotcallv2.conversion.application.port.output.ConversionGoalRepository;
import uz.murodjon.robotcallv2.conversion.domain.entity.ConversionGoal;
import uz.murodjon.robotcallv2.conversion.infrastructure.persistence.entity.ConversionGoalEntity;
import uz.murodjon.robotcallv2.conversion.infrastructure.persistence.repository.ConversionGoalJpaRepository;

import java.util.List;
import java.util.Optional;

@Component
public class ConversionGoalRepositoryAdapter implements ConversionGoalRepository {

    private final ConversionGoalJpaRepository jpaRepository;
    private final CompanyJpaRepository companyJpaRepository;
    private final ConversionGoalMapper mapper;

    public ConversionGoalRepositoryAdapter(ConversionGoalJpaRepository jpaRepository,
                                           CompanyJpaRepository companyJpaRepository,
                                           ConversionGoalMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.companyJpaRepository = companyJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public ConversionGoal save(ConversionGoal goal) {
        CompanyEntity company = companyJpaRepository.getReferenceById(goal.companyId());
        return mapper.toConversionGoal(jpaRepository.save(mapper.toEntity(goal, company)));
    }

    @Override
    public List<ConversionGoal> findByCompanyId(long companyId) {
        return jpaRepository.findByCompanyIdOrderByGoalKeyAsc(companyId).stream()
                .map(mapper::toConversionGoal)
                .toList();
    }

    @Override
    public Optional<ConversionGoal> findByCompanyIdAndGoalKey(long companyId, String goalKey) {
        return jpaRepository.findByCompanyIdAndGoalKey(companyId, goalKey).map(mapper::toConversionGoal);
    }

    @Override
    public Optional<ConversionGoal> findByCompanyIdAndId(long companyId, long id) {
        return jpaRepository.findByCompanyIdAndId(companyId, id).map(mapper::toConversionGoal);
    }
}
