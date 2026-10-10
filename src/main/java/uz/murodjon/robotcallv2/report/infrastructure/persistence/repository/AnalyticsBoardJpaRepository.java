package uz.murodjon.robotcallv2.report.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uz.murodjon.robotcallv2.report.infrastructure.persistence.entity.AnalyticsBoardEntity;

import java.util.List;
import java.util.Optional;

public interface AnalyticsBoardJpaRepository extends JpaRepository<AnalyticsBoardEntity, Long> {

    Optional<AnalyticsBoardEntity> findByIdAndCompanyId(Long id, Long companyId);

    List<AnalyticsBoardEntity> findAllByCompanyIdOrderByCreatedAtDesc(Long companyId);

    @Query("SELECT b FROM AnalyticsBoardEntity b LEFT JOIN FETCH b.widgets WHERE b.id = :id AND b.companyId = :companyId")
    Optional<AnalyticsBoardEntity> findByIdAndCompanyIdWithWidgets(Long id, Long companyId);
}
