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
 *
 * <p>The last endpoint is the odd one out: it is called by the embed script on a public
 * page, so it carries no token and is keyed by the widget's own key. It is declared
 * {@code permitAll} in {@code SecurityConfig}, and the widget's allowed-origins list is
 * what keeps that key from working when lifted onto another site.
 */
@RequestMapping("/api")
public interface AgentWidgetController {

    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'COMPANY_ADMIN', 'OPERATOR')")
    @PostMapping("/ai-agents/{agentId:\\d+}/widgets")
    ResponseEntity<ResponseData<WidgetRow>> create(@CurrentCompanyId long companyId,
                                                    @PathVariable long agentId,
                                                    @Valid @RequestBody CreateWidgetRequest request);

    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'COMPANY_ADMIN', 'OPERATOR')")
    @GetMapping("/ai-agents/{agentId:\\d+}/widgets")
    ResponseEntity<ResponseData<List<WidgetRow>>> listByAgent(@CurrentCompanyId long companyId,
                                                               @PathVariable long agentId);

    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'COMPANY_ADMIN', 'OPERATOR')")
    @GetMapping("/widgets/{widgetId:\\d+}")
    ResponseEntity<ResponseData<WidgetRow>> get(@CurrentCompanyId long companyId,
                                                 @PathVariable long widgetId);

    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'COMPANY_ADMIN', 'OPERATOR')")
    @PutMapping("/widgets/{widgetId:\\d+}")
    ResponseEntity<ResponseData<WidgetRow>> update(@CurrentCompanyId long companyId,
                                                    @PathVariable long widgetId,
                                                    @Valid @RequestBody UpdateWidgetRequest request);

    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'COMPANY_ADMIN', 'OPERATOR')")
    @DeleteMapping("/widgets/{widgetId:\\d+}")
    ResponseEntity<ResponseData<Void>> delete(@CurrentCompanyId long companyId,
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
