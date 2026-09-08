package uz.murodjon.robotcallv2.aimodel.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.murodjon.robotcallv2.aiagent.domain.enums.PipelineMode;
import uz.murodjon.robotcallv2.aimodel.domain.enums.AiModelKind;
import uz.murodjon.robotcallv2.aimodel.infrastructure.persistence.entity.AiModelEntity;

import java.util.List;

@Repository
public interface AiModelJpaRepository extends JpaRepository<AiModelEntity, String> {

    List<AiModelEntity> findAllByKindOrderByIdAsc(AiModelKind kind);

    List<AiModelEntity> findAllByKindAndModeOrderByIdAsc(AiModelKind kind, PipelineMode mode);
}
