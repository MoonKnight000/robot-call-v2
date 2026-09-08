package uz.murodjon.robotcallv2.widget.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.agent.ari.AriService;
import uz.murodjon.robotcallv2.agent.ari.WebTestProperties;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.shared.exception.*;
import uz.murodjon.robotcallv2.widget.application.dto.*;
import uz.murodjon.robotcallv2.widget.application.port.input.AgentWidgetUseCase;
import uz.murodjon.robotcallv2.widget.application.port.output.AgentWidgetRepository;
import uz.murodjon.robotcallv2.widget.domain.entity.AgentWidget;
import uz.murodjon.robotcallv2.widget.domain.entity.WidgetTheme;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AgentWidgetService implements AgentWidgetUseCase {

    /** Shown to the visitor before the microphone opens, so it is their language, not ours. */
    private static final String DEFAULT_CONSENT_TEXT =
            "Ushbu ovozli suhbat sifatni yaxshilash uchun yozib olinadi va matnga o'giriladi.";

    /** Served from this application's own static resources. */
    private static final String WIDGET_SCRIPT_URL = "/widgets/v1/widget.js";

    /** The SIP header the visitor's browser carries its booked session id in. */
    private static final String WIDGET_SESSION_HEADER = "X-Web-Test";

    private final AgentWidgetRepository repository;
    private final AriService ariService;
    private final WidgetCallGate callGate;
    private final WebTestProperties webTestProperties;
    private final AiAgentUseCase aiAgentUseCase;
    private final AuditService auditService;

    public AgentWidgetService(
            AgentWidgetRepository repository,
            AriService ariService,
            WidgetCallGate callGate,
            WebTestProperties webTestProperties,
            AiAgentUseCase aiAgentUseCase,
            AuditService auditService
    ) {
        this.repository = repository;
        this.ariService = ariService;
        this.callGate = callGate;
        this.webTestProperties = webTestProperties;
        this.aiAgentUseCase = aiAgentUseCase;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public WidgetRow createWidget(long companyId, long agentId, CreateWidgetRequest request) {
        AiAgent agent = aiAgentUseCase.requireAgent(companyId, agentId);

        String widgetKey = "wgt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        WidgetTheme theme = request.theme() != null ? request.theme() : WidgetTheme.defaultTheme();

        AgentWidget widget = new AgentWidget(
                0L,
                widgetKey,
                companyId,
                agentId,
                request.name().trim(),
                request.enabled() == null || request.enabled(),
                List.copyOf(request.allowedOrigins()),
                theme,
                request.consentRequired() == null || request.consentRequired(),
                request.consentText() != null ? request.consentText().trim() : DEFAULT_CONSENT_TEXT,
                Instant.now(),
                Instant.now()
        );

        AgentWidget saved = repository.save(widget);
        auditService.record(companyId, "WIDGET_CREATE", "agent_widget", String.valueOf(saved.id()), saved.name());
        return toRow(saved, agent.name());
    }

    @Override
    @Transactional
    public WidgetRow updateWidget(long companyId, long widgetId, UpdateWidgetRequest request) {
        AgentWidget existing = repository.findByCompanyIdAndId(companyId, widgetId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.AGENT_WIDGET_NOT_FOUND, widgetId));

        AiAgent agent = aiAgentUseCase.requireAgent(companyId, existing.agentId());

        AgentWidget updated = new AgentWidget(
                existing.id(),
                existing.widgetKey(),
                existing.companyId(),
                existing.agentId(),
                request.name() != null ? request.name().trim() : existing.name(),
                request.enabled() != null ? request.enabled() : existing.enabled(),
                request.allowedOrigins() != null ? request.allowedOrigins() : existing.allowedOrigins(),
                request.theme() != null ? request.theme() : existing.theme(),
                request.consentRequired() != null ? request.consentRequired() : existing.consentRequired(),
                request.consentText() != null ? request.consentText().trim() : existing.consentText(),
                existing.createdAt(),
                Instant.now()
        );

        AgentWidget saved = repository.save(updated);
        auditService.record(companyId, "WIDGET_UPDATE", "agent_widget", String.valueOf(saved.id()), saved.name());
        return toRow(saved, agent.name());
    }

    @Override
    @Transactional(readOnly = true)
    public WidgetRow findWidget(long companyId, long widgetId) {
        AgentWidget widget = repository.findByCompanyIdAndId(companyId, widgetId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.AGENT_WIDGET_NOT_FOUND, widgetId));
        AiAgent agent = aiAgentUseCase.requireAgent(companyId, widget.agentId());
        return toRow(widget, agent.name());
    }

    @Override
    @Transactional(readOnly = true)
    public List<WidgetRow> findWidgetsByAgentId(long companyId, long agentId) {
        AiAgent agent = aiAgentUseCase.requireAgent(companyId, agentId);
        return repository.findByCompanyIdAndAgentId(companyId, agentId)
                .stream()
                .map(w -> toRow(w, agent.name()))
                .toList();
    }

    @Override
    @Transactional
    public void deleteWidget(long companyId, long widgetId) {
        AgentWidget widget = repository.findByCompanyIdAndId(companyId, widgetId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.AGENT_WIDGET_NOT_FOUND, widgetId));
        repository.deleteByCompanyIdAndId(companyId, widgetId);
        auditService.record(companyId, "WIDGET_DELETE", "agent_widget", String.valueOf(widgetId), widget.name());
    }

    @Override
    @Transactional(readOnly = true)
    public PublicWidgetConfigResponse findPublicConfig(String widgetKey, String origin) {
        AgentWidget widget = requirePublicWidget(widgetKey, origin);
        AiAgent agent = aiAgentUseCase.requireAgent(widget.companyId(), widget.agentId());

        return new PublicWidgetConfigResponse(
                widget.widgetKey(),
                widget.name(),
                agent.name(),
                widget.themeOrDefault(),
                widget.consentRequired(),
                widget.consentText()
        );
    }

    @Override
    @Transactional
    public PublicWidgetSessionResponse startPublicSession(String widgetKey, String origin, String language) {
        AgentWidget widget = requirePublicWidget(widgetKey, origin);
        if (webTestProperties.wsUrl() == null || webTestProperties.wsUrl().isBlank()) {
            throw new ExternalServiceException(ErrorCode.WEB_TEST_NOT_CONFIGURED, "asterisk");
        }
        AiAgent agent = aiAgentUseCase.requireAgent(widget.companyId(), widget.agentId());
        callGate.checkCallAllowed(agent);

        String sessionId = ariService.prepareWidgetCall(widget.companyId(), agent, language);
        callGate.recordBooking(agent, sessionId);
        auditService.record(widget.companyId(), "WIDGET_CALL_START", "agent_widget",
                String.valueOf(widget.id()), origin);
        return new PublicWidgetSessionResponse(sessionId, webTestProperties.wsUrl(),
                webTestProperties.sipUser(), webTestProperties.sipPassword(),
                webTestProperties.dialNumber(), WIDGET_SESSION_HEADER);
    }

    /**
     * The widget behind a public request, or a refusal. Shared by the two public
     * endpoints so a disabled widget and a foreign origin are rejected identically
     * whether the page is reading the configuration or starting a call.
     */
    private AgentWidget requirePublicWidget(String widgetKey, String origin) {
        AgentWidget widget = repository.findByWidgetKey(widgetKey)
                .orElseThrow(() -> new NotFoundException(ErrorCode.AGENT_WIDGET_NOT_FOUND, widgetKey));
        if (!widget.enabled()) {
            throw new ConflictException(ErrorCode.AGENT_WIDGET_DISABLED, widgetKey);
        }
        // A missing Origin is refused rather than waved through: a browser on a real page
        // always sends one, so its absence means the caller is not the page the widget was
        // issued to — and letting it past would make the allow-list opt-out by omission.
        //
        // An empty list denies for the same reason. The widget key is printed in the
        // customer's own page, so it is public by construction; the allow-list is the only
        // thing that ties it to one site. A widget nobody has scoped yet is not ready to
        // take calls, and refusing says so — where serving every origin until someone
        // remembers to fill the list in says nothing at all. "*" stays available for the
        // sites that genuinely want it, but it has to be written down.
        List<String> allowedOrigins = widget.allowedOriginsOrEmpty();
        boolean originAllowed = origin != null && allowedOrigins.stream()
                .anyMatch(allowed -> allowed.equals("*") || allowed.equalsIgnoreCase(origin));
        if (!originAllowed) {
            throw new ForbiddenException(ErrorCode.AGENT_WIDGET_ORIGIN_FORBIDDEN, origin);
        }
        return widget;
    }

    private WidgetRow toRow(AgentWidget widget, String agentName) {
        String snippet = String.format(
                "<script src=\"%s\" data-widget-key=\"%s\" async></script>",
                WIDGET_SCRIPT_URL,
                widget.widgetKey()
        );

        return new WidgetRow(
                widget.id(),
                widget.widgetKey(),
                widget.companyId(),
                widget.agentId(),
                agentName,
                widget.name(),
                widget.enabled(),
                widget.allowedOriginsOrEmpty(),
                widget.themeOrDefault(),
                widget.consentRequired(),
                widget.consentText(),
                snippet,
                WIDGET_SCRIPT_URL,
                widget.createdAt(),
                widget.updatedAt()
        );
    }
}
