package uz.murodjon.robotcallv2.webhook.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.ari.LiveKitCallService;
import uz.murodjon.robotcallv2.agent.flow.InboundCallGate;
import uz.murodjon.robotcallv2.agent.flow.InboundDecision;
import uz.murodjon.robotcallv2.agent.flow.InboundDecisionKind;
import uz.murodjon.robotcallv2.agent.livekit.LiveKitTelephonyService;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.shared.util.PhoneMasker;
import uz.murodjon.robotcallv2.webhook.application.dto.LiveKitParticipantInfo;
import uz.murodjon.robotcallv2.webhook.application.dto.LiveKitWebhookEvent;
import uz.murodjon.robotcallv2.webhook.application.port.input.LiveKitWebhookUseCase;

import java.io.IOException;

/**
 * Implements {@link LiveKitWebhookUseCase}. The phone legs ({@code kind = SIP}) and the
 * operators who join a transferred call from the browser matter here; LiveKit sends every room
 * and participant event to the one URL.
 */
@Service
public class LiveKitWebhookService implements LiveKitWebhookUseCase {

    private static final Logger log = LoggerFactory.getLogger(LiveKitWebhookService.class);

    private final InboundCallGate inboundCallGate;
    private final LiveKitTelephonyService liveKitTelephonyService;
    private final LiveKitCallService liveKitCallService;
    private final ObjectMapper objectMapper;

    public LiveKitWebhookService(InboundCallGate inboundCallGate,
                                 LiveKitTelephonyService liveKitTelephonyService,
                                 LiveKitCallService liveKitCallService,
                                 ObjectMapper objectMapper) {
        this.inboundCallGate = inboundCallGate;
        this.liveKitTelephonyService = liveKitTelephonyService;
        this.liveKitCallService = liveKitCallService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handleEvent(String authorization, byte[] body) {
        liveKitTelephonyService.verifyWebhook(authorization, body);
        LiveKitWebhookEvent event = parse(body);
        LiveKitParticipantInfo participant = event.participant();
        if (participant == null || event.room() == null || event.room().name() == null) {
            return;
        }
        String roomName = event.room().name();
        if (!participant.isSip()) {
            handleOperator(event.event(), roomName, participant);
            return;
        }
        // The transfer leg the agent rang into an inbound room is not a second caller.
        if ("participant_joined".equals(event.event()) && roomName.startsWith(LiveKitTelephonyService.INBOUND_ROOM_PREFIX)
                && !LiveKitTelephonyService.OPERATOR_IDENTITY.equals(participant.identity())) {
            admitInboundCaller(roomName, participant);
        } else if ("participant_left".equals(event.event()) && LiveKitTelephonyService.isLiveKitRoom(roomName)) {
            liveKitCallService.onPhoneLegLeft(roomName, participant.identity(), participant.disconnectReason());
        }
    }

    /** An operator joining or leaving, from the browser, the room of a call transferred to them. */
    private void handleOperator(String eventName, String roomName, LiveKitParticipantInfo participant) {
        String identity = participant.identity();
        if (LiveKitTelephonyService.BROWSER_IDENTITY.equals(identity)
                && roomName.startsWith(LiveKitTelephonyService.BROWSER_ROOM_PREFIX)) {
            if ("participant_joined".equals(eventName)) {
                liveKitCallService.onBrowserCallerJoined(roomName);
            } else if ("participant_left".equals(eventName)) {
                liveKitCallService.onPhoneLegLeft(roomName, identity, participant.disconnectReason());
            }
            return;
        }
        if (identity == null || !identity.startsWith(LiveKitTelephonyService.OPERATOR_WEB_PREFIX)
                || !LiveKitTelephonyService.isLiveKitRoom(roomName)) {
            return;
        }
        if ("participant_joined".equals(eventName)) {
            liveKitCallService.onOperatorJoined(roomName, identity);
        } else if ("participant_left".equals(eventName)) {
            liveKitCallService.onOperatorLeft(roomName, identity);
        }
    }

    /**
     * The dispatch rule has already put the caller in a room; the inbound rules decide here
     * whether anyone answers: the route's AI agent, or its call flow (IVR). Anything else is refused
     * like a rejected caller.
     */
    private void admitInboundCaller(String roomName, LiveKitParticipantInfo participant) {
        String did = participant.findAttribute("sip.trunkPhoneNumber");
        String caller = participant.findAttribute("sip.phoneNumber");
        InboundDecision decision = inboundCallGate.decide(did, caller);
        if (decision.kind() == InboundDecisionKind.AGENT && decision.route() != null) {
            log.info("LiveKit inbound call {} -> {} in room {} routed to agent {}", PhoneMasker.mask(caller), did,
                    roomName, decision.route().aiAgentId());
            liveKitCallService.startInboundCall(roomName, decision.route(), caller, did);
            return;
        }
        if (decision.kind() == InboundDecisionKind.FLOW && decision.route() != null) {
            log.info("LiveKit inbound call {} -> {} in room {} runs call flow {}", PhoneMasker.mask(caller), did,
                    roomName, decision.route().callFlowId());
            liveKitCallService.startInboundFlow(roomName, decision, caller, did);
            return;
        }
        log.info("LiveKit inbound call {} -> {} in room {} refused ({}), hanging up",
                PhoneMasker.mask(caller), did, roomName, decision.kind());
        liveKitTelephonyService.deleteRoom(roomName);
    }

    private LiveKitWebhookEvent parse(byte[] body) {
        try {
            return objectMapper.readValue(body, LiveKitWebhookEvent.class);
        } catch (IOException e) {
            throw new ValidationException(ErrorCode.LIVEKIT_WEBHOOK_PAYLOAD_INVALID);
        }
    }
}
