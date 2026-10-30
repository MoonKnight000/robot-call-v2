package uz.murodjon.robotcallv2.aiagent.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.aiagent.domain.entity.DataEvaluationCriterion;
import uz.murodjon.robotcallv2.aiagent.domain.entity.DataExtractionField;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AiAgentChannel;

import java.util.List;
import java.util.Set;

/**
 * Dedicated request for creating a Retell-style Single-Prompt Agent.
 */
public record CreateSinglePromptAgentRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 500) String description,
        String language,
        List<String> speechLanguages,
        AiAgentChannel channel,
        @NotBlank String systemPrompt,
        String firstMessage,
        String engineProfileId,
        AgentVoiceDto voice,
        AgentSpeechEngineDto speechEngine,
        @Size(max = 120) String llmModel,
        Double temperature,
        Integer maxOutputTokens,
        AgentAmbienceDto ambience,
        List<DataExtractionField> dataNeeded,
        List<DataEvaluationCriterion> dataEvaluation,
        Set<Long> sipTrunkIds
) {
}
