package uz.murodjon.robotcallv2.widget.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.widget.application.dto.*;

import java.util.List;

/**
 * Voice widgets embedded on a customer's own website, each one an entry point to a
 * single AI agent.
 */
@RequestMapping("/api")
public interface AgentWidgetController {

    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @PostMapping("/ai-agents/{agentId:\\d+}/widgets")
    ResponseEntity<ResponseData<WidgetRow>> create(
            @CurrentCompanyId long companyId,
            @PathVariable long agentId,
            @Valid @RequestBody CreateWidgetRequest request);

    @PreAuthorize("hasAuthority('AI_AGENT_READ')")
    @GetMapping("/ai-agents/{agentId:\\d+}/widgets")
    ResponseEntity<ResponseData<List<WidgetRow>>> listByAgent(
            @CurrentCompanyId long companyId,
            @PathVariable long agentId);

    @PreAuthorize("hasAuthority('AI_AGENT_READ')")
    @GetMapping("/widgets/{widgetId:\\d+}")
    ResponseEntity<ResponseData<WidgetRow>> get(
            @CurrentCompanyId long companyId,
            @PathVariable long widgetId);

    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @PutMapping("/widgets/{widgetId:\\d+}")
    ResponseEntity<ResponseData<WidgetRow>> update(
            @CurrentCompanyId long companyId,
            @PathVariable long widgetId,
            @Valid @RequestBody UpdateWidgetRequest request);

    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @DeleteMapping("/widgets/{widgetId:\\d+}")
    ResponseEntity<ResponseData<Void>> delete(
            @CurrentCompanyId long companyId,
            @PathVariable long widgetId);

    /** Called by the embed script on the customer's page; no token, keyed by widget key. */
    @GetMapping("/public/widgets/{widgetKey}/config")
    ResponseEntity<ResponseData<PublicWidgetConfigResponse>> getPublicConfig(
            @PathVariable String widgetKey,
            @RequestHeader(value = "Origin", required = false) String origin);

    /**
     * Books the call a visitor just asked for and returns what their browser dials in
     * with. Same origin rules as the configuration endpoint, plus the agent's own daily
     * and concurrent call limits — this is the one endpoint a stranger can spend the
     * company's minutes on.
     */
    @PostMapping("/public/widgets/{widgetKey}/session")
    ResponseEntity<ResponseData<PublicWidgetSessionResponse>> startPublicSession(
            @PathVariable String widgetKey,
            @RequestParam(required = false) String language,
            @RequestHeader(value = "Origin", required = false) String origin);
}
