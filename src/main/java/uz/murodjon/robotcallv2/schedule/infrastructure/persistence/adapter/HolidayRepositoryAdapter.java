package uz.murodjon.robotcallv2.schedule.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.schedule.application.port.output.HolidayRepository;
import uz.murodjon.robotcallv2.schedule.domain.entity.Holiday;
import uz.murodjon.robotcallv2.schedule.domain.entity.HolidayFilter;
import uz.murodjon.robotcallv2.schedule.infrastructure.persistence.entity.HolidayEntity;
import uz.murodjon.robotcallv2.schedule.infrastructure.persistence.mapper.BusinessScheduleEntityMapper;
import uz.murodjon.robotcallv2.schedule.infrastructure.persistence.repository.HolidayJpaRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Component
public class HolidayRepositoryAdapter implements HolidayRepository {

    private final HolidayJpaRepository jpaRepository;
    private final BusinessScheduleEntityMapper mapper;
    private final CompanyJpaRepository companyJpaRepository;

    public HolidayRepositoryAdapter(HolidayJpaRepository jpaRepository, BusinessScheduleEntityMapper mapper,
                                    CompanyJpaRepository companyJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.companyJpaRepository = companyJpaRepository;
    }

    @Override
    public long create(long companyId, Holiday holiday) {
        HolidayEntity entity = new HolidayEntity();
        entity.setCompany(companyJpaRepository.getReferenceById(companyId));
        entity.setCreatedAt(Instant.now());
        copy(holiday, entity);
        return jpaRepository.save(entity).getId();
    }

    @Override
    public void update(long companyId, long id, Holiday holiday) {
        jpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(entity -> {
            copy(holiday, entity);
            jpaRepository.save(entity);
        });
    }

    @Override
    public void delete(long companyId, long id) {
        jpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(jpaRepository::delete);
    }

    @Override
    public Holiday find(long companyId, long id) {
        return jpaRepository.findByIdAndCompanyId(id, companyId).map(mapper::toHoliday).orElse(null);
    }

    @Override
    public boolean existsByDate(long companyId, LocalDate date, Long excludeId) {
        return excludeId == null
                ? jpaRepository.existsByCompanyIdAndDate(companyId, date)
                : jpaRepository.existsByCompanyIdAndDateAndIdNot(companyId, date, excludeId);
    }

    @Override
    public List<Holiday> findAll(long companyId, HolidayFilter filter) {
        List<HolidayEntity> rows = filter.year() == null
                ? jpaRepository.findByCompanyId(companyId, filter.pageable())
                : jpaRepository.findByCompanyIdAndDateBetween(companyId, LocalDate.of(filter.year(), 1, 1),
                LocalDate.of(filter.year(), 12, 31), filter.pageable());
        return rows.stream().map(mapper::toHoliday).toList();
    }

    @Override
    public long count(long companyId, HolidayFilter filter) {
        return filter.year() == null
                ? jpaRepository.countByCompanyId(companyId)
                : jpaRepository.countByCompanyIdAndDateBetween(companyId, LocalDate.of(filter.year(), 1, 1),
                LocalDate.of(filter.year(), 12, 31));
    }

    @Override
    public List<Holiday> findBetween(long companyId, LocalDate from, LocalDate to) {
        return jpaRepository.findByCompanyIdAndDateBetweenOrderByDateAsc(companyId, from, to).stream()
                .map(mapper::toHoliday)
                .toList();
    }

    private static void copy(Holiday holiday, HolidayEntity entity) {
        entity.setDate(holiday.date());
        entity.setName(holiday.name());
        entity.setKind(holiday.kind());
        entity.setOpenTime(holiday.openTime());
        entity.setCloseTime(holiday.closeTime());
    }
}
