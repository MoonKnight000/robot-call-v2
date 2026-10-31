package uz.murodjon.robotcallv2.agent.ari;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.EventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.audio.AudioListener;
import uz.murodjon.robotcallv2.agent.audio.LiveAudioMonitor;
import uz.murodjon.robotcallv2.agent.dialog.*;
import uz.murodjon.robotcallv2.agent.flow.CallFlowRunner;
import uz.murodjon.robotcallv2.agent.flow.FlowCall;
import uz.murodjon.robotcallv2.agent.flow.InboundDecision;
import uz.murodjon.robotcallv2.agent.queue.QueueOutcome;
import uz.murodjon.robotcallv2.agent.livekit.LiveKitBridgeClient;
import uz.murodjon.robotcallv2.agent.livekit.LiveKitDialOutcome;
import uz.murodjon.robotcallv2.agent.livekit.LiveKitRoute;
import uz.murodjon.robotcallv2.agent.livekit.LiveKitTelephonyService;
import uz.murodjon.robotcallv2.agent.metrics.CallMetrics;
import uz.murodjon.robotcallv2.agent.realtime.RealtimeAudioBridge;
import uz.murodjon.robotcallv2.agent.routing.CallRouteRegistry;
import uz.murodjon.robotcallv2.agent.rtp.RtpEndpoint;
import uz.murodjon.robotcallv2.agent.rtp.RtpPortAllocator;
import uz.murodjon.robotcallv2.agent.rtp.RtpProperties;
import uz.murodjon.robotcallv2.agent.rtp.WavRecorder;
import uz.murodjon.robotcallv2.agent.session.CallMdc;
import uz.murodjon.robotcallv2.agent.session.CallSession;
import uz.murodjon.robotcallv2.agent.telephony.TelephonyGatewayResolver;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AgentWebhookUseCase;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallAttempt;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignVariantUseCase;
import uz.murodjon.robotcallv2.company.infrastructure.config.CompanyProperties;
import uz.murodjon.robotcallv2.crm.application.port.input.CrmUseCase;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmClientSnapshot;
import uz.murodjon.robotcallv2.dialer.application.dto.OutboundCall;
import uz.murodjon.robotcallv2.dialer.application.port.input.OutboundCallTrackingUseCase;
import uz.murodjon.robotcallv2.dialer.domain.service.CallContextFactory;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundRoute;
import uz.murodjon.robotcallv2.memory.application.port.input.ClientMemoryUseCase;
import uz.murodjon.robotcallv2.memory.domain.entity.ClientMemory;
import uz.murodjon.robotcallv2.scenario.application.port.input.FactWebhookUseCase;
import uz.murodjon.robotcallv2.scenario.domain.entity.FactWebhookRequest;
import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.dialog.CallLanguage;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.dialog.PipelineMode;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.util.PhoneMasker;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;
import uz.murodjon.robotcallv2.siptrunk.application.dto.SipTrunkRow;
import uz.murodjon.robotcallv2.siptrunk.application.port.input.SipTrunkUseCase;
import uz.murodjon.robotcallv2.siptrunk.domain.entity.SipTrunkCredentials;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * A LiveKit-engine call from ringing to teardown — {@link CallSetupService} for a call whose
 * phone leg is a SIP participant in a LiveKit room instead of an Asterisk channel. The room
 * name is the channel id everywhere (attempt row, session, tracking), and the Go media bridge
 * ({@link LiveKitBridgeClient}) stands where Asterisk's externalMedia stands: our
 * {@link RtpEndpoint}, VAD, STT, dialog and TTS run unchanged behind it.
 *
 * <p>A transfer rings a plain number into the room ({@link #transferToNumber}). Not on this engine
 * (Asterisk only): queue waiting lines and softphones, and browser test calls. A route's call flow runs here
 * ({@link #startInboundFlow}); its people come from {@link LiveKitFlowPeople}.
 */
@Service
public class LiveKitCallService {

    private static final Logger log = LoggerFactory.getLogger(LiveKitCallService.class);
    /** What the call shows as its channel in call records ({@code CallSession#channelName}). */
    private static final String CHANNEL_NAME_PREFIX = "LiveKit/";
    private static final String TRUNK = "livekit";

    private final ExecutorService callExecutor;
    private final LiveKitTelephonyService liveKitTelephonyService;
    private final LiveKitBridgeClient liveKitBridgeClient;
    private final CallSessionRegistry callSessionRegistry;
    private final CallTeardownService callTeardownService;
    private final UnansweredCallSettler unansweredCallSettler;
    private final OutboundCallTrackingUseCase outboundCallTrackingUseCase;
    private final CallAttemptUseCase callAttemptUseCase;
    private final CampaignVariantUseCase campaignVariantUseCase;
    private final AiAgentUseCase aiAgentService;
    private final FactWebhookUseCase factWebhookUseCase;
    private final CrmUseCase crmUseCase;
    private final ClientMemoryUseCase clientMemoryUseCase;
    private final AgentWebhookUseCase agentWebhookUseCase;
    private final PreCallWorkflowRunner preCallWorkflowRunner;
    private final RtpProperties rtpProperties;
    private final RtpPortAllocator portAllocator;
    private final EventLoopGroup rtpEventLoopGroup;
    private final DialogProperties dialogProperties;
    private final DialogStarter dialogStarter;
    private final DialogEngine dialogEngine;
    private final DialogRouter dialogRouter;
    private final AudioPipelineAssembler audioPipelineAssembler;
    private final PreConversationAudio preConversationAudio;
    private final CallMetrics metrics;
    private final CallRouteRegistry routeRegistry;
    private final ObjectMapper objectMapper;
    private final OperatorAcceptance operatorAcceptance;
    private final SipTrunkUseCase sipTrunkUseCase;
    private final CompanyProperties companyProperties;
    private final PendingCallRegistry pendingCallRegistry;
    private final WebTestCallService webTestCallService;
    private final TelephonyGatewayResolver telephonyGatewayResolver;
    private final LiveKitOperatorTransfers liveKitOperatorTransfers;
    private final FlowCallRegistry flowCallRegistry;
    private final CallFlowRunner callFlowRunner;
    /** Lazy: the router reaches queues and flows, which reach back to the call lifecycle. */
    private final ObjectProvider<DtmfInputRouter> dtmfInputRouterProvider;

    LiveKitCallService(ExecutorService callExecutor,
                       LiveKitTelephonyService liveKitTelephonyService,
                       LiveKitBridgeClient liveKitBridgeClient,
                       CallSessionRegistry callSessionRegistry,
                       CallTeardownService callTeardownService,
                       UnansweredCallSettler unansweredCallSettler,
                       OutboundCallTrackingUseCase outboundCallTrackingUseCase,
                       CallAttemptUseCase callAttemptUseCase,
                       CampaignVariantUseCase campaignVariantUseCase,
                       AiAgentUseCase aiAgentService,
                       FactWebhookUseCase factWebhookUseCase,
                       CrmUseCase crmUseCase,
                       ClientMemoryUseCase clientMemoryUseCase,
                       AgentWebhookUseCase agentWebhookUseCase,
                       PreCallWorkflowRunner preCallWorkflowRunner,
                       RtpProperties rtpProperties,
                       RtpPortAllocator portAllocator,
                       EventLoopGroup rtpEventLoopGroup,
                       DialogProperties dialogProperties,
                       DialogStarter dialogStarter,
                       DialogEngine dialogEngine,
                       DialogRouter dialogRouter,
                       AudioPipelineAssembler audioPipelineAssembler,
                       PreConversationAudio preConversationAudio,
                       CallMetrics metrics,
                       CallRouteRegistry routeRegistry,
                       ObjectMapper objectMapper,
                       OperatorAcceptance operatorAcceptance,
                       SipTrunkUseCase sipTrunkUseCase,
                       CompanyProperties companyProperties,
                       PendingCallRegistry pendingCallRegistry,
                       WebTestCallService webTestCallService,
                       TelephonyGatewayResolver telephonyGatewayResolver,
                       LiveKitOperatorTransfers liveKitOperatorTransfers,
                       FlowCallRegistry flowCallRegistry,
                       CallFlowRunner callFlowRunner,
                       ObjectProvider<DtmfInputRouter> dtmfInputRouterProvider) {
        this.dtmfInputRouterProvider = dtmfInputRouterProvider;
        this.flowCallRegistry = flowCallRegistry;
        this.callFlowRunner = callFlowRunner;
        this.liveKitOperatorTransfers = liveKitOperatorTransfers;
        this.telephonyGatewayResolver = telephonyGatewayResolver;
        this.pendingCallRegistry = pendingCallRegistry;
        this.webTestCallService = webTestCallService;
        this.callExecutor = callExecutor;
        this.liveKitTelephonyService = liveKitTelephonyService;
        this.liveKitBridgeClient = liveKitBridgeClient;
        this.callSessionRegistry = callSessionRegistry;
        this.callTeardownService = callTeardownService;
        this.unansweredCallSettler = unansweredCallSettler;
        this.outboundCallTrackingUseCase = outboundCallTrackingUseCase;
        this.callAttemptUseCase = callAttemptUseCase;
        this.campaignVariantUseCase = campaignVariantUseCase;
        this.aiAgentService = aiAgentService;
        this.factWebhookUseCase = factWebhookUseCase;
        this.crmUseCase = crmUseCase;
        this.clientMemoryUseCase = clientMemoryUseCase;
        this.agentWebhookUseCase = agentWebhookUseCase;
        this.preCallWorkflowRunner = preCallWorkflowRunner;
        this.rtpProperties = rtpProperties;
        this.portAllocator = portAllocator;
        this.rtpEventLoopGroup = rtpEventLoopGroup;
        this.dialogProperties = dialogProperties;
        this.dialogStarter = dialogStarter;
        this.dialogEngine = dialogEngine;
        this.dialogRouter = dialogRouter;
        this.audioPipelineAssembler = audioPipelineAssembler;
        this.preConversationAudio = preConversationAudio;
        this.metrics = metrics;
        this.routeRegistry = routeRegistry;
        this.objectMapper = objectMapper;
        this.operatorAcceptance = operatorAcceptance;
        this.sipTrunkUseCase = sipTrunkUseCase;
        this.companyProperties = companyProperties;
    }

    /**
     * Rings {@code number} into {@code roomName} over the first of {@code routes} that takes the
     * call, on a call thread, and, once answered, starts the agent on it. The attempt row and the
     * tracking entry already exist under the room's name (LiveKitGateway). The call counts as
     * being set up while it rings, so a phone leg that leaves meanwhile ends it instead of racing
     * the answer.
     */
    public void dialOutbound(String roomName, String number, List<LiveKitRoute> routes, OutboundCall outbound) {
        callSessionRegistry.markSettingUp(roomName);
        callExecutor.execute(() -> CallMdc.with(roomName, () -> {
            try {
                LiveKitDialOutcome dial;
                try {
                    dial = dialOverRoutes(roomName, number, routes);
                } catch (RuntimeException e) {
                    log.warn("[{}] LiveKit dial failed: {}", roomName, e.getMessage());
                    unansweredCallSettler.settleUnanswered(roomName, Disposition.FAILED, null, "LiveKit dial failed");
                    liveKitTelephonyService.hangUp(roomName);
                    return;
                }
                if (!dial.answered()) {
                    unansweredCallSettler.settleUnanswered(roomName, toDisposition(dial.sipStatus()), null,
                            "SIP " + dial.sipStatus());
                    liveKitTelephonyService.hangUp(roomName);
                    return;
                }
                SessionPlan plan;
                try {
                    plan = outbound != null ? createOutboundPlan(outbound) : createManualPlan(roomName);
                } catch (Exception e) {
                    log.error("[{}] answered LiveKit call could not be prepared: {}", roomName, e.getMessage(), e);
                    unansweredCallSettler.settleFailedOutbound(roomName, outboundCallTrackingUseCase.remove(roomName));
                    liveKitTelephonyService.hangUp(roomName);
                    return;
                }
                startSession(roomName, plan);
            } finally {
                callSessionRegistry.finishSettingUp(roomName);
                // A manual or test call that never got as far as its setup leaves nothing waiting for it.
                pendingCallRegistry.forgetUnanswered(roomName);
            }
        }));
    }

    /** An operator's browser joined the room of a call offered to them; see {@link LiveKitOperatorTransfers}. */
    public void onOperatorJoined(String roomName, String identity) {
        liveKitOperatorTransfers.onOperatorJoined(roomName, identity);
    }

    /** An operator's browser left the room. */
    public void onOperatorLeft(String roomName, String identity) {
        liveKitOperatorTransfers.onOperatorLeft(roomName, identity);
    }

    /**
     * Rings over each route in turn until one gives an outcome the callee decided — answered,
     * busy, declined. A route that fails at the trunk (LiveKit could not place the call, or the
     * carrier answered 5xx) passes the call to the next one; the last route's failure is the call's.
     */
    private LiveKitDialOutcome dialOverRoutes(String roomName, String number, List<LiveKitRoute> routes) {
        for (int i = 0; ; i++) {
            LiveKitRoute route = routes.get(i);
            boolean last = i == routes.size() - 1;
            try {
                LiveKitDialOutcome dial = liveKitTelephonyService.dialAndWaitForAnswer(roomName,
                        LiveKitTelephonyService.CALLEE_IDENTITY, number, route.callerId().get(), route.credentials(), Map.of());
                if (last || !dial.failedAtTrunk()) {
                    return dial;
                }
                log.warn("[{}] trunk answered SIP {}; trying the next trunk", roomName, dial.sipStatus());
            } catch (RuntimeException e) {
                if (last) {
                    throw e;
                }
                log.warn("[{}] dial over a trunk failed ({}); trying the next trunk", roomName, e.getMessage());
            }
        }
    }

    /**
     * An inbound caller the inbound rules gave to an AI agent ({@code InboundCallGate}): looks
     * them up as {@link CallSetupService} does for an Asterisk caller and starts the agent.
     *
     * @param caller the caller's number, blank when withheld
     */
    public void startInboundCall(String roomName, InboundRoute route, String caller, String did) {
        if (!callSessionRegistry.markSettingUp(roomName)) {
            log.debug("Ignoring a second join of the phone leg in room {}", roomName);
            return;
        }
        callExecutor.execute(() -> CallMdc.with(roomName, () -> {
            try {
                String callerNumber = caller == null || caller.isBlank() ? null : caller;
                long companyId = route.companyId();
                AiAgent agent = aiAgentService.requireCallAgent(companyId, route.aiAgentId(), callerNumber);
                Scenario scenario = aiAgentService.resolveScenario(companyId, agent);
                String facts = null;
                if (scenario.definition() != null && scenario.definition().factWebhook() != null) {
                    facts = factWebhookUseCase.fetchFacts(scenario.definition().factWebhook(),
                            FactWebhookRequest.inbound(callerNumber, did));
                }
                CrmClientSnapshot crm = callerNumber != null ? crmUseCase.findByPhone(companyId, callerNumber) : null;
                ClientMemory memory = callerNumber != null
                        ? clientMemoryUseCase.findByCompanyIdAndPhone(companyId, callerNumber) : null;
                String language = CallLanguage.resolve(
                        crm != null ? crm.preferredLanguage() : null,
                        memory != null ? memory.preferredLanguage() : null,
                        agent.language());
                CallContext context = CallContextFactory.overlayWebhookFacts(objectMapper,
                        CallContextFactory.mergeCrmFacts(new CallContext(Map.of(), null), crm).withMemory(memory),
                        facts, scenario.definition().factSchema());
                String phone = callerNumber != null ? callerNumber : "INBOUND";
                if (context != null && PhoneNumbers.isValid(phone)) {
                    context = context.withPhone(phone);
                }
                startSession(roomName, new SessionPlan(companyId, agent, scenario, scenario.definition(), language,
                        agent.voiceFor(language), context, phone, route.storedId(), null, false, null));
            } catch (Exception e) {
                log.error("[{}] LiveKit inbound call could not be started: {}", roomName, e.getMessage(), e);
                liveKitTelephonyService.hangUp(roomName);
            } finally {
                callSessionRegistry.finishSettingUp(roomName);
            }
        }));
    }

    /**
     * The caller of an inbound room whose route runs a call flow ({@link CallFlowRunner}); the media
     * is set up as for an agent call, so a flow that reaches an AI agent needs nothing more.
     * Queues, rings and forwards need people the LiveKit engine does not reach yet — see
     * {@link CallFlowRunner}.
     *
     * @param caller the caller's number, blank when withheld
     */
    public void startInboundFlow(String roomName, InboundDecision decision, String caller, String did) {
        if (!callSessionRegistry.markSettingUp(roomName)) {
            log.debug("Ignoring a second join of the phone leg in room {}", roomName);
            return;
        }
        callExecutor.execute(() -> CallMdc.with(roomName, () -> {
            try {
                InboundRoute route = decision.route();
                long companyId = route.companyId();
                String callerNumber = caller == null || caller.isBlank() ? null : caller;
                CrmClientSnapshot crm = callerNumber != null ? crmUseCase.findByPhone(companyId, callerNumber) : null;
                ClientMemory memory = callerNumber != null
                        ? clientMemoryUseCase.findByCompanyIdAndPhone(companyId, callerNumber) : null;
                String knownLanguage = crm != null && crm.preferredLanguage() != null ? crm.preferredLanguage()
                        : memory != null ? memory.preferredLanguage() : null;
                String clientName = crm != null && crm.name() != null ? crm.name()
                        : memory != null ? memory.preferredName() : null;
                startSession(roomName, new SessionPlan(companyId, null, null, null,
                        knownLanguage != null ? knownLanguage : decision.language(), null, null,
                        callerNumber != null ? callerNumber : "INBOUND", route.storedId(), null, false,
                        new FlowEntry(decision, callerNumber, did, knownLanguage != null, clientName)));
            } catch (Exception e) {
                log.error("[{}] LiveKit inbound call flow could not be started: {}", roomName, e.getMessage(), e);
                liveKitTelephonyService.hangUp(roomName);
            } finally {
                callSessionRegistry.finishSettingUp(roomName);
            }
        }));
    }

    /** Walks the call through its flow on this thread, and ends the call if the flow is done before the caller is. */
    private void runFlow(String roomName, SessionPlan plan, RtpEndpoint endpoint, LiveAudioMonitor audioMonitor,
                         long attemptId) {
        FlowEntry entry = plan.flow();
        InboundDecision decision = entry.decision();
        FlowCall call = new FlowCall(roomName, null, endpoint, plan.companyId(), attemptId, entry.callerNumber(),
                entry.did(), plan.inboundRouteId(), plan.language(), entry.languageKnown(), List.of(audioMonitor));
        call.setVip(decision.vip());
        call.setClientName(entry.clientName());
        call.restoreListeners();
        flowCallRegistry.register(call);
        if (decision.vip()) {
            flowCallRegistry.markVip(roomName);
        }
        log.info("LiveKit inbound call {} to {} runs call flow {} (attempt {})", roomName, entry.did(),
                decision.route().callFlowId(), attemptId);
        try {
            callFlowRunner.run(call, decision.graph(), decision.route().callFlowId());
        } finally {
            if (!call.isGone()) {
                liveKitTelephonyService.hangUp(roomName);
            }
        }
    }

    /**
     * The browser of a test or widget call joined its room: the waiting session it was issued for is claimed
     * and the call runs the profile that session carries, as a browser dialling Asterisk would.
     */
    public void onBrowserCallerJoined(String roomName) {
        if (!callSessionRegistry.markSettingUp(roomName)) {
            log.debug("Ignoring a second join of the caller in room {}", roomName);
            return;
        }
        callExecutor.execute(() -> CallMdc.with(roomName, () -> {
            try {
                String sessionId = roomName.substring(LiveKitTelephonyService.BROWSER_ROOM_PREFIX.length());
                if (!pendingCallRegistry.claimWebTest(roomName, sessionId)) {
                    liveKitTelephonyService.hangUp(roomName);
                    return;
                }
                OutboundCall outbound = pendingCallRegistry.findOutboundProfile(roomName);
                startSession(roomName, outbound != null ? createOutboundPlan(outbound) : createManualPlan(roomName));
            } catch (Exception e) {
                log.error("[{}] LiveKit browser call could not be started: {}", roomName, e.getMessage(), e);
                pendingCallRegistry.forgetUnanswered(roomName);
                liveKitTelephonyService.hangUp(roomName);
            } finally {
                callSessionRegistry.finishSettingUp(roomName);
            }
        }));
    }

    /** A transfer asked for from outside the dialog (the manual transfer button): as if the agent had asked for it. */
    void transferCall(String roomName) {
        CallSession session = callSessionRegistry.find(roomName);
        if (session == null) {
            log.info("[{}] transfer asked for a call that is gone", roomName);
            return;
        }
        transfer(roomName, session.companyId(), session.agent());
    }

    /** The caller pressed a key; the media bridge reports it. Goes where an Asterisk call's ARI digit goes. */
    public void onCallerDtmf(String roomName, char digit) {
        if (callSessionRegistry.contains(roomName)) {
            CallMdc.with(roomName, () -> dtmfInputRouterProvider.getObject().onDigit(roomName, digit));
        }
    }

    /**
     * The phone leg left its room. A call this side knows is ended like a StasisEnd: a live
     * one is torn down (which closes the room), one still ringing or being set up is left to
     * its setup thread. An outbound one it never got to is settled as unanswered.
     *
     * @param disconnectReason LiveKit's {@code DisconnectReason} name, e.g. {@code USER_UNAVAILABLE}
     */
    public void onPhoneLegLeft(String roomName, String identity, String disconnectReason) {
        if (LiveKitTelephonyService.OPERATOR_IDENTITY.equals(identity) && !callSessionRegistry.isOperatorJoined(roomName)) {
            // A transfer that rang out or was declined: its own dial outcome lets the caller go.
            log.debug("[{}] transfer leg left before it joined ({})", roomName, disconnectReason);
            return;
        }
        liveKitOperatorTransfers.onCallEnded(roomName);
        if (callSessionRegistry.hasPhase(roomName)) {
            log.info("[{}] LiveKit phone leg left ({}) — ending the call", roomName, disconnectReason);
            CallMdc.with(roomName, () -> callTeardownService.endStasis(roomName));
            return;
        }
        if (roomName.startsWith(LiveKitTelephonyService.OUTBOUND_ROOM_PREFIX)) {
            unansweredCallSettler.settleUnanswered(roomName, toDisposition(disconnectReason), null,
                    disconnectReason == null || disconnectReason.isBlank() ? "unknown" : disconnectReason);
        }
    }

    /**
     * What an answered manual or test call runs, as {@link CallSetupService} works it out for an
     * Asterisk one: the agent or scenario it was placed with, the company it was placed for and the
     * number it dialled, all left in {@link PendingCallRegistry} by {@code OutboundDialService}.
     */
    private SessionPlan createManualPlan(String roomName) {
        AiAgent agent = pendingCallRegistry.takeManualAgent(roomName);
        Long companyId = pendingCallRegistry.takeManualCompany(roomName);
        if (companyId == null) {
            // Every manual and test call registers its company when it is placed; without
            // one this call would run, and be billed, as the default company.
            throw new ConflictException(ErrorCode.CALL_COMPANY_UNKNOWN, roomName);
        }
        String language = agent != null ? agent.language() : dialogProperties.language();
        String ttsVoice = agent != null ? agent.voiceFor(language) : dialogProperties.ttsVoice();
        Scenario scenario = agent != null
                ? aiAgentService.resolveScenario(companyId, agent)
                : webTestCallService.resolveManualScenario(companyId, roomName);
        String manualPhone = pendingCallRegistry.takeManualPhone(roomName);
        String phone = manualPhone != null && !manualPhone.isBlank() ? manualPhone : "MANUAL";
        CrmClientSnapshot crm = PhoneNumbers.isValid(phone) ? crmUseCase.findByPhone(companyId, phone) : null;
        ClientMemory memory = PhoneNumbers.isValid(phone)
                ? clientMemoryUseCase.findByCompanyIdAndPhone(companyId, phone) : null;
        CallContext context = CallContextFactory.mergeCrmFacts(
                webTestCallService.buildTestContext(scenario.definition()), crm).withMemory(memory);
        if (context != null && PhoneNumbers.isValid(phone)) {
            context = context.withPhone(phone);
        }
        return new SessionPlan(companyId, agent, scenario, scenario.definition(), language, ttsVoice, context, phone,
                null, null, true, null);
    }

    private SessionPlan createOutboundPlan(OutboundCall outbound) {
        AiAgent agent = outbound.agent();
        Scenario scenario = aiAgentService.resolveScenario(outbound.companyId(), agent);
        CallContext context = outbound.context();
        if (context != null && PhoneNumbers.isValid(outbound.phone())) {
            context = context.withPhone(outbound.phone());
        }
        return new SessionPlan(outbound.companyId(), agent, scenario,
                // An A/B variant may replace the role prompt for this call only.
                scenario.definition().withRolePrompt(outbound.promptOverride()),
                outbound.language() != null ? outbound.language() : dialogProperties.language(),
                outbound.ttsVoice(), context, outbound.phone(), null, outbound, false, null);
    }

    /**
     * The agent hands the call to a person, where Asterisk's transfer would: a number of the
     * scenario stage first, then the agent's transfer queue; with a queue, or with no number at all,
     * to an operator in the browser ({@link LiveKitOperatorTransfers}), else to the number.
     */
    private void transfer(String roomName, long companyId, AiAgent agent) {
        OperatorSnapshot snapshot = dialogRouter.operatorSnapshot(roomName);
        boolean stageNumber = snapshot != null && snapshot.transferTo() != null && !snapshot.transferTo().isBlank();
        String agentNumber = agent != null ? agent.callBehaviour().transferPhoneNumber() : null;
        boolean hasNumber = stageNumber || agentNumber != null && !agentNumber.isBlank();
        boolean queued = !stageNumber && agent != null && agent.callBehaviour().transferQueueId() != null;
        if (queued || !hasNumber) {
            liveKitOperatorTransfers.transfer(roomName, companyId);
        } else {
            transferToNumber(roomName, companyId, agent);
        }
    }

    /**
     * The agent hands the call to a person: the transfer number of the scenario stage, else the
     * agent's {@code transferPhoneNumber}, rung into the room over the company's trunk. Once it
     * answers the media bridge leaves, so the caller and the person talk through LiveKit alone;
     * no answer within the operator timeout falls back as on Asterisk (operators busy,
     * CALLBACK_REQUESTED). Queues and softphone operators live on Asterisk and are not reached.
     */
    private void transferToNumber(String roomName, long companyId, AiAgent agent) {
        OperatorSnapshot snapshot = dialogRouter.operatorSnapshot(roomName);
        boolean stageNumber = snapshot != null && snapshot.transferTo() != null && !snapshot.transferTo().isBlank();
        String target = stageNumber
                ? snapshot.transferTo()
                : agent != null ? agent.callBehaviour().transferPhoneNumber() : null;
        // The CALL_TRANSFER stage's own SIP headers go with its own number only.
        Map<String, String> sipHeaders = stageNumber ? snapshot.transferHeaders() : Map.of();
        String number = toDialableNumber(target);
        if (number == null) {
            log.info("[{}] no transfer number a LiveKit call can dial ({}); hanging up", roomName, target);
            liveKitTelephonyService.hangUp(roomName);
            return;
        }
        // No press-1 acceptance: that needs DTMF on the transfer leg, which this engine does not read.
        operatorAcceptance.onOperatorDialled(roomName, false);
        callExecutor.execute(() -> CallMdc.with(roomName, () -> {
            try {
                LiveKitDialOutcome dial = dialTransferLeg(roomName, companyId, number, sipHeaders);
                if (!dial.answered()) {
                    log.info("[{}] transfer to {} not answered (SIP {})", roomName, PhoneMasker.mask(number), dial.sipStatus());
                    operatorAcceptance.fallBack(roomName);
                    return;
                }
                if (!operatorAcceptance.settle(roomName) || !callSessionRegistry.contains(roomName)) {
                    // The watchdog let the caller go first, or the caller hung up meanwhile.
                    log.info("[{}] transfer answered after the caller was let go — dropping it", roomName);
                    liveKitTelephonyService.removeParticipant(roomName, LiveKitTelephonyService.OPERATOR_IDENTITY);
                    return;
                }
                // A person is on the line now: the reaper's AI call limit no longer applies.
                callSessionRegistry.markOperatorJoined(roomName);
                liveKitBridgeClient.stopMedia(roomName);
                log.info("[{}] transfer to {} answered — the agent left the room", roomName, PhoneMasker.mask(number));
            } catch (Exception e) {
                log.error("[{}] transfer failed: {}", roomName, e.getMessage());
                operatorAcceptance.fallBack(roomName);
            }
        }));
    }

    /**
     * Rings {@code number} into the room over the company's default trunk and waits for the answer;
     * {@code sipHeaders} go on the leg's INVITE.
     */
    private LiveKitDialOutcome dialTransferLeg(String roomName, long companyId, String number,
                                               Map<String, String> sipHeaders) {
        SipTrunkRow trunk = sipTrunkUseCase.findDefaultForCall(companyId);
        if (trunk == null && companyId != companyProperties.defaultId()) {
            throw new ConflictException(ErrorCode.SIP_TRUNK_NONE_CONFIGURED, companyId);
        }
        SipTrunkCredentials credentials = trunk != null
                ? sipTrunkUseCase.requireCredentialsForCall(companyId, trunk.id()) : null;
        log.info("[{}] transferring to {}", roomName, PhoneMasker.mask(number));
        return liveKitTelephonyService.dialAndWaitForAnswer(roomName, LiveKitTelephonyService.OPERATOR_IDENTITY, number,
                trunk != null ? trunk.callerId() : null, credentials, sipHeaders);
    }

    /**
     * A call flow's call forwarded to a phone number: rung into the room, and once answered the media bridge
     * leaves and the caller talks to the person through LiveKit alone. Returns when the call is over
     * for them; {@link QueueOutcome#NO_ANSWER} when the number does not answer or cannot be dialled.
     */
    QueueOutcome forwardFlowCall(FlowCall call, String target) throws InterruptedException {
        String roomName = call.channelId();
        String number = toDialableNumber(target);
        if (number == null) {
            log.info("[{}] forward target {} is not a number a LiveKit call can dial", roomName, target);
            return QueueOutcome.NO_ANSWER;
        }
        LiveKitDialOutcome dial;
        try {
            dial = dialTransferLeg(roomName, call.companyId(), number, Map.of());
        } catch (RuntimeException e) {
            log.error("[{}] forward failed: {}", roomName, e.getMessage());
            return QueueOutcome.NO_ANSWER;
        }
        if (!dial.answered()) {
            log.info("[{}] forward to {} not answered (SIP {})", roomName, PhoneMasker.mask(number), dial.sipStatus());
            return QueueOutcome.NO_ANSWER;
        }
        if (call.isGone() || !callSessionRegistry.contains(roomName)) {
            liveKitTelephonyService.removeParticipant(roomName, LiveKitTelephonyService.OPERATOR_IDENTITY);
            return QueueOutcome.ABANDONED;
        }
        callSessionRegistry.markOperatorJoined(roomName);
        liveKitBridgeClient.stopMedia(roomName);
        log.info("[{}] forward to {} answered — the agent left the room", roomName, PhoneMasker.mask(number));
        while (!call.isGone()) {
            Thread.sleep(200);
        }
        return QueueOutcome.ANSWERED;
    }

    /**
     * A transfer target as a number LiveKit can put in a SIP INVITE: the Asterisk dial-string
     * prefixes ({@code PJSIP/}, {@code SIP/}) taken off; anything else that is not a plain
     * number (a named endpoint, a Local channel) has no meaning outside Asterisk.
     */
    private static String toDialableNumber(String target) {
        if (target == null || target.isBlank()) {
            return null;
        }
        String number = target.trim();
        if (number.startsWith("PJSIP/")) {
            number = number.substring("PJSIP/".length());
        } else if (number.startsWith("SIP/")) {
            number = number.substring("SIP/".length());
        }
        return number.matches("\\+?\\d{3,20}") ? number : null;
    }

    /**
     * Everything {@link #startSession} needs to know about the call, outbound or inbound.
     *
     * @param manual a manual or test call, whose dialog starts only when {@code dialog.auto-start} says so
     */
    private record SessionPlan(long companyId, AiAgent agent, Scenario scenario, ScenarioDefinition definition,
                               String language, String ttsVoice, CallContext context, String phone,
                               Long inboundRouteId, OutboundCall outbound, boolean manual, FlowEntry flow) {
    }

    /**
     * An inbound call that runs a call flow instead of an AI agent's dialog: the verdict that chose the flow and
     * what the caller is already known by.
     */
    private record FlowEntry(InboundDecision decision, String callerNumber, String did, boolean languageKnown,
                             String clientName) {
    }

    /** {@link CallSetupService}'s media-and-dialog half, with the bridge in place of Asterisk's legs. */
    private void startSession(String roomName, SessionPlan plan) {
        OutboundCall outbound = plan.outbound();
        AiAgent agent = plan.agent();
        int port = -1;
        RtpEndpoint endpoint = null;
        long attemptId = 0;
        // From registration on, the resources are the teardown's to release, never the catch's.
        boolean registered = false;
        try {
            port = portAllocator.allocate();
            WavRecorder recorder = null;
            String wavPath = null;
            if (agent == null || agent.dataPolicy().keepsCallAudio()) {
                Files.createDirectories(Path.of(rtpProperties.recordingDir()));
                Path wav = Path.of(rtpProperties.recordingDir(), roomName + ".wav");
                recorder = new WavRecorder(wav, CallMediaFactory.SAMPLE_RATE, rtpProperties.recordingMode());
                wavPath = wav.toString();
            }

            LiveAudioMonitor audioMonitor = new LiveAudioMonitor(rtpEventLoopGroup);
            endpoint = new RtpEndpoint(roomName, port, rtpProperties.codec(), recorder,
                    List.<AudioListener>of(audioMonitor), audioMonitor::onBotAudio, rtpProperties.receiveQueueCapacity(),
                    rtpProperties.maxQueuedPlaybackSeconds());
            endpoint.bind(rtpEventLoopGroup);
            endpoint.setRemote(liveKitBridgeClient.startMedia(roomName, rtpProperties.localIp(), port, rtpProperties.codec()));

            Instant startedAt = Instant.now();
            attemptId = callAttemptUseCase.findAttemptIdByChannel(roomName);
            if (attemptId == 0) {
                attemptId = callAttemptUseCase.startAttempt(plan.companyId(),
                        outbound != null ? outbound.targetId() : CallAttempt.NO_TARGET, roomName, plan.phone(),
                        plan.language(), plan.inboundRouteId(), outbound != null ? outbound.variantId() : null);
            }
            callAttemptUseCase.markAnswered(attemptId);
            if (outbound != null && outbound.variantId() != null) {
                campaignVariantUseCase.recordAnswer(outbound.variantId());
            }

            boolean flowCall = plan.flow() != null;
            PipelineMode pipelineMode = flowCall ? null : dialogStarter.resolveMode(roomName, agent,
                    outbound != null ? outbound.pipelineModeOverride() : null);
            boolean realtime = pipelineMode != null && pipelineMode.usesRealtimeEngine();
            RealtimeAudioBridge realtimeBridge = realtime ? new RealtimeAudioBridge(pipelineMode == PipelineMode.REALTIME) : null;
            CallContext context = plan.context();

            if (!flowCall) {
                List<AudioListener> audioListeners = audioPipelineAssembler.buildAudioListeners(roomName, plan.companyId(),
                        attemptId, startedAt, plan.language(), realtimeBridge, context, agent, endpoint::recentOutboundLevel);
                audioListeners.add(audioMonitor);
                endpoint.replaceListeners(audioListeners);
            }

            boolean active = callSessionRegistry.register(new CallSession(roomName, plan.companyId(), null, null,
                    port, endpoint, attemptId, startedAt, wavPath, CHANNEL_NAME_PREFIX + roomName, TRUNK,
                    plan.scenario() != null ? plan.scenario().id() : 0, audioMonitor,
                    agent != null && agent.callBehaviour().dtmfInputEnabled(), agent));
            registered = true;
            if (outbound != null) {
                outboundCallTrackingUseCase.markAnswered(roomName);
            }
            log.info("LiveKit media ready for {}: rtpPort={}, wav={}, attempt={}", roomName, port, wavPath, attemptId);

            metrics.callStarted();
            metrics.activeInc();
            routeRegistry.register(roomName, plan.companyId());

            if (!active) {
                log.info("[{}] phone leg left during setup — tearing down before the dialog starts", roomName);
                callTeardownService.tearDownIfClaimed(roomName, dialogRouter.outcome(roomName),
                        null, dialogRouter.technicalSnapshot(roomName), false);
                return;
            }

            if (flowCall) {
                runFlow(roomName, plan, endpoint, audioMonitor, attemptId);
                return;
            }

            if (agent != null && !agent.preCallFunctions().isEmpty() && context != null) {
                // Before the initiation webhook, so the webhook sees what the lookups found.
                context = context.withMergedFacts(preCallWorkflowRunner.run(agent, plan.companyId(), roomName,
                        attemptId, plan.language(), plan.phone(), context.facts()));
            }

            if (agent != null && agent.initiationWebhook() != null && agentWebhookUseCase != null) {
                try {
                    var initResult = agentWebhookUseCase.executeInitiation(plan.companyId(), attemptId, roomName,
                            plan.phone(), agent, context != null ? context.facts() : Map.of());
                    if (initResult != null) {
                        if (initResult.variables() != null && !initResult.variables().isEmpty() && context != null) {
                            context = context.withMergedFacts(initResult.variables());
                        }
                        if (initResult.firstMessageOverride() != null && !initResult.firstMessageOverride().isBlank()) {
                            agent = agent.withSpokenOpening(initResult.firstMessageOverride());
                        }
                    }
                } catch (Exception e) {
                    log.warn("[{}] initiation webhook execution failed: {}", roomName, e.getMessage());
                }
            }

            if (agent != null) {
                endpoint.setAmbientSound(agent.ambience().background(),
                        agent.ambience().backgroundVolume(),
                        agent.ambience().backgroundFadeInSeconds());
                endpoint.setThinkingSound(agent.ambience().thinking(), agent.ambience().thinkingVolume());
                endpoint.setNoiseCancellation(agent.ambience().noiseCancellationEnabled(), agent.ambience().noiseCancellationMode());
            }
            audioPipelineAssembler.attachDenoiser(endpoint);
            if (!dialogProperties.enabled() || plan.manual() && !dialogProperties.autoStart()) {
                return;
            }
            preConversationAudio.play(roomName, plan.companyId(), agent, endpoint);
            if (!callSessionRegistry.isActive(roomName)) {
                log.info("[{}] phone leg left during setup — the dialog is not started", roomName);
                return;
            }
            AiAgent callAgent = agent;
            // Keypad tones only on calls we placed, as on Asterisk (CallSetupService); a browser has no keypad leg.
            Consumer<String> dtmfSender = outbound != null && !roomName.startsWith(LiveKitTelephonyService.BROWSER_ROOM_PREFIX)
                    ? digits -> telephonyGatewayResolver.findByCallId(roomName).sendDtmf(roomName, digits) : null;
            CallStart start = new CallStart(roomName, endpoint, context, plan.definition(), plan.language(),
                    plan.ttsVoice(), agent, () -> liveKitTelephonyService.hangUp(roomName),
                    () -> transfer(roomName, plan.companyId(), callAgent),
                    attemptId, dtmfSender);
            if (realtime) {
                boolean started = dialogStarter.startRealtime(start, pipelineMode, realtimeBridge,
                        reconnecting -> continueOnCascade(reconnecting, startedAt, audioMonitor));
                if (!started) {
                    log.warn("[{}] realtime dialog did not start — continuing on the cascade pipeline", roomName);
                    continueOnCascade(start.withDefaultVoice(), startedAt, audioMonitor);
                }
            } else {
                dialogEngine.startCall(start);
            }
            if (!callSessionRegistry.isActive(roomName)) {
                log.info("[{}] phone leg left while the dialog was starting — ending it", roomName);
                dialogRouter.endCall(roomName);
            }
        } catch (Exception e) {
            log.error("Failed to set up LiveKit media for {}: {}", roomName, e.getMessage(), e);
            callAttemptUseCase.recordError(attemptId, e.toString());
            if (!registered) {
                if (endpoint != null) {
                    endpoint.close();
                }
                if (port >= 0) {
                    portAllocator.release(port);
                }
                OutboundCall failedOutbound = outboundCallTrackingUseCase.remove(roomName);
                if (failedOutbound != null) {
                    unansweredCallSettler.settleFailedOutbound(roomName, failedOutbound);
                }
            }
            // Closing the room takes the bridge and the phone leg with it; a registered call is
            // then torn down by the participant_left that follows.
            liveKitTelephonyService.hangUp(roomName);
        }
    }

    /** CallSetupService's continueOnCascade, hanging up through the room. */
    private void continueOnCascade(CallStart start, Instant startedAt, LiveAudioMonitor audioMonitor) {
        String roomName = start.channelId();
        CallSession session = callSessionRegistry.find(roomName);
        if (session == null) {
            return;
        }
        if (!dialogEngine.available()) {
            log.error("[{}] realtime engine lost and the cascade pipeline is unavailable — hanging up", roomName);
            liveKitTelephonyService.hangUp(roomName);
            return;
        }
        List<AudioListener> listeners;
        try {
            listeners = audioPipelineAssembler.buildAudioListeners(roomName, session.companyId(), start.callAttemptId(),
                    startedAt, start.language(), null, start.context(), start.agent(), start.endpoint()::recentOutboundLevel);
        } catch (Exception e) {
            log.error("[{}] realtime engine lost and the cascade could not start: {} — hanging up",
                    roomName, e.getMessage());
            liveKitTelephonyService.hangUp(roomName);
            return;
        }
        listeners.add(audioMonitor);
        start.endpoint().replaceListeners(listeners);
        log.warn("[{}] continuing the call on the cascade pipeline", roomName);
        dialogEngine.startCall(start);
    }

    /** The final SIP response of a call that was not answered, as Q.850 causes map in {@link HangupCause}. */
    private static Disposition toDisposition(int sipStatus) {
        return switch (sipStatus) {
            case 404, 410, 484, 604 -> Disposition.WRONG_NUMBER;
            // busy, no answer within the ring timeout, temporarily unavailable, cancelled at the timeout
            case 486, 600, 408, 480, 487 -> Disposition.NO_ANSWER;
            case 603 -> Disposition.REFUSED;
            case 403 -> Disposition.CARRIER_REJECTED;
            default -> Disposition.FAILED;
        };
    }

    private static Disposition toDisposition(String disconnectReason) {
        if (disconnectReason == null) {
            return Disposition.HUNG_UP;
        }
        return switch (disconnectReason) {
            case "USER_UNAVAILABLE" -> Disposition.NO_ANSWER;
            case "USER_REJECTED" -> Disposition.REFUSED;
            case "SIP_TRUNK_FAILURE" -> Disposition.CARRIER_REJECTED;
            case "JOIN_FAILURE", "MEDIA_FAILURE", "CONNECTION_TIMEOUT", "SERVER_SHUTDOWN" -> Disposition.FAILED;
            // CLIENT_INITIATED: the callee hung up
            default -> Disposition.HUNG_UP;
        };
    }
}
