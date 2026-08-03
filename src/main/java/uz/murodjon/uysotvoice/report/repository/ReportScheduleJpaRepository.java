package uz.murodjon.uysotvoice.report.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.report.entity.ReportScheduleEntity;

import java.util.List;
import java.util.Optional;

/** Spring Data repository for {@link ReportScheduleEntity}. */
@Repository
public interface ReportScheduleJpaRepository extends JpaRepository<ReportScheduleEntity, Long> {

    Optional<ReportScheduleEntity> findByIdAndCompanyId(long id, long companyId);

    List<ReportScheduleEntity> findByCompanyId(long companyId, Pageable pageable);

    long countByCompanyId(long companyId);

    /** The dispatch sweep's own scan (§10.10) — every company, since it is not a per-request read. */
    List<ReportScheduleEntity> findByEnabledTrue();
}
