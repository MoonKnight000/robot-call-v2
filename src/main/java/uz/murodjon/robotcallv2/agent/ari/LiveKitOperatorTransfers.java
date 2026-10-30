package uz.murodjon.robotcallv2.agent.ari;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.dialog.DialogRouter;
import uz.murodjon.robotcallv2.agent.audio.RingbackToneGenerator;
import uz.murodjon.robotcallv2.agent.dialog.OperatorSnapshot;
import uz.murodjon.robotcallv2.agent.flow.FlowCall;
import uz.murodjon.robotcallv2.agent.livekit.LiveKitBridgeClient;
import uz.murodjon.robotcallv2.agent.queue.QueueOutcome;
import uz.murodjon.robotcallv2.agent.telephony.WebRtcJoin;
import uz.murodjon.robotcallv2.agent.livekit.LiveKitTelephonyService;
import uz.murodjon.robotcallv2.agent.session.CallMdc;
import uz.murodjon.robotcallv2.agent.session.CallSession;
import uz.murodjon.robotcallv2.operator.application.port.input.ContactCenterEventUseCase;
import uz.murodjon.robotcallv2.operator.application.port.input.OperatorPresenceUseCase;
import uz.murodjon.robotcallv2.operator.domain.entity.CallOffer;
import uz.murodjon.robotcallv2.operator.domain.entity.OperatorPresence;
import uz.murodjon.robotcallv2.operator.domain.enums.ContactCenterEventType;
import uz.murodjon.robotcallv2.operator.domain.enums.OperatorStatus;
import uz.murodjon.robotcallv2.operator.infrastructure.config.OperatorProperties;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A LiveKit call handed to a person who takes it in the browser. The agent's transfer picks the
 * company's longest-available operator and offers them the call ({@code CALL_OFFER}); the operator
 * accepts and gets a token for the call's room, joins it as {@code op-<userId>}, and the media
 * bridge (the agent) leaves — caller and operator then talk through LiveKit alone, as after a
 * transfer to a phone number.
 *
 * <p>An offer nobody accepts and joins in {@code voice-agent.operator.answer-timeout-sec} falls
 * back like an Asterisk transfer does (operators busy, CALLBACK_REQUESTED). Queues, hold music and
 * a choice among several operators are Asterisk's (CallQueueDispatcher); here one operator is
 * offered the call.
 */
@Component
class LiveKitOperatorTransfers {

    private static final Logger log = LoggerFactory.getLogger(LiveKitOperatorTransfers.class);
    /** How long the operator wraps up after a call, as the dialers give it. */
    private static final int WRAP_UP_SEC = 10;

    /**
     * The operator a call has been offered to, until they join the room or the offer lapses.
     *
     * @param result how a call flow's offer ended; null when the AI agent offered the call, whose lapse falls back instead
     */
    private record Offer(long userId, long companyId, String identity, CompletableFuture<QueueOutcome> result) {
    }

    private final OperatorPresenceUseCase operatorPresenceUseCase;
    private final ContactCenterEventUseCase contactCenterEventUseCase;
    private final CallSessionRegistry callSessionRegistry;
    private final DialogRouter dialogRouter;
    /** Lazy: the transfer service reaches the flow call service, whose flow asks this class for operators. */
    private final ObjectProvider<OperatorTransferService> operatorTransferServiceProvider;
    private final OperatorAcceptance operatorAcceptance;
    private final OperatorProperties operatorProperties;
    private final LiveKitTelephonyService liveKitTelephonyService;
    private final LiveKitBridgeClient liveKitBridgeClient;
    private final ExecutorService callExecutor;
    private final FlowCallRegistry flowCallRegistry;

    private final Map<String, Offer> offers = new ConcurrentHashMap<>();
    /** Room -> the operator on the line, from joining to the end of the call. */
    private final Map<String, Long> connected = new ConcurrentHashMap<>();
    /** Room -> the operators a waiting line has already offered the call to this round. */
    private final Map<String, Set<Long>> triedByRoom = new ConcurrentHashMap<>();

    LiveKitOperatorTransfers(OperatorPresenceUseCase operatorPresenceUseCase,
                             ContactCenterEventUseCase contactCenterEventUseCase,
                             CallSessionRegistry callSessionRegistry,
                             DialogRouter dialogRouter,
                             ObjectProvider<OperatorTransferService> operatorTransferServiceProvider,
                             OperatorAcceptance operatorAcceptance,
                             OperatorProperties operatorProperties,
                             LiveKitTelephonyService liveKitTelephonyService,
                             LiveKitBridgeClient liveKitBridgeClient,
                             ExecutorService callExecutor,
                             FlowCallRegistry flowCallRegistry) {
        this.flowCallRegistry = flowCallRegistry;
        this.operatorPresenceUseCase = operatorPresenceUseCase;
        this.contactCenterEventUseCase = contactCenterEventUseCase;
        this.callSessionRegistry = callSessionRegistry;
        this.dialogRouter = dialogRouter;
        this.operatorTransferServiceProvider = operatorTransferServiceProvider;
        this.operatorAcceptance = operatorAcceptance;
        this.operatorProperties = operatorProperties;
        this.liveKitTelephonyService = liveKitTelephonyService;
        this.liveKitBridgeClient = liveKitBridgeClient;
        this.callExecutor = callExecutor;
    }

    /**
     * Offers the call to the company's longest-available operator. Without one — or when the call
     * is already gone — the caller is let go the way an unanswered transfer lets them go.
     */
    void transfer(String roomName, long companyId) {
        operatorAcceptance.onOperatorDialled(roomName, false);
        if (!offer(roomName, companyId)) {
            log.info("[{}] no operator is available for the transfer", roomName);
            operatorAcceptance.fallBack(roomName);
        }
    }

    private boolean offer(String roomName, long companyId) {
        CallSession session = callSessionRegistry.find(roomName);
        if (session == null) {
            return false;
        }
        for (OperatorPresence candidate : findAvailable(companyId, null)) {
            if (tryOffer(roomName, session, companyId, candidate.userId(), null,
                    Math.max(1, operatorProperties.answerTimeoutSec()))) {
                return true;
            }
        }
        return false;
    }

    /** The company's available operators, longest-available first; only those of {@code among} when it is given. */
    private List<OperatorPresence> findAvailable(long companyId, Set<Long> among) {
        return operatorPresenceUseCase.findByCompany(companyId).stream()
                .filter(presence -> presence.status() == OperatorStatus.AVAILABLE)
                .filter(presence -> among == null || among.contains(presence.userId()))
                .sorted(Comparator.comparing(OperatorPresence::since))
                .toList();
    }

    /** Offers the call to one operator; false when they were taken meanwhile or the offer could not be sent. */
    private boolean tryOffer(String roomName, CallSession session, long companyId, long userId,
                             CompletableFuture<QueueOutcome> result, int timeoutSec) {
        if (!operatorPresenceUseCase.claimForRinging(userId, roomName)) {
            return false;
        }
        Offer offer = new Offer(userId, companyId, LiveKitTelephonyService.OPERATOR_WEB_PREFIX + userId, result);
        offers.put(roomName, offer);
        try {
            OperatorSnapshot snapshot = flowCallRegistry.findSnapshot(roomName);
            if (snapshot == null) {
                snapshot = dialogRouter.operatorSnapshot(roomName);
            }
            CallOffer callOffer = operatorTransferServiceProvider.getObject().buildCallOffer(roomName, session, snapshot,
                    OperatorTransferService.buildHandoverSummary(snapshot));
            contactCenterEventUseCase.publishToUser(userId, ContactCenterEventType.CALL_OFFER, callOffer);
        } catch (RuntimeException e) {
            log.warn("[{}] could not offer the call to operator {}: {}", roomName, userId, e.getMessage());
            offers.remove(roomName, offer);
            operatorPresenceUseCase.releaseRinging(userId);
            return false;
        }
        log.info("[{}] offered to operator {}", roomName, userId);
        CompletableFuture.delayedExecutor(timeoutSec, TimeUnit.SECONDS, callExecutor)
                .execute(() -> CallMdc.with(roomName, () -> lapse(roomName, offer)));
        return true;
    }

    /** The offer was neither joined nor declined in time. */
    private void lapse(String roomName, Offer offer) {
        if (!offers.remove(roomName, offer)) {
            return;
        }
        log.info("[{}] operator {} did not take the transfer in time", roomName, offer.userId());
        operatorPresenceUseCase.markMissed(offer.userId());
        if (offer.result() != null) {
            offer.result().complete(QueueOutcome.NO_ANSWER);
        } else {
            operatorAcceptance.fallBack(roomName);
        }
    }

    /**
     * A call flow's call offered in turn to the available ones of {@code userIds}, each for
     * {@code ringSeconds}, with a ringback tone for the caller. Returns once the call is over for
     * the person who took it, so the flow carries on only after that.
     */
    QueueOutcome ringUsers(FlowCall call, List<Long> userIds, int ringSeconds) throws InterruptedException {
        return ring(call, userIds, ringSeconds, false);
    }

    /** One step of a waiting line: the next available one of {@code userIds} who has not yet let this call go. */
    QueueOutcome ringNextUser(FlowCall call, List<Long> userIds, int ringSeconds) throws InterruptedException {
        return ring(call, userIds, ringSeconds, true);
    }

    /** Whether any of {@code userIds} is signed in, whatever they are doing. */
    boolean anySignedIn(long companyId, List<Long> userIds) {
        Set<Long> among = Set.copyOf(userIds);
        return operatorPresenceUseCase.findByCompany(companyId).stream()
                .anyMatch(presence -> among.contains(presence.userId()) && presence.status() != OperatorStatus.OFFLINE);
    }

    /**
     * @param oneStep ask only the next operator who has not had the call yet; once everyone has, the
     *                round starts over. Otherwise every available one is asked in turn.
     */
    private QueueOutcome ring(FlowCall call, List<Long> userIds, int ringSeconds, boolean oneStep)
            throws InterruptedException {
        String roomName = call.channelId();
        CallSession session = callSessionRegistry.find(roomName);
        if (session == null) {
            return QueueOutcome.ABANDONED;
        }
        Set<Long> tried = triedByRoom.computeIfAbsent(roomName, room -> ConcurrentHashMap.newKeySet());
        if (!oneStep) {
            tried.clear();
        }
        List<OperatorPresence> candidates = findAvailable(call.companyId(), Set.copyOf(userIds)).stream()
                .filter(presence -> !tried.contains(presence.userId()))
                .toList();
        if (candidates.isEmpty()) {
            tried.clear();
        }
        for (OperatorPresence candidate : candidates) {
            CompletableFuture<QueueOutcome> result = new CompletableFuture<>();
            if (!tryOffer(roomName, session, call.companyId(), candidate.userId(), result, Math.max(1, ringSeconds))) {
                continue;
            }
            tried.add(candidate.userId());
            QueueOutcome outcome = awaitOffer(call, result);
            if (outcome == QueueOutcome.ANSWERED) {
                while (!call.isGone()) {
                    Thread.sleep(200);
                }
                return QueueOutcome.ANSWERED;
            }
            if (outcome == QueueOutcome.ABANDONED || oneStep) {
                return outcome == QueueOutcome.ABANDONED ? outcome : QueueOutcome.NO_ANSWER;
            }
        }
        log.debug("[{}] no operator among {} took the call", roomName, userIds);
        return QueueOutcome.NO_ANSWER;
    }

    private QueueOutcome awaitOffer(FlowCall call, CompletableFuture<QueueOutcome> result) throws InterruptedException {
        short[] ringback = RingbackToneGenerator.generateRingback(6000);
        while (!result.isDone()) {
            if (call.isGone()) {
                onCallEnded(call.channelId());
                return QueueOutcome.ABANDONED;
            }
            if (!call.endpoint().isPlaying()) {
                call.endpoint().playPcm(ringback);
            }
            Thread.sleep(200);
        }
        call.endpoint().flushPlayback();
        return result.getNow(QueueOutcome.NO_ANSWER);
    }

    /**
     * The operator accepts: the room and a token to join it with. Joining is what completes the
     * transfer ({@link #onOperatorJoined}); asking again before that gives a fresh token.
     */
    WebRtcJoin accept(long companyId, long userId, String roomName) {
        requireOffered(companyId, userId, roomName);
        return liveKitTelephonyService.createOperatorJoin(roomName, userId);
    }

    /** The operator turns the call down: they are free again and the caller is let go. */
    void decline(long companyId, long userId, String roomName) {
        Offer offer = requireOffered(companyId, userId, roomName);
        if (!offers.remove(roomName, offer)) {
            return;
        }
        operatorPresenceUseCase.releaseRinging(userId);
        if (offer.result() != null) {
            offer.result().complete(QueueOutcome.NO_ANSWER);
        } else {
            operatorAcceptance.fallBack(roomName);
        }
    }

    private Offer requireOffered(long companyId, long userId, String roomName) {
        Offer offer = offers.get(roomName);
        if (offer == null || offer.userId() != userId || offer.companyId() != companyId) {
            throw new NotFoundException(ErrorCode.LIVEKIT_TRANSFER_NOT_FOUND, roomName);
        }
        return offer;
    }

    /**
     * A browser participant {@code identity} joined {@code roomName}. The offered operator becomes
     * the call's person and the agent leaves; anyone else is put out of the room.
     */
    void onOperatorJoined(String roomName, String identity) {
        callExecutor.execute(() -> CallMdc.with(roomName, () -> {
            Offer offer = offers.remove(roomName);
            if (offer == null) {
                Long onTheLine = connected.get(roomName);
                if (onTheLine == null || !identity.equals(LiveKitTelephonyService.OPERATOR_WEB_PREFIX + onTheLine)) {
                    liveKitTelephonyService.removeParticipant(roomName, identity);
                }
                return;
            }
            if (!offer.identity().equals(identity)) {
                offers.putIfAbsent(roomName, offer);
                liveKitTelephonyService.removeParticipant(roomName, identity);
                return;
            }
            // A flow's offer has no watchdog: it is the flow's own wait that the join answers.
            boolean settled = offer.result() != null || operatorAcceptance.settle(roomName);
            if (!settled || !callSessionRegistry.contains(roomName)) {
                // The watchdog let the caller go first, or the caller hung up meanwhile.
                log.info("[{}] operator {} joined after the caller was let go — putting them out", roomName,
                        offer.userId());
                operatorPresenceUseCase.releaseRinging(offer.userId());
                if (offer.result() != null) {
                    offer.result().complete(QueueOutcome.ABANDONED);
                }
                liveKitTelephonyService.removeParticipant(roomName, identity);
                return;
            }
            connected.put(roomName, offer.userId());
            // A person is on the line now: the reaper's AI call limit no longer applies.
            callSessionRegistry.markOperatorJoined(roomName);
            operatorPresenceUseCase.markOnCall(offer.userId(), roomName);
            liveKitBridgeClient.stopMedia(roomName);
            log.info("[{}] operator {} joined — the agent left the room", roomName, offer.userId());
            if (offer.result() != null) {
                offer.result().complete(QueueOutcome.ANSWERED);
            }
        }));
    }

    /** The operator on the line left the room: the call is over. */
    void onOperatorLeft(String roomName, String identity) {
        Long onTheLine = connected.get(roomName);
        if (onTheLine != null && identity.equals(LiveKitTelephonyService.OPERATOR_WEB_PREFIX + onTheLine)) {
            log.info("[{}] operator {} left — ending the call", roomName, onTheLine);
            liveKitTelephonyService.hangUp(roomName);
        }
    }

    /** The call's phone leg is gone: the operator, if one was offered or on the line, is free again. */
    void onCallEnded(String roomName) {
        triedByRoom.remove(roomName);
        Offer offer = offers.remove(roomName);
        if (offer != null) {
            operatorPresenceUseCase.releaseRinging(offer.userId());
            if (offer.result() != null) {
                offer.result().complete(QueueOutcome.ABANDONED);
            }
        }
        Long userId = connected.remove(roomName);
        if (userId != null) {
            operatorPresenceUseCase.markCallEnded(userId, WRAP_UP_SEC);
        }
    }
}
