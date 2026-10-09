package uz.murodjon.robotcallv2.agent.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.ari.AriService;
import uz.murodjon.robotcallv2.agent.flow.FlowCall;
import uz.murodjon.robotcallv2.agent.flow.FlowPrompter;
import uz.murodjon.robotcallv2.agent.flow.PbxPhrases;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueue;
import uz.murodjon.robotcallv2.callqueue.domain.enums.CallQueueStrategy;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallEventUseCase;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallTransferTarget;
import uz.murodjon.robotcallv2.callrecord.domain.enums.CallEventType;
import uz.murodjon.robotcallv2.missedcall.application.port.input.MissedCallUseCase;
import uz.murodjon.robotcallv2.missedcall.domain.enums.MissedCallStatus;
import uz.murodjon.robotcallv2.operator.application.port.input.ContactCenterEventUseCase;
import uz.murodjon.robotcallv2.operator.application.port.input.OperatorAccountUseCase;
import uz.murodjon.robotcallv2.operator.application.port.input.OperatorPresenceUseCase;
import uz.murodjon.robotcallv2.operator.domain.entity.CallOffer;
import uz.murodjon.robotcallv2.operator.domain.entity.OperatorPresence;
import uz.murodjon.robotcallv2.operator.domain.entity.OperatorSipAccount;
import uz.murodjon.robotcallv2.operator.domain.enums.ContactCenterEventType;
import uz.murodjon.robotcallv2.operator.domain.enums.OperatorStatus;
import uz.murodjon.robotcallv2.profile.application.port.input.ProfileScheduleUseCase;
import uz.murodjon.robotcallv2.profile.domain.entity.ScheduleSlot;
import uz.murodjon.robotcallv2.schedule.application.port.input.BusinessScheduleUseCase;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * The automatic call distributor (docs/INBOUND-PBX-PLAN.md §7.3): keeps every queue's
 * waiting calls in service order (VIP first, then by arrival), rings the operators the
 * queue's strategy picks, and joins the first one to answer to the caller.
 *
 * <p>An offer is an originate of the operator's PJSIP endpoint (or, when the softphone is
 * offline, their mobile over the trunk) into this application with the call's bridge in
 * the Stasis arguments. The ring timeout is Asterisk's: an offer nobody answers ends as a
 * destroyed channel, which counts against the operator (RONA — two in a row and they are
 * put on a break). An answered offer hears the handover summary alone, then joins the
 * caller's bridge; the call's recording leg stays in it, so the operator conversation is
 * recorded like any other.
 *
 * <p>State is in memory: one voice-agent instance serves one Asterisk.
 */
@Component
public class CallQueueDispatcher {

    private static final Logger log = LoggerFactory.getLogger(CallQueueDispatcher.class);
    private static final int HANDLE_TIME_SAMPLES = 50;
    private static final long ENDPOINT_STATE_TTL_MS = 5_000;
    private static final long CACHE_TTL_MS = 30_000;
    private static final long CONFIRM_TIMEOUT_MS = 10_000;
    /** How far back a caller's last operator still counts for sticky routing. */
    private static final Duration STICKY_LOOKBACK = Duration.ofDays(30);

    private final ObjectProvider<AriService> ariProvider;
    private final OperatorPresenceUseCase operatorPresenceUseCase;
    private final OperatorAccountUseCase operatorAccountUseCase;
    private final ProfileScheduleUseCase profileScheduleUseCase;
    private final BusinessScheduleUseCase businessScheduleUseCase;
    private final CallAttemptUseCase callAttemptUseCase;
    private final CallEventUseCase callEventUseCase;
    private final ContactCenterEventUseCase contactCenterEventUseCase;
    private final MissedCallUseCase missedCallUseCase;
    private final FlowPrompter prompter;
    private final ExecutorService callExecutor;
    private final Clock clock;
    private final Random random = new Random();

    /** Queue id → its waiting calls in service order; an ad-hoc call is alone under minus its own id. */
    private final Map<Long, List<WaitingCall>> lines = new ConcurrentHashMap<>();
    private final Map<String, Offer> offersById = new ConcurrentHashMap<>();
    private final Map<String, Offer> offersByChannel = new ConcurrentHashMap<>();
    private final Map<String, WaitingCall> connectedByOperatorChannel = new ConcurrentHashMap<>();
    private final Map<String, WaitingCall> byCallerChannel = new ConcurrentHashMap<>();
    private final Map<Long, Long> lastOfferedUser = new ConcurrentHashMap<>();
    private final Map<Long, Deque<Integer>> handleTimes = new ConcurrentHashMap<>();
    private final Map<String, Cached<Boolean>> endpointStates = new ConcurrentHashMap<>();
    private final Map<Long, Cached<OperatorSipAccount>> accountCache = new ConcurrentHashMap<>();
    private final Map<Long, Cached<List<ScheduleSlot>>> shiftCache = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Character>> pendingConfirms = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    /** One operator (or number) ringing for one waiting call. */
    private static final class Offer {
        private final String id;
        private final WaitingCall call;
        private final Long userId;
        private final boolean confirm;
        private volatile String channelId;

        private Offer(String id, WaitingCall call, Long userId, boolean confirm) {
            this.id = id;
            this.call = call;
            this.userId = userId;
            this.confirm = confirm;
        }
    }

    private record Cached<T>(T value, long atMillis) {
        boolean fresh(long ttlMillis) {
            return System.currentTimeMillis() - atMillis < ttlMillis;
        }
    }

    public CallQueueDispatcher(ObjectProvider<AriService> ariProvider, OperatorPresenceUseCase operatorPresenceUseCase,
                               OperatorAccountUseCase operatorAccountUseCase, ProfileScheduleUseCase profileScheduleUseCase,
                               BusinessScheduleUseCase businessScheduleUseCase, CallAttemptUseCase callAttemptUseCase,
                               CallEventUseCase callEventUseCase, ContactCenterEventUseCase contactCenterEventUseCase,
                               MissedCallUseCase missedCallUseCase, FlowPrompter prompter, ExecutorService callExecutor,
                               Clock clock) {
        this.ariProvider = ariProvider;
        this.operatorPresenceUseCase = operatorPresenceUseCase;
        this.operatorAccountUseCase = operatorAccountUseCase;
        this.profileScheduleUseCase = profileScheduleUseCase;
        this.businessScheduleUseCase = businessScheduleUseCase;
        this.callAttemptUseCase = callAttemptUseCase;
        this.callEventUseCase = callEventUseCase;
        this.contactCenterEventUseCase = contactCenterEventUseCase;
        this.missedCallUseCase = missedCallUseCase;
        this.prompter = prompter;
        this.callExecutor = callExecutor;
        this.clock = clock;
    }

    /**
     * Puts a call in line. For a forward ({@code externalNumber} set) the line is the call
     * alone and the number is rung once instead of operators.
     */
    public WaitingCall enqueue(FlowCall call, CallQueue queue, String handoverLanguage, String transcript,
                               String externalNumber, boolean confirmExternal) {
        long id = sequence.incrementAndGet();
        Long sticky = queue.id() > 0 && queue.stickySeconds() > 0
                ? callAttemptUseCase.findLastOperator(call.companyId(), call.callerNumber(), clock.instant().minus(STICKY_LOOKBACK))
                : null;
        WaitingCall waiting = new WaitingCall(id, call, queue, clock.instant(), handoverLanguage, transcript, sticky,
                externalNumber, confirmExternal);
        List<WaitingCall> line = lines.computeIfAbsent(lineKey(waiting), key -> new CopyOnWriteArrayList<>());
        synchronized (line) {
            int index = line.size();
            if (call.vip()) {
                index = 0;
                while (index < line.size() && line.get(index).call().vip()) {
                    index++;
                }
            }
            line.add(index, waiting);
        }
        byCallerChannel.put(call.channelId(), waiting);
        callExecutor.execute(() -> dispatchLine(line));
        return waiting;
    }

    /** Takes a call out of line and withdraws every offer still ringing for it. */
    public void cancel(WaitingCall waiting) {
        waiting.setState(WaitingCallState.CANCELLED);
        long key = lineKey(waiting);
        List<WaitingCall> line = lines.get(key);
        if (line != null) {
            line.remove(waiting);
            if (key < 0 && line.isEmpty()) {
                lines.remove(key);
            }
        }
        byCallerChannel.remove(waiting.call().channelId(), waiting);
        for (String channelId : List.copyOf(waiting.ringingChannels())) {
            withdraw(offersByChannel.get(channelId));
        }
    }

    /** 1-based place in line among calls still waiting for an operator; 0 when no longer waiting. */
    public int position(WaitingCall waiting) {
        List<WaitingCall> line = lines.get(lineKey(waiting));
        if (line == null) {
            return 0;
        }
        int position = 0;
        for (WaitingCall other : line) {
            if (other.state() == WaitingCallState.WAITING || other.state() == WaitingCallState.RINGING) {
                position++;
            }
            if (other == waiting) {
                return position;
            }
        }
        return 0;
    }

    /** Minutes to say, or null when the estimate is not worth saying. */
    public Integer estimateWaitMinutes(WaitingCall waiting) {
        Deque<Integer> times = handleTimes.get(waiting.queue().id());
        if (times == null) {
            return null;
        }
        double average;
        int samples;
        synchronized (times) {
            samples = times.size();
            average = times.stream().mapToInt(Integer::intValue).average().orElse(0);
        }
        long active = waiting.queue().members().stream()
                .map(m -> operatorPresenceUseCase.find(waiting.call().companyId(), m.userId()).status())
                .filter(s -> s == OperatorStatus.AVAILABLE || s == OperatorStatus.RINGING
                        || s == OperatorStatus.ON_CALL || s == OperatorStatus.WRAP_UP)
                .count();
        return WaitTimeEstimator.estimateMinutes(position(waiting), average, samples, (int) active);
    }

    /** Whether anyone in the queue is signed in and not on a break — if not, waiting is pointless. */
    public boolean anyMemberSignedIn(CallQueue queue) {
        return queue.members().stream()
                .map(m -> operatorPresenceUseCase.find(queue.companyId(), m.userId()).status())
                .anyMatch(s -> s != OperatorStatus.OFFLINE && s != OperatorStatus.BREAK);
    }

    public int lineLength(long queueId) {
        List<WaitingCall> line = lines.get(queueId);
        return line == null ? 0 : (int) line.stream()
                .filter(w -> w.state() == WaitingCallState.WAITING || w.state() == WaitingCallState.RINGING)
                .count();
    }

    public WaitingCall findByCallerChannel(String channelId) {
        return byCallerChannel.get(channelId);
    }

    /** Every call waiting in or connected through {@code queueId}, for the wallboard. */
    public List<WaitingCallView> findLive(long queueId) {
        List<WaitingCall> line = lines.get(queueId);
        if (line == null) {
            return List.of();
        }
        Instant now = clock.instant();
        List<WaitingCallView> views = new ArrayList<>();
        for (WaitingCall waiting : line) {
            views.add(new WaitingCallView(queueId, waiting.call().channelId(), waiting.call().callerNumber(),
                    position(waiting), waiting.waitedSeconds(now), waiting.state(), waiting.call().vip(),
                    waiting.connectedUserId()));
        }
        return views;
    }

    /** An operator of {@code queue} who could take a call right now; null when nobody can. */
    public Long findFreeOperator(CallQueue queue) {
        List<Long> ordered = OperatorSelector.order(queue.strategy(), queue.members(),
                userId -> operatorPresenceUseCase.find(queue.companyId(), userId),
                userId -> onShift(queue.companyId(), userId) && reachable(queue.companyId(), userId),
                null, Long.MAX_VALUE, lastOfferedUser.get(queue.id()), random);
        return ordered.isEmpty() ? null : ordered.get(0);
    }

    /** A blind transfer: the operator's leg is hung up and the call's thread takes it on to {@code target}. */
    public void requestTransfer(WaitingCall waiting, CallTransferTarget target) {
        waiting.setTransfer(target);
        String operatorChannel = waiting.connectedChannelId();
        if (operatorChannel != null) {
            ariProvider.getObject().hangupChannel(operatorChannel);
        }
    }

    /**
     * An attended transfer completed: {@code channelId} is the call's operator from now on.
     * Returns the previous operator's channel, which the caller of this hangs up.
     */
    public String replaceOperator(WaitingCall waiting, String channelId, Long userId) {
        String previous = waiting.connectedChannelId();
        Long previousUser = waiting.connectedUserId();
        if (previous != null) {
            connectedByOperatorChannel.remove(previous);
        }
        if (previousUser != null) {
            operatorPresenceUseCase.markCallEnded(previousUser, waiting.queue().wrapUpSec());
        }
        connectedByOperatorChannel.put(channelId, waiting);
        waiting.connected(channelId, userId, clock.instant());
        waiting.call().setOperator(channelId, userId);
        if (userId != null) {
            operatorPresenceUseCase.markOnCall(userId, waiting.call().channelId());
        }
        return previous;
    }

    @Scheduled(fixedDelay = 1000)
    public void dispatch() {
        for (List<WaitingCall> line : lines.values()) {
            try {
                dispatchLine(line);
            } catch (Exception e) {
                log.warn("Queue dispatch failed: {}", e.getMessage());
            }
        }
    }

    private void dispatchLine(List<WaitingCall> line) {
        for (WaitingCall waiting : line) {
            waiting.lock().lock();
            try {
                if (waiting.state() != WaitingCallState.WAITING || waiting.call().isGone()) {
                    continue;
                }
                if (waiting.externalNumber() != null) {
                    if (!waiting.externalTried()) {
                        offerExternal(waiting);
                    }
                    continue;
                }
                List<Long> candidates = candidates(waiting);
                if (waiting.queue().strategy() == CallQueueStrategy.RING_ALL) {
                    candidates.forEach(userId -> offerTo(waiting, userId));
                } else {
                    for (Long userId : candidates) {
                        if (offerTo(waiting, userId)) {
                            break;
                        }
                    }
                }
            } finally {
                waiting.lock().unlock();
            }
        }
    }

    private List<Long> candidates(WaitingCall waiting) {
        CallQueue queue = waiting.queue();
        long companyId = waiting.call().companyId();
        long waited = waiting.waitedSeconds(clock.instant());
        Predicate<Long> eligible = userId -> onShift(companyId, userId) && reachable(companyId, userId);
        List<Long> ordered = OperatorSelector.order(queue.strategy(), queue.members(),
                userId -> operatorPresenceUseCase.find(companyId, userId), eligible, waiting.call().language(), waited,
                lastOfferedUser.get(queue.id()), random);
        Long sticky = waiting.stickyUserId();
        if (sticky != null && waited < queue.stickySeconds() && ordered.contains(sticky)) {
            return List.of(sticky);
        }
        return ordered;
    }

    private boolean offerTo(WaitingCall waiting, long userId) {
        long companyId = waiting.call().companyId();
        OperatorSipAccount account = cachedAccount(companyId, userId);
        if (account == null || !operatorPresenceUseCase.claimForRinging(userId, waiting.call().channelId())) {
            return false;
        }
        boolean online = isOnline(account.endpointName());
        if (!online && account.forwardNumber() == null) {
            operatorPresenceUseCase.releaseRinging(userId);
            return false;
        }
        Offer offer = new Offer(UUID.randomUUID().toString(), waiting, userId, !online);
        offersById.put(offer.id, offer);
        try {
            AriService ari = ariProvider.getObject();
            String channelId = online
                    ? ari.originateToStasis(ari.buildOperatorDialString(account.endpointName()), appArgs(waiting, offer),
                    waiting.queue().ringTimeoutSec(), callerIdOf(waiting))
                    : ari.originateExternalToStasis(companyId, account.forwardNumber(), appArgs(waiting, offer),
                    waiting.queue().ringTimeoutSec());
            registerOffer(waiting, offer, channelId);
            lastOfferedUser.put(waiting.queue().id(), userId);
            callEventUseCase.record(companyId, waiting.call().callAttemptId(), CallEventType.OPERATOR_RINGING,
                    "userId", userId, "channelId", channelId, "forwarded", !online);
            contactCenterEventUseCase.publishToUser(userId, ContactCenterEventType.CALL_OFFER, offerPayload(waiting));
            return true;
        } catch (Exception e) {
            log.warn("[{}] could not ring operator {}: {}", waiting.call().channelId(), userId, e.getMessage());
            offersById.remove(offer.id);
            operatorPresenceUseCase.releaseRinging(userId);
            return false;
        }
    }

    private void offerExternal(WaitingCall waiting) {
        waiting.setExternalTried(true);
        Offer offer = new Offer(UUID.randomUUID().toString(), waiting, null, waiting.confirmExternal());
        offersById.put(offer.id, offer);
        try {
            String channelId = ariProvider.getObject().originateExternalToStasis(waiting.call().companyId(),
                    waiting.externalNumber(), appArgs(waiting, offer), waiting.queue().ringTimeoutSec());
            registerOffer(waiting, offer, channelId);
            callEventUseCase.record(waiting.call().companyId(), waiting.call().callAttemptId(), CallEventType.OPERATOR_RINGING,
                    "number", waiting.externalNumber(), "channelId", channelId);
        } catch (Exception e) {
            log.warn("[{}] could not forward to the external number: {}", waiting.call().channelId(), e.getMessage());
            offersById.remove(offer.id);
        }
    }

    private void registerOffer(WaitingCall waiting, Offer offer, String channelId) {
        offer.channelId = channelId;
        offersByChannel.put(channelId, offer);
        waiting.ringingChannels().add(channelId);
        if (waiting.state() == WaitingCallState.WAITING) {
            waiting.setState(WaitingCallState.RINGING);
        }
    }

    /** An operator's leg entered the application: they picked up. */
    public void onOperatorAnswered(String offerId, String channelId) {
        Offer offer = offersById.get(offerId);
        AriService ari = ariProvider.getObject();
        if (offer == null) {
            ari.hangupChannel(channelId);
            return;
        }
        offer.channelId = channelId;
        WaitingCall waiting = offer.call;
        waiting.lock().lock();
        try {
            if (waiting.state() != WaitingCallState.RINGING || waiting.call().isGone()) {
                withdraw(offer);
                return;
            }
            waiting.setState(WaitingCallState.CONNECTING);
        } finally {
            waiting.lock().unlock();
        }
        for (String other : List.copyOf(waiting.ringingChannels())) {
            if (!other.equals(channelId)) {
                withdraw(offersByChannel.get(other));
            }
        }
        callExecutor.execute(() -> connect(waiting, offer, channelId));
    }

    private void connect(WaitingCall waiting, Offer offer, String channelId) {
        AriService ari = ariProvider.getObject();
        FlowCall call = waiting.call();
        long companyId = call.companyId();
        boolean onCall = false;
        try {
            ari.answerChannel(channelId);
            if (offer.confirm && !confirmed(ari, waiting, channelId)) {
                forget(offer);
                ari.hangupChannel(channelId);
                if (offer.userId != null) {
                    operatorPresenceUseCase.markMissed(offer.userId);
                }
                resumeWaiting(waiting);
                return;
            }
            int waited = (int) waiting.waitedSeconds(clock.instant());
            short[] whisper = prompter.speech(handoverText(waiting, waited), waiting.handoverLanguage());
            if (whisper.length > 0) {
                ari.playToChannel(channelId, whisper);
                callEventUseCase.record(companyId, call.callAttemptId(), CallEventType.WHISPER,
                        "userId", offer.userId, "seconds", whisper.length / 8000);
            }
            if (call.isGone()) {
                forget(offer);
                ari.hangupChannel(channelId);
                if (offer.userId != null) {
                    operatorPresenceUseCase.releaseRinging(offer.userId);
                }
                return;
            }
            call.endpoint().flushPlayback();
            ari.addToBridge(call.bridgeId(), channelId);
            forget(offer);
            ari.recordOperatorLeg(call.channelId(), channelId);
            connectedByOperatorChannel.put(channelId, waiting);
            waiting.connected(channelId, offer.userId, clock.instant());
            call.setOperator(channelId, offer.userId);
            call.markServed();
            call.setMissedReason(null);
            if (offer.userId != null) {
                operatorPresenceUseCase.markOnCall(offer.userId, call.channelId());
                onCall = true;
                if (!waiting.adHoc() || call.queueId() == null) {
                    callAttemptUseCase.markOperatorAnswered(call.callAttemptId(), offer.userId, waited);
                }
                contactCenterEventUseCase.publishToUser(offer.userId, ContactCenterEventType.CALL_CONNECTED, offerPayload(waiting));
            }
            callEventUseCase.record(companyId, call.callAttemptId(), CallEventType.OPERATOR_ANSWERED,
                    "userId", offer.userId, "waitSeconds", waited, "channelId", channelId);
            if (call.callerNumber() != null) {
                missedCallUseCase.resolveByPhone(companyId, call.callerNumber(), MissedCallStatus.RESOLVED, offer.userId);
            }
            waiting.setState(WaitingCallState.CONNECTED);
        } catch (Exception e) {
            log.warn("[{}] joining operator leg {} failed: {}", call.channelId(), channelId, e.getMessage());
            forget(offer);
            connectedByOperatorChannel.remove(channelId);
            ari.hangupChannel(channelId);
            if (offer.userId != null) {
                if (onCall) {
                    operatorPresenceUseCase.markCallEnded(offer.userId, 0);
                } else {
                    operatorPresenceUseCase.releaseRinging(offer.userId);
                }
            }
            resumeWaiting(waiting);
        }
    }

    /** A forwarded leg must press 1 before it gets the caller — otherwise a mobile's voicemail would take it. */
    private boolean confirmed(AriService ari, WaitingCall waiting, String channelId) {
        CompletableFuture<Character> key = new CompletableFuture<>();
        pendingConfirms.put(channelId, key);
        try {
            String language = waiting.handoverLanguage();
            ari.playToChannel(channelId, prompter.speech(PbxPhrases.forwardConfirm(language), language));
            Character pressed = key.get(CONFIRM_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            return pressed != null && pressed == '1';
        } catch (TimeoutException | ExecutionException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            pendingConfirms.remove(channelId);
        }
    }

    /** A digit on a leg this dispatcher is waiting on; true when it was consumed here. */
    public boolean onDigit(String channelId, char digit) {
        CompletableFuture<Character> key = pendingConfirms.get(channelId);
        if (key == null) {
            return false;
        }
        key.complete(digit);
        return true;
    }

    /** Any channel ended; the ones that were offers or connected operators matter here. */
    public void onChannelGone(String channelId, Integer cause) {
        CompletableFuture<Character> confirm = pendingConfirms.remove(channelId);
        if (confirm != null) {
            confirm.complete(null);
        }
        Offer offer = offersByChannel.remove(channelId);
        if (offer != null) {
            offersById.remove(offer.id);
            WaitingCall waiting = offer.call;
            waiting.ringingChannels().remove(channelId);
            if (offer.userId != null) {
                operatorPresenceUseCase.markMissed(offer.userId);
                contactCenterEventUseCase.publishToUser(offer.userId, ContactCenterEventType.CALL_ENDED, offerPayload(waiting));
            }
            callEventUseCase.record(waiting.call().companyId(), waiting.call().callAttemptId(), CallEventType.OPERATOR_MISSED,
                    "userId", offer.userId, "cause", cause);
            waiting.lock().lock();
            try {
                if (waiting.state() == WaitingCallState.RINGING && waiting.ringingChannels().isEmpty()) {
                    waiting.setState(WaitingCallState.WAITING);
                }
            } finally {
                waiting.lock().unlock();
            }
            return;
        }
        WaitingCall waiting = connectedByOperatorChannel.remove(channelId);
        if (waiting == null) {
            return;
        }
        Long userId = waiting.connectedUserId();
        if (waiting.connectedAt() != null && !waiting.adHoc()) {
            int talked = (int) Duration.between(waiting.connectedAt(), clock.instant()).toSeconds();
            recordHandleTime(waiting.queue().id(), talked + waiting.queue().wrapUpSec());
        }
        if (userId != null) {
            operatorPresenceUseCase.markCallEnded(userId, waiting.queue().wrapUpSec());
            contactCenterEventUseCase.publishToUser(userId, ContactCenterEventType.CALL_ENDED, offerPayload(waiting));
        }
        waiting.call().setOperator(null, userId);
        if (waiting.state() == WaitingCallState.CONNECTED || waiting.state() == WaitingCallState.CONNECTING) {
            waiting.setState(WaitingCallState.OPERATOR_LEFT);
        }
    }

    private void resumeWaiting(WaitingCall waiting) {
        waiting.lock().lock();
        try {
            if (waiting.state() == WaitingCallState.CONNECTING) {
                waiting.setState(waiting.ringingChannels().isEmpty() ? WaitingCallState.WAITING : WaitingCallState.RINGING);
            }
        } finally {
            waiting.lock().unlock();
        }
    }

    /** Stops an offer that is still ringing: the operator is free again, not at fault. */
    private void withdraw(Offer offer) {
        if (offer == null) {
            return;
        }
        forget(offer);
        if (offer.userId != null) {
            operatorPresenceUseCase.releaseRinging(offer.userId);
            contactCenterEventUseCase.publishToUser(offer.userId, ContactCenterEventType.CALL_ENDED, offerPayload(offer.call));
        }
        if (offer.channelId != null) {
            ariProvider.getObject().hangupChannel(offer.channelId);
        }
    }

    private void forget(Offer offer) {
        offersById.remove(offer.id);
        if (offer.channelId != null) {
            offersByChannel.remove(offer.channelId);
            offer.call.ringingChannels().remove(offer.channelId);
        }
    }

    private void recordHandleTime(long queueId, int seconds) {
        Deque<Integer> times = handleTimes.computeIfAbsent(queueId, id -> new ArrayDeque<>());
        synchronized (times) {
            times.addLast(seconds);
            while (times.size() > HANDLE_TIME_SAMPLES) {
                times.removeFirst();
            }
        }
    }

    private boolean onShift(long companyId, long userId) {
        Cached<List<ScheduleSlot>> cached = shiftCache.get(userId);
        if (cached == null || !cached.fresh(CACHE_TTL_MS)) {
            List<ScheduleSlot> slots;
            try {
                slots = profileScheduleUseCase.findByUserId(userId);
            } catch (Exception e) {
                slots = List.of();
            }
            cached = new Cached<>(slots, System.currentTimeMillis());
            shiftCache.put(userId, cached);
        }
        List<ScheduleSlot> slots = cached.value();
        if (slots.isEmpty()) {
            return true;
        }
        ZonedDateTime now = clock.instant().atZone(businessScheduleUseCase.findZone(companyId, null));
        LocalTime time = now.toLocalTime();
        return slots.stream().anyMatch(slot -> slot.dayOfWeek() == now.getDayOfWeek()
                && !time.isBefore(slot.startTime()) && time.isBefore(slot.endTime()));
    }

    private boolean reachable(long companyId, long userId) {
        OperatorSipAccount account = cachedAccount(companyId, userId);
        return account != null && (account.forwardNumber() != null || isOnline(account.endpointName()));
    }

    private OperatorSipAccount cachedAccount(long companyId, long userId) {
        Cached<OperatorSipAccount> cached = accountCache.get(userId);
        if (cached == null || !cached.fresh(CACHE_TTL_MS)) {
            cached = new Cached<>(operatorAccountUseCase.findSipAccount(companyId, userId), System.currentTimeMillis());
            accountCache.put(userId, cached);
        }
        OperatorSipAccount account = cached.value();
        return account != null && account.companyId() == companyId ? account : null;
    }

    private boolean isOnline(String endpointName) {
        Cached<Boolean> cached = endpointStates.get(endpointName);
        if (cached == null || !cached.fresh(ENDPOINT_STATE_TTL_MS)) {
            cached = new Cached<>(ariProvider.getObject().isEndpointOnline(endpointName), System.currentTimeMillis());
            endpointStates.put(endpointName, cached);
        }
        return cached.value();
    }

    private String handoverText(WaitingCall waiting, int waitedSeconds) {
        FlowCall call = waiting.call();
        return PbxPhrases.operatorWhisper(waiting.handoverLanguage(), waiting.adHoc() ? null : waiting.queue().name(),
                call.callerNumber(), call.clientName(), call.vip(), waitedSeconds, String.join(", ", call.choices()));
    }

    private CallOffer offerPayload(WaitingCall waiting) {
        FlowCall call = waiting.call();
        int waited = (int) waiting.waitedSeconds(clock.instant());
        return new CallOffer(call.channelId(), call.callAttemptId(), call.callerNumber(), call.clientName(),
                waiting.adHoc() ? null : waiting.queue().name(), waited, call.language(), call.vip(),
                handoverText(waiting, waited), waiting.transcript());
    }

    private static String appArgs(WaitingCall waiting, Offer offer) {
        return "operator," + waiting.call().bridgeId() + "," + waiting.call().channelId() + "," + offer.id;
    }

    /** What the operator's phone shows: the client's name if the CRM knew it, and the caller's number. */
    private static String callerIdOf(WaitingCall waiting) {
        FlowCall call = waiting.call();
        String name = call.clientName() != null ? call.clientName()
                : waiting.adHoc() ? "Kiruvchi" : waiting.queue().name();
        String number = call.callerNumber() != null ? call.callerNumber().replaceAll("[^0-9+]", "") : "";
        return "\"" + (name == null ? "" : name.replaceAll("[\"<>]", "")) + "\" <" + number + ">";
    }

    private static long lineKey(WaitingCall waiting) {
        return waiting.adHoc() ? -waiting.id() : waiting.queue().id();
    }

    /** Presence of every member, for the wallboard's operator counts. */
    public List<OperatorPresence> findMemberPresence(CallQueue queue) {
        return queue.members().stream().map(m -> operatorPresenceUseCase.find(queue.companyId(), m.userId())).toList();
    }
}
