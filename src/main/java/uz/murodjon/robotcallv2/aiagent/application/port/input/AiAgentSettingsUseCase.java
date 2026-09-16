package uz.murodjon.robotcallv2.aiagent.application.port.input;

import uz.murodjon.robotcallv2.aiagent.application.dto.AgentAdvancedDto;
import uz.murodjon.robotcallv2.aiagent.application.dto.AgentAnalysisDto;
import uz.murodjon.robotcallv2.aiagent.application.dto.AgentLimitsDto;
import uz.murodjon.robotcallv2.aiagent.application.dto.AgentPostCallActionsDto;
import uz.murodjon.robotcallv2.aiagent.application.dto.AgentPronunciationDto;
import uz.murodjon.robotcallv2.aiagent.application.dto.AgentScenarioDto;

/** The agent editor's sections, each read and written on its own against the draft. */
public interface AiAgentSettingsUseCase {

    AgentScenarioDto findScenario(long companyId, long agentId);

    AgentScenarioDto updateScenario(long companyId, long agentId, AgentScenarioDto request);

    AgentAnalysisDto findAnalysis(long companyId, long agentId);

    AgentAnalysisDto updateAnalysis(long companyId, long agentId, AgentAnalysisDto request);

    AgentLimitsDto findLimits(long companyId, long agentId);

    AgentLimitsDto updateLimits(long companyId, long agentId, AgentLimitsDto request);

    AgentAdvancedDto findAdvanced(long companyId, long agentId);

    AgentAdvancedDto updateAdvanced(long companyId, long agentId, AgentAdvancedDto request);

    AgentPronunciationDto findPronunciation(long companyId, long agentId);

    AgentPronunciationDto updatePronunciation(long companyId, long agentId, AgentPronunciationDto request);

    AgentPostCallActionsDto findPostCallActions(long companyId, long agentId);

    AgentPostCallActionsDto updatePostCallActions(long companyId, long agentId, AgentPostCallActionsDto request);
}
