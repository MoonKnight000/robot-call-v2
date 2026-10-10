package uz.murodjon.robotcallv2.report.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.murodjon.robotcallv2.report.infrastructure.persistence.entity.AnalyticsBoardWidgetEntity;

import java.util.Optional;

public interface AnalyticsBoardWidgetJpaRepository extends JpaRepository<AnalyticsBoardWidgetEntity, Long> {

    Optional<AnalyticsBoardWidgetEntity> findByIdAndBoardId(Long id, Long boardId);
}
