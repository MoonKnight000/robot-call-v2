package uz.murodjon.robotcallv2.knowledgebase.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * @param agentIds the agents the collection is attached to. On create null means none; on update null
 *                 leaves the attachments as they are and an empty list detaches every agent
 */
public record SaveKnowledgeCollectionRequest(
        @NotBlank @Size(max = 200) String name,
        List<Long> agentIds
) {
}
