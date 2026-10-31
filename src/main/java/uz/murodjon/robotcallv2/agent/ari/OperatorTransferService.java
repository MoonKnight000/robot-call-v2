package uz.murodjon.robotcallv2.agent.ari;

import ch.loway.oss.ari4java.generated.actions.requests.ChannelsOriginatePostRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.dialog.DialogRouter;
import uz.murodjon.robotcallv2.agent.dialog.OperatorSnapshot;
import uz.murodjon.robotcallv2.agent.session.CallSession;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationUseCase;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.operator.application.port.input.ContactCenterEventUseCase;
import uz.murodjon.robotcallv2.operator.application.port.input.OperatorAccountUseCase;
import uz.murodjon.robotcallv2.operator.domain.entity.CallOffer;
import uz.murodjon.robotcallv2.operator.domain.entity.OperatorSipAccount;
import uz.murodjon.robotcallv2.operator.domain.enums.ContactCenterEventType;
import uz.murodjon.robotcallv2.operator.infrastructure.config.OperatorProperties;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An AI call handed to a person: the agent's transfer queue, its transfer number, the
 * deployment's operator endpoint, or a supervisor taking the call over — with a warm-transfer
 * notification carrying what has been said so far.
 */
@Service
class OperatorTransferService {

    private static final Logger log = LoggerFactory.getLogger(OperatorTransferService.class);

    /** How many of the last transcript lines travel with a transfer notification. */
    private static final int HANDOVER_TRANSCRIPT_LINES = 6;

    private final CallSessionRegistry callSessionRegistry;
    private final FlowCallService flowCallService;
    private final AriClient ariClient;
    private final AsteriskProperties asteriskProperties;
    private final OperatorProperties operatorProperties;
    private final NotificationUseCase notificationService;
    private final CallAttemptUseCase callAttemptUseCase;
    private final DialogRouter dialogRouter;
    private final OperatorAcceptance operatorAcceptance;
    private final ContactCenterEventUseCase contactCenterEventUseCase;
    private final OperatorAccountUseCase operatorAccountUseCase;

    OperatorTransferService(CallSessionRegistry callSessionRegistry,
                            FlowCallService flowCallService,
                            AriClient ariClient,
                            AsteriskProperties asteriskProperties,
                            OperatorProperties operatorProperties,
                            NotificationUseCase notificationService,
                            CallAttemptUseCase callAttemptUseCase,
                            DialogRouter dialogRouter,
                            OperatorAcceptance operatorAcceptance,
                            ContactCenterEventUseCase contactCenterEventUseCase,
                            OperatorAccountUseCase operatorAccountUseCase) {
        this.callSessionRegistry = callSessionRegistry;
        this.flowCallService = flowCallService;
        this.ariClient = ariClient;
        this.asteriskProperties = asteriskProperties;
        this.operatorProperties = operatorProperties;
        this.notificationService = notificationService;
        this.callAttemptUseCase = callAttemptUseCase;
        this.dialogRouter = dialogRouter;
        this.operatorAcceptance = operatorAcceptance;
        this.contactCenterEventUseCase = contactCenterEventUseCase;
        this.operatorAccountUseCase = operatorAccountUseCase;
    }

    void transferToOperator(String channelId) {
        transferToOperator(channelId, null);
    }

    /**
     * {@link #transferToOperator(String)}, except that {@code operatorEndpoint} — the PJSIP
     * endpoint ({@code op-<userId>}) of the supervisor taking the call over, already checked
     * to belong to an operator of the call's company — wins over the agent's transfer queue
     * and number when given.
     */
    void transferToOperator(String channelId, String operatorEndpoint) {
        CallSession session = callSessionRegistry.find(channelId);
        if (session == null) {
            log.info("Session not found for {}; hanging up", channelId);
            ariClient.hangup(channelId);
            return;
        }

        AiAgent agent = session.agent();
        boolean toOperator = operatorEndpoint != null && !operatorEndpoint.isBlank();
        // A CALL_TRANSFER stage of the call's flow names its own number, and that is where the
        // flow means this caller to go — ahead of wherever the agent sends its transfers.
        OperatorSnapshot snapshot = dialogRouter.operatorSnapshot(channelId);
        String stageNumber = !toOperator && snapshot != null ? snapshot.transferTo() : null;
        boolean toStageNumber = stageNumber != null && !stageNumber.isBlank();
        // An agent with a transfer queue hands over to the operators' queue — hold music, the
        // caller's position, the whisper — instead of ringing one fixed endpoint.
        Long transferQueueId = !toOperator && !toStageNumber && agent != null ? agent.callBehaviour().transferQueueId() : null;
        if (transferQueueId != null) {
            flowCallService.transferCallToQueue(callAttemptUseCase.findCompanyIdByAttemptId(session.callAttemptId()), channelId, transferQueueId);
            return;
        }
        String targetEndpoint = null;
        if (toOperator) {
            targetEndpoint = asteriskProperties.buildOperatorDialString(operatorEndpoint.trim());
        } else if (toStageNumber) {
            targetEndpoint = "PJSIP/" + stageNumber.trim();
        } else if (agent != null && agent.callBehaviour().transferPhoneNumber() != null && !agent.callBehaviour().transferPhoneNumber().isBlank()) {
            String num = agent.callBehaviour().transferPhoneNumber().trim();
            targetEndpoint = num.startsWith("PJSIP/") || num.startsWith("SIP/") ? num : ("PJSIP/" + num);
        } else if (operatorProperties.enabled() && operatorProperties.endpoint() != null && !operatorProperties.endpoint().isBlank()) {
            targetEndpoint = operatorProperties.endpoint();
        }

        if (targetEndpoint == null) {
            log.info("Operator transfer unavailable for {}; hanging up", channelId);
            ariClient.hangup(channelId);
            return;
        }
        // The CALL_TRANSFER stage's own SIP headers ride on the new leg's INVITE; Asterisk adds a
        // header for each PJSIP_HEADER(add,…) variable set on the channel it originates.
        Map<String, String> sipVariables = new LinkedHashMap<>();
        if (toStageNumber) {
            snapshot.transferHeaders().forEach((name, value) -> sipVariables.put("PJSIP_HEADER(add," + name + ")", value));
        }
        try {
            ChannelsOriginatePostRequest originate = ariClient.requireConnection().channels()
                    .originate(targetEndpoint)
                    .setApp(asteriskProperties.appName())
                    .setAppArgs("operator," + session.bridgeId() + "," + channelId)
                    .setTimeout(operatorProperties.answerTimeoutSec());
            if (!sipVariables.isEmpty()) {
                originate.setVariables(sipVariables);
            }
            originate.execute();
            // A supervisor asked for the call; anyone else may still not take it.
            operatorAcceptance.onOperatorDialled(channelId, !toOperator);
            log.info("Transferring {} to target {} (bridge {})",
                    channelId, targetEndpoint, session.bridgeId());

            long companyId = callAttemptUseCase.findCompanyIdByAttemptId(session.callAttemptId());
            String handoverSummary = buildHandoverSummary(snapshot);

            // A warm transfer: the operator reads who is on the line and what has been said
            // while the phone is still ringing, instead of asking the caller to start over.
            notificationService.notify(companyId,
                    NotificationType.OPERATOR_REQUEST,
                    "Operatorga so'rov", "Qo'ng'iroq " + channelId + " " + targetEndpoint + " ga uzatildi"
                            + handoverSummary, null);

            // Live screen pop on operator's web panel (SSE CALL_OFFER event)
            CallOffer offer = buildCallOffer(channelId, session, snapshot, handoverSummary);
            OperatorSipAccount account = toOperator
                    ? operatorAccountUseCase.findSipAccountByEndpoint(operatorEndpoint.trim())
                    : (operatorProperties.endpoint() != null ? operatorAccountUseCase.findSipAccountByEndpoint(operatorProperties.endpoint().trim()) : null);
            if (account != null) {
                contactCenterEventUseCase.publishToUser(account.userId(), ContactCenterEventType.CALL_OFFER, offer);
            } else {
                contactCenterEventUseCase.publishToCompany(companyId, ContactCenterEventType.CALL_OFFER, offer);
            }
        } catch (Exception e) {
            log.error("Operator transfer failed for {}: {}", channelId, e.getMessage());
            ariClient.hangup(channelId);
        }
    }

    CallOffer buildCallOffer(String channelId, CallSession session, OperatorSnapshot snapshot, String summary) {
        long attemptId = session.callAttemptId();
        String callerNumber = callAttemptUseCase.findPhoneByAttemptId(attemptId);
        String clientName = snapshot != null ? snapshot.clientName() : null;
        String language = (session.agent() != null && session.agent().language() != null)
                ? session.agent().language() : "uz-UZ";
        String transcript = snapshot != null ? snapshot.transcript() : null;
        int waitSeconds = (session.startedAt() != null)
                ? (int) Duration.between(session.startedAt(), Instant.now()).getSeconds()
                : 0;
        return new CallOffer(channelId, attemptId, callerNumber, clientName,
                "Operatorga uzatish", waitSeconds, language, false,
                summary != null && !summary.isBlank() ? summary : "Operatorga so'rov",
                transcript);
    }

    /**
     * The facts and the tail of the conversation, as the operator should see them before
     * picking up; empty when the dialog has nothing to hand over.
     */
    static String buildHandoverSummary(OperatorSnapshot snapshot) {
        if (snapshot == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        appendFact(text, "Mijoz", snapshot.clientName());
        appendFact(text, "Sabab", snapshot.transferReason());
        appendFact(text, "Qarz", snapshot.debtAmount() == null ? null
                : snapshot.debtAmount() + (snapshot.currency() != null ? " " + snapshot.currency() : ""));
        appendFact(text, "Muddat", snapshot.dueDate());
        appendFact(text, "Shartnoma", snapshot.contractNumber());
        appendFact(text, "Bosqich", snapshot.dialogState());
        String transcript = snapshot.transcript();
        if (transcript != null && !transcript.isBlank()) {
            String[] lines = transcript.split("\n");
            int from = Math.max(0, lines.length - HANDOVER_TRANSCRIPT_LINES);
            text.append("\nOxirgi gaplar:");
            for (int i = from; i < lines.length; i++) {
                text.append('\n').append(lines[i]);
            }
        }
        return text.toString();
    }

    private static void appendFact(StringBuilder text, String label, String value) {
        if (value != null && !value.isBlank()) {
            text.append('\n').append(label).append(": ").append(value);
        }
    }
}
