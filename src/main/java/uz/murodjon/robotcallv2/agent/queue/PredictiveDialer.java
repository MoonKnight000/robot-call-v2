package uz.murodjon.robotcallv2.agent.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.ari.AriService;
import uz.murodjon.robotcallv2.agent.ari.HangupCause;
import uz.murodjon.robotcallv2.callqueue.application.port.input.CallQueueUseCase;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueue;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueueMember;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignTargetUseCase;
import uz.murodjon.robotcallv2.dialer.application.dto.OutboundCall;
import uz.murodjon.robotcallv2.dialer.application.port.input.DialerSlotUseCase;
import uz.murodjon.robotcallv2.dialer.application.port.input.OutboundCallTrackingUseCase;
import uz.murodjon.robotcallv2.dialer.domain.entity.DialPacingStats;
import uz.murodjon.robotcallv2.operator.application.port.input.OperatorAccountUseCase;
import uz.murodjon.robotcallv2.operator.application.port.input.OperatorPresenceUseCase;
import uz.murodjon.robotcallv2.operator.domain.entity.OperatorSipAccount;
import uz.murodjon.robotcallv2.operator.domain.enums.OperatorStatus;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.util.PhoneMasker;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * PREDICTIVE campaign calls (docs/OPERATOR-DIALER-ANALYTICS-PLAN.md §1.4): clients are dialled
 * ahead of the operators, and an operator is looked for only once a client answers.
 *
 * <p>An answered client gets the first free operator of the campaign's queue, rung for 15
 * seconds, with an answering-machine watch on the client's side meanwhile — as PROGRESSIVE.
 * When no operator is free, or the one rung does not answer, the client is not hung up on: the
 * campaign's AI agent takes the call ({@code call_attempt.ai_overflow}), and from then on it is
 * an ordinary outbound AI call that teardown settles. Every other outcome is settled here —
 * a machine as {@code VOICEMAIL}, a client nobody answered from the hangup cause, one who hung
 * up while an operator was being rung as an abandoned {@code HUNG_UP} — or by the operator's
 * wrap-up once they talked.
 *
 * <p>The pace is set from what the campaign's calls did over the last half hour
 * ({@link #findStats}); the window lives in memory, so it starts empty after a restart and the
 * dialer goes back to one call per operator until it fills again.
 */
@Component
public class PredictiveDialer {

    private static final Logger log = LoggerFactory.getLogger(PredictiveDialer.class);
    private static final int CLIENT_RING_SEC = 30;
    /** The client is already on the line, in silence: past this the AI takes them. */
    private static final int OPERATOR_RING_SEC = 15;
    private static final int WRAP_UP_SEC = 10;
    private static final Duration STATS_WINDOW = Duration.ofMinutes(30);
    /** What the operator's presence shows as the call being rung for them. */
    private static final String RINGING_CHANNEL = "predictive";

    private final ObjectProvider<AriService> ariProvider;
    private final CallQueueUseCase callQueueUseCase;
    private final OperatorAccountUseCase operatorAccountUseCase;
    private final OperatorPresenceUseCase operatorPresenceUseCase;
    private final CallAttemptUseCase callAttemptUseCase;
    private final CampaignTargetUseCase campaignTargetUseCase;
    private final OutboundCallTrackingUseCase outboundCallTrackingUseCase;
    private final DialerSlotUseCase dialerSlotUseCase;
    private final ExecutorService callExecutor;
    private final Clock clock;

    /** One client dialled for whichever operator is free when they answer. */
    private static final class PredictiveCall {
        private final String token = UUID.randomUUID().toString();
        private final long callQueueId;
        /** What the AI agent needs to take the client over; prepared before dialling. Also the call's target. */
        private final OutboundCall aiCall;
        private final AtomicBoolean settled = new AtomicBoolean();
        /** Whether the call has counted in the pacing window — answered or not — yet. */
        private final AtomicBoolean counted = new AtomicBoolean();
        private volatile String clientChannelId;
        private volatile String clientChannelName;
        private volatile Instant clientAnsweredAt;
        private Long operatorUserId;
        private String operatorChannelId;
        private boolean operatorAnswered;
        private boolean machine;
        private boolean clientGone;
        private boolean handedToAi;

        private PredictiveCall(long callQueueId, OutboundCall aiCall) {
            this.callQueueId = callQueueId;
            this.aiCall = aiCall;
        }
    }

    /** A decided call in the pacing window. */
    private record Sample(Instant at, boolean answered, boolean aiOverflow) {
    }

    /** Token → call, from dialling until the client's channel ends. */
    private final Map<String, PredictiveCall> calls = new ConcurrentHashMap<>();
    private final Map<String, PredictiveCall> byChannel = new ConcurrentHashMap<>();
    /** Operator channel → user, while the operator is on such a call. */
    private final Map<String, Long> busyOperators = new ConcurrentHashMap<>();
    private final Map<Long, Deque<Sample>> samplesByCampaign = new ConcurrentHashMap<>();

    public PredictiveDialer(ObjectProvider<AriService> ariProvider, CallQueueUseCase callQueueUseCase,
                            OperatorAccountUseCase operatorAccountUseCase,
                            OperatorPresenceUseCase operatorPresenceUseCase, CallAttemptUseCase callAttemptUseCase,
                            CampaignTargetUseCase campaignTargetUseCase,
                            OutboundCallTrackingUseCase outboundCallTrackingUseCase,
                            DialerSlotUseCase dialerSlotUseCase, ExecutorService callExecutor, Clock clock) {
        this.ariProvider = ariProvider;
        this.callQueueUseCase = callQueueUseCase;
        this.operatorAccountUseCase = operatorAccountUseCase;
        this.operatorPresenceUseCase = operatorPresenceUseCase;
        this.callAttemptUseCase = callAttemptUseCase;
        this.campaignTargetUseCase = campaignTargetUseCase;
        this.outboundCallTrackingUseCase = outboundCallTrackingUseCase;
        this.dialerSlotUseCase = dialerSlotUseCase;
        this.callExecutor = callExecutor;
        this.clock = clock;
    }

    /**
     * Dials {@code aiCall}'s client. The caller has reserved the dialer slot; from a successful
     * dial on it is released here, or by teardown once the AI took the call. On a failure nothing
     * is left behind: the caller gives the slot and the target back.
     *
     * @return the client's channel
     */
    public String dial(long callQueueId, OutboundCall aiCall) {
        PredictiveCall call = new PredictiveCall(callQueueId, aiCall);
        calls.put(call.token, call);
        try {
            String channelId = ariProvider.getObject().originateClientCall(aiCall.companyId(), aiCall.phone(),
                    "predictive-client," + call.token, CLIENT_RING_SEC, aiCall.language(), aiCall.targetId());
            call.clientChannelId = channelId;
            byChannel.put(channelId, call);
            outboundCallTrackingUseCase.trackOperatorTarget(aiCall.targetId());
            return channelId;
        } catch (RuntimeException e) {
            calls.remove(call.token);
            throw e;
        }
    }

    /** The campaign's calls over the last half hour that are decided — answered or not. */
    public DialPacingStats findStats(long campaignId) {
        Deque<Sample> samples = samplesByCampaign.get(campaignId);
        if (samples == null) {
            return DialPacingStats.EMPTY;
        }
        Instant since = clock.instant().minus(STATS_WINDOW);
        samples.removeIf(sample -> sample.at().isBefore(since));
        int dialled = 0;
        int answered = 0;
        int overflowed = 0;
        for (Sample sample : samples) {
            dialled++;
            answered += sample.answered() ? 1 : 0;
            overflowed += sample.aiOverflow() ? 1 : 0;
        }
        return new DialPacingStats(dialled, answered, overflowed);
    }

    /** Clients of the campaign dialled here whose fate is not known yet: ringing, or waiting for an operator. */
    public int countUndecided(long campaignId) {
        int undecided = 0;
        for (PredictiveCall call : calls.values()) {
            if (call.aiCall.campaignId() == campaignId && !call.counted.get()) {
                undecided++;
            }
        }
        return undecided;
    }

    /** The client answered: listen for a machine, and look for an operator meanwhile. */
    public void onClientAnswered(String token, String channelId, String channelName) {
        PredictiveCall call = calls.get(token);
        AriService ari = ariProvider.getObject();
        if (call == null) {
            ari.hangupChannel(channelId);
            return;
        }
        call.clientChannelName = channelName;
        call.clientAnsweredAt = clock.instant();
        ari.watchForAnsweringMachine(channelId, () -> onMachine(call));
        Long userId = claimFreeOperator(call);
        if (userId == null) {
            log.info("Predictive call to {}: no operator free — the AI agent takes it",
                    PhoneMasker.mask(call.aiCall.phone()));
            handToAi(call);
            return;
        }
        synchronized (call) {
            call.operatorUserId = userId; // before the ring: the answer may come back before originate returns
        }
        try {
            OperatorSipAccount account = operatorAccountUseCase.findSipAccount(call.aiCall.companyId(), userId);
            String operatorChannelId = ari.originateToStasis(ari.buildOperatorDialString(account.endpointName()),
                    "predictive-operator," + token, OPERATOR_RING_SEC,
                    "\"Kampaniya\" <" + call.aiCall.phone().replaceAll("\\D", "") + ">");
            synchronized (call) {
                call.operatorChannelId = operatorChannelId;
            }
            byChannel.put(operatorChannelId, call);
        } catch (RuntimeException e) {
            log.warn("Predictive call to {}: operator {} could not be rung: {}", PhoneMasker.mask(call.aiCall.phone()),
                    userId, e.getMessage());
            synchronized (call) {
                call.operatorUserId = null;
            }
            operatorPresenceUseCase.releaseRinging(userId);
            handToAi(call);
        }
    }

    /** The operator picked up: one recorded bridge with the client, like any other operator call. */
    public void onOperatorAnswered(String token, String channelId) {
        PredictiveCall call = calls.get(token);
        AriService ari = ariProvider.getObject();
        boolean join;
        if (call == null) {
            join = false;
        } else {
            synchronized (call) {
                join = !call.machine && !call.clientGone && !call.handedToAi;
                call.operatorAnswered = join;
            }
        }
        if (!join) {
            ari.hangupChannel(channelId);
            return;
        }
        count(call, true, false);
        busyOperators.put(channelId, call.operatorUserId);
        operatorPresenceUseCase.markOnCall(call.operatorUserId, channelId);
        callExecutor.execute(() -> {
            try {
                long attemptId = ari.bridgeWithRecording(call.clientChannelId, call.clientChannelName,
                        call.aiCall.companyId(), call.aiCall.phone(), channelId, call.aiCall.language());
                callAttemptUseCase.markOperatorAnswered(attemptId, call.operatorUserId, secondsSinceClientAnswered(call));
            } catch (Exception e) {
                log.warn("Joining {} with operator {} failed: {}", PhoneMasker.mask(call.aiCall.phone()),
                        call.operatorUserId, e.getMessage());
                ari.hangupChannel(call.clientChannelId);
                ari.hangupChannel(channelId);
            }
        });
    }

    public void onChannelGone(String channelId, Integer cause) {
        Long busyUserId = busyOperators.remove(channelId);
        PredictiveCall call = byChannel.remove(channelId);
        if (busyUserId != null) {
            operatorPresenceUseCase.markCallEnded(busyUserId, WRAP_UP_SEC);
            if (call != null && isMachine(call)) {
                operatorPresenceUseCase.finishWrapUp(busyUserId); // nothing to wrap up on a voicemail
            }
        }
        if (call == null) {
            return;
        }
        if (channelId.equals(call.clientChannelId)) {
            onClientGone(call, cause);
        } else {
            onOperatorGone(call, channelId);
        }
    }

    private void onClientGone(PredictiveCall call, Integer cause) {
        calls.remove(call.token);
        outboundCallTrackingUseCase.untrackOperatorTarget(call.aiCall.targetId());
        AriService ari = ariProvider.getObject();
        ari.stopWatchingForAnsweringMachine(call.clientChannelId);
        String operatorChannelId;
        Long operatorUserId;
        synchronized (call) {
            call.clientGone = true;
            if (call.handedToAi) {
                return; // an AI call now: teardown releases the slot and settles the target
            }
            operatorChannelId = call.operatorAnswered ? null : call.operatorChannelId;
            operatorUserId = call.operatorAnswered ? null : call.operatorUserId;
        }
        dialerSlotUseCase.release(call.aiCall.companyId(), call.aiCall.campaignId());
        if (isMachine(call) || isOperatorAnswered(call)) {
            return; // settled by the machine, or left to the operator's wrap-up
        }
        if (operatorChannelId != null) {
            byChannel.remove(operatorChannelId);
            ari.hangupChannel(operatorChannelId);
        }
        if (operatorUserId != null) {
            operatorPresenceUseCase.releaseRinging(operatorUserId);
        }
        if (call.clientAnsweredAt == null) {
            // The attempt row is closed from the same cause when the channel is destroyed.
            count(call, false, false);
            Disposition disposition = HangupCause.toDisposition(cause);
            settle(call, disposition != null ? disposition : Disposition.NO_ANSWER);
            return;
        }
        log.info("Predictive call to {}: the client hung up while an operator was being found",
                PhoneMasker.mask(call.aiCall.phone()));
        count(call, true, false);
        callAttemptUseCase.finishUnansweredAttempt(call.clientChannelId, Disposition.HUNG_UP);
        markAbandoned(call);
        settle(call, Disposition.HUNG_UP);
    }

    /** The operator's phone rang out or was refused: the AI agent takes the client instead. */
    private void onOperatorGone(PredictiveCall call, String operatorChannelId) {
        Long userId;
        synchronized (call) {
            if (!operatorChannelId.equals(call.operatorChannelId) || call.operatorAnswered || call.machine
                    || call.clientGone || call.handedToAi) {
                return;
            }
            userId = call.operatorUserId;
        }
        log.info("Predictive call to {}: operator {} did not answer — the AI agent takes it",
                PhoneMasker.mask(call.aiCall.phone()), userId);
        operatorPresenceUseCase.markMissed(userId);
        handToAi(call);
    }

    /**
     * No operator for an answered client: the campaign's agent talks to them. The slot, the
     * target and the attempt row are teardown's from here on, as for any outbound AI call.
     */
    private void handToAi(PredictiveCall call) {
        synchronized (call) {
            if (call.machine || call.clientGone || call.operatorAnswered || call.handedToAi) {
                return;
            }
            call.handedToAi = true;
        }
        calls.remove(call.token); // a late operator answer finds no call and is hung up
        AriService ari = ariProvider.getObject();
        ari.stopWatchingForAnsweringMachine(call.clientChannelId);
        count(call, true, true);
        long attemptId = callAttemptUseCase.findAttemptIdByChannel(call.clientChannelId);
        if (attemptId != 0) {
            callAttemptUseCase.markAiOverflow(attemptId, secondsSinceClientAnswered(call));
        }
        // Registered as an outbound call before the operator target is let go, so the expired-claim
        // sweep never sees the target live nowhere.
        ari.startOutboundAiCall(call.clientChannelId, call.clientChannelName, call.aiCall);
        outboundCallTrackingUseCase.untrackOperatorTarget(call.aiCall.targetId());
    }

    /** On a call thread, once: the client's side is a recording, not a person. */
    private void onMachine(PredictiveCall call) {
        boolean operatorAnswered;
        String operatorChannelId;
        Long operatorUserId;
        synchronized (call) {
            if (call.machine || call.clientGone || call.handedToAi) {
                return;
            }
            call.machine = true;
            operatorAnswered = call.operatorAnswered;
            operatorChannelId = call.operatorChannelId;
            operatorUserId = call.operatorUserId;
        }
        log.info("Predictive call to {}: answering machine{}", PhoneMasker.mask(call.aiCall.phone()),
                operatorAnswered ? " — ending the operator's call" : "");
        count(call, false, false);
        settle(call, Disposition.VOICEMAIL);
        AriService ari = ariProvider.getObject();
        if (operatorAnswered) {
            // The operator's leg is linked to the client's and follows it.
            ari.hangupChannelWithDisposition(call.clientChannelId, Disposition.VOICEMAIL);
            return;
        }
        callAttemptUseCase.finishUnansweredAttempt(call.clientChannelId, Disposition.VOICEMAIL);
        if (operatorChannelId != null) {
            byChannel.remove(operatorChannelId);
            ari.hangupChannel(operatorChannelId);
        }
        if (operatorUserId != null) {
            operatorPresenceUseCase.releaseRinging(operatorUserId);
        }
        ari.hangupChannel(call.clientChannelId);
    }

    /** The first member of the queue who is AVAILABLE with a softphone signed in, claimed for ringing. */
    private Long claimFreeOperator(PredictiveCall call) {
        long companyId = call.aiCall.companyId();
        CallQueue queue = callQueueUseCase.findQueue(companyId, call.callQueueId);
        if (queue == null) {
            return null;
        }
        AriService ari = ariProvider.getObject();
        for (CallQueueMember member : queue.members()) {
            if (operatorPresenceUseCase.find(companyId, member.userId()).status() != OperatorStatus.AVAILABLE) {
                continue;
            }
            OperatorSipAccount account = operatorAccountUseCase.findSipAccount(companyId, member.userId());
            if (account != null && ari.isEndpointOnline(account.endpointName())
                    && operatorPresenceUseCase.claimForRinging(member.userId(), RINGING_CHANNEL)) {
                return member.userId();
            }
        }
        return null;
    }

    private void count(PredictiveCall call, boolean answered, boolean aiOverflow) {
        if (call.counted.compareAndSet(false, true)) {
            samplesByCampaign.computeIfAbsent(call.aiCall.campaignId(), id -> new ConcurrentLinkedDeque<>())
                    .add(new Sample(clock.instant(), answered, aiOverflow));
        }
    }

    private static boolean isMachine(PredictiveCall call) {
        synchronized (call) {
            return call.machine;
        }
    }

    private static boolean isOperatorAnswered(PredictiveCall call) {
        synchronized (call) {
            return call.operatorAnswered;
        }
    }

    private void settle(PredictiveCall call, Disposition disposition) {
        if (!call.settled.compareAndSet(false, true)) {
            return;
        }
        try {
            campaignTargetUseCase.applyOutcome(call.aiCall.companyId(), call.aiCall.targetId(), disposition);
        } catch (Exception e) {
            log.warn("Target {} could not be settled as {}: {}", call.aiCall.targetId(), disposition, e.getMessage());
        }
    }

    private void markAbandoned(PredictiveCall call) {
        long attemptId = callAttemptUseCase.findAttemptIdByChannel(call.clientChannelId);
        if (attemptId != 0) {
            callAttemptUseCase.markAbandoned(attemptId, secondsSinceClientAnswered(call));
        }
    }

    private int secondsSinceClientAnswered(PredictiveCall call) {
        Instant answeredAt = call.clientAnsweredAt;
        return answeredAt == null ? 0 : (int) Duration.between(answeredAt, clock.instant()).toSeconds();
    }
}
