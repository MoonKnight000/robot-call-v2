package uz.murodjon.robotcallv2.aimodel.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.aiagent.domain.enums.PipelineMode;
import uz.murodjon.robotcallv2.aimodel.application.mapper.AiModelMapper;
import uz.murodjon.robotcallv2.aimodel.application.port.output.AiModelRepository;
import uz.murodjon.robotcallv2.aimodel.domain.entity.AiModel;
import uz.murodjon.robotcallv2.aimodel.domain.enums.AiModelKind;
import uz.murodjon.robotcallv2.aimodel.infrastructure.persistence.repository.AiModelJpaRepository;

import java.util.List;

@Component
public class AiModelRepositoryAdapter implements AiModelRepository {

    private final AiModelJpaRepository jpaRepository;
    private final AiModelMapper mapper;

    public AiModelRepositoryAdapter(AiModelJpaRepository jpaRepository, AiModelMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public List<AiModel> findByKindAndMode(AiModelKind kind, PipelineMode mode) {
        return (mode == null
                ? jpaRepository.findAllByKindOrderByIdAsc(kind)
                : jpaRepository.findAllByKindAndModeOrderByIdAsc(kind, mode)).stream()
                .map(mapper::toAiModel)
                .toList();
    }

    @Override
    public AiModel findById(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return jpaRepository.findById(id.trim()).map(mapper::toAiModel).orElse(null);
    }
}
