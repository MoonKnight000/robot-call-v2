package uz.murodjon.robotcallv2.aiagent.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentWebhookConfig;
import uz.murodjon.robotcallv2.aiagent.domain.entity.DataEvaluationCriterion;
import uz.murodjon.robotcallv2.aiagent.domain.entity.DataExtractionField;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentClearableField;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AiAgentChannel;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AiAgentMode;
import uz.murodjon.robotcallv2.aiagent.domain.enums.HandbookRule;
import uz.murodjon.robotcallv2.shared.dialog.AgentPersona;

import java.util.List;
import java.util.Set;

/**
 * Full edit of an agent ({@code PUT /api/ai-agents/{id}}). The groups are the same as
 * {@link AiAgentRow} returns, so a form can send back what it read; a null field — or a
 * group left out — keeps the agent's value.
 *
 * <p>{@code version} is the one {@link AiAgentRow} carried when the form was opened — the
 * draft row's lock counter, not {@code publishedVersion}. An agent saved by someone else
 * since is answered 409 instead of being overwritten. Optional, so an older client that
 * does not send it keeps working.
 *
 * <p>{@code engineProfileId} replaces the agent's recognizer, models, temperature and reply cap
 * with that profile's ({@code GET /api/ai-agents/engine-profiles}); any of those fields sent
 * alongside it still wins.
 *
 * <p>{@code clearFields} removes the listed optional settings — the one way to empty a value,
 * since null means "keep". Sending a value for a field and listing it too is refused (400).
 */
public record UpdateAiAgentRequest(
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
        Set<Long> sipTrunkIds,
        Boolean enabled,
        Long version,
        Set<AgentClearableField> clearFields
) {
}
