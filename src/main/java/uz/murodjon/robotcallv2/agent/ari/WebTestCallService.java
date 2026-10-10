package uz.murodjon.robotcallv2.agent.ari;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.dialog.CallContext;
import uz.murodjon.robotcallv2.agent.dialog.DialogProperties;
import uz.murodjon.robotcallv2.agent.dialog.TestContextProperties;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallAttempt;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignTargetUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignUseCase;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.crm.application.port.input.CrmUseCase;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmClientSnapshot;
import uz.murodjon.robotcallv2.dialer.application.dto.OutboundCall;
import uz.murodjon.robotcallv2.dialer.domain.service.CallContextFactory;
import uz.murodjon.robotcallv2.memory.application.port.input.ClientMemoryUseCase;
import uz.murodjon.robotcallv2.memory.domain.entity.ClientMemory;
import uz.murodjon.robotcallv2.scenario.application.port.input.ScenarioUseCase;
import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Calls with nobody to dial: browser test calls and website widget calls, which arrive over
 * WebRTC carrying a session id, and the scenario and facts a manual or test call runs with.
 */
@Service
class WebTestCallService {

    private static final Logger log = LoggerFactory.getLogger(WebTestCallService.class);

    /** Phone recorded on a browser test call's attempt row — there is no number, as with {@code MANUAL}. */
    private static final String WEB_TEST_PHONE = "WEB-TEST";
    /**
     * Phone recorded on a call a website visitor started from an embedded widget. Same
     * shape as a browser test — there is no number to dial, the browser dials us — but a
     * different label, because these are real conversations with real people and reading
     * them as tests in the reports would understate what the agent handled.
     */
    private static final String WIDGET_PHONE = "WIDGET";

    private final PendingCallRegistry pendingCallRegistry;
    private final OutboundDialService outboundDialService;
    private final CampaignUseCase campaignUseCase;
    private final CampaignTargetUseCase campaignTargetUseCase;
    private final AiAgentUseCase aiAgentService;
    private final CrmUseCase crmUseCase;
    private final ClientMemoryUseCase clientMemoryUseCase;
    private final ScenarioUseCase scenarioUseCase;
    private final DialogProperties dialogProperties;
    private final AuditUseCase audit;
    private final ObjectMapper objectMapper;

    WebTestCallService(PendingCallRegistry pendingCallRegistry,
                       OutboundDialService outboundDialService,
                       CampaignUseCase campaignUseCase,
                       CampaignTargetUseCase campaignTargetUseCase,
                       AiAgentUseCase aiAgentService,
                       CrmUseCase crmUseCase,
                       ClientMemoryUseCase clientMemoryUseCase,
                       ScenarioUseCase scenarioUseCase,
                       DialogProperties dialogProperties,
                       AuditUseCase audit,
                       ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.pendingCallRegistry = pendingCallRegistry;
        this.outboundDialService = outboundDialService;
        this.campaignUseCase = campaignUseCase;
        this.campaignTargetUseCase = campaignTargetUseCase;
        this.aiAgentService = aiAgentService;
        this.crmUseCase = crmUseCase;
        this.clientMemoryUseCase = clientMemoryUseCase;
        this.scenarioUseCase = scenarioUseCase;
        this.dialogProperties = dialogProperties;
        this.audit = audit;
    }

    /**
     * Registers a browser test call ({@code POST /api/calls/web-test}) and returns the
     * session id the browser must dial in with. With a {@code campaignId} the call runs
     * under that campaign's full profile (scenario, voices, persona, ambient sound, ...)
     * and, given a {@code targetId}, that target's facts; with an {@code aiAgentId} it runs
     * under that agent's profile with the test facts; otherwise it is a manual test call of
     * the draft, the scenario, or the default test scenario.
     */
    String prepareWebTest(long companyId, Long campaignId, Long targetId, Long aiAgentId, Integer agentVersion,
                          Long scenarioId, ScenarioDefinition definition) {
        pendingCallRegistry.purgeExpiredWebTests();
        OutboundCall outbound = campaignId != null ? buildWebTestProfile(companyId, campaignId, targetId, agentVersion)
                : aiAgentId != null ? buildAgentWebTestProfile(companyId, aiAgentId, agentVersion)
                : null;
        // The scenario has to be the caller's own, and the session carries the company: it
        // used to carry neither, so a scenario test ran as the default company — any tenant
        // could run company 1's private scenarios and bill their tests to it.
        if (scenarioId != null) {
            scenarioUseCase.requireScenario(companyId, scenarioId);
        }
        outboundDialService.requireCompanyMayCall(companyId);
        String sessionId = UUID.randomUUID().toString();
        pendingCallRegistry.registerWebTest(sessionId, new WebTestCall(outbound, definition, scenarioId, companyId, Instant.now()));
        audit.record(companyId, "CALL_WEB_TEST", "call", sessionId,
                campaignId != null ? "campaign " + campaignId + (targetId != null ? " target " + targetId : "")
                        : aiAgentId != null ? "agent " + aiAgentId : null);
        return sessionId;
    }

    /**
     * Registers a call a website visitor is about to start from an embedded widget, and
     * returns the session id their browser dials in with.
     *
     * <p>The same waiting-session mechanism as {@link #prepareWebTest}, because from
     * Asterisk's side it is the same thing: a browser arrives over WebRTC carrying a
     * session id instead of a number we dialled. What differs is the profile — a widget
     * call belongs to an agent, not to a campaign, so there is no target, no A/B variant
     * and no campaign statistic to move.
     *
     * <p>The visitor is anonymous: the call carries no facts, so an agent whose scenario
     * expects a debt amount finds none and must ask. That is the correct behaviour — the
     * page has no idea who is standing in front of it.
     */
    String prepareWidgetCall(long companyId, AiAgent agent, String language) {
        pendingCallRegistry.purgeExpiredWebTests();

        // Resolved here only to fail now rather than later: the scenario itself is looked
        // up again in setupMedia, but an agent with none must be refused while the visitor
        // is still looking at a button, not after their browser has connected.
        aiAgentService.resolveScenario(companyId, agent);
        outboundDialService.requireCompanyMayCall(companyId);

        String callLanguage = language != null && !language.isBlank() ? language : agent.language();
        OutboundCall outbound = new OutboundCall(null, CallAttempt.NO_TARGET, null, WIDGET_PHONE,
                callLanguage, agent.voiceFor(callLanguage), new CallContext(Map.of(), null), agent, companyId,
                null, null, null, null);

        String sessionId = UUID.randomUUID().toString();
        pendingCallRegistry.registerWebTest(sessionId, new WebTestCall(outbound, null, null, companyId, Instant.now()));
        audit.record(companyId, "CALL_WIDGET", "call", sessionId, "agent " + agent.id());
        return sessionId;
    }

    /**
     * The campaign's call profile for a browser test — what {@code CallTaskConsumer} builds
     * for a real target, minus the target itself: the attempt is booked on the manual
     * target and the phone is a placeholder, so no campaign statistic moves.
     */
    private OutboundCall buildWebTestProfile(long companyId, long campaignId, Long targetId, Integer agentVersion) {
        Campaign campaign = campaignUseCase.requireCampaign(companyId, campaignId);
        if (campaign.aiAgentId() == null) {
            throw new ConflictException(ErrorCode.CAMPAIGN_NOT_AI, campaignId, campaign.dialMode());
        }
        // The draft by default — a browser test is how an editor hears a change before publishing it.
        AiAgent agent = aiAgentService.requireAgentVersion(campaign.companyId(), campaign.aiAgentId(), agentVersion);
        Scenario scenario = aiAgentService.resolveScenario(campaign.companyId(), agent);
        CampaignTarget target = targetId != null
                ? campaignTargetUseCase.requireTarget(companyId, campaignId, targetId) : null;
        String language = target != null && target.language() != null ? target.language() : agent.language();
        CallContext context;
        if (target != null) {
            CrmClientSnapshot crm = crmUseCase.fetchClient(campaign.companyId(), target.clientId());
            ClientMemory memory = clientMemoryUseCase.findByCompanyIdAndPhone(campaign.companyId(), target.phone());
            context = CallContextFactory.mergeCrmFacts(
                    CallContextFactory.readContextData(objectMapper, target.contextData(), null, scenario.definition().factSchema()), crm)
                    .withMemory(memory);
        } else {
            context = buildTestContext(scenario.definition());
        }
        return new OutboundCall(campaign.id(), CallAttempt.NO_TARGET, null, WEB_TEST_PHONE,
                language, agent.voiceFor(language), context, agent, campaign.companyId(),
                null, null, null, null);
    }

    /**
     * An agent's call profile for a browser test, without a campaign: its voice, persona and
     * models, its scenario, and the configured test facts — how an editor hears the agent
     * before any campaign points at it.
     */
    private OutboundCall buildAgentWebTestProfile(long companyId, long aiAgentId, Integer agentVersion) {
        AiAgent agent = aiAgentService.requireAgentVersion(companyId, aiAgentId, agentVersion);
        Scenario scenario = aiAgentService.resolveScenario(companyId, agent);
        String language = agent.language();
        return new OutboundCall(null, CallAttempt.NO_TARGET, null, WEB_TEST_PHONE,
                language, agent.voiceFor(language), buildTestContext(scenario.definition()), agent, companyId,
                null, null, null, null);
    }

    /** The scenario a manual or test call runs: its unsaved draft, the scenario it named, or the default test scenario. */
    Scenario resolveManualScenario(long companyId, String channelId) {
        ScenarioDefinition adHoc = pendingCallRegistry.takeManualDefinition(channelId);
        if (adHoc != null) {
            return new Scenario(0, "adhoc-test-call", 0, "Ad-hoc test call", null, false, false, adHoc,
                    Instant.now(), null);
        }
        Long requested = pendingCallRegistry.takeManualScenarioId(channelId);
        return requested != null && requested != PendingCallRegistry.NO_EXPLICIT_SCENARIO
                ? scenarioUseCase.requireScenario(companyId, requested)
                : scenarioUseCase.requireScenarioByKey(defaultTestScenarioKey());
    }

    private String defaultTestScenarioKey() {
        TestContextProperties t = dialogProperties.testContext();
        return t != null ? t.scenarioKey() : "debt-collection";
    }

    /** The configured test facts ({@code voice-agent.dialog.test-context}), limited to those the scenario declares. */
    CallContext buildTestContext(ScenarioDefinition scenario) {
        TestContextProperties t = dialogProperties.testContext();
        Map<String, Object> facts = new HashMap<>();
        String clientName = t != null && t.clientName() != null ? t.clientName() : "Murodjon";
        java.math.BigDecimal debtAmount = t != null && t.debtAmount() != null ? t.debtAmount() : new java.math.BigDecimal("1500000");
        String currency = t != null && t.currency() != null ? t.currency() : "so'm";
        String contractNumber = t != null && t.contractNumber() != null ? t.contractNumber() : "12345";
        String goal = t != null && t.goal() != null ? t.goal() : "Qarzdorlik bo'yicha to'lov sanasini kelishish";

        putIfDeclared(facts, scenario, "clientName", clientName);
        putIfDeclared(facts, scenario, "debtAmount", debtAmount);
        putIfDeclared(facts, scenario, "currency", currency);
        putIfDeclared(facts, scenario, "contractNumber", contractNumber);

        LocalDate dueDate = null;
        if (t != null && t.dueDate() != null && !t.dueDate().isBlank()) {
            try {
                dueDate = LocalDate.parse(t.dueDate());
            } catch (Exception e) {
                log.warn("Invalid voice-agent.dialog.test-context.due-date '{}': {}", t.dueDate(), e.getMessage());
            }
        }
        if (dueDate == null) {
            dueDate = LocalDate.now().minusDays(10);
        }
        putIfDeclared(facts, scenario, "dueDate", dueDate);

        if (t != null) {
            putIfDeclared(facts, scenario, "penaltyAmount", t.penaltyAmount());
            putIfDeclared(facts, scenario, "contractCancelDays", t.contractCancelDays());
        }
        return new CallContext(facts, goal);
    }

    private static void putIfDeclared(Map<String, Object> facts, ScenarioDefinition scenario,
                                      String factName, Object value) {
        if (value == null || scenario.factSchema() == null) {
            return;
        }
        boolean declared = scenario.factSchema().stream().anyMatch(f -> f.name().equals(factName));
        if (declared) {
            facts.put(factName, value);
        }
    }
}
