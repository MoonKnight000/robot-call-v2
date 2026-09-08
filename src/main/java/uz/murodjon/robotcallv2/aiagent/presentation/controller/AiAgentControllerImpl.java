package uz.murodjon.robotcallv2.aiagent.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.aiagent.application.dto.*;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.tool.application.dto.ToolRow;
import uz.murodjon.robotcallv2.tool.application.port.input.ToolUseCase;

import java.util.List;

@RestController
public class AiAgentControllerImpl implements AiAgentController {

    private final AiAgentUseCase aiAgentUseCase;
    private final ToolUseCase toolUseCase;

    public AiAgentControllerImpl(AiAgentUseCase aiAgentUseCase, ToolUseCase toolUseCase) {
        this.aiAgentUseCase = aiAgentUseCase;
        this.toolUseCase = toolUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<List<TemplatePresetDto>>> getTemplates(long companyId, String language) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentUseCase.getTemplates(language)));
    }

    @Override
    public ResponseEntity<ResponseData<EngineOptionsResponse>> getEngineOptions() {
        return ResponseEntity.ok(ResponseData.ok(aiAgentUseCase.findEngineOptions()));
    }

    @Override
    public ResponseEntity<ResponseData<AiAgentRow>> create(long companyId, CreateAiAgentRequest request) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentUseCase.createAgent(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<AiAgentRow>>> filter(long companyId, AiAgentFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentUseCase.filterAgents(companyId, filter)));
    }

    @Override
    public ResponseEntity<ResponseData<AiAgentRow>> get(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentUseCase.findAgentRow(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<AiAgentRow>> update(long companyId, long id, UpdateAiAgentRequest request) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentUseCase.updateAgent(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> delete(long companyId, long id) {
        aiAgentUseCase.deleteAgent(companyId, id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<AgentScenarioDto>> getScenario(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentUseCase.getScenario(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<AgentScenarioDto>> updateScenario(long companyId, long id, AgentScenarioDto request) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentUseCase.updateScenario(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<AgentAnalysisDto>> getAnalysis(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentUseCase.getAnalysis(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<AgentAnalysisDto>> updateAnalysis(long companyId, long id, AgentAnalysisDto request) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentUseCase.updateAnalysis(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<AgentLimitsDto>> getLimits(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentUseCase.getLimits(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<AgentLimitsDto>> updateLimits(long companyId, long id, AgentLimitsDto request) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentUseCase.updateLimits(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<AgentAdvancedDto>> getAdvanced(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentUseCase.getAdvanced(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<AgentAdvancedDto>> updateAdvanced(long companyId, long id, AgentAdvancedDto request) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentUseCase.updateAdvanced(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<AgentPronunciationDto>> getPronunciation(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentUseCase.getPronunciation(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<AgentPronunciationDto>> updatePronunciation(long companyId, long id, AgentPronunciationDto request) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentUseCase.updatePronunciation(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<AgentPostCallActionsDto>> getPostCallActions(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentUseCase.getPostCallActions(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<AgentPostCallActionsDto>> updatePostCallActions(long companyId, long id, AgentPostCallActionsDto request) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentUseCase.updatePostCallActions(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<List<ToolRow>>> getTools(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(toolUseCase.findAgentTools(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> attachTool(long companyId, long id, long toolId) {
        toolUseCase.attachTool(companyId, id, toolId);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> detachTool(long companyId, long id, long toolId) {
        toolUseCase.detachTool(companyId, id, toolId);
        return ResponseEntity.ok(ResponseData.ok(null));
    }
}
