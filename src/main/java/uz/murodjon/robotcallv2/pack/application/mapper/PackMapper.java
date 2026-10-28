package uz.murodjon.robotcallv2.pack.application.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.aiagent.application.dto.AgentDataPolicyDto;
import uz.murodjon.robotcallv2.aiagent.application.dto.AgentScriptDto;
import uz.murodjon.robotcallv2.aiagent.application.dto.AgentSpeechEngineDto;
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
import uz.murodjon.robotcallv2.pack.domain.entity.PackTool;
import uz.murodjon.robotcallv2.pack.domain.entity.VerticalPack;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.dialog.PipelineMode;
import uz.murodjon.robotcallv2.tool.application.dto.CreateToolRequest;
import uz.murodjon.robotcallv2.tool.application.dto.ToolHttpConfigDto;
import uz.murodjon.robotcallv2.tool.application.dto.ToolSpeechDto;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolHttpConfig;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolSpeech;

import java.util.List;

/** A pack's parts, as the create requests of the features that store them. */
@Component
public class PackMapper {

    /** Company tools are called only on the cascade pipeline (UNIVERSAL-OPERATOR-PLAN B.3), and a pack's agent exists to call them. */
    private static final PipelineMode PACK_PIPELINE = PipelineMode.CASCADE;

    public PackRow toPackRow(VerticalPack pack) {
        return new PackRow(pack.key(), pack.name(), pack.description(), pack.industry(),
                pack.tools().stream().map(PackTool::name).toList(),
                pack.catalogFeed() != null, pack.testCases().size(), pack.secrets());
    }

    /** Everything a pack does not decide is left null, for the agent's own defaults. */
    public CreateAiAgentRequest toCreateAiAgentRequest(PackAgent agent) {
        AiAgentDataPolicy policy = agent.dataPolicy();
        return new CreateAiAgentRequest(
                agent.name(), agent.description(), agent.language(),
                null, null,                                    // persona, engineProfileId
                new AgentScriptDto(null, null, ScenarioMode.STRUCTURED_STEPS, agent.scenario(),
                        agent.firstMessage(), agent.scenario().rolePrompt()),
                new AgentSpeechEngineDto(PACK_PIPELINE, null, null, null, null, null, null, null, null),
                null,                                          // voice
                null, null, null, null,                        // llmModel, fastLlmModel, temperature, maxOutputTokens
                null, null, null,                              // ambience, call behaviour, limits
                // All four together or none: a pack that answers "what is kept" answers it whole
                // (E.3), and one that stays silent leaves every part to the agent's defaults.
                policy == null ? null : AgentDataPolicyDto.of(policy),
                null, null, agent.useRag(),                    // preemptiveGeneration, ivrNavigationEnabled
                agent.dataNeeded(), agent.dataEvaluation(),
                null, null,                                    // initiation and post-call webhooks
                null, null);                                   // sipTrunkIds, enabled
    }

    /** The reverse of {@link #toCreateAiAgentRequest}: only what a pack decides, with the scenario the agent runs. */
    public PackAgent toPackAgent(AiAgent agent, ScenarioDefinition scenario) {
        return new PackAgent(agent.name(), agent.description(), agent.language(), agent.script().firstMessage(),
                agent.useRag(), agent.dataPolicy(), scenario, agent.dataNeeded(), agent.dataEvaluation());
    }

    public CreateToolRequest toCreateToolRequest(PackTool tool) {
        return new CreateToolRequest(tool.name(), tool.description(),
                new ToolHttpConfigDto(tool.apiUrl(), tool.apiMethod(), tool.apiHeaders(), tool.apiBody(),
                        tool.apiQueryParams(), tool.apiPathParams(), tool.bodySchema(), tool.responseTimeoutSecs(),
                        tool.retryAttempts(), tool.auth()),
                tool.dynamicVariables(),
                new ToolSpeechDto(tool.disableInterruptions(), tool.forcePreToolSpeech(), tool.preToolSpeech()),
                tool.responseMapping(), tool.maxCallsPerCall(), tool.effect(), tool.requiresConfirmation(),
                tool.readbackTemplate());
    }

    /** A stored tool as a pack file spells it; ids, company and timestamps stay behind. */
    public static PackTool toPackTool(Tool tool) {
        ToolHttpConfig http = tool.http();
        ToolSpeech speech = tool.speech();
        return new PackTool(tool.name(), tool.description(), http.url(), http.method(), http.headers(), http.body(),
                http.queryParams(), http.pathParams(), http.responseTimeoutSecs(), tool.dynamicVariables(),
                speech.disableInterruptions(), speech.forcePreToolSpeech(), speech.preToolSpeech(),
                tool.responseMapping(), http.retryAttempts(), tool.maxCallsPerCall(), http.bodySchema(),
                tool.effect(), tool.requiresConfirmation(), tool.readbackTemplate(), http.auth());
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
