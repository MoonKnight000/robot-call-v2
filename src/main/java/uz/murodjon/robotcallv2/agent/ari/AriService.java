package uz.murodjon.robotcallv2.agent.ari;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.dialog.DialogRouter;
import uz.murodjon.robotcallv2.agent.flow.FlowCall;
import uz.murodjon.robotcallv2.agent.lifecycle.GracefulShutdownManager;
import uz.murodjon.robotcallv2.agent.lifecycle.ShutdownProperties;
import uz.murodjon.robotcallv2.agent.lifecycle.ShutdownTasks;
import uz.murodjon.robotcallv2.agent.session.CallMdc;
import uz.murodjon.robotcallv2.agent.session.CallSession;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.callrecord.application.dto.CallOriginateResponse;
import uz.murodjon.robotcallv2.callrecord.application.dto.LiveCallRow;
import uz.murodjon.robotcallv2.callrecord.application.dto.PlayResponse;
import uz.murodjon.robotcallv2.callrecord.application.dto.SayResponse;
import uz.murodjon.robotcallv2.dialer.application.dto.OutboundCall;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.api.AudioStreamWriter;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The call-control API of the voice pipeline (PROJECT.md §8) — what the rest of the
 * application may ask of Asterisk and of the calls on this instance — and the shutdown of
 * those calls.
 *
 * <p>A facade: every method hands over to the component of {@code agent/ari} that owns
 * it. The ARI connection and its events live in {@link AriEventDispatcher}, a call's start
 * in {@link CallSetupService}, its end in {@link CallTeardownService}.</p>
 */
@Service
public class AriService implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(AriService.class);

    /**
     * Calls are shut down in a lifecycle phase of their own instead of a {@code @PreDestroy}:
     * every lifecycle bean stops before any bean is destroyed, so the calls still up are torn
     * down (summary, CRM note, recording, route) while the RTP event loop, the STT/TTS
     * providers, the dialog executors and the database are all still there. Below the web
     * server's two phases, so HTTP has stopped and no manual or test call can start during
     * the drain; above Lettuce's phase 0, so Redis is still connected for the teardown.
     */
    private static final int SHUTDOWN_PHASE = SmartLifecycle.DEFAULT_PHASE - 4096;
    /** Between {@link #start} and {@link #stop} — the SmartLifecycle contract. */
    private volatile boolean lifecycleRunning;

    private final OutboundDialService outboundDialService;
    private final WebTestCallService webTestCallService;
    private final ActiveCallService activeCallService;
    private final OperatorTransferService operatorTransferService;
    private final FlowCallService flowCallService;
    private final PbxLegService pbxLegService;
    private final CallMediaFactory callMediaFactory;
    private final AriClient ariClient;
    private final CallSessionRegistry callSessionRegistry;
    private final CallTeardownService callTeardownService;
    private final AriEventLanes ariEventLanes;
    private final DialogRouter dialogRouter;
    private final GracefulShutdownManager shutdownManager;
    private final ShutdownProperties shutdownProperties;

    public AriService(OutboundDialService outboundDialService,
                      WebTestCallService webTestCallService,
                      ActiveCallService activeCallService,
                      OperatorTransferService operatorTransferService,
                      FlowCallService flowCallService,
                      PbxLegService pbxLegService,
                      CallMediaFactory callMediaFactory,
                      AriClient ariClient,
                      CallSessionRegistry callSessionRegistry,
                      CallTeardownService callTeardownService,
                      AriEventLanes ariEventLanes,
                      DialogRouter dialogRouter,
                      GracefulShutdownManager shutdownManager,
                      ShutdownProperties shutdownProperties) {
        this.outboundDialService = outboundDialService;
        this.webTestCallService = webTestCallService;
        this.activeCallService = activeCallService;
        this.operatorTransferService = operatorTransferService;
        this.flowCallService = flowCallService;
        this.pbxLegService = pbxLegService;
        this.callMediaFactory = callMediaFactory;
        this.ariClient = ariClient;
        this.callSessionRegistry = callSessionRegistry;
        this.callTeardownService = callTeardownService;
        this.ariEventLanes = ariEventLanes;
        this.dialogRouter = dialogRouter;
        this.shutdownManager = shutdownManager;
        this.shutdownProperties = shutdownProperties;
    }

    // ---------------------------------------------------------------------------------
    // Dialling out — OutboundDialService, WebTestCallService.
    // ---------------------------------------------------------------------------------

    public String originate(String rawNumber, long companyId) {
        return outboundDialService.originate(rawNumber, companyId);
    }

    public String originate(String rawNumber, long companyId, OutboundCall outboundCall) {
        return outboundDialService.originate(rawNumber, companyId, outboundCall);
    }

    public String originate(String rawNumber, long companyId, Long sipTrunkId) {
        return outboundDialService.originate(rawNumber, companyId, sipTrunkId);
    }

    public CallOriginateResponse originateManualCall(long companyId, String number, Long scenarioId) {
        return outboundDialService.originateManualCall(companyId, number, scenarioId);
    }

    public CallOriginateResponse originateManualCall(long companyId, String number, Long scenarioId, Long agentId,
                                                     Long sipTrunkId) {
        return outboundDialService.originateManualCall(companyId, number, scenarioId, agentId, sipTrunkId);
    }

    public CallOriginateResponse originateTestCall(long companyId, String number, ScenarioDefinition definition) {
        return outboundDialService.originateTestCall(companyId, number, definition);
    }

    public CallOriginateResponse originateTestCall(long companyId, String number, ScenarioDefinition definition,
                                                   Long sipTrunkId) {
        return outboundDialService.originateTestCall(companyId, number, definition, sipTrunkId);
    }

    /** See {@link WebTestCallService#prepareWebTest}. */
    public String prepareWebTest(long companyId, Long campaignId, Long targetId, Integer agentVersion,
                                 Long scenarioId, ScenarioDefinition definition) {
        return webTestCallService.prepareWebTest(companyId, campaignId, targetId, agentVersion, scenarioId, definition);
    }

    /** See {@link WebTestCallService#prepareWidgetCall}. */
    public String prepareWidgetCall(long companyId, AiAgent agent, String language) {
        return webTestCallService.prepareWidgetCall(companyId, agent, language);
    }

    /** See {@link OutboundDialService#requirePlatformTrunkAllowed}. */
    public void requirePlatformTrunkAllowed(long companyId) {
        outboundDialService.requirePlatformTrunkAllowed(companyId);
    }

    /** Originates {@code dial} into this application with {@code appArgs}; returns the new channel. */
    public String originateToStasis(String dial, String appArgs, int timeoutSec, String callerId) {
        return ariClient.originateToStasis(dial, appArgs, timeoutSec, callerId);
    }

    /** The same for an outside number, over the company's trunk and with its caller id. */
    public String originateExternalToStasis(long companyId, String rawNumber, String appArgs, int timeoutSec) {
        return outboundDialService.originateExternalToStasis(companyId, rawNumber, appArgs, timeoutSec);
    }

    /** An outside number dialled for an operator (a callback, a softphone call) — booked as a call from the start. */
    public String originateClientCall(long companyId, String rawNumber, String appArgs, int timeoutSec, String language) {
        return outboundDialService.originateClientCall(companyId, rawNumber, appArgs, timeoutSec, language);
    }

    // ---------------------------------------------------------------------------------
    // A call in progress — ActiveCallService, OperatorTransferService.
    // ---------------------------------------------------------------------------------

    public void play(String channelId, Path file) {
        activeCallService.play(channelId, file);
    }

    public PlayResponse playRecording(String channelId, String file) {
        return activeCallService.playRecording(channelId, file);
    }

    public void speak(String channelId, String text, String language) {
        activeCallService.speak(channelId, text, language);
    }

    public void speak(String channelId, String text, String language, String ttsVoice) {
        activeCallService.speak(channelId, text, language, ttsVoice);
    }

    public SayResponse say(String channelId, String text, String language, String voice) {
        return activeCallService.say(channelId, text, language, voice);
    }

    public AudioStreamWriter listen(String channelId) {
        return activeCallService.listen(channelId);
    }

    /** The calls of {@code companyId} still in conversation — never another tenant's. */
    public List<LiveCallRow> findLiveCalls(long companyId) {
        return activeCallService.findLiveCalls(companyId);
    }

    /** Whether {@code channelId} is a live call of {@code companyId}. */
    public boolean isActiveCallOf(long companyId, String channelId) {
        return activeCallService.isActiveCallOf(companyId, channelId);
    }

    /** See {@link ActiveCallService#requireLiveCallOf}. */
    public void requireLiveCallOf(long companyId, String channelId) {
        activeCallService.requireLiveCallOf(companyId, channelId);
    }

    public long callAttemptIdOf(String channelId) {
        return activeCallService.callAttemptIdOf(channelId);
    }

    public String bridgeOf(String channelId) {
        return activeCallService.bridgeOf(channelId);
    }

    public void transferToOperator(String channelId) {
        operatorTransferService.transferToOperator(channelId);
    }

    /** See {@link OperatorTransferService#transferToOperator(String, String)}. */
    public void transferToOperator(String channelId, String operatorEndpoint) {
        operatorTransferService.transferToOperator(channelId, operatorEndpoint);
    }

    // ---------------------------------------------------------------------------------
    // The PBX side (docs/INBOUND-PBX-PLAN.md §3.2): what call flows, queues, operators and
    // supervisors need from Asterisk, and the two places a flow hands a call to an AI agent
    // and takes it back — FlowCallService, PbxLegService, CallMediaFactory, AriClient.
    // ---------------------------------------------------------------------------------

    /** The call the PBX side has on {@code channelId}, or null. */
    public FlowCall findFlowCall(String channelId) {
        return flowCallService.findFlowCall(channelId);
    }

    /** What an operator reads before picking up: the AI conversation so far, if an agent talked first. */
    public String findHandoverTranscript(String channelId) {
        return flowCallService.findHandoverTranscript(channelId);
    }

    /** See {@link FlowCallService#startAgentOnFlowCall}. */
    public boolean startAgentOnFlowCall(FlowCall call, long agentId, Runnable onCompleted, Runnable onTransfer) {
        return flowCallService.startAgentOnFlowCall(call, agentId, onCompleted, onTransfer);
    }

    /** See {@link FlowCallService#stopAgentOnFlowCall}. */
    public void stopAgentOnFlowCall(FlowCall call) {
        flowCallService.stopAgentOnFlowCall(call);
    }

    /** See {@link FlowCallService#transferCallToQueue}. */
    public void transferCallToQueue(long companyId, String channelId, long queueId) {
        flowCallService.transferCallToQueue(companyId, channelId, queueId);
    }

    public void hangupChannel(String channelId) {
        ariClient.hangup(channelId);
    }

    public void answerChannel(String channelId) {
        ariClient.answer(channelId);
    }

    /** Refuses the channel with a busy signal instead of a plain hang-up. */
    public void hangupChannelBusy(String channelId) {
        ariClient.hangupBusy(channelId);
    }

    /** Ringback to a channel that is waiting for someone to pick up. */
    public void ringChannel(String channelId) {
        ariClient.ring(channelId);
    }

    /** Whether a PJSIP endpoint has a registered, reachable contact — a softphone that is signed in. */
    public boolean isEndpointOnline(String endpointName) {
        return ariClient.isEndpointOnline(endpointName);
    }

    /** See {@link AriClient#snoop}. */
    public String snoop(String channelId, String spy, String whisper) {
        return ariClient.snoop(channelId, spy, whisper);
    }

    public String createBridge() {
        return ariClient.createBridge();
    }

    public void addToBridge(String bridgeId, String channelId) {
        ariClient.addToBridge(bridgeId, channelId);
    }

    public void removeFromBridge(String bridgeId, String channelId) {
        ariClient.removeFromBridge(bridgeId, channelId);
    }

    /** Best-effort: a bridge that is already gone is what was wanted anyway. */
    public void destroyBridge(String bridgeId) {
        ariClient.destroyBridge(bridgeId);
    }

    /** Ties two legs together: when either ends, the other is hung up and {@code bridgeId} (if any) destroyed. */
    public void linkLegs(String first, String second, String bridgeId) {
        pbxLegService.linkLegs(first, second, bridgeId);
    }

    /** See {@link CallMediaFactory#playToChannel}. */
    public void playToChannel(String channelId, short[] pcm) {
        callMediaFactory.playToChannel(channelId, pcm);
    }

    /** See {@link PbxLegService#bridgeWithRecording}. */
    public long bridgeWithRecording(String clientChannelId, String clientChannelName, long companyId, String phone,
                                    String operatorChannelId, String language) {
        return pbxLegService.bridgeWithRecording(clientChannelId, clientChannelName, companyId, phone,
                operatorChannelId, language);
    }

    /** See {@link AriClient#sendDtmf}. */
    public void sendDtmf(String channelId, String digits) {
        ariClient.sendDtmf(channelId, digits);
    }

    // ---------------------------------------------------------------------------------
    // Shutdown.
    // ---------------------------------------------------------------------------------

    @Override
    public void start() {
        lifecycleRunning = true;
    }

    @Override
    public boolean isRunning() {
        return lifecycleRunning;
    }

    @Override
    public int getPhase() {
        return SHUTDOWN_PHASE;
    }

    /**
     * Shuts the calls down, in order: drain (no new call is dispatched, live ones finish on
     * their own) → every call still up is hung up and torn down, side by side, within
     * {@code shutdown.teardown-timeout-sec} → the ARI connection is closed → the event lanes
     * finish what was already queued on them, within {@code shutdown.event-lane-timeout-sec}.
     * Only after this do the destroy callbacks take RTP, STT/TTS and the executors away.
     */
    @Override
    public void stop() {
        lifecycleRunning = false;
        shutdownManager.drain();
        // Each call is claimed before it is hung up, so the StasisEnd the hangup produces finds
        // nothing to tear down a second time — and a call a StasisEnd claimed first is left to it.
        List<Runnable> teardowns = new ArrayList<>();
        for (CallSession session : callSessionRegistry.findAll()) {
            CallSession claimed = callSessionRegistry.claim(session.channelId());
            if (claimed != null) {
                teardowns.add(() -> CallMdc.with(claimed.channelId(), () -> hangUpAndTearDown(claimed)));
            }
        }
        if (!teardowns.isEmpty()) {
            log.info("Shutdown: hanging up and tearing down {} call(s), up to {}s",
                    teardowns.size(), shutdownProperties.teardownTimeoutSec());
        }
        int unfinished = ShutdownTasks.runInParallel(teardowns,
                Duration.ofSeconds(shutdownProperties.teardownTimeoutSec()));
        if (unfinished > 0) {
            log.warn("Shutdown: {} call teardown(s) still running after {}s — not waiting for them",
                    unfinished, shutdownProperties.teardownTimeoutSec());
        }
        ariClient.cleanup();
        if (!ariEventLanes.shutdown(Duration.ofSeconds(shutdownProperties.eventLaneTimeoutSec()))) {
            log.warn("Shutdown: ARI event lanes still busy after {}s", shutdownProperties.eventLaneTimeoutSec());
        }
    }

    /**
     * Caller first: tearing down only the media leg left each of them on an open line with
     * nobody behind it (audit §4.13). The hangup stays ahead of the teardown for every call.
     */
    private void hangUpAndTearDown(CallSession session) {
        ariClient.hangup(session.channelId());
        try {
            callTeardownService.tearDownClaimed(session, dialogRouter.outcome(session.channelId()),
                    callSessionRegistry.findHangupCause(session.channelId()),
                    dialogRouter.technicalSnapshot(session.channelId()), false);
        } catch (Exception e) {
            log.warn("Teardown during shutdown failed for {}: {}", session.channelId(), e.getMessage());
        }
    }
}
