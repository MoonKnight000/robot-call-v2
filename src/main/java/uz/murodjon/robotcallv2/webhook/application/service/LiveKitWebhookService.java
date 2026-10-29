package uz.murodjon.robotcallv2.webhook.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.flow.InboundCallGate;
import uz.murodjon.robotcallv2.agent.flow.InboundDecision;
import uz.murodjon.robotcallv2.agent.flow.InboundDecisionKind;
import uz.murodjon.robotcallv2.agent.livekit.LiveKitTelephonyService;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundRoute;
import uz.murodjon.robotcallv2.shared.util.PhoneMasker;
import uz.murodjon.robotcallv2.webhook.application.dto.LiveKitSipWebhookRequest;
import uz.murodjon.robotcallv2.webhook.application.dto.LiveKitSipWebhookResponse;
import uz.murodjon.robotcallv2.webhook.application.port.input.LiveKitWebhookUseCase;

/**
 * Implements {@link LiveKitWebhookUseCase} by querying {@link InboundCallGate#decide(String, String)}
 * and mapping the decision to LiveKit SIP dispatch response.
 */
@Service
public class LiveKitWebhookService implements LiveKitWebhookUseCase {

    private static final Logger log = LoggerFactory.getLogger(LiveKitWebhookService.class);
    private static final String DEFAULT_LANGUAGE = "uz-UZ";

    private final InboundCallGate inboundCallGate;
    private final LiveKitTelephonyService liveKitTelephonyService;

    public LiveKitWebhookService(InboundCallGate inboundCallGate, LiveKitTelephonyService liveKitTelephonyService) {
        this.inboundCallGate = inboundCallGate;
        this.liveKitTelephonyService = liveKitTelephonyService;
    }

    @Override
    public LiveKitSipWebhookResponse handleSipWebhook(LiveKitSipWebhookRequest request) {
        if (request == null) {
            return LiveKitSipWebhookResponse.reject("Empty webhook request", DEFAULT_LANGUAGE);
        }
        String did = request.resolveDid();
        String caller = request.resolveCaller();
        log.info("LiveKit SIP inbound webhook: did={}, caller={}, callId={}", did, PhoneMasker.mask(caller), request.callId());

        InboundDecision decision = inboundCallGate.decide(did, caller);
        return switch (decision.kind()) {
            case AGENT, FLOW -> {
                String roomName = resolveRoomName(request);
                if (liveKitTelephonyService.isEnabled()) {
                    liveKitTelephonyService.createCallRoom(roomName);
                }
                InboundRoute route = decision.route();
                boolean agent = decision.kind() == InboundDecisionKind.AGENT;
                yield LiveKitSipWebhookResponse.accept(roomName, route != null ? route.id() : null,
                        agent && route != null ? route.aiAgentId() : null,
                        !agent && route != null ? route.callFlowId() : null,
                        decision.language(), decision.vip());
            }
            case REJECT, CLOSED -> LiveKitSipWebhookResponse.reject(decision.message(), decision.language());
            case NO_ROUTE -> LiveKitSipWebhookResponse.reject("No route found for number: " + did, DEFAULT_LANGUAGE);
        };
    }

    private String resolveRoomName(LiveKitSipWebhookRequest request) {
        if (request.roomName() != null && !request.roomName().isBlank()) {
            return request.roomName();
        }
        String id = request.callId() != null && !request.callId().isBlank()
                ? request.callId()
                : String.valueOf(System.currentTimeMillis());
        return "inbound-" + id;
    }
}
