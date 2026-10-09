package uz.murodjon.robotcallv2.agent.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.ari.AriService;
import uz.murodjon.robotcallv2.agent.flow.FlowCall;
import uz.murodjon.robotcallv2.callqueue.application.port.input.CallQueueUseCase;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallEventUseCase;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallTransferTarget;
import uz.murodjon.robotcallv2.callrecord.domain.enums.CallEventType;
import uz.murodjon.robotcallv2.callrecord.domain.enums.TransferTargetType;
import uz.murodjon.robotcallv2.operator.application.port.input.OperatorAccountUseCase;
import uz.murodjon.robotcallv2.operator.domain.entity.OperatorSipAccount;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;
import uz.murodjon.robotcallv2.user.application.port.input.UserUseCase;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What an operator does to the call they are on (docs/INBOUND-PBX-PLAN.md §7.4): hold,
 * blind transfer (to a queue, a colleague or a number — the caller's own thread takes it
 * there once the operator's leg is gone), and attended transfer: the caller holds while
 * the operator speaks to the colleague first, then completes or cancels.
 */
@Component
public class OperatorCallControl {

    private static final Logger log = LoggerFactory.getLogger(OperatorCallControl.class);
    private static final int CONSULT_RING_SEC = 30;

    private final ObjectProvider<AriService> ariProvider;
    private final CallQueueDispatcher dispatcher;
    private final CallEventUseCase callEventUseCase;
    private final OperatorAccountUseCase operatorAccountUseCase;
    private final CallQueueUseCase callQueueUseCase;
    private final UserUseCase userUseCase;

    /** An attended transfer in progress: the operator and the colleague in a bridge of their own. */
    private static final class Consultation {
        private final String bridgeId;
        private final String operatorChannelId;
        private final Long targetUserId;
        private final String target;
        /** The colleague's leg while it rings; {@link #targetChannelId} once they answer. */
        private volatile String ringingChannelId;
        private volatile String targetChannelId;

        private Consultation(String bridgeId, String operatorChannelId, Long targetUserId, String target) {
            this.bridgeId = bridgeId;
            this.operatorChannelId = operatorChannelId;
            this.targetUserId = targetUserId;
            this.target = target;
        }

        private String colleagueChannelId() {
            return targetChannelId != null ? targetChannelId : ringingChannelId;
        }
    }

    /** Caller channel → its consultation. */
    private final Map<String, Consultation> consultations = new ConcurrentHashMap<>();

    public OperatorCallControl(ObjectProvider<AriService> ariProvider, CallQueueDispatcher dispatcher,
                               CallEventUseCase callEventUseCase, OperatorAccountUseCase operatorAccountUseCase, CallQueueUseCase callQueueUseCase,
                               UserUseCase userUseCase) {
        this.ariProvider = ariProvider;
        this.dispatcher = dispatcher;
        this.callEventUseCase = callEventUseCase;
        this.operatorAccountUseCase = operatorAccountUseCase;
        this.callQueueUseCase = callQueueUseCase;
        this.userUseCase = userUseCase;
    }

    public void hold(long companyId, String callerChannelId) {
        WaitingCall waiting = requireConnected(companyId, callerChannelId);
        FlowCall call = waiting.call();
        ariProvider.getObject().removeFromBridge(call.bridgeId(), waiting.connectedChannelId());
        call.setOnHold(true);
        callEventUseCase.record(companyId, call.callAttemptId(), CallEventType.HOLD, "userId", waiting.connectedUserId());
    }

    public void unhold(long companyId, String callerChannelId) {
        WaitingCall waiting = requireConnected(companyId, callerChannelId);
        FlowCall call = waiting.call();
        call.setOnHold(false);
        call.endpoint().flushPlayback();
        ariProvider.getObject().addToBridge(call.bridgeId(), waiting.connectedChannelId());
        callEventUseCase.record(companyId, call.callAttemptId(), CallEventType.UNHOLD, "userId", waiting.connectedUserId());
    }

    /**
     * Blind transfer. A call with an operator: their leg is hung up and the call goes on to
     * {@code target}. A call with no operator yet (the AI is talking) can only be sent to a queue.
     */
    public void transfer(long companyId, String callerChannelId, CallTransferTarget target) {
        requireTarget(companyId, target);
        WaitingCall waiting = dispatcher.findByCallerChannel(callerChannelId);
        if (waiting != null && waiting.call().companyId() == companyId && waiting.connectedChannelId() != null) {
            callEventUseCase.record(companyId, waiting.call().callAttemptId(), CallEventType.TRANSFER,
                    "from", waiting.connectedUserId(), "to", describe(target), "mode", "BLIND");
            dispatcher.requestTransfer(waiting, target);
            return;
        }
        if (target.type() == TransferTargetType.QUEUE) {
            ariProvider.getObject().transferCallToQueue(companyId, callerChannelId, target.targetId());
            return;
        }
        throw new ConflictException(ErrorCode.CALL_NOT_WITH_OPERATOR, callerChannelId);
    }

    /** Puts the caller on hold and rings {@code target} for the operator to speak to first. */
    public void startConsult(long companyId, String callerChannelId, CallTransferTarget target) {
        if (target.type() == TransferTargetType.QUEUE) {
            throw new ValidationException(ErrorCode.CALL_CONSULT_TARGET_INVALID);
        }
        requireTarget(companyId, target);
        WaitingCall waiting = requireConnected(companyId, callerChannelId);
        if (consultations.containsKey(callerChannelId)) {
            throw new ConflictException(ErrorCode.CALL_CONSULT_ALREADY_ACTIVE, callerChannelId);
        }
        AriService ari = ariProvider.getObject();
        FlowCall call = waiting.call();
        String operatorChannel = waiting.connectedChannelId();
        ari.removeFromBridge(call.bridgeId(), operatorChannel);
        call.setOnHold(true);
        Consultation consultation = null;
        try {
            consultation = new Consultation(ari.createBridge(), operatorChannel,
                    target.type() == TransferTargetType.USER ? target.targetId() : null, describe(target));
            consultations.put(callerChannelId, consultation);
            ari.addToBridge(consultation.bridgeId, operatorChannel);
            if (target.type() == TransferTargetType.USER) {
                OperatorSipAccount account = operatorAccountUseCase.findSipAccount(companyId, target.targetId());
                consultation.ringingChannelId = ari.originateToStasis(ari.buildOperatorDialString(account.endpointName()),
                        "consult," + callerChannelId, CONSULT_RING_SEC, "\"Konsultatsiya\" <consult>");
            } else {
                consultation.ringingChannelId = ari.originateExternalToStasis(companyId, target.number(),
                        "consult," + callerChannelId, CONSULT_RING_SEC);
            }
        } catch (RuntimeException e) {
            restore(callerChannelId, consultation, waiting);
            throw e;
        }
        callEventUseCase.record(companyId, call.callAttemptId(), CallEventType.TRANSFER,
                "from", waiting.connectedUserId(), "to", consultation.target, "mode", "CONSULT");
    }

    public void onConsultAnswered(String callerChannelId, String channelId) {
        Consultation consultation = consultations.get(callerChannelId);
        AriService ari = ariProvider.getObject();
        if (consultation == null) {
            ari.hangupChannel(channelId);
            return;
        }
        try {
            ari.answerChannel(channelId);
            ari.addToBridge(consultation.bridgeId, channelId);
            consultation.targetChannelId = channelId;
        } catch (Exception e) {
            log.warn("Consultation for {} could not be joined: {}", callerChannelId, e.getMessage());
            ari.hangupChannel(channelId);
        }
    }

    /** The colleague takes over: they join the caller, the operator drops off. */
    public void completeConsult(long companyId, String callerChannelId) {
        WaitingCall waiting = requireConnected(companyId, callerChannelId);
        Consultation consultation = consultations.get(callerChannelId);
        if (consultation == null || consultation.targetChannelId == null) {
            throw new ConflictException(ErrorCode.CALL_CONSULT_NOT_ACTIVE, callerChannelId);
        }
        consultations.remove(callerChannelId);
        AriService ari = ariProvider.getObject();
        FlowCall call = waiting.call();
        Long fromUserId = waiting.connectedUserId();
        ari.removeFromBridge(consultation.bridgeId, consultation.targetChannelId);
        ari.removeFromBridge(consultation.bridgeId, consultation.operatorChannelId);
        String previous = dispatcher.replaceOperator(waiting, consultation.targetChannelId, consultation.targetUserId);
        call.setOnHold(false);
        call.endpoint().flushPlayback();
        ari.addToBridge(call.bridgeId(), consultation.targetChannelId);
        ari.recordOperatorLeg(call.channelId(), consultation.targetChannelId);
        if (previous != null) {
            ari.hangupChannel(previous);
        }
        ari.destroyBridge(consultation.bridgeId);
        callEventUseCase.record(companyId, call.callAttemptId(), CallEventType.TRANSFER,
                "from", fromUserId, "to", consultation.target, "mode", "ATTENDED");
    }

    /** The colleague is not taking it (or is still ringing): they are hung up and the operator is back with the caller. */
    public void cancelConsult(long companyId, String callerChannelId) {
        WaitingCall waiting = requireConnected(companyId, callerChannelId);
        Consultation consultation = consultations.get(callerChannelId);
        if (consultation == null) {
            throw new ConflictException(ErrorCode.CALL_CONSULT_NOT_ACTIVE, callerChannelId);
        }
        restore(callerChannelId, consultation, waiting);
        String colleague = consultation.colleagueChannelId();
        if (colleague != null) {
            ariProvider.getObject().hangupChannel(colleague);
        }
    }

    /** A colleague who hung up (or never answered) mid-consultation hands the caller back to the operator. */
    public void onChannelGone(String channelId) {
        for (Map.Entry<String, Consultation> entry : consultations.entrySet()) {
            Consultation consultation = entry.getValue();
            if (channelId.equals(consultation.operatorChannelId)) {
                consultations.remove(entry.getKey());
                String colleague = consultation.colleagueChannelId();
                if (colleague != null) {
                    ariProvider.getObject().hangupChannel(colleague);
                }
                ariProvider.getObject().destroyBridge(consultation.bridgeId);
                return;
            }
            if (channelId.equals(consultation.colleagueChannelId())) {
                WaitingCall waiting = dispatcher.findByCallerChannel(entry.getKey());
                if (waiting != null) {
                    restore(entry.getKey(), consultation, waiting);
                }
                return;
            }
        }
    }

    /**
     * Puts the operator back with the caller. {@code consultation} is null when setting one up
     * failed before it existed. Destroying the consultation bridge frees the operator's leg
     * without hanging it up, so this works however far the setup got.
     */
    private void restore(String callerChannelId, Consultation consultation, WaitingCall waiting) {
        AriService ari = ariProvider.getObject();
        FlowCall call = waiting.call();
        if (consultation != null) {
            consultations.remove(callerChannelId);
            ari.destroyBridge(consultation.bridgeId);
        }
        call.setOnHold(false);
        call.endpoint().flushPlayback();
        ari.addToBridge(call.bridgeId(), waiting.connectedChannelId());
    }

    private WaitingCall requireConnected(long companyId, String callerChannelId) {
        WaitingCall waiting = dispatcher.findByCallerChannel(callerChannelId);
        if (waiting == null || waiting.call().companyId() != companyId || waiting.connectedChannelId() == null) {
            throw new ConflictException(ErrorCode.CALL_NOT_WITH_OPERATOR, callerChannelId);
        }
        return waiting;
    }

    private void requireTarget(long companyId, CallTransferTarget target) {
        if (target == null || target.type() == null) {
            throw new ValidationException(ErrorCode.CALL_TRANSFER_TYPE_REQUIRED);
        }
        switch (target.type()) {
            case QUEUE -> {
                if (target.targetId() == null) {
                    throw new ValidationException(ErrorCode.CALL_TRANSFER_TARGET_ID_REQUIRED);
                }
                callQueueUseCase.requireQueue(companyId, target.targetId());
            }
            case USER -> {
                if (target.targetId() == null) {
                    throw new ValidationException(ErrorCode.CALL_TRANSFER_TARGET_ID_REQUIRED);
                }
                userUseCase.requireUser(companyId, target.targetId());
                if (operatorAccountUseCase.findSipAccount(companyId, target.targetId()) == null) {
                    throw new ConflictException(ErrorCode.OPERATOR_NOT_REACHABLE, target.targetId());
                }
            }
            case EXTERNAL -> PhoneNumbers.require(target.number());
        }
    }

    private static String describe(CallTransferTarget target) {
        return target.type() == TransferTargetType.EXTERNAL ? target.number()
                : target.type().name().toLowerCase() + " " + target.targetId();
    }
}
