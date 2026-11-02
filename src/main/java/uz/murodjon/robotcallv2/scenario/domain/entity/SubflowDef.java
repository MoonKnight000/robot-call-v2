package uz.murodjon.robotcallv2.scenario.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * A reusable group of stages that a flow embeds as one {@code SUBFLOW} stage: the call enters it at
 * {@code startStageId}, runs its stages, and leaves through every stage that has no way on, to
 * whatever the embedding stage's own transitions say. It is flattened into the flow when the
 * scenario is resolved for a call ({@code SubflowExpander}), so a call never knows it was in one.
 *
 * @param id           what a {@code SUBFLOW} stage's {@code action.subflowId} names; unique in the scenario
 * @param title        the name the editor shows; null = the editor shows {@code id}
 * @param startStageId the stage the call enters on; null = the first of {@code stages}
 * @param stages       the subflow's own stages; never null. Their ids need only be unique inside it
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SubflowDef(String id, String title, String startStageId, List<StageDef> stages) {

    public SubflowDef {
        stages = stages == null ? List.of() : List.copyOf(stages);
    }

    /** The stage the call enters on: {@code startStageId}, or the first stage when unset or unknown; null when empty. */
    public StageDef findStartStage() {
        if (startStageId != null) {
            for (StageDef stage : stages) {
                if (startStageId.equalsIgnoreCase(stage.id())) {
                    return stage;
                }
            }
        }
        return stages.isEmpty() ? null : stages.get(0);
    }
}
