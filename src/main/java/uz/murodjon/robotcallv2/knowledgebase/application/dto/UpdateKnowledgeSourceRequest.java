package uz.murodjon.robotcallv2.knowledgebase.application.dto;

import jakarta.validation.constraints.Size;

/**
 * Renames a source or moves it to another agent. Changing the {@code url} re-indexes it;
 * to replace a file, add a new source instead — the old chunks belong to the old file.
 */
public record UpdateKnowledgeSourceRequest(
        @Size(max = 200) String name,
        Long agentId,
        @Size(max = 2048) String url
) {
}
