package uz.murodjon.robotcallv2.schedule.infrastructure.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.murodjon.robotcallv2.schedule.infrastructure.persistence.entity.HolidayEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface HolidayJpaRepository extends JpaRepository<HolidayEntity, Long> {

    Optional<HolidayEntity> findByIdAndCompanyId(long id, long companyId);

    List<HolidayEntity> findByCompanyId(long companyId, Pageable pageable);

    List<HolidayEntity> findByCompanyIdAndDateBetween(long companyId, LocalDate from, LocalDate to, Pageable pageable);

    List<HolidayEntity> findByCompanyIdAndDateBetweenOrderByDateAsc(long companyId, LocalDate from, LocalDate to);

    long countByCompanyId(long companyId);

    long countByCompanyIdAndDateBetween(long companyId, LocalDate from, LocalDate to);

    boolean existsByCompanyIdAndDate(long companyId, LocalDate date);

    boolean existsByCompanyIdAndDateAndIdNot(long companyId, LocalDate date, long id);
}
