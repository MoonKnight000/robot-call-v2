package uz.murodjon.robotcallv2.aiagent.application.port.input;

import uz.murodjon.robotcallv2.aiagent.application.dto.*;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentFilter;
import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface AiAgentUseCase {

    AiAgentRow createAgent(long companyId, CreateAiAgentRequest request);

    AiAgentRow updateAgent(long companyId, long id, UpdateAiAgentRequest request);

    AiAgentRow findAgentRow(long companyId, long id);

    PageableData<AiAgentRow> filterAgents(long companyId, AiAgentFilter filter);

    void deleteAgent(long companyId, long id);

    /** The agent a call runs under — also reached from the dialer sweep and the ARI event loop. */
    AiAgent requireAgent(long companyId, long id);

    AiAgent findAgent(long companyId, long id);

    Map<Long, String> findNamesByIds(Collection<Long> ids);

    List<TemplatePresetDto> getTemplates(String language);

    /** Resolves or synthesizes the Scenario this agent executes for a call. */
    Scenario resolveScenario(long companyId, AiAgent agent);

    AgentScenarioDto getScenario(long companyId, long agentId);

    AgentScenarioDto updateScenario(long companyId, long agentId, AgentScenarioDto request);

    AgentAnalysisDto getAnalysis(long companyId, long agentId);

    AgentAnalysisDto updateAnalysis(long companyId, long agentId, AgentAnalysisDto request);

    AgentLimitsDto getLimits(long companyId, long agentId);

    AgentLimitsDto updateLimits(long companyId, long agentId, AgentLimitsDto request);

    AgentAdvancedDto getAdvanced(long companyId, long agentId);

    AgentAdvancedDto updateAdvanced(long companyId, long agentId, AgentAdvancedDto request);

    AgentPronunciationDto getPronunciation(long companyId, long agentId);

    AgentPronunciationDto updatePronunciation(long companyId, long agentId, AgentPronunciationDto request);

    AgentPostCallActionsDto getPostCallActions(long companyId, long agentId);

    AgentPostCallActionsDto updatePostCallActions(long companyId, long agentId, AgentPostCallActionsDto request);

    EngineOptionsResponse findEngineOptions();
}
