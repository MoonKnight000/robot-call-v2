package uz.murodjon.robotcallv2.agent.ari;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.flow.FlowPrompter;
import uz.murodjon.robotcallv2.agent.flow.PbxPhrases;
import uz.murodjon.robotcallv2.agent.session.CallSession;
import uz.murodjon.robotcallv2.agent.telephony.TelephonyGatewayResolver;
import uz.murodjon.robotcallv2.operator.infrastructure.config.OperatorProperties;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.util.Map;
import java.util.concurrent.*;

/**
 * A transfer without a queue, from the operator being dialled to the caller being either
 * connected or let go (AI-BENCHMARK-ROADMAP C9).
 *
 * <p>Before this a transfer nobody took left the caller in silence: the AI had said goodbye,
 * and nothing was watching the operator leg it dialled. Now a watchdog falls back once the
 * ring time is over, and so does an operator who answers but does not accept: the caller
 * hears that the operators are busy and the call is filed {@code CALLBACK_REQUESTED}, so a
 * campaign dials it again later.
 *
 * <p>Exactly one of "connect" and "fall back" happens per caller: whichever {@link #settle}s
 * first. The watchdog only acts while the operator leg is still ringing — once it has
 * answered, the briefing and the 1 may take longer than the ring time did.
 */
@Component
class OperatorAcceptance {

    private static final Logger log = LoggerFactory.getLogger(OperatorAcceptance.class);

    /** Over the ring time, before the watchdog decides nobody answered. */
    private static final long WATCHDOG_SLACK_SEC = 5;
    /** Beyond the message's own length, how long its playback may lag. */
    private static final long PLAYBACK_SLACK_MS = 1500;

    private enum State { RINGING, ANSWERED }

    /** A caller waiting on an operator leg, and whether that operator must press 1. */
    private record Waiting(State state, boolean askToAccept) {
    }

    /** Callers waiting on an operator leg, keyed by the caller's channel; gone once settled. */
    private final Map<String, Waiting> waiting = new ConcurrentHashMap<>();
    /** The 1 being waited for, keyed by the operator's channel. */
    private final Map<String, CompletableFuture<Character>> pendingKeys = new ConcurrentHashMap<>();

    private final CallSessionRegistry callSessionRegistry;
    private final TelephonyGatewayResolver telephonyGatewayResolver;
    private final CallMediaFactory callMediaFactory;
    private final ObjectProvider<FlowPrompter> flowPrompterProvider;
    private final OperatorProperties operatorProperties;
    private final ExecutorService callExecutor;

    OperatorAcceptance(CallSessionRegistry callSessionRegistry, TelephonyGatewayResolver telephonyGatewayResolver,
                       CallMediaFactory callMediaFactory, ObjectProvider<FlowPrompter> flowPrompterProvider,
                       OperatorProperties operatorProperties, ExecutorService callExecutor) {
        this.callSessionRegistry = callSessionRegistry;
        this.telephonyGatewayResolver = telephonyGatewayResolver;
        this.callMediaFactory = callMediaFactory;
        this.flowPrompterProvider = flowPrompterProvider;
        this.operatorProperties = operatorProperties;
        this.callExecutor = callExecutor;
    }

    /**
     * An operator leg was dialled for {@code callerId}. Starts the watchdog.
     *
     * @param askToAccept false for a supervisor taking the call over — they asked for it
     */
    void onOperatorDialled(String callerId, boolean askToAccept) {
        waiting.put(callerId, new Waiting(State.RINGING, askToAccept && operatorProperties.requireAcceptance()));
        long afterSec = Math.max(1, operatorProperties.answerTimeoutSec()) + WATCHDOG_SLACK_SEC;
        CompletableFuture.delayedExecutor(afterSec, TimeUnit.SECONDS, callExecutor).execute(() -> {
            Waiting current = waiting.get(callerId);
            if (current != null && current.state() == State.RINGING) {
                log.info("[{}] no operator answered the transfer in {}s", callerId, afterSec);
                fallBack(callerId);
            }
        });
    }

    /** The operator leg answered; the watchdog leaves the caller to the briefing and the 1 from here. */
    void onOperatorAnswered(String callerId) {
        waiting.computeIfPresent(callerId, (id, current) -> new Waiting(State.ANSWERED, current.askToAccept()));
    }

    /**
     * Asks the operator to press 1 and waits for it, when this transfer asks for that; true
     * otherwise. Blocks — called from the operator leg's own setup thread.
     */
    boolean awaitAcceptance(String callerId, String operatorChannelId, String language) {
        Waiting current = waiting.get(callerId);
        if (current == null || !current.askToAccept()) {
            return true;
        }
        FlowPrompter prompter = flowPrompterProvider.getIfAvailable();
        CompletableFuture<Character> key = new CompletableFuture<>();
        pendingKeys.put(operatorChannelId, key);
        try {
            if (prompter != null) {
                callMediaFactory.playToChannel(operatorChannelId,
                        prompter.speech(PbxPhrases.forwardConfirm(language), language));
            }
            Character pressed = key.get(operatorProperties.acceptTimeoutSec(), TimeUnit.SECONDS);
            boolean accepted = pressed != null && pressed == '1';
            log.info("[{}] operator {} {} the transfer", callerId, operatorChannelId, accepted ? "accepted" : "declined");
            return accepted;
        } catch (TimeoutException | ExecutionException e) {
            log.info("[{}] operator {} did not accept the transfer in time", callerId, operatorChannelId);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            pendingKeys.remove(operatorChannelId);
        }
    }

    /** A digit on an operator leg being asked to accept; true when it was consumed here. */
    boolean onDigit(String channelId, char digit) {
        CompletableFuture<Character> key = pendingKeys.get(channelId);
        if (key == null) {
            return false;
        }
        key.complete(digit);
        return true;
    }

    /**
     * Claims the caller for one outcome. True for the first claim only — the operator leg
     * connects only when it won, the fallback runs only when it did.
     */
    boolean settle(String callerId) {
        return waiting.remove(callerId) != null;
    }

    /**
     * Lets the caller go: tells them the operators are busy, files the call
     * {@code CALLBACK_REQUESTED} and hangs up. Nothing when the caller was already settled
     * or has left.
     */
    void fallBack(String callerId) {
        if (!settle(callerId)) {
            return;
        }
        CallSession caller = callSessionRegistry.find(callerId);
        if (caller == null) {
            return;
        }
        String language = caller.agent() != null && caller.agent().language() != null
                ? caller.agent().language() : "uz-UZ";
        FlowPrompter prompter = flowPrompterProvider.getIfAvailable();
        short[] pcm = prompter != null ? prompter.speech(PbxPhrases.operatorsBusy(language), language) : new short[0];
        if (pcm.length > 0) {
            // The caller's own media leg is still up: it is hung up only when an operator joins.
            caller.endpoint().playPcm(pcm);
            callMediaFactory.awaitSilence(caller.endpoint(), pcm.length * 1000L / CallMediaFactory.SAMPLE_RATE
                    + PLAYBACK_SLACK_MS);
        }
        callSessionRegistry.rememberDisposition(callerId, Disposition.CALLBACK_REQUESTED);
        telephonyGatewayResolver.findByCallId(callerId).hangup(callerId);
        log.info("[{}] transfer fell back: caller told the operators are busy, filed CALLBACK_REQUESTED", callerId);
    }
}
