package uz.murodjon.robotcallv2.agent.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.ari.AriService;
import uz.murodjon.robotcallv2.agent.flow.FlowPrompter;
import uz.murodjon.robotcallv2.agent.flow.PbxPhrases;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallAttempt;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignTargetUseCase;
import uz.murodjon.robotcallv2.company.domain.enums.Language;
import uz.murodjon.robotcallv2.missedcall.application.port.input.MissedCallUseCase;
import uz.murodjon.robotcallv2.missedcall.domain.enums.MissedCallStatus;
import uz.murodjon.robotcallv2.operator.application.port.input.OperatorAccountUseCase;
import uz.murodjon.robotcallv2.operator.application.port.input.OperatorPresenceUseCase;
import uz.murodjon.robotcallv2.operator.domain.entity.OperatorSipAccount;
import uz.murodjon.robotcallv2.operator.domain.enums.OperatorStatus;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.util.PhoneMasker;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;
import uz.murodjon.robotcallv2.user.application.port.input.UserUseCase;
import uz.murodjon.robotcallv2.user.domain.entity.User;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Calls an operator places — the ones that start at the operator's own phone
 * (docs/INBOUND-PBX-PLAN.md §8.2, §12):
 * <ul>
 *   <li>a callback from the missed-call list, by hand or automatically: the operator's
 *       phone rings first, and only once they answer is the client dialled — nobody is left
 *       holding for an operator who is not there;</li>
 *   <li>a colleague's extension dialled from the softphone;</li>
 *   <li>an outside number dialled from the softphone, which goes out through the
 *       company's trunk and is recorded like any other call.</li>
 * </ul>
 * The operator's phone reaches only the application ({@code [from-operators]}); the trunk
 * is dialled here, on the operator's company account, so a leaked SIP password cannot
 * dial out on its own.
 */
@Component
public class OperatorFirstDialer {

    private static final Logger log = LoggerFactory.getLogger(OperatorFirstDialer.class);
    private static final int OPERATOR_RING_SEC = 30;
    private static final int CLIENT_RING_SEC = 45;
    private static final int COLLEAGUE_RING_SEC = 30;
    private static final int WRAP_UP_SEC = 10;
    /** Fewer digits than this dialled from a softphone is an extension, more is an outside number. */
    private static final int OUTSIDE_NUMBER_MIN_DIGITS = 7;
    private static final Pattern ENDPOINT_OF_CHANNEL = Pattern.compile("PJSIP/(.+)-[0-9a-fA-F]+");

    private final ObjectProvider<AriService> ariProvider;
    private final OperatorAccountUseCase operatorAccountUseCase;
    private final OperatorPresenceUseCase operatorPresenceUseCase;
    private final MissedCallUseCase missedCallUseCase;
    private final UserUseCase userUseCase;
    private final CallAttemptUseCase callAttemptUseCase;
    private final CampaignTargetUseCase campaignTargetUseCase;
    private final FlowPrompter prompter;
    private final ExecutorService callExecutor;

    /** A call whose operator side exists and whose client side is being placed. */
    private static final class PendingCall {
        private final String token;
        private final long companyId;
        private final long userId;
        private final String phone;
        private final Long missedCallId;
        private final boolean automatic;
        /** The campaign target a preview card dials, or {@code CallAttempt.NO_TARGET}. */
        private final long targetId;
        private final Instant createdAt = Instant.now();
        private volatile String operatorChannelId;
        private volatile String clientChannelId;
        private volatile boolean operatorAnswered;

        private PendingCall(String token, long companyId, long userId, String phone, Long missedCallId, boolean automatic,
                            long targetId) {
            this.token = token;
            this.companyId = companyId;
            this.userId = userId;
            this.phone = phone;
            this.missedCallId = missedCallId;
            this.automatic = automatic;
            this.targetId = targetId;
        }
    }

    private final Map<String, PendingCall> pending = new ConcurrentHashMap<>();
    private final Map<String, PendingCall> byChannel = new ConcurrentHashMap<>();
    /** Operator channel → user, while the operator is on such a call. */
    private final Map<String, Long> busyOperators = new ConcurrentHashMap<>();
    /** Caller channel of an extension call → the bridge waiting for the colleague to answer. */
    private final Map<String, String> colleagueBridges = new ConcurrentHashMap<>();

    public OperatorFirstDialer(ObjectProvider<AriService> ariProvider, OperatorAccountUseCase operatorAccountUseCase,
                               OperatorPresenceUseCase operatorPresenceUseCase, MissedCallUseCase missedCallUseCase, UserUseCase userUseCase,
                               CallAttemptUseCase callAttemptUseCase, CampaignTargetUseCase campaignTargetUseCase,
                               FlowPrompter prompter, ExecutorService callExecutor) {
        this.ariProvider = ariProvider;
        this.operatorAccountUseCase = operatorAccountUseCase;
        this.operatorPresenceUseCase = operatorPresenceUseCase;
        this.missedCallUseCase = missedCallUseCase;
        this.userUseCase = userUseCase;
        this.callAttemptUseCase = callAttemptUseCase;
        this.campaignTargetUseCase = campaignTargetUseCase;
        this.prompter = prompter;
        this.callExecutor = callExecutor;
    }

    /**
     * Rings the operator; once they answer, dials {@code phone}.
     *
     * @param automatic an unattended callback — the operator is claimed like for a queue
     *                  call, so the queue does not ring them at the same time
     * @return the channel ringing the operator
     */
    public String dial(long companyId, long operatorUserId, String phone, Long missedCallId, boolean automatic) {
        return ringOperator(companyId, operatorUserId, phone, missedCallId, automatic, CallAttempt.NO_TARGET);
    }

    /**
     * A preview card: rings the operator, then dials the target's number. The client leg is
     * booked on {@code targetId}; a client who never answers settles the target as NO_ANSWER,
     * one who does is settled by the operator's wrap-up.
     *
     * @return the channel ringing the operator
     */
    public String dialTarget(long companyId, long operatorUserId, String phone, long targetId) {
        return ringOperator(companyId, operatorUserId, phone, null, false, targetId);
    }

    private String ringOperator(long companyId, long operatorUserId, String phone, Long missedCallId, boolean automatic,
                                long targetId) {
        OperatorSipAccount account = operatorAccountUseCase.findSipAccount(companyId, operatorUserId);
        if (account == null) {
            throw new ConflictException(ErrorCode.OPERATOR_NOT_REACHABLE, operatorUserId);
        }
        String number = PhoneNumbers.require(phone);
        OperatorStatus status = operatorPresenceUseCase.find(companyId, operatorUserId).status();
        if (status == OperatorStatus.RINGING || status == OperatorStatus.ON_CALL) {
            throw new ConflictException(ErrorCode.OPERATOR_ON_CALL);
        }
        if (automatic && !operatorPresenceUseCase.claimForRinging(operatorUserId, "callback")) {
            throw new ConflictException(ErrorCode.OPERATOR_ON_CALL);
        }
        PendingCall call = new PendingCall(UUID.randomUUID().toString(), companyId, operatorUserId, number,
                missedCallId, automatic, targetId);
        pending.put(call.token, call);
        try {
            String channelId = ariProvider.getObject().originateToStasis(ariProvider.getObject().buildOperatorDialString(account.endpointName()),
                    "callback," + call.token, OPERATOR_RING_SEC, "\"Qayta qo'ng'iroq\" <" + digits(number) + ">");
            call.operatorChannelId = channelId;
            byChannel.put(channelId, call);
            return channelId;
        } catch (RuntimeException e) {
            pending.remove(call.token);
            if (automatic) {
                operatorPresenceUseCase.releaseRinging(operatorUserId);
            }
            throw e;
        }
    }

    /** The operator picked up a callback: tell them who, then dial the client. */
    public void onOperatorAnswered(String token, String channelId) {
        PendingCall call = pending.get(token);
        AriService ari = ariProvider.getObject();
        if (call == null) {
            ari.hangupChannel(channelId);
            return;
        }
        call.operatorAnswered = true;
        busyOperators.put(channelId, call.userId);
        operatorPresenceUseCase.markOnCall(call.userId, channelId);
        callExecutor.execute(() -> {
            try {
                ari.answerChannel(channelId);
                String language = Language.DEFAULT.code();
                ari.playToChannel(channelId, prompter.speech(PbxPhrases.callingClient(call.phone, language), language));
                placeClientLeg(call);
            } catch (Exception e) {
                log.warn("Callback to {} could not be placed: {}", PhoneMasker.mask(call.phone), e.getMessage());
                clientUnreachable(call, Disposition.FAILED);
            }
        });
    }

    /** The client answered: one recorded bridge with the operator, like any other call. */
    public void onClientAnswered(String token, String channelId, String channelName) {
        PendingCall call = pending.remove(token);
        AriService ari = ariProvider.getObject();
        if (call == null || call.operatorChannelId == null) {
            ari.hangupChannel(channelId);
            return;
        }
        byChannel.remove(channelId);
        byChannel.remove(call.operatorChannelId);
        callExecutor.execute(() -> {
            try {
                long attemptId = ari.bridgeWithRecording(channelId, channelName, call.companyId, call.phone,
                        call.operatorChannelId, Language.DEFAULT.code());
                callAttemptUseCase.markOperatorAnswered(attemptId, call.userId, 0);
                if (call.missedCallId != null || call.automatic) {
                    missedCallUseCase.resolveByPhone(call.companyId, call.phone, MissedCallStatus.CALLED_BACK, call.userId);
                }
            } catch (Exception e) {
                log.warn("Joining {} with operator {} failed: {}", PhoneMasker.mask(call.phone), call.userId, e.getMessage());
                ari.hangupChannel(channelId);
                ari.hangupChannel(call.operatorChannelId);
            }
        });
    }

    /** An operator dialled something from their softphone. */
    public void onOperatorDialled(String channelId, String channelName, String extension) {
        AriService ari = ariProvider.getObject();
        OperatorSipAccount caller = operatorAccountUseCase.findSipAccountByEndpoint(endpointOf(channelName));
        String dialled = extension == null ? "" : extension.replaceAll("\\D", "");
        if (caller == null || dialled.isEmpty()) {
            ari.hangupChannel(channelId);
            return;
        }
        User colleague = userUseCase.findBySipExtension(caller.companyId(), dialled);
        if (colleague != null) {
            ringColleague(ari, caller, colleague, channelId);
            return;
        }
        if (dialled.length() >= OUTSIDE_NUMBER_MIN_DIGITS) {
            PendingCall call = new PendingCall(UUID.randomUUID().toString(), caller.companyId(), caller.userId(),
                    dialled, null, false, CallAttempt.NO_TARGET);
            call.operatorChannelId = channelId;
            call.operatorAnswered = true;
            pending.put(call.token, call);
            byChannel.put(channelId, call);
            busyOperators.put(channelId, caller.userId());
            operatorPresenceUseCase.markOnCall(caller.userId(), channelId);
            ari.ringChannel(channelId);
            callExecutor.execute(() -> {
                try {
                    placeClientLeg(call);
                } catch (Exception e) {
                    log.warn("Outside call to {} from operator {} failed: {}", PhoneMasker.mask(dialled), caller.userId(),
                            e.getMessage());
                    clientUnreachable(call, Disposition.FAILED);
                }
            });
            return;
        }
        ari.hangupChannelBusy(channelId);
    }

    /** The colleague picked up an extension call. */
    public void onColleagueAnswered(String callerChannelId, String channelId) {
        AriService ari = ariProvider.getObject();
        String bridgeId = colleagueBridges.remove(callerChannelId);
        if (bridgeId == null) {
            ari.hangupChannel(channelId);
            return;
        }
        try {
            ari.answerChannel(callerChannelId);
            ari.answerChannel(channelId);
            ari.addToBridge(bridgeId, callerChannelId);
            ari.addToBridge(bridgeId, channelId);
            ari.linkLegs(callerChannelId, channelId, bridgeId);
        } catch (Exception e) {
            log.warn("Extension call {} could not be joined: {}", callerChannelId, e.getMessage());
            ari.hangupChannel(callerChannelId);
            ari.hangupChannel(channelId);
            ari.destroyBridge(bridgeId);
        }
    }

    public void onChannelGone(String channelId) {
        Long userId = busyOperators.remove(channelId);
        if (userId != null) {
            operatorPresenceUseCase.markCallEnded(userId, WRAP_UP_SEC);
        }
        String bridge = colleagueBridges.remove(channelId);
        if (bridge != null) {
            ariProvider.getObject().destroyBridge(bridge);
        }
        PendingCall call = byChannel.remove(channelId);
        if (call == null) {
            return;
        }
        if (channelId.equals(call.operatorChannelId)) {
            pending.remove(call.token);
            if (call.clientChannelId != null) {
                byChannel.remove(call.clientChannelId);
                ariProvider.getObject().hangupChannel(call.clientChannelId);
            }
            if (!call.operatorAnswered && call.automatic) {
                operatorPresenceUseCase.markMissed(call.userId);
            }
            reopen(call);
        } else if (channelId.equals(call.clientChannelId) && pending.remove(call.token) != null) {
            clientUnreachable(call, Disposition.NO_ANSWER);
        }
    }

    private void placeClientLeg(PendingCall call) {
        // Booked as a call the moment it is dialled: an unanswered callback is an attempt too.
        String clientChannel = ariProvider.getObject().originateClientCall(call.companyId, call.phone,
                "callback-client," + call.token, CLIENT_RING_SEC, Language.DEFAULT.code(), call.targetId);
        call.clientChannelId = clientChannel;
        byChannel.put(clientChannel, call);
    }

    /**
     * The client never answered: the operator hears so, the missed call goes back on the
     * list, and a preview card's target is settled as {@code why} — it has no wrap-up to wait for.
     */
    private void clientUnreachable(PendingCall call, Disposition why) {
        pending.remove(call.token);
        reopen(call);
        if (call.targetId != CallAttempt.NO_TARGET) {
            try {
                campaignTargetUseCase.applyOutcome(call.companyId, call.targetId, why);
            } catch (Exception e) {
                log.warn("Target {} could not be settled as {}: {}", call.targetId, why, e.getMessage());
            }
        }
        String operatorChannel = call.operatorChannelId;
        if (operatorChannel == null) {
            return;
        }
        byChannel.remove(operatorChannel);
        callExecutor.execute(() -> {
            AriService ari = ariProvider.getObject();
            try {
                String language = Language.DEFAULT.code();
                ari.answerChannel(operatorChannel);
                ari.playToChannel(operatorChannel, prompter.speech(PbxPhrases.clientDidNotAnswer(language), language));
            } catch (Exception e) {
                log.debug("Could not tell operator {} the client did not answer: {}", call.userId, e.getMessage());
            } finally {
                ari.hangupChannel(operatorChannel);
            }
        });
    }

    private void ringColleague(AriService ari, OperatorSipAccount caller, User colleague, String callerChannelId) {
        OperatorSipAccount target = operatorAccountUseCase.findSipAccount(caller.companyId(), colleague.id());
        if (target == null) {
            ari.hangupChannelBusy(callerChannelId);
            return;
        }
        String bridgeId = ari.createBridge();
        colleagueBridges.put(callerChannelId, bridgeId);
        ari.ringChannel(callerChannelId);
        try {
            ari.originateToStasis(ari.buildOperatorDialString(target.endpointName()), "internal-answer," + callerChannelId,
                    COLLEAGUE_RING_SEC, "\"" + caller.endpointName() + "\" <" + caller.endpointName() + ">");
        } catch (RuntimeException e) {
            colleagueBridges.remove(callerChannelId);
            ari.destroyBridge(bridgeId);
            ari.hangupChannelBusy(callerChannelId);
        }
    }

    private void reopen(PendingCall call) {
        if (call.missedCallId != null) {
            missedCallUseCase.markCallbackFailed(call.companyId, call.missedCallId);
        }
    }

    static String endpointOf(String channelName) {
        if (channelName == null) {
            return null;
        }
        Matcher matcher = ENDPOINT_OF_CHANNEL.matcher(channelName);
        return matcher.matches() ? matcher.group(1) : null;
    }

    private static String digits(String number) {
        return number.replaceAll("\\D", "");
    }
}
