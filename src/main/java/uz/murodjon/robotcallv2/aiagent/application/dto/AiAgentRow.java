package uz.murodjon.robotcallv2.aiagent.application.dto;

import uz.murodjon.robotcallv2.aiagent.domain.entity.*;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AiAgentChannel;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AiAgentMode;
import uz.murodjon.robotcallv2.aiagent.domain.enums.HandbookRule;
import uz.murodjon.robotcallv2.shared.dialog.AgentPersona;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * {@link AiAgent} representation returned to the agent screens. The groups are the ones
 * {@link UpdateAiAgentRequest} takes, so the form can send back what it read.
 */
public record AiAgentRow(
        long id,
        long companyId,
        String name,
        String description,
        String language,
        AiAgentChannel channel,
        AiAgentMode mode,
        AgentPersona persona,
        boolean disclosureEnabled,
        String engineProfileId,
        AgentScriptDto script,
        String scenarioName,
        AgentSpeechEngineDto speechEngine,
        AgentVoiceDto voice,
        String llmModel,
        String fastLlmModel,
        Double temperature,
        Integer maxOutputTokens,
        AgentAmbienceDto ambience,
        AgentCallBehaviourDto callBehaviour,
        AgentLimitsDto limits,
        AgentDataPolicyDto dataPolicy,
        boolean useRag,
        List<DataExtractionField> dataNeeded,
        List<DataEvaluationCriterion> dataEvaluation,
        AgentWebhookConfig initiationWebhook,
        AgentWebhookConfig postCallWebhook,
        List<PronunciationRule> pronunciationRules,
        List<PostCallAction> postCallActions,
        Set<HandbookRule> handbook,
        String timezone,
        Set<Long> sipTrunkIds,
        boolean enabled,
        Instant createdAt,
        Long createdBy,
        String createdByName,
        Integer publishedVersion,
        boolean hasDraftChanges,
        long version
) {

    /**
     * @param scenarioName     the name of {@code script.scenarioId}, when the agent speaks a shared scenario
     * @param engineProfileId  the engine profile whose choices the agent has exactly, or null when
     *                         they were tuned by hand — what the simple form shows as picked
     * @param publishedVersion the version calls run on; null while the agent was never published
     *                         (then calls run on the draft itself)
     * @param hasDraftChanges  the draft differs from {@code publishedVersion} — a publish would change calls
     *
     * <p>{@code version} is the draft row's lock counter, which {@code PUT /api/ai-agents/{id}}
     * takes back; 0 on a row built from a published snapshot.
     */
    public static AiAgentRow of(AiAgent agent, String scenarioName, String createdByName, String engineProfileId,
                                Integer publishedVersion, boolean hasDraftChanges) {
        return new AiAgentRow(
                agent.id(),
                agent.companyId(),
                agent.name(),
                agent.description(),
                agent.language(),
                agent.channel(),
                agent.mode(),
                agent.persona(),
                agent.disclosureEnabled(),
                engineProfileId,
                AgentScriptDto.of(agent.script()),
                scenarioName,
                AgentSpeechEngineDto.of(agent.speechEngine()),
                AgentVoiceDto.of(agent.voice()),
                agent.llmModel(),
                agent.fastLlmModel(),
                agent.temperature(),
                agent.maxOutputTokens(),
                AgentAmbienceDto.of(agent.ambience()),
                AgentCallBehaviourDto.of(agent.callBehaviour()),
                AgentLimitsDto.of(agent.limits()),
                AgentDataPolicyDto.of(agent.dataPolicy()),
                agent.useRag(),
                agent.dataNeeded(),
                agent.dataEvaluation(),
                agent.initiationWebhook(),
                agent.postCallWebhook(),
                agent.pronunciationRules(),
                agent.postCallActions(),
                agent.handbook(),
                agent.timezone(),
                agent.sipTrunkIds(),
                agent.enabled(),
                agent.createdAt(),
                agent.createdBy(),
                createdByName,
                publishedVersion,
                hasDraftChanges,
                agent.version()
        );
    }
}
