package uz.murodjon.robotcallv2.knowledgebase.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KnowledgeItemUpdateRequest(
        @NotBlank @Size(max = 100) String key,
        @NotBlank @Size(max = 50) String topic,
        @NotBlank @Size(max = 255) String title,
        @NotBlank String answerUz,
        String answerRu,
        String answerEn,
        String keywords,
        boolean active
) {
}
