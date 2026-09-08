package uz.murodjon.robotcallv2.aiagent.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.aiagent.application.dto.*;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentFilter;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.tool.application.dto.ToolRow;

import java.util.List;

/**
 * AI agents — who speaks a call (V12).
 *
 * <p>An agent binds a scenario (what is said) to a voice, a persona, a model and a set of
 * SIP trunks. A campaign dials with one; an inbound route answers with one. Nothing else
 * in the API decides those settings any more.
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

    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @PostMapping
    ResponseEntity<ResponseData<AiAgentRow>> create(@CurrentCompanyId long companyId,
                                                    @Valid @RequestBody CreateAiAgentRequest request);

    @PreAuthorize("hasAuthority('AI_AGENT_READ')")
    @PostMapping({"/filter", "/list"})
    ResponseEntity<ResponseData<PageableData<AiAgentRow>>> filter(@CurrentCompanyId long companyId,
                                                                  @Valid @RequestBody AiAgentFilter filter);

    @PreAuthorize("hasAuthority('AI_AGENT_READ')")
    @GetMapping("/{id:\\d+}")
    ResponseEntity<ResponseData<AiAgentRow>> get(@CurrentCompanyId long companyId, @PathVariable long id);

    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @PutMapping("/{id:\\d+}")
    ResponseEntity<ResponseData<AiAgentRow>> update(@CurrentCompanyId long companyId, @PathVariable long id,
                                                    @Valid @RequestBody UpdateAiAgentRequest request);

    /** 409 while any campaign or inbound route still runs this agent. */
    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @DeleteMapping("/{id:\\d+}")
    ResponseEntity<ResponseData<Void>> delete(@CurrentCompanyId long companyId, @PathVariable long id);

    // Scenario & Behavior section
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

    // Analysis section
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

    // Limits section
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

    // Advanced section
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

    // Pronunciation section
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

    // Post-Call Actions section
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

    // Tools section
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
}
