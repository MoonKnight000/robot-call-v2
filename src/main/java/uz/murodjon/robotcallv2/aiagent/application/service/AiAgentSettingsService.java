package uz.murodjon.robotcallv2.aiagent.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.aiagent.application.dto.*;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentSettingsUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.*;
import uz.murodjon.robotcallv2.aiagent.domain.enums.ScenarioMode;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinitionBuilder;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;

import java.util.List;

/**
 * The agent editor's sections — scenario, analysis, limits, advanced, pronunciation and
 * post-call actions. Each writes the draft through {@link AiAgentDrafts}, so a section save
 * is checked exactly like a whole-agent save.
 */
@Service
public class AiAgentSettingsService implements AiAgentSettingsUseCase {

    private final AiAgentDrafts drafts;
    private final AuditUseCase auditUseCase;

    public AiAgentSettingsService(AiAgentDrafts drafts, AuditUseCase auditUseCase) {
        this.drafts = drafts;
        this.auditUseCase = auditUseCase;
    }

    @Override
    public AgentScenarioDto findScenario(long companyId, long agentId) {
        AiAgent agent = drafts.requireAgent(companyId, agentId);
        ScenarioDefinition def = agent.script().definition();
        return new AgentScenarioDto(
                agent.script().mode(),
                agent.script().firstMessage(),
                agent.script().systemPrompt(),
                def != null ? def.stages() : List.of(),
                def != null ? def.guardrails() : List.of(),
                def != null ? def.factSchema() : List.of(),
                def != null ? def.disclosureText() : null,
                def != null ? def.callPurpose() : null
        );
    }

    @Override
    @Transactional
    public AgentScenarioDto updateScenario(long companyId, long agentId, AgentScenarioDto request) {
        AiAgent existing = drafts.requireAgent(companyId, agentId);
        // A shared scenario outranks the agent's own definition when a call starts, so a step
        // edit saved here would never be spoken.
        if (existing.script().scenarioId() != null) {
            throw new ConflictException(ErrorCode.AI_AGENT_SCENARIO_SHARED, agentId, existing.script().scenarioId());
        }
        String rolePrompt = request.systemPrompt() != null ? request.systemPrompt().trim() : existing.script().systemPrompt();
        String firstMessage = request.firstMessage() != null ? request.firstMessage().trim() : existing.script().firstMessage();
        ScenarioMode mode = request.scenarioModeOrDefault();

        List<StageDef> stages = request.stagesOrEmpty();
        if (stages.isEmpty()) {
            stages = List.of(StageDef.builder()
                    .id("CONVERSATION")
                    .purpose("Engage in natural conversation based on agent instructions")
                    .allowedTransitions(List.of())
                    .allowedTools(List.of())
                    .build());
        }

        // What this form does not carry (tools, outcomes, global nodes, ...) stays as the agent has it.
        ScenarioDefinition current = existing.script().definition();
        ScenarioDefinitionBuilder base = current != null
                ? current.toBuilder()
                : ScenarioDefinition.builder().tools(List.of()).outcomeSchema(List.of());
        ScenarioDefinition def = base
                .stages(stages)
                .factSchema(request.factSchemaOrEmpty())
                .rolePrompt(rolePrompt != null ? rolePrompt : "")
                .guardrails(request.guardrailsOrEmpty())
                .disclosureText(request.disclosureText())
                .callPurpose(request.callPurpose())
                .build();

        AiAgent updated = existing.withScenario(new AiAgentScript(
                existing.script().templateId(), existing.script().scenarioId(), mode, def, firstMessage, rolePrompt));
        drafts.update(companyId, agentId, updated);
        auditUseCase.record(companyId, "AI_AGENT_SCENARIO_UPDATE", "ai_agent", String.valueOf(agentId), updated.name());
        return findScenario(companyId, agentId);
    }

    @Override
    public AgentAnalysisDto findAnalysis(long companyId, long agentId) {
        AiAgent agent = drafts.requireAgent(companyId, agentId);
        return new AgentAnalysisDto(agent.dataNeeded(), agent.dataEvaluation());
    }

    @Override
    @Transactional
    public AgentAnalysisDto updateAnalysis(long companyId, long agentId, AgentAnalysisDto request) {
        AiAgent existing = drafts.requireAgent(companyId, agentId);
        AiAgent updated = existing.withAnalysis(
                request.dataNeeded() != null ? request.dataNeeded() : existing.dataNeeded(),
                request.dataEvaluation() != null ? request.dataEvaluation() : existing.dataEvaluation()
        );
        drafts.update(companyId, agentId, updated);
        auditUseCase.record(companyId, "AI_AGENT_ANALYSIS_UPDATE", "ai_agent", String.valueOf(agentId), updated.name());
        return new AgentAnalysisDto(updated.dataNeeded(), updated.dataEvaluation());
    }

    @Override
    public AgentLimitsDto findLimits(long companyId, long agentId) {
        AiAgent agent = drafts.requireAgent(companyId, agentId);
        return new AgentLimitsDto(
                agent.limits().concurrentCallsLimit(),
                agent.limits().dailyCallsLimit(),
                agent.limits().maxConversationDurationSeconds(),
                agent.limits().silenceEndCallTimeoutSeconds(),
                agent.limits().turnTimeoutSeconds()
        );
    }

    @Override
    @Transactional
    public AgentLimitsDto updateLimits(long companyId, long agentId, AgentLimitsDto request) {
        AiAgent existing = drafts.requireAgent(companyId, agentId);
        AiAgentLimits limits = existing.limits();
        AiAgent updated = existing.withLimits(new AiAgentLimits(
                request.maxConversationDurationSeconds() != null ? request.maxConversationDurationSeconds() : limits.maxConversationDurationSeconds(),
                request.silenceEndCallTimeoutSeconds() != null ? request.silenceEndCallTimeoutSeconds() : limits.silenceEndCallTimeoutSeconds(),
                request.turnTimeoutSeconds() != null ? request.turnTimeoutSeconds() : limits.turnTimeoutSeconds(),
                request.concurrentCallsLimit() != null ? request.concurrentCallsLimit() : limits.concurrentCallsLimit(),
                request.dailyCallsLimit() != null ? request.dailyCallsLimit() : limits.dailyCallsLimit()
        ));
        drafts.update(companyId, agentId, updated);
        auditUseCase.record(companyId, "AI_AGENT_LIMITS_UPDATE", "ai_agent", String.valueOf(agentId), updated.name());
        return new AgentLimitsDto(
                updated.limits().concurrentCallsLimit(),
                updated.limits().dailyCallsLimit(),
                updated.limits().maxConversationDurationSeconds(),
                updated.limits().silenceEndCallTimeoutSeconds(),
                updated.limits().turnTimeoutSeconds()
        );
    }

    @Override
    public AgentAdvancedDto findAdvanced(long companyId, long agentId) {
        AiAgent agent = drafts.requireAgent(companyId, agentId);
        return new AgentAdvancedDto(
                agent.useRag(),
                agent.preemptiveGeneration(),
                agent.ivrNavigationEnabled(),
                agent.dataPolicy().storeCallAudio(),
                agent.dataPolicy().zeroPiiRetention(),
                agent.dataPolicy().conversationRetentionDays(),
                agent.dataPolicy().realtimeFactsDelivery(),
                agent.initiationWebhook(),
                agent.postCallWebhook(),
                agent.ambience().background(),
                agent.ambience().backgroundVolume(),
                agent.ambience().backgroundFadeInSeconds(),
                agent.ambience().thinking(),
                agent.ambience().thinkingVolume(),
                agent.ambience().noiseCancellationEnabled(),
                agent.ambience().noiseCancellationMode(),
                agent.voice().emotionAdaptive(),
                agent.voice().warmth(),
                agent.callBehaviour().dtmfInputEnabled(),
                agent.callBehaviour().voicemailAction(),
                agent.callBehaviour().voicemailMessage(),
                agent.callBehaviour().midCallSmsEnabled(),
                agent.callBehaviour().midCallSmsTemplate(),
                agent.callBehaviour().transferPhoneNumber(),
                agent.callBehaviour().transferMessage(),
                agent.callBehaviour().transferQueueId(),
                agent.callBehaviour().interruptionSensitivity(),
                agent.callBehaviour().endpointingDelayMs()
        );
    }

    @Override
    @Transactional
    public AgentAdvancedDto updateAdvanced(long companyId, long agentId, AgentAdvancedDto request) {
        AiAgent existing = drafts.requireAgent(companyId, agentId);
        AiAgentDrafts.requireCallableWebhookUrls(request.initiationWebhook(), request.postCallWebhook());
        drafts.requireOwnQueue(companyId, request.transferQueueId());
        boolean zeroPii = request.zeroPiiRetention() != null ? request.zeroPiiRetention() : existing.dataPolicy().zeroPiiRetention();
        boolean storeAudio = request.storeCallAudio() != null ? request.storeCallAudio() : existing.dataPolicy().storeCallAudio();
        AiAgentDrafts.requireCompatibleDataPolicy(zeroPii, storeAudio);

        AiAgentAmbience ambience = existing.ambience();
        AiAgentCallBehaviour behaviour = existing.callBehaviour();
        AiAgentVoice voice = existing.voice();

        AiAgent updated = existing.withAdvanced(
                new AiAgentAmbience(
                        request.ambientSound() != null ? request.ambientSound() : ambience.background(),
                        request.ambientSoundVolume() != null ? request.ambientSoundVolume() : ambience.backgroundVolume(),
                        request.ambientSoundFadeInSeconds() != null ? request.ambientSoundFadeInSeconds() : ambience.backgroundFadeInSeconds(),
                        request.thinkingSound() != null ? request.thinkingSound() : ambience.thinking(),
                        request.thinkingSoundVolume() != null ? request.thinkingSoundVolume() : ambience.thinkingVolume(),
                        request.noiseCancellationEnabled() != null ? request.noiseCancellationEnabled() : ambience.noiseCancellationEnabled(),
                        request.noiseCancellationMode() != null ? request.noiseCancellationMode() : ambience.noiseCancellationMode()),
                new AiAgentCallBehaviour(
                        request.interruptionSensitivity() != null ? request.interruptionSensitivity() : behaviour.interruptionSensitivity(),
                        request.endpointingDelayMs() != null ? request.endpointingDelayMs() : behaviour.endpointingDelayMs(),
                        request.dtmfInputEnabled() != null ? request.dtmfInputEnabled() : behaviour.dtmfInputEnabled(),
                        request.voicemailAction() != null ? request.voicemailAction() : behaviour.voicemailAction(),
                        request.voicemailMessage() != null ? request.voicemailMessage() : behaviour.voicemailMessage(),
                        request.midCallSmsEnabled() != null ? request.midCallSmsEnabled() : behaviour.midCallSmsEnabled(),
                        request.midCallSmsTemplate() != null ? request.midCallSmsTemplate() : behaviour.midCallSmsTemplate(),
                        request.transferPhoneNumber() != null ? request.transferPhoneNumber() : behaviour.transferPhoneNumber(),
                        request.transferMessage() != null ? request.transferMessage() : behaviour.transferMessage(),
                        request.transferQueueId() != null ? request.transferQueueId() : behaviour.transferQueueId(),
                        behaviour.preConversationAudioFileId()),
                new AiAgentDataPolicy(
                        zeroPii,
                        storeAudio,
                        request.conversationRetentionDays() != null ? request.conversationRetentionDays() : existing.dataPolicy().conversationRetentionDays(),
                        request.realtimeFactsDelivery() != null ? request.realtimeFactsDelivery() : existing.dataPolicy().realtimeFactsDelivery()),
                voice.withEmotionAdaptive(
                        request.emotionAdaptiveVoice() != null ? request.emotionAdaptiveVoice() : voice.emotionAdaptive())
                        .withWarmth(request.speakingWarmth() != null ? request.speakingWarmth() : voice.warmth()),
                request.preemptiveGeneration() != null ? request.preemptiveGeneration() : existing.preemptiveGeneration(),
                request.ivrNavigationEnabled() != null ? request.ivrNavigationEnabled() : existing.ivrNavigationEnabled(),
                request.useRag() != null ? request.useRag() : existing.useRag(),
                request.initiationWebhook() != null ? request.initiationWebhook() : existing.initiationWebhook(),
                request.postCallWebhook() != null ? request.postCallWebhook() : existing.postCallWebhook()
        );
        drafts.update(companyId, agentId, updated);
        auditUseCase.record(companyId, "AI_AGENT_ADVANCED_UPDATE", "ai_agent", String.valueOf(agentId), updated.name());
        return findAdvanced(companyId, agentId);
    }

    @Override
    public AgentPronunciationDto findPronunciation(long companyId, long agentId) {
        AiAgent agent = drafts.requireAgent(companyId, agentId);
        return new AgentPronunciationDto(agent.pronunciationRules());
    }

    @Override
    @Transactional
    public AgentPronunciationDto updatePronunciation(long companyId, long agentId, AgentPronunciationDto request) {
        AiAgent existing = drafts.requireAgent(companyId, agentId);
        AiAgent updated = existing.withPronunciationRules(request.rulesOrEmpty());
        drafts.update(companyId, agentId, updated);
        auditUseCase.record(companyId, "AI_AGENT_PRONUNCIATION_UPDATE", "ai_agent", String.valueOf(agentId), updated.name());
        return new AgentPronunciationDto(updated.pronunciationRules());
    }

    @Override
    public AgentPostCallActionsDto findPostCallActions(long companyId, long agentId) {
        AiAgent agent = drafts.requireAgent(companyId, agentId);
        return new AgentPostCallActionsDto(agent.postCallActions());
    }

    @Override
    @Transactional
    public AgentPostCallActionsDto updatePostCallActions(long companyId, long agentId, AgentPostCallActionsDto request) {
        AiAgent existing = drafts.requireAgent(companyId, agentId);
        AiAgent updated = existing.withPostCallActions(request.actionsOrEmpty());
        drafts.update(companyId, agentId, updated);
        auditUseCase.record(companyId, "AI_AGENT_POST_CALL_ACTIONS_UPDATE", "ai_agent", String.valueOf(agentId), updated.name());
        return new AgentPostCallActionsDto(updated.postCallActions());
    }
}
