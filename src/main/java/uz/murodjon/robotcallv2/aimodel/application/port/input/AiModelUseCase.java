package uz.murodjon.robotcallv2.aimodel.application.port.input;

import uz.murodjon.robotcallv2.aiagent.domain.enums.PipelineMode;
import uz.murodjon.robotcallv2.aimodel.domain.entity.AiModel;
import uz.murodjon.robotcallv2.aimodel.domain.enums.AiModelKind;

import java.util.List;

public interface AiModelUseCase {

    /**
     * The models of {@code kind} this build can actually run. {@code mode} narrows to one
     * pipeline's family of ids; null asks for both.
     */
    List<AiModel> findSelectableByKindAndMode(AiModelKind kind, PipelineMode mode);

    /**
     * The catalog row for {@code id}, reachable in this build or not, or null when no
     * such id was ever seeded. Needed to tell "no such model" apart from "that model is
     * another provider's" — a distinction {@link #isSelectable} collapses.
     */
    AiModel find(String id);

    boolean isSelectable(AiModelKind kind, PipelineMode mode, String id);

    List<String> findSelectableIds(AiModelKind kind, PipelineMode mode);
}
