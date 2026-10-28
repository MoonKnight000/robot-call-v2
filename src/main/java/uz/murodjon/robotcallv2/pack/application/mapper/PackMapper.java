package uz.murodjon.robotcallv2.pack.application.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.aiagent.application.dto.CreateAiAgentRequest;
import uz.murodjon.robotcallv2.aiagent.application.dto.SaveAgentTestCaseRequest;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentTestCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentDataPolicy;
import uz.murodjon.robotcallv2.aiagent.domain.enums.ScenarioMode;
import uz.murodjon.robotcallv2.conversion.application.dto.ConversionGoalRequest;
import uz.murodjon.robotcallv2.conversion.domain.entity.ConversionGoal;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.CreateKnowledgeSourceItem;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.CreateKnowledgeSourceRequest;
import uz.murodjon.robotcallv2.knowledgebase.domain.enums.KnowledgeSourceType;
import uz.murodjon.robotcallv2.pack.application.dto.InstallPackRequest;
import uz.murodjon.robotcallv2.pack.application.dto.PackRow;
import uz.murodjon.robotcallv2.pack.domain.entity.PackAgent;
import uz.murodjon.robotcallv2.pack.domain.entity.PackCatalogFeed;
import uz.murodjon.robotcallv2.pack.domain.entity.VerticalPack;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.dialog.PipelineMode;
import uz.murodjon.robotcallv2.tool.application.dto.CreateToolRequest;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;

import java.util.List;

/** A pack's parts, as the create requests of the features that store them. */
@Component
public class PackMapper {

    /** Company tools are called only on the cascade pipeline (UNIVERSAL-OPERATOR-PLAN B.3), and a pack's agent exists to call them. */
    private static final PipelineMode PACK_PIPELINE = PipelineMode.CASCADE;

    public PackRow toPackRow(VerticalPack pack) {
        return new PackRow(pack.key(), pack.name(), pack.description(), pack.industry(),
                pack.tools().stream().map(Tool::name).toList(),
                pack.catalogFeed() != null, pack.testCases().size(), pack.secrets());
    }

    /** Everything a pack does not decide is left null, for the agent's own defaults. */
    public CreateAiAgentRequest toCreateAiAgentRequest(PackAgent agent) {
        AiAgentDataPolicy policy = agent.dataPolicy();
        return new CreateAiAgentRequest(
                agent.name(), agent.description(),
                null, null, null,                              // scenarioId, templateId, engineProfileId
                agent.firstMessage(), agent.scenario().rolePrompt(),
                ScenarioMode.STRUCTURED_STEPS, agent.scenario(),
                null, null, agent.useRag(),                    // preemptiveGeneration, ivrNavigationEnabled
                agent.language(), PACK_PIPELINE,
                null, null, null, null,                        // realtimeProvider, pipecat stt / llm / tts
                null, null, null, null, null,                  // stt provider / model, tts provider / model, voice
                null, null, null, null, null,                  // voice speed, stability, similarity, per-language voices and speeds
                null,                                          // persona
                null, null, null, null,                        // llmModel, fastLlmModel, temperature, maxOutputTokens
                agent.dataNeeded(), agent.dataEvaluation(),
                // All four together or none: a pack that answers "what is kept" answers it whole
                // (E.3), and one that stays silent leaves every part to the agent's defaults.
                policy == null ? null : policy.zeroPiiRetention(),
                policy == null ? null : policy.storeCallAudio(),
                policy == null ? null : policy.conversationRetentionDays(),
                policy == null ? null : policy.realtimeFactsDelivery(),
                null, null, null, null, null,                  // duration, silence, turn, concurrent and daily limits
                null, null,                                    // initiation and post-call webhooks
                null, null, null, null, null, null, null,      // ambient and thinking sound, noise cancellation
                null, null, null,                              // emotionAdaptiveVoice, speakingWarmth, dtmfInputEnabled
                null, null,                                    // voicemail action and message
                null, null,                                    // mid-call SMS switch and template
                null, null, null,                              // transfer phone, message, queue
                null, null,                                    // interruptionSensitivity, endpointingDelayMs
                null, null,                                    // sipTrunkIds, enabled
                null);                                         // preConversationAudioFileId
    }

    /** The reverse of {@link #toCreateAiAgentRequest}: only what a pack decides, with the scenario the agent runs. */
    public PackAgent toPackAgent(AiAgent agent, ScenarioDefinition scenario) {
        return new PackAgent(agent.name(), agent.description(), agent.language(), agent.script().firstMessage(),
                agent.useRag(), agent.dataPolicy(), scenario, agent.dataNeeded(), agent.dataEvaluation());
    }

    public CreateToolRequest toCreateToolRequest(Tool tool) {
        return new CreateToolRequest(tool.name(), tool.description(), tool.apiUrl(), tool.apiMethod(),
                tool.apiHeaders(), tool.apiBody(), tool.apiQueryParams(), tool.apiPathParams(),
                tool.responseTimeoutSecs(), tool.dynamicVariables(), tool.disableInterruptions(),
                tool.forcePreToolSpeech(), tool.preToolSpeech(), tool.responseMapping(), tool.retryAttempts(),
                tool.maxCallsPerCall(), tool.bodySchema(), tool.effect(), tool.requiresConfirmation(),
                tool.readbackTemplate(), tool.auth());
    }

    public SaveAgentTestCaseRequest toSaveAgentTestCaseRequest(AgentTestCase testCase) {
        return new SaveAgentTestCaseRequest(testCase.name(), testCase.personaPrompt(), testCase.facts(),
                testCase.language(), testCase.expectDisposition(), testCase.expectStage(),
                testCase.mustSay(), testCase.mustNotSay(), testCase.maxTurns(), testCase.toolMocks(),
                testCase.expectTools());
    }

    public ConversionGoalRequest toConversionGoalRequest(ConversionGoal goal) {
        return new ConversionGoalRequest(goal.goalKey(), goal.name(), goal.attributionWindowHours(),
                goal.attributionModel(), goal.enabled());
    }

    public CreateKnowledgeSourceRequest toCreateKnowledgeSourceRequest(long agentId, PackCatalogFeed feed,
                                                                      InstallPackRequest request) {
        return new CreateKnowledgeSourceRequest(agentId, List.of(new CreateKnowledgeSourceItem(
                feed.name(), KnowledgeSourceType.STRUCTURED_FEED, request.catalogUrl().trim(), null, null,
                request.catalogItemsPath(), feed.feedKeyField(), feed.refreshMinutes())));
    }
}
