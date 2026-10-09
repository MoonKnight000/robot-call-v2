package uz.murodjon.robotcallv2.agent.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.ari.AriService;
import uz.murodjon.robotcallv2.agent.ari.HangupCause;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignTargetUseCase;
import uz.murodjon.robotcallv2.operator.application.port.input.OperatorAccountUseCase;
import uz.murodjon.robotcallv2.operator.application.port.input.OperatorPresenceUseCase;
import uz.murodjon.robotcallv2.operator.domain.entity.OperatorSipAccount;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.util.PhoneMasker;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * PROGRESSIVE campaign calls (docs/OPERATOR-DIALER-ANALYTICS-PLAN.md §1.3): one client dialled
 * for one operator the dialer has already taken off the queue (RINGING).
 *
 * <p>The client is dialled first. When they answer, the operator's phone rings and an
 * answering-machine watch starts on the client's side at the same moment — the person who
 * picked up is not kept waiting for a verdict. A machine found before the operator answers
 * ends the client's call and gives the operator back; one found after it ends the joined
 * call. Either way the target is settled as {@code VOICEMAIL} and retried by the campaign's
 * policy, and the operator owes no wrap-up.
 *
 * <p>Every other answered call is settled by the operator's wrap-up, as a preview card is.
 * A client nobody answered is settled from the hangup cause; one the operator's phone rang
 * out on, or who hung up before the operator joined, is an abandoned call.
 */
@Component
public class ProgressiveDialer {

    private static final Logger log = LoggerFactory.getLogger(ProgressiveDialer.class);
    private static final int CLIENT_RING_SEC = 45;
    /** The client is already on the line, in silence: the operator gets less time than a queue offer. */
    private static final int OPERATOR_RING_SEC = 15;
    private static final int WRAP_UP_SEC = 10;

    private final ObjectProvider<AriService> ariProvider;
    private final OperatorAccountUseCase operatorAccountUseCase;
    private final OperatorPresenceUseCase operatorPresenceUseCase;
    private final CallAttemptUseCase callAttemptUseCase;
    private final CampaignTargetUseCase campaignTargetUseCase;
    private final ExecutorService callExecutor;

    /** One client dialled for one operator. */
    private static final class ProgressiveCall {
        private final String token = UUID.randomUUID().toString();
        private final long companyId;
        private final long userId;
        private final long targetId;
        private final String phone;
        private final String language;
        private final String operatorEndpoint;
        private final AtomicBoolean settled = new AtomicBoolean();
        private volatile String clientChannelId;
        private volatile String clientChannelName;
        private volatile String operatorChannelId;
        private volatile Instant clientAnsweredAt;
        private boolean operatorAnswered;
        private boolean machine;
        private boolean clientGone;

        private ProgressiveCall(long companyId, long userId, long targetId, String phone, String language,
                                String operatorEndpoint) {
            this.companyId = companyId;
            this.userId = userId;
            this.targetId = targetId;
            this.phone = phone;
            this.language = language;
            this.operatorEndpoint = operatorEndpoint;
        }
    }

    private final Map<String, ProgressiveCall> calls = new ConcurrentHashMap<>();
    private final Map<String, ProgressiveCall> byChannel = new ConcurrentHashMap<>();
    /** Operator channel → user, while the operator is on such a call. */
    private final Map<String, Long> busyOperators = new ConcurrentHashMap<>();

    public ProgressiveDialer(ObjectProvider<AriService> ariProvider, OperatorAccountUseCase operatorAccountUseCase,
                             OperatorPresenceUseCase operatorPresenceUseCase, CallAttemptUseCase callAttemptUseCase,
                             CampaignTargetUseCase campaignTargetUseCase, ExecutorService callExecutor) {
        this.ariProvider = ariProvider;
        this.operatorAccountUseCase = operatorAccountUseCase;
        this.operatorPresenceUseCase = operatorPresenceUseCase;
        this.callAttemptUseCase = callAttemptUseCase;
        this.campaignTargetUseCase = campaignTargetUseCase;
        this.callExecutor = callExecutor;
    }

    /**
     * Dials the target's client for {@code operatorUserId}, whom the caller has already
     * claimed for ringing. On a failure nothing is left behind here: the caller gives the
     * operator and the target back.
     *
     * @return the client's channel
     */
    public String dial(long companyId, long operatorUserId, long targetId, String phone, String language) {
        OperatorSipAccount account = operatorAccountUseCase.findSipAccount(companyId, operatorUserId);
        if (account == null) {
            throw new ConflictException(ErrorCode.OPERATOR_NOT_REACHABLE, operatorUserId);
        }
        ProgressiveCall call = new ProgressiveCall(companyId, operatorUserId, targetId, phone, language,
                account.endpointName());
        calls.put(call.token, call);
        try {
            String channelId = ariProvider.getObject().originateClientCall(companyId, phone,
                    "progressive-client," + call.token, CLIENT_RING_SEC, language, targetId);
            call.clientChannelId = channelId;
            byChannel.put(channelId, call);
            return channelId;
        } catch (RuntimeException e) {
            calls.remove(call.token);
            throw e;
        }
    }

    /** The client answered: ring the operator, and listen for a machine meanwhile. */
    public void onClientAnswered(String token, String channelId, String channelName) {
        ProgressiveCall call = calls.get(token);
        AriService ari = ariProvider.getObject();
        if (call == null) {
            ari.hangupChannel(channelId);
            return;
        }
        call.clientChannelName = channelName;
        call.clientAnsweredAt = Instant.now();
        ari.watchForAnsweringMachine(channelId, () -> onMachine(call));
        try {
            String operatorChannelId = ari.originateToStasis(ari.buildOperatorDialString(call.operatorEndpoint),
                    "progressive-operator," + token, OPERATOR_RING_SEC,
                    "\"Kampaniya\" <" + call.phone.replaceAll("\\D", "") + ">");
            call.operatorChannelId = operatorChannelId;
            byChannel.put(operatorChannelId, call);
        } catch (RuntimeException e) {
            log.warn("Progressive call to {}: operator {} could not be rung: {}", PhoneMasker.mask(call.phone),
                    call.userId, e.getMessage());
            operatorPresenceUseCase.releaseRinging(call.userId);
            abandon(call);
        }
    }

    /** The operator picked up: one recorded bridge with the client, like any other operator call. */
    public void onOperatorAnswered(String token, String channelId) {
        ProgressiveCall call = calls.get(token);
        AriService ari = ariProvider.getObject();
        boolean join;
        if (call == null) {
            join = false;
        } else {
            synchronized (call) {
                join = !call.machine && !call.clientGone;
                call.operatorAnswered = join;
            }
        }
        if (!join) {
            ari.hangupChannel(channelId);
            return;
        }
        busyOperators.put(channelId, call.userId);
        operatorPresenceUseCase.markOnCall(call.userId, channelId);
        callExecutor.execute(() -> {
            try {
                long attemptId = ari.bridgeWithRecording(call.clientChannelId, call.clientChannelName, call.companyId,
                        call.phone, channelId, call.language);
                callAttemptUseCase.markOperatorAnswered(attemptId, call.userId, secondsSinceClientAnswered(call));
            } catch (Exception e) {
                log.warn("Joining {} with operator {} failed: {}", PhoneMasker.mask(call.phone), call.userId,
                        e.getMessage());
                ari.hangupChannel(call.clientChannelId);
                ari.hangupChannel(channelId);
            }
        });
    }

    public void onChannelGone(String channelId, Integer cause) {
        Long busyUserId = busyOperators.remove(channelId);
        ProgressiveCall call = byChannel.remove(channelId);
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
        } else if (channelId.equals(call.operatorChannelId)) {
            onOperatorGone(call);
        }
    }

    private void onClientGone(ProgressiveCall call, Integer cause) {
        calls.remove(call.token);
        AriService ari = ariProvider.getObject();
        ari.stopWatchingForAnsweringMachine(call.clientChannelId);
        synchronized (call) {
            call.clientGone = true;
            if (call.machine || call.operatorAnswered) {
                return; // settled by the machine, or left to the operator's wrap-up
            }
        }
        String operatorChannelId = call.operatorChannelId;
        if (operatorChannelId != null) {
            byChannel.remove(operatorChannelId);
            ari.hangupChannel(operatorChannelId);
        }
        operatorPresenceUseCase.releaseRinging(call.userId);
        if (call.clientAnsweredAt == null) {
            // The attempt row is closed from the same cause when the channel is destroyed.
            Disposition disposition = HangupCause.toDisposition(cause);
            settle(call, disposition != null ? disposition : Disposition.NO_ANSWER);
            return;
        }
        log.info("Progressive call to {}: the client hung up before operator {} answered",
                PhoneMasker.mask(call.phone), call.userId);
        callAttemptUseCase.finishUnansweredAttempt(call.clientChannelId, Disposition.HUNG_UP);
        markAbandoned(call);
        settle(call, Disposition.HUNG_UP);
    }

    /** Before answering — rang out or refused — unless the call is already over for another reason. */
    private void onOperatorGone(ProgressiveCall call) {
        synchronized (call) {
            if (call.operatorAnswered || call.machine || call.clientGone) {
                return;
            }
        }
        log.info("Progressive call to {}: operator {} did not answer — the client is let go",
                PhoneMasker.mask(call.phone), call.userId);
        operatorPresenceUseCase.markMissed(call.userId);
        abandon(call);
    }

    /** The client answered and no operator will join: the client's call ends and the target is retried. */
    private void abandon(ProgressiveCall call) {
        calls.remove(call.token);
        byChannel.remove(call.clientChannelId);
        AriService ari = ariProvider.getObject();
        ari.stopWatchingForAnsweringMachine(call.clientChannelId);
        callAttemptUseCase.finishUnansweredAttempt(call.clientChannelId, Disposition.FAILED);
        markAbandoned(call);
        settle(call, Disposition.FAILED);
        ari.hangupChannel(call.clientChannelId);
    }

    /** On a call thread, once: the client's side is a recording, not a person. */
    private void onMachine(ProgressiveCall call) {
        boolean operatorAnswered;
        synchronized (call) {
            if (call.machine || call.clientGone) {
                return;
            }
            call.machine = true;
            operatorAnswered = call.operatorAnswered;
        }
        log.info("Progressive call to {}: answering machine{}", PhoneMasker.mask(call.phone),
                operatorAnswered ? " — ending the operator's call" : "");
        settle(call, Disposition.VOICEMAIL);
        AriService ari = ariProvider.getObject();
        if (operatorAnswered) {
            // The operator's leg is linked to the client's and follows it.
            ari.hangupChannelWithDisposition(call.clientChannelId, Disposition.VOICEMAIL);
            return;
        }
        callAttemptUseCase.finishUnansweredAttempt(call.clientChannelId, Disposition.VOICEMAIL);
        String operatorChannelId = call.operatorChannelId;
        if (operatorChannelId != null) {
            byChannel.remove(operatorChannelId);
            ari.hangupChannel(operatorChannelId);
        }
        operatorPresenceUseCase.releaseRinging(call.userId);
        ari.hangupChannel(call.clientChannelId);
    }

    private static boolean isMachine(ProgressiveCall call) {
        synchronized (call) {
            return call.machine;
        }
    }

    private void settle(ProgressiveCall call, Disposition disposition) {
        if (!call.settled.compareAndSet(false, true)) {
            return;
        }
        try {
            campaignTargetUseCase.applyOutcome(call.companyId, call.targetId, disposition);
        } catch (Exception e) {
            log.warn("Target {} could not be settled as {}: {}", call.targetId, disposition, e.getMessage());
        }
    }

    private void markAbandoned(ProgressiveCall call) {
        long attemptId = callAttemptUseCase.findAttemptIdByChannel(call.clientChannelId);
        if (attemptId != 0) {
            callAttemptUseCase.markAbandoned(attemptId, secondsSinceClientAnswered(call));
        }
    }

    private static int secondsSinceClientAnswered(ProgressiveCall call) {
        Instant answeredAt = call.clientAnsweredAt;
        return answeredAt == null ? 0 : (int) Duration.between(answeredAt, Instant.now()).toSeconds();
    }
}
