package uz.murodjon.robotcallv2.knowledgebase.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateKnowledgeSourceRequest(
        Long agentId,
        @NotEmpty List<@Valid CreateKnowledgeSourceItem> documents
) {
}
