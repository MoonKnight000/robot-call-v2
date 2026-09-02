package uz.murodjon.robotcallv2.report.infrastructure.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import uz.murodjon.robotcallv2.report.infrastructure.persistence.entity.ReportScheduleEntity;

import java.util.List;
import java.util.Optional;

/** Spring Data repository for {@link ReportScheduleEntity}. */
@Repository
public interface ReportScheduleJpaRepository extends JpaRepository<ReportScheduleEntity, Long> {

    Optional<ReportScheduleEntity> findByIdAndCompany_Id(long id, long companyId);

    List<ReportScheduleEntity> findByCompany_Id(long companyId, Pageable pageable);

    long countByCompany_Id(long companyId);

    /** The dispatch sweep's own scan (§10.10) — every company, since it is not a per-request read. */
    List<ReportScheduleEntity> findByEnabledTrue();
}
