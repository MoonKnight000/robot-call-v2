package uz.murodjon.robotcallv2.aimodel.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import uz.murodjon.robotcallv2.aimodel.infrastructure.persistence.entity.AiModelEntity;
import uz.murodjon.robotcallv2.engine.domain.enums.PipelineMode;

import java.util.List;

@Repository
public interface AiModelJpaRepository extends JpaRepository<AiModelEntity, String> {

    List<AiModelEntity> findAllByModeOrderByIdAsc(PipelineMode mode);
}
