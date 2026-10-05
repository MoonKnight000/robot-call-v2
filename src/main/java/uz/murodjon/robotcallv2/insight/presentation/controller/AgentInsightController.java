package uz.murodjon.robotcallv2.insight.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.insight.application.dto.RejectInsightRequest;
import uz.murodjon.robotcallv2.insight.domain.entity.AgentInsight;
import uz.murodjon.robotcallv2.insight.domain.entity.AgentInsightFilter;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * The improvement inbox ({@code /api/agent-insights}, UNIVERSAL-OPERATOR-PLAN D.3–D.4): what a
 * day of an agent's own calls suggests changing, and a person's decision on each.
 *
 * <p>Proposals are written by the nightly job and cannot be created through the API — an
 * insight nobody's calls asked for is an opinion, not a signal. Accepting is as far as the
 * loop reaches: a prompt change goes into the draft behind the agent's test cases, and
 * publishing stays {@code POST /api/ai-agents/{id}/publish}.
 */
@RequestMapping("/api/agent-insights")
public interface AgentInsightController {

    @PreAuthorize("hasAuthority('AI_AGENT_READ')")
    @PostMapping({"/filter", "/list"})
    ResponseEntity<ResponseData<PageableData<AgentInsight>>> filter(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody AgentInsightFilter filter);

    @PreAuthorize("hasAuthority('AI_AGENT_READ')")
    @GetMapping("/{insightId:\\d+}")
    ResponseEntity<ResponseData<AgentInsight>> get(
            @CurrentCompanyId long companyId,
            @PathVariable long insightId);

    /**
     * Carries the proposal out as far as it can be carried out on its own. A {@code PROMPT_FIX}
     * comes back {@code EVALUATING}: the sentence is in the draft and the agent's test cases are
     * being played against it, and the gate either keeps it or takes it back out again.
     */
    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @PostMapping("/{insightId:\\d+}/accept")
    ResponseEntity<ResponseData<AgentInsight>> accept(
            @CurrentCompanyId long companyId,
            @PathVariable long insightId);

    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @PostMapping("/{insightId:\\d+}/reject")
    ResponseEntity<ResponseData<AgentInsight>> reject(
            @CurrentCompanyId long companyId,
            @PathVariable long insightId,
            @Valid @RequestBody(required = false) RejectInsightRequest request);
}
