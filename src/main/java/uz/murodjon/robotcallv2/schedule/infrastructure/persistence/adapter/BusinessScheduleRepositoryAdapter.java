package uz.murodjon.robotcallv2.schedule.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.schedule.application.port.output.BusinessScheduleRepository;
import uz.murodjon.robotcallv2.schedule.domain.entity.BusinessSchedule;
import uz.murodjon.robotcallv2.schedule.domain.entity.BusinessScheduleFilter;
import uz.murodjon.robotcallv2.schedule.infrastructure.persistence.entity.BusinessScheduleEntity;
import uz.murodjon.robotcallv2.schedule.infrastructure.persistence.mapper.BusinessScheduleEntityMapper;
import uz.murodjon.robotcallv2.schedule.infrastructure.persistence.repository.BusinessScheduleJpaRepository;

import java.time.Instant;
import java.util.List;

@Component
public class BusinessScheduleRepositoryAdapter implements BusinessScheduleRepository {

    private final BusinessScheduleJpaRepository jpaRepository;
    private final BusinessScheduleEntityMapper mapper;
    private final CompanyJpaRepository companyJpaRepository;

    public BusinessScheduleRepositoryAdapter(BusinessScheduleJpaRepository jpaRepository, BusinessScheduleEntityMapper mapper,
                                             CompanyJpaRepository companyJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.companyJpaRepository = companyJpaRepository;
    }

    @Override
    @Transactional
    public long create(long companyId, BusinessSchedule schedule) {
        BusinessScheduleEntity entity = new BusinessScheduleEntity();
        entity.setCompany(companyJpaRepository.getReferenceById(companyId));
        entity.setCreatedAt(Instant.now());
        copy(schedule, entity);
        return jpaRepository.save(entity).getId();
    }

    @Override
    @Transactional
    public void update(long companyId, long id, BusinessSchedule schedule) {
        jpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(entity -> {
            copy(schedule, entity);
            jpaRepository.save(entity);
        });
    }

    @Override
    @Transactional
    public void delete(long companyId, long id) {
        jpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(jpaRepository::delete);
    }

    @Override
    public BusinessSchedule find(long companyId, long id) {
        return jpaRepository.findByIdAndCompanyId(id, companyId).map(mapper::toBusinessSchedule).orElse(null);
    }

    @Override
    public List<BusinessSchedule> findAll(long companyId, BusinessScheduleFilter filter) {
        return jpaRepository.findByCompanyId(companyId, filter.pageable()).stream()
                .map(mapper::toBusinessSchedule)
                .toList();
    }

    @Override
    public long count(long companyId) {
        return jpaRepository.countByCompanyId(companyId);
    }

    private void copy(BusinessSchedule schedule, BusinessScheduleEntity entity) {
        entity.setName(schedule.name());
        entity.setTimezone(schedule.timezone());
        entity.setSlots(mapper.toSlotEntities(schedule.slots()));
    }
}
