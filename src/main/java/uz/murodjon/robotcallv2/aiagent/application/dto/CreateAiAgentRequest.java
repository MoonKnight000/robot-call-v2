package uz.murodjon.robotcallv2.aiagent.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentWebhookConfig;
import uz.murodjon.robotcallv2.aiagent.domain.entity.DataEvaluationCriterion;
import uz.murodjon.robotcallv2.aiagent.domain.entity.DataExtractionField;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AiAgentChannel;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AiAgentMode;
import uz.murodjon.robotcallv2.aiagent.domain.enums.HandbookRule;
import uz.murodjon.robotcallv2.shared.dialog.AgentPersona;

import java.util.List;
import java.util.Set;

/**
 * A new AI agent ({@code POST /api/ai-agents}). Each group may be left out as a whole; the
 * agent then takes that group's defaults.
 */
public record CreateAiAgentRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 500) String description,
        String language,
        List<String> speechLanguages,
        AiAgentChannel channel,
        AiAgentMode mode,
        AgentPersona persona,
        String engineProfileId,
        AgentScriptDto script,
        AgentSpeechEngineDto speechEngine,
        AgentVoiceDto voice,
        @Size(max = 120) String llmModel,
        @Size(max = 120) String fastLlmModel,
        Double temperature,
        Integer maxOutputTokens,
        AgentAmbienceDto ambience,
        @Valid AgentGreetingDto greeting,
        @Valid AgentCallBehaviourDto callBehaviour,
        AgentLimitsDto limits,
        AgentDataPolicyDto dataPolicy,
        Boolean useRag,
        List<DataExtractionField> dataNeeded,
        List<DataEvaluationCriterion> dataEvaluation,
        AgentWebhookConfig initiationWebhook,
        AgentWebhookConfig postCallWebhook,
        Set<HandbookRule> handbook,
        @Size(max = 64) String timezone,
        Boolean enabled
) {
}
