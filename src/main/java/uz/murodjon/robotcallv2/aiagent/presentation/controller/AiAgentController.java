package uz.murodjon.robotcallv2.aiagent.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.aiagent.application.dto.*;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentEvalRun;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentTestCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentVersionChange;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentFilter;
import uz.murodjon.robotcallv2.aiagent.domain.entity.EngineProfile;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentEvalMode;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.tool.application.dto.ToolRow;

import java.util.List;

/**
 * AI agents — who speaks a call (V12, docs/api/ai-agents.md).
 *
 * <p>An agent binds a scenario (what is said) to a voice, a persona, a model and a set of
 * SIP trunks. A campaign dials with one; an inbound route answers with one.
 */
@RequestMapping("/api/ai-agents")
public interface AiAgentController {

    @PreAuthorize("hasAuthority('AI_AGENT_READ')")
    @GetMapping("/templates")
    ResponseEntity<ResponseData<List<TemplatePresetDto>>> getTemplates(
            @CurrentCompanyId long companyId,
            @RequestParam(required = false) String language);

    @PreAuthorize("hasAuthority('AI_AGENT_READ')")
    @GetMapping("/engine-options")
    ResponseEntity<ResponseData<EngineOptionsResponse>> getEngineOptions();

    /**
     * Ready-made engine bundles (speed / cost / quality) this build can run in full. A profile
     * whose provider or model is missing here is left out of the list.
     */
    @PreAuthorize("hasAuthority('AI_AGENT_READ')")
    @GetMapping("/engine-profiles")
    ResponseEntity<ResponseData<List<EngineProfile>>> getEngineProfiles();

    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @PostMapping
    ResponseEntity<ResponseData<AiAgentRow>> create(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody CreateAiAgentRequest request);

    @PreAuthorize("hasAuthority('AI_AGENT_READ')")
    @PostMapping({"/filter", "/list"})
    ResponseEntity<ResponseData<PageableData<AiAgentRow>>> filter(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody AiAgentFilter filter);

    @PreAuthorize("hasAuthority('AI_AGENT_READ')")
    @GetMapping("/{id:\\d+}")
    ResponseEntity<ResponseData<AiAgentRow>> get(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @PutMapping("/{id:\\d+}")
    ResponseEntity<ResponseData<AiAgentRow>> update(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @Valid @RequestBody UpdateAiAgentRequest request);

    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @DeleteMapping("/{id:\\d+}")
    ResponseEntity<ResponseData<Void>> delete(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @PostMapping("/{id:\\d+}/publish")
    ResponseEntity<ResponseData<AiAgentVersionRow>> publish(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @Valid @RequestBody(required = false) PublishAiAgentRequest request);

    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @PostMapping("/{id:\\d+}/rollback/{version:\\d+}")
    ResponseEntity<ResponseData<AiAgentVersionRow>> rollback(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @PathVariable int version);

    @PreAuthorize("hasAuthority('AI_AGENT_READ')")
    @GetMapping("/{id:\\d+}/versions")
    ResponseEntity<ResponseData<List<AiAgentVersionRow>>> versions(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    @PreAuthorize("hasAuthority('AI_AGENT_READ')")
    @GetMapping("/{id:\\d+}/versions/{version:\\d+}")
    ResponseEntity<ResponseData<AiAgentRow>> version(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @PathVariable int version);

    /**
     * Settings that differ from version {@code from} to version {@code to}; without {@code to}, against
     * the draft — what a publish would change.
     */
    @PreAuthorize("hasAuthority('AI_AGENT_READ')")
    @GetMapping("/{id:\\d+}/versions/diff")
    ResponseEntity<ResponseData<List<AgentVersionChange>>> diffVersions(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @RequestParam int from,
            @RequestParam(required = false) Integer to);

    /** Publishes the draft as a candidate beside the live version and sends part of the traffic to it. */
    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @PostMapping("/{id:\\d+}/rollout")
    ResponseEntity<ResponseData<AgentRolloutRow>> startRollout(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @Valid @RequestBody StartAgentRolloutRequest request);

    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @PutMapping("/{id:\\d+}/rollout")
    ResponseEntity<ResponseData<AgentRolloutRow>> updateRollout(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @Valid @RequestBody UpdateAgentRolloutRequest request);

    /** The running rollout, with each version's calls and conversions since it started. */
    @PreAuthorize("hasAuthority('AI_AGENT_READ')")
    @GetMapping("/{id:\\d+}/rollout")
    ResponseEntity<ResponseData<AgentRolloutRow>> rollout(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    /** The candidate goes live for every caller. */
    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @PostMapping("/{id:\\d+}/rollout/promote")
    ResponseEntity<ResponseData<AiAgentVersionRow>> promoteRollout(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    /** The candidate is archived; every caller is back on the live version. */
    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @DeleteMapping("/{id:\\d+}/rollout")
    ResponseEntity<ResponseData<Void>> cancelRollout(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @PostMapping("/{id:\\d+}/clone")
    ResponseEntity<ResponseData<AiAgentRow>> clone(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    @PreAuthorize("hasAuthority('AI_AGENT_READ')")
    @GetMapping("/{id:\\d+}/scenario")
    ResponseEntity<ResponseData<AgentScenarioDto>> getScenario(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @PutMapping("/{id:\\d+}/scenario")
    ResponseEntity<ResponseData<AgentScenarioDto>> updateScenario(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @Valid @RequestBody AgentScenarioDto request);

    @PreAuthorize("hasAuthority('AI_AGENT_READ')")
    @GetMapping("/{id:\\d+}/analysis")
    ResponseEntity<ResponseData<AgentAnalysisDto>> getAnalysis(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @PutMapping("/{id:\\d+}/analysis")
    ResponseEntity<ResponseData<AgentAnalysisDto>> updateAnalysis(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @Valid @RequestBody AgentAnalysisDto request);

    @PreAuthorize("hasAuthority('AI_AGENT_READ')")
    @GetMapping("/{id:\\d+}/limits")
    ResponseEntity<ResponseData<AgentLimitsDto>> getLimits(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @PutMapping("/{id:\\d+}/limits")
    ResponseEntity<ResponseData<AgentLimitsDto>> updateLimits(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @Valid @RequestBody AgentLimitsDto request);

    @PreAuthorize("hasAuthority('AI_AGENT_READ')")
    @GetMapping("/{id:\\d+}/advanced")
    ResponseEntity<ResponseData<AgentAdvancedDto>> getAdvanced(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @PutMapping("/{id:\\d+}/advanced")
    ResponseEntity<ResponseData<AgentAdvancedDto>> updateAdvanced(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @Valid @RequestBody AgentAdvancedDto request);

    @PreAuthorize("hasAuthority('AI_AGENT_READ')")
    @GetMapping("/{id:\\d+}/pronunciation")
    ResponseEntity<ResponseData<AgentPronunciationDto>> getPronunciation(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @PutMapping("/{id:\\d+}/pronunciation")
    ResponseEntity<ResponseData<AgentPronunciationDto>> updatePronunciation(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @Valid @RequestBody AgentPronunciationDto request);

    @PreAuthorize("hasAuthority('AI_AGENT_READ')")
    @GetMapping("/{id:\\d+}/post-call-actions")
    ResponseEntity<ResponseData<AgentPostCallActionsDto>> getPostCallActions(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @PutMapping("/{id:\\d+}/post-call-actions")
    ResponseEntity<ResponseData<AgentPostCallActionsDto>> updatePostCallActions(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @Valid @RequestBody AgentPostCallActionsDto request);

    @PreAuthorize("hasAuthority('AI_AGENT_READ')")
    @GetMapping("/{id:\\d+}/tools")
    ResponseEntity<ResponseData<List<ToolRow>>> getTools(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @PostMapping("/{id:\\d+}/tools/{toolId:\\d+}")
    ResponseEntity<ResponseData<Void>> attachTool(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @PathVariable long toolId);

    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @DeleteMapping("/{id:\\d+}/tools/{toolId:\\d+}")
    ResponseEntity<ResponseData<Void>> detachTool(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @PathVariable long toolId);

    @PreAuthorize("hasAuthority('AI_AGENT_READ')")
    @GetMapping("/{id:\\d+}/test-cases")
    ResponseEntity<ResponseData<List<AgentTestCase>>> testCases(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @PostMapping("/{id:\\d+}/test-cases")
    ResponseEntity<ResponseData<AgentTestCase>> createTestCase(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @Valid @RequestBody SaveAgentTestCaseRequest request);

    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @PutMapping("/{id:\\d+}/test-cases/{caseId:\\d+}")
    ResponseEntity<ResponseData<AgentTestCase>> updateTestCase(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @PathVariable long caseId,
            @Valid @RequestBody SaveAgentTestCaseRequest request);

    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @DeleteMapping("/{id:\\d+}/test-cases/{caseId:\\d+}")
    ResponseEntity<ResponseData<Void>> deleteTestCase(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @PathVariable long caseId);

    /**
     * Starts an evaluation run. {@code repeat} plays each case that many times (1–10) to show a
     * flaky one. Allowed to an API key holding {@code AGENT_EVAL_RUN}, for CI: it changes nothing
     * about the agent, and the run is polled with {@code GET /eval-runs/{runId}}.
     */
    @PreAuthorize("hasAnyAuthority('AI_AGENT_EDIT', 'AGENT_EVAL_RUN')")
    @PostMapping("/{id:\\d+}/evaluate")
    ResponseEntity<ResponseData<AgentEvalRun>> evaluate(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @RequestParam(required = false) Integer version,
            @RequestParam(required = false) AgentEvalMode mode,
            @RequestParam(defaultValue = "1") int repeat);

    @PreAuthorize("hasAuthority('AI_AGENT_READ')")
    @GetMapping("/{id:\\d+}/eval-runs")
    ResponseEntity<ResponseData<List<AgentEvalRun>>> evalRuns(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    @PreAuthorize("hasAuthority('AI_AGENT_READ')")
    @GetMapping("/{id:\\d+}/eval-runs/{runId:\\d+}")
    ResponseEntity<ResponseData<AgentEvalRunDetail>> evalRun(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @PathVariable long runId);
}
