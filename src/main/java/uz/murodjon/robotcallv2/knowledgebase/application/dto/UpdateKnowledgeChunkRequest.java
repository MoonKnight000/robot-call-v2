package uz.murodjon.robotcallv2.knowledgebase.application.dto;

import jakarta.validation.constraints.Size;

/**
 * A person's change to one passage. {@code content} replaces its text (and its vector); {@code disabled} switches
 * it off or back on. Null leaves that part as it is.
 */
public record UpdateKnowledgeChunkRequest(
        @Size(max = 4000) String content,
        Boolean disabled
) {
}
