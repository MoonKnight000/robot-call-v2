package uz.murodjon.robotcallv2.agent.ari;

import ch.loway.oss.ari4java.ARI;
import ch.loway.oss.ari4java.generated.models.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.audio.AudioListener;
import uz.murodjon.robotcallv2.agent.dialog.*;
import uz.murodjon.robotcallv2.agent.flow.CallFlowRunner;
import uz.murodjon.robotcallv2.agent.flow.FlowCall;
import uz.murodjon.robotcallv2.agent.flow.InboundDecision;
import uz.murodjon.robotcallv2.agent.metrics.CallMetrics;
import uz.murodjon.robotcallv2.agent.realtime.RealtimeAudioBridge;
import uz.murodjon.robotcallv2.agent.routing.CallRouteRegistry;
import uz.murodjon.robotcallv2.agent.rtp.RtpEndpoint;
import uz.murodjon.robotcallv2.agent.rtp.RtpPortAllocator;
import uz.murodjon.robotcallv2.agent.session.CallMdc;
import uz.murodjon.robotcallv2.agent.session.CallSession;
import uz.murodjon.robotcallv2.agent.tts.TtsProperties;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallAttempt;
import uz.murodjon.robotcallv2.crm.application.port.input.CrmUseCase;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmClientSnapshot;
import uz.murodjon.robotcallv2.dialer.application.dto.OutboundCall;
import uz.murodjon.robotcallv2.dialer.domain.service.CallContextFactory;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundRoute;
import uz.murodjon.robotcallv2.memory.application.port.input.ClientMemoryUseCase;
import uz.murodjon.robotcallv2.memory.domain.entity.ClientMemory;
import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.dialog.CallLanguage;
import uz.murodjon.robotcallv2.shared.dialog.PipelineMode;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

/**
 * Calls the PBX side walks (docs/INBOUND-PBX-PLAN.md §4): an inbound call answered into a
 * call flow, an AI agent started on and stopped off such a call, and a live call handed to
 * an operator queue.
 */
@Service
class FlowCallService {

    private static final Logger log = LoggerFactory.getLogger(FlowCallService.class);

    private final CallSessionRegistry callSessionRegistry;
    private final FlowCallRegistry flowCallRegistry;
    private final PendingCallRegistry pendingCallRegistry;
    private final CallMediaFactory callMediaFactory;
    private final AudioPipelineAssembler audioPipelineAssembler;
    private final AriClient ariClient;
    private final DialogEngine dialogEngine;
    private final RealtimeDialogEngine realtimeDialogEngine;
    private final DialogStarter dialogStarter;
    private final DialogRouter dialogRouter;
    private final DialogProperties dialogProperties;
    private final TtsProperties ttsProperties;
    private final AiAgentUseCase aiAgentUseCase;
    private final CrmUseCase crmUseCase;
    private final ClientMemoryUseCase clientMemoryUseCase;
    private final CallAttemptUseCase callAttemptUseCase;
    private final CallFlowRunner callFlowRunner;
    private final RtpPortAllocator portAllocator;
    private final CallMetrics metrics;
    private final CallRouteRegistry routeRegistry;
    private final CallTeardownService callTeardownService;
    private final ExecutorService callExecutor;

    FlowCallService(CallSessionRegistry callSessionRegistry,
                    FlowCallRegistry flowCallRegistry,
                    PendingCallRegistry pendingCallRegistry,
                    CallMediaFactory callMediaFactory,
                    AudioPipelineAssembler audioPipelineAssembler,
                    AriClient ariClient,
                    DialogEngine dialogEngine,
                    RealtimeDialogEngine realtimeDialogEngine,
                    DialogStarter dialogStarter,
                    DialogRouter dialogRouter,
                    DialogProperties dialogProperties,
                    TtsProperties ttsProperties,
                    AiAgentUseCase aiAgentUseCase,
                    CrmUseCase crmUseCase,
                    ClientMemoryUseCase clientMemoryUseCase,
                    CallAttemptUseCase callAttemptUseCase,
                    CallFlowRunner callFlowRunner,
                    RtpPortAllocator portAllocator,
                    CallMetrics metrics,
                    CallRouteRegistry routeRegistry,
                    CallTeardownService callTeardownService,
                    ExecutorService callExecutor) {
        this.callSessionRegistry = callSessionRegistry;
        this.flowCallRegistry = flowCallRegistry;
        this.pendingCallRegistry = pendingCallRegistry;
        this.callMediaFactory = callMediaFactory;
        this.audioPipelineAssembler = audioPipelineAssembler;
        this.ariClient = ariClient;
        this.dialogEngine = dialogEngine;
        this.realtimeDialogEngine = realtimeDialogEngine;
        this.dialogStarter = dialogStarter;
        this.dialogRouter = dialogRouter;
        this.dialogProperties = dialogProperties;
        this.ttsProperties = ttsProperties;
        this.aiAgentUseCase = aiAgentUseCase;
        this.crmUseCase = crmUseCase;
        this.clientMemoryUseCase = clientMemoryUseCase;
        this.callAttemptUseCase = callAttemptUseCase;
        this.callFlowRunner = callFlowRunner;
        this.portAllocator = portAllocator;
        this.metrics = metrics;
        this.routeRegistry = routeRegistry;
        this.callTeardownService = callTeardownService;
        this.callExecutor = callExecutor;
    }

    /**
     * Answers a call whose route runs a call flow, and walks it through the flow on this
     * thread (docs/INBOUND-PBX-PLAN.md §4). The media is set up as for an agent call — a
     * recording leg in a mixing bridge — so a flow that reaches an AI agent or an operator
     * needs nothing more, and the call is torn down and finalized like any other.
     */
    void startInboundFlow(String channelId, String channelName, Channel channel, InboundDecision decision,
                          String did, String callerNumber) {
        InboundRoute route = decision.route();
        long companyId = route.companyId();
        ARI current = ariClient.requireConnection();
        int port = -1;
        MediaLegs legs = null;
        long attemptId = 0;
        FlowCall call = null;
        // From registration on, the resources are the teardown's to release, never the catch's.
        boolean registered = false;
        try {
            // Inside the try: with no port left the caller is still hung up rather than left ringing.
            port = portAllocator.allocate();
            // Who is calling, asked before answering: it decides the language the flow starts
            // in, and whether a language menu is needed at all.
            CrmClientSnapshot crm = callerNumber != null ? crmUseCase.findByPhone(companyId, callerNumber) : null;
            ClientMemory memory = callerNumber != null
                    ? clientMemoryUseCase.findByCompanyIdAndPhone(companyId, callerNumber) : null;
            String knownLanguage = crm != null && crm.preferredLanguage() != null ? crm.preferredLanguage()
                    : memory != null ? memory.preferredLanguage() : null;
            String language = knownLanguage != null ? knownLanguage : decision.language();

            legs = callMediaFactory.openMediaLegs(current, channelId, port);
            ariClient.answer(channelId);
            ariClient.enableJitterBuffer(current, channelId);
            current.bridges().addChannel(legs.bridgeId(), channelId).execute();

            attemptId = callAttemptUseCase.startAttempt(companyId, CallAttempt.NO_TARGET, channelId,
                    callerNumber != null ? callerNumber : "INBOUND", language, route.id(), null);
            callAttemptUseCase.markAnswered(attemptId);

            // The flow call before the session: a StasisEnd from here on finds the flow call and
            // marks it gone, and one that came earlier has left the call for the register below.
            call = new FlowCall(channelId, legs.bridgeId(), legs.endpoint(), companyId, attemptId, callerNumber, did,
                    route.id(), language, knownLanguage != null, List.of(legs.audioMonitor()));
            call.setVip(decision.vip());
            call.setClientName(crm != null && crm.name() != null ? crm.name()
                    : memory != null ? memory.preferredName() : null);
            call.restoreListeners();
            flowCallRegistry.register(call);
            if (decision.vip()) {
                flowCallRegistry.markVip(channelId);
            }
            boolean active = callSessionRegistry.register(new CallSession(channelId, companyId, legs.extMediaChannelId(), legs.bridgeId(),
                    port, legs.endpoint(), attemptId, Instant.now(), legs.wav().toString(), channelName,
                    ChannelNames.extractTrunk(channelName), 0, legs.audioMonitor(), false, null));
            registered = true;
            metrics.callStarted();
            metrics.activeInc();
            routeRegistry.register(channelId, companyId);
            if (!active) {
                // The caller hung up while the call was being answered: no flow runs for them.
                log.info("[{}] caller left before the call flow started — tearing down", channelId);
                flowCallRegistry.remove(channelId);
                flowCallRegistry.forgetVip(channelId);
                call.markGone();
                callTeardownService.tearDownIfClaimed(channelId, dialogRouter.outcome(channelId),
                        callSessionRegistry.findHangupCause(channelId), dialogRouter.technicalSnapshot(channelId), false);
                return;
            }
            log.info("Inbound call {} to {} runs call flow {} (attempt {})", channelId, did, route.callFlowId(), attemptId);
            callFlowRunner.run(call, decision.graph(), route.callFlowId());
        } catch (Exception e) {
            log.error("Call flow for {} could not start: {}", channelId, e.getMessage(), e);
            callAttemptUseCase.recordError(attemptId, e.toString());
            if (!registered) {
                if (legs != null) {
                    callMediaFactory.releaseMediaLegs(current, legs);
                }
                portAllocator.release(port);
            }
        } finally {
            if (call == null || !call.isGone()) {
                ariClient.hangup(channelId);
            }
        }
    }

    /** The call the PBX side has on {@code channelId}, or null. */
    FlowCall findFlowCall(String channelId) {
        return flowCallRegistry.find(channelId);
    }

    /** What an operator reads before picking up: the AI conversation so far, if an agent talked first. */
    String findHandoverTranscript(String channelId) {
        OperatorSnapshot snapshot = flowCallRegistry.findSnapshot(channelId);
        if (snapshot == null && (dialogEngine.owns(channelId) || realtimeDialogEngine.owns(channelId))) {
            snapshot = dialogRouter.operatorSnapshot(channelId);
        }
        return snapshot != null ? snapshot.transcript() : null;
    }

    /**
     * Starts AI agent {@code agentId} on a call a flow already answered. {@code onCompleted}
     * runs when the agent would hang up, {@code onTransfer} when it asks for a person — the
     * flow decides what either means. False when the agent cannot start.
     */
    boolean startAgentOnFlowCall(FlowCall call, long agentId, Runnable onCompleted, Runnable onTransfer) {
        String channelId = call.channelId();
        CallSession session = callSessionRegistry.find(channelId);
        if (session == null || !dialogProperties.enabled()) {
            return false;
        }
        try {
            long companyId = call.companyId();
            String callerNumber = call.callerNumber();
            AiAgent agent = aiAgentUseCase.requireCallAgent(companyId, agentId, callerNumber);
            Scenario scenario = aiAgentUseCase.resolveScenario(companyId, agent);
            CrmClientSnapshot crm = callerNumber != null ? crmUseCase.findByPhone(companyId, callerNumber) : null;
            ClientMemory memory = callerNumber != null
                    ? clientMemoryUseCase.findByCompanyIdAndPhone(companyId, callerNumber) : null;
            String language = call.languageKnown() ? call.language()
                    : CallLanguage.resolve(crm != null ? crm.preferredLanguage() : null,
                    memory != null ? memory.preferredLanguage() : null, agent.language());
            String ttsVoice = agent.voiceFor(language);
            CallContext context = CallContextFactory.mergeCrmFacts(new CallContext(Map.of(), null), crm).withMemory(memory)
                    .withPhone(callerNumber);
            ScenarioDefinition definition = scenario.definition();
            PipelineMode mode = dialogStarter.resolveMode(channelId, agent, null);
            RtpEndpoint endpoint = session.endpoint();
            long attemptId = call.callAttemptId();
            Instant startedAt = call.startedAt();
            callSessionRegistry.update(session.withAgent(agent, scenario.id(), agent.callBehaviour().dtmfInputEnabled()));
            endpoint.setNoiseCancellation(agent.ambience().noiseCancellationEnabled(),
                    agent.ambience().noiseCancellationMode());
            flowCallRegistry.registerTransferCallback(channelId, onTransfer);
            Runnable transfer = () -> {
                Runnable handler = flowCallRegistry.takeTransferCallback(channelId);
                if (handler != null) {
                    handler.run();
                }
            };
            call.setAgentActive(true);
            CallStart start = new CallStart(channelId, endpoint, context, definition, language, ttsVoice, agent,
                    onCompleted, transfer, attemptId, null);
            if (mode.usesRealtimeEngine()) {
                RealtimeAudioBridge realtimeBridge = new RealtimeAudioBridge();
                useAgentListeners(call, audioPipelineAssembler.buildAudioListeners(channelId, companyId, attemptId, startedAt, language,
                        realtimeBridge, context, agent, endpoint != null ? endpoint::recentOutboundLevel : null));
                boolean started = dialogStarter.startRealtime(start, mode, realtimeBridge, reconnecting -> {
                    try {
                        useAgentListeners(call, audioPipelineAssembler.buildAudioListeners(channelId, companyId, attemptId, startedAt, language,
                                null, context, agent, endpoint != null ? endpoint::recentOutboundLevel : null));
                    } catch (Exception e) {
                        // Runs later, on the realtime engine's thread: without speech
                        // recognition the cascade cannot take over, so the flow moves on.
                        log.warn("[{}] realtime agent lost and the cascade could not start: {}", channelId, e.getMessage());
                        onCompleted.run();
                        return;
                    }
                    if (dialogEngine.available()) {
                        dialogEngine.startCall(reconnecting);
                    } else {
                        onCompleted.run();
                    }
                });
                if (started) {
                    return true;
                }
                log.warn("[{}] realtime agent {} did not start — trying the cascade pipeline", channelId, agentId);
            }
            if (!dialogEngine.available()) {
                call.setAgentActive(false);
                call.restoreListeners();
                return false;
            }
            useAgentListeners(call, audioPipelineAssembler.buildAudioListeners(channelId, companyId, attemptId, startedAt, language, null,
                    context, agent, endpoint != null ? endpoint::recentOutboundLevel : null));
            dialogEngine.startCall(start);
            return true;
        } catch (Exception e) {
            log.warn("[{}] AI agent {} could not take the call: {}", channelId, agentId, e.getMessage());
            call.setAgentActive(false);
            call.restoreListeners();
            return false;
        }
    }

    /**
     * Stops the AI agent on a flow call, keeping what it decided (outcome, technical detail,
     * its view of the call for the operator) for teardown and the handover.
     */
    void stopAgentOnFlowCall(FlowCall call) {
        String channelId = call.channelId();
        flowCallRegistry.takeTransferCallback(channelId);
        if (dialogEngine.owns(channelId) || realtimeDialogEngine.owns(channelId)) {
            OperatorSnapshot snapshot = dialogRouter.operatorSnapshot(channelId);
            if (snapshot != null) {
                flowCallRegistry.keepSnapshot(channelId, snapshot);
            }
            DialogOutcome outcome = dialogRouter.outcome(channelId);
            if (outcome != null && outcome.disposition() != null) {
                flowCallRegistry.keepOutcome(channelId, outcome);
            }
            DialogTechnicalSnapshot technical = dialogRouter.technicalSnapshot(channelId);
            if (technical != null) {
                flowCallRegistry.keepTechnical(channelId, technical);
            }
            dialogRouter.endCall(channelId);
        }
        call.setAgentActive(false);
        call.endpoint().flushPlayback();
        call.restoreListeners();
    }

    /**
     * Sends a live call to operator queue {@code queueId}: an AI agent handing over, or a
     * supervisor moving a call. A call already walking a flow is left to its flow — the
     * agent's transfer exit decides where it goes.
     */
    void transferCallToQueue(long companyId, String channelId, long queueId) {
        CallSession session = callSessionRegistry.find(channelId);
        if (session == null || session.companyId() != companyId) {
            throw new ConflictException(ErrorCode.CALL_NOT_ACTIVE, channelId);
        }
        if (flowCallRegistry.contains(channelId)) {
            Runnable transfer = flowCallRegistry.takeTransferCallback(channelId);
            if (transfer != null) {
                transfer.run();
            }
            return;
        }
        FlowCall call = adoptCall(session, companyId);
        flowCallRegistry.register(call);
        log.info("[{}] handed to operator queue {}", channelId, queueId);
        callExecutor.execute(() -> CallMdc.with(channelId, () -> {
            try {
                // Let the agent finish saying it is putting the caller through.
                callMediaFactory.awaitSilence(call.endpoint(), 8000);
                stopAgentOnFlowCall(call);
                callFlowRunner.runQueue(call, queueId);
            } finally {
                if (!call.isGone()) {
                    ariClient.hangup(channelId);
                }
            }
        }));
    }

    /** A call that was not in a flow (an AI call, inbound or outbound) as the PBX side sees it. */
    private FlowCall adoptCall(CallSession session, long companyId) {
        String channelId = session.channelId();
        OutboundCall outbound = pendingCallRegistry.findOutboundProfile(channelId);
        String phone = outbound != null ? outbound.phone() : callAttemptUseCase.findPhoneByAttemptId(session.callAttemptId());
        String language = dialogRouter.liveDialogs().stream()
                .filter(dialog -> channelId.equals(dialog.channelId()))
                .map(LiveDialogSnapshot::language)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(session.agent() != null ? session.agent().language() : ttsProperties.defaultLanguage());
        FlowCall call = new FlowCall(channelId, session.bridgeId(), session.endpoint(), companyId,
                session.callAttemptId(), phone, null, callAttemptUseCase.findInboundRouteIdByAttemptId(session.callAttemptId()),
                language, true, List.of(session.audioMonitor()));
        call.setVip(flowCallRegistry.isVip(channelId));
        OperatorSnapshot snapshot = dialogRouter.operatorSnapshot(channelId);
        if (snapshot != null) {
            call.setClientName(snapshot.clientName());
        }
        return call;
    }

    private static void useAgentListeners(FlowCall call, List<AudioListener> agentListeners) {
        List<AudioListener> all = new ArrayList<>(agentListeners);
        all.addAll(call.baseListeners());
        call.endpoint().replaceListeners(all);
    }
}
