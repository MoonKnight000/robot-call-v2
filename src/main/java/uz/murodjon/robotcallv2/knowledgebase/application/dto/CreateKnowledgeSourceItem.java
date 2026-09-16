package uz.murodjon.robotcallv2.knowledgebase.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.knowledgebase.domain.enums.KnowledgeSourceType;

/**
 * One document of a create request. A file source names the {@code storedFileId} returned
 * by {@code POST /api/files}; a {@code URL} source names the address instead. A
 * {@code STRUCTURED_FEED} names either, plus where its items are ({@code feedItemsPath}),
 * which column is the key ({@code feedKeyField}) and, for a URL, how often to fetch it again.
 */
public record CreateKnowledgeSourceItem(
        @NotBlank @Size(max = 200) String name,
        @NotNull KnowledgeSourceType sourceType,
        @Size(max = 2048) String url,
        Long storedFileId,
        @Size(max = 255) String originalFileName,
        @Size(max = 200) String feedItemsPath,
        @Size(max = 100) String feedKeyField,
        @Min(15) Integer refreshMinutes
) {
}
