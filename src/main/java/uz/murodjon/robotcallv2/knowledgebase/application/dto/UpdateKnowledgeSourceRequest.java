package uz.murodjon.robotcallv2.knowledgebase.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Renames a source or moves it to another agent. Changing the {@code url} re-indexes it;
 * to replace a file, add a new source instead — the old chunks belong to the old file.
 * {@code refreshMinutes} changes how often a URL catalog feed is fetched again.
 */
public record UpdateKnowledgeSourceRequest(
        @Size(max = 200) String name,
        Long agentId,
        @Size(max = 2048) String url,
        @Min(15) Integer refreshMinutes
) {
}
