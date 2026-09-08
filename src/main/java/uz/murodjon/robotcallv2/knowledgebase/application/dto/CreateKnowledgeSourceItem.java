package uz.murodjon.robotcallv2.knowledgebase.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.knowledgebase.domain.enums.KnowledgeSourceType;

/**
 * One document of a create request. A file source names the {@code storedFileId} returned
 * by {@code POST /api/files}; a {@code URL} source names the address instead.
 */
public record CreateKnowledgeSourceItem(
        @NotBlank @Size(max = 200) String name,
        @NotNull KnowledgeSourceType sourceType,
        @Size(max = 2048) String url,
        Long storedFileId,
        @Size(max = 255) String originalFileName
) {
}
