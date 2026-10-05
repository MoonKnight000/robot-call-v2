package uz.murodjon.robotcallv2.aiagent.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.aiagent.application.dto.*;
import uz.murodjon.robotcallv2.aiagent.application.port.input.*;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentEvalRun;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentTestCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.tool.application.dto.ToolRow;
import uz.murodjon.robotcallv2.tool.application.port.input.ToolUseCase;

import java.util.List;

@RestController
public class AiAgentControllerImpl implements AiAgentController {

    private final AiAgentUseCase aiAgentUseCase;
    private final AiAgentVersionUseCase aiAgentVersionUseCase;
    private final AiAgentSettingsUseCase aiAgentSettingsUseCase;
    private final AiAgentCatalogUseCase aiAgentCatalogUseCase;
    private final ToolUseCase toolUseCase;
    private final AgentEvaluationUseCase evaluationUseCase;

    public AiAgentControllerImpl(AiAgentUseCase aiAgentUseCase, AiAgentVersionUseCase aiAgentVersionUseCase,
                                 AiAgentSettingsUseCase aiAgentSettingsUseCase,
                                 AiAgentCatalogUseCase aiAgentCatalogUseCase, ToolUseCase toolUseCase,
                                 AgentEvaluationUseCase evaluationUseCase) {
        this.aiAgentUseCase = aiAgentUseCase;
        this.aiAgentVersionUseCase = aiAgentVersionUseCase;
        this.aiAgentSettingsUseCase = aiAgentSettingsUseCase;
        this.aiAgentCatalogUseCase = aiAgentCatalogUseCase;
        this.toolUseCase = toolUseCase;
        this.evaluationUseCase = evaluationUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<List<TemplatePresetDto>>> getTemplates(long companyId, String language) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentCatalogUseCase.findTemplates(language)));
    }

    @Override
    public ResponseEntity<ResponseData<EngineOptionsResponse>> getEngineOptions() {
        return ResponseEntity.ok(ResponseData.ok(aiAgentCatalogUseCase.findEngineOptions()));
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
    public ResponseEntity<ResponseData<AiAgentVersionRow>> publish(long companyId, long id, PublishAiAgentRequest request) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentVersionUseCase.publishAgent(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<AiAgentVersionRow>> rollback(long companyId, long id, int version) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentVersionUseCase.rollbackAgent(companyId, id, version)));
    }

    @Override
    public ResponseEntity<ResponseData<List<AiAgentVersionRow>>> versions(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentVersionUseCase.findVersions(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<AiAgentRow>> version(long companyId, long id, int version) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentUseCase.findVersionRow(companyId, id, version)));
    }

    @Override
    public ResponseEntity<ResponseData<AiAgentRow>> clone(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentUseCase.cloneAgent(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<AgentScenarioDto>> getScenario(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentSettingsUseCase.findScenario(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<AgentScenarioDto>> updateScenario(long companyId, long id, AgentScenarioDto request) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentSettingsUseCase.updateScenario(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<AgentAnalysisDto>> getAnalysis(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentSettingsUseCase.findAnalysis(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<AgentAnalysisDto>> updateAnalysis(long companyId, long id, AgentAnalysisDto request) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentSettingsUseCase.updateAnalysis(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<AgentLimitsDto>> getLimits(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentSettingsUseCase.findLimits(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<AgentLimitsDto>> updateLimits(long companyId, long id, AgentLimitsDto request) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentSettingsUseCase.updateLimits(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<AgentAdvancedDto>> getAdvanced(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentSettingsUseCase.findAdvanced(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<AgentAdvancedDto>> updateAdvanced(long companyId, long id, AgentAdvancedDto request) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentSettingsUseCase.updateAdvanced(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<AgentPronunciationDto>> getPronunciation(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentSettingsUseCase.findPronunciation(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<AgentPronunciationDto>> updatePronunciation(long companyId, long id, AgentPronunciationDto request) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentSettingsUseCase.updatePronunciation(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<AgentPostCallActionsDto>> getPostCallActions(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentSettingsUseCase.findPostCallActions(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<AgentPostCallActionsDto>> updatePostCallActions(long companyId, long id, AgentPostCallActionsDto request) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentSettingsUseCase.updatePostCallActions(companyId, id, request)));
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

    @Override
    public ResponseEntity<ResponseData<List<AgentTestCase>>> testCases(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(evaluationUseCase.findTestCases(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<AgentTestCase>> createTestCase(long companyId, long id, SaveAgentTestCaseRequest request) {
        return ResponseEntity.ok(ResponseData.ok(evaluationUseCase.createTestCase(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<AgentTestCase>> updateTestCase(long companyId, long id, long caseId,
                                                                      SaveAgentTestCaseRequest request) {
        return ResponseEntity.ok(ResponseData.ok(evaluationUseCase.updateTestCase(companyId, id, caseId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> deleteTestCase(long companyId, long id, long caseId) {
        evaluationUseCase.deleteTestCase(companyId, id, caseId);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<AgentEvalRun>> evaluate(long companyId, long id, Integer version) {
        return ResponseEntity.ok(ResponseData.ok(evaluationUseCase.evaluate(companyId, id, version)));
    }

    @Override
    public ResponseEntity<ResponseData<List<AgentEvalRun>>> evalRuns(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(evaluationUseCase.findRuns(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<AgentEvalRunDetail>> evalRun(long companyId, long id, long runId) {
        return ResponseEntity.ok(ResponseData.ok(evaluationUseCase.findRun(companyId, id, runId)));
    }
}
