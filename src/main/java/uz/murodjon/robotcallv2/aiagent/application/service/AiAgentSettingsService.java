package uz.murodjon.robotcallv2.aiagent.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.aiagent.application.dto.*;
import uz.murodjon.robotcallv2.aiagent.application.mapper.AiAgentSettingsMapper;
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
        return AgentLimitsDto.of(agent.limits());
    }

    @Override
    @Transactional
    public AgentLimitsDto updateLimits(long companyId, long agentId, AgentLimitsDto request) {
        AiAgent existing = drafts.requireAgent(companyId, agentId);
        AiAgent updated = existing.withLimits(AiAgentSettingsMapper.toAiAgentLimits(request, existing.limits()));
        drafts.update(companyId, agentId, updated);
        auditUseCase.record(companyId, "AI_AGENT_LIMITS_UPDATE", "ai_agent", String.valueOf(agentId), updated.name());
        return AgentLimitsDto.of(updated.limits());
    }

    @Override
    public AgentAdvancedDto findAdvanced(long companyId, long agentId) {
        AiAgent agent = drafts.requireAgent(companyId, agentId);
        return new AgentAdvancedDto(
                agent.useRag(),
                agent.initiationWebhook(),
                agent.postCallWebhook(),
                agent.voice().emotionAdaptive(),
                agent.voice().warmth(),
                AgentAmbienceDto.of(agent.ambience()),
                AgentCallBehaviourDto.of(agent.callBehaviour()),
                AgentDataPolicyDto.of(agent.dataPolicy())
        );
    }

    @Override
    @Transactional
    public AgentAdvancedDto updateAdvanced(long companyId, long agentId, AgentAdvancedDto request) {
        AiAgent existing = drafts.requireAgent(companyId, agentId);
        AiAgentDrafts.requireCallableWebhookUrls(request.initiationWebhook(), request.postCallWebhook());
        drafts.requireOwnQueue(companyId, request.callBehaviour() != null ? request.callBehaviour().transferQueueId() : null);
        AiAgentDataPolicy dataPolicy = AiAgentSettingsMapper.toAiAgentDataPolicy(request.dataPolicy(), existing.dataPolicy());
        AiAgentDrafts.requireCompatibleDataPolicy(dataPolicy.zeroPiiRetention(), dataPolicy.storeCallAudio());

        AiAgentVoice voice = existing.voice();

        AiAgent updated = existing.withAdvanced(
                AiAgentSettingsMapper.toAiAgentAmbience(request.ambience(), existing.ambience()),
                AiAgentSettingsMapper.toAiAgentCallBehaviour(request.callBehaviour(), existing.callBehaviour()),
                dataPolicy,
                voice.withEmotionAdaptive(
                        request.emotionAdaptiveVoice() != null ? request.emotionAdaptiveVoice() : voice.emotionAdaptive())
                        .withWarmth(request.speakingWarmth() != null ? request.speakingWarmth() : voice.warmth()),
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
