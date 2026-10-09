package uz.murodjon.robotcallv2.agent.ari;

import ch.loway.oss.ari4java.ARI;
import ch.loway.oss.ari4java.generated.models.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.dialog.DialogRouter;
import uz.murodjon.robotcallv2.agent.dialog.OperatorSnapshot;
import uz.murodjon.robotcallv2.agent.flow.FlowPrompter;
import uz.murodjon.robotcallv2.agent.metrics.CallMetrics;
import uz.murodjon.robotcallv2.agent.queue.CallQueueDispatcher;
import uz.murodjon.robotcallv2.agent.queue.OperatorCallControl;
import uz.murodjon.robotcallv2.agent.queue.OperatorFirstDialer;
import uz.murodjon.robotcallv2.agent.queue.PredictiveDialer;
import uz.murodjon.robotcallv2.agent.queue.ProgressiveDialer;
import uz.murodjon.robotcallv2.agent.queue.SupervisorMonitor;
import uz.murodjon.robotcallv2.agent.routing.CallRouteRegistry;
import uz.murodjon.robotcallv2.agent.rtp.RtpPortAllocator;
import uz.murodjon.robotcallv2.agent.session.CallMdc;
import uz.murodjon.robotcallv2.agent.session.CallSession;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallAttempt;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Legs the PBX side placed or was dialled on (docs/INBOUND-PBX-PLAN.md §3.2): an operator
 * answering a queue offer, a callback, a softphone dialling out, a consultation, a
 * supervisor — who gets them on {@code StasisStart}, who cleans up after them when they end,
 * and the person-to-person calls whose two legs end together.
 */
@Service
class PbxLegService {

    private static final Logger log = LoggerFactory.getLogger(PbxLegService.class);

    private final ExecutorService callExecutor;
    private final CallQueueDispatcher callQueueDispatcher;
    private final OperatorFirstDialer operatorFirstDialer;
    private final ProgressiveDialer progressiveDialer;
    private final PredictiveDialer predictiveDialer;
    private final SupervisorMonitor supervisorMonitor;
    private final OperatorCallControl operatorCallControl;
    private final AriClient ariClient;
    private final CallMediaFactory callMediaFactory;
    private final OperatorCallRecorder operatorCallRecorder;
    private final CallSessionRegistry callSessionRegistry;
    private final CallAttemptUseCase callAttemptUseCase;
    private final CallMetrics metrics;
    private final CallRouteRegistry routeRegistry;
    private final RtpPortAllocator portAllocator;
    private final CallTeardownService callTeardownService;
    private final DialogRouter dialogRouter;
    private final ObjectProvider<FlowPrompter> flowPrompterProvider;
    private final OperatorAcceptance operatorAcceptance;

    /** Two legs of one person-to-person call: when either ends, the other is hung up (and their bridge destroyed). */
    private final Map<String, String> linkedLegs = new ConcurrentHashMap<>();
    private final Map<String, String> linkedBridges = new ConcurrentHashMap<>();

    PbxLegService(ExecutorService callExecutor,
                  CallQueueDispatcher callQueueDispatcher,
                  OperatorFirstDialer operatorFirstDialer,
                  ProgressiveDialer progressiveDialer,
                  PredictiveDialer predictiveDialer,
                  SupervisorMonitor supervisorMonitor,
                  OperatorCallControl operatorCallControl,
                  AriClient ariClient,
                  CallMediaFactory callMediaFactory,
                  OperatorCallRecorder operatorCallRecorder,
                  CallSessionRegistry callSessionRegistry,
                  CallAttemptUseCase callAttemptUseCase,
                  CallMetrics metrics,
                  CallRouteRegistry routeRegistry,
                  RtpPortAllocator portAllocator,
                  CallTeardownService callTeardownService,
                  DialogRouter dialogRouter,
                  ObjectProvider<FlowPrompter> flowPrompterProvider,
                  OperatorAcceptance operatorAcceptance) {
        this.callExecutor = callExecutor;
        this.callQueueDispatcher = callQueueDispatcher;
        this.operatorFirstDialer = operatorFirstDialer;
        this.progressiveDialer = progressiveDialer;
        this.predictiveDialer = predictiveDialer;
        this.supervisorMonitor = supervisorMonitor;
        this.operatorCallControl = operatorCallControl;
        this.ariClient = ariClient;
        this.callMediaFactory = callMediaFactory;
        this.operatorCallRecorder = operatorCallRecorder;
        this.callSessionRegistry = callSessionRegistry;
        this.callAttemptUseCase = callAttemptUseCase;
        this.metrics = metrics;
        this.routeRegistry = routeRegistry;
        this.portAllocator = portAllocator;
        this.callTeardownService = callTeardownService;
        this.dialogRouter = dialogRouter;
        this.flowPrompterProvider = flowPrompterProvider;
        this.operatorAcceptance = operatorAcceptance;
    }

    /**
     * Legs the PBX side placed, or was dialled on: an operator answering a queue offer, a
     * callback, a softphone dialling out, a consultation, a supervisor. True when the leg was
     * handed on and needs nothing more here.
     */
    boolean routePbxLeg(String kind, String argument, List<String> args, Channel channel) {
        if (kind == null) {
            return false;
        }
        String channelId = channel.getId();
        Runnable handler = switch (kind) {
            case "operator" -> args.size() > 3
                    ? () -> callQueueDispatcher.onOperatorAnswered(args.get(3), channelId)
                    : () -> handleOperatorJoin(channel, args);
            case "callback" -> () -> operatorFirstDialer.onOperatorAnswered(argument, channelId);
            case "callback-client" -> () -> operatorFirstDialer.onClientAnswered(argument, channelId, channel.getName());
            case "progressive-client" -> () -> progressiveDialer.onClientAnswered(argument, channelId, channel.getName());
            case "progressive-operator" -> () -> progressiveDialer.onOperatorAnswered(argument, channelId);
            case "predictive-client" -> () -> predictiveDialer.onClientAnswered(argument, channelId, channel.getName());
            case "predictive-operator" -> () -> predictiveDialer.onOperatorAnswered(argument, channelId);
            case "internal" -> () -> operatorFirstDialer.onOperatorDialled(channelId, channel.getName(), argument);
            case "internal-answer" -> () -> operatorFirstDialer.onColleagueAnswered(argument, channelId);
            case "consult" -> () -> operatorCallControl.onConsultAnswered(argument, channelId);
            case "monitor" -> () -> supervisorMonitor.onSupervisorAnswered(argument, channelId);
            default -> null;
        };
        if (handler == null) {
            return false;
        }
        log.info("StasisStart ({}): channel {} args {}", kind, channelId, args);
        callExecutor.execute(() -> CallMdc.with(channelId, handler));
        return true;
    }

    private void handleOperatorJoin(Channel operatorChannel, List<String> args) {
        String bridgeId = args.get(1);
        String callerId = args.get(2);
        try {
            ARI current = ariClient.requireConnection();
            current.channels().answer(operatorChannel.getId()).execute();
            operatorAcceptance.onOperatorAnswered(callerId);
            CallSession caller = callSessionRegistry.find(callerId);
            String language = (caller != null && caller.agent() != null && caller.agent().language() != null)
                    ? caller.agent().language() : "uz-UZ";
            OperatorSnapshot snapshot = dialogRouter.operatorSnapshot(callerId);
            FlowPrompter prompter = flowPrompterProvider.getIfAvailable();
            if (snapshot != null && prompter != null) {
                String whisper = buildOperatorWhisper(snapshot);
                if (!whisper.isBlank()) {
                    short[] pcm = prompter.speech(whisper, language);
                    if (pcm.length > 0) {
                        callMediaFactory.playToChannel(operatorChannel.getId(), pcm);
                    }
                }
            }
            if (!operatorAcceptance.awaitAcceptance(callerId, operatorChannel.getId(), language)) {
                ariClient.hangup(operatorChannel.getId());
                operatorAcceptance.fallBack(callerId);
                return;
            }
            if (caller == null || !callSessionRegistry.contains(callerId)) {
                log.info("Caller {} hung up before operator join completed; hanging up operator {}", callerId, operatorChannel.getId());
                operatorAcceptance.settle(callerId);
                ariClient.hangup(operatorChannel.getId());
                return;
            }
            if (!operatorAcceptance.settle(callerId)) {
                // The watchdog let the caller go first.
                log.info("Caller {} was already let go; hanging up operator {}", callerId, operatorChannel.getId());
                ariClient.hangup(operatorChannel.getId());
                return;
            }
            // Only now: until the operator is in, the caller's media leg is what tells them,
            // should it come to that, that nobody could take the call.
            try {
                current.channels().hangup(caller.extMediaChannelId()).execute();
            } catch (Exception e) {
                log.debug("extMedia {} already gone: {}", caller.extMediaChannelId(), e.getMessage());
            }
            current.bridges().addChannel(bridgeId, operatorChannel.getId()).execute();
            // A person is on the line now: without this the reaper cut the customer off
            // mid-conversation once the AI call's limit (maxCallSeconds + 60) had passed.
            callSessionRegistry.markOperatorJoined(callerId);
            operatorCallRecorder.attach(callerId, operatorChannel.getId());
            log.info("Operator {} joined bridge {} for caller {}", operatorChannel.getId(), bridgeId, callerId);
        } catch (Exception e) {
            log.error("Operator join failed for bridge {}: {}", bridgeId, e.getMessage());
            ariClient.hangup(operatorChannel.getId());
            operatorAcceptance.fallBack(callerId);
        }
    }

    private static String buildOperatorWhisper(OperatorSnapshot snapshot) {
        if (snapshot == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (snapshot.clientName() != null && !snapshot.clientName().isBlank()) {
            sb.append("Mijoz: ").append(snapshot.clientName()).append(". ");
        }
        if (snapshot.transferReason() != null && !snapshot.transferReason().isBlank()) {
            // Why the caller is being handed over is the one thing the operator cannot read
            // off the facts — without it the caller has to explain everything again.
            sb.append("Sabab: ").append(snapshot.transferReason()).append(". ");
        }
        if (snapshot.dialogState() != null && !snapshot.dialogState().isBlank()) {
            sb.append("Holat: ").append(snapshot.dialogState()).append(". ");
        }
        if (snapshot.debtAmount() != null && !snapshot.debtAmount().isBlank()) {
            sb.append("Summa: ").append(snapshot.debtAmount()).append(". ");
        }
        return sb.toString().trim();
    }

    /** Ties two legs together: when either ends, the other is hung up and {@code bridgeId} (if any) destroyed. */
    void linkLegs(String first, String second, String bridgeId) {
        linkedLegs.put(first, second);
        linkedLegs.put(second, first);
        if (bridgeId != null) {
            linkedBridges.put(first, bridgeId);
            linkedBridges.put(second, bridgeId);
        }
    }

    /** Whether {@code channelId} is one leg of a person-to-person call. */
    boolean isLinked(String channelId) {
        return linkedLegs.containsKey(channelId);
    }

    /**
     * Joins an operator and a client in one recorded bridge — the client leg of a callback or
     * of a softphone's outside call. The client's channel becomes a session like any other,
     * so it is finalized, recorded and listed; either side hanging up ends both.
     *
     * @return the call row
     */
    long bridgeWithRecording(String clientChannelId, String clientChannelName, long companyId, String phone,
                             String operatorChannelId, String language) {
        ARI current = ariClient.requireConnection();
        int port = portAllocator.allocate();
        MediaLegs legs = null;
        // From registration on, the resources are the teardown's to release, never the catch's.
        boolean registered = false;
        // The client leg reached Stasis through routePbxLeg, which marks nothing: marked here, so
        // a StasisEnd arriving while the bridge is built is left to this thread, as in any setup.
        callSessionRegistry.markSettingUp(clientChannelId);
        try {
            legs = callMediaFactory.openMediaLegs(current, clientChannelId, port);
            ariClient.answer(clientChannelId);
            ariClient.answer(operatorChannelId);
            current.bridges().addChannel(legs.bridgeId(), clientChannelId).execute();
            current.bridges().addChannel(legs.bridgeId(), operatorChannelId).execute();
            long attemptId = callAttemptUseCase.findAttemptIdByChannel(clientChannelId);
            if (attemptId == 0) {
                attemptId = callAttemptUseCase.startAttempt(companyId, CallAttempt.NO_TARGET, clientChannelId,
                        phone, language, null, null);
            }
            callAttemptUseCase.markAnswered(attemptId);
            boolean active = callSessionRegistry.register(new CallSession(clientChannelId, companyId, legs.extMediaChannelId(),
                    legs.bridgeId(), port, legs.endpoint(), attemptId, Instant.now(), legs.wav().toString(),
                    clientChannelName, ChannelNames.extractTrunk(clientChannelName), 0, legs.audioMonitor(), false, null));
            registered = true;
            metrics.callStarted();
            metrics.activeInc();
            routeRegistry.register(clientChannelId, companyId);
            if (!active) {
                // The client hung up while the two were being joined: the operator is not left
                // on a bridge with nobody, and the client's call is torn down here.
                log.info("[{}] client left while being joined with operator leg {} — tearing down",
                        clientChannelId, operatorChannelId);
                ariClient.hangup(operatorChannelId);
                callTeardownService.tearDownIfClaimed(clientChannelId, dialogRouter.outcome(clientChannelId),
                        callSessionRegistry.findHangupCause(clientChannelId),
                        dialogRouter.technicalSnapshot(clientChannelId), false);
                return attemptId;
            }
            linkLegs(clientChannelId, operatorChannelId, null);
            operatorCallRecorder.attach(clientChannelId, operatorChannelId);
            return attemptId;
        } catch (Exception e) {
            if (!registered) {
                if (legs != null) {
                    callMediaFactory.releaseMediaLegs(current, legs);
                }
                portAllocator.release(port);
            }
            throw new ExternalServiceException(ErrorCode.ARI_OPERATOR_CALL_FAILED, "asterisk", e, clientChannelId,
                    e.getMessage());
        } finally {
            callSessionRegistry.finishSettingUp(clientChannelId);
        }
    }

    /** A leg ended: the components that own PBX legs clean up theirs, and a linked leg follows it. */
    void notifyPbxLegGone(String channelId, Integer cause) {
        operatorCallRecorder.release(channelId);
        try {
            callQueueDispatcher.onChannelGone(channelId, cause);
            operatorFirstDialer.onChannelGone(channelId);
            progressiveDialer.onChannelGone(channelId, cause);
            predictiveDialer.onChannelGone(channelId, cause);
            supervisorMonitor.onChannelGone(channelId);
            operatorCallControl.onChannelGone(channelId);
        } catch (Exception e) {
            log.warn("Cleaning up after channel {} failed: {}", channelId, e.getMessage());
        }
        String partner = linkedLegs.remove(channelId);
        String bridge = linkedBridges.remove(channelId);
        if (partner != null) {
            linkedLegs.remove(partner);
            linkedBridges.remove(partner);
            ariClient.hangup(partner);
        }
        if (bridge != null) {
            ariClient.destroyBridge(bridge);
        }
    }
}
